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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.util.ArrayList;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.NoteGeometry;
import songscribe.engraving.Staff;
import songscribe.engraving.SMuFLConstants;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class RenderingUtilsTest extends UnitTest {

    // T1: line == null → fatal exit (corruption, not a preview scenario)
    @Test
    void testGetDecorationColorNullLineThrows() {
        var element = new StaffElement(ElementType.CROTCHET);
        var invariants = RenderContextTestHelper.newContext(new Song()).build();

        assertThatThrownBy(() -> RenderingUtils.getDecorationColor(element, invariants, ElementFrame.LINE_LEVEL))
            .isInstanceOf(AssertionError.class);
    }

    // T2: element not in line (index < 0) → preview element color
    @Test
    void testGetDecorationColorElementNotInLineReturnsPreviewColor() {
        var element = new StaffElement(ElementType.CROTCHET);
        var line = mock(Line.class);
        when(line.getElementIndex(element)).thenReturn(-1);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .build();

        var color = RenderingUtils.getDecorationColor(element, invariants, ElementFrame.LINE_LEVEL);

        assertThat(color).isEqualTo(ScoreView.getPreviewElementColor());
    }

    // T3: element in line (index >= 0) → invariants.getElementColor(index) result
    @Test
    void testGetDecorationColorElementInLineReturnsCtxColor() {
        var element = new StaffElement(ElementType.CROTCHET);
        var line = mock(Line.class);
        when(line.getElementIndex(element)).thenReturn(0);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setCurrentLine(line)
            .setPlayingNoteIndex(0)
            .build();

        var color = RenderingUtils.getDecorationColor(element, invariants, ElementFrame.LINE_LEVEL);

        assertThat(color).isEqualTo(ScoreView.getPlayingNoteColor());
    }

    // T4: frame carries a valid element index (≥0) → fast path returns color without
    // consulting the line (no line is needed — the method would throw if it tried to use one)
    @Test
    void testGetDecorationColorFrameWithValidIndexBypassesLineScan() {
        var element = new StaffElement(ElementType.CROTCHET);
        // No current line set — fast-path must not try to use it
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        // Frame with a valid element index (≥0) takes the fast path
        var frame = ElementFrame.LINE_LEVEL.withElement(0, Double.NaN);

        // Outside edit mode, getElementColor always returns BLACK
        var color = RenderingUtils.getDecorationColor(element, invariants, frame);

        assertThat(color).isEqualTo(Color.BLACK);
    }

    // forEachLedgerLineYSs — below-staff position generates one ledger-line Y offset
    // (positive staff position = lower pitch = below staff in Y-down coordinates)
    @Test
    void testForEachLedgerLineYSsBelowStaff() {
        // Staff position 7 (odd, below staff): i adjusted to 6, one ledger line at sp 6
        // Y offset relative to note: spToSs(6 - 7) = spToSs(-1) = -0.5
        var positions = new ArrayList<Double>();
        RenderingUtils.forEachLedgerLineYSs(7, positions::add);

        assertThat(positions).containsExactly(Staff.spToSs(-1));
    }

    // forEachLedgerLineYSs — two ledger lines below staff
    @Test
    void testForEachLedgerLineYSsTwoLinesBelowStaff() {
        // Staff position 9 (odd, below staff): i adjusted to 8, two ledger lines at 8 and 6
        // Y offsets: spToSs(8-9)=-0.5, spToSs(6-9)=-1.5
        var positions = new ArrayList<Double>();
        RenderingUtils.forEachLedgerLineYSs(9, positions::add);

        assertThat(positions).containsExactly(
            Staff.spToSs(-1),
            Staff.spToSs(-3)
        );
    }

    // forEachLedgerLineYSs — above-staff position generates one ledger line
    // (negative staff position = higher pitch = above staff in Y-down coordinates)
    @Test
    void testForEachLedgerLineYSsAboveStaff() {
        // Staff position -7 (odd, above staff): i adjusted to -6, one ledger line
        // Y offset: spToSs(-6 - (-7)) = spToSs(1) = 0.5
        var positions = new ArrayList<Double>();
        RenderingUtils.forEachLedgerLineYSs(-7, positions::add);

        assertThat(positions).containsExactly(Staff.spToSs(1));
    }

    // forEachLedgerLineYSs — on-staff position produces no ledger lines
    @Test
    void testForEachLedgerLineYSsOnStaffProducesNoLines() {
        var positions = new ArrayList<Double>();
        RenderingUtils.forEachLedgerLineYSs(4, positions::add);

        assertThat(positions).isEmpty();
    }

    // forEachLedgerLineYSs — parity normalization: even below-staff position is used as-is
    @Test
    void testForEachLedgerLineYSsEvenBelowStaffPosition() {
        // Staff position 8 (even, below staff): no parity adjustment, i = 8, step = -2
        // Y offset: spToSs(8-8)=0, spToSs(6-8)=-1.0
        var positions = new ArrayList<Double>();
        RenderingUtils.forEachLedgerLineYSs(8, positions::add);

        assertThat(positions).containsExactly(
            Staff.spToSs(0),
            Staff.spToSs(-2)
        );
    }

    // centeredGlyphX — arithmetic: layoutX + noteheadCenter - bboxLeft - glyphWidth/2
    @Test
    void testCenteredGlyphXComputesCorrectArithmetic() {
        var note = new StaffElement(ElementType.CROTCHET);
        note.setUpper(true);
        var layoutXSs = 10.0;
        var glyphBBoxLeft = -0.1;
        var glyphWidthSs = 0.8;

        var type = note.getType();
        var noteheadCenterXSs =
            type.getElementCenterXSs() + NoteGeometry.getNoteheadXOffsetSs(type, note.getDirection());
        var expected = layoutXSs + noteheadCenterXSs - glyphBBoxLeft - glyphWidthSs / 2.0;

        var actual = RenderingUtils.centeredGlyphX(layoutXSs, note, glyphBBoxLeft, glyphWidthSs);

        assertThat(actual).isCloseTo(expected, within(1e-9));
    }

    // stemCenterXOffsetSs — all 4 branches: minim-up, minim-down, black-up, black-down
    @Test
    void testStemCenterXOffsetSsMinimUp() {
        var expected = SMuFLConstants.NOTEHEAD_HALF_STEM_UP_SE.x() - NoteGeometry.STEM_WIDTH_SS / 2.0;
        assertThat(RenderingUtils.stemCenterXOffsetSs(ElementType.MINIM, StaffElement.Direction.UP))
            .isCloseTo(expected, within(1e-9));
    }

    @Test
    void testStemCenterXOffsetSsMinimDown() {
        var expected = SMuFLConstants.NOTEHEAD_HALF_STEM_DOWN_NW.x() + NoteGeometry.STEM_WIDTH_SS / 2.0;
        assertThat(RenderingUtils.stemCenterXOffsetSs(ElementType.MINIM, StaffElement.Direction.DOWN))
            .isCloseTo(expected, within(1e-9));
    }

    @Test
    void testStemCenterXOffsetSsBlackNoteheadUp() {
        var expected = SMuFLConstants.NOTEHEAD_BLACK_STEM_UP_SE.x() - NoteGeometry.STEM_WIDTH_SS / 2.0;
        assertThat(RenderingUtils.stemCenterXOffsetSs(ElementType.CROTCHET, StaffElement.Direction.UP))
            .isCloseTo(expected, within(1e-9));
    }

    @Test
    void testStemCenterXOffsetSsBlackNoteheadDown() {
        var expected = SMuFLConstants.NOTEHEAD_BLACK_STEM_DOWN_NW.x() + NoteGeometry.STEM_WIDTH_SS / 2.0;
        assertThat(RenderingUtils.stemCenterXOffsetSs(ElementType.CROTCHET, StaffElement.Direction.DOWN))
            .isCloseTo(expected, within(1e-9));
    }

    // ======================================================================
    // decorationSelectionColor
    // ======================================================================

    private LineInvariants invariantsFor(LineComponent.@Nullable SelectionProvider selectionProvider) {
        return RenderContextTestHelper.newContext(new Song())
            .setSelectionProvider(selectionProvider)
            .build();
    }

    @Test
    void testDecorationSelectionColorSelectedReturnsSelectionColor() {
        var element = new StaffElement(ElementType.CROTCHET);
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isDecorationSelected(element, 0)).thenReturn(true);

        var color = RenderingUtils.decorationSelectionColor(element, invariantsFor(selectionProvider));

        assertThat(color).isEqualTo(ScoreView.getSelectionColor());
    }

    @Test
    void testDecorationSelectionColorNotSelectedReturnsElementColor() {
        var element = new StaffElement(ElementType.CROTCHET);
        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isDecorationSelected(element, 0)).thenReturn(false);

        var color = RenderingUtils.decorationSelectionColor(element, invariantsFor(selectionProvider));

        assertThat(color).isEqualTo(Color.BLACK);
    }

    @Test
    void testDecorationSelectionColorNoSelectionProviderReturnsElementColor() {
        var element = new StaffElement(ElementType.CROTCHET);

        var color = RenderingUtils.decorationSelectionColor(element, invariantsFor(null));

        assertThat(color).isEqualTo(Color.BLACK);
    }
}
