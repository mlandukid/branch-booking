import { Injectable, computed, effect, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'theme';

/** An explicit choice always wins. Without one, the device setting decides. */
export function resolveTheme(stored: string | null, systemPrefersDark: boolean): Theme {
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return systemPrefersDark ? 'dark' : 'light';
}

function readStored(): Theme | null {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return value === 'light' || value === 'dark' ? value : null;
  } catch {
    // Storage can be blocked, for example in some private browsing modes.
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly query =
    typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  private readonly explicit = signal<Theme | null>(readStored());
  private readonly systemDark = signal(this.query?.matches ?? false);

  readonly theme = computed<Theme>(() => resolveTheme(this.explicit(), this.systemDark()));
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor() {
    this.query?.addEventListener('change', (event) => this.systemDark.set(event.matches));
    effect(() => document.documentElement.setAttribute('data-theme', this.theme()));
  }

  toggle(): void {
    const next: Theme = this.isDark() ? 'light' : 'dark';
    this.explicit.set(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // The choice still applies for this visit.
    }
  }
}