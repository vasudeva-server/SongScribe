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

import java.util.Objects;
import javax.swing.JComboBox;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Key;
import songscribe.error.RuntimeError;
import songscribe.ui.KeyCellRenderer;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Names the key a change establishes: one combo over the fifteen key signatures, opened on the key
 * already in effect where the change is bound.
 *
 * <p><b>OK stays disabled until the notator picks a different key</b>, so this dialog commits a
 * change or nothing. The entry it opens on is the one entry OK refuses; choosing it is choosing
 * nothing, which is what Cancel is for.
 *
 * <p><b>It cannot tell the four key-editing gestures apart, and does not need to.</b> A line's own
 * key and a key signature standing in the middle of a line are one {@link Key} in and one
 * {@link Key} out either way. Which line, which position, whether the change fits and which route
 * writes it all belong to {@link KeyChangeDialogController}, the only thing that opens this window.
 *
 * <p>There is no entry meaning "inherit the previous line's key". Whether a line holds a key of its
 * own or inherits one is storage — {@link songscribe.dom.Line#setKey} normalizes a key equal to the
 * inherited one back to inheritance on its own — and the notator sees only a key signature on the
 * score, never the distinction.
 */
public class KeyChangeDialog extends StandardDialog<Key, Key> {

    /**
     * Fixed at construction: the fifteen key signatures are the whole domain and never vary by
     * gesture, so the model outlives any one opening and {@link #populate} only chooses within it.
     */
    private final JComboBox<Key> keysCombo = new JComboBox<>(Key.allSignatures().toArray(Key[]::new));

    /**
     * The key the combo opened on, which is what OK is measured against. Null only before the first
     * {@link #populate}, which happens before the window is ever on screen.
     */
    private @Nullable Key keyInEffect = null;

    public KeyChangeDialog(MainFrame mainFrame, DialogOps<Key, Key> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_KEY_CHANGE_TITLE), ops);

        addLabeledField(contentPanel, Strings.get(Strings.LABEL_KEY_SELECT_PROMPT), keysCombo, LabelPosition.TOP);
        keysCombo.setRenderer(new KeyCellRenderer());
        keysCombo.addItemListener(_ -> okButton.setEnabled(isChangeFromCurrent()));
        UIUtils.forceLightModeCombo(keysCombo);
        okButton.setEnabled(false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Selecting the key in effect fires the item listener, which reads what OK is measured
     * against — so that is recorded first. OK is disabled afterwards regardless, because a combo
     * already showing that key fires nothing at all.
     *
     * @param values the key in effect where the change is bound
     */
    @Override
    protected void populate(Key values) {
        keyInEffect = values;
        keysCombo.setSelectedItem(values);
        okButton.setEnabled(false);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The combo is built over the whole of {@link Key#allSignatures()}, so its model is never
     * empty and a non-empty combo always carries a selection. The guard is there to satisfy
     * the compiler, the call to RuntimeError.exit is theoretically unreachable.
     *
     * @return the key the notator chose
     */
    @Override
    protected Key gather() {
        var key = (Key) keysCombo.getSelectedItem();

        if (key == null) {
            throw RuntimeError.exit("the key combo has no selection");
        }

        return key;
    }

    /**
     * @return {@code true} when the combo names something other than the key already in effect,
     *         which is the whole condition for OK being offered at all
     */
    private boolean isChangeFromCurrent() {
        return !Objects.equals(keysCombo.getSelectedItem(), keyInEffect);
    }
}
