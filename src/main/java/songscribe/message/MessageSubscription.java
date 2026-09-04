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
 * A listener's registration with the message bus: held by the listener that owns it, and ended
 * by disposing it.
 * <p>
 * The invariant this type exists for: <b>a registration cannot outlive the thing that owns
 * it.</b> A listener joins the bus in one of two ways, one per lifetime. A listener retired
 * while the process continues constructs one of these, and what the constructor hands back is
 * the object that ends the registration — so whoever registers necessarily holds the means to
 * unregister. A listener that lives for the process calls {@link #addProcessListener} instead
 * and holds nothing, because a registration that ends with the process has nothing to end it.
 * <p>
 * <strong>Reachability.</strong> The bus holds subscribers weakly, and this subscription is a
 * field <em>of</em> the listener, so it cannot keep the listener alive: a listener that nothing
 * else keeps strongly reachable is collected and silently stops receiving messages, subscription
 * or not. The listener must stay reachable for as long as its subscription is meant to deliver.
 * <p>
 * <strong>Threading.</strong> Registering and disposing are safe from any thread. A delivery
 * already running on another thread when {@link #dispose} is called completes.
 * <p>
 * <strong>Lifecycle.</strong> Owned by the listener it registers, and disposed from that
 * listener's {@link Disposable#dispose()} — alone, or composed with the listener's other
 * disposables through {@link Disposable#of}.
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
     * Registers {@code listener}'s {@code @Handler} methods with the bus for the rest of the
     * process. Nothing is handed back, because nothing ends the registration.
     * <p>
     * The bus holds the listener weakly, so a process-lifetime listener with no owner of its
     * own is annotated {@code @Listener(references = References.Strong)}; see
     * {@code docs/messages.md}.
     *
     * @param listener the object whose handlers receive messages
     * @effects the listener begins receiving messages and does so until the process exits
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
