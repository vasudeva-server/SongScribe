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

import java.awt.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.smufl.SMuFLGlyph;
import songscribe.ui.layout.KeySignature;
import songscribe.ui.layout.LayoutStylesheet;

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
    private static final int[] FLAT_Y_POSITIONS = {0, -3, 1, -2, 2, -1, 3};
    // Sharps: F, C, G, D, A, E, B
    private static final int[] SHARP_Y_POSITIONS = {-4, -1, -5, -2, 1, -3, 0};

    // Y positions indexed by KeyType ordinal (for key change rendering)
    private static final int[][] KEY_Y_POSITIONS = new int[][]{
        new int[]{}, // NONE
        FLAT_Y_POSITIONS,
        SHARP_Y_POSITIONS
    };

    // Horizontal spacing between accidentals
    private static final int ACCIDENTAL_SPACING = 9;

    // Spacing used in key change rendering (slightly tighter)
    private static final int KEY_CHANGE_SPACING = 8;

    // Right margin for key change from line end
    private static final int KEY_CHANGE_RIGHT_MARGIN = 5;

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
    public static @NotNull KeySignatureRenderer getInstance() {
        return INSTANCE;
    }

    // ==========================================================================
    // Rendering
    // ==========================================================================

    @Override
    protected void renderElement(
        @NotNull KeySignature element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
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
            g2.setFont(BRAVURA_FONT);
            g2.setColor(NOTE_COLOR);

            // Get the starting X position from the element
            double xPos = element.getX();
            int middleLineY = ctx.getMiddleLineY();

            // Determine glyph and Y positions based on key type
            SMuFLGlyph glyph;
            int[] yPositions;

            if (keyType == KeyType.FLATS) {
                glyph = FLAT_GLYPH;
                yPositions = FLAT_Y_POSITIONS;
            } else {
                glyph = SHARP_GLYPH;
                yPositions = SHARP_Y_POSITIONS;
            }

            // Draw each accidental
            var glyphStr = glyph.asString();

            for (int i = 0; i < accidentalCount; i++) {
                // Calculate Y position for this accidental
                int yPos = yPositions[i % 7];
                int y = middleLineY + (int) (yPos * LayoutStylesheet.NOTE_Y_OFFSET);

                g2.drawString(glyphStr, (float) xPos, (float) y);
                xPos += ACCIDENTAL_SPACING;
            }
        }
    }

    /**
     * Renders a key signature at the specified position.
     * <p>
     * Utility method for direct rendering without a KeySignature element.
     *
     * @param g2              Graphics context
     * @param keyType         The key type (FLATS or SHARPS)
     * @param accidentalCount Number of accidentals (1-7)
     * @param xPos            Starting X position
     * @param middleLineY     Y coordinate of the middle staff line
     * @param ctx             Render context for font access
     */
    public void renderKeySignature(
        @NotNull Graphics2D g2,
        @NotNull KeyType keyType,
        int accidentalCount,
        float xPos,
        int middleLineY,
        @NotNull ElementRenderContext ctx
    ) {
        if (keyType == KeyType.NONE || accidentalCount == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.setColor(NOTE_COLOR);

            SMuFLGlyph glyph;
            int[] yPositions;

            if (keyType == KeyType.FLATS) {
                glyph = FLAT_GLYPH;
                yPositions = FLAT_Y_POSITIONS;
            } else {
                glyph = SHARP_GLYPH;
                yPositions = SHARP_Y_POSITIONS;
            }

            var glyphStr = glyph.asString();
            float currentX = xPos;

            for (int i = 0; i < accidentalCount; i++) {
                int yPos = yPositions[i % 7];
                int y = middleLineY + (int) (yPos * LayoutStylesheet.NOTE_Y_OFFSET);

                g2.drawString(glyphStr, currentX, (float) y);
                currentX += ACCIDENTAL_SPACING;
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
     * @param lineWidth The width of the staff line
     * @param ctx       Render context
     */
    public void renderKeyChange(
        @NotNull Graphics2D g2,
        @NotNull Line line,
        @NotNull Line nextLine,
        int lineWidth,
        @NotNull ElementRenderContext ctx
    ) {
        // If key signature is identical, nothing to draw
        if (nextLine.getKeyAccidentalCount() == line.getKeyAccidentalCount()
            && nextLine.getKeyType() == line.getKeyType()) {
            return;
        }

        // Prepare arrays for up to 2 key signatures (naturals + new key)
        var keyTypes = new KeyType[2];
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
     * @param lineWidth       Width of the staff line
     * @param ctx             Render context
     */
    private void renderKeySignatureChange(
        @NotNull Graphics2D g2,
        @NotNull KeyType @Nullable [] keyTypes,
        int @NotNull [] accidentalCounts,
        int @NotNull [] startingOffsets,
        boolean @NotNull [] isNaturals,
        int lineWidth,
        @NotNull ElementRenderContext ctx
    ) {
        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(BRAVURA_FONT);
            g2.setColor(NOTE_COLOR);

            var middleLineY = ctx.getMiddleLineY();

            // Calculate starting X position (right-aligned with margin)
            float xPos = lineWidth - KEY_CHANGE_RIGHT_MARGIN;

            // Calculate total width needed
            for (var count : accidentalCounts) {
                xPos -= count * KEY_CHANGE_SPACING;
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

                var yPositions = KEY_Y_POSITIONS[keyTypeOrdinal];
                var glyph = isNaturals[kt] ? NATURAL_GLYPH : getGlyphForKeyType(keyTypes[kt]);
                var glyphStr = glyph.asString();

                for (var i = 0; i < accidentalCounts[kt]; i++) {
                    var yPos = yPositions[(i + startingOffsets[kt]) % 7];
                    var y = middleLineY + (int) (yPos * LayoutStylesheet.NOTE_Y_OFFSET);

                    g2.drawString(glyphStr, xPos, y);
                    xPos += KEY_CHANGE_SPACING;
                }
            }
        }
    }

    /**
     * Returns the glyph for the given key type.
     */
    private @NotNull SMuFLGlyph getGlyphForKeyType(@NotNull KeyType keyType) {
        return switch (keyType) {
            case FLATS -> FLAT_GLYPH;
            case SHARPS -> SHARP_GLYPH;
            default -> throw new IllegalArgumentException("No glyph for key type: " + keyType);
        };
    }
}
