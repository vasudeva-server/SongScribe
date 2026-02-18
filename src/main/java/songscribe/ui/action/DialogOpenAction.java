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
package songscribe.ui.action;

import static songscribe.util.StringUtils.toKebabCase;

import java.awt.event.*;

import songscribe.ui.dialog.StandardDialog;
import songscribe.util.Log;

/**
 * An action that opens a standard dialog.
 */
public class DialogOpenAction<T extends StandardDialog> extends UIAction {

    private T dialog = null;
    private final Class<? extends T> dialogClass;

    public DialogOpenAction(String name, Class<? extends T> dialogClass) {
        this(name, 0, 0, dialogClass);
    }

    public DialogOpenAction(
        String name,
        int virtualKey,
        int modifiers,
        Class<? extends T> dialogClass
    ) {
        super(name, toKebabCase(name), virtualKey, modifiers);
        this.dialogClass = dialogClass;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        getDialog().setVisible(true);
    }

    public T getDialog() {
        if (dialog == null) {
            try {
                dialog = dialogClass.getConstructor().newInstance();
            } catch (Exception error) {
                Log.error("Error creating dialog", error);
            }
        }

        return dialog;
    }
}
