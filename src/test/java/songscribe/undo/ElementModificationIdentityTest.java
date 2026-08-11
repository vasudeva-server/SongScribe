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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementField;

/**
 * Exercises the element-identity guarantee of {@code docs/undo.md}: replaying an
 * {@link songscribe.message.mutation.ElementModification} restores state <em>in place</em>
 * and never swaps the instance, so everything holding a reference to that element stays
 * valid.
 *
 * <p><b>The promise itself</b> — after an undo and again after a redo, the element at the
 * modified index is the same object it was before, asserted by identity rather than by
 * equality. Equality would pass under exactly the implementation the promise forbids.
 *
 * <p><b>The promise as a consequence</b> — a beam anchored to the modified note still
 * points at the live note after an interleaved sequence of undos and redos. This is why
 * identity is promised at all, and it is the case that would fail if replay reverted to
 * {@code setElement} while the identity assertion above were somehow satisfied.
 *
 * <p>Two tests, and the second is not a duplicate of the first: one states the mechanism,
 * the other states what the mechanism is for.
 */
class ElementModificationIdentityTest extends UnitTest {

    private static boolean hasFermata(StaffElement element) {
        return element.findAttachment(FermataAttachment.class) != null;
    }

    private static Song songWithNotes(int count) {
        var song = new Song();
        UndoTestSupport.addCrotchets(song, song.getLine(0), count);
        return song;
    }

    @Test
    void testModificationReplayPreservesElementInstance() {
        var song = songWithNotes(3);
        var line = song.getLine(0);
        var element = line.getElement(1);

        var batch = UndoTestSupport.captureBatch(song, () ->
            line.modifyElement(1, ElementField.FERMATA, () -> line.getElement(1).setFermata(true)));

        // The forward edit mutates the element in place — same instance, new state.
        assertThat(line.getElement(1)).isSameAs(element);
        assertThat(hasFermata(element)).isTrue();

        var scoreView = UndoTestSupport.scoreViewFor(song);

        UndoTestSupport.replayUndo(scoreView, batch);
        assertThat(line.getElement(1))
            .as("undo must restore state onto the same instance, not swap it")
            .isSameAs(element);
        assertThat(hasFermata(element)).isFalse();

        UndoTestSupport.replayRedo(scoreView, batch);
        assertThat(line.getElement(1))
            .as("redo must re-apply state onto the same instance, not swap it")
            .isSameAs(element);
        assertThat(hasFermata(element)).isTrue();
    }

    @Test
    void testBeamAnchoredToModifiedNoteSurvivesInterleavedUndoRedo() {
        var song = songWithNotes(2);
        var line = song.getLine(0);
        var element0 = line.getElement(0);
        var element1 = line.getElement(1);
        var beam = new Beam(element0, element1);
        song.withoutMutationTracking(() -> line.addBeaming(beam));

        // Forward: remove beam, then modify the note the beam was anchored to.
        var removeBeamBatch = UndoTestSupport.captureBatch(song, () -> line.removeBeaming(beam));
        var modifyBatch = UndoTestSupport.captureBatch(song, () ->
            line.modifyElement(0, ElementField.FERMATA, () -> line.getElement(0).setFermata(true)));

        var scoreView = UndoTestSupport.scoreViewFor(song);

        // Undo modify, then undo the beam removal — the beam is re-anchored to element0.
        UndoTestSupport.replayUndo(scoreView, modifyBatch);
        UndoTestSupport.replayUndo(scoreView, removeBeamBatch);

        // Identity preserved, so the re-added beam's anchor is the live element and the
        // beam serializes with valid indices. A swapped identity would leave the beam
        // anchored to a stale, out-of-line instance.
        assertThat(line.getElement(0)).isSameAs(element0);
        assertThat(line.getElement(1)).isSameAs(element1);
        assertThat(hasFermata(element0)).isFalse();
        assertThat(UndoTestSupport.serialize(song))
            .as("the beam must be restored, anchored to the live modified note")
            .contains("<beamings>0,1;</beamings>");

        // Redo both — beam removed again, note re-modified.
        UndoTestSupport.replayRedo(scoreView, removeBeamBatch);
        UndoTestSupport.replayRedo(scoreView, modifyBatch);

        assertThat(line.getElement(0)).isSameAs(element0);
        assertThat(hasFermata(element0)).isTrue();
        assertThat(UndoTestSupport.serialize(song))
            .as("redo of the beam removal drops the beam again")
            .doesNotContain("<beamings>");
    }
}
