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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.KeyStroke;

import net.engio.mbassy.listener.Handler;
import net.engio.mbassy.listener.Listener;
import net.engio.mbassy.listener.References;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.ui.Mode;
import songscribe.ui.action.UIAction.AppMenuAction;
import songscribe.ui.component.MainFrame;
import songscribe.undo.UndoController;
import songscribe.util.UIUtils;

import static songscribe.util.UIUtils.MENU_SHORTCUT_MASK;

/**
 * A repository for global action-related constants and action groups.
 *
 * <p>Constants are populated by {@link #initialize(MainFrame)}, which must be called
 * before any constant is first read.
 *
 * <h2>Lifecycle</h2>
 * All constants live for the process; nothing tears them down. A caller that replaces them must
 * first have retired the bus they joined, because the constants it replaces keep whatever
 * registrations they made.
 */
// The action constants are @NonNull but populated lazily by initialize() (which needs
// the MainFrame, unavailable at class-load), not at declaration. NullAway.Init suppresses
// the "uninitialized field" check for them; keeping them non-null avoids poisoning the
// ~70 call sites across the codebase with nullability.
@SuppressWarnings({
    "NullAway.Init",
    "StaticVariableMayNotBeInitialized",
    "StaticNonFinalField",
    "StaticVariableUsedBeforeInitialization"
})
public final class Actions {

    public static ModeAction SELECT_MODE_ACTION;
    public static ModeAction EDIT_MODE_ACTION;
    public static CycleModeAction CYCLE_MODE_ACTION;
    public static ActionGroup<ModeAction> MODE_ACTION_GROUP;

    public static ElementTypeAction GRACE_EIGHTH_NOTE_ACTION;
    public static ElementTypeAction THIRTY_SECOND_NOTE_ACTION;
    public static ElementTypeAction SIXTEENTH_NOTE_ACTION;
    public static ElementTypeAction EIGHTH_NOTE_ACTION;
    public static ElementTypeAction QUARTER_NOTE_ACTION;
    public static ElementTypeAction HALF_NOTE_ACTION;
    public static ElementTypeAction WHOLE_NOTE_ACTION;

    public static List<ElementTypeAction> NOTE_DURATION_ACTIONS;
    public static DurationActionGroup DURATION_ACTION_GROUP;

    public static DotAction<?> DOT_ACTION;
    public static DotAction<?> DOUBLE_DOT_ACTION;
    public static ActionGroup<DotAction<?>> DOT_ACTION_GROUP;

    public static RestModeAction REST_ACTION;

    public static AccidentalAction FLAT_ACTION;
    public static AccidentalAction DOUBLE_FLAT_ACTION;
    public static AccidentalAction NATURAL_ACTION;
    public static AccidentalAction SHARP_ACTION;
    public static AccidentalAction DOUBLE_SHARP_ACTION;

    public static ActionGroup<AccidentalAction> ACCIDENTAL_ACTION_GROUP;

    public static ElementTypeAction[] REPEAT_ACTIONS;
    public static ElementTypeAction[] BARLINE_ACTIONS;

    // Also a member of BARLINE_ACTIONS. BarlineMenu identifies it through this field rather
    // than by array index.
    public static FinalDoubleBarlineAction FINAL_DOUBLE_BARLINE_ACTION;

    public static ElementTypeAction BREATH_MARK_ACTION;
    public static ActionGroup<ElementTypeAction> NON_DURATION_ACTION_GROUP;

    public static FirstSecondEndingAction MAKE_ENDING_ACTION;
    public static ArticulationAction ACCENT_ACTION;
    public static ArticulationAction STACCATO_ACTION;

    public static ToggleNotationAction TOGGLE_BEAM_ACTION;
    public static ToggleNotationAction TOGGLE_TIE_ACTION;
    public static ToggleNotationAction GLISSANDO_ACTION;
    public static ToggleNotationAction FALL_ACTION;

    public static List<TupletAction> TOGGLE_TUPLET_ACTIONS;

