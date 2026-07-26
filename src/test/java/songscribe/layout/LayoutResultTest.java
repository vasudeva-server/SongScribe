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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Clef;
import songscribe.dom.KeySignature;
import songscribe.dom.ScaleContext;
import songscribe.engraving.Staff;
import songscribe.font.DocumentFonts;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.engraving.SMuFLConstants;

class LayoutResultTest extends UnitTest {

    // T3a: Builder.setClef() round-trips through getClef()
    @Test
    void testBuilderClefRoundTrip() {
        var clef = new Clef();
        var result = LayoutResult.builder()
            .setClef(clef)
            .build();

        assertThat(result.getClef()).isSameAs(clef);
    }

    // T3b: Builder.setKeySignature() round-trips through getKeySignature()
    @Test
    void testBuilderKeySignatureRoundTrip() {
        var keySig = new KeySignature(KeyType.FLATS, 2);
        var result = LayoutResult.builder()
            .setKeySignature(keySig)
            .build();

        assertThat(result.getKeySignature()).isSameAs(keySig);
    }

    // T3c: Builder without setClef/setKeySignature returns null for both
    @Test
    void testBuilderDefaultsToNullHeaderElements() {
        var result = LayoutResult.builder().build();

        assertThat(result.getClef()).isNull();
        assertThat(result.getKeySignature()).isNull();
    }

    // T1: getLyricAnchor returns box-anchored geometry when verse-1 box exists
    @Test
    void testGetLyricAnchorBoxAnchored() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(ANCHOR_BOX_X_SS, ANCHOR_BOX_WIDTH_SS, 1, "do");
        var layoutResult = anchorLayoutBuilder().addLyricBox(element, box).build();
        var anchor = layoutResult.getLyricAnchor(element, testLyricMetrics());

