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

package songscribe.ui.action;

import static songscribe.util.UIUtils.MENU_SHORTCUT_MASK;

import java.awt.event.*;
import java.util.Arrays;
import java.util.List;

import songscribe.music.DurationArticulation;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.dialog.AboutDialog;
import songscribe.ui.dialog.CompositionSettingsDialog;
import songscribe.ui.dialog.LyricsDialog;
import songscribe.ui.dialog.PreferencesDialog;

/**
 * This class serves as a repository for global action-related constants and action groups.
 * Playback actions are in the playback package.
 */
public final class Actions {

    //
    // Control actions
    //
    public static final ControlAction MOUSE_CONTROL_ACTION = new ControlAction(
        Control.MOUSE
    );

    public static final ControlAction KEYBOARD_CONTROL_ACTION =
        new ControlAction(Control.KEYBOARD);

    public static final ActionGroup<ControlAction> CONTROL_ACTION_GROUP =
        new ActionGroup<>(MOUSE_CONTROL_ACTION, KEYBOARD_CONTROL_ACTION);

    //
    // Mode actions
    //
    public static final ModeAction SELECT_MODE_ACTION = new ModeAction(
        Mode.SELECT,
        "Select Mode",
        "@\uF3D2",
        22,
        "select-mode",
        "Select song elements"
    );

    public static final ModeAction EDIT_MODE_ACTION = new ModeAction(
        Mode.NOTE_EDIT,
        "Edit Mode",
        "@\uEF63",
        22,
        "edit-mode",
        "Edit song"
    );

    public static final CycleModeAction CYCLE_MODE_ACTION = new CycleModeAction();

    public static final ModeAction ADJUST_MUSIC_MODE_ACTION = new ModeAction(
        Mode.NOTE_ADJUSTMENT,
        "Music Adjustment",
        "mode-note-adjustment.svg",
        26,
        "adjust-note-mode",
        "Adjust horizontal position of music elements"
    );

    public static final ModeAction ADJUST_LYRICS_MODE_ACTION = new ModeAction(
        Mode.LYRICS_ADJUSTMENT,
        "Lyrics Adjustment",
        "mode-lyrics-adjustment.svg",
        26,
        "adjust-lyrics-mode",
        "Adjust horizontal position of lyrics"
    );

    public static final ModeAction ADJUST_VERTICAL_MODE_ACTION = new ModeAction(
        Mode.VERTICAL_ADJUSTMENT,
        "Vertical Adjustment",
        "mode-vertical-adjustment.svg",
        26,
        "adjust-vertical-mode",
        "Adjust vertical position of non-music elements"
    );

    public static final ActionGroup<ModeAction> MODE_ACTION_GROUP =
        new ActionGroup<>(
            SELECT_MODE_ACTION,
            EDIT_MODE_ACTION,
            ADJUST_MUSIC_MODE_ACTION,
            ADJUST_LYRICS_MODE_ACTION,
            ADJUST_VERTICAL_MODE_ACTION
        );

    //
    // Duration actions
    //
    public static final DurationAction GRACE_EIGHTH_NOTE_ACTION =
        new DurationAction(
            NoteType.GRACE_QUAVER,
            "Grace note",
            "grace.svg",
            26,
            "duration-grace-eighth",
            "Insert grace eighth note followed by glissando",
            KeyEvent.VK_G,
            InputEvent.SHIFT_DOWN_MASK
        );

    // Grace note and rest mode are mutually exclusive
    static {
        GRACE_EIGHTH_NOTE_ACTION.setFlags(UIAction.Flag.DISABLE_IN_REST_MODE);
    }


    public static final DurationAction THIRTY_SECOND_NOTE_ACTION =
        new DurationAction(
            NoteType.DEMI_SEMIQUAVER,
            "Thirty-second",
            "@\uF36B",
            18,
            "duration-thirty-second",
            "Set duration to thirty-second note",
            KeyEvent.VK_1,
            0
        );

