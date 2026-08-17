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
 * A value that can be read, observed and written — the two-way case.
 *
 * <p>This is the shape of storage the user edits and the framework also writes: a
 * text field, a checkbox, a combo box, a spinner. Both directions are real, so both
 * capabilities are declared, and a {@code Property} may stand on either end of a
 * binding.
 *
 * <p>It adds no members of its own. The whole of it is the conjunction of
 * {@link ObservableValue} and {@link WritableValue}, and the promises of
 * {@link ObservableValue#get}, {@link ObservableValue#observe} and
 * {@link WritableValue#set} apply here unchanged — including the requirement that
 * {@code get} register the read with the dependency tracker.
 *
 * <p><b>Do not type a target as {@code Property} for convenience.</b> A target the
 * framework only ever writes is declared {@link WritableValue}, so the compiler
 * refuses it as a source. Widening it to {@code Property} makes a binding that can
 * never fire compile, and nothing at runtime reports one.
 *
 * <p>A write is expected to fire the underlying control's notification, so
 * {@code set} on a {@code Property} generally causes {@code observe} actions to
 * run. Bindings absorb the write they caused themselves; see
 * {@link Bindings#bindBidirectional(Property, Property)}.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 *
 * @param <T> the value's type
 */
public interface Property<T> extends ObservableValue<T>, WritableValue<T> {}
