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

package songscribe.layout;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What a reconciliation does about the elements an edit takes off a line, and how far the deletion
 * of a whole line reaches.
 *
 * <p>A mid-line key signature and the barline it stands behind are both accidental barriers, so
 * every note after the pair resolved against a context the pair began. Removing the pair — which
 * is what an edit does to a key signature its own key move strands — hands those notes back to
 * whatever stands earlier on the line, and that moves sounding pitches. Each fixture here is that
 * one situation: an explicit accidental before the pair and a bare note at the same staff position
 * after it, so a projection that fails to leave the pair out reports no change where a pitch is
 * about to move.
 *
 * <p>The barrier fixtures re-key a line from {@link #KEY_BEFORE} to {@link #PLAIN_KEY}, which is
 * what a reach always does — a line only ever carries ranges to remove when its key actually
 * moved, since a key change restating the key a line already runs in cannot exist in a document
 * this program will hand out. Neither key alters the pitch class at
 * {@link #SHARED_STAFF_POSITION}, so every assertion below turns on the barrier rather than on
 * which notes a key happens to touch.
 */
class AccidentalReconciliationTest extends UnitTest {

    /** The key a barrier fixture is re-keyed <em>to</em>, which alters nothing on its own. */
    private static final Key PLAIN_KEY = Key.NO_ACCIDENTALS;

    /**
     * The key a barrier fixture runs in before the edit. It has to differ from {@link #PLAIN_KEY},
     * so that the mid-line key change is a real change until the re-key strands it, and it has to
     * leave {@link #SHARED_STAFF_POSITION} alone, so that what the assertions observe is the
     * barrier rather than the key. Its one sharp is an F, and that position is a C.
     */
    private static final Key KEY_BEFORE = Key.ONE_SHARP;

    /** A key that is not {@link #PLAIN_KEY}, for the line whose deletion re-keys the ones after. */
    private static final Key OTHER_KEY = Key.TWO_SHARPS;

    /** The staff position every note in the barrier fixtures sits at, which is a C. */
    private static final int SHARED_STAFF_POSITION = 6;

    private static final int BARLINE_INDEX = 1;
    private static final int SIGNATURE_INDEX = 2;

    /** The second pair's indices, on the fixture that holds two of them. */
    private static final int SECOND_BARLINE_INDEX = 4;
    private static final int SECOND_SIGNATURE_INDEX = 5;

    /**
     * A line in {@link #KEY_BEFORE} holding {@code note, barline, key change, note}, where the
     * key change establishes {@link #PLAIN_KEY} and both notes sit at
     * {@link #SHARED_STAFF_POSITION}.
     *
     * <p>As it stands the key change is a real change, which is the only way such a line can
     * exist. Re-keying the line to {@link #PLAIN_KEY} is what strands it.
     *
     * @param leadingAccidental the accidental written on the note in front of the pair
     * @return the line
     */
    private static Line barrierLine(StaffElement.Accidental leadingAccidental) {
        var line = detachedLine();

        line.setKey(KEY_BEFORE);
        line.addElement(note(SHARED_STAFF_POSITION, leadingAccidental));
        line.addElement(singleBarline());
        line.addElement(keyChange(PLAIN_KEY));
        line.addElement(note(SHARED_STAFF_POSITION));

        return line;
    }

    /**
     * A three-line song: line 0 in {@link #PLAIN_KEY}, line 1 in {@link #OTHER_KEY} of its own,
     * and line 2 inheriting from line 1 while carrying a mid-line signature restating
     * {@link #PLAIN_KEY}.
     *
     * <p>That last signature is meaningful while line 1 stands and stranded the moment it goes,
     * which is the whole of what a line deletion's reach has to notice.
     *
     * @return the song
     */
    private static Song threeLineSong() {
        return SongFactory.buildSong(
            line -> {
                line.setKey(PLAIN_KEY);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(OTHER_KEY);
                line.addElement(crotchet());
            },
            line -> {
                line.addElement(crotchet());
                line.addElement(singleBarline());
                line.addElement(keyChange(PLAIN_KEY));
            });
    }

    @Test
    void testAReKeyedLineCarriesTheRangesItsNewKeyStrandsAndAnUntouchedOneCarriesNone() {
        var line = barrierLine(StaffElement.Accidental.SHARP);

        assertThat(AccidentalReconciliation.ReachedLine.reKeyed(line, PLAIN_KEY).removedRanges())
            .as("the key change restates the key the line will run in, so it goes with its barline")
            .containsExactly(new StaffElementRun.EffectiveRange(BARLINE_INDEX, SIGNATURE_INDEX));

        assertThat(AccidentalReconciliation.ReachedLine.of(line, List.of()).removedRanges())
            .as("an ordinary in-place modification removes nothing")
            .isEmpty();
    }

    @Test
    void testARemovedRangeIsLeftOutOfTheProjectionSoTheNotesAfterItKeepTheirPitch() {
        var line = barrierLine(StaffElement.Accidental.SHARP);
        var trailingNote = line.getElement(SIGNATURE_INDEX + 1);

        var reconciled = AccidentalReconciliation.reconcileReach(
            List.of(AccidentalReconciliation.ReachedLine.reKeyed(line, PLAIN_KEY)),
            AccidentalReconciliation.RestatementRemoval.NONE);

        assertThat(reconciled).hasSize(1);
        assertThat(reconciled.getFirst().changes())
            .as("with the pair gone the sharp in front of it reaches the bare note, which must be "
                + "made natural to go on sounding what it sounded")
            .containsExactly(new AccidentalReconciliation.AccidentalChange(
                trailingNote, StaffElement.Accidental.NATURAL));
    }

    @Test
    void testALineWithNothingChangedNothingRemovedAndAnUnmovedKeyReconcilesNothing() {
        var line = barrierLine(StaffElement.Accidental.SHARP);

        var reconciled = AccidentalReconciliation.reconcileReach(
            List.of(AccidentalReconciliation.ReachedLine.of(line, List.of())),
            AccidentalReconciliation.RestatementRemoval.NONE);

        assertThat(reconciled.getFirst().changes()).isEmpty();
    }

    @Test
    void testAProjectionLeavesAStrandedRangeOutOfItsTrailingRun() {
        var line = detachedLine();

        // Both key changes are real ones as the line stands: it runs in KEY_BEFORE, steps to
        // PLAIN_KEY at the first, and steps back at the second. Re-pointing the first at
        // KEY_BEFORE is what removes it and strands the second.
        line.setKey(KEY_BEFORE);
        line.addElement(note(SHARED_STAFF_POSITION, StaffElement.Accidental.SHARP));
        line.addElement(singleBarline());
        line.addElement(keyChange(PLAIN_KEY));
        line.addElement(note(SHARED_STAFF_POSITION, StaffElement.Accidental.SHARP));
        line.addElement(singleBarline());
        line.addElement(keyChange(KEY_BEFORE));
        line.addElement(note(SHARED_STAFF_POSITION));

        var middleNote = line.getElement(SIGNATURE_INDEX + 1);
        var trailingNote = line.getElement(SECOND_SIGNATURE_INDEX + 1);

        // The mid-line key edit that removes the key change it was pointed at, described as the
        // removal it is: the pair goes and nothing takes its place, while the pair further along
        // the line goes with it as a stranded range.
        var reached = AccidentalReconciliation.ReachedLine.receiving(
            line,
            new AccidentalReconciliation.Insertion(
                BARLINE_INDEX,
                new InsertionSpacingCalculator.DeletedRange(BARLINE_INDEX, SIGNATURE_INDEX),
                AccidentalReconciliation.ArrivingElements.NONE),
            List.of(new StaffElementRun.EffectiveRange(
                SECOND_BARLINE_INDEX, SECOND_SIGNATURE_INDEX)));

        assertThat(AccidentalReconciliation.reconcile(
            reached, AccidentalReconciliation.RestatementRemoval.NONE))
            .as("neither pair is a barrier any longer: the first note's sharp reaches the second, "
                + "whose own sharp is then redundant, and reaches the bare note past the stranded "
                + "pair, which must be made natural to go on sounding what it sounded")
            .containsExactly(
                new AccidentalReconciliation.AccidentalChange(middleNote, null),
                new AccidentalReconciliation.AccidentalChange(
                    trailingNote, StaffElement.Accidental.NATURAL));
    }

    @Test
    void testALineDeletionReachesTheLinesThatThenInheritFromTheLineBeforeIt() {
        var song = threeLineSong();
        var deletedLine = song.getLine(1);
        var inheritingLine = song.getLine(2);

        var reach = AccidentalReconciliation.lineDeletionReach(deletedLine);

        assertThat(reach).hasSize(1);
        assertThat(reach.getFirst().line()).isSameAs(inheritingLine);
        assertThat(reach.getFirst().runningKey())
            .as("what the line after the deleted one inherits is the key the line before it "
                + "leaves off in")
            .isEqualTo(PLAIN_KEY);
        assertThat(reach.getFirst().removedRanges())
            .as("under that key the mid-line key change restates what is already in effect")
            .containsExactly(new StaffElementRun.EffectiveRange(BARLINE_INDEX, SIGNATURE_INDEX));
    }

    @Test
    void testDeletingLineZeroReachesNothing() {
        var song = threeLineSong();

        assertThat(AccidentalReconciliation.lineDeletionReach(song.getLine(0)))
            .as("the line that follows becomes line 0 and materializes the key it was already "
                + "inheriting, so nothing downstream of it moves")
            .isEmpty();
    }
}
