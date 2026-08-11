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
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.awt.Font;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.engraving.SMuFLConstants;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.MetronomeContent;

/**
 * Covers the header tempo mark's layout contract: stacked only on line 0, positioned at the
 * header's right edge, skipped when its computed content width is zero, and reserving vertical
 * space above the staff. Mirrors {@code LayoutEngineTest}'s fixture setup — a real {@link
 * LayoutEngine} laying out a real {@link Line} — since {@code SystemStacker.stackTempoMark}
 * reads the tempo straight from {@code line.getSong().getTempo()}.
 */
class SongTempoMarkStackingTest extends UnitTest {

    private static final double STAFF_RIGHT_MARGIN_SS = 60.0;
    private static final double TOLERANCE = 0.001;

    // Large enough that the annotation and attribution fonts cannot measure the same width.
    private static final int ANNOTATION_FONT_SIZE_DELTA_PX = 12;

    // Staff positions are half staff-spaces counted downward, so a negative one is higher on
    // the page. The middle line sits inside the staff; the ledger position is well above it.
    private static final int MIDDLE_LINE_STAFF_POSITION_SP = 0;
    private static final int LEDGERS_ABOVE_STAFF_POSITION_SP = -12;

    private static LyricRenderMetrics lyricRenderMetrics() {
        var lyricsFont = new Font(Font.DIALOG, Font.PLAIN, 12);
        return LyricRenderMetrics.forFont(lyricsFont);
    }

    private static LayoutEngine engine() {
        return engine(DocumentFonts.defaultFonts());
    }

    private static LayoutEngine engine(DocumentFonts fonts) {
        return new LayoutEngine(lyricRenderMetrics(), STAFF_RIGHT_MARGIN_SS, fonts);
    }

    /** Asserts value is not null and returns it non-null for NullAway. */
    private static <T> T require(@Nullable T value, String description) {
        assertThat(value).describedAs(description).isNotNull();
        return value;
    }

