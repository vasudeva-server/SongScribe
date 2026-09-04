package songscribe.message;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.PublicationError;
import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;

/**
 * The application's message bus.
 * <p>
 * There is one bus in force at a time, and it is supplied from outside through {@link #setBus}:
 * the application entry point sets one at startup, before anything can post or subscribe, and
 * keeps it for the life of the process. {@link #post} delivers to the bus in force; a listener
 * joins and leaves it only through {@link MessageSubscription}.
 * <p>
 * What happens when a {@code @Handler} throws is decided by the bus itself: MBassador takes its
 * publication-error handler at construction, so whoever constructs the bus chooses the policy.
 * The application's is {@link #exitOnPublicationError}.
 * <p>
 * The rules that span the whole codebase — how messages are named, what a handler may do — are
 * in {@code docs/messages.md}.
 */
public final class MessageCenter {

    /**
     * The bus in force, or null before {@link #setBus} has been called. {@code volatile} because
     * it is set on one thread and a handler may post from another.
     */
    private static volatile @Nullable MBassador<Message> bus = null;

    private MessageCenter() {}

    /**
     * Makes {@code bus} the bus every post, subscribe and unsubscribe from now on acts on.
     * <p>
     * The application entry point calls this once at startup. Nothing that subscribed to the
     * bus this replaces is carried over: a listener registered on the old bus stays registered
     * there and hears nothing from the new one.
     *
     * @effects {@code bus} becomes the bus in force
     */
    public static void setBus(MBassador<Message> bus) {
        MessageCenter.bus = bus;
    }

    /**
     * The bus in force.
     *
     * @throws RuntimeException reported through {@link RuntimeError#exit} if no bus has been set,
     *                          which means something posted or subscribed before the entry point
     *                          ran
     */
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
     * Registers {@code listener}'s {@code @Handler} methods with the bus. Registering a listener
     * the bus already holds does nothing. Package-private because {@link MessageSubscription} is
     * the only caller: it is what ties every registration to the lifetime that ends it.
     * <p>
     * The bus holds subscribers <em>weakly</em>: a listener that nothing else keeps strongly
     * reachable is collected and silently stops receiving messages.
     *
     * @effects the listener begins receiving messages
     */
    static void subscribe(Object listener) {
        requireBus().subscribe(listener);
    }

    /**
     * Removes {@code listener} from the bus. Removing a listener the bus does not hold does
     * nothing. Package-private because {@link MessageSubscription} is the only caller.
     *
     * @effects the listener stops receiving messages
     */
    static void unsubscribe(Object listener) {
        requireBus().unsubscribe(listener);
    }

    /**
     * Renders a publication error as diagnostic text: which listener's handler threw, for which
     * message, and the cause if MBassador reported one.
     * <p>
     * Public because a publication-error handler is supplied by whoever constructs the bus, which
     * is generally in another package and wants the same rendering the application's policy uses.
     *
     * @return the multi-line description, ending in the cause when there is one
     */
    public static String describe(PublicationError error) {
        var detail = whichHandlerThrew(error);
        var cause = error.getCause();

        return cause != null ? detail + "\n  cause:    " + cause : detail;
    }

    /**
     * The listener/handler/message triple, without the cause. Kept separate from
     * {@link #describe} because the built-in policies pass the cause to their sink as a throwable
     * rather than as message text.
     */
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
     * The application's publication-error policy, which the entry point constructs its bus with:
     * a {@code @Handler} that throws has left the application in an undefined state, so this
     * reports it as fatal.
     * <p>
     * MBassador wraps the error handler in {@code catch(Throwable)} and swallows whatever it
     * throws, so the throw below never propagates — {@link RuntimeError#exit} has already
     * reported and terminated by the time it is evaluated.
     *
     * @effects reports the error through {@link RuntimeError#exit}, which terminates the process
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
