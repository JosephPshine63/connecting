package dev.pioruocco.wacchat.file;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private R2StorageService r2StorageService;

    private static TestingAuthenticationToken authenticatedAs(String userId) {
        return new TestingAuthenticationToken(userId, null);
    }

    @Test
    void uploadAvatar_returnsCreatedWithUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "content".getBytes());
        when(r2StorageService.uploadAvatar(any(), eq("user-1"))).thenReturn("https://r2.example.com/avatars/user-1/1.png");

        mockMvc.perform(multipart("/api/v1/files/avatars/user-1").file(file).principal(authenticatedAs("user-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://r2.example.com/avatars/user-1/1.png"));
    }

    @Test
    void uploadAvatar_propagatesBadRequestFromService() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.exe", "application/octet-stream", "content".getBytes());
        when(r2StorageService.uploadAvatar(any(), eq("user-1")))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: exe"));

        mockMvc.perform(multipart("/api/v1/files/avatars/user-1").file(file).principal(authenticatedAs("user-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadAvatar_rejectsWhenUserIdDoesNotMatchAuthenticatedUser() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/files/avatars/user-1").file(file).principal(authenticatedAs("someone-else")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(r2StorageService);
    }

    @Test
    void uploadMessageMedia_returnsCreatedWithUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "content".getBytes());
        when(r2StorageService.uploadMessageMedia(any(), eq("user-1"))).thenReturn("https://r2.example.com/messages/user-1/1.jpg");

        mockMvc.perform(multipart("/api/v1/files/messages/user-1/media").file(file).principal(authenticatedAs("user-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://r2.example.com/messages/user-1/1.jpg"));
    }

    @Test
    void deleteObject_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/files/objects")
                        .param("url", "https://r2.example.com/avatars/user-1/1.png")
                        .principal(authenticatedAs("user-1")))
                .andExpect(status().isNoContent());

        verify(r2StorageService).deleteObject("https://r2.example.com/avatars/user-1/1.png", "user-1");
    }
}
