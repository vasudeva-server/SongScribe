/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.desktop.AppForegroundEvent;
import java.awt.desktop.AppForegroundListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatNativeMacLibrary;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.FileExtensions;
import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.io.SongFileWriter;
import songscribe.layout.PageModel;
import songscribe.lifecycle.Shutdown;
import songscribe.message.MessageCenter;
import songscribe.message.MessageLogger;
import songscribe.message.command.NewFileCommand;
import songscribe.message.command.OpenFileCommand;
import songscribe.message.command.PrintCommand;
import songscribe.message.command.RevertToSavedCommand;
import songscribe.message.command.SaveAsCommand;
import songscribe.message.command.SaveCommand;
import songscribe.message.command.ShowOpenDialogCommand;
import songscribe.message.command.ToggleLoopPlaybackCommand;
import songscribe.message.command.TogglePlayWithRepeatsCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.UndoStateDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.prefs.StartupAction;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.SaveAction;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.component.toolbar.MainToolbarPanel;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.ui.dialog.PropertiesStateStore;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.menu.MenuController;
import songscribe.ui.platform.mac.MacWindowControls;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.ExtensionFileFilter;
import songscribe.util.FileUtils;
import songscribe.util.ModifierState;
import songscribe.util.UIUtils;

public class MainFrame extends JFrame implements Printable {