        assertThat(anchor.centerXSs())
            .isCloseTo(ANCHOR_BOX_X_SS + ANCHOR_BOX_WIDTH_SS / 2, within(TOLERANCE));
        assertThat(anchor.baselineYSs()).isCloseTo(EXPECTED_VERSE_1_BASELINE_SS, within(TOLERANCE));
    }

    // T2: getLyricAnchor returns column-anchored geometry when no boxes
    @Test
    void testGetLyricAnchorColumnAnchored() {
        var element = ElementType.CROTCHET.newInstance();
        var column = testColumnAt(element, ANCHOR_COLUMN_X_SS);
        var layoutResult = anchorLayoutBuilder().putElementColumn(element, column).build();
        var anchor = layoutResult.getLyricAnchor(element, testLyricMetrics());

        assertThat(anchor.centerXSs())
            .isCloseTo(ANCHOR_COLUMN_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2.0, within(TOLERANCE));
        assertThat(anchor.baselineYSs()).isCloseTo(EXPECTED_VERSE_1_BASELINE_SS, within(TOLERANCE));
    }

    // T2b: column-anchored getLyricAnchor centers on the notehead extent (excluding augmentation),
    // so the editor cursor on a dotted note matches where the committed lyric box lands (#451).
    @Test
    void testGetLyricAnchorColumnAnchoredIgnoresDots() {
        var element = ElementType.CROTCHET.newInstance();
        var column = testDottedColumnAt(element, ANCHOR_COLUMN_X_SS);
        var layoutResult = anchorLayoutBuilder().putElementColumn(element, column).build();
        var anchor = layoutResult.getLyricAnchor(element, testLyricMetrics());

        // Centerd on the notehead (excluding the dot), not the full extent that includes it.
        assertThat(anchor.centerXSs())
            .as("dotted note: anchor must be centerd on the notehead, not shifted right by the dot")
            .isCloseTo(ANCHOR_COLUMN_X_SS + SMuFLConstants.NOTE_HEAD_WIDTH_SS / 2.0, within(TOLERANCE));
    }

    // T3: getLyricAnchor Y matches verseYSsInLine(1) exactly
    @Test
    void testGetLyricAnchorYMatchesVerseBaseline() {
        var element = ElementType.CROTCHET.newInstance();
        var box = new LyricBoxLayout(2.0, 1.0, 1, "re");
        var layoutResult = anchorLayoutBuilder().addLyricBox(element, box).build();
        var metrics = testLyricMetrics();
        var anchor = layoutResult.getLyricAnchor(element, metrics);

        assertThat(anchor.baselineYSs()).isCloseTo(EXPECTED_VERSE_1_BASELINE_SS, within(TOLERANCE));
        assertThat(anchor.baselineYSs())
            .isCloseTo(layoutResult.verseYSsInLine(1, metrics), within(TOLERANCE));
    }

    // T4: getLyricAnchor throws IllegalStateException when neither boxes nor column exist
    @Test
    void testGetLyricAnchorThrowsWhenNoBoxOrColumn() {
        var element = ElementType.CROTCHET.newInstance();
        var layoutResult = LayoutResult.builder().build();
        var metrics = testLyricMetrics();

        assertThatThrownBy(() -> layoutResult.getLyricAnchor(element, metrics))
            .isInstanceOf(IllegalStateException.class);
    }

    // ==========================================================================
    // Per-line staff geometry and verse baselines
    // (ported from the retired song-wide SongLayoutMetricsTest)
    // ==========================================================================

    // The staff Y helpers place the staff within the line's own coordinate frame: the top at
    // the line's above-staff content extent, the bottom a fixed staff height below it — once
    // that content clears the painted floor.
    @Test
    void testStaffYHelpersFollowThisLinesOwnAboveStaffContent() {
        var result = LayoutResult.builder()
            .setContentAboveStaffSs(TALL_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS)
            .build();

        assertThat(result.staffTopYSsInLine()).isCloseTo(TALL_ABOVE_STAFF_SS, within(TOLERANCE));
        assertThat(result.staffBottomYSsInLine())
            .isCloseTo(TALL_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS, within(TOLERANCE));
    }

    // Below the floor the staff Y helpers report the floored position, because that is where
    // LineComponent draws the staff. Reporting the measured content here instead left every
    // lyric on a short line drawn a floor's worth above the staff it belongs to (refs #591).
    @Test
    void testStaffYHelpersReportFlooredPositionOnShortLine() {
        var result = LayoutResult.builder()
            .setContentAboveStaffSs(ABOVE_STAFF_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS)
            .build();

        assertThat(result.aboveMidlineSs())
            .as("fixture must sit below the floor, or this proves nothing")
            .isLessThan(LineSpacing.MIN_ABOVE_MIDLINE_SS);

        assertThat(result.staffTopYSsInLine()).isCloseTo(Staff.MIN_ABOVE_STAFF_SS, within(TOLERANCE));
        assertThat(result.staffBottomYSsInLine())
            .isCloseTo(Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS, within(TOLERANCE));
    }

    // verseYSsInLine walks down one measured row height per verse from the first baseline,
    // which itself sits a staff-to-lyrics gap below this line's below-staff content.
    @Test
    void testVerseYSsInLineStepsOneMeasuredRowPerVerse() {
        var result = LayoutResult.builder()
            .setContentAboveStaffSs(ABOVE_STAFF_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS)
            .setVerseCount(2)
            .build();
        var metrics = testLyricMetrics();

        // The row height is measured from the font, never a constant.
        var rowHeightSs = LyricRenderMetrics.fontHeightSs(metrics.lyricsFont());
        // ABOVE_STAFF_SS is below the painted floor, so the staff — and every verse baseline
        // hanging off it — sits at MIN_ABOVE_STAFF_SS, not at the measured content.
        var expectedVerse1Ss = Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS
            + BELOW_CONTENT_SS + ANCHOR_STAFF_TO_LYRICS_GAP_SS;

        assertThat(result.verseYSsInLine(1, metrics)).isCloseTo(expectedVerse1Ss, within(TOLERANCE));
        assertThat(result.verseYSsInLine(2, metrics))
            .isCloseTo(expectedVerse1Ss + rowHeightSs, within(TOLERANCE));
        assertThat(rowHeightSs)
            .as("a verse row must have real measured height, or the per-verse step proves nothing")
            .isGreaterThan(0.0);
    }

    // The lyrics band is the visual row margin plus one measured row per reserved verse, and it
    // reserves a row even before the line's first verse exists so typing a lyric does not
    // re-space the song.
    @Test
    void testLyricsBandHeightReservesOneMeasuredRowWhenLineHasNoVerses() {
        var metrics = testLyricMetrics();
        var rowHeightSs = LyricRenderMetrics.fontHeightSs(metrics.lyricsFont());

        var noVerses = LayoutResult.builder().build();
        var twoVerses = LayoutResult.builder().setVerseCount(2).build();

        assertThat(noVerses.lyricsBandHeightSs(metrics))
            .isCloseTo(
                LineSpacing.LYRICS_ROW_MARGIN_SS
                    + LineSpacing.MIN_RESERVED_VERSE_ROWS * rowHeightSs,
                within(TOLERANCE));
        assertThat(twoVerses.lyricsBandHeightSs(metrics))
            .isCloseTo(LineSpacing.LYRICS_ROW_MARGIN_SS + 2 * rowHeightSs, within(TOLERANCE));
    }

    // A line's own height is the staff plus its measured content extents plus its own lyrics
    // band — and carries no inter-line gap, which belongs to the layout manager that stacks
    // the lines (refs #591).
    @Test
    void testLineHeightSsCoversOwnContentOnlyWithoutInterLineGap() {
        var result = LayoutResult.builder()
            .setContentAboveStaffSs(TALL_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(TALL_BELOW_CONTENT_SS)
            .build();
        var metrics = testLyricMetrics();

        var expectedHeightSs = Staff.STAFF_HEIGHT_SS
            + TALL_ABOVE_STAFF_SS
            + TALL_BELOW_CONTENT_SS
            + LineSpacing.LYRICS_ROW_MARGIN_SS
            + LineSpacing.MIN_RESERVED_VERSE_ROWS * LyricRenderMetrics.fontHeightSs(metrics.lyricsFont());

        // Content chosen to clear both floors, so the measured height is purely data-driven.
        assertThat(result.aboveMidlineSs()).isGreaterThan(LineSpacing.MIN_ABOVE_MIDLINE_SS);
        assertThat(result.belowMidlineSs(metrics)).isGreaterThan(LineSpacing.MIN_BELOW_MIDLINE_SS);

        assertThat(result.lineHeightSs(metrics)).isCloseTo(expectedHeightSs, within(TOLERANCE));
        assertThat(result.paintLineHeightSs(metrics))
            .as("with both floors cleared, the painted height equals the measured height")
            .isCloseTo(expectedHeightSs, within(TOLERANCE));
    }

    // The painted extents floor a line's bounds so Swing never clips its staff or ledger lines,
    // even though the measured extents that drive inter-line spacing stay unfloored.
    @Test
    void testPaintedExtentsFloorAnEmptyLineAtTheMinimumLineHeight() {
        var result = LayoutResult.builder().build();
        var metrics = testLyricMetrics();

        // Precondition: with no content this line's measured reach is inside both floors.
        assertThat(result.aboveMidlineSs()).isLessThan(LineSpacing.MIN_ABOVE_MIDLINE_SS);
        assertThat(result.belowMidlineSs(metrics)).isLessThan(LineSpacing.MIN_BELOW_MIDLINE_SS);

        assertThat(result.paintAboveMidlineSs())
            .isCloseTo(LineSpacing.MIN_ABOVE_MIDLINE_SS, within(TOLERANCE));
        assertThat(result.paintBelowMidlineSs(metrics))
            .isCloseTo(LineSpacing.MIN_BELOW_MIDLINE_SS, within(TOLERANCE));
        assertThat(result.paintLineHeightSs(metrics))
            .isCloseTo(LineSpacing.MIN_LINE_HEIGHT_SS, within(TOLERANCE));
    }

    // MIN_LINE_HEIGHT_SS is pinned to a concrete value so a change to the staff geometry it is
    // derived from cannot silently re-space every song.
    @Test
    void testMinLineHeightSsConstantHasConcretePinnedValue() {
        // (STAFF_HALF_SS 2.0 + MIN_ABOVE_STAFF_SS 3.0) + (STAFF_HALF_SS 2.0 + MIN_BELOW_STAFF_SS 4.0)
        final double expectedMinLineHeightSs = 11.0;

        assertThat(LineSpacing.MIN_LINE_HEIGHT_SS)
            .isCloseTo(expectedMinLineHeightSs, within(TOLERANCE));
    }

    @Test
    void testHitTestLyricHitsInsideBounds() {
        var song = new Song();
        var line = song.getLine(0);
        var element = ElementType.CROTCHET.newInstance();
        song.withoutMutationTracking(() -> line.addElement(0, element));
        var box = new LyricBoxLayout(ANCHOR_BOX_X_SS, ANCHOR_BOX_WIDTH_SS, 1, "do");
        var lyricsFont = DocumentFonts.defaultFonts().getLyricsFont();
        var layoutResult = anchorLayoutBuilder()
            .addLyricBox(element, box)
            .build();

        // Probe the vertical middle of verse row 1, derived independently of the production
        // helper: the row band starts one visual margin below this line's below-staff content,
        // and each row is one measured font ink height tall.
        var rowTopYSs = Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS + ANCHOR_BELOW_CONTENT_SS
            + LineSpacing.LYRICS_ROW_MARGIN_SS;
        var probeYSs = rowTopYSs + LyricRenderMetrics.fontHeightSs(lyricsFont) / 2;

        var hit = layoutResult.hitTestLyric(
            testLyricMetrics(),
            line,
            new Point2D.Double(
                ScaleContext.ssToRoundedPx(ANCHOR_BOX_X_SS + ANCHOR_BOX_WIDTH_SS / 2),
                ScaleContext.ssToRoundedPx(probeYSs)
            )
        );

        var nonNullHit = Objects.requireNonNull(hit);
        assertThat(nonNullHit.element()).isSameAs(element);
        assertThat(nonNullHit.verse()).isEqualTo(1);
    }

    @Test
    void testHitTestLyricMissesOutsideBounds() {
        var song = new Song();
        var line = song.getLine(0);
        var element = ElementType.CROTCHET.newInstance();
        song.withoutMutationTracking(() -> line.addElement(0, element));
        var box = new LyricBoxLayout(ANCHOR_BOX_X_SS, ANCHOR_BOX_WIDTH_SS, 1, "do");
        var layoutResult = anchorLayoutBuilder()
            .addLyricBox(element, box)
            .build();

        var metrics = testLyricMetrics();
        var rowTopYSs = Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS + ANCHOR_BELOW_CONTENT_SS
            + LineSpacing.LYRICS_ROW_MARGIN_SS;
        var rowMiddleYSs = rowTopYSs + LyricRenderMetrics.fontHeightSs(metrics.lyricsFont()) / 2;

        // Right of the box, at the row's own height — an X miss.
        assertThat(layoutResult.hitTestLyric(
            metrics,
            line,
            new Point2D.Double(
                ScaleContext.ssToRoundedPx(ANCHOR_BOX_X_SS + ANCHOR_BOX_WIDTH_SS + MISS_MARGIN_SS),
                ScaleContext.ssToRoundedPx(rowMiddleYSs)
            )
        )).isNull();

        // Over the box, but above the first verse row — a Y miss.
        assertThat(layoutResult.hitTestLyric(
            metrics,
            line,
            new Point2D.Double(
                ScaleContext.ssToRoundedPx(ANCHOR_BOX_X_SS + ANCHOR_BOX_WIDTH_SS / 2),
                ScaleContext.ssToRoundedPx(rowTopYSs - MISS_MARGIN_SS)
            )
        )).isNull();
    }

    // lyricAreaBaseYSs pins the verse base offset and tracks both vertical inputs. Measured
    // above a content extent that clears the painted floor, so the floor does not absorb the
    // above-staff delta the test is asserting on.
    @Test
    void testLyricAreaBaseYSsFollowsAboveStaffAndBelowContent() {
        var base = LayoutResult.builder()
            .setContentAboveStaffSs(TALL_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS)
            .build();
        var raisedAbove = LayoutResult.builder()
            .setContentAboveStaffSs(TALL_ABOVE_STAFF_SS + ABOVE_STAFF_DELTA_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS)
            .build();
        var raisedBelow = LayoutResult.builder()
            .setContentAboveStaffSs(TALL_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS + BELOW_CONTENT_DELTA_SS)
            .build();

        var expectedBase = TALL_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS
            + BELOW_CONTENT_SS + LineSpacing.LYRICS_ROW_MARGIN_SS;

        assertThat(base.lyricAreaBaseYSs()).isCloseTo(expectedBase, within(TOLERANCE));
        assertThat(raisedAbove.lyricAreaBaseYSs() - base.lyricAreaBaseYSs())
            .isCloseTo(ABOVE_STAFF_DELTA_SS, within(TOLERANCE));
        assertThat(raisedBelow.lyricAreaBaseYSs() - base.lyricAreaBaseYSs())
            .isCloseTo(BELOW_CONTENT_DELTA_SS, within(TOLERANCE));
    }

    // Lyrics hug each line individually: a verse baseline is driven by that line's own
    // below-staff content, never by a song-wide maximum. This is the regression guard for the
    // second half of issue #591 — the song-wide anchoring that put lyrics on lines after the
    // first where hit-testing did not respond.
    @Test
    void testVerseBaselinesFollowEachLinesOwnBelowStaffContent() {
        var metrics = testLyricMetrics();
        var shallow = LayoutResult.builder()
            .setContentAboveStaffSs(TALL_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS)
            .build();
        var deep = LayoutResult.builder()
            .setContentAboveStaffSs(TALL_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(BELOW_CONTENT_SS + BELOW_CONTENT_DELTA_SS)
            .build();

        assertThat(deep.verseYSsInLine(1, metrics) - shallow.verseYSsInLine(1, metrics))
            .as("a line reaching further below its staff pushes its own verses down by exactly that much")
            .isCloseTo(BELOW_CONTENT_DELTA_SS, within(TOLERANCE));
    }

    // findRangeElementDecorationLayout returns the layout whose range matches anchor AND type.
    @Test
    void testFindRangeElementDecorationLayoutMatchesAnchorAndType() {
        var anchor = ElementType.CROTCHET.newInstance();
        var trill = new Trill(anchor);
        var layout = sampleDecorationLayout();
        var result = LayoutResult.builder()
            .putDecorationLayout(trill, layout)
            .build();

        assertThat(result.findRangeElementDecorationLayout(anchor, Trill.class)).isSameAs(layout);
    }

    // A different anchor element finds no range decoration.
    @Test
    void testFindRangeElementDecorationLayoutReturnsNullForUnmatchedAnchor() {
        var anchor = ElementType.CROTCHET.newInstance();
        var otherAnchor = ElementType.CROTCHET.newInstance();
        var trill = new Trill(anchor);
        var result = LayoutResult.builder()
            .putDecorationLayout(trill, sampleDecorationLayout())
            .build();

        assertThat(result.findRangeElementDecorationLayout(otherAnchor, Trill.class)).isNull();
    }

    // A range type the stored element is not an instance of finds no decoration.
    @Test
    void testFindRangeElementDecorationLayoutReturnsNullForUnmatchedType() {
        var anchor = ElementType.CROTCHET.newInstance();
        var trill = new Trill(anchor);
        var result = LayoutResult.builder()
            .putDecorationLayout(trill, sampleDecorationLayout())
            .build();

        assertThat(result.findRangeElementDecorationLayout(anchor, Tuplet.class)).isNull();
    }

    // ==========================================================================
    // findElementAtXSs — head-bounds hit testing
    // ==========================================================================

    // A mouse X inside an element head's horizontal span returns that element's index;
    // boundary edges are inclusive on both sides.
    @Test
    void testFindElementAtXSsReturnsIndexInsideHeadBounds() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findElementAtXSs(MOUSE_OVER_FIRST_HEAD_SS, line)).isEqualTo(0);
        assertThat(result.findElementAtXSs(MOUSE_OVER_SECOND_HEAD_SS, line)).isEqualTo(1);
        // Both edges of the head span are inclusive hits.
        assertThat(result.findElementAtXSs(FIRST_ELEMENT_X_SS, line)).isEqualTo(0);
        assertThat(result.findElementAtXSs(FIRST_ELEMENT_X_SS + HEAD_RIGHT_EXTENT_SS, line)).isEqualTo(0);
    }

    // A mouse X in the gap between heads — or outside all heads — returns -1.
    @Test
    void testFindElementAtXSsReturnsMinusOneOutsideHeads() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findElementAtXSs(MOUSE_IN_GAP_SS, line)).isEqualTo(-1);
        assertThat(result.findElementAtXSs(MOUSE_BEFORE_FIRST_SS, line)).isEqualTo(-1);
        assertThat(result.findElementAtXSs(MOUSE_AFTER_LAST_SS, line)).isEqualTo(-1);
    }

    // ==========================================================================
    // findInsertionIndex — replacement / insertion slot resolution
    // ==========================================================================

    // Hovering over an element head resolves to that element's index (replacement slot).
    @Test
    void testFindInsertionIndexReturnsElementIndexOverHead() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findInsertionIndex(MOUSE_OVER_FIRST_HEAD_SS, line)).isEqualTo(0);
        assertThat(result.findInsertionIndex(MOUSE_OVER_SECOND_HEAD_SS, line)).isEqualTo(1);
    }

    // A mouse X left of the first element head resolves to slot 0.
    @Test
    void testFindInsertionIndexReturnsZeroBeforeFirstElement() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findInsertionIndex(MOUSE_BEFORE_FIRST_SS, line)).isEqualTo(0);
    }

    // Past the last real element, the slot is effectiveElementCount — the auto-maintained
    // terminal is excluded, so a one-element line yields slot 1, not 2.
    @Test
    void testFindInsertionIndexReturnsEffectiveCountAfterLastElement() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(line.effectiveElementCount()).isEqualTo(1);
        assertThat(result.findInsertionIndex(MOUSE_AFTER_LAST_SS, line)).isEqualTo(1);
    }

    // A mouse X in the gap between two heads resolves to the in-between slot.
    @Test
    void testFindInsertionIndexReturnsSlotBetweenElements() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.findInsertionIndex(MOUSE_IN_GAP_SS, line)).isEqualTo(1);
    }

    // ==========================================================================
    // calculateInsertionXSs — preview placement
    // ==========================================================================

    // An empty line (only the auto-maintained terminal) places the preview at the
    // first-element position from the horizontal spacing calculator.
    @Test
    void testCalculateInsertionXSsEmptyLineUsesFirstElementPosition() {
        var line = lineWithElements();
        var preview = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder().build();

        var expected = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());

        assertThat(result.calculateInsertionXSs(0, MOUSE_BEFORE_FIRST_SS, preview, line, false))
            .isCloseTo(expected, within(TOLERANCE));
    }

    // Hovering over an element head snaps the preview to that head's X position.
    @Test
    void testCalculateInsertionXSsSnapsToElementHead() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var preview = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.calculateInsertionXSs(1, MOUSE_OVER_SECOND_HEAD_SS, preview, line, false))
            .isCloseTo(SECOND_ELEMENT_X_SS, within(TOLERANCE));
    }

    // Hovering over the auto-maintained terminal right-aligns the preview to the
    // terminal's right edge (so a wider replacement does not overflow the staff).
    @Test
    void testCalculateInsertionXSsRightAlignsPreviewOverTerminal() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var terminal = line.getElement(line.elementCount() - 1);
        var preview = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(terminal, columnAt(terminal, TERMINAL_X_SS, TERMINAL_RIGHT_EXTENT_SS))
            .build();

        var previewRightExtentSs =
            ElementColumnBuilder.calculateRightExtentSs(preview, false, StaffElement.Direction.UP);
        var expected = TERMINAL_X_SS + TERMINAL_RIGHT_EXTENT_SS - previewRightExtentSs;

        assertThat(result.calculateInsertionXSs(1, TERMINAL_X_SS, preview, line, false))
            .isCloseTo(expected, within(TOLERANCE));
    }

    // Past the last real element, the preview is spaced through the same spring engine the
    // committed layout uses rather than snapped.
    @Test
    void testCalculateInsertionXSsAfterLastUsesSpacingCalculator() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        line.getSong().setLineWidthSs(WIDE_LINE_WIDTH_SS);
        var preview = ElementType.CROTCHET.newInstance();
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);
        var result = LayoutResult.builder()
            .putElementColumn(first, lastColumn)
            .build();

        var previewColumn = new ElementColumn(
            preview,
            Collections.emptyList(),
            ElementColumnBuilder.calculateLeftExtentSs(preview),
            ElementColumnBuilder.calculateRightExtentSs(preview, false, StaffElement.Direction.UP),
            0.0, 0.0, null, 0.0, false);
        var expected = lastColumn.getXSs()
            + HorizontalSpacingCalculator.buildSpring(
                lastColumn, previewColumn, line.getSong().getDefaultRestLengthSs()).naturalLengthSs();

        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);

        assertThat(actual).isCloseTo(expected, within(TOLERANCE));
        assertThat(actual).isGreaterThan(lastColumn.getRightEdgeXSs());
    }

    /**
     * The append preview must scale with the song's line rest. The retired greedy path this used to
     * run through was pinned to a fixed default gap, so a song with a loosened or tightened rest got
     * a preview that disagreed with where the note would actually land (refs #330).
     */
    @Test
    void testCalculateInsertionXSsAfterLastHonoursANonDefaultLineRest() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var song = line.getSong();
        song.withoutMutationTracking(() -> song.setDefaultRestLengthSs(LOOSENED_LINE_REST_SS));
        song.setLineWidthSs(WIDE_LINE_WIDTH_SS);

        var preview = ElementType.CROTCHET.newInstance();
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);
        var result = LayoutResult.builder()
            .putElementColumn(first, lastColumn)
            .build();

        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);

        assertThat(actual - lastColumn.getXSs())
            .as("the preview gap follows the song's line rest, not a fixed default gap")
            .isCloseTo(
                HEAD_RIGHT_EXTENT_SS + LOOSENED_LINE_REST_SS, within(TOLERANCE));
        assertThat(actual - lastColumn.getXSs())
            .as("a loosened rest must space the preview wider than the default would")
            .isGreaterThan(HEAD_RIGHT_EXTENT_SS + Song.DEFAULT_REST_LENGTH_SS);
    }

    // On an interior line — which carries no auto-maintained terminal — the staff margin is the
    // boundary. When the line is full enough that the preview's default spacing would overflow
    // that margin, the preview centers in the room that remains rather than sitting flush against
    // the margin (refs #608).
    @Test
    void testCalculateInsertionXSsAfterLastCentersAgainstMarginOnInteriorLine() {
        var first = ElementType.CROTCHET.newInstance();
        var line = interiorLineWithElements(first);
        var song = line.getSong();

        assertThat(line.effectiveElementCount())
            .as("an interior line must have no auto-maintained terminal, leaving the margin as the boundary")
            .isEqualTo(line.elementCount());

        var preview = ElementType.CROTCHET.newInstance();
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);
        var result = LayoutResult.builder()
            .putElementColumn(first, lastColumn)
            .build();

        var previewColumn = previewColumnFor(preview);
        var naturalXSs = naturalPreviewXSs(lastColumn, previewColumn, song);

        // Shrink the margin so it lands between the last element's right edge and the preview's
        // natural (uncompressed) right edge — the line is full, but some room still remains.
        var marginSs = midpointOf(lastColumn.getRightEdgeXSs(), naturalXSs + previewColumn.getRightExtentSs());
        song.setLineWidthSs(marginSs);

        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);
        var noteheadWidthSs = NoteGeometry.getGlyphRightEdgeSs(preview);
        var expected = centeredInRoom(lastColumn.getRightEdgeXSs(), marginSs, noteheadWidthSs);

        assertThat(actual).isCloseTo(expected, within(TOLERANCE));
        assertThat(actual).isGreaterThan(lastColumn.getRightEdgeXSs());
        assertThat(actual).isLessThan(naturalXSs);
    }

    // When the line ends with the auto-maintained terminal, the terminal's own column — not the
    // far staff margin — is the boundary the preview centers against, since the terminal can sit
    // well short of the margin (refs #608).
    @Test
    void testCalculateInsertionXSsAfterLastCentersAgainstTerminalWhenLineHasOne() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var terminal = line.getElement(line.elementCount() - 1);
        var song = line.getSong();
        song.setLineWidthSs(WIDE_LINE_WIDTH_SS);

        var preview = ElementType.CROTCHET.newInstance();
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);

        var previewColumn = previewColumnFor(preview);
        var naturalXSs = naturalPreviewXSs(lastColumn, previewColumn, song);

        // Put the terminal well short of the wide margin — between the last note's right edge
        // and the preview's natural (uncompressed) right edge — so the terminal, not the margin,
        // is what the preview would overflow.
        var terminalXSs = midpointOf(lastColumn.getRightEdgeXSs(), naturalXSs + previewColumn.getRightExtentSs());
        var result = resultWithTerminal(first, lastColumn, terminal, terminalXSs);

        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);
        var noteheadWidthSs = NoteGeometry.getGlyphRightEdgeSs(preview);
        var expected = centeredInRoom(lastColumn.getRightEdgeXSs(), terminalXSs, noteheadWidthSs);

        assertThat(actual).isCloseTo(expected, within(TOLERANCE));
        assertThat(actual)
            .as("the terminal, not the far margin, must bound the preview")
            .isLessThan(centeredInRoom(lastColumn.getRightEdgeXSs(), WIDE_LINE_WIDTH_SS, noteheadWidthSs));
    }

    // The centering ignores the preview's flag, accidental, and augmentation dots — only the
    // notehead itself is centerd in the remaining room, so a wide unbeamed quaver with an
    // accidental and a dot lands further right than centering its full footprint would put it
    // (refs #608).
    @Test
    void testCalculateInsertionXSsAfterLastCentersOnlyTheNoteheadIgnoringFlagAccidentalAndDots() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var terminal = line.getElement(line.elementCount() - 1);
        var song = line.getSong();
        song.setLineWidthSs(WIDE_LINE_WIDTH_SS);

        // calculateInsertionXSs itself measures the preview's accidental (for the left extent of
        // its internal working column), so accidental metadata must be initialized even though
        // this test only asserts on the right side.
        NoteGeometry.initializeAccidentalWidths();

        var preview = ElementType.QUAVER.newInstance();
        preview.setAccidental(StaffElement.Accidental.SHARP);
        preview.setDotCount(1);
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);

        var previewFootprintColumn = previewColumnFor(preview);
        var footprintWidthSs = previewFootprintColumn.getRightExtentSs();
        var noteheadWidthSs = NoteGeometry.getGlyphRightEdgeSs(preview);
        assertThat(footprintWidthSs)
            .as("the quaver's flag/dot footprint must be wider than its bare notehead for this test to be meaningful")
            .isGreaterThan(noteheadWidthSs);

        var naturalXSs = naturalPreviewXSs(lastColumn, previewFootprintColumn, song);
        var terminalXSs = midpointOf(lastColumn.getRightEdgeXSs(), naturalXSs + footprintWidthSs);
        var result = resultWithTerminal(first, lastColumn, terminal, terminalXSs);

        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);
        var lastEdgeSs = lastColumn.getRightEdgeXSs();

        assertThat(actual).isCloseTo(centeredInRoom(lastEdgeSs, terminalXSs, noteheadWidthSs), within(TOLERANCE));

        // The wider footprint would center the preview further left; regressing to footprint-based
        // centering would leave the notehead visibly hugging the last element.
        assertThat(actual)
            .as("centering must measure the notehead, not the full flag/accidental/dot footprint")
            .isGreaterThan(centeredInRoom(lastEdgeSs, terminalXSs, footprintWidthSs));
    }

    // A non-note preview (here a rest) has no notehead, so its full element width — not a SMuFL
    // notehead bounding box — is what gets centerd in the remaining room (refs #608).
    @Test
    void testCalculateInsertionXSsAfterLastCentersNonNotePreviewByElementWidth() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var terminal = line.getElement(line.elementCount() - 1);
        var song = line.getSong();
        song.setLineWidthSs(WIDE_LINE_WIDTH_SS);

        var previewType = ElementType.CROTCHET_REST;
        assertThat(previewType.isNote())
            .as("this test must exercise the non-note branch of the centering width")
            .isFalse();

        var preview = previewType.newInstance();
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);

        var previewColumn = previewColumnFor(preview);
        var naturalXSs = naturalPreviewXSs(lastColumn, previewColumn, song);
        var terminalXSs = midpointOf(lastColumn.getRightEdgeXSs(), naturalXSs + previewColumn.getRightExtentSs());
        var result = resultWithTerminal(first, lastColumn, terminal, terminalXSs);

        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);
        var expected = centeredInRoom(lastColumn.getRightEdgeXSs(), terminalXSs, previewType.getElementWidthSs());

        assertThat(actual).isCloseTo(expected, within(TOLERANCE));
        assertThat(actual).isGreaterThan(lastColumn.getRightEdgeXSs());
    }

    // When the room left before the boundary is narrower than the preview's own notehead, the
    // midpoint would fall left of the last element and the preview would overlap it. It sits
    // flush against the last element instead (refs #608).
    @Test
    void testCalculateInsertionXSsAfterLastSitsFlushWhenRoomIsNarrowerThanNotehead() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var terminal = line.getElement(line.elementCount() - 1);
        var song = line.getSong();
        song.setLineWidthSs(WIDE_LINE_WIDTH_SS);

        var preview = ElementType.CROTCHET.newInstance();
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);
        var lastEdgeSs = lastColumn.getRightEdgeXSs();

        var terminalXSs = lastEdgeSs + SUB_NOTEHEAD_ROOM_SS;
        assertThat(SUB_NOTEHEAD_ROOM_SS)
            .as("the room left must be narrower than the notehead for this test to be meaningful")
            .isLessThan(NoteGeometry.getGlyphRightEdgeSs(preview));

        var result = resultWithTerminal(first, lastColumn, terminal, terminalXSs);
        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);

        assertThat(actual).isCloseTo(lastEdgeSs, within(TOLERANCE));
    }

    // The boundary check is an overflow check, not a fit check: a preview whose natural right edge
    // lands exactly on the boundary still uses its natural spacing rather than being re-centerd
    // (refs #608).
    @Test
    void testCalculateInsertionXSsAfterLastKeepsNaturalSpacingWhenPreviewExactlyMeetsBoundary() {
        var first = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first);
        var terminal = line.getElement(line.elementCount() - 1);
        var song = line.getSong();
        song.setLineWidthSs(WIDE_LINE_WIDTH_SS);

        var preview = ElementType.CROTCHET.newInstance();
        var lastColumn = columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS);

        var previewColumn = previewColumnFor(preview);
        var naturalXSs = naturalPreviewXSs(lastColumn, previewColumn, song);

        // Put the terminal exactly on the preview's natural right edge — nothing overflows.
        var terminalXSs = naturalXSs + previewColumn.getRightExtentSs();
        var result = resultWithTerminal(first, lastColumn, terminal, terminalXSs);

        var actual = result.calculateInsertionXSs(1, MOUSE_AFTER_LAST_SS, preview, line, false);

        assertThat(actual).isCloseTo(naturalXSs, within(TOLERANCE));
    }

    // Between two element heads, the preview is placed at the midpoint of their X positions.
    @Test
    void testCalculateInsertionXSsBetweenElementsUsesMidpoint() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var preview = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.calculateInsertionXSs(1, MOUSE_IN_GAP_SS, preview, line, false))
            .isCloseTo(EXPECTED_MIDPOINT_SS, within(TOLERANCE));
    }

    // betweenElementsOnly=true must skip the element-head snap entirely: the mouse sits directly
    // over the second element's head, where betweenElementsOnly=false snaps to that head, but the
    // paste-mode marker (betweenElementsOnly=true) must instead fall through to the
    // between-elements midpoint — every one of the 7 pre-existing calculateInsertionXSs calls in
    // this file passes false, so the true branch was otherwise never exercised.
    @Test
    void testCalculateInsertionXSsBetweenElementsOnlySkipsElementHeadSnap() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        var line = lineWithElements(first, second);
        var preview = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder()
            .putElementColumn(first, columnAt(first, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .putElementColumn(second, columnAt(second, SECOND_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.calculateInsertionXSs(1, MOUSE_OVER_SECOND_HEAD_SS, preview, line, false))
            .as("betweenElementsOnly=false snaps the preview onto the element head under the mouse")
            .isCloseTo(SECOND_ELEMENT_X_SS, within(TOLERANCE));

        assertThat(result.calculateInsertionXSs(1, MOUSE_OVER_SECOND_HEAD_SS, preview, line, true))
            .as("betweenElementsOnly=true ignores the head snap and returns the between-elements midpoint instead")
            .isCloseTo(EXPECTED_MIDPOINT_SS, within(TOLERANCE));
    }

    // ==========================================================================
    // findAttachmentBounds / findAttachment — owner+type lookup
    // ==========================================================================

    // Two same-type attachments on different owners resolve to their own bounds;
    // an owner with no attachment of that type resolves to null.
    @Test
    void testFindAttachmentBoundsMatchesOwnerAndType() {
        var ownerOne = ElementType.CROTCHET.newInstance();
        var ownerTwo = ElementType.CROTCHET.newInstance();
        var boundsOne = sampleBounds();
        var boundsTwo = sampleBounds();
        var result = LayoutResult.builder()
            .putElementBounds(new FermataAttachment(ownerOne), boundsOne)
            .putElementBounds(new FermataAttachment(ownerTwo), boundsTwo)
            .build();

        assertThat(result.findAttachmentBounds(ownerOne, FermataAttachment.class)).isSameAs(boundsOne);
        assertThat(result.findAttachmentBounds(ownerTwo, FermataAttachment.class)).isSameAs(boundsTwo);
        var unownedNote = ElementType.CROTCHET.newInstance();
        assertThat(result.findAttachmentBounds(unownedNote, FermataAttachment.class)).isNull();
    }

    // findAttachment returns the attachment object whose owner and type match, else null.
    @Test
    void testFindAttachmentReturnsMatchingAttachmentElseNull() {
        var owner = ElementType.CROTCHET.newInstance();
        var fermata = new FermataAttachment(owner);
        var result = LayoutResult.builder()
            .putElementBounds(fermata, sampleBounds())
            .build();

        assertThat(result.findAttachment(owner, FermataAttachment.class)).isSameAs(fermata);
        var otherNote = ElementType.CROTCHET.newInstance();
        assertThat(result.findAttachment(otherNote, FermataAttachment.class)).isNull();
    }

    // ==========================================================================
    // findRangeElementBounds — anchor+end+type lookup
    // ==========================================================================

    // A range element resolves by matching anchor AND end AND type; a differing end
    // element resolves to null.
    @Test
    void testFindRangeElementBoundsMatchesAnchorEndAndType() {
        var anchor = ElementType.CROTCHET.newInstance();
        var end = ElementType.CROTCHET.newInstance();
        var trill = new Trill(anchor, end);
        var bounds = sampleBounds();
        var result = LayoutResult.builder()
            .putElementBounds(trill, bounds)
            .build();

        assertThat(result.findRangeElementBounds(anchor, end, Trill.class)).isSameAs(bounds);
        var otherEnd = ElementType.CROTCHET.newInstance();
        assertThat(result.findRangeElementBounds(anchor, otherEnd, Trill.class)).isNull();
    }

    // ==========================================================================
    // contains — element-bounds membership
    // ==========================================================================

    // contains is true exactly when elementBounds holds the given LineElement; a
    // non-laid-out element and a non-LineElement both return false.
    @Test
    void testContainsReflectsElementBoundsMembership() {
        var present = ElementType.CROTCHET.newInstance();
        var absent = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder()
            .putElementBounds(present, sampleBounds())
            .build();

        assertThat(result.contains(present)).isTrue();
        assertThat(result.contains(absent)).isFalse();
        assertThat(result.contains(NON_ELEMENT)).isFalse();
    }

    // ==========================================================================
    // getDecorationLayoutsByType — type-filtered decoration entries
    // ==========================================================================

    // Filtering by type returns only the entries whose key is an instance of that
    // type, paired with their own layout.
    @Test
    void testGetDecorationLayoutsByTypeFiltersByClass() {
        var anchor = ElementType.CROTCHET.newInstance();
        var trill = new Trill(anchor);
        var fermata = new FermataAttachment(anchor);
        var trillLayout = sampleDecorationLayout();
        var fermataLayout = sampleDecorationLayout();
        var result = LayoutResult.builder()
            .putDecorationLayout(trill, trillLayout)
            .putDecorationLayout(fermata, fermataLayout)
            .build();

        var trills = result.getDecorationLayoutsByType(Trill.class);
        assertThat(trills).hasSize(1);
        assertThat(trills.get(0).getKey()).isSameAs(trill);
        assertThat(trills.get(0).getValue()).isSameAs(trillLayout);

        var fermatas = result.getDecorationLayoutsByType(FermataAttachment.class);
        assertThat(fermatas).hasSize(1);
        assertThat(fermatas.get(0).getKey()).isSameAs(fermata);
        assertThat(fermatas.get(0).getValue()).isSameAs(fermataLayout);
    }

    // ==========================================================================
    // getElementXSs / getElementPosition — element lookup with absent fallback
    // ==========================================================================

    // getElementXSs returns the column X for a laid-out element and 0 for an unknown one.
    @Test
    void testGetElementXSsReturnsColumnXOrZero() {
        var present = ElementType.CROTCHET.newInstance();
        var absent = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder()
            .putElementColumn(present, columnAt(present, FIRST_ELEMENT_X_SS, HEAD_RIGHT_EXTENT_SS))
            .build();

        assertThat(result.getElementXSs(present)).isCloseTo(FIRST_ELEMENT_X_SS, within(TOLERANCE));
        assertThat(result.getElementXSs(absent)).isEqualTo(0.0);
    }

    // getElementPosition returns the content top-left for a laid-out element and null
    // for an unknown one.
    @Test
    void testGetElementPositionReturnsTopLeftOrNull() {
        var present = ElementType.CROTCHET.newInstance();
        var absent = ElementType.CROTCHET.newInstance();
        var result = LayoutResult.builder()
            .putElementBounds(present, sampleBounds())
            .build();

        var position = result.getElementPosition(present);
        assertThat(position).isNotNull();
        assertThat(Objects.requireNonNull(position).getX()).isCloseTo(SAMPLE_BOUNDS_LEFT_SS, within(TOLERANCE));
        assertThat(position.getY()).isCloseTo(SAMPLE_BOUNDS_TOP_SS, within(TOLERANCE));
        assertThat(result.getElementPosition(absent)).isNull();
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private static final double HEAD_RIGHT_EXTENT_SS = 1.0;
    private static final double FIRST_ELEMENT_X_SS = 10.0;
    /** A line rest well above the default, so a fixed-gap regression is unmistakable. */
    private static final double LOOSENED_LINE_REST_SS = 4.0;
    private static final double SECOND_ELEMENT_X_SS = 20.0;
    private static final double MOUSE_OVER_FIRST_HEAD_SS = 10.5;
    private static final double MOUSE_OVER_SECOND_HEAD_SS = 20.5;
    private static final double MOUSE_IN_GAP_SS = 14.0;
    private static final double MOUSE_BEFORE_FIRST_SS = 5.0;
    private static final double MOUSE_AFTER_LAST_SS = 25.0;
    // Wide enough that the preview's default spacing never overflows it, so tests unrelated to
    // the margin-overflow behavior can ignore it.
    private static final double WIDE_LINE_WIDTH_SS = 1000.0;
    // Narrower than any notehead, so centering would place the preview left of the last element.
    private static final double SUB_NOTEHEAD_ROOM_SS = 0.25;
    private static final double TERMINAL_X_SS = 20.0;
    private static final double TERMINAL_RIGHT_EXTENT_SS = 3.0;
    // Midpoint of FIRST_ELEMENT_X_SS and SECOND_ELEMENT_X_SS.
    private static final double EXPECTED_MIDPOINT_SS = 15.0;

    // Builds a fresh single-line song, prepending the given elements before the
    // line's auto-maintained FINAL_DOUBLE_BARLINE terminal so they occupy indices
    // 0..n-1 with the terminal last.
    private static Line lineWithElements(StaffElement... elements) {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < elements.length; i++) {
                line.addElement(i, elements[i]);
            }
        });
        return line;
    }

    // Builds a two-line song and returns its first line, carrying the given elements. The terminal
    // invariant applies only to the last line, so line 0 has no auto-maintained terminal — the
    // state in which the staff margin, not a terminal column, bounds the preview.
    private static Line interiorLineWithElements(StaffElement... elements) {
        var line = lineWithElements(elements);
        var song = line.getSong();
        song.addLine(new Line(song));
        return line;
    }

    // Mirrors the working column calculateInsertionXSs builds internally for the preview element,
    // so tests can derive the same natural spacing the production code will.
    private static ElementColumn previewColumnFor(StaffElement preview) {
        return new ElementColumn(
            preview,
            Collections.emptyList(),
            ElementColumnBuilder.calculateLeftExtentSs(preview),
            ElementColumnBuilder.calculateRightExtentSs(preview, false, StaffElement.Direction.UP),
            0.0, 0.0, null, 0.0, false);
    }

    // The X the preview would take from the spring engine alone, before any boundary clamping.
    private static double naturalPreviewXSs(ElementColumn lastColumn, ElementColumn previewColumn, Song song) {
        return lastColumn.getXSs()
            + HorizontalSpacingCalculator.buildSpring(
                lastColumn, previewColumn, song.getDefaultRestLengthSs()).naturalLengthSs();
    }

    private static double midpointOf(double lowSs, double highSs) {
        return (lowSs + highSs) / 2;
    }

    // Where an element of the given width lands when centerd between a left edge and a boundary.
    private static double centeredInRoom(double leftEdgeSs, double boundarySs, double widthSs) {
        return leftEdgeSs + (boundarySs - leftEdgeSs - widthSs) / 2;
    }

    private static LayoutResult resultWithTerminal(
        StaffElement lastElement, ElementColumn lastColumn, StaffElement terminal, double terminalXSs) {

        return LayoutResult.builder()
            .putElementColumn(lastElement, lastColumn)
            .putElementColumn(terminal, columnAt(terminal, terminalXSs, TERMINAL_RIGHT_EXTENT_SS))
            .build();
    }

    private static ElementColumn columnAt(StaffElement element, double xSs, double rightExtentSs) {
        var column = new ElementColumn(
            element, Collections.emptyList(), 0.0, rightExtentSs,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }

    private static final double TOLERANCE = 0.0001;

    // Arbitrary dot width used in tests that need rightExtentSs > rightExtentExcludingAugmentationSs.
    private static final double FAKE_DOT_EXTENT_SS = 2.0;

    private static final double ABOVE_STAFF_SS = 1.5;
    private static final double BELOW_CONTENT_SS = 0.75;
    private static final double ABOVE_STAFF_DELTA_SS = 2.0;
    private static final double BELOW_CONTENT_DELTA_SS = 0.5;

    private static final double DECORATION_X_SS = 2.0;
    private static final double DECORATION_Y_SS = -3.0;
    private static final double DECORATION_WIDTH_SS = 1.5;
    private static final double DECORATION_HEIGHT_SS = 1.0;
    private static final double DECORATION_MARGIN_SS = 0.25;

    // Content extents chosen to clear both painted-extent floors, so a measured line height
    // built from them is purely data-driven.
    private static final double TALL_ABOVE_STAFF_SS = 6.0;
    private static final double TALL_BELOW_CONTENT_SS = 5.0;

    private static final double SAMPLE_BOUNDS_LEFT_SS = 5.0;
    private static final double SAMPLE_BOUNDS_TOP_SS = 1.0;
    private static final double SAMPLE_BOUNDS_WIDTH_SS = 2.0;
    private static final double SAMPLE_BOUNDS_HEIGHT_SS = 3.0;

    private static final Object NON_ELEMENT = "not-a-line-element";

    // Each call returns a distinct ElementBoundsSs instance so reference-identity
    // (isSameAs) assertions can distinguish entries.
    private static ElementBoundsSs sampleBounds() {
        return ElementBoundsSs.contentOnly(new Rectangle2D.Double(
            SAMPLE_BOUNDS_LEFT_SS, SAMPLE_BOUNDS_TOP_SS,
            SAMPLE_BOUNDS_WIDTH_SS, SAMPLE_BOUNDS_HEIGHT_SS));
    }

    private static LayoutResult.DecorationLayout sampleDecorationLayout() {
        return new LayoutResult.DecorationLayout(
            DECORATION_X_SS, DECORATION_Y_SS, DECORATION_WIDTH_SS,
            DECORATION_HEIGHT_SS, DECORATION_MARGIN_SS);
    }

    // ---- Lyric-anchor / hit-test fixture -------------------------------------
    //
    // A single line whose content extents and staff-to-lyrics gap are known, so verse
    // baselines and lyric row bands can be derived by hand.

    private static final double ANCHOR_ABOVE_STAFF_SS = 1.0;
    private static final double ANCHOR_BELOW_CONTENT_SS = 0.5;
    private static final double ANCHOR_STAFF_TO_LYRICS_GAP_SS = 0.25;
    private static final double ANCHOR_BOX_X_SS = 3.0;
    private static final double ANCHOR_BOX_WIDTH_SS = 2.0;
    private static final double ANCHOR_COLUMN_X_SS = 5.0;
    /** Comfortably more than one rounded pixel, so a "miss" probe is unambiguously outside. */
    private static final double MISS_MARGIN_SS = 0.5;
    // ANCHOR_ABOVE_STAFF_SS is below the painted floor, so the staff bottom sits at
    // (Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HEIGHT_SS), not at the measured content:
    //   (3.0 + 4.0) + ANCHOR_BELOW_CONTENT_SS + ANCHOR_STAFF_TO_LYRICS_GAP_SS = 7.0 + 0.5 + 0.25
    private static final double EXPECTED_VERSE_1_BASELINE_SS = 7.75;

    private static LayoutResult.Builder anchorLayoutBuilder() {
        return LayoutResult.builder()
            .setContentAboveStaffSs(ANCHOR_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(ANCHOR_BELOW_CONTENT_SS);
    }

    private static LyricRenderMetrics testLyricMetrics() {
        var lyricsFont = DocumentFonts.defaultFonts().getLyricsFont();
        return new LyricRenderMetrics(
            lyricsFont, lyricsFont, 0.0, 0.0, ANCHOR_STAFF_TO_LYRICS_GAP_SS);
    }

    private static ElementColumn testColumnAt(StaffElement element, double xSs) {
        var column = new ElementColumn(
            element, Collections.emptyList(), 0.0, SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }

    // A dotted column: rightExtentSs includes the dot, rightExtentExcludingAugmentationSs does not.
    private static ElementColumn testDottedColumnAt(StaffElement element, double xSs) {
        var column = new ElementColumn(
            element, Collections.emptyList(), 0.0,
            SMuFLConstants.NOTE_HEAD_WIDTH_SS + FAKE_DOT_EXTENT_SS,
            SMuFLConstants.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }
}
