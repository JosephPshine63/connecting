import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SILENT_ERROR } from '../http/error-log.interceptor';

export interface InviteeOffer {
  peerId: string;
  sdpOffer: string;
}

// Hand-written, mirroring utils/username/username.service.ts — call-service isn't part
// of the ng-openapi-gen pipeline (that only points at the backend's own OpenAPI spec),
// so this client is written directly against the gateway-routed REST endpoints.
// keycloakHttpInterceptor already attaches Authorization/X-Tab-Id to every HttpClient
// request regardless of target path, so no extra auth wiring is needed here. A 1:1 call
// is simply a single-element invitees list (mesh topology, see the roadmap plan).
@Injectable({ providedIn: 'root' })
export class CallApiService {

  constructor(private http: HttpClient) {}

  invite(chatId: string, invitees: InviteeOffer[], callType: 'AUDIO' | 'VIDEO'): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/invite`, { invitees, callType });
  }

  answer(chatId: string, sdpAnswer: string): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/answer`, { sdpAnswer });
  }

  peerOffer(chatId: string, peerId: string, sdpOffer: string): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/peer-offer`, { peerId, sdpOffer });
  }

  peerAnswer(chatId: string, peerId: string, sdpAnswer: string): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/peer-answer`, { peerId, sdpAnswer });
  }

  // A 404 here means the call session (or that one participant) was already torn down
  // server-side (hangup/reject/ring-timeout) while the browser was still trickling ICE
  // candidates — expected noise for a late candidate, not an error worth surfacing.
  iceCandidate(chatId: string, peerId: string, candidate: string, sdpMid: string | null, sdpMLineIndex: number | null): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/ice-candidate`, { peerId, candidate, sdpMid, sdpMLineIndex }, {
      context: new HttpContext().set(SILENT_ERROR, true)
    }).pipe(
      catchError((err: unknown) => err instanceof HttpErrorResponse && err.status === 404 ? EMPTY : throwError(() => err))
    );
  }

  end(chatId: string, reason: 'HANGUP' | 'REJECT'): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/end`, { reason });
  }
}
