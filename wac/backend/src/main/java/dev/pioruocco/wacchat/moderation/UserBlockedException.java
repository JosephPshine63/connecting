package dev.pioruocco.wacchat.moderation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserBlockedException extends RuntimeException {

    public UserBlockedException(String userIdA, String userIdB) {
        super("A block exists between " + userIdA + " and " + userIdB);
    }
}
