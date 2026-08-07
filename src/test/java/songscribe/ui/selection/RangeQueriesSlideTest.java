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
import static songscribe.dom.StaffElementFactory.createNote;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/**
 * Unit tests for the slide eligibility predicates on {@link RangeQueries} —
 * {@link RangeQueries#glissandoSourceIndex}, {@link RangeQueries#canToggleGlissando},
 * {@link RangeQueries#fallIndices} and {@link RangeQueries#canToggleFall}.
 * <p>
 * They are the one gate both the select-mode toggle actions and the edit-mode keys pass through,
 * so what they refuse is what neither mode can do. Every fixture builds a range directly: the
 * queries are pure functions of {@code (line, begin, end)}.
 * <p>
 * Kept apart from {@link RangeQueriesTest} because the glissando rules are a decision table of
 * their own — the add gates versus the removal that skips all of them.
 */
class RangeQueriesSlideTest extends UnitTest {

    /** Index of the third element of the fixtures built here. */
    private static final int THIRD_INDEX = 2;

    /** A note the fixtures pair with {@link #higherNote} to make a glissando's two pitches differ. */
    private static StaffElement lowerNote() {
        return createNote(0, false);
    }

    /** A note one staff position above {@link #lowerNote}, so a glissando has distance to cover. */
    private static StaffElement higherNote() {
        return createNote(1, false);
    }

