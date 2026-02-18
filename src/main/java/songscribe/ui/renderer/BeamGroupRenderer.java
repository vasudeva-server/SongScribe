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

import java.awt.*;
import java.awt.geom.*;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.NoteType;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.BeamGroup;
import songscribe.ui.layout.LayoutStylesheet;

import static songscribe.ui.renderer.GraphicsState.Property.*;

/**
 * Renders beam bars connecting beamed notes.
 * <p>
 * Beams are drawn as filled parallelograms connecting the stems of beamed notes.
 * Multiple beam levels (8th, 16th, 32nd) are stacked vertically.
 */
public class BeamGroupRenderer extends BaseElementRenderer<BeamGroup> {

    // ==========================================================================
    // Constants from Renderer
    // ==========================================================================

    private static final float NOTE_FONT_SIZE = BaseElementRenderer.NOTE_FONT_SIZE;

    private static final EngravingDefaults ENGRAVING_DEFAULTS =
        SMuFLMetadata.getInstance().getEngravingDefaults();

    // Beam strokes
    private static final BasicStroke BEAM_STROKE = new BasicStroke(
        (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.beamThickness()),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );
    private static final BasicStroke STEM_STROKE = new BasicStroke(
        (float) StaffSpaces.toPixels(ENGRAVING_DEFAULTS.stemThickness()),
        BasicStroke.CAP_BUTT,
        BasicStroke.JOIN_MITER
    );

    // Inner beam dimensions
    private static final double INNER_BEAM_LENGTH = 11d;
    private static final double INNER_BEAM_OFFSET =
        StaffSpaces.toPixels(ENGRAVING_DEFAULTS.beamThickness() + ENGRAVING_DEFAULTS.beamSpacing());

    // Note types for beam levels (32nd, 16th, 8th)
    private static final NoteType[] BEAM_LEVELS = new NoteType[]{
        NoteType.DEMI_SEMIQUAVER,
        NoteType.SEMIQUAVER,
        NoteType.QUAVER,
    };

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
        @NotNull BeamGroup element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var notes = element.getBeamedNotes();

        if (notes.size() < 2) {
            return;
        }

        var line = ctx.getCurrentLine();

        if (line == null) {
            return;
        }

        // Find the indices of the first and last notes
        int beginIndex = line.getNoteIndex(notes.get(0));
        int endIndex = line.getNoteIndex(notes.get(notes.size() - 1));

        if (beginIndex < 0 || endIndex < 0) {
            return;
        }

        // Determine beam level based on shortest note value
        int level = getBeamLevel(line, beginIndex, endIndex);

