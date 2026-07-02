package dev.pioruocco.wacchat.notification;

import java.io.Serializable;

/**
 * Published to RabbitMQ by the backend and consumed by notification-service, which
 * turns it into a convertAndSendToUser push. userId is carried explicitly (not read
 * back out of notification.receiverId) because some notifications (e.g. AVATAR_UPDATED)
 * never set receiverId.
 */
public record NotificationEvent(String userId, Notification notification) implements Serializable {
}
