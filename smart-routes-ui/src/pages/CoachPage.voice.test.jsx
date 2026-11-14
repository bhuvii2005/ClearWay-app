// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { coachApi } from '../lib/api';
import { COACH_MAX_CHARS } from '../lib/coach';
import { MAX_LISTEN_MS } from '../hooks/useSpeechRecognition';
import CoachPage from './CoachPage';

vi.mock('../lib/api', async (importOriginal) => ({
  ...(await importOriginal()),
  coachApi: { chat: vi.fn() },
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

/**
 * A stand-in for the browser's SpeechRecognition that the test drives by hand. Like Chrome, the
 * page's handlers are its on* properties; stop() and abort() only record the call, and the test
 * decides what the "browser" does next.
 */
class FakeRecognition {
  static instances = [];

  constructor() {
    this.calls = [];
    FakeRecognition.instances.push(this);
  }

  start() {
    this.calls.push('start');
  }

  stop() {
    this.calls.push('stop');
  }

  abort() {
    this.calls.push('abort');
  }

  /** Results as [text, isFinal] pairs, like one SpeechRecognitionEvent. */
  hear(...parts) {
    const results = parts.map(([transcript, isFinal]) => Object.assign([{ transcript }], { isFinal }));
    act(() => this.onresult?.({ resultIndex: 0, results }));
  }

  fail(code) {
    act(() => this.onerror?.({ error: code }));
  }

  end() {
    act(() => this.onend?.());
  }
}

const latest = () => FakeRecognition.instances.at(-1);

let container;
let root;
let unmounted;

function setBrowser({ api = FakeRecognition, prefixed = false, secure = true } = {}) {
  delete window.SpeechRecognition;
  delete window.webkitSpeechRecognition;
  if (api) window[prefixed ? 'webkitSpeechRecognition' : 'SpeechRecognition'] = api;
  Object.defineProperty(window, 'isSecureContext', { value: secure, configurable: true });
}

beforeEach(() => {
  FakeRecognition.instances = [];
  setBrowser();
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
  unmounted = false;
});

afterEach(() => {
  if (!unmounted) act(() => root.unmount());
  container.remove();
  delete window.SpeechRecognition;
  delete window.webkitSpeechRecognition;
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

function render() {
  act(() => {
    root.render(
      <MemoryRouter initialEntries={['/coach']}>
        <CoachPage />
      </MemoryRouter>,
    );
  });
}

const box = () => container.querySelector('textarea');
const mic = () => container.querySelector('button[aria-label$="voice input"]');
const alerts = () => [...container.querySelectorAll('[role="alert"]')].map((a) => a.textContent);
const listening = () => container.querySelector('[data-testid="voice-listening"]');
const interim = () => container.querySelector('[data-testid="voice-interim"]');
const live = () => container.querySelector('[aria-live="polite"].sr-only').textContent;
const button = (label) => [...container.querySelectorAll('button')].find((b) => b.textContent.includes(label));

function type(value) {
  const setValue = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
  act(() => {
    setValue.call(box(), value);
    box().dispatchEvent(new Event('input', { bubbles: true }));
  });
}

const click = (el) => act(() => el.click());

describe('Coach voice input', () => {
  it('has no mic button when the browser has no speech recognition (Firefox)', () => {
    setBrowser({ api: null });
    render();
    expect(mic()).toBeNull();
    expect(container.querySelector('form').getAttribute('title')).toBe('Voice input works in Chrome, Edge and Safari.');
  });

  it('has no mic button outside a secure context (plain http)', () => {
    setBrowser({ secure: false });
    render();
    expect(mic()).toBeNull();
  });

  it('uses the prefixed webkitSpeechRecognition (Chrome, Safari) with the agreed settings', () => {
    setBrowser({ prefixed: true });
    render();
    click(mic());
    const rec = latest();
    expect(rec.calls).toEqual(['start']);
    expect(rec.continuous).toBe(false);
    expect(rec.interimResults).toBe(true);
    expect(rec.maxAlternatives).toBe(1);
    expect(rec.lang).toMatch(/^en/);
  });

  it('start shows the listening state; clicking again stops it', () => {
    render();
    expect(mic().getAttribute('aria-label')).toBe('Start voice input');
    expect(mic().getAttribute('aria-pressed')).toBe('false');
    expect(live()).toBe('');

    click(mic());
    expect(listening().textContent).toContain('Listening…');
    expect(listening().textContent).toContain('audio is sent to Google');
    expect(mic().getAttribute('aria-label')).toBe('Stop voice input');
    expect(mic().getAttribute('aria-pressed')).toBe('true');
    expect(live()).toBe('Listening');

    click(mic());
    expect(latest().calls).toEqual(['start', 'stop']);
    latest().end();
    expect(listening()).toBeNull();
    expect(mic().getAttribute('aria-label')).toBe('Start voice input');
    expect(live()).toBe('Stopped listening');
  });

  it('shows interim words greyed, then puts the final text in the box', () => {
    render();
    click(mic());
    latest().hear(['when is the', false]);
    expect(interim().textContent).toBe('when is the');
    expect(box().value).toBe('');

    latest().hear(['when is the best time to walk', true]);
    expect(interim()).toBeNull();
    expect(box().value).toBe('when is the best time to walk');
    latest().end();
    expect(listening()).toBeNull();
  });

  it('appends to what is already typed, with a space, never overwriting it', () => {
    render();
    type('About my Riverside walk:');
    click(mic());
    latest().hear(['is it clean today', true]);
    latest().end();
    expect(box().value).toBe('About my Riverside walk: is it clean today');

    click(mic());
    latest().hear(['and tomorrow', true]);
    expect(box().value).toBe('About my Riverside walk: is it clean today and tomorrow');
  });

  it('cuts the transcript at 2,000 characters and shows the counter warning', () => {
    render();
    const typed = 'x'.repeat(COACH_MAX_CHARS - 10);
    type(typed);
    click(mic());
    latest().hear(['this sentence is far longer than ten characters', true]);

    expect(box().value).toHaveLength(COACH_MAX_CHARS);
    expect(box().value.startsWith(typed + ' this sen')).toBe(true);
    const counter = document.getElementById(box().getAttribute('aria-describedby'));
    expect(counter.className).toContain('text-danger');
    expect(counter.textContent).toContain('2,000 / 2,000');
    expect(counter.textContent).toContain('voice input cut at the limit');
    // Typing again clears the warning.
    type(box().value.slice(0, 100));
    expect(counter.className).not.toContain('text-danger');
  });

  describe('errors show a friendly message and keep the typed text', () => {
    const cases = [
      ['not-allowed', "Microphone access is blocked. Allow it in your browser's site settings."],
      ['service-not-allowed', "Microphone access is blocked. Allow it in your browser's site settings."],
      ['audio-capture', 'No microphone found.'],
      ['no-speech', "Didn't catch that. Try again."],
      ['network', 'Speech service unreachable. Check your connection.'],
    ];

    it.each(cases)('%s', (code, message) => {
      render();
      type('my typed question');
      click(mic());
      latest().fail(code);
      latest().end();
      expect(alerts()).toEqual([message]);
      expect(box().value).toBe('my typed question');
      expect(listening()).toBeNull();
      // Trying again clears the message.
      click(mic());
      expect(alerts()).toEqual([]);
    });

    it('aborted shows nothing', () => {
      render();
      type('my typed question');
      click(mic());
      latest().fail('aborted');
      latest().end();
      expect(alerts()).toEqual([]);
      expect(box().value).toBe('my typed question');
    });
  });

  it('never sends by itself: a final result only fills the box', () => {
    render();
    click(mic());
    latest().hear(['what is the WHO limit for PM2.5', true]);
    latest().end();
    expect(box().value).toBe('what is the WHO limit for PM2.5');
    expect(coachApi.chat).not.toHaveBeenCalled();
  });

  it('Send stops listening, and the mic is disabled while the Coach is thinking', async () => {
    let answer;
    coachApi.chat.mockReturnValue(new Promise((resolve) => (answer = resolve)));
    render();
    click(mic());
    latest().hear(['is the air ok', true]);
    const rec = latest();

    await act(async () => container.querySelector('button[type="submit"]').click());
    expect(rec.calls).toContain('abort');
    expect(listening()).toBeNull();
    expect(coachApi.chat).toHaveBeenCalledTimes(1);
    expect(coachApi.chat.mock.calls[0][1]).toBe('is the air ok');
    expect(container.textContent).toContain('Thinking');
    expect(mic().disabled).toBe(true);

    // A late result from the stopped recognition is ignored.
    rec.hear(['ghost words', true]);
    expect(box().value).toBe('');

    await act(async () => answer({ answer: 'Yes.', toolsUsed: [], citations: [] }));
    expect(mic().disabled).toBe(false);
  });

  it('New chat stops listening and drops unfinished words', async () => {
    coachApi.chat.mockResolvedValue({ answer: 'ok', toolsUsed: [], citations: [] });
    render();
    type('first');
    await act(async () => container.querySelector('button[type="submit"]').click());
    click(mic());
    latest().hear(['half a sen', false]);
    const rec = latest();

    click(button('New chat'));
    expect(rec.calls).toContain('abort');
    expect(listening()).toBeNull();
    expect(box().value).toBe('');
  });

  it('stops when the tab is hidden', () => {
    render();
    click(mic());
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    expect(latest().calls).toContain('abort');
    expect(listening()).toBeNull();
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
  });

  it(`stops by itself after ${MAX_LISTEN_MS / 1000} s, keeping what was said`, () => {
    vi.useFakeTimers();
    render();
    click(mic());
    act(() => vi.advanceTimersByTime(MAX_LISTEN_MS - 1));
    expect(latest().calls).toEqual(['start']);
    act(() => vi.advanceTimersByTime(1));
    expect(latest().calls).toEqual(['start', 'stop']);
    // The browser never answers the stop: give up after a short grace period.
    act(() => vi.advanceTimersByTime(2_000));
    expect(latest().calls).toEqual(['start', 'stop', 'abort']);
    expect(listening()).toBeNull();
  });

  it('unmounting while listening aborts, detaches its handlers and logs no React warnings', () => {
    const errors = vi.spyOn(console, 'error');
    render();
    click(mic());
    const rec = latest();

    act(() => root.unmount());
    unmounted = true;
    expect(rec.calls).toContain('abort');
    expect([rec.onresult, rec.onerror, rec.onend, rec.onstart]).toEqual([null, null, null, null]);
    expect(errors).not.toHaveBeenCalled();
  });
});
