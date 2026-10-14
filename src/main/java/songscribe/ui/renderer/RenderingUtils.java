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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Direction;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.engraving.LedgerLine;
import songscribe.engraving.StemMetrics;
import songscribe.hit.HitTarget;
import songscribe.layout.NoteGeometry;
import songscribe.smufl.BravuraFont;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StemmedNotehead;
import songscribe.ui.component.ScoreView;
import songscribe.util.GraphicUtils;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;

public final class RenderingUtils {

    private RenderingUtils() {
    }

    // ==========================================================================
    // Font Constants
    // ==========================================================================

    public static final float FONT_SIZE = BravuraFont.SIZE_SS;

    public static final Font MUSIC_FONT;

    public static final Font GRACE_NOTE_FONT;

    /**
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

    public static Font getGlyphFont(StaffElement note) {
        return note.getType().isGraceNote() ? GRACE_NOTE_FONT : MUSIC_FONT;
    }

    // ==========================================================================
    // Colors
    // ==========================================================================

    public static final Color STAFF_LINE_COLOR = Color.BLACK;
    static final Color ELEMENT_COLOR = Color.BLACK;

    /**
     * An element the current line does not contain is the insertion preview, so its decorations
     * take the preview note's color.
     */
    static Color getDecorationColor(
        StaffElement element,
        LineInvariants invariants,
        ElementFrame frame
    ) {
        var index = frame.currentElementIndex();

        if (index != null) {
            return invariants.getElementColor(index);
        }

        var line = invariants.requireCurrentLine();

        if (!line.contains(element)) {
            return ScoreView.getPreviewElementColor();
        }

        return invariants.getElementColor(line.getElementIndex(element));
    }

    /**
     * <b>Selection takes precedence over everything else:</b> a selected decoration draws in
     * {@link ScoreView#getSelectionColor()} even while its note is playing, hovered, or the
     * insertion preview. Otherwise it takes its owner note's color, so that each of those colors
     * the whole note, decorations included. A decoration that belongs to no single note passes a
     * {@code null} owner and is plain black unless it is the selection.
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
        var targetColor = target instanceof HitTarget.Element element
            ? invariants.colorFor(element, frame.currentElementIndex())
            : invariants.colorFor(target);

        if (!LineInvariants.isDefaultColor(targetColor)) {
            return targetColor;
        }

        if (owner == null) {
            return ELEMENT_COLOR;
        }

        return getDecorationColor(owner, invariants, frame);
    }

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

    static double layoutYToComponentYSs(double layoutYSs, LineInvariants invariants) {
        return layoutYToComponentYSs(layoutYSs, invariants.getMiddleLineYSs());
    }

    static double layoutYToComponentYSs(double layoutYSs, double middleLineYSs) {
        return middleLineYSs + layoutYSs;
    }

    // ==========================================================================
    // Shared Drawing Utilities
    // ==========================================================================

    static void drawLedgerLine(Graphics2D g2, double leftXSs, double rightXSs, double ySs, LineInvariants invariants) {
        // Color is intentionally not set — inherited from caller so insertion notes
        // draw ledger lines in their own color.
        var thicknessSs = LedgerLine.THICKNESS_SS;
        GraphicUtils.drawRoundedLine(g2, leftXSs, ySs, rightXSs, ySs, thicknessSs);
    }

    static void drawBravuraGlyph(
        Graphics2D g2,
        SMuFLGlyph glyph,
        double xSs,
        double ySs
    ) {
        drawBravuraGlyph(g2, glyph, xSs, ySs, false);
    }

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
     * Layout positions are the top edge of the bounding box, but SMuFL glyphs are drawn from their
     * origin; {@code topY + height} would place a glyph that extends below its origin too low.
     */
    static double glyphOriginYFromLayoutTop(double layoutTopYSs, SMuFLGlyph glyph) {
        var bbox = SMuFLMetadata.bboxSs(glyph);
        return layoutTopYSs - bbox.topSs();
    }

    /**
     * The arithmetic lives in {@link NoteGeometry#noteStaffPositionToCoordinateSs} because layout
     * needs the same quantity and must not depend on {@code songscribe.ui}.
     */
    public static double noteStaffPositionToCoordinateSs(int staffPosition, double middleLineYSs) {
        return NoteGeometry.noteStaffPositionToCoordinateSs(staffPosition, middleLineYSs);
    }

    static double stemCenterXOffsetSs(StemmedNotehead notehead, Direction direction) {
        var anchorXSs = NoteGeometry.stemSideAnchor(notehead, direction).xSs();
        var upper = direction.isUp();

        // upper: SE anchor is the stem's right edge; center = anchorXSs - half stem width
        // lower: NW anchor is the stem's left edge; center = anchorXSs + half stem width
        return upper
            ? anchorXSs - StemMetrics.halfWidthSs()
            : anchorXSs + StemMetrics.halfWidthSs();
    }
}
