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
package songscribe.undo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.undo.OpNames;

/**
 * Exercises the declared-name half of {@link UndoController#undoLabel()}'s contract: an edit
 * whose initiator declared an op-name is called by that name, verbatim. The fallback half —
 * what an undeclared edit is called — belongs to {@link MutationLabelTest}.
 *
 * <p><b>Both ways a name is declared</b> — the Tier-B labeled bracket and a name assembled
 * by {@code OpNames} — reach the label unchanged. The two are the same clause with
 * different sources, and neither may be reworded on the way through.
 *
 * <p><b>The choice between declared and derived</b> — an edit that declares nothing gets the
 * type-based name, so the two halves of the contract meet here: which one applies is decided
 * by whether a name was declared, never by what the mutations happen to be.
 *
 * <p><b>Redo carries the same name</b> — after an undo, the step's name follows it onto the
 * redo stack rather than being recomputed, so Redo names the operation being re-applied.
 *
 * <p><b>The empty-stack boundary</b> — both labels collapse to the plain verb.
 *
 * <p>Each case drives a real edit on a real {@link Song} through the real message bus into
 * the singleton, which is the only way the declaration and the label meet the way they do in
 * production. The stack is reset before each test by posting a
 * {@link DocumentDidLoadNotification} — the path the app itself takes on file load, rather
 * than a test-only entry point.
 */
class UndoOpNameLabelTest extends UnitTest {

    // A song already carries Tempo's defaults, so a value-equal setTempo() call now
    // early-returns and records no mutation. Every test here needs a real tempo edit,
    // so it uses a tempo that actually differs from the default.
    private static final int NON_DEFAULT_BPM = Tempo.DEFAULT_BPM * 2;

    private Song song;
    private MockedStatic<MainFrame> mainFrameMock;

    @BeforeEach
    void setUp() {
        song = new Song();

        var mockScore = mock(ScoreView.class);
        when(mockScore.getSong()).thenReturn(song);
        var mockFrame = mock(MainFrame.class);
        when(mockFrame.getScoreView()).thenReturn(mockScore);

        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);

        // Force the singleton to load and subscribe, then reset its stacks to a known
        // empty baseline so a prior test's steps do not leak in.
        UndoController.initialize();
        UndoController.reset();
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
    }

    private String expectedUndo(String opName) {
        return Strings.get(Strings.ACTION_EDIT_UNDO_LABELED, opName);
    }

    private String expectedRedo(String opName) {
        return Strings.get(Strings.ACTION_EDIT_REDO_LABELED, opName);
    }

    @Test
    void testDeclaredTierBLabelIsUsedVerbatim() {
        var label = Strings.get(Strings.ACTION_EDIT_OP_SONG_SETTINGS);
        song.withModification(label, () -> song.setTempo(
            new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)));

        assertThat(UndoController.undoLabel()).isEqualTo(expectedUndo(label));
    }

    @Test
    void testDeclaredOpNamesLabelIsUsedVerbatim() {
        // OpNames.addLabel is the same helper the insertion sites use.
        var label = OpNames.addLabel(ElementType.CROTCHET);
        song.withModification(label, () -> song.setTempo(
            new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)));

        assertThat(UndoController.undoLabel()).isEqualTo(expectedUndo(label));
    }

    @Test
    void testUndeclaredEditFallsBackToTypeBasedLabel() {
        // No pending name, no labeled bracket — the fallback derives the label from the
        // dominant mutation (a TEMPO metadata change → "Change Tempo").
        song.setTempo(
            new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO));

        assertThat(UndoController.undoLabel())
            .isEqualTo(expectedUndo(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_TEMPO)));
    }

    @Test
    void testEmptyStackYieldsBareUndoAndRedo() {
        // Reset left both stacks empty; the label collapses to the plain verb.
        assertThat(UndoController.undoLabel()).isEqualTo(Strings.get(Strings.ACTION_EDIT_UNDO));
        assertThat(UndoController.redoLabel()).isEqualTo(Strings.get(Strings.ACTION_EDIT_REDO));
    }

    @Test
    void testRedoLabelCarriesDeclaredNameAfterUndo() {
        var label = OpNames.addLabel(ElementType.CROTCHET);
        song.withModification(label, () -> song.setTempo(
            new Tempo(NON_DEFAULT_BPM, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_DESCRIPTION, Tempo.DEFAULT_SHOW_TEMPO)));

        UndoController.undo();

        assertThat(UndoController.redoLabel()).isEqualTo(expectedRedo(label));
    }
}
