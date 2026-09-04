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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.engio.mbassy.listener.Handler;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * States the rule that nothing at runtime checks: a class that handles messages registers
 * through {@link MessageSubscription}, either by holding one or by calling
 * {@link MessageSubscription#addProcessListener}. A class declaring {@link Handler} methods
 * that does neither is never seen by the bus and fails silently.
 *
 * <p>The scan reads the compiled classes rather than source text so it sees the truth the JVM
 * sees, and loads each class without initializing it — running static initializers would
 * construct the message bus, the action constants and other process-global state as a side
 * effect of the scan.
 */
class SubscriptionOwnershipTest extends UnitTest {

    private static final Path CLASSES_ROOT = Paths.get("build/classes/java/main");
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String SCANNED_PACKAGE_DIR = "songscribe";

    /*
     * A class file that does not name the handler annotation in its constant pool declares no
     * handler, so it either inherits one or has none, and its superclass is checked on its own.
     * Each internal name is a run of ASCII bytes, so the ISO-8859-1 view of the class bytes
     * contains it exactly when the bytes do.
     */
    private static final String HANDLER_INTERNAL_NAME = "net/engio/mbassy/listener/Handler";
    private static final String SUBSCRIPTION_INTERNAL_NAME = "songscribe/message/MessageSubscription";
    private static final String ADD_PROCESS_LISTENER_METHOD_NAME = "addProcessListener";

    private static List<Class<?>> classesNamingHandler() throws IOException {
        var scannedRoot = CLASSES_ROOT.resolve(SCANNED_PACKAGE_DIR);
        var candidates = new ArrayList<Class<?>>();

        try (var walk = Files.walk(scannedRoot)) {
            walk.filter(path -> path.toString().endsWith(CLASS_FILE_SUFFIX))
                .filter(classFile -> constantPoolTextOf(classFile).contains(HANDLER_INTERNAL_NAME))
                .map(SubscriptionOwnershipTest::classNameOf)
                .map(SubscriptionOwnershipTest::loadWithoutInitializing)
                .forEach(candidates::add);
        }

        assertThat(candidates)
            .as("the scan of %s found no class naming a handler; the byte filter or the classes "
                + "root is wrong", CLASSES_ROOT)
            .isNotEmpty();

        return candidates;
    }

    private static String constantPoolTextOf(Path classFile) {
        try {
            return Files.readString(classFile, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path classFileOf(Class<?> type) {
        return CLASSES_ROOT.resolve(type.getName().replace('.', '/') + CLASS_FILE_SUFFIX);
    }

    private static String classNameOf(Path classFile) {
        var relative = CLASSES_ROOT.relativize(classFile).toString();
        var withoutSuffix = relative.substring(0, relative.length() - CLASS_FILE_SUFFIX.length());

        return withoutSuffix.replace(classFile.getFileSystem().getSeparator(), ".");
    }

    private static Class<?> loadWithoutInitializing(String className) {
        try {
            return Class.forName(className, false, SubscriptionOwnershipTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<Class<?>> hierarchyOf(Class<?> type) {
        return Stream.iterate(type, current -> current != null, Class::getSuperclass);
    }

    private static boolean handlesMessages(Class<?> type) {
        return hierarchyOf(type)
            .flatMap(current -> Stream.of(current.getDeclaredMethods()))
            .anyMatch(method -> method.isAnnotationPresent(Handler.class));
    }

    private static boolean ownsSubscription(Class<?> type) {
        return hierarchyOf(type)
            .flatMap(current -> Stream.of(current.getDeclaredFields()))
            .anyMatch(field -> field.getType() == MessageSubscription.class);
    }

    /**
     * Whether the class or a superclass calls {@link MessageSubscription#addProcessListener}.
     * A call site names the method in the constant pool of the class that makes it, and a
     * subclass's pool does not repeat what its superclass's holds, so each is read on its own.
     */
    private static boolean registersForProcess(Class<?> type) {
        return hierarchyOf(type)
            .filter(current -> current.getName().startsWith(SCANNED_PACKAGE_DIR))
            .map(SubscriptionOwnershipTest::classFileOf)
            .map(SubscriptionOwnershipTest::constantPoolTextOf)
            .anyMatch(constantPoolText -> constantPoolText.contains(SUBSCRIPTION_INTERNAL_NAME)
                && constantPoolText.contains(ADD_PROCESS_LISTENER_METHOD_NAME));
    }

    @Test
    void testEveryClassThatHandlesMessagesRegistersThroughMessageSubscription() throws IOException {
        var violations = classesNamingHandler().stream()
            .filter(SubscriptionOwnershipTest::handlesMessages)
            .filter(type -> !ownsSubscription(type) && !registersForProcess(type))
            .map(type -> type.getName() + " declares @Handler methods but neither holds a "
                + "MessageSubscription nor calls MessageSubscription.addProcessListener in its hierarchy")
            .toList();

        assertThat(violations).isEmpty();
    }
}
