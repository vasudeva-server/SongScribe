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

import java.util.List;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.CompositionDidChangeNotification;
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
    public static final ControlAction MOUSE_CONTROL_ACTION =
        ControlAction.createMouseControlAction();

    public static final ControlAction KEYBOARD_CONTROL_ACTION =
        ControlAction.createKeyboardControlAction();

    public static final ActionGroup<ControlAction> CONTROL_ACTION_GROUP =
        new ActionGroup<>(MOUSE_CONTROL_ACTION, KEYBOARD_CONTROL_ACTION);

    //
    // Mode actions
    //
    public static final ModeAction SELECT_MODE_ACTION =
        ModeAction.createSelectModeAction();

    public static final ModeAction EDIT_MODE_ACTION =
        ModeAction.createEditModeAction();

    public static final CycleModeAction CYCLE_MODE_ACTION =
        CycleModeAction.createAction();

    public static final ModeAction ADJUST_MUSIC_MODE_ACTION =
        ModeAction.createAdjustMusicModeAction();

    public static final ModeAction ADJUST_LYRICS_MODE_ACTION =
        ModeAction.createAdjustLyricsModeAction();

    public static final ModeAction ADJUST_VERTICAL_MODE_ACTION =
        ModeAction.createAdjustVerticalModeAction();

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
        ElementTypeAction.createGraceEighthNoteAction();

    public static final ElementTypeAction THIRTY_SECOND_NOTE_ACTION =
        ElementTypeAction.createThirtySecondNoteAction();

    public static final ElementTypeAction SIXTEENTH_NOTE_ACTION =
        ElementTypeAction.createSixteenthNoteAction();

    public static final ElementTypeAction EIGHTH_NOTE_ACTION =
        ElementTypeAction.createEighthNoteAction();

    public static final ElementTypeAction QUARTER_NOTE_ACTION =
        ElementTypeAction.createQuarterNoteAction();

    public static final ElementTypeAction HALF_NOTE_ACTION =
        ElementTypeAction.createHalfNoteAction();

    public static final ElementTypeAction WHOLE_NOTE_ACTION =
        ElementTypeAction.createWholeNoteAction();

    public static final ElementTypeAction GLISSANDO_ACTION =
        ElementTypeAction.createGlissandoAction();

    public static final ElementTypeAction SLIDE_OUT_ACTION =
        ElementTypeAction.createSlideOutAction();

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

    public static final DotAction DOT_ACTION = DotAction.createDotAction();

    public static final DotAction DOUBLE_DOT_ACTION =
        DotAction.createDoubleDotAction();

    public static final ActionGroup<DotAction> DOT_ACTION_GROUP =
        new ActionGroup<>(DOT_ACTION, DOUBLE_DOT_ACTION);

    public static final RestModeAction REST_ACTION =
        RestModeAction.createAction();

    public static final AccidentalAction FLAT_ACTION =
        AccidentalAction.createFlatAction();

    public static final AccidentalAction DOUBLE_FLAT_ACTION =
        AccidentalAction.createDoubleFlatAction();

    public static final AccidentalAction NATURAL_FLAT_ACTION =
        AccidentalAction.createNaturalFlatAction();

    public static final AccidentalAction NATURAL_ACTION =
        AccidentalAction.createNaturalAction();

    public static final AccidentalAction SHARP_ACTION =
        AccidentalAction.createSharpAction();

    public static final AccidentalAction DOUBLE_SHARP_ACTION =
        AccidentalAction.createDoubleSharpAction();

    public static final AccidentalAction NATURAL_SHARP_ACTION =
        AccidentalAction.createNaturalSharpAction();

    public static final AccidentalInParensAction ACCIDENTAL_IN_PARENS_ACTION =
        AccidentalInParensAction.createAction();

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
        ElementTypeAction.createLeftRepeatAction(),
        ElementTypeAction.createRightRepeatAction(),
        ElementTypeAction.createLeftRightRepeatAction(),
    };

    public static final ElementTypeAction[] BARLINE_ACTIONS = new ElementTypeAction[]{
        ElementTypeAction.createFinalDoubleBarlineAction(),
        ElementTypeAction.createDoubleBarlineAction(),
        ElementTypeAction.createSingleBarlineAction(),
    };

    public static final ElementTypeAction BREATH_MARK_ACTION =
        ElementTypeAction.createBreathMarkAction();

    public static final ActionGroup<ElementTypeAction> NON_DURATION_ACTION_GROUP =
        new NonDurationActionGroup();

    public static final ForceArticulationAction ACCENT_ACTION =
        ForceArticulationAction.createAccentAction();

    public static final DurationArticulationAction STACCATO_ACTION =
        DurationArticulationAction.createStaccatoAction();

    public static final ActionGroup<
        DurationArticulationAction
        > ARTICULATION_ACTION_GROUP = new ActionGroup<>(
        STACCATO_ACTION
    );

    public static final ToggleBeamAction TOGGLE_BEAM_ACTION =
        ToggleBeamAction.createAction();

    public static final ToggleTieAction TOGGLE_TIE_ACTION =
        ToggleTieAction.createAction();

    public static final List<TupletAction> TOGGLE_TUPLET_ACTIONS =
        List.of(
            TupletAction.createDupletAction(),
            TupletAction.createTripletAction(),
            TupletAction.createQuadrupletAction(),
            TupletAction.createQuintupletAction(),
            TupletAction.createSextupletAction(),
            TupletAction.createSeptupletAction()
        );

    public static final TupletAction REMOVE_TUPLET_ACTION =
        TupletAction.createRemoveAction();

    public static final FlipStemDirectionAction FLIP_STEM_DIRECTION_ACTION =
        FlipStemDirectionAction.createAction();

    public static final AddDynamicsAction ADD_CRESCENDO_ACTION =
        AddDynamicsAction.createCrescendoAction();

    public static final AddDynamicsAction ADD_DIMINUENDO_ACTION =
        AddDynamicsAction.createDiminuendoAction();

    public static final RemoveDynamicsAction REMOVE_DYNAMICS_ACTION =
        RemoveDynamicsAction.createAction();

    public static final TempoChangeAction TEMPO_CHANGE_ACTION =
        TempoChangeAction.createAction();

    public static final BeatChangeAction BEAT_CHANGE_ACTION =
        BeatChangeAction.createAction();

    public static final AnnotationAction ANNOTATION_ACTION =
        AnnotationAction.createAction();

    public static final KeySignatureChangeAction KEY_SIGNATURE_CHANGE_ACTION =
        KeySignatureChangeAction.createAction();

    public static final FermataAction FERMATA_ACTION =
        FermataAction.createAction();

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

    public static final PrintAction PRINT_ACTION = PrintAction.createAction();
    public static final QuitAction QUIT_ACTION = QuitAction.createAction();

    public static final CutAction CUT_ACTION = CutAction.createAction();
    public static final CopyAction COPY_ACTION = CopyAction.createAction();
    public static final PasteAction PASTE_ACTION = PasteAction.createAction();
    public static final DeleteAction DELETE_ACTION = DeleteAction.createAction();
    public static final SelectLineAction SELECT_LINE_ACTION = SelectLineAction.createAction();
    public static final DeselectAction DESELECT_ACTION = DeselectAction.createAction();

    // Strong reference prevents GC (mbassy uses weak references)
    private static final ResetHandler RESET_HANDLER = new ResetHandler();

    static {
        DURATION_ACTION_GROUP.setDefaultAction(QUARTER_NOTE_ACTION);
        MessageCenter.subscribe(RESET_HANDLER);
    }

    private static void resetToDefaults() {
        // Non-silent resets — these need perform() to update downstream state
        // (Score.mode via ModeDidChangeNotification, insertion element via
        // UpdateInsertionElementCommand)
        MODE_ACTION_GROUP.select(EDIT_MODE_ACTION, EDIT_MODE_ACTION);
        DURATION_ACTION_GROUP.select(QUARTER_NOTE_ACTION, QUARTER_NOTE_ACTION);

        // Silent resets — no downstream state to update
        ACCIDENTAL_ACTION_GROUP.reset();
        ARTICULATION_ACTION_GROUP.reset();
        DOT_ACTION_GROUP.reset();
        NON_DURATION_ACTION_GROUP.reset();

        // Standalone toggles
        ACCENT_ACTION.reset();
        REST_ACTION.reset();
        FERMATA_ACTION.reset();
        ACCIDENTAL_IN_PARENS_ACTION.reset();
    }

    private Actions() {
    }

    private static class ResetHandler {
        @Handler(priority = Message.HIGH_PRIORITY)
        public void compositionDidChange(CompositionDidChangeNotification message) {
            if (message.hasChangeType(CompositionDidChangeNotification.ChangeType.FULL)) {
                resetToDefaults();
            }
        }
    }
}
