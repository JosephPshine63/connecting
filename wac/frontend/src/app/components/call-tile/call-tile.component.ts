import { AfterViewChecked, Component, ElementRef, Input, ViewChild } from '@angular/core';

@Component({
  selector: 'app-call-tile',
  templateUrl: './call-tile.component.html',
  styleUrl: './call-tile.component.scss',
  imports: []
})
export class CallTileComponent implements AfterViewChecked {
  @Input() name: string | null = null;
  @Input() avatarUrl: string | null = null;
  @Input() callType: 'AUDIO' | 'VIDEO' = 'AUDIO';
  @Input() stream: MediaStream | null = null;
  @Input() muted = false;
  @Input() statusLabel: string | null = null;
  @Input() isLocal = false;

  @ViewChild('videoEl') videoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('audioEl') audioRef?: ElementRef<HTMLAudioElement>;

  // Same "re-check every view check" approach the pre-mesh CallComponent used for its
  // single local/remote video: a plain ViewChild lookup right after this.stream changes
  // (e.g. a peer joining mid-call, or this tile only just entering the @for list) can
  // race the template's own re-render, so the srcObject assignment is redone on every
  // check instead of only reacting to an @Input change event.
  ngAfterViewChecked(): void {
    if (this.videoRef && this.videoRef.nativeElement.srcObject !== this.stream) {
      this.videoRef.nativeElement.srcObject = this.stream;
    }
    if (this.audioRef && this.audioRef.nativeElement.srcObject !== this.stream) {
      this.audioRef.nativeElement.srcObject = this.stream;
    }
  }

  get initial(): string {
    return (this.name || '?').charAt(0).toUpperCase();
  }
}
