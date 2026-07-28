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

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.smufl.SMuFLGlyph;
import songscribe.dom.KeySignature;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.engraving.Staff;
import songscribe.util.GraphicsState;

/**
 * Renders key signatures (sharps or flats) at the start of a staff line.
 * <p>
 * The key signature follows the clef and shows the sharps or flats
 * in the order they appear (FCGDAEB for sharps, BEADGCF for flats).
 */
public final class KeySignatureRenderer implements ElementRenderer<KeySignature> {

    // ==========================================================================
    // Accidental Glyphs
    // ==========================================================================

    private static final SMuFLGlyph FLAT_GLYPH = SMuFLGlyph.ACCIDENTAL_FLAT;
    private static final SMuFLGlyph SHARP_GLYPH = SMuFLGlyph.ACCIDENTAL_SHARP;
    private static final SMuFLGlyph NATURAL_GLYPH = SMuFLGlyph.ACCIDENTAL_NATURAL;

    // Staff positions for accidentals, relative to middle line (0 = B4), in accidental order.
    // Flats: B, E, A, D, G, C, F
    static final int[] FLAT_STAFF_POSITIONS = {0, -3, 1, -2, 2, -1, 3};
    // Sharps: F, C, G, D, A, E, B
    static final int[] SHARP_STAFF_POSITIONS = {-4, -1, -5, -2, 1, -3, 0};

    // Y positions indexed by KeyType ordinal (for key change rendering).
    // Index 0 = NONE (empty), Index 1 = FLATS, Index 2 = SHARPS
    private static final int[][] KEY_STAFF_POSITIONS = new int[][]{
        new int[]{}, // NONE
        FLAT_STAFF_POSITIONS,
        SHARP_STAFF_POSITIONS
    };

    // Right margin for key change from line end, in staff-space units.
    // Package-private so KeySignatureRendererTest can assert against it rather than copy it.
    static final double KEY_CHANGE_RIGHT_MARGIN_SS = 0.625;  // 5px / 8 px/ss

