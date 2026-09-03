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

/**
 * Preview-section layout helpers shared by more than one {@link SongSettingsDialog} tab.
 * <p>
 * These are Swing composition units rather than the pure-logic helpers {@link SongSettingsDialog}
 * hosts, so they live here instead of adding layout code back to the dialog the tabs were split
 * out of.
 */
final class SongSettingsLayout {

    private SongSettingsLayout() {}

    /**
     * Builds a "Preview" section wrapping the given live-preview widget, with the
     * widget's own background bleeding into a top/bottom matte border. Shared by
     * the title and attribution tabs.
     */
    static JPanel createPreviewSection(JComponent preview) {
        var section = new BaseDialog.TitledSection(
            Strings.get(Strings.DIALOG_SONG_SETTINGS_SECTION_PREVIEW)
        );
        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);
        section.add(createPreviewWrapper(preview, new Insets(gap, 0, gap, 0)));
        return section;
    }

    /**
     * Wraps {@code preview} in a panel bordered by the preview's own background
     * color, so the background bleeds into the {@code border} margins. Shared by
     * the title/attribution preview sections and the Fonts tab's font previews.
     */
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
     * A page-colored row carrying one preview, centered within it, through a zero-gap
     * {@link FlowLayout} that keeps the preview at its preferred size.
     * <p>
     * Shared by the title and attribution tabs. The title tab's previews wrap at the
     * song's line width, so its rows are {@link SongSettingsTitleTab.FixedWidthPreviewRow},
     * which pins the row to that width; the attribution block has no page-line-width
     * counterpart, so its row is this class as is, sized to its own content.
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
