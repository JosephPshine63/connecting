package dev.pioruocco.wacchat.message;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidReplyException extends RuntimeException {

    public InvalidReplyException(Long replyToId) {
        super("Message " + replyToId + " cannot be replied to in this chat");
    }
}
