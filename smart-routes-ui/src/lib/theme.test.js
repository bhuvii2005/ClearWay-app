// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest';
import { applyTheme, saveTheme, storedTheme } from './theme';
import { mapTheme } from './mapUtils';

describe('theme', () => {
  beforeEach(() => localStorage.clear());

  it('starts light when nothing is saved', () => {
    expect(storedTheme()).toBe('light');
  });

  it('remembers the chosen theme and ignores unknown values', () => {
    saveTheme('dark');
    expect(storedTheme()).toBe('dark');
    localStorage.setItem('cw-theme', 'purple');
    expect(storedTheme()).toBe('light');
  });

  it('sets the theme on the page', () => {
    applyTheme('dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
    applyTheme('light');
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('uses a matching map style for each theme', () => {
    expect(mapTheme('light').style).toMatch(/positron$/);
    expect(mapTheme('dark').style).toMatch(/dark$/);
    expect(mapTheme('unknown')).toBe(mapTheme('light'));
  });
});
