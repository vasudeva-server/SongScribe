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

import module java.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.Engraving;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LayoutResult;

/**
 * Renders beam bars connecting beamed notes.
 * <p>
 * Beams are drawn as filled parallelograms connecting the stems of beamed notes.
 * Multiple beam levels (8th, 16th, 32nd) are stacked vertically.
 */
public class BeamGroupRenderer extends BaseElementRenderer<LineElement> {

    // ==========================================================================
    // Constants from Renderer
    // ==========================================================================

    private static final double CLIP_SLOP_SS = 0.25;   // extra clipping margin (~2 px)
    private static final double BEAM_STUB_SS = 1.0;  // 8px

    // Note types for beam levels (32nd, 16th, 8th)
    private static final ElementType[] BEAM_LEVELS = new ElementType[]{
        ElementType.DEMI_SEMIQUAVER,
        ElementType.SEMIQUAVER,
        ElementType.QUAVER,
    };

    private static final Logger LOG = LoggerFactory.getLogger(BeamGroupRenderer.class);

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
    protected void renderElement(
        LineElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
    }

    /**
     * Renders beams for notes in a beaming span.
     * <p>
     * Entry point for rendering beams directly from a Line's beaming spans.
     */
    public void renderBeams(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx,
        int beginIndex,
        int endIndex
    ) {
        int level = getBeamLevel(line, beginIndex, endIndex);
        var highlightColor = getBeamHighlightColor(ctx, beginIndex, endIndex);
        drawBeams(g2, level, line, ctx, beginIndex, endIndex, highlightColor != null,
            highlightColor != null ? highlightColor : ctx.getSelectionColor());
    }

