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

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.FONT;

import module java.desktop;


import songscribe.model.StaffElement;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.ScaleContext;

/**
 * Renders text annotations attached to notes.
 * <p>
 * Annotations are text labels that appear above or below notes,
 * typically used for performance instructions or other markings.
 */
public final class AnnotationRenderer extends BaseElementRenderer<StaffElement> {

    private static final AnnotationRenderer INSTANCE = new AnnotationRenderer();

    private AnnotationRenderer() {
    }

    public static AnnotationRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    protected void renderElement(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        var annotation = element.getAnnotation();

        if (annotation == null) {
            return;
        }

        var decorationLayout = ctx.getLayoutResult().findAttachmentDecorationLayout(
            element, AnnotationAttachment.class);

        if (decorationLayout == null) {
            throw new IllegalStateException(
                "No DecorationLayout found for AnnotationAttachment on note");
        }

        var annotationFont = ctx.getAnnotationFont();

        try (var ignored = GraphicsState.save(g2, FONT, COLOR)) {
            g2.setFont(ScaleContext.scaleFont(annotationFont));
            applyDecorationColor(g2, element, ctx);

            var metrics = g2.getFontMetrics(annotationFont);
            var ascentSs = ScaleContext.pxToSs(metrics.getAscent());
            var xSs = decorationLayout.xSs();
            var baselineYSs = layoutYToComponentYSs(decorationLayout.ySs(), ctx) + ascentSs;

            g2.drawString(annotation.getAnnotation(), (float) xSs, (float) baselineYSs);
        }
    }
}