    private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);

    // This directory is used to store preferences and logs
    public static final File SONGSCRIBE_DIR = new File(
        System.getProperty("user.home"),
        ".songscribe"
    );

    public static final int MIN_WINDOW_HEIGHT = 500;

    // Splash timing constants
    static final long MIN_SPLASH_DURATION_MS = 2_000;
    static final long MIDI_INIT_TIMEOUT_MS = 5_000;

    // State shared between main(), the startup gate, and reveal()
    private static long splashShownAtMs = 0;
    private static CountDownLatch midiReadyLatch = new CountDownLatch(0);
    private static Runnable pendingStartupAction = () -> {};

    static {
        if (!SONGSCRIBE_DIR.exists() && !SONGSCRIBE_DIR.mkdir()) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INITIALIZATION_ERROR,
                Strings.ERROR_DIRECTORY_CREATE
            );
        }

        var logFile = new File(SONGSCRIBE_DIR, "log");

        if (logFile.length() > 1_000_000L) {
            // noinspection ResultOfMethodCallIgnored
            logFile.delete();
        }
    }

    // Splash screen
    @Nullable
    private static SplashWindow splashWindow = null;

    // This class is shared by several applications. This is the name of the application.
    public String appName;

    // The music sheet that is displayed in the main window
    @Nullable
    protected ScoreView scoreView = null;

    // The current open file
    @Nullable
    protected File currentFile = null;

    @Nullable
    private JLabel titleBarLabel = null;

    /** macOS 26 (Tahoe) is the first release that left-aligns the window title. */
    private static final int MACOS_TAHOE_VERSION = 26;

    /**
     * True on macOS 26 and later. That release left-aligns the window title beside
     * the window controls and makes the title bar taller. The app draws its own
     * title into the title bar area, so it must follow the platform itself.
     */
    private static final boolean IS_MACOS_TAHOE_OR_LATER = SystemInfo.isMacOS &&
        SystemInfo.osVersion >= SystemInfo.toVersion(MACOS_TAHOE_VERSION, 0, 0, 0);

    private static final int MAC_TITLE_BAR_HEIGHT_PRE_TAHOE = 28;
    private static final int MAC_TITLE_BAR_HEIGHT_TAHOE = 32;

    private static final int MAC_TITLE_BAR_HEIGHT = IS_MACOS_TAHOE_OR_LATER
        ? MAC_TITLE_BAR_HEIGHT_TAHOE
        : MAC_TITLE_BAR_HEIGHT_PRE_TAHOE;

    /**
     * Gap in points between the right edge of the window controls and a left-aligned
     * title. macOS leaves this much room before the first title element, which is the
     * proxy icon in an app that shows one.
     */
    private static final int MAC_TITLE_CONTROLS_GAP = 16;

    private static final int MAC_TITLE_FONT_SIZE = 13;
    private static final double PRINT_EXTRA_MARGIN = 0.25 * 72;

    @Nullable
    private PrinterJob printerJob = null;

    /**
     * A startup error collected before the main window is shown. Non-fatal errors are
     * displayed as warnings after the window is revealed; a fatal error triggers
     * {@link RuntimeError#exit(String)} before the window appears.
     */
    public record StartupError(String title, String message, boolean fatal) {}

    private static final ConcurrentLinkedQueue<StartupError> STARTUP_ERRORS =
        new ConcurrentLinkedQueue<>();

    /** Enqueues a startup error to be drained by {@link #drainStartupErrors()}. */
    public static void enqueueStartupError(StartupError error) {
        STARTUP_ERRORS.add(error);
    }

    /**
     * Returns the first fatal {@link StartupError} in the queue, or {@code null} if
     * none exists.
     */
    @Nullable
    public static StartupError firstFatal() {
        for (var error : STARTUP_ERRORS) {
            if (error.fatal()) {
                return error;
            }
        }

        return null;
    }

    public MainFrame() {
        // We would like the cool transparent title bar on macOS
        if (SystemInfo.isMacOS && SystemInfo.isMacFullWindowContentSupported) {
            rootPane.putClientProperty("apple.awt.fullWindowContent", true);
            rootPane.putClientProperty("apple.awt.transparentTitleBar", true);
            rootPane.putClientProperty("apple.awt.windowTitleVisible", false);
        }

        appName = Strings.get(Strings.APP_SONGWRITER);

        // There are some tasks we need to perform the first time the app is run
        if (Prefs.getBoolean(PrefsKey.FIRST_RUN)) {
            firstRun();
        }

        // Initialize SystemFileChooser state persistence
        SystemFileChooser.setStateStore(new PropertiesStateStore());

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    Shutdown.now();
                }
            }
        );

        MessageCenter.subscribe(this);
        installShutdownTasks();
    }

    public static void main(String[] args) {
        try {
            // Initialize the minimal theme (Regular font face + preferred family)
            // before showing the splash so it renders in Source Sans, not a fallback.
            UIUtils.initMinimalTheme();
            showSplash();

            // Force a synchronous first paint so the splash appears immediately.
            var rawSplashContent = splashWindow != null ? splashWindow.getContentPane() : null;

            if (rawSplashContent instanceof JComponent splashContent) {
                splashContent.paintImmediately(0, 0, splashContent.getWidth(), splashContent.getHeight());
                Toolkit.getDefaultToolkit().sync();
            }

            // Record when the splash became visible so the gate can enforce the floor.
            splashShownAtMs = System.currentTimeMillis();

            // Start MIDI init on a daemon thread so it runs in parallel with font
            // installation and window construction.
            midiReadyLatch = MidiController.openMidiAsync();

            // Install remaining fonts while MIDI initializes in the background.
            UIUtils.installEagerFonts();

            MessageLogger.init();
            Song.setDefaultLineWidthProvider(PageModel::getDefaultLineWidthSs);

            // Build the main window but do NOT show it — reveal() will show it
            // after the gate fires on the EDT.
            var instance = getInstance();
            instance.initFrame();

            // Capture the startup action to run after the window is shown.
            var recents = RecentDocumentsManager.getRecents();
            var mostRecentPath = recents.isEmpty() ? null : recents.getFirst();

            if (args.length == 0) {
                pendingStartupAction = () -> performStartupAction(mostRecentPath);
            } else {
                var fileToOpen = new File(args[0]);

                if (fileToOpen.exists()) {
                    pendingStartupAction = () -> instance.handleOpenFile(fileToOpen);
                }
            }

            // All setup is done — start the gate, which will call reveal() on the EDT.
            startStartupGate();
        } catch (Exception e) {
            LOG.error("Application startup failed", e);
            enqueueStartupError(new StartupError(
                Strings.ALERT_TITLE_INITIALIZATION_ERROR,
                "Application startup failed",
                true
            ));
            drainStartupErrors();
        }
    }

    /**
     * Drains the startup error queue. Must be called on the EDT.
     * <p>
     * The splash is always hidden before any dialog is shown. If a fatal error is present,
     * {@link RuntimeError#exit(String, String)} is called and the main window
     * is not revealed. Otherwise each non-fatal error is shown as a warning dialog in queue
     * order and the queue is cleared — a second call with no newly enqueued errors shows
     * nothing.
     */
    static void drainStartupErrors() {
        var fatal = firstFatal();

        if (fatal != null) {
            hideSplash();
            throw RuntimeError.exit("Fatal startup error", fatal.message());
        }

        if (!STARTUP_ERRORS.isEmpty()) {
            hideSplash();

            for (var error : STARTUP_ERRORS) {
                OptionDialogs.showWarningMessage(null, error.title(), error.message());
            }

            STARTUP_ERRORS.clear();
        }
    }

    /** Starts the {@code "startup-gate"} daemon thread, which calls {@link #runStartupGate()}. */
    private static void startStartupGate() {
        var thread = new Thread(MainFrame::runStartupGate, "startup-gate");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Runs on the {@code "startup-gate"} thread. Waits for the minimum splash duration
     * floor to pass, then waits for MIDI init (capped), then schedules reveal() on the EDT.
     *
     * <p>The EDT installs a minimal theme, shows the splash and forces its first paint, kicks off
     * MIDI init on the {@code "midi-init"} thread, installs the remaining fonts, builds the main
     * window without showing it, records the pending startup action, and starts this gate. The gate
     * then sleeps out the splash floor, awaits the MIDI latch up to the cap, and schedules
     * {@code reveal()} back on the EDT, which drains startup errors — exiting on a fatal one
     * without hiding the splash — then hides the splash, shows the window, warns about any
     * non-fatal errors, and runs the pending startup action.
     *
     * <p>See {@code docs/lifecycle.md} for the full cross-thread sequence.
     */
    private static void runStartupGate() {
        var elapsedMs = System.currentTimeMillis() - splashShownAtMs;
        var floorMs = remainingFloorMs(elapsedMs);

        if (floorMs > 0) {
            try {
                Thread.sleep(floorMs);
            } catch (InterruptedException ignored) {}
        }

        var elapsedAfterFloor = System.currentTimeMillis() - splashShownAtMs;
        var capMs = remainingCapMs(elapsedAfterFloor);

        try {
            midiReadyLatch.await(capMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}

        SwingUtilities.invokeLater(() -> getInstance().reveal());
    }

    /**
     * Reveals the main window on the EDT after the startup gate fires.
     * Drains startup errors first — a fatal error exits before the window appears.
     */
    private void reveal() {
        drainStartupErrors();

        hideSplash();
        setVisible(true);

        // The app owns no window until now, so this is the first layout that can
        // measure the window controls.
        if (IS_MACOS_TAHOE_OR_LATER) {
            positionTitleAfterWindowControls();
        }

        forceInitialPaint();
        UIUtils.preWarmDialogPeer(this);
        ActivationGate.install(this);
        LOG.info("Application UI ready");
        requireScoreView().requestFocusInWindow();

        pendingStartupAction.run();
    }

    /**
     * Works around a macOS bug in the full-window-content path: a window that uses
     * {@code apple.awt.fullWindowContent} and is shown at close to the full height of
     * the screen's available area never presents its first frame, so it comes up
     * completely blank and stays that way until the user resizes it. The Swing layout
     * and paint are both correct — only the native surface is missing — so
     * {@code revalidate()}/{@code repaint()} do not help. Changing the window size once
     * it is on screen does, so shrink it by a pixel and restore it.
     * <p>
     * The trigger is how close the window's bottom edge comes to the bottom of the
     * available screen area — measured at roughly 25 px on a 1440 px-tall screen with
     * the Dock showing. {@link #setFrameSize()} always sizes the window to the full
     * available height, so it is always inside that band, and the workaround is
     * unconditional on macOS rather than tied to any particular Dock state.
     */
    private void forceInitialPaint() {
        if (!SystemInfo.isMacOS || !SystemInfo.isMacFullWindowContentSupported) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            var bounds = getBounds();
            setSize(bounds.width, bounds.height - 1);
            setSize(bounds.width, bounds.height);
        });
    }

    /**
     * Returns the remaining milliseconds to meet the minimum splash duration floor,
     * clamped to {@code [0, MIN_SPLASH_DURATION_MS]}.
     */
    static long remainingFloorMs(long elapsedMs) {
        return Math.clamp(MIN_SPLASH_DURATION_MS - elapsedMs, 0, MIN_SPLASH_DURATION_MS);
    }

    /**
     * Returns the remaining milliseconds before the MIDI init timeout cap,
     * clamped to {@code [0, MIDI_INIT_TIMEOUT_MS]}.
     */
    static long remainingCapMs(long elapsedMs) {
        return Math.clamp(MIDI_INIT_TIMEOUT_MS - elapsedMs, 0, MIDI_INIT_TIMEOUT_MS);
    }

    static void performStartupAction(@Nullable Path mostRecentPath) {
        var startupAction = Prefs.getChoice(PrefsKey.STARTUP_ACTION, StartupAction.class);

        if (ModifierState.isAltPressed()) {
            startupAction = StartupAction.DO_NOTHING;
        }

        switch (startupAction) {
            case DO_NOTHING -> {}
            case SHOW_FILE_CHOOSER -> MessageCenter.post(new ShowOpenDialogCommand());
            case OPEN_MOST_RECENT -> {
                if (mostRecentPath == null) {
                    return;
                }

                if (mostRecentPath.toFile().exists()) {
                    MessageCenter.post(new OpenFileCommand(mostRecentPath.toFile()));
                } else {
                    OptionDialogs.showErrorMessage(
                        getInstance(),
                        Strings.ALERT_TITLE_OPEN_RECENT_DOCUMENT,
                        Strings.ERROR_RECENT_DOCUMENT_NOT_FOUND,
                        mostRecentPath.getFileName()
                    );
                }
            }
        }
    }

    private static final class InstanceHolder {

        // This class is a singleton
        private static final MainFrame instance = new MainFrame();
    }

    public static MainFrame getInstance() {
        return InstanceHolder.instance;
    }

    protected static void showSplash() {
        splashWindow = new SplashWindow();
        splashWindow.showSplash();
    }

    public static void hideSplash() {
        if (splashWindow == null) {
            return;
        }

        splashWindow.closeSplash();
        splashWindow = null;
    }

    // Reserve for future use, such as showing a welcome message
    // or tutorial on the first run
    private void firstRun() {
        Prefs.put(PrefsKey.FIRST_RUN, false);
    }

    public void initFrame() {
        // Initialize action constants before anything else in this method uses them.
        //
        // main() constructs the singleton, which calls initFrame() to wire the UI; initFrame()
        // calls Actions.initialize(this) to populate the Actions.* constants, and the first read
        // of one of those constants is MenuController.init(this). See docs/lifecycle.md.
        Actions.initialize(this);
        PlaybackController.initialize(this);
        PreviewElementManager.initialize();

        setTitle(appName);
        setAppIcon();

        // A lot of init code depends on this being set
        scoreView = new ScoreView(this::setCurrentFile);
        PlaybackController.register(scoreView);


        initContent();
        updateTitle();

        installDesktopHandlers();
        MenuController.init(this);

        // When the application goes to the background, cancel any pending paste and activate
        // the glass pane so the reactivation click is consumed. ActivationGate.activate() posts
        // ApplicationDidEnterBackgroundNotification, which is what hides the insertion note —
        // see PreviewElementManager.applicationDidEnterBackground.
        // Use the Desktop API on macOS, fall back to WindowListener elsewhere.
        var usingDesktopApi = false;

        if (Desktop.isDesktopSupported()) {
            var desktop = Desktop.getDesktop();

            if (desktop.isSupported(Desktop.Action.APP_EVENT_FOREGROUND)) {
                desktop.addAppEventListener(new AppForegroundListener() {
                    @Override
                    public void appRaisedToForeground(AppForegroundEvent e) {
                        ActivationGate.appRaisedToForeground();
                    }

                    @Override
                    public void appMovedToBackground(AppForegroundEvent e) {
                        cancelPendingPlacement();
                        ActivationGate.activate();
                    }
                });
                usingDesktopApi = true;
            }
        }

        if (!usingDesktopApi) {
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowActivated(WindowEvent e) {
                    ActivationGate.appRaisedToForeground();
                }

                @Override
                public void windowDeactivated(WindowEvent e) {
                    cancelPendingPlacement();
                    ActivationGate.activate();
                }
            });
        }

        setFrameSize();
    }

    // Backgrounding the app cancels a pending insertion-point placement, whatever was
    // going to be placed — both background paths (Desktop appMovedToBackground and the
    // windowDeactivated fallback) call here.
    private void cancelPendingPlacement() {
        EditModeManager.getInsertionPointMode().cancel();
    }

    private void installDesktopHandlers() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }

        var desktop = Desktop.getDesktop();

        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(_ -> Actions.ABOUT_ACTION.perform(this));
        }

        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(_ -> handlePrefs());
        }

        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(event -> handleOpenFile(event.getFiles().getFirst()));
        }

        if (desktop.isSupported(Desktop.Action.APP_PRINT_FILE)) {
            desktop.setPrintFileHandler(event -> {
                handleOpenFile(event.getFiles().getFirst());
                handlePrint();
            });
        }

        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((_, response) -> {
                Shutdown.now();
                response.cancelQuit();
            });
        }
    }

    private void setAppIcon() {
        var smallIcon = new ImageIcon(
            "icons/SongWriter.iconset/icon256x256.png"
        ).getImage();

        var smallRetinaIcon = new ImageIcon(
            "icons/SongWriter.iconset/icon256x256@2x.png"
        ).getImage();

        var largeIcon = new ImageIcon(
            "icons/SongWriter.iconset/icon512x512.png"
        ).getImage();

        var largeRetinaIcon = new ImageIcon(
            "icons/SongWriter.iconset/icon512x512@2x.png"
        ).getImage();

        var image = new BaseMultiResolutionImage(
            smallIcon,
            smallRetinaIcon,
            largeIcon,
            largeRetinaIcon
        );

        setIconImage(image);
    }

    private void initContent() {
        // We lay out the content in a border layout: the toolbar in NORTH, the ScoreView in
        // CENTER, and the status bar in SOUTH.
        var contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());


        var toolbarPanel = new MainToolbarPanel();


        // On macOS with full window content, draw a custom title in the
        // title bar area and place it above the toolbar.
        if (SystemInfo.isMacOS && SystemInfo.isMacFullWindowContentSupported) {

            var titleBar = new TitlePanel();
            titleBar.setPreferredSize(new Dimension(0, MAC_TITLE_BAR_HEIGHT));

            var alignment = IS_MACOS_TAHOE_OR_LATER
                ? SwingConstants.LEADING
                : SwingConstants.CENTER;

            titleBarLabel = new JLabel("", alignment) {
                @Override
                public void updateUI() {
                    super.updateUI();
                    updateTitle();   // re-render with fresh UIManager colors
                }
            };
            // macOS private system UI font for native title bar appearance
            titleBarLabel.setFont(new Font(".AppleSystemUIFont", Font.BOLD, MAC_TITLE_FONT_SIZE));

            if (IS_MACOS_TAHOE_OR_LATER) {
                positionTitleAfterWindowControls();

                // FlatLaf republishes the control bounds when the native window
                // appears and on every full-screen toggle. The measurement itself
                // never changes; the indent does, because full screen hides the
                // controls.
                rootPane.addPropertyChangeListener(
                    FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS,
                    event -> positionTitleAfterWindowControls()
                );
            }

            titleBar.add(titleBarLabel, BorderLayout.CENTER);

            var northPanel = new JPanel(new BorderLayout());
            northPanel.add(titleBar, BorderLayout.NORTH);
            northPanel.add(toolbarPanel, BorderLayout.CENTER);
            contentPane.add(northPanel, BorderLayout.NORTH);
        } else {

            contentPane.add(toolbarPanel, BorderLayout.NORTH);
        }


        requireScoreView().init();

        contentPane.add(requireScoreView().requireScrollPane(), BorderLayout.CENTER);
        contentPane.add(new StatusBar(), BorderLayout.SOUTH);

    }

    /**
     * Indents the title so that it clears the macOS window controls.
     *
     * @effects sets the title label's border; messages AppKit through
     *          {@link MacWindowControls}, so call on the event dispatch thread
     */
    private void positionTitleAfterWindowControls() {
        if (titleBarLabel == null) {
            return;
        }

        // Full screen hides the controls, so the title starts at the gap alone.
        // FlatLaf answers for this window; MacWindowControls reports a system
        // metric and cannot say which window is in full screen.
        var isFullScreen = FlatNativeMacLibrary.isLoaded() &&
            FlatNativeMacLibrary.isWindowFullScreen(this);

        var controlsWidth = isFullScreen
            ? 0
            : (int) Math.round(MacWindowControls.zoomControlRightEdge());

        titleBarLabel.setBorder(
            BorderFactory.createEmptyBorder(0, controlsWidth + MAC_TITLE_CONTROLS_GAP, 0, 0)
        );
    }

    public void setFrameSize() {
        var size = getLayout().preferredLayoutSize(this);
        var scrollBarWidth = ((Integer) UIManager.get("ScrollBar.width"));
        var maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();

        var width = Math.min(size.width + scrollBarWidth, maxBounds.width);
        var height = maxBounds.height;
        var x = maxBounds.x + (maxBounds.width - width) / 2;

        setBounds(x, maxBounds.y, width, height);

        var minSize = getLayout().minimumLayoutSize(this);
        setMinimumSize(new Dimension(minSize.width, MIN_WINDOW_HEIGHT));
    }

    boolean showSaveDialog() {
        if (scoreView == null || !scoreView.getSong().isModified()) {
            return true;
        }

        var docName = getDisplayName();
        var saveIdx = 0;
        var dontSaveIdx = 1;
        var options = new Object[]{
            Strings.get(Strings.BUTTON_SAVE),
            Strings.get(Strings.BUTTON_DONT_SAVE),
            Strings.get(Strings.BUTTON_CANCEL)
        };
        var answer = OptionDialogs.showOptionDialog(
            this,
            Strings.CONFIRM_TITLE_SAVE_CHANGES,
            Strings.CONFIRM_SAVE_MODIFIED,
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[saveIdx],
            docName
        );

        if (answer == saveIdx) {
            return SaveAction.createAction(this).perform(this);
        }

        return answer == dontSaveIdx;
    }

    void updateTitle() {
        if (scoreView == null || !scoreView.isInitialized()) {
            return;
        }

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateTitle);
            return;
        }

        var name = getDisplayName();
        var isModified = scoreView.getSong().isModified();
        var title = isModified ? '•' + name : name;
        setTitle(title);

        if (titleBarLabel != null) {
            var labelFg = UIManager.getColor("Label.foreground");

            if (isModified) {
                var titleColor = UIManager.getColor("Label.foreground");
                var editedColor = UIManager.getColor("Label.disabledForeground");
                var hexTitleColor = String.format("#%06x", titleColor.getRGB() & 0xFFFFFF);
                var hexEditedColor = String.format("#%06x", editedColor.getRGB() & 0xFFFFFF);
                titleBarLabel.setText(
                    Strings.get(Strings.LABEL_TITLE_EDITED, hexTitleColor, name, hexEditedColor)
                );
            } else {
                titleBarLabel.setText(name);
            }
        }
    }

    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        updateTitle();
    }

    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        updateTitle();
    }

    @Handler
    public void documentWasSaved(DocumentWasSavedNotification message) {
        updateTitle();
    }

    /**
     * Undo/redo recomputes the modified flag after the replay bracket's
     * SongDidChangeNotification has already refreshed the title with the stale
     * flag, so the title must be refreshed again once the new state is known.
     */
    @Handler
    public void undoStateDidChange(UndoStateDidChangeNotification message) {
        updateTitle();
    }

    @Nullable
    public ScoreView getScoreView() {
        return scoreView;
    }

    /**
     * Returns the scoreView, exiting fatally if it is null.
     * Use this in code that runs after initialization, where a null scoreView
     * indicates corrupted application state.
     */
    public ScoreView requireScoreView() {
        var result = scoreView;

        if (result == null) {
            throw RuntimeError.exit("scoreView not initialized");
        }

        return result;
    }

    @Nullable
    public File getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(@Nullable File saveFile) {
        currentFile = saveFile;
        updateTitle();
    }

    String getDisplayName() {
        if (currentFile == null) {
            return Strings.get(Strings.DOCUMENT_UNTITLED);
        }

        return FileUtils.getPathWithoutExtension(currentFile.getName());
    }

    @Handler
    public void handleNewFile(NewFileCommand message) {
        if (!showSaveDialog()) {
            return;
        }

        setCurrentFile(null);

        if (scoreView != null) {
            scoreView.setSong(new Song());
            scoreView.installDocumentFonts(DocumentFonts.defaultFonts());
            scoreView.requestFocusInWindow();
        }

        LOG.info("New song");
    }

    /**
     * Opens Preferences, the same way the menu action does.
     * <p>
     * Routed through the action rather than building a dialog here: Preferences is the one
     * non-modal dialog, so its opener is what keeps a second window from appearing beside
     * the one already up, and two entry points that did not share an opener could not keep
     * that between them.
     */
    public void handlePrefs() throws IllegalStateException {
        Actions.PREFERENCES_ACTION.open();
    }

    private void installShutdownTasks() {
        Shutdown.registerConfirmTask("save-dirty-doc", this::showSaveDialog);
        Shutdown.registerEDTTask("disable-main-frame", () -> setEnabled(false));
    }

    @Handler
    public void handleOpenFile(OpenFileCommand message) {
        handleOpenFile(message.getFile());
    }

    @Handler
    public void handleShowOpenDialog(ShowOpenDialogCommand message) {
        var dialog = new PlatformFileDialog(
            this,
            Strings.get(Strings.DIALOG_OPEN_TITLE),
            true,
            new ExtensionFileFilter[] {
                new ExtensionFileFilter(
                    Strings.get(Strings.FILTER_ALL_SUPPORTED),
                    FileExtensions.MUSICXML,
                    FileExtensions.XML,
                    FileExtensions.SONGWRITER
                ),
                new ExtensionFileFilter(
                    Strings.get(Strings.FILTER_MUSICXML),
                    FileExtensions.MUSICXML,
                    FileExtensions.XML
                ),
                new ExtensionFileFilter(
                    Strings.get(Strings.FILTER_SONGSCRIBE),
                    FileExtensions.SONGWRITER
                )
            },
            0
        );

        if (dialog.showDialog()) {
            handleOpenFile(dialog.getFile());
        }
    }

    public void handleOpenFile(File file) {
        if (!showSaveDialog() || scoreView == null) {
            return;
        }

        openFileAndUpdateRecents(file);
    }

    @Handler
    public void handleRevertToSaved(RevertToSavedCommand message) {
        if (scoreView == null || currentFile == null) {
            return;
        }

        var answer = OptionDialogs.showConfirmDialog(
            this,
            Strings.CONFIRM_TITLE_REVERT_TO_SAVED,
            Strings.CONFIRM_REVERT_TO_SAVED,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        openFileAndUpdateRecents(currentFile);
    }

    // Loads the file into the score and records the result in the recent-documents list.
    private void openFileAndUpdateRecents(File file) {
        if (scoreView == null) {
            return;
        }

        var path = file.toPath().toAbsolutePath();
        var opened = scoreView.openFile(file, true);

        if (opened) {
            RecentDocumentsManager.add(path);
        } else {
            RecentDocumentsManager.remove(path);
        }
    }

    @Handler
    public void handlePrint(PrintCommand message) {
        handlePrint();
    }

    public void handlePrint() {
        printerJob = PrinterJob.getPrinterJob();
        printerJob.setPrintable(this);

        if (printerJob.printDialog()) {
            try {
                printerJob.print();
            } catch (PrinterException e1) {
                OptionDialogs.showErrorMessage(
                    this,
                    Strings.ALERT_TITLE_PRINT_ERROR,
                    Strings.ERROR_PRINT
                );
            }
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
        if (pageIndex >= 1) {
            return NO_SUCH_PAGE;
        }

        if (printerJob == null) {
            throw RuntimeError.exit("printerJob not initialized");
        }

        var format = printerJob.validatePage(pageFormat);
        var paper = format.getPaper();

        // Add 1/4 inch margin to ensure it's within the printable area
        var width = paper.getImageableWidth() - (PRINT_EXTRA_MARGIN * 2);
        var x = format.getImageableX() + PRINT_EXTRA_MARGIN;
        paper.setImageableArea(
            x,
            paper.getImageableY(),
            width,
            paper.getImageableHeight()
        );
        format.setPaper(paper);

        graphics.translate(
            (int) format.getImageableX(),
            (int) format.getImageableY()
        );

        var g2 = (Graphics2D) graphics;

        // Print not yet implemented with component-based rendering
        g2.setColor(Color.BLACK);
        g2.drawString(
            Strings.get(Strings.ERROR_PRINT_NOT_IMPLEMENTED),
            50,
            50
        );
        g2.drawString(
            Strings.get(Strings.ERROR_EXPORT_PENDING),
            50,
            70
        );

        return PAGE_EXISTS;
    }

    @Handler
    public void handleSave(SaveCommand message) {
        save();
    }

    @Handler
    public void handleSaveAs(SaveAsCommand message) {
        saveAsNewFile();
    }

    /** Returns true on success, false on write failure or user-cancelled file chooser. */
    public boolean save() {
        return (currentFile == null) ? saveAsNewFile() : saveCurrentFile();
    }

    boolean saveCurrentFile() {
        if (currentFile == null || scoreView == null) {
            return false;
        }

        var song = scoreView.getSong();

        try {
            // scoreView is both the document fonts holder and the line layout provider: saved
            // coordinates come from the live layouts the score is painted from.
            if (SongFileWriter.write(song, scoreView, scoreView, currentFile)) {
                song.setModified(false);
                LOG.info("Saved: {}", currentFile.getName());
                MessageCenter.post(new DocumentWasSavedNotification());
                return true;
            }
        } catch (IOException e) {
            LOG.error("Failed to save {}", currentFile.getName(), e);
        }

        OptionDialogs.showErrorMessage(
            this,
            Strings.ALERT_TITLE_FILE_ERROR,
            Strings.ERROR_FILE_SAVE
        );
        return false;
    }

    boolean saveAsNewFile() {
        if (scoreView == null) {
            return false;
        }

        var suggestedFileName = currentFile == null
            ? scoreView.getSuggestedFileName()
            : FileUtils.getPathWithoutExtension(currentFile.getName());
        var saveFile = PlatformFileDialog.showSaveDialog(
            this,
            Strings.get(Strings.DIALOG_SAVE_AS_TITLE),
            Strings.get(Strings.FILTER_MUSICXML),
            suggestedFileName,
            FileExtensions.MUSICXML
        );

        if (saveFile == null) {
            return false;
        }

        setCurrentFile(saveFile);
        var saved = saveCurrentFile();

        if (saved) {
            RecentDocumentsManager.add(saveFile.toPath().toAbsolutePath());
        }

        return saved;
    }

    @Handler
    public void handleToggleLoopPlayback(ToggleLoopPlaybackCommand message) {
        Prefs.put(PrefsKey.LOOP_PLAYBACK, message.isSelected());
    }

    @Handler
    public void handleTogglePlayWithRepeats(TogglePlayWithRepeatsCommand message) {
        Prefs.put(PrefsKey.PLAY_WITH_REPEATS, message.isSelected());
    }

    /**
     * The title bar area drawn by the app when macOS full window content is in
     * effect. Because the app's content covers the native title bar, macOS never
     * sees the clicks that would normally zoom the window, so the standard
     * double-click-to-zoom gesture is reimplemented here.
     */
    private static final class TitlePanel extends JPanel {
        private TitlePanel() {
            super(new BorderLayout());

            addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (UIUtils.isLeftDoubleClick(e)) {
                            toggleZoom();
                        }
                    }
                }
            );
        }

        private void toggleZoom() {
            if (!(SwingUtilities.getWindowAncestor(this) instanceof Frame frame)) {
                return;
            }

            var isZoomed = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) ==
                Frame.MAXIMIZED_BOTH;

            if (isZoomed) {
                frame.setExtendedState(Frame.NORMAL);
            } else {
                frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            }
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setBackground(UIManager.getColor("ToolBar.background"));
        }
    }
}
