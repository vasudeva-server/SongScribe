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

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.print.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Properties;
import javax.swing.*;
import net.engio.mbassy.listener.Handler;
import org.jetbrains.annotations.Nullable;

import songscribe.MusicChangeListener;
import songscribe.Version;
import songscribe.data.FileExtensions;
import songscribe.data.MyFileFilter;
import songscribe.io.CompositionIO;
import songscribe.ui.Constants;
import songscribe.ui.ProfileManager;
import songscribe.ui.action.Actions;
import songscribe.ui.action.SaveAction;
import songscribe.ui.component.toolbar.MainToolbarPanel;
import songscribe.ui.dialog.PlatformFileDialog;
import songscribe.ui.dialog.PropertiesStateStore;
import songscribe.ui.dialog.UpdateDialog;
import songscribe.ui.dialog.WhatsNewDialog;
import songscribe.ui.menu.MenuController;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MessageLogger;
import songscribe.ui.message.NewFileMessage;
import songscribe.ui.message.OpenFileMessage;
import songscribe.ui.message.PrintMessage;
import songscribe.ui.message.SaveAsMessage;
import songscribe.ui.message.SaveMessage;
import songscribe.ui.message.SelectableMessage;
import songscribe.ui.playback.LoopPlaybackMessage;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayWithRepeatsMessage;
import songscribe.ui.playback.PlaybackTempoChangedMessage;
import songscribe.util.FileUtils;
import songscribe.util.Log;
import songscribe.util.Utils;

public class MainFrame extends JFrame implements IMainFrame, Printable {

    public static final String COULD_NOT_SAVE_MESSAGE =
        "Could not save the file. Check if you have permission to create a file in this directory" +
        " or if the overwritten file is write-protected.";

    // This directory is used to store preferences and logs
    public static final File SONGSCRIBE_DIR = new File(
        System.getProperty("user.home"),
        ".songscribe"
    );

    public static final int MIN_WINDOW_HEIGHT = 500;

    static {
        if (!SONGSCRIBE_DIR.exists() && !SONGSCRIBE_DIR.mkdir()) {
            JOptionPane.showMessageDialog(
                null,
                """
                Oops, we cannot make a “.songscribe” directory in the current user’s home directory. Please give proper permissions.

                Until then preferences cannot be saved.""",
                Constants.PACKAGE_NAME,
                JOptionPane.ERROR_MESSAGE
            );
        }

        var logFile = new File(SONGSCRIBE_DIR, "log");

        if (logFile.length() > 1_000_000L) {
            // noinspection ResultOfMethodCallIgnored
            logFile.delete();
        }
    }

    // Used to store preferences
    public static final File PROPS_FILE = new File(SONGSCRIBE_DIR, "props");

    // Splash screen
    private static JWindow splashWindow = null;

    // Default user preferences
    private final Properties defaultProps = new Properties();

    // User preferences
    protected final Properties properties = new Properties(defaultProps);

    private final ProfileManager profileManager = new ProfileManager(this);

    // This class is shared by several applications. This is the name of the application.
    public String appName;

    // The music sheet that is displayed in the main window
    protected Score score = null;

    // The current open file
    protected File currentFile = null;

    // The name of the document type, e.g. "song", "score", etc
    @Nullable
    protected String documentTypeName;

    // The most recently used directory for opening and saving files
    protected File recentFileDirectory;

    private boolean documentModified = false;

    // A list of listeners that are notified when a property changes that changes the music
    // TODO: Replace with Message
    private final ArrayList<MusicChangeListener> musicChangeListeners =
        new ArrayList<>();

    // UI components
    private StatusBar statusBar = null;
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

        appName = "Song Writer";
        documentTypeName = "song";

        try (
            var stream = new FileInputStream(
                Utils.getResourcePath("conf/defprops")
            )
        ) {
            defaultProps.load(stream);
        } catch (IOException e) {
            showErrorMessage(
                "The application could not start, because a necessary file is not available. " +
                "Please reinstall the software."
            );
            System.exit(0);
        }

        // There are some tasks we need to perform the first time the app is run
        if (
            properties
                .getProperty(Constants.FIRST_RUN)
                .equals(Constants.TRUE_VALUE)
        ) {
            firstRun();
        }

