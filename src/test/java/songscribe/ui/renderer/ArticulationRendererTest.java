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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.smufl.SMuFLGlyph;

class ArticulationRendererTest extends UnitTest {

    private static final ArticulationRenderer RENDERER = ArticulationRenderer.getInstance();

    // X override that drives the insertion-preview path:
    // ArticulationRenderer.render() branches on frame.hasOverrideElementX() to use
    // NoteAttachedStacker.computePreviewDecorationLayouts(), which populates the
    // LayoutResult from scratch without requiring a full layout pipeline.
    private static final double ELEMENT_X_SS = 4.0;

    /**
     * Renders a note with the given articulations and returns the spy so callers
     * can capture the drawString calls to see which glyphs were drawn.
     */
    private Graphics2D renderAndSpy(Articulation... articulations) {
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var note = ElementType.CROTCHET.newInstance();

        for (var a : articulations) {
            note.addArticulation(a);
        }

        // withElement(0, xSs) sets both currentElementIndex=0 (so getDecorationColor uses
        // getElementColor(0) without needing a current line) and overrideElementXSs=ELEMENT_X_SS.
        var frame = ElementFrame.LINE_LEVEL.withElement(0, ELEMENT_X_SS);

        RENDERER.render(invariants, frame, note, g2Spy);

        return g2Spy;
    }

    // ==========================================================================
    // render() — glyph selection branches (row 26)
    // ==========================================================================

    @Test
    void testRenderSoloStaccatoDrawsStaccatoGlyph() {
        var staccato = new Articulation(ArticulationType.STACCATO);
        var g2Spy = renderAndSpy(staccato);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        // drawBravuraGlyph calls g2.drawString with the glyph string
        verify(g2Spy).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getValue())
            .isEqualTo(SMuFLGlyph.ARTIC_STACCATO_ABOVE.asString());
    }

    @Test
    void testRenderSoloAccentDrawsAccentGlyph() {
        var accent = new Articulation(ArticulationType.ACCENT);
        var g2Spy = renderAndSpy(accent);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        verify(g2Spy).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getValue())
            .isEqualTo(SMuFLGlyph.ARTIC_ACCENT_ABOVE.asString());
    }

    @Test
    void testRenderStaccatoPlusAccentComboDrawsComboGlyphOnce() {
        // When both staccato and accent are present, the combo glyph
        // ARTIC_ACCENT_STACCATO_ABOVE is drawn once (via the staccato loop entry).
        // The accent articulation has no layout entry in combo mode and is skipped.
        var staccato = new Articulation(ArticulationType.STACCATO);
        var accent = new Articulation(ArticulationType.ACCENT);
        var g2Spy = renderAndSpy(staccato, accent);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        verify(g2Spy, times(1)).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getValue())
            .isEqualTo(SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE.asString());
    }
}
