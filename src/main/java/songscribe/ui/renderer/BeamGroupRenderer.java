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

import java.awt.*;
import java.awt.geom.*;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.InsertionNoteManager;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout2.LayoutConstants;
import songscribe.ui.layout2.LayoutResult;
import songscribe.util.GraphicUtils;

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

    // Beam geometry constants (staff-space units; scale transform handles pixel conversion)
    private static final double BEAM_DEPTH_SS  = 0.4;    // beam thickness
    private static final double BEAM_SHIFT_SS  = 0.625;  // gap between stacked beam levels
    private static final double BEAM_STUB_SS   = 1.0;    // partial beam stub length
    private static final double CLIP_SLOP_SS   = 0.25;   // extra clipping margin (~2 px)

    // Note types for beam levels (32nd, 16th, 8th)
    private static final NoteType[] BEAM_LEVELS = new NoteType[]{
        NoteType.DEMI_SEMIQUAVER,
        NoteType.SEMIQUAVER,
        NoteType.QUAVER,
    };

    private static final Logger LOG = Logger.getLogger(BeamGroupRenderer.class.getName());

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
    public static @NotNull BeamGroupRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull LineElement element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
    }

    /**
     * Renders beams for notes in a beaming interval.
     * <p>
     * Entry point for rendering beams directly from a Line's beaming intervals.
     */
    public void renderBeams(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        int beginIndex,
        int endIndex
    ) {
        LOG.fine("[BeamRenderer] ============================================");
        LOG.fine("[BeamRenderer] renderBeams: beginIndex=" + beginIndex + " endIndex=" + endIndex);
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
        @NotNull ElementRenderContext ctx,
        int beginIndex,
        int endIndex
    ) {
        if (!ctx.isEditMode()) {
            return null;
        }

        var selectionProvider = ctx.getSelectionProvider();
        var line = ctx.getCurrentLine();
        var lineIndex = ctx.getLineIndex();
        var hoveredLineIndex = InsertionNoteManager.getHoveredNoteLineIndex();
        var hoveredNoteIndex = InsertionNoteManager.getHoveredNoteIndex();
        var anySelected = false;
        var anyHovered = false;
        var remainingBeamableNotes = 0;

        for (var i = beginIndex; i <= endIndex; i++) {
            var isSelected = selectionProvider != null && selectionProvider.isNoteSelected(i, lineIndex);
            var isHovered = hoveredLineIndex == lineIndex && i == hoveredNoteIndex;

            if (isSelected) {
                anySelected = true;
            } else if (isHovered) {
                anyHovered = true;
            } else if (line.getNote(i).getNoteType().isBeamable()) {
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
            return Score.INSERTION_NOTE_COLOR;
        }

        return null;
    }

    /**
     * Determines the beam level based on the shortest note in the range.
     */
    private int getBeamLevel(@NotNull Line line, int beginIndex, int endIndex) {
        int maxLevel = 0;

        for (int i = beginIndex; i <= endIndex; i++) {
            var noteType = line.getNote(i).getNoteType();

            for (int j = 0; j < BEAM_LEVELS.length; j++) {
                if (noteType == BEAM_LEVELS[j]) {
                    var level = BEAM_LEVELS.length - 1 - j;
                    LOG.fine("[BeamRenderer] getBeamLevel: note[" + i + "] type=" + noteType
                        + " beamLevelIndex=" + j + " level=" + level);
                    maxLevel = Math.max(maxLevel, level);
                    break;
                }
            }
        }

        LOG.fine("[BeamRenderer] getBeamLevel: maxLevel=" + maxLevel);
        return maxLevel;
    }

    private void drawBeams(
        @NotNull Graphics2D g2,
        int level,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        int beginIndex,
        int endIndex,
        boolean selected,
        @NotNull Color selectionColor
    ) {
        var outerNotes = new Point(beginIndex, endIndex);
        var layoutResult = ctx.getLayoutResult();
        var interval = (layoutResult != null) ? line.getBeamings().findInterval(beginIndex) : null;
        var beamLayout = (layoutResult != null && interval != null)
            ? layoutResult.getBeamLayout(interval) : null;
        doDrawBeams(g2, level, line, ctx, outerNotes,
            beginIndex, endIndex, beginIndex, endIndex, false, 0, selected, beamLayout, selectionColor);
    }

    private void doDrawBeams(
        @NotNull Graphics2D g2,
        int level,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        @NotNull Point outerNotes,
        int beginIndex,
        int endIndex,
        int prevBeginIndex,
        int prevEndIndex,
        boolean isPrevLeftOriented,
        int recursionLevel,
        boolean selected,
        @Nullable LayoutResult.BeamLayout beamLayout,
        @NotNull Color selectionColor
    ) {
        String indent = "  ".repeat(recursionLevel);
        LOG.fine("[BeamRenderer] " + indent + "doDrawBeams: level=" + level
            + " beginIndex=" + beginIndex + " endIndex=" + endIndex
            + " prevBegin=" + prevBeginIndex + " prevEnd=" + prevEndIndex
            + " isPrevLeftOriented=" + isPrevLeftOriented
            + " recursionLevel=" + recursionLevel
            + " outerNotes=(" + outerNotes.x + "," + outerNotes.y + ")");

        if (level == -1) {
            LOG.fine("[BeamRenderer] " + indent + "  -> level=-1, returning");
            return;
        }

        var beginNote = line.getNote(beginIndex);
        var isUpper = beginNote.isUpper();
        var leftOriented = false;

        // Half beam (single note at this level)
        if (beginIndex == endIndex) {
            if (beginNote.getNoteType().isGraceNote()) {
                LOG.fine("[BeamRenderer] " + indent + "  -> grace note, skipping half beam");
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
            LOG.fine("[BeamRenderer] " + indent + "  -> HALF beam: leftOriented=" + leftOriented
                + " type=" + type + " drawBeam(" + begin + "," + end + ") isUpper=" + isUpper);
            drawBeam(g2, line, ctx, begin, end, isUpper, type, recursionLevel, selected, beamLayout, selectionColor);
        }
        // Full beam
        else {
            LOG.fine("[BeamRenderer] " + indent + "  -> FULL beam: drawBeam(" + beginIndex + "," + endIndex + ") isUpper=" + isUpper);
            drawBeam(g2, line, ctx, beginIndex, endIndex, isUpper, BeamType.FULL, recursionLevel, selected, beamLayout, selectionColor);
        }

        // Sub-beams for inner levels.
        var beamLevel = recursionLevel + 1;
        var startSubBeam = -1;

        LOG.fine("[BeamRenderer] " + indent + "  scanning sub-beams at beamLevel=" + beamLevel);

        for (var i = beginIndex; i <= endIndex + 1; i++) {
            if (i <= endIndex && isNoteTypeInLevel(line, i, beamLevel)) {
                LOG.fine("[BeamRenderer] " + indent + "    note[" + i + "] IS in level " + beamLevel
                    + " (type=" + line.getNote(i).getNoteType() + ")");
                if (startSubBeam == -1) {
                    startSubBeam = i;
                }
            } else {
                if (i <= endIndex) {
                    LOG.fine("[BeamRenderer] " + indent + "    note[" + i + "] NOT in level " + beamLevel
                        + " (type=" + line.getNote(i).getNoteType() + ")");
                }
                if (startSubBeam != -1) {
                    LOG.fine("[BeamRenderer] " + indent + "    -> sub-beam range: " + startSubBeam + " to " + (i - 1));
                    doDrawBeams(g2, beamLevel, line, ctx, outerNotes,
                        startSubBeam, i - 1, beginIndex, endIndex,
                        leftOriented, recursionLevel + 1, selected, beamLayout, selectionColor);
                    startSubBeam = -1;
                }
            }
        }
    }

    private boolean isNoteTypeInLevel(@NotNull Line line, int noteIndex, int level) {
        var type = line.getNote(noteIndex).getNoteType();

        if (!type.isGraceNote()) {
            for (int i = 0; i < BEAM_LEVELS.length; i++) {
                if (BEAM_LEVELS[i] == type) {
                    var result = i <= (BEAM_LEVELS.length - 1 - level);
                    LOG.fine("[BeamRenderer]       isNoteTypeInLevel: note[" + noteIndex + "] type=" + type
                        + " beamLevelIndex=" + i + " threshold=" + (BEAM_LEVELS.length - 1 - level)
                        + " -> " + result);
                    return result;
                }
            }
            LOG.fine("[BeamRenderer]       isNoteTypeInLevel: note[" + noteIndex + "] type=" + type + " NOT beamable -> false");
            return false;
        }

        // Grace notes: check surrounding notes
        int begin = noteIndex - 1;
        int end = noteIndex + 1;

        while (begin > 0 && line.getNote(begin).getNoteType().isGraceNote()) {
            begin--;
        }

        while (end < line.noteCount() && line.getNote(end).getNoteType().isGraceNote()) {
            end++;
        }

        var result = begin >= 0 && isNoteTypeInLevel(line, begin, level) &&
            end < line.noteCount() && isNoteTypeInLevel(line, end, level);
        LOG.fine("[BeamRenderer]       isNoteTypeInLevel: grace note[" + noteIndex + "] -> " + result);
        return result;
    }

    private void drawBeam(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        int beginIndex,
        int endIndex,
        boolean isUpper,
        @NotNull BeamType type,
        int recursionLevel,
        boolean selected,
        @Nullable LayoutResult.BeamLayout beamLayout,
        @NotNull Color selectionColor
    ) {
        var beginNote = line.getNote(beginIndex);
        var endNote   = line.getNote(endIndex);
        var layoutResult = ctx.getLayoutResult();
        double middleLineYSs = ctx.getMiddleLineYSs();
        double halfStemWidthSs = LayoutConstants.STEM_WIDTH_SS / 2.0;

        LOG.fine("[BeamRenderer]   drawBeam: type=" + type + " beginIndex=" + beginIndex + " endIndex=" + endIndex
            + " isUpper=" + isUpper + " recursionLevel=" + recursionLevel);

        // --- Thickening (from BeamLayout, zero if unavailable) ---
        double thickeningSs = (beamLayout != null) ? beamLayout.thickeningSs() : 0.0;
        double effectiveBeamDepthSs = BEAM_DEPTH_SS + thickeningSs;
        double beamDepthSs          = isUpper ? effectiveBeamDepthSs : -effectiveBeamDepthSs;
        double effectiveBeamShiftSs = BEAM_SHIFT_SS + thickeningSs;
        double innerBeamOffsetSs    = effectiveBeamShiftSs * recursionLevel * (isUpper ? 1 : -1);

        // --- First note stem geometry ---
        var firstStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(beginNote) : null;
        double firstNoteXSs = (layoutResult != null)
            ? layoutResult.getNoteXSs(beginNote) : beginNote.getXPosSs();
        double firstStemCenterXSs = firstNoteXSs
            + stemCenterXOffsetSs(beginNote.getNoteType(), isUpper);
        double firstX      = GraphicUtils.snapXToDevicePixel(g2, firstStemCenterXSs - halfStemWidthSs);
        double firstTipYSs = stemTipYSsOffset(firstStemLayout, isUpper, beginNote);
        double firstOuterY = GraphicUtils.snapYToDevicePixel(
            g2, middleLineYSs + firstTipYSs + innerBeamOffsetSs);
        double firstInnerY = GraphicUtils.snapYToDevicePixel(g2, firstOuterY + beamDepthSs);

        // --- Last note stem geometry ---
        var lastStemLayout = (layoutResult != null) ? layoutResult.getStemLayout(endNote) : null;
        double lastNoteXSs = (layoutResult != null)
            ? layoutResult.getNoteXSs(endNote) : endNote.getXPosSs();
        double lastStemCenterXSs = lastNoteXSs
            + stemCenterXOffsetSs(endNote.getNoteType(), isUpper);
        double lastX      = GraphicUtils.snapXToDevicePixel(g2, lastStemCenterXSs + halfStemWidthSs);
        double lastTipYSs = stemTipYSsOffset(lastStemLayout, isUpper, endNote);
        double lastOuterY = GraphicUtils.snapYToDevicePixel(
            g2, middleLineYSs + lastTipYSs + innerBeamOffsetSs);
        double lastInnerY = GraphicUtils.snapYToDevicePixel(g2, lastOuterY + beamDepthSs);

        LOG.fine("[BeamRenderer]     firstX=" + firstX + " firstOuterY=" + firstOuterY
            + " lastX=" + lastX + " lastOuterY=" + lastOuterY);

        // --- Build and draw parallelogram ---
        var beam = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
        beam.moveTo(firstX, firstOuterY);
        beam.lineTo(lastX,  lastOuterY);
        beam.lineTo(lastX,  lastInnerY);
        beam.lineTo(firstX, firstInnerY);
        beam.closePath();

        Shape oldClip = null;

        if (type != BeamType.FULL) {
            var clip = beam.getBounds2D();
            double x1 = (type == BeamType.ATTACH_LEFT)
                ? firstX - CLIP_SLOP_SS
                : lastX  - BEAM_STUB_SS;
            clip.setRect(
                x1,
                clip.getMinY() - CLIP_SLOP_SS,
                BEAM_STUB_SS + CLIP_SLOP_SS,
                clip.getHeight() + CLIP_SLOP_SS * 2);
            oldClip = g2.getClip();
            g2.setClip(clip);
        }

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(selected ? selectionColor : NOTE_COLOR);
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
     * @param note    fallback note for staff-position estimate when layout is null
     */
    private static double stemTipYSsOffset(
        @Nullable LayoutResult.StemLayout layout,
        boolean isUpper,
        @NotNull Note note
    ) {
        if (layout != null) {
            // topYSs = smaller Y (higher screen) = stem tip for stem-up
            // bottomYSs = larger Y (lower screen) = stem tip for stem-down
            return isUpper ? layout.topYSs() : layout.bottomYSs();
        }

        // Fallback: approximate from staff position + standard stem length
        double noteYSs = note.getStaffPosition() * 0.5;
        return isUpper
            ? noteYSs - LayoutConstants.STEM_LENGTH_SS
            : noteYSs + LayoutConstants.STEM_LENGTH_SS;
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
