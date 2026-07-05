package dev.pioruocco.wacchat.call;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {

    private static final String CHAT_ID = "chat-1";
    private static final String CALLER_ID = "caller-1";
    private static final String CALLEE_ID = "callee-1";

    @Mock
    private ChatValidationClient chatValidationClient;
    @Mock
    private InternalMessageClient internalMessageClient;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private CallSessionStore sessionStore;
    private CallService callService;

    @BeforeEach
    void setUp() {
        sessionStore = new CallSessionStore();
        callService = new CallService(sessionStore, chatValidationClient, internalMessageClient, rabbitTemplate);
        ReflectionTestUtils.setField(callService, "exchangeName", "wacchat.calls");
        ReflectionTestUtils.setField(callService, "routingKey", "call");
        ReflectionTestUtils.setField(callService, "ringTimeoutSeconds", 45L);
    }

    @Test
    void invite_chatNotAccepted_isRejectedAndNoSessionCreated() {
        when(chatValidationClient.isAccepted(CALLER_ID, CALLEE_ID)).thenReturn(false);

        assertThatThrownBy(() -> callService.invite(CHAT_ID, CALLER_ID, CALLEE_ID, "AUDIO", "sdp-offer"))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(sessionStore.get(CHAT_ID)).isEmpty();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void invite_chatAccepted_createsRingingSessionAndPublishesInvite() {
        when(chatValidationClient.isAccepted(CALLER_ID, CALLEE_ID)).thenReturn(true);

        callService.invite(CHAT_ID, CALLER_ID, CALLEE_ID, "AUDIO", "sdp-offer");

        assertThat(sessionStore.get(CHAT_ID)).isPresent();
        assertThat(sessionStore.get(CHAT_ID).get().getState()).isEqualTo(CallSessionState.RINGING);

        ArgumentCaptor<CallSignalEvent> captor = ArgumentCaptor.forClass(CallSignalEvent.class);
        verify(rabbitTemplate).convertAndSend(eq("wacchat.calls"), eq("call"), captor.capture());
        CallSignalEvent event = captor.getValue();
        assertThat(event.toUserId()).isEqualTo(CALLEE_ID);
        assertThat(event.signal().type()).isEqualTo(CallSignalType.INVITE);
        assertThat(event.signal().sdp()).isEqualTo("sdp-offer");
    }

    @Test
    void answer_noSessionForChat_throwsNotFound() {
        assertThatThrownBy(() -> callService.answer(CHAT_ID, CALLEE_ID, "sdp-answer"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void answer_existingSession_movesToInCallAndNotifiesCaller() {
        when(chatValidationClient.isAccepted(CALLER_ID, CALLEE_ID)).thenReturn(true);
        callService.invite(CHAT_ID, CALLER_ID, CALLEE_ID, "AUDIO", "sdp-offer");

        callService.answer(CHAT_ID, CALLEE_ID, "sdp-answer");

        assertThat(sessionStore.get(CHAT_ID)).isPresent();
        assertThat(sessionStore.get(CHAT_ID).get().getState()).isEqualTo(CallSessionState.IN_CALL);
        assertThat(sessionStore.get(CHAT_ID).get().getAnsweredAt()).isNotNull();

        ArgumentCaptor<CallSignalEvent> captor = ArgumentCaptor.forClass(CallSignalEvent.class);
        verify(rabbitTemplate, times(2)).convertAndSend(eq("wacchat.calls"), eq("call"), captor.capture());
        CallSignalEvent answerEvent = captor.getAllValues().get(1);
        assertThat(answerEvent.toUserId()).isEqualTo(CALLER_ID);
        assertThat(answerEvent.signal().type()).isEqualTo(CallSignalType.ANSWER);
    }

    @Test
    void end_fromInCall_removesSessionAndLeavesDurationSystemMessage() {
        when(chatValidationClient.isAccepted(CALLER_ID, CALLEE_ID)).thenReturn(true);
        callService.invite(CHAT_ID, CALLER_ID, CALLEE_ID, "AUDIO", "sdp-offer");
        callService.answer(CHAT_ID, CALLEE_ID, "sdp-answer");

        callService.end(CHAT_ID, CALLER_ID, "HANGUP");

        assertThat(sessionStore.get(CHAT_ID)).isEmpty();
        verify(internalMessageClient).sendSystemMessage(eq(CHAT_ID), eq(CALLER_ID), eq(CALLEE_ID),
                eq("📞 Chiamata terminata - durata 00:00"));
    }

    @Test
    void end_whileStillRinging_leavesMissedCallSystemMessage() {
        when(chatValidationClient.isAccepted(CALLER_ID, CALLEE_ID)).thenReturn(true);
        callService.invite(CHAT_ID, CALLER_ID, CALLEE_ID, "AUDIO", "sdp-offer");

        callService.end(CHAT_ID, CALLEE_ID, "REJECT");

        verify(internalMessageClient).sendSystemMessage(CHAT_ID, CALLER_ID, CALLEE_ID, "📞 Chiamata persa");
    }

    @Test
    void sweepRingTimeouts_ringingPastTimeout_isMarkedMissedAndBothPeersNotified() {
        when(chatValidationClient.isAccepted(CALLER_ID, CALLEE_ID)).thenReturn(true);
        callService.invite(CHAT_ID, CALLER_ID, CALLEE_ID, "AUDIO", "sdp-offer");
        // Backdate ringingSince past the 45s timeout without waiting in the test.
        ReflectionTestUtils.setField(sessionStore.get(CHAT_ID).orElseThrow(), "ringingSince",
                Instant.now().minus(Duration.ofSeconds(46)));

        callService.sweepRingTimeouts();

        assertThat(sessionStore.get(CHAT_ID)).isEmpty();
        ArgumentCaptor<CallSignalEvent> captor = ArgumentCaptor.forClass(CallSignalEvent.class);
        verify(rabbitTemplate, times(3)).convertAndSend(eq("wacchat.calls"), eq("call"), captor.capture());
        assertThat(captor.getAllValues().get(1).signal().type()).isEqualTo(CallSignalType.MISSED);
        assertThat(captor.getAllValues().get(2).signal().type()).isEqualTo(CallSignalType.MISSED);
        verify(internalMessageClient).sendSystemMessage(CHAT_ID, CALLER_ID, CALLEE_ID, "📞 Chiamata persa");
    }

    @Test
    void sweepRingTimeouts_stillWithinTimeout_leavesSessionUntouched() {
        when(chatValidationClient.isAccepted(CALLER_ID, CALLEE_ID)).thenReturn(true);
        callService.invite(CHAT_ID, CALLER_ID, CALLEE_ID, "AUDIO", "sdp-offer");

        callService.sweepRingTimeouts();

        assertThat(sessionStore.get(CHAT_ID)).isPresent();
    }
}
