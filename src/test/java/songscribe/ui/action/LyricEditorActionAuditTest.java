/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.action;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.prefs.Prefs;
import songscribe.ui.playback.LoopPlaybackAction;
import songscribe.ui.playback.PlayPauseAction;
import songscribe.ui.playback.PlayWithRepeatsAction;
import songscribe.ui.playback.RewindAction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * T25 — Audit test locking the DISABLE_WHEN_EDITING_TEXT invariant for all toolbar actions.
 * <p>
 * Every action in a toolbar must carry Flag.DISABLE_WHEN_EDITING_TEXT so that single-character
 * accelerators cannot fire while the lyric editor is focused. See LyricEditor for the invariant.
 * <p>
 * To add a new toolbar action: instantiate it in testAllToolbarActionsCarryDisableWhenEditingTextFlag
 * and add Flag.DISABLE_WHEN_EDITING_TEXT to the action's flags.
 * To remove an action from enforcement: remove it from the list below and leave a comment explaining why.
 */
class LyricEditorActionAuditTest extends MainFrameMockTest {

    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUp() {
        prefsMock = mockStatic(Prefs.class);
    }

    @AfterEach
    void tearDown() {
        prefsMock.close();
    }

    // T25

    @Test
    void testAllToolbarActionsCarryDisableWhenEditingTextFlag() {
        var mainFrame = mainFrame();
        var toolbarActions = List.of(
            // DurationToolbar
            ElementTypeAction.createGraceEighthNoteAction(mainFrame),
            ElementTypeAction.createThirtySecondNoteAction(mainFrame),
            ElementTypeAction.createSixteenthNoteAction(mainFrame),
            ElementTypeAction.createEighthNoteAction(mainFrame),
            ElementTypeAction.createQuarterNoteAction(mainFrame),
            ElementTypeAction.createHalfNoteAction(mainFrame),
            ElementTypeAction.createWholeNoteAction(mainFrame),
            ElementTypeAction.createGlissandoAction(mainFrame),
            ElementTypeAction.createFallAction(mainFrame),
            // DotRestToolbar
            DotAction.createDotAction(mainFrame),
            DotAction.createDoubleDotAction(mainFrame),
            RestModeAction.createAction(mainFrame),
            // AccidentalToolbar
            AccidentalAction.createDoubleFlatAction(mainFrame),
            AccidentalAction.createFlatAction(mainFrame),
            AccidentalAction.createNaturalFlatAction(mainFrame),
            AccidentalAction.createNaturalAction(mainFrame),
            AccidentalAction.createSharpAction(mainFrame),
            AccidentalAction.createDoubleSharpAction(mainFrame),
            AccidentalAction.createNaturalSharpAction(mainFrame),
            // ArticulationToolbar
            ForceArticulationAction.createAccentAction(mainFrame),
            DurationArticulationAction.createStaccatoAction(mainFrame),
            // BarToolbar
            ElementTypeAction.createLeftRepeatAction(mainFrame),
            ElementTypeAction.createRightRepeatAction(mainFrame),
            ElementTypeAction.createLeftRightRepeatAction(mainFrame),
            ElementTypeAction.createDoubleBarlineAction(mainFrame),
            ElementTypeAction.createSingleBarlineAction(mainFrame),
            // ModifyNoteToolbar
            ToggleNotationAction.createBeamAction(mainFrame),
            ToggleNotationAction.createTieAction(mainFrame),
            TupletAction.createTripletAction(mainFrame),
            StemDirectionAction.createFlipAction(mainFrame),
            EditLyricAction.createAction(mainFrame),
            // StaffAnnotationPopupButton
            AnnotationAction.createAction(mainFrame),
            BeatChangeAction.createAction(mainFrame),
            KeySignatureChangeAction.createAction(mainFrame),
            TempoChangeAction.createAction(mainFrame),
            // PlaybackToolbar
            LoopPlaybackAction.createAction(mainFrame),
            PlayPauseAction.createAction(mainFrame),
            PlayWithRepeatsAction.createAction(mainFrame),
            RewindAction.createAction(mainFrame),
            // Mode toolbar
            CycleModeAction.createAction(mainFrame)
        );

        for (var action : toolbarActions) {
            assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT))
                .as(
                    "Add Flag.DISABLE_WHEN_EDITING_TEXT to %s, or remove it from this whitelist with justification",
                    action.getClass().getSimpleName()
                )
                .isTrue();
        }
    }
}
