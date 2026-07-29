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
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.ui.action.UIAction.AppMenuAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.SongSettingsDialog;
import songscribe.undo.UndoController;
import songscribe.util.UIUtils;

/**
 * This class serves as a repository for global action-related constants and action groups.
 * Playback actions are in the playback package.
 *
 * <p>Constants are populated by {@link #initialize(MainFrame)}, which must be called
 * before any constant is first read. The expected call sequence is:
 *
 * <pre>
 * MainFrame.getInstance()
 *   └─► MainFrame.initFrame()
 *         └─► Actions.initialize(this)
 *               └─► first constant use (e.g. MODE_ACTION_GROUP.select(...))
 * </pre>
 */
// The action constants are @NonNull but populated lazily by initialize() (which needs
// the MainFrame, unavailable at class-load), not at declaration. NullAway.Init suppresses
// the "uninitialized field" check for them; keeping them non-null avoids poisoning the
// ~70 call sites across the codebase with nullability.
@SuppressWarnings("NullAway.Init")
public final class Actions {

    // Injected by initialize() before any constant is referenced.
    // Null until initialize() is called; null again after resetForTest().
    @Nullable
    private static MainFrame mainFrame;

    private static final Logger LOG = LoggerFactory.getLogger(Actions.class);

    //
    // Mode actions
    //
    public static ModeAction SELECT_MODE_ACTION;
    public static ModeAction EDIT_MODE_ACTION;
    public static CycleModeAction CYCLE_MODE_ACTION;
    public static ModeAction ADJUST_MUSIC_MODE_ACTION;
    public static ModeAction ADJUST_VERTICAL_MODE_ACTION;
    public static ActionGroup<ModeAction> MODE_ACTION_GROUP;

    //
    // Duration actions
    //
    public static ElementTypeAction GRACE_EIGHTH_NOTE_ACTION;
    public static ElementTypeAction THIRTY_SECOND_NOTE_ACTION;
    public static ElementTypeAction SIXTEENTH_NOTE_ACTION;
    public static ElementTypeAction EIGHTH_NOTE_ACTION;
    public static ElementTypeAction QUARTER_NOTE_ACTION;
    public static ElementTypeAction HALF_NOTE_ACTION;
    public static ElementTypeAction WHOLE_NOTE_ACTION;
    public static ElementTypeAction GLISSANDO_ACTION;
    public static ElementTypeAction FALL_ACTION;

    public static List<ElementTypeAction> NOTE_DURATION_ACTIONS;
    public static DurationActionGroup DURATION_ACTION_GROUP;

    public static DotAction DOT_ACTION;
    public static DotAction DOUBLE_DOT_ACTION;
    public static ActionGroup<DotAction> DOT_ACTION_GROUP;

    public static RestModeAction REST_ACTION;

    public static AccidentalAction FLAT_ACTION;
    public static AccidentalAction DOUBLE_FLAT_ACTION;
    public static AccidentalAction NATURAL_FLAT_ACTION;
    public static AccidentalAction NATURAL_ACTION;
    public static AccidentalAction SHARP_ACTION;
    public static AccidentalAction DOUBLE_SHARP_ACTION;
    public static AccidentalAction NATURAL_SHARP_ACTION;
    public static AccidentalInParensAction ACCIDENTAL_IN_PARENS_ACTION;

    public static ActionGroup<AccidentalAction> ACCIDENTAL_ACTION_GROUP;

    public static ElementTypeAction[] REPEAT_ACTIONS;
    public static ElementTypeAction[] BARLINE_ACTIONS;
    public static ElementTypeAction BREATH_MARK_ACTION;
    public static ActionGroup<ElementTypeAction> NON_DURATION_ACTION_GROUP;

    public static FirstSecondEndingAction MAKE_ENDING_ACTION;
    public static ForceArticulationAction ACCENT_ACTION;
    public static DurationArticulationAction STACCATO_ACTION;
    public static ActionGroup<DurationArticulationAction> ARTICULATION_ACTION_GROUP;

    public static ToggleNotationAction TOGGLE_BEAM_ACTION;
    public static ToggleNotationAction TOGGLE_TIE_ACTION;

