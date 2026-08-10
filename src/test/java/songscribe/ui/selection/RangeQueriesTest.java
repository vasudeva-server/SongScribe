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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.BeatAt;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.ui.action.TupletAction;

/**
 * Unit tests for {@link RangeQueries} and for the accessors on {@link Selection.Range}.
 * <p>
 * Everything here is a pure function of {@code (line, begin, end)}, so every fixture builds a
 * range directly rather than going through the coordinator. The queries that need a selection
 * to be <em>made</em> — and the "nothing is selected" answers, which a range can no longer
 * express — live in {@link SelectionCoordinatorRangeTest}.
 */
class RangeQueriesTest extends UnitTest {

    /** Five notes — the span a quintuplet covers. */
    private static final int QUINTUPLET_NOTE_COUNT = 5;

    /**
     * Stubs the mock song behind a detached fixture so tuplet validation can resolve a beat.
     * An unstubbed mock resolves to no beat at all, which no real song ever does.
     */
    private static Line withQuarterBeat(Line line) {
        when(line.getSong().resolveBeatAt(anyInt(), anyInt()))
            .thenReturn(new BeatAt(Duration.CROTCHET, 0, 0));
        return line;
    }

    /**
     * Builds a detached line from {@code types} and returns a range covering all of it.
     */
    private static ElementSelection rangeOverAllOf(ElementType... types) {
        var line = withQuarterBeat(detachedLine());

        for (var type : types) {
            line.addElement(type.newInstance());
        }

        return new ElementSelection(line, 0, types.length - 1);
    }

    /** A range over {@code begin..end} of {@code line}, as the queries take it. */
    private static ElementSelection rangeOver(Line line, int begin, int end) {
        return new ElementSelection(line, begin, end);
    }

    /** The same range as a {@link Selection.Range}, anchored at {@code begin}. */
    private static Selection.Range anchoredRange(Line line, int begin, int end) {
        return new Selection.Range(line, begin, end, begin);
    }

    // -- Range construction --

