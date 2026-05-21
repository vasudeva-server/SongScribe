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
import static songscribe.ui.action.UIAction.Flag;

import module java.desktop;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.ui.action.UIAction.AppMenuAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.SongSettingsDialog;

/**
 * This class serves as a repository for global action-related constants and action groups.
 * Playback actions are in the playback package.
 */
public final class Actions {

    // Resolved once at class load and injected into every action below, so individual
    // actions no longer call MainFrame.getInstance() themselves. Assumes the MainFrame
    // singleton is already constructed before this holder class is first referenced.
    private static final MainFrame MAIN_FRAME = MainFrame.getInstance();

    private static final Logger LOG = LoggerFactory.getLogger(Actions.class);

    //
    // Control actions
    //
    public static final ControlAction MOUSE_CONTROL_ACTION =
        ControlAction.createMouseControlAction(MAIN_FRAME);

    public static final ControlAction KEYBOARD_CONTROL_ACTION =
        ControlAction.createKeyboardControlAction(MAIN_FRAME);

    public static final ActionGroup<ControlAction> CONTROL_ACTION_GROUP =
        new ActionGroup<>(MOUSE_CONTROL_ACTION, KEYBOARD_CONTROL_ACTION);

    //
    // Mode actions
    //
    public static final ModeAction SELECT_MODE_ACTION =
        ModeAction.createSelectModeAction(MAIN_FRAME);

    public static final ModeAction EDIT_MODE_ACTION =
        ModeAction.createEditModeAction(MAIN_FRAME);

    public static final CycleModeAction CYCLE_MODE_ACTION =
        CycleModeAction.createAction(MAIN_FRAME);

    public static final ModeAction ADJUST_MUSIC_MODE_ACTION =
        ModeAction.createAdjustMusicModeAction(MAIN_FRAME);

    public static final ModeAction ADJUST_VERTICAL_MODE_ACTION =
        ModeAction.createAdjustVerticalModeAction(MAIN_FRAME);

    public static final ActionGroup<ModeAction> MODE_ACTION_GROUP =
        new ActionGroup<>(
            SELECT_MODE_ACTION,
            EDIT_MODE_ACTION,
            ADJUST_MUSIC_MODE_ACTION,
            ADJUST_VERTICAL_MODE_ACTION
        );

    //
    // Duration actions
    //
    public static final ElementTypeAction GRACE_EIGHTH_NOTE_ACTION =
        ElementTypeAction.createGraceEighthNoteAction(MAIN_FRAME);

    public static final ElementTypeAction THIRTY_SECOND_NOTE_ACTION =
        ElementTypeAction.createThirtySecondNoteAction(MAIN_FRAME);

    public static final ElementTypeAction SIXTEENTH_NOTE_ACTION =
        ElementTypeAction.createSixteenthNoteAction(MAIN_FRAME);

    public static final ElementTypeAction EIGHTH_NOTE_ACTION =
        ElementTypeAction.createEighthNoteAction(MAIN_FRAME);

    public static final ElementTypeAction QUARTER_NOTE_ACTION =
        ElementTypeAction.createQuarterNoteAction(MAIN_FRAME);

    public static final ElementTypeAction HALF_NOTE_ACTION =
        ElementTypeAction.createHalfNoteAction(MAIN_FRAME);

    public static final ElementTypeAction WHOLE_NOTE_ACTION =
        ElementTypeAction.createWholeNoteAction(MAIN_FRAME);

    public static final ElementTypeAction GLISSANDO_ACTION =
        ElementTypeAction.createGlissandoAction(MAIN_FRAME);

    public static final ElementTypeAction SLIDE_OUT_ACTION =
        ElementTypeAction.createSlideOutAction(MAIN_FRAME);

    public static final List<ElementTypeAction> NOTE_DURATION_ACTIONS = List.of(
        GRACE_EIGHTH_NOTE_ACTION,
        THIRTY_SECOND_NOTE_ACTION,
        SIXTEENTH_NOTE_ACTION,
        EIGHTH_NOTE_ACTION,
        QUARTER_NOTE_ACTION,
        HALF_NOTE_ACTION,
        WHOLE_NOTE_ACTION
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

    public static final DotAction DOT_ACTION = DotAction.createDotAction(MAIN_FRAME);

    public static final DotAction DOUBLE_DOT_ACTION =
        DotAction.createDoubleDotAction(MAIN_FRAME);

    public static final ActionGroup<DotAction> DOT_ACTION_GROUP =
        new ActionGroup<>(DOT_ACTION, DOUBLE_DOT_ACTION);

    public static final RestModeAction REST_ACTION =
        RestModeAction.createAction(MAIN_FRAME);

    public static final AccidentalAction FLAT_ACTION =
        AccidentalAction.createFlatAction(MAIN_FRAME);

    public static final AccidentalAction DOUBLE_FLAT_ACTION =
        AccidentalAction.createDoubleFlatAction(MAIN_FRAME);

    public static final AccidentalAction NATURAL_FLAT_ACTION =
        AccidentalAction.createNaturalFlatAction(MAIN_FRAME);

    public static final AccidentalAction NATURAL_ACTION =
        AccidentalAction.createNaturalAction(MAIN_FRAME);

    public static final AccidentalAction SHARP_ACTION =
        AccidentalAction.createSharpAction(MAIN_FRAME);

    public static final AccidentalAction DOUBLE_SHARP_ACTION =
        AccidentalAction.createDoubleSharpAction(MAIN_FRAME);

    public static final AccidentalAction NATURAL_SHARP_ACTION =
        AccidentalAction.createNaturalSharpAction(MAIN_FRAME);

    public static final AccidentalInParensAction ACCIDENTAL_IN_PARENS_ACTION =
        AccidentalInParensAction.createAction(MAIN_FRAME);

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
        ElementTypeAction.createLeftRepeatAction(MAIN_FRAME),
        ElementTypeAction.createRightRepeatAction(MAIN_FRAME),
        ElementTypeAction.createLeftRightRepeatAction(MAIN_FRAME),
    };

