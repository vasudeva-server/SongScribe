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
import java.awt.Point;
import java.awt.geom.Path2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.engraving.BeamMetrics;
import songscribe.engraving.StaffPosition;
import songscribe.engraving.StemMetrics;
import songscribe.hit.HitTarget;
import songscribe.layout.BeamMath;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.CLIP;
import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.STROKE;

/**
 * Renders beam bars connecting beamed notes.
 * <p>
 * Beams are drawn as filled parallelograms connecting the stems of beamed notes.
 * Multiple beam levels (8th, 16th, 32nd) are stacked vertically.
 */
public final class BeamGroupRenderer implements ElementRenderer<LineElement> {

    // ==========================================================================
    // Constants from Renderer
    // ==========================================================================

    private static final double CLIP_SLOP_SS = 0.25;   // extra clipping margin (~2 px)
    private static final double BEAM_STUB_SS = 1.0;  // 8px

    // The round pen that rounds a drawn beam's corners (see drawBeam).  Immutable
    // and built from constants, so it is shared rather than rebuilt per repaint.
    private static final BasicStroke BLOT_STROKE = new BasicStroke(
        (float) BeamMetrics.BEAM_BLOT_DIAMETER_SS,
        BasicStroke.CAP_ROUND,
        BasicStroke.JOIN_ROUND
    );

