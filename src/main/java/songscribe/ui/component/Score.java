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
import java.awt.event.*;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Pair;
import org.xml.sax.SAXException;

import songscribe.MusicChangeListener;
import songscribe.io.CompositionIO;
import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.music.MusicEditOperations;
import songscribe.music.StaffElement;
import songscribe.prefs.Prefs;
import songscribe.ui.Constants;
import songscribe.ui.Control;
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
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.ui.message.ScoreMessageCoordinator;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.RenderContext;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.util.GraphicUtils;
import songscribe.util.Log;
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
    MusicChangeListener,
    RenderContext,
    ScoreMessageCoordinator.Callback,
    songscribe.ui.edit.ScoreActions {

    // The number of lines in a staff
    public static final int STAFF_LINE_COUNT = 5;

    // The vertical distance between whole tones on the staff (e.g. A to B)
    public static final float STAFF_POSITION_OFFSET_PX = (float) ScaleContext.getInstance().toPixels(LayoutStylesheet.STAFF_POSITION_OFFSET);

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

    // The frame that contains the score
    private final IMainFrame mainFrame;

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

    // Manages focus behavior to keep the score in focus
    private final ScoreFocusController focusController;

    // Navigates the component hierarchy
    private final ComponentHierarchyNavigator hierarchyNavigator;

    // Handles mouse and keyboard input
    private ScoreInputHandler inputHandler = null;

    // If true, the score is played with repeats
    private boolean playWithRepeats = false;

    public Score(@NotNull IMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        control = Control.valueOf(Prefs.getInstance().getString("control"));

        // Initialize focus controller
        focusController = new ScoreFocusController(this);

        // Initialize hierarchy navigator
        hierarchyNavigator = new ComponentHierarchyNavigator(this);

        selectionCoordinator = new SelectionCoordinator(this::getComposition);
        clipboardManager = new ClipboardManager();
        editModeManager = new EditModeManager(
            this::getComposition,
            mainFrame,
            clipboardManager,
            selectionCoordinator,
            this
        );

        // Initialize input handler
        inputHandler = new ScoreInputHandler(this, editModeManager);

        // Set layout for Phase 2 component hierarchy integration
        setLayout(new BorderLayout());

        try {
            saxParser = SAXParserFactory.newInstance().newSAXParser();
        } catch (Exception e) {
            mainFrame.showErrorMessage(
                Constants.PACKAGE_NAME +
                    " cannot start because of an initialization error."
            );
            System.exit(0);
        }

        setFocusable(true);
        addKeyListener(inputHandler);
    }

    public void createSVG(File outputFile, @NotNull Boolean isGUI) {
        songscribe.export.SVGExporter.createSVG(outputFile, isGUI);
    }

    public void init() {
        setName(ComponentNames.SCORE);

        // Create initial composition
        composition = new Composition(mainFrame);

        // Initialize UI components
        initView();
        initAdjustments();
        initMargin();
        initScorePanel();
        initMainPanel();

        setLineWidth((int) composition.getLineWidth());
        addMouseMotionListener(inputHandler);
        addMouseListener(inputHandler);
        addFocusListener(focusController);

        initEditPopup();
        selectionChanged();
        initKeys();

        // Initialize insertion note with default type
        setInsertionElement(editModeManager.makeInsertionElement());

        mainFrame.addMusicChangeListener(this);
        mainFrame.setDocumentModified(false);

        // Create operations and message coordinator (requires mainPanel to be set)
        operations = new MusicEditOperations(composition, selectionCoordinator, mainFrame);
        messageCoordinator = new ScoreMessageCoordinator(
            this,
            operations,
            editModeManager,
            selectionCoordinator,
            clipboardManager
        );
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
        scrollPane.setBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, UIManager.getColor("ToolBar.separatorColor"))
        );
        var backgroundColor = Color.lightGray;
        scrollPane.setBackground(backgroundColor);
        scrollPane.getViewport().setBackground(backgroundColor);
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


    public IMainFrame getMainFrame() {
        return mainFrame;
    }


    /**
     * Sets up selection provider and initial state for all LineComponents.
     * <p>
     * This wires up the selection checking from Score to enable
     * note coloring in the component-based rendering.
     */
    void setupLineComponentState() {
        hierarchyNavigator.setupLineComponentState(this, this);
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
        return focusController;
    }

    ScoreInputHandler getInputHandler() {
        return inputHandler;
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
        return hierarchyNavigator.getLineComponent(lineIndex);
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

    public boolean openFile(
        @NotNull IMainFrame mainFrame,
        File file,
        boolean setTitle
    ) {
        // Even though most of this code references the mainFrame, it is in this class
        // because this class is always the same whereas there are multiple implementations
        // of IMainFrame.
        var previousModifiedDocument = mainFrame.isDocumentModified();
        mainFrame.setDocumentModified(false);

        try {
            var reader = new CompositionIO.DocumentReader(mainFrame);
            saxParser.parse(file, reader);
            setComposition(reader.getComposition());
            mainFrame.setFrameSize();

            if (setTitle) {
                mainFrame.setCurrentFile(file);
            }

            return true;
        } catch (SAXException e) {
            var message =
                "Could not open the file “" +
                    file.getName() +
                    "” because it is damaged.";
            mainFrame.showErrorMessage(message);
            Log.error(message, e);
            mainFrame.setDocumentModified(previousModifiedDocument);
            return false;
        } catch (IOException e) {
            var message = "Could not open the file “" + file.getName() + '”';
            mainFrame.showErrorMessage(
                message + ". Check if you have the permission to open it."
            );
            Log.error(message, e);
            mainFrame.setDocumentModified(previousModifiedDocument);
            return false;
        }
    }

    // TODO: Use mbassador instead of this
    @Override
    public void musicDidChange() {
        var prefs = Prefs.getInstance();
        editModeManager.setPlayInsertedNote(prefs.getBoolean("playInsertedNote"));
        playWithRepeats = prefs.getBoolean("playWithRepeats");

        // Delegate playback settings to PlaybackController
        PlaybackController.setInstrument(prefs.getInt("instrument"));
        PlaybackController.setTempoChangePercent(prefs.getInt("tempoChangePercent"));
        PlaybackController.setNoteDurationPercent(prefs.getInt("playbackNoteDuration"));
        PlaybackController.setColorizeNotes(prefs.getBoolean("colorizeNote"));
        PlaybackController.setPlayWithRepeats(playWithRepeats);

        composition.musicChanged();
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
        hierarchyNavigator.updateLayoutFromComponents(layout -> {
            middleLineYPx = layout[0];
            rowHeightPx = layout[1];
        });
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

        // Initialize operations handler
        operations = new MusicEditOperations(composition, selectionCoordinator, mainFrame);

        // Initialize or update message coordinator
        if (messageCoordinator == null) {
            messageCoordinator = new ScoreMessageCoordinator(
                this,
                operations,
                editModeManager,
                selectionCoordinator,
                clipboardManager
            );
        } else {
            // Update operations reference for new composition
            messageCoordinator.setOperations(operations);
        }

        // Reset the playing state
        PlaybackController.stop();
        selectionCoordinator.clearSelection();
        setLineWidth((int) composition.getLineWidth());

        for (var i = 0; i < composition.lineCount(); i++) {
            drawWidthIfWiderLine(composition.getLine(i), true);
        }

        if (mainFrame.getLyricsModePanel() != null) {
            mainFrame.getLyricsModePanel().getData();
        }

        LyricsProcessor.spellLyrics(composition);

        // Update the MainPanel with the new composition
        if (mainPanel != null) {
            mainPanel.setComposition(composition);
            setupLineComponentState();
        }

        // Reset modified flag - initialization above is not a user change
        composition.setModified(false);

        // mainFrame.setMode(Mode.NOTE_EDIT);
        mainFrame.fireMusicChanged(null);
        viewChanged();
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
        return (int) composition.getLineWidth();
    }

    public int getSheetHeightPx() {
        // TODO: Calculate from component hierarchy
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

            if (
                line.getElement(line.elementCount() - 1).getXPosSs() >
                    (composition.getLineWidth() - idealSpace)
            ) {
                var firstX = line.getElement(0).getXPosSs();
                var ratio =
                    (composition.getLineWidth() - idealSpace - firstX) /
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
    public int getLeadingKeysPos() {
        return leadingKeysPosPx;
    }

    public void setLeadingKeysPosPx(int leadingKeysPosPx) {
        this.leadingKeysPosPx = leadingKeysPosPx;
    }

    @Override
    public int getRowHeight() {
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

    public void setLineWidth(int lineWidth) {
        composition.setLineWidth(lineWidth);
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
        focusController.allowFocusInComponent(component);
    }


    @NotNull
    public BufferedImage createImageForExport(
        Color background,
        double scale,
        @NotNull MyBorder border
    ) {
        return songscribe.export.ImageExporter.createImageForExport(
            background,
            scale,
            border
        );
    }

    public void createImageForExport(
        @NotNull BufferedImage image,
        Color background,
        double scale,
        @NotNull MyBorder border
    ) {
        songscribe.export.ImageExporter.createImageForExport(
            image,
            background,
            scale,
            border
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