    public static List<TupletAction> TOGGLE_TUPLET_ACTIONS;
    public static TupletAction REMOVE_TUPLET_ACTION;

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
    public static KeySignatureChangeAction KEY_SIGNATURE_CHANGE_ACTION;

    public static List<UIAction> STAFF_ANNOTATION_ACTIONS;
    public static FermataAction FERMATA_ACTION;

    public static PreferencesOpenAction PREFERENCES_ACTION;
    public static DialogOpenAction<SongSettingsDialog> SONG_SETTINGS_ACTION;
    public static AboutOpenAction ABOUT_ACTION;

    public static PrintAction PRINT_ACTION;
    public static QuitAction QUIT_ACTION;

    public static UndoAction UNDO_ACTION;
    public static RedoAction REDO_ACTION;

    public static CutAction CUT_ACTION;
    public static CopyAction COPY_ACTION;
    public static PasteAction PASTE_ACTION;
    public static DeleteAction DELETE_ACTION;
    public static SelectAllElementsAction SELECT_ALL_ELEMENTS_ACTION;
    public static DeselectAction DESELECT_ACTION;

    //
    // Zoom actions
    //
    public static ZoomAction ZOOM_IN_ACTION;
    public static ZoomAction ZOOM_OUT_ACTION;
    public static ResetZoomAction RESET_ZOOM_ACTION;

    // Strong reference prevents GC (mbassy uses weak references)
    private static final ResetHandler RESET_HANDLER = new ResetHandler();

    /**
     * Initializes all action constants using {@code mainFrame} as the owner.
     *
     * <p>Must be called once at the top of {@link MainFrame#initFrame()} before any
     * constant in this class is first referenced. Calling this method again (e.g. in
     * tests) replaces all constants with freshly constructed instances.
     */
    public static void initialize(MainFrame mainFrame) {
        Actions.mainFrame = mainFrame;

        // Subscribed here rather than in a static initializer so that merely loading
        // this class (e.g. a test teardown calling unsubscribeForTest) cannot register
        // the handler before the constants it dereferences exist. Subscribing is
        // idempotent, so repeated initialize() calls in tests are safe.
        MessageCenter.subscribe(RESET_HANDLER);

        UndoController.initialize();

        //
        // Mode actions
        //
        SELECT_MODE_ACTION = ModeAction.createSelectModeAction(mainFrame);
        EDIT_MODE_ACTION = ModeAction.createEditModeAction(mainFrame);
        CYCLE_MODE_ACTION = CycleModeAction.createAction(mainFrame);
        ADJUST_MUSIC_MODE_ACTION = ModeAction.createAdjustMusicModeAction(mainFrame);
        ADJUST_VERTICAL_MODE_ACTION = ModeAction.createAdjustVerticalModeAction(mainFrame);
        MODE_ACTION_GROUP = new ActionGroup<>(
            SELECT_MODE_ACTION,
            EDIT_MODE_ACTION,
            ADJUST_MUSIC_MODE_ACTION,
            ADJUST_VERTICAL_MODE_ACTION
        );

        //
        // Duration actions
        //
        GRACE_EIGHTH_NOTE_ACTION = ElementTypeAction.createGraceEighthNoteAction(mainFrame);
        THIRTY_SECOND_NOTE_ACTION = ElementTypeAction.createThirtySecondNoteAction(mainFrame);
        SIXTEENTH_NOTE_ACTION = ElementTypeAction.createSixteenthNoteAction(mainFrame);
        EIGHTH_NOTE_ACTION = ElementTypeAction.createEighthNoteAction(mainFrame);
        QUARTER_NOTE_ACTION = ElementTypeAction.createQuarterNoteAction(mainFrame);
        HALF_NOTE_ACTION = ElementTypeAction.createHalfNoteAction(mainFrame);
        WHOLE_NOTE_ACTION = ElementTypeAction.createWholeNoteAction(mainFrame);
        GLISSANDO_ACTION = ElementTypeAction.createGlissandoAction(mainFrame);
        FALL_ACTION = ElementTypeAction.createFallAction(mainFrame);

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
            WHOLE_NOTE_ACTION,
            GLISSANDO_ACTION,
            FALL_ACTION
        );
        DURATION_ACTION_GROUP.setDefaultAction(QUARTER_NOTE_ACTION);

