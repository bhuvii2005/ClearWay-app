import { useEffect, useId, useRef, useState } from 'react';
import Icon from './Icon';
import { Spinner } from './ui';
import { searchPlaces } from '../lib/geocode';

/**
 * Address box with suggestions as you type. `value` is the chosen place
 * ({ lat, lng, name, detail }) or null; picking a suggestion calls onSelect.
 */
export default function PlaceSearch({ label, marker, value, onSelect, onClear, near, placeholder, active, onFocus }) {
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
        className={`flex items-center gap-2 rounded-lg border bg-slate-950/70 pl-2.5 pr-1 transition-colors ${
          active ? 'border-emerald-500/70 ring-2 ring-emerald-500/15' : 'border-slate-700'
        }`}
      >
        <span
          className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-extrabold text-slate-950 ${
            marker === 'A' ? 'bg-emerald-400' : 'bg-sky-400'
          }`}
          aria-hidden="true"
        >
          {marker}
        </span>
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
          className="h-10 min-w-0 flex-1 bg-transparent text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none"
        />
        {loading && <Spinner className="h-4 w-4 shrink-0 text-slate-500" />}
        {(value || query) && (
          <button
            type="button"
            aria-label={`Clear ${label.toLowerCase()}`}
            onClick={() => {
              setQuery('');
              onClear();
            }}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 hover:bg-slate-800 hover:text-slate-300"
          >
            <Icon name="x" className="h-4 w-4" />
          </button>
        )}
      </div>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute inset-x-0 top-full z-[1200] mt-1 max-h-72 overflow-y-auto rounded-lg border border-slate-700 bg-slate-900 py-1 shadow-2xl"
        >
          {results.map((place, i) => (
            <li
              key={`${place.lat},${place.lng},${i}`}
              role="option"
              aria-selected={i === highlight}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => choose(place)}
              onMouseEnter={() => setHighlight(i)}
              className={`flex cursor-pointer items-start gap-2.5 px-3 py-2 ${i === highlight ? 'bg-slate-800' : ''}`}
            >
              <Icon name="pin" className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" />
              <span className="min-w-0">
                <span className="block truncate text-sm text-slate-100">{place.name}</span>
                {place.detail && <span className="block truncate text-xs text-slate-500">{place.detail}</span>}
              </span>
            </li>
          ))}
          {!loading && !results.length && (
            <li className="px-3 py-2.5 text-sm text-slate-500">
              {failed ? 'Place search is unavailable right now. Click the map instead.' : 'No places found.'}
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
