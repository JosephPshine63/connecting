import {HttpErrorResponse, HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {from, throwError} from 'rxjs';
import {catchError, switchMap, tap} from 'rxjs/operators';
import {KeycloakService} from '../keycloak/keycloak.service';
import {SessionGuardService} from '../session/session-guard.service';

export const keycloakHttpInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloakService = inject(KeycloakService);
  const sessionGuard = inject(SessionGuardService);

  // Refresh the token if it expires within 30 seconds before attaching it
  return from(keycloakService.keycloak.updateToken(30).catch(() => false)).pipe(
    switchMap(() => {
      const token = keycloakService.keycloak.token;
      if (token) {
        const authReq = req.clone({
          headers: req.headers.set('Authorization', `Bearer ${token}`)
        });
        return next(authReq);
      }
      return next(req);
    }),
    tap({
      next: () => sessionGuard.markUnblocked(),
    }),
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 409 && err.error?.code === 'SESSION_CONFLICT') {
        sessionGuard.markBlocked();
        return throwError(() => err);
      }
      return next(req);
    })
  );
};
