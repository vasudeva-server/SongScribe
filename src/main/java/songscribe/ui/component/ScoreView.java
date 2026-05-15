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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import songscribe.error.RuntimeError;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.export.ExportOptions;
import songscribe.export.ImageExporter;
import songscribe.export.SVGExporter;
import songscribe.io.SongIO;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.music.Song;
import songscribe.music.EndingValidationResult;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.MusicEditOperations;
import songscribe.message.mutation.FontChange;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.Constants;
import songscribe.ui.Control;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.adjustment.HorizontalAdjustment;
import songscribe.ui.adjustment.VerticalAdjustment;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.ScorePanel;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.layout.HorizontalSpacingCalculator;
import songscribe.ui.layout.SongLayoutMetrics;
import songscribe.ui.layout.LyricRenderMetrics;
import songscribe.ui.layout.PageModel;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.layout.StaffExtents;
import songscribe.ui.renderer.GraphicsState;
import songscribe.util.GraphicUtils;
import songscribe.util.StringUtils;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.RenderContext;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * This class is responsible for managing and drawing the music score
 * and its lyrics. It also handles user input for editing the score.
 *
 * <pre>
 * Page layout hierarchy:
 *
 *   JScrollPane
 *   └── ScorePanel [GridBagLayout, gray background]
 *       └── ScoreView [BorderLayout, white background, full page size]
 *           │  EmptyBorder: top/bottom = 0.5", left/right = horizontal margin
 *           └── MainPanel [BoxLayout Y_AXIS, CENTER]
 *               ├── TitleComponent
 *               ├── StaffPanel
 *               ├── TextPanel
 *               └── FootnotesComponent
 * </pre>
 */

