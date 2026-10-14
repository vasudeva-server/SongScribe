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

import java.awt.event.ActionEvent;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.BaseDialog;

import static songscribe.util.StringUtils.toKebabCase;

/**
 * An action that opens a dialog, built per opening so that it can be disposed on close. See
 * {@link BaseDialog}, which states that rule and the one {@link #open()} keeps.
 */
public class DialogOpenAction<T extends BaseDialog> extends UIAction {

    private @Nullable T openDialog = null;

    private final Function<? super MainFrame, ? extends T> dialogFactory;

    public DialogOpenAction(MainFrame mainFrame, String name, Function<? super MainFrame, ? extends T> dialogFactory, Flag... flags) {
        this(mainFrame, name, 0, 0, dialogFactory, flags);
    }

    public DialogOpenAction(
        MainFrame mainFrame,
        String name,
        int virtualKey,
        int modifiers,
        Function<? super MainFrame, ? extends T> dialogFactory,
        Flag... flags
    ) {
        super(mainFrame, name, toKebabCase(name), virtualKey, modifiers, flags);
        this.dialogFactory = dialogFactory;
        setFlags(Flag.OPENS_DIALOG);
    }

    @Override
    protected void performAction(ActionEvent e) {
        open();
    }

    /**
     * The entry point every caller uses: its already-open check keeps a repeat invocation of a
     * non-modal dialog from putting a second window beside the first.
     */
    public void open() {
        open(dialog -> dialog.setVisible(true));
    }

    /**
     * {@code show} receives a dialog that has never been shown and must put it on screen; it
     * must not retain it, since a dialog serves one opening and is disposed on close.
     */
    protected void open(Consumer<? super T> show) {
        var current = openDialog;

        if (current != null && current.isShowing()) {
            current.toFront();
            return;
        }

        var dialog = newDialog();
        openDialog = dialog;
        show.accept(dialog);

        // A modal show returns only once the window is gone, and a show cancelled by getData()
        // never put one up. Either way the dialog is spent, so let go of it rather than hold a
        // disposed instance until the next opening.
        if (!dialog.isShowing()) {
            openDialog = null;
        }
    }

    /**
     * Reachable only through {@link #open(Consumer)}; nothing may hold a dialog past its opening.
     */
    protected T newDialog() {
        return dialogFactory.apply(getMainFrame());
    }
}
