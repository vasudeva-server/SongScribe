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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.hit.HitTarget;
import songscribe.smufl.SMuFLGlyph;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

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

    // ==========================================================================
    // render() — per-articulation selection color
    // ==========================================================================

    /**
     * Renders a note carrying both a staccato and an accent, with {@code selected} reported as
     * the selected target, and returns the color installed on the graphics context at each of
     * the two draw calls: the staccato reaches {@code drawString}, the accent {@code fill}.
     */
    private record ArticulationColors(List<Color> staccato, List<Color> accent) {}

    private static ArticulationColors renderColorsWithSelected(
        Articulation staccato,
        Articulation accent,
        Articulation selected
    ) {
        var note = ElementType.CROTCHET.newInstance();
        note.addArticulation(staccato);
        note.addArticulation(accent);

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Articulation(selected), 0)).thenReturn(true);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setSelectionProvider(selectionProvider)
            .build();

        var g2Spy = spy(RenderContextTestHelper.realG2());
        var staccatoColors = new ArrayList<Color>();
        var accentColors = new ArrayList<Color>();
        doAnswer(invocation -> {
            staccatoColors.add(g2Spy.getColor());
            return null;
        }).when(g2Spy).drawString(anyString(), anyFloat(), anyFloat());
        doAnswer(invocation -> {
            accentColors.add(g2Spy.getColor());
            return null;
        }).when(g2Spy).fill(any(Shape.class));

        RENDERER.render(invariants, ElementFrame.LINE_LEVEL.withElement(0, ELEMENT_X_SS), note, g2Spy);

        return new ArticulationColors(staccatoColors, accentColors);
    }

    @Test
    void testRenderColorsOnlyTheSelectedArticulation() {
        var staccato = new Articulation(ArticulationType.STACCATO);
        var accent = new Articulation(ArticulationType.ACCENT);

        var colors = renderColorsWithSelected(staccato, accent, staccato);

        assertThat(colors.staccato())
            .as("the selected staccato draws in the selection color")
            .containsOnly(ScoreView.getSelectionColor());
        assertThat(colors.accent())
            .as("its unselected neighbour keeps the element color")
            .containsOnly(Color.BLACK);
    }

    @Test
    void testRenderColorsTheOtherArticulationWhenItIsTheSelectedOne() {
        var staccato = new Articulation(ArticulationType.STACCATO);
        var accent = new Articulation(ArticulationType.ACCENT);

        var colors = renderColorsWithSelected(staccato, accent, accent);

        assertThat(colors.accent()).containsOnly(ScoreView.getSelectionColor());
        assertThat(colors.staccato()).containsOnly(Color.BLACK);
    }
}
