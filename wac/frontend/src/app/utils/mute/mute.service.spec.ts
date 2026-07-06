import { MuteService } from './mute.service';

describe('MuteService', () => {
  let service: MuteService;

  beforeEach(() => {
    localStorage.clear();
    service = new MuteService();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('reports an unknown chat as not muted', () => {
    expect(service.isMuted('chat-1')).toBe(false);
  });

  it('toggles a chat to muted and back', () => {
    service.toggleMute('chat-1');
    expect(service.isMuted('chat-1')).toBe(true);
    service.toggleMute('chat-1');
    expect(service.isMuted('chat-1')).toBe(false);
  });

  it('persists muted chat ids as a JSON array and rehydrates as a Set', () => {
    service.toggleMute('chat-1');
    service.toggleMute('chat-2');
    const stored = JSON.parse(localStorage.getItem('mutedChats')!);
    expect(stored.sort()).toEqual(['chat-1', 'chat-2']);

    const freshService = new MuteService();
    expect(freshService.isMuted('chat-1')).toBe(true);
    expect(freshService.isMuted('chat-2')).toBe(true);
  });

  it('falls back to an empty set when localStorage holds corrupt JSON', () => {
    localStorage.setItem('mutedChats', 'not-json{{{');
    const freshService = new MuteService();
    expect(freshService.isMuted('chat-1')).toBe(false);
  });

  it('treats an undefined chatId as not muted', () => {
    expect(service.isMuted(undefined)).toBe(false);
  });
});