    public static StemDirectionAction FLIP_STEM_DIRECTION_ACTION;
    public static StemDirectionAction AUTO_STEM_DIRECTION_ACTION;
    public static EditLyricAction EDIT_LYRIC_ACTION;
    public static TrillAction TRILL_ACTION;

    public static HairpinAction HAIRPIN_CRESCENDO_ACTION;
    public static HairpinAction HAIRPIN_DIMINUENDO_ACTION;

    public static DynamicMarkingAction DYNAMIC_PP_ACTION;
    public static DynamicMarkingAction DYNAMIC_P_ACTION;
    public static DynamicMarkingAction DYNAMIC_MP_ACTION;
    public static DynamicMarkingAction DYNAMIC_MF_ACTION;
    public static DynamicMarkingAction DYNAMIC_F_ACTION;
    public static DynamicMarkingAction DYNAMIC_FF_ACTION;
    public static ActionGroup<DynamicMarkingAction> DYNAMIC_MARKING_ACTION_GROUP;

    public static TempoChangeAction TEMPO_CHANGE_ACTION;
    public static BeatChangeAction BEAT_CHANGE_ACTION;
    public static AnnotationAction ANNOTATION_ACTION;
    public static KeyChangeAction KEY_CHANGE_ACTION;

    public static List<UIAction> STAFF_ANNOTATION_ACTIONS;
    public static FermataAction FERMATA_ACTION;

    public static PreferencesOpenAction PREFERENCES_ACTION;
    public static SongSettingsOpenAction SONG_SETTINGS_ACTION;
    public static AboutOpenAction ABOUT_ACTION;

    public static PrintAction PRINT_ACTION;
    public static QuitAction QUIT_ACTION;
    public static ClearRecentsAction CLEAR_RECENTS_ACTION;

    public static UndoAction UNDO_ACTION;
    public static RedoAction REDO_ACTION;

    public static CutAction CUT_ACTION;
    public static CopyAction COPY_ACTION;
    public static PasteAction PASTE_ACTION;
    public static DeleteAction DELETE_ACTION;
    public static SelectAllElementsAction SELECT_ALL_ELEMENTS_ACTION;
    public static DeselectAction DESELECT_ACTION;

    public static ZoomAction ZOOM_IN_ACTION;
    public static ZoomAction ZOOM_OUT_ACTION;
    public static List<ZoomLevelAction> ZOOM_LEVEL_ACTIONS;

    public static MinimizeWindowAction MINIMIZE_WINDOW_ACTION;
    public static ZoomWindowAction ZOOM_WINDOW_ACTION;
    public static FocusScoreAction FOCUS_SCORE_ACTION;
    public static ToggleMessagesAction TOGGLE_MESSAGES_ACTION;

    private static final List<UIAction> registeredActions = new ArrayList<>();

    private static List<AppMenuAction> appMenuActions = List.of();

