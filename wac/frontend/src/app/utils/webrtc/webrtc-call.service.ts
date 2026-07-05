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
    const answer = await this.pc!.createAnswer();
    await this.pc!.setLocalDescription(answer);
    return answer.sdp ?? '';
  }

  async completeAsCaller(answerSdp: string): Promise<void> {
    if (!this.pc) return;
    await this.pc.setRemoteDescription({ type: 'answer', sdp: answerSdp });
  }

  async addRemoteIceCandidate(candidate: string, sdpMid: string | null, sdpMLineIndex: number | null): Promise<void> {
    if (!this.pc || !candidate) return;
    await this.pc.addIceCandidate({ candidate, sdpMid: sdpMid ?? undefined, sdpMLineIndex: sdpMLineIndex ?? undefined });
  }

  setMuted(muted: boolean): void {
    this.localMediaStream?.getAudioTracks().forEach(track => track.enabled = !muted);
  }

  close(): void {
    this.localMediaStream?.getTracks().forEach(track => track.stop());
    this.localMediaStream = null;
    this.pc?.close();
    this.pc = null;
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
