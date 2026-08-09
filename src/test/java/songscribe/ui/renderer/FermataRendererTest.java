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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Song;
import songscribe.hit.HitTarget;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;

class FermataRendererTest extends UnitTest {

    private static final FermataRenderer RENDERER = FermataRenderer.getInstance();

    // The drawn x is cast to float before reaching drawString, so the oracle is compared at float
    // precision, not double. This is still orders of magnitude tighter than the notehead-center vs
    // width/2 gap the old centeredGlyphX regression would introduce.
    private static final double FLOAT_TOLERANCE_SS = 1e-5;

    // X override that drives the insertion-preview path: render() branches on
    // frame.hasOverrideElementX() to use NoteAttachedStacker.computePreviewDecorationLayouts(),
    // which populates the LayoutResult from scratch without a full layout pipeline.
    private static final double ELEMENT_X_SS = 4.0;

    // The fermata glyph's own geometry, from the same SMuFL bbox the renderer draws against.
    private static final double FERMATA_BBOX_LEFT_SS =
        SMuFLMetadata.requireBBox(SMuFLGlyph.FERMATA_ABOVE).left();

    private static final double FERMATA_WIDTH_SS =
        SMuFLMetadata.requireBBox(SMuFLGlyph.FERMATA_ABOVE).width();

    @Test
    void testDrawnGlyphIsCenteredOnTheNotehead() {
        // The renderer's x contract: it draws the glyph origin as storedX - FERMATA_BBOX_LEFT_SS,
        // where storedX is the stacker's already-notehead-centered visual-left edge. The glyph's
        // visual center (origin + bboxLeft + width/2) must therefore land exactly on the notehead
        // center. Reverting the renderer to centeredGlyphX (which re-applies the centering the
        // stored x already carries) shifts the drawn glyph by noteheadCenter - width/2 and this
        // assertion catches it — no other test guards the drawn x.
        var note = ElementType.CROTCHET.newInstance();
        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var g2Spy = spy(RenderContextTestHelper.realG2());
        var invariants = RenderContextTestHelper.newContext(new Song()).build();
        var frame = ElementFrame.LINE_LEVEL.withElement(0, ELEMENT_X_SS);

        RENDERER.render(invariants, frame, note, g2Spy);

        var xCaptor = ArgumentCaptor.forClass(Float.class);
        verify(g2Spy).drawString(anyString(), xCaptor.capture(), anyFloat());

        var drawnOriginXSs = xCaptor.getValue();
        var drawnGlyphCenterSs = drawnOriginXSs + FERMATA_BBOX_LEFT_SS + FERMATA_WIDTH_SS / 2.0;
        var noteheadCenterSs = ELEMENT_X_SS + NoteGeometry.getNoteheadCenterXSs(note);

        assertThat(drawnGlyphCenterSs)
            .describedAs("the drawn fermata glyph must be visually centered on the notehead")
            .isCloseTo(noteheadCenterSs, within(FLOAT_TOLERANCE_SS));
    }

    // ==========================================================================
    // render() — the fermata's own selection color
    // ==========================================================================

    /**
     * Renders a note's fermata, with the selection provider reporting that fermata as
     * {@code selected}, and returns the colors installed on the graphics context at each
     * glyph draw.
     */
    private static List<Color> drawColorsFor(boolean selected) {
        var note = ElementType.CROTCHET.newInstance();
        var fermata = new FermataAttachment(note);
        note.addAttachment(fermata);

        var selectionProvider = mock(LineComponent.SelectionProvider.class);
        when(selectionProvider.isSelected(new HitTarget.Attachment(fermata), 0)).thenReturn(selected);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setSelectionProvider(selectionProvider)
            .build();

        var g2Spy = spy(RenderContextTestHelper.realG2());
        var colors = new ArrayList<Color>();
        doAnswer(invocation -> {
            colors.add(g2Spy.getColor());
            return null;
        }).when(g2Spy).drawString(anyString(), anyFloat(), anyFloat());

        RENDERER.render(invariants, ElementFrame.LINE_LEVEL.withElement(0, ELEMENT_X_SS), note, g2Spy);

        return colors;
    }

    @Test
    void testRenderDrawsSelectedFermataInSelectionColor() {
        assertThat(drawColorsFor(true)).containsOnly(ScoreView.getSelectionColor());
    }

    @Test
    void testRenderDrawsUnselectedFermataInTheElementColor() {
        assertThat(drawColorsFor(false)).containsOnly(Color.BLACK);
    }
}