        try (var stream = new FileInputStream(PROPS_FILE)) {
            properties.load(stream);
        } catch (IOException e) {
            // Ignore, it's okay if there are no user preferences
        }

        // Initialize SystemFileChooser state persistence
        SystemFileChooser.setStateStore(new PropertiesStateStore());

        recentFileDirectory = new File(
            properties.getProperty(Constants.PREVIOUS_DIRECTORY)
        );

        if (
            recentFileDirectory.getName().isEmpty() ||
            !recentFileDirectory.exists()
        ) {
            recentFileDirectory = FileUtils.getSongScribeDirectory();
        }

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
            var whatsNewProp =
                Constants.SHOW_WHATS_NEW + Version.PUBLIC_VERSION;

            if (
                (instance.properties.getProperty(whatsNewProp) == null) &&
                new File(WhatsNewDialog.WHATS_NEW_FILE).exists()
            ) {
                instance.properties.setProperty(
                    whatsNewProp,
                    Constants.TRUE_VALUE
                );
                new WhatsNewDialog(instance).setVisible(true);
            } // else {
            //                try {
            //                    if (
            //                        instance.properties
            //                            .getProperty(Constants.SHOW_TIP)
            //                            .equals(Constants.TRUE_VALUE)
            //                    ) {
            //                        new TipFrame(instance);
            //                    }
            //                } catch (IOException e) {
            //                    instance.properties.setProperty(
            //                        Constants.SHOW_TIP,
            //                        Constants.FALSE_VALUE
            //                    );
            //                }
            //            }

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

