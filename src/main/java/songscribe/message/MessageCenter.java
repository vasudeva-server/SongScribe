package songscribe.message;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.PublicationError;
import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;

/**
 * The application's message bus. The entry point sets the bus through {@link #setBus} at
 * startup, before anything can post or subscribe; a listener joins and leaves it only through
 * {@link MessageSubscription}. Whoever constructs the bus chooses the publication-error policy;
 * the application's is {@link #exitOnPublicationError}.
 */
public final class MessageCenter {

    private static volatile @Nullable MBassador<Message> bus = null;

    private MessageCenter() {}

    /** Subscriptions on the bus this replaces are not carried over. */
    public static void setBus(MBassador<Message> bus) {
        MessageCenter.bus = bus;
    }

    private static MBassador<Message> requireBus() {
        var result = bus;

        if (result == null) {
            throw RuntimeError.exit("MessageCenter used before a bus was set");
        }

        return result;
    }

    /**
     * Delivers {@code message} to every {@code @Handler} subscribed to the bus, in priority
     * order, and returns once the last of them has run.
     * <p>
     * Delivery is synchronous and on the calling thread, so a handler observes — and may
     * change — the state the caller was in when it posted, and a post from inside a handler
     * completes before the outer post resumes.
     *
     * @effects runs every matching subscriber's handler before returning
     */
    public static void post(Message message) {
        requireBus().post(message).now();
    }

    /**
     * Package-private because {@link MessageSubscription} is the only caller: it ties every
     * registration to the lifetime that ends it. The bus holds subscribers weakly.
     */
    static void subscribe(Object listener) {
        requireBus().subscribe(listener);
    }

    static void unsubscribe(Object listener) {
        requireBus().unsubscribe(listener);
    }

    /** Public because whoever constructs the bus, generally in another package, wants the same rendering. */
    public static String describe(PublicationError error) {
        var detail = whichHandlerThrew(error);
        var cause = error.getCause();

        return cause != null ? detail + "\n  cause:    " + cause : detail;
    }

    private static String whichHandlerThrew(PublicationError error) {
        var listener = error.getListener();
        var handler  = error.getHandler();
        var message  = error.getPublishedMessage();

        return "Unhandled exception in @Handler\n" +
            "  listener: " + (listener != null ? listener.getClass().getName() : "<null>") + '\n' +
            "  handler:  " + (handler  != null ? handler.getName()             : "<null>") + '\n' +
            "  message:  " + (message  != null ? message.getClass().getSimpleName()  : "<null>");
    }

    /**
     * The application's publication-error policy: a {@code @Handler} that throws has left the
     * application in an undefined state, so this reports it as fatal.
     */
    public static void exitOnPublicationError(PublicationError error) {
        var detail = whichHandlerThrew(error);
        var cause = error.getCause();

        if (cause != null) {
            throw RuntimeError.exit(detail, cause);
        }

        throw RuntimeError.exit(error.getMessage() != null ? error.getMessage() : detail);
    }
}
