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
package songscribe.ui.dialog;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import songscribe.util.Copyable;

/**
 * Everything a dialog may ask of the world outside itself.
 *
 * <p><strong>Function references rather than an interface, on purpose:</strong> a dialog holding
 * an object could ask for whatever its type exposes, including what the next person adds. These
 * references bound its reach to this record's components, with no receiver behind them.
 *
 * <p><strong>Assembled by {@link DialogController#ops()} and nowhere else</strong>, so a bundle
 * carries one controller's operations and is never partial.
 *
 * @param <I>      what the dialog shows. Its bound permits {@code @Nullable} itself, for a family
 *                 like {@code AttachmentDialog} whose input is absent on Add
 * @param <O>      what the dialog's controls say on OK. Its bound permits {@code @Nullable}
 *                 itself, for a dialog that gathers nothing and names {@code @Nullable Void}
 * @param read     asked afresh on each opening; what it answers is already a copy
 * @param wasModified asked first on OK, before {@code validate}
 * @param commit      reached only with values {@code wasModified} and {@code validate} both
 *                    accepted
 * @param remove      {@code null} when this dialog offers no Remove, in which case no Remove
 *                    button is built at all
 */
public record DialogOps<I extends @Nullable Copyable<I>, O extends @Nullable Object>(
    Supplier<I> read,
    Predicate<O> wasModified,
    Function<O, ValidationResult> validate,
    Consumer<O> commit,
    @Nullable Runnable remove
) {}
