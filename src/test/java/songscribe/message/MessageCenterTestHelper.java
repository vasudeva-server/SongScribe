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
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Gives each test its own message bus, via {@link MessageBusScope}.
 *
 * <p>A test's scope solves both problems a shared bus creates for tests:
 *
 * <ul>
 *   <li><b>Zombie listeners.</b> Production objects subscribe in their constructors, so merely
 *       constructing one in a test leaves a listener behind that fires against torn-down mocks
 *       in later tests. Closing the scope discards the bus and everything on it at once, so
 *       there is nothing to track and nothing to unsubscribe.</li>
 *   <li><b>Swallowed handler errors.</b> MBassador wraps its error handler in
 *       {@code catch(Throwable)}, so a {@code @Handler} that throws during
 *       {@link MessageCenter#post} silently aborts delivery to every lower-priority subscriber
 *       of that post and the test keeps running. The scope's error handler records them so
 *       {@link #assertNoPublicationErrors} can fail the test loudly instead.</li>
 * </ul>
 *
 * <p>Because the scope replaces the application bus rather than layering over it, anything
 * subscribed outside a test — a class that subscribes from a static initializer, say — is not
 * reachable from inside one, and is discarded along with the scope if it happened to load
 * during a test.
 */
public final class MessageCenterTestHelper {

    // Synchronized because handlers may publish from non-test threads.
    private static final List<String> publicationErrors = Collections.synchronizedList(new ArrayList<>());

    private static @Nullable MessageBusScope scope = null;

    /**
     * Opens this test's bus scope. Call from {@code @BeforeEach}, before anything the test
     * constructs can subscribe.
     */
    public static void openScope() {
        publicationErrors.clear();
        scope = new MessageBusScope(error -> publicationErrors.add(MessageCenter.describe(error)));
    }

    /**
     * Closes this test's bus scope, discarding every listener it subscribed. Call from
     * {@code @AfterEach}, before {@link #assertNoPublicationErrors} so the discard always runs.
     * Idempotent.
     */
    public static void closeScope() {
        if (scope != null) {
            scope.close();
            scope = null;
        }
    }

    /**
     * Fails if any publication error was recorded in this test's scope. Recorded errors are
     * cleared before throwing so a failure does not cascade into later tests in the same class.
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
