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
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

import org.jetbrains.annotations.NotNull;

import static songscribe.ui.renderer.GraphicsState.Property.*;

import songscribe.music.Note;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.LayoutStylesheet;

/**
 * Renders fermata symbols above or below notes.
 * <p>
 * A fermata indicates that a note should be held longer than its written duration.
 * The symbol consists of a dot under an arc (like an eyebrow over an eye).
 */
public class FermataRenderer extends BaseElementRenderer<Note> {

    // ==========================================================================
    // Constants
    // ==========================================================================

    // Fermata shape (from FughettaRenderer)
    private static final Shape FERMATA;

    static {
        // Build the fermata shape: arc with a dot underneath
        var arc = new Arc2D.Double(-115, -90, 230, 180, 0, 180, Arc2D.CHORD);
        var innerArc = new Arc2D.Double(-100, -75, 200, 160, 0, 180, Arc2D.CHORD);
        var dot = new Ellipse2D.Double(-15, 20, 30, 30);

        var area = new Area(arc);
        area.subtract(new Area(innerArc));
        area.add(new Area(dot));

        FERMATA = area;
    }

    // Singleton instance
    private static final FermataRenderer INSTANCE = new FermataRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private FermataRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static @NotNull FermataRenderer getInstance() {
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
        if (!element.isFermata()) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, TRANSFORM)) {
            // Position fermata above the note
            int noteX = element.getXPos();
            int noteY = getEffectiveFermataYPos(element, ctx);

            g2.translate(noteX - 5, noteY + 12);
            g2.scale(0.0625, 0.0625);
            g2.scale(0.9, 0.8);

            g2.fill(FERMATA);
        }
    }

    /**
     * Gets the Y position for a fermata from layout result,
     * falling back to a computed position when no layout is available
     * (e.g. for the insertion note preview).
     */
    private int getEffectiveFermataYPos(
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        var layoutResult = ctx.getLayoutResult();

        if (layoutResult != null) {
            var bounds = layoutResult.findAttachmentBounds(note, FermataAttachment.class);

            if (bounds != null) {
                return (int) bounds.getTop();
            }
        }

        // Fallback: compute position directly from note position
        int fermataYPos = getFermataYPos(note);
        return ctx.getMiddleLineY() + (int) (fermataYPos * LayoutStylesheet.NOTE_Y_OFFSET);
    }

    /**
     * Renders a fermata for a note if it has one.
     *
     * @param g2          Graphics context
     * @param note        The note to check
     * @param ctx         Render context
     */
    public void renderFermata(
        @NotNull Graphics2D g2,
        @NotNull Note note,
        @NotNull ElementRenderContext ctx
    ) {
        render(note, g2, ctx);
    }

    /**
     * Calculates the Y position for the fermata based on note position.
     * Fermata is placed above the note, further up for higher notes.
     */
    private int getFermataYPos(@NotNull Note note) {
        int yPos = note.getYPos();

        // For notes above the staff, place fermata higher
        if (yPos < -4) {
            return yPos - 3;
        }

        // Default position above the staff
        return -7;
    }
}
