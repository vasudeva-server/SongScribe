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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

import songscribe.Strings;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;

final class SongSettingsLayout {

    private SongSettingsLayout() {}

    static JPanel createPreviewSection(JComponent preview) {
        var section = new BaseDialog.TitledSection(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_PREVIEW)
        );
        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);
        section.add(createPreviewWrapper(preview, new Insets(gap, 0, gap, 0)));
        return section;
    }

    static JPanel createPreviewWrapper(JComponent preview, Insets border) {
        var backgroundColor = preview.getBackground();
        var previewWrapper = new JPanel();
        previewWrapper.setOpaque(true);
        previewWrapper.setBackground(backgroundColor);
        previewWrapper.setBorder(BorderFactory.createMatteBorder(
            border.top, border.left, border.bottom, border.right, backgroundColor
        ));
        previewWrapper.setLayout(new BorderLayout());
        previewWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewWrapper.add(preview, BorderLayout.CENTER);
        return previewWrapper;
    }

    /**
     * A page-colored row carrying one preview, centered within it. The title tab's rows are
     * {@link SongSettingsTitleTab.FixedWidthPreviewRow}, which pins the row to the song's line
     * width; the attribution row is this class as is, sized to its own content.
     */
    static class PreviewRow extends JPanel {

        PreviewRow(JComponent preview) {
            super(new FlowLayout(FlowLayout.CENTER, 0, 0));
            setOpaque(true);
            setBackground(FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            add(preview);
        }
    }
}