        DOT_ACTION = DotAction.createDotAction(mainFrame);
        DOUBLE_DOT_ACTION = DotAction.createDoubleDotAction(mainFrame);
        DOT_ACTION_GROUP = new ActionGroup<>(DOT_ACTION, DOUBLE_DOT_ACTION);

        REST_ACTION = RestModeAction.createAction(mainFrame);

        FLAT_ACTION = AccidentalAction.createFlatAction(mainFrame);
        DOUBLE_FLAT_ACTION = AccidentalAction.createDoubleFlatAction(mainFrame);
        NATURAL_FLAT_ACTION = AccidentalAction.createNaturalFlatAction(mainFrame);
        NATURAL_ACTION = AccidentalAction.createNaturalAction(mainFrame);
        SHARP_ACTION = AccidentalAction.createSharpAction(mainFrame);
        DOUBLE_SHARP_ACTION = AccidentalAction.createDoubleSharpAction(mainFrame);
        NATURAL_SHARP_ACTION = AccidentalAction.createNaturalSharpAction(mainFrame);
        ACCIDENTAL_IN_PARENS_ACTION = AccidentalInParensAction.createAction(mainFrame);
        ACCIDENTAL_ACTION_GROUP = new ActionGroup<>(
            FLAT_ACTION,
            DOUBLE_FLAT_ACTION,
            NATURAL_FLAT_ACTION,
            NATURAL_ACTION,
            SHARP_ACTION,
            DOUBLE_SHARP_ACTION,
            NATURAL_SHARP_ACTION
        );

        REPEAT_ACTIONS = new ElementTypeAction[]{
            ElementTypeAction.createLeftRepeatAction(mainFrame),
            ElementTypeAction.createRightRepeatAction(mainFrame),
            ElementTypeAction.createLeftRightRepeatAction(mainFrame),
        };
        BARLINE_ACTIONS = new ElementTypeAction[]{
            ElementTypeAction.createDoubleBarlineAction(mainFrame),
            ElementTypeAction.createSingleBarlineAction(mainFrame),
        };
        BREATH_MARK_ACTION = ElementTypeAction.createBreathMarkAction(mainFrame);
        NON_DURATION_ACTION_GROUP = new NonDurationActionGroup();

        MAKE_ENDING_ACTION = new FirstSecondEndingAction(mainFrame);
        ACCENT_ACTION = ForceArticulationAction.createAccentAction(mainFrame);
        STACCATO_ACTION = DurationArticulationAction.createStaccatoAction(mainFrame);
        ARTICULATION_ACTION_GROUP = new ActionGroup<>(STACCATO_ACTION);

        TOGGLE_BEAM_ACTION = ToggleNotationAction.createBeamAction(mainFrame);
        TOGGLE_TIE_ACTION = ToggleNotationAction.createTieAction(mainFrame);

        TOGGLE_TUPLET_ACTIONS = List.of(
            TupletAction.createDupletAction(mainFrame),
            TupletAction.createTripletAction(mainFrame),
            TupletAction.createQuadrupletAction(mainFrame),
            TupletAction.createQuintupletAction(mainFrame),
            TupletAction.createSextupletAction(mainFrame),
            TupletAction.createSeptupletAction(mainFrame)
        );
        REMOVE_TUPLET_ACTION = TupletAction.createRemoveAction(mainFrame);

        FLIP_STEM_DIRECTION_ACTION = StemDirectionAction.createFlipAction(mainFrame);
        AUTO_STEM_DIRECTION_ACTION = StemDirectionAction.createAutoAction(mainFrame);
        EDIT_LYRIC_ACTION = EditLyricAction.createAction(mainFrame);
        TRILL_ACTION = TrillAction.createAction(mainFrame);

        HAIRPIN_CRESCENDO_ACTION = HairpinAction.createCrescendoAction(mainFrame);
        HAIRPIN_DIMINUENDO_ACTION = HairpinAction.createDiminuendoAction(mainFrame);

