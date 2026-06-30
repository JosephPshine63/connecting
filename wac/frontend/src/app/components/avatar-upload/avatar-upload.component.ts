import { Component, EventEmitter, Input, Output } from '@angular/core';
import { UserService } from '../../services/services/user.service';

const ALLOWED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
const MAX_SIZE_BYTES = 5 * 1024 * 1024;

@Component({
  selector: 'app-avatar-upload',
  templateUrl: './avatar-upload.component.html',
  styleUrl: './avatar-upload.component.scss',
  imports: []
})
export class AvatarUploadComponent {

  @Input() currentAvatarUrl?: string;
  @Output() avatarChanged = new EventEmitter<string | undefined>();
  @Output() closed = new EventEmitter<void>();

  uploading = false;
  errorMessage = '';

  constructor(private userService: UserService) {}

  close(): void {
    this.closed.emit();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    if (!ALLOWED_TYPES.includes(file.type)) {
      this.errorMessage = 'Formato non supportato. Usa jpg, png o webp.';
      input.value = '';
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      this.errorMessage = "L'immagine supera i 5MB.";
      input.value = '';
      return;
    }

    this.errorMessage = '';
    this.uploading = true;
    this.userService.uploadAvatar({ body: { file } }).subscribe({
      next: user => {
        this.uploading = false;
        this.avatarChanged.emit(user.avatarUrl);
      },
      error: () => {
        this.uploading = false;
        this.errorMessage = 'Caricamento non riuscito. Riprova.';
      }
    });
  }

  removeAvatar(): void {
    this.uploading = true;
    this.userService.deleteAvatar().subscribe({
      next: () => {
        this.uploading = false;
        this.avatarChanged.emit(undefined);
      },
      error: () => {
        this.uploading = false;
        this.errorMessage = 'Rimozione non riuscita. Riprova.';
      }
    });
  }
}
