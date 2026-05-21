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

import module java.desktop;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.BaseDialog;

/**
 * An action that opens a dialog.
 */
public class DialogOpenAction<T extends BaseDialog> extends UIAction {

    private static final Logger LOG = LoggerFactory.getLogger(DialogOpenAction.class);

    private @Nullable T dialog = null;
    private final Class<? extends T> dialogClass;

    public DialogOpenAction(MainFrame mainFrame, String name, Class<? extends T> dialogClass, Flag... flags) {
        this(mainFrame, name, 0, 0, dialogClass, flags);
    }

    public DialogOpenAction(
        MainFrame mainFrame,
        String name,
        int virtualKey,
        int modifiers,
        Class<? extends T> dialogClass,
        Flag... flags
    ) {
        super(mainFrame, name, toKebabCase(name), virtualKey, modifiers, flags);
        this.dialogClass = dialogClass;
        setFlags(Flag.OPENS_DIALOG);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var d = getDialog();

        if (d != null) {
            d.setVisible(true);
        }
    }

    public @Nullable T getDialog() {
        if (dialog == null) {
            try {
                dialog = dialogClass.getConstructor().newInstance();
            } catch (Exception error) {
                LOG.error("Error creating dialog", error);
            }
        }

        return dialog;
    }
}
