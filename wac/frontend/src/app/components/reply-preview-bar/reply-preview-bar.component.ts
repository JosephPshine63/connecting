import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MessageResponse } from '../../services/models/message-response';

@Component({
  selector: 'app-reply-preview-bar',
  templateUrl: './reply-preview-bar.component.html',
  styleUrl: './reply-preview-bar.component.scss'
})
export class ReplyPreviewBarComponent {

  @Input() message: MessageResponse | null = null;
  @Output() cancelled = new EventEmitter<void>();

  previewText(): string {
    if (!this.message) return '';
    switch (this.message.type) {
      case 'VIDEO': return '🎥 Video';
      case 'AUDIO': return '🎤 Messaggio vocale';
      case 'IMAGE': return '📷 Foto';
      default: return this.message.content ?? '';
    }
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
