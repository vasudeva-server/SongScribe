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

import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.engraving.BarStroke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link BarAppearance}'s promise that a bar's width and both of its volta bracket anchors are
 * derived from the stroke sequence it is declared with, rather than restated by each reader of it.
 *
 * <p>The expected widths are the room every barline and repeat sign has always been given, so a
 * stroke sequence declared wrong on an {@link ElementType} surfaces here as a column that no longer
 * fits the sign drawn in it. The sequence itself is stated beside the geometry it produces, so a
 * stroke added or reordered shows up as the thing it is rather than only as a width that moved.
 *
 * <p>The rows are checked against the domain the production code defines — every type that answers
 * to {@code isBarLine()} or {@code isRepeat()} — so a bar type added later fails this class rather
 * than passing through it unmeasured.
 */
class BarAppearanceTest extends UnitTest {

    /**
     * Bar thicknesses are products of a base thickness, so a sum of them lands a few
     * floating-point ulps away from the decimal it is written as.
     */
    private static final Offset<Double> TOLERANCE_SS = within(1.0e-9);

    /**
     * One declared bar, with the geometry its stroke sequence must produce, all in staff spaces.
     *
     * @param type                     the element type whose declared appearance is measured
     * @param expectedStrokes          the strokes it is drawn from, left to right
     * @param expectedWidthSs          the width of the whole group
     * @param expectedOpeningAnchorSs  where a volta bracket opening on this bar puts its left arm
     * @param expectedClosingAnchorSs  where a volta bracket closing on this bar ends its
     *                                 horizontal arm
     */
    private record BarGeometryCase(
        ElementType type,
        List<BarStroke> expectedStrokes,
        double expectedWidthSs,
        double expectedOpeningAnchorSs,
        double expectedClosingAnchorSs
    ) {}

    /** Every type the production code treats as a bar: what this class must cover, in full. */
    static Stream<ElementType> barAndRepeatTypes() {
        return Stream.of(ElementType.values())
            .filter(type -> type.isBarLine() || type.isRepeat());
    }

    @Test
    void testEveryBarAndRepeatTypeIsMeasured() {
        assertThat(barGeometryCases().map(BarGeometryCase::type))
            .as("a bar type added later is measured rather than skipped")
            .containsExactlyInAnyOrderElementsOf(barAndRepeatTypes().toList());
    }

    static Stream<BarGeometryCase> barGeometryCases() {
        // Every closing anchor is half a thin barline past the first drawn line's left edge, that
        // being one distance whatever the line is drawn as: REPEAT_LEFT opens with a thick line
        // and takes the same 0.095 as the five that open with a thin one. The two that sit
        // further right are the sequences opening with repeat dots, which a bracket closes past
        // rather than on. The two opening anchors pulled back half a bracket are the sequences
        // ending in a thick line, whose arm aligns on that line's right edge instead of
        // straddling its center. Only REPEAT_RIGHT of those two is reachable: a final double
        // barline ends the last line, so nothing follows it that a bracket could open on. Its
        // opening anchor is derived here all the same, since the record computes one for any
        // sequence.
        return Stream.of(
            new BarGeometryCase(ElementType.SINGLE_BARLINE,
                List.of(BarStroke.THIN), 0.19, 0.095, 0.095),
            new BarGeometryCase(ElementType.DOUBLE_BARLINE,
                List.of(BarStroke.THIN, BarStroke.THIN), 0.68, 0.585, 0.095),
            new BarGeometryCase(ElementType.FINAL_DOUBLE_BARLINE,
                List.of(BarStroke.THIN, BarStroke.THICK), 1.09, 1.0095, 0.095),
            new BarGeometryCase(ElementType.REPEAT_LEFT,
                List.of(BarStroke.THICK, BarStroke.THIN, BarStroke.DOTS), 1.79, 0.995, 0.095),
            new BarGeometryCase(ElementType.REPEAT_RIGHT,
                List.of(BarStroke.DOTS, BarStroke.THIN, BarStroke.THICK), 1.79, 1.7095, 0.795),
            new BarGeometryCase(ElementType.REPEAT_LEFT_RIGHT,
                List.of(BarStroke.DOTS, BarStroke.THIN, BarStroke.THICK, BarStroke.THIN,
                    BarStroke.DOTS),
                2.98, 2.185, 0.795));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("barGeometryCases")
    void testDeclaredBarGeometryFollowsItsStrokeSequence(BarGeometryCase testCase) {
        var type = testCase.type();
        var appearance = type.appearance();

        assertThat(appearance)
            .as("%s is drawn as a bar", type)
            .isInstanceOf(BarAppearance.class);

        var bar = (BarAppearance) appearance;

        assertThat(bar.strokes())
            .as("the strokes %s is drawn from, left to right", type)
            .containsExactlyElementsOf(testCase.expectedStrokes());

        assertThat(bar.widthSs())
            .as("the room %s reserves", type)
            .isCloseTo(testCase.expectedWidthSs(), TOLERANCE_SS);

        assertThat(bar.openingAnchorSs())
            .as("where a volta bracket opening on %s puts its left arm", type)
            .isCloseTo(testCase.expectedOpeningAnchorSs(), TOLERANCE_SS);

        assertThat(bar.closingAnchorSs())
            .as("where a volta bracket closing on %s ends its horizontal arm", type)
            .isCloseTo(testCase.expectedClosingAnchorSs(), TOLERANCE_SS);
    }

    /**
     * A stroke sequence the record refuses, with the construction that offers it.
     *
     * @param description  the case, as the test's display name
     * @param construction the call that must be rejected
     */
    private record RejectedBarCase(String description, ThrowingCallable construction) {
        @Override
        public String toString() {
            return description;
        }
    }

    static Stream<RejectedBarCase> rejectedBarCases() {
        return Stream.of(
            new RejectedBarCase("no strokes at all", () -> BarAppearance.of()),
            new RejectedBarCase("nothing but repeat dots", () -> BarAppearance.of(BarStroke.DOTS)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedBarCases")
    void testABarWithNoDrawnLineIsRejected(RejectedBarCase testCase) {
        assertThatIllegalArgumentException().isThrownBy(testCase.construction());
    }
}
