import {HttpInterceptorFn} from '@angular/common/http';
import {inject} from '@angular/core';
import {from} from 'rxjs';
import {catchError, switchMap} from 'rxjs/operators';
import {KeycloakService} from '../keycloak/keycloak.service';

export const keycloakHttpInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloakService = inject(KeycloakService);

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
    catchError(() => next(req))
  );
};
