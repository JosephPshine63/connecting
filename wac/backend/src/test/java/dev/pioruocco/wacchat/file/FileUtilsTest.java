package dev.pioruocco.wacchat.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

    private static final Path UPLOADS_ROOT = Paths.get("uploads").normalize().toAbsolutePath();

    @AfterEach
    void cleanup() throws IOException {
        Path testFile = UPLOADS_ROOT.resolve("file-utils-test.txt");
        Files.deleteIfExists(testFile);
    }

    @Test
    void resolveMedia_returnsEmptyForBlankInput() {
        assertThat(FileUtils.resolveMedia(null)).isEmpty();
        assertThat(FileUtils.resolveMedia("")).isEmpty();
    }

    @Test
    void resolveMedia_passesThroughHttpUrlUnchanged() {
        assertThat(FileUtils.resolveMedia("https://r2.example.com/messages/user-1/photo.jpg"))
                .containsExactly("https://r2.example.com/messages/user-1/photo.jpg");
    }

    @Test
    void readFileFromLocation_readsFileInsideLegacyUploadsRoot() throws IOException {
        Files.createDirectories(UPLOADS_ROOT);
        Path testFile = UPLOADS_ROOT.resolve("file-utils-test.txt");
        Files.write(testFile, "hello".getBytes());

        byte[] content = FileUtils.readFileFromLocation(testFile.toString());

        assertThat(new String(content)).isEqualTo("hello");
    }

    @Test
    void readFileFromLocation_rejectsPathOutsideLegacyUploadsRoot() {
        byte[] content = FileUtils.readFileFromLocation("/etc/passwd");

        assertThat(content).isEmpty();
    }

    @Test
    void readFileFromLocation_rejectsTraversalSequence() {
        byte[] content = FileUtils.readFileFromLocation("uploads/../../../etc/passwd");

        assertThat(content).isEmpty();
    }

    @Test
    void readFileFromLocation_returnsEmptyForBlankInput() {
        assertThat(FileUtils.readFileFromLocation(null)).isEmpty();
        assertThat(FileUtils.readFileFromLocation("")).isEmpty();
    }
}
