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

import songscribe.UnitTest;
import songscribe.prefs.Prefs;
import songscribe.ui.component.MainFrame;
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
class LyricEditorActionAuditTest extends UnitTest {

    private MockedStatic<MainFrame> mainFrameMock;
    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUp() {
        mainFrameMock = mockStatic(MainFrame.class);
        prefsMock = mockStatic(Prefs.class);
        MockEnvHelper.setupMockEnv(mainFrameMock);
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
        prefsMock.close();
    }

    // T25

    @Test
    void testAllToolbarActionsCarryDisableWhenEditingTextFlag() {
        var toolbarActions = List.of(
            // DurationToolbar
            ElementTypeAction.createGraceEighthNoteAction(),
            ElementTypeAction.createThirtySecondNoteAction(),
            ElementTypeAction.createSixteenthNoteAction(),
            ElementTypeAction.createEighthNoteAction(),
            ElementTypeAction.createQuarterNoteAction(),
            ElementTypeAction.createHalfNoteAction(),
            ElementTypeAction.createWholeNoteAction(),
            ElementTypeAction.createGlissandoAction(),
            ElementTypeAction.createSlideOutAction(),
            // DotRestToolbar
            DotAction.createDotAction(),
            DotAction.createDoubleDotAction(),
            RestModeAction.createAction(),
            // AccidentalToolbar
            AccidentalAction.createDoubleFlatAction(),
            AccidentalAction.createFlatAction(),
            AccidentalAction.createNaturalFlatAction(),
            AccidentalAction.createNaturalAction(),
            AccidentalAction.createSharpAction(),
            AccidentalAction.createDoubleSharpAction(),
            AccidentalAction.createNaturalSharpAction(),
            // ArticulationToolbar
            ForceArticulationAction.createAccentAction(),
            DurationArticulationAction.createStaccatoAction(),
            // BarToolbar
            ElementTypeAction.createLeftRepeatAction(),
            ElementTypeAction.createRightRepeatAction(),
            ElementTypeAction.createLeftRightRepeatAction(),
            ElementTypeAction.createDoubleBarlineAction(),
            ElementTypeAction.createSingleBarlineAction(),
            // ModifyNoteToolbar
            ToggleNotationAction.createBeamAction(),
            ToggleNotationAction.createTieAction(),
            TupletAction.createTripletAction(),
            FlipStemDirectionAction.createAction(),
            EditLyricAction.createAction(),
            // StaffAnnotationPopupButton
            AnnotationAction.createAction(),
            BeatChangeAction.createAction(),
            KeySignatureChangeAction.createAction(),
            TempoChangeAction.createAction(),
            // PlaybackToolbar
            LoopPlaybackAction.createAction(),
            PlayPauseAction.createAction(),
            PlayWithRepeatsAction.createAction(),
            RewindAction.createAction(),
            // Mode toolbar
            CycleModeAction.createAction()
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
