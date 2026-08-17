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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import songscribe.lifecycle.Disposable;
import songscribe.ui.binding.Binding.InitialWrite;

/**
 * The set of bindings and effects one dialog declares, and the owner that tears
 * them all down together.
 *
 * <p>Declaring an edge here is the alternative to wiring a listener by hand: the
 * framework observes the source, decides when the target has to be written, and
 * absorbs the notification its own write causes. Every edge and every effect
 * registered here is held until {@link #dispose}, which is the only way to release
 * them.
 *
 * <p><b>Every method settles what it registers immediately.</b> A binding evaluates
 * its source once at registration and writes its target, so a dialog is consistent
 * the moment it has finished declaring itself and never needs an initial
 * synchronization pass.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>A {@code Bindings} belongs to exactly one dialog, which creates it when it is
 * built and calls {@code dispose()} on it when it closes. Dialogs are built per
 * opening and discarded on close, so an undisposed {@code Bindings} keeps its
 * observations — and through them the dialog, its controls and everything they
 * capture — alive past the close. See {@code docs/lifecycle.md}.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package.
 */
public final class Bindings implements Disposable {

    private final List<Subscription> registered = new ArrayList<>();

    /**
     * Writes {@code source}'s value into {@code target}, now and on every later
     * notification from {@code source}.
     *
     * <p>The target is a {@link WritableValue}, so this edge is one-way by
     * construction: an edit made to the target by anything else does not travel
     * back, and the next notification from the source overwrites it.
     *
     * @param <T> the target's value type
     * @param target what to write; typed {@code WritableValue} so a value that
     *     cannot notify can never be passed as the source instead
     * @param source what to read and observe
     * @effects evaluates {@code source} and writes {@code target} before returning,
     *     then holds an observation on {@code source} until {@link #dispose}.
     * @invariant the edge writes nothing when the value it computes equals the
     *     value this same edge last wrote, by {@code Objects.equals}. The
     *     comparison is against the last written value rather than the target's
     *     current value, since a {@code WritableValue} has none to read. An edge
     *     that has not yet written always writes, which is what makes the
     *     registration-time write happen.
     */
    public <T> void bind(WritableValue<T> target, ObservableValue<? extends T> source) {
        register(new Binding<>(target, source, source::get, InitialWrite.WRITE));
    }

    /**
     * Writes {@code transform} applied to {@code source}'s value into
     * {@code target}, now and on every later notification from {@code source}.
     *
     * <p>This is where single-source conversion belongs — formatting a number as a
     * label, deriving an enabled state from a selection — rather than on
     * {@link ObservableValue}, which has no {@code map}. The transform runs on
     * every notification, and it does <b>not</b> run under the dependency tracker:
     * only {@link ObservableValue#computed} discovers dependencies, so a transform
     * that reads some other observable value acquires no dependency on it and the
     * edge will not fire when that value changes. A value derived from more than
     * one source is a {@code computed}.
     *
     * @param <S> the source's value type
     * @param <T> the target's value type
     * @param target what to write
     * @param source what to read and observe
     * @param transform converts the source value to the target value; it must be
     *     pure, and must not write any value in this package
     * @effects evaluates the source, applies the transform and writes
     *     {@code target} before returning, then holds an observation on
     *     {@code source} until {@link #dispose}.
     * @invariant the edge writes nothing when the transformed value equals the
     *     value this same edge last wrote, by {@code Objects.equals}, so a source
     *     that notifies without changing what the transform produces causes no
     *     write at all.
     */
    public <S, T> void bind(WritableValue<T> target, ObservableValue<S> source, Function<S, T> transform) {
        register(new Binding<>(target, source, () -> transform.apply(source.get()), InitialWrite.WRITE));
    }

    /**
     * Folds {@code source}'s value into {@code target}'s current value with
     * {@code merge}, and writes the result back into {@code target} — now and on
     * every later notification from {@code source}.
     *
     * <p>For a target that carries more than the source determines: a font whose
     * family a combo box selects but whose size and style come from elsewhere, for
     * instance. {@code merge} receives the source value and the target's current
     * value, and returns what the target should become.
     *
     * <p><b>The target is a {@link Property} here and a {@link WritableValue} on the
     * other three-argument overload, and the difference is not an oversight.</b>
     * Merging has to read the target's current value, and only a {@code Property}
     * can be read. The two overloads resolve unambiguously in any case, since their
     * lambdas differ in arity.
     *
     * <p>Like the transform overload, {@code merge} does not run under the
     * dependency tracker: reading the target inside it acquires no dependency on
     * the target, so an edit to the target does not re-fire this edge.
     *
     * @param <S> the source's value type
     * @param <T> the target's value type
     * @param target what to read, fold into and write
     * @param source what to read and observe
     * @param merge receives the source's value and the target's current value, and
     *     returns the target's new value; it must be pure
     * @effects evaluates the source, reads {@code target}, and writes the merged
     *     value into {@code target} before returning, then holds an observation on
     *     {@code source} until {@link #dispose}.
     * @invariant the edge writes nothing when the merged value equals the value
     *     this same edge last wrote, by {@code Objects.equals}.
     */
    public <S, T> void bind(Property<T> target, ObservableValue<S> source, BiFunction<S, T, T> merge) {
        register(new Binding<>(target, source, () -> merge.apply(source.get(), target.get()), InitialWrite.WRITE));
    }

