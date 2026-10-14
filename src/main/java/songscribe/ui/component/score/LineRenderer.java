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

package songscribe.ui.component.score;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Beam;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.engraving.EngravingConstants;
import songscribe.error.RuntimeError;
import songscribe.hit.HitTarget;
import songscribe.layout.CautionaryKeySignature;
import songscribe.layout.LayoutResult;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.renderer.AnnotationRenderer;
import songscribe.ui.renderer.ArticulationRenderer;
import songscribe.ui.renderer.AttributionRenderer;
import songscribe.ui.renderer.BeamGroupRenderer;
import songscribe.ui.renderer.BeatChangeRenderer;
import songscribe.ui.renderer.DynamicMarkingRenderer;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.EndingRenderer;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.HairpinRenderer;
import songscribe.ui.renderer.KeySignatureRenderer;
import songscribe.ui.renderer.LineInvariants;
import songscribe.ui.renderer.LyricConnectorRenderer;
import songscribe.ui.renderer.LyricTextRenderer;
import songscribe.ui.renderer.StaffElementRenderer;
import songscribe.ui.renderer.RenderingUtils;
import songscribe.ui.renderer.SlideRenderer;
import songscribe.ui.renderer.SongTempoMarkRenderer;
import songscribe.ui.renderer.StaffHeaderRenderer;
import songscribe.ui.renderer.TempoChangeRenderer;
import songscribe.ui.renderer.TieRenderer;
import songscribe.ui.renderer.TrillRenderer;
import songscribe.ui.renderer.TupletRenderer;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

/**
 * Handles all rendering for a single staff line.
 * <p>
 * Reads state from the owning LineComponent but does not mutate it
 * (except for positioning the insertion note preview before drawing).
 */
class LineRenderer {

    private static final float SELECTION_RECT_STROKE_WIDTH_PX = 2.0f;

    private static final double SELECTION_RECT_ARC_PX = 2.0;

    /** So a zero-width band still renders. */
    private static final double MIN_BAND_WIDTH_PX = 1.0;

    private final LineComponent lc;

    LineRenderer(LineComponent lineComponent) {
        lc = lineComponent;
    }

    void render(Graphics2D g2) {
        var invariants = buildInvariants();

        // The line-level frame carries the grace-note insert preview shift (rightward
        // displacement of subsequent elements) when this is the active grace line; the
        // per-element frames built below inherit that shift.
        var lineFrame = lc.gracePreviewLineFrame();

        // Render in proper order (back to front)
        drawStaffLines(g2, invariants);
        renderLineBeginning(g2, invariants, lineFrame);
        renderElements(g2, invariants, lineFrame);
        renderSlides(g2, invariants, lineFrame);
        renderBeams(g2, invariants, lineFrame);
        renderTies(g2, invariants, lineFrame);
        renderTuplets(g2, invariants, lineFrame);
        renderKeyChanges(g2, invariants);
        renderDynamics(g2, invariants);
        renderEndings(g2, invariants);
        renderAttachments(g2, invariants, lineFrame);
        renderAttribution(g2, invariants);
    }

    LineInvariants buildInvariants() {
        var song = lc.getSong();
        var line = lc.getLine();
        var lineIndex = lc.getLineIndex();

        var layoutResult = lc.getLayoutResult();

        if (layoutResult == null) {
            throw RuntimeError.exit("LineRenderer.render called before layout was performed");
        }

        var score = lc.getScoreView();
        var activeEditor = score.getActiveLyricEditor();

        // The edited element and script are only meaningful as a pair, so derive both from one
        // check rather than letting two conditions drift apart.
        StaffElement editedElement = null;
        Song.Script editedScript = null;

        if (activeEditor != null) {
            editedElement = activeEditor.getActiveElement();
            editedScript = activeEditor.getActiveScript();
        }

        return LineInvariants.builder(song, score, score.getViewScale())
            .setCurrentLine(line)
            .setLineIndex(lineIndex)
            .setMiddleLineYSs(lc.getMiddleLineYSs())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(score.getLyricRenderMetrics())
            .setActivelyEditedElement(editedElement)
            .setActivelyEditedScript(editedScript)
            .setSelectionProvider(lc.getSelectionProvider())
            .setPlaying(lc.getPlaying())
            .setLintReport(song.getLinter().getReport())
            .build();
    }

