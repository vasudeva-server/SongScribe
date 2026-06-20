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

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.BaseDialog;

/**
 * An action that opens a dialog.
 *
 * <p>The dialog is created lazily on first use via a {@code dialogFactory} function. The factory
 * accepts the {@link MainFrame} and returns a fresh dialog instance. Passing a constructor
 * reference ({@code MyDialog::new}) against a {@code (MainFrame)} constructor gives a
 * compiler-checked factory contract: the dialog class must expose a {@code (MainFrame)}
 * constructor, which is verified at compile time rather than at runtime via reflection.
 */
public class DialogOpenAction<T extends BaseDialog> extends UIAction {

    private @Nullable T dialog = null;
    private final Function<MainFrame, T> dialogFactory;

    public DialogOpenAction(MainFrame mainFrame, String name, Function<MainFrame, T> dialogFactory, Flag... flags) {
        this(mainFrame, name, 0, 0, dialogFactory, flags);
    }

    public DialogOpenAction(
        MainFrame mainFrame,
        String name,
        int virtualKey,
        int modifiers,
        Function<MainFrame, T> dialogFactory,
        Flag... flags
    ) {
        super(mainFrame, name, toKebabCase(name), virtualKey, modifiers, flags);
        this.dialogFactory = dialogFactory;
        setFlags(Flag.OPENS_DIALOG);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        getDialog().setVisible(true);
    }

    public T getDialog() {
        if (dialog == null) {
            dialog = dialogFactory.apply(getMainFrame());
        }

        return dialog;
    }
}
