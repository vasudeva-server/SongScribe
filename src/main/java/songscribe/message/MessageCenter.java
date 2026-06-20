package songscribe.message;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import net.engio.mbassy.bus.error.PublicationError;

import songscribe.error.RuntimeError;

public final class MessageCenter {

    private static final MBassador<Message> eventBus =
        new MBassador<>((IPublicationErrorHandler) MessageCenter::handlePublicationError);

    private static void handlePublicationError(PublicationError error) {
        var listener = error.getListener();
        var handler  = error.getHandler();
        var message  = error.getPublishedMessage();
        var cause    = error.getCause();

        var detail = "Unhandled exception in @Handler\n" +
            "  listener: " + (listener != null ? listener.getClass().getSimpleName() : "<null>") + '\n' +
            "  handler:  " + (handler  != null ? handler.getName()                   : "<null>") + '\n' +
            "  message:  " + (message  != null ? message.getClass().getSimpleName()  : "<null>");

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
        eventBus.subscribe(listener);
    }

    public static void unsubscribe(Object listener) {
        eventBus.unsubscribe(listener);
    }
}
