import { DraftService } from './draft.service';
import { KeycloakService } from '../keycloak/keycloak.service';

describe('DraftService', () => {
  let service: DraftService;
  let keycloakServiceStub: Pick<KeycloakService, 'userId'>;

  beforeEach(() => {
    localStorage.clear();
    keycloakServiceStub = { userId: 'user-1' };
    service = new DraftService(keycloakServiceStub as KeycloakService);
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

  it('persists drafts to localStorage under a key namespaced by user id', () => {
    service.setDraft('chat-1', 'ciao');
    const stored = JSON.parse(localStorage.getItem('chatDrafts:user-1')!);
    expect(stored['chat-1']).toBe('ciao');
  });

  it('deletes the key entirely when the draft is cleared to an empty string', () => {
    service.setDraft('chat-1', 'ciao');
    service.setDraft('chat-1', '');
    const stored = JSON.parse(localStorage.getItem('chatDrafts:user-1')!);
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
    localStorage.setItem('chatDrafts:user-1', 'not-json{{{');
    const freshService = new DraftService(keycloakServiceStub as KeycloakService);
    expect(freshService.getDraft('chat-1')).toBe('');
  });

  it('does not see drafts written under a different user id', () => {
    service.setDraft('chat-1', 'segreto di user-1');
    const otherUserService = new DraftService({ userId: 'user-2' } as KeycloakService);
    expect(otherUserService.getDraft('chat-1')).toBe('');
  });

  it('clearAll wipes both the signal and the storage entry for this user', () => {
    service.setDraft('chat-1', 'uno');
    service.clearAll();
    expect(service.getDraft('chat-1')).toBe('');
    expect(localStorage.getItem('chatDrafts:user-1')).toBeNull();
  });
});
