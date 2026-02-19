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
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.NoteType;
import songscribe.ui.component.Score;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.BeamGroup;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.LayoutResult;
import songscribe.util.GraphicUtils;

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

    // Beam thickness in pixels (from SMuFL engravingDefaults)
    private static final double BEAM_THICKNESS_PX =
        StaffSpaces.toPixels(ENGRAVING_DEFAULTS.beamThickness());

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
        @NotNull BeamGroup element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var notes = element.getBeamedNotes();

        if (notes.size() < 2) {
            LOG.fine("[BeamRenderer] renderElement: skipping, notes.size()=" + notes.size());
            return;
        }

        var line = ctx.getCurrentLine();

        if (line == null) {
            LOG.fine("[BeamRenderer] renderElement: skipping, line is null");
            return;
        }

        // Find the indices of the first and last notes
        int beginIndex = line.getNoteIndex(notes.get(0));
        int endIndex = line.getNoteIndex(notes.get(notes.size() - 1));

        if (beginIndex < 0 || endIndex < 0) {
            LOG.fine("[BeamRenderer] renderElement: skipping, beginIndex=" + beginIndex + " endIndex=" + endIndex);
            return;
        }

        // Determine beam level based on shortest note value
        int level = getBeamLevel(line, beginIndex, endIndex);

        LOG.fine("[BeamRenderer] ============================================");
        LOG.fine("[BeamRenderer] renderElement: beginIndex=" + beginIndex + " endIndex=" + endIndex + " level=" + level);
        for (int i = beginIndex; i <= endIndex; i++) {
            var note = line.getNote(i);
            LOG.fine("[BeamRenderer]   note[" + i + "]: type=" + note.getNoteType()
                + " yPos=" + note.getYPos() + " xPos=" + note.getXPos()
                + " upper=" + note.isUpper()
                + " lengthening=" + note.properties.lengthening
                + " stem=(x1=" + note.properties.stem.x1
                + " y1=" + note.properties.stem.y1
                + " x2=" + note.properties.stem.x2
                + " y2=" + note.properties.stem.y2 + ")");
        }

        // Draw beams
        boolean selected = shouldBeamAppearSelected(ctx, beginIndex, endIndex);
        drawBeams(g2, level, line, ctx, beginIndex, endIndex, selected);
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
        boolean selected = shouldBeamAppearSelected(ctx, beginIndex, endIndex);
        drawBeams(g2, level, line, ctx, beginIndex, endIndex, selected);
    }

    /**
     * Determines whether a beam should appear in the selection color.
     * A beam should appear selected when removing the selected notes
     * would eliminate the beam (fewer than 2 beamable notes remain).
     */
    private boolean shouldBeamAppearSelected(
        @NotNull ElementRenderContext ctx,
        int beginIndex,
        int endIndex
    ) {
        var selectionProvider = ctx.getSelectionProvider();

        if (selectionProvider == null || !ctx.isEditMode()) {
            return false;
        }

        var line = ctx.getCurrentLine();
        var lineIndex = ctx.getLineIndex();
        var anySelected = false;
        var remainingBeamableNotes = 0;

        for (var i = beginIndex; i <= endIndex; i++) {
            if (selectionProvider.isNoteSelected(i, lineIndex)) {
                anySelected = true;
            } else if (line.getNote(i).getNoteType().isBeamable()) {
                remainingBeamableNotes++;
            }
        }

        return anySelected && remainingBeamableNotes < 2;
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
        boolean selected
    ) {
        var outerNotes = new Point(beginIndex, endIndex);
        doDrawBeams(g2, level, line, ctx, outerNotes,
            beginIndex, endIndex, beginIndex, endIndex, false, 0, selected);
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
        boolean selected
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
            LOG.fine("[BeamRenderer] " + indent + "  -> HALF beam: leftOriented=" + leftOriented
                + " type=" + type + " drawBeam(" + begin + "," + end + ") isUpper=" + isUpper);
            drawBeam(g2, line, ctx, begin, end, isUpper, type, recursionLevel, selected);
        }
        // Full beam
        else {
            LOG.fine("[BeamRenderer] " + indent + "  -> FULL beam: drawBeam(" + beginIndex + "," + endIndex + ") isUpper=" + isUpper);
            drawBeam(g2, line, ctx, beginIndex, endIndex, isUpper, BeamType.FULL, recursionLevel, selected);
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
                        leftOriented, recursionLevel + 1, selected);
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
        boolean selected
    ) {
        var beginNote = line.getNote(beginIndex);
        var endNote = line.getNote(endIndex);
        var firstStem = beginNote.properties.stem;
        var lastStem = endNote.properties.stem;

        LOG.fine("[BeamRenderer]   drawBeam: type=" + type + " beginIndex=" + beginIndex + " endIndex=" + endIndex
            + " isUpper=" + isUpper + " recursionLevel=" + recursionLevel);
        LOG.fine("[BeamRenderer]     beginNote: type=" + beginNote.getNoteType()
            + " yPos=" + beginNote.getYPos() + " xPos=" + beginNote.getXPos()
            + " lengthening=" + beginNote.properties.lengthening
            + " stem=(x1=" + firstStem.x1 + " y1=" + firstStem.y1 + " x2=" + firstStem.x2 + " y2=" + firstStem.y2 + ")");
        LOG.fine("[BeamRenderer]     endNote: type=" + endNote.getNoteType()
            + " yPos=" + endNote.getYPos() + " xPos=" + endNote.getXPos()
            + " lengthening=" + endNote.properties.lengthening
            + " stem=(x1=" + lastStem.x1 + " y1=" + lastStem.y1 + " x2=" + lastStem.x2 + " y2=" + lastStem.y2 + ")");

        int middleLineY = ctx.getMiddleLineY();
        var halfStemWidth = NoteRenderer.STEM_WIDTH / 2.0;

        var thickening = beginNote.properties.beamThickening;
        var effectiveBeamThickness = BEAM_THICKNESS_PX + thickening;
        var beamThickness = isUpper ? effectiveBeamThickness : -effectiveBeamThickness;
        var effectiveInnerOffset = INNER_BEAM_OFFSET + thickening;
        var innerBeamOffset = effectiveInnerOffset * recursionLevel * (isUpper ? 1 : -1);

        LOG.fine("[BeamRenderer]     middleLineY=" + middleLineY
            + " halfStemWidth=" + halfStemWidth
            + " BEAM_THICKNESS_PX=" + BEAM_THICKNESS_PX
            + " beamThickness=" + beamThickness
            + " INNER_BEAM_OFFSET=" + INNER_BEAM_OFFSET
            + " innerBeamOffset=" + innerBeamOffset);

        var layoutResult = ctx.getLayoutResult();

        // First note: left edge of stem, stem tip Y
        var noteX = (layoutResult != null) ? layoutResult.getNoteX(beginNote) : beginNote.getXPos();
        var snappedNoteX = GraphicUtils.snapXToDevicePixel(g2, noteX);
        var firstX = snappedNoteX + firstStem.x1 - halfStemWidth;
        var noteY = middleLineY + (beginNote.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET);
        var firstOuterY = GraphicUtils.snapYToDevicePixel(g2, noteY + firstStem.y2 + innerBeamOffset);
        var firstInnerY = GraphicUtils.snapYToDevicePixel(g2, firstOuterY + beamThickness);

        LOG.fine("[BeamRenderer]     firstNote coords: noteX=" + noteX + " snappedNoteX=" + snappedNoteX
            + " firstX=" + firstX + " noteY=" + noteY
            + " stemTipY(noteY+stem.y2)=" + (noteY + firstStem.y2)
            + " firstOuterY=" + firstOuterY + " firstInnerY=" + firstInnerY);

        // Last note: right edge of stem, stem tip Y
        noteX = (layoutResult != null) ? layoutResult.getNoteX(endNote) : endNote.getXPos();
        snappedNoteX = GraphicUtils.snapXToDevicePixel(g2, noteX);
        var lastX = snappedNoteX + lastStem.x1 + halfStemWidth;
        noteY = middleLineY + (endNote.getYPos() * LayoutStylesheet.NOTE_Y_OFFSET);
        var lastOuterY = GraphicUtils.snapYToDevicePixel(g2, noteY + lastStem.y2 + innerBeamOffset);
        var lastInnerY = GraphicUtils.snapYToDevicePixel(g2, lastOuterY + beamThickness);

        LOG.fine("[BeamRenderer]     lastNote coords: noteX=" + noteX + " snappedNoteX=" + snappedNoteX
            + " lastX=" + lastX + " noteY=" + noteY
            + " stemTipY(noteY+stem.y2)=" + (noteY + lastStem.y2)
            + " lastOuterY=" + lastOuterY + " lastInnerY=" + lastInnerY);

        // Build beam parallelogram: outer edge at stem tips, inner edge toward noteheads
        var beam = new Path2D.Double(Path2D.WIND_NON_ZERO, 4);
        beam.moveTo(firstX, firstOuterY);
        beam.lineTo(lastX, lastOuterY);
        beam.lineTo(lastX, lastInnerY);
        beam.lineTo(firstX, firstInnerY);
        beam.closePath();

        LOG.fine("[BeamRenderer]     beam parallelogram: (" + firstX + "," + firstOuterY + ") -> ("
            + lastX + "," + lastOuterY + ") -> (" + lastX + "," + lastInnerY + ") -> (" + firstX + "," + firstInnerY + ")");

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

            LOG.fine("[BeamRenderer]     clip: x=" + clip.getX() + " y=" + clip.getY()
                + " w=" + clip.getWidth() + " h=" + clip.getHeight());

            oldClip = g2.getClip();
            g2.setClip(clip);
        }

        try (var ignored = GraphicsState.save(g2, COLOR)) {
            g2.setColor(selected ? Score.SELECTION_STROKE_COLOR : NOTE_COLOR);
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
