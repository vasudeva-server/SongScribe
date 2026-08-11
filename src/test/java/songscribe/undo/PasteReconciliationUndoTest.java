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
import static songscribe.dom.StaffElementFactory.quaver;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.layout.NoteGeometry;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.MessageCenter;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Exercises paste's side of the complete-emission invariant ({@code docs/mutations.md}),
 * from the only place it is observable: the round-trip guarantee of {@code docs/undo.md}.
 *
 * <p>{@code PasteSpanReconciliation} decides which spans a paste discards, and
 * {@code tryInsertFragment} removes them through {@code Line}'s typed tracked removals
 * rather than a raw list mutation. Nothing about the paste's own result shows which of
 * those two it did — a raw removal produces the identical document — so the promise is
 * checked by undoing: an unrecorded removal restores the elements and leaves the discarded
 * beam or hairpin gone for good.
 *
 * <p><b>The classes of discarded span</b> — a beam the paste dropped outright, a hairpin the
 * paste widened, and two hairpins the paste merged into one. Each is a different shape of
 * loss, and each must come back exactly as it was.
 *
 * <p><b>Parentage after undo</b> — undoing a paste detaches the pasted clones and leaves the
 * elements that were already there attached, so the line's parentage matches the document
 * rather than merely its element count.
 *
 * <p>Lives in {@code songscribe.undo} because what it asserts is an undo guarantee, not
 * because of any access it needs — {@link UndoController#reset()} is public API since the
 * lifecycle contracts landed.
 */
class PasteReconciliationUndoTest extends UnitTest {

    // Pasting runs the fit gate, which measures column extents including accidental width,
    // and NoteGeometry exits the process rather than guessing when its width tables are
    // unpopulated. The tables are process-wide, so without this the class only passes when
    // some other test class in the same run happens to fill them first.
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

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
        UndoController.initialize();
        UndoController.reset();

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
    }

    /** Fills the line with three quavers, untracked, and returns them. */
    private List<StaffElement> threeQuavers() {
        var notes = List.of(
            quaver(),
            quaver(),
            quaver()
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
        Function<? super StaffElement, ? extends List<Span>> spansOver
    ) {
        var pastedNote = quaver();
        var clipboardManager = new ClipboardManager();
        clipboardManager.setFragment(
            new Fragment(
                List.of(pastedNote), Collections.singletonList(null), spansOver.apply(pastedNote)
            )
        );

        var scoreView = mock(ScoreView.class);
        when(scoreView.getSong()).thenReturn(song);

        var controller = new ScoreViewController(
            scoreView,
            mock(MusicEditOperations.class),
            mock(SelectionCoordinator.class),
            clipboardManager
        );

        // The controller subscribes itself to the shared message bus in its
        // constructor. MBassador holds subscribers weakly, but each test builds a
        // fresh controller, so leaving it subscribed would let it linger until GC
        // and handle notifications from later tests. Unsubscribe unconditionally.
        try {
            song.withModification(
                () -> controller.tryInsertFragment(line, INTERIOR_INSERT_INDEX, null));
        } finally {
            MessageCenter.unsubscribe(controller);
        }
    }

    @Test
    void testUndoRestoresTheBeamTheReconciliationDiscarded() {
        var notes = threeQuavers();
        var destinationBeam = new Beam(notes.getFirst(), notes.getLast());
        song.withoutMutationTracking(() -> line.addSpan(destinationBeam));

        pasteOneQuaverInside();

        assertThat(line.getSpans())
            .as("precondition: the straddled beam is discarded by the paste")
            .isEmpty();

        UndoController.undo();

        assertThat(line.getSpans())
            .as("one undo restores the discarded beam, not just the elements")
            .containsExactly(destinationBeam);
        assertThat(destinationBeam.getEndElementIndex())
            .as("restored over its original span, not shifted by the undone paste")
            .isEqualTo(LAST_NOTE_INDEX);

        UndoController.redo();

        assertThat(line.getSpans())
            .as("one redo discards the beam again, not just re-inserts the elements")
            .isEmpty();
    }

    @Test
    void testUndoRestoresTheHairpinSpanWidenedByThePaste() {
        // The hairpin is kept rather than discarded, but the paste still moves its end
        // element, so undo must put the span back where it was.
        var notes = threeQuavers();
        var destinationHairpin = new Crescendo(notes.getFirst(), notes.getLast());
        song.withoutMutationTracking(() -> line.addSpan(destinationHairpin));

        pasteOneQuaverInside();

        assertThat(destinationHairpin.getEndElementIndex())
            .as("precondition: the kept hairpin widens over the pasted note")
            .isEqualTo(LAST_NOTE_INDEX + 1);

        UndoController.undo();

        assertThat(line.getSpans()).containsExactly(destinationHairpin);
        assertThat(destinationHairpin.getEndElementIndex()).isEqualTo(LAST_NOTE_INDEX);

        UndoController.redo();

        assertThat(line.getSpans())
            .as("one redo restores the paste's widened hairpin, not just the elements")
            .containsExactly(destinationHairpin);
        assertThat(destinationHairpin.getEndElementIndex())
            .as("widened over the pasted note again")
            .isEqualTo(LAST_NOTE_INDEX + 1);
    }

    @Test
    void testUndoSeparatesTheHairpinsThePasteMerged() {
        // The pasted hairpin lands flush against the destination hairpin's anchor, so
        // Line.addCrescendo fuses the two into one — the same merge as drawing a
        // hairpin there by hand. Undo has to unpick it back into two.
        var notes = threeQuavers();
        var destinationHairpin =
            new Crescendo(notes.get(INTERIOR_INSERT_INDEX), notes.get(LAST_NOTE_INDEX));
        song.withoutMutationTracking(() -> line.addSpan(destinationHairpin));

        pasteOneQuaverInside(pastedNote -> List.of(new Crescendo(pastedNote, pastedNote)));

        assertThat(line.getSpans())
            .as("precondition: the abutting hairpins merge into exactly one")
            .hasSize(1);
        assertThat(line.getSpans().getFirst().getAnchorElementIndex())
            .as("the merged hairpin starts on the pasted note")
            .isEqualTo(INTERIOR_INSERT_INDEX);
        assertThat(line.getSpans().getFirst().getEndElementIndex())
            .as("and runs to where the destination hairpin ended")
            .isEqualTo(LAST_NOTE_INDEX + 1);

        UndoController.undo();

        assertThat(line.getSpans())
            .as("one undo restores the destination hairpin and discards the pasted one")
            .containsExactly(destinationHairpin);
        assertThat(destinationHairpin.getAnchorElementIndex()).isEqualTo(INTERIOR_INSERT_INDEX);
        assertThat(destinationHairpin.getEndElementIndex()).isEqualTo(LAST_NOTE_INDEX);

        UndoController.redo();

        var afterRedo = line.getSpans();

        assertThat(afterRedo)
            .as("one redo re-merges the hairpins the paste originally fused")
            .hasSize(1);
        assertThat(afterRedo.getFirst().getAnchorElementIndex())
            .as("the re-merged hairpin starts on the pasted note again")
            .isEqualTo(INTERIOR_INSERT_INDEX);
        assertThat(afterRedo.getFirst().getEndElementIndex())
            .as("and runs to where the destination hairpin ended, again")
            .isEqualTo(LAST_NOTE_INDEX + 1);
    }

    @Test
    void testUndoOfAPasteDetachesThePastedCloneAndLeavesSurvivorsAttached() {
        // Paste adds and removes elements and spans in one operation, so it is the
        // sharpest test of attach/detach ordering.
        var notes = threeQuavers();

        pasteOneQuaverInside();

        var pastedClone = line.getElement(INTERIOR_INSERT_INDEX);

        assertThat(pastedClone)
            .as("precondition: the paste inserts a clone, not one of the fixture notes")
            .isNotIn(notes);
        assertThat(pastedClone.getParentLine()).isSameAs(line);

        UndoController.undo();

        assertThat(pastedClone.getParentLine())
            .as("the undone clone must not keep pointing at the line it left")
            .isNull();
        assertThat(notes)
            .as("every surviving element still points at its real line")
            .allSatisfy(note -> assertThat(note.getParentLine()).isSameAs(line));
    }
}
