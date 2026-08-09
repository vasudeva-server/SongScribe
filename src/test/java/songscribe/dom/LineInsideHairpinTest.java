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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class LineInsideHairpinTest extends UnitTest {

    /** Adds {@code count} crotchets to line and returns it. */
    private static Line lineWithNotes(int count) {
        var line = detachedLine();

        for (var i = 0; i < count; i++) {
            line.addElement(StaffElementFactory.crotchet());
        }

        return line;
    }

    /** Adds a crescendo from index {@code a} to {@code b} inclusive. */
    private static void addCrescendo(Line line, int a, int b) {
        line.addSpan(new Crescendo(line.getElement(a), line.getElement(b)));
    }

    /** Adds a diminuendo from index {@code a} to {@code b} inclusive. */
    private static void addDiminuendo(Line line, int a, int b) {
        line.addSpan(new Diminuendo(line.getElement(a), line.getElement(b)));
    }

    @Test
    void testIndexInsideCrescendoRangeReturnsTrue() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInsideHairpin(3))
            .as("index inside a crescendo range must be reported as inside")
            .isTrue();
    }

    @Test
    void testIndexInsideDiminuendoRangeReturnsTrue() {
        var line = lineWithNotes(6);
        addDiminuendo(line, 2, 5);
        assertThat(line.isInsideHairpin(3))
            .as("index inside a diminuendo range must be reported as inside")
            .isTrue();
    }

    @Test
    void testIndexAtRangeBoundaryStartReturnsFalse() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInsideHairpin(2))
            .as("index at the range start boundary is a bound, not the interior")
            .isFalse();
    }

    @Test
    void testIndexAtRangeBoundaryEndReturnsFalse() {
        var line = lineWithNotes(6);
        addCrescendo(line, 2, 5);
        assertThat(line.isInsideHairpin(5))
            .as("index at the range end boundary is a bound, not the interior")
            .isFalse();
    }

    @Test
    void testIndexOutsideAnyRangeReturnsFalse() {
        var line = lineWithNotes(11);
        addCrescendo(line, 2, 5);
        addDiminuendo(line, 8, 10);
        assertThat(line.isInsideHairpin(6))
            .as("index between two hairpin ranges must not be reported as inside")
            .isFalse();
    }

    @Test
    void testNoHairpinsOnLineReturnsFalse() {
        var line = detachedLine();
        assertThat(line.isInsideHairpin(0))
            .as("a line with no hairpins must never report an index as inside")
            .isFalse();
    }

    @Test
    void testTwoElementHairpinHasNoInterior() {
        var line = lineWithNotes(2);
        addCrescendo(line, 0, 1);

        assertThat(line.isInsideHairpin(0))
            .as("anchor of a two-element hairpin is a bound, not the interior")
            .isFalse();
        assertThat(line.isInsideHairpin(1))
            .as("end of a two-element hairpin is a bound, not the interior")
            .isFalse();
    }

    @Test
    void testOffEdgeAnchorDoesNotExemptElementZero() {
        var endBound = new SpanBound.At(3);

        assertThat(Span.strictlyContaining(0).test(SpanBound.BEFORE_LINE, endBound))
            .as("an off-edge anchor has no position on this line, so it cannot exempt element 0")
            .isTrue();
        assertThat(Span.strictlyContaining(3).test(SpanBound.BEFORE_LINE, endBound))
            .as("the end bound itself is a bound, not the interior")
            .isFalse();
    }

    @Test
    void testOffEdgeEndDoesNotExemptLastElement() {
        var anchorBound = new SpanBound.At(0);

        assertThat(Span.strictlyContaining(3).test(anchorBound, SpanBound.AFTER_LINE))
            .as("an off-edge end has no position on this line, so it cannot exempt the last element")
            .isTrue();
        assertThat(Span.strictlyContaining(0).test(anchorBound, SpanBound.AFTER_LINE))
            .as("the anchor bound itself is a bound, not the interior")
            .isFalse();
    }

    @Test
    void testSpanCollapsedOntoOneElementHasNoInterior() {
        // Both bounds on the queried element at once. The inclusive predicate this one builds on
        // says yes, so only the two exclusions can make it say no — and each alone would be
        // enough, which is why the case is worth pinning: dropping either exclusion leaves every
        // other test in this file green.
        var collapsedIndex = 2;

        assertThat(Span.strictlyContaining(collapsedIndex)
            .test(new SpanBound.At(collapsedIndex), new SpanBound.At(collapsedIndex)))
            .as("a span whose anchor and end are the same element has no interior at all")
            .isFalse();
    }
}
