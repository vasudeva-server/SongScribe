package songscribe.ui.message;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;

import songscribe.util.Log;

public class MessageCenter {

    private static final MBassador<Message> eventBus = new MBassador<>((IPublicationErrorHandler) error -> {
        var cause = error.getCause();
        var message = error.getMessage() != null ? error.getMessage() : "Message publication error";

        if (cause != null) {
            Log.error(message, cause);
        } else {
            Log.error(message);
        }
    });

    private MessageCenter() {}

    public static void post(Message message) {
        eventBus.post(message).now();
    }

    public static void subscribe(Object listener) {
        eventBus.subscribe(listener);
    }
}
