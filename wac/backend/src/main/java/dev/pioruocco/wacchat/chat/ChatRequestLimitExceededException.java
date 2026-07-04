package dev.pioruocco.wacchat.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ChatRequestLimitExceededException extends RuntimeException {

    public ChatRequestLimitExceededException(String chatId) {
        super("Chat " + chatId + " has reached the pending message limit of "
                + ChatConstants.MAX_PENDING_MESSAGES);
    }
}
