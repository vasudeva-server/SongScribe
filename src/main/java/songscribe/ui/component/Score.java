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
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.IntStream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Pair;
import net.engio.mbassy.listener.Handler;
import org.jfree.svg.SVGGraphics2D;
import org.jfree.svg.SVGUtils;
import org.xml.sax.SAXException;

import songscribe.MusicChangeListener;
import songscribe.data.Interval;
import songscribe.data.IntervalSet;
import songscribe.data.TupletIntervalData;
import songscribe.io.CompositionIO;
import songscribe.music.Composition;
import songscribe.music.Crotchet;
import songscribe.music.ForceArticulation;
import songscribe.music.GraceSemiQuaver;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.music.RepeatLeftRight;
import songscribe.ui.Constants;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.adjustment.HorizontalAdjustment;
import songscribe.ui.adjustment.LyricsAdjustment;
import songscribe.ui.adjustment.VerticalAdjustment;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.dialog.LineWidthChangeDialog;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.FontBoundsProvider;
import songscribe.ui.layout.FughettaFontBoundsProvider;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.NoteSpacing;
import songscribe.ui.layout.SectionLayout;
import songscribe.ui.menu.DebugState;
import songscribe.ui.message.AddDynamicsMessage;
import songscribe.ui.message.ControlChangedMessage;
import songscribe.ui.message.DeselectMessage;
import songscribe.ui.message.FirstSecondEndingMessage;
import songscribe.ui.message.FlipPartialBeamsMessage;
import songscribe.ui.message.FlipStemDirectionMessage;
import songscribe.ui.message.InsertLineMessage;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.Message;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.ui.message.NewFileMessage;
import songscribe.ui.message.NoteTypeSelectedMessage;
import songscribe.ui.message.PasteboardOpMessage;
import songscribe.ui.message.RemoveDynamicsMessage;
import songscribe.ui.message.RestModeChangedMessage;
import songscribe.ui.message.ToggleBeamMessage;
import songscribe.ui.message.ToggleLyricsUnderRestsMessage;
import songscribe.ui.message.ToggleTieMessage;
import songscribe.ui.message.ToggleTrillMessage;
import songscribe.ui.message.ToggleTupletMessage;
import songscribe.ui.message.UpdateEditNoteMessage;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlayNoteThread;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.playback.PlaybackStateChangedMessage;
import songscribe.ui.playback.PlaybackStateManager;
import songscribe.ui.renderer.ElementRenderContext;
import songscribe.ui.renderer.GlissandoRenderer;
import songscribe.ui.renderer.NoteRenderer;
import songscribe.ui.renderer.RenderContext;
import songscribe.ui.selection.NoteSelection;
import songscribe.ui.selection.SelectionManager;
import songscribe.ui.selection.TieContext;
import songscribe.util.FileUtils;
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
    FocusListener,
    KeyListener,
    MetaEventListener,
    MouseListener,
    MouseMotionListener,
    MusicChangeListener,
    RenderContext {

    // The number of lines in a staff
    public static final int STAFF_LINE_COUNT = 5;

    // The vertical distance between staff line y positions
    public static final int STAFF_LINE_Y_OFFSET = 8;

    // The vertical distance between whole tones on the staff (e.g. A to B)
    public static final float NOTE_Y_OFFSET = (float) STAFF_LINE_Y_OFFSET / 2;

    // The content width and height in inches, excluding page margins
    public static final float PAGE_CONTENT_WIDTH = 7;
    public static final float PAGE_CONTENT_HEIGHT = 9.5f;
    public static final Dimension PAGE_CONTENT_SIZE = new Dimension(
        GraphicUtils.Unit.INCH.convertToPixels(PAGE_CONTENT_WIDTH),
        GraphicUtils.Unit.INCH.convertToPixels(PAGE_CONTENT_HEIGHT)
    );

    // The page margin in dpi
    public static final int PAGE_MARGIN = 80;

    // Delay in milliseconds for debouncing repaint when layout changes occur
    private static final int REPAINT_DEBOUNCE_DELAY_MS = 300;

    // Colors used to draw the music score in various states
    public static final Color PLAYING_NOTE_COLOR = new Color(31, 204, 0);
    public static final Color SELECTION_STROKE_COLOR = Color.magenta;

    // The color used to draw the edit "note", which can actually be any music element that can be
    // placed on a staff line. The edit note shows where the next edit will occur.
    private static final Color EDIT_NOTE_COLOR = new Color(3, 136, 255);

    // The stroke used to draw the selection rectangle border
    private static final BasicStroke SELECTION_RECT_STROKE = new BasicStroke(
        2.0f
    );

    // Use a transparent version of the selection stroke color for the selection fill color
    private static final Color SELECTION_RECT_FILL_COLOR = new Color(
        SELECTION_STROKE_COLOR.getRed(),
        SELECTION_STROKE_COLOR.getGreen(),
        SELECTION_STROKE_COLOR.getBlue(),
        8
    );

    // The maximum number of staff lines under a note that can be displayed above and below the staff.
    // The range of notes supported is C3 (3 lines below) to F6 (4 lines above).
    public static final int STAFF_LINES_ABOVE = 3;
    public static final int STAFF_LINES_BELOW = 4;

    // The maximum angle of a beam. If the beamed notes would create a beam with a greater angle,
    // the length of note stems is adjusted to make the beam angle <= this value.
    private static final double MAX_BEAM_ANGLE = 0.4;

    // When the edit note is before the first note in a line, it is placed this many pixels to the left
    // of the first note.
    private static final int FIRST_NOTE_IN_LINE_MOVEMENT = -15;

    // The number of pulses per quarter note (ticks per beat), used to calculate the duration of notes
    // when playing back the score or generating a MIDI file.
    public static final int PPQ = 96;

    // The duration of a grace note in ticks, effectively the same as a 32nd note
    public static final int GRACE_QUAVER_DURATION = PPQ / 8;

    // The MIDI velocity values for normal and accented notes
    public static final int NOTE_VELOCITY = 98;
    public static final int ACCENTED_NOTE_VELOCITY = 127;

    // Used when calculating tuplet note durations
    private static final double LOG2 = Math.log(2);

    // Edit popup
    private JPopupMenu popup = null;
    private Control prevPasteControl = null;

    private SAXParser saxParser = null;
    private Dimension sheetSize = new Dimension();

    private boolean playInsertingNote = true;

    private HorizontalAdjustment horizontalAdjustment = null;
    private VerticalAdjustment verticalAdjustment = null;
    private LyricsAdjustment lyricsAdjustment = null;

    // The frame that contains the score
    private final IMainFrame mainFrame;

    // The current editing mode
    private Mode mode = Mode.NOTE_EDIT;

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
    private int leadingKeysPos = 32;

    // The vertical distance between the top of one staff line and the next.
    // This can vary depending on what appears above and below the staff line,
    // as well as vertical adjustments made by the user.
    private int rowHeight = 0;

    // The y position (from the top of the scorePanel) of the middle line (B) of the first staff
    private int middleLineY = 0;

    // Manages selection state
    private SelectionManager selectionManager = null;

    // Manages playback state
    private PlaybackStateManager playbackStateManager = null;

    // Manages clipboard state for copy/paste operations
    private ClipboardManager clipboardManager = null;

    // Manages edit mode state (edit note and position)
    private EditModeManager editModeManager = null;

    // New JComponent-based score panel (Phase 2 hierarchy)
    private MainPanel mainPanel = null;

    // Feature flag for Phase 2: enables component-based rendering
    // When false, uses legacy ScoreRenderer; when true, uses MainPanel hierarchy
    private boolean useComponentRendering = true;

    // Timer for debouncing repaints when layout changes occur
    private Timer repaintDebounceTimer = null;

    // Preferred size of the score panel
    private final Dimension preferredSize = new Dimension();

    // Preferred size of the margin panel
    private final Dimension preferredSizeWithMargin = new Dimension();

    // Except for clicks on a few components such as lyrics fields, we don't want
    // the score to lose focus when other parts of the UI are clicked. When the score
    // loses focus, if the component gaining focus is not in this list, a thread is
    // started which gives focus back to the score.
    private final ArrayList<Component> componentsAllowedToGainFocus =
        new ArrayList<>();

    // True if the Shift key is pressed, which temporarily switches to select mode
    private boolean shiftPressed = false;

    // Sequence used to play the score
    private Sequence sequence = null;

    // The number of ticks to hold a quarter note during playback, ranging from 33-100 (staccato to legato)
    private int playbackNoteDuration = 0;

    // If true, the score is played with repeats
    private boolean playWithRepeats = false;

    // The MIDI instrument used to play the score
    private int instrument = 0;
    private int manualTempoChange = 0;
    private boolean colorizeNote = false;

    @SuppressWarnings("deprecation")
    public Score(@NotNull IMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        var property = mainFrame
            .getProperties()
            .getProperty(Constants.CONTROL_PROP);
        control = (property != null)
            ? Control.valueOf(property)
            : Control.MOUSE;

        // Initialize font bounds provider for measurement calculations
        FontBoundsProvider fontBoundsProvider = new FughettaFontBoundsProvider(
            this
        );

        selectionManager = new SelectionManager(this::getComposition);
        playbackStateManager = new PlaybackStateManager();
        clipboardManager = new ClipboardManager();
        editModeManager = new EditModeManager();

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
        addKeyListener(this);
        MessageCenter.subscribe(this);
    }

    public void createSVG(File outputFile, @NotNull Boolean isGUI) {
        // SVG export not yet implemented with component-based rendering
        if (isGUI) {
            mainFrame.showErrorMessage(
                "SVG export is not yet implemented. " +
                "Export functionality will be restored in a future update."
            );
        } else {
            System.err.println("ERROR: SVG export is not yet implemented");
        }
    }

    public void init() {
        composition = new Composition(mainFrame);
        initView();
        initAdjustments();
        initMargin();
        initScorePanel();
        initMainPanel();

        setLineWidth(composition.getLineWidth());
        addMouseMotionListener(this);
        addMouseListener(this);
        addFocusListener(this);

        initEditPopup();
        selectionChanged();
        initKeys();

        mainFrame.addMusicChangeListener(this);

        if (MidiController.sequencer != null) {
            MidiController.sequencer.addMetaEventListener(this);
        }

        mainFrame.setDocumentModified(false);
    }

    public static boolean defaultUpperNote(@NotNull Note note) {
        return (
            (note.getYPos() >= 0) ||
                note.getNoteType().isGraceNote() ||
                (note.getNoteType() == NoteType.GRACE_SEMIQUAVER_EDIT_STEP1)
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
            getActionMap().put(o, new KeyAction(keyCode));
        }
    }

    private void initEditPopup() {
        // Edit popup menu
        popup = new JPopupMenu();
        popup.add(Actions.CUT_ACTION);
        popup.add(Actions.COPY_ACTION);
        popup.add(Actions.PASTE_ACTION);
        popup.addSeparator();
        popup.add(Actions.DELETE_ACTION);
    }

    private void initScorePanel() {
        // The container for the score
        scorePanel = new ScorePanel(marginPanel);
        scrollPane = new JScrollPane(scorePanel);
        scrollPane.setBorder(
            BorderFactory.createMatteBorder(
                1,
                0,
                1,
                0,
                UIManager.getColor("ToolBar.separatorColor")
            )
        );
        var backgroundColor = Color.lightGray;
        scrollPane.setBackground(backgroundColor);
        scrollPane.getViewport().setBackground(backgroundColor);
    }

    /**
     * Initializes the new JComponent-based MainPanel.
     * <p>
     * The MainPanel is created and added to Score's layout (BorderLayout.CENTER).
     * It is initially invisible; rendering is controlled by the useComponentRendering flag.
     */
    private void initMainPanel() {
        mainPanel = new MainPanel();
        mainPanel.setComposition(composition);

        // Wire up selection provider for note coloring
        setupLineComponentState();

        // Add MainPanel to Score's layout hierarchy
        // Start invisible - will be shown when useComponentRendering is enabled
        mainPanel.setVisible(false);
        add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Sets up selection provider and initial state for all LineComponents.
     * <p>
     * This wires up the selection checking from Score to enable
     * note coloring in the component-based rendering.
     */
    private void setupLineComponentState() {
        if (mainPanel == null) {
            return;
        }

        var staffPanel = mainPanel.getStaffPanel();

        if (staffPanel == null) {
            return;
        }

        for (var linePanel : staffPanel.getLinePanels()) {
            var lineComponent = linePanel.getLineComponent();
            lineComponent.setSelectionProvider(this::isNoteSelected);
        }
    }

    /**
     * Updates playback state on LineComponents when playback position changes.
     *
     * @param playingLine The line index being played (-1 if not playing)
     * @param playingNote The note index being played (-1 if not playing)
     */
    private void updateLineComponentPlaybackState(int playingLine, int playingNote) {
        if (mainPanel == null) {
            return;
        }

        var staffPanel = mainPanel.getStaffPanel();

        if (staffPanel == null) {
            return;
        }

        for (var linePanel : staffPanel.getLinePanels()) {
            var lineComponent = linePanel.getLineComponent();
            var lineIndex = lineComponent.getLineIndex();

            // Set playing note index only for the currently playing line
            if (lineIndex == playingLine) {
                lineComponent.setPlayingNoteIndex(playingNote);
            } else {
                lineComponent.setPlayingNoteIndex(-1);
            }
        }
    }

    /**
     * Returns the MainPanel for the new component hierarchy.
     * <p>
     * This is the top-level panel for the Phase 2 JComponent-based rendering.
     */
    public MainPanel getMainPanel() {
        return mainPanel;
    }

    /**
     * Returns whether component-based rendering is enabled.
     * <p>
     * When true, uses the new JComponent hierarchy (MainPanel).
     * When false, rendering is disabled (exports not yet implemented).
     */
    public boolean isUseComponentRendering() {
        return useComponentRendering;
    }

    /**
     * Sets whether component-based rendering is enabled.
     * <p>
     * This feature flag controls whether the JComponent hierarchy is used.
     * When disabled, rendering is unavailable.
     *
     * @param useComponentRendering true to use component rendering
     */
    public void setUseComponentRendering(boolean useComponentRendering) {
        if (this.useComponentRendering != useComponentRendering) {
            this.useComponentRendering = useComponentRendering;
            repaint();
        }
    }

    private void initMargin() {
        // The margin between the composition and the edge of the page
        marginPanel = new JPanel();
        marginPanel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        marginPanel.setBackground(Color.white);
        marginPanel.add(this);
    }

    private void initAdjustments() {
        horizontalAdjustment = new HorizontalAdjustment(this);
        verticalAdjustment = new VerticalAdjustment(this);
        lyricsAdjustment = new LyricsAdjustment(this);
    }

    private void initView() {
        var width = (int) Math.round(
            LineWidthChangeDialog.MAX_LINE_WIDTH_IN_INCHES * UIUtils.RESOLUTION
        );

        viewChanged();
    }

    public void openFile(
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
        } catch (SAXException e) {
            var message =
                "Could not open the file “" +
                    file.getName() +
                    "” because it is damaged.";
            mainFrame.showErrorMessage(message);
            Log.error(message, e);
        } catch (IOException e) {
            var message = "Could not open the file “" + file.getName() + '”';
            mainFrame.showErrorMessage(
                message + ". Check if you have the permission to open it."
            );
            Log.error(message, e);
        }

        mainFrame.setDocumentModified(previousModifiedDocument);
    }

    // TODO: Use mbassador instead of this
    @Override
    public void musicDidChange(@NotNull Properties props) {
        playInsertingNote = props
            .getProperty(Constants.PLAY_INSERTING_NOTE)
            .equals(Constants.TRUE_VALUE);
        playWithRepeats = props
            .getProperty(Constants.WITH_REPEAT_PROP)
            .equals(Constants.TRUE_VALUE);
        instrument = Integer.parseInt(
            props.getProperty(Constants.INSTRUMENT_PROP)
        );
        manualTempoChange = Integer.parseInt(
            props.getProperty(Constants.TEMPO_CHANGE_PROP)
        );
        playbackNoteDuration = Integer.parseInt(
            props.getProperty(Constants.PLAYBACK_NOTE_DURATION_PROP)
        );
        colorizeNote = props
            .getProperty(Constants.COLORIZE_NOTE)
            .equals(Constants.TRUE_VALUE);
        composition.musicChanged(props);
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
        if (mainPanel == null) return;

        var staffPanel = mainPanel.getStaffPanel();
        if (staffPanel == null) return;

        var linePanels = staffPanel.getLinePanels();
        if (linePanels.isEmpty()) return;

        // middleLineY = first line's absolute middle Y
        middleLineY = getActualLineMiddleY(0);

        // rowHeight = distance between consecutive line midpoints
        if (linePanels.size() >= 2) {
            rowHeight = getActualLineMiddleY(1) - getActualLineMiddleY(0);
        } else {
            var linePanel = linePanels.get(0);
            rowHeight = linePanel.getLineComponent().getHeight()
                      + LayoutStylesheet.px(LayoutStylesheet.LINE_MARGIN_BOTTOM);
        }
    }

    public JScrollPane getScoreScrollPane() {
        return scrollPane;
    }

    @Override
    public boolean isNoteSelected(int xIndex, int line) {
        return selectionManager.isNoteSelected(xIndex, line);
    }

    @Override
    public void paintComponent(Graphics g) {
        // Control MainPanel visibility based on rendering mode
        // Must be set before super.paintComponent() to ensure proper painting
        mainPanel.setVisible(useComponentRendering);
        super.paintComponent(g);

        var g2 = (Graphics2D) g;
        g2.setColor(Color.white);
        g2.fillRect(0, 0, marginPanel.getWidth(), marginPanel.getHeight());

        // Derive coordinates from positioned components
        updateLayoutFromComponents();

        // Legacy rendering path removed - exports not yet implemented
        if (!useComponentRendering) {
            // TODO: Implement exports using component-based rendering
            System.err.println("WARNING: Rendering disabled - exports not yet implemented");
        }

        drawEditElements(g2);
        drawSelectionRect(g2);
        drawDebugOverlays(g2);
    }

    private void drawEditElements(Graphics2D g2) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (mode == Mode.NOTE_EDIT) {
            // We can insert in edit mode only if:
            // - A drag is not in process
            // - Shift is not pressed
            // - The sequencer is not playing
            var editNote = editModeManager.getEditNote();
            var editNotePoint = editModeManager.getEditNotePoint();

            if (
                (editNote != null) &&
                    ((control == Control.KEYBOARD) || editModeManager.isEditNoteVisible()) &&
                    !selectionManager.isStartedDrag() &&
                    !shiftPressed &&
                    !MidiController.isPlaying()
            ) {
                g2.setColor(EDIT_NOTE_COLOR);
                var lineIndex = editNotePoint.getLineIndex();
                var actualRenderLineMiddleY = getActualLineMiddleY(lineIndex);

                //noinspection ObjectEquality
                if (editNote != Note.GLISSANDO_NOTE) {
                    var x = editNote.getXPos();

                    if (x > (composition.getLineWidth() - 10)) {
                        editNote.setXPos(composition.getLineWidth() - 12);
                    }

                    NoteRenderer.getInstance().render(g2, editNote, actualRenderLineMiddleY);
                    editNote.setXPos(x);
                } else if (editNotePoint.getXIndex() > 0) {
                    var line = composition.getLine(lineIndex);
                    var ctx = new ElementRenderContext(composition);
                    ctx.setCurrentLine(line);
                    ctx.setLineIndex(lineIndex);
                    ctx.setMiddleLineY(actualRenderLineMiddleY);

                    GlissandoRenderer.getInstance().renderEditGlissando(
                        g2,
                        editNotePoint.getXIndex() - 1,
                        new Note.Glissando(editNote.getYPos()),
                        line,
                        ctx
                    );
                }
            }
        } else if (mode == Mode.NOTE_ADJUSTMENT) {
            horizontalAdjustment.repaint(g2);
        } else if (mode == Mode.VERTICAL_ADJUSTMENT) {
            verticalAdjustment.repaint(g2);
        } else if (mode == Mode.LYRICS_ADJUSTMENT) {
            lyricsAdjustment.repaint(g2);
        }
    }

    private void drawSelectionRect(Graphics2D g2) {
        if (selectionManager.isStartedDrag()) {
            g2.setColor(SELECTION_RECT_FILL_COLOR);
            g2.fill(selectionManager.getDragRectangle());

            g2.setStroke(SELECTION_RECT_STROKE);
            g2.setColor(SELECTION_STROKE_COLOR);
            g2.draw(selectionManager.getDragRectangle());
        }
    }

    private void drawDebugOverlays(Graphics2D g2) {
        // TODO: Implement debug overlays with component hierarchy
        // debugRenderer.drawDebugVisualization(g2, layoutResult, this);
    }

    @Override
    public int getNoteYPos(int yPos, int line) {
        return (int) (middleLineY +
            (yPos * NOTE_Y_OFFSET) +
            (line * rowHeight));
    }

    @Override
    public int getUnderLyricsYPos() {
        // TODO: Calculate from component hierarchy
        return 0;
    }

    public Note getEditNote() {
        return editModeManager.getEditNote();
    }

    @Handler
    public void noteTypeWasSelected(@NotNull NoteTypeSelectedMessage message) {
        setEditNote(makeEditNote(message.getNoteType()));
    }

    @Handler
    public void restModeDidChange(RestModeChangedMessage message) {
        setEditNote(makeEditNote());
    }

    @Handler
    public void onUpdateEditNote(UpdateEditNoteMessage message) {
        updateEditNote();
    }

    private void updateEditNote() {
        var editNote = editModeManager.getEditNote();

        if (editNote != null) {
            decorateNote(editNote);
            repaint();
        } else {
            setEditNote(makeEditNote());
        }
    }

    private static Note makeEditNote() {
        var noteType = NoteType.CROTCHET;
        var durationAction = Actions.DURATION_ACTION_GROUP.getSelected();

        if (durationAction != null) {
            noteType = durationAction.getType();
        } else {
            var barAction = Actions.BAR_ACTION_GROUP.getSelected();

            if (barAction != null) {
                noteType = barAction.getType();
            }
        }

        return makeEditNote(noteType);
    }

    private static Note makeEditNote(NoteType noteType) {
        // Make a new note or rest of the given type
        var type = noteType;

        if (Actions.REST_ACTION.isSelected()) {
            type = NoteType.valueOf(noteType.name() + "_REST");
        }

        var note = type.newInstance();
        decorateNote(note);
        return note;
    }

    private static void decorateNote(@NotNull Note note) {
        var dotAction = Actions.DOT_ACTION_GROUP.getSelected();
        note.setDotCount(
            (dotAction != null) ? dotAction.getDotLevel().ordinal() : 0
        );

        // Rests don't get any other decorations
        if (note.getNoteType().isRest()) {
            return;
        }

        var accidentalAction = Actions.ACCIDENTAL_ACTION_GROUP.getSelected();
        note.setAccidental(
            (accidentalAction != null)
                ? accidentalAction.getAccidental()
                : Note.Accidental.NONE
        );

        note.setAccidentalInParentheses(
            Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected()
        );

        note.setForceArticulation(
            Actions.ACCENT_ACTION.isSelected() ? ForceArticulation.ACCENT : null
        );

        var durationArticulationAction =
            Actions.ARTICULATION_ACTION_GROUP.getSelected();

        note.setDurationArticulation(
            (durationArticulationAction != null)
                ? durationArticulationAction.getArticulation()
                : null
        );

        note.setFermata(Actions.FERMATA_ACTION.isSelected());
    }

    public void setEditNote(@Nullable Note editNote) {
        if (editNote != null) {
            var currentEditNote = editModeManager.getEditNote();

            if (currentEditNote != null) {
                editNote.setYPos(currentEditNote.getYPos());
                editNote.setXPos(currentEditNote.getXPos());
            } else {
                editModeManager.setEditNote(editNote);
                setEditNotePositionToEnd();
            }

            editNote.setUpper(defaultUpperNote(editNote));
        }

        editModeManager.setEditNote(editNote);
        repaint();
    }

    @Handler
    public void onInsertLine(@NotNull InsertLineMessage message) {
        var shift = message.getShift();

        if ((selectionManager.getSelectedLine() != -1) || (shift == InsertLineAction.ADD)) {
            var index = (shift >= 0)
                ? (selectionManager.getSelectedLine() + shift)
                : InsertLineAction.ADD;
            composition.addLine(index, new Line());
            clearSelection();
            mainFrame.setDocumentModified(true);
            repaint();
        } else {
            mainFrame.showErrorMessage("Please select a line first.");
        }
    }

    @Override
    public int getPlayingLine() {
        return playbackStateManager.getPlayingLine();
    }

    @Override
    public int getPlayingNote() {
        return playbackStateManager.getPlayingNote();
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
        selectionManager.clearSelection();
        selectionChanged();
    }

    private void selectionChanged() {
        MessageCenter.post(new MusicSelectionChangedMessage(this));
    }

    private boolean noteWasModified(Line line, int noteIndex) {
        clearSelection();
        var editNote = editModeManager.getEditNote();
        var editNotePoint = editModeManager.getEditNotePoint();

        // If the active note is glissando, it needs different handling
        if (editNote.getNoteType() == NoteType.GLISSANDO) {
            if (editNotePoint.getXIndex() > 0) {
                line
                    .getNote(editNotePoint.getXIndex() - 1)
                    .setGlissando(editNote.getYPos());
            }

            return true;
        }

        if (
            (editNote.getNoteType() == NoteType.REPEAT_LEFT) &&
                ((noteIndex - 1) >= 0) &&
                (line.getNote(noteIndex - 1).getNoteType() == NoteType.REPEAT_RIGHT)
        ) {
            var repeatLeftRight = new RepeatLeftRight();
            repeatLeftRight.setXPos(line.getNote(noteIndex - 1).getXPos());
            line.setNote(noteIndex - 1, repeatLeftRight);
            return true;
        }

        if (
            (editNote.getNoteType() == NoteType.REPEAT_RIGHT) &&
                (noteIndex < line.noteCount()) &&
                (line.getNote(noteIndex).getNoteType() == NoteType.REPEAT_LEFT)
        ) {
            var repeatLeftRight = new RepeatLeftRight();
            repeatLeftRight.setXPos(line.getNote(noteIndex).getXPos());
            line.setNote(noteIndex, repeatLeftRight);
            return true;
        }

        if (editNote.getNoteType() == NoteType.GRACE_SEMIQUAVER_EDIT_STEP1) {
            return true;
        }

        if (editNote.getNoteType() == NoteType.PASTE) {
            // If the user tries to insert into triplet, they will get an error message.
            var iv = line.getTuplets().findInterval(noteIndex - 1);

            if ((iv != null) && ((noteIndex - 1) < iv.getEnd())) {
                mainFrame.showErrorMessage("Cannot insert into a triplet.");
                return true;
            }

            line.removeInterval(noteIndex - 1, noteIndex);
            var diff =
                ((noteIndex == line.noteCount())
                    ? NoteSpacing.calculateLastNoteXPos(line, clipboardManager.getFirstNote())
                    : line.getNote(noteIndex).getXPos()) -
                    clipboardManager.getFirstNote().getXPos();
            var copySize = clipboardManager.getSize();

            for (var i = 0; i < copySize; i++) {
                var note = clipboardManager.getNote(i);
                note.setXPos(note.getXPos() + diff);
                line.addNote(noteIndex + i, note.clone());
            }

            line.pasteIntervals(clipboardManager.getIntervalsCopyBuffer(), noteIndex);
            var lastNote = clipboardManager.getLastNote();
            var shift =
                (Math.round(
                    (NoteSpacing.getNoteSpacing(lastNote.getNoteType()) +
                        (lastNote.getAccidental().getWidthFactor() *
                            NoteSpacing.ACCIDENTAL_WIDTH)) *
                        line.getNoteDistChangeRatio()
                ) +
                    lastNote.getXPos()) -
                    clipboardManager.getFirstNote().getXPos();

            for (var i = noteIndex + copySize; i < line.noteCount(); i++) {
                line.getNote(i).setXPos(line.getNote(i).getXPos() + shift);
            }

            control = prevPasteControl;

            for (var i = noteIndex; i < (noteIndex + copySize); i++) {
                var interval = line.getBeamings().findInterval(i);

                if (interval != null) {
                    calculateLengthenings(i, line, true);
                    i = interval.getEnd();
                }
            }

            selectionManager.setInSelectMode(true);
            return true;
        }

        return false;
    }

    private void calculateEditNoteXPos() {
        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        var editNotePoint = editModeManager.getEditNotePoint();
        var line = composition.getLine(editNotePoint.getLineIndex());

        if (line.noteCount() == editNotePoint.getXIndex()) {
            editNote.setXPos(NoteSpacing.calculateLastNoteXPos(line, editNote));
        } else {
            var note = line.getNote(editNotePoint.getXIndex());
            editNote.setXPos(note.getXPos() + editNotePoint.getMovement());
        }
    }

    public void setEditNotePositionToEnd() {
        var editNotePoint = editModeManager.getEditNotePoint();
        editNotePoint.setMovement(0);
        editNotePoint.setLineIndex(composition.lineCount() - 1);
        editNotePoint.setXIndex(
            composition.getLine(editNotePoint.getLineIndex()).noteCount()
        );
        calculateEditNoteXPos();
    }

    private void editNoteDidChange(Line line, int noteIndex) {
        //mainFrame.getUndoManager().undoableEditHappened(new UndoableEditEvent(this, new
        // ModifyUndoableEdit(oldNote, oldNoteInfo, xIndex)));
        var editNote = editModeManager.getEditNote();
        Note nextNote;

        if (editNote.getNoteType().isGraceNote()) {
            nextNote = NoteType.GLISSANDO.newInstance();
        } else if (editNote.getNoteType() == NoteType.GLISSANDO) {
            NoteType nextNoteType;

            if (!line.getNote(noteIndex).getNoteType().isGraceNote()) {
                nextNoteType = line.getNote(noteIndex).getNoteType();
            } else if (noteIndex > 0) {
                nextNoteType = line.getNote(noteIndex - 1).getNoteType();
            } else {
                nextNoteType = NoteType.CROTCHET;
            }

            nextNote = nextNoteType.newInstance();
        } else if (
            editNote.getNoteType() == NoteType.GRACE_SEMIQUAVER_EDIT_STEP1
        ) {
            nextNote = new GraceSemiQuaver();
            ((GraceSemiQuaver) nextNote).setY0Pos(editNote.getYPos());
            ((GraceSemiQuaver) nextNote).setX2DiffPos(15);
            nextNote.setUpper(true);
        } else {
            nextNote = editNote.getNoteType().newInstance();
        }

        // After inserting a note, turn off fermata
        Actions.FERMATA_ACTION.setSelected(false);

        // Add any other note decorations
        decorateNote(nextNote);
        setEditNote(nextNote);
        spellLyrics(line);
        drawWidthIfWiderLine(line, false);
        repaint();

        if (playInsertingNote && editNote.getNoteType().isNote()) {
            new PlayNoteThread(editNote.getEditNotePitch(line)).start();
        }
    }

    public void addEditNote(Line line) {
        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        if (noteWasModified(line, line.noteCount())) {
            editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        editNote.setXPos(NoteSpacing.calculateLastNoteXPos(line, editNote));
        line.addNote(editNote);
        //mainFrame.getUndoManager().undoableEditHappened(new UndoableEditEvent(this, new
        // InsertUndoableEdit(cloneActiveNote, ni, noteInfo.size()-2)));

        // Decide automatic beaming
        if (
            editNote.getNoteType().isBeamable() &&
                (line.noteCount() >= 2) &&
                (line.getTuplets().findInterval(line.noteCount() - 2) == null)
        ) {
            var sum = 0;

            for (var i = line.noteCount() - 2; i >= 0; i--) {
                if (line.getNote(i).getNoteType() == NoteType.QUAVER) {
                    sum += 2;
                } else if (
                    (line.getNote(i).getNoteType() == NoteType.SEMIQUAVER) ||
                        (line.getNote(i).getNoteType() == NoteType.DEMI_SEMIQUAVER)
                ) {
                    sum += 1;
                } else {
                    break;
                }

                var interval = line.getBeamings().findInterval(i);

                if ((interval != null) && (interval.getStart() == i)) {
                    break;
                }
            }

            if (
                ((editNote.getNoteType() == NoteType.QUAVER) &&
                    (sum > 0) &&
                    ((sum % 2) == 0) &&
                    ((sum % 4) != 0)) ||
                    (((editNote.getNoteType() == NoteType.SEMIQUAVER) ||
                        (editNote.getNoteType() == NoteType.DEMI_SEMIQUAVER)) &&
                        (sum > 0) &&
                        ((sum % 4) != 0))
            ) {
                line
                    .getBeamings()
                    .addInterval(line.noteCount() - 2, line.noteCount() - 1);
                //activeNote.setXPos(activeNote.getXPos()-(ND-BEAMEDNOTEDIST));
            }

            calculateLengthenings(line.noteCount() - 1, line, true);
        }

        editNoteDidChange(line, line.noteCount() - 1);
    }

    private void insertEditNote(int xIndex, Line line) {
        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        if (noteWasModified(line, xIndex)) {
            editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        // If the user tries to insert into triplet, they will get an error message
        var iv = line.getTuplets().findInterval(xIndex - 1);

        if ((iv != null) && ((xIndex - 1) < iv.getEnd())) {
            mainFrame.showErrorMessage("Cannot insert into a triplet.");
            return;
        }

        line.removeInterval(xIndex - 1, xIndex);
        editNote.setXPos(
            line.getNote(xIndex).getXPos() +
                (editNote.getAccidental().getWidthFactor() * NoteSpacing.ACCIDENTAL_WIDTH)
        );
        line.addNote(xIndex, editNote);
        var shift = Math.round(
            (NoteSpacing.getNoteSpacing(editNote.getNoteType()) +
                (editNote.getAccidental().getWidthFactor() *
                    NoteSpacing.ACCIDENTAL_WIDTH)) *
                line.getNoteDistChangeRatio()
        );

        for (var i = xIndex + 1; i < line.noteCount(); i++) {
            line.getNote(i).setXPos(line.getNote(i).getXPos() + shift);
        }

        editNoteDidChange(line, xIndex);
    }

    private void modifyEditNote(int xIndex, Line line) {
        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        if (noteWasModified(line, xIndex)) {
            editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        var oldNote = line.getNote(xIndex);

        if (
            (line.getTuplets().findInterval(xIndex) != null) &&
                (oldNote.getNoteType() != editNote.getNoteType())
        ) {
            mainFrame.showErrorMessage(
                "Cannot modify a triplet with different note type."
            );
            return;
        }

        editNote.setXPos(
            oldNote.getXPos() +
                ((editNote.getAccidental().getWidthFactor() -
                    oldNote.getAccidental().getWidthFactor()) *
                    NoteSpacing.ACCIDENTAL_WIDTH)
        );
        var shift = Math.round(
            ((NoteSpacing.getNoteSpacing(editNote.getNoteType()) -
                NoteSpacing.getNoteSpacing(oldNote.getNoteType())) +
                ((editNote.getAccidental().getWidthFactor() -
                    oldNote.getAccidental().getWidthFactor()) *
                    NoteSpacing.ACCIDENTAL_WIDTH)) *
                line.getNoteDistChangeRatio()
        );
        line.setNote(xIndex, editNote);

        for (var i = xIndex + 1; i < line.noteCount(); i++) {
            line.getNote(i).setXPos(line.getNote(i).getXPos() + shift);
        }

        // Arrange beaming
        if (oldNote.getNoteType() != editNote.getNoteType()) {
            line.removeInterval(xIndex - 1, xIndex + 1);
            calculateLengthenings(xIndex - 1, line, true);
            calculateLengthenings(xIndex + 1, line, true);
        } else {
            calculateLengthenings(xIndex, line, true);
        }

        // Arrange ties
        if (oldNote.getYPos() != editNote.getYPos()) {
            line.getTies().removeInterval(xIndex - 1, xIndex + 1);
        }

        editNoteDidChange(line, xIndex);
    }

    private static void deleteNote(int xIndex, @NotNull Line line) {
        if (xIndex < (line.noteCount() - 1)) {
            var shift =
                line.getNote(xIndex).getXPos() -
                    line.getNote(xIndex + 1).getXPos();

            for (var i = xIndex + 1; i < line.noteCount(); i++) {
                line.getNote(i).setXPos(line.getNote(i).getXPos() + shift);
            }
        }

        line.removeNote(xIndex);
    }

    public int getSelectionSize() {
        return selectionManager.getSelectionSize();
    }

    private boolean shouldConnectSelection(@NotNull IntervalSet intervals) {
        return selectionManager.shouldConnectSelection(intervals);
    }

    public boolean canToggleBeaming() {
        return selectionManager.canToggleBeaming();
    }

    @Handler
    public void onToggleBeaming(ToggleBeamMessage message) {
        toggleBeaming();
    }

    // Assumes that canToggleBeamingOfSelection() is true
    public void toggleBeaming() {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var beamings = line.getBeamings();

        if (shouldConnectSelection(beamings)) {
            beamings.addInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
            calculateLengthenings(selectionManager.getSelectionBegin(), line, true);
        } else {
            beamings.removeInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
            calculateLengthenings(selectionManager.getSelectionBegin(), line, true);
            calculateLengthenings(selectionManager.getSelectionEnd(), line, true);
        }

        composition.setModified(true);
        repaint();
    }

    public TieContext getTieContext() {
        return selectionManager.getTieContext();
    }

    public boolean canToggleTie() {
        return selectionManager.canToggleTie();
    }

    @Handler
    public void onToggleTie(ToggleTieMessage message) {
        toggleTie();
    }

    // This method assumes canToggleTie() is true
    public void toggleTie() {
        // Get the context if necessary
        if (selectionManager.getTieContext() == null) {
            canToggleTie();
        }

        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var intervals = selectionManager.getTieContext().intervals();

        if (intervals != null) {
            // Remove existing tie
            intervals.removeInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
        } else {
            // Add a new tie
            line.getTies().addInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
        }

        // Reset the context so it is recalculated next time
        selectionManager.setTieContext(null);
        composition.setModified(true);
        repaint();
    }

    @NotNull
    @Contract(" -> new")
    public Pair<Boolean, Boolean> canToggleTuplet() {
        return selectionManager.canToggleTuplet();
    }

    @Handler
    public void onToggleTuplet(@NotNull ToggleTupletMessage message) {
        toggleTuplet(message.getTupletSize());
    }

    // This method assumes canTupletSelection() is true. If tupletSize == 0,
    // then there is an existing tuplet which should be removed.
    public void toggleTuplet(int tupletSize) {
        // If the beginning of the selection is in a tuplet, then all notes in the
        // selection are in the same tuplet.
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var tuplets = line.getTuplets();
        var interval = tuplets.findInterval(selectionManager.getSelectionBegin());

        if ((interval == null) || (tupletSize > 0)) {
            // If the selection is not in a tuplet, add a new one
            if (interval == null) {
                interval = tuplets.addInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
            }

            TupletIntervalData.setGrade(interval, tupletSize);
        } else {
            tuplets.removeInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
        }

        composition.setModified(true);
        repaint();

        // Post a MusicSelectionChangedMessage to update the UI
        selectionChanged();
    }

    @Handler
    public void onAddDynamics(@NotNull AddDynamicsMessage message) {
        addDynamicsToSelection(message.isCrescendo());
    }

    // This method assumes getSelectionSize() > 1
    public void addDynamicsToSelection(boolean crescendo) {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var intervalSet = crescendo
            ? line.getCrescendos()
            : line.getDiminuendos();
        intervalSet.addInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
        composition.setModified(true);
        repaint();
    }

    // Returns the crescendo and diminuendo intervals from the selection
    @NotNull
    @Contract(" -> new")
    private Pair<
        ArrayList<Interval>,
        ArrayList<Interval>
        > getDynamicsIntervalsFromSelection() {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var crescendos = line.getCrescendos();
        var diminuendos = line.getDiminuendos();
        var crescendoIntervals = new ArrayList<Interval>();
        var diminuendoIntervals = new ArrayList<Interval>();

        for (var i = selectionManager.getSelectionBegin(); i <= selectionManager.getSelectionEnd(); i++) {
            var interval = crescendos.findInterval(i);

            if (interval != null) {
                crescendoIntervals.add(interval);
            }

            interval = diminuendos.findInterval(i);

            if (interval != null) {
                diminuendoIntervals.add(interval);
            }
        }

        return new Pair<>(crescendoIntervals, diminuendoIntervals);
    }

    public boolean canRemoveDynamicsFromSelection() {
        if (selectionManager.getSelectedNotesLine() == -1) {
            return false;
        }

        var intervals = getDynamicsIntervalsFromSelection();

        return (
            !intervals.getFirst().isEmpty() || !intervals.getSecond().isEmpty()
        );
    }

    @Handler
    public void onRemoveDynamics(@NotNull RemoveDynamicsMessage message) {
        removeDynamicsFromSelection();
    }

    // This method assumes canRemoveDynamicsFromSelection() is not null
    public void removeDynamicsFromSelection() {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var crescendos = line.getCrescendos();
        var intervals = getDynamicsIntervalsFromSelection();
        var crescendoIntervals = intervals.getFirst();

        for (var interval : crescendoIntervals) {
            crescendos.removeInterval(interval);
        }

        var diminuendos = line.getDiminuendos();
        var diminuendoIntervals = intervals.getSecond();

        for (var interval : diminuendoIntervals) {
            diminuendos.removeInterval(interval);
        }

        composition.setModified(true);
        repaint();
    }

    public boolean canMakeFirstSecondEnding() {
        // TODO: Determine if it makes sense to return false if the selection
        //  does not conform to what a first-second ending must include:
        //  - At least one note/rest
        //  - Single bar line
        //  - At least one note/rest
        //  - Right repeat
        return true;
    }

    @Handler
    public void onFirstSecondEnding(@NotNull FirstSecondEndingMessage message) {
        if (message.isMakeEnding()) {
            makeFirstSecondEnding();
        } else {
            removeFirstSecondEnding();
        }
    }

    public void makeFirstSecondEnding() {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var repeatExists = IntStream.rangeClosed(
            selectionManager.getSelectionBegin(),
            selectionManager.getSelectionEnd()
        ).anyMatch(i -> line.getNote(i).getNoteType() == NoteType.REPEAT_RIGHT);

        if (!repeatExists) {
            var answer = mainFrame.showConfirmDialog(
                """
                    It does not make sense to create a first-second ending without a right side \
                    repeat.
                    
                    Do you want to continue anyway?""",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );

            if (answer == JOptionPane.NO_OPTION) {
                return;
            }
        }

        line.getFirstSecondEndings().addInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
        composition.setModified(true);
        repaint();
    }

    public void removeFirstSecondEnding() {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        line
            .getFirstSecondEndings()
            .removeInterval(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd());
        composition.setModified(true);
        repaint();
    }

    public boolean canChangeTempo() {
        var selectedNote = getSingleSelectedNote();

        // The first note cannot have its tempo changed
        //noinspection ObjectEquality
        return composition.getLine(0).getNote(0) != selectedNote;
    }

    public boolean canToggleTrill() {
        if (selectionManager.getSelectedNotesLine() == -1) {
            return false;
        }

        // A trill can only be applied if one or more real notes (no grace notes or rests)
        // are in the selection.
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        return line
            .getNotes(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd())
            .stream()
            .anyMatch(note -> note.getNoteType().isRealNote());
    }

    @Handler
    public void onToggleTrill(ToggleTrillMessage message) {
        toggleTrill();
    }

    public void toggleTrill() {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());

        for (var note : line.getNotes(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd())) {
            note.setTrill(!note.isTrill());
        }

        composition.setModified(true);
        repaint();
    }

    public boolean canToggleLyricsUnderRests() {
        // Only enable if there is exactly one rest selected
        var note = getSingleSelectedNote();
        return (note != null) && note.getNoteType().isRest();
    }

    @Handler
    public void onToggleLyricsUnderRests(
        ToggleLyricsUnderRestsMessage message
    ) {
        toggleLyricsUnderRests();
    }

    public void toggleLyricsUnderRests() {
        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        var note = line.getNote(selectionManager.getSelectionBegin());
        note.setForceSyllable(!note.isForceSyllable());
        spellLyrics(line);
        composition.setModified(true);
        repaint();
    }

    public boolean canFlipPartialBeamOrientation() {
        if (getSelectionSize() != 1) {
            return false;
        }

        var line = composition.getLine(selectionManager.getSelectedNotesLine());
        return line.getBeamings().isInsideAnyInterval(selectionManager.getSelectionBegin());
    }

    @Handler
    public void onFlipPartialBeams(FlipPartialBeamsMessage message) {
        flipPartialBeamOrientation();
    }

    public void flipPartialBeamOrientation() {
        try {
            if ((selectionManager.getSelectedNotesLine() == -1) || (selectionManager.getSelectionBegin() != selectionManager.getSelectionEnd())) {
                throw new IllegalArgumentException();
            }

            var line = composition.getLine(selectionManager.getSelectedNotesLine());

            if (!line.getBeamings().isInsideAnyInterval(selectionManager.getSelectionBegin())) {
                throw new IllegalArgumentException();
            }

            var note = line.getNote(selectionManager.getSelectionBegin());
            note.setInvertFractionBeamOrientation(
                !note.isInvertFractionBeamOrientation()
            );

            composition.setModified(true);
            repaint();
        } catch (IllegalArgumentException e) {
            mainFrame.showInfoMessage(
                "You must select one beamed note in order to flip partial beam orientation."
            );
        }
    }

    public boolean canFlipStemDirection() {
        if (getSelectionSize() == 0) {
            return false;
        }

        // There has to be at least one non-rest note in the selection
        var line = composition.getLine(selectionManager.getSelectedNotesLine());

        return line
            .getNotes(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd())
            .stream()
            .anyMatch(note -> !note.getNoteType().isRest());
    }

    @Handler
    public void onFlipStemDirection(FlipStemDirectionMessage message) {
        flipStemDirection();
    }

    public void flipStemDirection() {
        if (selectionManager.getSelectedNotesLine() == -1) {
            mainFrame.showInfoMessage(
                "You must select one or more notes in order to flip their stem direction."
            );
            return;
        }

        var line = composition.getLine(selectionManager.getSelectedNotesLine());

        for (var note : line.getNotes(selectionManager.getSelectionBegin(), selectionManager.getSelectionEnd())) {
            note.setUpper(!note.isUpper());
        }

        Interval lastInterval = null;

        for (var i = selectionManager.getSelectionBegin(); i <= selectionManager.getSelectionEnd(); i++) {
            var inverval = line.getBeamings().findInterval(i);

            //noinspection ObjectEquality
            if ((inverval != null) && (inverval != lastInterval)) {
                calculateLengthenings(i, line, false);
                lastInterval = inverval;
            }
        }

        composition.setModified(true);
        repaint();
    }

    private void calculateSelection(boolean fromRectangle) {
        var helper = new Rectangle();
        selectionManager.setSelectedLine(-1);
        selectionManager.setSelectedNotesLine(-1);
        selectionManager.setSelectionBegin(-1);
        selectionManager.setSelectionEnd(-1);

        for (
            var lineIndex = 0;
            lineIndex < composition.lineCount();
            lineIndex++
        ) {
            if ((selectionManager.getSelectedNotesLine() != -1) && (selectionManager.getSelectedNotesLine() != lineIndex)) {
                break;
            }

            var line = composition.getLine(lineIndex);

            for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
                var note = line.getNote(noteIndex);

                if (line.getBeamings().findInterval(noteIndex) != null) {
                    helper.setBounds(
                        note.isUpper()
                            ? Crotchet.REAL_UP_NOTE_RECT
                            : Crotchet.REAL_DOWN_NOTE_RECT
                    );
                } else {
                    helper.setBounds(
                        note.isUpper()
                            ? note.getRealUpNoteRect()
                            : note.getRealDownNoteRect()
                    );
                }

                helper.translate(
                    note.getXPos(),
                    getNoteYPos(note.getYPos(), lineIndex) - Note.HOT_SPOT.y
                );

                if (
                    (fromRectangle && selectionManager.getDragRectangle().intersects(helper)) ||
                        (!fromRectangle && helper.contains(selectionManager.getDragStart()))
                ) {
                    selectionManager.setSelectedNotesLine(lineIndex);

                    if (selectionManager.getSelectionBegin() == -1) {
                        selectionManager.setSelectionBegin(noteIndex);
                    }

                    selectionManager.setSelectionEnd(noteIndex);
                }
            }
        }
    }

    private static void calculateLengthenings(
        int xIndex,
        @NotNull Line line,
        boolean automaticStemDirection
    ) {
        // Determine start index, end index
        var interval = line.getBeamings().findInterval(xIndex);

        if (interval == null) {
            return;
        }

        var startIndex = interval.getStart();
        var endIndex = interval.getEnd();

        // Decide whether beaming should be up or down
        var sumY = 0;

        for (var i = startIndex; i <= endIndex; i++) {
            var note = line.getNote(i);

            if (automaticStemDirection) {
                sumY += note.getYPos();
            } else {
                sumY += note.isUpper() ? 1 : -1;
            }
        }

        // +1: upper
        // -1: lower
        var direction = (sumY >= 0) ? 1 : -1;
        var startY = line.getNote(startIndex).getYPos();
        var endY = line.getNote(endIndex).getYPos();
        var yDiff = (double) endY - startY;
        var startX = line.getNote(startIndex).getXPos();
        var endX = line.getNote(endIndex).getXPos();
        var xDiff = endX - startX;

        var angle = Math.atan((yDiff * NOTE_Y_OFFSET) / xDiff);
        angle = Math.max(-MAX_BEAM_ANGLE, Math.min(angle, MAX_BEAM_ANGLE));

        var goodIndex = -1;

        for (var i = startIndex; i <= endIndex; i++) {
            var note = line.getNote(i);
            var xPos = note.getXPos();
            var yPos = note.getYPos();
            var distance = (yPos * NOTE_Y_OFFSET) - (angle * xPos);

            if (
                isGoodNote(
                    line,
                    startIndex,
                    endIndex,
                    i,
                    angle,
                    distance,
                    direction
                )
            ) {
                goodIndex = i;
                break;
            }
        }

        var note = line.getNote(goodIndex);
        note.acceleration.lengthening = 0;
        note.setUpper(direction == 1);
        var distance =
            (note.getYPos() * NOTE_Y_OFFSET) - (angle * note.getXPos());

        for (var left = goodIndex - 1; left >= startIndex; left--) {
            note = line.getNote(left);
            calculateNoteLengthening(note, angle, distance, direction);
        }

        for (var right = goodIndex + 1; right <= endIndex; right++) {
            note = line.getNote(right);
            calculateNoteLengthening(note, angle, distance, direction);
        }
    }

    private static boolean isGoodNotePosition(
        @NotNull Note note,
        double angle,
        double distance,
        int direction
    ) {
        var xPos = note.getXPos();
        var yPos = note.getYPos();
        return (
            (Math.round((angle * xPos) + distance) * direction) <=
                (yPos * NOTE_Y_OFFSET * direction)
        );
    }

    @SuppressWarnings("Convert2streamapi")
    private static boolean isGoodNote(
        Line line,
        int startIndex,
        int endIndex,
        int noteIndex,
        double angle,
        double distance,
        int direction
    ) {
        for (var left = noteIndex - 1; left >= startIndex; left--) {
            var leftNote = line.getNote(left);

            if (!isGoodNotePosition(leftNote, angle, distance, direction)) {
                return false;
            }
        }

        for (var right = noteIndex + 1; right <= endIndex; right++) {
            var rightNote = line.getNote(right);

            if (!isGoodNotePosition(rightNote, angle, distance, direction)) {
                return false;
            }
        }

        return true;
    }

    private static void calculateNoteLengthening(
        @NotNull Note note,
        double angle,
        double distance,
        int direction
    ) {
        note.setUpper(direction == 1);

        if (note.getNoteType().isGraceNote()) {
            note.acceleration.lengthening = 0;
        } else {
            note.acceleration.lengthening = (int) Math.round(
                (note.getYPos() * NOTE_Y_OFFSET) -
                    ((angle * note.getXPos()) + distance)
            );
        }
    }

    @Override
    public Composition getComposition() {
        return composition;
    }

    @Handler
    public void onNewDocument(NewFileMessage message) {
        setComposition(new Composition(mainFrame));
        requestFocusInWindow();
    }

    @Handler
    public void onLayoutChanged(@NotNull LayoutChangeMessage message) {
        // Invalidate layout for affected lines when content changes
        if (message.getChangeType() == LayoutChangeMessage.ChangeType.CONTENT) {
            var staffPanel = mainPanel.getStaffPanel();

            if (staffPanel != null) {
                for (var linePanel : staffPanel.getLinePanels()) {
                    linePanel.getLineComponent().invalidateLayout();
                }
            }
        }

        // Clear inspector hover immediately when layout changes to avoid stale bounds
        if (DebugState.isInspectorEnabled() && DebugState.getHoveredElement() != null) {
            DebugState.setHoveredElement(null);
            // Force immediate repaint to clear stale visualization
            repaint();
            return;
        }

        // Debounce repaints to batch multiple rapid changes
        if (repaintDebounceTimer == null) {
            repaintDebounceTimer = new Timer(REPAINT_DEBOUNCE_DELAY_MS, e -> repaint());
            repaintDebounceTimer.setRepeats(false);
        }

        repaintDebounceTimer.restart();
    }

    public void setComposition(Composition composition) {
        this.composition = composition;

        if (composition == null) {
            return;
        }

        // Reset the playing state
        PlaybackController.stop();
        playbackStateManager.reset();
        selectionManager.setSelectedNotesLine(-1);
        setLineWidth(composition.getLineWidth());

        // global calculate lengthening
        for (var l = 0; l < composition.lineCount(); l++) {
            var line = composition.getLine(l);

            for (var li = line.getBeamings().listIterator(); li.hasNext(); ) {
                calculateLengthenings(li.next().getStart(), line, false);
            }
        }

        for (var i = 0; i < composition.lineCount(); i++) {
            drawWidthIfWiderLine(composition.getLine(i), true);
        }

        if (mainFrame.getLyricsModePanel() != null) {
            mainFrame.getLyricsModePanel().getData();
        }

        spellLyrics();
        setEditNotePositionToEnd();

        // Update the MainPanel with the new composition
        if (mainPanel != null) {
            mainPanel.setComposition(composition);
            setupLineComponentState();
        }

        // mainFrame.setMode(Mode.NOTE_EDIT);
        mainFrame.fireMusicChanged(null);
        viewChanged();
        repaint();
    }

    public void spellLyrics() {
        for (var l = 0; l < composition.lineCount(); l++) {
            spellLyrics(composition.getLine(l));
        }
    }

    public void spellLyrics(@NotNull Line line) {
        // delete the current values
        line.beginRelation = Note.SyllableRelation.NO;

        for (var n = 0; n < line.noteCount(); n++) {
            var note = line.getNote(n);
            note.acceleration.syllable = "";
            note.acceleration.syllableRelation = Note.SyllableRelation.NO;
        }

        // get the lyrics slice
        var beginIndex = 0;

        for (var j = composition.indexOfLine(line); j > 0; j--) {
            beginIndex = composition.getLyrics().indexOf('\n', beginIndex) + 1;
            if (beginIndex == 0) {
                return;
            }
        }

        var endIndex = composition.getLyrics().indexOf('\n', beginIndex);

        if (endIndex == -1) {
            endIndex = composition.getLyrics().length();
        }

        if (beginIndex == endIndex) {
            return;
        }

        var lyrics =
            composition.getLyrics().substring(beginIndex, endIndex) + '\n';

        // calculate the begin relations
        if (lyrics.startsWith("--")) {
            line.beginRelation = Note.SyllableRelation.ONE_DASH;
            lyrics = lyrics.substring(2);
            beginIndex += 2;
        }

        // make the lyrics
        var begin = 0;
        var noteIndex = 0;

        for (var i = 0; i < lyrics.length(); i++) {
            var c = lyrics.charAt(i);

            if ((c == '\n') || (c == ' ') || (c == '-') || (c == '_')) { //word end
                var syllable = (begin < i)
                    ? lyrics.substring(begin, i)
                    : Constants.UNDERSCORE;
                Note.SyllableRelation syllableRelation;

                if ((c == '\n') || (c == ' ')) {
                    syllableRelation = Note.SyllableRelation.NO;
                    noteIndex = setSyllableForNextNote(
                        line,
                        noteIndex,
                        syllable,
                        syllableRelation
                    );
                } else if (c == '-') {
                    // Gould/Ross: hyphen = syllable division only (always single hyphen)
                    if (
                        (lyrics.charAt(i + 1) == '-') ||
                            (lyrics.charAt(i + 1) == '\n')
                    ) {
                        syllableRelation = Note.SyllableRelation.ONE_DASH;
                        i++;
                    } else {
                        syllableRelation = Note.SyllableRelation.ONE_DASH;
                    }

                    noteIndex = setSyllableForNextNote(
                        line,
                        noteIndex,
                        syllable,
                        syllableRelation
                    );
                } else { // c == '_'
                    // Gould/Ross: underscore = duration only (always extender line)
                    var eus = beginIndex + i + 1;

                    while (
                        ((eus < composition.getLyrics().length()) &&
                            (composition.getLyrics().charAt(eus) == '_')) ||
                            (((eus + 1) < composition.getLyrics().length()) &&
                                (composition.getLyrics().charAt(eus) == '\n') &&
                                (composition.getLyrics().charAt(eus + 1) == '_'))
                    ) {
                        eus++;
                    }

                    // Always use EXTENDER for underscores (duration indication)
                    syllableRelation = Note.SyllableRelation.EXTENDER;

                    if (i > 0) {
                        noteIndex = setSyllableForNextNote(
                            line,
                            noteIndex,
                            syllable,
                            syllableRelation
                        );
                    } else {
                        line.beginRelation = syllableRelation;
                    }
                }

                if (noteIndex >= line.noteCount()) {
                    break;
                }

                begin = i + 1;
            }
        }

        if (System.getenv("DEBUG") != null) {
            System.out.println("Line: " + composition.indexOfLine(line));
            System.out.println("BeginRelation: " + line.beginRelation);

            for (var i = 0; i < line.noteCount(); i++) {
                System.out.println(
                    line.getNote(i).acceleration.syllable +
                        "   Relation: " +
                        line.getNote(i).acceleration.syllableRelation.name()
                );
            }

            System.out.println();
        }
    }

    private static int setSyllableForNextNote(
        @NotNull Line line,
        int noteIndex,
        String syllable,
        Note.SyllableRelation syllableRelation
    ) {
        var index = noteIndex;

        while (
            (index < line.noteCount()) &&
                !line.getNote(index).getNoteType().isNote() &&
                !line.getNote(index).isForceSyllable()
        ) {
            index++;
        }

        if (index < line.noteCount()) {
            line.getNote(index).acceleration.syllable = syllable;
            line.getNote(index).acceleration.syllableRelation =
                syllableRelation;
        }

        return index + 1;
    }

    @Nullable
    public Note getSingleSelectedNote() {
        return selectionManager.getSingleSelectedNote();
    }

    @Override
    public int getStartY() {
        // TODO: Calculate from component hierarchy
        return 0;
    }

    public Dimension getSheetSize() {
        return sheetSize;
    }

    public int getSheetWidth() {
        return composition.getLineWidth();
    }

    public int getSheetHeight() {
        // TODO: Calculate from component hierarchy
        return getHeight();
    }

    public void drawWidthIfWiderLine(@NotNull Line line, boolean strict) {
        if (line.noteCount() > 1) {
            var endNote = line.getNote(line.noteCount() - 1);
            float idealSpace;

            if (strict) {
                idealSpace = endNote.getRealUpNoteRect().width;
            } else {
                idealSpace = (NoteSpacing.getNoteSpacing(endNote.getNoteType()) *
                    line.getNoteDistChangeRatio()) +
                    20;
            }

            if (
                line.getNote(line.noteCount() - 1).getXPos() >
                    (composition.getLineWidth() - idealSpace)
            ) {
                var firstX = line.getNote(0).getXPos();
                var ratio =
                    (composition.getLineWidth() - idealSpace - firstX) /
                        (endNote.getXPos() - firstX);

                for (var i = 1; i < line.noteCount(); i++) {
                    var note = line.getNote(i);
                    note.setXPos(
                        firstX + Math.round((note.getXPos() - firstX) * ratio)
                    );
                }

                line.mulNoteDistChange(ratio);
            }
        }
    }

    public void setInSelectMode(boolean inSelectMode) {
        selectionManager.setInSelectMode(inSelectMode);
    }

    public Control getControl() {
        return control;
    }

    @Handler
    public void controlDidChange(@NotNull ControlChangedMessage message) {
        setControl(message.getControl());
    }

    public void setControl(Control control) {
        this.control = control;
        setEditNotePositionToEnd();
        repaint();
    }

    public Mode getMode() {
        return mode;
    }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void modeDidChange(@NotNull ModeChangedMessage message) {
        setMode(message.getMode());
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        horizontalAdjustment.setEnabled(mode == Mode.NOTE_ADJUSTMENT);
        verticalAdjustment.setEnabled(mode == Mode.VERTICAL_ADJUSTMENT);
        lyricsAdjustment.setEnabled(mode == Mode.LYRICS_ADJUSTMENT);
        repaint();
    }

    @Override
    public int getLeadingKeysPos() {
        return leadingKeysPos;
    }

    public void setLeadingKeysPos(int leadingKeysPos) {
        this.leadingKeysPos = leadingKeysPos;
    }

    @Override
    public int getRowHeight() {
        return rowHeight;
    }

    public void setRowHeight(int rowHeight) {
        this.rowHeight = rowHeight;
    }

    @Override
    public int getMiddleLineY() {
        return middleLineY;
    }

    public void setMiddleLineY(int middleLineY) {
        this.middleLineY = middleLineY;
    }

    public void setDragDisabled(boolean dragDisabled) {
        this.dragDisabled = dragDisabled;
    }

    public void saveProperties() {
        var props = mainFrame.getProperties();
        props.setProperty(Constants.CONTROL_PROP, control.name());
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
            preferredSizeWithMargin.width = width + PAGE_MARGIN;
            preferredSizeWithMargin.height = preferredSize.height + PAGE_MARGIN;
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
        return selectionManager.getSelectedLine();
    }

    @Nullable
    public NoteSelection getSelection() {
        return selectionManager.getSelection();
    }

    public Sequence getSequence() {
        if (!composition.isModified()) {
            return sequence;
        }

        composition.setModified(false);

        try {
            var sequenceTrack = createSequence();
            sequence = sequenceTrack.getFirst();
            var track = sequenceTrack.getSecond();
            var ticks = 0;
            var lines = composition.getLines();

            for (var lineNum = 0; lineNum < lines.size(); lineNum++) {
                ticks = addLineToTrack(lineNum, track, ticks);
            }

            var finalMessage = new MetaMessage();
            finalMessage.setMessage(
                MidiMetaMessageTypes.SEQUENCE_NUMBER,
                new byte[]{
                    (byte) (-1 >> 8),
                    (byte) -1,
                    (byte) (-1 >> 8),
                    (byte) -1,
                },
                4
            );
            track.add(new MidiEvent(finalMessage, ticks));
        } catch (InvalidMidiDataException e) {
            mainFrame.showErrorMessage(
                "Could not get the MIDI sequence because of an unexpected error."
            );
        }

        return sequence;
    }

    private int addLineToTrack(int lineNum, Track track, int ticks)
        throws InvalidMidiDataException {
        var lines = composition.getLines();
        var line = lines.get(lineNum);
        var repeating = false;
        var trackTicks = ticks;

        noteLoop:
        for (
            var noteIndex = 0;
            noteIndex < line.noteCount();
            noteIndex++
        ) {
            var note = line.getNote(noteIndex);

            if (
                playWithRepeats &&
                    ((note.getNoteType() == NoteType.REPEAT_RIGHT) ||
                        (note.getNoteType() == NoteType.REPEAT_LEFT_RIGHT))
            ) {
                if (repeating) {
                    repeating = false;
                } else {
                    repeating = true;

                    for (; lineNum >= 0; lineNum--) {
                        for (--noteIndex; noteIndex >= 0; noteIndex--) {
                            if (
                                line.getNote(noteIndex).getNoteType().isRepeat()
                            ) {
                                continue noteLoop;
                            }
                        }

                        if (lineNum > 0) {
                            line = lines.get(lineNum - 1);
                            noteIndex = line.noteCount();
                        }
                    }

                    lineNum = 0;
                    continue;
                }
            }

            // Handle first-second endings
            var firstSecondInterval = line
                .getFirstSecondEndings()
                .findInterval(noteIndex);

            if (repeating && (firstSecondInterval != null)) {
                for (; noteIndex <= firstSecondInterval.getEnd(); noteIndex++) {
                    if (
                        line.getNote(noteIndex).getNoteType() ==
                            NoteType.REPEAT_RIGHT
                    ) {
                        noteIndex--;
                        continue noteLoop;
                    }
                }
            }

            if (
                !playWithRepeats &&
                    (firstSecondInterval != null) &&
                    (note.getNoteType() == NoteType.REPEAT_RIGHT)
            ) {
                noteIndex = firstSecondInterval.getEnd();
                continue;
            }

            trackTicks = addNoteToTrack(
                line,
                note,
                track,
                trackTicks,
                lineNum,
                noteIndex
            );
        }

        return trackTicks;
    }

    public Sequence getSelectedSequence(Line line, int begin, int end) {
        Sequence selectedSequence = null;
        var ticks = 0;

        try {
            var sequenceTrack = createSequence();
            selectedSequence = sequenceTrack.getFirst();
            var track = sequenceTrack.getSecond();

            for (var noteIndex = begin; noteIndex <= end; noteIndex++) {
                var note = line.getNote(noteIndex);

                // Handle first-second endings
                var firstSecondInterval = line
                    .getFirstSecondEndings()
                    .findInterval(noteIndex);

                if (
                    (firstSecondInterval != null) &&
                        (note.getNoteType() == NoteType.REPEAT_RIGHT)
                ) {
                    noteIndex = firstSecondInterval.getEnd();
                    continue;
                }

                var lineNum = composition.getLines().indexOf(line);
                ticks = addNoteToTrack(
                    line,
                    note,
                    track,
                    ticks,
                    lineNum,
                    noteIndex
                );
            }
        } catch (InvalidMidiDataException e) {
            mainFrame.showErrorMessage(
                "Could not get the MIDI sequence because of an unexpected error."
            );
        }

        return selectedSequence;
    }

    private int addNoteToTrack(
        Line line,
        Note note,
        Track track,
        int ticks,
        int lineNum,
        int noteIndex
    ) throws InvalidMidiDataException {
        addTempoChangeToTrack(note, track, ticks);
        addColorizeNoteToTrack(lineNum, noteIndex, track, ticks);
        return addNoteOnOffMessagesToTrack(track, line, noteIndex, ticks);
    }

    private void addColorizeNoteToTrack(
        int lineNum,
        int noteIndex,
        Track track,
        int ticks
    ) throws InvalidMidiDataException {
        if (colorizeNote) {
            var playNoteMessage = new MetaMessage();
            playNoteMessage.setMessage(
                MidiMetaMessageTypes.SEQUENCE_NUMBER,
                new byte[]{
                    (byte) (lineNum >> 8),
                    (byte) lineNum,
                    (byte) (noteIndex >> 8),
                    (byte) noteIndex,
                },
                4
            );
            track.add(new MidiEvent(playNoteMessage, ticks));
        }
    }

    @NotNull
    @Contract(" -> new")
    private Pair<Sequence, Track> createSequence()
        throws InvalidMidiDataException {
        var newSequence = new Sequence(Sequence.PPQ, PPQ, 0);
        var track = newSequence.createTrack();
        var programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, instrument, 0);
        track.add(new MidiEvent(programChange, 0));
        track.add(
            new MidiEvent(
                getMidiTempoMessage(composition.getTempo().getRealTempo()),
                0
            )
        );

        return new Pair<>(newSequence, track);
    }

    private void addTempoChangeToTrack(
        @NotNull Note note,
        Track track,
        int ticks
    ) throws InvalidMidiDataException {
        if (note.getTempoChange() != null) {
            track.add(
                new MidiEvent(
                    getMidiTempoMessage(note.getTempoChange().getRealTempo()),
                    ticks
                )
            );
        }
    }

    private int addNoteOnOffMessagesToTrack(
        Track track,
        @NotNull Line line,
        int noteIndex,
        int ticks
    ) throws InvalidMidiDataException {
        var note = line.getNote(noteIndex);
        var type = note.getNoteType();
        var trackTicks = ticks;

        if (type.isGraceNote()) {
            switch (type) {
                case GRACE_SEMIQUAVER -> {
                    var graceSemiQuaver = (GraceSemiQuaver) note;
                    var yPos = note.getYPos();
                    note.setYPos(graceSemiQuaver.getY0Pos());
                    addNoteOnMessageToTrack(track, trackTicks, note);
                    trackTicks += GRACE_QUAVER_DURATION;
                    addNoteOffMessageToTrack(track, trackTicks, note);
                    note.setYPos(yPos);
                }
                case GRACE_QUAVER -> {
                    addNoteOnMessageToTrack(track, trackTicks, note);
                    trackTicks += GRACE_QUAVER_DURATION;
                    addNoteOffMessageToTrack(track, trackTicks, note);
                }
                default -> mainFrame.showErrorMessage(
                    "Programmer's error: no such NoteType in MIDI generation: " +
                        type
                );
            }
        } else if (type.isNote() || type.isRest()) {
            var noteDuration = getNoteDurationWithTuplet(line, note, noteIndex);

            if (type.isNote()) {
                var interval = line.getTies().findInterval(noteIndex);

                if ((interval == null) || (interval.getStart() == noteIndex)) {
                    addNoteOnMessageToTrack(track, trackTicks, note);
                }

                if ((interval == null) || (interval.getEnd() == noteIndex)) {
                    var currDuration = (note.getDurationArticulation() == null)
                        ? playbackNoteDuration
                        : note.getDurationArticulation().getDuration();
                    addNoteOffMessageToTrack(
                        track,
                        (int) (trackTicks +
                            ((noteDuration * currDuration) / 100f)),
                        note
                    );
                }
            }

            trackTicks += noteDuration;
        }

        return trackTicks;
    }

    private static void addNoteOnMessageToTrack(
        @NotNull Track track,
        int ticks,
        @NotNull Note note
    ) throws InvalidMidiDataException {
        var down = new ShortMessage();
        down.setMessage(
            ShortMessage.NOTE_ON,
            note.getPitch(),
            (note.getForceArticulation() == ForceArticulation.ACCENT)
                ? NOTE_VELOCITY
                : ACCENTED_NOTE_VELOCITY
        );
        track.add(new MidiEvent(down, ticks));
    }

    private static void addNoteOffMessageToTrack(
        @NotNull Track track,
        int ticks,
        @NotNull Note note
    ) throws InvalidMidiDataException {
        var up = new ShortMessage();
        up.setMessage(
            ShortMessage.NOTE_OFF,
            note.getPitch(),
            (note.getForceArticulation() == ForceArticulation.ACCENT)
                ? NOTE_VELOCITY
                : ACCENTED_NOTE_VELOCITY
        );
        track.add(new MidiEvent(up, ticks));
    }

    private int getNoteDurationWithTuplet(
        Line line,
        @NotNull Note note,
        int noteIndex
    ) {
        return Math.round(
            note.getDuration() * getTupletFactor(line, noteIndex)
        );
    }

    @NotNull
    private MetaMessage getMidiTempoMessage(int realTempo)
        throws InvalidMidiDataException {
        var tempoMessage = new MetaMessage();
        var midiTempo = 60000000 / ((realTempo * manualTempoChange) / 100);
        tempoMessage.setMessage(
            MidiMetaMessageTypes.SET_TEMPO,
            new byte[]{
                (byte) (midiTempo >> 16),
                (byte) (midiTempo >> 8),
                (byte) midiTempo,
            },
            3
        );
        return tempoMessage;
    }

    private float getTupletFactor(@NotNull Line line, int noteIndex) {
        var tupletInt = line.getTuplets().findInterval(noteIndex);

        if (tupletInt == null) {
            return 1;
        }

        var lastTempo = composition.getLastTempo(line, noteIndex);
        var tupletDuration = 0f;

        for (var i = tupletInt.getStart(); i <= tupletInt.getEnd(); i++) {
            tupletDuration += line.getNote(i).getDuration();
        }

        tupletDuration /= lastTempo.getTempoType().getNote().getDuration();
        float newDuration;

        if (tupletDuration >= 1) {
            newDuration = (float) Math.floor(tupletDuration);

            if ((newDuration == tupletDuration) && (newDuration > 1)) {
                newDuration--;
            }
        } else {
            newDuration = (float) Math.pow(
                2,
                Math.floor(Math.log(tupletDuration) / LOG2)
            );
        }

        return newDuration / tupletDuration;
    }

    public int getInstrument() {
        return instrument;
    }

    //***************************
    // MouseListener methods
    //***************************
    @Override
    public void mouseClicked(@NotNull MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        requestFocusInWindow();

        if (MidiController.isPlaying()) {
            return;
        }

        if (selectionManager.isInSelectMode() || e.isShiftDown()) {
            clearSelection();
            resetPlayback();
            selectionManager.getDragStart().setLocation(e.getX(), e.getY());
            selectionManager.getDragRectangle().setBounds(0, 0, 0, 0);
            updateSelection(e, false);
            selectionChanged();

            // If a single note was clicked, play it
            if (getSelectionSize() == 1) {
                var note = composition
                    .getLine(selectionManager.getSelectedNotesLine())
                    .getNote(selectionManager.getSelectionBegin());

                if (note.getNoteType().isNote()) {
                    new PlayNoteThread(note.getPitch()).start();
                }
            }
        } else if (control == Control.MOUSE) {
            var editNotePoint = editModeManager.getEditNotePoint();
            var line = composition.getLine(editNotePoint.getLineIndex());

            if (editNotePoint.getXIndex() == line.noteCount()) {
                addEditNote(line);
            } else if (editNotePoint.getMovement() != 0) {
                insertEditNote(
                    editNotePoint.getXIndex() +
                        ((editNotePoint.getMovement() < 0) ? 0 : 1),
                    line
                );
            } else {
                modifyEditNote(editNotePoint.getXIndex(), line);
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        // Don't allow selection during playback
        if (dragDisabled || MidiController.isPlaying()) {
            return;
        }

        var recalculateSelection = selectionManager.isStartedDrag();

        if (!selectionManager.isStartedDrag()) {
            selectionManager.setStartedDrag(true);
            selectionManager.getDragStart().setLocation(e.getX(), e.getY());
            clearSelection();
            resetPlayback();
        }

        var x = e.getX();

        if (x < 0) {
            x = 0;
        } else if (x >= getWidth()) {
            x = getWidth() - 1;
        }

        var y = e.getY();

        if (y < 0) {
            y = 0;
        } else if (y >= getHeight()) {
            y = getHeight() - 1;
        }

        var dragRect = selectionManager.getDragRectangle();
        var dragStart = selectionManager.getDragStart();
        dragRect.setBounds(
            Math.min(dragStart.x, x),
            Math.min(dragStart.y, y),
            Math.abs(dragStart.x - x),
            Math.abs(dragStart.y - y)
        );

        if (recalculateSelection) {
            updateSelection(e, true);
        }

        repaint();
    }

    private void updateSelection(MouseEvent e, boolean fromRectangle) {
        calculateSelection(fromRectangle);

        if (
            (selectionManager.getSelectionBegin() == -1) &&
                (Math.abs(
                    e.getY() -
                        getNoteYPos(
                            0,
                            (e.getY() - composition.getTopPadding()) / rowHeight
                        )
                ) <=
                    (2 * STAFF_LINE_Y_OFFSET))
        ) {
            var lineIndex = (e.getY() - composition.getTopPadding()) / rowHeight;

            if (
                (lineIndex < 0) || (lineIndex >= composition.lineCount())
            ) {
                selectionManager.setSelectedLine(-1);
            } else {
                selectionManager.setSelectedLine(lineIndex);
            }
        }

        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // Inspector hover tracking - only repaint if hovered element changes
        if (DebugState.isInspectorEnabled()) {
            var oldHoveredElement = DebugState.getHoveredElement();
            DebugState.setMousePosition(new Point(e.getX(), e.getY()));
            updateInspectorHover(e.getX(), e.getY());
            var newHoveredElement = DebugState.getHoveredElement();

            // Only repaint if the hovered element actually changed
            if (!Objects.equals(oldHoveredElement, newHoveredElement)) {
                logInspectorHover(newHoveredElement);
                repaint();
            }
        }

        var editNote = editModeManager.getEditNote();

        if (
            (editNote == null) ||
                (control != Control.MOUSE) ||
                (mode != Mode.NOTE_EDIT)
        ) {
            return;
        }

        var x = e.getX();
        var y = e.getY();
        var newEditNotePoint = editModeManager.getNewEditNotePoint();
        var editNotePoint = editModeManager.getEditNotePoint();

        // Use component hierarchy to find which line the mouse is over
        var lineIndex = findLineIndexAtPoint(y);

        if (lineIndex < 0 || lineIndex >= composition.lineCount()) {
            return;
        }

        newEditNotePoint.setLineIndex(lineIndex);

        // Get the actual middle line Y from the LineComponent (for new component rendering)
        // or fall back to the old calculation (for legacy rendering)
        var actualLineMiddleY = getActualLineMiddleY(newEditNotePoint.getLineIndex());
        var yPos = Math.round((y - actualLineMiddleY) / NOTE_Y_OFFSET);

        // Convert yPos to the internal coordinate system used by newEditNotePoint.Y
        // newEditNotePoint.Y = yPos + 12 (where 12 = (STAFF_LINES_ABOVE + 3) * 2)
        newEditNotePoint.setY((int) yPos + ((STAFF_LINES_ABOVE + 3) * 2));

        if (
            (newEditNotePoint.getY() <= 0) ||
                (newEditNotePoint.getY() >
                    (((STAFF_LINES_BELOW + STAFF_LINE_COUNT + STAFF_LINES_ABOVE) *
                        2) +
                        1))
        ) {
            return;
        }

        setNewEditNotePoint(x, newEditNotePoint.getLineIndex());
        editNote.setYPos(newEditNotePoint.getY() - ((STAFF_LINES_ABOVE + 3) * 2));

        if (!newEditNotePoint.equals(editNotePoint)) {
            editNotePoint.setXIndex(newEditNotePoint.getXIndex());
            editNotePoint.setY(newEditNotePoint.getY());
            editNotePoint.setMovement(newEditNotePoint.getMovement());
            editNotePoint.setLineIndex(newEditNotePoint.getLineIndex());
            editNote.setUpper(defaultUpperNote(editNote));
            calculateEditNoteXPos();
            repaint();
        }

        // TODO: Use EditNoteChangedEvent instead.
        mainFrame
            .getStatusBar()
            .setPitchString(
                editNote.getEditNotePitchString(
                    composition.getLine(editNotePoint.getLineIndex())
                )
            );
    }

    /**
     * Gets the actual middle line Y coordinate for a given line index from the LineComponent.
     *
     * @param lineIndex The line index
     * @return The absolute Y coordinate of the middle line in Score coordinates
     */
    /**
     * Finds the line index at the given Y coordinate using the component hierarchy.
     *
     * @param y Y coordinate in Score coordinates
     * @return The line index, or -1 if not found
     */
    private int findLineIndexAtPoint(int y) {
        if (mainPanel == null) {
            return (y - composition.getTopPadding()) / rowHeight;
        }

        var staffPanel = mainPanel.getStaffPanel();

        if (staffPanel == null) {
            return (y - composition.getTopPadding()) / rowHeight;
        }

        // Convert Score Y to StaffPanel Y
        var staffPanelY = y - mainPanel.getY() - staffPanel.getY();

        // Find which LinePanel contains this Y
        var linePanels = staffPanel.getLinePanels();

        for (var i = 0; i < linePanels.size(); i++) {
            var linePanel = linePanels.get(i);
            var bounds = linePanel.getBounds();

            if (staffPanelY >= bounds.y && staffPanelY < bounds.y + bounds.height) {
                return i;
            }
        }

        return -1;
    }

    private int getActualLineMiddleY(int lineIndex) {
        if (mainPanel == null) {
            return 0;
        }

        var staffPanel = mainPanel.getStaffPanel();

        if (staffPanel == null) {
            return 0;
        }

        var linePanels = staffPanel.getLinePanels();

        if (lineIndex < 0 || lineIndex >= linePanels.size()) {
            return 0;
        }

        var linePanel = linePanels.get(lineIndex);
        var lineComponent = linePanel.getLineComponent();

        // Get the LineComponent's Y position in the Score coordinate system
        var linePanelY = linePanel.getY();
        var staffPanelY = staffPanel.getY();
        var mainPanelY = mainPanel.getY();
        var lineComponentY = lineComponent.getY();
        var componentMiddleLineY = lineComponent.getMiddleLineY();

        // The middleLineY is relative to the LineComponent, so we need to add the offsets
        return mainPanelY + staffPanelY + linePanelY + lineComponentY + componentMiddleLineY;
    }

    private void setNewEditNotePoint(int xPos, int lineIndex) {
        var x = xPos - Note.HOT_SPOT.x;
        var foundX = 0;
        var line = composition.getLine(lineIndex);
        var newEditNotePoint = editModeManager.getNewEditNotePoint();

        for (var i = 0; i < (line.noteCount() - 1); i++) {
            if (
                (line.getNote(i).getXPos() < x) &&
                    (x <= line.getNote(i + 1).getXPos())
            ) {
                foundX = i + 1;
                break;
            }
        }

        if (foundX == 0) {
            if (line.noteCount() == 0) {
                newEditNotePoint.setMovement(0);
                newEditNotePoint.setXIndex(0);
            } else if (x <= line.getNote(0).getXPos()) {
                newEditNotePoint.setMovement(FIRST_NOTE_IN_LINE_MOVEMENT);
                newEditNotePoint.setXIndex(0);
            } else {
                newEditNotePoint.setMovement(0);
                newEditNotePoint.setXIndex(line.noteCount());
            }
        } else {
            var period =
                ((x - line.getNote(foundX - 1).getXPos()) * 4) /
                    (line.getNote(foundX).getXPos() -
                        line.getNote(foundX - 1).getXPos());
            //if(foundX==endNote-1 && period!=0) period=3;
            switch (period) {
                case 0 -> {
                    newEditNotePoint.setMovement(0);
                    newEditNotePoint.setXIndex(foundX - 1);
                }
                case 1, 2 -> {
                    newEditNotePoint.setMovement(
                        -(line.getNote(foundX).getXPos() -
                            line.getNote(foundX - 1).getXPos()) / 2
                    );
                    newEditNotePoint.setXIndex(foundX);
                }
                case 3, 4 -> {
                    newEditNotePoint.setMovement(0);
                    newEditNotePoint.setXIndex(foundX);
                }
            }
        }
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (e.isPopupTrigger()) {
            popup.show(this, e.getX(), e.getY());
        }
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (e.isPopupTrigger()) {
            popup.show(this, e.getX(), e.getY());
        } else if (selectionManager.isStartedDrag()) {
            selectionManager.setStartedDrag(false);
            repaint();
        }

        selectionChanged();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            return;
        }

        if (
            !editModeManager.isEditNoteVisible() &&
                (control == Control.MOUSE) &&
                (mode == Mode.NOTE_EDIT)
        ) {
            editModeManager.setEditNoteVisible(true);
            //setCursor(activeNote==null ? Cursor.getDefaultCursor() : emptyCursor);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (DebugState.isInspectorEnabled()) {
            DebugState.setHoveredElement(null);
            DebugState.setMousePosition(null);
            repaint();
        }

        if (
            editModeManager.isEditNoteVisible() &&
                (control == Control.MOUSE) &&
                (mode == Mode.NOTE_EDIT)
        ) {
            editModeManager.setEditNoteVisible(false);
            repaint();
            //setCursor(Cursor.getDefaultCursor());
        }
    }

    /**
     * Updates the inspector hovered element based on mouse position.
     * Checks elements in priority order (most specific first).
     */
    private void updateInspectorHover(int x, int y) {
        // TODO: Implement hover detection with component hierarchy
        DebugState.setHoveredElement(null);
    }

    // Stubbed method - temporarily disabled
    @SuppressWarnings("unused")
    private void updateInspectorHoverOLD(int x, int y) {
        /*
        // 1. Check notes
        int noteCount = 0;
        for (var line : layoutResult.getLines()) {
            for (var note : line.getNotes()) {
                noteCount++;
                if (note.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        note.getElementBounds(),
                        "Note",
                        DebugState.ElementType.NOTE
                    ));
                    return;
                }
            }
        }

        // 2. Check staff lyrics syllables
        int syllableCount = 0;
        for (var line : layoutResult.getLines()) {
            var lyrics = line.getLyrics();
            syllableCount += lyrics.getSyllables().size();

            for (var syllable : lyrics.getSyllables()) {
                if (syllable.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        syllable.getBounds(),
                        "Staff Lyrics: " + syllable.getText(),
                        DebugState.ElementType.STAFF_LYRICS
                    ));
                    return;
                }
            }
        }

        // 3. Check attachments
        int attachmentCount = 0;
        for (var line : layoutResult.getLines()) {
            attachmentCount += line.getAttachments().size();
            for (var attachment : line.getAttachments()) {
                if (attachment.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        attachment.getBounds(),
                        "Attachment: " + attachment.getType(),
                        DebugState.ElementType.ATTACHMENT
                    ));
                    return;
                }
            }
        }

        // 4. Check ranges
        int rangeCount = 0;
        for (var line : layoutResult.getLines()) {
            rangeCount += line.getRangeElements().size();
            for (var range : line.getRangeElements()) {
                if (range.containsPoint(x, y)) {
                    DebugState.setHoveredElement(new DebugState.HoveredElement(
                        range.getBounds(),
                        "Range: " + range.getType(),
                        DebugState.ElementType.RANGE
                    ));
                    return;
                }
            }
        }

        // 5. Check line bounds
        for (var line : layoutResult.getLines()) {
            if (line.getLineBounds().containsForHitTest(x, y)) {
                // Log element counts for this line
                System.out.printf(
                    "[Inspector Debug] Line hit | Total notes: %d, syllables: %d, " +
                        "attachments: %d, ranges: %d%n",
                    noteCount,
                    syllableCount,
                    attachmentCount,
                    rangeCount
                );

                DebugState.setHoveredElement(new DebugState.HoveredElement(
                    line.getLineBounds(),
                    "Line",
                    DebugState.ElementType.LINE
                ));
                return;
            }
        }

        // 6. Check sections
        if (checkSection(
            layoutResult.getTitle(), x, y, "Title", DebugState.ElementType.TITLE
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getAttribution(), x, y, "Attribution", DebugState.ElementType.ATTRIBUTION
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getLyrics(), x, y, "Under Lyrics", DebugState.ElementType.UNDER_LYRICS
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getBanglaLyrics(),
            x,
            y,
            "Bangla Lyrics",
            DebugState.ElementType.BANGLA_LYRICS
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getTranslation(),
            x,
            y,
            "Translation",
            DebugState.ElementType.TRANSLATION
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getFootnotes(), x, y, "Footnotes", DebugState.ElementType.FOOTNOTES
        )) {
            return;
        }

        if (checkSection(
            layoutResult.getScore(), x, y, "Score", DebugState.ElementType.SECTION
        )) {
            return;
        }

        // No element found
        DebugState.setHoveredElement(null);
        */
    }

    /**
     * Checks if a section contains the given point and sets the hovered element if so.
     *
     * @return true if the section was hit and hovered element was set
     */
    private boolean checkSection(
        SectionLayout section,
        int x,
        int y,
        String label,
        DebugState.ElementType type
    ) {
        var bounds = section.getBounds();

        if (bounds.containsForHitTest(x, y) && section.hasContent()) {
            DebugState.setHoveredElement(new DebugState.HoveredElement(bounds, label, type));
            return true;
        }

        return false;
    }

    /**
     * Logs inspector hover information for debugging.
     */
    private void logInspectorHover(DebugState.HoveredElement element) {
        if (element == null) {
            System.out.println("[Inspector] Hover cleared");
            return;
        }

        var bounds = element.getBounds();
        System.out.printf(
            "[Inspector] %s (%s) | Size: %s | Padding: %s | Margin: %s%n",
            element.getLabel(),
            element.getType(),
            bounds.getContentSizeString(),
            bounds.getPaddingCss(),
            bounds.getMarginCss()
        );
    }

    public void allowFocusInComponent(Component component) {
        componentsAllowedToGainFocus.add(component);
    }

    //***********************
    // KeyListener methods
    //***********************
    @Override
    public void keyPressed(@NotNull KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shiftPressed = true;
            repaint();
        }
    }

    @Override
    public void keyReleased(@NotNull KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shiftPressed = false;
            repaint();
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    //***********************
    // FocusListener methods
    //***********************
    @Override
    public void focusGained(FocusEvent e) {
    }

    @Override
    public void focusLost(@NotNull FocusEvent e) {
        if (!componentsAllowedToGainFocus.contains(e.getOppositeComponent())) {
            new FocusLostThread().start();
        }
    }

    @NotNull
    public BufferedImage createImageForExport(
        Color background,
        double scale,
        @NotNull MyBorder border
    ) {
        var image = new BufferedImage(
            (int) ((getSheetWidth()) * scale) + border.getWidth(),
            (int) ((getSheetHeight()) * scale) + border.getHeight(),
            BufferedImage.TYPE_BYTE_GRAY
        );
        createImageForExport(image, background, scale, border);
        return image;
    }

    public void createImageForExport(
        @NotNull BufferedImage image,
        Color background,
        double scale,
        @NotNull MyBorder border
    ) {
        // Image export not yet implemented with component-based rendering
        var g2 = image.createGraphics();
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        g2.setColor(background);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2.setColor(Color.BLACK);
        g2.drawString("Image export not yet implemented", 50, 50);
        g2.dispose();
    }

    // Handles MIDI messages during playback
    @Override
    public void meta(@NotNull MetaMessage meta) {
        if (meta.getType() == MidiMetaMessageTypes.SEQUENCE_NUMBER) {
            // We get this message when the next note is about to play.
            var data = meta.getData();
            var line = (data[0] << 8) | data[1];
            var note = (data[2] << 8) | data[3];
            playbackStateManager.setPlayingPosition(line, note);

            // Update LineComponent state for component-based rendering
            updateLineComponentPlaybackState(line, note);

            repaint();
        } else if (meta.getType() == MidiMetaMessageTypes.END_OF_TRACK) {
            // When we reach the end of the track, stop/rewind.
            PlaybackController.stop();
        }
    }

    @Handler
    public void playbackStateDidChange(
        @NotNull PlaybackStateChangedMessage message
    ) {
        if (message.getState() == PlaybackController.PlaybackState.STOPPED) {
            resetPlayback();
        }
    }

    private void resetPlayback() {
        MidiController.sequencer.setTickPosition(0);
        playbackStateManager.reset();

        // Reset LineComponent playback state
        updateLineComponentPlaybackState(-1, -1);

        repaint();
    }

    public enum ConnectionType {
        BEAM,
        TIE,
        SLUR,
    }

    private class FocusLostThread extends Thread {

        @Override
        public void run() {
            try {
                sleep(500);
            } catch (InterruptedException e) {
                Log.error(e.toString());
            }

            requestFocusInWindow();
        }
    }

    public int getPasteboardSize() {
        return clipboardManager.getSize();
    }

    @Handler
    public void onPasteboardOp(PasteboardOpMessage message) {
        // Make sure this component has focus
        if (!isFocusOwner()) {
            return;
        }

        switch (message.getOperation()) {
            case CUT -> handleCut();
            case COPY -> handleCopy();
            case DELETE -> handleDelete();
            case PASTE -> handlePaste();
        }
    }

    private void handleCut() {
        handleCopy();
        handleDelete();
    }

    private void handleCopy() {
        if (selectionManager.getSelectedNotesLine() > -1) {
            var line = composition.getLine(selectionManager.getSelectedNotesLine());
            clipboardManager.clear();

            for (var i = selectionManager.getSelectionBegin(); i <= selectionManager.getSelectionEnd(); i++) {
                clipboardManager.addNote(line.getNote(i).clone());
            }

            clipboardManager.setIntervalsCopyBuffer(line.copyIntervals(
                selectionManager.getSelectionBegin(),
                selectionManager.getSelectionEnd()
            ));
        }
    }

    public boolean canDeleteLine() {
        return selectionManager.canDeleteLine();
    }

    private void handleDelete() {
        if (selectionManager.getSelectedNotesLine() != -1) {
            var line = composition.getLine(selectionManager.getSelectedNotesLine());

            for (var i = selectionManager.getSelectionEnd(); i >= selectionManager.getSelectionBegin(); i--) {
                deleteNote(i, line);
            }

            calculateLengthenings(selectionManager.getSelectionBegin() - 1, line, true);
            calculateLengthenings(selectionManager.getSelectionBegin(), line, true);
            spellLyrics(line);
        } else if (canDeleteLine()) {
            composition.removeLine(selectionManager.getSelectedLine());
            spellLyrics();
        }

        clearSelection();
        setEditNotePositionToEnd();
        repaint();
        //mainFrame.getUndoManager().undoableEditHappened(new UndoableEditEvent(this, due));
    }

    private void handlePaste() {
        if (!clipboardManager.isEmpty()) {
            prevPasteControl = control;
            setEditNote(Note.PASTE_NOTE);
            control = Control.MOUSE;
            selectionManager.setInSelectMode(false);
            repaint();
        }
    }

    @Handler
    public void onDeselect(DeselectMessage message) {
        if (isFocusOwner()) {
            clearSelection();
            repaint();
        }
    }

    private class KeyAction extends AbstractAction {

        private final int code;

        KeyAction(int code) {
            this.code = code;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if ((mode != Mode.NOTE_EDIT) || (control != Control.KEYBOARD)) {
                return;
            }

            var editNote = editModeManager.getEditNote();

            if (editNote != null) {
                var editNotePoint = editModeManager.getEditNotePoint();
                var newEditNotePoint = editModeManager.getNewEditNotePoint();
                var line = composition.getLine(editNotePoint.getLineIndex());

                if (code == KeyEvent.VK_LEFT) {
                    if (
                        (editNotePoint.getXIndex() == 0) &&
                            ((editNotePoint.getMovement() != 0) ||
                                (line.noteCount() == 0))
                    ) {
                        if (editNotePoint.getLineIndex() > 0) {
                            editNotePoint.setLineIndex(editNotePoint.getLineIndex() - 1);
                            editNotePoint.setXIndex(
                                composition.getLine(editNotePoint.getLineIndex()).noteCount()
                            );
                            editNotePoint.setMovement(0);
                        } else {
                            return;
                        }
                    } else if (
                        (editNotePoint.getMovement() == 0) &&
                            (editNotePoint.getXIndex() < line.noteCount())
                    ) {
                        editNotePoint.setMovement(
                            (editNotePoint.getXIndex() != 0)
                                ? ((line.getNote(editNotePoint.getXIndex() - 1).getXPos() -
                                line.getNote(editNotePoint.getXIndex()).getXPos()) / 2)
                                : FIRST_NOTE_IN_LINE_MOVEMENT
                        );
                    } else {
                        editNotePoint.setMovement(0);
                        editNotePoint.setXIndex(editNotePoint.getXIndex() - 1);
                    }
                } else if (code == KeyEvent.VK_RIGHT) {
                    if (editNotePoint.getXIndex() == line.noteCount()) {
                        if (
                            editNotePoint.getLineIndex() <
                                (composition.lineCount() - 1)
                        ) {
                            editNotePoint.setLineIndex(editNotePoint.getLineIndex() + 1);
                            editNotePoint.setXIndex(0);
                            editNotePoint.setMovement(
                                (composition.getLine(editNotePoint.getLineIndex()).noteCount() == 0)
                                    ? 0
                                    : FIRST_NOTE_IN_LINE_MOVEMENT
                            );
                        } else {
                            return;
                        }
                    } else if (editNotePoint.getMovement() == 0) {
                        editNotePoint.setXIndex(editNotePoint.getXIndex() + 1);

                        if (editNotePoint.getXIndex() < line.noteCount()) {
                            editNotePoint.setMovement(
                                (editNotePoint.getXIndex() != 0)
                                    ? ((line.getNote(editNotePoint.getXIndex() - 1).getXPos() -
                                    line.getNote(editNotePoint.getXIndex()).getXPos()) / 2)
                                    : FIRST_NOTE_IN_LINE_MOVEMENT
                            );
                        } else {
                            editNotePoint.setMovement(0);
                        }
                    } else {
                        editNotePoint.setMovement(0);
                    }
                } else if (code == KeyEvent.VK_UP) {
                    if (editNote.getYPos() >= (-(STAFF_LINES_ABOVE + 2) * 2)) {
                        editNote.setYPos(editNote.getYPos() - 1);

                        // TODO: Use EditNoteChangedEvent instead.
                        mainFrame
                            .getStatusBar()
                            .setPitchString(
                                editNote.getEditNotePitchString(line)
                            );
                    }
                } else if (code == KeyEvent.VK_DOWN) {
                    if (editNote.getYPos() <= ((STAFF_LINES_BELOW + 2) * 2)) {
                        editNote.setYPos(editNote.getYPos() + 1);

                        // TODO: Use EditNoteChangedEvent instead.
                        mainFrame
                            .getStatusBar()
                            .setPitchString(
                                editNote.getEditNotePitchString(line)
                            );
                    }
                } else if (code == KeyEvent.VK_ENTER) {
                    if (editNotePoint.getXIndex() == line.noteCount()) {
                        addEditNote(line);
                        editNotePoint.setXIndex(line.noteCount());
                        editNotePoint.setMovement(0);
                    } else if (editNotePoint.getMovement() != 0) {
                        insertEditNote(
                            editNotePoint.getXIndex() +
                                ((editNotePoint.getMovement() < 0) ? 0 : 1),
                            line
                        );
                    } else {
                        modifyEditNote(editNotePoint.getXIndex(), line);
                    }
                } else if (code == KeyEvent.VK_PAGE_UP) {
                    if (editNotePoint.getLineIndex() > 0) {
                        editNotePoint.setLineIndex(editNotePoint.getLineIndex() - 1);
                        setNewEditNotePoint(
                            editNote.getXPos(),
                            editNotePoint.getLineIndex()
                        );
                        editNotePoint.setXIndex(newEditNotePoint.getXIndex());
                        editNotePoint.setMovement(newEditNotePoint.getMovement());
                    }
                } else if (code == KeyEvent.VK_PAGE_DOWN) {
                    if (
                        (editNotePoint.getLineIndex() + 1) < composition.lineCount()
                    ) {
                        editNotePoint.setLineIndex(editNotePoint.getLineIndex() + 1);
                        setNewEditNotePoint(
                            editNote.getXPos(),
                            editNotePoint.getLineIndex()
                        );
                        editNotePoint.setXIndex(newEditNotePoint.getXIndex());
                        editNotePoint.setMovement(newEditNotePoint.getMovement());
                    }
                } else if (code == KeyEvent.VK_BACK_SPACE) {
                    if (line.noteCount() > 0) {
                        deleteNote(line.noteCount() - 1, line);
                        spellLyrics(line);
                        setEditNotePositionToEnd();
                    }
                }

                calculateEditNoteXPos();
                editNote.setUpper(defaultUpperNote(editNote));
                repaint();
            }
        }
    }

    //-------------------------------scrollable-------------------------
    private static class ScorePanel extends JPanel implements Scrollable {

        private final Component content;

        ScorePanel(Component content) {
            this.content = content;
            setLayout(new GridBagLayout());
            setBorder(BorderFactory.createEmptyBorder());
            setBackground(Color.lightGray);
            add(content);
        }

        @Override
        @NotNull
        public Dimension getPreferredSize() {
            var contentSize = content.getPreferredSize();
            var parentSize = getParent().getSize();
            var width = Math.max(contentSize.width, parentSize.width);
            return new Dimension(width, contentSize.height);
        }

        @Override
        @NotNull
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction
        ) {
            return 30;
        }

        @Override
        public int getScrollableBlockIncrement(
            Rectangle visibleRect,
            int orientation,
            int direction
        ) {
            return (orientation == SwingConstants.VERTICAL)
                ? (visibleRect.height - 10)
                : (visibleRect.width - 20);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return false;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
