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
package songscribe.message;

import songscribe.lifecycle.Disposable;

/**
 * A listener's registration with the message bus, ended by disposing it. A registration cannot
 * outlive its owner: a listener retired while the process continues constructs one of these, and
 * a listener that lives for the process calls {@link #addProcessListener} instead.
 *
 * <p>The bus holds subscribers weakly and this subscription is a field of the listener, so it
 * does not keep the listener alive; the listener must stay reachable for as long as it should
 * receive messages.
 *
 * <p>Registering and disposing are safe from any thread. A delivery already running on another
 * thread when {@link #dispose} is called completes.
 *
 * <p>Owned by the listener it registers, and disposed from that listener's
 * {@link Disposable#dispose()}.
 */
public final class MessageSubscription implements Disposable {

    private final Object listener;

    /**
     * Registers {@code listener}'s {@code @Handler} methods with the bus.
     *
     * @param listener the object whose handlers receive messages
     * @effects the listener begins receiving messages
     */
    public MessageSubscription(Object listener) {
        this.listener = listener;
        MessageCenter.subscribe(listener);
    }

    /**
     * The bus holds the listener weakly, so a process-lifetime listener with no owner of its
     * own is annotated {@code @Listener(references = References.Strong)}.
     */
    public static void addProcessListener(Object listener) {
        MessageCenter.subscribe(listener);
    }

    /**
     * Ends the listener's registration. Idempotent.
     *
     * @effects the listener stops receiving messages
     */
    @Override
    public void dispose() {
        MessageCenter.unsubscribe(listener);
    }
}
