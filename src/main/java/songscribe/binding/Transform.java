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

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The pair of conversions a two-way binding needs between values of different types.
 *
 * <p>The functions must round-trip: {@code backward(forward(a))} equals {@code a}, and
 * likewise from the other side. Otherwise the control appears to edit itself.
 */
public record Transform<A extends @Nullable Object, B extends @Nullable Object>(
    Function<A, B> forward,
    Function<B, A> backward
) {}
