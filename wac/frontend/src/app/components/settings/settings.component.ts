import { Component, EventEmitter, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ThemeService, ThemeId } from '../../utils/theme/theme.service';

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
  ];

  constructor(protected themeService: ThemeService) {}

  close(): void {
    this.closed.emit();
  }

  onReportBug(): void {
    this.reportBug.emit();
  }
}
