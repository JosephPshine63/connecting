import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SILENT_ERROR } from '../http/error-log.interceptor';

// Hand-written, mirroring utils/username/username.service.ts — call-service isn't part
// of the ng-openapi-gen pipeline (that only points at the backend's own OpenAPI spec),
// so this client is written directly against the gateway-routed REST endpoints.
// keycloakHttpInterceptor already attaches Authorization/X-Tab-Id to every HttpClient
// request regardless of target path, so no extra auth wiring is needed here.
@Injectable({ providedIn: 'root' })
export class CallApiService {

  constructor(private http: HttpClient) {}

  invite(chatId: string, peerId: string, callType: 'AUDIO' | 'VIDEO', sdpOffer: string): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/invite`, { peerId, callType, sdpOffer });
  }

  answer(chatId: string, sdpAnswer: string): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/answer`, { sdpAnswer });
  }

  // A 404 here means the call session was already torn down server-side (hangup/reject/
  // ring-timeout) while the browser was still trickling ICE candidates — expected noise
  // for a late candidate, not an error worth surfacing.
  iceCandidate(chatId: string, candidate: string, sdpMid: string | null, sdpMLineIndex: number | null): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/ice-candidate`, { candidate, sdpMid, sdpMLineIndex }, {
      context: new HttpContext().set(SILENT_ERROR, true)
    }).pipe(
      catchError((err: unknown) => err instanceof HttpErrorResponse && err.status === 404 ? EMPTY : throwError(() => err))
    );
  }

  end(chatId: string, reason: 'HANGUP' | 'REJECT'): Observable<void> {
    return this.http.post<void>(`/api/v1/calls/${chatId}/end`, { reason });
  }
}