public final class ScoreView
    extends JComponent
    implements
    ComponentHierarchyProvider,
    InputHandlerCallback,
    LineComponent.SelectionProvider,
    RenderContext,
    ScoreActions {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreView.class);

    // The vertical distance between whole tones on the staff (e.g. A to B)
    public static final float STAFF_POSITION_OFFSET_PX = (float) ScaleContext.getInstance().ssToPx(StaffExtents.STAFF_POSITION_OFFSET_SS);

    // Runs before all HIGH_PRIORITY subscribers so the tuplet info cache is warm
    // by the time TupletAction handlers (HIGH_PRIORITY) read it.
    private static final int TUPLET_INFO_CACHE_PRIORITY = Message.HIGH_PRIORITY + 100;

    private static final String DISABLED_KEY_BINDING = "none";

    // Colors used to draw the music score in various states — read from UIManager for theming.
    // Callers should not cache these values; read at render time.
    public static Color getPlayingNoteColor() {
        return FlatLafProps.get(FlatLafKeys.SCORE_PLAYING_NOTE_COLOR);
    }

    public static Color getPreviewElementColor() {
        return FlatLafProps.get(FlatLafKeys.SCORE_PREVIEW_ELEMENT_COLOR);
    }

    public static Color getSelectionColor() {
        return FlatLafProps.get(FlatLafKeys.SCORE_SELECTION_COLOR);
    }

    // Cached per-notification-dispatch result of canToggleTuplet(), populated by
    // a TUPLET_INFO_CACHE_PRIORITY handler before any TupletAction handler reads it.
    @Nullable
    private TupletToggleInfo cachedTupletToggleInfo = null;

    // Edit popup
    @Nullable
    private JPopupMenu popup = null;

    @Nullable
    private SAXParser saxParser;
    private final Dimension sheetSize = new Dimension();

    @Nullable
    private HorizontalAdjustment horizontalAdjustment = null;
    @Nullable
    private VerticalAdjustment verticalAdjustment = null;

    // Called when a file is successfully opened (e.g. to update the window title)
    private final @Nullable Consumer<? super File> onFileOpened;

    // The current editing mode
    private Mode mode = Mode.EDIT;

    // Whether editing is done via mouse or keyboard
    private Control control;

    // In some contexts (such as playback), we don't want to allow dragging
    private boolean dragDisabled = false;

    // The model for the score
    @Nullable
    private Song song = null;

    // The scroll pane that contains the score + margin
    @Nullable
    private JScrollPane scrollPane = null;

    // The score itself
    @Nullable
    private JPanel scorePanel = null;

    // TODO: Not sure why this is here. It really should be in the renderer, and should be based
    //  on the advance of the clef.
    private int leadingKeysPosPx = 32;

    // The vertical distance between the top of one staff line and the next.
    // This can vary depending on what appears above and below the staff line,
    // as well as vertical adjustments made by the user.
    private int rowHeightPx = 0;

    // The y position (from the top of the scorePanel) of the middle line (B) of the first staff
    private int middleLineYPx = 0;

    // Coordinates selection state across lines
    private final SelectionCoordinator selectionCoordinator;

    // Handles music editing operations
    @Nullable
    private MusicEditOperations operations = null;

    // Manages clipboard state for copy/paste operations
    private final ClipboardManager clipboardManager;

    // Manages edit mode state (insertion note and position)
    private final EditModeManager editModeManager;

    // New JComponent-based score panel (Phase 2 hierarchy)
    @Nullable
    private MainPanel mainPanel = null;

    // Coordinates message handling
    @Nullable
    private ScoreViewController messageCoordinator = null;

    // Preferred size of the score panel
    private final Dimension preferredSizePx = new Dimension();

    // Navigates the component hierarchy (null in headless mode)
    private final @Nullable ComponentHierarchyNavigator hierarchyNavigator;

    // Handles mouse and keyboard input (null in headless mode)
    private final @Nullable ScoreInputHandler inputHandler;

    // True after init() has been called (interactive mode only)
    private boolean initialized = false;

    // Song-wide layout metrics shared across all line components.
    // Set by StaffPanel.updateSongMetrics before any layout/paint runs.
    private @Nullable SongLayoutMetrics songLayoutMetrics;

    // Song-wide lyric render metrics shared across all line components.
    // Set by StaffPanel.updateSongMetrics before any layout/paint runs.
    private @Nullable LyricRenderMetrics lyricRenderMetrics;

    // The currently-open lyric editor overlay, if any. Set by LyricEditor.openOn /
    // dismiss so getActiveLyricEditor() doesn't have to scan getComponents() per paint.
    @Nullable private LyricEditor activeLyricEditor;

    // Maps each registered KeyStroke to its action key so bindings can be toggled.
    private final Map<KeyStroke, Object> scoreKeyBindings = new LinkedHashMap<>();

    /**
     * Creates a ScoreView with core infrastructure (SAX parser, selection, clipboard,
     * edit mode). This is sufficient for headless use (converters pass {@code null}).
     * <p>
     * For interactive use, call {@link #init()} after construction to create the
     * UI components (view, panels, message coordinator) and the initial Song.
     *
     * @param onFileOpened callback invoked when a file is successfully opened,
     *                     or {@code null} for headless (converter) use
     */
    public ScoreView(@Nullable Consumer<? super File> onFileOpened) {
        this.onFileOpened = onFileOpened;
        var headless = onFileOpened == null;
        control = Control.valueOf(Prefs.getInstance().getString(PrefsKey.CONTROL));

        selectionCoordinator = new SelectionCoordinator(this::getSong);
        clipboardManager = new ClipboardManager();
        editModeManager = new EditModeManager(
            clipboardManager,
            selectionCoordinator,
            this
        );

        try {
            saxParser = SAXParserFactory.newInstance().newSAXParser();
        } catch (Exception e) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INITIALIZATION_ERROR,
                Strings.ERROR_INITIALIZATION,
                Constants.PACKAGE_NAME
            );
            System.exit(0);
        }

        if (headless) {
            hierarchyNavigator = null;
            inputHandler = null;
        } else {
            hierarchyNavigator = new ComponentHierarchyNavigator(this);
            var handler = new ScoreInputHandler(this, editModeManager);
            inputHandler = handler;
            setLayout(new BorderLayout());
            setFocusable(true);
            addKeyListener(handler);
        }
    }

    public void createSVG(File outputFile) {
        SVGExporter.createSVG(outputFile);
    }

    /**
     * Initializes the interactive UI: view, panels, message coordinator,
     * and the initial Song. Must be called exactly once after construction
     * for interactive (non-converter) use. Not needed for headless converters.
     */
    public void init() {
        setName(ComponentNames.SCORE);

        // Create initial song
        song = new Song();

        // Initialize UI components
        initView();
        initAdjustments();
        initScorePanel();
        initMainPanel();

        updatePageLayout(ScaleContext.getInstance().ssToRoundedPx(song.getLineWidthSs()));
        if (inputHandler != null) {
            addMouseMotionListener(inputHandler);
            addMouseListener(inputHandler);
        }

        initEditPopup();
        selectionChanged();
        initKeys();

        // Initialize insertion note with default type
        setPreviewElement(editModeManager.makePreviewElement());

        MessageCenter.subscribe(this);
        syncPlaybackPrefs();
        song.setModified(false);

        // Create operations and message coordinator (requires mainPanel to be set)
        operations = new MusicEditOperations(song, selectionCoordinator);
        messageCoordinator = new ScoreViewController(
            this,
            operations,
            editModeManager,
            selectionCoordinator,
            clipboardManager
        );

        initialized = true;
    }

    private void initKeys() {
        if (inputHandler != null) {
            scoreKeyBindings.putAll(inputHandler.installKeyBindings(this));
        }
    }

    private void initEditPopup() {
        popup = new JPopupMenu();
        popup.add(Actions.CUT_ACTION);
        popup.add(Actions.COPY_ACTION);
        popup.add(Actions.PASTE_ACTION);
        popup.addSeparator();
        popup.add(Actions.DELETE_ACTION);
    }

    private void initScorePanel() {
        scorePanel = new ScorePanel(this);
        scrollPane = new JScrollPane(scorePanel);
        scrollPane.setBorder(new ThemeAwareMatteBorder(1, 0, 1, 0, "ToolBar.separatorColor"));
        updateScoreSurroundBackground();
    }

    @Override
    public void updateUI() {
        super.updateUI();

        if (scrollPane != null) {
            updateScoreSurroundBackground();
        }
    }

    private void updateScoreSurroundBackground() {
        if (scrollPane == null) {
            return;
        }

        var color = FlatLafProps.<Color>get(FlatLafKeys.SCORE_PANEL_BACKGROUND);
        scrollPane.setBackground(color);
        scrollPane.getViewport().setBackground(color);
    }

    private void initMainPanel() {
        mainPanel = new MainPanel();
        mainPanel.setSong(getSong());
        mainPanel.setVisible(true);
        add(mainPanel, BorderLayout.CENTER);
        setupLineComponentState();
    }

    public static boolean defaultUpperNote(StaffElement note) {
        return (note.getStaffPosition() > 0) || note.getType().isGraceNote();
    }

    /**
     * Sets up selection provider and initial state for all LineComponents.
     * <p>
     * This wires up the selection checking from ScoreView to enable
     * note coloring in the component-based rendering.
     */
    void setupLineComponentState() {
        if (hierarchyNavigator != null) {
            hierarchyNavigator.setupLineComponentState(this, this);
        }
    }

    /**
     * Returns the MainPanel for the new component hierarchy.
     * <p>
     * This is the top-level panel for the Phase 2 JComponent-based rendering.
     */
    @Override
    public @Nullable MainPanel getMainPanel() {
        return mainPanel;
    }

    void setMainPanel(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
    }

    void setScorePanel(JPanel scorePanel) {
        this.scorePanel = scorePanel;
    }

    void setScrollPane(JScrollPane scrollPane) {
        this.scrollPane = scrollPane;
    }

    void setPopup(JPopupMenu popup) {
        this.popup = popup;
    }

    EditModeManager getEditModeManager() {
        return editModeManager;
    }

    public boolean isDragDisabled() {
        return dragDisabled;
    }

    @Override
    public @Nullable JPopupMenu getEditPopup() {
        return popup;
    }

    /**
     * Returns the LineComponent for the given line index, or null if not found.
     */
    @Nullable
    public LineComponent getLineComponent(int lineIndex) {
        return hierarchyNavigator != null ? hierarchyNavigator.getLineComponent(lineIndex) : null;
    }

    void initAdjustments() {
        horizontalAdjustment = new HorizontalAdjustment(this);
        verticalAdjustment = new VerticalAdjustment(this);
    }

    void initView() {
        viewChanged();
    }

    public boolean openFile(File file, boolean updateCurrentFile) {
        var previousModified = song != null && song.isModified();

        if (song != null) {
            song.setModified(false);
        }

        try {
            var reader = new SongIO.DocumentReader();
            if (saxParser == null) {
                throw RuntimeError.exit("saxParser not initialized");
            }

            saxParser.parse(file, reader);
            var newSong = reader.getSong();
            var lineWidthInches =
                ScaleContext.getInstance().ssToPx(newSong.getLineWidthSs()) /
                    GraphicUtils.getDpi();

            if (lineWidthInches > PageModel.MAX_LINE_WIDTH_INCHES) {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.get(Strings.ALERT_TITLE_FILE_ERROR),
                    Strings.get(Strings.ERROR_LINE_WIDTH_TOO_LARGE)
                );
                LOG.error(
                    "Refused to open {}: line width {} inches exceeds maximum {}",
                    file.getName(),
                    lineWidthInches,
                    PageModel.MAX_LINE_WIDTH_INCHES
                );

                if (song != null) {
                    song.setModified(previousModified);
                }

                return false;
            }

            setSong(newSong);

            if (updateCurrentFile && onFileOpened != null) {
                onFileOpened.accept(file);
            }

            LOG.info("Song loaded: {}", file.getName());
            return true;
        } catch (SongIO.NewerVersionException e) {
            OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_NEWER_VERSION);
            LOG.error("Could not open '{}': document version is newer than the application supports", file.getName(), e);

            if (song != null) {
                song.setModified(previousModified);
            }
            return false;
        } catch (SAXException e) {
            OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_DAMAGED, file.getName());
            LOG.error("Could not open damaged file '{}'", file.getName(), e);
            if (song != null) {
                song.setModified(previousModified);
            }
            return false;
        } catch (IOException e) {
            OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_NO_PERMISSION, file.getName());
            LOG.error("Could not open file '{}': permission error", file.getName(), e);
            if (song != null) {
                song.setModified(previousModified);
            }
            return false;
        }
    }

    public void syncPlaybackPrefs() {
        var prefs = Prefs.getInstance();
        editModeManager.setPlayInsertedNote(prefs.getBoolean(PrefsKey.PLAY_INSERTED_NOTE));
        // If true, the score is played with repeats
        var playWithRepeats = prefs.getBoolean(PrefsKey.PLAY_WITH_REPEATS);

        // Delegate playback settings to PlaybackController
        PlaybackController.setInstrument(prefs.getInt(PrefsKey.INSTRUMENT));
        PlaybackController.setTempoChangePercent(prefs.getInt(PrefsKey.TEMPO_CHANGE_PERCENT));
        PlaybackController.setNoteDurationPercent(prefs.getInt(PrefsKey.PLAYBACK_NOTE_DURATION));
        PlaybackController.setPlayWithRepeats(playWithRepeats);
        PlaybackController.applyVolumeFromPrefs();
        PlaybackController.applyPrefsDuringPlayback();
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void musicSelectionDidChangeCacheTupletInfo(MusicSelectionDidChangeNotification message) {
        cachedTupletToggleInfo = operations != null ? operations.canToggleTuplet() : null;
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void songDidChangeCacheTupletInfo(SongDidChangeNotification message) {
        cachedTupletToggleInfo = operations != null ? operations.canToggleTuplet() : null;
    }

    @Handler
    public void songDidChangeInvalidateLineLayouts(SongDidChangeNotification message) {
        if (!message.hasMutationOf(FontChange.class) || song == null) {
            return;
        }

        // Rebuild metrics before invalidating so that LineComponent.getPreferredSize()
        // calls performLayout() with up-to-date measurements during the layout pass.
        rebuildLyricRenderMetrics();

        for (var i = 0; i < song.lineCount(); i++) {
            var lineComponent = getLineComponent(i);

            if (lineComponent != null) {
                lineComponent.invalidateLayout();
            }
        }
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void documentDidLoadCacheTupletInfo(DocumentDidLoadNotification message) {
        cachedTupletToggleInfo = operations != null ? operations.canToggleTuplet() : null;
    }

    @Handler
    public void prefsDidChange(PrefsDidChangeNotification message) {
        switch (message.getKey()) {
            case LOOP_PLAYBACK, PLAY_WITH_REPEATS -> syncPlaybackPrefs();
            case PAGE_SIZE -> {
                if (song != null) {
                    updatePageLayout(ScaleContext.getInstance().ssToRoundedPx(song.getLineWidthSs()));
                }
            }
            default -> { }
        }
    }

    @Handler
    public void textEditingDidChange(TextEditingDidChangeNotification message) {
        var inputMap = getInputMap(JComponent.WHEN_FOCUSED);

        if (message.isEditing()) {
            scoreKeyBindings.keySet().forEach(keyStroke -> inputMap.put(keyStroke, DISABLED_KEY_BINDING));
        } else {
            scoreKeyBindings.forEach(inputMap::put);
        }
    }

    public void viewChanged() {
        // Syncs derived coordinates from current child positions without re-running the
        // layout manager. Callers either own child positions manually (drag) or expect
        // Swing to drive doLayout() separately via revalidate().
        updateLayoutFromComponents();
    }

    /**
     * Updates middleLineY and rowHeight from component hierarchy.
     * <p>
     * Derives layout coordinates from the actual positioned components
     * rather than a separate layout manager.
     */
    private void updateLayoutFromComponents() {
        if (hierarchyNavigator != null) {
            hierarchyNavigator.updateLayoutFromComponents(layout -> {
                middleLineYPx = layout[0];
                rowHeightPx = layout[1];
            });
        }
    }

    @Override
    public void doLayout() {
        super.doLayout();
        // Refresh derived layout coordinates after children have been positioned, so
        // paintComponent can read them without recomputing on every paint pass.
        updateLayoutFromComponents();
    }

    public @Nullable JScrollPane getScoreScrollPane() {
        return scrollPane;
    }

    @Override
    public boolean isElementSelected(int elementIndex, int lineIndex) {
        return selectionCoordinator.isElementSelected(elementIndex, lineIndex);
    }

    @Override
    public boolean isLineSelected(int lineIndex) {
        return selectionCoordinator.isLineSelected(lineIndex);
    }

    @Override
    public boolean isGlissandoSelected(int elementIndex, int lineIndex) {
        return selectionCoordinator.isGlissandoSelected(elementIndex, lineIndex);
    }

    @Override
    public boolean isLyricSelected(StaffElement element, int verse, int lineIndex) {
        return selectionCoordinator.isLyricSelected(element, verse, lineIndex);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        var g2 = (Graphics2D) g;

        try (var ignored = GraphicsState.save(
            g2,
            GraphicsState.Property.COLOR
        )) {
            g2.setColor(FlatLafProps.get(FlatLafKeys.SCORE_PAGE_SCREEN_BACKGROUND));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        drawEditElements(g2);
    }

    private void drawEditElements(Graphics2D g2) {
        //noinspection StatementWithEmptyBody
        if (mode == Mode.EDIT) {
            // Insertion note rendering is now handled by LineComponent
        } else if (mode == Mode.ADJUSTMENT && horizontalAdjustment != null) {
            horizontalAdjustment.repaint(g2);
        } else if (mode == Mode.VERTICAL_ADJUSTMENT && verticalAdjustment != null) {
            verticalAdjustment.repaint(g2);
        }
    }

    @Override
    public int getNoteYPosPx(int staffPosition, int lineIndex) {
        return (int) (middleLineYPx +
            (staffPosition * STAFF_POSITION_OFFSET_PX) +
            (lineIndex * rowHeightPx));
    }

    @Override
    public int getUnderLyricsYPosPx() {
        // TODO: Calculate from component hierarchy
        return 0;
    }

    @Nullable
    public StaffElement getPreviewElement() {
        return editModeManager.getPreviewElement();
    }

    @Override
    public void setPreviewElement(@Nullable StaffElement element) {
        if (element != null) {
            var currentPreviewElement = editModeManager.getPreviewElement();

            if (currentPreviewElement != null) {
                element.setStaffPosition(currentPreviewElement.getStaffPosition());
                element.setXOffsetPx(currentPreviewElement.getXOffsetPx());
            } else {
                editModeManager.setPreviewElement(element);
            }

            element.setUpper(defaultUpperNote(element));
        }

        editModeManager.setPreviewElement(element);
        repaint();
    }

    @Override
    public void clearSelection() {
        selectionCoordinator.clearSelection();
        selectionChanged();
    }

    public void deselect() {
        clearSelection();
        repaint();
    }

    public void selectionChanged() {
        MessageCenter.post(new MusicSelectionDidChangeNotification(this));
    }

    public int getSelectionSize() {
        return selectionCoordinator.getSelectionSize();
    }

    private MusicEditOperations requireOperations() {
        if (operations == null) {
            throw RuntimeError.exit("operations not initialized");
        }

        return operations;
    }

    public boolean canToggleBeaming() {
        return requireOperations().canToggleBeaming();
    }

    public boolean canToggleTie() {
        return requireOperations().canToggleTie();
    }

    public TupletToggleInfo canToggleTuplet() {
        //noinspection ReplaceNullCheck
        if (cachedTupletToggleInfo != null) {
            return cachedTupletToggleInfo;
        }

        return requireOperations().canToggleTuplet();
    }

    public boolean canRemoveDynamicsFromSelection() {
        return requireOperations().canRemoveDynamicsFromSelection();
    }

    public EndingValidationResult canMakeFirstSecondEnding() {
        return requireOperations().canMakeFirstSecondEnding();
    }

    public boolean canChangeTempo() {
        return requireOperations().canChangeTempo();
    }

    public boolean canToggleTrill() {
        return requireOperations().canToggleTrill();
    }

    public boolean canFlipStemDirection() {
        return requireOperations().canFlipStemDirection();
    }

    public boolean isInitialized() {
        return song != null;
    }

    @Override
    public Song getSong() {
        if (song == null) {
            throw RuntimeError.exit("song not initialized");
        }

        return song;
    }

    public String getSuggestedFileName() {
        var theSong = getSong();
        var title = theSong.getTitle();
        var numberStr = theSong.getNumber();
        var sb = new StringBuilder(title.length() + 10);

        try {
            var number = Integer.parseInt(numberStr);
            sb.append(String.format("%03d", number));
        } catch (NumberFormatException nfe) {
            sb.append(numberStr);
        }

        if (!numberStr.isEmpty()) {
            sb.append(' ');
        }

        sb.append(StringUtils.stripDiacritics(title));
        return sb.toString();
    }

    public void setSong(Song song) {
        this.song = song;
        var lineWidthPx = ScaleContext.getInstance().ssToRoundedPx(song.getLineWidthSs());

        // Core setup needed for both headless and interactive modes
        updatePageLayout(lineWidthPx);

        for (var i = 0; i < song.lineCount(); i++) {
            drawWidthIfWiderLine(song.getLine(i), true);
        }

        song.setModified(false);

        if (!initialized) {
            return;
        }

        // Interactive-only setup below

        operations = new MusicEditOperations(song, selectionCoordinator);

        if (messageCoordinator == null) {
            messageCoordinator = new ScoreViewController(
                this,
                operations,
                editModeManager,
                selectionCoordinator,
                clipboardManager
            );
        } else {
            messageCoordinator.setOperations(operations);
        }

        PlaybackController.stop();
        selectionCoordinator.clearSelection();

        if (mainPanel == null) {
            throw RuntimeError.exit("mainPanel not initialized");
        }

        mainPanel.setSong(getSong());
        setupLineComponentState();

        syncPlaybackPrefs();
        viewChanged();

        // Notify all subscribers (LyricsPanel, ScoreViewController, UIActions, etc.)
        // that the song has been fully replaced. This must happen after all
        // ScoreView state is consistent.
        MessageCenter.post(new DocumentDidLoadNotification(song));

        // Reset scroll position to top-left for the new/opened song
        if (scrollPane != null) {
            scrollPane.getViewport().setViewPosition(new Point(0, 0));
        }

        repaint();
    }

    @Nullable
    public StaffElement getSingleSelectedElement() {
        return selectionCoordinator.getSingleSelectedElement();
    }

    @Override
    public int getStartY() {
        // TODO: Calculate from component hierarchy
        return 0;
    }

    public Dimension getSheetSize() {
        return sheetSize;
    }

    public int getSheetWidthPx() {
        return ScaleContext.getInstance().ssToRoundedPx(getSong().getLineWidthSs());
    }

    public int getSheetHeightPx() {
        // TODO: Calculate from component hierarchy
        return getHeight();
    }

    /**
     * Returns the sheet height in pixels, adjusted for the given export options.
     * When the rendering pipeline is fully implemented, this will calculate
     * the height without including excluded sections (lyrics, title, attribution).
     *
     * @param options controls which content sections to include in the measurement
     * @return the sheet height in pixels
     */
    public int getSheetHeightPx(ExportOptions options) {
        // TODO: Calculate height based on options without relying on component layout.
        // For now, returns full height since rendering is not yet implemented.
        return getHeight();
    }

    @Override
    public void drawWidthIfWiderLine(Line line, boolean revalidateOnly) {
        // Exclude the auto-maintained FINAL_DOUBLE_BARLINE from stretch calculations:
        // its position is fixed at the line's right edge and must not be treated as
        // the end note when computing the ratio.
        var effectiveCount = line.effectiveElementCount();

        if (effectiveCount > 1) {
            var endNote = line.getElement(effectiveCount - 1);
            float idealSpace;

            if (revalidateOnly) {
                idealSpace = (float) endNote.getContentWidthPx();
            } else {
                idealSpace = (float) ScaleContext.getInstance().ssToPx(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS) + 20;
            }

            var lineWidthPx = getSong().getLineWidthPx();

            if (endNote.getXOffsetPx() > (lineWidthPx - idealSpace)) {
                var firstX = line.getElement(0).getXOffsetPx();
                var ratio =
                    (lineWidthPx - idealSpace - firstX) /
                        (endNote.getXOffsetPx() - firstX);

                for (var i = 1; i < effectiveCount; i++) {
                    var note = line.getElement(i);
                    note.setXOffsetPx(
                        firstX + Math.round((note.getXOffsetPx() - firstX) * ratio)
                    );
                }

                line.changeElementSpacingRatio(ratio);
            }
        }
    }

    public SelectionCoordinator getSelectionCoordinator() {
        return selectionCoordinator;
    }

    public void setInSelectMode(boolean inSelectMode) {
        selectionCoordinator.setInSelectMode(inSelectMode);
    }

    @Override
    public Control getControl() {
        return control;
    }

    @Override
    public void setControl(Control control) {
        this.control = control;
    }

    @Override
    public Mode getMode() {
        return mode;
    }

    @Override
    public @Nullable Window getWindow() {
        return SwingUtilities.getWindowAncestor(this);
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public @Nullable HorizontalAdjustment getHorizontalAdjustment() {
        return horizontalAdjustment;
    }

    public @Nullable VerticalAdjustment getVerticalAdjustment() {
        return verticalAdjustment;
    }

    @Override
    public int getLeadingKeysPosPx() {
        return leadingKeysPosPx;
    }

    public void setLeadingKeysPosPx(int leadingKeysPosPx) {
        this.leadingKeysPosPx = leadingKeysPosPx;
    }

    @Override
    public int getRowHeightPx() {
        return rowHeightPx;
    }

    public void setRowHeightPx(int rowHeightPx) {
        this.rowHeightPx = rowHeightPx;
    }

    @Override
    public int getMiddleLineYPx() {
        return middleLineYPx;
    }

    public void setMiddleLineYPx(int middleLineYPx) {
        this.middleLineYPx = middleLineYPx;
    }

    public void setDragDisabled(boolean dragDisabled) {
        this.dragDisabled = dragDisabled;
    }

    public void saveProperties() {
        Prefs.getInstance().put(PrefsKey.CONTROL, control.name());
    }

    public void updatePageLayout(int lineWidthPx) {
        getSong().setLineWidthSs(ScaleContext.getInstance().pxToSs(lineWidthPx));

        var pageModel = PageModel.getInstance();
        var pageWidthPx = pageModel.getPageWidthPx();
        var contentHeight = (mainPanel != null) ? mainPanel.getPreferredSize().height : 0;
        var minPageHeight = contentHeight + pageModel.getTopMarginPx() + pageModel.getBottomMarginPx();

        preferredSizePx.width = pageWidthPx;
        preferredSizePx.height = Math.max(pageModel.getPageHeightPx(), minPageHeight);
        setPreferredSize(preferredSizePx);

        var horizontalMarginPx = pageModel.getHorizontalMarginPx(lineWidthPx);
        setBorder(BorderFactory.createEmptyBorder(
            pageModel.getTopMarginPx(),
            horizontalMarginPx,
            pageModel.getBottomMarginPx(),
            horizontalMarginPx
        ));
        invalidate();

        if (scorePanel != null) {
            scorePanel.invalidate();
        }

        if (scrollPane != null) {
            scrollPane.validate();
        }

        repaint();
    }

    @Override
    public int getSelectedLine() {
        return selectionCoordinator.getSelectedLine();
    }

    @Override
    public int getPlayingLine() {
        // Playback state is now managed by PlaybackController
        // For now, return -1 (not playing) since LineComponents handle their own state
        return -1;
    }

    @Override
    public int getPlayingNote() {
        // Playback state is now managed by PlaybackController
        // For now, return -1 (not playing) since LineComponents handle their own state
        return -1;
    }

    @Nullable
    public ElementSelection getSelection() {
        return selectionCoordinator.getSelection();
    }

    public BufferedImage createImageForExport(
        Color background,
        double scale,
        MyBorder border,
        ExportOptions options
    ) {
        return ImageExporter.createImageForExport(
            this,
            background,
            scale,
            border,
            options
        );
    }

    public enum ConnectionType {
        BEAM,
        TIE,
        SLUR,
    }

    public int getPasteboardSize() {
        return clipboardManager.getSize();
    }

    public boolean canDeleteLine() {
        return selectionCoordinator.canDeleteLine();
    }

    public SongLayoutMetrics getSongLayoutMetrics() {
        if (songLayoutMetrics == null) {
            throw RuntimeError.exit(
                "SongLayoutMetrics accessed before StaffPanel populated it");
        }

        return songLayoutMetrics;
    }

    public void setSongLayoutMetrics(SongLayoutMetrics metrics) {
        songLayoutMetrics = metrics;
    }

    public LyricRenderMetrics getLyricRenderMetrics() {
        if (lyricRenderMetrics == null) {
            throw RuntimeError.exit(
                "LyricRenderMetrics accessed before StaffPanel populated it");
        }

        return lyricRenderMetrics;
    }

    public void setLyricRenderMetrics(LyricRenderMetrics metrics) {
        lyricRenderMetrics = metrics;
    }

    /**
     * Rebuilds {@link LyricRenderMetrics} from the current lyrics font if it has changed.
     * Must be called before any line layout runs so that {@link LineComponent#performLayout}
     * reads up-to-date hyphen and space widths.
     */
    public void rebuildLyricRenderMetrics() {
        if (song == null) {
            return;
        }

        var lyricsFont = song.getLyricsFont();

        if (lyricRenderMetrics != null && lyricRenderMetrics.lyricsFont().equals(lyricsFont)) {
            return;
        }

        var scale = ScaleContext.getInstance();
        lyricRenderMetrics = new LyricRenderMetrics(
            lyricsFont,
            scale.scaleFont(lyricsFont),
            scale.textWidthSs(lyricsFont, "-"),
            scale.textWidthSs(lyricsFont, "  "));
    }

    @Nullable
    public LyricEditor getActiveLyricEditor() {
        return activeLyricEditor;
    }

    public void setActiveLyricEditor(@Nullable LyricEditor editor) {
        activeLyricEditor = editor;
    }

    /**
     * Adds {@code overlay} as a free-floating absolute-bounds child. ScoreView uses BorderLayout
     * for its main panel, so we must register the overlay with null constraints (so the
     * layout manager ignores it) and restore the main panel's center mapping that
     * {@link #add(Component)} silently overwrote.
     */
    public void addOverlay(JComponent overlay) {
        add(overlay);

        var layout = (BorderLayout) getLayout();
        layout.removeLayoutComponent(overlay);

        if (mainPanel != null) {
            layout.addLayoutComponent(mainPanel, BorderLayout.CENTER);
        }
    }

}
