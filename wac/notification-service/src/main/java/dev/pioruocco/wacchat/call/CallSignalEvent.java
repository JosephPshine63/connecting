package dev.pioruocco.wacchat.call;

import java.io.Serializable;

/**
 * Published to RabbitMQ by call-service and consumed here by CallSignalListener, which
 * turns it into a convertAndSendToUser push on /queue/call. Duplicated verbatim (same
 * FQCN dev.pioruocco.wacchat.call.CallSignalEvent) between call-service and
 * notification-service — same reasoning as Notification/NotificationEvent: Jackson's
 * default __TypeId__ header must resolve to the same class on both ends without a
 * custom DefaultClassMapper.
 */
public record CallSignalEvent(String toUserId, CallSignal signal) implements Serializable {
}
