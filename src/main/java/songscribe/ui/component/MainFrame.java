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
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.desktop.AppForegroundEvent;
import java.awt.desktop.AppForegroundListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BaseMultiResolutionImage;
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
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatNativeMacLibrary;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.FileExtensions;
import songscribe.SongScribe;
import songscribe.Strings;
import songscribe.dom.ChangeReport;
import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.io.SongFileWriter;
import songscribe.layout.PageModel;
import songscribe.lifecycle.Shutdown;
import songscribe.lint.LintRule;
import songscribe.lint.Linter;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.command.NewFileCommand;
import songscribe.message.command.OpenFileCommand;
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

public class MainFrame extends JFrame {

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

    // The Messages panel, to the right of the score in the content split
    private final ReportPanel reportPanel = new ReportPanel(this);

    // The score on the left and the Messages panel on the right; holds the window's content area
    private final JSplitPane contentSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    /**
     * The theme's divider size, restored when the Messages panel is shown; hiding the panel sets
     * the pane's to 0.
     */
    private int messagesDividerSize = 0;

    private static final int MACOS_TAHOE_VERSION = 26;

    /**
     * That release left-aligns the title and makes the title bar taller; the app draws its own
     * title there, so it must follow.
     */
    private static final boolean IS_MACOS_TAHOE_OR_LATER = SystemInfo.isMacOS &&
        SystemInfo.osVersion >= SystemInfo.toVersion(MACOS_TAHOE_VERSION, 0, 0, 0);

    private static final int MAC_TITLE_BAR_HEIGHT_PRE_TAHOE = 28;
    private static final int MAC_TITLE_BAR_HEIGHT_TAHOE = 32;

    private static final int MAC_TITLE_BAR_HEIGHT = IS_MACOS_TAHOE_OR_LATER
        ? MAC_TITLE_BAR_HEIGHT_TAHOE
        : MAC_TITLE_BAR_HEIGHT_PRE_TAHOE;

    private static final int MAC_TITLE_CONTROLS_GAP = 16;

    private static final int MAC_TITLE_FONT_SIZE = 13;

    /**
     * A fatal error exits through {@link RuntimeError#exit(String)} before the window appears;
     * non-fatal errors are shown as warnings after it is revealed.
     */
    public record StartupError(String title, String message, boolean fatal) {}

    private static final ConcurrentLinkedQueue<StartupError> STARTUP_ERRORS =
        new ConcurrentLinkedQueue<>();

    public static void enqueueStartupError(StartupError error) {
        STARTUP_ERRORS.add(error);
    }

    /** {@code null} if no fatal error is queued. */
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

        installShutdownTasks();
        MessageSubscription.addProcessListener(this);
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

            // Build the main window while MIDI initializes in the background, but do NOT
            // show it — reveal() will show it after the gate fires on the EDT.
            var instance = SongScribe.buildMainWindow();

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
     * Must be called on the EDT. The splash is hidden before any dialog is shown.
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

    private static void startStartupGate() {
        var thread = new Thread(MainFrame::runStartupGate, "startup-gate");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Runs on the {@code "startup-gate"} thread.
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
        focusScore();

        pendingStartupAction.run();
    }

