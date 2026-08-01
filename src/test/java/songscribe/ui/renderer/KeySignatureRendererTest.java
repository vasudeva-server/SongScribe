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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import module java.desktop;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.smufl.SMuFLGlyph;

class KeySignatureRendererTest extends UnitTest {

    private static final KeySignatureRenderer RENDERER = KeySignatureRenderer.getInstance();

    // Arbitrary line width for key-change tests
    private static final double LINE_WIDTH_SS = 80.0;

    // Arbitrary X the key signature is laid out from
    private static final double KEY_SIGNATURE_X_SS = 4.0;

    private static final int KEY_ACCIDENTAL_COUNT = 4;

    /** Drawing X is rounded to float, so compare in staff spaces with a slack of this much. */
    private static final double TOLERANCE_SS = 1e-4;

    /**
     * Builds a real {@link Graphics2D} from a headless buffered image so that
     * font/color/transform operations inside the renderer do not throw.
     */
    private static Graphics2D realG2() {
        var img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics();
    }

    // ==========================================================================
    // render() no-op for C major (row 13 — adequate, kept)
    // ==========================================================================

    @Test
    void testRenderIsNoOpForCMajor() {
        var g2 = mock(Graphics2D.class);
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var keySig = new KeySignature(KeyType.NONE, 0);

        RENDERER.render(invariants, ElementFrame.LINE_LEVEL, keySig, g2);

        verifyNoInteractions(g2);
    }

    // ==========================================================================
    // render() horizontal spacing — LilyPond nests accidentals at their glyph width
    // ==========================================================================

    @Test
    void testRenderAdvancesEachSharpByItsGlyphWidth() {
        assertAccidentalsAdvanceByGlyphWidth(KeyType.SHARPS, SMuFLGlyph.ACCIDENTAL_SHARP);
    }

    @Test
    void testRenderAdvancesEachFlatByItsGlyphWidth() {
        assertAccidentalsAdvanceByGlyphWidth(KeyType.FLATS, SMuFLGlyph.ACCIDENTAL_FLAT);
    }

    private static void assertAccidentalsAdvanceByGlyphWidth(KeyType keyType, SMuFLGlyph glyph) {
        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var keySig = new KeySignature(keyType, KEY_ACCIDENTAL_COUNT);
        keySig.setPosition(KEY_SIGNATURE_X_SS, 0);

        RENDERER.render(invariants, ElementFrame.LINE_LEVEL, keySig, g2spy);

        var xValues = capturedXValues(g2spy, KEY_ACCIDENTAL_COUNT);
        var advanceSs = StaffHeaderMetrics.accidentalInkBboxSs(glyph);

        assertThat(xValues.getFirst()).isCloseTo(KEY_SIGNATURE_X_SS, within(TOLERANCE_SS));

        for (var i = 1; i < xValues.size(); i++) {
            assertThat(xValues.get(i) - xValues.get(i - 1))
                .describedAs("advance from accidental %d to %d", i - 1, i)
                .isCloseTo(advanceSs, within(TOLERANCE_SS));
        }
    }

    // ==========================================================================
    // renderKeyChange horizontal spacing
    // ==========================================================================

    @Test
    void testRenderKeyChangeRightAlignsTheLastAccidental() {
        final var currentCount = 3;
        final var newCount = 2;

        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(currentCount);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.FLATS);
        nextLine.setKeyAccidentalCount(newCount);

        RENDERER.renderKeyChange(g2spy, line, nextLine, LINE_WIDTH_SS, invariants);

