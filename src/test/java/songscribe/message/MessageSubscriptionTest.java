/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.message;

import java.util.ArrayList;
import java.util.List;

import net.engio.mbassy.listener.Handler;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The contract {@link MessageSubscription} is written against: constructing one registers the
 * listener, disposing it unregisters the listener, and a second dispose does nothing.
 */
class MessageSubscriptionTest extends UnitTest {

    /** Posted only by this test, so nothing else that posts on the class's bus reaches the listener. */
    private static final class ProbeMessage extends Message {}

    private static final class RecordingListener {
        private final List<ProbeMessage> received = new ArrayList<>();
        private final MessageSubscription subscription = new MessageSubscription(this);

        @Handler
        void onProbe(ProbeMessage message) {
            received.add(message);
        }
    }

    @Test
    void testSubscriptionDeliversUntilDisposedThenIgnoresRedisposal() {
        var listener = new RecordingListener();
        var firstMessage = new ProbeMessage();

        MessageCenter.post(firstMessage);
        assertThat(listener.received).containsExactly(firstMessage);

        listener.subscription.dispose();

        var secondMessage = new ProbeMessage();
        MessageCenter.post(secondMessage);
        assertThat(listener.received).containsExactly(firstMessage);

        listener.subscription.dispose();
        assertThat(listener.received).containsExactly(firstMessage);
    }
}
