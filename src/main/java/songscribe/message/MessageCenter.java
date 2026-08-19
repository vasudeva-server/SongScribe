package songscribe.message;

import java.util.ArrayDeque;
import java.util.Deque;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import net.engio.mbassy.bus.error.PublicationError;

import songscribe.error.RuntimeError;

/**
 * The application's message bus, and the scopes that can temporarily replace it.
 * <p>
 * {@link #post}, {@link #subscribe} and {@link #unsubscribe} always act on the bus in force.
 * Outside any scope that is the application bus, whose publication-error handler treats a
 * throwing {@code @Handler} as fatal.
 * <p>
 * A {@link MessageBusScope} pushes a fresh bus with a publication-error handler of its own.
 * While it is in force it <em>replaces</em> the application bus rather than layering over it:
 * posts reach only what subscribed inside the scope, and closing the scope discards that bus
 * and everything subscribed to it in one operation. See {@link MessageBusScope} for the
 * constraints that carries.
 * <p>
 * The rules that span the whole codebase — who must unsubscribe, how messages are named, what
 * a handler may do — are in {@code docs/messages.md}.
 */
public final class MessageCenter {

    /**
     * The bus every subscriber and poster reaches outside a scope. A field rather than something
     * built on demand so the JVM's class-initialization guarantee supplies the "exactly once, and
     * visible to every thread" that a check-then-build cannot.
     */
    private static final MBassador<Message> APPLICATION_BUS =
        new MBassador<>(MessageCenter::exitOnPublicationError);

    /** The scopes in force, innermost first. Empty whenever the application bus is the one in force. */
    private static final Deque<MBassador<Message>> SCOPE_STACK = new ArrayDeque<>();

    private MessageCenter() {}

    /**
     * Delivers {@code message} to every {@code @Handler} subscribed to the bus in force, in
     * priority order, and returns once the last of them has run.
     * <p>
     * Delivery is synchronous and on the calling thread, so a handler observes — and may
     * change — the state the caller was in when it posted, and a post from inside a handler
     * completes before the outer post resumes.
     *
     * @effects runs every matching subscriber's handler before returning
     */
    public static void post(Message message) {
        bus().post(message).now();
    }

    /**
     * Registers {@code listener}'s {@code @Handler} methods with the bus in force. Subscribing a
     * listener the bus already holds does nothing, so this is safe to call again.
     * <p>
     * The bus holds subscribers <em>weakly</em>: a listener that nothing else keeps strongly
     * reachable is collected and silently stops receiving messages. The caller is responsible for
     * that reference and for the matching {@link #unsubscribe} — see {@code docs/messages.md}.
     *
     * @effects the listener begins receiving messages on the bus in force
     */
    public static void subscribe(Object listener) {
        bus().subscribe(listener);
    }

    /**
     * Removes {@code listener} from the bus in force. Does nothing if that bus does not hold it —
     * including when the listener subscribed to the application bus and a scope is in force, since
     * a scope replaces the application bus rather than layering over it.
     *
     * @effects the listener stops receiving messages on the bus in force
     */
    public static void unsubscribe(Object listener) {
        bus().unsubscribe(listener);
    }

    /** The innermost scope's bus, or the application bus when no scope is in force. */
    private static MBassador<Message> bus() {
        var scoped = SCOPE_STACK.peek();

        return scoped != null ? scoped : APPLICATION_BUS;
    }

    /**
     * Pushes a bus that reports publication errors to {@code errorHandler}, making it the bus in
     * force until it is passed back to {@link #popBus}. Package-private because
     * {@link MessageBusScope} is the only supported way to drive the stack — it is what
     * guarantees the pop.
     *
     * @return the pushed bus, which its scope holds so that {@link #popBus} can verify it
     */
    static MBassador<Message> pushBus(IPublicationErrorHandler errorHandler) {
        var bus = new MBassador<Message>(errorHandler);
        SCOPE_STACK.push(bus);

        return bus;
    }

    /**
     * Discards {@code bus}, along with everything subscribed to it, and restores the one beneath.
     * Shuts it down so its dispatch threads are released.
     * <p>
     * Taking the bus rather than simply popping the head is what makes closing scopes out of
     * order — or closing one twice — fail here rather than silently discard another scope's bus.
     *
     * @throws RuntimeException reported through {@link RuntimeError#exit} if {@code bus} is not
     *                          the bus in force
     */
    static void popBus(MBassador<Message> bus) {
        if (SCOPE_STACK.peek() != bus) {
            throw RuntimeError.exit(
                "MessageCenter.popBus() for a bus that is not in force — message bus scopes were "
                    + "closed out of order, or one was closed twice"
            );
        }

        SCOPE_STACK.pop().shutdown();
    }

    /**
     * Renders a publication error as diagnostic text: which listener's handler threw, for which
     * message, and the cause if MBassador reported one.
     * <p>
     * Public because a {@link MessageBusScope}'s error handler is supplied by its caller, which
     * is generally in another package and wants the same rendering the application bus uses.
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
     * {@link #describe} because {@link #exitOnPublicationError} passes the cause to
     * {@link RuntimeError#exit} as a throwable rather than as message text.
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
     * The application bus's publication-error handler: a {@code @Handler} that throws has left
     * the application in an undefined state, so this reports it as fatal.
     * <p>
     * MBassador wraps the error handler in {@code catch(Throwable)} and swallows whatever it
     * throws, so the throw below never propagates — {@link RuntimeError#exit} has already
     * reported and terminated by the time it is evaluated.
     */
    private static void exitOnPublicationError(PublicationError error) {
        var detail = whichHandlerThrew(error);
        var cause = error.getCause();

        if (cause != null) {
            throw RuntimeError.exit(detail, cause);
        }

        throw RuntimeError.exit(error.getMessage() != null ? error.getMessage() : detail);
    }
}