        var xValues = capturedXValues(g2spy, currentCount + newCount);
        var lastRightEdgeSs = xValues.getLast()
            + StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_FLAT);

        assertThat(lastRightEdgeSs)
            .isCloseTo(
                LINE_WIDTH_SS - KeySignatureRenderer.KEY_CHANGE_RIGHT_MARGIN_SS, within(TOLERANCE_SS));
    }

    @Test
    void testRenderKeyChangeSeparatesTheCancellationFromTheNewKey() {
        final var currentCount = 3;
        final var newCount = 2;

        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(currentCount);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.FLATS);
        nextLine.setKeyAccidentalCount(newCount);

        RENDERER.renderKeyChange(g2spy, line, nextLine, LINE_WIDTH_SS, invariants);

        var xValues = capturedXValues(g2spy, currentCount + newCount);
        var naturalAdvanceSs = StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_NATURAL);

        // Last natural to first flat: the natural's own width plus the cancellation gap,
        // with no kerning reaching across the group boundary.
        assertThat(xValues.get(currentCount) - xValues.get(currentCount - 1))
            .isCloseTo(naturalAdvanceSs + StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS, within(TOLERANCE_SS));

        // Within the new key the flats nest at their glyph width.
        assertThat(xValues.getLast() - xValues.get(currentCount))
            .isCloseTo(StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_FLAT), within(TOLERANCE_SS));
    }

    /**
     * Dropping accidentals within one key type puts the key signature first and the naturals
     * after it — the reverse of the order above, which LilyPond spaces differently.
     */
    @Test
    void testRenderKeyChangeUsesTheReverseGapWhenTheNaturalsComeSecond() {
        final var currentCount = 4;
        final var newCount = 2;

        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(currentCount);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.SHARPS);
        nextLine.setKeyAccidentalCount(newCount);

        RENDERER.renderKeyChange(g2spy, line, nextLine, LINE_WIDTH_SS, invariants);

        var xValues = capturedXValues(g2spy, currentCount);
        var sharpAdvanceSs = StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_SHARP);

        // Last sharp of the new key to the first cancelling natural.
        assertThat(xValues.get(newCount) - xValues.get(newCount - 1))
            .isCloseTo(sharpAdvanceSs + StaffHeaderMetrics.KEY_TO_CANCELLATION_GAP_SS, within(TOLERANCE_SS));
    }

    @Test
    void testRenderKeyChangeKernsNaturalsApart() {
        final var currentCount = 3;
        final var newCount = 0;

        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(currentCount);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.NONE);
        nextLine.setKeyAccidentalCount(newCount);

        RENDERER.renderKeyChange(g2spy, line, nextLine, LINE_WIDTH_SS, invariants);

        var xValues = capturedXValues(g2spy, currentCount);
        var naturalAdvanceSs = StaffHeaderMetrics.accidentalInkBboxSs(SMuFLGlyph.ACCIDENTAL_NATURAL);
        var totalKerningSs = 0.0;

        for (var i = 1; i < xValues.size(); i++) {
            var previousSp = KeySignatureRenderer.SHARP_STAFF_POSITIONS[i - 1];
            var kerningSs =
                StaffHeaderMetrics.naturalKerningSs(previousSp, KeySignatureRenderer.SHARP_STAFF_POSITIONS[i]);
            totalKerningSs += kerningSs;

            assertThat(xValues.get(i) - xValues.get(i - 1))
                .describedAs("advance from natural %d to %d", i - 1, i)
                .isCloseTo(naturalAdvanceSs + kerningSs, within(TOLERANCE_SS));
        }

        // The first two naturals of a sharp cancellation overlap vertically, so at least
        // one pair must actually be pushed apart — otherwise this pins nothing.
        assertThat(totalKerningSs).isPositive();
    }

    /** Captures the X argument of exactly {@code expectedCount} drawString calls, in draw order. */
    private static List<Double> capturedXValues(Graphics2D g2spy, int expectedCount) {
        var xCaptor = ArgumentCaptor.forClass(Float.class);
        verify(g2spy, times(expectedCount)).drawString(anyString(), xCaptor.capture(), anyFloat());
        return xCaptor.getAllValues().stream().map(Float::doubleValue).toList();
    }

    // ==========================================================================
    // FLAT_STAFF_POSITIONS and SHARP_STAFF_POSITIONS arrays (row 19)
    // ==========================================================================

    @Test
    void testFlatStaffPositionsEncodeBeadgcfOrder() {
        // Expected staff positions for flats in BEADGCF order
        // (relative to middle line, in staff-position units)
        int[] expectedPositions = {0, -3, 1, -2, 2, -1, 3};

        assertThat(KeySignatureRenderer.FLAT_STAFF_POSITIONS)
            .containsExactly(expectedPositions);
    }

    @Test
    void testSharpStaffPositionsEncodeFcgdaebOrder() {
        // Expected staff positions for sharps in FCGDAEB order
        int[] expectedPositions = {-4, -1, -5, -2, 1, -3, 0};

        assertThat(KeySignatureRenderer.SHARP_STAFF_POSITIONS)
            .containsExactly(expectedPositions);
    }

    // ==========================================================================
    // renderKeyChange — 4 branches (row 20)
    // ==========================================================================

    @Test
    void testRenderKeyChangeIsNoOpForIdenticalKeys() {
        var g2 = mock(Graphics2D.class);
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(2);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.SHARPS);
        nextLine.setKeyAccidentalCount(2);

        RENDERER.renderKeyChange(g2, line, nextLine, LINE_WIDTH_SS, invariants);

        verifyNoInteractions(g2);
    }

    @Test
    void testRenderKeyChangeSameTypeAddingAccidentalsDrawsOnlyNewKey() {
        // line: 2 sharps → nextLine: 4 sharps (adding)
        // Should draw 4 accidentals (the full new key, no naturals)
        final int currentCount = 2;
        final int newCount = 4;

        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(currentCount);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.SHARPS);
        nextLine.setKeyAccidentalCount(newCount);

        RENDERER.renderKeyChange(g2spy, line, nextLine, LINE_WIDTH_SS, invariants);

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2spy, times(newCount)).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());
        // All drawn glyphs should be the sharp glyph (no naturals for adding)
        assertThat(glyphCaptor.getAllValues())
            .allMatch(s -> s.equals(SMuFLGlyph.ACCIDENTAL_SHARP.asString()));
    }

    @Test
    void testRenderKeyChangeSameTypeRemovingAccidentalsDrawsNewKeyAndNaturals() {
        // line: 4 sharps → nextLine: 2 sharps (removing 2)
        // Should draw 2 sharps + 2 naturals = 4 total drawString calls
        final int currentCount = 4;
        final int newCount = 2;
        final int removedCount = currentCount - newCount;

        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(currentCount);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.SHARPS);
        nextLine.setKeyAccidentalCount(newCount);

        RENDERER.renderKeyChange(g2spy, line, nextLine, LINE_WIDTH_SS, invariants);

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2spy, times(newCount + removedCount)).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());
        var allGlyphs = glyphCaptor.getAllValues();
        // First newCount glyphs should be sharps, last removedCount should be naturals
        assertThat(allGlyphs.subList(0, newCount))
            .allMatch(s -> s.equals(SMuFLGlyph.ACCIDENTAL_SHARP.asString()));
        assertThat(allGlyphs.subList(newCount, newCount + removedCount))
            .allMatch(s -> s.equals(SMuFLGlyph.ACCIDENTAL_NATURAL.asString()));
    }

    @Test
    void testRenderKeyChangeDifferentTypeDrawsNaturalsThenNewKey() {
        // line: 3 sharps → nextLine: 2 flats (type change)
        // Should draw 3 naturals + 2 flats = 5 total drawString calls
        final int currentCount = 3;
        final int newCount = 2;

        var g2spy = spy(realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var line = detachedLine();
        line.setKeyType(KeyType.SHARPS);
        line.setKeyAccidentalCount(currentCount);
        var nextLine = detachedLine();
        nextLine.setKeyType(KeyType.FLATS);
        nextLine.setKeyAccidentalCount(newCount);

        RENDERER.renderKeyChange(g2spy, line, nextLine, LINE_WIDTH_SS, invariants);

        var glyphCaptor = ArgumentCaptor.forClass(String.class);
        verify(g2spy, times(currentCount + newCount)).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());
        var allGlyphs = glyphCaptor.getAllValues();
        // First currentCount glyphs should be naturals, last newCount should be flats
        assertThat(allGlyphs.subList(0, currentCount))
            .allMatch(s -> s.equals(SMuFLGlyph.ACCIDENTAL_NATURAL.asString()));
        assertThat(allGlyphs.subList(currentCount, currentCount + newCount))
            .allMatch(s -> s.equals(SMuFLGlyph.ACCIDENTAL_FLAT.asString()));
    }

    // ==========================================================================
    // getGlyphForKeyType (row 21)
    // ==========================================================================

    @Test
    void testGetGlyphForKeyTypeReturnsFlatGlyphForFlats() {
        assertThat(KeySignatureRenderer.getGlyphForKeyType(KeyType.FLATS))
            .isEqualTo(SMuFLGlyph.ACCIDENTAL_FLAT);
    }

    @Test
    void testGetGlyphForKeyTypeReturnsSharpGlyphForSharps() {
        assertThat(KeySignatureRenderer.getGlyphForKeyType(KeyType.SHARPS))
            .isEqualTo(SMuFLGlyph.ACCIDENTAL_SHARP);
    }

    @Test
    void testGetGlyphForKeyTypeThrowsForNone() {
        assertThatThrownBy(() -> KeySignatureRenderer.getGlyphForKeyType(KeyType.NONE))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
