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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;
import songscribe.dom.BeamLevel;
import songscribe.dom.Direction;
import songscribe.dom.NoteElement;
import songscribe.engraving.BeamMetrics;
import songscribe.engraving.StemMetrics;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.CLIP;
import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.STROKE;

public final class BeamGroupRenderer {

    private static final double CLIP_SLOP_SS = 0.25;
    private static final double BEAM_STUB_SS = 1.0;

    /** The fewest unhighlighted notes that keep a beam in place once its highlighted notes go. */
    private static final int MIN_REMAINING_BEAMED_NOTES = 2;

    private static final int BEAM_SHAPE_CORNERS = 4;

    // The round pen that rounds a drawn beam's corners (see drawBeam).  Immutable
    // and built from constants, so it is shared rather than rebuilt per repaint.
    private static final BasicStroke BLOT_STROKE = new BasicStroke(
        (float) BeamMetrics.BEAM_BLOT_DIAMETER_SS,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    );

    private static final BeamGroupRenderer INSTANCE = new BeamGroupRenderer();

    private BeamGroupRenderer() {
    }

    public static BeamGroupRenderer getInstance() {
        return INSTANCE;
    }

    public void renderBeams(Graphics2D g2, LineInvariants invariants, Beam beam) {
        var highlightColor = getBeamHighlightColor(invariants, beam);
        var layoutResult = invariants.getLayoutResult();
        var beamLayout = layoutResult.getBeamLayout(beam);
        var members = beam.members();
        var group = new BeamDrawing(
            g2,
            layoutResult,
            invariants.getMiddleLineYSs(),
            members,
            beamLayout != null ? beamLayout.direction() : beam.direction(),
            beamLayout != null ? beamLayout.thickeningSs() : 0.0,
            highlightColor != null ? highlightColor : RenderingUtils.ELEMENT_COLOR);

        group.drawRun(new Beam.MemberRun(0, members.size() - 1), BeamLevel.EIGHTH);
    }

    /**
     * Returns null if the beam should not be highlighted. A beam is highlighted when removing the
     * highlighted notes would leave fewer than {@value #MIN_REMAINING_BEAMED_NOTES} beamable
     * notes.
     */
    @Nullable
    Color getBeamHighlightColor(LineInvariants invariants, Beam beam) {
        var beamColor = invariants.colorFor(new HitTarget.Beam(beam));

        if (!LineInvariants.isDefaultColor(beamColor)) {
            return beamColor;
        }

        var selectionProvider = invariants.getSelectionProvider();
        var line = invariants.requireCurrentLine();
        var lineIndex = invariants.getLineIndex();
        var anySelected = false;
        var anyHovered = false;
        var remainingBeamableNotes = 0;

        for (var i = beam.requireAnchorElementIndex(); i <= beam.requireEndElementIndex(); i++) {
            var element = line.getElement(i);
            var isSelected = selectionProvider != null
                && selectionProvider.isSelected(new HitTarget.Element(element), lineIndex);

            if (isSelected) {
                anySelected = true;
            } else if (invariants.isElementHovered(i)) {
                anyHovered = true;
            } else if (element.getType().isBeamable()) {
                remainingBeamableNotes++;
            }
        }

        if (remainingBeamableNotes >= MIN_REMAINING_BEAMED_NOTES) {
            return null;
        }

        if (anySelected) {
            return invariants.getSelectionColor();
        }

        if (anyHovered) {
            return LineInvariants.REPLACED_ELEMENT_COLOR;
        }

        return null;
    }

    /**
     * @param layout    null if unavailable
     * @param element   fallback for the staff-position estimate when layout is null
     */
    static double stemTipYSsOffset(
        LayoutResult.@Nullable StemLayout layout,
        Direction direction,
        NoteElement element
    ) {
        if (layout != null) {
            // topYSs = smaller Y (higher screen) = stem tip for stem-up
            // bottomYSs = larger Y (lower screen) = stem tip for stem-down
            return direction.isUp() ? layout.topYSs() : layout.bottomYSs();
        }

        var elementYSs = element.getStaffPosition().toSs();
        return direction.isUp()
            ? elementYSs - StemMetrics.STEM_LENGTH_SS
            : elementYSs + StemMetrics.STEM_LENGTH_SS;
    }

