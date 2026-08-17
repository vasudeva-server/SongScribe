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

/**
 * A property graph for dialog state: values that can be read and observed, values
 * that can be written, and declarative edges between them.
 *
 * <p>A value here is a <i>view</i> onto storage that already exists — a Swing
 * control, or a plain holder — never a second copy of it. A binding declares
 * <i>target &larr; source</i>, and the framework owns observation, propagation and
 * re-entrancy.
 *
 * <h2>Package invariants</h2>
 *
 * <p><b>Every call in this package happens on the EDT.</b> Nothing here is
 * synchronized and nothing here is thread-safe: the observer lists, the dirty
 * flags, the re-entrancy flags and the dependency sets are all plain mutable state
 * guarded by nothing but that invariant. A call from a background thread can
 * corrupt a dependency set or lose a notification, and no diagnostic reports it.
 *
 * <p><b>Values are replaced, never mutated.</b> An {@link
 * songscribe.ui.binding.ObservableValue} observes <i>replacement</i> of its value
 * and has no way to see a mutation made inside one. A mutable {@code T} whose
 * contents are edited in place therefore notifies nobody, and every binding and
 * {@code computed} that depends on it silently keeps the value it last saw. Use
 * immutable value types, and replace through {@link
 * songscribe.ui.binding.WritableValue#set} rather than editing what {@link
 * songscribe.ui.binding.ObservableValue#get} handed back.
 */
@NullMarked
package songscribe.ui.binding;

import org.jspecify.annotations.NullMarked;
