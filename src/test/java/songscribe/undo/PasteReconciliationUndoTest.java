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

package songscribe.undo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Undo round-trip for the paste span reconciliation (#614).
 *
 * <p>{@code PasteSpanReconciliation} decides which spans a paste discards, but
 * {@code tryInsertFragment} removes them through {@code Line}'s typed tracked
 * removals rather than a raw list mutation. That choice is only observable through
 * undo: a raw removal would drop the span with no {@code Mutation} record, so the
 * paste's single undo step would restore the elements and silently leave the
 * discarded beam or hairpin gone forever. These tests pin that down.
 *
 * <p>Lives in {@code songscribe.undo} rather than beside the other paste tests
 * because it needs {@link UndoController#resetForTest()}, which is package-private.
 */
class PasteReconciliationUndoTest extends UnitTest {

    private static final double WIDE_LINE_WIDTH_SS = 500;

    /** Index of the last note in the three-note fixture. */
    private static final int LAST_NOTE_INDEX = 2;

    /** An insertion index strictly inside the fixture's span. */
    private static final int INTERIOR_INSERT_INDEX = 1;

    private Song song;
    private Line line;
    private MockedStatic<MainFrame> mainFrameMock;

    @BeforeEach
    void setUp() {
        UndoController.resetForTest();

        song = new Song();
        song.withoutMutationTracking(() -> song.setLineWidthSs(WIDE_LINE_WIDTH_SS));
        line = song.getLine(0);

        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        var mainFrame = mock(MainFrame.class);
        when(mainFrame.getScoreView()).thenReturn(scoreView);
        mainFrameMock = mockStatic(MainFrame.class);
        mainFrameMock.when(MainFrame::getInstance).thenReturn(mainFrame);
    }

    @AfterEach
    void tearDown() {
        mainFrameMock.close();
        UndoController.resetForTest();
    }

    /** Fills the line with three quavers, untracked, and returns them. */
    private List<StaffElement> threeQuavers() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );

        song.withoutMutationTracking(() -> notes.forEach(line::addElement));

        return notes;
    }

    /** Pastes a single spanless quaver at {@code INTERIOR_INSERT_INDEX} as one undo step. */
    private void pasteOneQuaverInside() {
        pasteOneQuaverInside(quaver -> List.of());
    }

    /**
     * Pastes a single quaver at {@code INTERIOR_INSERT_INDEX} as one undo step,
     * carrying the spans {@code spansOver} builds over that one pasted note.
     */
    private void pasteOneQuaverInside(
        Function<StaffElement, List<RangeElement>> spansOver
    ) {
        var pastedNote = ElementType.QUAVER.newInstance();
        var clipboardManager = new ClipboardManager();
        clipboardManager.setFragment(new Fragment(List.of(pastedNote), spansOver.apply(pastedNote)));

        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        var controller = new ScoreViewController(
            scoreView,
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            clipboardManager
        );

        song.withModification(
            () -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));
    }

    @Test
    void testUndoRestoresTheBeamTheReconciliationDiscarded() {
        var notes = threeQuavers();
        var destinationBeam = new Beam(notes.getFirst(), notes.getLast());
        song.withoutMutationTracking(() -> line.addRangeElement(destinationBeam));

        pasteOneQuaverInside();

        assertThat(line.getRangeElements())
            .as("precondition: the straddled beam is discarded by the paste")
            .isEmpty();

        UndoController.undo();

        assertThat(line.getRangeElements())
            .as("one undo restores the discarded beam, not just the elements")
            .containsExactly(destinationBeam);
        assertThat(destinationBeam.getEndElementIndex())
            .as("restored over its original span, not shifted by the undone paste")
            .isEqualTo(LAST_NOTE_INDEX);
    }

    @Test
    void testUndoRestoresTheHairpinSpanWidenedByThePaste() {
        // The hairpin is kept rather than discarded, but the paste still moves its end
        // element, so undo must put the span back where it was.
        var notes = threeQuavers();
        var destinationHairpin = new Crescendo(notes.getFirst(), notes.getLast());
        song.withoutMutationTracking(() -> line.addRangeElement(destinationHairpin));

        pasteOneQuaverInside();

        assertThat(destinationHairpin.getEndElementIndex())
            .as("precondition: the kept hairpin widens over the pasted note")
            .isEqualTo(LAST_NOTE_INDEX + 1);

        UndoController.undo();

        assertThat(line.getRangeElements()).containsExactly(destinationHairpin);
        assertThat(destinationHairpin.getEndElementIndex()).isEqualTo(LAST_NOTE_INDEX);
    }

    @Test
    void testUndoSeparatesTheHairpinsThePasteMerged() {
        // The pasted hairpin lands flush against the destination hairpin's anchor, so
        // Line.addCrescendo fuses the two into one — the same merge as drawing a
        // hairpin there by hand. Undo has to unpick it back into two.
        var notes = threeQuavers();
        var destinationHairpin =
            new Crescendo(notes.get(INTERIOR_INSERT_INDEX), notes.get(LAST_NOTE_INDEX));
        song.withoutMutationTracking(() -> line.addRangeElement(destinationHairpin));

        pasteOneQuaverInside(pastedNote -> List.of(new Crescendo(pastedNote, pastedNote)));

        assertThat(line.getRangeElements())
            .as("precondition: the abutting hairpins merge into exactly one")
            .hasSize(1);
        assertThat(line.getRangeElements().getFirst().getAnchorElementIndex())
            .as("the merged hairpin starts on the pasted note")
            .isEqualTo(INTERIOR_INSERT_INDEX);
        assertThat(line.getRangeElements().getFirst().getEndElementIndex())
            .as("and runs to where the destination hairpin ended")
            .isEqualTo(LAST_NOTE_INDEX + 1);

        UndoController.undo();

        assertThat(line.getRangeElements())
            .as("one undo restores the destination hairpin and discards the pasted one")
            .containsExactly(destinationHairpin);
        assertThat(destinationHairpin.getAnchorElementIndex()).isEqualTo(INTERIOR_INSERT_INDEX);
        assertThat(destinationHairpin.getEndElementIndex()).isEqualTo(LAST_NOTE_INDEX);
    }
}