    /**
     * Called once per process, at the top of {@link MainFrame#initFrame()}, before any
     * constant in this class is first referenced.
     */
    public static void initialize(MainFrame mainFrame) {
        // Constructed here rather than in a static initializer so that merely loading this
        // class cannot register the handler before the constants it dereferences exist.
        new ResetHandler();

        UndoController.initialize(mainFrame::getScoreView);

        SELECT_MODE_ACTION = register(ModeAction.createSelectModeAction(mainFrame));
        EDIT_MODE_ACTION = register(ModeAction.createEditModeAction(mainFrame));
        CYCLE_MODE_ACTION = register(CycleModeAction.createAction(mainFrame));
        MODE_ACTION_GROUP = new ActionGroup<>(SELECT_MODE_ACTION, EDIT_MODE_ACTION);

        GRACE_EIGHTH_NOTE_ACTION = register(ElementTypeAction.createGraceEighthNoteAction(mainFrame));
        THIRTY_SECOND_NOTE_ACTION = register(ElementTypeAction.createThirtySecondNoteAction(mainFrame));
        SIXTEENTH_NOTE_ACTION = register(ElementTypeAction.createSixteenthNoteAction(mainFrame));
        EIGHTH_NOTE_ACTION = register(ElementTypeAction.createEighthNoteAction(mainFrame));
        QUARTER_NOTE_ACTION = register(ElementTypeAction.createQuarterNoteAction(mainFrame));
        HALF_NOTE_ACTION = register(ElementTypeAction.createHalfNoteAction(mainFrame));
        WHOLE_NOTE_ACTION = register(ElementTypeAction.createWholeNoteAction(mainFrame));

        NOTE_DURATION_ACTIONS = List.of(
            GRACE_EIGHTH_NOTE_ACTION,
            THIRTY_SECOND_NOTE_ACTION,
            SIXTEENTH_NOTE_ACTION,
            EIGHTH_NOTE_ACTION,
            QUARTER_NOTE_ACTION,
            HALF_NOTE_ACTION,
            WHOLE_NOTE_ACTION
        );

        DURATION_ACTION_GROUP = new DurationActionGroup(
            GRACE_EIGHTH_NOTE_ACTION,
            THIRTY_SECOND_NOTE_ACTION,
            SIXTEENTH_NOTE_ACTION,
            EIGHTH_NOTE_ACTION,
            QUARTER_NOTE_ACTION,
            HALF_NOTE_ACTION,
            WHOLE_NOTE_ACTION
        );
        DURATION_ACTION_GROUP.setDefaultAction(QUARTER_NOTE_ACTION);

        DOT_ACTION = register(DotAction.createDotAction(mainFrame));
        DOUBLE_DOT_ACTION = register(DotAction.createDoubleDotAction(mainFrame));
        DOT_ACTION_GROUP = new ActionGroup<>(DOT_ACTION, DOUBLE_DOT_ACTION);

        REST_ACTION = register(RestModeAction.createAction(mainFrame));

        FLAT_ACTION = register(AccidentalAction.createFlatAction(mainFrame));
        DOUBLE_FLAT_ACTION = register(AccidentalAction.createDoubleFlatAction(mainFrame));
        NATURAL_ACTION = register(AccidentalAction.createNaturalAction(mainFrame));
        SHARP_ACTION = register(AccidentalAction.createSharpAction(mainFrame));
        DOUBLE_SHARP_ACTION = register(AccidentalAction.createDoubleSharpAction(mainFrame));
        ACCIDENTAL_ACTION_GROUP = new ActionGroup<>(
            DOUBLE_FLAT_ACTION,
            FLAT_ACTION,
            NATURAL_ACTION,
            SHARP_ACTION,
            DOUBLE_SHARP_ACTION
        );

        REPEAT_ACTIONS = new ElementTypeAction[]{
            register(ElementTypeAction.createLeftRepeatAction(mainFrame)),
            register(ElementTypeAction.createRightRepeatAction(mainFrame)),
            register(ElementTypeAction.createLeftRightRepeatAction(mainFrame)),
        };
        FINAL_DOUBLE_BARLINE_ACTION = register(FinalDoubleBarlineAction.createAction(mainFrame));
        BARLINE_ACTIONS = new ElementTypeAction[]{
            register(ElementTypeAction.createDoubleBarlineAction(mainFrame)),
            register(ElementTypeAction.createSingleBarlineAction(mainFrame)),
            FINAL_DOUBLE_BARLINE_ACTION,
        };
        BREATH_MARK_ACTION = register(ElementTypeAction.createBreathMarkAction(mainFrame));
        NON_DURATION_ACTION_GROUP = new NonDurationActionGroup();

        MAKE_ENDING_ACTION = register(new FirstSecondEndingAction(mainFrame));
        ACCENT_ACTION = register(ArticulationAction.createAccentAction(mainFrame));
        STACCATO_ACTION = register(ArticulationAction.createStaccatoAction(mainFrame));

        TOGGLE_BEAM_ACTION = register(ToggleNotationAction.createBeamAction(mainFrame));
        TOGGLE_TIE_ACTION = register(ToggleNotationAction.createTieAction(mainFrame));
        GLISSANDO_ACTION = register(ToggleNotationAction.createGlissandoAction(mainFrame));
        FALL_ACTION = register(ToggleNotationAction.createFallAction(mainFrame));

        TOGGLE_TUPLET_ACTIONS = List.of(
            register(TupletAction.createDupletAction(mainFrame)),
            register(TupletAction.createTripletAction(mainFrame)),
            register(TupletAction.createQuadrupletAction(mainFrame)),
            register(TupletAction.createQuintupletAction(mainFrame)),
            register(TupletAction.createSextupletAction(mainFrame)),
            register(TupletAction.createSeptupletAction(mainFrame))
        );

        FLIP_STEM_DIRECTION_ACTION = register(StemDirectionAction.createFlipAction(mainFrame));
        AUTO_STEM_DIRECTION_ACTION = register(StemDirectionAction.createAutoAction(mainFrame));
        EDIT_LYRIC_ACTION = register(EditLyricAction.createAction(mainFrame));
        TRILL_ACTION = register(TrillAction.createAction(mainFrame));

        HAIRPIN_CRESCENDO_ACTION = register(HairpinAction.createCrescendoAction(mainFrame));
        HAIRPIN_DIMINUENDO_ACTION = register(HairpinAction.createDiminuendoAction(mainFrame));

        DYNAMIC_PP_ACTION = register(DynamicMarkingAction.createPianissimoAction(mainFrame));
        DYNAMIC_P_ACTION = register(DynamicMarkingAction.createPianoAction(mainFrame));
        DYNAMIC_MP_ACTION = register(DynamicMarkingAction.createMezzoPianoAction(mainFrame));
        DYNAMIC_MF_ACTION = register(DynamicMarkingAction.createMezzoForteAction(mainFrame));
        DYNAMIC_F_ACTION = register(DynamicMarkingAction.createForteAction(mainFrame));
        DYNAMIC_FF_ACTION = register(DynamicMarkingAction.createFortissimoAction(mainFrame));
        DYNAMIC_MARKING_ACTION_GROUP = new ActionGroup<>(
            DYNAMIC_PP_ACTION,
            DYNAMIC_P_ACTION,
            DYNAMIC_MP_ACTION,
            DYNAMIC_MF_ACTION,
            DYNAMIC_F_ACTION,
            DYNAMIC_FF_ACTION
        );

        TEMPO_CHANGE_ACTION = register(TempoChangeAction.createAction(mainFrame));
        BEAT_CHANGE_ACTION = register(BeatChangeAction.createAction(mainFrame));
        ANNOTATION_ACTION = register(AnnotationAction.createAction(mainFrame));
        KEY_CHANGE_ACTION = register(KeyChangeAction.createAction(mainFrame));
        STAFF_ANNOTATION_ACTIONS = List.of(
            TEMPO_CHANGE_ACTION,
            BEAT_CHANGE_ACTION,
            ANNOTATION_ACTION,
            KEY_CHANGE_ACTION
        );

        FERMATA_ACTION = register(FermataAction.createAction(mainFrame));
        PREFERENCES_ACTION = register(new PreferencesOpenAction(mainFrame));
        SONG_SETTINGS_ACTION = register(SongSettingsOpenAction.createAction(mainFrame));
        ABOUT_ACTION = register(AboutOpenAction.createAction(mainFrame));
        PRINT_ACTION = register(PrintAction.createAction(mainFrame));
        QUIT_ACTION = register(QuitAction.createAction(mainFrame));
        CLEAR_RECENTS_ACTION = register(ClearRecentsAction.createAction(mainFrame));

        UNDO_ACTION = register(UndoAction.createAction(mainFrame));
        REDO_ACTION = register(RedoAction.createAction(mainFrame));

        CUT_ACTION = register(CutAction.createAction(mainFrame));
        COPY_ACTION = register(CopyAction.createAction(mainFrame));
        PASTE_ACTION = register(PasteAction.createAction(mainFrame));
        DELETE_ACTION = register(DeleteAction.createAction(mainFrame));
        SELECT_ALL_ELEMENTS_ACTION = register(SelectAllElementsAction.createAction(mainFrame));
        DESELECT_ACTION = register(DeselectAction.createAction(mainFrame));

        ZOOM_IN_ACTION = register(ZoomAction.createZoomInAction(mainFrame));
        ZOOM_OUT_ACTION = register(ZoomAction.createZoomOutAction(mainFrame));
        ZOOM_LEVEL_ACTIONS = ZoomLevelAction.createActions(mainFrame);
        ZOOM_LEVEL_ACTIONS.forEach(Actions::register);

        MINIMIZE_WINDOW_ACTION = register(MinimizeWindowAction.createAction(mainFrame));
        ZOOM_WINDOW_ACTION = register(ZoomWindowAction.createAction(mainFrame));
        FOCUS_SCORE_ACTION = register(FocusScoreAction.createAction(mainFrame));
        TOGGLE_MESSAGES_ACTION = register(ToggleMessagesAction.createAction(mainFrame));

        // Cmd+Shift+= is a secondary accelerator for zoom-in (alongside Cmd+=), matching
        // the common "=" / "+" key ambiguity across keyboard layouts.
        UIUtils.registerActionKeystroke(
            mainFrame.getRootPane(),
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK),
            ZOOM_IN_ACTION
        );

