package dev.pioruocco.wacchat.file;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagicByteValidatorTest {

    @Test
    void validate_acceptsRealPngBytes() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        MagicByteValidator.validate("png", png);
    }

    @Test
    void validate_acceptsRealJpegBytes() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MagicByteValidator.validate("jpg", jpg);
        MagicByteValidator.validate("jpeg", jpg);
    }

    @Test
    void validate_acceptsRealWebpBytes() {
        byte[] webp = "RIFF____WEBP".getBytes();
        MagicByteValidator.validate("webp", webp);
    }

    @Test
    void validate_acceptsRealWebmBytes() {
        byte[] webm = {(byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02, 0x03, 0x04};
        MagicByteValidator.validate("webm", webm);
    }

    @Test
    void validate_rejectsContentClaimingToBeWebmWithWrongBytes() {
        byte[] fake = "not-a-webm-file".getBytes();
        assertThatThrownBy(() -> MagicByteValidator.validate("webm", fake))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_rejectsHtmlContentClaimingToBeJpg() {
        byte[] html = "<script>alert(1)</script>".getBytes();
        assertThatThrownBy(() -> MagicByteValidator.validate("jpg", html))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match its extension");
    }

    @Test
    void validate_rejectsSvgContentClaimingToBePng() {
        byte[] svg = "<svg onload=alert(1)></svg>".getBytes();
        assertThatThrownBy(() -> MagicByteValidator.validate("png", svg))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_rejectsUnknownExtension() {
        assertThatThrownBy(() -> MagicByteValidator.validate("exe", new byte[]{1, 2, 3}))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validate_rejectsTooShortContent() {
        assertThatThrownBy(() -> MagicByteValidator.validate("png", new byte[]{1, 2}))
                .isInstanceOf(ResponseStatusException.class);
    }
}
