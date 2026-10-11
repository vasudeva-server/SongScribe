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
import songscribe.dom.DocumentScale;
import songscribe.dom.StaffElement;
import songscribe.font.TextMeasurement;
import songscribe.hit.HitTarget;
import songscribe.smufl.SMuFLGlyph;
import songscribe.ui.component.ScoreView;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

/**
 * Renders text annotations attached to notes.
 * <p>
 * Annotations are text labels that appear above or below notes,
 * typically used for performance instructions or other markings. An annotation that a lint
 * finding names ({@link songscribe.lint.LintReport#isAnnotationFlagged}) keeps its own color
 * and gets a sawtooth squiggle under its text in {@link ScoreView#getLintColor()}, whatever
 * color the text is drawn in, with the squiggle's ink top {@value #SQUIGGLE_GAP_DOC_PX}
 * document pixels below the text baseline.
 */
public final class AnnotationRenderer implements ElementRenderer<StaffElement> {

    private static final double SQUIGGLE_SCALE = 0.5;

    private static final ScaledGlyph SQUIGGLE =
        new ScaledGlyph(SMuFLGlyph.WIGGLE_VIBRATO_MEDIUM_FASTER, SQUIGGLE_SCALE);

    private static final double SQUIGGLE_GAP_DOC_PX = 2.0;

    private static final double SQUIGGLE_GAP_SS = SQUIGGLE_GAP_DOC_PX / DocumentScale.PIXELS_PER_STAFF_SPACE;

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

        var decorationLayout = invariants.getLayoutResult().decorationLayouts().findAttachmentDecorationLayout(
            element, AnnotationAttachment.class);

        if (decorationLayout == null) {
            throw new IllegalStateException(
                "No DecorationLayout found for AnnotationAttachment on note");
        }

        var annotationFont = invariants.getAnnotationFont();
        var text = attachment.getAnnotation().text();
        var xSs = decorationLayout.xSs();
        var ascentSs = TextMeasurement.fontAscentSs(annotationFont).value();
        var baselineYSs = RenderingUtils.layoutYToComponentYSs(decorationLayout.ySs(), invariants) + ascentSs;

        // The annotation is selectable on its own, so its color is keyed on the attachment
        // rather than on the note it hangs off.
        try (var _ = GraphicsState.save(g2, FONT, COLOR)) {
            g2.setFont(DocumentScale.fontSizedInSs(annotationFont));
            g2.setColor(RenderingUtils.decorationColor(new HitTarget.Attachment(attachment), element, invariants, frame));
            g2.drawString(text, (float) xSs, (float) baselineYSs);

            if (invariants.getLintReport().isAnnotationFlagged(element)) {
                var widthSs = TextMeasurement.textWidthSs(annotationFont, text).value();

                g2.setColor(ScoreView.getLintColor());
                GlyphRun.centeredOn(
                    SQUIGGLE,
                    xSs,
                    xSs + widthSs,
                    baselineYSs + SQUIGGLE_GAP_SS - SQUIGGLE.inkTopYSs()
                ).draw(g2);
            }
        }
    }
}
