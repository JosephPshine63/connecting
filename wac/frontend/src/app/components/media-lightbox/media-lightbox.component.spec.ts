import { MediaLightboxComponent } from './media-lightbox.component';

describe('MediaLightboxComponent', () => {
  let component: MediaLightboxComponent;

  beforeEach(() => {
    component = new MediaLightboxComponent();
    component.mediaUrl = 'https://example.com/photo.jpg';
    component.mediaType = 'IMAGE';
  });

  it('emits closed and resets zoom/pan when closing', () => {
    let closedEmitted = false;
    component.closed.subscribe(() => closedEmitted = true);
    component.zoomLevel = 2;
    component.panX = 50;
    component.panY = 30;

    component.close();

    expect(closedEmitted).toBe(true);
    expect(component.zoomLevel).toBe(1);
    expect(component.panX).toBe(0);
    expect(component.panY).toBe(0);
  });

  it('closes on Escape key', () => {
    let closedEmitted = false;
    component.closed.subscribe(() => closedEmitted = true);

    component.onEscape();

    expect(closedEmitted).toBe(true);
  });

  it('clamps zoom to the maximum level', () => {
    component.zoomLevel = 4;
    component.zoomIn();
    expect(component.zoomLevel).toBe(4);
  });

  it('clamps zoom to the minimum level and resets pan', () => {
    component.zoomLevel = 1;
    component.panX = 10;
    component.panY = 10;
    component.zoomOut();
    expect(component.zoomLevel).toBe(1);
    expect(component.panX).toBe(0);
    expect(component.panY).toBe(0);
  });
});