    /**
     * Returns the color to use for beam highlighting, or null if the beam should not be highlighted.
     * A beam is highlighted when removing the highlighted note(s) would eliminate the beam
     * (fewer than 2 beamable notes remain). Selected notes use the selection color;
     * hovered notes use the insertion note color.
     */
    @Nullable
    private Color getBeamHighlightColor(
        ElementRenderContext ctx,
        int beginIndex,
        int endIndex
    ) {
        if (!ctx.isEditMode()) {
            return null;
        }

        var selectionProvider = ctx.getSelectionProvider();
        var line = ctx.getCurrentLine();

        if (line == null) {
            return null;
        }

        var lineIndex = ctx.getLineIndex();
        var matched = PreviewElementManager.getXMatchedElement();
        var anySelected = false;
        var anyHovered = false;
        var remainingBeamableNotes = 0;

        for (var i = beginIndex; i <= endIndex; i++) {
            var isSelected = selectionProvider != null && selectionProvider.isElementSelected(i, lineIndex);

            if (isSelected) {
                anySelected = true;
            } else if (matched != null && matched.matches(lineIndex, i)) {
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
            return ctx.getSelectionColor();
        }

        if (anyHovered) {
            return Score.getPreviewElementColor();
        }

        return null;
    }

    /**
     * Determines the beam level based on the shortest note in the range.
     */
    private int getBeamLevel(Line line, int beginIndex, int endIndex) {
        int maxLevel = 0;

        for (int i = beginIndex; i <= endIndex; i++) {
            var noteType = line.getElement(i).getType();

            for (int j = 0; j < BEAM_LEVELS.length; j++) {
                if (noteType == BEAM_LEVELS[j]) {
                    var level = BEAM_LEVELS.length - 1 - j;
                    maxLevel = Math.max(maxLevel, level);
                    break;
                }
            }
        }

        return maxLevel;
    }

    private void drawBeams(
        Graphics2D g2,
        int level,
        Line line,
        ElementRenderContext ctx,
        int beginIndex,
        int endIndex,
        boolean selected,
        Color selectionColor
    ) {
        var outerNotes = new Point(beginIndex, endIndex);
        var layoutResult = ctx.getLayoutResult();
        var beamSpan = (layoutResult != null) ? line.getBeamings().findSpan(beginIndex) : null;
        var beamLayout = (layoutResult != null && beamSpan != null)
            ? layoutResult.getBeamLayout(beamSpan) : null;
        doDrawBeams(g2, level, line, ctx, outerNotes,
            beginIndex, endIndex, beginIndex, endIndex, false, 0, selected, beamLayout, selectionColor);
    }

    private void doDrawBeams(
        Graphics2D g2,
        int level,
        Line line,
        ElementRenderContext ctx,
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
        var isUpper = beginNote.isUpper();
        var leftOriented = false;

        // Half beam (single note at this level)
        if (beginIndex == endIndex) {
            if (beginNote.getType().isGraceNote()) {
                return;
            }

            var layoutResult = ctx.getLayoutResult();
            var stubStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(beginNote) : null;
            leftOriented = (stubStemLayout != null)
                ? !stubStemLayout.stubRight()
                : (prevBeginIndex == prevEndIndex) ? isPrevLeftOriented : (beginIndex != prevBeginIndex);

            int begin, end;

            if (leftOriented) {
                begin = outerNotes.x;
                end = endIndex;
            } else {
                begin = beginIndex;
                end = outerNotes.y;
            }

            var type = leftOriented ? BeamType.ATTACH_RIGHT : BeamType.ATTACH_LEFT;
            drawBeam(g2, line, ctx, begin, end, isUpper, type, recursionLevel, selected, beamLayout, selectionColor);
        }
        // Full beam
        else {
            drawBeam(g2, line, ctx, beginIndex, endIndex, isUpper, BeamType.FULL, recursionLevel, selected, beamLayout, selectionColor);
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
                    doDrawBeams(g2, beamLevel, line, ctx, outerNotes,
                        startSubBeam, i - 1, beginIndex, endIndex,
                        leftOriented, recursionLevel + 1, selected, beamLayout, selectionColor);
                    startSubBeam = -1;
                }
            }
        }
    }

    private boolean isNoteTypeInLevel(Line line, int noteIndex, int level) {
        var type = line.getElement(noteIndex).getType();

        if (!type.isGraceNote()) {
            for (int i = 0; i < BEAM_LEVELS.length; i++) {
                if (BEAM_LEVELS[i] == type) {
                    return i <= (BEAM_LEVELS.length - 1 - level);
                }
            }
            return false;
        }

        // Grace notes: check surrounding notes
        int begin = noteIndex - 1;
        int end = noteIndex + 1;

        while (begin > 0 && line.getElement(begin).getType().isGraceNote()) {
            begin--;
        }

        while (end < line.elementCount() && line.getElement(end).getType().isGraceNote()) {
            end++;
        }

        return begin >= 0 && isNoteTypeInLevel(line, begin, level) &&
            end < line.elementCount() && isNoteTypeInLevel(line, end, level);
    }

    private void drawBeam(
        Graphics2D g2,
        Line line,
        ElementRenderContext ctx,
        int beginIndex,
        int endIndex,
        boolean isUpper,
        BeamType type,
        int recursionLevel,
        boolean selected,
        LayoutResult.@Nullable BeamLayout beamLayout,
        Color selectionColor
    ) {
        var beginNote = line.getElement(beginIndex);
        var endNote = line.getElement(endIndex);
        var layoutResult = ctx.getLayoutResult();
        double middleLineYSs = ctx.getMiddleLineYSs();
        double halfStemWidthSs = LayoutStylesheet.STEM_WIDTH_SS / 2.0;

        // --- Thickening (from BeamLayout, zero if unavailable) ---
        double thickeningSs = (beamLayout != null) ? beamLayout.thickeningSs() : 0.0;
        double effectiveBeamDepthSs = Engraving.BEAM_THICKNESS_SS + thickeningSs;
        double beamDepthSs = isUpper ? effectiveBeamDepthSs : -effectiveBeamDepthSs;
        double innerBeamOffsetSs = (effectiveBeamDepthSs + Engraving.BEAM_SPACING_SS) * recursionLevel * (isUpper ? 1 : -1);

        // --- First note stem geometry ---
        var firstStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(beginNote) : null;
        double firstNoteXSs = (layoutResult != null)
            ? layoutResult.getElementXSs(beginNote) : beginNote.getXPosSs();
        double firstStemCenterXSs = firstNoteXSs
            + stemCenterXOffsetSs(beginNote.getType(), isUpper);
        double firstX = firstStemCenterXSs - halfStemWidthSs;
        double firstTipYSs = stemTipYSsOffset(firstStemLayout, isUpper, beginNote);
        double firstOuterY = middleLineYSs + firstTipYSs + innerBeamOffsetSs;
        double firstInnerY = firstOuterY + beamDepthSs;

        // --- Last note stem geometry ---
        var lastStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(endNote) : null;
        double lastNoteXSs = (layoutResult != null)
            ? layoutResult.getElementXSs(endNote) : endNote.getXPosSs();
        double lastStemCenterXSs = lastNoteXSs
            + stemCenterXOffsetSs(endNote.getType(), isUpper);
        double lastX = lastStemCenterXSs + halfStemWidthSs;
        double lastTipYSs = stemTipYSsOffset(lastStemLayout, isUpper, endNote);
        double lastOuterY = middleLineYSs + lastTipYSs + innerBeamOffsetSs;
        double lastInnerY = lastOuterY + beamDepthSs;

        // --- Build and draw parallelogram ---
        var beam = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
        beam.moveTo(firstX, firstOuterY);
        beam.lineTo(lastX, lastOuterY);
        beam.lineTo(lastX, lastInnerY);
        beam.lineTo(firstX, firstInnerY);
        beam.closePath();

        Shape oldClip = null;

        if (type != BeamType.FULL) {
            var clip = beam.getBounds2D();
            double x1 = (type == BeamType.ATTACH_LEFT)
                ? firstX - CLIP_SLOP_SS
                : lastX - BEAM_STUB_SS;
            clip.setRect(
                x1,
                clip.getMinY() - CLIP_SLOP_SS,
                BEAM_STUB_SS + CLIP_SLOP_SS,
                clip.getHeight() + CLIP_SLOP_SS * 2);
            oldClip = g2.getClip();
            g2.setClip(clip);
        }

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(selected ? selectionColor : ELEMENT_COLOR);
            g2.fill(beam);
        }

        if (oldClip != null) {
            g2.setClip(oldClip);
        }
    }

    /**
     * Returns the Y offset from {@code middleLineYSs} to the beam-connection end of the stem
     * (the stem tip), in staff-space units.
     *
     * @param layout  StemLayout from LayoutResult, or null if unavailable
     * @param isUpper true = stem goes up (beam above notes)
     * @param element fallback element for staff-position estimate when layout is null
     */
    private static double stemTipYSsOffset(
        LayoutResult.@Nullable StemLayout layout,
        boolean isUpper,
        StaffElement element
    ) {
        if (layout != null) {
            // topYSs = smaller Y (higher screen) = stem tip for stem-up
            // bottomYSs = larger Y (lower screen) = stem tip for stem-down
            return isUpper ? layout.topYSs() : layout.bottomYSs();
        }

        // Fallback: approximate from staff position + standard stem length
        double elementYSs = element.getStaffPosition() * 0.5;
        return isUpper
            ? elementYSs - LayoutStylesheet.STEM_LENGTH_SS
            : elementYSs + LayoutStylesheet.STEM_LENGTH_SS;
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
