import { ChatListComponent } from './chat-list.component';
import { ChatResponse } from '../../services/models/chat-response';
import { ChatFilterService } from '../../utils/chat-filter/chat-filter.service';

describe('ChatListComponent', () => {
  let component: ChatListComponent;
  let chats: ChatResponse[];

  function makeChat(overrides: Partial<ChatResponse>): ChatResponse {
    return {
      id: 'chat-1',
      status: 'ACCEPTED',
      senderId: 'me',
      receiverId: 'peer',
      ...overrides
    };
  }

  beforeEach(() => {
    localStorage.clear();
    const fakeChatService = {} as any;
    const fakeUserService = {} as any;
    const fakeModerationService = {} as any;
    const fakeKeycloakService = { userId: 'me' } as any;
    const chatFilterService = new ChatFilterService();
    const fakeMuteService = { isMuted: () => false, toggleMute: () => {} } as any;

    component = new ChatListComponent(
      fakeChatService,
      fakeUserService,
      fakeModerationService,
      fakeKeycloakService,
      chatFilterService,
      fakeMuteService
    );

    chats = [
      makeChat({ id: 'a', name: 'Alice Smith' }),
      makeChat({ id: 'b', name: 'Bob Jones' }),
      makeChat({ id: 'c', name: 'Carol Alice' })
    ];
    (component as any).chats = () => chats;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('returns all visible chats when the search query is empty', () => {
    expect(component.visibleChats().length).toBe(3);
  });

  it('filters chats by name case-insensitively', () => {
    component.searchQuery = 'alice';
    const result = component.visibleChats();
    expect(result.map(c => c.id)).toEqual(['a', 'c']);
  });

  it('combines the active filter pill with the search query (AND, not OR)', () => {
    chats[0].favorite = true;
    chats[2].favorite = false;
    component.setFilter('favorites');
    component.searchQuery = 'alice';
    const result = component.visibleChats();
    expect(result.map(c => c.id)).toEqual(['a']);
  });

  it('trims whitespace from the search query', () => {
    component.searchQuery = '  bob  ';
    const result = component.visibleChats();
    expect(result.map(c => c.id)).toEqual(['b']);
  });

  it('excludes archived chats from the default "all" filter', () => {
    chats[1].archived = true;
    const result = component.visibleChats();
    expect(result.map(c => c.id)).toEqual(['a', 'c']);
  });

  it('shows only archived chats under the "archived" filter', () => {
    chats[1].archived = true;
    component.setFilter('archived');
    const result = component.visibleChats();
    expect(result.map(c => c.id)).toEqual(['b']);
  });

  it('excludes archived chats from the "unread" and "favorites" filters', () => {
    chats[0].archived = true;
    chats[0].unreadCount = 1;
    chats[0].favorite = true;
    chats[1].unreadCount = 1;
    chats[1].favorite = true;
    component.setFilter('unread');
    expect(component.visibleChats().map(c => c.id)).toEqual(['b']);
    component.setFilter('favorites');
    expect(component.visibleChats().map(c => c.id)).toEqual(['b']);
  });
});