    public static final DurationAction SIXTEENTH_NOTE_ACTION =
        new DurationAction(
            NoteType.SEMIQUAVER,
            "Sixteenth",
            "@\uF36A",
            18,
            "duration-sixteenth",
            "Set duration to sixteenth note",
            KeyEvent.VK_2,
            0
        );

    public static final DurationAction EIGHTH_NOTE_ACTION = new DurationAction(
        NoteType.QUAVER,
        "Eighth",
        "@\uF369",
        18,
        "duration-eighth",
        "Set duration to eighth note",
        KeyEvent.VK_3,
        0
    );

    public static final DurationAction QUARTER_NOTE_ACTION = new DurationAction(
        NoteType.CROTCHET,
        "Quarter",
        "@\uF368",
        18,
        "duration-quarter",
        "Set duration to quarter note",
        KeyEvent.VK_4,
        0
    );

    public static final DurationAction HALF_NOTE_ACTION = new DurationAction(
        NoteType.MINIM,
        "Half",
        "@\uF367",
        18,
        "duration-half",
        "Set duration to half note",
        KeyEvent.VK_5,
        0
    );

    public static final DurationAction WHOLE_NOTE_ACTION = new DurationAction(
        NoteType.SEMIBREVE,
        "Whole",
        "@\uF366",
        18,
        "duration-whole",
        "Set duration to whole note",
        KeyEvent.VK_6,
        0
    );

    public static final DurationAction GLISSANDO_ACTION = new DurationAction(
        NoteType.GLISSANDO,
        "Connecting glissando",
        "connecting-glissando.svg",
        26,
        "glissando",
        "Insert glissando between two notes",
        KeyEvent.VK_G,
        0
    );

    public static final DurationAction SLIDE_OUT_ACTION = new DurationAction(
        NoteType.GLISSANDO,
        "Slide out glissando",
        "slide-out.svg",
        26,
        "slide-out",
        "Insert slide out glissando after a note",
        KeyEvent.VK_G,
        InputEvent.SHIFT_DOWN_MASK + InputEvent.META_DOWN_MASK
    );

    public static final DurationActionGroup DURATION_ACTION_GROUP =
        new DurationActionGroup(
            GRACE_EIGHTH_NOTE_ACTION,
            THIRTY_SECOND_NOTE_ACTION,
            SIXTEENTH_NOTE_ACTION,
            EIGHTH_NOTE_ACTION,
            QUARTER_NOTE_ACTION,
            HALF_NOTE_ACTION,
            WHOLE_NOTE_ACTION,
            GLISSANDO_ACTION,
            SLIDE_OUT_ACTION
        );

    public static final DotAction DOT_ACTION = new DotAction(
        DotAction.DotLevel.SINGLE,
        "Dot",
        "@\uF372",
        18,
        "add-dot",
        "Add a single dot to the note",
        KeyEvent.VK_PERIOD,
        0
    );

    public static final DotAction DOUBLE_DOT_ACTION = new DotAction(
        DotAction.DotLevel.DOUBLE,
        "Double Dot",
        "@\uF395",
        18,
        "add-double-dot",
        "Add a double dot to the note",
        0,
        0
    );

    public static final ActionGroup<DotAction> DOT_ACTION_GROUP =
        new ActionGroup<>(DOT_ACTION, DOUBLE_DOT_ACTION);

    public static final RestModeAction REST_ACTION = new RestModeAction();

    public static final AccidentalAction FLAT_ACTION = new AccidentalAction(
        Note.Accidental.FLAT,
        "Flat",
        "@\uF388",
        18,
        "flat",
        "Add flat to note",
        0,
        0
    );

    public static final AccidentalAction DOUBLE_FLAT_ACTION =
        new AccidentalAction(
            Note.Accidental.DOUBLE_FLAT,
            "Double Flat",
            "@\uF389",
            18,
            "double-flat",
            "Add double flat to note",
            KeyEvent.VK_F,
            0
        );

    public static final AccidentalAction NATURAL_FLAT_ACTION =
        new AccidentalAction(
            Note.Accidental.NATURAL_FLAT,
            "Natural Flat",
            "#\uE267",
            32,
            "natural-flat",
            "Add natural flat to note"
        );

