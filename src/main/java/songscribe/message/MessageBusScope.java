package songscribe.message;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;

/**
 * A message bus that lasts as long as a bounded piece of work, and takes everything subscribed
 * to it away when that work ends.
 * <p>
 * Constructing one makes it the bus in force; closing it discards that bus and restores the one
 * beneath. Use it in a try-with-resources so the pop cannot be skipped:
 *
 * <pre>{@code
 * try (var scope = new MessageBusScope(error -> errors.add(MessageCenter.describe(error)))) {
 *     convert.accept(converter);
 * }
 * }</pre>
 *
 * <h2>What a scope promises</h2>
 *
 * <ul>
 *   <li><b>It replaces, it does not layer.</b> While a scope is in force, a
 *       {@link MessageCenter#post} reaches only listeners that subscribed inside it. Whatever
 *       subscribed to the bus beneath hears nothing until the scope closes, and hears nothing
 *       about what happened while it was open.</li>
 *   <li><b>Closing is the disposal.</b> Everything that subscribed inside the scope goes with
 *       it, in one operation, with no per-subscriber bookkeeping. That is the point: a bounded
 *       piece of work that builds an object graph does not have to find every subscriber in it
 *       again on the way out.</li>
 *   <li><b>The error handler is the scope's own.</b> A throwing {@code @Handler} is reported to
 *       the handler passed here rather than to the application bus's, which treats it as fatal
 *       and shows a dialog. A process with no display needs that difference.</li>
 * </ul>
 *
 * <h2>What it requires of the caller</h2>
 *
 * The stack is process-wide, not per-thread, because a bounded piece of work may hand parts of
 * itself to other threads and they must post to the same bus. That is only coherent if scopes
 * are pushed and popped at a boundary where nothing else is running: open one when the work
 * begins and close it when the work is finished, never around a section of a live application
 * while other threads are still posting. Nesting is allowed; interleaving is not, and
 * {@link #close()} reports an attempt at it rather than letting it through.
 *
 * @see MessageCenter
 */
public final class MessageBusScope implements AutoCloseable {

    private final MBassador<Message> bus;

    /**
     * Makes a fresh bus the one in force, reporting a throwing {@code @Handler} to
     * {@code errorHandler}.
     *
     * @effects the new bus becomes the one {@link MessageCenter#post},
     *          {@link MessageCenter#subscribe} and {@link MessageCenter#unsubscribe} act on
     */
    public MessageBusScope(IPublicationErrorHandler errorHandler) {
        bus = MessageCenter.pushBus(errorHandler);
    }

    /**
     * Discards this scope's bus, and everything subscribed to it, restoring the bus beneath.
     * <p>
     * Must be called exactly once, and before any scope opened after it — the scope holds its own
     * bus so that a second call, or a close out of order, is reported rather than silently
     * discarding another scope's bus.
     *
     * @effects the bus beneath becomes the one in force again; this scope's dispatch threads are
     *          released
     * @throws RuntimeException reported through {@code RuntimeError.exit} if this scope's bus is
     *                          not the one in force
     */
    @Override
    public void close() {
        MessageCenter.popBus(bus);
    }
}
