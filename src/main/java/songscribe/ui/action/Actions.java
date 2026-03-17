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

import module java.desktop;

import java.util.Arrays;
import java.util.List;

import songscribe.Strings;
import songscribe.music.ArticulationType;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
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
        Strings.get(Strings.ACTION_MODE_SELECT),
        "@\uF3D2",
        22,
        "select-mode",
        Strings.get(Strings.ACTION_MODE_SELECT_TOOLTIP)
    );

    public static final ModeAction EDIT_MODE_ACTION = new ModeAction(
        Mode.EDIT,
        Strings.get(Strings.ACTION_MODE_EDIT),
        "@\uEF63",
        22,
        "edit-mode",
        Strings.get(Strings.ACTION_MODE_EDIT_TOOLTIP)
    );

    public static final CycleModeAction CYCLE_MODE_ACTION = new CycleModeAction();

    public static final ModeAction ADJUST_MUSIC_MODE_ACTION = new ModeAction(
        Mode.ADJUSTMENT,
        Strings.get(Strings.ACTION_MODE_MUSIC_ADJUST),
        "mode-note-adjustment.svg",
        26,
        "adjust-note-mode",
        Strings.get(Strings.ACTION_MODE_MUSIC_ADJUST_TOOLTIP)
    );

    public static final ModeAction ADJUST_LYRICS_MODE_ACTION = new ModeAction(
        Mode.LYRICS_ADJUSTMENT,
        Strings.get(Strings.ACTION_MODE_LYRICS_ADJUST),
        "mode-lyrics-adjustment.svg",
        26,
        "adjust-lyrics-mode",
        Strings.get(Strings.ACTION_MODE_LYRICS_ADJUST_TOOLTIP)
    );

    public static final ModeAction ADJUST_VERTICAL_MODE_ACTION = new ModeAction(
        Mode.VERTICAL_ADJUSTMENT,
        Strings.get(Strings.ACTION_MODE_VERTICAL_ADJUST),
        "mode-vertical-adjustment.svg",
        26,
        "adjust-vertical-mode",
        Strings.get(Strings.ACTION_MODE_VERTICAL_ADJUST_TOOLTIP)
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
    public static final ElementTypeAction GRACE_EIGHTH_NOTE_ACTION =
        new ElementTypeAction(
            ElementTypeAction.Kind.DURATION,
            ElementType.GRACE_QUAVER,
            Strings.get(Strings.ACTION_DURATION_GRACE),
            "grace.svg",
            26,
            "duration-grace-eighth",
            Strings.get(Strings.ACTION_DURATION_GRACE_TOOLTIP),
            KeyEvent.VK_G,
            InputEvent.SHIFT_DOWN_MASK
        );

    // Grace note and rest mode are mutually exclusive
    static {
        GRACE_EIGHTH_NOTE_ACTION.setFlags(
            UIAction.Flag.DISABLE_IN_GRACE_MODE,
            UIAction.Flag.DISABLE_IN_REST_MODE,
            UIAction.Flag.DISABLE_IN_SELECT_MODE
        );
    }

    public static final ElementTypeAction THIRTY_SECOND_NOTE_ACTION =
        new ElementTypeAction(
            ElementTypeAction.Kind.DURATION,
            ElementType.DEMI_SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND),
            "@\uF36B",
            18,
            "duration-thirty-second",
            Strings.get(Strings.ACTION_DURATION_THIRTY_SECOND_TOOLTIP),
            KeyEvent.VK_1,
            0
        );

    public static final ElementTypeAction SIXTEENTH_NOTE_ACTION =
        new ElementTypeAction(
            ElementTypeAction.Kind.DURATION,
            ElementType.SEMIQUAVER,
            Strings.get(Strings.ACTION_DURATION_SIXTEENTH),
            "@\uF36A",
            18,
            "duration-sixteenth",
            Strings.get(Strings.ACTION_DURATION_SIXTEENTH_TOOLTIP),
            KeyEvent.VK_2,
            0
        );

    public static final ElementTypeAction EIGHTH_NOTE_ACTION = new ElementTypeAction(
        ElementTypeAction.Kind.DURATION,
        ElementType.QUAVER,
        Strings.get(Strings.ACTION_DURATION_EIGHTH),
        "@\uF369",
        18,
        "duration-eighth",
        Strings.get(Strings.ACTION_DURATION_EIGHTH_TOOLTIP),
        KeyEvent.VK_3,
        0
    );

    public static final ElementTypeAction QUARTER_NOTE_ACTION = new ElementTypeAction(
        ElementTypeAction.Kind.DURATION,
        ElementType.CROTCHET,
        Strings.get(Strings.ACTION_DURATION_QUARTER),
        "@\uF368",
        18,
        "duration-quarter",
        Strings.get(Strings.ACTION_DURATION_QUARTER_TOOLTIP),
        KeyEvent.VK_4,
        0
    );

    public static final ElementTypeAction HALF_NOTE_ACTION = new ElementTypeAction(
        ElementTypeAction.Kind.DURATION,
        ElementType.MINIM,
        Strings.get(Strings.ACTION_DURATION_HALF),
        "@\uF367",
        18,
        "duration-half",
        Strings.get(Strings.ACTION_DURATION_HALF_TOOLTIP),
        KeyEvent.VK_5,
        0
    );

    public static final ElementTypeAction WHOLE_NOTE_ACTION = new ElementTypeAction(
        ElementTypeAction.Kind.DURATION,
        ElementType.SEMIBREVE,
        Strings.get(Strings.ACTION_DURATION_WHOLE),
        "@\uF366",
        18,
        "duration-whole",
        Strings.get(Strings.ACTION_DURATION_WHOLE_TOOLTIP),
        KeyEvent.VK_6,
        0
    );

    public static final ElementTypeAction GLISSANDO_ACTION = new ElementTypeAction(
        ElementTypeAction.Kind.DURATION,
        ElementType.GLISSANDO,
        Strings.get(Strings.ACTION_DURATION_GLISSANDO),
        "connecting-glissando.svg",
        26,
        "glissando",
        Strings.get(Strings.ACTION_DURATION_GLISSANDO_TOOLTIP),
        KeyEvent.VK_G,
        0
    );

    public static final ElementTypeAction SLIDE_OUT_ACTION = new ElementTypeAction(
        ElementTypeAction.Kind.DURATION,
        ElementType.GLISSANDO,
        Strings.get(Strings.ACTION_DURATION_SLIDE_OUT),
        "slide-out.svg",
        26,
        "slide-out",
        Strings.get(Strings.ACTION_DURATION_SLIDE_OUT_TOOLTIP),
        KeyEvent.VK_G,
        InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK
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
        Strings.get(Strings.ACTION_DOT_SINGLE),
        "@\uF372",
        18,
        "add-dot",
        Strings.get(Strings.ACTION_DOT_SINGLE_TOOLTIP),
        KeyEvent.VK_PERIOD,
        0
    );

    public static final DotAction DOUBLE_DOT_ACTION = new DotAction(
        DotAction.DotLevel.DOUBLE,
        Strings.get(Strings.ACTION_DOT_DOUBLE),
        "@\uF395",
        18,
        "add-double-dot",
        Strings.get(Strings.ACTION_DOT_DOUBLE_TOOLTIP),
        0,
        0
    );

    public static final ActionGroup<DotAction> DOT_ACTION_GROUP =
        new ActionGroup<>(DOT_ACTION, DOUBLE_DOT_ACTION);

    public static final RestModeAction REST_ACTION = new RestModeAction();

    public static final AccidentalAction FLAT_ACTION = new AccidentalAction(
        StaffElement.Accidental.FLAT,
        Strings.get(Strings.ACTION_ACCIDENTAL_FLAT),
        "@\uF388",
        18,
        "flat",
        Strings.get(Strings.ACTION_ACCIDENTAL_FLAT_TOOLTIP),
        0,
        0
    );

    public static final AccidentalAction DOUBLE_FLAT_ACTION =
        new AccidentalAction(
            StaffElement.Accidental.DOUBLE_FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT),
            "@\uF389",
            18,
            "double-flat",
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_FLAT_TOOLTIP),
            KeyEvent.VK_F,
            0
        );

    public static final AccidentalAction NATURAL_FLAT_ACTION =
        new AccidentalAction(
            StaffElement.Accidental.NATURAL_FLAT,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_FLAT),
            "#\uE267",
            32,
            "natural-flat",
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_FLAT_TOOLTIP)
        );

    public static final AccidentalAction NATURAL_ACTION = new AccidentalAction(
        StaffElement.Accidental.NATURAL,
        Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL),
        "@\uF387",
        18,
        "natural",
        Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_TOOLTIP),
        KeyEvent.VK_N,
        0
    );

    public static final AccidentalAction SHARP_ACTION = new AccidentalAction(
        StaffElement.Accidental.SHARP,
        Strings.get(Strings.ACTION_ACCIDENTAL_SHARP),
        "@\uF386",
        18,
        "sharp",
        Strings.get(Strings.ACTION_ACCIDENTAL_SHARP_TOOLTIP),
        0,
        0
    );

    public static final AccidentalAction DOUBLE_SHARP_ACTION =
        new AccidentalAction(
            StaffElement.Accidental.DOUBLE_SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP),
            "@\uF38A",
            18,
            "double-sharp",
            Strings.get(Strings.ACTION_ACCIDENTAL_DOUBLE_SHARP_TOOLTIP),
            0,
            0
        );

    public static final AccidentalAction NATURAL_SHARP_ACTION =
        new AccidentalAction(
            StaffElement.Accidental.NATURAL_SHARP,
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_SHARP),
            "#\uE268",
            32,
            "natural-sharp",
            Strings.get(Strings.ACTION_ACCIDENTAL_NATURAL_SHARP_TOOLTIP),
            0,
            0
        );

    public static final AccidentalInParensAction ACCIDENTAL_IN_PARENS_ACTION =
        new AccidentalInParensAction();

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

    public static final ElementTypeAction[] REPEAT_ACTIONS = new ElementTypeAction[]{
        new ElementTypeAction(
            ElementTypeAction.Kind.NON_DURATION,
            ElementType.REPEAT_LEFT,
            Strings.get(Strings.ACTION_REPEAT_LEFT),
            "@\uEF68",
            24,
            "left-repeat",
            Strings.get(Strings.ACTION_REPEAT_LEFT_TOOLTIP),
            KeyEvent.VK_L,
            0
        ),
        new ElementTypeAction(
            ElementTypeAction.Kind.NON_DURATION,
            ElementType.REPEAT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_RIGHT),
            "@\uF345",
            24,
            "right-repeat",
            Strings.get(Strings.ACTION_REPEAT_RIGHT_TOOLTIP),
            KeyEvent.VK_R,
            InputEvent.SHIFT_DOWN_MASK
        ),
        new ElementTypeAction(
            ElementTypeAction.Kind.NON_DURATION,
            ElementType.REPEAT_LEFT_RIGHT,
            Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT),
            "@\uF34B",
            24,
            "left-right-repeat",
            Strings.get(Strings.ACTION_REPEAT_LEFT_RIGHT_TOOLTIP),
            0,
            0
        ),
    };

    public static final ElementTypeAction[] BARLINE_ACTIONS = new ElementTypeAction[]{
        new ElementTypeAction(
            ElementTypeAction.Kind.NON_DURATION,
            ElementType.FINAL_DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE),
            "@\uF34A",
            24,
            "final-double-barline",
            Strings.get(Strings.ACTION_BARLINE_FINAL_DOUBLE_TOOLTIP),
            KeyEvent.VK_F,
            InputEvent.SHIFT_DOWN_MASK
        ),
        new ElementTypeAction(
            ElementTypeAction.Kind.NON_DURATION,
            ElementType.DOUBLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_DOUBLE_FINE),
            "@\uF347",
            24,
            "double-barline",
            Strings.get(Strings.ACTION_BARLINE_DOUBLE_FINE_TOOLTIP),
            KeyEvent.VK_D,
            0
        ),
        new ElementTypeAction(
            ElementTypeAction.Kind.NON_DURATION,
            ElementType.SINGLE_BARLINE,
            Strings.get(Strings.ACTION_BARLINE_SINGLE),
            "@\uF346",
            24,
            "single-barline",
            Strings.get(Strings.ACTION_BARLINE_SINGLE_TOOLTIP),
            0,
            0
        ),
    };

    public static final ElementTypeAction BREATH_MARK_ACTION =
        new ElementTypeAction(
            ElementTypeAction.Kind.NON_DURATION,
            ElementType.BREATH_MARK,
            Strings.get(Strings.ACTION_BREATH_MARK),
            null,
            0,
            "breath-mark",
            Strings.get(Strings.ACTION_BREATH_MARK_TOOLTIP),
            0,
            0
        );

    public static final ActionGroup<ElementTypeAction> NON_DURATION_ACTION_GROUP =
        new NonDurationActionGroup();

    public static final ForceArticulationAction ACCENT_ACTION =
        new ForceArticulationAction(
            ArticulationType.ACCENT,
            Strings.get(Strings.ACTION_ACCENT),
            "@\uF38C",
            22,
            "accent",
            Strings.get(Strings.ACTION_ACCENT_TOOLTIP)
        );

    public static final DurationArticulationAction STACCATO_ACTION =
        new DurationArticulationAction(
            ArticulationType.STACCATO,
            Strings.get(Strings.ACTION_STACCATO),
            "@\uF38E",
            22,
            "staccato",
            Strings.get(Strings.ACTION_STACCATO_TOOLTIP)
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
        new DialogOpenAction<>(Strings.get(Strings.ACTION_SETTINGS), PreferencesDialog.class);

    public static final DialogOpenAction<
        CompositionSettingsDialog
        > COMPOSITION_SETTINGS_ACTION = new DialogOpenAction<>(
        Strings.get(Strings.ACTION_COMPOSITION_SETTINGS),
        KeyEvent.VK_G,
        MENU_SHORTCUT_MASK,
        CompositionSettingsDialog.class
    );

    public static final DialogOpenAction<LyricsDialog> LYRICS_DIALOG_ACTION =
        new DialogOpenAction<>(
            Strings.get(Strings.ACTION_LYRICS),
            KeyEvent.VK_L,
            MENU_SHORTCUT_MASK,
            LyricsDialog.class
        );

    public static final DialogOpenAction<AboutDialog> ABOUT_ACTION =
        new DialogOpenAction<>(Strings.get(Strings.ACTION_ABOUT), AboutDialog.class);

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
