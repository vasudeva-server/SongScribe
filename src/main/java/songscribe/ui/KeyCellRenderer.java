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
package songscribe.ui;

import module java.desktop;

import java.util.HashMap;
import java.util.Map;

import songscribe.dom.Key;
import songscribe.dom.KeyType;
import songscribe.error.RuntimeError;
import songscribe.ui.component.BaseLabel;
import songscribe.util.GraphicUtils;
import songscribe.util.MyFontUtils;

/**
 * Renders a key-signature combo or list entry: a MusescoreIcon glyph beside
 * the key's display name.
 */
public class KeyCellRenderer implements ListCellRenderer<Key> {

    private static final float FONT_SIZE_PT = 120f;

    private static final Font FONT = MyFontUtils.getIconFont()
        .deriveFont(FONT_SIZE_PT);

    // MusescoreIcon font glyph per key.
    private static final Map<Key, String> GLYPHS;

    static {
        var glyphs = new HashMap<Key, String>();
        glyphs.put(new Key(KeyType.NONE, 0), "");

        var flatGlyphs = new String[]{
            "", "", "", "", "", "", ""
        };

        for (var i = 0; i < Key.MAX_ACCIDENTAL_COUNT; i++) {
            glyphs.put(new Key(KeyType.FLATS, i + 1), flatGlyphs[i]);
        }

        var sharpGlyphs = new String[]{
            "", "", "", "", "", "", ""
        };

        for (var i = 0; i < Key.MAX_ACCIDENTAL_COUNT; i++) {
            glyphs.put(new Key(KeyType.SHARPS, i + 1), sharpGlyphs[i]);
        }

        GLYPHS = Map.copyOf(glyphs);
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends Key> list,
        Key value,
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
        // FRC, so precompute one per key and reuse across renders. Without
        // this cache, every getListCellRendererComponent call would allocate a
        // GlyphVector and a TextLayout.
        private record CellCache(
            GlyphVector glyphVector,
            Rectangle2D glyphBounds,
            TextLayout labelLayout
        ) {}

        private static final Map<Key, CellCache> CELL_CACHE;

        static {
            var cache = new HashMap<Key, CellCache>();

            var maxGlyphWidth = 0.0;
            var maxGlyphHeight = 0.0;
            var maxLabelWidth = 0;

            for (var key : Key.allSignatures()) {
                var glyph = GLYPHS.get(key);

                if (glyph == null) {
                    // glyph absent from the font => missing font resource, not a bad-key bug
                    throw RuntimeError.missingResource(
                        "Missing glyph for key: " + key
                    );
                }

                var glyphVector = FONT.createGlyphVector(GraphicUtils.SCREEN_FRC, glyph);
                var visualBounds = glyphVector.getVisualBounds();
                maxGlyphWidth = Math.max(maxGlyphWidth, visualBounds.getWidth());
                maxGlyphHeight = Math.max(maxGlyphHeight, visualBounds.getHeight());

                var attributed = KeyDisplay.getDisplayName(key);
                var textLayout = new TextLayout(
                    attributed.getIterator(),
                    GraphicUtils.SCREEN_FRC
                );
                maxLabelWidth = Math.max(
                    maxLabelWidth,
                    (int) Math.ceil(textLayout.getAdvance())
                );

                cache.put(key, new CellCache(glyphVector, visualBounds, textLayout));
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
            Key key,
            JList<?> list,
            int index,
            boolean isSelected
        ) {
            super("", list, index, isSelected);
            setPreferredSize(CELL_SIZE);
            setBackground(isSelected ? list.getSelectionBackground() : Color.WHITE);
            setForeground(isSelected ? list.getSelectionForeground() : Color.BLACK);

            var cache = CELL_CACHE.get(key);

            if (cache == null) {
                throw RuntimeError.exit(
                    "No render cache for key: " + key
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