        DYNAMIC_PP_ACTION = DynamicMarkingAction.createPianissimoAction(mainFrame);
        DYNAMIC_P_ACTION = DynamicMarkingAction.createPianoAction(mainFrame);
        DYNAMIC_MP_ACTION = DynamicMarkingAction.createMezzoPianoAction(mainFrame);
        DYNAMIC_MF_ACTION = DynamicMarkingAction.createMezzoForteAction(mainFrame);
        DYNAMIC_F_ACTION = DynamicMarkingAction.createForteAction(mainFrame);
        DYNAMIC_FF_ACTION = DynamicMarkingAction.createFortissimoAction(mainFrame);
        DYNAMIC_MARKING_ACTION_GROUP = new ActionGroup<>(
            DYNAMIC_PP_ACTION,
            DYNAMIC_P_ACTION,
            DYNAMIC_MP_ACTION,
            DYNAMIC_MF_ACTION,
            DYNAMIC_F_ACTION,
            DYNAMIC_FF_ACTION
        );

        TEMPO_CHANGE_ACTION = TempoChangeAction.createAction(mainFrame);
        BEAT_CHANGE_ACTION = BeatChangeAction.createAction(mainFrame);
        ANNOTATION_ACTION = AnnotationAction.createAction(mainFrame);
        KEY_SIGNATURE_CHANGE_ACTION = KeySignatureChangeAction.createAction(mainFrame);
        STAFF_ANNOTATION_ACTIONS = List.of(
            TEMPO_CHANGE_ACTION,
            BEAT_CHANGE_ACTION,
            ANNOTATION_ACTION,
            KEY_SIGNATURE_CHANGE_ACTION
        );

        FERMATA_ACTION = FermataAction.createAction(mainFrame);
        PREFERENCES_ACTION = new PreferencesOpenAction(mainFrame);
        SONG_SETTINGS_ACTION = new DialogOpenAction<>(
            mainFrame,
            Strings.get(Strings.ACTION_SONG_SETTINGS),
            KeyEvent.VK_G,
            MENU_SHORTCUT_MASK,
            SongSettingsDialog::new,
            Flag.DISABLE_WHEN_PLAYING
        );
        ABOUT_ACTION = new AboutOpenAction(mainFrame);
        PRINT_ACTION = PrintAction.createAction(mainFrame);
        QUIT_ACTION = QuitAction.createAction(mainFrame);

        UNDO_ACTION = UndoAction.createAction(mainFrame);
        REDO_ACTION = RedoAction.createAction(mainFrame);

        CUT_ACTION = CutAction.createAction(mainFrame);
        COPY_ACTION = CopyAction.createAction(mainFrame);
        PASTE_ACTION = PasteAction.createAction(mainFrame);
        DELETE_ACTION = DeleteAction.createAction(mainFrame);
        SELECT_ALL_ELEMENTS_ACTION = SelectAllElementsAction.createAction(mainFrame);
        DESELECT_ACTION = DeselectAction.createAction(mainFrame);

        //
        // Zoom actions
        //
        ZOOM_IN_ACTION = ZoomAction.createZoomInAction(mainFrame);
        ZOOM_OUT_ACTION = ZoomAction.createZoomOutAction(mainFrame);
        RESET_ZOOM_ACTION = ResetZoomAction.createAction(mainFrame);

