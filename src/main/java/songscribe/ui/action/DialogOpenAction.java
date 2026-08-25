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
 * An action that opens a dialog.
 *
 * <p>A fresh dialog is built for every opening via a {@code dialogFactory} function. The factory
 * accepts the {@link MainFrame} and returns a new dialog instance. Passing a constructor
 * reference ({@code MyDialog::new}) against a {@code (MainFrame)} constructor gives a
 * compiler-checked factory contract: the dialog class must expose a {@code (MainFrame)}
 * constructor, which is verified at compile time rather than at runtime via reflection.
 *
 * <p>Building per opening is what lets a dialog be disposed on close — an action, a listener
 * or a tab that a dialog owns is released when the window goes away instead of handling
 * messages for the rest of the run on behalf of a window nobody has open. See
 * {@link BaseDialog}, which states both that rule and the one {@link #open()} keeps.
 */
public class DialogOpenAction<T extends BaseDialog> extends UIAction {

    /**
     * The dialog this action built, held only for as long as it is on screen so a repeat
     * invocation can surface it instead of building a second window. See {@link #open()}.
     */
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
     * Shows this action's dialog, building it first — unless the dialog this action built
     * last is still on screen, in which case it comes to the front and no second window is
     * built.
     * <p>
     * That check is what makes this, rather than {@link #newDialog()}, the entry point every
     * caller uses. A modal dialog cannot reach it twice, because its {@code setVisible(true)}
     * blocks until it closes; a non-modal one returns at once, and without the check a second
     * invocation would put a second window beside the first — {@code PreferencesDialog} is
     * reached both from the menu action and from {@code MainFrame.handlePrefs()}.
     */
    public void open() {
        open(dialog -> dialog.setVisible(true));
    }

    /**
     * Shows this action's dialog through {@code show} rather than through a plain
     * {@code setVisible}, so a subclass offering a dialog-specific opening — landing on a
     * particular tab, say — keeps the already-open check, the tracking and the release that
     * {@link #open()} performs, instead of building and showing a window of its own.
     * <p>
     * {@code show} is the only step that varies. It receives a dialog that has never been
     * shown, and must put it on screen; it must not retain it, since a dialog serves one
     * opening and is disposed on close.
     *
     * @param show puts the freshly built dialog on screen
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
     * Builds a new dialog. Reachable only through {@link #open(Consumer)}, which shows it and
     * drops it: a dialog serves one opening and is disposed on close, so nothing may hold one
     * past that.
     *
     * @return a dialog that has never been shown
     */
    protected T newDialog() {
        return dialogFactory.apply(getMainFrame());
    }
}
