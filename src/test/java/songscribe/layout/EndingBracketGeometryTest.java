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
import songscribe.dom.BarAppearance;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.engraving.EngravingConstants;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.StaffElementFactory.bar;
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
 */
class EndingBracketGeometryTest extends UnitTest {

    /** Uniform column geometry: every stub column has the same ink extents and the same pitch. */
    private static final double COLUMN_PITCH_SS = 2.0;

    /** Left of the glyph origin, so a bracket reading the left edge cannot pass by reading X. */
    private static final double COLUMN_LEFT_EXTENT_SS = -0.5;

    private static final double COLUMN_RIGHT_EXTENT_SS = 1.0;

    private static final double TOLERANCE_SS = 1.0e-9;

    /** Opening, anchor note, note, split, note, note, closing. */
    private static final class StandardLine {
        static final int OPENING_INDEX = 0;

        static final int ANCHOR_INDEX = 1;

        static final int SPLIT_INDEX = 3;

        static final int END_INDEX = 6;
    }

    /**
     * The standard line with the ending ending on the note before the last element, so the
     * second bracket has a bar ahead of it to run on to.
     */
    private static final class RunOnLine {
        static final int END_INDEX = StandardLine.END_INDEX - 1;

        static final int BAR_INDEX = StandardLine.END_INDEX;
    }

    /** What splits the two brackets: an ending's split is always a right repeat. */
    private static final ElementType SPLIT_TYPE = ElementType.REPEAT_RIGHT;

    /** Every bar and repeat: what a bracket may run on to. */
    private static final List<ElementType> BAR_TYPES = Stream.of(ElementType.values())
        .filter(ElementType::isBar)
        .toList();

    /** The bars the engraving recognizes; a new bar type fails the coverage test until listed. */
    private static final List<ElementType> EXPECTED_BAR_TYPES = List.of(
        ElementType.SINGLE_BARLINE,
        ElementType.DOUBLE_BARLINE,
        ElementType.FINAL_DOUBLE_BARLINE,
        ElementType.REPEAT_LEFT,
        ElementType.REPEAT_RIGHT,
        ElementType.REPEAT_LEFT_RIGHT);

    /** A run-on row varies only what it runs on to, so it opens on a plain barline throughout. */
    private static final ElementType RUN_ON_OPENING_TYPE = ElementType.SINGLE_BARLINE;

    /** The stroke width of a bracket's path; its ink reaches half of this past the path. */
    private static final double STROKE_SS = EngravingConstants.VOLTA_BRACKET_SS;

    private static final double REGION_X_BASE_SS = 5.0;

    private static final double REGION_BRACKET_X1_SS = 10.0;

    private static final double REGION_BRACKET_LENGTH_SS = 6.0;

    /** The regions of a bracket without a closing stroke: arm, left leg, label. */
    private static final int OPEN_REGION_COUNT = 3;

    /** The regions of a bracket with a closing stroke: arm, both legs, label. */
    private static final int CLOSED_REGION_COUNT = 4;

    private static final int ARM_REGION = 0;

    /**
     * Checks where a bracket end lies against the column of the element it lands on, by the
     * engraving rule for that kind of element, stated as a difference from the column.
     */
    @FunctionalInterface
    private interface EndRule {

        void verify(double xSs, ElementColumn column, ElementType type);
    }

    /** A bar opens a bracket at its opening anchor, measured from the bar's column X. */
    private static final EndRule OPENS_ON_BAR = (xSs, column, type) ->
        assertThat(xSs - column.getXSs())
            .as("a bracket opening on a %s sits at the bar's opening anchor", type)
            .isCloseTo(barOf(type).openingAnchorSs(), within(TOLERANCE_SS));

    /** A bar closes a bracket at its closing anchor, measured from the bar's column X. */
    private static final EndRule CLOSES_ON_BAR = (xSs, column, type) ->
        assertThat(xSs - column.getXSs())
            .as("a bracket closing on a %s sits at the bar's closing anchor", type)
            .isCloseTo(barOf(type).closingAnchorSs(), within(TOLERANCE_SS));

    /** A note opens a bracket one accidental padding left of the column's left edge. */
    private static final EndRule OPENS_ON_NOTE = (xSs, column, type) ->
        assertThat(column.getLeftEdgeXSs() - xSs)
            .as("a bracket opening on a note sits one accidental padding left of its left edge")
            .isCloseTo(NoteGeometry.ACCIDENTAL_PADDING_SS, within(TOLERANCE_SS));