        // Cmd+Shift+= is a secondary accelerator for zoom-in (alongside Cmd+=), matching
        // the common "=" / "+" key ambiguity across keyboard layouts.
        UIUtils.registerActionKeystroke(
            mainFrame.getRootPane(),
            KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK),
            ZOOM_IN_ACTION
        );

        // Invalidate the cached reflection list so it is rebuilt with the new instances.
        appMenuActions = null;
    }

    /**
     * Resets the injected {@link MainFrame} (and the derived app-menu cache) to {@code null}
     * so a test that reads a constant without first calling {@link #initialize} observes a
     * cleared owner rather than a prior test's mock. The action constants themselves are
     * {@code @NonNull} (NullAway), so they are not nulled here; every test re-populates them
     * via {@link #initialize} in setup.
     *
     * <p>Call from {@code @AfterEach} in test base classes that own the mock lifecycle.
     */
    public static void resetForTest() {
        mainFrame = null;
        appMenuActions = null;
    }

    private static void resetToDefaults() {
        // Non-silent resets — these need perform() to update downstream state
        // (ScoreView.mode via ModeDidChangeNotification, preview element via
        // UpdatePreviewElementCommand)
        MODE_ACTION_GROUP.select(EDIT_MODE_ACTION, EDIT_MODE_ACTION);
        DURATION_ACTION_GROUP.select(QUARTER_NOTE_ACTION, QUARTER_NOTE_ACTION);

        // Silent resets — no downstream state to update
        clearNoteDecorations();
        DYNAMIC_MARKING_ACTION_GROUP.reset();
        NON_DURATION_ACTION_GROUP.reset();

        // Standalone toggles
        REST_ACTION.reset();
        FERMATA_ACTION.reset();
        TRILL_ACTION.reset();

        // Re-decorate the preview from the now-cleared toggles. We cannot rely on the
        // DURATION_ACTION_GROUP.select() above to rebuild it: when the quarter note is
        // already the selected duration (the common case on document load) that select()
        // is a no-op and posts nothing, so the preview would otherwise keep a previous
        // document's decorations even though the toolbar shows them cleared.
        MessageCenter.post(new UpdatePreviewElementCommand());
    }

    /**
     * Clears every toggle that {@link songscribe.ui.edit.EditModeManager#decorateElement}
     * reads (dot, accidental, accidental-parentheses, and articulations), so this is the
     * single place that set has to stay in sync. Callers that need a freshly-inserted
     * element to start undecorated — e.g. a grace note's host — should call this instead
     * of clearing the toggles individually.
     */
    public static void clearNoteDecorations() {
        DOT_ACTION_GROUP.clearSelection();
        ACCIDENTAL_ACTION_GROUP.clearSelection();
        ACCIDENTAL_IN_PARENS_ACTION.setSelected(false);
        ARTICULATION_ACTION_GROUP.clearSelection();
        ACCENT_ACTION.setSelected(false);
    }

    private static @Nullable List<AppMenuAction> appMenuActions = null;

    /**
     * Returns all actions that implement {@link AppMenuAction}, discovered
     * via reflection over the {@code public static} fields of this class.
     * The result is cached after the first call and invalidated by {@link #initialize}.
     */
    public static List<AppMenuAction> getAppMenuActions() {
        if (appMenuActions != null) {
            return appMenuActions;
        }

        var result = new ArrayList<AppMenuAction>();
        var requiredModifiers = Modifier.PUBLIC | Modifier.STATIC;

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

    /**
     * Unsubscribes every action constant from the message bus.
     *
     * <p>Test-support only. Because {@link #initialize} reassigns the constants on every
     * call (once per test), the previous test's action objects would otherwise linger as
     * weakly-held zombie subscribers and fire their {@code @Handler} logic against a stale
     * mock the next time a message is posted — surfacing as a spurious
     * {@code RuntimeError} during an unrelated test. Call from {@code @AfterEach} in test
     * base classes that own the mock lifecycle, before the next test re-initializes.
     */
    public static void unsubscribeForTest() {
        // The reflection loop below only covers the public action constants, so the
        // private reset handler must be removed explicitly — otherwise it lingers as a
        // zombie whose resetToDefaults() throws once the constants reference torn-down
        // mocks, aborting delivery to every lower-priority subscriber of that post.
        MessageCenter.unsubscribe(RESET_HANDLER);

        var requiredModifiers = Modifier.PUBLIC | Modifier.STATIC;

        for (var field : Actions.class.getDeclaredFields()) {
            if ((field.getModifiers() & requiredModifiers) != requiredModifiers) {
                continue;
            }

            try {
                unsubscribeValue(field.get(null));
            } catch (IllegalAccessException e) {
                LOG.warn("Cannot access field '{}'", field.getName(), e);
            }
        }
    }

    private static void unsubscribeValue(@Nullable Object value) {
        if (value instanceof UIAction action) {
            MessageCenter.unsubscribe(action);
        } else if (value instanceof Iterable<?> items) {
            for (var item : items) {
                unsubscribeValue(item);
            }
        } else if (value instanceof Object[] array) {
            for (var item : array) {
                unsubscribeValue(item);
            }
        }
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
