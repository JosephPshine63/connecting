package dev.pioruocco.wacchat.file.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAuthFilterTest {

    private final InternalAuthFilter filter = new InternalAuthFilter();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "internalApiKey", "expected-secret");
    }

    @Test
    void rejectsRequestMissingHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/files/avatars/user-1");

        filter.doFilter(request, response, chain);

        verify(response).sendError(anyInt(), anyString());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsRequestWithWrongHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/files/avatars/user-1");
        when(request.getHeader("X-Internal-Api-Key")).thenReturn("wrong-secret");

        filter.doFilter(request, response, chain);

        verify(response).sendError(anyInt(), anyString());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void allowsRequestWithCorrectHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/files/avatars/user-1");
        when(request.getHeader("X-Internal-Api-Key")).thenReturn("expected-secret");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsFilterForActuatorEndpoints() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/actuator/health");

        boolean shouldNotFilter = ReflectionTestUtils.invokeMethod(filter, "shouldNotFilter", request);

        org.junit.jupiter.api.Assertions.assertTrue(shouldNotFilter);
    }
}
