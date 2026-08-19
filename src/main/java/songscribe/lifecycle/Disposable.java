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
 * Implemented by a class that acquires something in its constructor which must be
 * released before the instance is discarded — today, always a message-bus
 * subscription.
 *
 * <p>Implement this if and only if there is real work to do. An empty
 * {@code dispose()} is indistinguishable from an unimplemented one, so it costs
 * the marker the only thing it is for: a class that implements this interface has
 * something to release, and a class that does not, does not.
 *
 * <p><b>Never implement this on a {@link java.awt.Window} subclass.</b>
 * {@code Window} already declares {@code dispose()} with its own meaning —
 * release the native peer — and Swing calls it on paths the application does not
 * control. Implementing the interface there silently merges two unrelated
 * operations. {@code MainFrame} is the case that would hit this, and it is
 * process-lifetime, so it does not implement this.
 *
 * <p>See {@code docs/lifecycle.md} for who calls {@code dispose()} for each
 * implementor, and {@code docs/messages.md} for the rule that a constructor-side
 * subscription creates this obligation.
 */
@FunctionalInterface
public interface Disposable {

    /**
     * Releases everything this instance acquired in its constructor, and disposes
     * the {@code Disposable}s it owns.
     *
     * <p>Idempotent — a second call is a no-op, never an error. The instance must
     * not be used afterwards; the contract of every other method on it is void
     * once this has been called.
     *
     * <p>Called by the owner that retires the instance, named in the implementing
     * class's {@code Lifecycle} Javadoc. An instance that lives as long as the
     * process is never disposed, which is why a process-lifetime class does not
     * implement this interface at all.
     */
    void dispose();

    /**
     * Returns one {@code Disposable} that disposes each of {@code parts}, in order.
     *
     * <p>For an owner holding several disposables that are always released together and
     * never individually. Naming them separately in such an owner buys nothing and costs
     * a pair of same-typed fields a construction site can transpose; one composite is the
     * whole of what the owner has.
     *
     * @param parts the disposables to release together; disposed in the order given
     * @return the composite
     * @effects the returned instance disposes every part on its first {@link #dispose},
     *     and nothing on any later call — so it is idempotent whether or not the parts
     *     are.
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
