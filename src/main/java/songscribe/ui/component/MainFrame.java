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

import java.awt.*;
import java.awt.desktop.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.print.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.listener.Handler;

import songscribe.MusicChangeListener;
import songscribe.Strings;
import songscribe.Version;
import songscribe.data.FileExtensions;
import songscribe.data.MyFileFilter;
import songscribe.io.CompositionIO;
import songscribe.music.Composition;
import songscribe.prefs.Prefs;
import songscribe.prefs.RecentDocumentsManager;
import songscribe.ui.Dialogs;
import songscribe.ui.ProfileManager;
import songscribe.ui.action.Actions;
import songscribe.ui.action.SaveAction;
import songscribe.ui.component.score.InsertionElementManager;
import songscribe.ui.component.toolbar.MainToolbarPanel;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.ui.dialog.PropertiesStateStore;
import songscribe.ui.dialog.WhatsNewDialog;
import songscribe.ui.menu.MenuController;
import songscribe.ui.message.CloseWindowMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MessageLogger;
import songscribe.ui.message.NewFileMessage;
import songscribe.ui.message.OpenFileMessage;
import songscribe.ui.message.PrintMessage;
import songscribe.ui.message.SaveAsMessage;
import songscribe.ui.message.SaveMessage;
import songscribe.ui.playback.LoopPlaybackMessage;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayWithRepeatsMessage;
import songscribe.ui.playback.PlaybackTempoChangedMessage;
import songscribe.util.FileUtils;
import songscribe.util.Log;

public class MainFrame extends JFrame implements IMainFrame, Printable {

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
    private static JWindow splashWindow = null;

    private final ProfileManager profileManager = new ProfileManager(this);

    // This class is shared by several applications. This is the name of the application.
    public String appName;

    // The music sheet that is displayed in the main window
    protected Score score = null;

    // The current open file
    protected File currentFile = null;

    private boolean documentModified = false;

    // A list of listeners that are notified when a property changes that changes the music
    // TODO: Replace with Message
    private final ArrayList<MusicChangeListener> musicChangeListeners =
        new ArrayList<>();

    // UI components
    private LyricsPanel lyricsPanel = null;

    private static final double PRINT_EXTRA_MARGIN = 0.25 * 72;
    private PrinterJob printerJob = null;

