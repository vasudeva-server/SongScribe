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

import org.jspecify.annotations.Nullable;

import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.smufl.SMuFLGlyph;
import songscribe.ui.layout.KeySignature;
import songscribe.ui.layout.StaffExtents;

/**
 * Renders key signatures (sharps or flats) at the start of a staff line.
 * <p>
 * The key signature follows the clef and shows the sharps or flats
 * in the order they appear (FCGDAEB for sharps, BEADGCF for flats).
 */
public class KeySignatureRenderer extends BaseElementRenderer<KeySignature> {

    // ==========================================================================
    // Accidental Glyphs
    // ==========================================================================

    private static final SMuFLGlyph FLAT_GLYPH = SMuFLGlyph.ACCIDENTAL_FLAT;
    private static final SMuFLGlyph SHARP_GLYPH = SMuFLGlyph.ACCIDENTAL_SHARP;
    private static final SMuFLGlyph NATURAL_GLYPH = SMuFLGlyph.ACCIDENTAL_NATURAL;

    // Y positions for accidentals relative to middle line (0 = B4)
    // Index 0 = None (empty), Index 1 = Flats, Index 2 = Sharps
    // Flats: B, E, A, D, G, C, F
    private static final int[] FLAT_STAFF_POSITIONS = {0, -3, 1, -2, 2, -1, 3};
    // Sharps: F, C, G, D, A, E, B
    private static final int[] SHARP_STAFF_POSITIONS = {-4, -1, -5, -2, 1, -3, 0};

    // Y positions indexed by KeyType ordinal (for key change rendering)
    private static final int[][] KEY_STAFF_POSITIONS = new int[][]{
        new int[]{}, // NONE
        FLAT_STAFF_POSITIONS,
        SHARP_STAFF_POSITIONS
    };

    // Horizontal spacing between accidentals, in staff-space units
    private static final double ACCIDENTAL_SPACING_SS = 1.125;  // 9px / 8 px/ss

    // Spacing used in key change rendering (slightly tighter), in staff-space units
    private static final double KEY_CHANGE_SPACING_SS = 1.0;  // 8px / 8 px/ss

    // Right margin for key change from line end, in staff-space units
    private static final double KEY_CHANGE_RIGHT_MARGIN_SS = 0.625;  // 5px / 8 px/ss

    // Singleton instance
    private static final KeySignatureRenderer INSTANCE = new KeySignatureRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private KeySignatureRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static KeySignatureRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        KeySignature element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        if (!element.hasAccidentals()) {
            return;
        }

        var keyType = element.getKeyType();
        int accidentalCount = element.getAccidentalCount();

