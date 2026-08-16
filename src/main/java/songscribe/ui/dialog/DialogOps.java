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
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Everything a dialog may ask of the world outside itself.
 *
 * <p><strong>Function references rather than an interface, on purpose.</strong> An interface —
 * however narrowly drawn — is an object the dialog holds, and an object can be asked for whatever
 * its type exposes, including whatever the next person adds to that type. A dialog holding four
 * references can make four calls and can never make a fifth, so its reach is bounded by this
 * record's components rather than by a reviewer noticing. There is no receiver behind them that
 * the dialog can name, store or pass on: the controller that supplied them is not reachable from
 * here.
 *
 * <p><strong>Assembled by {@link DialogController#ops()} and nowhere else</strong>, so a bundle
 * always carries one controller's four operations rather than a mixture of several, and no dialog
 * is handed a partial one.
 *
 * <p>Whether Remove is offered is fixed when the bundle is assembled and never re-asked, so a
 * controller whose answer depends on the document is constructed for the gesture it serves rather
 * than cached across gestures. See {@link DialogController#removal()}.
 *
 * @param <I>      what the dialog shows: values only, never a handle on the document. Its bound
 *                 permits {@code @Nullable} itself, for a family like {@code AttachmentDialog}
 *                 whose input is absent on Add
 * @param <O>      what the dialog's controls say on OK: values only, likewise
 * @param read     answers what to show, asked afresh on each opening rather than once at
 *                 construction, so a dialog reached from a cached action shows the document that
 *                 is open now
 * @param validate decides whether gathered values may be committed. Answers and displays nothing;
 *                 presenting the refusal is {@link StandardDialog}'s job
 * @param commit   writes the gathered values, and is reached only with values {@code validate}
 *                 accepted
 * @param remove   takes away what the dialog edits, or {@code null} when this dialog offers no
 *                 Remove — in which case no Remove button is built at all, so there is no state in
 *                 which one is on screen with nothing behind it
 */
public record DialogOps<I extends @Nullable Object, O>(
    Supplier<I> read,
    Function<O, ValidationResult> validate,
    Consumer<O> commit,
    @Nullable Runnable remove
) {}
