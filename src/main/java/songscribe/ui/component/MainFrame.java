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
import java.util.Objects;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.Version;
import songscribe.error.RuntimeError;
import songscribe.file.FileExtensions;
import songscribe.file.FileUtils;
import songscribe.file.MyFileFilter;
import songscribe.io.CompositionIO;
import songscribe.message.MessageCenter;
import songscribe.message.MessageLogger;
import songscribe.message.command.CloseWindowCommand;
import songscribe.message.command.NewFileCommand;
import songscribe.message.command.OpenFileCommand;
import songscribe.message.command.PrintCommand;
import songscribe.message.command.SaveAsCommand;
import songscribe.message.command.SaveCommand;
import songscribe.message.command.ShowOpenDialogCommand;
import songscribe.message.command.ToggleLoopPlaybackCommand;
import songscribe.message.command.TogglePlayWithRepeatsCommand;
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.PlaybackTempoDidChangeNotification;
import songscribe.music.Composition;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.ui.Dialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.SaveAction;
import songscribe.ui.component.score.InsertionElementManager;
import songscribe.ui.component.toolbar.MainToolbarPanel;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.ui.dialog.PropertiesStateStore;
import songscribe.ui.dialog.WhatsNewDialog;
import songscribe.ui.menu.MenuController;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;

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
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_INITIALIZATION_ERROR),
                Strings.get(Strings.ERROR_DIRECTORY_CREATE)
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
    private static JWindow splashWindow = null;

    // This class is shared by several applications. This is the name of the application.
    public String appName;

    // The music sheet that is displayed in the main window
    @Nullable
    protected Score score = null;

    // The current open file
    @Nullable
    protected File currentFile = null;

    // UI components
    @Nullable
    private LyricsPanel lyricsPanel = null;
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

        // Trigger Prefs initialization (auto-migrates from old props file)
        Prefs.getInstance();

        // There are some tasks we need to perform the first time the app is run
        if (Prefs.getInstance().getBoolean(PrefsKey.FIRST_RUN)) {
            firstRun();
        }

        // Initialize SystemFileChooser state persistence
        SystemFileChooser.setStateStore(new PropertiesStateStore());

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        addWindowListener(
            new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (handleQuit()) {
                        System.exit(0);
                    }
                }
            }
        );

        MessageCenter.subscribe(this);
    }

    public static void main(String[] args) {
        try {
            showSplash();

            MessageLogger.init();
            MidiController.openMidi();
            var instance = getInstance();
            instance.initFrame();

            if (
                !Version.PUBLIC_VERSION.equals(
                    Prefs.getInstance().getString(PrefsKey.LAST_SEEN_WHATS_NEW_VERSION)
                ) &&
                    new File(WhatsNewDialog.WHATS_NEW_FILE).exists()
            ) {
                Prefs.getInstance().put(
                    PrefsKey.LAST_SEEN_WHATS_NEW_VERSION,
                    Version.PUBLIC_VERSION
                );
                new WhatsNewDialog().setVisible(true);
            }

            if (args.length > 0) {
                var fileToOpen = new File(args[0]);

                if (fileToOpen.exists()) {
                    instance.handleOpenFile(fileToOpen);
                }
            }
        } catch (Exception e) {
            LOG.error("Application startup failed", e);
        }

        hideSplash();
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
    }

    public static void hideSplash() {
        if (splashWindow == null) {
            return;
        }

        splashWindow.setVisible(false);
        splashWindow.dispose();
    }

    // Reserve for future use, such as showing a welcome message
    // or tutorial on the first run
    private void firstRun() {
        Prefs.getInstance().put(PrefsKey.FIRST_RUN, false);
    }

    public void initFrame() {

        setTitle(appName);
        setAppIcon();

        // A lot of init code depends on this being set
        score = new Score(this::setCurrentFile);
        PlaybackController.register(score);


        initContent();

        MenuController.init(this);

        Actions.CONTROL_ACTION_GROUP.selectNext();


        // When the application goes to the background, hide the insertion note
        // and activate the glass pane so the reactivation click is consumed.
        // Use the Desktop API on macOS, fall back to WindowListener elsewhere.
        boolean usingDesktopApi = false;

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
                        hideInsertionNote();
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
                    hideInsertionNote();
                    ActivationGate.activate();
                }
            });
        }

        setFrameSize();
        setVisible(true);
        ActivationGate.install(this);
        LOG.info("Application UI ready");

        score.requestFocusInWindow();
    }

    private void hideInsertionNote() {
        InsertionElementManager.hideInsertionElement(true);
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
        // | CENTER: Score       |
        // +---------------------+
        var contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());


        var toolbarPanel = new MainToolbarPanel();


        // On macOS with full window content, draw a custom title in the
        // title bar area and place it above the toolbar.
        if (SystemInfo.isMacOS && SystemInfo.isMacFullWindowContentSupported) {

            var titleBar = new JPanel(new BorderLayout()) {
                @Override
                public void updateUI() {
                    super.updateUI();
                    setBackground(UIManager.getColor("ToolBar.background"));
                }
            };
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


        Objects.requireNonNull(score).init();

        contentPane.add(createCenterContent(), BorderLayout.CENTER);

    }

    private JSplitPane createCenterContent() {
        lyricsPanel = new LyricsPanel();
        var pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Since it's a split pane, we want to resize it continuously as the pane is resized
        pane.setContinuousLayout(true);

        // There is an unknown rendering problem in Linux
        pane.setResizeWeight(SystemInfo.isLinux ? 0.85 : 1.0);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setDividerSize(20);
        pane.setTopComponent(Objects.requireNonNull(score).getScoreScrollPane());
        pane.setBottomComponent(lyricsPanel.getLyricsModePanel());
        return pane;
    }

    public void setFrameSize() {
        var size = getLayout().preferredLayoutSize(this);
        var scrollBarWidth = ((Integer) UIManager.get("ScrollBar.width"));
        var maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();

        int width = Math.min(size.width + scrollBarWidth, maxBounds.width);
        int height = maxBounds.height;
        int x = maxBounds.x + (maxBounds.width - width) / 2;

        setBounds(x, maxBounds.y, width, height);

        var minSize = getLayout().minimumLayoutSize(this);
        setMinimumSize(new Dimension(minSize.width, MIN_WINDOW_HEIGHT));
    }

    private boolean showSaveDialog() {
        if (score == null || !score.getComposition().isModified()) {
            return true;
        }

        var docName = getDisplayName();
        var saveIdx = 0;
        var dontSaveIdx = 1;
        var cancelIdx = 2;
        var options = new Object[]{
            Strings.get(Strings.BUTTON_SAVE),
            Strings.get(Strings.BUTTON_DONT_SAVE),
            Strings.get(Strings.BUTTON_CANCEL)
        };
        var answer = Dialogs.showOptionDialog(
            this,
            Strings.get(Strings.DIALOG_TITLE_SAVE_CHANGES),
            Strings.get(Strings.CONFIRM_SAVE_MODIFIED, docName),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[saveIdx]
        );

        if (answer == saveIdx) {
            SaveAction.createAction().perform(this);
        }

        return answer == saveIdx || answer == dontSaveIdx;
    }

    private void updateTitle() {
        if (score == null || !score.isInitialized()) {
            return;
        }

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::updateTitle);
            return;
        }

        var name = getDisplayName();
        var isModified = score.getComposition().isModified();
        var title = isModified ? '•' + name : name;
        setTitle(title);

        if (titleBarLabel != null) {
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
    public void compositionDidChange(CompositionDidChangeNotification message) {
        updateTitle();
    }

    @Handler
    public void documentWasSaved(DocumentWasSavedNotification message) {
        updateTitle();
    }

    @Nullable
    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }


    @Nullable
    public File getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(@Nullable File saveFile) {
        currentFile = saveFile;
        updateTitle();
    }

    private String getDisplayName() {
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

        if (score != null) {
            score.setComposition(new Composition());
            score.requestFocusInWindow();
        }

        LOG.info("New composition");
    }

    public static void handlePrefs() throws IllegalStateException {
        var dialog = Actions.PREFERENCES_ACTION.getDialog();

        if (dialog == null) {
            RuntimeError.exit("Preferences dialog could not be created");
            throw new AssertionError("unreachable");
        }

        dialog.setVisible(true);
    }

    public boolean handleQuit() {
        var quit = showSaveDialog();

        if (!quit) {
            return false;
        }

        if (score != null) {
            score.saveProperties();
        }

        MidiController.closeMidi();
        LOG.info("Application shutting down");
        return true;
    }

    @Handler
    public void handleCloseWindow(CloseWindowCommand message) {
        if (handleQuit()) {
            System.exit(0);
        }
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
            new MyFileFilter(
                Strings.get(Strings.FILTER_SONGSCRIBE),
                FileExtensions.SONGWRITER.substring(1)
            )
        );

        if (dialog.showDialog()) {
            handleOpenFile(dialog.getFile());
        }
    }

    public void handleOpenFile(File file) {
        if (!showSaveDialog() || score == null) {
            return;
        }

        var opened = score.openFile(file, true);

        if (opened) {
            RecentDocumentsManager.getInstance().add(file.toPath().toAbsolutePath());
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
                Dialogs.showErrorMessage(
                    this,
                    Strings.get(Strings.DIALOG_TITLE_PRINT_ERROR),
                    Strings.get(Strings.ERROR_PRINT)
                );
            }
        }
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
        if (pageIndex >= 1) {
            return NO_SUCH_PAGE;
        }

        var format = Objects.requireNonNull(printerJob).validatePage(pageFormat);
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
        if (currentFile == null) {
            saveAsNewFile();
        } else {
            saveCurrentFile();
        }
    }

    @Handler
    public void handleSaveAs(SaveAsCommand message) {
        saveAsNewFile();
    }

    private void saveCurrentFile() {
        if (currentFile == null || score == null) {
            return;
        }

        try {
            var printWriter = new PrintWriter(
                currentFile,
                StandardCharsets.UTF_8
            );
            CompositionIO.writeComposition(score.getComposition(), printWriter);
            printWriter.close();
            score.getComposition().setModified(false);
            LOG.info("Saved: {}", currentFile.getName());
            MessageCenter.post(new DocumentWasSavedNotification());
        } catch (IOException e1) {
            Dialogs.showErrorMessage(
                this,
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                Strings.get(Strings.ERROR_FILE_SAVE)
            );
        }
    }

    private void saveAsNewFile() {
        if (score == null) {
            return;
        }

        var fileDialog = new PlatformFileDialog(
            this,
            Strings.get(Strings.DIALOG_SAVE_AS_TITLE),
            false,
            new MyFileFilter(
                Strings.get(Strings.FILTER_SONGSCRIBE),
                FileExtensions.SONGWRITER.substring(1)
            )
        );

        if (currentFile == null) {
            fileDialog.setFile(FileUtils.getSongFileNameForFileChooser(score));
        } else {
            fileDialog.setFile("");
        }

        if (fileDialog.showDialog()) {
            var saveFile = fileDialog.getFile();

            saveFile = FileUtils.ensureExtension(saveFile, FileExtensions.SONGWRITER);

            setCurrentFile(saveFile);
            saveCurrentFile();

            if (!score.getComposition().isModified()) {
                RecentDocumentsManager.getInstance().add(saveFile.toPath().toAbsolutePath());
            }
        }
    }

    @Handler
    public void handleToggleLoopPlayback(ToggleLoopPlaybackCommand message) {
        Prefs.getInstance().put(PrefsKey.LOOP_PLAYBACK, message.isSelected());
    }

    @Handler
    public void handleTogglePlayWithRepeats(TogglePlayWithRepeatsCommand message) {
        Prefs.getInstance().put(PrefsKey.PLAY_WITH_REPEATS, message.isSelected());
    }

    @Handler
    public void playbackTempoDidChange(PlaybackTempoDidChangeNotification message) {
        Prefs.getInstance().put(PrefsKey.TEMPO_CHANGE_PERCENT, message.getRatio());
    }
}
