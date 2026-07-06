import { ForwardPickerComponent } from './forward-picker.component';
import { ChatResponse } from '../../services/models/chat-response';

describe('ForwardPickerComponent', () => {
  let component: ForwardPickerComponent;

  beforeEach(() => {
    component = new ForwardPickerComponent();
  });

  it('emits closed when close() is called', () => {
    const spy = jasmine.createSpy('closed');
    component.closed.subscribe(spy);
    component.close();
    expect(spy).toHaveBeenCalled();
  });

  it('emits chatChosen with the selected chat', () => {
    const spy = jasmine.createSpy('chatChosen');
    component.chatChosen.subscribe(spy);
    const chat: ChatResponse = { id: 'chat-1', name: 'Alice' };

    component.choose(chat);

    expect(spy).toHaveBeenCalledWith(chat);
  });
});
