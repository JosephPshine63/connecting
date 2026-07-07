package dev.pioruocco.wacchat.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class GroupSizeLimitExceededException extends RuntimeException {

    public GroupSizeLimitExceededException(int maxMembers) {
        super("Group chats are limited to " + maxMembers + " members");
    }
}
