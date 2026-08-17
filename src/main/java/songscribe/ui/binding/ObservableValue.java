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

import java.util.function.Supplier;

/**
 * A value that can be read and observed, but not written — the source half of the
 * property graph.
 *
 * <p>The three capabilities are three types so that the compiler refuses a nonsense
 * edge:
 *
 * <table border="1">
 * <caption>Capabilities</caption>
 * <tr><th>Type</th><th>read</th><th>observe</th><th>write</th></tr>
 * <tr><td>{@code ObservableValue<T>}</td><td>yes</td><td>yes</td><td>no</td></tr>
 * <tr><td>{@link WritableValue}{@code <T>}</td><td>no</td><td>no</td><td>yes</td></tr>
 * <tr><td>{@link Property}{@code <T>}</td><td>yes</td><td>yes</td><td>yes</td></tr>
 * </table>
 *
 * <p>Something typed as this and not as {@code Property} can only ever be a bind
 * <i>source</i>. A {@link #computed} is exactly that case, and the type is what
 * stops a caller binding into a derivation whose next evaluation would overwrite
 * the write.
 *
 * <p>This interface has no {@code map}, no {@code combine} and no fluent predicate
 * algebra. Single-source transformation is expressed at the bind site, by the
 * {@link Bindings#bind(WritableValue, ObservableValue, java.util.function.Function)}
 * overload that takes the function; a value derived from more than one source is a
 * {@link #computed}. A rule that a binding shares with a dialog's validation is a
 * named domain function that both call — never a method here, because a rule that
 * lives inside the framework cannot be referenced by a controller.
 *
 * <p><b>Threading:</b> EDT-only, like everything in this package. See the package
 * documentation for that invariant and for the rule that values are replaced rather
 * than mutated.
 *
 * @param <T> the observed value's type
 */
public interface ObservableValue<T> {

    /**
     * Returns the current value.
     *
     * <p>Reading is also how a dependency is declared: an implementation must call
     * {@code DependencyTracker.track(this)} before answering, so that a read
     * happening inside a {@link #computed} body registers this value as one of that
     * computed's dependencies for the run in progress. An implementation that skips
     * that call is invisible to every computed that reads it, and the computed then
     * never recomputes — with nothing reporting it.
     *
     * @return the current value, never {@code null}
     * @effects registers this value with the enclosing dependency recording, when
     *     one is in progress. Reading is otherwise free of side effects; in
     *     particular it never notifies observers.
     * @invariant the value returned by two reads with no intervening change is the
     *     same value.
     */
    T get();

    /**
     * Registers {@code onChange} to run whenever this value notifies, and returns
     * the subscription that undoes the registration.
     *
     * <p><b>Notification is not the same as a change.</b> What counts as a
     * notification is each implementation's own promise: a {@link ValueProperty}
     * notifies only when {@link WritableValue#set} is given a value that differs
     * from the current one, while a {@code computed} notifies whenever one of its
     * dependencies notifies, whether or not the body would produce a different
     * result. A caller needing change-only semantics over a computed is described
     * in {@link Bindings#onChange}.
     *
     * <p>{@code onChange} is passed nothing, because the new value is read from
     * {@link #get} — that keeps the reading of a value in one place and lets an
     * action read several values rather than only the one that fired.
     *
     * @param onChange the action to run on notification; it may read any value and
     *     may register or cancel observations, including its own
     * @return the subscription that ends this registration
     * @effects retains {@code onChange} until the returned subscription is
     *     cancelled. An uncancelled registration keeps {@code onChange} — and
     *     everything it captures — reachable for as long as this value is.
     */
    Subscription observe(Runnable onChange);

    /**
     * Returns a value derived by running {@code body}, whose dependencies are
     * discovered by running it.
     *
     * <p>Every {@code ObservableValue} read during a run of {@code body} becomes a
     * dependency of the result for that run, and the set is re-collected on every
     * evaluation. A body that reads a value only on some branches is therefore
     * subscribed to that value only while those branches are the ones taken.
     *
     * <p>Evaluation is lazy: a dependency's notification marks the result stale and
     * is passed on to the result's own observers at once, but {@code body} does not
     * run again until the value is next read. An eager consumer — a
     * {@link Bindings#bind} edge — reads on every notification and so behaves
     * eagerly; an unbound computed shared by two readers evaluates once per change
     * rather than once per reader.
     *
     * <p>The result is an {@code ObservableValue} and not a {@link Property}, so it
     * cannot be a bind target.
     *
     * @param <T> the derived value's type
     * @param body the derivation; it must read only values and must not write any,
     *     must be free of side effects, and must not read the computed it defines
     * @return the derived value
     * @effects the returned value holds an observation on each of its current
     *     dependencies. Those observations are released when a dependency drops out
     *     of the set on a later run, and when the last consumer of the computed is
     *     itself disposed.
     * @invariant a read of the returned value is either the cached result of the
     *     last run of {@code body} or the result of a run made during that read;
     *     {@code body} never runs more than once per dependency change.
     */
    static <T> ObservableValue<T> computed(Supplier<T> body) {
        return new Computed<>(body);
    }
}
