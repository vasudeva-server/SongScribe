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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElement;
import songscribe.engraving.SMuFLConstants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Which element each end of a volta bracket lands on, and which of that element's two volta
 * anchors it uses.
 *
 * <p>Every column here is the same width and the same distance from the one before it, so a
 * bracket end's X says which column it came from. An expectation is therefore written as a
 * column index paired with the anchor the engraving rule names — never by running the
 * production formula a second time. The anchor offsets themselves are pinned independently
 * by {@code BarAppearanceTest}; what is under test is the selection.
 *
 * <p>{@code testDeclaredBarTypesKeepTheirKnownGeometry} is the exception, and deliberately so:
 * it states four X values as literals, so that a change in what a bar is drawn from — a stroke
 * added, a separation widened — shows up as a number that moved rather than as two formulas
 * agreeing with each other.
 */
class EndingBracketGeometryTest extends UnitTest {

    /** Uniform column geometry: every stub column has the same ink extents and the same pitch. */
    private static final double COLUMN_PITCH_SS = 2.0;

    /** Left of the glyph origin, so a bracket reading the left edge cannot pass by reading X. */
    private static final double COLUMN_LEFT_EXTENT_SS = -0.5;

    private static final double COLUMN_RIGHT_EXTENT_SS = 1.0;

    private static final double TOLERANCE_SS = 1.0e-9;

    // The standard fixture line: opening, anchor note, note, split, note, note, closing.
    private static final int OPENING_INDEX = 0;

    private static final int ANCHOR_INDEX = 1;

    private static final int SPLIT_INDEX = 3;

    private static final int END_INDEX = 6;

    // The run-on fixture: the same line again, but with the ending ending on the note before the
    // last element, so the second bracket has a bar ahead of it to run on to.
    private static final int RUN_ON_END_INDEX = END_INDEX - 1;

    private static final int RUN_ON_BAR_INDEX = END_INDEX;

    // The fixture for an ending whose anchor is itself a repeat:
    // note, note, anchor repeat, note, note, interior repeat, note, note, closing barline.
    private static final int REPEAT_ANCHOR_INDEX = 2;

    private static final int INTERIOR_REPEAT_INDEX = 5;

    private static final int REPEAT_ANCHOR_END_INDEX = 8;

    private static final int FIRST_COLUMN_INDEX = 0;

    /**
     * The four X values a {@code DOUBLE_BARLINE} / {@code REPEAT_LEFT_RIGHT} /
     * {@code REPEAT_RIGHT} ending has on the standard fixture, hand-derived from the strokes
     * each of those three bars is drawn from and the pitch of the stub columns.
     */
    private static final double KNOWN_FIRST_X1_SS = 0.585;

    private static final double KNOWN_FIRST_X2_SS = 6.795;

    private static final double KNOWN_SECOND_X1_SS = 8.185;

    private static final double KNOWN_SECOND_X2_SS = 12.795;

    /** What the bracket may open on. Excludes {@code FINAL_DOUBLE_BARLINE}: a line accepts one
     * only as the last element of the last line, so nothing can ever follow it. */
    private static final List<ElementType> OPENING_TYPES = List.of(
        ElementType.SINGLE_BARLINE,
        ElementType.DOUBLE_BARLINE,
        ElementType.REPEAT_LEFT,
        ElementType.REPEAT_RIGHT,
        ElementType.REPEAT_LEFT_RIGHT,
        ElementType.CROTCHET);

    /** What may split the two brackets. Only a right-facing repeat closes a repeated section. */
    private static final List<ElementType> SPLIT_TYPES = List.of(
        ElementType.REPEAT_RIGHT,
        ElementType.REPEAT_LEFT_RIGHT);

    /** Every bar and repeat: what a bracket may close on, and what it may run on to. */
    private static final List<ElementType> BAR_TYPES = List.of(
        ElementType.SINGLE_BARLINE,
        ElementType.DOUBLE_BARLINE,
        ElementType.FINAL_DOUBLE_BARLINE,
        ElementType.REPEAT_LEFT,
        ElementType.REPEAT_RIGHT,
        ElementType.REPEAT_LEFT_RIGHT);

    /** What the bracket may close on: any bar or repeat, or a note. */
    private static final List<ElementType> CLOSING_TYPES =
        Stream.concat(BAR_TYPES.stream(), Stream.of(ElementType.CROTCHET)).toList();

    /** The end element of a run-on row is a note: that is what makes the bracket run on. */
    private static final ElementType RUN_ON_END_TYPE = ElementType.CROTCHET;

    /** A run-on row varies only what it runs on to, so it opens on a plain barline throughout. */
    private static final ElementType RUN_ON_OPENING_TYPE = ElementType.SINGLE_BARLINE;

