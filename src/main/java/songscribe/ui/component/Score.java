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
import java.util.EnumMap;
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

import kotlin.Pair;
import net.engio.mbassy.listener.Handler;
import org.jfree.svg.SVGGraphics2D;
import org.jfree.svg.SVGUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
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
import songscribe.ui.layout.LayoutManager;
import songscribe.ui.dialog.LineWidthChangeDialog;
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
import songscribe.ui.message.ToggleTieOrSlurMessage;
import songscribe.ui.message.ToggleTrillMessage;
import songscribe.ui.message.ToggleTupletMessage;
import songscribe.ui.message.UpdateEditNoteMessage;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.MidiMetaMessageTypes;
import songscribe.ui.playback.PlayNoteThread;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.playback.PlaybackStateChangedMessage;
import songscribe.ui.renderer.FughettaRenderer;
import songscribe.ui.renderer.Renderer;
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
        MusicChangeListener {

    private static final Logger log = LoggerFactory.getLogger(Score.class);

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

    // How far from the cursor hotspot notes are painted when editing
    private static final Point CURSOR_OFFSET = new Point(0, -1);

    // The default right padding between a note and the next note in a staff line
    private static final EnumMap<NoteType, Integer> NOTE_PADDING =
        new EnumMap<>(NoteType.class);

    static {
        NOTE_PADDING.put(NoteType.SEMIBREVE, 70);
        NOTE_PADDING.put(NoteType.MINIM, 50);
        NOTE_PADDING.put(NoteType.CROTCHET, 35);
        NOTE_PADDING.put(NoteType.QUAVER, 25);
        NOTE_PADDING.put(NoteType.SEMIQUAVER, 25);
        NOTE_PADDING.put(NoteType.DEMI_SEMIQUAVER, 25);
        NOTE_PADDING.put(NoteType.SEMIBREVE_REST, 70);
        NOTE_PADDING.put(NoteType.MINIM_REST, 50);
        NOTE_PADDING.put(NoteType.CROTCHET_REST, 35);
        NOTE_PADDING.put(NoteType.QUAVER_REST, 25);
        NOTE_PADDING.put(NoteType.SEMIQUAVER_REST, 25);
        NOTE_PADDING.put(NoteType.DEMI_SEMIQUAVER_REST, 25);
        NOTE_PADDING.put(NoteType.GRACE_QUAVER, 30);
        NOTE_PADDING.put(NoteType.GRACE_SEMIQUAVER, 50);
        NOTE_PADDING.put(NoteType.GLISSANDO, 0);
        NOTE_PADDING.put(NoteType.REPEAT_LEFT, 25);
        NOTE_PADDING.put(NoteType.REPEAT_RIGHT, 25);
        NOTE_PADDING.put(NoteType.REPEAT_LEFT_RIGHT, 25);
        NOTE_PADDING.put(NoteType.BREATH_MARK, 15);
        NOTE_PADDING.put(NoteType.SINGLE_BARLINE, 60);
        NOTE_PADDING.put(NoteType.DOUBLE_BARLINE, 60);
        NOTE_PADDING.put(NoteType.FINAL_DOUBLE_BARLINE, 60);
        NOTE_PADDING.put(NoteType.PASTE, 0);
    }

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

    // The number of pixels reserved for a single accidental
    private static final int ACCIDENTAL_WIDTH = 7;

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

    // The offset from the left edge of a staff (line) to the first note on the staff
    private static int firstNoteX = 100;

    // Edit popup
    private JPopupMenu popup = null;
    private Control prevPasteControl = null;

    private SAXParser saxParser = null;
    private Dimension sheetSize = new Dimension();

    // The index of the currently playing line/note
    private int playingLine = -1;
    private int playingNote = -1;

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

    // When a selection is made, we determine whether a tie or slur can be added to or removed from
    // the selection. The result of that calculation is stored in this context.
    @Nullable
    private TieSlurContext tieSlurContext = null;

    // Controls whether the edit note is drawn in edit mode, based on whether
    // the mouse is over the score is mouse mode.
    private boolean editNoteIsVisible = false;

    // The music element that will be inserted
    private Note editNote = null;
    private final NotePosition newEditNotePoint = new NotePosition();
    private final NotePosition editNotePoint = new NotePosition();

    // True if the user is dragging the mouse to select notes
    private boolean inSelectMode = false;

    // The index of the selected staff line. When notes are selected, this will be -1
    // and selectedNotesLine will be the index of the staff line where the notes are selected.
    private int selectedLine = -1;

    // The index of the staff line on which notes are selected. When a staff line is selected,
    // selectedNotesLine will be -1 and selectedLine will be the index of the staff line.
    private int selectedNotesLine = -1;

    // The index (within the line) of the first and last (inclusive) selected note.
    // If a single note is selected, selectionBegin and selectionEnd will be the same.
    private int selectionBegin = 0;
    private int selectionEnd = 0;

    // True if the user is dragging the mouse to select notes
    private boolean startedDrag = false;

    // The rectangle defined by the point where the drag started and the current mouse position
    private final Rectangle dragRectangle = new Rectangle();

    // The point where the drag started
    private final Point dragStart = new Point();

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

    // The Renderer subclass that renders the music score
    private Renderer renderer = null;

    // Manages the vertical layout of all sections in the score.
    private LayoutManager layoutManager = null;

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

    // When notes are copied, they are stored here
    private final ArrayList<Note> pasteboard = new ArrayList<>();

    // When notes are copied, any associated interval sets (e.g. tie/slur) are stored here
    private IntervalSet[] intervalSetsCopyBuffer = null;

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

    public Score(@NotNull IMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        var property = mainFrame
            .getProperties()
            .getProperty(Constants.CONTROL_PROP);
        control = (property != null)
            ? Control.valueOf(property)
            : Control.MOUSE;

        try {
            // TODO: Change to BravuraRenderer
            renderer = new FughettaRenderer(this);
        } catch (Exception e) {
            mainFrame.showErrorMessage(
                "Sorry, but the app could not open a necessary font and has to quit."
            );
            System.exit(0);
        }

        layoutManager = new LayoutManager(this);

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
        try {
            var g2 = new SVGGraphics2D(getSheetWidth(), getSheetHeight());
            renderer.drawScore(g2, false, 1d);
            SVGUtils.writeToSVG(outputFile, g2.getSVGElement());

            if (isGUI) {
                FileUtils.openExportFile(mainFrame, outputFile);
            }
        } catch (IOException e1) {
            if (isGUI) {
                mainFrame.showErrorMessage(
                    "An unexpected error occurred and could not export as SVG."
                );
            }
        }
    }

    public void init() {
        composition = new Composition(mainFrame);
        initView();
        initAdjustments();
        initMargin();
        initScorePanel();

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

    public static int calculateLastNoteXPos(@NotNull Line line, Note note) {
        if (line.noteCount() == 0) {
            return firstNoteX;
        }

        var lastNote = line.getNote(line.noteCount() - 1);

        return (
            lastNote.getXPos() +
            Math.round(
                (NOTE_PADDING.get(lastNote.getNoteType()) +
                    (note.getAccidental().getWidthFactor() * ACCIDENTAL_WIDTH) +
                    (note.isAccidentalInParentheses() ? 8 : 0)) *
                line.getNoteDistChangeRatio()
            )
        );
        // lastNote.getXPos()+Math.round((ND+note.getAccidental().getNb()*FIX_PREFIX_WIDTH)*line
        // .getNoteDistChangeRatio());
    }

    public static void setFirstNoteX(int firstNoteX) {
        Score.firstNoteX = firstNoteX;
    }

    private void initKeys() {
        var keyCodes = new int[] {
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
        // Invalidate layout and measure with a temporary graphics context
        layoutManager.invalidate();

        // Only measure if composition is set (not during initialization)
        if (composition != null) {
            var img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            var g2 = img.createGraphics();

            try {
                layoutManager.measure(g2);
            } finally {
                g2.dispose();
            }

            updateLayoutFromManager();
        }
    }

    /**
     * Updates middleLineY and rowHeight from LayoutManager.
     * <p>
     * Called after layout measurement to sync the cached values used
     * throughout Score for hit-testing and positioning.
     */
    private void updateLayoutFromManager() {
        middleLineY = layoutManager.getMiddleLineY();
        rowHeight = layoutManager.getRowHeight();
    }

    public JScrollPane getScoreScrollPane() {
        return scrollPane;
    }

    public boolean isNoteSelected(int xIndex, int line) {
        return (
            (selectedNotesLine == line) &&
            (selectionBegin <= xIndex) &&
            (xIndex <= selectionEnd)
        );
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        var g2 = (Graphics2D) g;
        g2.setColor(Color.white);
        g2.fillRect(0, 0, marginPanel.getWidth(), marginPanel.getHeight());

        // Measure layout before drawing if invalidated
        if (!layoutManager.isValid()) {
            layoutManager.measure(g2);
            updateLayoutFromManager();
        }

        renderer.drawScore(g2, true, 1d);
        drawEditElements(g2);
        drawSelectionRect(g2);
    }

    private void drawEditElements(Graphics2D g2) {
        if (mode == Mode.NOTE_EDIT) {
            // We can insert in edit mode only if:
            // - A drag is not in process
            // - Shift is not pressed
            // - The sequencer is not playing
            if (
                (editNote != null) &&
                ((control == Control.KEYBOARD) || editNoteIsVisible) &&
                !startedDrag &&
                !shiftPressed &&
                !MidiController.isPlaying()
            ) {
                g2.setColor(EDIT_NOTE_COLOR);

                //noinspection ObjectEquality
                if (editNote != Note.GLISSANDO_NOTE) {
                    var x = editNote.getXPos();

                    if (x > (composition.getLineWidth() - 10)) {
                        editNote.setXPos(composition.getLineWidth() - 12);
                    }

                    renderer.paintNote(
                        g2,
                        editNote,
                        editNotePoint.lineIndex,
                        false
                    );
                    editNote.setXPos(x);
                } else if (editNotePoint.xIndex > 0) {
                    renderer.drawGlissando(
                        g2,
                        editNotePoint.xIndex - 1,
                        new Note.Glissando(editNote.getYPos()),
                        editNotePoint.lineIndex
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
        if (startedDrag) {
            g2.setColor(SELECTION_RECT_FILL_COLOR);
            g2.fill(dragRectangle);

            g2.setStroke(SELECTION_RECT_STROKE);
            g2.setColor(SELECTION_STROKE_COLOR);
            g2.draw(dragRectangle);
        }
    }

    public int getNoteYPos(int yPos, int line) {
        return (int) (middleLineY +
            (yPos * NOTE_Y_OFFSET) +
            (line * rowHeight));
    }

    public int getUnderLyricsYPos() {
        return layoutManager.getUnderLyricsYPos();
    }

    public Note getEditNote() {
        return editNote;
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
            if (this.editNote != null) {
                editNote.setYPos(this.editNote.getYPos());
                editNote.setXPos(this.editNote.getXPos());
            } else {
                this.editNote = editNote;
                setEditNotePositionToEnd();
            }

            editNote.setUpper(defaultUpperNote(editNote));
        }

        this.editNote = editNote;
        repaint();
    }

    @Handler
    public void onInsertLine(@NotNull InsertLineMessage message) {
        var shift = message.getShift();

        if ((selectedLine != -1) || (shift == InsertLineAction.ADD)) {
            var index = (shift >= 0)
                ? (selectedLine + shift)
                : InsertLineAction.ADD;
            composition.addLine(index, new Line());
            clearSelection();
            mainFrame.setDocumentModified(true);
            repaint();
        } else {
            mainFrame.showErrorMessage("Please select a line first.");
        }
    }

    public int getPlayingLine() {
        return playingLine;
    }

    public int getPlayingNote() {
        return playingNote;
    }

    public void clearSelection() {
        selectedLine = -1;
        selectedNotesLine = -1;
        selectionChanged();
    }

    private void selectionChanged() {
        MessageCenter.post(new MusicSelectionChangedMessage(this));
    }

    private boolean noteWasModified(Line line, int noteIndex) {
        clearSelection();

        // If the active note is glissando, it needs different handling
        if (editNote.getNoteType() == NoteType.GLISSANDO) {
            if (editNotePoint.xIndex > 0) {
                line
                    .getNote(editNotePoint.xIndex - 1)
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
                        ? calculateLastNoteXPos(line, pasteboard.getFirst())
                        : line.getNote(noteIndex).getXPos()) -
                pasteboard.getFirst().getXPos();
            var copySize = pasteboard.size();

            for (var i = 0; i < copySize; i++) {
                var note = pasteboard.get(i);
                note.setXPos(note.getXPos() + diff);
                line.addNote(noteIndex + i, note.clone());
            }

            line.pasteIntervals(intervalSetsCopyBuffer, noteIndex);
            var lastNote = pasteboard.get(copySize - 1);
            var shift =
                (Math.round(
                        (NOTE_PADDING.get(lastNote.getNoteType()) +
                            (lastNote.getAccidental().getWidthFactor() *
                                ACCIDENTAL_WIDTH)) *
                        line.getNoteDistChangeRatio()
                    ) +
                    lastNote.getXPos()) -
                pasteboard.getFirst().getXPos();
            //int shift = Math.round((ND+lastNote.getAccidental().getNb()*FIX_PREFIX_WIDTH)*line
            // .getNoteDistChangeRatio())+lastNote.getXPos()-pasteboard.get(0).getXPos();

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

            inSelectMode = true;
            return true;
        }

        return false;
    }

    private void calculateEditNoteXPos() {
        if (editNote == null) {
            return;
        }

        var line = composition.getLine(editNotePoint.lineIndex);

        if (line.noteCount() == editNotePoint.xIndex) {
            editNote.setXPos(calculateLastNoteXPos(line, editNote));
        } else {
            var note = line.getNote(editNotePoint.xIndex);
            editNote.setXPos(note.getXPos() + editNotePoint.movement);
        }
    }

    public void setEditNotePositionToEnd() {
        editNotePoint.movement = 0;
        editNotePoint.lineIndex = composition.lineCount() - 1;
        editNotePoint.xIndex = composition
            .getLine(editNotePoint.lineIndex)
            .noteCount();
        calculateEditNoteXPos();
    }

    private void editNoteDidChange(Line line, int noteIndex) {
        //mainFrame.getUndoManager().undoableEditHappened(new UndoableEditEvent(this, new
        // ModifyUndoableEdit(oldNote, oldNoteInfo, xIndex)));
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
        if (editNote == null) {
            return;
        }

        if (noteWasModified(line, line.noteCount())) {
            editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        editNote.setXPos(calculateLastNoteXPos(line, editNote));
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
            (editNote.getAccidental().getWidthFactor() * ACCIDENTAL_WIDTH)
        );
        line.addNote(xIndex, editNote);
        var shift = Math.round(
            (NOTE_PADDING.get(editNote.getNoteType()) +
                (editNote.getAccidental().getWidthFactor() *
                    ACCIDENTAL_WIDTH)) *
            line.getNoteDistChangeRatio()
        );
        //int shift = Math.round((ND+activeNote.getAccidental().getNb()*FIX_PREFIX_WIDTH)*line
        // .getNoteDistChangeRatio());

        for (var i = xIndex + 1; i < line.noteCount(); i++) {
            line.getNote(i).setXPos(line.getNote(i).getXPos() + shift);
        }

        editNoteDidChange(line, xIndex);
    }

    private void modifyEditNote(int xIndex, Line line) {
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
                ACCIDENTAL_WIDTH)
        );
        var shift = Math.round(
            ((NOTE_PADDING.get(editNote.getNoteType()) -
                    NOTE_PADDING.get(oldNote.getNoteType())) +
                ((editNote.getAccidental().getWidthFactor() -
                        oldNote.getAccidental().getWidthFactor()) *
                    ACCIDENTAL_WIDTH)) *
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
        if ((selectedNotesLine == -1) || (selectionBegin == -1)) {
            return 0;
        }

        // If the start and end index are the same, that is a single note
        return (selectionEnd - selectionBegin) + 1;
    }

    private boolean shouldConnectSelection(@NotNull IntervalSet intervals) {
        // Figure out if the start and end are part of the same connection.
        // If so, we will remove the connection, otherwise we will add it.
        var beginInterval = intervals.findInterval(selectionBegin);
        var endInterval = intervals.findInterval(selectionEnd);

        //noinspection ObjectEquality
        return (beginInterval == null) || (beginInterval != endInterval);
    }

    public boolean canToggleBeaming() {
        if (getSelectionSize() < 2) {
            return false;
        }

        var line = composition.getLine(selectedNotesLine);

        // The selection is beamable only if all notes are beamable
        return IntStream.rangeClosed(selectionBegin, selectionEnd).allMatch(
            i -> line.getNote(i).getNoteType().isBeamable()
        );
    }

    @Handler
    public void onToggleBeaming(ToggleBeamMessage message) {
        toggleBeaming();
    }

    // Assumes that canToggleBeamingOfSelection() is true
    public void toggleBeaming() {
        var line = composition.getLine(selectedNotesLine);
        var beamings = line.getBeamings();

        if (shouldConnectSelection(beamings)) {
            beamings.addInterval(selectionBegin, selectionEnd);
            calculateLengthenings(selectionBegin, line, true);
        } else {
            beamings.removeInterval(selectionBegin, selectionEnd);
            calculateLengthenings(selectionBegin, line, true);
            calculateLengthenings(selectionEnd, line, true);
        }

        composition.setModified(true);
        repaint();
    }

    public TieSlurContext getTieOrSlurContext() {
        return tieSlurContext;
    }

    /*
     * This method assumes getSelectionSize() >= 2. It sets a TieSlurContext:
     *
     *   - canToggle indicates whether the selection can toggle a tie/slur at all.
     *     If this is false, the other values don't matter.
     *
     *   - If canToggle is true, and the selection is part of the same tie or slur,
     *     intervals will be the IntervalSet corresponding to that tie or slur.
     *     If none of the notes in the selection are part of a tie or slur,
     *     intervals will be null.
     *
     *   - isTie indicates whether the selection is part of a tie (true) or slur (false).
     */
    public boolean canToggleTieOrSlur() {
        // Ties/slurs can only be toggled if:
        //   - All of the notes in the selection are real notes (no grace notes or rests)
        //   - The selection >= 2 notes
        //
        // Ties/slurs can only be added if:
        //   - None of the notes are currently in a tie or slur
        //
        // Ties/slurs can only be removed if:
        //   - The selection is part of a single tie/slur

        var line = composition.getLine(selectedNotesLine);
        var ties = line.getTies();
        var slurs = line.getSlurs();
        Interval firstTieInterval = null;
        Interval firstSlurInterval = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            var note = line.getNote(i);

            if (!note.getNoteType().isRealNote()) {
                tieSlurContext = new TieSlurContext(false, null, false);
                return false;
            }

            if (i == selectionBegin) {
                firstTieInterval = ties.findInterval(i);
                firstSlurInterval = slurs.findInterval(i);
            } else {
                // If the current note is part of a different tie/slur than the first note,
                // the selection cannot be toggled.
                //noinspection ObjectEquality
                if (
                    (ties.findInterval(i) != firstTieInterval) ||
                    (slurs.findInterval(i) != firstSlurInterval)
                ) {
                    tieSlurContext = new TieSlurContext(false, null, false);
                    return false;
                }
            }
        }

        // If we get to here, a tie/slur can either be added or removed
        IntervalSet set = null;
        Boolean isTie = null;

        if (firstTieInterval != null) {
            set = ties;
            isTie = true;
        } else if (firstSlurInterval != null) {
            set = slurs;
            isTie = false;
        }

        tieSlurContext = new TieSlurContext(true, set, isTie);
        return true;
    }

    @Handler
    public void onToggleTieOrSlur(ToggleTieOrSlurMessage message) {
        toggleTieOrSlur();
    }

    // This method assumes canToggleTieOrSlur() is true
    public void toggleTieOrSlur() {
        // Get the context if necessary
        if (tieSlurContext == null) {
            canToggleTieOrSlur();
        }

        var line = composition.getLine(selectedNotesLine);
        var intervals = tieSlurContext.intervals();

        if (intervals != null) {
            intervals.removeInterval(selectionBegin, selectionEnd);
        } else {
            // If we are adding a tie or slur, we need to determine which one
            // based on the pitches of the notes in the selection.
            var isTie =
                line
                    .getNotes(selectionBegin, selectionEnd)
                    .stream()
                    .map(Note::getPitch)
                    .distinct()
                    .count() ==
                1;

            intervals = isTie ? line.getTies() : line.getSlurs();
            intervals.addInterval(selectionBegin, selectionEnd);
        }

        // Reset the context so it is recalculated next time
        tieSlurContext = null;
        composition.setModified(true);
        repaint();
    }

    /*
     * This method assumes getSelectionSize() >= 2. It returns a Pair of Booleans:
     *   - The first Boolean indicates whether the selection can be tupleted/untupleted.
     *   - The second Boolean indicates whether the selection is currently tupleted.
     */
    @NotNull
    @Contract(" -> new")
    @SuppressWarnings("ObjectEquality")
    public Pair<Boolean, Boolean> canToggleTuplet() {
        // Tuplets can only be toggled if all notes are real notes (no grace notes or rests)
        // and are either in the same tuplet or are not in any tuplet.
        var line = composition.getLine(selectedNotesLine);
        var tuplets = line.getTuplets();
        Interval firstInterval = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
            if (!line.getNote(i).getNoteType().isRealNote()) {
                return new Pair<>(false, false);
            }

            var currentInterval = tuplets.findInterval(i);

            // If this is the first note, remember the current interval. This is what
            // we will compare against.
            // Otherwise, if the current interval does not match the previous one,
            // the selection cannot be tupleted/untupleted.
            if (i == selectionBegin) {
                firstInterval = currentInterval;
            } else if (currentInterval != firstInterval) {
                return new Pair<>(false, false);
            }
        }

        return new Pair<>(true, firstInterval != null);
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
        var line = composition.getLine(selectedNotesLine);
        var tuplets = line.getTuplets();
        var interval = tuplets.findInterval(selectionBegin);

        if ((interval == null) || (tupletSize > 0)) {
            // If the selection is not in a tuplet, add a new one
            if (interval == null) {
                interval = tuplets.addInterval(selectionBegin, selectionEnd);
            }

            TupletIntervalData.setGrade(interval, tupletSize);
        } else {
            tuplets.removeInterval(selectionBegin, selectionEnd);
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
        var line = composition.getLine(selectedNotesLine);
        var intervalSet = crescendo
            ? line.getCrescendos()
            : line.getDiminuendos();
        intervalSet.addInterval(selectionBegin, selectionEnd);
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
        var line = composition.getLine(selectedNotesLine);
        var crescendos = line.getCrescendos();
        var diminuendos = line.getDiminuendos();
        var crescendoIntervals = new ArrayList<Interval>();
        var diminuendoIntervals = new ArrayList<Interval>();

        for (var i = selectionBegin; i <= selectionEnd; i++) {
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
        if (selectedNotesLine == -1) {
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
        var line = composition.getLine(selectedNotesLine);
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
        var line = composition.getLine(selectedNotesLine);
        var repeatExists = IntStream.rangeClosed(
            selectionBegin,
            selectionEnd
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

        line.getFirstSecondEndings().addInterval(selectionBegin, selectionEnd);
        composition.setModified(true);
        repaint();
    }

    public void removeFirstSecondEnding() {
        var line = composition.getLine(selectedNotesLine);
        line
            .getFirstSecondEndings()
            .removeInterval(selectionBegin, selectionEnd);
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
        if (selectedNotesLine == -1) {
            return false;
        }

        // A trill can only be applied if one or more real notes (no grace notes or rests)
        // are in the selection.
        var line = composition.getLine(selectedNotesLine);
        return line
            .getNotes(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(note -> note.getNoteType().isRealNote());
    }

    @Handler
    public void onToggleTrill(ToggleTrillMessage message) {
        toggleTrill();
    }

    public void toggleTrill() {
        var line = composition.getLine(selectedNotesLine);

        for (var note : line.getNotes(selectionBegin, selectionEnd)) {
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
        var line = composition.getLine(selectedNotesLine);
        var note = line.getNote(selectionBegin);
        note.setForceSyllable(!note.isForceSyllable());
        spellLyrics(line);
        composition.setModified(true);
        repaint();
    }

    public boolean canFlipPartialBeamOrientation() {
        if (getSelectionSize() != 1) {
            return false;
        }

        var line = composition.getLine(selectedNotesLine);
        return line.getBeamings().isInsideAnyInterval(selectionBegin);
    }

    @Handler
    public void onFlipPartialBeams(FlipPartialBeamsMessage message) {
        flipPartialBeamOrientation();
    }

    public void flipPartialBeamOrientation() {
        try {
            if ((selectedNotesLine == -1) || (selectionBegin != selectionEnd)) {
                throw new IllegalArgumentException();
            }

            var line = composition.getLine(selectedNotesLine);

            if (!line.getBeamings().isInsideAnyInterval(selectionBegin)) {
                throw new IllegalArgumentException();
            }

            var note = line.getNote(selectionBegin);
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
        var line = composition.getLine(selectedNotesLine);

        return line
            .getNotes(selectionBegin, selectionEnd)
            .stream()
            .anyMatch(note -> !note.getNoteType().isRest());
    }

    @Handler
    public void onFlipStemDirection(FlipStemDirectionMessage message) {
        flipStemDirection();
    }

    public void flipStemDirection() {
        if (selectedNotesLine == -1) {
            mainFrame.showInfoMessage(
                "You must select one or more notes in order to flip their stem direction."
            );
            return;
        }

        var line = composition.getLine(selectedNotesLine);

        for (var note : line.getNotes(selectionBegin, selectionEnd)) {
            note.setUpper(!note.isUpper());
        }

        Interval lastInterval = null;

        for (var i = selectionBegin; i <= selectionEnd; i++) {
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
        selectedLine = -1;
        selectedNotesLine = -1;
        selectionBegin = -1;
        selectionEnd = -1;

        for (
            var lineIndex = 0;
            lineIndex < composition.lineCount();
            lineIndex++
        ) {
            if ((selectedNotesLine != -1) && (selectedNotesLine != lineIndex)) {
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
                    (fromRectangle && dragRectangle.intersects(helper)) ||
                    (!fromRectangle && helper.contains(dragStart))
                ) {
                    selectedNotesLine = lineIndex;

                    if (selectionBegin == -1) {
                        selectionBegin = noteIndex;
                    }

                    selectionEnd = noteIndex;
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

    public Composition getComposition() {
        return composition;
    }

    @NotNull
    public LayoutManager getLayoutManager() {
        return layoutManager;
    }

    @Handler
    public void onNewDocument(NewFileMessage message) {
        setComposition(new Composition(mainFrame));
        requestFocusInWindow();
    }

    @Handler
    public void onLayoutChanged(@NotNull LayoutChangeMessage message) {
        if (message.getHeightChanged()) {
            layoutManager.invalidateFromSection(message.getSection());
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
        playingLine = -1;
        playingNote = -1;
        selectedNotesLine = -1;
        setLineWidth(composition.getLineWidth());

        // global calculate lengthening
        for (var l = 0; l < composition.lineCount(); l++) {
            var line = composition.getLine(l);

            for (var li = line.getBeamings().listIterator(); li.hasNext();) {
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
                    if (
                        (lyrics.charAt(i + 1) == '-') ||
                        (lyrics.charAt(i + 1) == '\n')
                    ) {
                        syllableRelation = Note.SyllableRelation.ONE_DASH;
                        i++;
                    } else {
                        syllableRelation = Note.SyllableRelation.DASH;
                    }

                    noteIndex = setSyllableForNextNote(
                        line,
                        noteIndex,
                        syllable,
                        syllableRelation
                    );
                } else { // c == '_'
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

                    if (eus < composition.getLyrics().length()) {
                        var eusc = composition.getLyrics().charAt(eus);
                        syllableRelation = ((eusc == ' ') ||
                                (eusc == '\n') ||
                                (eusc == '-'))
                            ? Note.SyllableRelation.EXTENDER
                            : Note.SyllableRelation.DASH;
                    } else {
                        syllableRelation = Note.SyllableRelation.EXTENDER;
                    }

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

                    if (
                        !syllable.equals(Constants.UNDERSCORE) &&
                        (syllableRelation == Note.SyllableRelation.DASH)
                    ) {
                        noteIndex = setSyllableForNextNote(
                            line,
                            noteIndex,
                            Constants.UNDERSCORE,
                            Note.SyllableRelation.DASH
                        );
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
        if ((selectedNotesLine != -1) || (selectionBegin != selectionEnd)) {
            return composition
                .getLine(selectedNotesLine)
                .getNote(selectionBegin);
        }

        return null;
    }

    public int getStartY() {
        return layoutManager.getContentStartY();
    }

    public Dimension getSheetSize() {
        return sheetSize;
    }

    public int getSheetWidth() {
        return composition.getLineWidth();
    }

    public int getSheetHeight() {
        // If layout not valid, do a measurement pass
        if (!layoutManager.isValid()) {
            var image = new BufferedImage(
                sheetSize.width,
                sheetSize.height,
                BufferedImage.TYPE_INT_RGB
            );
            var g2 = image.createGraphics();
            layoutManager.measure(g2);
            g2.dispose();
        }

        return layoutManager.getTotalHeight();
    }

    public void drawWidthIfWiderLine(@NotNull Line line, boolean strict) {
        if (line.noteCount() > 1) {
            var endNote = line.getNote(line.noteCount() - 1);
            float idealSpace;

            if (strict) {
                idealSpace = endNote.getRealUpNoteRect().width;
            } else {
                idealSpace = (NOTE_PADDING.get(endNote.getNoteType()) *
                    line.getNoteDistChangeRatio()) +
                20;
                //idealSpace = ND*line.getNoteDistChangeRatio()+20;
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
        this.inSelectMode = inSelectMode;
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

    public int getLeadingKeysPos() {
        return leadingKeysPos;
    }

    public void setLeadingKeysPos(int leadingKeysPos) {
        this.leadingKeysPos = leadingKeysPos;
    }

    public int getRowHeight() {
        return rowHeight;
    }

    public void setRowHeight(int rowHeight) {
        this.rowHeight = rowHeight;
    }

    public int getMiddleLineY() {
        return middleLineY;
    }

    public void setMiddleLineY(int middleLineY) {
        this.middleLineY = middleLineY;
    }

    public Renderer getRenderer() {
        return renderer;
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

    public int getSelectedLine() {
        return selectedLine;
    }

    @Nullable
    public NoteSelection getSelection() {
        if (selectedLine != -1) {
            var line = composition.getLine(selectedLine);
            return new NoteSelection(line, 0, line.noteCount() - 1);
        }

        if (selectedNotesLine != -1) {
            return new NoteSelection(
                composition.getLine(selectedNotesLine),
                selectionBegin,
                selectionEnd
            );
        }

        return null;
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
                new byte[] {
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

        noteLoop:for (
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
                new byte[] {
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
            new byte[] {
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
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        requestFocusInWindow();

        if (MidiController.isPlaying()) {
            return;
        }

        if (inSelectMode || e.isShiftDown()) {
            clearSelection();
            resetPlayback();
            dragStart.setLocation(e.getX(), e.getY());
            dragRectangle.setBounds(0, 0, 0, 0);
            updateSelection(e, false);
            selectionChanged();

            // If a single note was clicked, play it
            if (getSelectionSize() == 1) {
                var note = composition
                    .getLine(selectedNotesLine)
                    .getNote(selectionBegin);

                if (note.getNoteType().isNote()) {
                    new PlayNoteThread(note.getPitch()).start();
                }
            }
        } else if (control == Control.MOUSE) {
            var line = composition.getLine(editNotePoint.lineIndex);

            if (editNotePoint.xIndex == line.noteCount()) {
                addEditNote(line);
            } else if (editNotePoint.movement != 0) {
                insertEditNote(
                    editNotePoint.xIndex +
                    ((editNotePoint.movement < 0) ? 0 : 1),
                    line
                );
            } else {
                modifyEditNote(editNotePoint.xIndex, line);
            }
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // Don't allow selection during playback
        if (dragDisabled || MidiController.isPlaying()) {
            return;
        }

        var recalculateSelection = startedDrag;

        if (!startedDrag) {
            startedDrag = true;
            dragStart.setLocation(e.getX(), e.getY());
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

        dragRectangle.setBounds(
            Math.min(dragStart.x, x),
            Math.min(dragStart.y, y),
            Math.abs(dragStart.x - x),
            Math.abs(dragStart.y - y)
        );
        dragRectangle.setBounds(
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
            (selectionBegin == -1) &&
            (Math.abs(
                    e.getY() -
                    getNoteYPos(
                        0,
                        (e.getY() - composition.getTopPadding()) / rowHeight
                    )
                ) <=
                (2 * STAFF_LINE_Y_OFFSET))
        ) {
            selectedLine = (e.getY() - composition.getTopPadding()) / rowHeight;

            if (
                (selectedLine < 0) || (selectedLine >= composition.lineCount())
            ) {
                selectedLine = -1;
            }
        }

        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (
            (editNote == null) ||
            (control != Control.MOUSE) ||
            (mode != Mode.NOTE_EDIT)
        ) {
            return;
        }

        var x = e.getX() + CURSOR_OFFSET.x;
        var y = e.getY() + CURSOR_OFFSET.y;
        newEditNotePoint.lineIndex = (y - composition.getTopPadding()) /
        rowHeight;

        if (
            (newEditNotePoint.lineIndex < 0) ||
            (newEditNotePoint.lineIndex >= composition.lineCount())
        ) {
            return;
        }

        newEditNotePoint.y = (int) ((y -
                composition.getTopPadding() -
                (newEditNotePoint.lineIndex * rowHeight) -
                (NOTE_Y_OFFSET / 2)) /
            NOTE_Y_OFFSET);

        if (
            (newEditNotePoint.y <= 0) ||
            (newEditNotePoint.y >
                (((STAFF_LINES_BELOW + STAFF_LINE_COUNT + STAFF_LINES_ABOVE) *
                        2) +
                    1))
        ) {
            return;
        }

        setNewEditNotePoint(x, newEditNotePoint.lineIndex);
        editNote.setYPos(newEditNotePoint.y - ((STAFF_LINES_ABOVE + 3) * 2));

        if (!newEditNotePoint.equals(editNotePoint)) {
            editNotePoint.xIndex = newEditNotePoint.xIndex;
            editNotePoint.y = newEditNotePoint.y;
            editNotePoint.movement = newEditNotePoint.movement;
            editNotePoint.lineIndex = newEditNotePoint.lineIndex;
            editNote.setUpper(defaultUpperNote(editNote));
            calculateEditNoteXPos();
            repaint();
        }

        // TODO: Use EditNoteChangedEvent instead.
        mainFrame
            .getStatusBar()
            .setPitchString(
                editNote.getEditNotePitchString(
                    composition.getLine(editNotePoint.lineIndex)
                )
            );
    }

    private void setNewEditNotePoint(int xPos, int lineIndex) {
        var x = xPos - Note.HOT_SPOT.x;
        var foundX = 0;
        var line = composition.getLine(lineIndex);

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
                newEditNotePoint.movement = 0;
                newEditNotePoint.xIndex = 0;
            } else if (x <= line.getNote(0).getXPos()) {
                newEditNotePoint.movement = FIRST_NOTE_IN_LINE_MOVEMENT;
                newEditNotePoint.xIndex = 0;
            } else {
                newEditNotePoint.movement = 0;
                newEditNotePoint.xIndex = line.noteCount();
            }
        } else {
            var period =
                ((x - line.getNote(foundX - 1).getXPos()) * 4) /
                (line.getNote(foundX).getXPos() -
                    line.getNote(foundX - 1).getXPos());
            //if(foundX==endNote-1 && period!=0) period=3;
            switch (period) {
                case 0 -> {
                    newEditNotePoint.movement = 0;
                    newEditNotePoint.xIndex = foundX - 1;
                }
                case 1, 2 -> {
                    newEditNotePoint.movement = -(line
                            .getNote(foundX)
                            .getXPos() -
                        line.getNote(foundX - 1).getXPos()) /
                    2;
                    newEditNotePoint.xIndex = foundX;
                }
                case 3, 4 -> {
                    newEditNotePoint.movement = 0;
                    newEditNotePoint.xIndex = foundX;
                }
            }
        }
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
        if (e.isPopupTrigger()) {
            popup.show(this, e.getX(), e.getY());
        }
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
        if (e.isPopupTrigger()) {
            popup.show(this, e.getX(), e.getY());
        } else if (startedDrag) {
            startedDrag = false;
            repaint();
        }

        selectionChanged();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (
            !editNoteIsVisible &&
            (control == Control.MOUSE) &&
            (mode == Mode.NOTE_EDIT)
        ) {
            editNoteIsVisible = true;
            //setCursor(activeNote==null ? Cursor.getDefaultCursor() : emptyCursor);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (
            editNoteIsVisible &&
            (control == Control.MOUSE) &&
            (mode == Mode.NOTE_EDIT)
        ) {
            editNoteIsVisible = false;
            repaint();
            //setCursor(Cursor.getDefaultCursor());
        }
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
    public void keyTyped(KeyEvent e) {}

    //***********************
    // FocusListener methods
    //***********************
    @Override
    public void focusGained(FocusEvent e) {}

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
        g2.translate(border.getLeft(), border.getTop());
        renderer.drawScore(g2, false, scale);
        g2.dispose();
    }

    // Handles MIDI messages during playback
    @Override
    public void meta(@NotNull MetaMessage meta) {
        if (meta.getType() == MidiMetaMessageTypes.SEQUENCE_NUMBER) {
            // We get this message when the next note is about to play.
            var data = meta.getData();
            playingLine = (data[0] << 8) | data[1];
            playingNote = (data[2] << 8) | data[3];
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
        playingLine = -1;
        playingNote = -1;
        repaint();
    }

    public enum ConnectionType {
        BEAM,
        TIE,
        SLUR,
    }

    private static class NotePosition {

        int xIndex = 0;
        int y = 0;
        int lineIndex = 0;
        int movement = 0;

        @SuppressWarnings("NonFinalFieldReferenceInEquals")
        @Override
        public synchronized boolean equals(Object obj) {
            //noinspection SimplifiableIfStatement
            if (this == obj) {
                return true;
            }

            //noinspection OverlyComplexBooleanExpression
            return (
                (obj instanceof NotePosition position) &&
                ((xIndex == position.xIndex) &&
                    (y == position.y) &&
                    (lineIndex == position.lineIndex) &&
                    (movement == position.movement))
            );
        }

        @SuppressWarnings("NonFinalFieldReferencedInHashCode")
        @Override
        public int hashCode() {
            return Objects.hash(xIndex, y, lineIndex, movement);
        }
    }

    public record NoteSelection(Line line, int begin, int end) {}

    public record TieSlurContext(
        boolean canToggle,
        IntervalSet intervals,
        Boolean isTie
    ) {}

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
        return pasteboard.size();
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
        if (selectedNotesLine > -1) {
            var line = composition.getLine(selectedNotesLine);
            pasteboard.clear();

            for (var i = selectionBegin; i <= selectionEnd; i++) {
                pasteboard.add(line.getNote(i).clone());
            }

            intervalSetsCopyBuffer = line.copyIntervals(
                selectionBegin,
                selectionEnd
            );
        }
    }

    public boolean canDeleteLine() {
        return (selectedLine != -1) && (composition.lineCount() > 1);
    }

    private void handleDelete() {
        if (selectedNotesLine != -1) {
            var line = composition.getLine(selectedNotesLine);

            for (var i = selectionEnd; i >= selectionBegin; i--) {
                deleteNote(i, line);
            }

            calculateLengthenings(selectionBegin - 1, line, true);
            calculateLengthenings(selectionBegin, line, true);
            spellLyrics(line);
        } else if (canDeleteLine()) {
            composition.removeLine(selectedLine);
            spellLyrics();
        }

        clearSelection();
        setEditNotePositionToEnd();
        repaint();
        //mainFrame.getUndoManager().undoableEditHappened(new UndoableEditEvent(this, due));
    }

    private void handlePaste() {
        if (!pasteboard.isEmpty()) {
            prevPasteControl = control;
            setEditNote(Note.PASTE_NOTE);
            control = Control.MOUSE;
            inSelectMode = false;
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

            if (editNote != null) {
                var line = composition.getLine(editNotePoint.lineIndex);

                if (code == KeyEvent.VK_LEFT) {
                    if (
                        (editNotePoint.xIndex == 0) &&
                        ((editNotePoint.movement != 0) ||
                            (line.noteCount() == 0))
                    ) {
                        if (editNotePoint.lineIndex > 0) {
                            editNotePoint.lineIndex--;
                            editNotePoint.xIndex = composition
                                .getLine(editNotePoint.lineIndex)
                                .noteCount();
                            editNotePoint.movement = 0;
                        } else {
                            return;
                        }
                    } else if (
                        (editNotePoint.movement == 0) &&
                        (editNotePoint.xIndex < line.noteCount())
                    ) {
                        editNotePoint.movement = (editNotePoint.xIndex != 0)
                            ? ((line
                                        .getNote(editNotePoint.xIndex - 1)
                                        .getXPos() -
                                    line
                                        .getNote(editNotePoint.xIndex)
                                        .getXPos()) /
                                2)
                            : FIRST_NOTE_IN_LINE_MOVEMENT;
                    } else {
                        editNotePoint.movement = 0;
                        editNotePoint.xIndex--;
                    }
                } else if (code == KeyEvent.VK_RIGHT) {
                    if (editNotePoint.xIndex == line.noteCount()) {
                        if (
                            editNotePoint.lineIndex <
                            (composition.lineCount() - 1)
                        ) {
                            editNotePoint.lineIndex++;
                            editNotePoint.xIndex = 0;
                            editNotePoint.movement = (composition
                                        .getLine(editNotePoint.lineIndex)
                                        .noteCount() ==
                                    0)
                                ? 0
                                : FIRST_NOTE_IN_LINE_MOVEMENT;
                        } else {
                            return;
                        }
                    } else if (editNotePoint.movement == 0) {
                        editNotePoint.xIndex++;

                        if (editNotePoint.xIndex < line.noteCount()) {
                            editNotePoint.movement = (editNotePoint.xIndex != 0)
                                ? ((line
                                            .getNote(editNotePoint.xIndex - 1)
                                            .getXPos() -
                                        line
                                            .getNote(editNotePoint.xIndex)
                                            .getXPos()) /
                                    2)
                                : FIRST_NOTE_IN_LINE_MOVEMENT;
                        } else {
                            editNotePoint.movement = 0;
                        }
                    } else {
                        editNotePoint.movement = 0;
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
                    if (editNotePoint.xIndex == line.noteCount()) {
                        addEditNote(line);
                        editNotePoint.xIndex = line.noteCount();
                        editNotePoint.movement = 0;
                    } else if (editNotePoint.movement != 0) {
                        insertEditNote(
                            editNotePoint.xIndex +
                            ((editNotePoint.movement < 0) ? 0 : 1),
                            line
                        );
                    } else {
                        modifyEditNote(editNotePoint.xIndex, line);
                    }
                } else if (code == KeyEvent.VK_PAGE_UP) {
                    if (editNotePoint.lineIndex > 0) {
                        editNotePoint.lineIndex--;
                        setNewEditNotePoint(
                            editNote.getXPos(),
                            editNotePoint.lineIndex
                        );
                        editNotePoint.xIndex = newEditNotePoint.xIndex;
                        editNotePoint.movement = newEditNotePoint.movement;
                    }
                } else if (code == KeyEvent.VK_PAGE_DOWN) {
                    if (
                        (editNotePoint.lineIndex + 1) < composition.lineCount()
                    ) {
                        editNotePoint.lineIndex++;
                        setNewEditNotePoint(
                            editNote.getXPos(),
                            editNotePoint.lineIndex
                        );
                        editNotePoint.xIndex = newEditNotePoint.xIndex;
                        editNotePoint.movement = newEditNotePoint.movement;
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
