import { useCallback, useLayoutEffect, useMemo, useState } from 'react';
import { ThemeContext } from './contexts';
import { applyTheme, saveTheme, storedTheme } from '../lib/theme';

export default function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(storedTheme);

  useLayoutEffect(() => applyTheme(theme), [theme]);

  const setTheme = useCallback((next) => {
    setThemeState(next);
    saveTheme(next);
  }, []);

  const value = useMemo(
    () => ({ theme, setTheme, toggle: () => setTheme(theme === 'dark' ? 'light' : 'dark') }),
    [theme, setTheme],
  );
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
