package dev.pioruocco.wacchat.notification;

public enum NotificationType {

    SEEN,
    MESSAGE,
    IMAGE,
    AUDIO,
    VIDEO,
    AVATAR_UPDATED,
    CHAT_REQUEST,
    CHAT_REQUEST_ACCEPTED,
    CHAT_REQUEST_REJECTED,
    MESSAGE_EDITED,
    MESSAGE_DELETED,
    REACTION_ADDED,
    REACTION_REMOVED,
    GROUP_ADDED,

    /** Produced and consumed entirely within notification-service (see ws.TypingController) —
     *  never published by the backend, so unlike the other constants above it does not need
     *  to stay in sync with backend's NotificationType. */
    TYPING_START,
    TYPING_STOP,

}
