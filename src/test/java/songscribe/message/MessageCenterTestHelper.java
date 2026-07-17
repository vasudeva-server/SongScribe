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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Test-only access to {@link MessageCenter} internals.
 * Lives in the same package to reach package-private methods without opening production APIs.
 *
 * <p>Two probes are installed:
 *
 * <ul>
 *   <li><b>Publication errors</b> — MBassador swallows any throwable the registered error
 *       handler throws, so a {@code @Handler} that throws during {@link MessageCenter#post}
 *       silently aborts delivery to every lower-priority subscriber of that post and the
 *       test keeps running. The probe records each publication error so {@code UnitTest}'s
 *       teardown can fail the test loudly instead.</li>
 *   <li><b>Subscriptions</b> — production objects subscribe in their constructors, so
 *       merely constructing one in a test leaves a zombie listener on the JVM-wide bus
 *       that fires against torn-down mocks in later tests. The probe records every
 *       listener subscribed during a test so {@code UnitTest}'s teardown can unsubscribe
 *       them all.</li>
 * </ul>
 */
public final class MessageCenterTestHelper {

    private static final List<String> publicationErrors = Collections.synchronizedList(new ArrayList<>());

    // Identity semantics: mocks and value-like listeners must be tracked by reference,
    // not equals(). Synchronized because handlers may subscribe from non-test threads.
    private static final Set<Object> trackedListeners =
        Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    /**
     * Installs the recording probes. Call once from a {@code @BeforeAll} in each test
     * base class. Safe to call multiple times.
     */
    public static void install() {
        MessageCenter.setPublicationErrorProbeForTesting(publicationErrors::add);
        MessageCenter.setSubscriptionProbeForTesting(trackedListeners::add);
    }

    /**
     * Unsubscribes every listener subscribed via {@link MessageCenter#subscribe} since
     * the last call, restoring an empty bus between tests. Call from {@code @AfterEach}
     * in test base classes.
     */
    public static void unsubscribeTrackedListeners() {
        // Snapshot: unsubscribing must not race a handler subscribing mid-iteration.
        Object[] listeners;

        synchronized (trackedListeners) {
            listeners = trackedListeners.toArray();
            trackedListeners.clear();
        }

        for (var listener : listeners) {
            MessageCenter.unsubscribe(listener);
        }
    }

    /**
     * Discards recorded publication errors. Call after intentionally driving the error
     * path (e.g. invoking {@code handlePublicationError} directly) so teardown does not
     * flag the deliberate error.
     */
    public static void clearPublicationErrors() {
        publicationErrors.clear();
    }

    /**
     * Fails if any publication error was recorded since the last clear. Recorded errors
     * are cleared before throwing so a failure does not cascade into later tests in the
     * same class.
     */
    public static void assertNoPublicationErrors() {
        if (publicationErrors.isEmpty()) {
            return;
        }

        var details = String.join("\n\n", publicationErrors);
        publicationErrors.clear();
        throw new AssertionError(
            "A @Handler threw during MessageCenter.post — MBassador swallowed the error and "
                + "aborted delivery to all lower-priority subscribers of that post:\n\n" + details);
    }

    private MessageCenterTestHelper() {}
}
