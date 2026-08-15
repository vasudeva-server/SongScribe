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

package songscribe.ui.dialog.fontchooser.panes;

import java.awt.Dimension;
import java.awt.Font;
import java.util.Objects;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionListener;

import org.jspecify.annotations.Nullable;

import songscribe.ui.dialog.fontchooser.FontFamilies;
import songscribe.ui.dialog.fontchooser.model.FontSelectionModel;

public class StylePane extends JScrollPane implements ChangeListener {

    private final JList<StyleEntry> styleList = new JList<>();

    private final DefaultListModel<StyleEntry> styleListModel;

    private @Nullable String family = null;

    public StylePane() {
        styleListModel = new DefaultListModel<>();
        styleList.setModel(styleListModel);
        styleList
            .getSelectionModel()
            .setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleList.setCellRenderer(new StyleCellRenderer());
        setMinimumSize(new Dimension(160, 100));
        setPreferredSize(new Dimension(200, 100));
        setViewportView(styleList);
    }

    public void addListSelectionListener(ListSelectionListener listener) {
        styleList.addListSelectionListener(listener);
    }

    public void removeListSelectionListener(ListSelectionListener listener) {
        styleList.removeListSelectionListener(listener);
    }

    public void setSelectedStyle(Font font) {
        styleList.setSelectedValue(new StyleEntry(font), true);
    }

    public StyleEntry getSelectedStyle() {
        return styleList.getSelectedValue();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        var fontSelectionModel = (FontSelectionModel) e.getSource();
        var selectedFont = fontSelectionModel.getSelectedFont();
        loadFamily(selectedFont.getFamily());
    }

    public void loadFamily(String family) {
        if (Objects.equals(this.family, family)) {
            return;
        }

        this.family = family;
        var fontFamilies = FontFamilies.getInstance();
        var fontFamily = fontFamilies.get(family);

        if (fontFamily != null) {
            var selectionListeners = styleList.getListSelectionListeners();
            removeSelectionListeners(selectionListeners);
            updateListModel(fontFamily.getStyles());
            addSelectionListeners(selectionListeners);
        }
    }

    private void updateListModel(Iterable<? extends Font> fonts) {
        styleListModel.clear();

        for (var font : fonts) {
            styleListModel.addElement(new StyleEntry(font));
        }
    }

    private void addSelectionListeners(
        ListSelectionListener[] selectionListeners
    ) {
        for (var listener : selectionListeners) {
            styleList.addListSelectionListener(listener);
        }
    }

    private void removeSelectionListeners(
        ListSelectionListener[] selectionListeners
    ) {
        for (var listener : selectionListeners) {
            styleList.removeListSelectionListener(listener);
        }
    }
}
