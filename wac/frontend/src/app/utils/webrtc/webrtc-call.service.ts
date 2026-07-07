import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';

export interface LocalIceCandidate {
  peerId: string;
  candidate: string;
  sdpMid: string | null;
  sdpMLineIndex: number | null;
}

interface PendingCandidate {
  candidate: string;
  sdpMid: string | null;
  sdpMLineIndex: number | null;
}

interface PeerLink {
  pc: RTCPeerConnection;
  // The remote pc's answer/offer isn't set yet the moment ICE candidates start trickling
  // in (candidate generation begins right after createOffer/createAnswer, independent of
  // when the remote description actually gets applied) — candidates that arrive before
  // then must be queued and replayed once one exists, or they're silently dropped and
  // that specific peer connection never completes ICE negotiation.
  remoteDescriptionSet: boolean;
  pendingRemoteCandidates: PendingCandidate[];
}

/**
 * Thin wrapper around RTCPeerConnection/getUserMedia for mesh calls: one direct
 * RTCPeerConnection per other participant (Map<peerId, PeerLink>), all sharing the same
 * local getUserMedia stream. A 1:1 call is simply the case where this map has one entry.
 * Public STUN only (no TURN in v1 — see the roadmap plan) via Google's public STUN
 * server, which covers most NAT setups; symmetric-NAT/corporate-firewall cases are an
 * explicitly deferred fast-follow (coturn).
 */
@Injectable({ providedIn: 'root' })
export class WebRtcCallService {

  private peers = new Map<string, PeerLink>();
  // ICE candidates (or even a peer-offer/peer-answer signal) can arrive for a peerId
  // before its PeerLink has been created at all — e.g. a PARTICIPANT_JOINED bootstrap
  // racing a slightly-delayed ICE_CANDIDATE for the same new peer. Buffered here until
  // createPeerLink(peerId) claims them.
  private preLinkCandidates = new Map<string, PendingCandidate[]>();
  private localMediaStream: MediaStream | null = null;

  readonly remoteStreams$ = new BehaviorSubject<Map<string, MediaStream>>(new Map());
  readonly localIceCandidate$ = new Subject<LocalIceCandidate>();

  get localStream(): MediaStream | null {
    return this.localMediaStream;
  }

  async createOfferFor(peerId: string, callType: 'AUDIO' | 'VIDEO'): Promise<string> {
    await this.ensureLocalMedia(callType);
    const link = this.getOrCreateLink(peerId);
    const offer = await link.pc.createOffer();
    await link.pc.setLocalDescription(offer);
    return offer.sdp ?? '';
  }

  async createAnswerFor(peerId: string, callType: 'AUDIO' | 'VIDEO', offerSdp: string): Promise<string> {
    await this.ensureLocalMedia(callType);
    const link = this.getOrCreateLink(peerId);
    await link.pc.setRemoteDescription({ type: 'offer', sdp: offerSdp });
    await this.onRemoteDescriptionSet(peerId);
    const answer = await link.pc.createAnswer();
    await link.pc.setLocalDescription(answer);
    return answer.sdp ?? '';
  }

  async setRemoteAnswer(peerId: string, answerSdp: string): Promise<void> {
    const link = this.peers.get(peerId);
    if (!link) return;
    await link.pc.setRemoteDescription({ type: 'answer', sdp: answerSdp });
    await this.onRemoteDescriptionSet(peerId);
  }

  async addRemoteIceCandidate(peerId: string, candidate: string, sdpMid: string | null, sdpMLineIndex: number | null): Promise<void> {
    if (!candidate) return;
    const pending: PendingCandidate = { candidate, sdpMid, sdpMLineIndex };
    const link = this.peers.get(peerId);
    if (!link) {
      const queue = this.preLinkCandidates.get(peerId) ?? [];
      queue.push(pending);
      this.preLinkCandidates.set(peerId, queue);
      return;
    }
    if (!link.remoteDescriptionSet) {
      link.pendingRemoteCandidates.push(pending);
      return;
    }
    await link.pc.addIceCandidate({ candidate, sdpMid: sdpMid ?? undefined, sdpMLineIndex: sdpMLineIndex ?? undefined });
  }

  isConnectedTo(peerId: string): boolean {
    return this.peers.has(peerId);
  }

  closePeer(peerId: string): void {
    const link = this.peers.get(peerId);
    if (link) {
      link.pc.close();
      this.peers.delete(peerId);
    }
    this.preLinkCandidates.delete(peerId);
    const streams = new Map(this.remoteStreams$.value);
    if (streams.delete(peerId)) {
      this.remoteStreams$.next(streams);
    }
  }

  setMuted(muted: boolean): void {
    this.localMediaStream?.getAudioTracks().forEach(track => track.enabled = !muted);
  }

  close(): void {
    this.localMediaStream?.getTracks().forEach(track => track.stop());
    this.localMediaStream = null;
    this.peers.forEach(link => link.pc.close());
    this.peers.clear();
    this.preLinkCandidates.clear();
    this.remoteStreams$.next(new Map());
  }

  private async onRemoteDescriptionSet(peerId: string): Promise<void> {
    const link = this.peers.get(peerId);
    if (!link) return;
    link.remoteDescriptionSet = true;
    const queued = link.pendingRemoteCandidates;
    link.pendingRemoteCandidates = [];
    for (const c of queued) {
      await link.pc.addIceCandidate({ candidate: c.candidate, sdpMid: c.sdpMid ?? undefined, sdpMLineIndex: c.sdpMLineIndex ?? undefined });
    }
  }

  private getOrCreateLink(peerId: string): PeerLink {
    const existing = this.peers.get(peerId);
    if (existing) return existing;

    const pc = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    });
    this.localMediaStream!.getTracks().forEach(track => pc.addTrack(track, this.localMediaStream!));

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.localIceCandidate$.next({
          peerId,
          candidate: event.candidate.candidate,
          sdpMid: event.candidate.sdpMid,
          sdpMLineIndex: event.candidate.sdpMLineIndex
        });
      }
    };

    pc.ontrack = (event) => {
      const streams = new Map(this.remoteStreams$.value);
      streams.set(peerId, event.streams[0] ?? new MediaStream());
      this.remoteStreams$.next(streams);
    };

    const link: PeerLink = {
      pc,
      remoteDescriptionSet: false,
      pendingRemoteCandidates: this.preLinkCandidates.get(peerId) ?? []
    };
    this.preLinkCandidates.delete(peerId);
    this.peers.set(peerId, link);
    return link;
  }

  private async ensureLocalMedia(callType: 'AUDIO' | 'VIDEO'): Promise<void> {
    if (this.localMediaStream) return;
    this.localMediaStream = await navigator.mediaDevices.getUserMedia({
      audio: true,
      video: callType === 'VIDEO'
    });
  }
}
