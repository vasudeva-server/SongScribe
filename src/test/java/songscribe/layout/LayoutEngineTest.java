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
import static org.assertj.core.api.Assertions.within;

import module java.desktop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Attribution;
import songscribe.dom.Beam;
import songscribe.dom.Line;
import songscribe.dom.Tie;
import songscribe.font.DocumentFonts;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.engraving.Staff;
import songscribe.shape.BezierBow;

@SuppressWarnings("DataFlowIssue")
class LayoutEngineTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double TOLERANCE = 0.001;

    /**
     * How far the fit-boundary search climbs before giving up. Comfortably past the note count that
     * cannot fit STAFF_RIGHT_MARGIN_SS even at maximum compression, so the search always finds the
     * boundary; a search that runs out says the flag never turns on, which is itself the failure.
     */
    private static final int BOUNDARY_SEARCH_LIMIT = 100;

    /** Staff position (sp > 0) places the note below the middle staff line → auto stem up. */
    private static final int SP_BELOW_MIDDLE = 2;

    /** Staff position (sp < 0) places the note above the middle staff line → auto stem down. */
    private static final int SP_ABOVE_MIDDLE = -2;

    /** Staff position well above the middle line, used for the grace-note stem test. */
    private static final int SP_ABOVE_MIDDLE_GRACE = -4;

    /** Staff position below the middle line, used for the manual-override test. */
    private static final int SP_BELOW_MIDDLE_MANUAL = 4;

    // Row 18 — beam auto-direction constants
    /** Staff position above the middle line; pair makes min+max < 0 → stemsDown. */
    private static final int SP_BEAM_ABOVE_1 = -2;
    /** Staff position above the middle line; pair makes min+max < 0 → stemsDown. */
    private static final int SP_BEAM_ABOVE_2 = -4;

    /** Staff position below the middle line; pair makes min+max > 0 → stemsUp. */
    private static final int SP_BEAM_BELOW_1 = 2;
    /** Staff position below the middle line; pair makes min+max > 0 → stemsUp. */
    private static final int SP_BEAM_BELOW_2 = 4;

    // Row 20 — slope dampening constant
    /** Extreme below-midline sp; creates a large raw slope that the dampener saturates. */
    private static final int SP_SLOPE_EXTREME_LOW = 16;
    /**
     * Vertical slack (ss) a single quant step may add to a beam edge on top of the damped slope:
     * the quant offsets straddle/sit/inter/hang tile one staff space.
     */
    private static final double QUANT_STEP_SS = 1.0;
    /** {@code dirSign} for a stems-up group, per the BeamScoring sign convention. */
    private static final int STEMS_UP_DIR_SIGN = 1;

    // Row 21 — stem-reduction invariant constants
    /** First note of the non-linear beam contour; also the anchor (min sp → highest pitch). */
    private static final int SP_CONTOUR_FIRST = 2;
    /** Middle note of the non-linear beam contour; positioned below the linear interpolation. */
    private static final int SP_CONTOUR_MIDDLE = 8;
    /** Last note of the non-linear beam contour. */
    private static final int SP_CONTOUR_LAST = 4;

    // Row 47 (#579) — the concave triplet `g8 d'8 e,8`: G4, D5, E4. The group's
    // extremes sum above the middle line, so the stems point up and the middle
    // note (the highest pitch) is the one nearest the beam.
    private static final int SP_579_FIRST = 2;
    private static final int SP_579_MIDDLE = -2;
    private static final int SP_579_LAST = 4;

    // Row 48 — contours whose quanted middle stem lands below the forced-stem floor. Each
    // inner array is one beamed quaver group's staff positions.
    //
    // This is a list rather than a single contour because the regime is narrow: quanting
    // overshoots the cap by only ~0.01 ss, so a change to horizontal spacing can move any
    // given contour back under it — #560 did exactly that to the single contour originally
    // pinned here. The entries come from different contour families (ascending, descending,
    // and wide-leap), so one spacing change is unlikely to disqualify them all. The test
    // asserts the contract on the first entry that still qualifies and fails loudly if none
    // do, which is the signal that the regime itself is gone rather than merely relocated.
    private static final int[][] SP_SUB_FLOOR_CONTOURS = {
        {-6, -4, 7},
        {6, 4, -7},
        {-8, -6, 9},
        {9, -4, -6},
        {10, 9, -10},
        {12, 11, -12},
    };

    // Row 49 — a group straddling the middle line, so it is stemmed down and the lower
    // note's stem is forced against its default direction while the upper note's is not.
    //
    // The pair is chosen so the forced and unforced solutions land on DIFFERENT quants:
    // the assertions below compare the two, and on a pair where both solutions quant to
    // the same beam position the comparison passes no matter what fraction the engine
    // actually used, which would make the pass-through claim vacuous. This pair keeps a
    // full staff space between them. A symmetric pair such as (-5, +5) does NOT — it was
    // the original choice here and went vacuous when horizontal spacing changed (#560),
    // so prefer margin over symmetry if this ever needs re-picking.
    private static final int SP_FORCED_ABOVE = -2;
    private static final int SP_FORCED_BELOW = 1;
    /** One of the two stems of the row-49 group is forced. */
    private static final double ROW_49_FORCED_FRACTION = 0.5;
    /** The forced fraction the row-49 group would have if forcing were never counted. */
    private static final double NO_FORCED_STEMS = 0.0;

    // Row 22 — flat-beam snapping constant
    /** Odd staff position; equal for both notes in the beam so the slope is 0. */
    private static final int SP_FLAT_BEAM = 3;

    // Row 24 — stub direction constants
    /** Staff position shared by both notes in the stub-direction beam test. */
    private static final int SP_STUB_NOTE = 2;

    /** Smallest group with both an edge stem and more than one inner stem to compare. */
    private static final int FRENCH_GROUP_SIZE = 4;

    // Row 24b — grace-note-inside-a-beam constants. The pitches reproduce the reported case:
    // in BeamScoring's Y-up half-space, the heads read 3, 2, 1, 6, 2, and the grace note's 1
    // is the only value below the interval the first and last heads span. Including it makes
    // is_concave_single_notes fire and force the beam flat; the real notes alone do not.
    private static final int GRACE_GROUP_SIZE = 5;
    private static final int GRACE_INDEX = 2;
    private static final int SP_GRACE_FIRST = -3;
    private static final int SP_GRACE_INNER = -2;
    private static final int SP_GRACE_NOTE = -1;
    private static final int SP_GRACE_PEAK = -6;

    /**
     * A second grace-note pitch, one staff position from {@link #SP_GRACE_NOTE}. Measured: if
     * the grace note reaches the scorer, these two pitches place the beam 0.81 ss apart.
     */
    private static final int SP_GRACE_NOTE_ALT = 0;

    /** Staff position that puts a whole group's stems down, so a grace note's must not follow. */
    private static final int SP_DOWN_STEM_GROUP = -4;

    // Row 24c — a grace note's stem as a factor in the beam-above/below decision. The group's
    // heads sit at or below the middle line, so its contour asks for a beam above it; whether
    // that beam would land on the grace note's stem is what these two pitches vary.
    private static final int SP_UP_STEM_GROUP_LOW = 2;
    private static final int SP_UP_STEM_GROUP_HIGH = 0;

    /** A grace note high enough that a beam above this group would cross its stem. */
    private static final int SP_GRACE_UNDER_BEAM = -1;

    /** A grace note low enough that its stem stays clear of the same beam. */
    private static final int SP_GRACE_CLEAR_OF_BEAM = 4;

    // The two staff positions the collision flips between, for a two-beam stack over this group.
    // A staff position is half a staff space, so these are the closest a pair can be, and they
    // straddle the threshold by less than the clearance constant itself: the test that uses them
    // fails if the clearance changes, if the beam-height estimate drifts, or if the comparison
    // turns from < into <=.
    /** Highest grace note that still collides: its stem tip sits 0.29 ss above the threshold. */
    private static final int SP_GRACE_BARELY_UNDER_BEAM = 1;

    /** Lowest grace note that already clears: its stem tip sits 0.21 ss below the threshold. */
    private static final int SP_GRACE_BARELY_CLEAR_OF_BEAM = 2;

    // Row 25 — tie geometry constant
    /** Staff position shared by both tied notes. */
    private static final int SP_TIE_NOTE = 2;

    // Phase 5 — render-vs-export agreement constant
    /** sp < 0 → stem down → arc bulges upward (arcSignSs=-1); mirrors SP_TIE_NOTE (stem up). */
    private static final int SP_TIE_NOTE_STEM_DOWN = -2;

    // Phase 4 (#503) — tie staff-line avoidance constants
    /** Even sp → staff line; sp > 0 → stem up → arc bulges downward (arcSignSs=+1). */
    private static final int SP_TIE_LINE_DOWN_ARC = 2;

    /** Even sp → staff line; sp < 0 → stem down → arc bulges upward (arcSignSs=-1); mirrors SP_TIE_LINE_DOWN_ARC. */
    private static final int SP_TIE_LINE_UP_ARC = -2;

    /** Odd sp → a staff space; sp > 0 → stem up → arc down onto the sp-2 line, so the seat is pushed. */
    private static final int SP_TIE_SPACE_CENTER = 1;

    /** sp 0 → the middle staff line. */
    private static final int SP_TIE_MIDDLE_LINE = 0;

    /** Even sp → the bottom staff line (outermost within the staff). */
    private static final int SP_TIE_BOTTOM_LINE = 4;

    /** Odd sp → the space just above the bottom line; an arc-down edge row lands on the bottom line. */
    private static final int SP_TIE_SPACE_ABOVE_BOTTOM_LINE = 3;

    /** Odd sp → the space just below the bottom line; an arc-down edge row is off the staff. */
    private static final int SP_TIE_SPACE_BELOW_STAFF = 5;

    /** Small natural arc height whose body already clears the nearest staff line (no adjustment). */
    private static final double TIE_TEST_CLEAR_HEIGHT_SS = 0.3;
    /** Natural arc height whose apex sits below the line but whose outer edge intrudes, so it is flattened (fit-below). */
    private static final double TIE_TEST_FLATTEN_HEIGHT_SS = 0.5;
    /** Natural arc height whose apex pokes past the line, so it is heightened over it (clear-above). */
    private static final double TIE_TEST_HEIGHTEN_HEIGHT_SS = 0.95;
    /**
     * Natural arc whose apex pokes just past the line while flattening under it would be a smaller
     * change: it must still be lifted over the line (heightened), never squashed back under it.
     */
    private static final double TIE_TEST_POKE_HEIGHT_SS = 0.7;

    // Natural slur_height / slur_indent growth-curve inputs (tie widths, staff spaces).
    /** A short tie. */
    private static final double TIE_TEST_NARROW_WIDTH_SS = 2.0;
    /** A wide tie: arcs higher and indents more than the narrow one, but still below the asymptotes. */
    private static final double TIE_TEST_WIDE_WIDTH_SS = 12.0;
    /** A tie so wide that both curves are effectively at their asymptotic limits. */
    private static final double TIE_TEST_HUGE_WIDTH_SS = 100.0;

    /** Baseline below the staff whose apex rounds to a ledger line beyond ±STAFF_HALF_SS (no real line to avoid). */
    private static final double TIE_TEST_BEYOND_STAFF_BASE_Y_SS = 2.5;
    /** Baseline just below a staff line: a short arc's apex fits below the line, yet the line is too close for even a flattened arc to clear. */
    private static final double TIE_TEST_TIGHT_LINE_BASE_Y_SS = 0.85;
    /** Small natural height whose apex fits below the near line, forcing the flatten-then-give-up fall-through to heighten. */
    private static final double TIE_TEST_TIGHT_NATURAL_HEIGHT_SS = 0.1;

    // Stem-lengthening constants — notes beyond MIN_STEM_SS (3.5 ss) from center
    /** Two ledger lines above the staff (sp < 0 → stems down); |spToSs(-8)| = 4.0 → lengtheningSs = 0.5. */
    private static final int SP_LEDGER_ABOVE_2 = -8;

    /** Three ledger lines above the staff (sp < 0 → stems down); |spToSs(-10)| = 5.0 → lengtheningSs = 1.5. */
    private static final int SP_LEDGER_ABOVE_3 = -10;

    /** Two ledger lines below the staff (sp > 0 → stems up); spToSs(8) = 4.0 → lengtheningSs = 0.5. */
    private static final int SP_LEDGER_BELOW_2 = 8;

    /** Lengthening threshold: spToSs(7) = 3.5 = MIN_STEM_SS exactly → lengtheningSs = 0 (last sp needing none). */
    private static final int SP_LENGTHENING_THRESHOLD = 7;

    /** Expected lengtheningSs at SP_LEDGER_ABOVE_2 / SP_LEDGER_BELOW_2: |4.0| - 3.5. */
    private static final double LENGTHENING_TWO_LEDGER_LINES = 0.5;

    /** Expected lengtheningSs at SP_LEDGER_ABOVE_3: |5.0| - 3.5. */
    private static final double LENGTHENING_THREE_LEDGER_LINES = 1.5;

    // T5b — empty-line attribution constants
    /** Non-zero content width for an attribution stacked on an empty first line. */
    private static final double EMPTY_LINE_ATTRIBUTION_WIDTH_SS = 20.0;
    /** Non-zero content height for an attribution stacked on an empty first line. */
    private static final double EMPTY_LINE_ATTRIBUTION_HEIGHT_SS = 3.0;

    // Row 30 — beam count (flag levels) per note type
    private static final int QUAVER_BEAMS = 1;
    private static final int SEMIQUAVER_BEAMS = 2;
    private static final int DEMI_SEMIQUAVER_BEAMS = 3;

    private static LyricRenderMetrics lyricRenderMetrics() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        return LyricRenderMetrics.forFont(lyricsFont);
    }

    private static LayoutEngine engine() {
        return new LayoutEngine(
            lyricRenderMetrics(), STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultFonts());
    }

    /** Asserts value is not null and returns it non-null for NullAway. */
    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    // T1: layout() stores a Clef at LayoutEngine.CLEF_X_POSITION_SS
    @Test
    void testLayoutStoresClefAtStandardPosition() {
        var result = require(engine().layout(detachedLine()), "LayoutResult");
        var clef = require(result.getClef(), "Clef");

        assertThat(clef.getXSs()).isCloseTo(LayoutEngine.CLEF_X_POSITION_SS, within(TOLERANCE));
    }

    // T2: layout() stores a KeySignature immediately after the clef with correct key data
    @Test
    void testLayoutStoresKeySignatureAfterClef() {
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(3);

        var result = require(engine().layout(line), "LayoutResult");
        var keySig = require(result.getKeySignature(), "KeySignature");

        assertThat(keySig.getXSs())
            .isCloseTo(HorizontalSpacingCalculator.calculateKeySignatureXSs(), within(TOLERANCE));
        assertThat(keySig.getKeyType()).isEqualTo(KeyType.SHARPS);
        assertThat(keySig.getAccidentalCount()).isEqualTo(3);
    }

    // T3: On the last line, the final barline is positioned flush-right
    @Test
    void testFinalBarlineFlushRightOnLastLine() {
        var song = new Song();
        var line = song.getLine(0);
        var finalBarline = line.getElement(line.elementCount() - 1);

        var result = require(engine().layout(line, true), "LayoutResult");
        var expectedXSs = ElementType.terminalFlushRightXSs(STAFF_RIGHT_MARGIN_SS, ElementType.FINAL_DOUBLE_BARLINE);

        assertThat(result.getElementXSs(finalBarline)).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // T3b: On the last line with a REPEAT_RIGHT terminal, the terminal is flush-right
    @Test
    void testRightRepeatTerminalFlushRightOnLastLine() {
        var song = new Song();
        song.replaceTerminal(ElementType.REPEAT_RIGHT);
        var line = song.getLine(0);
        var terminal = line.getElement(line.elementCount() - 1);

        assertThat(terminal.getType()).isEqualTo(ElementType.REPEAT_RIGHT);

        var result = require(engine().layout(line, true), "LayoutResult");
        var expectedXSs = ElementType.terminalFlushRightXSs(STAFF_RIGHT_MARGIN_SS, ElementType.REPEAT_RIGHT);

        assertThat(result.getElementXSs(terminal)).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // T4: On a non-last line, the final barline lands at the normal horizontal-spacing position
    @Test
    void testFinalBarlineNotFlushRightOnNonLastLine() {
        var song = new Song();
        var line = song.getLine(0);
        var finalBarline = line.getElement(line.elementCount() - 1);

        var result = require(engine().layout(line, false), "LayoutResult");
        // The barline is the only column; it gets the first-element position from the spacing calculator.
        var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);

        assertThat(result.getElementXSs(finalBarline)).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // T5: Empty line result still contains clef and key signature
    @Test
    void testEmptyLineResultContainsHeaderElements() {
        var result = require(engine().layout(detachedLine()), "LayoutResult");

        assertThat(result.getClef()).describedAs("Clef on empty line").isNotNull();
        assertThat(result.getKeySignature()).describedAs("KeySignature on empty line").isNotNull();
    }

    // T5a: An empty line reports no content extents at all. These feed inter-line spacing, so
    // padding them to give the line height made every empty line space itself as if it held that
    // much content (refs #630). The height it needs comes from the paint floors instead, which is
    // what the companion assertion pins — the two must not be conflated again.
    @Test
    void testEmptyLineReportsNoContentButKeepsMinimumPaintedHeight() {
        var result = require(engine().layout(detachedLine()), "LayoutResult");

        assertThat(result.getContentAboveStaffSs())
            .describedAs("empty line content above the staff")
            .isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.getContentBelowStaffSs())
            .describedAs("empty line content below the staff")
            .isCloseTo(0.0, within(TOLERANCE));

        // The staff still gets its full surround, so the clef and key signature are not clipped.
        assertThat(result.paintAboveMidlineSs())
            .describedAs("empty line painted extent above the midline")
            .isCloseTo(LineSpacing.MIN_ABOVE_MIDLINE_SS, within(TOLERANCE));
        assertThat(result.paintBelowMidlineSs(lyricRenderMetrics()))
            .describedAs("empty line painted extent below the midline")
            .isCloseTo(LineSpacing.MIN_BELOW_MIDLINE_SS, within(TOLERANCE));

        // A line with no columns has no chain to solve, so it cannot fail to fit. Were it ever
        // reported as overflowing, every empty line would draw red and every new document would
        // open behind the clipped-content warning (refs #696).
        assertThat(result.overflowsStaffWidth())
            .describedAs("empty line overflow flag")
            .isFalse();
    }

    // T5b: Empty first line (no columns) reserves room for a tall attribution (refs #616).
    // The stacking maths itself is covered by VerticalStackingCalculatorTest.EmptyLineAttribution;
    // this only pins LayoutEngine's own wiring — that it calls the calculator at all and lets a
    // tall attribution lift the band above the zero baseline an empty line otherwise reports.
    @Test
    void testEmptyLineStacksAttribution() {
        var attribution = new Attribution();
        attribution.setDimensionsSs(EMPTY_LINE_ATTRIBUTION_WIDTH_SS, EMPTY_LINE_ATTRIBUTION_HEIGHT_SS);

        var withAttribution = require(
            engine().layout(detachedLine(), false, false, attribution), "LayoutResult");
        var withoutAttribution = require(
            engine().layout(detachedLine(), false, false, null), "LayoutResult");

        assertThat(withAttribution.getDecorationLayout(attribution))
            .describedAs("attribution DecorationLayout on an empty first line")
            .isNotNull();
        assertThat(withAttribution.getContentAboveStaffSs())
            .describedAs("a tall attribution must lift the band above an empty line's zero baseline")
            .isGreaterThan(withoutAttribution.getContentAboveStaffSs());
    }

    // T6: A line that cannot fit is still laid out — on its collision floors, marked overflowing,
    //     and running past the staff so the component's bounds clip it (refs #696). Layout has no
    //     failure mode: before this it returned null and the line did not draw at all.
    @Nested
    class OverflowingLines {

        /**
         * Loads {@code fixtures/overflowing-lines.musicxml} at the line width the file itself
         * carries. Its first two lines cannot fit that width and its third can, so one document
         * covers both sides of the fit decision — and, because it is read back through
         * {@code MusicXmlReader}, covers them over a real document rather than a hand-built line.
         * It is also the fixture for looking at an over-full line next to a normal one by eye.
         * <p>
         * If the spacing model changes enough that the note counts no longer straddle the width,
         * the fixture is stale, not the model: regenerate it as three lines cycling
         * {@code CROTCHET}, {@code QUAVER}, {@code SEMIBREVE} — 60, 60 and 8 notes — written
         * through {@code MusicXmlWriter}, and check the counts still straddle the new limit.
         */
        private Song fixtureSong() throws Exception {
            return loadFixture("overflowing-lines");
        }

        private LayoutEngine engineFor(Song song) {
            return new LayoutEngine(
                lyricRenderMetrics(), song.getLineWidthSs(), DocumentFonts.defaultFonts());
        }

        /** The fixture's first line, which holds more than the staff can show. */
        private Line overflowingLine(Song song) {
            return song.getLine(0);
        }

        // T6a: The flag tracks the fit, line by line, over a real document — set on the two lines
        //      that cannot fit and clear on the one that can, so it is neither stuck on nor off.
        @Test
        void testEachLineIsFlaggedByWhetherItFitsTheStaffWidth() throws Exception {
            var song = fixtureSong();
            var engine = engineFor(song);
            var lineCount = song.lineCount();
            var flags = new Boolean[lineCount];

            for (var i = 0; i < lineCount; i++) {
                flags[i] = engine.layout(song.getLine(i), i == lineCount - 1).overflowsStaffWidth();
            }

            assertThat(flags)
                .describedAs("per-line overflow of the fixture at its stored width of %s Ss",
                    song.getLineWidthSs())
                .containsExactly(true, true, false);
        }

        // T6b: The placement of a line that cannot fit — every gap on its own collision floor, the
        //      tightest legal spacing there is, with the tail left past the staff to be clipped.
        @Test
        void testOverflowingLineIsPlacedOnItsCollisionFloors() throws Exception {
            var song = fixtureSong();
            var line = overflowingLine(song);
            var result = engineFor(song).layout(line, false);
            var lineRestSs = song.getDefaultRestLengthSs();
            var elements = line.getElements();
            var floorsSs = new ArrayList<Double>();

            // Each pair's own floor, not one floor reused: the fixture cycles three note widths, so
            // neighbouring pairs have genuinely different floors and a placement that reused a
            // single floor for every gap would fail here. The floors come from the spacing model
            // rather than from literals, which keeps the assertion tied to the model — the model's
            // own arithmetic is HorizontalSpacingCalculatorSpringTest's subject, not this test's.
            for (var i = 1; i < elements.size(); i++) {
                var prevColumn = require(
                    result.getElementColumn(elements.get(i - 1)), "ElementColumn " + (i - 1));
                var currColumn = require(
                    result.getElementColumn(elements.get(i)), "ElementColumn " + i);
                var floorSs = HorizontalSpacingCalculator
                    .buildSpring(prevColumn, currColumn, lineRestSs)
                    .strutSs();
                floorsSs.add(floorSs);

                assertThat(result.getElementXSs(elements.get(i))
                        - result.getElementXSs(elements.get(i - 1)))
                    .describedAs("gap %d of an overflowing line sits on its own collision floor", i)
                    .isCloseTo(floorSs, within(TOLERANCE));
            }

            // Guards the assertions above against losing their point: if the fixture were ever
            // regenerated with notes of one width, every floor would coincide and the loop could no
            // longer tell a per-gap placement from a single floor copied across all of them.
            assertThat(Set.copyOf(floorsSs))
                .describedAs("distinct collision floors among this line's gaps — with only one, the"
                    + " per-gap check above would pass on a single floor reused for every gap")
                .hasSizeGreaterThan(1);

            // The point of placing it at all: the tail really is out past the staff waiting to be
            // clipped, rather than squeezed inside the staff on top of itself.
            assertThat(result.getElementXSs(elements.getLast()))
                .describedAs("last element of an overflowing line, against the staff width")
                .isGreaterThan(song.getLineWidthSs());
        }

        // T6c: The same line as the song's last line. The last line's terminal is normally snapped
        //      flush against the right margin; on an overflowing line that snap is skipped, so the
        //      terminal stays out past the staff with the rest of the overflow instead of being
        //      pulled back on top of the content it follows (refs #696).
        @Test
        void testOverflowingLastLineLeavesItsTerminalPastTheStaff() throws Exception {
            var song = fixtureSong();
            var line = overflowingLine(song);
            var asLastLine = engineFor(song).layout(line, true);
            var asInnerLine = engineFor(song).layout(line, false);
            var terminal = line.getElements().getLast();

            assertThat(asLastLine.getElementXSs(terminal))
                .describedAs("terminal of an overflowing last line, against the staff width")
                .isGreaterThan(song.getLineWidthSs());
            assertThat(asLastLine.getElementXSs(terminal))
                .describedAs("being the last line must not move an overflowing line's terminal")
                .isCloseTo(asInnerLine.getElementXSs(terminal), within(TOLERANCE));
        }

        // T6d: The flag flips exactly where the solver's verdict flips, rather than at some margin
        //      of its own. The boundary is found rather than written down, so it stays correct when
        //      the spacing model changes.
        @Test
        void testOverflowFlagFlipsAtTheFitBoundary() {
            var firstOverflowingCount = 0;

            for (var count = 1; count <= BOUNDARY_SEARCH_LIMIT && firstOverflowingCount == 0; count++) {
                if (overflowsWith(count)) {
                    firstOverflowingCount = count;
                }
            }

            assertThat(firstOverflowingCount)
                .describedAs("no note count up to %d overflows a %s Ss staff, so there is no"
                    + " boundary to test — the flag may be stuck off",
                    BOUNDARY_SEARCH_LIMIT, STAFF_RIGHT_MARGIN_SS)
                .isGreaterThan(1);

            assertThat(overflowsWith(firstOverflowingCount - 1))
                .describedAs("%d notes are one below the first count that overflows, so they must"
                    + " still fit", firstOverflowingCount - 1)
                .isFalse();
            assertThat(overflowsWith(firstOverflowingCount))
                .describedAs("%d notes are the first count that overflows", firstOverflowingCount)
                .isTrue();
        }

        private boolean overflowsWith(int noteCount) {
            var line = detachedLine();

            for (var i = 0; i < noteCount; i++) {
                line.addElement(ElementType.CROTCHET.newInstance());
            }

            return engine().layout(line, false).overflowsStaffWidth();
        }
    }

    // T7: layout(line, false, true) threads hasLeadingLyricContinuation — a leading extender
    //     connector starting at x=0 is emitted when the flag is true
    @Test
    void testLeadingLyricContinuationEmitsExtenderFromLineStart() {
        var line = detachedLine();
        line.addElement(ElementType.CROTCHET_REST.newInstance());

        var result = require(engine().layout(line, false, true), "LayoutResult");
        var connectors = result.getLyricConnectors();

        assertThat(connectors).describedAs("lyric connectors with leading continuation").isNotEmpty();

        var leading = connectors.getFirst();
        assertThat(leading.startXSs()).describedAs("leading extender startXSs").isCloseTo(0.0, within(TOLERANCE));
        assertThat(leading.kind()).describedAs("leading connector kind").isEqualTo(LyricConnectorLayout.Kind.EXTENDER);
    }

    // T8: Unbeamed note below the middle staff line (sp > 0) gets stem up under auto direction
    @Test
    void testUnbeamedNoteWithPositiveSPGetsStemUp() {
        var line = detachedLine();
        var element = ElementType.CROTCHET.newInstance();
        element.setStaffPosition(SP_BELOW_MIDDLE);
        line.addElement(element);

        var result = require(engine().layout(line), "LayoutResult");
        var stem = require(result.getStemLayout(element), "StemLayout");

        var elementYSs = Staff.spToSs(SP_BELOW_MIDDLE);
        assertThat(stem.topYSs()).describedAs("stem-up top Y").isCloseTo(elementYSs - SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
        assertThat(stem.bottomYSs()).describedAs("stem-up bottom Y").isCloseTo(elementYSs, within(TOLERANCE));
    }

    // T9: Unbeamed note above the middle staff line (sp < 0) gets stem down under auto direction
    @Test
    void testUnbeamedNoteWithNegativeSPGetsStemDown() {
        var line = detachedLine();
        var element = ElementType.CROTCHET.newInstance();
        element.setStaffPosition(SP_ABOVE_MIDDLE);
        line.addElement(element);

        var result = require(engine().layout(line), "LayoutResult");
        var stem = require(result.getStemLayout(element), "StemLayout");

        var elementYSs = Staff.spToSs(SP_ABOVE_MIDDLE);
        assertThat(stem.topYSs()).describedAs("stem-down top Y").isCloseTo(elementYSs, within(TOLERANCE));
        assertThat(stem.bottomYSs()).describedAs("stem-down bottom Y").isCloseTo(elementYSs + SMuFLConstants.STEM_LENGTH_SS, within(TOLERANCE));
    }

    // T10: Unbeamed grace note always gets stem up, with grace-note stem length, even when above the middle line
    @Test
    void testGraceNoteAlwaysGetsStemUpWithGraceLength() {
        var line = detachedLine();
        var element = ElementType.GRACE_QUAVER.newInstance();
        element.setStaffPosition(SP_ABOVE_MIDDLE_GRACE);
        line.addElement(element);

        var result = require(engine().layout(line), "LayoutResult");
        var stem = require(result.getStemLayout(element), "StemLayout");

        var elementYSs = Staff.spToSs(SP_ABOVE_MIDDLE_GRACE);
        assertThat(stem.topYSs()).describedAs("grace stem-up top Y").isCloseTo(elementYSs - SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS, within(TOLERANCE));
        assertThat(stem.bottomYSs()).describedAs("grace stem-up bottom Y").isCloseTo(elementYSs, within(TOLERANCE));
    }

    // T11: Manual stem override (stemDirectionAuto=false, upper=false) is not auto-corrected even when sp > 0
    @Test
    void testManualStemOverrideNotAutoCorrected() {
        var line = detachedLine();
        var element = ElementType.CROTCHET.newInstance();
        element.setStaffPosition(SP_BELOW_MIDDLE_MANUAL);
        element.setStemDirectionAuto(false);
        element.setUpper(false);
        line.addElement(element);

        var result = require(engine().layout(line), "LayoutResult");
        var stem = require(result.getStemLayout(element), "StemLayout");

        // A manual down-stem below the middle line opposes defaultDirection (UP), so it is itself
        // forced and shortened (Ross & Gourlay) -- distinct from the auto-correction this test guards.
        var elementYSs = Staff.spToSs(SP_BELOW_MIDDLE_MANUAL);
        var forcedShorteningSs = NoteGeometry.forcedShorteningSs(
            SP_BELOW_MIDDLE_MANUAL, StaffElement.Direction.DOWN, false);
        assertThat(stem.topYSs()).describedAs("manual stem-down top Y").isCloseTo(elementYSs, within(TOLERANCE));
        assertThat(stem.bottomYSs()).describedAs("manual stem-down bottom Y")
            .isCloseTo(elementYSs + SMuFLConstants.STEM_LENGTH_SS - forcedShorteningSs, within(TOLERANCE));
    }

    // T12a: Beamed group with all notes above the middle line → auto stem direction is down (stemsUp=false)
    @Test
    void testBeamedGroupAutoDirectionAboveMidlineGetsStemsDown() {
        var line = detachedLine();
        var note1 = ElementType.QUAVER.newInstance();
        note1.setStaffPosition(SP_BEAM_ABOVE_1);
        var note2 = ElementType.QUAVER.newInstance();
        note2.setStaffPosition(SP_BEAM_ABOVE_2);
        line.addElement(note1);
        line.addElement(note2);
        line.addBeaming(new Beam(note1, note2));

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        assertThat(beamLayout.stemsUp()).describedAs("stemsUp for group with all sp<0").isFalse();
    }

    // T12b: Beamed group with all notes below the middle line → auto stem direction is up (stemsUp=true)
    @Test
    void testBeamedGroupAutoDirectionBelowMidlineGetsStemsUp() {
        var line = detachedLine();
        var note1 = ElementType.QUAVER.newInstance();
        note1.setStaffPosition(SP_BEAM_BELOW_1);
        var note2 = ElementType.QUAVER.newInstance();
        note2.setStaffPosition(SP_BEAM_BELOW_2);
        line.addElement(note1);
        line.addElement(note2);
        line.addBeaming(new Beam(note1, note2));

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        assertThat(beamLayout.stemsUp()).describedAs("stemsUp for group with all sp>0").isTrue();
    }

    // T13: First note with manual stem override (upper=true) wins over auto stemsDown for the whole group
    @Test
    void testBeamedGroupFirstNoteManualOverrideWinsForStemsUp() {
        var line = detachedLine();
        var note1 = ElementType.QUAVER.newInstance();
        note1.setStaffPosition(SP_BEAM_ABOVE_1);
        note1.setStemDirectionAuto(false);
        note1.setUpper(true);           // manual: force stems up
        var note2 = ElementType.QUAVER.newInstance();
        note2.setStaffPosition(SP_BEAM_ABOVE_2); // auto would contribute to stemsDown
        line.addElement(note1);
        line.addElement(note2);
        line.addBeaming(new Beam(note1, note2));

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        assertThat(beamLayout.stemsUp()).describedAs("manual upper=true on first note overrides auto stemsDown").isTrue();
    }

    // T14: Beam slope with a very large pitch difference is dampened well below the raw pitch slope
    @Test
    void testBeamSlopeWithLargePitchDifferenceIsDampened() {
        var line = detachedLine();
        var note1 = ElementType.QUAVER.newInstance();
        note1.setStaffPosition(SP_SLOPE_EXTREME_LOW); // very low (far below middle line)
        var note2 = ElementType.QUAVER.newInstance();
        note2.setStaffPosition(0);                    // middle line (sp=0)
        line.addElement(note1);
        line.addElement(note2);
        line.addBeaming(new Beam(note1, note2));

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        var xSpanSs = result.getElementXSs(note2) - result.getElementXSs(note1);
        var rawSlope = Math.abs(
            Staff.spToSs(note2.getStaffPosition() - note1.getStaffPosition()) / xSpanSs);

        // Quanting may move each beam edge by up to one quant step, which the damped
        // slope cannot account for; allow exactly that slack over the group's X span.
        var dampedSlopeBound = BeamScoring.SLOPE_DAMPING_COEFFICIENT + QUANT_STEP_SS / xSpanSs;

        assertThat(Math.abs(beamLayout.slope()))
            .describedAs("dampened slope absolute value must be well below the raw pitch slope")
            .isLessThan(rawSlope);
        assertThat(Math.abs(beamLayout.slope()))
            .describedAs("dampened slope absolute value must respect the damping coefficient plus quant slack")
            .isLessThanOrEqualTo(dampedSlopeBound);
    }

    // T15: Every stem in a beamed group is at least LilyPond's extreme minimum stem length
    @Test
    void testBeamedGroupAllStemsAtLeastMinimumStemLength() {
        var line = detachedLine();
        var note1 = ElementType.QUAVER.newInstance();
        note1.setStaffPosition(SP_CONTOUR_FIRST);
        var note2 = ElementType.QUAVER.newInstance();
        note2.setStaffPosition(SP_CONTOUR_MIDDLE);
        var note3 = ElementType.QUAVER.newInstance();
        note3.setStaffPosition(SP_CONTOUR_LAST);
        line.addElement(note1);
        line.addElement(note2);
        line.addElement(note3);
        line.addBeaming(new Beam(note1, note3));

        var result = require(engine().layout(line), "LayoutResult");

        // Quanting may shorten a stem below the standard length, but never below LilyPond's
        // extreme minimum: the free length for a single beam plus half the beam thickness.
        var extremeMinimumSs = BeamScoring.BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS[0]
            + LineThickness.BEAM_THICKNESS_SS / 2.0;

        for (var sp : new int[]{SP_CONTOUR_FIRST, SP_CONTOUR_MIDDLE, SP_CONTOUR_LAST}) {
            var note = (sp == SP_CONTOUR_FIRST) ? note1 : (sp == SP_CONTOUR_MIDDLE) ? note2 : note3;
            var stem = require(result.getStemLayout(note), "StemLayout at sp=" + sp);
            assertThat(stem.bottomYSs() - stem.topYSs())
                .describedAs("stem length at sp=%d must be ≥ the extreme minimum stem length".formatted(sp))
                .isGreaterThanOrEqualTo(extremeMinimumSs - TOLERANCE);
        }
    }

    // T16: Flat beam (equal-position notes, slope=0) lands its center on a straddle/sit/inter/hang quant
    @Test
    void testFlatBeamCenterLandsOnAQuant() {
        var line = detachedLine();
        var note1 = ElementType.QUAVER.newInstance();
        note1.setStaffPosition(SP_FLAT_BEAM);
        var note2 = ElementType.QUAVER.newInstance();
        note2.setStaffPosition(SP_FLAT_BEAM);
        line.addElement(note1);
        line.addElement(note2);
        line.addBeaming(new Beam(note1, note2));

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        // Recover the scoring-space beam center from the layout's Y-down outer edge.
        // Both notes sit below the middle line, so the group is stems-up.
        var beamCenterYUpSs =
            -(beamLayout.startYSs() + STEMS_UP_DIR_SIGN * LineThickness.BEAM_THICKNESS_SS / 2.0);
        var fractionSs = beamCenterYUpSs - Math.floor(beamCenterYUpSs);

        // The last entry wraps straddle around the top of the fractional range.
        var quantOffsets = new double[]{
            BeamScoring.STRADDLE_SS,
            BeamScoring.SIT_SS,
            BeamScoring.INTER_SS,
            BeamScoring.HANG_SS,
            1.0 + BeamScoring.STRADDLE_SS
        };

        var distanceToNearestQuantSs = Double.MAX_VALUE;

        for (var offset : quantOffsets) {
            distanceToNearestQuantSs = Math.min(distanceToNearestQuantSs, Math.abs(fractionSs - offset));
        }

        assertThat(distanceToNearestQuantSs)
            .describedAs("flat beam center must land on a straddle/sit/inter/hang quant")
            .isCloseTo(0.0, within(TOLERANCE));
    }

    // T17: Sloped beam has thickeningSs strictly in (0, BEAM_DEPTH_SS * 0.088]
    @Test
    void testSlopedBeamHasThickeningInBoundedRange() {
        var line = detachedLine();
        var note1 = ElementType.QUAVER.newInstance();
        note1.setStaffPosition(SP_BEAM_BELOW_1);   // below middle → stemsUp
        var note2 = ElementType.QUAVER.newInstance();
        note2.setStaffPosition(SP_BEAM_ABOVE_1);   // above middle → different sp → non-zero slope
        line.addElement(note1);
        line.addElement(note2);
        line.addBeaming(new Beam(note1, note2));

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        assertThat(beamLayout.thickeningSs())
            .describedAs("thickeningSs for sloped beam must be positive")
            .isGreaterThan(0.0);
        assertThat(beamLayout.thickeningSs())
            .describedAs("thickeningSs must be at most BEAM_DEPTH_SS * 0.088")
            .isLessThanOrEqualTo(LayoutEngine.BEAM_DEPTH_SS * 0.088 + TOLERANCE);
    }

    // T18: Semiquaver at the beam-group start (beamStart) gets a right-pointing stub
    @Test
    void testBeamStubDirectionOfSemiquaverAtGroupStartIsRight() {
        var line = detachedLine();
        var semiquaver = ElementType.SEMIQUAVER.newInstance();
        semiquaver.setStaffPosition(SP_STUB_NOTE);
        var quaver = ElementType.QUAVER.newInstance();
        quaver.setStaffPosition(SP_STUB_NOTE);
        line.addElement(semiquaver);   // beamStart → stub goes right
        line.addElement(quaver);
        line.addBeaming(new Beam(semiquaver, quaver));

        var result = require(engine().layout(line), "LayoutResult");
        var stem = require(result.getStemLayout(semiquaver), "StemLayout for semiquaver");

        assertThat(stem.stubRight())
            .describedAs("stub direction for semiquaver at group start must be right")
            .isTrue();
    }

    // T18b: French beaming is resolved during layout, not at paint time. The rule itself is
    //       BeamMathTest's subject; this pins that the engine actually stores its answer, since
    //       nothing else connects BeamMath.frenchBeamShortening to what NoteRenderer draws.
    @Test
    void testFrenchShorteningIsStoredOnlyForInnerStemsOfABeamGroup() {
        var line = detachedLine();
        var notes = new ArrayList<StaffElement>();

        for (var i = 0; i < FRENCH_GROUP_SIZE; i++) {
            var semiquaver = ElementType.SEMIQUAVER.newInstance();
            semiquaver.setStaffPosition(SP_STUB_NOTE);
            line.addElement(semiquaver);
            notes.add(semiquaver);
        }

        line.addBeaming(new Beam(notes.getFirst(), notes.getLast()));

        var result = require(engine().layout(line), "LayoutResult");
        var storedLevels = notes.stream()
            .map(note -> require(result.getStemLayout(note), "StemLayout").frenchShorteningLevels())
            .toList();

        assertThat(storedLevels)
            .describedAs("the engine stores one shortening level for each inner sixteenth stem, none for the edges")
            .containsExactly(0, 1, 1, 0);
    }

    /**
     * Beams the given note types into one group at the given staff positions. Both arrays are
     * in element order and must be the same length.
     */
    private static Line beamedLineAt(ElementType[] types, int[] staffPositions) {
        var line = detachedLine();

        for (var i = 0; i < types.length; i++) {
            var element = types[i].newInstance();
            element.setStaffPosition(staffPositions[i]);
            line.addElement(element);
        }

        line.addBeaming(new Beam(line.getElement(0), line.getElement(types.length - 1)));
        return line;
    }

    /**
     * The reported case: a grace note inserted between the sixteenths of one beamed run, at
     * the given pitch. Only the grace note's staff position varies, so two calls differing
     * in {@code gracePos} produce beams that must be identical if the grace note is
     * correctly excluded from beam scoring — same element count, same column spacing.
     */
    private static Line lineWithGraceInsideBeamGroup(int gracePos) {
        return beamedLineAt(
            new ElementType[] {
                ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
                ElementType.GRACE_QUAVER,
                ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
            },
            new int[] {SP_GRACE_FIRST, SP_GRACE_INNER, gracePos, SP_GRACE_PEAK, SP_GRACE_INNER});
    }

    private static LayoutResult.BeamLayout onlyBeamLayout(Line line, LayoutResult result) {
        return require(result.getBeamLayout(require(line.findBeamAt(0), "Beam")), "BeamLayout");
    }

    // T18c: A grace note may be inserted inside a beam group's span, but it takes no part in
    //       the beaming. Its stem must keep the short grace length instead of being stretched
    //       to the beam, which is what happens if the beam pass treats it as one of its stems.
    @Test
    void testGraceNoteInsideABeamGroupKeepsItsOwnStemLength() {
        var line = lineWithGraceInsideBeamGroup(SP_GRACE_NOTE);
        var result = require(engine().layout(line), "LayoutResult");
        var graceStem = require(result.getStemLayout(line.getElement(GRACE_INDEX)), "grace StemLayout");

        assertThat(graceStem.lengtheningSs())
            .describedAs("a grace note is never lengthened to reach a beam it is not part of")
            .isEqualTo(0.0);
        assertThat(graceStem.bottomYSs() - graceStem.topYSs())
            .describedAs("the grace stem stays at the grace length")
            .isCloseTo(SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS, within(TOLERANCE));
    }

    // The counterpart: the real stems of the same group are still measured to the beam, so the
    // exclusion above cannot have been achieved by skipping the whole group.
    @Test
    void testBeamedStemsAroundAGraceNoteAreStillMeasuredToTheBeam() {
        var line = lineWithGraceInsideBeamGroup(SP_GRACE_NOTE);
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = onlyBeamLayout(line, result);

        assertThat(beamLayout.stems().keySet())
            .describedAs("every stem of the group except the grace note is beam-measured")
            .doesNotContain(line.getElement(GRACE_INDEX))
            .hasSize(GRACE_GROUP_SIZE - 1);
    }

    // A grace note's pitch must not reach BeamScoring at all. Laying the same run out twice,
    // varying only the grace note's pitch, pins that directly and without depending on which
    // quant the scorer picks: identical real notes must produce an identical beam. The two
    // pitches are chosen because they place the beam 0.81 ss apart when the grace note is
    // wrongly scored as a stem, which is far outside TOLERANCE.
    @Test
    void testGraceNotePitchDoesNotMoveTheBeam() {
        var oneGracePitch = lineWithGraceInsideBeamGroup(SP_GRACE_NOTE);
        var otherGracePitch = lineWithGraceInsideBeamGroup(SP_GRACE_NOTE_ALT);
        var oneBeam =
            onlyBeamLayout(oneGracePitch, require(engine().layout(oneGracePitch), "LayoutResult"));
        var otherBeam =
            onlyBeamLayout(otherGracePitch, require(engine().layout(otherGracePitch), "LayoutResult"));

        assertThat(oneBeam.startYSs())
            .describedAs("moving only the grace note may not move the beam")
            .isCloseTo(otherBeam.startYSs(), within(TOLERANCE));
        assertThat(oneBeam.slope())
            .describedAs("nor change its slope")
            .isCloseTo(otherBeam.slope(), within(TOLERANCE));
    }

    // Grace notes always point up (StaffElement.defaultDirection). A group whose own stems
    // point down must not drag an interposed grace note down with it.
    @Test
    void testGraceNoteKeepsItsUpStemInADownStemBeamGroup() {
        var line = beamedLineAt(
            new ElementType[] {
                ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
                ElementType.GRACE_QUAVER,
                ElementType.SEMIQUAVER, ElementType.SEMIQUAVER,
            },
            new int[] {
                SP_DOWN_STEM_GROUP, SP_DOWN_STEM_GROUP, SP_DOWN_STEM_GROUP,
                SP_DOWN_STEM_GROUP, SP_DOWN_STEM_GROUP,
            });

        require(engine().layout(line), "LayoutResult");

        assertThat(line.getElement(0).getDirection())
            .describedAs("the group's own stems point down")
            .isEqualTo(StaffElement.Direction.DOWN);
        assertThat(line.getElement(GRACE_INDEX).getDirection())
            .describedAs("the grace note keeps its upward stem")
            .isEqualTo(StaffElement.Direction.UP);
    }

    /**
     * A run of {@code noteType} whose contour asks for a beam above it, with a grace note at
     * {@code gracePos} inside the span. Only the note type and the grace note's pitch vary, so
     * between them they decide whether the beam can stay above: the type sets how far the beam
     * stack hangs below the outer beam, the pitch sets how far the grace stem reaches up.
     */
    private static Line lineWithGraceUnderAnUpwardBeam(ElementType noteType, int gracePos) {
        return beamedLineAt(
            new ElementType[] {
                noteType, noteType,
                ElementType.GRACE_QUAVER,
                noteType, noteType,
            },
            new int[] {
                SP_UP_STEM_GROUP_LOW, SP_UP_STEM_GROUP_LOW, gracePos,
                SP_UP_STEM_GROUP_LOW, SP_UP_STEM_GROUP_HIGH,
            });
    }

    private static Line lineWithGraceUnderAnUpwardBeam(int gracePos) {
        return lineWithGraceUnderAnUpwardBeam(ElementType.SEMIQUAVER, gracePos);
    }

    private static StaffElement.Direction beamGroupDirectionOf(Line line) {
        require(engine().layout(line), "LayoutResult");
        return line.getElement(0).getDirection();
    }

    // T24c: A grace note keeps its own short upward stem, so a beam drawn above a group it sits in
    //       can cross that stem and swallow the notehead. The grace note's reach is therefore one
    //       of the factors in the above/below decision: it sends the beam below rather than be
    //       buried.
    @Test
    void testGraceStemUnderAnUpwardBeamForcesTheBeamBelow() {
        var line = lineWithGraceUnderAnUpwardBeam(SP_GRACE_UNDER_BEAM);

        require(engine().layout(line), "LayoutResult");

        assertThat(line.getElement(0).getDirection())
            .describedAs("a grace note the beam would run into pushes the beam below the group")
            .isEqualTo(StaffElement.Direction.DOWN);
        assertThat(line.getElement(GRACE_INDEX).getDirection())
            .describedAs("the grace note still stems up, now clear of the beam")
            .isEqualTo(StaffElement.Direction.UP);
    }

    // T24d: The counterpart: the same group, with the grace note low enough to clear the beam,
    //       keeps the beam the contour asked for — so the rule above is a collision test, not a
    //       blanket ban on beaming above a grace note.
    @Test
    void testGraceStemClearOfAnUpwardBeamLeavesTheBeamAbove() {
        var line = lineWithGraceUnderAnUpwardBeam(SP_GRACE_CLEAR_OF_BEAM);

        require(engine().layout(line), "LayoutResult");

        assertThat(line.getElement(0).getDirection())
            .describedAs("a grace note that clears the beam does not move it")
            .isEqualTo(StaffElement.Direction.UP);
        assertThat(line.getElement(GRACE_INDEX).getDirection())
            .describedAs("the grace note stems up either way")
            .isEqualTo(StaffElement.Direction.UP);
    }

    // T24e: The two tests above sit well to either side of the threshold, so they would still pass
    //       with the clearance mis-set or the comparison inverted. This pins where the flip
    //       actually happens: the adjacent pair of staff positions that straddle it.
    @Test
    void testTheGraceStemCollisionFlipsAtItsClearanceThreshold() {
        assertThat(beamGroupDirectionOf(lineWithGraceUnderAnUpwardBeam(SP_GRACE_BARELY_UNDER_BEAM)))
            .describedAs("the highest grace note that still collides sends the beam below")
            .isEqualTo(StaffElement.Direction.DOWN);
        assertThat(
            beamGroupDirectionOf(lineWithGraceUnderAnUpwardBeam(SP_GRACE_BARELY_CLEAR_OF_BEAM)))
            .describedAs("one staff position lower, the same grace note clears and the beam stays")
            .isEqualTo(StaffElement.Direction.UP);
    }

    // T24f: The beam's lowest edge is the outer beam plus the stack hanging below it, so a
    //       thirty-second group reaches further down toward a grace note than a sixteenth one.
    //       Holding the grace note still and shortening the notes must therefore flip the beam —
    //       which it does not if the estimate ignores how many beams the group carries.
    @Test
    void testAThirdBeamReachesAGraceNoteThatTwoBeamsClear() {
        assertThat(beamGroupDirectionOf(lineWithGraceUnderAnUpwardBeam(
            ElementType.SEMIQUAVER, SP_GRACE_BARELY_CLEAR_OF_BEAM)))
            .describedAs("two beams stop short of this grace note")
            .isEqualTo(StaffElement.Direction.UP);
        assertThat(beamGroupDirectionOf(lineWithGraceUnderAnUpwardBeam(
            ElementType.DEMI_SEMIQUAVER, SP_GRACE_BARELY_CLEAR_OF_BEAM)))
            .describedAs("a third beam hangs low enough to reach it, sending the beam below")
            .isEqualTo(StaffElement.Direction.DOWN);
    }

    // T24g: The collision scan has to walk every grace note in the span, not just the first. Here
    //       the first one clears and the second does not, so a scan that stops early — or that
    //       misses the last index — leaves the beam above and buries the second grace note.
    @Test
    void testACollidingGraceNoteIsFoundBehindAClearingOne() {
        var line = beamedLineAt(
            new ElementType[] {
                ElementType.SEMIQUAVER,
                ElementType.GRACE_QUAVER,
                ElementType.SEMIQUAVER,
                ElementType.GRACE_QUAVER,
                ElementType.SEMIQUAVER,
            },
            new int[] {
                SP_UP_STEM_GROUP_LOW, SP_GRACE_CLEAR_OF_BEAM, SP_UP_STEM_GROUP_LOW,
                SP_GRACE_UNDER_BEAM, SP_UP_STEM_GROUP_HIGH,
            });

        assertThat(beamGroupDirectionOf(line))
            .describedAs("a later grace note collides, so the beam still goes below")
            .isEqualTo(StaffElement.Direction.DOWN);
    }

    // T24h: A manual stem direction is the engraver's decision and outranks the grace note: the
    //       override scan returns before the collision check runs. Pinned because it is the one
    //       case where a grace note is knowingly left under the beam.
    @Test
    void testAManualUpStemKeepsTheBeamAboveACollidingGraceNote() {
        var line = lineWithGraceUnderAnUpwardBeam(SP_GRACE_UNDER_BEAM);
        var firstNote = line.getElement(0);
        firstNote.setStemDirectionAuto(false);
        firstNote.setDirection(StaffElement.Direction.UP);

        require(engine().layout(line), "LayoutResult");

        assertThat(line.getElement(1).getDirection())
            .describedAs("a manual direction wins over the grace note's collision")
            .isEqualTo(StaffElement.Direction.UP);
    }

    // T19: Tie endpoints attach at the notehead's facing (span-side) edge plus note-head-gap while the
    //      seat stays within the head box (LilyPond tie-formatting-problem.cc get_attachment, edge case).
    //      SP_TIE_NOTE is a line position, so the tie seats STAFF_LINE_TIE_CLEARANCE_GAP_SS into the
    //      adjacent space — within the head box — giving an edge attach. Both endpoints are mirror images.
    @Test
    void testTieEndpointXSsAttachAtFacingEdgeForEdgeSeat() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_NOTE);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_NOTE);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        // Left endpoint (dir=+1): facing edge is the anchor note's right edge, then note-head-gap in.
        var note1XSs = result.getElementXSs(note1);
        var expectedStartXSs = note1XSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS + LayoutEngine.NOTE_HEAD_GAP_SS;

        assertThat(tieLayout.startXSs())
            .describedAs("edge-seat tie startXSs attaches at the facing edge plus note-head-gap")
            .isCloseTo(expectedStartXSs, within(TOLERANCE));

        // Right endpoint (dir=-1, mirror image): facing edge is the end note's left edge.
        var note2XSs = result.getElementXSs(note2);
        var expectedEndXSs = note2XSs - LayoutEngine.NOTE_HEAD_GAP_SS;

        assertThat(tieLayout.endXSs())
            .describedAs("edge-seat tie endXSs mirrors startXSs: facing edge minus note-head-gap")
            .isCloseTo(expectedEndXSs, within(TOLERANCE));
    }

    // A left tie endpoint attaches at the note's own right edge, so a whole note — whose head is
    // wider than a black notehead — pushes the endpoint further right by exactly that difference.
    // With the shared black-notehead constant the endpoint used to land inside a whole note's ink.
    @Test
    void testTieEndpointXSsAttachesToTheNotesOwnHeadWidth() {
        var columnXSs = 10.0;
        var wholeNoteXSs = LayoutEngine.tieEndpointXSs(columnXSs, 1, false, ElementType.SEMIBREVE);
        var crotchetXSs = LayoutEngine.tieEndpointXSs(columnXSs, 1, false, ElementType.CROTCHET);
        var expectedDifferenceSs = ElementType.SEMIBREVE.getElementWidthSs()
            - ElementType.CROTCHET.getElementWidthSs();

        assertThat(expectedDifferenceSs)
            .as("precondition: the whole notehead really is the wider glyph")
            .isPositive();
        assertThat(wholeNoteXSs - crotchetXSs)
            .describedAs("the left endpoint moves right by the whole head's extra width, nothing else")
            .isCloseTo(expectedDifferenceSs, within(TOLERANCE));
    }

    // T19b: A space note whose edge-seat row lands on a staff line is pushed past it, dropping the seat
    //      below the head box so the endpoints recede to the notehead center (center attach). Both
    //      endpoints share the pushed Y. SP_TIE_SPACE_CENTER (sp 1, a space) arcs down (stem up) onto the
    //      sp-2 line, so it is pushed by STAFF_LINE_TIE_CLEARANCE_GAP_SS beyond the head-box edge.
    @Test
    void testTieSpaceNotePushedPastLineSeatsAtCenterAttach() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_SPACE_CENTER);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_SPACE_CENTER);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        // Down arc (stem up): seat is the head-box edge plus the clearance push, past the sp-2 line.
        var arcSignSs = 1;
        var expectedYSs = Staff.spToSs(SP_TIE_SPACE_CENTER)
            + arcSignSs * (Staff.STAFF_POSITION_OFFSET_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS);

        assertThat(tieLayout.startYSs())
            .describedAs("space note on a line edge-row is pushed past it to clear the line")
            .isCloseTo(expectedYSs, within(TOLERANCE));

        assertThat(tieLayout.endYSs())
            .describedAs("both endpoints share the pushed seat Y")
            .isCloseTo(expectedYSs, within(TOLERANCE));

        // Center attach: the endpoint has receded to the notehead center plus note-head-gap.
        var note1XSs = result.getElementXSs(note1);
        var expectedStartXSs =
            note1XSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2 + LayoutEngine.NOTE_HEAD_GAP_SS;

        assertThat(tieLayout.startXSs())
            .describedAs("a seat below the head box attaches at the notehead center (center attach)")
            .isCloseTo(expectedStartXSs, within(TOLERANCE));

        // Right endpoint (mirror image): facing edge is the end note's own center, minus the gap.
        var note2XSs = result.getElementXSs(note2);
        var expectedEndXSs =
            note2XSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2 - LayoutEngine.NOTE_HEAD_GAP_SS;

        assertThat(tieLayout.endXSs())
            .describedAs("center-attach endXSs mirrors startXSs: notehead center minus note-head-gap")
            .isCloseTo(expectedEndXSs, within(TOLERANCE));
    }

    // T23: Tie direction: stem-up note (isUpper=true, direction=+1) has arc bulging downward.
    //      For Y-increases-down coordinates: cp1YSs > startYSs confirms the arc goes below the note.
    @Test
    void testTieDirectionStemUpNoteArcBulgesDown() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_NOTE);  // sp > 0 → stem up → direction=+1
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_NOTE);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        assertThat(tieLayout.cp1YSs())
            .describedAs("stem-up tie outer control point must be below (larger Y than) start endpoint")
            .isGreaterThan(tieLayout.startYSs());
        assertThat(tie.isAbove())
            .describedAs("Phase 5: isAbove() must agree with the rendered (downward) arc — both-up ties are not above")
            .isFalse();
    }

    // T23b: Tie direction: stem-down note arc bulges upward, and isAbove() agrees with the render.
    //      Mirror of T23 for the opposite branch — guards the corrected inversion in both directions.
    @Test
    void testTieDirectionStemDownNoteArcBulgesUpAndIsAboveAgrees() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_NOTE_STEM_DOWN);  // sp < 0 → stem down → direction=-1
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_NOTE_STEM_DOWN);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        assertThat(tieLayout.cp1YSs())
            .describedAs("stem-down tie outer control point must be above (smaller Y than) start endpoint")
            .isLessThan(tieLayout.startYSs());
        assertThat(tie.isAbove())
            .describedAs("Phase 5: isAbove() must agree with the rendered (upward) arc — both-down ties are above")
            .isTrue();
    }

    // T31: A note on a staff line seats its tie STAFF_LINE_TIE_CLEARANCE_GAP_SS into the adjacent space,
    //      clearing its own line. SP_TIE_LINE_DOWN_ARC is a line position (even sp) with sp > 0 → stem up
    //      → arcSignSs=+1; the on-line seat is fixed regardless of tie width, so both endpoints sit exactly
    //      the clearance below the note's line.
    @Test
    void testTieOnLineNoteSeatsClearanceIntoAdjacentSpace() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_LINE_DOWN_ARC);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_LINE_DOWN_ARC);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        var expectedYSs = Staff.spToSs(SP_TIE_LINE_DOWN_ARC) + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS;

        assertThat(tieLayout.startYSs())
            .describedAs("on-line note seats its tie the clearance below its own line")
            .isCloseTo(expectedYSs, within(TOLERANCE));
        assertThat(tieLayout.endYSs())
            .describedAs("both endpoints share the same seat")
            .isCloseTo(expectedYSs, within(TOLERANCE));
    }

    // T32: A short arc whose body already clears the nearest staff line keeps its natural height.
    @Test
    void testTieAlreadyClearOfStaffLinesKeepsNaturalHeight() {
        var baseYSs = Staff.spToSs(SP_TIE_SPACE_CENTER);

        var heightSs = LayoutEngine.tieLineAvoidedHeightSs(baseYSs, 1, TIE_TEST_CLEAR_HEIGHT_SS);

        assertThat(heightSs)
            .describedAs("an arc already clear of staff lines must keep its natural height")
            .isCloseTo(TIE_TEST_CLEAR_HEIGHT_SS, within(TOLERANCE));
    }

    // T36: A moderately tall arc near a line is flattened so its outer edge sits exactly the outer-edge
    //      clearance below the line (fit-below), and the flattened height is below the natural height.
    @Test
    void testTieFlattenedToKeepOuterEdgeBelowLine() {
        var baseYSs = Staff.spToSs(SP_TIE_SPACE_CENTER);
        var halfStrokeSs = LayoutEngine.TIE_OUTLINE_THICKNESS_SS / 2;
        var nearestLineYSs = (double) Math.round(
            baseYSs + LayoutEngine.TIE_APEX_CONTROL_REACH * TIE_TEST_FLATTEN_HEIGHT_SS);
        var lineDistSs = nearestLineYSs - baseYSs;

        var heightSs = LayoutEngine.tieLineAvoidedHeightSs(baseYSs, 1, TIE_TEST_FLATTEN_HEIGHT_SS);

        assertThat(heightSs)
            .describedAs("flattened height must be below the natural height")
            .isLessThan(TIE_TEST_FLATTEN_HEIGHT_SS);

        var outerEdgeDistSs = LayoutEngine.TIE_APEX_CONTROL_REACH
            * (heightSs + LayoutEngine.TIE_MID_THICKNESS_SS) + halfStrokeSs;
        assertThat(outerEdgeDistSs)
            .describedAs("outer edge must sit exactly TIE_OUTER_EDGE_LINE_CLEARANCE_SS below the line")
            .isCloseTo(lineDistSs - LayoutEngine.TIE_OUTER_EDGE_LINE_CLEARANCE_SS, within(TOLERANCE));
    }

    // T37: A tall arc poking past a line is heightened so the top of its stroked arc rises the fixed ink
    //      height above the endpoints (clear-above), exceeding the natural height.
    @Test
    void testTieHeightenedToFixedInkHeightAboveLine() {
        var baseYSs = Staff.spToSs(SP_TIE_SPACE_CENTER);

        var heightSs = LayoutEngine.tieLineAvoidedHeightSs(baseYSs, 1, TIE_TEST_HEIGHTEN_HEIGHT_SS);

        assertThat(heightSs)
            .describedAs("heightened height must exceed the natural height")
            .isGreaterThan(TIE_TEST_HEIGHTEN_HEIGHT_SS);

        assertThat(heightenedInkHeightSs(heightSs))
            .describedAs("stroked arc top must rise exactly TIE_HEIGHTENED_INK_HEIGHT_SS above the endpoints")
            .isCloseTo(LayoutEngine.TIE_HEIGHTENED_INK_HEIGHT_SS, within(TOLERANCE));
    }

    // T38: An arc whose natural apex pokes just past the line is lifted over it (heightened), even when
    //      flattening under it would be a smaller change. Matches LilyPond: the arc stays on the side its
    //      apex favors rather than being squashed under the line.
    @Test
    void testTiePokingPastLineIsHeightenedNotFlattened() {
        var baseYSs = Staff.spToSs(SP_TIE_SPACE_CENTER);

        var heightSs = LayoutEngine.tieLineAvoidedHeightSs(baseYSs, 1, TIE_TEST_POKE_HEIGHT_SS);

        assertThat(heightSs)
            .describedAs("an arc whose apex pokes past the line must be heightened over it, not flattened under")
            .isGreaterThan(TIE_TEST_POKE_HEIGHT_SS);

        assertThat(heightenedInkHeightSs(heightSs))
            .describedAs("stroked arc top must rise exactly TIE_HEIGHTENED_INK_HEIGHT_SS above the endpoints")
            .isCloseTo(LayoutEngine.TIE_HEIGHTENED_INK_HEIGHT_SS, within(TOLERANCE));
    }

    // T39 (Phase 4, #503): the natural slur_height growth curve — zero at zero width, strictly
    //      increasing, and saturating strictly below the asymptotic height limit. Pins the wiring of
    //      BezierBow.height (input scaling + saturation), which the direct avoidance tests bypass by
    //      passing literal heights.
    @Test
    void testSlurHeightGrowsMonotonicallyAndSaturatesBelowLimit() {
        assertThat(BezierBow.height(0.0, LayoutEngine.TIE_RATIO, LayoutEngine.TIE_HEIGHT_LIMIT_SS))
            .describedAs("a zero-width tie has zero arc height")
            .isCloseTo(0.0, within(TOLERANCE));

        var narrowHeightSs = BezierBow.height(
            TIE_TEST_NARROW_WIDTH_SS, LayoutEngine.TIE_RATIO, LayoutEngine.TIE_HEIGHT_LIMIT_SS);
        var wideHeightSs = BezierBow.height(
            TIE_TEST_WIDE_WIDTH_SS, LayoutEngine.TIE_RATIO, LayoutEngine.TIE_HEIGHT_LIMIT_SS);

        assertThat(narrowHeightSs)
            .describedAs("a narrow tie must arc lower than a wide one")
            .isPositive()
            .isLessThan(wideHeightSs);

        assertThat(BezierBow.height(TIE_TEST_HUGE_WIDTH_SS, LayoutEngine.TIE_RATIO, LayoutEngine.TIE_HEIGHT_LIMIT_SS))
            .describedAs("arc height must saturate strictly below TIE_HEIGHT_LIMIT_SS")
            .isGreaterThan(wideHeightSs)
            .isLessThan(LayoutEngine.TIE_HEIGHT_LIMIT_SS);
    }

    // T40 (Phase 4, #503): the slur_shape control-point indent — zero at zero width, strictly
    //      increasing, always inside the tie (indent < width/2 for a real tie), saturating below its
    //      2 × TIE_HEIGHT_LIMIT_SS asymptote. Pins BezierBow.indent, which the avoidance tests never touch.
    @Test
    void testSlurIndentGrowsAndStaysInsideTheTie() {
        var maxFraction = LayoutEngine.TIE_SLUR_MAX_FRACTION;

        assertThat(BezierBow.indent(0.0, LayoutEngine.TIE_HEIGHT_LIMIT_SS, maxFraction))
            .describedAs("a zero-width tie has zero control-point indent")
            .isCloseTo(0.0, within(TOLERANCE));

        var narrowIndentSs = BezierBow.indent(TIE_TEST_NARROW_WIDTH_SS, LayoutEngine.TIE_HEIGHT_LIMIT_SS, maxFraction);
        var wideIndentSs = BezierBow.indent(TIE_TEST_WIDE_WIDTH_SS, LayoutEngine.TIE_HEIGHT_LIMIT_SS, maxFraction);

        assertThat(narrowIndentSs)
            .describedAs("indent grows with tie width and stays inside the tie (indent < width/2)")
            .isPositive()
            .isLessThan(TIE_TEST_NARROW_WIDTH_SS / 2)
            .isLessThan(wideIndentSs);

        var indentLimitSs = 2 * LayoutEngine.TIE_HEIGHT_LIMIT_SS;
        assertThat(BezierBow.indent(TIE_TEST_HUGE_WIDTH_SS, LayoutEngine.TIE_HEIGHT_LIMIT_SS, maxFraction))
            .describedAs("indent saturates strictly below 2 × TIE_HEIGHT_LIMIT_SS")
            .isGreaterThan(wideIndentSs)
            .isLessThan(indentLimitSs);
    }

    // T43 (Phase 4, #503): when the arc apex sits beyond the outermost staff line there is no real
    //      line to avoid, so tieLineAvoidedHeightSs returns the natural height unchanged (the
    //      early-return guard). Uses a baseline below the staff whose apex rounds to a ledger
    //      position outside ±STAFF_HALF_SS.
    @Test
    void testTieApexBeyondStaffKeepsNaturalHeight() {
        var heightSs = LayoutEngine.tieLineAvoidedHeightSs(
            TIE_TEST_BEYOND_STAFF_BASE_Y_SS, 1, TIE_TEST_CLEAR_HEIGHT_SS);

        assertThat(heightSs)
            .describedAs("an apex beyond the outermost staff line has no line to avoid")
            .isCloseTo(TIE_TEST_CLEAR_HEIGHT_SS, within(TOLERANCE));
    }

    // T44 (Phase 4, #503): the flatten fall-through — the apex fits below the near line, but the line
    //      is too close for even a flattened arc to clear it, so the arc is lifted over instead
    //      (heightened). Distinct from T38, which enters via an apex already poking past the line.
    @Test
    void testTieFlattenGivesUpAndHeightensWhenLineTooClose() {
        var heightSs = LayoutEngine.tieLineAvoidedHeightSs(
            TIE_TEST_TIGHT_LINE_BASE_Y_SS, 1, TIE_TEST_TIGHT_NATURAL_HEIGHT_SS);

        assertThat(heightSs)
            .describedAs("a line too close to flatten under must be cleared above (heightened)")
            .isGreaterThan(TIE_TEST_TIGHT_NATURAL_HEIGHT_SS);

        assertThat(heightenedInkHeightSs(heightSs))
            .describedAs("stroked arc top must rise exactly TIE_HEIGHTENED_INK_HEIGHT_SS above the endpoints")
            .isCloseTo(LayoutEngine.TIE_HEIGHTENED_INK_HEIGHT_SS, within(TOLERANCE));
    }

    // T42: tieSeatSs across the D4..B4 sweep (SongScribe coordinates, sp increasing downward). An
    //      inner on-line note seats the clearance into the adjacent space; an outer staff line seats
    //      outside the staff (TIE_OUTER_STAFF_LINE_SEAT_SS); a space note seats at the head-box edge,
    //      pushed a further clearance when its edge row is a real staff line but not when that row is
    //      off the staff. centerAttach follows: only a seat past the head-box edge recedes the
    //      endpoints to the notehead center.
    @Test
    void testTieSeatSsAcrossStaffPositions() {
        record SeatCase(int sp, int arcSignSs, double expectedSeatSs, boolean centerAttach) {}

        var onLineSeatSs = LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS;
        var outerLineSeatSs = LayoutEngine.TIE_OUTER_STAFF_LINE_SEAT_SS;
        var edgeSeatSs = Staff.STAFF_POSITION_OFFSET_SS;
        var pushedSeatSs = edgeSeatSs + onLineSeatSs;

        var cases = new SeatCase[] {
            new SeatCase(SP_TIE_MIDDLE_LINE, -1, onLineSeatSs, false),            // B4: middle line, arc up
            new SeatCase(SP_TIE_LINE_DOWN_ARC, 1, onLineSeatSs, false),           // G4: inner line, arc down
            new SeatCase(SP_TIE_BOTTOM_LINE, 1, outerLineSeatSs, true),           // E4: outer line, arc down (outside the staff)
            new SeatCase(SP_TIE_SPACE_CENTER, 1, pushedSeatSs, true),             // A4: space, edge row on a line
            new SeatCase(SP_TIE_SPACE_ABOVE_BOTTOM_LINE, 1, pushedSeatSs, true),  // F4: space, edge row on bottom line
            new SeatCase(SP_TIE_SPACE_BELOW_STAFF, 1, edgeSeatSs, false),         // D4: space below staff, no line
        };

        for (var testCase : cases) {
            var seatSs = LayoutEngine.tieSeatSs(testCase.sp(), testCase.arcSignSs(), false);

            assertThat(seatSs)
                .describedAs("seat at sp=%d, arcSign=%d".formatted(testCase.sp(), testCase.arcSignSs()))
                .isCloseTo(testCase.expectedSeatSs(), within(TOLERANCE));
            assertThat(seatSs > Staff.STAFF_POSITION_OFFSET_SS)
                .describedAs("center attach at sp=%d".formatted(testCase.sp()))
                .isEqualTo(testCase.centerAttach());
        }
    }

    /** Total stroked-ink height: endpoint stroke cap (halfStroke below the anchor) to the top of the apex stroke; staff spaces. */
    private static double heightenedInkHeightSs(double heightSs) {
        var halfStrokeSs = LayoutEngine.TIE_OUTLINE_THICKNESS_SS / 2;
        var topOfInkSs =
            LayoutEngine.TIE_APEX_CONTROL_REACH * (heightSs + LayoutEngine.TIE_MID_THICKNESS_SS) + halfStrokeSs;

        return topOfInkSs + halfStrokeSs;
    }

    // T34 (up-stem symmetry, mirrors T31 with the opposite arc sign; also Phase 6 task 3c baseline):
    //     tip-on-line clearance for an upward-bulging arc (stem-down note, SP_TIE_LINE_UP_ARC < 0 →
    //     arcSignSs=-1). SP_TIE_LINE_UP_ARC == SP_TIE_NOTE_STEM_DOWN, so this also doubles as the
    //     non-dotted baseline contrasted with T45: a note with no augmentation dot has none to move.
    @Test
    void testTieTipOnStaffLineClearedByExactTipClearanceForUpwardArc() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_LINE_UP_ARC);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_LINE_UP_ARC);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        var arcSignSs = -1;
        var expectedYSs =
            Staff.spToSs(SP_TIE_LINE_UP_ARC) + arcSignSs * LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS;

        assertThat(tieLayout.startYSs())
            .describedAs("on-line note seats its upward tie the clearance above its own line")
            .isCloseTo(expectedYSs, within(TOLERANCE));
        assertThat(tieLayout.endYSs())
            .describedAs("both endpoints share the same seat")
            .isCloseTo(expectedYSs, within(TOLERANCE));

        // Symmetry: the arc bulges upward (smaller/more negative Y) rather than downward.
        assertThat(tieLayout.cp1YSs())
            .describedAs("stem-down tie outer control point must be above (smaller Y than) start endpoint")
            .isLessThan(tieLayout.startYSs());

        var dotYOffsets = new ArrayList<Double>();
        NoteGeometry.forEachDotPosition(note1, false, note1.getDirection(), (x, y) -> dotYOffsets.add(y));
        assertThat(dotYOffsets)
            .describedAs("a non-dotted note has no augmentation dot to move")
            .isEmpty();
    }

    // T45 (Phase 6 task 3a): a down-stem dotted note's tie lifts a further TIE_DOT_ROW_NUDGE_SS to
    //      clear its augmentation dot. sp < 0 → stem down → the arc bulges upward, toward the dot's
    //      up-displaced row, so the seat row coincides with the dot row (tieSeatRowHasDot) and the
    //      plain on-line nudge is replaced by the dot-row lift. The dot itself is never repositioned:
    //      it stays on its own on-line row regardless of the tie.
    @Test
    void testDownStemDottedNoteLiftsTieByDotRowNudge() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_NOTE_STEM_DOWN);
        note1.setDotCount(1);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_NOTE_STEM_DOWN);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        var arcSignSs = -1; // sp < 0 → stem down → arc bulges upward, toward the dot's up-displaced row
        // Dot-row coincidence seats at the head-box edge plus the dot-row lift, clearing the dot.
        var expectedYSs = Staff.spToSs(SP_TIE_NOTE_STEM_DOWN)
            + arcSignSs * (Staff.STAFF_POSITION_OFFSET_SS + LayoutEngine.TIE_DOT_ROW_NUDGE_SS);

        assertThat(tieLayout.startYSs())
            .describedAs("dot-row coincidence seats the tie at the head-box edge plus TIE_DOT_ROW_NUDGE_SS to clear the dot")
            .isCloseTo(expectedYSs, within(TOLERANCE));
        assertThat(tieLayout.endYSs())
            .describedAs("both endpoints share the same dot-row seat")
            .isCloseTo(expectedYSs, within(TOLERANCE));

        var dotYOffsets = new ArrayList<Double>();
        NoteGeometry.forEachDotPosition(note1, false, note1.getDirection(), (x, y) -> dotYOffsets.add(y));
        assertThat(dotYOffsets)
            .describedAs("the augmentation dot must stay on its own on-line row, unmoved by the tie")
            .containsExactly(NoteGeometry.DOT_ON_LINE_Y_SHIFT_SS);
    }

    // T46 (Phase 6 task 3b): an up-stem dotted note's tie arcs away from its dot — the dot is always
    //      displaced toward the top row, and an up-stem arc bulges downward, toward the opposite row —
    //      so the seat row never coincides with the dot row, and the tie keeps the plain on-line nudge
    //      instead of the dot-row lift. The dot again stays on its own on-line row.
    @Test
    void testUpStemDottedNoteTieNotLiftedByDotRowNudge() {
        var line = detachedLine();
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setStaffPosition(SP_TIE_NOTE);
        note1.setDotCount(1);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setStaffPosition(SP_TIE_NOTE);
        line.addElement(note1);
        line.addElement(note2);
        var tie = new Tie(note1, note2);
        line.addRangeElement(tie);

        var result = require(engine().layout(line), "LayoutResult");
        var tieLayout = require(result.getTieLayout(tie), "TieLayout");

        var arcSignSs = 1; // sp > 0 → stem up → arc bulges downward, away from the dot's up-displaced row
        // No dot-row coincidence: the on-line note keeps its plain clearance seat, not the dot-row lift.
        var expectedYSs = Staff.spToSs(SP_TIE_NOTE) + arcSignSs * LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS;

        assertThat(tieLayout.startYSs())
            .describedAs("no dot-row coincidence: the tie keeps the plain on-line clearance seat, not the dot-row lift")
            .isCloseTo(expectedYSs, within(TOLERANCE));
        assertThat(tieLayout.endYSs())
            .describedAs("both endpoints share the same seat")
            .isCloseTo(expectedYSs, within(TOLERANCE));

        var dotYOffsets = new ArrayList<Double>();
        NoteGeometry.forEachDotPosition(note1, false, note1.getDirection(), (x, y) -> dotYOffsets.add(y));
        assertThat(dotYOffsets)
            .describedAs("the augmentation dot must stay on its own on-line row, unmoved by the tie")
            .containsExactly(NoteGeometry.DOT_ON_LINE_Y_SHIFT_SS);
    }

    // T24: createHeaderElements null keyType → key signature stored with KeyType.NONE
    @Test
    void testCreateHeaderElementsNullKeyTypeDefaultsToNone() {
        // detachedLine() returns a Line with null keyType (no key set).
        var line = detachedLine();

        var result = require(engine().layout(line), "LayoutResult");
        var keySig = require(result.getKeySignature(), "KeySignature");

        assertThat(keySig.getKeyType())
            .describedAs("null keyType in line must produce KeyType.NONE in key signature")
            .isEqualTo(KeyType.NONE);
    }

    // T30: beamCount → flag levels per note type (QUAVER 1, SEMIQUAVER 2, DEMI_SEMIQUAVER 3)
    @Test
    void testBeamCountReturnsFlagLevelPerNoteType() {
        assertThat(LayoutEngine.beamCount(ElementType.QUAVER.newInstance()))
            .describedAs("QUAVER has one flag")
            .isEqualTo(QUAVER_BEAMS);
        assertThat(LayoutEngine.beamCount(ElementType.SEMIQUAVER.newInstance()))
            .describedAs("SEMIQUAVER has two flags")
            .isEqualTo(SEMIQUAVER_BEAMS);
        assertThat(LayoutEngine.beamCount(ElementType.DEMI_SEMIQUAVER.newInstance()))
            .describedAs("DEMI_SEMIQUAVER has three flags")
            .isEqualTo(DEMI_SEMIQUAVER_BEAMS);
    }

    /**
     * Builds a beamed line of quavers (or another beamable type) at the given staff
     * positions and returns the notes in order.
     */
    private static List<StaffElement> beamedNotes(Line line, ElementType type, int... staffPositions) {
        var notes = new ArrayList<StaffElement>(staffPositions.length);

        for (var staffPosition : staffPositions) {
            var note = type.newInstance();
            note.setStaffPosition(staffPosition);
            line.addElement(note);
            notes.add(note);
        }

        line.addBeaming(new Beam(notes.get(0), notes.get(notes.size() - 1)));
        return notes;
    }

    /**
     * @param result the laid-out line
     * @param note   a beamed note of that line
     * @return the note's stem length in staff spaces
     */
    private static double stemLengthSs(LayoutResult result, StaffElement note) {
        var stem = require(result.getStemLayout(note), "StemLayout");
        return stem.bottomYSs() - stem.topYSs();
    }

    // T47 (#579): the concave triplet g8 d'8 e,8 gets a flat beam, and the middle
    //      note — the one nearest the beam — ends up with the shortest stem, which
    //      stays above LilyPond's extreme minimum stem length.
    @Test
    void testConcaveTripletGetsAFlatBeamWithTheMiddleStemShortest() {
        var line = detachedLine();
        var notes = beamedNotes(line, ElementType.QUAVER, SP_579_FIRST, SP_579_MIDDLE, SP_579_LAST);

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        var firstLengthSs = stemLengthSs(result, notes.get(0));
        var middleLengthSs = stemLengthSs(result, notes.get(1));
        var lastLengthSs = stemLengthSs(result, notes.get(2));

        // The stem reaches the beam's outer edge, hence the half-thickness term.
        var extremeMinimumSs = BeamScoring.BEAMED_EXTREME_MINIMUM_FREE_LENGTHS_SS[0]
            + LineThickness.BEAM_THICKNESS_SS / 2.0;

        assertThat(beamLayout.slope())
            .describedAs("a concave group is beamed flat")
            .isCloseTo(0.0, within(TOLERANCE));
        assertThat(middleLengthSs)
            .describedAs("the middle note is nearest the beam, so its stem is the shortest")
            .isLessThan(firstLengthSs)
            .isLessThan(lastLengthSs);
        assertThat(middleLengthSs)
            .describedAs("even the shortest stem clears the extreme minimum stem length")
            .isGreaterThanOrEqualTo(extremeMinimumSs - TOLERANCE);
    }

    /** A beamed stem that quanting drove past the forced-shortening cap, with its layout. */
    private record SubFloorStem(
        String contour,
        LayoutResult result,
        StaffElement note,
        LayoutResult.StemLayout stem) {
    }

    /**
     * Searches {@link #SP_SUB_FLOOR_CONTOURS} for a group whose middle stem quants past
     * {@link NoteGeometry#MAX_FORCED_SHORTEN_SS}, which is the precondition
     * {@link #testBeamedStemShorterThanTheForcedStemFloorKeepsItsQuantedLength} needs in order
     * to say anything. Every listed contour puts its shortest stem in the middle.
     *
     * @return the first qualifying contour's middle stem, or null if none of them qualify
     */
    private static @Nullable SubFloorStem findSubFloorStem() {
        for (var staffPositions : SP_SUB_FLOOR_CONTOURS) {
            var line = detachedLine();
            var notes = beamedNotes(line, ElementType.QUAVER, staffPositions);
            var result = engine().layout(line);

            if (result == null) {
                continue;
            }

            var note = notes.get(1);
            var stem = result.getStemLayout(note);

            if (stem != null && stem.forcedShorteningSs() > NoteGeometry.MAX_FORCED_SHORTEN_SS) {
                return new SubFloorStem(Arrays.toString(staffPositions), result, note, stem);
            }
        }

        return null;
    }

    // T48 (Phase 6 task 7): quanting may shorten a beamed stem past the forced-stem
    //      floor. The layout must express that length verbatim, and NoteRenderer
    //      must not floor it back up to FORCED_STEM_FLOOR_SS for a beamed note —
    //      the beam stays where quanting put it, so a floored stem would overshoot
    //      it. This pins the layout half of that contract; the renderer applies the
    //      floor only on its unbeamed branch (NoteRenderer.renderStem).
    @Test
    void testBeamedStemShorterThanTheForcedStemFloorKeepsItsQuantedLength() {
        var subFloor = require(
            findSubFloorStem(),
            "a contour whose quanting shortens a beamed stem past the forced-shortening cap "
                + "(no entry in SP_SUB_FLOOR_CONTOURS still reaches that regime)");
        var stem = subFloor.stem();

        // What NoteRenderer.renderStem computes for a beamed note, from the same fields.
        var renderedLengthSs =
            SMuFLConstants.STEM_LENGTH_SS + stem.lengtheningSs() - stem.forcedShorteningSs();

        assertThat(renderedLengthSs)
            .describedAs(
                "contour %s: the rendered length is the quanted length, not the floor",
                subFloor.contour())
            .isLessThan(NoteGeometry.FORCED_STEM_FLOOR_SS)
            .isCloseTo(stemLengthSs(subFloor.result(), subFloor.note()), within(TOLERANCE));
    }

    // T49 (Phase 6 task 8a): a group with a forced-direction member is scored with a
    //      non-zero forcedFraction, which shortens its stems. Solving the same stems
    //      with no forced members must put the beam somewhere else — otherwise the
    //      forced count never reached BeamScoring.
    @Test
    void testForcedDirectionGroupIsScoredWithItsForcedFraction() {
        var line = detachedLine();
        var notes = beamedNotes(line, ElementType.SEMIQUAVER, SP_FORCED_ABOVE, SP_FORCED_BELOW);

        var beam = require(line.findBeamAt(0), "Beam at index 0");
        var result = require(engine().layout(line), "LayoutResult");
        var beamLayout = require(result.getBeamLayout(beam), "BeamLayout");

        var firstXSs = result.getElementXSs(notes.get(0));
        var stems = new ArrayList<BeamScoring.StemInput>(notes.size());

        for (var note : notes) {
            stems.add(new BeamScoring.StemInput(
                result.getElementXSs(note) - firstXSs,
                -Staff.spToSs(note.getStaffPosition()),
                -note.getStaffPosition(),
                BeamMath.beamCount(note)));
        }

        var dirSign = beamLayout.stemsUp() ? 1 : -1;
        var forced = BeamScoring.solve(stems, dirSign, ROW_49_FORCED_FRACTION);
        var unforced = BeamScoring.solve(stems, dirSign, NO_FORCED_STEMS);
        var forcedStartYSs =
            -forced.leftYUpSs() - dirSign * LineThickness.BEAM_THICKNESS_SS / 2.0;

        // Guards the sign of dirSign below, which flips the direction of both comparisons.
        assertThat(beamLayout.stemsUp())
            .describedAs("the group straddles the middle line, so the stems point down")
            .isFalse();
        assertThat(beamLayout.startYSs())
            .describedAs("the beam is placed with the group's forced fraction")
            .isCloseTo(forcedStartYSs, within(TOLERANCE));

        // Closer to the noteheads means a smaller Y in direction-multiplied space.
        assertThat(dirSign * forced.leftYUpSs())
            .describedAs("forcing a stem pulls the beam's left edge toward the noteheads")
            .isLessThan(dirSign * unforced.leftYUpSs());
        assertThat(dirSign * forced.rightYUpSs())
            .describedAs("forcing a stem pulls the beam's right edge toward the noteheads")
            .isLessThan(dirSign * unforced.rightYUpSs());
    }

    // T50 (Phase 6 task 8b): an all-natural-direction group has no forced stems, and
    //      laying the same content out twice yields identical beam geometry.
    @Test
    void testUnforcedGroupGeometryIsDeterministic() {
        var firstLine = detachedLine();
        beamedNotes(firstLine, ElementType.QUAVER, SP_BEAM_BELOW_1, SP_BEAM_BELOW_2);
        var secondLine = detachedLine();
        beamedNotes(secondLine, ElementType.QUAVER, SP_BEAM_BELOW_1, SP_BEAM_BELOW_2);

        var firstBeam = require(firstLine.findBeamAt(0), "Beam at index 0");
        var secondBeam = require(secondLine.findBeamAt(0), "Beam at index 0");
        var firstLayout = require(
            require(engine().layout(firstLine), "LayoutResult").getBeamLayout(firstBeam),
            "BeamLayout");
        var secondLayout = require(
            require(engine().layout(secondLine), "LayoutResult").getBeamLayout(secondBeam),
            "BeamLayout");

        assertThat(secondLayout.slope())
            .describedAs("beam slope is deterministic")
            .isCloseTo(firstLayout.slope(), within(TOLERANCE));
        assertThat(secondLayout.startYSs())
            .describedAs("beam position is deterministic")
            .isCloseTo(firstLayout.startYSs(), within(TOLERANCE));
    }

    @Nested
    class UnbeamedStemLengthening {

        // T25a: Note within staff (|sp| ≤ 7): natural stem already clears center → no lengthening
        @Test
        void testNoteWithinStaffHasNoLengthening() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(SP_BELOW_MIDDLE);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("note within staff needs no stem lengthening")
                .isCloseTo(0.0, within(TOLERANCE));
        }

        // T25b: Down-stem note 2 ledger lines above (sp=-8); lengtheningSs = 0.5, tip reaches Y=0
        @Test
        void testDownStemTwoLedgerLinesAboveHasLengthening() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(SP_LEDGER_ABOVE_2);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("2 ledger lines above → lengtheningSs")
                .isCloseTo(LENGTHENING_TWO_LEDGER_LINES, within(TOLERANCE));
            assertThat(stem.bottomYSs())
                .describedAs("extended down-stem tip must reach staff center (Y=0)")
                .isCloseTo(0.0, within(TOLERANCE));
        }

        // T25c: Down-stem note 3 ledger lines above (sp=-10); lengtheningSs = 1.5, tip reaches Y=0
        @Test
        void testDownStemThreeLedgerLinesAboveHasLengthening() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(SP_LEDGER_ABOVE_3);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("3 ledger lines above → lengtheningSs")
                .isCloseTo(LENGTHENING_THREE_LEDGER_LINES, within(TOLERANCE));
            assertThat(stem.bottomYSs())
                .describedAs("extended down-stem tip must reach staff center (Y=0)")
                .isCloseTo(0.0, within(TOLERANCE));
        }

        // T25d: Up-stem note 2 ledger lines below (sp=+8); lengtheningSs = 0.5, tip reaches Y=0
        @Test
        void testUpStemTwoLedgerLinesBelowHasLengthening() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(SP_LEDGER_BELOW_2);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("2 ledger lines below → lengtheningSs")
                .isCloseTo(LENGTHENING_TWO_LEDGER_LINES, within(TOLERANCE));
            assertThat(stem.topYSs())
                .describedAs("extended up-stem tip must reach staff center (Y=0)")
                .isCloseTo(0.0, within(TOLERANCE));
        }

        // T25e: Grace note at a ledger-line position; lengthening is always 0.0 and the stem keeps its grace length
        @Test
        void testGraceNoteAtLedgerLineHasNoLengthening() {
            var line = detachedLine();
            var element = ElementType.GRACE_QUAVER.newInstance();
            element.setStaffPosition(SP_LEDGER_ABOVE_2);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("grace notes are exempt from stem lengthening")
                .isCloseTo(0.0, within(TOLERANCE));

            // Grace notes auto-direction to stem-up; an unextended grace stem keeps its grace length.
            var elementYSs = Staff.spToSs(SP_LEDGER_ABOVE_2);
            assertThat(stem.topYSs())
                .describedAs("grace stem-up top Y keeps grace length (no lengthening)")
                .isCloseTo(elementYSs - SMuFLConstants.GRACE_NOTE_STEM_LENGTH_SS, within(TOLERANCE));
            assertThat(stem.bottomYSs())
                .describedAs("grace stem-up bottom Y sits at the notehead")
                .isCloseTo(elementYSs, within(TOLERANCE));
        }

        // T25f: At the exact threshold (sp=7 → |elementYSs| = MIN_STEM_SS), no lengthening is applied
        @Test
        void testNoteAtThresholdHasNoLengthening() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(SP_LENGTHENING_THRESHOLD);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("note exactly MIN_STEM_SS from center needs no lengthening")
                .isCloseTo(0.0, within(TOLERANCE));
        }

        // T25g: A manual stem pointing AWAY from center (forced up on a note above the staff) is never lengthened
        @Test
        void testManualStemAwayFromCenterIsNotLengthened() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(SP_LEDGER_ABOVE_2);
            element.setStemDirectionAuto(false);
            element.setUpper(true);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("stem pointing away from center is not lengthened")
                .isCloseTo(0.0, within(TOLERANCE));

            // Tip is not lengthened toward center, but this forced up-stem (opposing defaultDirection
            // DOWN at sp <= 0) is still shortened (Ross & Gourlay) -- a distinct, disjoint concern.
            var elementYSs = Staff.spToSs(SP_LEDGER_ABOVE_2);
            var forcedShorteningSs =
                NoteGeometry.forcedShorteningSs(SP_LEDGER_ABOVE_2, StaffElement.Direction.UP, false);
            assertThat(stem.topYSs())
                .describedAs("away-from-center up-stem is not lengthened, but is forced-shortened")
                .isCloseTo(elementYSs - SMuFLConstants.STEM_LENGTH_SS + forcedShorteningSs, within(TOLERANCE));
        }
    }
}
