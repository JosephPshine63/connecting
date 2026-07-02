package dev.pioruocco.wacchat.notification;

import java.io.Serializable;

/**
 * Must keep the identical fully-qualified class name (and that of Notification) as the
 * backend module — Jackson2JsonMessageConverter's default __TypeId__ header resolves to
 * this FQCN, so both sides deserialize into the same class without extra config.
 */
public record NotificationEvent(String userId, Notification notification) implements Serializable {
}
