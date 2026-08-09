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
package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for how {@link Line#removeElement} and {@link Line#removeRange} reshape
 * hairpins.
 *
 * <p>Deleting an element a hairpin is anchored to shortens the hairpin to the nearest
 * surviving element instead of destroying it; the hairpin is only removed when what
 * survives holds fewer than {@link Hairpin#MIN_COLUMNS} columns — which two elements can
 * fail to do, since a grace note shares its host's column. Deleting everything that
 * separated two same-type
 * hairpins merges them into one, matching the merge {@code Line.addCrescendo} performs
 * when the user draws a hairpin flush against another.
 */
class LineHairpinDeletionTest extends UnitTest {

    // Indices into the line built in setUp()
    private static final int IDX_0 = 0;
    private static final int IDX_1 = 1;
    private static final int IDX_2 = 2;
    private static final int IDX_3 = 3;
    private static final int IDX_4 = 4;
    private static final int IDX_5 = 5;

    // Number of note elements placed in the line fixture
    private static final int NOTE_COUNT = 6;

    private Song song;
    private Line line;

    /** Adds {@value #NOTE_COUNT} quarter notes to {@code line} via suspended tracking. */
    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                line.addElement(new StaffElement(ElementType.CROTCHET));
            }
        });
    }

    private void addCrescendo(int anchorIndex, int endIndex) {
        song.withoutMutationTracking(() ->
            line.addCrescendo(new Crescendo(line.getElement(anchorIndex), line.getElement(endIndex))));
    }

    private void addDiminuendo(int anchorIndex, int endIndex) {
        song.withoutMutationTracking(() ->
            line.addDiminuendo(new Diminuendo(line.getElement(anchorIndex), line.getElement(endIndex))));
    }

    // -----------------------------------------------------------------------
    // Shortening
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenAnEndpointIsDeleted {

        /**
         * A crescendo over elements 1–3 whose anchor is deleted must survive as a
         * crescendo over the elements that were 2 and 3.
         */
        @Test
        void testDeletingTheAnchorPullsTheHairpinInToTheNextElement() {
            addCrescendo(IDX_1, IDX_3);
            var newAnchor = line.getElement(IDX_2);
            var unchangedEnd = line.getElement(IDX_3);

            song.withModification(() -> line.removeElement(IDX_1));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("deleting an endpoint must shorten the hairpin, not remove it")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElement())
                    .as("the anchor must move to the element that followed the deleted one")
                    .isSameAs(newAnchor),
                () -> assertThat(crescendos.getFirst().getEndElement())
                    .as("the surviving end must not move")
                    .isSameAs(unchangedEnd)
            );
        }

        /**
         * A crescendo over elements 1–3 whose end is deleted must survive as a
         * crescendo over the elements that were 1 and 2.
         */
        @Test
        void testDeletingTheEndPullsTheHairpinInToThePreviousElement() {
            addCrescendo(IDX_1, IDX_3);
            var unchangedAnchor = line.getElement(IDX_1);
            var newEnd = line.getElement(IDX_2);

            song.withModification(() -> line.removeElement(IDX_3));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos).hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElement())
                    .as("the surviving anchor must not move")
                    .isSameAs(unchangedAnchor),
                () -> assertThat(crescendos.getFirst().getEndElement())
                    .as("the end must move to the element that preceded the deleted one")
                    .isSameAs(newEnd)
            );
        }

        /**
         * A two-element hairpin has nothing to shorten to: deleting either endpoint
         * leaves one element, which cannot carry a wedge.
         */
        @Test
        void testDeletingAnEndpointOfATwoElementHairpinRemovesIt() {
            addCrescendo(IDX_1, IDX_2);

            song.withModification(() -> line.removeElement(IDX_2));

            assertThat(line.findSpans(Crescendo.class))
                .as("a hairpin left with one element must be removed")
                .isEmpty();
        }

        /**
         * A hairpin anchored on a grace note covers a single column across that note and
         * its host, so a deletion that leaves only the pair has nothing left to slope
         * across — two surviving elements are not always two columns.
         */
        @Test
        void testDeletingDownToAGraceHostPairRemovesTheHairpin() {
            song.withoutMutationTracking(() ->
                line.setElement(IDX_1, new StaffElement(ElementType.GRACE_QUAVER)));
            addCrescendo(IDX_1, IDX_3);

            song.withModification(() -> line.removeElement(IDX_3));

            assertThat(line.findSpans(Crescendo.class))
                .as("a hairpin left with one column must be removed")
                .isEmpty();
        }

        /**
         * A range deletion that takes both the end and every interior element leaves the
         * anchor alone in the range, so the hairpin goes.
         */
        @Test
        void testDeletingAllButOneElementOfAHairpinRemovesIt() {
            addCrescendo(IDX_1, IDX_4);

            song.withModification(() -> line.removeRange(IDX_2, IDX_4));

            assertThat(line.findSpans(Crescendo.class))
                .as("a hairpin left with one element must be removed")
                .isEmpty();
        }

        /**
         * A range deletion that takes the end but leaves two elements shortens the
         * hairpin to what survives.
         */
        @Test
        void testDeletingARangeAtTheEndShortensTheHairpin() {
            addCrescendo(IDX_1, IDX_4);
            var unchangedAnchor = line.getElement(IDX_1);
            var newEnd = line.getElement(IDX_2);

            song.withModification(() -> line.removeRange(IDX_3, IDX_4));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos).hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElement()).isSameAs(unchangedAnchor),
                () -> assertThat(crescendos.getFirst().getEndElement())
                    .as("the end must move to the last surviving element of the range")
                    .isSameAs(newEnd)
            );
        }

        /**
         * A hairpin ends at a rest's left edge as legitimately as on a note, so an end
         * pulled in onto a rest stops there.
         */
        @Test
        void testDeletingTheEndPullsInToARest() {
            song.withoutMutationTracking(() -> line.setElement(IDX_2, new StaffElement(ElementType.CROTCHET_REST)));
            addCrescendo(IDX_0, IDX_3);
            var newEnd = line.getElement(IDX_2);

            song.withModification(() -> line.removeElement(IDX_3));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos).hasSize(1),
                () -> assertThat(crescendos.getFirst().getEndElement())
                    .as("a rest bounds a wedge, so the end must stop on it rather than skip past")
                    .isSameAs(newEnd)
            );
        }

        /**
         * A hairpin may end on at most one rest, so a deleted end that leaves two of them
         * behind stops on the first — the same rule the menu applies when offering a new
         * hairpin over a selection.
         */
        @Test
        void testDeletingTheEndPullsInToTheFirstOfTwoRests() {
            song.withoutMutationTracking(() -> {
                line.setElement(IDX_2, new StaffElement(ElementType.CROTCHET_REST));
                line.setElement(IDX_3, new StaffElement(ElementType.CROTCHET_REST));
            });
            addCrescendo(IDX_0, IDX_4);
            var newEnd = line.getElement(IDX_2);

            song.withModification(() -> line.removeElement(IDX_4));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos).hasSize(1),
                () -> assertThat(crescendos.getFirst().getEndElement())
                    .as("the second rest cannot end a hairpin, so the end walks back to the first")
                    .isSameAs(newEnd)
            );
        }

        /**
         * A hairpin over note–rest–note whose last note is deleted still has two elements
         * a wedge may span, the second of them a rest, so it survives shortened.
         */
        @Test
        void testDeletingTheEndLeavingANoteAndARestKeepsTheHairpin() {
            song.withoutMutationTracking(() -> line.setElement(IDX_1, new StaffElement(ElementType.CROTCHET_REST)));
            addCrescendo(IDX_0, IDX_2);
            var newEnd = line.getElement(IDX_1);

            song.withModification(() -> line.removeElement(IDX_2));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("a note and the rest after it can still carry a wedge")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getEndElement())
                    .as("the end must move to the surviving rest")
                    .isSameAs(newEnd)
            );
        }

        /**
         * A grace note is part of the note it precedes, so it may begin a hairpin — an
         * anchor pulled in stops there rather than at its host.
         */
        @Test
        void testDeletingTheAnchorPullsInToAGraceNoteBeforeAPitchedNote() {
            song.withoutMutationTracking(() -> line.setElement(IDX_1, new StaffElement(ElementType.GRACE_QUAVER)));
            addCrescendo(IDX_0, IDX_3);
            var newAnchor = line.getElement(IDX_1);

            song.withModification(() -> line.removeElement(IDX_0));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos).hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElement())
                    .as("a grace note before a pitched note may anchor the hairpin")
                    .isSameAs(newAnchor)
            );
        }

        /** A surviving endpoint stays put, even one an older build left on a rest. */
        @Test
        void testDeletingAnInteriorElementLeavesARestAnchoredHairpinAlone() {
            song.withoutMutationTracking(() -> line.setElement(IDX_0, new StaffElement(ElementType.CROTCHET_REST)));
            addCrescendo(IDX_0, IDX_3);
            var crescendo = line.findSpans(Crescendo.class).getFirst();
            var anchor = crescendo.getAnchorElement();

            song.withModification(() -> line.removeElement(IDX_2));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos).containsExactly(crescendo),
                () -> assertThat(crescendos.getFirst().getAnchorElement())
                    .as("a legacy rest anchor is not this deletion's to correct")
                    .isSameAs(anchor)
            );
        }

        /** A deletion inside a hairpin's span must leave the hairpin object alone. */
        @Test
        void testDeletingAnInteriorElementLeavesTheHairpinUntouched() {
            addCrescendo(IDX_0, IDX_3);
            var crescendo = line.findSpans(Crescendo.class).getFirst();
            var anchor = crescendo.getAnchorElement();
            var end = crescendo.getEndElement();

            song.withModification(() -> line.removeElement(IDX_2));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("an untouched hairpin must not be replaced")
                    .containsExactly(crescendo),
                () -> assertThat(crescendos.getFirst().getAnchorElement()).isSameAs(anchor),
                () -> assertThat(crescendos.getFirst().getEndElement()).isSameAs(end)
            );
        }

        /**
         * {@code resolveEndIndex} walks backward from the last survivor in the hairpin's
         * range, skipping any that cannot end a hairpin, until it finds one that can. Two
         * grace notes intervene between the deleted end and a surviving rest, so this proves
         * the walk itself rather than a rest found on the first candidate.
         */
        @Test
        void testDeletingTheEndWalksBackPastGraceNotesToASurvivingRest() {
            song.withoutMutationTracking(() -> {
                line.setElement(IDX_2, new StaffElement(ElementType.CROTCHET_REST));
                line.setElement(IDX_3, new StaffElement(ElementType.GRACE_QUAVER));
            });
            addCrescendo(IDX_0, IDX_4);
            var newEnd = line.getElement(IDX_2);

            song.withModification(() -> line.removeElement(IDX_4));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("a rest reachable by walking back past ineligible elements still ends the hairpin")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getEndElement())
                    .as("the end must land on the rest, skipping the intervening grace note")
                    .isSameAs(newEnd)
            );
        }

        /**
         * When every surviving element in a hairpin's range is a grace note — nothing that
         * can end a hairpin — {@code resolveEndIndex} returns -1 and the hairpin is removed,
         * distinct from removal via too few survivors: here the anchor itself survives, but
         * cannot serve as the end either.
         */
        @Test
        void testDeletingTheEndLeavingOnlyGraceNotesRemovesTheHairpin() {
            song.withoutMutationTracking(() -> {
                line.setElement(IDX_0, new StaffElement(ElementType.GRACE_QUAVER));
                line.setElement(IDX_1, new StaffElement(ElementType.GRACE_QUAVER));
            });
            addCrescendo(IDX_0, IDX_2);

            song.withModification(() -> line.removeElement(IDX_2));

            assertThat(line.findSpans(Crescendo.class))
                .as("a range with nothing eligible to end a hairpin leaves it with no end")
                .isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Merging
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenASeparatingElementIsDeleted {

        /**
         * Crescendos over 0–1 and 3–4 are separated by element 2 alone. Deleting it
         * leaves nothing between them, so they become one crescendo.
         */
        @Test
        void testDeletingTheOnlyElementBetweenSameTypeHairpinsMergesThem() {
            addCrescendo(IDX_0, IDX_1);
            addCrescendo(IDX_3, IDX_4);
            var mergedAnchor = line.getElement(IDX_0);
            var mergedEnd = line.getElement(IDX_4);

            song.withModification(() -> line.removeElement(IDX_2));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos)
                    .as("hairpins left touching must merge into one")
                    .hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElement()).isSameAs(mergedAnchor),
                () -> assertThat(crescendos.getFirst().getEndElement()).isSameAs(mergedEnd)
            );
        }

        /**
         * Crescendos over 0–1 and 4–5 are separated by elements 2 and 3. Deleting both
         * closes the gap entirely, so they merge.
         */
        @Test
        void testDeletingEveryElementBetweenSameTypeHairpinsMergesThem() {
            addCrescendo(IDX_0, IDX_1);
            addCrescendo(IDX_4, IDX_5);
            var mergedAnchor = line.getElement(IDX_0);
            var mergedEnd = line.getElement(IDX_5);

            song.withModification(() -> line.removeRange(IDX_2, IDX_3));

            var crescendos = line.findSpans(Crescendo.class);

            assertAll(
                () -> assertThat(crescendos).hasSize(1),
                () -> assertThat(crescendos.getFirst().getAnchorElement()).isSameAs(mergedAnchor),
                () -> assertThat(crescendos.getFirst().getEndElement()).isSameAs(mergedEnd)
            );
        }

        /** One surviving element between two hairpins is still a break in the gesture. */
        @Test
        void testDeletingOneOfTwoSeparatingElementsKeepsTheHairpinsApart() {
            addCrescendo(IDX_0, IDX_1);
            addCrescendo(IDX_4, IDX_5);

            song.withModification(() -> line.removeElement(IDX_2));

            assertThat(line.findSpans(Crescendo.class))
                .as("a hairpin still separated from another must stay separate")
                .hasSize(2);
        }

        /** Opposite-type hairpins are different gestures and must never merge. */
        @Test
        void testDeletingTheOnlyElementBetweenOppositeTypeHairpinsKeepsThemApart() {
            addCrescendo(IDX_0, IDX_1);
            addDiminuendo(IDX_3, IDX_4);

            song.withModification(() -> line.removeElement(IDX_2));

            assertAll(
                () -> assertThat(line.findSpans(Crescendo.class))
                    .as("the crescendo must survive on its own")
                    .hasSize(1),
                () -> assertThat(line.findSpans(Diminuendo.class))
                    .as("the diminuendo must survive on its own")
                    .hasSize(1)
            );
        }
    }
}
