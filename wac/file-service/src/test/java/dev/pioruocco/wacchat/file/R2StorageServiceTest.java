package dev.pioruocco.wacchat.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    @Mock
    private S3Client r2Client;

    private R2StorageService r2StorageService;

    @BeforeEach
    void setUp() {
        r2StorageService = new R2StorageService(r2Client);
        ReflectionTestUtils.setField(r2StorageService, "bucketName", "wacchat-media");
        ReflectionTestUtils.setField(r2StorageService, "publicBaseUrl", "https://r2.example.com");
        when(r2Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    @Test
    void uploadAvatar_clientSpoofsContentTypeAsHtml_storesCanonicalImageMimeInstead() {
        byte[] jpegBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "text/html", jpegBytes);

        r2StorageService.uploadAvatar(file, "user-1");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        org.mockito.Mockito.verify(r2Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void uploadMessageMedia_webmExtension_storesVideoWebmContentType() {
        byte[] webmBytes = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x00, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "clip.webm", "application/octet-stream", webmBytes);

        r2StorageService.uploadMessageMedia(file, "user-1");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        org.mockito.Mockito.verify(r2Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("video/webm");
    }
}
