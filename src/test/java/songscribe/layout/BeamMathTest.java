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

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.semiquaver;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;

/**
 * Tests for {@link BeamMath#frenchBeamShortening} — the per-stem French-beaming
 * rule that decides how many beams an inner stem stops short of the outer beam
 * (issue #652).
 */
class BeamMathTest extends UnitTest {

    // Index of the note whose shortening each test inspects. The groups below are
    // built so this position is interior, which is the only place the rule fires.
    private static final int INNER_INDEX = 1;

    // Enough leading elements that a rule ignoring beamStart would land on the wrong
    // notes rather than merely off by one.
    private static final int LEADING_NOTE_COUNT = 2;

    /** Runs the rule over a whole group, so each member's result can be asserted at once. */
    private static int[] shorteningsFor(ElementType... types) {
        return shorteningsAfterLeadingNotes(0, types);
    }

    /**
     * As {@link #shorteningsFor}, but with {@code leadingNotes} unbeamed sixteenths placed
     * ahead of the group, so the beam starts partway into the line rather than at index 0
     * and the rule has to honour {@code beamStart} instead of assuming it.
     *
     * <p>They are sixteenths, not rests, on purpose: a rest belongs to no beam level, so a
     * rule that read past {@code beamStart} into one would still come back with 0 and the
     * fixture would prove nothing.
     */
    private static int[] shorteningsAfterLeadingNotes(int leadingNotes, ElementType... types) {
        var line = detachedLine();

        for (var i = 0; i < leadingNotes; i++) {
            line.addElement(semiquaver());
        }

        for (var type : types) {
            line.addElement(type.newInstance());
        }

        var beamStart = leadingNotes;
        var beamEnd = leadingNotes + types.length - 1;
        line.addBeaming(new Beam(line.getElement(beamStart), line.getElement(beamEnd)));

        var shortenings = new int[types.length];

        for (var i = 0; i < types.length; i++) {
            shortenings[i] = BeamMath.frenchBeamShortening(line, beamStart + i, beamStart, beamEnd);
        }

        return shortenings;
    }

    // A one-beam group has no inner beam to stop at, so French beaming is a no-op —
    // this is the guard against shortening every beamed stem in the score.
    @Test
    void testQuaverGroupIsNeverShortened() {
        var shortenings = shorteningsFor(
            ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER, ElementType.QUAVER);

        assertThat(shortenings)
            .describedAs("no stem of an eighth-note group is shortened")
            .containsExactly(0, 0, 0, 0);
    }

    // The headline case: the inner stems of a sixteenth-note group stop at the
    // secondary beam while the outer two still run out to the primary beam.
    @Test
    void testSemiquaverGroupShortensOnlyItsInnerStems() {
        var shortenings = shorteningsFor(
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("inner sixteenth stems stop one beam short, outer stems do not")
            .containsExactly(0, 1, 1, 0);
    }

    // Every additional beam level moves the inner stem ends one translation closer
    // to the noteheads.
    @Test
    void testDemiSemiquaverGroupShortensInnerStemsByTwoBeams() {
        var shortenings = shorteningsFor(
            ElementType.DEMI_SEMIQUAVER, ElementType.DEMI_SEMIQUAVER,
            ElementType.DEMI_SEMIQUAVER, ElementType.DEMI_SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("inner thirty-second stems stop two beams short")
            .containsExactly(0, 2, 2, 0);
    }

    // Two inner stems of the same group shortened by different amounts. Without this
    // every group is uniform, so a rule that computed one shortening for the whole group
    // and handed it to every inner stem would pass all of the tests above.
    @Test
    void testInnerStemsOfAMixedGroupAreShortenedIndependently() {
        var shortenings = shorteningsFor(
            ElementType.SEMIQUAVER,
            ElementType.DEMI_SEMIQUAVER, ElementType.DEMI_SEMIQUAVER, ElementType.DEMI_SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("the sixteenth caps its neighbour at one beam while the next stem loses two")
            .containsExactly(0, 1, 2, 0);
    }

    // Every other fixture beams from index 0, which would let the rule ignore beamStart
    // entirely and still agree. Offsetting the group behind notes that share its beam
    // levels proves the group's first stem is judged an edge by beamStart, not by index 0.
    @Test
    void testShorteningIsUnchangedWhenTheGroupDoesNotStartTheLine() {
        var shortenings = shorteningsAfterLeadingNotes(
            LEADING_NOTE_COUNT,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("a group offset from the start of the line shortens exactly as one at index 0")
            .containsExactly(0, 1, 1, 0);
    }

    // A stem where the secondary beam terminates is the last stem of that sub-beam,
    // so it must carry the beam out to the primary — the same role the group's outer
    // stems play. Shortening it would leave the sub-beam hanging in mid-air.
    @Test
    void testStemWhereSubBeamEndsIsNotShortened() {
        var shortenings = shorteningsFor(
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
            ElementType.QUAVER,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("the stems bounding the two sixteenth runs reach the primary beam")
            .containsExactly(0, 0, 0, 0, 0);
    }

    // A stem carries a stub at a level neither neighbour shares, so the stub is not
    // what the stem has to reach: it stops at the innermost beam that passes through
    // it, and the stub — nearer the notehead — is covered on the way.
    @Test
    void testStemWithAStubStopsAtItsInnermostThroughBeam() {
        var shortenings = shorteningsFor(
            ElementType.SEMIQUAVER, ElementType.DEMI_SEMIQUAVER, ElementType.SEMIQUAVER);

        assertThat(shortenings[INNER_INDEX])
            .describedAs("the thirty-second's stub does not stop its stem short of the sixteenth beam")
            .isEqualTo(1);
    }

    // A grace note can be inserted between two beamed notes without joining their beams:
    // the sixteenth beam runs straight past it, so the stems on either side still stop on
    // it. Reading the grace note as an ordinary one-beam neighbour would push them back
    // out to the primary beam.
    //
    // The grace note's own entry is 0 because its stem carries no beam to stop on. Nothing
    // reads it either way — NoteRenderer.isNoteBeamed keeps grace notes off the beamed
    // path entirely — so this pins the honest value rather than a value that matters.
    @Test
    void testBeamRunsPastAnInsertedGraceNote() {
        var shortenings = shorteningsFor(
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
            ElementType.GRACE_QUAVER,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("the sixteenths flanking a grace note stop on the beam that crosses it")
            .containsExactly(0, 1, 0, 1, 0);
    }

    // A rest is the opposite case: it carries no beam, so it breaks the run rather than
    // being stepped over, and the stems beside it must reach the primary beam. This is
    // also the only path that drives the shared-beam count to zero.
    @Test
    void testRestInsideAGroupStopsTheBeamRatherThanBeingSteppedOver() {
        var shortenings = shorteningsFor(
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
            ElementType.SEMIQUAVER_REST,
            ElementType.SEMIQUAVER, ElementType.SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("no stem beside a rest is shortened, and the rest itself is not either")
            .containsExactly(0, 0, 0, 0, 0);
    }

    // The rule is asked about every group member, including the edges; the guard
    // against an out-of-range neighbour lookup is what keeps that safe.
    @Test
    void testTwoNoteGroupHasNoInnerStems() {
        var shortenings = shorteningsFor(ElementType.SEMIQUAVER, ElementType.SEMIQUAVER);

        assertThat(shortenings)
            .describedAs("a two-note group is all edges")
            .containsExactly(0, 0);
    }
}
