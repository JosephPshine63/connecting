import { MessageActionsMenuComponent } from './message-actions-menu.component';

describe('MessageActionsMenuComponent', () => {
  let component: MessageActionsMenuComponent;

  beforeEach(() => {
    component = new MessageActionsMenuComponent();
  });

  it('emits toggle and stops propagation when the ⋮ button is clicked', () => {
    const emitSpy = jasmine.createSpy('toggle');
    component.toggle.subscribe(emitSpy);
    const event = new Event('click');
    spyOn(event, 'stopPropagation');

    component.onToggleClick(event);

    expect(emitSpy).toHaveBeenCalled();
    expect(event.stopPropagation).toHaveBeenCalled();
  });

  it('emits replyRequested and closeRequested when reply is clicked', () => {
    const replySpy = jasmine.createSpy('reply');
    const closeSpy = jasmine.createSpy('close');
    component.replyRequested.subscribe(replySpy);
    component.closeRequested.subscribe(closeSpy);

    component.onReply(new Event('click'));

    expect(replySpy).toHaveBeenCalled();
    expect(closeSpy).toHaveBeenCalled();
  });

  it('emits forwardRequested and closeRequested when forward is clicked', () => {
    const forwardSpy = jasmine.createSpy('forward');
    const closeSpy = jasmine.createSpy('close');
    component.forwardRequested.subscribe(forwardSpy);
    component.closeRequested.subscribe(closeSpy);

    component.onForward(new Event('click'));

    expect(forwardSpy).toHaveBeenCalled();
    expect(closeSpy).toHaveBeenCalled();
  });

  it('emits copyRequested and closeRequested when copy is clicked', () => {
    const copySpy = jasmine.createSpy('copy');
    const closeSpy = jasmine.createSpy('close');
    component.copyRequested.subscribe(copySpy);
    component.closeRequested.subscribe(closeSpy);

    component.onCopy(new Event('click'));

    expect(copySpy).toHaveBeenCalled();
    expect(closeSpy).toHaveBeenCalled();
  });

  it('emits editRequested and closeRequested when edit is clicked', () => {
    const editSpy = jasmine.createSpy('edit');
    const closeSpy = jasmine.createSpy('close');
    component.editRequested.subscribe(editSpy);
    component.closeRequested.subscribe(closeSpy);

    component.onEdit(new Event('click'));

    expect(editSpy).toHaveBeenCalled();
    expect(closeSpy).toHaveBeenCalled();
  });

  it('emits deleteRequested and closeRequested when delete is clicked', () => {
    const deleteSpy = jasmine.createSpy('delete');
    const closeSpy = jasmine.createSpy('close');
    component.deleteRequested.subscribe(deleteSpy);
    component.closeRequested.subscribe(closeSpy);

    component.onDelete(new Event('click'));

    expect(deleteSpy).toHaveBeenCalled();
    expect(closeSpy).toHaveBeenCalled();
  });

  it('emits reactRequested and closeRequested when react is clicked', () => {
    const reactSpy = jasmine.createSpy('react');
    const closeSpy = jasmine.createSpy('close');
    component.reactRequested.subscribe(reactSpy);
    component.closeRequested.subscribe(closeSpy);

    component.onReact(new Event('click'));

    expect(reactSpy).toHaveBeenCalled();
    expect(closeSpy).toHaveBeenCalled();
  });

  it('emits closeRequested on an outside document click only while open', () => {
    const closeSpy = jasmine.createSpy('close');
    component.closeRequested.subscribe(closeSpy);

    component.open = false;
    component.onDocumentClick();
    expect(closeSpy).not.toHaveBeenCalled();

    component.open = true;
    component.onDocumentClick();
    expect(closeSpy).toHaveBeenCalled();
  });
});
