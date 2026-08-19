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

/**
 * The observer bookkeeping shared by every {@link ObservableValue} implementation
 * in this package: the list of registered actions, the {@link Subscription} that
 * removes one, and the notification pass.
 *
 * <p>Every implementation that notifies — {@link ValueProperty}, {@link Computed},
 * and each control adapter in {@link Controls} — holds one of these rather than
 * repeating the list, the idempotent cancel and the copy-iterating pass. Every call
 * happens on the EDT, per the package invariant, so nothing here is synchronized.
 */
final class Observers {

    private final List<Runnable> observers = new ArrayList<>();

    /**
     * Registers {@code onNotify} and returns the subscription that removes it.
     *
     * <p>The same {@code Runnable} may be registered more than once; each
     * registration is independent and each returned subscription removes exactly
     * one of them.
     *
     * @param onNotify the action to run on every notification
     * @return the subscription that removes this registration, idempotent per
     *     {@link Subscription#cancel}
     */
    Subscription add(Runnable onNotify) {
        observers.add(onNotify);

        return new Subscription() {

            private boolean cancelled = false;

            @Override
            public void cancel() {
                if (!cancelled) {
                    cancelled = true;
                    observers.remove(onNotify);
                }
            }
        };
    }

    /**
     * Runs every registered action, in registration order.
     *
     * <p>Iterates a copy, so an action that registers or cancels an observation —
     * which a {@code computed} re-collecting its dependencies routinely does —
     * cannot invalidate the pass in progress. An action registered during a pass is
     * not run by that pass; one cancelled during a pass may still be run by it.
     */
    void notifyObservers() {
        for (var observer : List.copyOf(observers)) {
            observer.run();
        }
    }
}