    public static final ElementTypeAction[] BARLINE_ACTIONS = new ElementTypeAction[]{
        ElementTypeAction.createDoubleBarlineAction(MAIN_FRAME),
        ElementTypeAction.createSingleBarlineAction(MAIN_FRAME),
    };

    public static final ElementTypeAction BREATH_MARK_ACTION =
        ElementTypeAction.createBreathMarkAction(MAIN_FRAME);

    public static final ActionGroup<ElementTypeAction> NON_DURATION_ACTION_GROUP =
        new NonDurationActionGroup();

    public static final FirstSecondEndingAction MAKE_ENDING_ACTION =
        new FirstSecondEndingAction(MAIN_FRAME);

    public static final ForceArticulationAction ACCENT_ACTION =
        ForceArticulationAction.createAccentAction(MAIN_FRAME);

    public static final DurationArticulationAction STACCATO_ACTION =
        DurationArticulationAction.createStaccatoAction(MAIN_FRAME);

    public static final ActionGroup<
        DurationArticulationAction
        > ARTICULATION_ACTION_GROUP = new ActionGroup<>(
        STACCATO_ACTION
    );

    public static final ToggleNotationAction TOGGLE_BEAM_ACTION =
        ToggleNotationAction.createBeamAction(MAIN_FRAME);

    public static final ToggleNotationAction TOGGLE_TIE_ACTION =
        ToggleNotationAction.createTieAction(MAIN_FRAME);

    public static final List<TupletAction> TOGGLE_TUPLET_ACTIONS =
        List.of(
            TupletAction.createDupletAction(MAIN_FRAME),
            TupletAction.createTripletAction(MAIN_FRAME),
            TupletAction.createQuadrupletAction(MAIN_FRAME),
            TupletAction.createQuintupletAction(MAIN_FRAME),
            TupletAction.createSextupletAction(MAIN_FRAME),
            TupletAction.createSeptupletAction(MAIN_FRAME)
        );

    public static final TupletAction REMOVE_TUPLET_ACTION =
        TupletAction.createRemoveAction(MAIN_FRAME);

    public static final FlipStemDirectionAction FLIP_STEM_DIRECTION_ACTION =
        FlipStemDirectionAction.createAction(MAIN_FRAME);

    public static final EditLyricAction EDIT_LYRIC_ACTION = EditLyricAction.createAction(MAIN_FRAME);

    public static final ToggleTrillAction TOGGLE_TRILL_ACTION = ToggleTrillAction.createAction(MAIN_FRAME);

    public static final AddDynamicsAction ADD_CRESCENDO_ACTION =
        AddDynamicsAction.createCrescendoAction(MAIN_FRAME);

    public static final AddDynamicsAction ADD_DIMINUENDO_ACTION =
        AddDynamicsAction.createDiminuendoAction(MAIN_FRAME);

    public static final RemoveDynamicsAction REMOVE_DYNAMICS_ACTION =
        RemoveDynamicsAction.createAction(MAIN_FRAME);

    public static final DynamicMarkingAction DYNAMIC_PP_ACTION =
        DynamicMarkingAction.createPianissimoAction(MAIN_FRAME);

    public static final DynamicMarkingAction DYNAMIC_P_ACTION =
        DynamicMarkingAction.createPianoAction(MAIN_FRAME);

    public static final DynamicMarkingAction DYNAMIC_MP_ACTION =
        DynamicMarkingAction.createMezzoPianoAction(MAIN_FRAME);

    public static final DynamicMarkingAction DYNAMIC_MF_ACTION =
        DynamicMarkingAction.createMezzoForteAction(MAIN_FRAME);

    public static final DynamicMarkingAction DYNAMIC_F_ACTION =
        DynamicMarkingAction.createForteAction(MAIN_FRAME);

    public static final DynamicMarkingAction DYNAMIC_FF_ACTION =
        DynamicMarkingAction.createFortissimoAction(MAIN_FRAME);

    public static final ActionGroup<DynamicMarkingAction> DYNAMIC_MARKING_ACTION_GROUP =
        new ActionGroup<>(
            DYNAMIC_PP_ACTION,
            DYNAMIC_P_ACTION,
            DYNAMIC_MP_ACTION,
            DYNAMIC_MF_ACTION,
            DYNAMIC_F_ACTION,
            DYNAMIC_FF_ACTION
        );

