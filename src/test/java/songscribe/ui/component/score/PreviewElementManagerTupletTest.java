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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Tuplet;

/**
 * Tests that tuplet spans are removed when elements within them are modified
 * or inserted via {@link PreviewElementManager} — specifically:
 * <ul>
 *   <li>{@code modifyExistingElement}: replacing a note with one of a different
 *       duration type or dot count removes any containing tuplet.</li>
 *   <li>{@code modifyExistingElement}: replacing a note with the same type and
 *       dot count (e.g., different pitch only) preserves the tuplet.</li>
 *   <li>{@code insertElement}: inserting a note into an existing tuplet removes it
 *       without a confirmation dialog.</li>
 * </ul>
 */
class PreviewElementManagerTupletTest extends PreviewElementManagerTestBase {

    private static final int TRIPLET_SIZE = 3;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void addNotes(int count, ElementType type) {
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < count; i++) {
                line.addElement(type.newInstance());
            }
        });
    }

    private void addTriplet(int start, int end) {
        // Add directly to spans via song.withoutMutationTracking to bypass
        // the modification bracket requirement.
        song.withoutMutationTracking(() -> line.addTuplet(
            Tuplet.withUnresolvedRatio(line.getElement(start), line.getElement(end), TRIPLET_SIZE)));
    }

    // -----------------------------------------------------------------------
    // modifyExistingElement
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModifyExistingElement {

        /**
         * Routing: xPosSsMatchesElement=true → modifyExistingElement at index 1.
         * Three quavers with a triplet [0..2]. Replacing index 1 with a crotchet
         * (different type) must remove the tuplet.
         */
        @Test
        void testDifferentDurationTypeRemovesTuplet() {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.setCurrentXIndex(1);
            setPreviewElement(ElementType.CROTCHET.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.findSpans(Tuplet.class)).isEmpty();
        }

        /**
         * Same type (quaver) but dot count changes from 0 to 1.
         * The tuplet must be removed.
         */
        @Test
        void testDifferentDotCountRemovesTuplet() {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            var dottedQuaver = ElementType.QUAVER.newInstance();
            dottedQuaver.setDotCount(1);

            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.setCurrentXIndex(1);
            setPreviewElement(dottedQuaver);

            PreviewElementManager.handleClick(lc);

            assertThat(line.findSpans(Tuplet.class)).isEmpty();
        }

        /**
         * Same type and same dot count (only pitch differs): tuplet must be preserved.
         */
        @Test
        void testSameDurationAndDotCountPreservesTuplet() {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            PreviewElementManager.setXPosSsMatchesElement(true);
            PreviewElementManager.setCurrentXIndex(1);
            setPreviewElement(ElementType.QUAVER.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.findSpans(Tuplet.class)).isNotEmpty();
            assertThat(line.findTupletAt(0)).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // insertElement
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertElement {

        /**
         * Routing: xPosSsMatchesElement=false, currentXIndex < elementCount → insertElement.
         * Three quavers with a triplet [0..2]. Inserting at index 1 (inside the span)
         * must remove the tuplet without a confirmation dialog.
         */
        @Test
        void testInsertingIntoTupletRemovesIt() {
            addNotes(3, ElementType.QUAVER);
            addTriplet(0, 2);

            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.setCurrentXIndex(1);
            setPreviewElement(ElementType.QUAVER.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.findSpans(Tuplet.class)).isEmpty();
        }

        /**
         * Inserting after a tuplet (at an index beyond the tuplet's span)
         * must leave the tuplet intact.
         */
        @Test
        void testInsertingAfterTupletPreservesIt() {
            addNotes(3, ElementType.QUAVER);
            // Tuplet covers only [0..1]; inserting at index 3 (after index 2) is outside
            addTriplet(0, 1);

            PreviewElementManager.setXPosSsMatchesElement(false);
            PreviewElementManager.setCurrentXIndex(3);
            setPreviewElement(ElementType.QUAVER.newInstance());

            PreviewElementManager.handleClick(lc);

            assertThat(line.findSpans(Tuplet.class)).isNotEmpty();
            assertThat(line.findTupletAt(0)).isNotNull();
        }
    }
}