    // Singleton instance
    private static final BeamGroupRenderer INSTANCE = new BeamGroupRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private BeamGroupRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static BeamGroupRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        LineElement element,
        Graphics2D g2
    ) {
    }

    /**
     * Renders beams for notes in a beaming span.
     * <p>
     * Entry point for rendering beams directly from a Line's beaming spans.
     */
    public void renderBeams(
        Graphics2D g2,
        LineInvariants invariants,
        ElementFrame frame,
        int beginIndex,
        int endIndex
    ) {
        var line = invariants.requireCurrentLine();
        var level = getBeamLevel(line, beginIndex, endIndex);
        var highlightColor = getBeamHighlightColor(invariants, beginIndex, endIndex);
        drawBeams(g2, level, line, invariants, frame, beginIndex, endIndex, highlightColor != null,
            highlightColor != null ? highlightColor : invariants.getSelectionColor());
    }

    /**
     * Returns the color to use for beam highlighting, or null if the beam should not be highlighted.
     * <p>
     * A directly selected beam always highlights. Failing that, the note-driven rule applies:
     * the beam is highlighted when removing the highlighted note(s) would eliminate it
     * (fewer than 2 beamable notes remain). Selected notes use the selection color;
     * hovered notes use the replaced-element color (matching the hovered note itself).
     */
    @Nullable
    Color getBeamHighlightColor(
        LineInvariants invariants,
        int beginIndex,
        int endIndex
    ) {
        var selectionProvider = invariants.getSelectionProvider();
        var line = invariants.requireCurrentLine();
        var lineIndex = invariants.getLineIndex();
        var beam = line.findBeamAt(beginIndex);

        if (beam != null) {
            var beamColor = invariants.colorFor(
                new HitTarget.Beam(beam), LineInvariants.NO_ELEMENT_INDEX);

            if (!LineInvariants.isDefaultColor(beamColor)) {
                return beamColor;
            }
        }

        var anySelected = false;
        var anyHovered = false;
        var remainingBeamableNotes = 0;

        for (var i = beginIndex; i <= endIndex; i++) {
            var isSelected = selectionProvider != null
                && selectionProvider.isSelected(new HitTarget.Element(line.getElement(i)), lineIndex);

            if (isSelected) {
                anySelected = true;
            } else if (invariants.isElementHovered(i)) {
                anyHovered = true;
            } else if (line.getElement(i).getType().isBeamable()) {
                remainingBeamableNotes++;
            }
        }

        if (remainingBeamableNotes >= 2) {
            return null;
        }

        // Selection takes priority over hover
        if (anySelected) {
            return invariants.getSelectionColor();
        }

        if (anyHovered) {
            return LineInvariants.REPLACED_ELEMENT_COLOR;
        }

        return null;
    }

    /**
     * Determines the beam level based on the shortest note in the range.
     * Delegates to {@link BeamMath#beamLevel}.
     */
    int getBeamLevel(Line line, int beginIndex, int endIndex) {
        return BeamMath.beamLevel(line, beginIndex, endIndex);
    }

    private void drawBeams(
        Graphics2D g2,
        int level,
        Line line,
        LineInvariants invariants,
        ElementFrame frame,
        int beginIndex,
        int endIndex,
        boolean selected,
        Color selectionColor
    ) {
        var outerNotes = new Point(beginIndex, endIndex);
        var beam = line.findBeamAt(beginIndex);
        var beamLayout = (beam != null)
            ? invariants.getLayoutResult().getBeamLayout(beam) : null;
        doDrawBeams(g2, level, line, invariants, frame, outerNotes,
            beginIndex, endIndex, beginIndex, endIndex, false, 0, selected, beamLayout, selectionColor);
    }

    private void doDrawBeams(
        Graphics2D g2,
        int level,
        Line line,
        LineInvariants invariants,
        ElementFrame frame,
        Point outerNotes,
        int beginIndex,
        int endIndex,
        int prevBeginIndex,
        int prevEndIndex,
        boolean isPrevLeftOriented,
        int recursionLevel,
        boolean selected,
        LayoutResult.@Nullable BeamLayout beamLayout,
        Color selectionColor
    ) {
        if (level == -1) {
            return;
        }

        var beginNote = line.getElement(beginIndex);
        var direction = beginNote.getDirection();
        var leftOriented = false;

        // Half beam (single note at this level)
        if (beginIndex == endIndex) {
            if (beginNote.getType().isGraceNote()) {
                return;
            }

            var stubStemLayout = invariants.getLayoutResult().getStemLayout(beginNote);

            if (stubStemLayout != null) {
                leftOriented = !stubStemLayout.stubRight();
            } else if (prevBeginIndex == prevEndIndex) {
                leftOriented = isPrevLeftOriented;
            } else {
                leftOriented = (beginIndex != prevBeginIndex);
            }

            int begin, end;

            if (leftOriented) {
                begin = outerNotes.x;
                end = endIndex;
            } else {
                begin = beginIndex;
                end = outerNotes.y;
            }

            var type = leftOriented ? BeamType.ATTACH_RIGHT : BeamType.ATTACH_LEFT;
            drawBeam(g2, line, invariants, begin, end, direction, type, recursionLevel, selected, beamLayout, selectionColor);
        }
        // Full beam
        else {
            drawBeam(g2, line, invariants, beginIndex, endIndex, direction, BeamType.FULL, recursionLevel, selected, beamLayout, selectionColor);
        }

        // Sub-beams for inner levels.
        var beamLevel = recursionLevel + 1;
        var startSubBeam = -1;

        for (var i = beginIndex; i <= endIndex + 1; i++) {
            if (i <= endIndex && isNoteTypeInLevel(line, i, beamLevel)) {
                if (startSubBeam == -1) {
                    startSubBeam = i;
                }
            } else {
                if (startSubBeam != -1) {
                    doDrawBeams(g2, beamLevel, line, invariants, frame, outerNotes,
                        startSubBeam, i - 1, beginIndex, endIndex,
                        leftOriented, recursionLevel + 1, selected, beamLayout, selectionColor);
                    startSubBeam = -1;
                }
            }
        }
    }

    boolean isNoteTypeInLevel(Line line, int noteIndex, int level) {
        return BeamMath.noteTypeInLevel(line, noteIndex, level);
    }

    private void drawBeam(
        Graphics2D g2,
        Line line,
        LineInvariants invariants,
        int beginIndex,
        int endIndex,
        StaffElement.Direction direction,
        BeamType type,
        int recursionLevel,
        boolean selected,
        LayoutResult.@Nullable BeamLayout beamLayout,
        Color selectionColor
    ) {
        var beginNote = line.getElement(beginIndex);
        var endNote = line.getElement(endIndex);
        var layoutResult = invariants.getLayoutResult();
        var middleLineYSs = invariants.getMiddleLineYSs();
        var isUpper = direction.isUp();

        // --- Thickening (from BeamLayout, zero if unavailable) ---
        var thickeningSs = (beamLayout != null) ? beamLayout.thickeningSs() : 0.0;
        var effectiveBeamDepthSs = BeamMetrics.BEAM_THICKNESS_SS + thickeningSs;
        var beamDepthSs = isUpper ? effectiveBeamDepthSs : -effectiveBeamDepthSs;
        var innerBeamOffsetSs =
            BeamMetrics.beamTranslationSs(thickeningSs) * recursionLevel * (isUpper ? 1 : -1);

        // --- First note stem geometry ---
        var firstStemLayout = layoutResult.getStemLayout(beginNote);
        var firstNoteXSs = layoutResult.getElementXSs(beginNote);
        var firstStemCenterXSs = firstNoteXSs
            + RenderingUtils.stemCenterXOffsetSs(beginNote.getType(), direction);
        var firstX = firstStemCenterXSs - StemMetrics.STEM_HALF_WIDTH_SS;
        var firstTipYSs = stemTipYSsOffset(firstStemLayout, direction, beginNote);
        var firstOuterY = middleLineYSs + firstTipYSs + innerBeamOffsetSs;
        var firstInnerY = firstOuterY + beamDepthSs;

        // --- Last note stem geometry ---
        var lastStemLayout = layoutResult.getStemLayout(endNote);
        var lastNoteXSs = layoutResult.getElementXSs(endNote);
        var lastStemCenterXSs = lastNoteXSs
            + RenderingUtils.stemCenterXOffsetSs(endNote.getType(), direction);
        var lastX = lastStemCenterXSs + StemMetrics.STEM_HALF_WIDTH_SS;
        var lastTipYSs = stemTipYSsOffset(lastStemLayout, direction, endNote);
        var lastOuterY = middleLineYSs + lastTipYSs + innerBeamOffsetSs;
        var lastInnerY = lastOuterY + beamDepthSs;

        // --- Build parallelogram, inset for LilyPond-style rounded corners ---
        // LilyPond's Lookup::beam pulls every corner half a blot diameter toward
        // the beam's interior, then strokes the result with a round pen of one
        // full blot diameter. Fill plus stroke restores the original extent, so
        // the beam measures BEAM_THICKNESS_SS as before and only its corners round
        // off. Insetting without stroking would draw a beam one blot too thin.
        var blotRadiusSs = BeamMetrics.BEAM_BLOT_DIAMETER_SS / 2.0;
        var towardInnerSs = isUpper ? blotRadiusSs : -blotRadiusSs;

        var beam = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
        beam.moveTo(firstX + blotRadiusSs, firstOuterY + towardInnerSs);
        beam.lineTo(lastX - blotRadiusSs, lastOuterY + towardInnerSs);
        beam.lineTo(lastX - blotRadiusSs, lastInnerY - towardInnerSs);
        beam.lineTo(firstX + blotRadiusSs, firstInnerY - towardInnerSs);
        beam.closePath();

        try (var _ = GraphicsState.save(g2, CLIP, COLOR, STROKE)) {
            if (type != BeamType.FULL) {
                var clip = beam.getBounds2D();
                var x1 = (type == BeamType.ATTACH_LEFT)
                    ? firstX - CLIP_SLOP_SS
                    : lastX - BEAM_STUB_SS;
                clip.setRect(
                    x1,
                    clip.getMinY() - CLIP_SLOP_SS,
                    BEAM_STUB_SS + CLIP_SLOP_SS,
                    clip.getHeight() + CLIP_SLOP_SS * 2);
                g2.setClip(clip);
            }

            g2.setColor(selected ? selectionColor : RenderingUtils.ELEMENT_COLOR);
            g2.setStroke(BLOT_STROKE);
            g2.fill(beam);
            g2.draw(beam);
        }
    }

    /**
     * Returns the Y offset from {@code middleLineYSs} to the beam-connection end of the stem
     * (the stem tip), in staff-space units.
     *
     * @param layout    StemLayout from LayoutResult, or null if unavailable
     * @param direction UP = stem goes up (beam above notes); DOWN = stem goes down
     * @param element   fallback element for staff-position estimate when layout is null
     */
    static double stemTipYSsOffset(
        LayoutResult.@Nullable StemLayout layout,
        StaffElement.Direction direction,
        StaffElement element
    ) {
        if (layout != null) {
            // topYSs = smaller Y (higher screen) = stem tip for stem-up
            // bottomYSs = larger Y (lower screen) = stem tip for stem-down
            return direction.isUp() ? layout.topYSs() : layout.bottomYSs();
        }

        // Fallback: approximate from staff position + standard stem length
        var elementYSs = StaffPosition.toSs(element.getStaffPosition());
        return direction.isUp()
            ? elementYSs - StemMetrics.STEM_LENGTH_SS
            : elementYSs + StemMetrics.STEM_LENGTH_SS;
    }

    /**
     * Beam type for partial beams.
     */
    private enum BeamType {
        FULL,           // Full beam connecting multiple notes
        ATTACH_LEFT,    // Partial beam attached to left note
        ATTACH_RIGHT    // Partial beam attached to right note
    }
}
