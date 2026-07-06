import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';

export interface LocalIceCandidate {
  candidate: string;
  sdpMid: string | null;
  sdpMLineIndex: number | null;
}

/**
 * Thin wrapper around RTCPeerConnection/getUserMedia for 1:1 calls. Public STUN only
 * (no TURN in v1 — see the roadmap plan) via Google's public STUN server, which covers
 * most NAT setups; symmetric-NAT/corporate-firewall cases are an explicitly deferred
 * fast-follow (coturn).
 */
@Injectable({ providedIn: 'root' })
export class WebRtcCallService {

  private pc: RTCPeerConnection | null = null;
  private localMediaStream: MediaStream | null = null;
  // The callee's pc doesn't exist yet while the call is ringing (only created in
  // startAsCallee, on accept), but the caller trickles ICE candidates the moment it
  // creates its offer — well before a human reacts to a ringing call. Likewise the
  // caller's pc exists from the start but its remote description (the answer) isn't
  // set until the ANSWER signal arrives, which can lose a race against the callee's
  // own trickled candidates sent right after createAnswer. Either way, candidates
  // that arrive before there's a pc with a remote description set must be queued and
  // replayed once one exists, or they're silently dropped and the connection never
  // completes ICE negotiation (remote track never arrives).
  private remoteDescriptionSet = false;
  private pendingRemoteCandidates: LocalIceCandidate[] = [];

  readonly remoteStream$ = new BehaviorSubject<MediaStream | null>(null);
  readonly localIceCandidate$ = new Subject<LocalIceCandidate>();

  get localStream(): MediaStream | null {
    return this.localMediaStream;
  }

  async startAsCaller(callType: 'AUDIO' | 'VIDEO'): Promise<string> {
    await this.setUpLocalMediaAndPeerConnection(callType);
    const offer = await this.pc!.createOffer();
    await this.pc!.setLocalDescription(offer);
    return offer.sdp ?? '';
  }

  async startAsCallee(callType: 'AUDIO' | 'VIDEO', offerSdp: string): Promise<string> {
    await this.setUpLocalMediaAndPeerConnection(callType);
    await this.pc!.setRemoteDescription({ type: 'offer', sdp: offerSdp });
    await this.onRemoteDescriptionSet();
    const answer = await this.pc!.createAnswer();
    await this.pc!.setLocalDescription(answer);
    return answer.sdp ?? '';
  }

  async completeAsCaller(answerSdp: string): Promise<void> {
    if (!this.pc) return;
    await this.pc.setRemoteDescription({ type: 'answer', sdp: answerSdp });
    await this.onRemoteDescriptionSet();
  }

  async addRemoteIceCandidate(candidate: string, sdpMid: string | null, sdpMLineIndex: number | null): Promise<void> {
    if (!candidate) return;
    if (!this.pc || !this.remoteDescriptionSet) {
      this.pendingRemoteCandidates.push({ candidate, sdpMid, sdpMLineIndex });
      return;
    }
    await this.pc.addIceCandidate({ candidate, sdpMid: sdpMid ?? undefined, sdpMLineIndex: sdpMLineIndex ?? undefined });
  }

  private async onRemoteDescriptionSet(): Promise<void> {
    this.remoteDescriptionSet = true;
    const queued = this.pendingRemoteCandidates;
    this.pendingRemoteCandidates = [];
    for (const c of queued) {
      await this.pc!.addIceCandidate({ candidate: c.candidate, sdpMid: c.sdpMid ?? undefined, sdpMLineIndex: c.sdpMLineIndex ?? undefined });
    }
  }

  setMuted(muted: boolean): void {
    this.localMediaStream?.getAudioTracks().forEach(track => track.enabled = !muted);
  }

  close(): void {
    this.localMediaStream?.getTracks().forEach(track => track.stop());
    this.localMediaStream = null;
    this.pc?.close();
    this.pc = null;
    this.remoteDescriptionSet = false;
    this.pendingRemoteCandidates = [];
    this.remoteStream$.next(null);
  }

  private async setUpLocalMediaAndPeerConnection(callType: 'AUDIO' | 'VIDEO'): Promise<void> {
    this.localMediaStream = await navigator.mediaDevices.getUserMedia({
      audio: true,
      video: callType === 'VIDEO'
    });

    this.pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    });

    this.localMediaStream.getTracks().forEach(track => this.pc!.addTrack(track, this.localMediaStream!));

    this.pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.localIceCandidate$.next({
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex
        });
      }
    };

    this.pc.ontrack = (event) => {
      this.remoteStream$.next(event.streams[0] ?? null);
    };
  }
}
