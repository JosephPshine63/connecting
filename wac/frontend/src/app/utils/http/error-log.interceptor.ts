import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { ErrorLogService } from '../error-log/error-log.service';

// Set on a request's HttpContext to opt out of error-log reporting for failures that
// are expected noise at the call site (e.g. a 404 on a late WebRTC ICE candidate,
// already handled/swallowed there) — the error still propagates, it's just not logged.
export const SILENT_ERROR = new HttpContextToken<boolean>(() => false);

export const errorLogInterceptor: HttpInterceptorFn = (req, next) => {
  const errorLog = inject(ErrorLogService);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && !req.context.get(SILENT_ERROR)) {
        errorLog.report({
          source: 'http',
          message: err.error?.message || err.message,
          status: err.status,
          url: req.url
        });
      }
      return throwError(() => err);
    })
  );
};
