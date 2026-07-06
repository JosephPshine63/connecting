import { TestBed } from '@angular/core/testing';
import { ChatFilterService } from './chat-filter.service';

describe('ChatFilterService', () => {
  let service: ChatFilterService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ChatFilterService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('defaults to "all" when localStorage is empty', () => {
    expect(service.filter()).toBe('all');
  });

  it('falls back to "all" when localStorage holds an invalid value', () => {
    localStorage.setItem('activeChatFilter', 'not-a-real-filter');
    const freshService = TestBed.inject(ChatFilterService);
    expect(freshService.filter()).toBe('all');
  });

  it('persists the selected filter and updates the signal', () => {
    service.setFilter('favorites');
    expect(service.filter()).toBe('favorites');
    expect(localStorage.getItem('activeChatFilter')).toBe('favorites');
  });

  it('roundtrips a persisted value across a fresh injection', () => {
    service.setFilter('unread');
    const anotherInstance = new ChatFilterService();
    expect(anotherInstance.filter()).toBe('unread');
  });
});
