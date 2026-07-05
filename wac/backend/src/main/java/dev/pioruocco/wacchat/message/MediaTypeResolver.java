package dev.pioruocco.wacchat.message;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Slf4j
public final class MediaTypeResolver {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "ogg", "m4a");

    private MediaTypeResolver() {
    }

    public static MessageType fromUrl(String mediaUrl) {
        String extension = extractExtension(mediaUrl);
        if (IMAGE_EXTENSIONS.contains(extension)) {
            return MessageType.IMAGE;
        }
        if (VIDEO_EXTENSIONS.contains(extension)) {
            return MessageType.VIDEO;
        }
        if (AUDIO_EXTENSIONS.contains(extension)) {
            return MessageType.AUDIO;
        }
        log.warn("Unrecognized media extension '{}' for url {}, defaulting to IMAGE", extension, mediaUrl);
        return MessageType.IMAGE;
    }

    private static String extractExtension(String mediaUrl) {
        if (mediaUrl == null) {
            return "";
        }
        int dotIndex = mediaUrl.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == mediaUrl.length() - 1) {
            return "";
        }
        return mediaUrl.substring(dotIndex + 1).toLowerCase();
    }
}