    public static final TempoChangeAction TEMPO_CHANGE_ACTION =
        TempoChangeAction.createAction(MAIN_FRAME);

    public static final BeatChangeAction BEAT_CHANGE_ACTION =
        BeatChangeAction.createAction(MAIN_FRAME);

    public static final AnnotationAction ANNOTATION_ACTION =
        AnnotationAction.createAction(MAIN_FRAME);

    public static final KeySignatureChangeAction KEY_SIGNATURE_CHANGE_ACTION =
        KeySignatureChangeAction.createAction(MAIN_FRAME);

    public static final List<UIAction> STAFF_ANNOTATION_ACTIONS = List.of(
        TEMPO_CHANGE_ACTION,
        BEAT_CHANGE_ACTION,
        ANNOTATION_ACTION,
        KEY_SIGNATURE_CHANGE_ACTION
    );

    public static final FermataAction FERMATA_ACTION =
        FermataAction.createAction(MAIN_FRAME);

    public static final PreferencesOpenAction PREFERENCES_ACTION = new PreferencesOpenAction(MAIN_FRAME);

    public static final DialogOpenAction<
        SongSettingsDialog
        > SONG_SETTINGS_ACTION = new DialogOpenAction<>(
        MAIN_FRAME,
        Strings.get(Strings.ACTION_SONG_SETTINGS),
        KeyEvent.VK_G,
        MENU_SHORTCUT_MASK,
        SongSettingsDialog.class,
        Flag.DISABLE_WHEN_PLAYING
    );

    public static final AboutOpenAction ABOUT_ACTION = new AboutOpenAction(MAIN_FRAME);

    public static final PrintAction PRINT_ACTION = PrintAction.createAction(MAIN_FRAME);
    public static final QuitAction QUIT_ACTION = QuitAction.createAction(MAIN_FRAME);

    public static final CutAction CUT_ACTION = CutAction.createAction(MAIN_FRAME);
    public static final CopyAction COPY_ACTION = CopyAction.createAction(MAIN_FRAME);
    public static final PasteAction PASTE_ACTION = PasteAction.createAction(MAIN_FRAME);
    public static final DeleteAction DELETE_ACTION = DeleteAction.createAction(MAIN_FRAME);
    public static final SelectLineAction SELECT_LINE_ACTION = SelectLineAction.createAction(MAIN_FRAME);
    public static final DeselectAction DESELECT_ACTION = DeselectAction.createAction(MAIN_FRAME);

    // Strong reference prevents GC (mbassy uses weak references)
    private static final ResetHandler RESET_HANDLER = new ResetHandler();

    static {
        DURATION_ACTION_GROUP.setDefaultAction(QUARTER_NOTE_ACTION);
        MessageCenter.subscribe(RESET_HANDLER);
    }

    private static void resetToDefaults() {
        // Non-silent resets — these need perform() to update downstream state
        // (ScoreView.mode via ModeDidChangeNotification, preview element via
        // UpdatePreviewElementCommand)
        MODE_ACTION_GROUP.select(EDIT_MODE_ACTION, EDIT_MODE_ACTION);
        DURATION_ACTION_GROUP.select(QUARTER_NOTE_ACTION, QUARTER_NOTE_ACTION);

        // Silent resets — no downstream state to update
        ACCIDENTAL_ACTION_GROUP.reset();
        ARTICULATION_ACTION_GROUP.reset();
        DOT_ACTION_GROUP.reset();
        DYNAMIC_MARKING_ACTION_GROUP.reset();
        NON_DURATION_ACTION_GROUP.reset();

        // Standalone toggles
        ACCENT_ACTION.reset();
        REST_ACTION.reset();
        FERMATA_ACTION.reset();
        ACCIDENTAL_IN_PARENS_ACTION.reset();
    }

    private static @Nullable List<AppMenuAction> appMenuActions = null;

    /**
     * Returns all actions that implement {@link AppMenuAction}, discovered
     * via reflection over the {@code public static final} fields of this class.
     * The result is cached after the first call.
     */
    public static List<AppMenuAction> getAppMenuActions() {
        if (appMenuActions != null) {
            return appMenuActions;
        }

        var result = new ArrayList<AppMenuAction>();
        var requiredModifiers = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;

        for (var field : Actions.class.getDeclaredFields()) {
            if ((field.getModifiers() & requiredModifiers) != requiredModifiers) {
                continue;
            }

            try {
                if (field.get(null) instanceof AppMenuAction action) {
                    result.add(action);
                }
            } catch (IllegalAccessException e) {
                LOG.warn("Cannot access field '{}'", field.getName(), e);
            }
        }

        appMenuActions = Collections.unmodifiableList(result);
        return appMenuActions;
    }

    private Actions() {
    }

    private static class ResetHandler {
        @Handler(priority = Message.HIGH_PRIORITY)
        public void documentDidLoad(DocumentDidLoadNotification message) {
            resetToDefaults();
        }
    }
}