    /**
     * The empty range is not a state to represent: nothing selected is a null selection, so a
     * range that selects nothing can only be a caller bug. Rejecting it at construction is what
     * lets every query below skip an emptiness guard.
     */
    @Test
    void testRangeRejectsAnEmptyOrReversedSpan() {
        var line = detachedLine();
        line.addElement(crotchet());

        assertThatThrownBy(() -> new Selection.Range(line, -1, -1, -1))
            .as("an empty range")
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Selection.Range(line, 2, 1, 2))
            .as("a reversed range")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSingleBuildsAOneElementRangeAnchoredOnIt() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());

        var range = Selection.Range.single(line, 1);

        assertThat(range.begin()).isEqualTo(1);
        assertThat(range.end()).isEqualTo(1);
        assertThat(range.anchor()).isEqualTo(1);
        assertThat(range.size()).isEqualTo(1);
    }

    // -- Range.size --

    @Test
    void testSizeCountsBothEndpoints() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());

        assertThat(anchoredRange(line, 0, 2).size()).isEqualTo(3);
    }

    // -- Range.toElementSelection --

    @Test
    void testToElementSelectionCarriesTheLineAndBothEndpoints() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());

        assertThat(anchoredRange(line, 1, 2).toElementSelection())
            .satisfies(selection -> {
                assertThat(selection.begin()).isEqualTo(1);
                assertThat(selection.end()).isEqualTo(2);
                assertThat(selection.line()).isSameAs(line);
            });
    }

    // -- Range.contains --

    @Test
    void testContainsIsTrueWithinTheInclusiveRange() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());

        var range = anchoredRange(line, 0, 1);

        assertThat(range.contains(0)).isTrue();
        assertThat(range.contains(1)).isTrue();
        assertThat(range.contains(2)).isFalse();
    }

    @Test
    void testContainsIncludesBreathMarkAfterTheRangeEnd() {
        // [CROTCHET(0), BREATH_MARK(1), CROTCHET(2)] — the breath mark belongs to the
        // crotchet before it and is deleted with it, so selecting only that crotchet must
        // report the breath mark as selected too (refs #698).
        var line = lineWith(ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET);
        var range = Selection.Range.single(line, 0);

        assertThat(range.contains(1)).as("breath mark after the selection").isTrue();
        assertThat(range.contains(2)).as("crotchet past the breath mark").isFalse();
    }

    @Test
    void testContainsIncludesBreathMarkAfterAMultiElementRange() {
        // [CROTCHET(0), CROTCHET(1), BREATH_MARK(2), CROTCHET(3)] — dragging across the two
        // crotchets must carry the breath mark trailing the range *end*. Selecting a single
        // element leaves begin and end equal, so only a range this wide can tell the two
        // apart: extending from begin instead would reach index 1, not the breath mark.
        var line = lineWith(
            ElementType.CROTCHET, ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET
        );
        var range = anchoredRange(line, 0, 1);

        assertThat(range.contains(2)).as("breath mark after the range end").isTrue();
        assertThat(range.contains(3)).as("crotchet past the breath mark").isFalse();
    }

    @Test
    void testContainsExcludesABreathMarkBeforeTheRange() {
        // [CROTCHET(0), BREATH_MARK(1), CROTCHET(2), CROTCHET(3)] — the breath mark belongs
        // to crotchet 0, which is outside the selection, so selecting crotchet 2 must leave
        // it unselected. Only a lookup below the range start reaches this branch, and
        // making the breath-mark rule symmetric would silently break it (refs #698).
        var line = lineWith(
            ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET, ElementType.CROTCHET
        );
        var range = Selection.Range.single(line, 2);

        assertThat(range.contains(1)).as("breath mark before the selection").isFalse();
        assertThat(range.contains(0)).as("crotchet owning that breath mark").isFalse();
    }

    @Test
    void testContainsHandlesARangeEndingAtTheLastElement() {
        // [CROTCHET(0), CROTCHET(1)] — with the range on the last element there is no
        // successor to inspect for a breath mark, so it must come back unextended rather
        // than reaching past the end of the line.
        var line = lineWith(ElementType.CROTCHET, ElementType.CROTCHET);
        var range = Selection.Range.single(line, 1);

        assertThat(range.contains(1)).as("the selected last element").isTrue();
        assertThat(range.contains(2)).as("one index past the end of the line").isFalse();
    }

    @Test
    void testTrailingBreathMarkDoesNotWidenTheRange() {
        // The trailing breath mark reads as selected without joining the range: the range is
        // what the toolbar actions reflect and what a tie or a beam is built from, and a
        // single-note selection must stay single (refs #698).
        var line = lineWith(ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET);
        var range = Selection.Range.single(line, 0);

        assertThat(range.contains(1))
            .as("breath mark reads as selected")
            .isTrue();
        assertThat(range.end())
            .as("breath mark does not move the range end")
            .isEqualTo(0);
        assertThat(range.size())
            .as("breath mark does not grow the range")
            .isEqualTo(1);
        assertThat(range.singleElement())
            .as("the selection is still the single clicked crotchet")
            .isSameAs(line.getElement(0));
    }

    // -- Range.singleElement --

    @Test
    void testSingleElementIsNullForAMultiElementRange() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());

        assertThat(anchoredRange(line, 0, 1).singleElement()).isNull();
    }

    // -- canToggleBeaming / canToggleTuplet with grace notes (refs #592) --

    @Test
    void testGraceHostPairInsideRangeCanToggleBeaming() {
        var range = rangeOverAllOf(ElementType.QUAVER, ElementType.GRACE_QUAVER, ElementType.QUAVER);

        assertThat(RangeQueries.canToggleBeaming(range)).isTrue();
        assertThat(RangeQueries.nonGraceBegin(range)).isEqualTo(0);
        assertThat(RangeQueries.nonGraceEnd(range)).isEqualTo(2);
    }

    @Test
    void testLeadingGraceNoteIsExcludedFromTheBeamSpan() {
        var range = rangeOverAllOf(ElementType.GRACE_QUAVER, ElementType.QUAVER, ElementType.QUAVER);

        assertThat(RangeQueries.canToggleBeaming(range)).isTrue();
        assertThat(RangeQueries.nonGraceBegin(range)).isEqualTo(1);
        assertThat(RangeQueries.nonGraceEnd(range)).isEqualTo(2);
    }

    @Test
    void testSingleNoteWithGraceNoteCannotToggleBeaming() {
        var range = rangeOverAllOf(ElementType.GRACE_QUAVER, ElementType.QUAVER);

        assertThat(RangeQueries.canToggleBeaming(range)).isFalse();
    }

    // The leading QUAVER makes the grace-note exemption in the range check actually run; a
    // fixture whose first element is already unbeamable would short-circuit before reaching it.
    @Test
    void testGraceNoteDoesNotMakeUnbeamableRangeBeamable() {
        var range = rangeOverAllOf(ElementType.QUAVER, ElementType.GRACE_QUAVER, ElementType.CROTCHET);

        assertThat(RangeQueries.canToggleBeaming(range))
            .as("the unbeamable crotchet still blocks beaming")
            .isFalse();
    }

    @Test
    void testOnlyGraceNotesSelectedCannotToggleBeaming() {
        var range = rangeOverAllOf(ElementType.GRACE_QUAVER, ElementType.GRACE_QUAVER);

        assertThat(RangeQueries.canToggleBeaming(range)).isFalse();
        assertThat(RangeQueries.nonGraceBegin(range)).isEqualTo(-1);
        assertThat(RangeQueries.nonGraceEnd(range)).isEqualTo(-1);
    }

    @Test
    void testGraceHostPairInsideRangeCanToggleTuplet() {
        var range = rangeOverAllOf(ElementType.CROTCHET, ElementType.GRACE_QUAVER, ElementType.CROTCHET);

        assertThat(RangeQueries.canToggleTuplet(range).canToggle()).isTrue();
    }

    @Test
    void testSingleNoteWithGraceNoteCannotToggleTuplet() {
        var range = rangeOverAllOf(ElementType.GRACE_QUAVER, ElementType.CROTCHET);

        assertThat(RangeQueries.canToggleTuplet(range).canToggle()).isFalse();
    }

    @Test
    void testTupletCoverageIgnoresTrailingGraceNote() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(graceQuaver());
        line.addTuplet(Tuplet.withUnresolvedRatio(
            line.getElement(0), line.getElement(1), TupletAction.Tuplet.TRIPLET.getSize()));

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 2));

        assertThat(info.canToggle()).isTrue();
        assertThat(info.coversExisting())
            .as("the trailing grace note does not stop the range from covering the tuplet")
            .isTrue();
    }

    // -- canToggleTuplet --

    @Test
    void testTwoPitchedNotesNoTupletCanToggle() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(crotchet());
        line.addElement(crotchet());

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 1));

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testFullCoverageOfTripletReportsCoversExisting() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addTuplet(Tuplet.withUnresolvedRatio(
            line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 2));

        assertThat(info.canToggle()).isTrue();
        assertThat(info.existing())
            .isNotNull()
            .extracting(Tuplet::getGrade)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isTrue();
    }

    @Test
    void testPartialCoverageOfTripletCannotToggleButStillReportsTheTuplet() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addTuplet(Tuplet.withUnresolvedRatio(
            line.getElement(0), line.getElement(2), TupletAction.Tuplet.TRIPLET.getSize()));

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 1));

        assertThat(info.canToggle())
            .as("a strict sub-range offers no creation decision")
            .isFalse();
        assertThat(info.validGrades()).isEmpty();
        assertThat(info.existing())
            .isNotNull()
            .extracting(Tuplet::getGrade)
            .isEqualTo(TupletAction.Tuplet.TRIPLET.getSize());
        assertThat(info.coversExisting()).isFalse();
    }

    /**
     * The set must name the grades the span could really become, not simply every grade.
     * Three quarter notes at a quarter beat total three quarters, so only a 3 and a 6 say
     * anything, and the other four are each refused for a different reason: a 2 leaves a
     * dotted quarter with no conventional span, a 4 leaves a dotted eighth with none, a 5
     * and a 7 do not divide the span evenly at all.
     */
    @Test
    void testValidGradesNamesOnlyTheGradesTheSpanCanBecome() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 2));

        assertThat(info.validGrades())
            .as("three quarters at a quarter beat are notatable as 3:2 or 6:4 and nothing else")
            .containsExactlyInAnyOrder(
                TupletAction.Tuplet.TRIPLET.getSize(), TupletAction.Tuplet.SEXTUPLET.getSize());
    }

    /**
     * The counterpart to the case above: a span that only one grade fits. Five eighths
     * divide evenly by five and by nothing else in range, so every other grade leaves a
     * written value that is not a note.
     */
    @Test
    void testValidGradesCanNameASingleGrade() {
        var line = withQuarterBeat(detachedLine());

        for (var i = 0; i < QUINTUPLET_NOTE_COUNT; i++) {
            line.addElement(quaver());
        }

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, QUINTUPLET_NOTE_COUNT - 1));

        assertThat(info.validGrades())
            .as("five eighths at a quarter beat are a quintuplet or nothing")
            .containsExactly(TupletAction.Tuplet.QUINTUPLET.getSize());
    }

    /**
     * A rest carries written duration exactly as a note does, so a span containing one is
     * still a candidate. Before this rule a rest anywhere in the selection disabled the
     * tuplet control outright.
     */
    @Test
    void testValidGradesCountsARestAsPartOfTheSpan() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(crotchet());
        line.addElement(crotchetRest());
        line.addElement(crotchet());

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 2));

        assertThat(info.validGrades())
            .as("a rest contributes its duration, so the span reads the same as three notes")
            .containsExactlyInAnyOrder(
                TupletAction.Tuplet.TRIPLET.getSize(), TupletAction.Tuplet.SEXTUPLET.getSize());
    }

    @Test
    void testRangeSpanningTwoDifferentTupletsCannotToggle() {
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addTuplet(Tuplet.withUnresolvedRatio(
            line.getElement(0), line.getElement(1), TupletAction.Tuplet.DUPLET.getSize()));
        line.addTuplet(Tuplet.withUnresolvedRatio(
            line.getElement(2), line.getElement(3), TupletAction.Tuplet.TRIPLET.getSize()));

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 3));

        assertThat(info.canToggle()).isFalse();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    @Test
    void testRangeContainingRestCanToggleTuplet() {
        var line = withQuarterBeat(detachedLine());
        line.addElement(crotchet());
        line.addElement(crotchetRest());

        var info = RangeQueries.canToggleTuplet(rangeOver(line, 0, 1));

        assertThat(info.canToggle())
            .as("a rest carries written duration, so it belongs inside a new tuplet")
            .isTrue();
        assertThat(info.existing()).isNull();
        assertThat(info.coversExisting()).isFalse();
    }

    // -- canToggleTie failure paths --

    @Test
    void testCanToggleTieWithASingleElementAndNoAdjacentLineIsFalse() {
        // A single selected element is no longer rejected outright — it is the cross-line tie
        // case (#493), which pairs it with a same-pitch note at the adjacent line's own edge.
        // What still rejects it is having no adjacent line to pair with: this line stands alone
        // in its song, so there is no note on either side of it to tie to.
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.addElement(crotchet());
            line.addElement(crotchet());
        });

        assertThat(song.lineCount())
            .as("the song must hold only this line, or a partner note would exist")
            .isEqualTo(1);
        assertThat(RangeQueries.canToggleTie(ElementSelection.single(line, 0))).isFalse();
    }

    @Test
    void testCanToggleTieWithTwoSeparatorsIsFalse() {
        // [CROTCHET(0), SINGLE_BARLINE(1), SINGLE_BARLINE(2), CROTCHET(3)] — a tie may
        // span at most one separator, so two in a row must be rejected (refs #527).
        var range = rangeOverAllOf(
            ElementType.CROTCHET,
            ElementType.SINGLE_BARLINE,
            ElementType.SINGLE_BARLINE,
            ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isFalse();
    }

    @Test
    void testCanToggleTieWithNonPitchedElementIsFalse() {
        var range = rangeOverAllOf(ElementType.CROTCHET, ElementType.CROTCHET_REST);

        assertThat(RangeQueries.canToggleTie(range)).isFalse();
    }

    @Test
    void testCanToggleTieWithPitchMismatchIsFalse() {
        // Two notes at different staff positions → different pitches.
        var line = detachedLine();
        var note0 = crotchet();
        note0.setStaffPosition(0);
        var note1 = crotchet();
        note1.setStaffPosition(1);
        line.addElement(note0);
        line.addElement(note1);

        assertThat(RangeQueries.canToggleTie(rangeOver(line, 0, 1))).isFalse();
    }

    @Test
    void testCanToggleTieAcrossBarlineIsTrue() {
        // [CROTCHET(0), SINGLE_BARLINE(1), CROTCHET(2)] — a barline between two
        // same-pitch notes must not block the tie (refs #527).
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.SINGLE_BARLINE, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isTrue();
    }

    @Test
    void testCanToggleTieAcrossRepeatIsTrue() {
        // [CROTCHET(0), REPEAT_RIGHT(1), CROTCHET(2)] — a repeat between two
        // same-pitch notes must not block the tie (refs #527).
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.REPEAT_RIGHT, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isTrue();
    }

    @Test
    void testCanToggleTieAcrossBreathMarkIsTrue() {
        // [CROTCHET(0), BREATH_MARK(1), CROTCHET(2)] — a breath mark is the third kind of
        // separator a tie may span, alongside barlines and repeats (refs #527).
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.BREATH_MARK, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isTrue();
    }

    @Test
    void testCanToggleTieAcrossDoubleBarlineIsTrue() {
        // [CROTCHET(0), DOUBLE_BARLINE(1), CROTCHET(2)] — an ordinary double barline is a
        // section break, not the end of the piece, so a tie may span it (refs #527).
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.DOUBLE_BARLINE, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isTrue();
    }

    @Test
    void testCanToggleTieAcrossFinalBarlineIsFalse() {
        // [CROTCHET(0), FINAL_DOUBLE_BARLINE(1), CROTCHET(2)] — a final barline ends the
        // piece, so no note may sound across it (refs #527).
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isFalse();
    }

    @Test
    void testCanToggleTieWithNoteBetweenTwoNotesIsFalse() {
        // [CROTCHET(0), CROTCHET(1), CROTCHET(2)] — a real note between the two
        // endpoints is not transparent to a tie, unlike a barline or repeat.
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isFalse();
    }

    @Test
    void testCanToggleTieWithRestBetweenTwoNotesIsFalse() {
        // [CROTCHET(0), CROTCHET_REST(1), CROTCHET(2)] — a rest takes time, so the notes
        // on either side are not adjacent and cannot be tied (refs #527).
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.CROTCHET_REST, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isFalse();
    }

    @Test
    void testCanToggleTieWithGraceNoteBetweenTwoNotesIsFalse() {
        // [CROTCHET(0), GRACE_QUAVER(1), CROTCHET(2)] — a grace note is transparent to a
        // beam or tuplet, but not to a tie: only a separator may sit between tied notes.
        var range = rangeOverAllOf(
            ElementType.CROTCHET, ElementType.GRACE_QUAVER, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isFalse();
    }

    @Test
    void testCanToggleTieWithLeadingRestIsFalse() {
        // [CROTCHET_REST(0), CROTCHET(1), CROTCHET(2)] — the range's endpoints are the
        // tied notes themselves. Padding at the edge is not skipped, so a leading rest
        // makes this an invalid tie selection rather than a tie of notes 1 and 2.
        var range = rangeOverAllOf(
            ElementType.CROTCHET_REST, ElementType.CROTCHET, ElementType.CROTCHET
        );

        assertThat(RangeQueries.canToggleTie(range)).isFalse();
    }

    @Test
    void testCanToggleTieAcrossBarlineWithPitchMismatchIsFalse() {
        // A separator does not excuse the pitch rule: tied notes must share a pitch.
        var line = detachedLine();
        var note0 = crotchet();
        note0.setStaffPosition(0);
        var note2 = crotchet();
        note2.setStaffPosition(1);
        line.addElement(note0);
        line.addElement(singleBarline());
        line.addElement(note2);

        assertThat(RangeQueries.canToggleTie(rangeOver(line, 0, 2))).isFalse();
    }

    @Test
    void testCanToggleTieWithElementsInDifferentTiesAllowsChainedTie() {
        // tie1 spans notes 0-1, tie2 spans notes 2-3.
        // Selecting notes 1 and 2 chains a new tie between the two existing ties.
        var line = detachedLine();
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addElement(crotchet());
        line.addTie(new Tie(line.getElement(0), line.getElement(1)));
        line.addTie(new Tie(line.getElement(2), line.getElement(3)));

        assertThat(RangeQueries.canToggleTie(rangeOver(line, 1, 2))).isTrue();
    }

    // -- canToggleTrill --

    @Test
    void testCanToggleTrillIsTrueWhenAtLeastOnePitchedNoteInRange() {
        var range = rangeOverAllOf(ElementType.CROTCHET, ElementType.CROTCHET);

        assertThat(RangeQueries.canToggleTrill(range)).isTrue();
    }

    @Test
    void testCanToggleTrillIsFalseWhenRangeContainsOnlyRests() {
        var range = rangeOverAllOf(ElementType.CROTCHET_REST, ElementType.CROTCHET_REST);

        assertThat(RangeQueries.canToggleTrill(range)).isFalse();
    }

    // -- canModifyStemDirection --

    @Test
    void testCanModifyStemDirectionIsTrueWhenAtLeastOneStemmedNoteInRange() {
        var range = rangeOverAllOf(ElementType.CROTCHET, ElementType.CROTCHET);

        assertThat(RangeQueries.canModifyStemDirection(range)).isTrue();
    }

    @Test
    void testCanModifyStemDirectionIsFalseWhenRangeIsAllRests() {
        var range = rangeOverAllOf(ElementType.CROTCHET_REST, ElementType.CROTCHET_REST);

        assertThat(RangeQueries.canModifyStemDirection(range)).isFalse();
    }

    @Test
    void testCanModifyStemDirectionIsFalseWhenRangeIsAllWholeNotes() {
        var range = rangeOverAllOf(ElementType.SEMIBREVE, ElementType.SEMIBREVE);

        assertThat(RangeQueries.canModifyStemDirection(range)).isFalse();
    }

    @Test
    void testCanModifyStemDirectionIsTrueWhenWholeNoteRangedWithStemmedNote() {
        var range = rangeOverAllOf(ElementType.SEMIBREVE, ElementType.CROTCHET);

        assertThat(RangeQueries.canModifyStemDirection(range)).isTrue();
    }
}
