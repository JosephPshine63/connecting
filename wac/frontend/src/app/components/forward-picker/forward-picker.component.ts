import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ChatResponse } from '../../services/models/chat-response';

@Component({
  selector: 'app-forward-picker',
  templateUrl: './forward-picker.component.html',
  styleUrl: './forward-picker.component.scss'
})
export class ForwardPickerComponent {

  @Input() chats: ChatResponse[] = [];
  @Output() closed = new EventEmitter<void>();
  @Output() chatChosen = new EventEmitter<ChatResponse>();

  close(): void {
    this.closed.emit();
  }

  choose(chat: ChatResponse): void {
    this.chatChosen.emit(chat);
  }
}
