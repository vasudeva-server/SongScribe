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
 * Names the key a change establishes: one combo over the fifteen key signatures, opened on the key
 * already in effect where the change is bound.
 *
 * <p><b>OK stays enabled throughout</b>, including on the key it opened on. Committing that key
 * would write a change that changes nothing, and what refuses it is
 * {@link DialogController#dataWasModified} — asked before anything is judged or written, rather
 * than a disabled button — so pressing OK on an untouched dialog closes it and writes nothing.
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

    /** The notator's current choice, which is what a commit is measured against. */
    private final Property<Key> selectedKey;

    public KeyChangeDialog(MainFrame mainFrame, DialogOps<Key, Key> ops) {
        super(mainFrame, Strings.get(Strings.DIALOG_KEY_CHANGE_TITLE), ops);

        // The fifteen key signatures are the whole domain and never vary by gesture, so the model
        // is fixed here and populate() only chooses within it.
        var keysCombo = new JComboBox<>(Key.allSignatures().toArray(Key[]::new));
        selectedKey = Controls.item(keysCombo);

        addLabeledField(contentPanel, Strings.get(Strings.LABEL_KEY_SELECT_PROMPT), keysCombo, LabelPosition.TOP);
        keysCombo.setRenderer(new KeyCellRenderer());
        UIUtils.forceLightModeCombo(keysCombo);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The combo opens on the key already in effect, so a dialog the notator leaves alone
     * gathers that key and the commit refuses it.
     *
     * @param values the key in effect where the change is bound
     */
    @Override
    protected void populate(Key values) {
        selectedKey.set(values);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The combo is built over the whole of {@link Key#allSignatures()}, so its model is never
     * empty and a non-empty combo always carries a selection — which is what lets the property
     * answer a {@link Key} rather than something possibly absent.
     *
     * @return the key the notator chose, which is the key in effect until they choose another
     */
    @Override
    protected Key gather() {
        return selectedKey.get();
    }
}
