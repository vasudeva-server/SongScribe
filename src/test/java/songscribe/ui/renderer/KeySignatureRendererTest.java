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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.dom.Song;
import songscribe.smufl.SMuFLGlyph;

class KeySignatureRendererTest extends UnitTest {

    private static final KeySignatureRenderer RENDERER = KeySignatureRenderer.getInstance();

    // Arbitrary line width for key-change tests
    private static final double LINE_WIDTH_SS = 80.0;

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
    // FLAT_STAFF_POSITIONS and SHARP_STAFF_POSITIONS arrays (row 19)
    // ==========================================================================

    @Test
    void testFlatStaffPositionsEncodeBeadgcfOrder() {
        // Expected staff positions for flats in BEADGCF order
        // (relative to middle line, in staff-position units)
        final int[] expectedPositions = {0, -3, 1, -2, 2, -1, 3};

        assertThat(KeySignatureRenderer.FLAT_STAFF_POSITIONS)
            .containsExactly(expectedPositions);
    }

    @Test
    void testSharpStaffPositionsEncodeFcgdaebOrder() {
        // Expected staff positions for sharps in FCGDAEB order
        final int[] expectedPositions = {-4, -1, -5, -2, 1, -3, 0};

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
        assertThat(RENDERER.getGlyphForKeyType(KeyType.FLATS))
            .isEqualTo(SMuFLGlyph.ACCIDENTAL_FLAT);
    }

    @Test
    void testGetGlyphForKeyTypeReturnsSharpGlyphForSharps() {
        assertThat(RENDERER.getGlyphForKeyType(KeyType.SHARPS))
            .isEqualTo(SMuFLGlyph.ACCIDENTAL_SHARP);
    }

    @Test
    void testGetGlyphForKeyTypeThrowsForNone() {
        assertThatThrownBy(() -> RENDERER.getGlyphForKeyType(KeyType.NONE))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