    /**
     * @param members     the beam's {@link Beam#members}, which every {@link Beam.MemberRun}
     *                      here indexes
     * @param thickeningSs  the beam's raster thickening, 0 when the beam has no layout
     */
    private record BeamDrawing(
        Graphics2D g2,
        LayoutResult layoutResult,
        double middleLineYSs,
        List<Beam.Member> members,
        Direction direction,
        double thickeningSs,
        Color color
    ) {

        void drawRun(Beam.MemberRun run, BeamLevel level) {
            if (run.isSingle()) {
                drawStub(members.get(run.first()), level);
            } else {
                drawBeam(members.get(run.first()).note(), members.get(run.last()).note(), BeamType.FULL, level);
            }

            var innerLevel = level.next();

            if (innerLevel == null) {
                return;
            }

            var position = run.first();

            while (position <= run.last()) {
                var innerRun = members.get(position).runAt(innerLevel);

                if (innerRun == null) {
                    position++;
                } else {
                    drawRun(innerRun, innerLevel);
                    position = innerRun.last() + 1;
                }
            }
        }

        /**
         * The stub takes the group's slope.
         */
        private void drawStub(Beam.Member member, BeamLevel level) {
            switch (member.stub()) {
                case LEFT -> drawBeam(members.getFirst().note(), member.note(), BeamType.ATTACH_RIGHT, level);
                case RIGHT -> drawBeam(member.note(), members.getLast().note(), BeamType.ATTACH_LEFT, level);
                case NONE -> throw new IllegalStateException("a member alone in a run always has a stub");
            }
        }

        private void drawBeam(NoteElement beginNote, NoteElement endNote, BeamType type, BeamLevel level) {
            var isUpper = direction.isUp();

            var effectiveBeamDepthSs = BeamMetrics.BEAM_THICKNESS_SS + thickeningSs;
            var beamDepthSs = isUpper ? effectiveBeamDepthSs : -effectiveBeamDepthSs;
            // The primary beam sits on the stem tip; each level further in steps one translation
            // toward the noteheads.
            var translationsFromTip = level.number() - BeamLevel.EIGHTH.number();
            var innerBeamOffsetSs =
                BeamMetrics.beamTranslationSs(thickeningSs) * translationsFromTip * (isUpper ? 1 : -1);

            var firstStemLayout = layoutResult.getStemLayout(beginNote);
            var firstNoteXSs = layoutResult.getElementXSs(beginNote);
            var firstStemCenterXSs = firstNoteXSs
                + RenderingUtils.stemCenterXOffsetSs(beginNote.stemmedNotehead(), direction);
            var firstXSs = firstStemCenterXSs - StemMetrics.halfWidthSs();
            var firstTipYSs = stemTipYSsOffset(firstStemLayout, direction, beginNote);
            var firstOuterYSs = middleLineYSs + firstTipYSs + innerBeamOffsetSs;
            var firstInnerYSs = firstOuterYSs + beamDepthSs;

            var lastStemLayout = layoutResult.getStemLayout(endNote);
            var lastNoteXSs = layoutResult.getElementXSs(endNote);
            var lastStemCenterXSs = lastNoteXSs
                + RenderingUtils.stemCenterXOffsetSs(endNote.stemmedNotehead(), direction);
            var lastXSs = lastStemCenterXSs + StemMetrics.halfWidthSs();
            var lastTipYSs = stemTipYSsOffset(lastStemLayout, direction, endNote);
            var lastOuterYSs = middleLineYSs + lastTipYSs + innerBeamOffsetSs;
            var lastInnerYSs = lastOuterYSs + beamDepthSs;

            // LilyPond's Lookup::beam pulls every corner half a blot diameter toward
            // the beam's interior, then strokes the result with a round pen of one
            // full blot diameter. Fill plus stroke restores the original extent, so
            // the beam measures BEAM_THICKNESS_SS and only its corners round off.
            // Insetting without stroking would draw a beam one blot too thin.
            var blotRadiusSs = BeamMetrics.BEAM_BLOT_DIAMETER_SS / 2.0;
            var towardInnerSs = isUpper ? blotRadiusSs : -blotRadiusSs;

            var beamShape = new Path2D.Double(Path2D.WIND_NON_ZERO, BEAM_SHAPE_CORNERS);
            beamShape.moveTo(firstXSs + blotRadiusSs, firstOuterYSs + towardInnerSs);
            beamShape.lineTo(lastXSs - blotRadiusSs, lastOuterYSs + towardInnerSs);
            beamShape.lineTo(lastXSs - blotRadiusSs, lastInnerYSs - towardInnerSs);
            beamShape.lineTo(firstXSs + blotRadiusSs, firstInnerYSs - towardInnerSs);
            beamShape.closePath();

            try (var _ = GraphicsState.save(g2, CLIP, COLOR, STROKE)) {
                if (type != BeamType.FULL) {
                    var clipSs = beamShape.getBounds2D();
                    var clipLeftXSs = (type == BeamType.ATTACH_LEFT)
                        ? firstXSs - CLIP_SLOP_SS
                        : lastXSs - BEAM_STUB_SS;
                    clipSs.setRect(
                        clipLeftXSs,
                        clipSs.getMinY() - CLIP_SLOP_SS,
                        BEAM_STUB_SS + CLIP_SLOP_SS,
                        clipSs.getHeight() + CLIP_SLOP_SS * 2);
                    g2.setClip(clipSs);
                }

                g2.setColor(color);
                g2.setStroke(BLOT_STROKE);
                g2.fill(beamShape);
                g2.draw(beamShape);
            }
        }
    }

    private enum BeamType {
        FULL,
        ATTACH_LEFT,
        ATTACH_RIGHT
    }
}