    public static final AccidentalAction NATURAL_ACTION = new AccidentalAction(
        Note.Accidental.NATURAL,
        "Natural",
        "@\uF387",
        18,
        "natural",
        "Add natural to note",
        KeyEvent.VK_N,
        0
    );

    public static final AccidentalAction SHARP_ACTION = new AccidentalAction(
        Note.Accidental.SHARP,
        "Sharp",
        "@\uF386",
        18,
        "sharp",
        "Add sharp to note",
        0,
        0
    );

    public static final AccidentalAction DOUBLE_SHARP_ACTION =
        new AccidentalAction(
            Note.Accidental.DOUBLE_SHARP,
            "Double Sharp",
            "@\uF38A",
            18,
            "double-sharp",
            "Add double sharp to note",
            0,
            0
        );

    public static final AccidentalAction NATURAL_SHARP_ACTION =
        new AccidentalAction(
            Note.Accidental.NATURAL_SHARP,
            "Natural Sharp",
            "#\uE268",
            32,
            "natural-sharp",
            "Add natural sharp to note",
            0,
            0
        );

    public static final UIAction ACCIDENTAL_IN_PARENS_ACTION = new UIAction(
        "In Parentheses",
        null,
        0,
        "accidental-in-parens",
        "Add accidental in parentheses",
        true
    );

    static {
        ACCIDENTAL_IN_PARENS_ACTION.setFlags(
            UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE
        );
    }

    public static final ActionGroup<AccidentalAction> ACCIDENTAL_ACTION_GROUP =
        new ActionGroup<>(
            FLAT_ACTION,
            DOUBLE_FLAT_ACTION,
            NATURAL_FLAT_ACTION,
            NATURAL_ACTION,
            SHARP_ACTION,
            DOUBLE_SHARP_ACTION,
            NATURAL_SHARP_ACTION
        );

    public static final NonDurationAction[] REPEAT_ACTIONS = new NonDurationAction[]{
        new NonDurationAction(
            NoteType.REPEAT_LEFT,
            "Left Repeat",
            "@\uEF68",
            24,
            "left-repeat",
            "Insert left repeat",
            KeyEvent.VK_L,
            0
        ),
        new NonDurationAction(
            NoteType.REPEAT_RIGHT,
            "Right Repeat",
            "@\uF345",
            24,
            "right-repeat",
            "Insert right repeat",
            KeyEvent.VK_R,
            InputEvent.SHIFT_DOWN_MASK
        ),
        new NonDurationAction(
            NoteType.REPEAT_LEFT_RIGHT,
            "Left/Right Repeat",
            "@\uF34B",
            24,
            "left-right-repeat",
            "Insert left/right repeat",
            0,
            0
        ),
    };

    public static final NonDurationAction[] BARLINE_ACTIONS = new NonDurationAction[]{
        new NonDurationAction(
            NoteType.FINAL_DOUBLE_BARLINE,
            "Final Double Barline",
            "@\uF34A",
            24,
            "final-double-barline",
            "Insert final double barline",
            KeyEvent.VK_F,
            InputEvent.SHIFT_DOWN_MASK
        ),
        new NonDurationAction(
            NoteType.DOUBLE_BARLINE,
            "Double Barline (Fine)",
            "@\uF347",
            24,
            "double-barline",
            "Insert double barline and <em>fine</em>",
            KeyEvent.VK_D,
            0
        ),
        new NonDurationAction(
            NoteType.SINGLE_BARLINE,
            "Single Barline",
            "@\uF346",
            24,
            "single-barline",
            "Insert single barline",
            0,
            0
        ),
    };

    public static final NonDurationAction BREATH_MARK_ACTION =
        new NonDurationAction(
            NoteType.BREATH_MARK,
            "Breath Mark",
            null,
            0,
            "breath-mark",
            "Insert breath mark",
            0,
            0
        );

    public static final ActionGroup<NonDurationAction> NON_DURATION_ACTION_GROUP =
        new NonDurationActionGroup();

