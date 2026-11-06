import Icon from './Icon';
import { useTheme } from '../context/contexts';

/** Sun / moon switch between the light ("Clear Sky") and dark ("Night Air") themes. */
export default function ThemeSwitch({ className = '' }) {
  const { theme, toggle } = useTheme();
  const dark = theme === 'dark';
  return (
    <button
      type="button"
      role="switch"
      aria-checked={dark}
      aria-label="Dark theme"
      title={dark ? 'Switch to light theme' : 'Switch to dark theme'}
      onClick={toggle}
      className={`relative inline-flex h-9 w-[68px] shrink-0 items-center rounded-full bg-surface-2/70 p-1 transition-colors hover:bg-surface-3/70 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent ${className}`}
    >
      <span
        aria-hidden="true"
        className={`absolute top-1 h-7 w-7 rounded-full bg-surface shadow-card transition-transform duration-300 ease-[var(--ease-out-soft)] motion-reduce:transition-none ${dark ? 'translate-x-8' : 'translate-x-0'}`}
      />
      <span className="relative z-10 grid w-7 place-items-center" aria-hidden="true">
        <Icon name="sun" className={`h-4 w-4 ${dark ? 'text-faint' : 'text-haze'}`} strokeWidth={2.2} />
      </span>
      <span className="relative z-10 ml-1 grid w-7 place-items-center" aria-hidden="true">
        <Icon name="moon" className={`h-4 w-4 ${dark ? 'text-accent-ink' : 'text-faint'}`} strokeWidth={2.2} />
      </span>
    </button>
  );
}
