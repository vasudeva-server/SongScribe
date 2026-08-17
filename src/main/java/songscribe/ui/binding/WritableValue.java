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
 * A sink: something the framework can write, and nothing else.
 *
 * <p>A {@code WritableValue} has no readable current value and no notification, so
 * it can only ever be a bind <i>target</i>. That is a promise about the underlying
 * storage, not a restriction imposed for tidiness: the presentation state a dialog
 * computes — a component's enabled state, its font, a preview rendering — is
 * write-only in Swing itself. There is no notification behind any of it, so a
 * binding that took one as a source would be a binding that silently never runs.
 * Typing them this way is what makes the compiler refuse that edge.
 *
 * <p>The adapters over such state return this type for exactly that reason. A
 * control the user also edits has a notification route and is typed
 * {@link Property} instead.
 *
 * <p>Because there is no way to read one, a binding into a {@code WritableValue}
 * cannot compare the value it is about to write against what the target currently
 * holds; it compares against the value it last wrote. See
 * {@link Bindings#bind(WritableValue, ObservableValue)}.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 *
 * @param <T> the written value's type
 */
public interface WritableValue<T> {

    /**
     * Writes {@code value} into the underlying storage.
     *
     * <p>An implementation must be tolerant of being given the value it already
     * holds: a write is not required to be a change, and callers do not check.
     * Whether the write is observable at all is up to the storage — writing a Swing
     * control's enabled state or font has an immediate visual effect, while writing
     * a plain holder has none until something reads it.
     *
     * @param value the value to write, never {@code null}
     * @effects replaces what the storage holds. When the storage is a Swing control
     *     this repaints it, and — for the control adapters that are also
     *     {@link Property} — fires that control's own notification route, which is
     *     what a binding's re-entrancy guard exists to absorb.
     */
    void set(T value);
}