    /**
     * One accidental of a key signature, ready to be laid out.
     *
     * @param glyph           the glyph to draw
     * @param staffPositionSp staff position relative to the middle line
     * @param leadingGapSs    extra space in front of this accidental, separating it from
     *                        the group to its left
     */
    private record DrawnAccidental(SMuFLGlyph glyph, int staffPositionSp, double leadingGapSs) {}

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
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        KeySignature element,
        Graphics2D g2
    ) {
        if (!element.hasAccidentals()) {
            return;
        }

        var keyType = element.getKeyType();
        var accidentalCount = element.getAccidentalCount();

        if (keyType == KeyType.NONE || accidentalCount == 0) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, FONT, COLOR)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            g2.setColor(RenderingUtils.ELEMENT_COLOR);

            // Get the starting X position from the element
            var xPosSs = element.getXSs();
            var middleLineYSs = invariants.getMiddleLineYSs();

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

            // Draw each accidental. Sharps and flats need no kerning, so each simply
            // advances by its own glyph width.
            var glyphStr = glyph.asString();
            var advanceSs = StaffHeaderMetrics.accidentalInkBboxSs(glyph);

            for (var i = 0; i < accidentalCount; i++) {
                var staffPosition = staffPositions[i % KeySignature.MAX_ACCIDENTAL_COUNT];
                var y = middleLineYSs + Staff.spToSs(staffPosition);

                g2.drawString(glyphStr, (float) xPosSs, (float) y);
                xPosSs += advanceSs;
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
     * @param invariants       Line invariants
     */
    public void renderKeyChange(
        Graphics2D g2,
        Line line,
        Line nextLine,
        double lineWidth,
        LineInvariants invariants
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
                // Adding more accidentals - show the new key signature without cancellation naturals
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

        renderKeySignatureChange(g2, keyTypes, accidentalCounts, startingOffsets, isNaturals, lineWidth, invariants);
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
     * @param invariants             Line invariants
     */
    private void renderKeySignatureChange(
        Graphics2D g2,
        @Nullable KeyType [] keyTypes,
        int [] accidentalCounts,
        int [] startingOffsets,
        boolean [] isNaturals,
        double lineWidth,
        LineInvariants invariants
    ) {
        var accidentals = collectAccidentals(keyTypes, accidentalCounts, startingOffsets, isNaturals);

        if (accidentals.isEmpty()) {
            return;
        }

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            g2.setColor(RenderingUtils.ELEMENT_COLOR);

            var middleLineYSs = invariants.getMiddleLineYSs();

            // Right-align the whole run against the line end, less the margin.
            var xPosSs = lineWidth - KEY_CHANGE_RIGHT_MARGIN_SS - totalWidthSs(accidentals);

            for (var i = 0; i < accidentals.size(); i++) {
                var accidental = accidentals.get(i);
                var y = middleLineYSs + Staff.spToSs(accidental.staffPositionSp());

                xPosSs += accidental.leadingGapSs();
                g2.drawString(accidental.glyph().asString(), (float) xPosSs, (float) y);
                xPosSs += advanceSs(accidentals, i);
            }
        }
    }

    /**
     * Flattens the key signature groups into the accidentals to draw, left to right,
     * separating consecutive groups by the gap LilyPond gives their order — see
     * {@link StaffHeaderMetrics#CANCELLATION_TO_KEY_GAP_SS} and
     * {@link StaffHeaderMetrics#KEY_TO_CANCELLATION_GAP_SS}.
     */
    private static List<DrawnAccidental> collectAccidentals(
        @Nullable KeyType [] keyTypes,
        int [] accidentalCounts,
        int [] startingOffsets,
        boolean [] isNaturals
    ) {
        var accidentals = new ArrayList<DrawnAccidental>();

        for (var groupIndex = 0; groupIndex < keyTypes.length; groupIndex++) {
            var keyType = keyTypes[groupIndex];

            if (keyType == null) {
                break;
            }

            if (keyType == KeyType.NONE) {
                continue;
            }

            var staffPositions = KEY_STAFF_POSITIONS[keyType.ordinal()];
            var isNatural = isNaturals[groupIndex];
            var glyph = isNatural ? NATURAL_GLYPH : getGlyphForKeyType(keyType);
            // Only the first accidental of a group that follows another group is pushed
            // away; within a group the glyphs nest with no gap. LilyPond's tables give the
            // two possible orders different gaps, so which one applies depends on whether
            // this group is the cancellation or the key signature.
            var groupGapSs = 0.0;

            if (!accidentals.isEmpty()) {
                groupGapSs = isNatural
                    ? StaffHeaderMetrics.KEY_TO_CANCELLATION_GAP_SS
                    : StaffHeaderMetrics.CANCELLATION_TO_KEY_GAP_SS;
            }

            for (var i = 0; i < accidentalCounts[groupIndex]; i++) {
                var staffPosition =
                    staffPositions[(i + startingOffsets[groupIndex]) % KeySignature.MAX_ACCIDENTAL_COUNT];
                accidentals.add(new DrawnAccidental(glyph, staffPosition, i == 0 ? groupGapSs : 0));
            }
        }

        return accidentals;
    }

    /**
     * Returns how far the pen advances after drawing the accidental at {@code index},
     * including the kerning a natural needs to clear the accidental to its right.
     * <p>
     * Kerning stays within a group: LilyPond computes it per key signature object, and
     * the gap that separates two groups already holds them apart.
     */
    private static double advanceSs(List<DrawnAccidental> accidentals, int index) {
        var accidental = accidentals.get(index);
        var advanceSs = StaffHeaderMetrics.accidentalInkBboxSs(accidental.glyph());
        var isLast = index == accidentals.size() - 1;

        if (isLast || accidental.glyph() != NATURAL_GLYPH) {
            return advanceSs;
        }

        var next = accidentals.get(index + 1);

        if (next.leadingGapSs() != 0) {
            return advanceSs;
        }

        return advanceSs
            + StaffHeaderMetrics.naturalKerningSs(accidental.staffPositionSp(), next.staffPositionSp());
    }

    /** Returns the total width of the laid-out accidentals, in staff-space units. */
    private static double totalWidthSs(List<DrawnAccidental> accidentals) {
        var widthSs = 0.0;

        for (var i = 0; i < accidentals.size(); i++) {
            widthSs += accidentals.get(i).leadingGapSs() + advanceSs(accidentals, i);
        }

        return widthSs;
    }

    /**
     * Returns the glyph for the given key type.
     */
    static SMuFLGlyph getGlyphForKeyType(KeyType keyType) {
        return switch (keyType) {
            case FLATS -> FLAT_GLYPH;
            case SHARPS -> SHARP_GLYPH;
            default -> throw new IllegalArgumentException("No glyph for key type: " + keyType);
        };
    }
}
