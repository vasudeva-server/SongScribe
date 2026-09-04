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

import net.engio.mbassy.bus.MBassador;
import org.jspecify.annotations.Nullable;

/**
 * Gives each test class a message bus of its own, and fails a test loudly when a
 * {@code @Handler} throws during {@link MessageCenter#post}.
 *
 * <p>A bus per class is what keeps one class's listeners out of the next: a listener a test
 * builds and never disposes stays registered on the bus it joined, and that bus is discarded
 * with the class. Between classes a {@link NoOpMessageBus} is in force, so a post from something
 * a finished class leaked reaches nobody.
 *
 * <p>MBassador wraps its error handler in {@code catch(Throwable)}, so a {@code @Handler} that
 * throws during a post silently aborts delivery to every lower-priority subscriber of that post
 * and the test keeps running. The class's bus is built with a handler that records the error,
 * so {@link #assertNoPublicationErrors} can report what MBassador would otherwise swallow.
 */
public final class MessageCenterTestHelper {

    // Synchronized because handlers may publish from non-test threads.
    private static final List<String> publicationErrors = Collections.synchronizedList(new ArrayList<>());

    private static @Nullable MBassador<Message> classBus = null;

    /**
     * Puts a fresh bus in force for the test class about to run. Call from {@code @BeforeAll},
     * before anything the class constructs can subscribe or post.
     *
     * @effects the new bus is the one every post, subscribe and unsubscribe in the class acts on
     * @throws IllegalStateException if the previous class's bus was never retired
     */
    public static void installClassBus() {
        if (classBus != null) {
            throw new IllegalStateException("A test class bus is already in force; retireClassBus() was not called");
        }

        publicationErrors.clear();
        classBus = new MBassador<>(error -> publicationErrors.add(MessageCenter.describe(error)));
        MessageCenter.setBus(classBus);
    }

    /**
     * Discards the class's bus, with every listener still registered on it, and puts a
     * {@link NoOpMessageBus} in force until the next class installs its own. Call from
     * {@code @AfterAll}.
     *
     * @effects the class's dispatch threads are released; a post from now on reaches no listener
     * @throws IllegalStateException if no class bus is in force
     */
    public static void retireClassBus() {
        var retired = classBus;

        if (retired == null) {
            throw new IllegalStateException("No test class bus is in force; installClassBus() was not called");
        }

        classBus = null;
        MessageCenter.setBus(new NoOpMessageBus());
        retired.shutdown();
    }

    /**
     * Fails if any publication error was recorded since the last assertion. Recorded errors are
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
