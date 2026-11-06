import { useEffect, useId, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import Icon from './Icon';
import { Spinner } from './ui';
import { searchPlaces } from '../lib/geocode';

/**
 * Address box with suggestions as you type. `value` is the chosen place
 * ({ lat, lng, name, detail }) or null; picking a suggestion calls onSelect.
 */
/**
 * `bare` drops the box and marker so a parent can stack two searches in one panel.
 * `trailing` shows in place of the clear button while the box is empty.
 */
export default function PlaceSearch({ label, marker, value, onSelect, onClear, near, placeholder, active, onFocus, bare = false, trailing }) {
  const [query, setQuery] = useState(value?.name ?? '');
  const [shownValue, setShownValue] = useState(value);
  // The last finished search: which text it was for, and what it found.
  const [answer, setAnswer] = useState({ query: '', places: [], failed: false });
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const listId = useId();
  const blurTimer = useRef(null);
  const nearRef = useRef(near);
  useEffect(() => {
    nearRef.current = near;
  }, [near]);

  // When the chosen place changes from outside (map click, swap, clear), show its name.
  if (value !== shownValue) {
    setShownValue(value);
    setQuery(value?.name ?? '');
  }

  const editing = !value || query !== value.name;
  const trimmed = query.trim();
  const searchable = editing && trimmed.length >= 3;
  const results = searchable && answer.query === trimmed ? answer.places : [];
  const failed = searchable && answer.query === trimmed && answer.failed;
  const loading = searchable && answer.query !== trimmed;

  useEffect(() => {
    if (!searchable) return undefined;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      searchPlaces(trimmed, { near: nearRef.current, signal: controller.signal })
        .then((places) => {
          setAnswer({ query: trimmed, places, failed: false });
          setHighlight(0);
        })
        .catch((err) => {
          if (err.name !== 'AbortError') setAnswer({ query: trimmed, places: [], failed: true });
        });
    }, 350);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [trimmed, searchable]);

  function choose(place) {
    onSelect(place);
    setOpen(false);
  }

  function onKeyDown(e) {
    if (!open || !results.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlight((h) => (h + 1) % results.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlight((h) => (h - 1 + results.length) % results.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      choose(results[highlight]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  }

  const showList = open && editing && trimmed.length >= 3;

  return (
    <div className="relative">
      <label className="sr-only" htmlFor={`${listId}-input`}>
        {label}
      </label>
      <div
        className={
          bare
            ? `flex items-center gap-2 rounded-xl pl-3 pr-1 transition-colors ${active ? 'bg-surface shadow-card' : 'hover:bg-surface/60'}`
            : `flex items-center gap-2 rounded-xl border bg-surface pl-2.5 pr-1 transition-[border-color,box-shadow] ${
                active ? 'border-accent/70 ring-4 ring-accent/15' : 'border-line-strong'
              }`
        }
      >
        {!bare && (<span
          className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-extrabold ${
            marker === 'A' ? 'bg-accent text-on-accent' : 'bg-haze text-on-haze'
          }`}
          aria-hidden="true"
        >
          {marker}
        </span>)}
        <input
          id={`${listId}-input`}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          autoComplete="off"
          value={query}
          placeholder={placeholder}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => {
            clearTimeout(blurTimer.current);
            setOpen(true);
            onFocus?.();
          }}
          onBlur={() => {
            blurTimer.current = setTimeout(() => setOpen(false), 150);
          }}
          onKeyDown={onKeyDown}
          className={`min-w-0 flex-1 bg-transparent text-sm text-ink placeholder:text-faint focus:outline-none ${bare ? 'h-11' : 'h-10'}`}
        />
        {loading && <Spinner className="h-4 w-4 shrink-0 text-faint" />}
        {!(value || query) && trailing}
        {(value || query) && (
          <button
            type="button"
            aria-label={`Clear ${label.toLowerCase()}`}
            onClick={() => {
              setQuery('');
              onClear();
            }}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-faint transition-colors hover:bg-surface-2 hover:text-ink-2"
          >
            <Icon name="x" className="h-4 w-4" />
          </button>
        )}
      </div>

      <AnimatePresence>
      {showList && (
        <motion.ul
          id={listId}
          role="listbox"
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -4, transition: { duration: 0.1 } }}
          className="absolute inset-x-0 top-full z-[1200] mt-1.5 max-h-72 overflow-y-auto rounded-2xl bg-surface p-1.5 shadow-pop"
        >
          {results.map((place, i) => (
            <li
              key={`${place.lat},${place.lng},${i}`}
              role="option"
              aria-selected={i === highlight}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => choose(place)}
              onMouseEnter={() => setHighlight(i)}
              className={`flex cursor-pointer items-center gap-3 rounded-xl px-2.5 py-2 ${i === highlight ? 'bg-surface-2' : ''}`}
            >
              <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-surface-2 text-muted">
                <Icon name="pin" className="h-4 w-4" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm text-ink">{place.name}</span>
                {place.detail && <span className="block truncate text-xs text-faint">{place.detail}</span>}
              </span>
            </li>
          ))}
          {!loading && !results.length && (
            <li className="px-3 py-2.5 text-sm text-faint">
              {failed ? 'Place search is unavailable right now. Click the map instead.' : 'No places found.'}
            </li>
          )}
        </motion.ul>
      )}
      </AnimatePresence>
    </div>
  );
}
