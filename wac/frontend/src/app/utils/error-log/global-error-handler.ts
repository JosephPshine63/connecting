import { ErrorHandler, Injectable, Injector } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorLogService } from './error-log.service';

/**
 * Catches uncaught client-side exceptions (template/change-detection errors, thrown
 * promises, etc.) that never go through HttpClient — those are already captured with
 * richer context (status/url) by errorLogInterceptor, so HttpErrorResponse is skipped
 * here to avoid double-reporting the same failure.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {

  constructor(private injector: Injector) {}

  handleError(error: unknown): void {
    console.error(error);
    if (error instanceof HttpErrorResponse) return;
    const errorLog = this.injector.get(ErrorLogService);
    const message = error instanceof Error ? error.message : String(error);
    errorLog.report({ source: 'client', message });
  }
}
