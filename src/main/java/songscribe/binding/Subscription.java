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

/**
 * A registered observation, handed back by {@link ObservableValue#observe} so the
 * registrant can undo it.
 *
 * <p>Holding one of these is the only way to stop observing. An observation that
 * nobody cancels lives as long as the value it observes, which for a Swing control
 * is as long as the dialog holding it.
 */
public interface Subscription {

    /**
     * Ends the observation, so the registered action is not run again.
     *
     * <p>Idempotent: a second and every later call is a no-op, never an error. That
     * is what lets an owner cancel unconditionally on teardown without tracking
     * whether some earlier path already did.
     *
     * @effects releases the observation. The observed value keeps notifying its
     *     other observers; only this one is detached.
     */
    void cancel();
}
