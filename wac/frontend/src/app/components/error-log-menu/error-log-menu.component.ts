import { Component, HostListener } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AppErrorEntry, ErrorLogService } from '../../utils/error-log/error-log.service';

@Component({
  selector: 'app-error-log-menu',
  imports: [DatePipe],
  templateUrl: './error-log-menu.component.html',
  styleUrl: './error-log-menu.component.scss'
})
export class ErrorLogMenuComponent {

  open = false;

  constructor(readonly errorLog: ErrorLogService) {}

  toggle(event: Event): void {
    event.stopPropagation();
    this.open = !this.open;
    if (this.open) {
      this.errorLog.markSeen();
    }
  }

  clear(event: Event): void {
    event.stopPropagation();
    this.errorLog.clear();
  }

  sourceLabel(entry: AppErrorEntry): string {
    return entry.source === 'http' ? `HTTP ${entry.status ?? ''}`.trim() : 'Client';
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.open = false;
  }
}
