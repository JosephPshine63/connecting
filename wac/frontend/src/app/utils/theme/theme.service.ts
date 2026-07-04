import { Injectable, signal } from '@angular/core';

export type ThemeId = 'pio' | 'blue' | 'light' | 'dark';

const THEME_STORAGE_KEY = 'appTheme';
const DEFAULT_THEME: ThemeId = 'pio';
const VALID_THEMES: ThemeId[] = ['pio', 'blue', 'light', 'dark'];

@Injectable({ providedIn: 'root' })
export class ThemeService {

  readonly theme = signal<ThemeId>(this.readInitial());

  constructor() {
    this.applyToDom(this.theme());
  }

  setTheme(id: ThemeId): void {
    this.theme.set(id);
    localStorage.setItem(THEME_STORAGE_KEY, id);
    this.applyToDom(id);
  }

  private applyToDom(id: ThemeId): void {
    document.documentElement.setAttribute('data-theme', id);
  }

  private readInitial(): ThemeId {
    const stored = localStorage.getItem(THEME_STORAGE_KEY) as ThemeId | null;
    return stored && VALID_THEMES.includes(stored) ? stored : DEFAULT_THEME;
  }
}
