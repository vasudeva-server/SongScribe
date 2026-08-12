/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog;

import module java.desktop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import songscribe.error.RuntimeError;
import songscribe.dom.KeySignature;
import songscribe.dom.KeyType;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.KeySignatureDisplay;
import songscribe.ui.component.BaseLabel;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Renders the {@link SongSettingsDialog} Music tab's key-signature combo: a
 * MusescoreIcon glyph beside the key's display name.
 */
public class SongSettingsKeyCellRenderer implements ListCellRenderer<KeySelection> {

    private static final float FONT_SIZE_PT = 120f;

    private static final Font FONT = MyFontUtils.getIconFont()
        .deriveFont(FONT_SIZE_PT);

    /**
     * All key-signature selections in display order:
     * no accidentals, then 1-7 flats, then 1-7 sharps.
     */
    public static final List<KeySelection> SELECTIONS;

    // MusescoreIcon font glyph per selection.
    private static final Map<KeySelection, String> GLYPHS;

    static {
        var selections = new ArrayList<KeySelection>(1 + 2 * KeySignature.MAX_ACCIDENTAL_COUNT);
        var glyphs = new HashMap<KeySelection, String>();

        var noAccidentals = new KeySelection(KeyType.FLATS, 0);
        selections.add(noAccidentals);
        glyphs.put(noAccidentals, "\uF377");

        var flatGlyphs = new String[]{
            "\uF37F", "\uF380", "\uF381", "\uF382", "\uF383", "\uF384", "\uF385"
        };

        for (var i = 0; i < KeySignature.MAX_ACCIDENTAL_COUNT; i++) {
            var flatSelection = new KeySelection(KeyType.FLATS, i + 1);
            selections.add(flatSelection);
            glyphs.put(flatSelection, flatGlyphs[i]);
        }

        var sharpGlyphs = new String[]{
            "\uF378", "\uF379", "\uF37A", "\uF37B", "\uF37C", "\uF37D", "\uF37E"
        };

        for (var i = 0; i < KeySignature.MAX_ACCIDENTAL_COUNT; i++) {
            var sharpSelection = new KeySelection(KeyType.SHARPS, i + 1);
            selections.add(sharpSelection);
            glyphs.put(sharpSelection, sharpGlyphs[i]);
        }

        SELECTIONS = List.copyOf(selections);
        GLYPHS = Map.copyOf(glyphs);
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends KeySelection> list,
        KeySelection value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
        return new KeyLabel(value, list, index, isSelected);
    }

    private static final class KeyLabel extends BaseLabel {

        private static final int CELL_PADDING_Y_PX = 10;
        private static final int GLYPH_BOX_WIDTH_PX;
        private static final int GLYPH_BOX_HEIGHT_PX;
        private static final int LABEL_GAP_PX;
        private static final int LABEL_WIDTH_PX;
        private static final Dimension CELL_SIZE;

        // GlyphVector and TextLayout are immutable once constructed for a fixed
        // FRC, so precompute one per selection and reuse across renders. Without
        // this cache, every getListCellRendererComponent call would allocate a
        // GlyphVector and a TextLayout.
        private record CellCache(
            GlyphVector glyphVector,
            Rectangle2D glyphBounds,
            TextLayout labelLayout
        ) {}

        private static final Map<KeySelection, CellCache> CELL_CACHE;

        static {
            var cache = new HashMap<KeySelection, CellCache>();

            var maxGlyphWidth = 0.0;
            var maxGlyphHeight = 0.0;
            var maxLabelWidth = 0;

            for (var selection : SELECTIONS) {
                var glyph = GLYPHS.get(selection);

                if (glyph == null) {
                    // glyph absent from the font => missing font resource, not a bad-selection bug
                    throw RuntimeError.missingResource(
                        "Missing glyph for key selection: " + selection
                    );
                }

                var glyphVector = FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, glyph);
                var visualBounds = glyphVector.getVisualBounds();
                maxGlyphWidth = Math.max(maxGlyphWidth, visualBounds.getWidth());
                maxGlyphHeight = Math.max(maxGlyphHeight, visualBounds.getHeight());

                var attributed = KeySignatureDisplay.getDisplayName(
                    selection.keyType(),
                    selection.count()
                );
                var textLayout = new TextLayout(
                    attributed.getIterator(),
                    GraphicUtils.SCREEN_FRC
                );
                maxLabelWidth = Math.max(
                    maxLabelWidth,
                    (int) Math.ceil(textLayout.getAdvance())
                );

                cache.put(selection, new CellCache(glyphVector, visualBounds, textLayout));
            }

            CELL_CACHE = Map.copyOf(cache);

            // Font metrics for icon fonts include large built-in whitespace that
            // doesn't reflect the actual ink bounds. Use visual bounds so the cell
            // tightly wraps the rendered image.
            GLYPH_BOX_WIDTH_PX = (int) Math.ceil(maxGlyphWidth);
            GLYPH_BOX_HEIGHT_PX = (int) Math.ceil(maxGlyphHeight);
            LABEL_GAP_PX = FlatLafProps.getInt(
                FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP
            );
            LABEL_WIDTH_PX = maxLabelWidth;

            var cellWidth = GLYPH_BOX_WIDTH_PX + LABEL_GAP_PX + LABEL_WIDTH_PX;
            var cellHeight = GLYPH_BOX_HEIGHT_PX + 2 * CELL_PADDING_Y_PX;
            CELL_SIZE = new Dimension(cellWidth, cellHeight);
        }

        private final GlyphVector glyphVector;
        private final Rectangle2D glyphBounds;
        private final TextLayout labelLayout;

        private KeyLabel(
            KeySelection selection,
            JList<?> list,
            int index,
            boolean isSelected
        ) {
            super("", list, index, isSelected);
            setPreferredSize(CELL_SIZE);
            setBackground(isSelected ? list.getSelectionBackground() : Color.WHITE);
            setForeground(isSelected ? list.getSelectionForeground() : Color.BLACK);

            var cache = CELL_CACHE.get(selection);

            if (cache == null) {
                throw RuntimeError.exit(
                    "No render cache for key selection: " + selection
                );
            }

            glyphVector = cache.glyphVector();
            glyphBounds = cache.glyphBounds();
            labelLayout = cache.labelLayout();
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            var g2 = (Graphics2D) g;
            // Use the glyph's visual bounds (not advance width) for centering, since
            // the MusescoreIcon font has a large advance width unrelated to ink size.
            var contentXOffset = Math.max(0, (getWidth() - CELL_SIZE.width) / 2);
            var glyphX = (float) (contentXOffset
                + (GLYPH_BOX_WIDTH_PX - glyphBounds.getWidth()) / 2
                - glyphBounds.getX());
            var glyphY = (float) (CELL_PADDING_Y_PX - glyphBounds.getY());
            g2.drawGlyphVector(glyphVector, glyphX, glyphY);

            var labelX = (float) (contentXOffset + GLYPH_BOX_WIDTH_PX + LABEL_GAP_PX);
            var labelY = (getHeight() + labelLayout.getAscent()
                - labelLayout.getDescent()) / 2f;
            labelLayout.draw(g2, labelX, labelY);
        }
    }
}
