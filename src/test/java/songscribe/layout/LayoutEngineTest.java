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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Clef;
import songscribe.dom.ScaleContext;
import songscribe.font.DocumentFonts;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LyricConnectorLayout;
import songscribe.layout.LyricRenderMetrics;
import songscribe.smufl.Engraving;

@SuppressWarnings("DataFlowIssue")
class LayoutEngineTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double CLEF_X_POSITION_SS = 0.625;
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
    /** Extreme below-midline sp; creates a large raw slope that the hyperbolic dampener saturates. */
    private static final int SP_SLOPE_EXTREME_LOW = 16;

    // Row 21 — stem-reduction invariant constants
    /** First note of the non-linear beam contour; also the anchor (min sp → highest pitch). */
    private static final int SP_CONTOUR_FIRST = 2;
    /** Middle note of the non-linear beam contour; positioned below the linear interpolation. */
    private static final int SP_CONTOUR_MIDDLE = 8;
    /** Last note of the non-linear beam contour. */
    private static final int SP_CONTOUR_LAST = 4;

    private static LayoutEngine engine() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var hyphenWidthSs = ScaleContext.textWidthSs(lyricsFont, "-");
        var spaceWidthSs = ScaleContext.textWidthSs(lyricsFont, " ");
        var metrics = new LyricRenderMetrics(
            lyricsFont, ScaleContext.scaleFont(lyricsFont), hyphenWidthSs, spaceWidthSs);
        return new LayoutEngine(metrics, STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultsFromPrefs());
    }

    /** Asserts value is not null and returns it non-null for NullAway. */
    @SuppressWarnings("NullAway")
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    // T1: layout() stores a Clef at CLEF_X_POSITION_SS
    @Test
    void testLayoutStoresClefAtStandardPosition() {
        var result = require(engine().layout(detachedLine()), "LayoutResult");
        var clef = require(result.getClef(), "Clef");

        assertThat(clef.getXSs()).isCloseTo(CLEF_X_POSITION_SS, within(TOLERANCE));
    }

    // T2: layout() stores a KeySignature immediately after the clef with correct key data
    @Test
    void testLayoutStoresKeySignatureAfterClef() {
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(3);

        var result = require(engine().layout(line), "LayoutResult");
        var keySig = require(result.getKeySignature(), "KeySignature");

        var expectedXSs = CLEF_X_POSITION_SS
            + Engraving.G_CLEF_WIDTH_SS
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

        var elementYSs = StaffExtents.spToSs(SP_BELOW_MIDDLE);
        assertThat(stem.topYSs()).describedAs("stem-up top Y").isCloseTo(elementYSs - NoteGeometry.STEM_LENGTH_SS, within(TOLERANCE));
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

        var elementYSs = StaffExtents.spToSs(SP_ABOVE_MIDDLE);
        assertThat(stem.topYSs()).describedAs("stem-down top Y").isCloseTo(elementYSs, within(TOLERANCE));
        assertThat(stem.bottomYSs()).describedAs("stem-down bottom Y").isCloseTo(elementYSs + NoteGeometry.STEM_LENGTH_SS, within(TOLERANCE));
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

        var elementYSs = StaffExtents.spToSs(SP_ABOVE_MIDDLE_GRACE);
        assertThat(stem.topYSs()).describedAs("grace stem-up top Y").isCloseTo(elementYSs - NoteGeometry.GRACE_NOTE_STEM_LENGTH_SS, within(TOLERANCE));
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

        var elementYSs = StaffExtents.spToSs(SP_BELOW_MIDDLE_MANUAL);
        assertThat(stem.topYSs()).describedAs("manual stem-down top Y").isCloseTo(elementYSs, within(TOLERANCE));
        assertThat(stem.bottomYSs()).describedAs("manual stem-down bottom Y").isCloseTo(elementYSs + NoteGeometry.STEM_LENGTH_SS, within(TOLERANCE));
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

    // T14: Beam slope with a very large pitch difference is dampened below BEAM_SLOPE_MAX
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

        assertThat(Math.abs(beamLayout.slope()))
            .describedAs("dampened slope absolute value must be strictly below the saturation limit")
            .isLessThan(LayoutEngine.BEAM_SLOPE_MAX);
    }

    // T15: Every stem in a beamed group is at least the minimum stem length after slope reduction
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

        for (var sp : new int[]{SP_CONTOUR_FIRST, SP_CONTOUR_MIDDLE, SP_CONTOUR_LAST}) {
            var note = (sp == SP_CONTOUR_FIRST) ? note1 : (sp == SP_CONTOUR_MIDDLE) ? note2 : note3;
            var stem = require(result.getStemLayout(note), "StemLayout at sp=" + sp);
            assertThat(stem.bottomYSs() - stem.topYSs())
                .describedAs("stem length at sp=%d must be ≥ minimum stem length".formatted(sp))
                .isGreaterThanOrEqualTo(NoteGeometry.STEM_LENGTH_SS - TOLERANCE);
        }
    }
}
