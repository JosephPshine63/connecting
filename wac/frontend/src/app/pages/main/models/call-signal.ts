// Hand-written, mirroring ./notification.ts — call-service isn't part of the
// ng-openapi-gen pipeline (that only covers the backend's own OpenAPI spec), so this
// payload shape is kept in sync with call-service's CallSignal record by hand.
export interface CallSignal {
  chatId?: string;
  fromUserId?: string;
  type?: 'INVITE' | 'ANSWER' | 'ICE_CANDIDATE' | 'END' | 'REJECT' | 'BUSY' | 'MISSED';
  callType?: 'AUDIO' | 'VIDEO';
  sdp?: string;
  candidate?: string;
  candidateSdpMid?: string;
  candidateSdpMLineIndex?: number;
}