    /**
     * Keeps {@code a} and {@code b} holding the same value, in both directions.
     *
     * <p>{@code a} is the side that wins at registration: {@code b} is written from
     * {@code a}, and {@code a} is left as it was.
     *
     * @param <T> the shared value type
     * @param a one side, and the one that supplies the initial value
     * @param b the other side
     * @effects writes {@code a}'s value into {@code b} before returning, then holds
     *     an observation on each side until {@link #dispose}.
     * @invariant propagation terminates. Each of the two edges carries its own
     *     re-entrancy flag and drops the notification caused by its own write, and
     *     each also declines to write a value equal to the one it last wrote; a
     *     write echoing back through the far side therefore stops on the second
     *     pass rather than cycling.
     */
    public <T> void bindBidirectional(Property<T> a, Property<T> b) {
        bindBidirectional(a, b, new Transform<>(Function.identity(), Function.identity()));
    }

    /**
     * Keeps {@code a} and {@code b} holding values that correspond under
     * {@code transform}, in both directions.
     *
     * <p>The two-way case for sides of different types — a text field and the number
     * it spells, a combo box selection and the domain value it names.
     * {@code transform.forward()} converts an {@code a} value for {@code b}, and
     * {@code transform.backward()} converts back.
     *
     * <p>{@code a} is the side that wins at registration: {@code b} is written with
     * {@code forward(a.get())}, and {@code a} is left as it was. The reverse edge is
     * created already knowing that value, so it does not immediately write
     * {@code a} back.
     *
     * @param <A> one side's value type
     * @param <B> the other side's value type
     * @param a one side, and the one that supplies the initial value
     * @param b the other side
     * @param transform the conversions between the two sides; they must round-trip,
     *     or each side will keep rewriting the other's value into something else
     * @effects writes {@code forward(a.get())} into {@code b} before returning, then
     *     holds an observation on each side until {@link #dispose}.
     * @invariant propagation terminates, by the same per-edge re-entrancy flag and
     *     unchanged-value stop as {@link #bindBidirectional(Property, Property)}.
     */
    public <A, B> void bindBidirectional(Property<A> a, Property<B> b, Transform<A, B> transform) {
        register(new Binding<>(b, a, () -> transform.forward().apply(a.get()), InitialWrite.WRITE));
        register(new Binding<>(a, b, () -> transform.backward().apply(b.get()), InitialWrite.SKIP));
    }

    /**
     * Runs {@code action} whenever {@code source} notifies.
     *
     * <p>The framework's effect construct, and the only member here that produces
     * no value: for the consequences of a change that are not a value anybody could
     * bind — repacking a window, moving focus, starting playback. Unlike a binding,
     * {@code action} is <b>not</b> run at registration; it runs on notifications
     * only.
     *
     * <p><b>Whether this fires on a real change or on every write is a property of
     * the source, not of this method.</b> A {@link ValueProperty} notifies only when
     * {@link WritableValue#set} is given a different value, so an effect on one runs
     * on transitions. A {@code computed} notifies whenever any dependency notifies,
     * whatever its body would now produce, so an effect on one runs far more often
     * than the derived value changes. A caller that needs change-only semantics over
     * a derivation binds a {@code ValueProperty} from the computed and calls this on
     * the {@code ValueProperty}. Repacking a dialog when a subtitle appears or
     * disappears is the worked example: the computed is the subtitle's visibility,
     * and repacking on every keystroke that leaves it visible would fight the user.
     *
     * @param source what to observe
     * @param action what to run on each notification; it may read any value and
     *     write any {@code WritableValue}, and a write that feeds back into
     *     {@code source} will run it again
     * @effects holds an observation on {@code source} until {@link #dispose}.
     */
    public void onChange(ObservableValue<?> source, Runnable action) {
        register(source.observe(action));
    }

    /**
     * Cancels every observation held by every binding and effect registered here.
     *
     * <p>Idempotent: a second call cancels nothing because nothing is left
     * registered. The instance is not reusable afterwards — registering a new edge
     * on a disposed {@code Bindings} would work, and would then never be torn down,
     * because the dialog that owned the disposal is gone.
     *
     * @effects releases every observation, dropping the framework's references to
     *     the dialog's controls, transforms and effect actions. Nothing is written:
     *     targets keep whatever value they last received.
     */
    @Override
    public void dispose() {
        for (var subscription : registered) {
            subscription.cancel();
        }

        registered.clear();
    }

    private void register(Subscription subscription) {
        registered.add(subscription);
    }
}
