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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.LayoutResult;

/**
 * Tests for {@link AnnotationRenderer#render}: verifies baseline-Y computation
 * (using FontMetrics ascent + layoutYToComponentYSs) and the IllegalStateException
 * when no DecorationLayout is present for the annotation.
 */
class AnnotationRendererTest extends UnitTest {

    private static final double TOLERANCE = 0.001;
    private static final AnnotationRenderer RENDERER = AnnotationRenderer.getInstance();
    private static final double MIDDLE_LINE_Y_SS = 5.0;
    private static final double DECORATION_X_SS = 3.5;
    private static final double DECORATION_Y_SS = -2.0;
    private static final double DECORATION_WIDTH_SS = 5.0;
    private static final double DECORATION_HEIGHT_SS = 1.0;
    private static final String ANNOTATION_TEXT = "dolce";
    private static final int IMAGE_SIZE = 200;

    /**
     * Returns a real {@link Graphics2D} backed by a headless buffered image so
     * that font-metrics calls inside the renderer do not throw.
     */
    private static Graphics2D realG2() {
        var image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        return image.createGraphics();
    }

    /**
     * Builds the annotation font the same way {@link LineInvariants#getAnnotationFont()} does,
     * so the expected ascent value can be computed independently of the renderer.
     */
    private static Font annotationFont() {
        return DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
    }

    /**
     * Computes the expected baseline Y in staff-space units using the same formula
     * as the renderer: {@code middleLineYSs + decorationYSs + fontAscentSs(font)}. The
     * ascent term is a document-scale staff-space quantity ({@code pxToSs}-based).
     */
    private static double expectedBaselineYSs(Font font, double middleLineYSs, double decorationYSs) {
        var ascentSs = ScaleContext.fontAscentSs(font).value();
        return middleLineYSs + decorationYSs + ascentSs;
    }

    // ======================================================================
    // Baseline Y computation — row 34
    // ======================================================================

    @Test
    void testRender_baselineYCombinesMiddleLineAndDecorationYAndFontAscent() {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);

        // Attach the annotation to the note
        var attachment = new AnnotationAttachment(ANNOTATION_TEXT);
        note.addAttachment(attachment);

        // Put a DecorationLayout keyed on the AnnotationAttachment itself — that is
        // what findAttachmentDecorationLayout looks up via findByAttachment.
        var layout = new LayoutResult.DecorationLayout(
            DECORATION_X_SS, DECORATION_Y_SS, DECORATION_WIDTH_SS, DECORATION_HEIGHT_SS, 0.0);
        var layoutResult = LayoutResult.builder()
            .putDecorationLayout(attachment, layout)
            .build();

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(layoutResult)
            .setCurrentLine(line)
            .setMiddleLineYSs(MIDDLE_LINE_Y_SS)
            .build();

        var g2Spy = spy(realG2());
        var frame = ElementFrame.LINE_LEVEL.withElement(0, Double.NaN);
        RENDERER.render(invariants, frame, note, g2Spy);

        // Capture the float x and y arguments passed to drawString(String, float, float)
        var xCaptor = ArgumentCaptor.forClass(Float.class);
        var yCaptor = ArgumentCaptor.forClass(Float.class);
        verify(g2Spy).drawString(
            org.mockito.ArgumentMatchers.eq(ANNOTATION_TEXT),
            xCaptor.capture(),
            yCaptor.capture()
        );

        var font = annotationFont();
        var expectedY = expectedBaselineYSs(font, MIDDLE_LINE_Y_SS, DECORATION_Y_SS);

        assertThat((double) xCaptor.getValue())
            .as("x passed to drawString must equal decorationLayout.xSs()")
            .isCloseTo(DECORATION_X_SS, within(TOLERANCE));

        assertThat((double) yCaptor.getValue())
            .as("y passed to drawString must equal middleLineYSs + decorationYSs + fontAscentSs(font)")
            .isCloseTo(expectedY, within(TOLERANCE));
    }

    // ======================================================================
    // Missing layout → IllegalStateException — row 35
    // ======================================================================

    @Test
    void testRender_noDecorationLayout_throwsIllegalStateException() {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);

        // Attach the annotation but do NOT add any DecorationLayout to the result
        var attachment = new AnnotationAttachment(ANNOTATION_TEXT);
        note.addAttachment(attachment);

        var invariants = RenderContextTestHelper.newContext(new Song())
            .setLayoutResult(LayoutResult.builder().build())
            .setCurrentLine(line)
            .build();

        var g2 = realG2();
        var frame = ElementFrame.LINE_LEVEL.withElement(0, Double.NaN);

        assertThatThrownBy(() -> RENDERER.render(invariants, frame, note, g2))
            .isInstanceOf(IllegalStateException.class);
    }
}
