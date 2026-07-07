package dev.pioruocco.wacchat.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Security;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    private static final String USER_ID = "user-1";
    // A syntactically valid P-256 uncompressed public key / auth secret (base64url, no
    // padding) — Notification's constructor decodes p256dh as an actual EC point (via
    // BouncyCastle), so a placeholder string would throw before webPushService.send() is
    // ever reached.
    private static final String P256DH = "BDo-2swH5MMpaJod-WlTKdV1STGwtHErddMsAf929koUgURjgn1rlXIo2jES1H5EiqMntqt5fe-RaNCDWTj0aJ8";
    private static final String AUTH = "5ryPFpq_wlrILBD9Ij8OmQ";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Mock
    private nl.martijndwars.webpush.PushService webPushService;
    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    private PushService pushService;

    @BeforeEach
    void setUp() {
        pushService = new PushService(webPushService, pushSubscriptionRepository, new ObjectMapper());
        ReflectionTestUtils.setField(pushService, "vapidPublicKey", "dummy-public-key");
        ReflectionTestUtils.setField(pushService, "vapidPrivateKey", "dummy-private-key");
    }

    private PushService disabledPushService() {
        PushService disabled = new PushService(webPushService, pushSubscriptionRepository, new ObjectMapper());
        ReflectionTestUtils.setField(disabled, "vapidPublicKey", "");
        ReflectionTestUtils.setField(disabled, "vapidPrivateKey", "");
        return disabled;
    }

    private PushSubscription subscription(String endpoint) {
        PushSubscription sub = new PushSubscription();
        sub.setUserId(USER_ID);
        sub.setEndpoint(endpoint);
        sub.setP256dh(P256DH);
        sub.setAuthKey(AUTH);
        return sub;
    }

    private HttpResponse responseWithStatus(int status) throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(status);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }

    @Test
    void isEnabled_falseWhenVapidKeysAreBlank() {
        org.assertj.core.api.Assertions.assertThat(disabledPushService().isEnabled()).isFalse();
    }

    @Test
    void sendPush_doesNothingWhenDisabled() {
        disabledPushService().sendPush(USER_ID, "title", "body", "chat-1");
        verify(pushSubscriptionRepository, never()).findByUserId(any());
    }

    @Test
    void sendPush_fansOutAcrossEverySubscriptionForTheUser() throws Exception {
        PushSubscription sub1 = subscription("https://push.example.com/1");
        PushSubscription sub2 = subscription("https://push.example.com/2");
        when(pushSubscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub1, sub2));
        HttpResponse ok = responseWithStatus(201);
        when(webPushService.send(any())).thenReturn(ok);

        pushService.sendPush(USER_ID, "title", "body", "chat-1");

        verify(webPushService, times(2)).send(any());
        verify(pushSubscriptionRepository, never()).deleteByEndpoint(any());
    }

    @Test
    void sendPush_prunesSubscriptionOn410Gone() throws Exception {
        PushSubscription sub = subscription("https://push.example.com/expired");
        when(pushSubscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub));
        HttpResponse gone = responseWithStatus(410);
        when(webPushService.send(any())).thenReturn(gone);

        pushService.sendPush(USER_ID, "title", "body", "chat-1");

        verify(pushSubscriptionRepository).deleteByEndpoint("https://push.example.com/expired");
    }

    @Test
    void sendPush_prunesSubscriptionOn404NotFound() throws Exception {
        PushSubscription sub = subscription("https://push.example.com/missing");
        when(pushSubscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub));
        HttpResponse notFound = responseWithStatus(404);
        when(webPushService.send(any())).thenReturn(notFound);

        pushService.sendPush(USER_ID, "title", "body", "chat-1");

        verify(pushSubscriptionRepository).deleteByEndpoint("https://push.example.com/missing");
    }

    @Test
    void sendPush_doesNotPropagateExceptionFromOneFailingSubscription() throws Exception {
        PushSubscription sub1 = subscription("https://push.example.com/broken");
        PushSubscription sub2 = subscription("https://push.example.com/ok");
        when(pushSubscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub1, sub2));
        HttpResponse ok = responseWithStatus(201);
        when(webPushService.send(any()))
                .thenThrow(new RuntimeException("network error"))
                .thenReturn(ok);

        pushService.sendPush(USER_ID, "title", "body", "chat-1");

        verify(webPushService, times(2)).send(any());
    }
}
