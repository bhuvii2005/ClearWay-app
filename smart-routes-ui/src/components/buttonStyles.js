/** Shared button looks, so links can look like buttons too. */
const BUTTON_VARIANTS = {
  primary:
    'bg-accent text-on-accent shadow-[0_1px_2px_rgb(0_0_0/0.12),inset_0_1px_0_rgb(255_255_255/0.14)] hover:bg-accent-hover disabled:bg-surface-2 disabled:text-faint disabled:shadow-none',
  secondary:
    'bg-surface text-ink shadow-card ring-1 ring-hairline hover:bg-surface-2 disabled:opacity-50',
  dangerGhost: 'text-danger hover:bg-danger/10 hover:text-danger disabled:opacity-50',
  ghost: 'text-muted hover:bg-surface-2 hover:text-ink disabled:opacity-50',
  danger: 'bg-danger-solid text-white hover:bg-danger-solid/90 disabled:opacity-50',
};

const BUTTON_BASE =
  'inline-flex min-h-10 items-center justify-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition-[background-color,box-shadow,transform] duration-150 active:scale-[0.98] disabled:active:scale-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed';

/** Button classes for things that look like buttons but are links. */
export const buttonClass = (variant = 'primary', className = '') => `${BUTTON_BASE} ${BUTTON_VARIANTS[variant]} ${className}`;
