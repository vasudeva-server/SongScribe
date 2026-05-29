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
import static songscribe.ui.renderer.GraphicsState.Property.FONT;

import module java.desktop;

import java.util.function.DoubleConsumer;


import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.component.ScoreView;
import songscribe.layout.NoteGeometry;

import org.jspecify.annotations.Nullable;

import songscribe.layout.StaffExtents;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Stateless rendering utilities shared by element renderers.
 * <p>
 * Holds the music-font constants and the common drawing/coordinate helpers
 * that the renderers used to inherit from {@code BaseElementRenderer}.
 */
public final class RenderingUtils {

    private RenderingUtils() {
    }

    // ==========================================================================
    // Font Constants
    // ==========================================================================

    /**
     * The base font size for music notation glyphs, in staff-space units.
     * Under the Graphics2D scale transform, this produces the correct pixel size
     * (4.0 ss * 8 px/ss = 32px).
     */
    public static final float FONT_SIZE = 4.0f;

    /**
     * The Bravura (SMuFL) music notation font at standard note size.
     */
    public static final Font MUSIC_FONT;

    /**
     * Scale factor applied to grace notes relative to regular notes.
     * Grace notes use the regular glyphs drawn with a scaled-down Bravura font.
     */
    public static final float GRACE_NOTE_SCALE = 0.75f;

    /**
     * The music font scaled for grace note rendering ({@link #GRACE_NOTE_SCALE}).
     */
    public static final Font GRACE_NOTE_FONT;

    /** Cached Bravura music font at {@link #FONT_SIZE}. */
    @Nullable
    private static Font noteFont = null;

    /**
     * Returns the Bravura (SMuFL) music font at standard note size ({@link #FONT_SIZE}).
     * Callers needing a different size should call {@code deriveFont()}.
     */
    public static Font getMusicFont() {
        if (noteFont == null) {
            noteFont = MyFontUtils.getLocalFont("Bravura.otf", FONT_SIZE);
        }

        return noteFont;
    }

    static {
        try {
            MUSIC_FONT = getMusicFont();
            GRACE_NOTE_FONT = getMusicFont().deriveFont(FONT_SIZE * GRACE_NOTE_SCALE);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load required fonts for rendering.", e);
        }
    }

    // ==========================================================================
    // Colors
    // ==========================================================================

    public static final Color STAFF_LINE_COLOR = Color.BLACK;
    static final Color ELEMENT_COLOR = Color.BLACK;

    /**
     * Returns the rendering color for a decoration attached to {@code element}.
     * <p>
     * Uses {@link ElementFrame#currentElementIndex()} when set (avoids
     * a linear scan), otherwise falls back to {@code line.getElementIndex(element)}.
     * <p>
     * Returns the preview element color when the element is not found in the current
     * line (index &lt; 0): the element is the insertion preview, so its decorations
     * must match the preview note's color.
     * Throws on corruption if there is no current line.
     */
    static Color getDecorationColor(
        StaffElement element,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var index = frame.currentElementIndex();

        if (index >= 0) {
            return invariants.getElementColor(index);
        }

        var line = invariants.requireCurrentLine();
        index = line.getElementIndex(element);

        if (index < 0) {
            return ScoreView.getPreviewElementColor();
        }

        return invariants.getElementColor(index);
    }

    /**
     * Sets the graphics color for a decoration attached to {@code element}.
     * Delegates to {@link #getDecorationColor(StaffElement, LineInvariants, ElementFrame)}.
     */
    static void applyDecorationColor(
        Graphics2D g2,
        StaffElement element,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        g2.setColor(getDecorationColor(element, invariants, frame));
    }

    // ==========================================================================
    // Coordinate Transformation Utilities
    // ==========================================================================

    /**
     * Converts a layout Y coordinate to component space.
     * <p>
     * Layout Y coordinates are relative to middleLineY=0.
     * Component Y coordinates are relative to the component's top edge.
     *
     * @param layoutYSs the Y coordinate in layout space (from a DecorationLayout)
     * @param invariants       the per-line invariants containing middleLineY
     * @return the Y coordinate in component space
     */
    static double layoutYToComponentYSs(double layoutYSs, LineInvariants invariants) {
        return invariants.getMiddleLineYSs() + layoutYSs;
    }

    // ==========================================================================
    // Shared Drawing Utilities
    // ==========================================================================

    /**
     * Draws a ledger line for a note above or below the staff.
     * <p>
     * Uses a filled rounded rectangle with pixel-snapped top/bottom edges
     * (same technique as {@code LineRenderer.drawStaffLines()}) to avoid
     * antialiasing fuzz. The semicircular ends come from setting the arc
     * height equal to the snapped thickness.
     *
     * @param g2       Graphics context
     * @param xSs      Center X position of the note in staff spaces
     * @param ySs      Y position of the ledger line in staff spaces
     * @param widthSs  Width of the ledger line in staff spaces
     */
    static void drawLedgerLine(Graphics2D g2, double xSs, double ySs, double widthSs, LineInvariants invariants) {
        // Color is intentionally not set — inherited from caller so insertion notes
        // draw ledger lines in their own color.
        var thicknessSs = invariants.getLineThickness().ledgerLineSs();
        var halfWidth = widthSs / 2.0;
        GraphicUtils.fillHorizontalLine(g2, xSs - halfWidth, xSs + halfWidth, ySs, thicknessSs);
    }

    /**
     * Draws a SMuFL glyph using the Bravura font.
     *
     * @param g2    Graphics context
     * @param glyph The SMuFL glyph to draw
     * @param xSs   X position in staff spaces
     * @param ySs   Y position in staff spaces
     */
    static void drawBravuraGlyph(
        Graphics2D g2,
        SMuFLGlyph glyph,
        double xSs,
        double ySs
    ) {
        drawBravuraGlyph(g2, glyph, xSs, ySs, false);
    }