    @Test
    void testLine0GetsTheTempoMarkDecorationAndOtherLinesDoNot() {
        var song = new Song();
        var line0 = song.getLine(0);

        var line0Result = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null),
            "LayoutResult for line 0");

        assertThat(line0Result.getDecorationLayout(song.getTempoMarkElement()))
            .describedAs("line 0 must carry a DecorationLayout for the song's tempo mark")
            .isNotNull();

        // Mirrors LineComponent.performLayout, which passes the tempo mark only for line 0.
        var line1 = new Line(song);
        song.addLine(line1);

        var line1Result = require(
            engine().layout(line1, true, false, null, null), "LayoutResult for line 1");

        assertThat(line1Result.getDecorationLayout(song.getTempoMarkElement()))
            .describedAs("a line other than line 0 must never carry the tempo mark's DecorationLayout")
            .isNull();
    }

    @Test
    void testTempoMarkXIsANoteheadWidthPastTheHeaderRightEdge() {
        var song = new Song();
        var line0 = song.getLine(0);

        var result = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null), "LayoutResult");
        var decoration = require(
            result.getDecorationLayout(song.getTempoMarkElement()), "tempo mark DecorationLayout");

        var expectedXSs =
            HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line0) + SMuFLConstants.NOTE_HEAD_WIDTH_SS;

        assertThat(decoration.xSs())
            .describedAs("the tempo mark must begin one notehead width right of the header's right edge")
            .isCloseTo(expectedXSs, within(TOLERANCE));
    }

    /**
     * Regression for issue #735 divergence 3, on the song-level path. Layout once measured every
     * metronome marking with the attribution font while rendering drew with the annotation font;
     * the two are invisibly identical in {@code system-defaults.json}, so only a font swap this
     * large catches a revert.
     * <p>
     * {@code SystemStacker} resolves the annotation font in two independent places — once for
     * per-note attachments and once here for the song-level mark — so the beat-change guard in
     * {@code SystemTierStackingTest} does not cover this one.
     */
    @Test
    void testTempoMarkWidthUsesTheResolvedAnnotationFontNotTheAttributionFont() {
        var fonts = DocumentFonts.defaultFonts();
        var attributionFont = fonts.getAttributionFont();
        fonts.setFont(
            FontKey.ANNOTATION,
            attributionFont.getName(),
            attributionFont.getSize() + ANNOTATION_FONT_SIZE_DELTA_PX);

        var song = new Song();
        var line0 = song.getLine(0);

        var result = require(
            engine(fonts).layout(line0, false, false, song.getTempoMarkElement(), null),
            "LayoutResult");
        var decoration = require(
            result.getDecorationLayout(song.getTempoMarkElement()), "tempo mark DecorationLayout");

        var tempo = song.getTempo();
        var annotationWidthSs =
            MetronomeContent.forTempo(tempo, fonts.getAnnotationFont()).widthSs();
        var attributionWidthSs = MetronomeContent.forTempo(tempo, attributionFont).widthSs();

        assertThat(decoration.widthSs())
            .describedAs("stacked width must match the resolved annotation-font content")
            .isCloseTo(annotationWidthSs, within(TOLERANCE));
        assertThat(decoration.widthSs())
            .describedAs("precondition: the two fonts must produce different widths, "
                + "or this test cannot fail")
            .isNotCloseTo(attributionWidthSs, within(TOLERANCE));
    }

    /**
     * The stacker must attach the typeset content to the layout it registers, because
     * {@code SongTempoMarkRenderer} reads the mark off {@code DecorationLayout.content()} and
     * throws when it is absent.
     */
    @Test
    void testTempoMarkLayoutCarriesItsContent() {
        var song = new Song();
        var line0 = song.getLine(0);

        var result = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null), "LayoutResult");
        var decoration = require(
            result.getDecorationLayout(song.getTempoMarkElement()), "tempo mark DecorationLayout");

        assertThat(decoration.content())
            .describedAs("the stacker must attach the content the renderer reads back")
            .isNotNull();
        assertThat(decoration.content().widthSs())
            .isCloseTo(decoration.widthSs(), within(TOLERANCE));
    }

    @Test
    void testHiddenTempoWithEmptyDescriptionGetsNoDecorationLayout() {
        var song = new Song();
        var line0 = song.getLine(0);

        var tempo = song.getTempo().copy();
        tempo.setShowTempo(false);
        tempo.setTempoDescription("");
        song.setTempo(tempo);

        var result = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null), "LayoutResult");

        assertThat(result.getDecorationLayout(song.getTempoMarkElement()))
            .describedAs("showTempo false with an empty description leaves zero content width, so the "
                + "mark must be skipped entirely")
            .isNull();
    }

    @Test
    void testHiddenTempoWithNonEmptyDescriptionStillGetsADecorationLayout() {
        var song = new Song();
        var line0 = song.getLine(0);

        var tempo = song.getTempo().copy();
        tempo.setShowTempo(false);
        tempo.setTempoDescription("Moderate");
        song.setTempo(tempo);

        var result = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null), "LayoutResult");

        assertThat(result.getDecorationLayout(song.getTempoMarkElement()))
            .describedAs("shouldShowTempo suppresses only the glyph and BPM, not a non-empty description")
            .isNotNull();
    }

    /**
     * The mark belongs to line 0 whatever line 0 holds. The design this replaced put the tempo
     * on the first line that had any content, so emptying line 0 moved the mark down to line 1;
     * now it stays put. A regression to the old rule would leave the first system of such a song
     * with no tempo on it at all.
     */
    @Test
    void testAnEmptyLine0StillCarriesTheMarkWhileLine1HoldsTheNotes() {
        var song = new Song();
        var line0 = song.getLine(0);
        var line1 = new Line(song);
        song.addLine(line1);
        song.withoutMutationTracking(() -> line1.addElement(0, crotchet()));

        assertThat(line0.elementCount())
            .describedAs("the fixture needs line 0 genuinely empty for this to mean anything")
            .isZero();

        var result = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null), "LayoutResult");

        assertThat(result.getDecorationLayout(song.getTempoMarkElement()))
            .describedAs("line 0 must carry the mark even with every note on a later line")
            .isNotNull();
    }

    /**
     * The mark is stacked before the per-column pass so that notes push it upward. A high note
     * in the first column reaches further above the staff than an empty line does, so the mark
     * has to sit higher to clear it — otherwise the tempo would be drawn through the notehead
     * and its ledger lines.
     */
    @Test
    void testAHighNoteInTheFirstColumnPushesTheMarkUp() {
        var lowNoteMarkTopSs = tempoMarkTopSsWithFirstNoteAt(MIDDLE_LINE_STAFF_POSITION_SP);
        var highNoteMarkTopSs = tempoMarkTopSsWithFirstNoteAt(LEDGERS_ABOVE_STAFF_POSITION_SP);

        // Smaller Y is higher on the page.
        assertThat(highNoteMarkTopSs)
            .describedAs("a note reaching above the staff must push the tempo mark higher")
            .isLessThan(lowNoteMarkTopSs);
    }

    /** Lays out a one-note line 0 and returns the tempo mark's top Y in staff spaces. */
    private double tempoMarkTopSsWithFirstNoteAt(int staffPosition) {
        var song = new Song();
        var line0 = song.getLine(0);
        song.withoutMutationTracking(
            () -> line0.addElement(0, StaffElementFactory.createNote(staffPosition, true)));

        var result = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null), "LayoutResult");
        var decoration = require(
            result.getDecorationLayout(song.getTempoMarkElement()), "tempo mark DecorationLayout");

        return decoration.ySs();
    }

    @Test
    void testTempoMarkReservesVerticalSpaceOnLine0() {
        var song = new Song();
        var line0 = song.getLine(0);

        var withMark = require(
            engine().layout(line0, false, false, song.getTempoMarkElement(), null),
            "LayoutResult with the tempo mark");
        var withoutMark = require(
            engine().layout(line0, false, false, null, null), "LayoutResult without the tempo mark");

        assertThat(withMark.getContentAboveStaffSs())
            .describedAs("a stacked tempo mark must reserve vertical space above an otherwise "
                + "identical line")
            .isGreaterThan(withoutMark.getContentAboveStaffSs());
    }
}
