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
package songscribe.ui.binding;

/**
 * When a text-bearing control's property notifies.
 *
 * <p>Swing offers text controls several unrelated notification routes with
 * different semantics, and the choice is not a detail a caller can be spared: the
 * dialogs this framework serves deliberately use more than one, because a preview
 * that has to track what is being typed and a field whose value is only meaningful
 * once entry has finished are different requirements.
 *
 * <p>This is a parameter of the text-control factories only. It means nothing for a
 * checkbox, a combo box or a spinner, and it never appears on a binding — a
 * binding's timing is the timing of whatever source it was given.
 */
public enum Timing {
    /** Notifies on every keystroke, as the text changes. */
    WHILE_TYPING,

    /** Notifies once entry has finished, when the control loses focus. */
    ON_COMMIT
}
