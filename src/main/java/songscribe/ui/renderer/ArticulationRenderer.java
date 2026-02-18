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
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;

import org.jetbrains.annotations.NotNull;

import static songscribe.ui.renderer.GraphicsState.Property.*;

import songscribe.music.ArticulationType;
import songscribe.music.Note;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.LayoutResult;
import songscribe.util.GraphicUtils;

/**
 * Renders articulation markings on notes (staccato, accent).
 * <p>
 * Articulations modify how a note is played:
 * <ul>
 *   <li>Staccato - short, detached (dot above/below note)</li>
 *   <li>Accent - emphasized attack (> symbol)</li>
 * </ul>
 */
public class ArticulationRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Crotchet width for positioning
    private static final double CROTCHET_WIDTH = BaseElementRenderer.NOTE_FONT_SIZE / 3.6056337d;

    // Accent drawing parameters (DPI-aware stroke width).
    private static final float ACCENT_STROKE_WIDTH = GraphicUtils.getDpiAwareStrokeWidth(1.0f);
    private static final Stroke ACCENT_STROKE = new BasicStroke(ACCENT_STROKE_WIDTH);
    private static final int ACCENT_LINE_HALF_HEIGHT = 3;

    // Accent bounding rect (origin-centered, used for positioning).
    // When this becomes a font glyph, replace with the glyph's bounding rect.
    private static final Rectangle ACCENT_BOUNDS = computeAccentBounds();

    // Staccato dot shape
    private static final Ellipse2D.Double STACCATO_ELLIPSE = new Ellipse2D.Double(0, 0, 4, 4);

    // Staccato visual half-height (4px dot / 2)
    private static final int STACCATO_HALF_HEIGHT = 2;

    // Singleton instance
    private static final ArticulationRenderer INSTANCE = new ArticulationRenderer();

    /**
     * Computes the bounding rect of the accent shape drawn at origin.
     * Two lines from (0, -HALF_HEIGHT) to (width, 0) and (0, +HALF_HEIGHT) to (width, 0),
     * expanded by the stroke width.
     */
    private static Rectangle computeAccentBounds() {
        int strokeExpansion = (int) Math.ceil(ACCENT_STROKE_WIDTH / 2.0);
        int width = (int) CROTCHET_WIDTH + 2 + strokeExpansion * 2;
        int halfHeight = ACCENT_LINE_HALF_HEIGHT + strokeExpansion;

        return new Rectangle(-strokeExpansion, -halfHeight, width, halfHeight * 2);
    }

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private ArticulationRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull ArticulationRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull Note element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        if (element.getArticulations().isEmpty()) {
            return;
        }

        var layoutResult = ctx.getLayoutResult();

        if (layoutResult != null) {
            renderFromLayout(element, g2, ctx, layoutResult);
        } else {
            renderFallback(element, g2, ctx);
        }
    }

    /**
     * Renders articulations using pre-computed positions from the layout engine.
     */
    private void renderFromLayout(
        @NotNull Note note,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx,
        @NotNull LayoutResult layoutResult
    ) {
        for (var articulation : note.getArticulations()) {
            var bounds = layoutResult.getElementBounds(articulation);

            if (bounds == null) {
                continue;
            }

            int componentY = layoutYToComponentY(bounds, ctx);

            if (articulation.isStaccato()) {
                drawStaccatoFromLayout(note, g2, componentY);
            } else if (articulation.isAccent()) {
                // Center of 8px content box
                int accentCenterY = componentY + 4;
                drawAccent(note, g2, accentCenterY);
            }
        }
    }

    /**
     * Renders a staccato dot at a layout-computed Y position.
     * The Y is the top of the 8px content box; the 4px dot is centered within it.
     */
    private void drawStaccatoFromLayout(
        @NotNull Note note,
        @NotNull Graphics2D g2,
        int contentTopY
    ) {
        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            double halfNoteWidth = getHalfNoteWidthForTie(note);
            int xPos = note.getXPos();

            // Center the 4px dot in the 8px content box: top + 2
            g2.translate((xPos + halfNoteWidth) - 2, contentTopY + 2);
            g2.fill(STACCATO_ELLIPSE);
        }
    }

    /**
     * Fallback rendering for when no layout result is available
     * (e.g. insertion note preview).
     */
    private void renderFallback(
        @NotNull Note element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var hasAccent = element.hasArticulation(ArticulationType.ACCENT);
        var hasStaccato = element.hasArticulation(ArticulationType.STACCATO);

        if (!hasAccent && !hasStaccato) {
            return;
        }

        int middleLineY = ctx.getMiddleLineY();
        int staccatoY = hasStaccato ? calculateStaccatoY(element, middleLineY) : 0;

        if (hasAccent) {
            int accentY = calculateAccentY(element, middleLineY, staccatoY, hasStaccato);
            drawAccent(element, g2, accentY);
        }

        if (hasStaccato) {
            renderStaccato(element, g2, middleLineY);
        }
    }

    /**
     * Calculates the Y center for an accent marking.
     * <p>
     * For notes within the staff, the accent anchors to the staff edge.
     * For ledger-line notes, it anchors to the note head.
     * When a staccato is present, the accent stacks beyond it with a 1px gap.
     * <p>
     * Pass {@code middleLineY=0} for layout-space coordinates,
     * or the actual middleLineY for component-space coordinates.
     */
    public static int calculateAccentY(
        @NotNull Note note, int middleLineY, int staccatoY, boolean hasStaccato
    ) {
        int dir = note.isUpper() ? 1 : -1;
        int yPos = note.getYPos();
        int accentHalfHeight = ACCENT_BOUNDS.height / 2;
        int margin = LayoutStylesheet.px(0.75);

        // If staccato is present, stack accent beyond it with 1px gap
        if (hasStaccato) {
            return staccatoY + dir * (STACCATO_HALF_HEIGHT + 1 + accentHalfHeight);
        }

        // Accent alone.
        // Offset = accent visual half-height + margin so the painted edge clears the reference.
        if (Math.abs(yPos) < 4) {
            // Within staff (not on edge lines) — anchor to staff edge
            int staffEdgeY = middleLineY + (int) (dir * 4 * LayoutStylesheet.NOTE_Y_OFFSET);

            return staffEdgeY + dir * (accentHalfHeight + margin);
        } else {
            // On staff edge or beyond (ledger lines) — anchor to note head
            int noteHeadY = middleLineY + (int) (yPos * LayoutStylesheet.NOTE_Y_OFFSET);
            int noteHeadRadius = (int) LayoutStylesheet.NOTE_Y_OFFSET;

            return noteHeadY + dir * (noteHeadRadius + margin + accentHalfHeight);
        }
    }

    /**
     * Draws an accent marking (> shape) at the given Y position.
     */
    private void drawAccent(@NotNull Note note, @NotNull Graphics2D g2, int accentY) {
        int xPos = note.getXPos();
        int x2 = xPos + (int) CROTCHET_WIDTH + 2;

        try (var ignored = GraphicsState.save(g2, STROKE)) {
            g2.setStroke(ACCENT_STROKE);
            g2.drawLine(xPos, accentY - ACCENT_LINE_HALF_HEIGHT, x2, accentY);
            g2.drawLine(xPos, accentY + ACCENT_LINE_HALF_HEIGHT, x2, accentY);
        }
    }

    /**
     * Calculates the Y center of a staccato dot for a note.
     * <p>
     * Normal stems: notes near the middle get a fixed staff space;
     * notes approaching the edge use the standard margin from the staff edge;
     * ledger-line notes use the standard margin from the note head.
     * <p>
     * Inverted stems: the staccato is always placed in a fixed staff space,
     * positioned toward the center of the staff.
     * <p>
     * Pass {@code middleLineY=0} for layout-space coordinates,
     * or the actual middleLineY for component-space coordinates.
     */
    public static int calculateStaccatoY(@NotNull Note note, int middleLineY) {
        boolean isUpper = note.isUpper();
        int dir = isUpper ? 1 : -1;
        int yPos = note.getYPos();
        boolean normalStem = (yPos > 0) == isUpper;

        if (normalStem) {
            int margin = LayoutStylesheet.px(0.75);

            return switch (Math.abs(yPos)) {
                // B4/C5 or A4: fixed space near opposite staff edge
                case 0, 1 -> middleLineY + (int) (dir * 3 * LayoutStylesheet.NOTE_Y_OFFSET);

                // D5/E5 or G4/F4: standard margin from staff edge
                case 2, 3 -> {
                    int staffEdgeY = middleLineY + (int) (dir * 4 * LayoutStylesheet.NOTE_Y_OFFSET);
                    yield staffEdgeY + dir * (STACCATO_HALF_HEIGHT + margin);
                }

                // F5+ or E4+: standard margin from note head
                default -> {
                    int noteHeadY = middleLineY + (int) (yPos * LayoutStylesheet.NOTE_Y_OFFSET);
                    int noteHeadRadius = (int) LayoutStylesheet.NOTE_Y_OFFSET;
                    yield noteHeadY + dir * (noteHeadRadius + margin + STACCATO_HALF_HEIGHT);
                }
            };
        }

        // Inverted stem: staccato always in a fixed staff space, toward center
        int targetYPos = switch (yPos) {
            case 0 -> 3;           // B4 inverted → F4 space
            case -1, -2 -> 1;     // C5/D5 inverted → A4 space
            case -3, -4 -> -1;    // E5/F5 inverted → C5 space
            case 1, 2 -> -1;      // A4/G4 inverted → C5 space
            case 3, 4 -> 1;       // F4/E4 inverted → A4 space
            default -> yPos < 0 ? -3 : 3;  // beyond staff → E5 or F4 space
        };

        return middleLineY + (int) (targetYPos * LayoutStylesheet.NOTE_Y_OFFSET);
    }

    /**
     * Renders a staccato dot.
     */
    private void renderStaccato(
        @NotNull Note note,
        @NotNull Graphics2D g2,
        int middleLineY
    ) {
        int durY = calculateStaccatoY(note, middleLineY);

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            double halfNoteWidth = getHalfNoteWidthForTie(note);
            int xPos = note.getXPos();

            g2.translate((xPos + halfNoteWidth) - 2, durY - 2);
            g2.fill(STACCATO_ELLIPSE);
        }
    }

    /**
     * Returns half the width of a note for positioning.
     */
    private double getHalfNoteWidthForTie(@NotNull Note note) {
        var noteType = note.getNoteType();

        if (noteType == songscribe.music.NoteType.SEMIBREVE ||
            noteType == songscribe.music.NoteType.MINIM) {
            return note.getRealUpNoteRect().width / 2.0;
        }

        return CROTCHET_WIDTH / 2.0;
    }
}
