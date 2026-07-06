import { DraftService } from './draft.service';

describe('DraftService', () => {
  let service: DraftService;

  beforeEach(() => {
    localStorage.clear();
    service = new DraftService();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('returns an empty string for a chat with no draft', () => {
    expect(service.getDraft('chat-1')).toBe('');
  });

  it('roundtrips a set draft through get', () => {
    service.setDraft('chat-1', 'testo non inviato');
    expect(service.getDraft('chat-1')).toBe('testo non inviato');
  });

  it('persists drafts to localStorage as JSON', () => {
    service.setDraft('chat-1', 'ciao');
    const stored = JSON.parse(localStorage.getItem('chatDrafts')!);
    expect(stored['chat-1']).toBe('ciao');
  });

  it('deletes the key entirely when the draft is cleared to an empty string', () => {
    service.setDraft('chat-1', 'ciao');
    service.setDraft('chat-1', '');
    const stored = JSON.parse(localStorage.getItem('chatDrafts')!);
    expect(stored.hasOwnProperty('chat-1')).toBe(false);
  });

  it('clearDraft removes the draft for that chat only', () => {
    service.setDraft('chat-1', 'uno');
    service.setDraft('chat-2', 'due');
    service.clearDraft('chat-1');
    expect(service.getDraft('chat-1')).toBe('');
    expect(service.getDraft('chat-2')).toBe('due');
  });

  it('falls back to an empty object when localStorage holds corrupt JSON', () => {
    localStorage.setItem('chatDrafts', 'not-json{{{');
    const freshService = new DraftService();
    expect(freshService.getDraft('chat-1')).toBe('');
  });
});
