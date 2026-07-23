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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Attribution;
import songscribe.dom.Beam;
import songscribe.dom.Clef;
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

    /** Enough notes to exceed STAFF_RIGHT_MARGIN_SS even after maximum compression. */
    private static final int OVERSTUFFED_NOTE_COUNT = 100;

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

    // Row 22 — flat-beam snapping constant
    /** Odd staff position; equal for both notes in the beam so the slope is 0. */
    private static final int SP_FLAT_BEAM = 3;

    // Row 24 — stub direction constants
    /** Staff position shared by both notes in the stub-direction beam test. */
    private static final int SP_STUB_NOTE = 2;

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

    // Stem-lengthening constants — notes beyond MIN_STEM_SS (3.5 ss) from centre
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

        var expectedXSs = LayoutEngine.CLEF_X_POSITION_SS
            + SMuFLConstants.G_CLEF_WIDTH_SS
            + new Clef().getMarginRightSs();

        assertThat(keySig.getXSs()).isCloseTo(expectedXSs, within(TOLERANCE));
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
        var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());

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

    // T6: Un-justifiable line returns null from layout() with a non-null getLastError()
    @Test
    void testOverstuffedLineReturnsNullWithError() {
        var line = detachedLine();

        for (var i = 0; i < OVERSTUFFED_NOTE_COUNT; i++) {
            line.addElement(ElementType.CROTCHET.newInstance());
        }

        var engine = engine();
        var result = engine.layout(line, false);

        assertThat(result).describedAs("layout() result for overstuffed line").isNull();
        assertThat(engine.getLastError()).describedAs("getLastError() for overstuffed line").isNotNull();
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

    // T19b: A space note whose edge-seat row lands on a staff line is pushed past it, dropping the seat
    //      below the head box so the endpoints recede to the notehead centre (center attach). Both
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

        // Center attach: the endpoint has receded to the notehead centre plus note-head-gap.
        var note1XSs = result.getElementXSs(note1);
        var expectedStartXSs =
            note1XSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2 + LayoutEngine.NOTE_HEAD_GAP_SS;

        assertThat(tieLayout.startXSs())
            .describedAs("a seat below the head box attaches at the notehead centre (center attach)")
            .isCloseTo(expectedStartXSs, within(TOLERANCE));

        // Right endpoint (mirror image): facing edge is the end note's own centre, minus the gap.
        var note2XSs = result.getElementXSs(note2);
        var expectedEndXSs =
            note2XSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2 - LayoutEngine.NOTE_HEAD_GAP_SS;

        assertThat(tieLayout.endXSs())
            .describedAs("center-attach endXSs mirrors startXSs: notehead centre minus note-head-gap")
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
    //      endpoints to the notehead centre.
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

    @Nested
    class UnbeamedStemLengthening {

        // T25a: Note within staff (|sp| ≤ 7): natural stem already clears centre → no lengthening
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
                .describedAs("extended down-stem tip must reach staff centre (Y=0)")
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
                .describedAs("extended down-stem tip must reach staff centre (Y=0)")
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
                .describedAs("extended up-stem tip must reach staff centre (Y=0)")
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
                .describedAs("note exactly MIN_STEM_SS from centre needs no lengthening")
                .isCloseTo(0.0, within(TOLERANCE));
        }

        // T25g: A manual stem pointing AWAY from centre (forced up on a note above the staff) is never lengthened
        @Test
        void testManualStemAwayFromCentreIsNotLengthened() {
            var line = detachedLine();
            var element = ElementType.CROTCHET.newInstance();
            element.setStaffPosition(SP_LEDGER_ABOVE_2);
            element.setStemDirectionAuto(false);
            element.setUpper(true);
            line.addElement(element);

            var result = require(engine().layout(line), "LayoutResult");
            var stem = require(result.getStemLayout(element), "StemLayout");

            assertThat(stem.lengtheningSs())
                .describedAs("stem pointing away from centre is not lengthened")
                .isCloseTo(0.0, within(TOLERANCE));

            // Tip is not lengthened toward centre, but this forced up-stem (opposing defaultDirection
            // DOWN at sp <= 0) is still shortened (Ross & Gourlay) -- a distinct, disjoint concern.
            var elementYSs = Staff.spToSs(SP_LEDGER_ABOVE_2);
            var forcedShorteningSs =
                NoteGeometry.forcedShorteningSs(SP_LEDGER_ABOVE_2, StaffElement.Direction.UP, false);
            assertThat(stem.topYSs())
                .describedAs("away-from-centre up-stem is not lengthened, but is forced-shortened")
                .isCloseTo(elementYSs - SMuFLConstants.STEM_LENGTH_SS + forcedShorteningSs, within(TOLERANCE));
        }
    }
}