        if (keyType == KeyType.NONE || accidentalCount == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, FONT, COLOR)) {
            g2.setFont(MUSIC_FONT);
            g2.setColor(ELEMENT_COLOR);

            // Get the starting X position from the element
            double xPosSs = element.getXSs();
            double middleLineYSs = ctx.getMiddleLineYSs();

            // Determine glyph and Y positions based on key type
            SMuFLGlyph glyph;
            int[] staffPositions;

            if (keyType == KeyType.FLATS) {
                glyph = FLAT_GLYPH;
                staffPositions = FLAT_STAFF_POSITIONS;
            } else {
                glyph = SHARP_GLYPH;
                staffPositions = SHARP_STAFF_POSITIONS;
            }

            // Draw each accidental
            var glyphStr = glyph.asString();

            for (int i = 0; i < accidentalCount; i++) {
                int staffPosition = staffPositions[i % 7];
                double y = middleLineYSs + StaffExtents.spToSs(staffPosition);

                g2.drawString(glyphStr, (float) xPosSs, (float) y);
                xPosSs += ACCIDENTAL_SPACING_SS;
            }
        }
    }

    // ==========================================================================
    // Key Change Rendering (at end of line)
    // ==========================================================================

    /**
     * Renders a key change at the end of a staff line.
     * <p>
     * This is called when the next line has a different key signature.
     * It draws naturals to cancel accidentals (if needed) and the new key signature.
     *
     * @param g2        Graphics context
     * @param line      The current line
     * @param nextLine  The next line (with different key)
     * @param lineWidth The width of the staff line (ss)
     * @param ctx       Render context
     */
    public void renderKeyChange(
        Graphics2D g2,
        Line line,
        Line nextLine,
        double lineWidth,
        ElementRenderContext ctx
    ) {
        // If key signature is identical, nothing to draw
        if (nextLine.getKeyAccidentalCount() == line.getKeyAccidentalCount()
            && nextLine.getKeyType() == line.getKeyType()) {
            return;
        }

        // Prepare arrays for up to 2 key signatures (naturals + new key)
        @Nullable KeyType[] keyTypes = new KeyType[2];
        var accidentalCounts = new int[2];
        var startingOffsets = new int[2];
        var isNaturals = new boolean[2];

        if (nextLine.getKeyType() == line.getKeyType()) {
            // Same key type, different count
            keyTypes[0] = nextLine.getKeyType();
            accidentalCounts[0] = nextLine.getKeyAccidentalCount();

            if (nextLine.getKeyAccidentalCount() > line.getKeyAccidentalCount()) {
                // Adding more accidentals - just show the new ones
                keyTypes[1] = null;
            } else {
                // Removing accidentals - show naturals for the removed ones
                keyTypes[1] = line.getKeyType();
                accidentalCounts[1] = line.getKeyAccidentalCount() - nextLine.getKeyAccidentalCount();
                startingOffsets[1] = nextLine.getKeyAccidentalCount();
                isNaturals[1] = true;
            }
        } else {
            // Different key type - show naturals for all current, then new key
            keyTypes[0] = line.getKeyType();
            accidentalCounts[0] = line.getKeyAccidentalCount();
            isNaturals[0] = true;

            keyTypes[1] = nextLine.getKeyType();
            accidentalCounts[1] = nextLine.getKeyAccidentalCount();
        }

        renderKeySignatureChange(g2, keyTypes, accidentalCounts, startingOffsets, isNaturals, lineWidth, ctx);
    }

    /**
     * Renders key signature change components at the end of a line.
     *
     * @param g2              Graphics context
     * @param keyTypes        Array of up to 2 key types to render (null marks end)
     * @param accidentalCounts Number of accidentals for each key type
     * @param startingOffsets  Starting offset in Y position array (for partial naturals)
     * @param isNaturals      Whether each key type should be rendered as naturals
     * @param lineWidth       Width of the staff line (ss)
     * @param ctx             Render context
     */
    private void renderKeySignatureChange(
        Graphics2D g2,
        @Nullable KeyType [] keyTypes,
        int [] accidentalCounts,
        int [] startingOffsets,
        boolean [] isNaturals,
        double lineWidth,
        ElementRenderContext ctx
    ) {
        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(MUSIC_FONT);
            g2.setColor(ELEMENT_COLOR);

            var middleLineYSs = ctx.getMiddleLineYSs();

            // Calculate starting X position (right-aligned with margin)
            double xPosSs = lineWidth - KEY_CHANGE_RIGHT_MARGIN_SS;

            // Calculate total width needed
            for (var count : accidentalCounts) {
                xPosSs -= count * KEY_CHANGE_SPACING_SS;
            }

            // Render each key signature group
            for (var kt = 0; kt < keyTypes.length; kt++) {
                if (keyTypes[kt] == null) {
                    break;
                }

                var keyTypeOrdinal = keyTypes[kt].ordinal();

                if (keyTypeOrdinal == 0) {
                    // NONE - skip
                    continue;
                }

                var staffPositions = KEY_STAFF_POSITIONS[keyTypeOrdinal];
                var glyph = isNaturals[kt] ? NATURAL_GLYPH : getGlyphForKeyType(keyTypes[kt]);
                var glyphStr = glyph.asString();

                for (var i = 0; i < accidentalCounts[kt]; i++) {
                    var staffPosition = staffPositions[(i + startingOffsets[kt]) % 7];
                    var y = middleLineYSs + StaffExtents.spToSs(staffPosition);

                    g2.drawString(glyphStr, (float) xPosSs, (float) y);
                    xPosSs += KEY_CHANGE_SPACING_SS;
                }
            }
        }
    }

    /**
     * Returns the glyph for the given key type.
     */
    private SMuFLGlyph getGlyphForKeyType(KeyType keyType) {
        return switch (keyType) {
            case FLATS -> FLAT_GLYPH;
            case SHARPS -> SHARP_GLYPH;
            default -> throw new IllegalArgumentException("No glyph for key type: " + keyType);
        };
    }
}
