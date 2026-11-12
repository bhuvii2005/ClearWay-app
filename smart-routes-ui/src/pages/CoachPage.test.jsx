// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { coachApi } from '../lib/api';
import CoachPage from './CoachPage';

vi.mock('../lib/api', async (importOriginal) => ({
  ...(await importOriginal()),
  coachApi: { chat: vi.fn() },
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let container;
let root;

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
  vi.clearAllMocks();
});

function render(state) {
  act(() => {
    root.render(
      <MemoryRouter initialEntries={[{ pathname: '/coach', state }]}>
        <CoachPage />
      </MemoryRouter>,
    );
  });
}

const box = () => container.querySelector('textarea');
const sendButton = () => container.querySelector('button[type="submit"]');
const text = () => container.textContent;

function type(value) {
  const setValue = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
  act(() => {
    setValue.call(box(), value);
    box().dispatchEvent(new Event('input', { bubbles: true }));
  });
}

function press(key, options = {}) {
  act(() => {
    box().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...options }));
  });
}

/** A promise the test resolves or rejects by hand, to look at the page while it waits. */
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

async function settle(fn) {
  await act(async () => {
    fn();
    await Promise.resolve();
  });
}

const httpError = (status, data = {}, headers = {}) =>
  Object.assign(new Error(`HTTP ${status}`), { response: { status, data, headers } });

describe('CoachPage', () => {
  it('starts empty, with three suggested questions and the medical note', () => {
    render();
    expect(text()).toContain('Ask the Coach');
    expect(container.querySelectorAll('[aria-label="Suggested questions"] button')).toHaveLength(3);
    expect(text()).toContain('Not medical advice');
    expect(sendButton().disabled).toBe(true);
  });

  it('sends on Enter with the conversation id and time zone, waits, then shows the answer', async () => {
    const reply = deferred();
    coachApi.chat.mockReturnValue(reply.promise);
    render();

    type('When should I walk?');
    expect(text()).toContain('19 / 2,000');
    press('Enter');

    expect(coachApi.chat).toHaveBeenCalledTimes(1);
    const [conversationId, message, timeZone] = coachApi.chat.mock.calls[0];
    expect(conversationId).toMatch(/^[0-9a-f-]{36}$/);
    expect(message).toBe('When should I walk?');
    expect(timeZone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    // While waiting: the question is shown, sending is off, and the Coach is "thinking".
    expect(text()).toContain('When should I walk?');
    expect(container.querySelector('[role="status"]').textContent).toContain('Thinking');
    type('another');
    expect(sendButton().disabled).toBe(true);
    press('Enter');
    expect(coachApi.chat).toHaveBeenCalledTimes(1);

    await settle(() =>
      reply.resolve({
        answer: 'Go at **6 AM**, when your score is lowest.',
        toolsUsed: ['listMyRoutes', 'getRouteHistory', 'searchKnowledgeBase'],
        citations: [
          { title: 'WHO 2021 air quality guideline levels', url: 'https://www.who.int/guidelines' },
          { title: 'Bad link', url: 'javascript:alert(1)' },
        ],
      }),
    );

    expect(text()).toContain('Go at 6 AM, when your score is lowest.');
    expect(text()).not.toContain('**');
    expect(text()).toContain('Checked your routes');
    expect(text()).toContain('Read route history');
    expect(text()).toContain('Searched health guidance');
    const links = container.querySelectorAll('a[href]');
    expect(links).toHaveLength(1);
    expect(links[0].getAttribute('href')).toBe('https://www.who.int/guidelines');
    expect(links[0].getAttribute('target')).toBe('_blank');
    expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
    expect(container.querySelector('[role="status"]')).toBeNull();
  });

  it('keeps conversation id within a chat and starts a new one on "New chat"', async () => {
    coachApi.chat.mockResolvedValue({ answer: 'ok', toolsUsed: [], citations: [] });
    render();
    type('one');
    await settle(() => press('Enter'));
    type('two');
    await settle(() => press('Enter'));
    const newChat = [...container.querySelectorAll('button')].find((b) => b.textContent.includes('New chat'));
    act(() => newChat.click());
    expect(text()).toContain('Ask the Coach');
    type('three');
    await settle(() => press('Enter'));

    const ids = coachApi.chat.mock.calls.map((c) => c[0]);
    expect(ids[0]).toBe(ids[1]);
    expect(ids[2]).not.toBe(ids[0]);
  });

  it('Shift+Enter does not send', () => {
    render();
    type('line one');
    press('Enter', { shiftKey: true });
    expect(coachApi.chat).not.toHaveBeenCalled();
  });

  it('a suggestion sends straight away', async () => {
    coachApi.chat.mockResolvedValue({ answer: 'ok', toolsUsed: [], citations: [] });
    render();
    await settle(() => container.querySelector('[aria-label="Suggested questions"] button').click());
    expect(coachApi.chat.mock.calls[0][1]).toBe('When is the best time of day to walk my saved routes?');
  });

  it('shows the seconds after 5 s of thinking', () => {
    vi.useFakeTimers();
    coachApi.chat.mockReturnValue(new Promise(() => {}));
    render();
    type('slow question');
    press('Enter');
    act(() => vi.advanceTimersByTime(3000));
    expect(container.querySelector('[role="status"]').textContent).toBe('Thinking…');
    act(() => vi.advanceTimersByTime(4000));
    expect(container.querySelector('[role="status"]').textContent).toBe('Thinking… 7 s');
  });

  it('shows answers as text, never as HTML', async () => {
    coachApi.chat.mockResolvedValue({ answer: '<img src=x onerror="alert(1)"><b>hi</b>', toolsUsed: [], citations: [] });
    render();
    type('hi');
    await settle(() => press('Enter'));
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('b')).toBeNull();
    expect(text()).toContain('<img src=x onerror="alert(1)"><b>hi</b>');
  });

  it('can start with a question filled in (from a route page) without sending it', () => {
    render({ prefill: 'How is my "Riverside walk" route?' });
    expect(box().value).toBe('How is my "Riverside walk" route?');
    expect(coachApi.chat).not.toHaveBeenCalled();
  });

  describe('errors keep the typed question', () => {
    const cases = [
      ['429 with Retry-After', httpError(429, { message: 'limit' }, { 'retry-after': '300' }), 'Try again in 5 minutes'],
      ['daily quota', httpError(503, { code: 'quota_exhausted' }), "Coach has reached today's free limit; it resets"],
      ['our daily budget', httpError(503, { code: 'budget' }), "Coach has reached today's free limit; it resets"],
      ['time budget', httpError(503, { code: 'time_budget' }), 'That took too long. Please try again.'],
      ['provider down', httpError(503, { code: 'provider_error' }), 'Coach is offline right now'],
      ['503 without a code', httpError(503, {}), 'Coach is offline right now'],
      ['browser timeout', Object.assign(new Error('timeout'), { code: 'ECONNABORTED' }), 'That took too long'],
      ['no server', new Error('Network Error'), 'Cannot reach the ClearWay server'],
    ];

    it.each(cases)('%s', async (_name, error, expected) => {
      coachApi.chat.mockRejectedValue(error);
      render();
      type('What about today?');
      await settle(() => press('Enter'));

      expect(container.querySelector('[role="alert"]').textContent).toContain(expected);
      expect(box().value).toBe('What about today?');
      // The unanswered question is not left in the conversation.
      expect(container.querySelector('[role="log"]').textContent).not.toContain('What about today?');
      expect(sendButton().disabled).toBe(false);
    });
  });
});
