import { Component, ElementRef, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';

// Rough max height of the dropdown (5 items × ~36px + padding) — used as the
// threshold to decide whether there's enough room below the button to open
// downward, or whether it should flip upward instead.
const DROPDOWN_MAX_HEIGHT = 220;

@Component({
  selector: 'app-message-actions-menu',
  templateUrl: './message-actions-menu.component.html',
  styleUrl: './message-actions-menu.component.scss'
})
export class MessageActionsMenuComponent implements OnChanges {

  @Input() open = false;
  @Input() canForward = true;
  @Input() canCopy = true;
  @Input() canEdit = false;
  @Input() canDelete = false;
  @Input() starred = false;
  @Output() toggle = new EventEmitter<void>();
  @Output() closeRequested = new EventEmitter<void>();
  @Output() replyRequested = new EventEmitter<void>();
  @Output() forwardRequested = new EventEmitter<void>();
  @Output() copyRequested = new EventEmitter<void>();
  @Output() editRequested = new EventEmitter<void>();
  @Output() deleteRequested = new EventEmitter<void>();
  @Output() reactRequested = new EventEmitter<void>();
  @Output() starRequested = new EventEmitter<void>();

  @ViewChild('toggleBtn') toggleBtn?: ElementRef<HTMLButtonElement>;

  opensUpward = false;

  // Recomputed whenever the menu transitions to open — covers both the ⋮
  // button toggle and right-click, which sets `open` directly on the parent
  // without going through onToggleClick().
  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      this.opensUpward = this.computeOpensUpward();
    }
  }

  onToggleClick(event: Event): void {
    event.stopPropagation();
    this.toggle.emit();
  }

  // The dropdown is clipped by the scrollable messages container if there
  // isn't enough room below the button — happens on short bubbles sitting
  // near the bottom of the chat. Flip it upward in that case.
  private computeOpensUpward(): boolean {
    const btn = this.toggleBtn?.nativeElement;
    if (!btn) return false;
    const rect = btn.getBoundingClientRect();
    const scrollContainer = btn.closest('.messages-area');
    const boundaryBottom = scrollContainer
      ? scrollContainer.getBoundingClientRect().bottom
      : window.innerHeight;
    return boundaryBottom - rect.bottom < DROPDOWN_MAX_HEIGHT;
  }

  onReply(event: Event): void {
    event.stopPropagation();
    this.replyRequested.emit();
    this.closeRequested.emit();
  }

  onForward(event: Event): void {
    event.stopPropagation();
    this.forwardRequested.emit();
    this.closeRequested.emit();
  }

  onCopy(event: Event): void {
    event.stopPropagation();
    this.copyRequested.emit();
    this.closeRequested.emit();
  }

  onEdit(event: Event): void {
    event.stopPropagation();
    this.editRequested.emit();
    this.closeRequested.emit();
  }

  onDelete(event: Event): void {
    event.stopPropagation();
    this.deleteRequested.emit();
    this.closeRequested.emit();
  }

  onReact(event: Event): void {
    event.stopPropagation();
    this.reactRequested.emit();
    this.closeRequested.emit();
  }

  onStar(event: Event): void {
    event.stopPropagation();
    this.starRequested.emit();
    this.closeRequested.emit();
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.open) {
      this.closeRequested.emit();
    }
  }
}
