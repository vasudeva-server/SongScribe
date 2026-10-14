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
package songscribe.lifecycle;

import java.util.List;

/**
 * Implemented by a class whose constructor registers the instance with something that
 * outlives it, so the registration must be released before the instance is discarded.
 *
 * <p>Implement this if and only if there is real work to do, or the marker means nothing.
 *
 * <p>Never implement this on a {@link java.awt.Window} subclass: {@code Window.dispose()}
 * already means releasing the native peer, and Swing calls it on paths the application does
 * not control.
 */
@FunctionalInterface
public interface Disposable {

    /**
     * Idempotent. The instance must not be used afterwards; the contract of every other
     * method is void once this has been called. A process-lifetime class does not implement
     * this interface.
     */
    void dispose();

    /**
     * The returned instance disposes every part on its first {@link #dispose} and nothing on
     * any later call, so it is idempotent whether or not the parts are.
     */
    static Disposable of(Disposable... parts) {
        var owned = List.of(parts);

        return new Disposable() {

            private boolean disposed = false;

            @Override
            public void dispose() {
                if (disposed) {
                    return;
                }

                disposed = true;
                owned.forEach(Disposable::dispose);
            }
        };
    }
}
