package dev.pioruocco.wacchat.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ChatNotAcceptedException extends RuntimeException {

    public ChatNotAcceptedException(String chatId) {
        super("Chat " + chatId + " is not accepted");
    }
}
