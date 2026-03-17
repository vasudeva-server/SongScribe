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
import java.util.Optional;
import java.util.function.Consumer;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Pair;
import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.Strings;
import songscribe.export.ExportOptions;
import songscribe.io.CompositionIO;
import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.music.MusicEditOperations;
import songscribe.music.StaffElement;
import songscribe.prefs.Prefs;
import songscribe.ui.Constants;
import songscribe.ui.Control;
import songscribe.ui.Dialogs;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.adjustment.HorizontalAdjustment;
import songscribe.ui.adjustment.LyricsAdjustment;
import songscribe.ui.adjustment.VerticalAdjustment;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.ScorePanel;
import songscribe.ui.dialog.LineWidthChangeDialog;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.LayoutConstants;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.menu.DebugState;
import songscribe.message.CompositionChangedMessage;
import songscribe.message.MessageCenter;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.ui.playback.PlaybackPrefsChangedMessage;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.RenderContext;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * This class is responsible for managing and drawing the music score
 * and its lyrics. It also handles user input for editing the score.
 */

public final class Score
    extends JComponent
    implements
    ComponentHierarchyProvider,
    FocusRestorationCallback,
    InputHandlerCallback,
    LineComponent.SelectionProvider,
    RenderContext,
    songscribe.ui.edit.ScoreActions {

    private static final Logger LOG = LoggerFactory.getLogger(Score.class);

    // The number of lines in a staff
    public static final int STAFF_LINE_COUNT = 5;

    // The vertical distance between whole tones on the staff (e.g. A to B)
    public static final float STAFF_POSITION_OFFSET_PX = (float) ScaleContext.getInstance().toPixels(LayoutStylesheet.STAFF_POSITION_OFFSET_SS);

    // The content width and height in inches, excluding page margins
    public static final float PAGE_CONTENT_WIDTH = 7;
    public static final float PAGE_CONTENT_HEIGHT = 9.5f;
    public static final Dimension PAGE_CONTENT_SIZE = new Dimension(
        GraphicUtils.Unit.INCH.convertToPixels(PAGE_CONTENT_WIDTH),
        GraphicUtils.Unit.INCH.convertToPixels(PAGE_CONTENT_HEIGHT)
    );

    // The page margin in dpi
    public static final int PAGE_MARGIN_PX = 80;

    // Delay in milliseconds for debouncing repaint when layout changes occur
    private static final int REPAINT_DEBOUNCE_DELAY_MS = 300;

    // Colors used to draw the music score in various states
    public static final Color PLAYING_NOTE_COLOR = new Color(31, 204, 0);
    public static final Color INSERTION_NOTE_COLOR = new Color(3, 136, 255);

    public static final Color SELECTION_STROKE_COLOR = Color.magenta;

    // The maximum number of staff lines under a note that can be displayed above and below the staff.
    // The range of notes supported is C3 (3 lines below) to F6 (4 lines above).
    public static final int STAFF_LINES_ABOVE = 3;
    public static final int STAFF_LINES_BELOW = 4;

    // Edit popup
    private JPopupMenu popup = null;

    private SAXParser saxParser = null;
    private Dimension sheetSize = new Dimension();

    private HorizontalAdjustment horizontalAdjustment = null;
    private VerticalAdjustment verticalAdjustment = null;
    private LyricsAdjustment lyricsAdjustment = null;

    // Called when a file is successfully opened (e.g. to update the window title)
    @Nullable private final Consumer<File> onFileOpened;

    // The current editing mode
    private Mode mode = Mode.EDIT;

    // Whether editing is done via mouse or keyboard
    private Control control;

    // In some contexts (such as playback), we don't want to allow dragging
    private boolean dragDisabled = false;

    // The model for the score
    private Composition composition = null;

    // The scroll pane that contains the score + margin
    private JScrollPane scrollPane = null;

    // Inside the scroll pane is a panel that provides the margin around the score
    private JPanel marginPanel = null;

    // The score itself
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
    private SelectionCoordinator selectionCoordinator = null;

    // Handles music editing operations
    private MusicEditOperations operations = null;

    // Manages clipboard state for copy/paste operations
    private ClipboardManager clipboardManager = null;

    // Manages edit mode state (insertion note and position)
    private EditModeManager editModeManager = null;

    // New JComponent-based score panel (Phase 2 hierarchy)
    private MainPanel mainPanel = null;

    // Coordinates message handling
    private ScoreMessageCoordinator messageCoordinator = null;

    // Preferred size of the score panel
    private final Dimension preferredSize = new Dimension();

    // Preferred size of the margin panel
    private final Dimension preferredSizeWithMargin = new Dimension();

    // Manages focus behavior to keep the score in focus (empty in headless mode)
    private final Optional<ScoreFocusController> focusController;

    // Navigates the component hierarchy (empty in headless mode)
    private final Optional<ComponentHierarchyNavigator> hierarchyNavigator;

    // Handles mouse and keyboard input (empty in headless mode)
    private final Optional<ScoreInputHandler> inputHandler;

    // True after init() has been called (interactive mode only)
    private boolean initialized = false;

    // If true, the score is played with repeats
    private boolean playWithRepeats = false;

    /**
     * Creates a Score with core infrastructure (SAX parser, selection, clipboard,
     * edit mode). This is sufficient for headless use (converters pass {@code null}).
     * <p>
     * For interactive use, call {@link #init()} after construction to create the
     * UI components (view, panels, message coordinator) and the initial Composition.
     *
     * @param onFileOpened callback invoked when a file is successfully opened,
     *                     or {@code null} for headless (converter) use
     */
    public Score(@Nullable Consumer<File> onFileOpened) {
        this.onFileOpened = onFileOpened;
        var headless = onFileOpened == null;
        control = Control.valueOf(Prefs.getInstance().getString("control"));

        selectionCoordinator = new SelectionCoordinator(this::getComposition);
        clipboardManager = new ClipboardManager();
        editModeManager = new EditModeManager(
            this::getComposition,
            clipboardManager,
            selectionCoordinator,
            this
        );

        try {
            saxParser = SAXParserFactory.newInstance().newSAXParser();
        } catch (Exception e) {
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_INITIALIZATION_ERROR),
                Constants.PACKAGE_NAME +
                    " cannot start because of an initialization error."
            );
            System.exit(0);
        }

        if (headless) {
            focusController = Optional.empty();
            hierarchyNavigator = Optional.empty();
            inputHandler = Optional.empty();
        } else {
            focusController = Optional.of(new ScoreFocusController(this));
            hierarchyNavigator = Optional.of(new ComponentHierarchyNavigator(this));
            var handler = new ScoreInputHandler(this, editModeManager);
            inputHandler = Optional.of(handler);
            setLayout(new BorderLayout());
            setFocusable(true);
            addKeyListener(handler);
        }
    }

    public void createSVG(File outputFile) {
        songscribe.export.SVGExporter.createSVG(outputFile);
    }

    /**
     * Initializes the interactive UI: view, panels, message coordinator,
     * and the initial Composition. Must be called exactly once after construction
     * for interactive (non-converter) use. Not needed for headless converters.
     */
    public void init() {
        setName(ComponentNames.SCORE);

        // Create initial composition
        composition = new Composition();

        // Initialize UI components
        initView();
        initAdjustments();
        initMargin();
        initScorePanel();
        initMainPanel();

        setLineWidthPx(ScaleContext.getInstance().toRoundedPixels(composition.getLineWidthSs()));
        inputHandler.ifPresent(h -> { addMouseMotionListener(h); addMouseListener(h); });
        focusController.ifPresent(this::addFocusListener);

        initEditPopup();
        selectionChanged();
        initKeys();

        // Initialize insertion note with default type
        setInsertionElement(editModeManager.makeInsertionElement());

        MessageCenter.subscribe(this);
        syncPlaybackPrefs();
        composition.setModified(false);

        // Create operations and message coordinator (requires mainPanel to be set)
        operations = new MusicEditOperations(composition, selectionCoordinator);
        messageCoordinator = new ScoreMessageCoordinator(
            this,
            operations,
            editModeManager,
            selectionCoordinator,
            clipboardManager
        );

        initialized = true;
    }

    private void initKeys() {
        var keyCodes = new int[]{
            KeyEvent.VK_UP,
            KeyEvent.VK_DOWN,
            KeyEvent.VK_LEFT,
            KeyEvent.VK_RIGHT,
            KeyEvent.VK_PAGE_UP,
            KeyEvent.VK_PAGE_DOWN,
            KeyEvent.VK_ENTER,
        };

        for (var keyCode : keyCodes) {
            var o = new Object();
            getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(keyCode, 0),
                o
            );
            getActionMap().put(
                o,
                new ScoreInputHandler.KeyAction(this, editModeManager, keyCode)
            );
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
        scorePanel = new ScorePanel(marginPanel);
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
        var color = UIManager.getColor(ScorePanel.BACKGROUND_KEY);

        if (color == null) {
            color = Color.LIGHT_GRAY;
        }

        scrollPane.setBackground(color);
        scrollPane.getViewport().setBackground(color);
    }

    private void initMainPanel() {
        mainPanel = new MainPanel();
        mainPanel.setComposition(composition);
        mainPanel.setVisible(true);
        add(mainPanel, BorderLayout.CENTER);
        setupLineComponentState();
    }

    public static boolean defaultUpperNote(@NotNull StaffElement note) {
        return (note.getStaffPosition() > 0) || note.getType().isGraceNote();
    }

    /**
     * Sets up selection provider and initial state for all LineComponents.
     * <p>
     * This wires up the selection checking from Score to enable
     * note coloring in the component-based rendering.
     */
    void setupLineComponentState() {
        hierarchyNavigator.ifPresent(nav -> nav.setupLineComponentState(this, this));
    }

    /**
     * Returns the MainPanel for the new component hierarchy.
     * <p>
     * This is the top-level panel for the Phase 2 JComponent-based rendering.
     */
    public MainPanel getMainPanel() {
        return mainPanel;
    }

    void setMainPanel(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
    }

    JPanel getMarginPanel() {
        return marginPanel;
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

    ScoreFocusController getFocusController() {
        return focusController.orElseThrow();
    }

    ScoreInputHandler getInputHandler() {
        return inputHandler.orElseThrow();
    }

    EditModeManager getEditModeManager() {
        return editModeManager;
    }

    public boolean isDragDisabled() {
        return dragDisabled;
    }

    @Override
    public JPopupMenu getEditPopup() {
        return popup;
    }

    /**
     * Returns the LineComponent for the given line index, or null if not found.
     */
    @Nullable
    public LineComponent getLineComponent(int lineIndex) {
        return hierarchyNavigator
            .map(nav -> nav.getLineComponent(lineIndex))
            .orElse(null);
    }

    void initMargin() {
        // The margin between the composition and the edge of the page
        marginPanel = new JPanel();
        marginPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        marginPanel.setBackground(LayoutStylesheet.SCORE_BACKGROUND);
        marginPanel.add(this);
    }

    void initAdjustments() {
        horizontalAdjustment = new HorizontalAdjustment(this);
        verticalAdjustment = new VerticalAdjustment(this);
        lyricsAdjustment = new LyricsAdjustment(this);
    }

    void initView() {
        var width = (int) Math.round(
            LineWidthChangeDialog.MAX_LINE_WIDTH_IN_INCHES * UIUtils.RESOLUTION
        );

        viewChanged();
    }

    public boolean openFile(File file, boolean updateCurrentFile) {
        var previousModified = composition != null && composition.isModified();

        if (composition != null) {
            composition.setModified(false);
        }

        try {
            var reader = new CompositionIO.DocumentReader();
            saxParser.parse(file, reader);
            var newComposition = reader.getComposition();
            setComposition(newComposition);

            if (updateCurrentFile && onFileOpened != null) {
                onFileOpened.accept(file);
            }

            LOG.info("Composition loaded: {}", file.getName());
            return true;
        } catch (SAXException e) {
            var message =
                "Could not open the file “" +
                    file.getName() +
                    "” because it is damaged.";
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                message
            );
            LOG.error(message, e);
            if (composition != null) {
                composition.setModified(previousModified);
            }
            return false;
        } catch (IOException e) {
            var message = "Could not open the file “" + file.getName() + '”';
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_FILE_ERROR),
                message + ". Check if you have the permission to open it."
            );
            LOG.error(message, e);
            if (composition != null) {
                composition.setModified(previousModified);
            }
            return false;
        }
    }

    public void syncPlaybackPrefs() {
        var prefs = Prefs.getInstance();
        editModeManager.setPlayInsertedNote(prefs.getBoolean("playInsertedNote"));
        playWithRepeats = prefs.getBoolean("playWithRepeats");

        // Delegate playback settings to PlaybackController
        PlaybackController.setInstrument(prefs.getInt("instrument"));
        PlaybackController.setTempoChangePercent(prefs.getInt("tempoChangePercent"));
        PlaybackController.setNoteDurationPercent(prefs.getInt("playbackNoteDuration"));
        PlaybackController.setColorizeNotes(prefs.getBoolean("colorizeNote"));
        PlaybackController.setPlayWithRepeats(playWithRepeats);
    }

    @Handler
    public void onPlaybackPrefsChanged(PlaybackPrefsChangedMessage message) {
        syncPlaybackPrefs();
    }

    public void viewChanged() {
        // Clear inspector hover since layout bounds will be recalculated
        var needsImmediateRepaint = false;

        if (DebugState.isInspectorEnabled() && DebugState.getHoveredElement() != null) {
            DebugState.setHoveredElement(null);
            needsImmediateRepaint = true;
        }

        // Component hierarchy handles layout automatically
        updateLayoutFromComponents();

        // Force immediate repaint to clear stale inspector visualization
        if (needsImmediateRepaint) {
            repaint();
        }
    }

    /**
     * Updates middleLineY and rowHeight from component hierarchy.
     * <p>
     * Derives layout coordinates from the actual positioned components
     * rather than a separate layout manager.
     */
    private void updateLayoutFromComponents() {
        hierarchyNavigator.ifPresent(nav ->
            nav.updateLayoutFromComponents(layout -> {
                middleLineYPx = layout[0];
                rowHeightPx = layout[1];
            })
        );
    }

    public JScrollPane getScoreScrollPane() {
        return scrollPane;
    }

    @Override
    public boolean isElementSelected(int xIndex, int line) {
        return selectionCoordinator.isElementSelected(xIndex, line);
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
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        var g2 = (Graphics2D) g;

        try (var ignored = songscribe.ui.renderer.GraphicsState.save(
            g2,
            songscribe.ui.renderer.GraphicsState.Property.COLOR
        )) {
            g2.setColor(LayoutStylesheet.SCORE_BACKGROUND);
            g2.fillRect(0, 0, marginPanel.getWidth(), marginPanel.getHeight());
        }

        // Derive coordinates from positioned components
        updateLayoutFromComponents();

        drawEditElements(g2);
        drawDebugOverlays(g2);
    }

    private void drawEditElements(Graphics2D g2) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (mode == Mode.EDIT) {
            // Insertion note rendering is now handled by LineComponent
        } else if (mode == Mode.ADJUSTMENT) {
            horizontalAdjustment.repaint(g2);
        } else if (mode == Mode.VERTICAL_ADJUSTMENT) {
            verticalAdjustment.repaint(g2);
        } else if (mode == Mode.LYRICS_ADJUSTMENT) {
            lyricsAdjustment.repaint(g2);
        }
    }

    private void drawDebugOverlays(Graphics2D g2) {
        // TODO: Implement debug overlays with component hierarchy
        // debugRenderer.drawDebugVisualization(g2, layoutResult, this);
    }

    @Override
    public int getNoteYPosPx(int staffPosition, int line) {
        return (int) (middleLineYPx +
            (staffPosition * STAFF_POSITION_OFFSET_PX) +
            (line * rowHeightPx));
    }

    @Override
    public int getUnderLyricsYPosPx() {
        // TODO: Calculate from component hierarchy
        return 0;
    }

    public StaffElement getInsertionElement() {
        return editModeManager.getInsertionElement();
    }

    public void setInsertionElement(@Nullable StaffElement insertionElement) {
        if (insertionElement != null) {
            var currentInsertionElement = editModeManager.getInsertionElement();

            if (currentInsertionElement != null) {
                insertionElement.setStaffPosition(currentInsertionElement.getStaffPosition());
                insertionElement.setXPosSs(currentInsertionElement.getXPosSs());
            } else {
                editModeManager.setInsertionElement(insertionElement);
            }

            insertionElement.setUpper(defaultUpperNote(insertionElement));
        }

        editModeManager.setInsertionElement(insertionElement);
        repaint();
    }

    @Override
    public boolean isShowLayoutBoxes() {
        return DebugState.isShowLayoutBoxes();
    }

    @Override
    public boolean isShowBoundingBoxes() {
        return DebugState.isShowBoundingBoxes();
    }

    @Override
    public boolean isShowMargins() {
        return DebugState.isShowMargins();
    }

    public void clearSelection() {
        selectionCoordinator.clearSelection();
        selectionChanged();
    }

    public void selectionChanged() {
        MessageCenter.post(new MusicSelectionChangedMessage(this));
    }

    public int getSelectionSize() {
        return selectionCoordinator.getSelectionSize();
    }

    public boolean canToggleBeaming() {
        return operations.canToggleBeaming();
    }

    public boolean canToggleTie() {
        return operations.canToggleTie();
    }

    @NotNull
    @Contract(" -> new")
    public Pair<Boolean, Boolean> canToggleTuplet() {
        return operations.canToggleTuplet();
    }

    public boolean canRemoveDynamicsFromSelection() {
        return operations.canRemoveDynamicsFromSelection();
    }

    public boolean canMakeFirstSecondEnding() {
        return operations.canMakeFirstSecondEnding();
    }

    public boolean canChangeTempo() {
        return operations.canChangeTempo();
    }

    public boolean canToggleTrill() {
        return operations.canToggleTrill();
    }

    public boolean canToggleLyricsUnderRests() {
        return operations.canToggleLyricsUnderRests();
    }

    public boolean canFlipStemDirection() {
        return operations.canFlipStemDirection();
    }

    @Override
    public Composition getComposition() {
        return composition;
    }

    public void setComposition(Composition composition) {
        this.composition = composition;

        if (composition == null) {
            return;
        }

        var lineWidthPx = ScaleContext.getInstance().toRoundedPixels(composition.getLineWidthSs());

        // Core setup needed for both headless and interactive modes
        setLineWidthPx(lineWidthPx);

        for (var i = 0; i < composition.lineCount(); i++) {
            drawWidthIfWiderLine(composition.getLine(i), true);
        }

        LyricsProcessor.spellLyrics(composition);
        composition.setModified(false);

        if (!initialized) {
            return;
        }

        // Interactive-only setup below

        operations = new MusicEditOperations(composition, selectionCoordinator);

        if (messageCoordinator == null) {
            messageCoordinator = new ScoreMessageCoordinator(
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

        mainPanel.setComposition(composition);
        setupLineComponentState();

        syncPlaybackPrefs();
        viewChanged();

        // Notify all subscribers (LyricsPanel, ScoreMessageCoordinator, UIActions, etc.)
        // that the composition has been fully replaced. This must happen after all
        // Score state is consistent.
        MessageCenter.post(new CompositionChangedMessage(
            CompositionChangedMessage.ChangeType.FULL, composition
        ));

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
        return ScaleContext.getInstance().toRoundedPixels(composition.getLineWidthSs());
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
    public int getSheetHeightPx(@NotNull ExportOptions options) {
        // TODO: Calculate height based on options without relying on component layout.
        // For now, returns full height since rendering is not yet implemented.
        return getHeight();
    }

    public void drawWidthIfWiderLine(@NotNull Line line, boolean strict) {
        if (line.elementCount() > 1) {
            var endNote = line.getElement(line.elementCount() - 1);
            float idealSpace;

            if (strict) {
                idealSpace = (float) endNote.getContentWidth();
            } else {
                idealSpace = (float) ScaleContext.getInstance().toPixels(LayoutConstants.DEFAULT_COLUMN_GAP_SS) + 20;
            }

            // Note: getXPosSs() is a legacy misnomer — it stores pixels.
            // Use getLineWidthPx() to compare in the same unit.
            var lineWidthPx = composition.getLineWidthPx();

            if (
                line.getElement(line.elementCount() - 1).getXPosSs() >
                    (lineWidthPx - idealSpace)
            ) {
                var firstX = line.getElement(0).getXPosSs();
                var ratio =
                    (lineWidthPx - idealSpace - firstX) /
                        (endNote.getXPosSs() - firstX);

                for (var i = 1; i < line.elementCount(); i++) {
                    var note = line.getElement(i);
                    note.setXPosSs(
                        (int) (firstX + Math.round((note.getXPosSs() - firstX) * ratio))
                    );
                }

                line.mulElementDistChange((float) ratio);
            }
        }
    }

    public SelectionCoordinator getSelectionCoordinator() {
        return selectionCoordinator;
    }

    public void setInSelectMode(boolean inSelectMode) {
        selectionCoordinator.setInSelectMode(inSelectMode);
    }

    public Control getControl() {
        return control;
    }

    public void setControl(Control control) {
        this.control = control;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public HorizontalAdjustment getHorizontalAdjustment() {
        return horizontalAdjustment;
    }

    public VerticalAdjustment getVerticalAdjustment() {
        return verticalAdjustment;
    }

    public LyricsAdjustment getLyricsAdjustment() {
        return lyricsAdjustment;
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
        Prefs.getInstance().put("control", control.name());
    }

    public void setLineWidthPx(int lineWidthPx) {
        composition.setLineWidthSs(ScaleContext.getInstance().fromPixels(lineWidthPx));
        var lineWidth = lineWidthPx;
        preferredSize.width = lineWidth;
        preferredSize.height = Math.round(
            (lineWidth > PAGE_CONTENT_SIZE.width)
                ? (lineWidth * (PAGE_CONTENT_HEIGHT / PAGE_CONTENT_WIDTH))
                : PAGE_CONTENT_SIZE.height
        );
        setPreferredSize(preferredSize);

        if (marginPanel != null) {
            var width = Math.max(lineWidth, PAGE_CONTENT_SIZE.width);
            preferredSizeWithMargin.width = width + PAGE_MARGIN_PX;
            preferredSizeWithMargin.height = preferredSize.height + PAGE_MARGIN_PX;
            marginPanel.setPreferredSize(preferredSizeWithMargin);
            invalidate();
            marginPanel.invalidate();
            scorePanel.invalidate();
            scrollPane.validate();
            repaint();
        }
    }

    @Override
    public int getSelectedLine() {
        return selectionCoordinator.getSelectedLine();
    }

    public int getPlayingLine() {
        // Playback state is now managed by PlaybackController
        // For now, return -1 (not playing) since LineComponents handle their own state
        return -1;
    }

    public int getPlayingNote() {
        // Playback state is now managed by PlaybackController
        // For now, return -1 (not playing) since LineComponents handle their own state
        return -1;
    }

    @Nullable
    public ElementSelection getSelection() {
        return selectionCoordinator.getSelection();
    }

    public void allowFocusInComponent(Component component) {
        focusController.ifPresent(fc -> fc.allowFocusInComponent(component));
    }

    @NotNull
    public BufferedImage createImageForExport(
        Color background,
        double scale,
        @NotNull MyBorder border,
        @NotNull ExportOptions options
    ) {
        return songscribe.export.ImageExporter.createImageForExport(
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

}