    /**
     * Works around a macOS bug: a window using {@code apple.awt.fullWindowContent} shown near
     * the full available screen height never presents its first frame, and only a size change
     * fixes it. Unconditional on macOS because {@link #setFrameSize()} always sizes the window
     * to the full available height.
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

    static long remainingFloorMs(long elapsedMs) {
        return Math.clamp(MIN_SPLASH_DURATION_MS - elapsedMs, 0, MIN_SPLASH_DURATION_MS);
    }

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

        setTitle(appName);
        setAppIcon();

        // A lot of init code depends on this being set
        scoreView = new ScoreView(this::setCurrentFile);
        PlaybackController.register(scoreView);

        initContent();
        updateTitle();

        installDesktopHandlers();
        MenuController.init(this);
        installFocusTracking();

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
                Actions.PRINT_ACTION.perform(this);
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
        // We lay out the content in a border layout: the toolbar in NORTH, the split of the
        // ScoreView over the Messages panel in CENTER, and the status bar in SOUTH.
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

        initContentSplitPane();
        contentPane.add(contentSplitPane, BorderLayout.CENTER);
        contentPane.add(new StatusBar(), BorderLayout.SOUTH);
    }

    private void initContentSplitPane() {
        contentSplitPane.setLeftComponent(requireScoreView().requireScrollPane());
        contentSplitPane.setRightComponent(reportPanel);

        // A window resize goes entirely to the score, so the panel keeps its width.
        contentSplitPane.setResizeWeight(1);
        messagesDividerSize = contentSplitPane.getDividerSize();

        // A theme change installs a new UI, and with it a new divider.
        installDividerReleaseListener();
        contentSplitPane.addPropertyChangeListener("UI", _ -> installDividerReleaseListener());

        applyMessagesPanelVisible(Prefs.getBoolean(PrefsKey.MESSAGES_PANEL_VISIBLE));
    }

    /**
     * The width is saved on release, not on each divider move: every preference write saves the
     * whole file.
     */
    private void installDividerReleaseListener() {
        if (!(contentSplitPane.getUI() instanceof BasicSplitPaneUI ui)) {
            return;
        }

        ui.getDivider().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (reportPanel.isVisible()) {
                    Prefs.put(
                        PrefsKey.MESSAGES_PANEL_WIDTH,
                        messagesPanelRight() - contentSplitPane.getDividerLocation()
                    );
                }
            }
        });
    }

    /**
     * Every visibility change goes through here, so the Messages action's selected state always
     * agrees with the panel.
     */
    private void applyMessagesPanelVisible(boolean visible) {
        reportPanel.setVisible(visible);
        Actions.TOGGLE_MESSAGES_ACTION.setPanelShown(visible);
        contentSplitPane.setDividerSize(visible ? messagesDividerSize : 0);

        if (visible) {
            placeDividerAtSavedWidth();
        }
    }

    /**
     * The place is measured from the pane's right edge, so it waits until the pane first has a
     * size.
     */
    private void placeDividerAtSavedWidth() {
        if (contentSplitPane.getWidth() <= 0) {
            contentSplitPane.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    contentSplitPane.removeComponentListener(this);
                    placeDividerAtSavedWidth();
                }
            });
            return;
        }

        var panelWidth = Prefs.getInt(PrefsKey.MESSAGES_PANEL_WIDTH);
        contentSplitPane.setDividerLocation(Math.max(0, messagesPanelRight() - panelWidth));
    }

    private int messagesPanelRight() {
        return contentSplitPane.getWidth()
            - contentSplitPane.getInsets().right
            - contentSplitPane.getDividerSize();
    }

    /** Leaves keyboard focus where it is. */
    public void showMessagesPanel() {
        if (reportPanel.isVisible()) {
            return;
        }

        applyMessagesPanelVisible(true);
        Prefs.put(PrefsKey.MESSAGES_PANEL_VISIBLE, true);
    }

    /** Keyboard focus stays where it is, or moves to the score when the panel holds it. */
    public void hideMessagesPanel() {
        if (!reportPanel.isVisible()) {
            return;
        }

        // A hidden component gives its focus to the next one in the traversal order, which is not
        // always the score.
        var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        var panelHadFocus = focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, reportPanel);

        applyMessagesPanelVisible(false);
        Prefs.put(PrefsKey.MESSAGES_PANEL_VISIBLE, false);

        if (panelHadFocus) {
            focusScore();
        }
    }

    public boolean isMessagesPanelVisible() {
        return reportPanel.isVisible();
    }

    /** The move happens when the focus system processes the request. */
    public void focusScore() {
        requireScoreView().requestFocusInWindow();
    }

    private void installFocusTracking() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addPropertyChangeListener("permanentFocusOwner", _ -> syncScoreAction());
    }

    /**
     * A check box menu item flips its own selected state when clicked, and the click does not
     * always move focus, so the Score action calls this after it acts.
     */
    public void syncScoreAction() {
        var owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        var scoreView = requireScoreView();
        Actions.FOCUS_SCORE_ACTION.setSelected(
            owner != null && SwingUtilities.isDescendingFrom(owner, scoreView.requireScrollPane()));
    }

    public void toggleZoom() {
        var isZoomed = (getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        setExtendedState(isZoomed ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
    }

    /**
     * Call on the event dispatch thread.
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
     * Undo/redo recomputes the modified flag after the replay bracket's notification has refreshed
     * the title with the stale flag.
     */
    @Handler
    public void undoStateDidChange(UndoStateDidChangeNotification message) {
        updateTitle();
    }

    @Nullable
    public ScoreView getScoreView() {
        return scoreView;
    }

    /** {@code null} before the score view or its controller exists. */
    public @Nullable ScoreViewController getScoreViewController() {
        return scoreView != null ? scoreView.getController() : null;
    }

    /**
     * For code that runs after initialization.
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
            scoreView.setSong(new Song(), ChangeReport.EMPTY);
            scoreView.installDocumentFonts(DocumentFonts.defaultFonts());
            scoreView.requestFocusInWindow();
        }

        LOG.info("New song");
    }

    /**
     * Routed through the action: Preferences is the one non-modal dialog, and its opener keeps a
     * second window from appearing.
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
        var opened = scoreView.openFile(file);

        if (opened) {
            RecentDocumentsManager.add(path);
        } else {
            RecentDocumentsManager.remove(path);
        }
    }

    @Handler
    public void handleSave(SaveCommand message) {
        save();
    }

    @Handler
    public void handleSaveAs(SaveAsCommand message) {
        saveAsNewFile();
    }

    public boolean save() {
        return (currentFile == null) ? saveAsNewFile() : saveCurrentFile();
    }

    private boolean saveCurrentFile() {
        if (currentFile == null) {
            return false;
        }

        return writeSong(currentFile);
    }

    /**
     * Leaves the window's current file alone. Every save goes through here, so this is the one
     * place a save is gated: a finding carrying {@link LintRule.Flag#BLOCKS_EXPORT} first asks
     * the user to confirm, Yes by default; where dialogs are suppressed the default is taken and
     * the file is written.
     */
    private boolean writeSong(File file) {
        if (scoreView == null) {
            return false;
        }

        var song = scoreView.getSong();

        if (song.getLinter().hasFlag(LintRule.Flag.BLOCKS_EXPORT)) {
            var answer = OptionDialogs.showConfirmDialog(
                this,
                Strings.CONFIRM_TITLE_SONG_HAS_ERRORS,
                Strings.CONFIRM_SAVE_WITH_ERRORS,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                JOptionPane.YES_OPTION
            );

            if (answer != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        try {
            // scoreView is both the document fonts holder and the line layout provider: saved
            // coordinates come from the live layouts the score is painted from.
            if (SongFileWriter.write(song, scoreView, scoreView, file)) {
                song.setModified(false);
                LOG.info("Saved: {}", file.getName());
                MessageCenter.post(new DocumentWasSavedNotification());
                return true;
            }
        } catch (IOException e) {
            LOG.error("Failed to save {}", file.getName(), e);
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

        if (!writeSong(saveFile)) {
            return false;
        }

        setCurrentFile(saveFile);
        RecentDocumentsManager.add(saveFile.toPath().toAbsolutePath());
        return true;
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
     * The app's content covers the native title bar, so macOS never sees the clicks that zoom
     * the window; double-click-to-zoom is reimplemented here.
     */
    private final class TitlePanel extends JPanel {
        private TitlePanel() {
            super(new BorderLayout());

            addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (UIUtils.isLeftDoubleClick(e)) {
                            MainFrame.this.toggleZoom();
                        }
                    }
                }
            );
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setBackground(UIManager.getColor("ToolBar.background"));
        }
    }
}
