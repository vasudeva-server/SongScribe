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
package songscribe.binding;

import org.jspecify.annotations.Nullable;

/**
 * A value that can be read, observed and written — storage the user edits and the framework
 * also writes. The promises of {@link ObservableValue} and {@link WritableValue} apply unchanged.
 *
 * <p>Do not type a target as {@code Property} for convenience: a target the framework only
 * writes is a {@link WritableValue}, so the compiler refuses it as a source. Widening it
 * compiles a binding that can never fire.
 *
 * <p>EDT-only.
 */
public interface Property<T extends @Nullable Object> extends ObservableValue<T>, WritableValue<T> {}