    public static final ForceArticulationAction ACCENT_ACTION =
        new ForceArticulationAction(
            "Accent",
            "@\uF38C",
            22,
            "accent",
            "Add accent articulation"
        );

    public static final DurationArticulationAction STACCATO_ACTION =
        new DurationArticulationAction(
            DurationArticulation.STACCATO,
            "Staccato",
            "@\uF38E",
            22,
            "staccato",
            "Add staccato articulation"
        );

    public static final ActionGroup<
        DurationArticulationAction
        > ARTICULATION_ACTION_GROUP = new ActionGroup<>(
        STACCATO_ACTION
    );

    public static final ToggleBeamAction TOGGLE_BEAM_ACTION =
        new ToggleBeamAction();

    public static final ToggleTieAction TOGGLE_TIE_ACTION =
        new ToggleTieAction();

    public static final List<TupletAction> TOGGLE_TUPLET_ACTIONS =
        Arrays.asList(
            new TupletAction(TupletAction.Tuplet.DUPLET),
            new TupletAction(TupletAction.Tuplet.TRIPLET),
            new TupletAction(TupletAction.Tuplet.QUADRUPLET),
            new TupletAction(TupletAction.Tuplet.QUINTUPLET),
            new TupletAction(TupletAction.Tuplet.SEXTUPLET),
            new TupletAction(TupletAction.Tuplet.SEPTUPLET)
        );

    public static final TupletAction REMOVE_TUPLET_ACTION = new TupletAction(
        TupletAction.Tuplet.REMOVE
    );

    public static final FlipStemDirectionAction FLIP_STEM_DIRECTION_ACTION =
        new FlipStemDirectionAction();

    public static final AddDynamicsAction ADD_CRESCENDO_ACTION =
        new AddDynamicsAction(true);

    public static final AddDynamicsAction ADD_DIMINUENDO_ACTION =
        new AddDynamicsAction(false);

    public static final RemoveDynamicsAction REMOVE_DYNAMICS_ACTION =
        new RemoveDynamicsAction();

    public static final TempoChangeAction TEMPO_CHANGE_ACTION =
        new TempoChangeAction();

    public static final BeatChangeAction BEAT_CHANGE_ACTION =
        new BeatChangeAction();

    public static final AnnotationAction ANNOTATION_ACTION =
        new AnnotationAction();

    public static final KeySignatureChangeAction KEY_SIGNATURE_CHANGE_ACTION =
        new KeySignatureChangeAction();

    public static final FermataAction FERMATA_ACTION = new FermataAction();

    public static final DialogOpenAction<PreferencesDialog> PREFERENCES_ACTION =
        new DialogOpenAction<>("Settings...", PreferencesDialog.class);

    public static final DialogOpenAction<
        CompositionSettingsDialog
        > COMPOSITION_SETTINGS_ACTION = new DialogOpenAction<>(
        "Composition Settings...",
        KeyEvent.VK_G,
        MENU_SHORTCUT_MASK,
        CompositionSettingsDialog.class
    );

    public static final DialogOpenAction<LyricsDialog> LYRICS_DIALOG_ACTION =
        new DialogOpenAction<>(
            "Lyrics...",
            KeyEvent.VK_L,
            MENU_SHORTCUT_MASK,
            LyricsDialog.class
        );

    public static final DialogOpenAction<AboutDialog> ABOUT_ACTION =
        new DialogOpenAction<>("About SongScribe...", AboutDialog.class);

    public static final PrintAction PRINT_ACTION = new PrintAction();
    public static final QuitAction QUIT_ACTION = new QuitAction();

    public static final CutAction CUT_ACTION = new CutAction();
    public static final CopyAction COPY_ACTION = new CopyAction();
    public static final PasteAction PASTE_ACTION = new PasteAction();
    public static final DeleteAction DELETE_ACTION = new DeleteAction();
    public static final SelectLineAction SELECT_LINE_ACTION = new SelectLineAction();
    public static final DeselectAction DESELECT_ACTION = new DeselectAction();

    private Actions() {
    }
}