    private static Line lineOf(StaffElement... elements) {
        var line = detachedLine();

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    /** A range over {@code begin..end} of {@code line}, anchored at {@code begin}. */
    private static Selection.Range rangeOver(Line line, int begin, int end) {
        return new Selection.Range(line, begin, end, begin);
    }

    // -----------------------------------------------------------------------
    // glissandoSourceIndex — which element a glissando toggle would act on
    // -----------------------------------------------------------------------

    @Nested
    class ResolvingTheGlissandoSource {

        @Test
        void testSingleSelectionResolvesTheSourceToThePrecedingElement() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(RangeQueries.glissandoSourceIndex(Selection.Range.single(line, 1)))
                .as("one selected element reads as the glissando's target, whose source precedes it")
                .isZero();
        }

        @Test
        void testPairSelectionResolvesTheSourceToItsFirstElement() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1)))
                .as("a selected pair reads as source and target in order")
                .isZero();
        }

        @Test
        void testThreeElementRangeHasNoUnambiguousSource() {
            var line = lineOf(lowerNote(), higherNote(), lowerNote());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, THIRD_INDEX)))
                .as("a glissando connects exactly two elements, so three name no single pair")
                .isEqualTo(-1);
        }

        @Test
        void testSelectionAtTheFirstElementHasNoPredecessor() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(RangeQueries.glissandoSourceIndex(Selection.Range.single(line, 0)))
                .as("the source would be index -1")
                .isEqualTo(-1);
        }

        /**
         * A range can outlive the line it was made on — an undo that removed an element leaves one
         * pointing past the end. The source still resolves inside the line, but the target it would
         * connect to does not exist.
         */
        @Test
        void testRangeReachingPastTheEndOfTheLineIsRefused() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(RangeQueries.glissandoSourceIndex(Selection.Range.single(line, line.elementCount())))
                .isEqualTo(-1);
        }
    }

    @Nested
    class WhenAddingAGlissando {

        @Test
        void testRestSourceIsRefused() {
            var line = lineOf(crotchetRest(), higherNote());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1)))
                .as("a rest is not a pitched note, so nothing can slide from it")
                .isEqualTo(-1);
        }

        @Test
        void testRestTargetIsRefused() {
            var line = lineOf(lowerNote(), crotchetRest());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1))).isEqualTo(-1);
        }

        @Test
        void testBarlineTargetIsRefused() {
            var line = lineOf(lowerNote(), singleBarline());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1))).isEqualTo(-1);
        }

        @Test
        void testGraceNoteSourceIsRefused() {
            var line = lineOf(graceQuaver(), higherNote());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1)))
                .as("a grace note carries a pitch but is not a pitched note")
                .isEqualTo(-1);
        }

        @Test
        void testGraceNoteTargetIsRefused() {
            var line = lineOf(lowerNote(), graceQuaver());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1))).isEqualTo(-1);
        }

        @Test
        void testEqualPitchesAreRefused() {
            var line = lineOf(lowerNote(), lowerNote());

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1)))
                .as("a glissando between two notes at one pitch has no distance to traverse")
                .isEqualTo(-1);
        }
    }

    /**
     * Removal answers before any add-only gate runs. A glissando that reached the model through a
     * MusicXML import can sit on a note the add rules would never have allowed one on, and gating
     * removal on those rules would leave it impossible to toggle off.
     */
    @Nested
    class WhenRemovingAGlissando {

        @Test
        void testEqualPitchesStillResolve() {
            var line = lineOf(lowerNote(), lowerNote());
            line.getElement(0).setGlissando();

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1))).isZero();
        }

        @Test
        void testNonPitchedFollowerStillResolves() {
            var line = lineOf(lowerNote(), crotchetRest());
            line.getElement(0).setGlissando();

            assertThat(RangeQueries.glissandoSourceIndex(rangeOver(line, 0, 1)))
                .as("the type gate applies to the add branch only")
                .isZero();
        }

        @Test
        void testASourceAtTheEndOfTheLineStillResolves() {
            var line = lineOf(lowerNote(), higherNote());
            line.getElement(1).setGlissando();

            assertThat(RangeQueries.glissandoSourceIndex(Selection.Range.single(line, line.elementCount())))
                .as("nothing follows the source, yet the glissando on it must still come off")
                .isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // canToggleGlissando — the predicate the actions are enabled from
    // -----------------------------------------------------------------------

    @Nested
    class CanToggleGlissando {

        @Test
        void testTrueWhenASourceResolves() {
            var line = lineOf(lowerNote(), higherNote());

            assertThat(RangeQueries.canToggleGlissando(rangeOver(line, 0, 1))).isTrue();
        }

        @Test
        void testFalseWhenNoSourceResolves() {
            var line = lineOf(lowerNote(), lowerNote());

            assertThat(RangeQueries.canToggleGlissando(rangeOver(line, 0, 1))).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // fallIndices / canToggleFall — which notes a fall toggle applies to
    // -----------------------------------------------------------------------

    @Nested
    class WhichNotesTakeAFall {

        @Test
        void testOnlyPitchedNotesOfAMixedRangeAreNamed() {
            var line = lineOf(lowerNote(), crotchetRest(), higherNote(), graceQuaver(), singleBarline());

            assertThat(RangeQueries.fallIndices(rangeOver(line, 0, line.elementCount() - 1)))
                .as("rests, grace notes and barlines cannot carry a fall")
                .containsExactly(0, THIRD_INDEX);
        }

        @Test
        void testTheRangeEndpointsAreBothIncluded() {
            var line = lineOf(lowerNote(), higherNote(), lowerNote(), higherNote());

            assertThat(RangeQueries.fallIndices(rangeOver(line, 1, THIRD_INDEX)))
                .as("the range is inclusive at both ends, and nothing outside it is named")
                .containsExactly(1, THIRD_INDEX);
        }

        @Test
        void testARangeWithoutAPitchedNoteNamesNothing() {
            var line = lineOf(crotchetRest(), graceQuaver(), singleBarline(), lowerNote());

            assertThat(RangeQueries.fallIndices(rangeOver(line, 0, THIRD_INDEX)))
                .as("the trailing note sits outside the range")
                .isEmpty();
        }

        @Test
        void testCanToggleFallFollowsWhetherAnyIndexWasNamed() {
            var line = lineOf(crotchetRest(), lowerNote());

            assertThat(RangeQueries.canToggleFall(rangeOver(line, 0, 1)))
                .as("one pitched note in the range is enough")
                .isTrue();
            assertThat(RangeQueries.canToggleFall(Selection.Range.single(line, 0)))
                .as("a lone rest is not")
                .isFalse();
        }
    }
}