    /** One way a bracket can be aligned: what it opens on, what splits it, what it closes on. */
    private record Alignment(ElementType opening, ElementType split, ElementType closing) {

        @Override
        public String toString() {
            return "open=%s split=%s close=%s".formatted(opening, split, closing);
        }
    }

    /** One way a bracket can run on: what splits it, and the bar it finds beyond its end note. */
    private record RunOn(ElementType split, ElementType following) {

        @Override
        public String toString() {
            return "split=%s following=%s".formatted(split, following);
        }
    }

    /** A line carrying one ending, with a stub column per element. */
    private record Fixture(Line line, Ending ending, List<ElementColumn> columns) {

        List<Ending.BracketRange> bracketRanges() {
            return EndingBracketGeometry.computeBracketRanges(
                ending, line, element -> columns.get(line.getElementIndex(element)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reachableAlignments")
    void testEachReachableAlignmentAnchorsToTheRuleElement(Alignment alignment) {
        var opening = alignment.opening();
        var split = alignment.split();
        var closing = alignment.closing();
        var ranges = standardFixture(opening, split, closing).bracketRanges();

        assertThat(ranges).as("an ending always has both of its brackets").hasSize(2);

        var firstBracket = ranges.getFirst();
        var secondBracket = ranges.getLast();

        assertThat(firstBracket.number()).as("the first bracket is numbered 1").isEqualTo(1);
        assertThat(secondBracket.number()).as("the second bracket is numbered 2").isEqualTo(2);

        double expectedFirstX1Ss;

        if (isBarOrRepeat(opening)) {
            expectedFirstX1Ss = openingAnchorXSs(OPENING_INDEX, opening);
        }
        else {
            expectedFirstX1Ss = noteOpeningXSs(ANCHOR_INDEX);
        }

        assertThat(firstBracket.x1Ss())
            .as("the first bracket opens on the element in front of the anchor when that is a "
                + "bar or a repeat, otherwise on the anchor note itself")
            .isCloseTo(expectedFirstX1Ss, within(TOLERANCE_SS));

        assertThat(firstBracket.x2Ss())
            .as("the first bracket closes on the split repeat")
            .isCloseTo(closingAnchorXSs(SPLIT_INDEX, split), within(TOLERANCE_SS));

        assertThat(secondBracket.x1Ss())
            .as("the second bracket opens on the same split repeat, from its other side")
            .isCloseTo(openingAnchorXSs(SPLIT_INDEX, split), within(TOLERANCE_SS));

        double expectedSecondX2Ss;

        if (isBarOrRepeat(closing)) {
            expectedSecondX2Ss = closingAnchorXSs(END_INDEX, closing);
        }
        else {
            expectedSecondX2Ss = noteClosingXSs(END_INDEX);
        }

        assertThat(secondBracket.x2Ss())
            .as("the second bracket closes on the end element")
            .isCloseTo(expectedSecondX2Ss, within(TOLERANCE_SS));

        assertThat(secondBracket.hasClosingStroke())
            .as("only a bar that ends a repeated section is struck through at the bracket's end")
            .isEqualTo(closesRepeatedSection(closing));

        assertThat(firstBracket.hasClosingStroke())
            .as("the first bracket is always struck through, since the repeat loops back there")
            .isTrue();
    }

    /**
     * An ending whose end element is a note closes its second bracket on the bar or repeat that
     * follows the note, not on the note. This is the ordinary case in real music: a volta's
     * material ends on a note and a barline stands after it.
     *
     * <p>Only the selection is crossed here. Once the following bar is picked, the arithmetic is
     * the one {@code testEachReachableAlignmentAnchorsToTheRuleElement} already pins for an
     * ending that ends on that bar directly, so the rows vary what is picked — which bar stands
     * beyond the note, and which repeat splits the two brackets — and hold the opening constant.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("runOnAlignments")
    void testABracketClosingOnANoteRunsOnToTheFollowingBar(RunOn runOn) {
        var following = runOn.following();
        var ranges = runOnFixture(runOn.split(), following).bracketRanges();

        assertThat(ranges).as("an ending always has both of its brackets").hasSize(2);

        var secondBracket = ranges.getLast();

        assertThat(secondBracket.x2Ss())
            .as("the second bracket passes over its end note and closes on the bar beyond it")
            .isNotCloseTo(noteClosingXSs(RUN_ON_END_INDEX), within(TOLERANCE_SS))
            .isCloseTo(closingAnchorXSs(RUN_ON_BAR_INDEX, following), within(TOLERANCE_SS));

        assertThat(secondBracket.hasClosingStroke())
            .as("the bar run on to decides the closing stroke, as it would were it the end element")
            .isEqualTo(closesRepeatedSection(following));
    }

    /**
     * An ending may be anchored on a repeat, and then that repeat opens the first bracket while a
     * <em>later</em> repeat splits the two. Taking the anchor for the split would leave the first
     * bracket unbuilt and the second one starting at the line's left edge.
     */
    @Test
    void testAnAnchorThatIsItselfARepeatSplitsOnTheInteriorRepeat() {
        var elements = List.of(
            crotchet(),
            crotchet(),
            ElementType.REPEAT_LEFT_RIGHT.newInstance(),
            crotchet(),
            crotchet(),
            ElementType.REPEAT_RIGHT.newInstance(),
            crotchet(),
            crotchet(),
            ElementType.SINGLE_BARLINE.newInstance());
        var ranges = fixtureOf(elements, REPEAT_ANCHOR_INDEX, REPEAT_ANCHOR_END_INDEX)
            .bracketRanges();

        assertThat(ranges).as("both brackets are built, the anchor repeat notwithstanding").hasSize(2);

        var firstBracket = ranges.getFirst();
        var secondBracket = ranges.getLast();

        assertThat(firstBracket.x1Ss())
            .as("the first bracket opens on the anchor's own repeat, not at the line's left edge")
            .isNotCloseTo(0.0, within(TOLERANCE_SS))
            .isNotCloseTo(noteOpeningXSs(FIRST_COLUMN_INDEX), within(TOLERANCE_SS))
            .isCloseTo(
                openingAnchorXSs(REPEAT_ANCHOR_INDEX, ElementType.REPEAT_LEFT_RIGHT),
                within(TOLERANCE_SS));

        assertThat(firstBracket.x2Ss())
            .as("the first bracket closes on the interior repeat, not on the anchor")
            .isCloseTo(
                closingAnchorXSs(INTERIOR_REPEAT_INDEX, ElementType.REPEAT_RIGHT),
                within(TOLERANCE_SS));

        assertThat(secondBracket.x1Ss())
            .as("the second bracket opens on that same interior repeat")
            .isCloseTo(
                openingAnchorXSs(INTERIOR_REPEAT_INDEX, ElementType.REPEAT_RIGHT),
                within(TOLERANCE_SS));
    }

    /**
     * A known-correct corpus for one ending: a double barline opening it, a left/right repeat
     * splitting it, a right repeat closing it, over columns two staff spaces apart.
     */
    @Test
    void testDeclaredBarTypesKeepTheirKnownGeometry() {
        var ranges = standardFixture(
            ElementType.DOUBLE_BARLINE,
            ElementType.REPEAT_LEFT_RIGHT,
            ElementType.REPEAT_RIGHT).bracketRanges();

        assertThat(ranges).hasSize(2);

        var firstBracket = ranges.getFirst();
        var secondBracket = ranges.getLast();

        assertThat(firstBracket.x1Ss())
            .as("centered on the double barline's second thin stroke")
            .isCloseTo(KNOWN_FIRST_X1_SS, within(TOLERANCE_SS));

        assertThat(firstBracket.x2Ss())
            .as("half a thin barline into the split repeat's first drawn line, its leading dots "
                + "passed over")
            .isCloseTo(KNOWN_FIRST_X2_SS, within(TOLERANCE_SS));

        assertThat(secondBracket.x1Ss())
            .as("centered on the split repeat's last drawn line, its trailing dots passed over")
            .isCloseTo(KNOWN_SECOND_X1_SS, within(TOLERANCE_SS));

        assertThat(secondBracket.x2Ss())
            .as("half a thin barline into the closing repeat's first drawn line")
            .isCloseTo(KNOWN_SECOND_X2_SS, within(TOLERANCE_SS));

        assertThat(secondBracket.hasClosingStroke())
            .as("a right repeat ends the repeated section, so the bracket is struck through")
            .isTrue();
    }

    /**
     * The cross product of the three axes, less the pairs {@link Ending#isValidEnd} refuses: a
     * split and an end state the same repeat structure from its two sides, so a row the document
     * model cannot hold is not a row this test pins.
     */
    static Stream<Alignment> reachableAlignments() {
        return OPENING_TYPES.stream().flatMap(opening ->
            SPLIT_TYPES.stream().flatMap(split ->
                CLOSING_TYPES.stream()
                    .filter(closing -> Ending.isValidEnd(split, closing))
                    .map(closing -> new Alignment(opening, split, closing))));
    }

    /**
     * The rows of {@link #testABracketClosingOnANoteRunsOnToTheFollowingBar}: every bar a note's
     * bracket may run on to, against every repeat that may split the brackets. The end element is
     * a note, which {@link Ending#isValidEnd} admits under either split, so no row is refused —
     * the filter is applied all the same, so both tests admit their rows the same way.
     */
    static Stream<RunOn> runOnAlignments() {
        return BAR_TYPES.stream().flatMap(following ->
            SPLIT_TYPES.stream()
                .filter(split -> Ending.isValidEnd(split, RUN_ON_END_TYPE))
                .map(split -> new RunOn(split, following)));
    }

    /**
     * The standard fixture line: the opening element, the anchor note, a note, the split repeat,
     * two notes, and the closing element. A closing note is followed by another note, so the
     * bracket has nothing beyond the end element to run on to.
     */
    private static Fixture standardFixture(
        ElementType opening, ElementType split, ElementType closing
    ) {
        return fixtureOf(standardElements(opening, split, closing), ANCHOR_INDEX, END_INDEX);
    }

    /**
     * The standard fixture line again, with {@code following} as its last element and the ending
     * ended on the note before it, so the second bracket has a bar ahead of it to run on to. The
     * opening is held at a single barline: what opens the bracket has no bearing on where the
     * other end of it lands.
     */
    private static Fixture runOnFixture(ElementType split, ElementType following) {
        return fixtureOf(
            standardElements(RUN_ON_OPENING_TYPE, split, following),
            ANCHOR_INDEX,
            RUN_ON_END_INDEX);
    }

    /**
     * The elements of the standard fixture line. A closing note takes another note behind it, so
     * that the bracket cannot run on past the element the ending ends on.
     */
    private static List<StaffElement> standardElements(
        ElementType opening, ElementType split, ElementType closing
    ) {
        var elements = new ArrayList<StaffElement>();
        elements.add(opening.newInstance());
        elements.add(crotchet());
        elements.add(crotchet());
        elements.add(split.newInstance());
        elements.add(crotchet());
        elements.add(crotchet());
        elements.add(closing.newInstance());

        if (!isBarOrRepeat(closing)) {
            elements.add(crotchet());
        }

        return elements;
    }

    /** A one-line song of {@code elements}, carrying one ending, with a stub column per element. */
    private static Fixture fixtureOf(List<StaffElement> elements, int anchorIndex, int endIndex) {
        var ending = new Ending(elements.get(anchorIndex), elements.get(endIndex));
        var song = SongFactory.buildSong(line -> {
            for (var element : elements) {
                line.addElement(element);
            }

            line.addSpan(ending);
        });
        var columns = new ArrayList<ElementColumn>(elements.size());

        for (var index = 0; index < elements.size(); index++) {
            columns.add(column(index, elements.get(index)));
        }

        return new Fixture(song.getLine(0), ending, List.copyOf(columns));
    }

    private static ElementColumn column(int index, StaffElement element) {
        var column = new ElementColumn(
            element,
            List.of(),
            COLUMN_LEFT_EXTENT_SS,
            COLUMN_RIGHT_EXTENT_SS,
            COLUMN_RIGHT_EXTENT_SS,
            0.0,
            0.0,
            null,
            0.0,
            false);

        column.setXSs(columnXSs(index));

        return column;
    }

    private static double columnXSs(int index) {
        return index * COLUMN_PITCH_SS;
    }

    /** Where a bracket opening on the bar in column {@code index} puts its left stroke. */
    private static double openingAnchorXSs(int index, ElementType type) {
        return columnXSs(index) + type.voltaOpeningXOffsetSs();
    }

    /** Where a bracket closing on the bar in column {@code index} puts its right end. */
    private static double closingAnchorXSs(int index, ElementType type) {
        return columnXSs(index) + type.voltaClosingXOffsetSs();
    }

    /** Where a bracket opening on the note in column {@code index} puts its left stroke. */
    private static double noteOpeningXSs(int index) {
        return columnXSs(index) + COLUMN_LEFT_EXTENT_SS - NoteGeometry.ACCIDENTAL_PADDING_SS;
    }

    /** Where a bracket closing on the note in column {@code index} puts its right end. */
    private static double noteClosingXSs(int index) {
        return columnXSs(index) + COLUMN_RIGHT_EXTENT_SS + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS;
    }

    private static boolean isBarOrRepeat(ElementType type) {
        return type.isBarLine() || type.isRepeat();
    }

    /** The bars that end a repeated section, and so take a closing stroke on the bracket. */
    private static boolean closesRepeatedSection(ElementType type) {
        return type == ElementType.REPEAT_RIGHT
            || type == ElementType.REPEAT_LEFT_RIGHT
            || type == ElementType.FINAL_DOUBLE_BARLINE;
    }
}
