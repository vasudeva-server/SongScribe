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

/**
 * The pair of conversions a two-way binding needs between values of different
 * types.
 *
 * <p>A record rather than two parameters at the bind site: {@code Function<A,B>}
 * and {@code Function<B,A>} are transposable at a lambda call site without the
 * compiler objecting whenever {@code A} and {@code B} are inferred, and a
 * transposed pair produces a binding that converts in the wrong direction on both
 * sides. Naming the two halves is what makes the mistake visible.
 *
 * <p>The two functions are expected to round-trip: {@code backward(forward(a))}
 * should equal {@code a}, and likewise from the other side. A pair that does not
 * leaves a two-way binding writing a value back that differs from the one the user
 * entered, which reads as the control editing itself.
 *
 * @param <A> the first side's type
 * @param <B> the second side's type
 * @param forward converts a value of the first side to the second
 * @param backward converts a value of the second side to the first
 */
public record Transform<A, B>(Function<A, B> forward, Function<B, A> backward) {}
