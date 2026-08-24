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
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Key;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.StaffHeaderMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The geometry {@link CautionaryKeySignature} promises: how much room a line reserves for a
 * cautionary at its end, and where the parts of it then land.
 * <p>
 * The two are asserted against each other rather than separately, because the failure this type
 * exists to prevent is the two drifting apart — a reservation the drawn glyphs do not fill, or
 * glyphs that spill past what was reserved.
 */
class CautionaryKeySignatureTest extends UnitTest {

    /** The song's line rest for every case here — the gap ahead of a barline the cautionary draws. */
    private static final double LINE_REST_SS = 2.5;

    /** A staff width wide enough that no case's reservation reaches back past the line's start. */
    private static final double LINE_WIDTH_SS = 100.0;

    /**
     * Where an overflowing line's content ends: past {@link #LINE_WIDTH_SS}, which is what makes
     * the line overflow and what pinning the cautionary to the staff width would draw over.
     */
    private static final double OVERFLOWING_CONTENT_RIGHT_EDGE_SS = 120.0;

    /** The ink width of the one column the overflowing fixture holds. */
    private static final double COLUMN_INK_WIDTH_SS = 1.0;

    /** Staff spaces within which two computed coordinates count as the same. */
    private static final double TOLERANCE_SS = 1e-9;

    /** The key a line leaves off in, for every case that draws a cautionary. */
    private static final Key PREVIOUS_KEY = Key.NO_ACCIDENTALS;

    /** The key the next line begins in, chosen to differ from {@link #PREVIOUS_KEY}. */
    private static final Key NEXT_KEY = Key.TWO_SHARPS;

    /**
     * One line ending, and what the cautionary after it owes.
     *
     * @param description  the case, as the test's display name
     * @param lastElement  the line's last element, or null when the line holds none
     * @param drawsBarLine whether the cautionary is expected to draw a barline of its own
     * @param leadInSs     the expected span from that element's ink to the first accidental
     */
    private record LineEndingCase(
        String description,
        @Nullable StaffElement lastElement,
        boolean drawsBarLine,
        double leadInSs
    ) {}

    /** One pair of keys that warns of nothing, so no cautionary is drawn. */
    private record NoCautionaryCase(String description, LineKeys keys) {}

    /**
     * A solved layout whose content overflows the staff, ending at
     * {@link #OVERFLOWING_CONTENT_RIGHT_EDGE_SS}. One column carries that edge, since the content
     * edge is the rightmost column edge rather than a value a caller states.
     */
    private static LayoutResult overflowingLayout() {
        var element = crotchet();
        var column = new ElementColumn(
            element, List.of(), 0.0, COLUMN_INK_WIDTH_SS, COLUMN_INK_WIDTH_SS, 0.0, 0.0, null, 0.0, false);

        column.setXSs(OVERFLOWING_CONTENT_RIGHT_EDGE_SS - COLUMN_INK_WIDTH_SS);

        return LayoutResult.builder()
            .setOverflowsStaffWidth(true)
            .putElementColumn(element, column)
            .build();
    }

    static Stream<LineEndingCase> lineEndingCases() {
        var drawnLeadInSs = LINE_REST_SS
            + LineThickness.THIN_BARLINE_SS
            + StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS;

        return Stream.of(
            new LineEndingCase("a note owes the cautionary its own barline",
                crotchet(), true, drawnLeadInSs),
            new LineEndingCase("an empty line owes the cautionary its own barline",
                null, true, drawnLeadInSs),
            new LineEndingCase("a single barline is the one the cautionary stands behind",
                singleBarline(), false, StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS
            ),
            new LineEndingCase("a final double barline is one the cautionary stands behind",
                finalDoubleBarline(), false, StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS
            ),
            new LineEndingCase("a repeat is one the cautionary stands behind",
                repeatRight(), false, StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS
            ));
    }

