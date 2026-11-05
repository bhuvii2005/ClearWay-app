/** Light ("Clear Sky") is the default; a user's pick is remembered in this browser. */
export const THEMES = ['light', 'dark'];
const KEY = 'cw-theme';

export function storedTheme() {
  try {
    const value = localStorage.getItem(KEY);
    return THEMES.includes(value) ? value : 'light';
  } catch {
    return 'light'; // storage blocked (private mode etc.)
  }
}

export function saveTheme(theme) {
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    // Not saved; the theme still applies for this visit.
  }
}

/** Colour of the browser's address bar on phones, matching the page background. */
const BAR_COLOR = { light: '#f4f7fa', dark: '#070b12' };

export function applyTheme(theme) {
  const root = document.documentElement;
  root.dataset.theme = theme;
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', BAR_COLOR[theme]);
}