        // Draw beams
        drawBeams(g2, level, line, ctx, beginIndex, endIndex);
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
        int level = getBeamLevel(line, beginIndex, endIndex);
        drawBeams(g2, level, line, ctx, beginIndex, endIndex);
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
                    maxLevel = Math.max(maxLevel, BEAM_LEVELS.length - 1 - j);
                    break;
                }
            }
        }

        return maxLevel;
    }

    private void drawBeams(
        @NotNull Graphics2D g2,
        int level,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        int beginIndex,
        int endIndex
    ) {
        var outerNotes = new Point(beginIndex, endIndex);
        doDrawBeams(g2, level, line, ctx, outerNotes,
            beginIndex, endIndex, beginIndex, endIndex, false, 0);
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
        int recursionLevel
    ) {
        if (level == -1) {
            return;
        }

        var beginNote = line.getNote(beginIndex);
        var isUpper = beginNote.isUpper();
        var leftOriented = false;

        // Half beam (single note at this level)
        if (beginIndex == endIndex) {
            if (beginNote.getNoteType().isGraceNote()) {
                return;
            }

            leftOriented = (prevBeginIndex == prevEndIndex)
                ? isPrevLeftOriented
                : ((beginIndex == prevBeginIndex) == beginNote.isInvertFractionBeamOrientation());

            int begin, end;

            if (leftOriented) {
                begin = outerNotes.x;
                end = endIndex;
            } else {
                begin = beginIndex;
                end = outerNotes.y;
            }

            var type = leftOriented ? BeamType.ATTACH_RIGHT : BeamType.ATTACH_LEFT;
            drawBeam(g2, line, ctx, begin, end, isUpper, type, recursionLevel);
        }
        // Full beam
        else {
            drawBeam(g2, line, ctx, beginIndex, endIndex, isUpper, BeamType.FULL, recursionLevel);
        }

        // Sub-beams for higher levels
        var beamLevel = level - 1;
        var startSubBeam = -1;

        for (var i = beginIndex; i <= endIndex + 1; i++) {
            if (i <= endIndex && isNoteTypeInLevel(line, i, beamLevel)) {
                if (startSubBeam == -1) {
                    startSubBeam = i;
                }
            } else if (startSubBeam != -1) {
                doDrawBeams(g2, beamLevel, line, ctx, outerNotes,
                    startSubBeam, i - 1, beginIndex, endIndex,
                    leftOriented, recursionLevel + 1);
                startSubBeam = -1;
            }
        }
    }

    private boolean isNoteTypeInLevel(@NotNull Line line, int noteIndex, int level) {
        var type = line.getNote(noteIndex).getNoteType();

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

        while (begin > 0 && line.getNote(begin).getNoteType().isGraceNote()) {
            begin--;
        }

        while (end < line.noteCount() && line.getNote(end).getNoteType().isGraceNote()) {
            end++;
        }

        return begin >= 0 && isNoteTypeInLevel(line, begin, level) &&
            end < line.noteCount() && isNoteTypeInLevel(line, end, level);
    }

    private void drawBeam(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull ElementRenderContext ctx,
        int beginIndex,
        int endIndex,
        boolean isUpper,
        @NotNull BeamType type,
        int recursionLevel
    ) {
        var beginNote = line.getNote(beginIndex);
        var endNote = line.getNote(endIndex);
        var firstStem = beginNote.properties.stem;
        var lastStem = endNote.properties.stem;

        int middleLineY = ctx.getMiddleLineY();

        // Reduce height by half stem width for rendering accuracy
        var halfStemWidth = STEM_STROKE.getLineWidth() / 2;
        var halfBeamWidth = (BEAM_STROKE.getLineWidth() / 2) - halfStemWidth;

        // Inner beam offset: moves beam toward note head for secondary/tertiary beams
        var innerBeamOffset = INNER_BEAM_OFFSET * recursionLevel * (isUpper ? 1 : -1);

        // Calculate first note beam position using actual stem end (y2)
        var noteX = beginNote.getXPos();
        var firstX = firstStem.x1 + noteX;
        var noteY = middleLineY + (beginNote.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET);

        // Use stem.y2 directly - it already incorporates lengthening from NoteRenderer
        // Adjust so beam edge meets stem end: upper beams have bottom edge at stem,
        // lower beams have top edge at stem
        var firstY = noteY + firstStem.y2 + innerBeamOffset;

        if (isUpper) {
            firstY -= halfBeamWidth;  // Beam bottom at stem end
        } else {
            firstY += halfBeamWidth;  // Beam top at stem end
        }

        // Build beam path
        var beam = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
        beam.moveTo(firstX, firstY + halfBeamWidth);  // Bottom left
        beam.lineTo(firstX, firstY - halfBeamWidth);  // Top left

        // Calculate last note beam position
        noteX = endNote.getXPos();
        var lastX = lastStem.x1 + noteX;
        noteY = middleLineY + (endNote.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET);
        var lastY = noteY + lastStem.y2 + innerBeamOffset;

        if (isUpper) {
            lastY -= halfBeamWidth;
        } else {
            lastY += halfBeamWidth;
        }

        beam.lineTo(lastX, lastY - halfBeamWidth);    // Top right
        beam.lineTo(lastX, lastY + halfBeamWidth);    // Bottom right
        beam.closePath();

        Shape oldClip = null;
        Rectangle2D clip = null;

        if (type != BeamType.FULL) {
            // Clip partial beams
            clip = beam.getBounds2D();
            double clipSlop = 2d;
            double x1;

            if (type == BeamType.ATTACH_LEFT) {
                x1 = firstX - clipSlop;
            } else {
                x1 = lastX - INNER_BEAM_LENGTH;
            }

            clip.setRect(
                x1,
                clip.getMinY() - clipSlop,
                INNER_BEAM_LENGTH + clipSlop,
                clip.getHeight() + clipSlop * 2
            );
            oldClip = g2.getClip();
            g2.setClip(clip);
        }

        try (var ignored = GraphicsState.save(g2, COLOR, STROKE)) {
            g2.setColor(Color.BLACK);
            g2.setStroke(STEM_STROKE);
            g2.draw(beam);
            g2.fill(beam);
        }

        if (clip != null) {
            g2.setClip(oldClip);
        }
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