    /** A note closes a bracket one augmentation-dot advance right of the column's right edge. */
    private static final EndRule CLOSES_ON_NOTE = (xSs, column, type) ->
        assertThat(xSs - column.getRightEdgeXSs())
            .as("a bracket closing on a note sits one augmentation-dot advance right of its right edge")
            .isCloseTo(SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT), within(TOLERANCE_SS));

    /**
     * What may stand in front of the anchor note ({@link Ending} refuses a note anchor behind any
     * other bar), the column the bracket opens on, and the rule for it: a single barline pulls the
     * bracket back to itself, a note is opened on directly.
     */
    private record OpeningEnd(ElementType type, int columnIndex, EndRule rule) {
    }

    /**
     * What the bracket may close on, the rule for it, and whether the bracket is struck through
     * there. {@code EndingTest} states the accepted set; a right repeat and a left-right repeat
     * are not in it.
     */
    private record ClosingEnd(ElementType type, EndRule rule, boolean struckThrough) {
    }

    /** One way a bracket can be aligned: what it opens on and what it closes on. */
    private record Alignment(OpeningEnd opening, ClosingEnd closing) {

        @Override
        public String toString() {
            return "open=%s close=%s".formatted(opening.type(), closing.type());
        }
    }

    /** One way a bracket can be collided with: whether it has a closing stroke. */
    private record RegionCase(String description, boolean closed, int expectedRegionCount) {

        @Override
        public String toString() {
            return description;
        }
    }

    /** One way a bracket can run on: the bar it finds beyond its end note. */
    private record RunOn(ElementType following) {

        @Override
        public String toString() {
            return "following=%s".formatted(following);
        }
    }

    /** A line carrying one ending, with a stub column per element. */
    private record Fixture(Line line, Ending ending, List<ElementColumn> columns) {

        BracketRange.Pair bracketRanges() {
            return EndingBracketGeometry.computeBracketRanges(
                ending, line, element -> columns.get(line.getElementIndex(element)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reachableAlignments")
    void testEachReachableAlignmentAnchorsToTheRuleElement(Alignment alignment) {
        var opening = alignment.opening();
        var closing = alignment.closing();
        var fixture = standardFixture(opening.type(), SPLIT_TYPE, closing.type());
        var ranges = fixture.bracketRanges();
        var columns = fixture.columns();

        var firstBracket = ranges.first();
        var secondBracket = ranges.second();

        opening.rule().verify(firstBracket.x1Ss(), columns.get(opening.columnIndex()), opening.type());
        CLOSES_ON_BAR.verify(firstBracket.x2Ss(), columns.get(StandardLine.SPLIT_INDEX), SPLIT_TYPE);
        OPENS_ON_BAR.verify(secondBracket.x1Ss(), columns.get(StandardLine.SPLIT_INDEX), SPLIT_TYPE);
        closing.rule().verify(secondBracket.x2Ss(), columns.get(StandardLine.END_INDEX), closing.type());

        assertThat(secondBracket.hasClosingStroke())
            .as("a bracket ending on a bar other than a single barline is struck through there; "
                + "one ending on a single barline, or on a note with nothing after it, stays open")
            .isEqualTo(closing.struckThrough());

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
     * beyond the note — and hold the opening and the split constant.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("runOnAlignments")
    void testABracketClosingOnANoteRunsOnToTheFollowingBar(RunOn runOn) {
        var following = runOn.following();
        var fixture = runOnFixture(SPLIT_TYPE, following);
        var ranges = fixture.bracketRanges();
        var columns = fixture.columns();

        var secondBracket = ranges.second();

        assertThat(secondBracket.x2Ss() - columns.get(RunOnLine.END_INDEX).getRightEdgeXSs())
            .as("the second bracket passes over its end note")
            .isNotCloseTo(SMuFLMetadata.advanceWidthSs(SMuFLGlyph.AUGMENTATION_DOT), within(TOLERANCE_SS));
        CLOSES_ON_BAR.verify(secondBracket.x2Ss(), columns.get(RunOnLine.BAR_INDEX), following);

        assertThat(secondBracket.hasClosingStroke())
            .as("the bar run on to closes the bracket unless it is a single barline")
            .isEqualTo(following != ElementType.SINGLE_BARLINE);
    }

    @Test
    void testBarTypesCoverEveryBarAndRepeat() {
        assertThat(BAR_TYPES).containsExactlyInAnyOrderElementsOf(EXPECTED_BAR_TYPES);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("regionCases")
    void testCollisionRegionsCoverTheBracketInk(RegionCase regionCase) {
        var bracket = new BracketRange(
            REGION_BRACKET_X1_SS, REGION_BRACKET_X1_SS + REGION_BRACKET_LENGTH_SS, regionCase.closed());

        var regions = EndingBracketGeometry.computeCollisionRegions(
            bracket, BracketRange.Bracket.FIRST, REGION_X_BASE_SS);

        assertThat(regions).hasSize(regionCase.expectedRegionCount());

        var arm = regions.get(ARM_REGION);
        var halfStrokeSs = STROKE_SS / 2;

        assertThat(arm.xOffsetSs())
            .as("the arm reaches half a stroke left of the bracket's left path")
            .isCloseTo(REGION_X_BASE_SS - halfStrokeSs, within(TOLERANCE_SS));
        assertThat(arm.xOffsetSs() + arm.widthSs())
            .as("the arm reaches half a stroke right of the bracket's right path")
            .isCloseTo(REGION_X_BASE_SS + REGION_BRACKET_LENGTH_SS + halfStrokeSs, within(TOLERANCE_SS));
        assertThat(arm.heightSs()).as("the arm is one stroke high").isCloseTo(STROKE_SS, within(TOLERANCE_SS));
    }

    /** An open bracket has no right leg; a closed one does. */
    static Stream<RegionCase> regionCases() {
        return Stream.of(
            new RegionCase("open bracket", false, OPEN_REGION_COUNT),
            new RegionCase("closed bracket", true, CLOSED_REGION_COUNT));
    }

    /** Every opening paired with every closing: the cross product of the two axes. */
    static Stream<Alignment> reachableAlignments() {
        var openings = List.of(
            new OpeningEnd(ElementType.SINGLE_BARLINE, StandardLine.OPENING_INDEX, OPENS_ON_BAR),
            new OpeningEnd(ElementType.CROTCHET, StandardLine.ANCHOR_INDEX, OPENS_ON_NOTE));
        var closings = List.of(
            new ClosingEnd(ElementType.SINGLE_BARLINE, CLOSES_ON_BAR, false),
            new ClosingEnd(ElementType.DOUBLE_BARLINE, CLOSES_ON_BAR, true),
            new ClosingEnd(ElementType.FINAL_DOUBLE_BARLINE, CLOSES_ON_BAR, true),
            new ClosingEnd(ElementType.REPEAT_LEFT, CLOSES_ON_BAR, true),
            new ClosingEnd(ElementType.CROTCHET, CLOSES_ON_NOTE, false));

        return openings.stream().flatMap(opening ->
            closings.stream().map(closing -> new Alignment(opening, closing)));
    }

    /**
     * The rows of {@link #testABracketClosingOnANoteRunsOnToTheFollowingBar}: every bar a note's
     * bracket may run on to. The end element is a note, which {@link Ending#isValidEnd} admits,
     * so no row is refused.
     */
    static Stream<RunOn> runOnAlignments() {
        return BAR_TYPES.stream().map(RunOn::new);
    }

    /**
     * The standard fixture line: the opening element, the anchor note, a note, the split repeat,
     * two notes, and the closing element. A closing note is followed by another note, so the
     * bracket has nothing beyond the end element to run on to.
     */
    private static Fixture standardFixture(
        ElementType opening, ElementType split, ElementType closing
    ) {
        return fixtureOf(standardElements(opening, split, closing), StandardLine.ANCHOR_INDEX, StandardLine.END_INDEX);
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
            StandardLine.ANCHOR_INDEX,
            RunOnLine.END_INDEX);
    }

    /**
     * The elements of the standard fixture line. A closing note takes another note behind it, so
     * that the bracket cannot run on past the element the ending ends on.
     */
    private static List<StaffElement> standardElements(
        ElementType opening, ElementType split, ElementType closing
    ) {
        var elements = new ArrayList<StaffElement>();
        elements.add(elementOf(opening));
        elements.add(crotchet());
        elements.add(crotchet());
        elements.add(elementOf(split));
        elements.add(crotchet());
        elements.add(crotchet());
        elements.add(elementOf(closing));

        if (!closing.isBar()) {
            elements.add(crotchet());
        }

        return elements;
    }

    /** A bar of {@code type}, or a crotchet: the one note type the fixture's rows use. */
    private static StaffElement elementOf(ElementType type) {
        if (type.isBar()) {
            return bar(type);
        }

        if (type == ElementType.CROTCHET) {
            return crotchet();
        }

        throw new IllegalArgumentException("the fixture has no element of type " + type);
    }

    /**
     * A one-line song of {@code elements}, carrying one ending, with a stub column per element.
     * The ending is placed once the line is in the song, because {@link Ending#spanning} reads the
     * song's repeat sections.
     */
    private static Fixture fixtureOf(List<StaffElement> elements, int anchorIndex, int endIndex) {
        var song = SongFactory.buildSong(line -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });
        var line = song.getLine(0);
        var ending = addWithoutTracking(song, () -> endingOver(line, new ElementRange(anchorIndex, endIndex)));
        var columns = new ArrayList<ElementColumn>(elements.size());

        for (var index = 0; index < elements.size(); index++) {
            columns.add(column(index, elements.get(index)));
        }

        return new Fixture(line, ending, List.copyOf(columns));
    }

    private static ElementColumn column(int index, StaffElement element) {
        var extents = new ElementColumn.Extents(
            COLUMN_LEFT_EXTENT_SS, COLUMN_RIGHT_EXTENT_SS, COLUMN_RIGHT_EXTENT_SS, 0.0, 0.0);
        var column = new ElementColumn(element, extents, null, null);

        column.setXSs(columnXSs(index));

        return column;
    }

    private static double columnXSs(int index) {
        return index * COLUMN_PITCH_SS;
    }

    private static BarAppearance barOf(ElementType type) {
        return (BarAppearance) type.appearance();
    }
}
