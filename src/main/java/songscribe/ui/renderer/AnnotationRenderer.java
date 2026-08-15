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

import java.awt.Graphics2D;

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

/**
 * Renders text annotations attached to notes.
 * <p>
 * Annotations are text labels that appear above or below notes,
 * typically used for performance instructions or other markings.
 */
public final class AnnotationRenderer implements ElementRenderer<StaffElement> {

    private static final AnnotationRenderer INSTANCE = new AnnotationRenderer();

    private AnnotationRenderer() {
    }

    public static AnnotationRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        var attachment = element.findAttachment(AnnotationAttachment.class);

        if (attachment == null) {
            return;
        }

        var decorationLayout = invariants.getLayoutResult().findAttachmentDecorationLayout(
            element, AnnotationAttachment.class);

        if (decorationLayout == null) {
            throw new IllegalStateException(
                "No DecorationLayout found for AnnotationAttachment on note");
        }

        var annotationFont = invariants.getAnnotationFont();

        try (var _ = GraphicsState.save(g2, FONT, COLOR)) {
            g2.setFont(ScaleContext.scaleFont(annotationFont));
            // The annotation is selectable on its own, so the color is keyed on the attachment
            // rather than on the note it hangs off.
            RenderingUtils.applyDecorationColor(
                g2, new HitTarget.Attachment(attachment), element, invariants, frame);

            var ascentSs = ScaleContext.fontAscentSs(annotationFont).value();
            var xSs = decorationLayout.xSs();
            var baselineYSs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants) + ascentSs;

            g2.drawString(attachment.getAnnotation().getAnnotation(), (float) xSs, (float) baselineYSs);
        }
    }
}
