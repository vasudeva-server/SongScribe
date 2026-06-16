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

import module java.desktop;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.Version;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFonts;
import songscribe.FileExtensions;
import songscribe.util.FileUtils;
import songscribe.util.ExtensionFileFilter;
import songscribe.io.SongIO;
import songscribe.lifecycle.Shutdown;
import songscribe.message.MessageCenter;
import songscribe.message.MessageLogger;
import songscribe.message.command.NewFileCommand;
import songscribe.message.command.OpenFileCommand;
import songscribe.message.command.PrintCommand;
import songscribe.message.command.SaveAsCommand;
import songscribe.message.command.SaveCommand;
import songscribe.message.command.ShowOpenDialogCommand;
import songscribe.message.command.ToggleLoopPlaybackCommand;
import songscribe.message.command.TogglePlayWithRepeatsCommand;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.dom.Song;
import songscribe.layout.PageModel;
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
import songscribe.ui.dialog.WhatsNewDialog;
import songscribe.ui.menu.MenuController;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.ModifierState;
import songscribe.util.UIUtils;
import songscribe.util.Utils;

public class MainFrame extends JFrame implements Printable {

    private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);

    // This directory is used to store preferences and logs
    public static final File SONGSCRIBE_DIR = new File(
        System.getProperty("user.home"),
        ".songscribe"
    );

    public static final int MIN_WINDOW_HEIGHT = 500;

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

    private static final int MAC_TITLE_BAR_HEIGHT = 28;
    private static final int MAC_TITLE_FONT_SIZE = 13;
    private static final double PRINT_EXTRA_MARGIN = 0.25 * 72;

    @Nullable
    private PrinterJob printerJob = null;

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
            showSplash();

            MessageLogger.init();
            MidiController.openMidi();
            Song.setDefaultLineWidthProvider(PageModel::getDefaultLineWidthSs);
            var instance = getInstance();
            var recents = RecentDocumentsManager.getRecents();
            var mostRecentPath = recents.isEmpty() ? null : recents.getFirst();
            instance.initFrame();

            if (
                !Version.PUBLIC_VERSION.equals(
                    Prefs.getString(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION)
                ) &&
                    new File(WhatsNewDialog.WHATS_NEW_FILE).exists()
            ) {
                Prefs.put(
                    PrefsKey.LAST_SEEN_WHATS_NEW_VERSION,
                    Version.PUBLIC_VERSION
                );
                new WhatsNewDialog().setVisible(true);
            }

            if (args.length == 0) {
                SwingUtilities.invokeLater(() -> performStartupAction(mostRecentPath));
            } else {
                var fileToOpen = new File(args[0]);

                if (fileToOpen.exists()) {
                    instance.handleOpenFile(fileToOpen);
                }
            }
        } catch (Exception e) {
            throw RuntimeError.exit("Application startup failed", e);
        }
    }

    static void performStartupAction(@Nullable Path mostRecentPath) {
        var startupAction = StartupAction.DO_NOTHING;

        try {
            startupAction = StartupAction.valueOf(
                Prefs.getString(PrefsKey.STARTUP_ACTION)
            );
        } catch (IllegalArgumentException ignored) {}

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

        setTitle(appName);
        setAppIcon();

        // A lot of init code depends on this being set
        scoreView = new ScoreView(this::setCurrentFile);
        PlaybackController.register(scoreView);


        initContent();
        updateTitle();

        installDesktopHandlers();
        MenuController.init(this);

        Actions.CONTROL_ACTION_GROUP.selectNext();


        // When the application goes to the background, hide the insertion note
        // and activate the glass pane so the reactivation click is consumed.
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
                        hidePreviewNote();
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
                    hidePreviewNote();
                    ActivationGate.activate();
                }
            });
        }

        setFrameSize();
        hideSplash();
        Utils.sleep(100);
        setVisible(true);
        UIUtils.preWarmDialogPeer(this);
        ActivationGate.install(this);
        LOG.info("Application UI ready");

        scoreView.requestFocusInWindow();
    }

    private void hidePreviewNote() {
        PreviewElementManager.hidePreviewElement(true);
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
        // We lay out the content in a border layout:
        // +---------------------+
        // | NORTH: Toolbar      |
        // | CENTER: ScoreView       |
        // +---------------------+
        var contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());


        var toolbarPanel = new MainToolbarPanel();


        // On macOS with full window content, draw a custom title in the
        // title bar area and place it above the toolbar.
        if (SystemInfo.isMacOS && SystemInfo.isMacFullWindowContentSupported) {

            var titleBar = new TitlePanel();
            titleBar.setPreferredSize(new Dimension(0, MAC_TITLE_BAR_HEIGHT));

            titleBarLabel = new JLabel("", SwingConstants.CENTER) {
                @Override
                public void updateUI() {
                    super.updateUI();
                    updateTitle();   // re-render with fresh UIManager colors
                }
            };
            // macOS private system UI font for native title bar appearance
            titleBarLabel.setFont(new Font(".AppleSystemUIFont", Font.BOLD, MAC_TITLE_FONT_SIZE));
            titleBar.add(titleBarLabel, BorderLayout.CENTER);

            var northPanel = new JPanel(new BorderLayout());
            northPanel.add(titleBar, BorderLayout.NORTH);
            northPanel.add(toolbarPanel, BorderLayout.CENTER);
            contentPane.add(northPanel, BorderLayout.NORTH);
        } else {

            contentPane.add(toolbarPanel, BorderLayout.NORTH);
        }


        requireScoreView().init();

        contentPane.add(createCenterContent(), BorderLayout.CENTER);

    }

    private JSplitPane createCenterContent() {
        var pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Since it's a split pane, we want to resize it continuously as the pane is resized
        pane.setContinuousLayout(true);

        // There is an unknown rendering problem in Linux
        pane.setResizeWeight(SystemInfo.isLinux ? 0.85 : 1.0);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setDividerSize(0);
        pane.setTopComponent(requireScoreView().requireScrollPane());

        return pane;
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

    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
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

    public void handlePrefs() throws IllegalStateException {
        var dialog = Actions.PREFERENCES_ACTION.getDialog();

        if (dialog == null) {
            throw RuntimeError.exit("Preferences dialog could not be created");
        }

        dialog.setVisible(true);
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
            new ExtensionFileFilter(
                Strings.get(Strings.FILTER_SONGSCRIBE),
                FileExtensions.SONGWRITER
            )
        );

        if (dialog.showDialog()) {
            handleOpenFile(dialog.getFile());
        }
    }

    public void handleOpenFile(File file) {
        if (!showSaveDialog() || scoreView == null) {
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
        if (currentFile == null) {
            return saveAsNewFile();
        } else {
            return saveCurrentFile();
        }
    }

    boolean saveCurrentFile() {
        if (currentFile == null || scoreView == null) {
            return false;
        }

        try {
            var printWriter = new PrintWriter(
                currentFile,
                StandardCharsets.UTF_8
            );
            SongIO.writeSong(scoreView.getSong(), scoreView, printWriter);
            printWriter.close();
            scoreView.getSong().setModified(false);
            LOG.info("Saved: {}", currentFile.getName());
            MessageCenter.post(new DocumentWasSavedNotification());
            return true;
        } catch (IOException e1) {
            OptionDialogs.showErrorMessage(
                this,
                Strings.ALERT_TITLE_FILE_ERROR,
                Strings.ERROR_FILE_SAVE
            );
            return false;
        }
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
            Strings.get(Strings.FILTER_SONGSCRIBE),
            suggestedFileName,
            FileExtensions.SONGWRITER
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

    private static final class TitlePanel extends JPanel {
        private TitlePanel() {
            super(new BorderLayout());
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setBackground(UIManager.getColor("ToolBar.background"));
        }
    }
}
