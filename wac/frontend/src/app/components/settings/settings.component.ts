import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ThemeService, ThemeId } from '../../utils/theme/theme.service';
import { ChatBackgroundService, ChatBackgroundId } from '../../utils/chat-background/chat-background.service';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
  imports: [FormsModule]
})
export class SettingsComponent {
  @Output() closed = new EventEmitter<void>();
  @Output() reportBug = new EventEmitter<void>();

  readonly themes: { id: ThemeId; label: string }[] = [
    { id: 'pio-light', label: 'Pio Light' },
    { id: 'pio-dark', label: 'Pio Dark' },
    { id: 'blue', label: 'Blu classico' },
    { id: 'light', label: 'Bianco / Chiaro' },
    { id: 'dark', label: 'Nero / Scuro' },
    { id: 'green', label: 'Verde' },
    { id: 'indigo', label: 'Indaco' },
    { id: 'magenta', label: 'Magenta' },
    { id: 'crimson', label: 'Rosso cardinale' },
    { id: 'earth', label: 'Terra' },
  ];

  readonly chatBackgrounds: { id: ChatBackgroundId; label: string }[] = [
    { id: 'none', label: 'Nessuno' },
    { id: 'birds-solid', label: 'Uccellini' },
    { id: 'birds-red', label: 'Uccellini rossi' },
    { id: 'birds-outline', label: 'Uccellini contorno' },
  ];

  constructor(
    protected themeService: ThemeService,
    protected chatBackgroundService: ChatBackgroundService,
  ) {}

  close(): void {
    this.closed.emit();
  }

  onReportBug(): void {
    this.reportBug.emit();
  }
}