    static Stream<NoCautionaryCase> noCautionaryCases() {
        return Stream.of(
            new NoCautionaryCase("the line has no line after it",
                new LineKeys(PREVIOUS_KEY, PREVIOUS_KEY, null)),
            new NoCautionaryCase("the next line begins in the key this one leaves off in",
                new LineKeys(PREVIOUS_KEY, PREVIOUS_KEY, PREVIOUS_KEY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lineEndingCases")
    void testReservationSpansTheLeadInTheAccidentalsAndTheTrailingPadding(LineEndingCase testCase) {
        var cautionary = cautionaryAfter(testCase.lastElement());

        assertThat(cautionary).isNotNull();
        assertThat(cautionary.drawsBarLine()).isEqualTo(testCase.drawsBarLine());
        assertThat(cautionary.reservationSs()).isCloseTo(
            testCase.leadInSs()
                + cautionary.accidentalsWidthSs()
                + StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS,
            within(TOLERANCE_SS));
    }

    /**
     * The reservation and the placement are two halves of one promise, so this reads the line's
     * last element back out of the reservation — the solver lands its ink at
     * {@code lineWidth - reservationSs()} — and measures the drawn parts from there.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("lineEndingCases")
    void testPlacementLeavesThePaddingEitherSideOfTheAccidentals(LineEndingCase testCase) {
        var cautionary = cautionaryAfter(testCase.lastElement());

        assertThat(cautionary).isNotNull();

        var placement = cautionary.placeIn(LayoutResult.builder().build(), LINE_WIDTH_SS);
        var accidentalsXSs = placement.accidentalsXSs();
        var lastElementInkEndSs = LINE_WIDTH_SS - cautionary.reservationSs();

        assertThat(accidentalsXSs - lastElementInkEndSs)
            .isCloseTo(testCase.leadInSs(), within(TOLERANCE_SS));
        assertThat(accidentalsXSs + cautionary.accidentalsWidthSs())
            .isCloseTo(
                LINE_WIDTH_SS - StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS,
                within(TOLERANCE_SS));

        if (testCase.drawsBarLine()) {
            assertThat(placement)
                .as("a cautionary that draws its own barline places one")
                .isInstanceOf(CautionaryKeySignature.Placement.WithBarLine.class);

            var barLineXSs =
                ((CautionaryKeySignature.Placement.WithBarLine) placement).barLineXSs();

            assertThat(barLineXSs - lastElementInkEndSs)
                .isCloseTo(LINE_REST_SS, within(TOLERANCE_SS));
            assertThat(accidentalsXSs - barLineXSs - LineThickness.THIN_BARLINE_SS)
                .isCloseTo(StaffHeaderMetrics.KEY_SIGNATURE_PADDING_SS, within(TOLERANCE_SS));
        } else {
            assertThat(placement)
                .as("a cautionary standing behind the line's own barline places none of its own")
                .isInstanceOf(CautionaryKeySignature.Placement.AccidentalsOnly.class);
        }
    }

    /**
     * On a line whose content already overflows the staff, the margin the other placement pins to
     * is behind the last element, so the cautionary starts one lead-in past the rightmost element
     * edge and extends the overflow instead.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("lineEndingCases")
    void testPlacementOnAnOverflowingLineStartsOneLeadInPastTheContent(LineEndingCase testCase) {
        var cautionary = cautionaryAfter(testCase.lastElement());

        assertThat(cautionary).isNotNull();

        var accidentalsXSs =
            cautionary.placeIn(overflowingLayout(), LINE_WIDTH_SS).accidentalsXSs();

        assertThat(accidentalsXSs - OVERFLOWING_CONTENT_RIGHT_EDGE_SS)
            .as("the run starts one lead-in past the content rather than being pinned to the staff")
            .isCloseTo(testCase.leadInSs(), within(TOLERANCE_SS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noCautionaryCases")
    void testNoCautionaryWhereTheNextLineWarnsOfNothing(NoCautionaryCase testCase) {
        assertThat(CautionaryKeySignature.of(testCase.keys(), crotchet(), LINE_REST_SS)).isNull();
    }

    private static @Nullable CautionaryKeySignature cautionaryAfter(
        @Nullable StaffElement lastElement
    ) {
        return CautionaryKeySignature.of(
            new LineKeys(PREVIOUS_KEY, PREVIOUS_KEY, NEXT_KEY),
            lastElement,
            LINE_REST_SS);
    }
}
