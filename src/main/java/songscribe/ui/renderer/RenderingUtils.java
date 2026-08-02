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

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

import module java.desktop;

import java.util.List;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.LineElement;
import songscribe.dom.StaffElement;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.smufl.BravuraFont;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.component.ScoreView;
import songscribe.engraving.LineThickness;
import songscribe.layout.NoteGeometry;

import songscribe.engraving.Staff;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

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
    public static final float FONT_SIZE = BravuraFont.SIZE_SS;

    /**
     * The Bravura (SMuFL) music notation font at standard note size.
     */
    public static final Font MUSIC_FONT;

    /**
     * The music font scaled for grace note rendering ({@link ElementType#GRACE_NOTE_SCALE}).
     */
    public static final Font GRACE_NOTE_FONT;

    /**
     * Returns the Bravura (SMuFL) music font at standard note size ({@link #FONT_SIZE}).
     * Callers needing a different size should call {@code deriveFont()}.
     */
    public static Font getMusicFont() {
        return BravuraFont.font();
    }

    static {
        try {
            MUSIC_FONT = getMusicFont();
            GRACE_NOTE_FONT = getMusicFont().deriveFont(FONT_SIZE * ElementType.GRACE_NOTE_SCALE);
        } catch (Exception e) {
            throw new RuntimeException("Cannot load required fonts for rendering.", e);
        }
    }

    /**
     * Returns the music font to draw {@code note}'s glyphs: {@link #GRACE_NOTE_FONT} for grace notes,
     * else {@link #MUSIC_FONT}. Pair with {@link NoteGeometry#getGlyphScale} so the font and the pen
     * advances stay in step.
     */
    public static Font getGlyphFont(StaffElement note) {
        return note.getType().isGraceNote() ? GRACE_NOTE_FONT : MUSIC_FONT;
    }

    // ==========================================================================
    // Colors
    // ==========================================================================

    public static final Color STAFF_LINE_COLOR = Color.BLACK;
    static final Color ELEMENT_COLOR = Color.BLACK;

    /**
     * Returns the rendering color for a decoration attached to {@code element}.
     * <p>
     * Uses {@link ElementFrame#currentElementIndex()} when set, since the drawing loop
     * already has it; otherwise falls back to {@code line.getElementIndex(element)}.
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
     * Returns the rendering color for any decoration that is selectable in its own right —
     * every kind, whether or not it hangs off a note.
     * <p>
     * <b>Selection takes precedence over everything else.</b> A selected decoration draws in
     * {@link ScoreView#getSelectionColor()} even while its note is playing, hovered, or standing
     * in as the insertion preview. It has to: in edit mode the selection is what the next Delete
     * removes, and the user cannot be left guessing at that because playback happens to be
     * running.
     * <p>
     * Only when the decoration is <em>not</em> selected does it take its owner note's color, so
     * that playback, the replaced-element hover, the insertion preview and a range selection each
     * color the whole note, decorations included. A decoration that belongs to no single note —
     * a hairpin, an ending, a tuplet — passes a {@code null} owner and is plain black unless it
     * is the selection.
     *
     * @param owner the note whose color an unselected decoration inherits, or {@code null} when
     *              the decoration belongs to no single note
     */
    static Color decorationColor(
        HitTarget target,
        @Nullable StaffElement owner,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var targetColor = invariants.colorFor(target, frame.currentElementIndex());

        if (!LineInvariants.isDefaultColor(targetColor)) {
            return targetColor;
        }

        if (owner == null) {
            return ELEMENT_COLOR;
        }

        return getDecorationColor(owner, invariants, frame);
    }

    /**
     * Sets the graphics color for a selectable decoration.
     * Delegates to {@link #decorationColor}.
     */
    static void applyDecorationColor(
        Graphics2D g2,
        HitTarget target,
        @Nullable StaffElement owner,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        g2.setColor(decorationColor(target, owner, invariants, frame));
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
        return layoutYToComponentYSs(layoutYSs, invariants.getMiddleLineYSs());
    }

    /**
     * Converts a layout Y coordinate to component space, given the middle-line Y directly.
     * <p>
     * Hit-testing runs outside a render pass and so has no {@link LineInvariants} to hand;
     * it calls this overload so hit geometry cannot drift from what was drawn.
     *
     * @param layoutYSs     the Y coordinate in layout space (from a DecorationLayout)
     * @param middleLineYSs the line's middle-staff-line Y in component space
     * @return the Y coordinate in component space
     */
    static double layoutYToComponentYSs(double layoutYSs, double middleLineYSs) {
        return middleLineYSs + layoutYSs;
    }

    /**
     * Returns the first decoration in {@code decorations} whose drawn box contains the
     * click point, or null if none does.
     * <p>
     * The box comes from the decoration's {@link LayoutResult.DecorationLayout} and
     * spans its width and its height plus margin. Containment follows
     * {@link Rectangle2D#contains}, so the left and top edges are inclusive and the
     * right and bottom edges are exclusive. When decorations overlap, the first match
     * in the given order wins.
     * <p>
     * This answers only the geometric question. Turning a hit into a selection is the
     * selection layer's job, which is why this returns the decoration itself. Every
     * decoration type hit-tests through here, so a change to the box or the overlap
     * rule reaches all of them at once.
     *
     * @param decorations   the decorations to test, in the order ties should be broken
     * @param clickXSs      click X in staff spaces (line-local, same space as DecorationLayout.xSs)
     * @param clickYSs      click Y in staff spaces (component space, relative to the component top)
     * @param layoutResult  the line's layout result, or null if layout has not run yet
     * @param middleLineYSs the line's middle-staff-line Y in component space (staff spaces)
     */
    static <D extends LineElement> @Nullable D hitTestDecoration(
        List<D> decorations,
        double clickXSs,
        double clickYSs,
        @Nullable LayoutResult layoutResult,
        double middleLineYSs
    ) {
        if (layoutResult == null) {
            return null;
        }

        for (var decoration : decorations) {
            var decorationLayout = layoutResult.getDecorationLayout(decoration);

            // A decoration whose anchor element is missing gets no layout entry, so a
            // null layout here is an expected incomplete decoration, not an error.
            if (decorationLayout == null) {
                continue;
            }

            var hitRect = new Rectangle2D.Double(
                decorationLayout.xSs(),
                layoutYToComponentYSs(decorationLayout.ySs(), middleLineYSs),
                decorationLayout.widthSs(),
                decorationLayout.heightSs() + decorationLayout.marginSs());

            if (hitRect.contains(clickXSs, clickYSs)) {
                return decoration;
            }
        }

        return null;
    }

    // ==========================================================================
    // Shared Drawing Utilities
    // ==========================================================================

    /**
     * Draws a ledger line for a note above or below the staff.
     *
     * @param g2       Graphics context
     * @param leftXSs  Left edge X position of the ledger line in staff spaces
     * @param rightXSs Right edge X position of the ledger line in staff spaces
     * @param ySs      Y position of the ledger line in staff spaces
     */
    static void drawLedgerLine(Graphics2D g2, double leftXSs, double rightXSs, double ySs, LineInvariants invariants) {
        // Color is intentionally not set — inherited from caller so insertion notes
        // draw ledger lines in their own color.
        var thicknessSs = LineThickness.LEDGER_LINE_SS;
        GraphicUtils.drawRoundedLine(g2, leftXSs, ySs, rightXSs, ySs, thicknessSs);
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
        try (var _ = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(MUSIC_FONT);
            if (!preserveColor) {
                g2.setColor(ELEMENT_COLOR);
            }
            g2.drawString(glyph.asString(), (float) xSs, (float) ySs);
        }
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
     * The arithmetic lives in {@link NoteGeometry#noteStaffPositionToCoordinateSs}, on the layout
     * side of the boundary, because layout needs the same quantity and must not depend on
     * {@code songscribe.ui}. This delegate keeps the render call sites reading naturally.
     *
     * @param staffPosition The note's staff position relative to middle line
     * @param middleLineYSs Y position of middle staff line in staff spaces
     * @return Y coordinate for the note in staff spaces
     */
    public static double noteStaffPositionToCoordinateSs(int staffPosition, double middleLineYSs) {
        return NoteGeometry.noteStaffPositionToCoordinateSs(staffPosition, middleLineYSs);
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
            consumer.accept(Staff.spToSs(i - staffPosition));
            i += step;
        }
    }

    /**
     * Returns the X offset in staff spaces from the note reference point to the stem center,
     * for the given stem direction and note type.
     *
     * @param noteType  the note type (determines which notehead anchor to use)
     * @param direction stem direction: UP = stem-up SE anchor; DOWN = stem-down NW anchor
     * @return X offset from note reference point to stem center, in staff spaces
     */
    static double stemCenterXOffsetSs(ElementType noteType, StaffElement.Direction direction) {
        var anchorX = NoteGeometry.stemSideAnchor(noteType, direction).x();
        var upper = direction.isUp();

        // upper: SE anchor is the stem's right edge; center = anchorX - half stem width
        // lower: NW anchor is the stem's left edge; center = anchorX + half stem width
        var halfStemWidthSs = NoteGeometry.STEM_WIDTH_SS / 2.0;
        return upper ? anchorX - halfStemWidthSs : anchorX + halfStemWidthSs;
    }
}
