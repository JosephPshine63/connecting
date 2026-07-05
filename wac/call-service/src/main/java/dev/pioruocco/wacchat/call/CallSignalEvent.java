package dev.pioruocco.wacchat.call;

import java.io.Serializable;

/**
 * Published to RabbitMQ by call-service and consumed by notification-service, which
 * turns it into a convertAndSendToUser push on /queue/call. Same 1:1 pattern as
 * dev.pioruocco.wacchat.notification.NotificationEvent — duplicated verbatim (same FQCN)
 * between call-service and notification-service so Jackson's default __TypeId__ header
 * resolves to the same class on both ends without a custom DefaultClassMapper.
 */
public record CallSignalEvent(String toUserId, CallSignal signal) implements Serializable {
}
