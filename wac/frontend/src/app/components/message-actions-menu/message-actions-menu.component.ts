import { Component, EventEmitter, HostListener, Input, Output } from '@angular/core';

@Component({
  selector: 'app-message-actions-menu',
  templateUrl: './message-actions-menu.component.html',
  styleUrl: './message-actions-menu.component.scss'
})
export class MessageActionsMenuComponent {

  @Input() open = false;
  @Input() canForward = true;
  @Input() canCopy = true;
  @Input() canEdit = false;
  @Input() canDelete = false;
  @Output() toggle = new EventEmitter<void>();
  @Output() closeRequested = new EventEmitter<void>();
  @Output() replyRequested = new EventEmitter<void>();
  @Output() forwardRequested = new EventEmitter<void>();
  @Output() copyRequested = new EventEmitter<void>();
  @Output() editRequested = new EventEmitter<void>();
  @Output() deleteRequested = new EventEmitter<void>();
  @Output() reactRequested = new EventEmitter<void>();

  onToggleClick(event: Event): void {
    event.stopPropagation();
    this.toggle.emit();
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

  @HostListener('document:click')
  onDocumentClick(): void {
    if (this.open) {
      this.closeRequested.emit();
    }
  }
}