    /**
     * Draws a SMuFL glyph using the Bravura font.
     *
     * @param g2            Graphics context
     * @param glyph         The SMuFL glyph to draw
     * @param xSs           X position in staff spaces
     * @param ySs           Y position in staff spaces
     * @param preserveColor If true, preserves the current graphics color instead of setting ELEMENT_COLOR
     */
    static void drawBravuraGlyph(
        Graphics2D g2,
        SMuFLGlyph glyph,
        double xSs,
        double ySs,
        boolean preserveColor
    ) {
        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(MUSIC_FONT);
            if (!preserveColor) {
                g2.setColor(ELEMENT_COLOR);
            }
            g2.drawString(glyph.asString(), (float) xSs, (float) ySs);
        }
    }

    /**
     * Returns the X position that centers a glyph of the given width over the notehead
     * of the specified element.
     * <p>
     * The glyph's visual left edge is at {@code origin + bboxLeft}, so centering
     * the visual content requires offsetting by {@code bboxLeft}.
     * <p>
     * Uses the notehead-only width (excluding flag extent) and accounts for the
     * down-stem notehead shift so ornaments stay centered over the notehead
     * regardless of note duration or stem direction.
     *
     * @param layoutXSs     the layout X position (left edge of the note column)
     * @param note          the note whose notehead center is used
     * @param glyphBBoxLeft the glyph's bounding box left edge (x offset from origin)
     * @param glyphWidthSs  the width of the glyph to center (bBox right - left)
     * @return the X coordinate for drawing
     */
    static double centeredGlyphX(
        double layoutXSs, StaffElement note,
        double glyphBBoxLeft, double glyphWidthSs) {

        var type = note.getType();
        var noteheadCenterXSs = type.getElementCenterXSs()
            + NoteGeometry.getNoteheadXOffsetSs(type, note.isUpper());
        return layoutXSs + noteheadCenterXSs - glyphBBoxLeft - glyphWidthSs / 2.0;
    }

    /**
     * Converts a layout top Y position to the glyph origin Y for drawing.
     * <p>
     * Layout positions represent the top edge of the bounding box, but SMuFL glyphs
     * are drawn from their origin (y=0 in glyph coordinates). If the glyph extends
     * below its origin (bbox.bottom > 0), using {@code topY + height} places the glyph
     * too low. This method correctly computes the origin Y using the bbox top offset.
     *
     * @param layoutTopYSs the top edge of the element's bounding box in staff spaces
     * @param glyph        the SMuFL glyph to draw
     * @return the Y coordinate to pass to {@link #drawBravuraGlyph}
     */
    static double glyphOriginYFromLayoutTop(double layoutTopYSs, SMuFLGlyph glyph) {
        var bbox = SMuFLMetadata.requireBBox(glyph);
        return layoutTopYSs - bbox.top();
    }

    /**
     * Calculates the Y coordinate for a note given its staff position.
     * <p>
     * The staff position is relative to the middle line (B4), where:
     * <ul>
     *   <li>0 = B4 (middle line)</li>
     *   <li>Negative values = higher pitches (above middle line)</li>
     *   <li>Positive values = lower pitches (below middle line)</li>
     * </ul>
     * Each staff position is 0.5 ss (half a staff space).
     *
     * @param staffPosition The note's staff position relative to middle line
     * @param middleLineYSs Y position of middle staff line in staff spaces
     * @return Y coordinate for the note in staff spaces
     */
    public static double noteStaffPositionToCoordinateSs(int staffPosition, double middleLineYSs) {
        return middleLineYSs + StaffExtents.spToSs(staffPosition);
    }

    /**
     * Iterates over the Y offsets (in staff spaces, relative to the note's staff position)
     * of each ledger line needed for a note at the given staff position.
     *
     * @param staffPosition The note's staff position (integer index along the Y axis)
     * @param consumer      Called once per ledger line with the Y offset in staff spaces
     */
    static void forEachLedgerLineYSs(int staffPosition, DoubleConsumer consumer) {
        var i = staffPosition;

        if ((staffPosition % 2) != 0) {
            i += (staffPosition > 0) ? -1 : 1;
        }

        var step = (staffPosition > 0) ? -2 : 2;

        while (Math.abs(i) > 5) {
            consumer.accept(StaffExtents.spToSs(i - staffPosition));
            i += step;
        }
    }

    /**
     * Returns the X offset in staff spaces from the note reference point to the stem center,
     * for the given stem direction and note type.
     *
     * @param noteType the note type (determines which notehead anchor to use)
     * @param upper    true = stem goes up (stem-up SE anchor); false = stem goes down (stem-down NW anchor)
     * @return X offset from note reference point to stem center, in staff spaces
     */
    static double stemCenterXOffsetSs(ElementType noteType, boolean upper) {
        var isMinim = noteType == ElementType.MINIM;
        double anchorX;

        if (isMinim) {
            anchorX = (upper ? Engraving.NOTEHEAD_HALF_STEM_UP_SE : Engraving.NOTEHEAD_HALF_STEM_DOWN_NW).x();
        } else {
            anchorX = (upper ? Engraving.NOTEHEAD_BLACK_STEM_UP_SE : Engraving.NOTEHEAD_BLACK_STEM_DOWN_NW).x();
        }

        // upper: SE anchor is the stem's right edge; center = anchorX - half stem width
        // lower: NW anchor is the stem's left edge (after notehead shift); center = anchorX
        return upper ? anchorX - NoteGeometry.STEM_WIDTH_SS / 2.0 : anchorX;
    }
}
