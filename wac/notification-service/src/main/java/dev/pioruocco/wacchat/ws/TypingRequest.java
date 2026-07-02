package dev.pioruocco.wacchat.ws;

public record TypingRequest(String chatId, String receiverId, boolean typing) {
}