            recentFileDirectory = documentsDirectory;
        }

        properties.setProperty(Constants.FIRST_RUN, Constants.FALSE_VALUE);
    }

    public void initFrame() {
        setTitle(appName);
        setAppIcon();

        // A lot of init code depends on this being set
        score = new Score(this);

        initContent();
        MenuController.init();
        Actions.CONTROL_ACTION_GROUP.selectNext();

        setFrameSize();
        setLocationRelativeTo(null);
        setVisible(true);

        score.requestFocusInWindow();
        fireMusicChanged(this);
        // automaticCheckForUpdate();
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
        // | SOUTH: Status bar   |
        // +---------------------+
        var contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(new MainToolbarPanel(), BorderLayout.NORTH);

        score.init();
        contentPane.add(createCenterContent(), BorderLayout.CENTER);

        statusBar = new StatusBar();
        contentPane.add(statusBar, BorderLayout.SOUTH);
    }

    private JSplitPane createCenterContent() {
        lyricsPanel = new LyricsPanel(this);
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

        var answer = JOptionPane.showConfirmDialog(
            this,
            "The document has been modified. Do you want to save the " +
            documentTypeName +
            '?',
            appName,
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );

        if (answer == JOptionPane.YES_OPTION) {
            new SaveAction().perform(this);
        }

        return answer != JOptionPane.CANCEL_OPTION;
    }

    @Override
    public void showErrorMessage(String message) {
        Toolkit.getDefaultToolkit().beep();
        JOptionPane.showMessageDialog(
            this,
            message,
            appName,
            JOptionPane.ERROR_MESSAGE
        );
    }

    @Override
    public void showInfoMessage(String message) {
        JOptionPane.showMessageDialog(
            this,
            message,
            appName,
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    @Override
    public int showConfirmDialog(
        String message,
        int optionType,
        int messageType
    ) {
        return JOptionPane.showConfirmDialog(
            this,
            message,
            appName,
            optionType,
            messageType
        );
    }

    @Override
    public boolean isDocumentModified() {
        return documentModified;
    }

    @Override
    public void setDocumentModified(boolean documentWasModified) {
        documentModified = documentWasModified;
    }

    @Override
    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    @Override
    public StatusBar getStatusBar() {
        return statusBar;
    }

    @Override
    public LyricsPanel getLyricsModePanel() {
        return lyricsPanel;
    }

    @Override
    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public File getRecentFileDirectory() {
        return recentFileDirectory;
    }

    public void setRecentFileDirectory(File directory) {
        recentFileDirectory = directory;
    }

    // TODO: Use message center instead of this
    @Override
    public void addMusicChangeListener(MusicChangeListener listener) {
        musicChangeListeners.add(listener);
    }

    // TODO: Use message cmenter instead of this
    @Override
    public void fireMusicChanged(Object source) {
        for (var listener : musicChangeListeners) {
            //noinspection ObjectEquality
            if (listener != source) {
                listener.musicDidChange(properties);
            }
        }
    }

    public File getCurrentFile() {
        return currentFile;
    }

    @Override
    public void setCurrentFile(File saveFile) {
        currentFile = saveFile;

        if (saveFile == null) {
            setTitle(appName);
        } else {
            setTitle(appName + " - " + saveFile.getName());
        }

        documentModified = false;
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public Properties getDefaultProps() {
        return defaultProps;
    }

    protected static void automaticCheckForUpdate() {
        new UpdateDialog.UpdateInternetThread(true).start();
    }

    @Handler
    public void onNewDocument(NewFileMessage message) {
        if (!showSaveDialog()) {
            return;
        }

        setCurrentFile(null);
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

        properties.setProperty(
            Constants.PREVIOUS_DIRECTORY,
            recentFileDirectory.getAbsolutePath()
        );

        try (var out = new FileOutputStream(PROPS_FILE)) {
            properties.store(out, null);
        } catch (IOException e) {
            Log.error("Could not save properties file", e);

            showErrorMessage(
                "Sorry, we could not save the properties file. Please reinstall the software."
            );
        }

        MidiController.closeMidi();
        return true;
    }

    @Handler
    public void onOpenFile(OpenFileMessage message) {
        if (!showSaveDialog()) {
            return;
        }

        var dialog = new PlatformFileDialog(
            MainFrame.getInstance(),
            "Open",
            true,
            new MyFileFilter(
                "SongScribe song files",
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

        score.openFile(this, file, true);

        // Reset the mode to edit mode
        Actions.MODE_ACTION_GROUP.select(Actions.EDIT_MODE_ACTION, this);

        // Select quarter not duration, turn off dot and rest mode
        Actions.DURATION_ACTION_GROUP.select(Actions.QUARTER_NOTE_ACTION, this);
        Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, false);
        Actions.REST_ACTION.setSelected(false);
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
                MainFrame.getInstance()
                    .showErrorMessage("Could not print the song.");
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
            "Print functionality is not yet implemented.",
            50,
            50
        );
        g2.drawString(
            "Export functionality will be restored in a future update.",
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
            documentModified = false;
        } catch (IOException e1) {
            showErrorMessage(MainFrame.COULD_NOT_SAVE_MESSAGE);
        }
    }

    @Handler
    public void onSaveAs(@Nullable SaveAsMessage message) {
        var fileDialog = new PlatformFileDialog(
            this,
            "Save As",
            false,
            new MyFileFilter(
                "SongScribe song files",
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

            if (
                !saveFile
                    .getName()
                    .toLowerCase()
                    .endsWith(FileExtensions.SONGWRITER)
            ) {
                saveFile = new File(
                    saveFile.getAbsolutePath() + FileExtensions.SONGWRITER
                );
            }

            if (saveFile.exists()) {
                var response = JOptionPane.showConfirmDialog(
                    this,
                    "The file “" +
                    saveFile.getName() +
                    "” already exists. Do you want to overwrite it?",
                    appName,
                    JOptionPane.YES_NO_OPTION
                );

                if (response == JOptionPane.NO_OPTION) {
                    return;
                }
            }

            setCurrentFile(saveFile);
            onSave(null);
        }
    }

    @Handler
    public void loopPlaybackDidChange(LoopPlaybackMessage message) {
        handlePlaybackMessage(message, Constants.LOOP_PLAYBACK_PROP);
    }

    @Handler
    public void playWithRepeatsDidChange(PlayWithRepeatsMessage message) {
        handlePlaybackMessage(message, Constants.WITH_REPEAT_PROP);
    }

    private void handlePlaybackMessage(SelectableMessage message, String prop) {
        properties.setProperty(
            prop,
            message.isSelected() ? Constants.TRUE_VALUE : Constants.FALSE_VALUE
        );

        fireMusicChanged(this);
    }

    @Handler
    public void playbackTempoDidChange(PlaybackTempoChangedMessage message) {
        properties.setProperty(
            Constants.TEMPO_CHANGE_PROP,
            Integer.toString(message.getRatio())
        );
        fireMusicChanged(this);
    }
}