    private void drawStaffLines(Graphics2D g2, LineInvariants invariants) {
        var selectionProvider = invariants.getSelectionProvider();
        var lineIndex = invariants.getLineIndex();
        var staffSelected = selectionProvider != null
            && selectionProvider.isSelected(new HitTarget.StaffLine(), lineIndex);
        var layoutResult = invariants.getLayoutResult();

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            // Red says this line holds more than the staff can show, so the tail of its content is
            // clipped. Selection still wins: it is the transient state the user is
            // acting on, and the red returns the moment the line is deselected.
            if (staffSelected) {
                g2.setColor(ScoreView.getSelectionColor());
            } else if (layoutResult.overflowsStaffWidth()) {
                g2.setColor(FlatLafProps.getColor(FlatLafKey.SCORE_OVERFLOW_COLOR));
            } else {
                g2.setColor(RenderingUtils.STAFF_LINE_COLOR);
            }

            var lineWidth = invariants.getSong().getLineWidthSs().value();
            var middleLineYSs = invariants.getMiddleLineYSs();
            var staffLineThicknessSs = EngravingConstants.STAFF_LINE_THICKNESS_SS;

            // Staff has 5 lines, middle line (B) is at index 2.
            // Lines are at: middleLineYSs - 2, middleLineYSs - 1, middleLineYSs,
            //               middleLineYSs + 1, middleLineYSs + 2
            for (var i = -2; i <= 2; i++) {
                var centerY = middleLineYSs + i;
                GraphicUtils.drawRoundedLine(g2, 0, centerY, lineWidth, centerY, staffLineThicknessSs);
            }
        }
    }

    private void renderLineBeginning(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        StaffHeaderRenderer.getInstance().render(
            invariants, frame, invariants.getLayoutResult().staffHeader(), g2);
        renderSongTempoMark(g2, invariants, frame);
    }

    private void renderSongTempoMark(
        Graphics2D g2, LineInvariants invariants, ElementFrame frame) {

        SongTempoMarkRenderer.getInstance()
            .render(invariants, frame, invariants.getSong().getTempoMarkElement(), g2);
    }

    private void renderAttribution(Graphics2D g2, LineInvariants invariants) {
        AttributionRenderer.getInstance().render(invariants, g2);
    }

    private void renderElements(Graphics2D g2, LineInvariants invariants, ElementFrame lineFrame) {
        var noteRenderer = StaffElementRenderer.getInstance();
        var line = invariants.requireCurrentLine();
        var layoutResult = invariants.getLayoutResult();

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            for (var i = 0; i < line.elementCount(); i++) {
                var element = line.getElement(i);

                var color = getElementColor(i, invariants);
                g2.setColor(color);

                var overrideXSs = computeOverrideXSs(lineFrame, i, element, layoutResult);

                var frame = lineFrame.withElement(i, overrideXSs);
                noteRenderer.render(invariants, frame, element, g2);
            }
        }
    }

    /**
     * @return {@link Double#NaN} if no override applies (no shift, element before boundary,
     *     or element is a final double barline which is never shifted)
     */
    private static double computeOverrideXSs(
        ElementFrame lineFrame,
        int elementIndex,
        StaffElement element,
        LayoutResult layoutResult
    ) {
        var previewShift = lineFrame.previewShift();
        return previewShift != null
            && previewShift.displaces(elementIndex)
            && element.getType() != ElementType.FINAL_DOUBLE_BARLINE
            ? layoutResult.getElementXSs(element) + previewShift.shiftSs()
            : Double.NaN;
    }

    private Color getElementColor(int elementIndex, LineInvariants invariants) {
        var color = invariants.getElementColor(elementIndex);

        if (!LineInvariants.isDefaultColor(color)) {
            return color;
        }

        var line = lc.getLine();

        if (line != null && lc.isPendingCancelElement(line.getElement(elementIndex))) {
            return Color.RED;
        }

        return Color.BLACK;
    }

    private void renderSlides(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        SlideRenderer.getInstance().renderSlidesFromLine(g2, invariants, frame);
        renderPendingConnectGlissando(g2, invariants);
    }

    /**
     * The pending connection is render-only state: mutating the grace note's slide during the
     * drag would leak an untracked change into undo's before-state clones.
     */
    private void renderPendingConnectGlissando(Graphics2D g2, LineInvariants invariants) {
        // Common case: no grace-mode drag is active anywhere — skip the per-element scan.
        if (!GraceModeManager.hasPendingConnect()) {
            return;
        }

        var line = invariants.requireCurrentLine();

        for (var i = 0; i < line.effectiveElementCount(); i++) {
            if (!lc.isPendingConnectElement(line.getElement(i))) {
                continue;
            }

            try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
                g2.setColor(ScoreView.getPreviewElementColor());
                SlideRenderer.getInstance().renderPreviewGlissando(g2, i, invariants);
            }

            return;
        }
    }

    private void renderBeams(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var beamRenderer = BeamGroupRenderer.getInstance();
        var line = invariants.requireCurrentLine();

        for (var beam : line.spanRegistry().findSpans(Beam.class)) {
            var anchorIdx = beam.requireAnchorElementIndex();
            renderWithPreviewShiftIfNeeded(g2, frame, anchorIdx,
                () -> beamRenderer.renderBeams(g2, invariants, beam));
        }
    }

    private void renderTies(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var tieRenderer = TieRenderer.getInstance();

        for (var tieLayout : invariants.getLayoutResult().tieLayouts()) {
            renderWithPreviewShiftIfNeeded(g2, frame, previewShiftStartOf(tieLayout.placement()),
                () -> tieRenderer.renderTie(g2, tieLayout, invariants));
        }
    }

    private static int previewShiftStartOf(Line.TiePlacement placement) {
        return placement.range().begin();
    }

    private static void renderWithPreviewShiftIfNeeded(
        Graphics2D g2,
        ElementFrame frame,
        int spanStart,
        Runnable render
    ) {
        var previewShift = frame.previewShift();

        if (previewShift != null && previewShift.displaces(spanStart)) {
            try (var _ = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
                g2.translate(previewShift.shiftSs(), 0);
                render.run();
            }
        } else {
            render.run();
        }
    }

    private void renderTuplets(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        TupletRenderer.getInstance().renderTupletsFromLine(g2, invariants, frame);
    }

    /**
     * The cautionary comes from {@link CautionaryKeySignature#of(Line)}, the same answer
     * {@code HorizontalSpacingCalculator.trailingReservationSs} reserves room against, so what
     * is drawn and what layout kept clear come from one source.
     */
    private void renderKeyChanges(Graphics2D g2, LineInvariants invariants) {
        var cautionary = CautionaryKeySignature.of(invariants.requireCurrentLine());

        if (cautionary == null) {
            return;
        }

        KeySignatureRenderer.getInstance().renderCautionary(g2, cautionary, invariants);
    }

    private void renderDynamics(Graphics2D g2, LineInvariants invariants) {
        HairpinRenderer.getInstance().renderHairpinsFromLine(g2, invariants);
    }

    private void renderEndings(Graphics2D g2, LineInvariants invariants) {
        EndingRenderer.getInstance().renderEndings(g2, invariants);
    }

    private void renderAttachments(Graphics2D g2, LineInvariants invariants, ElementFrame lineFrame) {
        var articulationRenderer = ArticulationRenderer.getInstance();
        var fermataRenderer = FermataRenderer.getInstance();
        var dynamicMarkingRenderer = DynamicMarkingRenderer.getInstance();
        var tempoRenderer = TempoChangeRenderer.getInstance();
        var beatChangeRenderer = BeatChangeRenderer.getInstance();
        var annotationRenderer = AnnotationRenderer.getInstance();
        var lyricTextRenderer = LyricTextRenderer.getInstance();
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var layoutResult = invariants.getLayoutResult();
        var decorationLayouts = layoutResult.decorationLayouts();

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
            var previewShift = lineFrame.previewShift();
            var attachmentShiftActive = false;

            for (var i = 0; i < line.elementCount(); i++) {
                if (!attachmentShiftActive && previewShift != null && previewShift.displaces(i)) {
                    g2.translate(previewShift.shiftSs(), 0);
                    attachmentShiftActive = true;
                }

                // Attachments use the cumulative g2.translate above for the preview shift,
                // not a per-element X override, so the override is always absent here.
                var frame = lineFrame.withElement(i, Double.NaN);
                var element = line.getElement(i);

                // Tier 1: Articulations (near-note)
                if (element instanceof NoteElement note && !note.getArticulations().isEmpty()) {
                    articulationRenderer.render(invariants, frame, note, g2);
                }

                // Tier 2: Fermata (note decoration)
                if (decorationLayouts.findAttachmentDecorationLayout(
                        element, FermataAttachment.class) != null) {
                    fermataRenderer.render(invariants, frame, element, g2);
                }

                // Tier 3: Dynamic markings (below staff)
                if (decorationLayouts.findAttachmentDecorationLayout(
                        element, DynamicAttachment.class) != null) {
                    dynamicMarkingRenderer.render(invariants, frame, element, g2);
                }

                // Tier 4: Tempo (system)
                if (decorationLayouts.findAttachmentDecorationLayout(
                        element, TempoChangeAttachment.class) != null) {
                    tempoRenderer.render(invariants, frame, element, g2);
                }

                // Tier 4: Beat change (system)
                if (decorationLayouts.findAttachmentDecorationLayout(
                        element, BeatChangeAttachment.class) != null) {
                    beatChangeRenderer.render(invariants, frame, element, g2);
                }

                // Tier 4: Annotation (system)
                if (decorationLayouts.findAttachmentDecorationLayout(
                        element, AnnotationAttachment.class) != null) {
                    annotationRenderer.render(invariants, frame, element, g2);
                }

                // Tier 5: Lyric syllable text, or a missing-lyric mark (below staff)
                lyricTextRenderer.render(invariants, frame, element, g2);
            }
        }

        // Tier 2: Trills (rendered separately as they may span multiple notes)
        TrillRenderer.getInstance().renderTrillsFromLine(g2, invariants, lineFrame);

        // Tier 5: Lyric span connectors (hyphens, extenders) — line-level
        LyricConnectorRenderer.getInstance().render(g2, invariants, lineFrame);
    }

    void renderSelectionBand(Graphics2D g2) {
        var band = lc.getSelectionBand();

        if (band == null) {
            return;
        }

        // This rectangle is a pixel-space overlay drawn outside the Ss transform (see
        // LineComponent.render), so the border must be scaled by the zoom factor explicitly
        // rather than relying on the transform to do it, unlike Ss-space engraved lines.
        var viewScale = lc.getViewScale();
        var factor = viewScale.factor();
        var arcPx = SELECTION_RECT_ARC_PX * factor;
        var strokeWidthPx = SELECTION_RECT_STROKE_WIDTH_PX * factor;
        var halfStrokeWidthPx = strokeWidthPx / 2;

        // The stroke is centered on the rectangle's path, so it extends halfStrokeWidthPx
        // beyond each edge. Swing clips painting at the line's own bounds, so a drag that
        // reaches the left or right of the line would otherwise have its
        // border cut off there. Keep the drawn path inset horizontally by that half-width so
        // the full stroke always lands inside the line.
        var minX = halfStrokeWidthPx;
        var maxX = lc.getWidth() - 1 - halfStrokeWidthPx;

        // Sub-pixel on purpose: the shape drawn is a RoundRectangle2D.Double, and rounding the
        // edges would make the band's two axes disagree about where they sit.
        var left = Math.max(viewScale.toViewPx(band.leftSs()).value(), minX);
        var right = Math.min(viewScale.toViewPx(band.rightSs()).value(), maxX);
        var width = Math.max(MIN_BAND_WIDTH_PX, right - left);

        // The band's vertical extent is the staff's own, not the drag's: it straddles the top
        // and bottom staff lines however the mouse moves. Unlike the horizontal edges, those
        // are well inside the component, so no inset is needed to keep the stroke from clipping.
        var top = viewScale.toViewPx(lc.getStaffTopYSs()).value();
        var bottom = viewScale.toViewPx(lc.getStaffBottomYSs()).value();

        var roundRect = new RoundRectangle2D.Double(
                left, top, width, bottom - top, arcPx, arcPx);

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.STROKE, GraphicsState.Property.COLOR)) {
            g2.setStroke(new BasicStroke((float) strokeWidthPx));
            g2.setColor(ScoreView.getSelectionColor());
            g2.draw(roundRect);
        }
    }
}