    public MainFrame() {
        // We would like the cool transparent title bar on macOS
        if (SystemInfo.isMacOS && SystemInfo.isMacFullWindowContentSupported) {
            rootPane.putClientProperty(
                "apple.awt.transparentTitleBar",
                Boolean.TRUE
            );
        }

        appName = Strings.get(Strings.APP_SONGWRITER);

        // Trigger Prefs initialization (auto-migrates from old props file)
        Prefs.getInstance();

        // There are some tasks we need to perform the first time the app is run
        if (Prefs.getInstance().getBoolean("firstRun")) {
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

            if (System.getenv("DEBUG") != null) {
                MessageLogger.init();
            }

            MidiController.openMidi();
            var instance = getInstance();
            instance.initFrame();
            if (
                !Version.PUBLIC_VERSION.equals(
                    Prefs.getInstance().getString("lastSeenWhatsNewVersion")
                ) &&
                    new File(WhatsNewDialog.WHATS_NEW_FILE).exists()
            ) {
                Prefs.getInstance().put(
                    "lastSeenWhatsNewVersion",
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
            Log.error(e);
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
        splashWindow.setVisible(false);
        splashWindow.dispose();
    }

    private void firstRun() {
        // Copy the example files
        var documentsDirectory = FileUtils.getSongScribeDirectory();
        var created = documentsDirectory.mkdir();

        if (created) {
            var examples = new File("examples").listFiles();

            if (examples != null) {
                for (var file : examples) {
                    FileUtils.copyFile(
                        file,
                        new File(documentsDirectory, file.getName())
                    );
                }
            }
        }

        Prefs.getInstance().put("firstRun", false);
    }

    public void initFrame() {
        setTitle(appName);
        setAppIcon();

        // A lot of init code depends on this being set
        score = new Score(this);

        initContent();
        MenuController.init();
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
        setLocationRelativeTo(null);
        setVisible(true);
        ActivationGate.install(this);

        score.requestFocusInWindow();
        fireMusicChanged(this);
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
        contentPane.add(new MainToolbarPanel(), BorderLayout.NORTH);

        score.init();
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
        pane.setTopComponent(score.getScoreScrollPane());
        pane.setBottomComponent(lyricsPanel.getLyricsModePanel());
        return pane;
    }

    @Override
    public void setFrameSize() {
        var size = getLayout().preferredLayoutSize(this);
        var scrollBarWidth = ((Integer) UIManager.get("ScrollBar.width"));
        var maxBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getMaximumWindowBounds();
        setSize(
            Math.min(size.width + scrollBarWidth, maxBounds.width),
            Math.min(size.height, maxBounds.height)
        );

        var minSize = getLayout().minimumLayoutSize(this);
        setMinimumSize(new Dimension(minSize.width, MIN_WINDOW_HEIGHT));
    }

    public boolean showSaveDialog() {
        if (!documentModified) {
            return true;
        }

        var docName = (currentFile != null) ? currentFile.getName() : Strings.get(Strings.DOCUMENT_UNTITLED);
        var answer = Dialogs.showConfirmDialog(
            this,
            Strings.get(Strings.DIALOG_TITLE_SAVE_CHANGES),
            Strings.get(Strings.CONFIRM_SAVE_MODIFIED, docName),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (answer == JOptionPane.YES_OPTION) {
            new SaveAction().perform(this);
        }

        return answer != JOptionPane.CANCEL_OPTION;
    }

    @Override
    public boolean isDocumentModified() {
        return documentModified;
    }

    private void updateTitle() {
        var name = (currentFile != null) ? currentFile.getName() : Strings.get(Strings.DOCUMENT_UNTITLED);
        setTitle(documentModified ? '•' + name : name);
    }

    @Override
    public void setDocumentModified(boolean documentWasModified) {
        documentModified = documentWasModified;
        updateTitle();
    }

    @Override
    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    @Override
    public LyricsPanel getLyricsModePanel() {
        return lyricsPanel;
    }

    @Override
    public ProfileManager getProfileManager() {
        return profileManager;
    }

    // TODO: Use message center instead of this
    @Override
    public void addMusicChangeListener(MusicChangeListener listener) {
        musicChangeListeners.add(listener);
    }

    // TODO: Use message center instead of this
    @Override
    public void fireMusicChanged(Object source) {
        for (var listener : musicChangeListeners) {
            //noinspection ObjectEquality
            if (listener != source) {
                listener.musicDidChange();
            }
        }
    }

    public File getCurrentFile() {
        return currentFile;
    }

    @Override
    public void setCurrentFile(File saveFile) {
        currentFile = saveFile;
        setDocumentModified(false);
    }

    @Handler
    public void onNewDocument(NewFileMessage message) {
        if (!showSaveDialog()) {
            return;
        }

        setCurrentFile(null);

        if (score != null) {
            score.setComposition(new Composition(this));
            score.requestFocusInWindow();
        }
    }

    public static void handlePrefs() throws IllegalStateException {
        Actions.PREFERENCES_ACTION.getDialog().setVisible(true);
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
        return true;
    }

    @Handler
    public void onCloseWindow(CloseWindowMessage message) {
        if (handleQuit()) {
            System.exit(0);
        }
    }

    @Handler
    public void onOpenFile(OpenFileMessage message) {
        var dialog = new PlatformFileDialog(
            MainFrame.getInstance(),
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
        if (!showSaveDialog()) {
            return;
        }

        var opened = score.openFile(this, file, true);

        // Reset the mode to edit mode
        Actions.MODE_ACTION_GROUP.select(Actions.EDIT_MODE_ACTION, this);

        // Select quarter not duration, turn off dot and rest mode
        Actions.DURATION_ACTION_GROUP.select(Actions.QUARTER_NOTE_ACTION, this);
        Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, false);
        Actions.REST_ACTION.setSelected(false);

        if (opened) {
            RecentDocumentsManager.getInstance().add(file.toPath().toAbsolutePath());
        }
    }

    @Handler
    public void onPrint(PrintMessage message) {
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
                    MainFrame.getInstance(),
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
    public void onSave(@Nullable SaveMessage message) {
        // If there is no current file (meaning this is a new document that has not yet
        // been saved), then do a Save As instead.
        if (currentFile == null) {
            onSaveAs(null);
            return;
        }

        try {
            var printWriter = new PrintWriter(
                currentFile,
                StandardCharsets.UTF_8
            );
            CompositionIO.writeComposition(score.getComposition(), printWriter);
            printWriter.close();
            setDocumentModified(false);
        } catch (IOException e1) {
            Dialogs.showErrorMessage(
                this,
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                Strings.get(Strings.ERROR_FILE_SAVE)
            );
        }
    }

    @Handler
    public void onSaveAs(@Nullable SaveAsMessage message) {
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

            if (!Dialogs.confirmFileOverwrite(this, appName, saveFile)) {
                return;
            }

            setCurrentFile(saveFile);
            onSave(null);

            if (!isDocumentModified()) {
                RecentDocumentsManager.getInstance().add(saveFile.toPath().toAbsolutePath());
            }
        }
    }

    @Handler
    public void loopPlaybackDidChange(LoopPlaybackMessage message) {
        Prefs.getInstance().put("loopPlayback", message.isSelected());
        fireMusicChanged(this);
    }

    @Handler
    public void playWithRepeatsDidChange(PlayWithRepeatsMessage message) {
        Prefs.getInstance().put("playWithRepeats", message.isSelected());
        fireMusicChanged(this);
    }

    @Handler
    public void playbackTempoDidChange(PlaybackTempoChangedMessage message) {
        Prefs.getInstance().put("tempoChangePercent", message.getRatio());
        fireMusicChanged(this);
    }
}