        appMenuActions = registeredActions.stream()
            .filter(AppMenuAction.class::isInstance)
            .map(AppMenuAction.class::cast)
            .toList();
    }

    private static <A extends UIAction> A register(A action) {
        registeredActions.add(action);
        return action;
    }

    /** Every action that {@link #initialize} builds, each one once, in construction order. */
    public static List<UIAction> allActions() {
        return Collections.unmodifiableList(registeredActions);
    }

    public static ModeAction modeAction(Mode mode) {
        return switch (mode) {
            case SELECT -> SELECT_MODE_ACTION;
            case EDIT -> EDIT_MODE_ACTION;
        };
    }

    private static void resetToDefaults() {
        // Non-silent resets — these need perform() to update downstream state
        // (ScoreView.mode via ModeDidChangeNotification, preview element via
        // UpdatePreviewElementCommand)
        MODE_ACTION_GROUP.select(EDIT_MODE_ACTION, EDIT_MODE_ACTION);
        DURATION_ACTION_GROUP.select(QUARTER_NOTE_ACTION, QUARTER_NOTE_ACTION);

        clearNoteDecorations();
        DYNAMIC_MARKING_ACTION_GROUP.reset();
        NON_DURATION_ACTION_GROUP.reset();

        REST_ACTION.reset();
        FERMATA_ACTION.reset();
        TRILL_ACTION.reset();

        // The select() above posts nothing when the quarter note is already selected, and the
        // resets after it are silent, so without this the preview keeps the previous
        // document's decorations. The scope is ELEMENT because a rest or barline preview
        // cannot be decorated back into a note.
        MessageCenter.post(new UpdatePreviewElementCommand(UpdatePreviewElementCommand.Scope.ELEMENT));
    }

    /**
     * The one place that must stay in sync with the toggles
     * {@link songscribe.ui.edit.EditModeManager#decorateElement} reads.
     */
    public static void clearNoteDecorations() {
        DOT_ACTION_GROUP.clearSelection();
        ACCIDENTAL_ACTION_GROUP.clearSelection();
        ACCENT_ACTION.setSelected(false);
        STACCATO_ACTION.setSelected(false);
    }

    public static List<AppMenuAction> getAppMenuActions() {
        return appMenuActions;
    }

    private Actions() {
    }

    /**
     * Lives for the process; the bus holds it strongly because nothing else does.
     */
    @Listener(references = References.Strong)
    private static final class ResetHandler {

        private ResetHandler() {
            MessageSubscription.addProcessListener(this);
        }

        @Handler(priority = Message.HIGH_PRIORITY)
        public void documentDidLoad(DocumentDidLoadNotification message) {
            resetToDefaults();
        }
    }
}
