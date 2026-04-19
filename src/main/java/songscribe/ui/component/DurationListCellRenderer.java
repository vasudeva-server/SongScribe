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
package songscribe.ui.component;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.music.Duration;
import songscribe.smufl.SMuFLGlyph;
import songscribe.ui.renderer.BaseElementRenderer;

/**
 * A {@link ListCellRenderer} for {@link Duration} values that paints SMuFL music glyphs.
 * Use {@link #createCombo(Duration[])} to build a fully configured combo box.
 */
public final class DurationListCellRenderer implements ListCellRenderer<Object> {

    private static final Font FONT = BaseElementRenderer.getMusicFont().deriveFont(24f);
    private static final int VERTICAL_PADDING_PX = 8;
    private static final Dimension CELL_SIZE;
    private static final int BASELINE_Y_PX;

    static {
        var frc = new FontRenderContext(null, false, false);
        var glyphVector = FONT.createGlyphVector(frc, SMuFLGlyph.NOTE_QUARTER_UP.asString());
        var bounds = glyphVector.getVisualBounds();
        var cellWidth = (int) Math.ceil(bounds.getWidth()) * 2;
        var cellHeight = (int) Math.ceil(bounds.getHeight()) + VERTICAL_PADDING_PX * 2;
        CELL_SIZE = new Dimension(cellWidth, cellHeight);
        BASELINE_Y_PX = VERTICAL_PADDING_PX + (int) Math.ceil(-bounds.getMinY());
    }

    @Nullable
    private NoteLabel cell;

    private static SMuFLGlyph noteGlyphFor(Duration duration) {
        return switch (duration) {
            case SEMI_BREVE -> SMuFLGlyph.NOTE_WHOLE;
            case MINIM, MINIM_DOTTED -> SMuFLGlyph.NOTE_HALF_UP;
            case CROTCHET, CROTCHET_DOTTED -> SMuFLGlyph.NOTE_QUARTER_UP;
            case QUAVER, QUAVER_DOTTED -> SMuFLGlyph.NOTE_8TH_UP;
        };
    }

    public static JComboBox<Duration> createCombo(Duration[] values) {
        var combo = new JComboBox<>(values);
        combo.setRenderer(new DurationListCellRenderer());
        combo.setEditable(false);
        return combo;
    }

    @Override
    public Component getListCellRendererComponent(
        JList list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
        var duration = (Duration) value;

        if (cell == null) {
            cell = new NoteLabel(duration, list, index, isSelected);
        } else {
            cell.configure(duration, list, index, isSelected);
        }

        return cell;
    }

    private static final class NoteLabel extends BaseLabel {

        private static final String DOT = SMuFLGlyph.AUGMENTATION_DOT.asString();
        private static final int DOT_OFFSET = 3;
        private static final int EIGHTH_DOT_OFFSET = 1;

        private Duration duration;

        private NoteLabel(
            Duration duration,
            JList<?> list,
            int index,
            boolean isSelected
        ) {
            super(noteGlyphFor(duration).asString(), list, index, isSelected);
            this.duration = duration;
            setPreferredSize(CELL_SIZE);
            setMinimumSize(CELL_SIZE);
        }

        private void configure(Duration duration, JList<?> list, int index, boolean isSelected) {
            this.duration = duration;
            setText(noteGlyphFor(duration).asString());

            if (index == -1) {
                setBackground(list.getBackground());
            } else {
                setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            }

            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setFont(FONT);

            var text = getText();
            var x = getWidth() / 2;
            var y = (duration == Duration.SEMI_BREVE) ? (getHeight() / 2) : BASELINE_Y_PX;

            g.drawString(text, x, y);

            if (duration.getNote().getDotCount() > 0) {
                var metrics = g.getFontMetrics();
                var width = metrics.stringWidth(text);

                var dotOffset = (duration == Duration.QUAVER_DOTTED)
                    ? EIGHTH_DOT_OFFSET
                    : DOT_OFFSET;

                g.drawString(DOT, x + width + dotOffset, y);
            }
        }
    }
}
