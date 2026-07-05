import {
  AfterViewChecked,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { Subscription } from 'rxjs';
import { WebRtcCallService } from '../../utils/webrtc/webrtc-call.service';

@Component({
  selector: 'app-call',
  templateUrl: './call.component.html',
  styleUrl: './call.component.scss'
})
export class CallComponent implements OnChanges, AfterViewChecked, OnDestroy {

  // Includes 'idle' purely so the type matches MainComponent's broader field as-is —
  // the parent only renders <app-call> at all once callState !== 'idle' (see
  // main.component.html), so none of this component's template branches ever match it.
  @Input() callState!: 'idle' | 'incoming' | 'outgoing' | 'in-call';
  @Input() peerName: string | null = null;
  @Input() peerAvatarUrl: string | null = null;
  @Input() callType: 'AUDIO' | 'VIDEO' = 'AUDIO';
  @Output() accepted = new EventEmitter<void>();
  @Output() rejected = new EventEmitter<void>();
  @Output() hungUp = new EventEmitter<void>();

  @ViewChild('localVideo') localVideoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('remoteVideo') remoteVideoRef?: ElementRef<HTMLVideoElement>;
  @ViewChild('remoteAudio') remoteAudioRef?: ElementRef<HTMLAudioElement>;

  muted = false;
  elapsedLabel = '00:00';
  private elapsedSeconds = 0;
  private timerHandle: ReturnType<typeof setInterval> | null = null;
  private readonly remoteStreamSub: Subscription;

  constructor(private webRtcCallService: WebRtcCallService) {
    this.remoteStreamSub = this.webRtcCallService.remoteStream$.subscribe(stream => {
      if (this.remoteVideoRef) {
        this.remoteVideoRef.nativeElement.srcObject = stream;
      }
      if (this.remoteAudioRef) {
        this.remoteAudioRef.nativeElement.srcObject = stream;
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['callState']) {
      if (this.callState === 'in-call') {
        this.startTimer();
      } else {
        this.stopTimer();
      }
    }
  }

  // Local/remote <video>/<audio> elements only exist in the DOM for whichever branch of
  // the template is currently active (incoming vs. outgoing/in-call, audio vs. video) —
  // same "re-check every view check" approach MainComponent already uses for
  // scrollToBottom(), since a plain ViewChild lookup right after a state flip can race
  // the template's own re-render.
  ngAfterViewChecked(): void {
    if (this.localVideoRef && this.localVideoRef.nativeElement.srcObject !== this.webRtcCallService.localStream) {
      this.localVideoRef.nativeElement.srcObject = this.webRtcCallService.localStream;
    }
  }

  ngOnDestroy(): void {
    this.stopTimer();
    this.remoteStreamSub.unsubscribe();
  }

  toggleMute(): void {
    this.muted = !this.muted;
    this.webRtcCallService.setMuted(this.muted);
  }

  private startTimer(): void {
    if (this.timerHandle !== null) return;
    this.elapsedSeconds = 0;
    this.elapsedLabel = '00:00';
    this.timerHandle = setInterval(() => {
      this.elapsedSeconds++;
      const mins = Math.floor(this.elapsedSeconds / 60);
      const secs = this.elapsedSeconds % 60;
      this.elapsedLabel = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timerHandle !== null) {
      clearInterval(this.timerHandle);
      this.timerHandle = null;
    }
  }
}
