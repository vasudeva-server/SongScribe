package songscribe.message;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageCenter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageCenter.class);

    private static final MBassador<Message> eventBus = new MBassador<>((IPublicationErrorHandler) error -> {
        var cause = error.getCause();
        var message = error.getMessage() != null ? error.getMessage() : "Message publication error";

        if (cause != null) {
            LOG.error(message, cause);
        } else {
            LOG.error(message);
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
