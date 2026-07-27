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

import com.formdev.flatlaf.util.SystemInfo;

import java.io.File;
import java.util.LinkedHashMap;
import songscribe.error.RuntimeError;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.engio.mbassy.listener.Handler;

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
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.dom.DocPx;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.ui.MusicEditOperations;
import songscribe.message.mutation.FontChange;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.ZoomController;
import songscribe.ui.platform.mac.PinchZoomGesture;
import songscribe.ui.adjustment.HorizontalAdjustment;
import songscribe.ui.adjustment.VerticalAdjustment;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.LineOverlayComponent;
import songscribe.ui.component.score.OverlayHost;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.ScorePanel;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.ScoreActions;
import songscribe.layout.HorizontalSpacingCalculator;
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
 *           ├── LyricEditor            (absolute bounds, topmost; only while editing a lyric)
 *           ├── LineOverlayComponent × N (absolute bounds, above the score, below the editor)
 *           └── MainPanel [BoxLayout Y_AXIS, CENTER]
 *               ├── TitleComponent
 *               ├── SubtitleComponent
 *               ├── ScoreMarginStrut
 *               ├── StaffPanel [StaffLinesLayout]
 *               │   └── LinePanel × N
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
    OverlayHost,
    RenderContext,
    ScoreActions {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreView.class);

    /**
     * Z-order index of the active lyric editor: the topmost child of this view. Overlays
     * registered through {@link #addOverlay} sit directly beneath it — see that method's
     * z-order contract.
     */
    public static final int LYRIC_EDITOR_Z_ORDER = 0;

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

    private final Dimension sheetSize = new Dimension();

    // Per-view zoom state — the sole source of truth for this view's zoom.
    // On-score components read it on demand via getViewScale().
    private final ViewScale viewScale = new ViewScale();

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

    // Song-wide lyric render metrics shared across all line components.
    // Rebuilt by StaffPanel.ensureAllLineLayouts before any layout/paint runs.
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
        viewState = new ScoreViewState();

        selectionCoordinator = new SelectionCoordinator();
        selectionCoordinator.setScoreView(this);
        clipboardManager = new ClipboardManager();
        EditModeManager.init(clipboardManager, selectionCoordinator, this, this);

        if (headless) {
            hierarchyNavigator = null;
            inputHandler = null;
        } else {
            hierarchyNavigator = new ComponentHierarchyNavigator(this);
            hierarchyNavigator.setScoreView(this);
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
            addMouseWheelListener(inputHandler);

            if (SystemInfo.isMacOS) {
                PinchZoomGesture.installOn(this);
            }
        }

        selectionChanged();
        initKeys();

        // One preview overlay for this view's lifetime; it is retargeted, never recreated.
        PreviewElementManager.installOverlay(this);
        EditModeManager.getPasteModeManager().installOverlay();

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
        mainPanel.setScoreView(this);
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

        // The line components the overlays point at may have just been recreated.
        retargetOverlays();
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

    public boolean isDragDisabled() {
        return dragDisabled;
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
    public boolean isDecorationSelected(LineElement element, int lineIndex) {
        return selectionCoordinator.isDecorationSelected(element, lineIndex);
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

    /**
     * Declares that this container has overlapping children: the line overlays are siblings of
     * the score content and deliberately paint on top of it.
     * <p>
     * This is a statement of fact, not a workaround. {@link JLayeredPane} returns {@code false}
     * for exactly the same reason, and any future container that hosts overlays — the page
     * components under {@code specs/184-pagination.md} — must too.
     * <p>
     * The consequence is that Swing cannot resolve a repaint to a single child: this view
     * repaints <b>every</b> child intersecting the damaged rectangle, including the
     * {@link LineComponent} beneath an overlay, which runs the full line render pipeline.
     * Sizing overlays to exact ink <b>shrinks</b> that rectangle — it used to be inflated by
     * 1.5 staff spaces of slop — but it does not remove the underlying repaint. The win is
     * correctness (no stale ink, no clipped glyphs), not a reduction in repaint cost.
     */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
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

        // The preview overlay caches the ink the renderers produced for the previous element,
        // so a new element is a rebuild, not merely a repaint. It must also re-anchor to the
        // real mouse position rather than whatever insertion index/staff position was last
        // tracked, or the preview draws in the wrong spot until the mouse actually moves.
        PreviewElementManager.previewElementTypeDidChange();
        repaint();
    }

    @Override
    public void clearSelection() {
        // Callers repaint the line they are acting on, not the one losing its selection,
        // so the outgoing line would keep painting a stale highlight without this.
        var deselectedLine = getLineComponent(selectionCoordinator.getActiveLineIndex());

        selectionCoordinator.clearSelection();
        selectionChanged();

        if (deselectedLine != null) {
            deselectedLine.repaint();
        }
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

    @Override
    public void editLyricOnSelection() {
        // Leaving SELECT mode clears the selection, so a live selection already implies
        // the mode gate that EditLyricAction spells out; playback does not clear it.
        if (PlaybackController.isPlaying()) {
            return;
        }

        var selection = selectionCoordinator.getSelection();

        if (selection == null) {
            return;
        }

        // EditLyricAction is disabled unless exactly one element is selected, and a
        // selected staff line reports itself as a selection spanning the whole line.
        // Requiring a single element keeps Return in step with the menu command rather
        // than silently picking one note out of a range the user never singled out.
        if (selection.begin() != selection.end()) {
            return;
        }

        var line = selection.line();
        var targetIndex = LyricTargetResolver.resolveLyricTarget(line, selection.begin());

        if (targetIndex < 0) {
            return;
        }

        LyricEditor.deselectAndOpenOn(this, line, targetIndex);
    }

    @Override
    public void zoomByWheel(double preciseWheelRotation, Point viewPoint) {
        ZoomController.zoomByWheel(preciseWheelRotation, viewPoint);
    }

    @Override
    public void zoomByMagnification(double magnification, @Nullable Point viewPoint) {
        ZoomController.zoomByMagnification(magnification, viewPoint);
    }

    @Override
    public void forwardWheelScroll(MouseWheelEvent e) {
        if (scrollPane == null) {
            return;
        }

        // Rebuild the event with the scroll pane as source/target so it is delivered
        // straight to JScrollPane's own listeners, bypassing AWT's ancestor search
        // (which would otherwise stop at this view, since it has its own wheel listener).
        var pointInScrollPane = SwingUtilities.convertPoint(this, e.getPoint(), scrollPane);
        var forwardedEvent = new MouseWheelEvent(
            scrollPane,
            e.getID(),
            e.getWhen(),
            e.getModifiersEx(),
            pointInScrollPane.x,
            pointInScrollPane.y,
            e.getXOnScreen(),
            e.getYOnScreen(),
            e.getClickCount(),
            e.isPopupTrigger(),
            e.getScrollType(),
            e.getScrollAmount(),
            e.getWheelRotation(),
            e.getPreciseWheelRotation()
        );

        scrollPane.dispatchEvent(forwardedEvent);
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
        // MBassador holds Song subscribers by weak reference, so the outgoing Song
        // keeps handling broadcast commands — spawning spurious undo steps against
        // the dead document — until GC clears it. Detach it deterministically here.
        if (this.song != null && this.song != song) {
            this.song.unsubscribeFromBus();
        }

        this.song = song;

        // Reset zoom to 100% for the new/opened song before laying out at the old zoom.
        ZoomController.resetZoom();

        var lineWidthPx = ScaleContext.ssToRoundedPx(song.getLineWidthSs());

        // Core setup needed for both headless and interactive modes
        updatePageLayout(lineWidthPx);

        // The fit pass records ELEMENT_SPACING_RATIO changes through a
        // mutation-tracked setter. On load there is no open modification bracket,
        // and this normalization is not a user edit — suspend tracking so it
        // neither throws nor pollutes undo history or the modified flag.
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < song.lineCount(); i++) {
                drawWidthIfWiderLine(song.getLine(i), true);
            }
        });

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
        //
        // Zoom-independent by construction: unlike ScoreView's own getHeight() (which is
        // clamped against the page height and rounded at the view scale in layoutPage),
        // this reproduces that max/margin arithmetic entirely in document space so future
        // exporters never inherit view zoom. mainPanel's preferred height is the one
        // remaining view-scaled read; it is converted back to document px via the view's
        // own ViewScale rather than a raw factor() division, and is not first clamped or
        // rounded against the page height, so no page-height information is lost in the
        // round trip.
        var contentHeightViewPx = (mainPanel != null) ? mainPanel.getPreferredSize().height : 0;
        var contentHeightDocPx = viewScale.toDocPx(new ViewPx(contentHeightViewPx));
        var topMarginPx = PageModel.getTopMarginPx();
        var bottomMarginPx = PageModel.getBottomMarginPx();
        var minPageHeightDocPx = contentHeightDocPx.value() + topMarginPx.value() + bottomMarginPx.value();
        var pageHeightDocPx = PageModel.getPageHeightPx();
        return new DocPx(Math.max(pageHeightDocPx.value(), minPageHeightDocPx)).roundedPx();
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
        return getSheetHeightPx();
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

    public void updatePageLayout(int lineWidthDocPx) {
        getSong().setLineWidthSs(ScaleContext.pxToSs(lineWidthDocPx));
        // layoutPage expects view px; the width arrives in document px, so fold in the
        // current zoom. Skipping this left the page centered for the wrong width at any
        // zoom other than 100% (content against the left edge when zoomed out, clipped
        // on the right when zoomed in).
        layoutPage(viewScale.toViewPx(new DocPx(lineWidthDocPx)).roundedPx());
    }

    /**
     * Re-sizes the page canvas and re-centers the content for {@code lineWidthPx}
     * at the current zoom, <em>without</em> mutating the song's stored line width.
     * {@link #updatePageLayout} writes the width to the model first and then calls
     * this; the zoom handler calls it directly so a pure view change never records
     * a document mutation or undo entry.
     */
    private void layoutPage(int lineWidthPx) {
        // PageModel returns document (100%-zoom) page dimensions; scale them through this
        // view's ViewScale to size the page the same way zoom scales the staff content.
        // Sizes (widths/heights) round up via ceilPx() so content is never clipped at high
        // zoom; positions/margins round to nearest via roundedPx().
        var pageWidthPx = viewScale.toViewPx(PageModel.getPageWidthPx()).ceilPx();
        var pageHeightPx = viewScale.toViewPx(PageModel.getPageHeightPx()).ceilPx();
        var topMarginPx = viewScale.toViewPx(PageModel.getTopMarginPx()).roundedPx();
        var bottomMarginPx = viewScale.toViewPx(PageModel.getBottomMarginPx()).roundedPx();

        var contentHeight = (mainPanel != null) ? mainPanel.getPreferredSize().height : 0;
        var minPageHeight = contentHeight + topMarginPx + bottomMarginPx;

        preferredSizePx.width = pageWidthPx;
        preferredSizePx.height = Math.max(pageHeightPx, minPageHeight);
        setPreferredSize(preferredSizePx);

        // getHorizontalMarginPx operates in document space; convert lineWidthPx (view px) to
        // document px before the call, then convert the resulting margin back to view px.
        var lineWidthDocPx = viewScale.toDocPx(new ViewPx(lineWidthPx)).roundedPx();
        var horizontalMarginPx =
            viewScale.toViewPx(PageModel.getHorizontalMarginPx(lineWidthDocPx)).roundedPx();
        setBorder(BorderFactory.createEmptyBorder(
            topMarginPx,
            horizontalMarginPx,
            bottomMarginPx,
            horizontalMarginPx
        ));
        invalidate();

        if (scorePanel != null) {
            scorePanel.invalidate();
        }

        runWithoutViewportBlit(JScrollPane::validate);

        repaint();
    }

    /**
     * Invalidates {@code component} and every descendant.
     * <p>
     * {@link Container#invalidate} propagates <em>upward</em> only, and a still-valid
     * container serves cached geometry: {@link Container#preferredSize} reuses its last
     * answer, and a {@code LayoutManager2} is told to discard its own cache only when the
     * container it manages is invalidated. Every other trigger for that — a mutation, a
     * resize — originates inside the tree, so the normal upward propagation suffices.
     * <p>
     * A zoom change is the one input that comes from outside it: it alters every pixel
     * dimension in the tree while changing no component's own state, so nothing below this
     * view is ever marked invalid. Without this walk {@code StaffPanel} kept returning the
     * previous zoom's cached preferred size, was therefore never resized, was therefore
     * never invalidated, and {@code StaffLinesLayout} never ran again — leaving every line
     * painting at the new zoom inside bounds measured at the old one, so the lines
     * overlapped and clipped (issue #591).
     */
    private static void invalidateTree(Component component) {
        component.invalidate();

        if (component instanceof Container container) {
            for (var child : container.getComponents()) {
                invalidateTree(child);
            }
        }
    }

    /** The per-view zoom state. On-score components read it on demand. */
    public ViewScale getViewScale() {
        return viewScale;
    }

    /**
     * Runs before any other {@link ZoomDidChangeNotification} handler so the zoom is already
     * applied by the time they react — see the priority requirement documented on {@link
     * ZoomDidChangeNotification}.
     */
    private static final int ZOOM_APPLY_PRIORITY = Message.HIGH_PRIORITY;

    /**
     * Applies a {@link ZoomDidChangeNotification} to this view. The sole handler responsible
     * for actually performing a zoom change — see {@link #applyZoomPercent}.
     */
    @Handler(priority = ZOOM_APPLY_PRIORITY)
    public void zoomDidChangeApplyZoom(ZoomDidChangeNotification message) {
        applyZoomPercent(message.getNewZoomPercent(), message.getAnchorPoint());
    }

    /**
     * Refreshes every hosted overlay's on-screen bounds against the now-current zoom.
     * <p>
     * Overlay bounds are pixel-cached ({@link LineOverlayComponent#updateBounds()}) and
     * otherwise only refreshed on the next validation pass or mouse-driven update; without
     * this an overlay (e.g. the hover preview) stays at its pre-zoom screen position until
     * the mouse moves. Runs after {@link #zoomDidChangeApplyZoom} (default priority is lower
     * than {@link #ZOOM_APPLY_PRIORITY}), so the zoom is already applied.
     */
    @Handler
    public void zoomDidChangeRefreshOverlayBounds(ZoomDidChangeNotification message) {
        forEachLineOverlay(LineOverlayComponent::updateBounds);
    }

    /**
     * Applies {@code newPercent} to this view's {@link ViewScale} and re-anchors
     * the viewport around {@code anchorPoint}, a point in this ScoreView's local
     * (content) coordinate space — e.g. the cursor position for wheel/pinch zoom.
     * When {@code anchorPoint} is null, anchors at the viewport's horizontal
     * center and top edge instead (menu/keyboard zoom).
     * <p>
     * Called only from {@link #zoomDidChangeApplyZoom}, which is this view's own
     * high-priority handler of {@link ZoomDidChangeNotification} — see that message's
     * class doc for why every other reactor to a zoom change goes through the same
     * notification rather than a direct call. On-score components read
     * {@link #getViewScale()} on demand, so nothing is pushed into the tree here.
     * EDT-only, per the {@code ZoomController} contract.
     */
    public void applyZoomPercent(int newPercent, @Nullable Point anchorPoint) {
        runWithoutViewportBlit(
            scoreScrollPane -> applyZoomPercentAndReanchor(scoreScrollPane, newPercent, anchorPoint)
        );

        repaint();
    }

    /**
     * Runs {@code body} against this view's scroll pane with the viewport's blit
     * optimization disabled, restoring the previous scroll mode afterward. Does nothing
     * when this view has no scroll pane yet. Safe to nest — each level restores the mode
     * it found.
     * <p>
     * A relayout that resizes the content or moves the scroll position invalidates every
     * pixel in the viewport, but JViewport's blit optimization cannot know that. In
     * {@code BLIT_SCROLL_MODE} every scroll-position change — an explicit
     * {@code setViewPosition}, and the clamping one {@code ViewportLayout} performs from
     * inside {@code validate()} — synchronously copies the pixels already on screen to the
     * new offset and repaints only the newly exposed strip. That puts a frame of the
     * <em>previous</em> rendering, shifted, on screen before the queued repaint draws the
     * score afresh: glaring on a large jump such as 800% to 400% zoom, imperceptible when
     * the per-step delta is tiny, as during a pinch or slider drag.
     * {@code SIMPLE_SCROLL_MODE} paints from scratch instead, so no stale pixels ever reach
     * the screen; the previous mode is restored so ordinary scrolling keeps the blit.
     * Refs #651.
     */
    private void runWithoutViewportBlit(Consumer<JScrollPane> body) {
        if (scrollPane == null) {
            return;
        }

        var viewport = scrollPane.getViewport();
        var previousScrollMode = viewport.getScrollMode();
        viewport.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);

        try {
            body.accept(scrollPane);
        } finally {
            viewport.setScrollMode(previousScrollMode);
        }
    }

    /**
     * Applies {@code newPercent} to this view's {@link ViewScale}, re-lays out the page at
     * the new zoom, and scrolls so that {@code anchorPoint} — in this view's local (content)
     * coordinate space, or the viewport's horizontal center and top edge when null — stays
     * put on screen. The body of {@link #applyZoomPercent}.
     */
    private void applyZoomPercentAndReanchor(
        JScrollPane scoreScrollPane,
        int newPercent,
        @Nullable Point anchorPoint
    ) {
        var viewport = scoreScrollPane.getViewport();
        var extentSize = viewport.getExtentSize();

        Point anchorViewportOffset;
        Point anchorContentPoint;

        if (anchorPoint == null) {
            // Horizontally anchor at the viewport's center; vertically anchor at the
            // current scroll position (the viewport's top edge).
            anchorViewportOffset = new Point(extentSize.width / 2, 0);

            // Convert the anchor to ScoreView-content coordinates using the pre-revalidate
            // bounds — component bounds still reflect the old zoom at this point.
            anchorContentPoint = SwingUtilities.convertPoint(viewport, anchorViewportOffset, this);
        } else {
            // anchorPoint already arrives in ScoreView-content coordinates (it comes
            // from a MouseEvent whose listener is registered directly on this view).
            anchorContentPoint = anchorPoint;
            anchorViewportOffset = SwingUtilities.convertPoint(this, anchorPoint, viewport);
        }

        var oldPercent = viewScale.getZoomPercent();
        viewScale.setZoomPercent(newPercent);

        // Drop every cached pixel size in the score tree first: layoutPage reads
        // mainPanel's preferred size, and that read must already reflect the new zoom.
        invalidateTree(this);

        // Recompute the canvas's preferred size at the new zoom before re-layout. Go through
        // layoutPage (not updatePageLayout) so this pure view change does not write the
        // round-tripped width back to the model and record a spurious undo entry.
        layoutPage(viewScale.toViewPx(new Ss(getSong().getLineWidthSs())).roundedPx());

        // Force synchronous re-layout so the new (post-zoom) sizes are realized before
        // we read them below; plain revalidate() is async and would leave stale sizes.
        if (scorePanel != null) {
            scorePanel.invalidate();
        }

        scoreScrollPane.validate();

        var zoomRatio = newPercent / (double) oldPercent;
        var view = viewport.getView();

        // ScoreView's origin within the scrolled view (ScorePanel), post-validate.
        var contentOrigin = SwingUtilities.convertPoint(this, 0, 0, view);

        var newViewPosition = computeAnchoredViewPosition(
            anchorContentPoint,
            zoomRatio,
            contentOrigin,
            anchorViewportOffset,
            viewport.getViewSize(),
            extentSize
        );

        viewport.setViewPosition(newViewPosition);
    }

    /**
     * Computes the post-zoom scroll position that keeps {@code anchorContentPoint}
     * (a point in old-zoom ScoreView-content coordinates) under
     * {@code anchorViewportOffset} (a point in viewport-local coordinates).
     * <p>
     * Pure function of its arguments — no Swing component access — so it can be
     * unit-tested. Scales the content anchor by {@code zoomRatio}, offsets it by the
     * post-validate content origin within the scrolled view, subtracts the viewport
     * anchor offset, and clamps into {@code [0, viewSize - extentSize]} per axis.
     */
    static Point computeAnchoredViewPosition(
        Point anchorContentPoint,
        double zoomRatio,
        Point contentOrigin,
        Point anchorViewportOffset,
        Dimension viewSize,
        Dimension extentSize
    ) {
        var scaledAnchorX = anchorContentPoint.x * zoomRatio;
        var scaledAnchorY = anchorContentPoint.y * zoomRatio;

        var targetX = contentOrigin.x + scaledAnchorX - anchorViewportOffset.x;
        var targetY = contentOrigin.y + scaledAnchorY - anchorViewportOffset.y;

        var maxX = Math.max(0, viewSize.width - extentSize.width);
        var maxY = Math.max(0, viewSize.height - extentSize.height);

        var clampedX = (int) Math.round(Math.clamp(targetX, 0, maxX));
        var clampedY = (int) Math.round(Math.clamp(targetY, 0, maxY));

        return new Point(clampedX, clampedY);
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

    /**
     * Returns the song-wide lyric metrics, or null when StaffPanel has not populated them yet.
     * Unlike {@link #getLyricRenderMetrics()} this never exits: it is for callers that may run
     * before the first layout and can space a projection as if the line had no lyrics.
     */
    public @Nullable LyricRenderMetrics findLyricRenderMetrics() {
        return lyricRenderMetrics;
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

        lyricRenderMetrics = LyricRenderMetrics.forFont(lyricsFont);
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
     *
     * <h2>Z-order contract</h2>
     * Overlays paint above the score content and <b>below</b> the active lyric editor: a lyric
     * editor is a focused text field with a caret, and an overlay painting over it is a defect.
     * The ordering is a property of this container, not of the order things were registered in
     * — this method places every overlay directly beneath {@link #LYRIC_EDITOR_Z_ORDER}, and
     * {@link LyricEditor#openOn} reclaims that index when an editor opens, pushing the existing
     * overlays down by one.
     */
    @Override
    public void addOverlay(JComponent overlay) {
        add(overlay);

        var layout = (BorderLayout) getLayout();
        layout.removeLayoutComponent(overlay);

        if (mainPanel != null) {
            layout.addLayoutComponent(mainPanel, BorderLayout.CENTER);
        }

        // Index 0 is the topmost child; an open lyric editor owns it, so overlays start
        // immediately below it and above everything else.
        var occupiedByEditor = activeLyricEditor != null && activeLyricEditor != overlay;
        setComponentZOrder(overlay, occupiedByEditor ? LYRIC_EDITOR_Z_ORDER + 1 : LYRIC_EDITOR_Z_ORDER);
    }

    @Override
    public void removeOverlay(JComponent overlay) {
        var boundsPx = overlay.getBounds();
        remove(overlay);
        repaint(boundsPx);
    }

    @Override
    public JComponent getHostComponent() {
        return this;
    }

    /**
     * Notifies the overlays once this host's subtree is laid out.
     * <p>
     * Overlay bounds are fixed in staff spaces but not in pixels, so they go stale whenever a
     * line moves or resizes. The causes do not share a mechanism: a title or subtitle height
     * change moves StaffPanel without re-running its layout manager, while a line height change
     * re-runs it. {@code validateTree()} sits above both and is cause-independent by
     * construction. {@link Container#validateTree()} lays out this container and then recurses
     * into its children, so calling it first guarantees every descendant's bounds are final
     * before the overlays read them.
     * <p>
     * Only {@code setBounds}/{@code setLocation} may be called on the overlays from here —
     * never {@code revalidate()}, which would re-enter layout.
     */
    @Override
    protected void validateTree() {
        super.validateTree();

        forEachLineOverlay(LineOverlayComponent::updateBounds);
    }

    /**
     * Re-resolves every overlay's target line after the line components have been recreated.
     * <p>
     * A rebuilt layout discards the {@link LineComponent}s the overlays point at, and an
     * overlay holding a stale target hides itself. Managers retarget on mouse motion only, so
     * without this the preview would visibly wink out on every edit that rebuilds the layout
     * and stay hidden until the pointer moved.
     */
    private void retargetOverlays() {
        forEachLineOverlay(overlay -> {
            // retarget() ends in inkDidChange(), which recomputes the bounds itself.
            overlay.retarget();
        });
    }

    /**
     * Applies {@code action} to every {@link LineOverlayComponent} currently parented to this
     * view.
     * <p>
     * Derived from the children rather than from a list maintained by
     * {@link #addOverlay}/{@link #removeOverlay}, so it cannot disagree with the component
     * hierarchy — an overlay removed by any route simply stops being visited. Indexes rather
     * than {@link Container#getComponents()} because that returns a defensive copy, and this
     * runs from {@link #validateTree()} on every layout pass.
     */
    private void forEachLineOverlay(Consumer<LineOverlayComponent> action) {
        for (var i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof LineOverlayComponent overlay) {
                action.accept(overlay);
            }
        }
    }

}
