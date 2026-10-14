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

import java.util.ArrayList;
import java.util.List;

/**
 * The observer bookkeeping shared by every {@link ObservableValue} implementation in this
 * package. EDT-only; not synchronized.
 */
final class Observers {

    private final List<Runnable> observers = new ArrayList<>();

    /** The same {@code Runnable} may be registered more than once; each registration is independent. */
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
     * An action registered during a pass is not run by that pass; one cancelled during a pass
     * may still be run by it.
     */
    void notifyObservers() {
        for (var observer : List.copyOf(observers)) {
            observer.run();
        }
    }
}
