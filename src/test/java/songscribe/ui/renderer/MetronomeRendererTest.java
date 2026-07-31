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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.MetronomeAttachment;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.smufl.SMuFLGlyph;

class MetronomeRendererTest extends UnitTest {

    // Use TempoChangeRenderer (a concrete subclass) to access the protected
    // MetronomeRenderer methods from within the same package.
    private static final MetronomeRenderer RENDERER = TempoChangeRenderer.getInstance();

    private static final double TOLERANCE = 1e-9;

    // ==========================================================================
    // requireMetronomeGlyph — mapping + throw (row 33)
    // ==========================================================================

    @Test
    void testRequireMetronomeGlyphReturnsSemibreveGlyph() {
        assertThat(MetronomeRenderer.requireMetronomeGlyph(ElementType.SEMIBREVE))
            .isEqualTo(SMuFLGlyph.MET_NOTE_WHOLE);
    }

    @Test
    void testRequireMetronomeGlyphReturnsMinimGlyph() {
        assertThat(MetronomeRenderer.requireMetronomeGlyph(ElementType.MINIM))
            .isEqualTo(SMuFLGlyph.MET_NOTE_HALF_UP);
    }

    @Test
    void testRequireMetronomeGlyphReturnsCrotchetGlyph() {
        assertThat(MetronomeRenderer.requireMetronomeGlyph(ElementType.CROTCHET))
            .isEqualTo(SMuFLGlyph.MET_NOTE_QUARTER_UP);
    }

    @Test
    void testRequireMetronomeGlyphReturnsQuaverGlyph() {
        assertThat(MetronomeRenderer.requireMetronomeGlyph(ElementType.QUAVER))
            .isEqualTo(SMuFLGlyph.MET_NOTE_8TH_UP);
    }

    @Test
    void testRequireMetronomeGlyphReturnsSemiquaverGlyph() {
        assertThat(MetronomeRenderer.requireMetronomeGlyph(ElementType.SEMIQUAVER))
            .isEqualTo(SMuFLGlyph.MET_NOTE_16TH_UP);
    }

    @Test
    void testRequireMetronomeGlyphReturnsDemiSemiquaverGlyph() {
        assertThat(MetronomeRenderer.requireMetronomeGlyph(ElementType.DEMI_SEMIQUAVER))
            .isEqualTo(SMuFLGlyph.MET_NOTE_32ND_UP);
    }

    @Test
    void testRequireMetronomeGlyphThrowsForUnmappedType() {
        // SINGLE_BARLINE has no metronome glyph; RuntimeError.exit() is redirected
        // to AssertionError by UnitTest.suppressDialogs().
        assertThatThrownBy(() -> MetronomeRenderer.requireMetronomeGlyph(ElementType.SINGLE_BARLINE))
            .isInstanceOf(AssertionError.class);
    }

    // ==========================================================================
    // drawDurationEquals — advance accounting (row 34)
    // ==========================================================================

    @Test
    void testDrawDurationEqualsForUndottedNoteCallsTwoDrawStrings() {
        // Undotted note: draws (1) duration glyph, (2) "=" string — no dot glyph
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var attrFont = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        final double startXSs = 2.0;
        final double ySs = 0.0;

        RENDERER.drawDurationEquals(g2Spy, Duration.CROTCHET, startXSs, ySs, attrFont, Color.BLACK);

        verify(g2Spy, times(2)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testDrawDurationEqualsForDottedNoteCallsThreeDrawStrings() {
        // Dotted note: draws (1) duration glyph, (2) augmentation dot glyph, (3) "=" string
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var attrFont = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        final double startXSs = 2.0;
        final double ySs = 0.0;

        RENDERER.drawDurationEquals(g2Spy, Duration.CROTCHET_DOTTED, startXSs, ySs, attrFont, Color.BLACK);

        verify(g2Spy, times(3)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testDrawDurationEqualsReturnsXGreaterThanStart() {
        // The returned X must be further right than the start, regardless of scale or font.
        var g2 = RenderContextTestHelper.realG2();
        var attrFont = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        final double startXSs = 2.0;
        final double ySs = 0.0;

        var endX = RENDERER.drawDurationEquals(g2, Duration.CROTCHET, startXSs, ySs, attrFont, Color.BLACK);

        assertThat(endX).isGreaterThan(startXSs);
    }

    @Test
    void testDrawDurationEqualsForDottedNoteReturnsFurtherThanUndotted() {
        // dotted return − undotted return ≈ dotAdvanceWidthSs() (one extra dot advance)
        var g2 = RenderContextTestHelper.realG2();
        var attrFont = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        final double startXSs = 2.0;
        final double ySs = 0.0;
        double dotAdvance = MetronomeAttachment.dotAdvanceWidthSs();

        var undottedEnd = RENDERER.drawDurationEquals(g2, Duration.CROTCHET, startXSs, ySs, attrFont, Color.BLACK);
        var dottedEnd = RENDERER.drawDurationEquals(g2, Duration.CROTCHET_DOTTED, startXSs, ySs, attrFont, Color.BLACK);

        assertThat(dottedEnd - undottedEnd).isCloseTo(dotAdvance, within(TOLERANCE));
    }
}
