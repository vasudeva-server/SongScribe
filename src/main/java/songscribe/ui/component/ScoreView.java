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
import java.util.LinkedHashMap;
import songscribe.error.RuntimeError;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.FileExtensions;
import songscribe.Strings;
import songscribe.export.ExportOptions;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.export.ImageExporter;
import songscribe.export.SVGExporter;
import songscribe.io.SongLoadResult;
import songscribe.io.SongFileLoader;
import songscribe.message.MessageCenter;
import songscribe.dom.Song;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.MusicEditOperations;
import songscribe.message.mutation.FontChange;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.Control;
import songscribe.ui.FlatLafKey;
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
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.SongLayoutMetrics;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.PageModel;
import songscribe.dom.ScaleContext;
import songscribe.engraving.Staff;
import songscribe.util.FileUtils;
import songscribe.util.GraphicsState;
import songscribe.util.GraphicUtils;
import songscribe.util.StringUtils;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.RenderContext;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;

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
    DocumentFontsHolder,
    InputHandlerCallback,
    LineComponent.SelectionProvider,
    RenderContext,
    ScoreActions {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreView.class);

    private static final String DISABLED_KEY_BINDING = "none";

    // Colors used to draw the music score in various states — read from UIManager for theming.
    // Callers should not cache these values; read at render time.
    public static Color getPlayingNoteColor() {
        return FlatLafProps.getColor(FlatLafKey.SCORE_PLAYING_NOTE_COLOR);
    }

    public static Color getPreviewElementColor() {
        return FlatLafProps.getColor(FlatLafKey.SCORE_PREVIEW_ELEMENT_COLOR);
    }

    public static Color getSelectionColor() {
        return FlatLafProps.getColor(FlatLafKey.SCORE_SELECTION_COLOR);
    }

    // Edit popup
    @Nullable
    private JPopupMenu popup = null;

    private final Dimension sheetSize = new Dimension();

    // Called when a file is successfully opened (e.g. to update the window title)
    private final @Nullable Consumer<? super File> onFileOpened;

    private final ScoreViewState viewState;

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

    // New JComponent-based score panel (Phase 2 hierarchy)
    @Nullable
    private MainPanel mainPanel = null;

    // Coordinates message handling
    @Nullable
    private ScoreViewController controller = null;

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
    // Package-private so tests can observe the rebuildLyricRenderMetrics() no-op guards
    // without going through getLyricRenderMetrics(), which fatally exits when unset.
    @Nullable LyricRenderMetrics lyricRenderMetrics;

    // Authoritative document-level fonts. Null only before first bootstrap in init().
    // Phase 5+: sole source of truth; ScoreView.setFonts() is the only write entry.
    @Nullable
    private DocumentFonts documentFonts = null;

    // The currently-open lyric editor overlay, if any. Set by LyricEditor.openOn /
    // dismiss so getActiveLyricEditor() doesn't have to scan getComponents() per paint.
    @Nullable private LyricEditor activeLyricEditor;

    // Maps each registered KeyStroke to its action key so bindings can be toggled.
    // Package-private so tests can inject synthetic bindings directly.
    final Map<KeyStroke, Object> scoreKeyBindings = new LinkedHashMap<>();

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
        viewState = new ScoreViewState(Control.valueOf(Prefs.getString(PrefsKey.CONTROL)));

        selectionCoordinator = new SelectionCoordinator(this::getSong);
        clipboardManager = new ClipboardManager();
        EditModeManager.init(clipboardManager, selectionCoordinator, this);

        if (headless) {
            hierarchyNavigator = null;
            inputHandler = null;
        } else {
            hierarchyNavigator = new ComponentHierarchyNavigator(this);
            var handler = new ScoreInputHandler(this);
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
        documentFonts = DocumentFonts.defaultFonts();

        // Initialize UI components
        initView();
        initAdjustments();
        initScorePanel();
        initMainPanel();
        applyDocumentFonts();

        updatePageLayout(ScaleContext.ssToRoundedPx(song.getLineWidthSs()));

        if (inputHandler != null) {
            addMouseMotionListener(inputHandler);
            addMouseListener(inputHandler);
        }

        initEditPopup();
        selectionChanged();
        initKeys();

        // Initialize insertion note with default type
        setPreviewElement(EditModeManager.makePreviewElement());

        MessageCenter.subscribe(this);
        syncPlaybackPrefs();
        song.setModified(false);

        // Create operations and message coordinator (requires mainPanel to be set)
        operations = new MusicEditOperations(song, selectionCoordinator);
        controller = new ScoreViewController(
            this,
            operations,
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

        var color = FlatLafProps.getColor(FlatLafKey.SCORE_PANEL_BACKGROUND);
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
        viewState.setHorizontalAdjustment(new HorizontalAdjustment(this));
        viewState.setVerticalAdjustment(new VerticalAdjustment(this));
    }

    void initView() {
        viewChanged();
    }

    public boolean openFile(File file, boolean updateCurrentFile) {
        var result = SongFileLoader.load(file);

        if (result instanceof SongLoadResult.Success success) {
            var lineWidthInches =
                ScaleContext.ssToPx(success.song().getLineWidthSs()) /
                    GraphicUtils.getDpi();

            if (lineWidthInches > PageModel.MAX_LINE_WIDTH_INCHES) {
                result = new SongLoadResult.LineWidthTooLarge(file, lineWidthInches, PageModel.MAX_LINE_WIDTH_INCHES);
            }
        }

        return switch (result) {
            case SongLoadResult.Success success -> {
                // Install the document's fonts before setSong so the initial layout
                // pass uses them; otherwise the line is laid out with the previous
                // defaults-from-prefs attribution font, and the first edit shifts
                // attachments (tempo, etc.) once layout reruns with the doc font.
                installDocumentFonts(success.fonts());
                setSong(success.song());

                if (updateCurrentFile && onFileOpened != null) {
                    onFileOpened.accept(FileUtils.hasExtension(file, FileExtensions.SONGWRITER) ? null : file);
                }

                var warning = success.warning();

                if (warning != null) {
                    switch (warning.type()) {
                        case INVALID_LYRICS_DATE -> OptionDialogs.showWarningMessage(
                            null,
                            Strings.ALERT_TITLE_LYRICS_DATE,
                            Strings.ALERT_LYRICS_DATE_INVALID,
                            warning.description()
                        );
                    }
                }

                LOG.info("Song loaded: {}", file.getName());
                yield true;
            }
            case SongLoadResult.NewerVersion e -> {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_NEWER_VERSION);
                LOG.error("Could not open '{}': document version is newer than the application supports", file.getName(), e.cause());
                yield false;
            }
            case SongLoadResult.ParseError e -> {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_DAMAGED, file.getName());
                LOG.error("Could not open damaged file '{}'", file.getName(), e.cause());
                yield false;
            }
            case SongLoadResult.IoError e -> {
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ERROR_FILE_OPEN_NO_PERMISSION, file.getName());
                LOG.error("Could not open file '{}': permission error", file.getName(), e.cause());
                yield false;
            }
            case SongLoadResult.LineWidthTooLarge e -> {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_FILE_ERROR,
                    Strings.ERROR_LINE_WIDTH_TOO_LARGE
                );
                LOG.error(
                    "Refused to open {}: line width {} inches exceeds maximum {}",
                    file.getName(),
                    e.actualInches(),
                    e.maxInches()
                );
                yield false;
            }
            case SongLoadResult.WrongSoftware e -> {
                var software = e.software();
                var name = (software != null && !software.isBlank())
                    ? software
                    : Strings.get(Strings.ALERT_MUSICXML_FOREIGN_OTHER);
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_MUSICXML_FOREIGN, name);
                LOG.error("Refused to open '{}': not created by SongScribe (software: {})", file.getName(), software);
                yield false;
            }
            case SongLoadResult.UnsupportedFileFormat e -> {
                var fileName = file.getName();
                OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_FILE_ERROR, Strings.ALERT_MUSICXML_UNSUPPORTED, fileName);
                LOG.error("Refused to open '{}': unsupported file format ({})", fileName, e.detail());
                yield false;
            }
        };
    }

    public void syncPlaybackPrefs() {
        EditModeManager.setPlayInsertedNote(Prefs.getBoolean(PrefsKey.PLAY_INSERTED_NOTE));
        // If true, the score is played with repeats
        var playWithRepeats = Prefs.getBoolean(PrefsKey.PLAY_WITH_REPEATS);

        // Delegate playback settings to PlaybackController
        PlaybackController.setInstrument(Prefs.getInt(PrefsKey.INSTRUMENT));
        PlaybackController.setTempoChangePercent(Prefs.getInt(PrefsKey.TEMPO_CHANGE_PERCENT));
        PlaybackController.setNoteDurationPercent(Prefs.getInt(PrefsKey.PLAYBACK_NOTE_DURATION));
        PlaybackController.setPlayWithRepeats(playWithRepeats);
        PlaybackController.applyVolumeFromPrefs();
        PlaybackController.applyPrefsDuringPlayback();
    }

    public void setKeyBindingsEnabled(boolean enabled) {
        var inputMap = getInputMap(JComponent.WHEN_FOCUSED);

        if (enabled) {
            scoreKeyBindings.forEach(inputMap::put);
        } else {
            scoreKeyBindings.keySet().forEach(keyStroke -> inputMap.put(keyStroke, DISABLED_KEY_BINDING));
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

    public JScrollPane requireScrollPane() {
        if (scrollPane == null) {
            throw RuntimeError.exit("score scroll pane not initialized");
        }
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
    public boolean isSlideSelected(int elementIndex, int lineIndex) {
        return selectionCoordinator.isSlideSelected(elementIndex, lineIndex);
    }

    @Override
    public boolean isLyricSelected(StaffElement element, int verse, int lineIndex) {
        return selectionCoordinator.isLyricSelected(element, verse, lineIndex);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        var graphics2d = (Graphics2D) g;

        try (var ignored = GraphicsState.save(
            graphics2d,
            GraphicsState.Property.COLOR
        )) {
            graphics2d.setColor(FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND));
            graphics2d.fillRect(0, 0, getWidth(), getHeight());
        }

        drawEditElements(graphics2d);
    }

    private void drawEditElements(Graphics2D g2) {
        var mode = viewState.getMode();
        var horizontalAdjustment = viewState.getHorizontalAdjustment();
        var verticalAdjustment = viewState.getVerticalAdjustment();

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
        return (int) Math.round(middleLineYPx +
            ScaleContext.ssToPx(Staff.spToSs(staffPosition)) +
            (lineIndex * rowHeightPx));
    }

    @Override
    public int getUnderLyricsYPosPx() {
        // TODO: Calculate from component hierarchy
        return 0;
    }

    @Nullable
    public StaffElement getPreviewElement() {
        return EditModeManager.getPreviewElement();
    }

    @Override
    public void setPreviewElement(@Nullable StaffElement element) {
        if (element != null) {
            var currentPreviewElement = EditModeManager.getPreviewElement();

            if (currentPreviewElement != null) {
                element.setStaffPosition(currentPreviewElement.getStaffPosition());
                element.setXOffsetPx(currentPreviewElement.getXOffsetPx());
            } else {
                EditModeManager.setPreviewElement(element);
            }

            element.setDirection(StaffElement.defaultDirection(element));
        }

        EditModeManager.setPreviewElement(element);
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

    @Override
    public void extendSelectionTo(int targetIndex) {
        var state = selectionCoordinator.getActiveSelection();

        if (state == null) {
            return;
        }

        // A missing anchor is a no-op inside LineSelectionState.extendSelectionTo.
        state.extendSelectionTo(targetIndex);
        selectionChanged();
        repaint();
    }

    public int getSelectionSize() {
        return selectionCoordinator.getSelectionSize();
    }

    public boolean isInitialized() {
        return song != null;
    }

    public @Nullable ScoreViewController getController() {
        return controller;
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
        var stringBuilder = new StringBuilder(title.length() + 10);

        try {
            var number = Integer.parseInt(numberStr);
            stringBuilder.append(String.format("%03d", number));
        } catch (NumberFormatException nfe) {
            stringBuilder.append(numberStr);
        }

        if (!numberStr.isEmpty()) {
            stringBuilder.append(' ');
        }

        stringBuilder.append(StringUtils.stripDiacritics(title));
        return stringBuilder.toString();
    }

    public void setSong(Song song) {
        this.song = song;
        var lineWidthPx = ScaleContext.ssToRoundedPx(song.getLineWidthSs());

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

        if (operations == null) {
            operations = new MusicEditOperations(song, selectionCoordinator);
        } else {
            operations.setSong(song);
        }

        if (controller == null) {
            controller = new ScoreViewController(
                this,
                operations,
                selectionCoordinator,
                clipboardManager
            );
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
        return ScaleContext.ssToRoundedPx(getSong().getLineWidthSs());
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
                idealSpace = (float) ScaleContext.ssToPx(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS) + 20;
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

    public ScoreViewState getViewState() {
        return viewState;
    }

    @Override
    public Control getControl() {
        return viewState.getControl();
    }

    public void setControl(Control control) {
        viewState.setControl(control);
    }

    @Override
    public Mode getMode() {
        return viewState.getMode();
    }

    @Override
    public @Nullable Window getWindow() {
        return SwingUtilities.getWindowAncestor(this);
    }

    public void setMode(Mode mode) {
        viewState.setMode(mode);
    }

    public @Nullable HorizontalAdjustment getHorizontalAdjustment() {
        return viewState.getHorizontalAdjustment();
    }

    public @Nullable VerticalAdjustment getVerticalAdjustment() {
        return viewState.getVerticalAdjustment();
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

    public void updatePageLayout(int lineWidthPx) {
        getSong().setLineWidthSs(ScaleContext.pxToSs(lineWidthPx));

        var pageWidthPx = PageModel.getPageWidthPx();
        var contentHeight = (mainPanel != null) ? mainPanel.getPreferredSize().height : 0;
        var minPageHeight = contentHeight + PageModel.getTopMarginPx() + PageModel.getBottomMarginPx();

        preferredSizePx.width = pageWidthPx;
        preferredSizePx.height = Math.max(PageModel.getPageHeightPx(), minPageHeight);
        setPreferredSize(preferredSizePx);

        var horizontalMarginPx = PageModel.getHorizontalMarginPx(lineWidthPx);
        setBorder(BorderFactory.createEmptyBorder(
            PageModel.getTopMarginPx(),
            horizontalMarginPx,
            PageModel.getBottomMarginPx(),
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
        if (song == null || documentFonts == null) {
            return;
        }

        var lyricsFont = documentFonts.getLyricsFont();

        if (lyricRenderMetrics != null && lyricRenderMetrics.lyricsFont().equals(lyricsFont)) {
            return;
        }

        lyricRenderMetrics = new LyricRenderMetrics(
            lyricsFont,
            ScaleContext.scaleFont(lyricsFont),
            ScaleContext.textWidthSs(lyricsFont, "-"),
            ScaleContext.textWidthSs(lyricsFont, "  "));
    }

    @Nullable
    public LyricEditor getActiveLyricEditor() {
        return activeLyricEditor;
    }

    public void setActiveLyricEditor(@Nullable LyricEditor editor) {
        activeLyricEditor = editor;
    }

    // -------------------------------------------------------------------------
    // Document fonts
    // -------------------------------------------------------------------------

    /**
     * Returns internal document font storage. Do not retain — Phase 5 swaps the reference
     * on each font change.
     *
     * @throws IllegalStateException if called before {@link #init()} completes
     */
    public DocumentFonts getDocumentFonts() {
        if (documentFonts == null) {
            throw new IllegalStateException("documentFonts not initialized");
        }
        return documentFonts;
    }

    /** {@inheritDoc} */
    @Override
    public Font getFont(FontKey key) {
        return getDocumentFonts().getFont(key);
    }

    /**
     * Applies the current {@link #documentFonts} to each JComponent leaf and rebuilds
     * lyric render metrics. Each call triggers one {@link LyricRenderMetrics} rebuild
     * and one Swing {@code revalidate()} pass on the ScoreView subtree. For batched
     * font changes, build a single {@code DocumentFonts} and call {@code setFonts()}
     * once rather than applying role-by-role.
     */
    private void applyDocumentFonts() {
        if (documentFonts == null) {
            throw new IllegalStateException("applyDocumentFonts called before documentFonts initialized");
        }

        // Headless converter path: openFile -> installDocumentFonts runs without init().
        if (mainPanel == null) {
            return;
        }

        rebuildLyricRenderMetrics();
        var textPanel = mainPanel.getTextPanel();
        var lyricsFont = documentFonts.getLyricsFont();
        mainPanel.getTitleComponent().setFont(documentFonts.getTitleFont());
        mainPanel.getSubtitleComponent().setFont(documentFonts.getSubtitleFont());
        textPanel.getUnderLyricsComponent().setFont(lyricsFont);
        textPanel.getBanglaLyricsComponent().setFont(documentFonts.getBanglaFont());
        textPanel.getTranslationComponent().setFont(lyricsFont);
        mainPanel.getFootnotesComponent().setFont(documentFonts.getFootnoteFont());
        revalidate();
    }

    /**
     * Installs new document fonts and cascades them through the component tree.
     * <p>
     * Called by the load path ({@link #openFile} after {@link #setSong}) and
     * by new-document creation. Unlike {@link #setFonts}, this is not undoable
     * and does not record a {@link FontChange} mutation — it establishes the
     * starting state, not a user edit.
     */
    public void installDocumentFonts(DocumentFonts fonts) {
        documentFonts = fonts;
        applyDocumentFonts();
    }

    /**
     * Replaces the document fonts and cascades the change.
     *
     * <p>If {@code newFonts.equals(this.documentFonts)} the call is a no-op:
     * no mutation is recorded, no cascade runs.
     *
     * <p>Otherwise records a single {@link FontChange} mutation inside the
     * song's modification bracket — a commit that changes multiple roles
     * produces one undoable group, not one per role.
     *
     * <p><b>Cost:</b> each non-no-op call triggers a full
     * {@link LyricRenderMetrics} rebuild and one Swing {@code revalidate()}
     * pass. For batched changes, construct a single {@link DocumentFonts} and
     * call this method once rather than role-by-role.
     */
    public void setFonts(DocumentFonts newFonts) {
        var oldFonts = getDocumentFonts();

        if (newFonts.equals(oldFonts)) {
            return;
        }

        var theSong = getSong();
        theSong.withModification(() ->
            theSong.applyChange(new FontChange(oldFonts, newFonts), () -> {
                documentFonts = newFonts;
                applyDocumentFonts();
            })
        );
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
