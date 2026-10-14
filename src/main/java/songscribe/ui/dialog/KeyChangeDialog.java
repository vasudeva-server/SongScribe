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

import javax.swing.JComboBox;

import songscribe.Strings;
import songscribe.dom.Key;
import songscribe.ui.KeyCellRenderer;
import songscribe.ui.binding.Controls;
import songscribe.binding.Property;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Names the key a change establishes: one combo over the fifteen keys, opened on the key
 * already in effect where the change is bound.
 *
 * <p><b>OK stays enabled throughout</b>, including on the key it opened on: pressing it on an
 * untouched dialog closes it and writes nothing, because {@link DialogController#dataWasModified}
 * refuses.
 *
 * <p>It cannot tell the four key-editing gestures apart; that belongs to
 * {@link KeyChangeDialogController}.
 *
 * <p>There is no entry meaning "inherit the previous line's key": {@link songscribe.dom.Line#setKey}
 * normalizes a key equal to the inherited one back to inheritance on its own.
 */
public class KeyChangeDialog extends StandardDialog<Key, Key> {

    private final Property<Key> selectedKey;

    public KeyChangeDialog(MainFrame mainFrame, DialogOps<Key, Key> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_KEY_CHANGE_TITLE), ops);

        // The fifteen keys are the whole domain and never vary by gesture, so the model
        // is fixed here and populate() only chooses within it.
        var keysCombo = new JComboBox<>(Key.allKeys().toArray(Key[]::new));
        selectedKey = Controls.item(keysCombo);

        addLabeledField(contentPanel, Strings.get(Strings.LABEL_KEY_SELECT_PROMPT), keysCombo, LabelPosition.TOP);
        keysCombo.setRenderer(new KeyCellRenderer());
        UIUtils.forceLightModeCombo(keysCombo);

        // A key cell is a staff-height glyph, so eight of them — Swing's fixed default — overflow
        // a short display and under-fill a tall one.
        UIUtils.fitPopupToScreen(keysCombo, KeyCellRenderer.cellHeightPx());
    }

    /**
     * The combo opens on the key already in effect, so an untouched dialog gathers that key.
     */
    @Override
    protected void populate(Key values) {
        selectedKey.set(values);
    }

    /**
     * The combo always carries a selection, so this never answers an absent {@link Key}.
     */
    @Override
    protected Key gather() {
        return selectedKey.get();
    }
}
