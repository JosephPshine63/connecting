package dev.pioruocco.wacchat.message;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NonEditableMessageException extends RuntimeException {

    public NonEditableMessageException(Long messageId) {
        super("Message " + messageId + " cannot be edited");
    }
}
