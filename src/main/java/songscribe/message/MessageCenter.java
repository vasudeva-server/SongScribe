package songscribe.message;

import java.util.function.Consumer;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import net.engio.mbassy.bus.error.PublicationError;
import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;

public final class MessageCenter {

    private static final MBassador<Message> eventBus =
        new MBassador<>((IPublicationErrorHandler) MessageCenter::handlePublicationError);

    // Test-only observation hook, null in production. MBassador wraps its error
    // handler in catch(Throwable) and swallows whatever it throws, so a throwing
    // @Handler silently aborts delivery to every lower-priority subscriber of that
    // post — invisible to tests without this probe.
    private static @Nullable Consumer<String> publicationErrorProbe;

    // Test-only observation hook, null in production. Production objects subscribe in
    // their constructors, so merely constructing one in a test leaves a zombie listener
    // on the JVM-wide bus; this probe lets test teardown unsubscribe everything a test
    // subscribed.
    private static @Nullable Consumer<Object> subscriptionProbe;

    // Package-private — for use only by MessageCenterTestHelper in tests.
    static void setPublicationErrorProbeForTesting(@Nullable Consumer<String> probe) {
        publicationErrorProbe = probe;
    }

    // Package-private — for use only by MessageCenterTestHelper in tests.
    static void setSubscriptionProbeForTesting(@Nullable Consumer<Object> probe) {
        subscriptionProbe = probe;
    }

    private static void handlePublicationError(PublicationError error) {
        var listener = error.getListener();
        var handler  = error.getHandler();
        var message  = error.getPublishedMessage();
        var cause    = error.getCause();

        var detail = "Unhandled exception in @Handler\n" +
            "  listener: " + (listener != null ? listener.getClass().getName() : "<null>") + '\n' +
            "  handler:  " + (handler  != null ? handler.getName()             : "<null>") + '\n' +
            "  message:  " + (message  != null ? message.getClass().getSimpleName()  : "<null>");

        if (publicationErrorProbe != null) {
            publicationErrorProbe.accept(cause != null ? detail + "\n  cause:    " + cause : detail);
        }

        if (cause != null) {
            throw RuntimeError.exit(detail, cause);
        }

        throw RuntimeError.exit(error.getMessage() != null ? error.getMessage() : detail);
    }

    private MessageCenter() {}

    public static void post(Message message) {
        eventBus.post(message).now();
    }

    public static void subscribe(Object listener) {
        if (subscriptionProbe != null) {
            subscriptionProbe.accept(listener);
        }

        eventBus.subscribe(listener);
    }

    public static void unsubscribe(Object listener) {
        eventBus.unsubscribe(listener);
    }
}
