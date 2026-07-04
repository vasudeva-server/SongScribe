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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
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
     * Renders a down-stem note with the given articulations and returns the spy so
     * callers can capture the drawString calls to see which glyphs were drawn.
     */
    private Graphics2D renderAndSpy(Articulation... articulations) {
        return renderAndSpy(false, articulations);
    }

    /**
     * Renders a note with the given stem direction and articulations, returning the
     * spy so callers can capture the drawString calls to see which glyphs were drawn.
     */
    private Graphics2D renderAndSpy(boolean upper, Articulation... articulations) {
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var note = ElementType.CROTCHET.newInstance();
        note.setUpper(upper);

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
    void testRenderSoloAccentFillsAccentWedge() {
        var accent = new Articulation(ArticulationType.ACCENT);
        var g2Spy = renderAndSpy(accent);

        // Accent is drawn as a filled AccentShape wedge, not a drawString glyph.
        verify(g2Spy).fill(any(Shape.class));
        verify(g2Spy, times(0)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testRenderSkipsArticulationWithNoDecorationLayout() {
        // Non-preview path: layoutResult comes from invariants.getLayoutResult(), which
        // RenderContextTestHelper.newContext seeds as an empty LayoutResult. With no
        // override X, getDecorationLayout returns null for every articulation, exercising
        // the `if (layout == null) continue;` skip branch — render() must not draw or throw.
        var g2Spy = spy(RenderContextTestHelper.realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var note = ElementType.CROTCHET.newInstance();
        note.addArticulation(new Articulation(ArticulationType.STACCATO));

        var frame = ElementFrame.LINE_LEVEL.withElement(0, Double.NaN);
        RENDERER.render(invariants, frame, note, g2Spy);

        verify(g2Spy, times(0)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testRenderStaccatoPlusAccentDrawsSeparateGlyphs() {
        var staccato = new Articulation(ArticulationType.STACCATO);
        var accent = new Articulation(ArticulationType.ACCENT);
        var g2Spy = renderAndSpy(staccato, accent);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        verify(g2Spy).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());
        verify(g2Spy).fill(any(Shape.class));

        assertThat(glyphCaptor.getValue()).isEqualTo(SMuFLGlyph.ARTIC_STACCATO_ABOVE.asString());
    }

    // ==========================================================================
    // render() — glyph selection branches, below-staff (up-stem note)
    // ==========================================================================

    @Test
    void testRenderSoloStaccatoDrawsStaccatoBelowGlyphForUpStemNote() {
        var staccato = new Articulation(ArticulationType.STACCATO);
        var g2Spy = renderAndSpy(true, staccato);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        // drawBravuraGlyph calls g2.drawString with the glyph string
        verify(g2Spy).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());

        assertThat(glyphCaptor.getValue())
            .isEqualTo(SMuFLGlyph.ARTIC_STACCATO_BELOW.asString());
    }

    @Test
    void testRenderSoloAccentFillsAccentWedgeForUpStemNote() {
        var accent = new Articulation(ArticulationType.ACCENT);
        var g2Spy = renderAndSpy(true, accent);

        verify(g2Spy).fill(any(Shape.class));
        verify(g2Spy, times(0)).drawString(anyString(), anyFloat(), anyFloat());
    }

    @Test
    void testRenderStaccatoPlusAccentDrawsSeparateBelowGlyphsForUpStemNote() {
        var staccato = new Articulation(ArticulationType.STACCATO);
        var accent = new Articulation(ArticulationType.ACCENT);
        var g2Spy = renderAndSpy(true, staccato, accent);
        var glyphCaptor = ArgumentCaptor.forClass(String.class);

        verify(g2Spy).drawString(glyphCaptor.capture(), anyFloat(), anyFloat());
        verify(g2Spy).fill(any(Shape.class));

        assertThat(glyphCaptor.getValue()).isEqualTo(SMuFLGlyph.ARTIC_STACCATO_BELOW.asString());
    }
}
