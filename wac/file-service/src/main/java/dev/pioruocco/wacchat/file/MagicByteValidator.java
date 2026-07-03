package dev.pioruocco.wacchat.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/**
 * Confirms uploaded content actually matches the extension the client claims,
 * instead of trusting the client-supplied filename/Content-Type — a mismatched
 * file (e.g. an SVG/HTML payload masquerading as .jpg) is served back publicly
 * and unmodified from R2, so this is the only server-side check standing
 * between an upload and a stored-XSS/MIME-confusion vector.
 */
final class MagicByteValidator {

    private MagicByteValidator() {
    }

    static void validate(String extension, byte[] content) {
        boolean valid = switch (extension) {
            case "jpg", "jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> matchesAscii(content, 0, "GIF87a") || matchesAscii(content, 0, "GIF89a");
            case "webp" -> matchesAscii(content, 0, "RIFF") && matchesAscii(content, 8, "WEBP");
            case "wav" -> matchesAscii(content, 0, "RIFF") && matchesAscii(content, 8, "WAVE");
            case "ogg" -> matchesAscii(content, 0, "OggS");
            case "mp4", "mov", "m4a" -> matchesAscii(content, 4, "ftyp")
                    || matchesAscii(content, 4, "moov")
                    || matchesAscii(content, 4, "free")
                    || matchesAscii(content, 4, "mdat")
                    || matchesAscii(content, 4, "wide")
                    || matchesAscii(content, 4, "skip");
            case "mp3" -> matchesAscii(content, 0, "ID3") || isMpegFrameSync(content);
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File content does not match its extension (" + extension + ")");
        }
    }

    private static boolean startsWith(byte[] content, int... expected) {
        if (content.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAscii(byte[] content, int offset, String expected) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (content.length < offset + expectedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedBytes.length; i++) {
            if (content[offset + i] != expectedBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMpegFrameSync(byte[] content) {
        if (content.length < 2) {
            return false;
        }
        int b0 = content[0] & 0xFF;
        int b1 = content[1] & 0xFF;
        return b0 == 0xFF && (b1 & 0xE0) == 0xE0;
    }
}
