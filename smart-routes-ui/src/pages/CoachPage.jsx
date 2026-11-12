import { useEffect, useId, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'motion/react';
import { coachApi } from '../lib/api';
import { COACH_MAX_CHARS, SUGGESTIONS, coachErrorMessage, plainText, safeCitations, toolChips } from '../lib/coach';
import Icon from '../components/Icon';
import { Button, ErrorNote } from '../components/ui';

/** Seconds of waiting before the "Thinking…" line starts counting. */
const SHOW_ELAPSED_AFTER_S = 5;

const newId = () => crypto.randomUUID();
const browserTimeZone = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
};

/**
 * The AI Coach: questions about the user's routes and air quality. Answers are shown as plain
 * text (never as HTML), with the tools the Coach used and links to its sources.
 */
export default function CoachPage() {
  const location = useLocation();
  const [conversationId, setConversationId] = useState(newId);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState(() => location.state?.prefill ?? '');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [elapsed, setElapsed] = useState(0);
  const inputRef = useRef(null);
  const endRef = useRef(null);
  const inputId = useId();
  const counterId = useId();

  // Count the seconds while waiting for an answer.
  useEffect(() => {
    if (!pending) return undefined;
    setElapsed(0);
    const started = Date.now();
    const timer = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 1000);
    return () => clearInterval(timer);
  }, [pending]);

  // Keep the newest message in view.
  useEffect(() => {
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    endRef.current?.scrollIntoView?.({ behavior: reduce ? 'auto' : 'smooth', block: 'end' });
  }, [messages, pending]);

  async function send(text) {
    const message = text.trim();
    if (!message || pending || message.length > COACH_MAX_CHARS) return;
    const userMessage = { id: newId(), role: 'user', text: message };
    setMessages((list) => [...list, userMessage]);
    setInput('');
    setError('');
    setPending(true);
    try {
      const reply = await coachApi.chat(conversationId, message, browserTimeZone());
      setMessages((list) => [
        ...list,
        {
          id: newId(),
          role: 'coach',
          text: reply?.answer ?? '',
          tools: toolChips(reply?.toolsUsed),
          citations: safeCitations(reply?.citations),
        },
      ]);
    } catch (err) {
      // Take the unanswered question back out of the list and put it back in the box.
      setMessages((list) => list.filter((m) => m.id !== userMessage.id));
      setInput((current) => (current.trim() ? current : message));
      setError(coachErrorMessage(err));
    } finally {
      setPending(false);
      inputRef.current?.focus();
    }
  }

  function onKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      send(input);
    }
  }

  function newChat() {
    setConversationId(newId());
    setMessages([]);
    setError('');
    setInput('');
    inputRef.current?.focus();
  }

  const tooLong = input.length > COACH_MAX_CHARS;
  const canSend = !pending && input.trim().length > 0 && !tooLong;

  return (
    <div className="mx-auto flex h-full max-w-3xl flex-col px-4 sm:px-6">
      <header className="flex items-end justify-between gap-4 pb-4 pt-8 sm:pt-10">
        <div className="min-w-0">
          <h1 className="font-display text-[28px] font-bold leading-tight tracking-tight text-ink">Coach</h1>
          <p className="mt-1 text-sm text-muted">Ask about your routes, the air right now, and what it means for your health.</p>
        </div>
        <Button
          variant="secondary"
          icon="edit"
          className="shrink-0 whitespace-nowrap"
          onClick={newChat}
          disabled={pending || (messages.length === 0 && !error)}
        >
          New chat
        </Button>
      </header>

      <div role="log" aria-live="polite" aria-relevant="additions" aria-label="Conversation" className="min-h-0 flex-1 overflow-y-auto pb-4">
        {messages.length === 0 && !pending ? (
          <EmptyCoach onPick={send} />
        ) : (
          <ol className="flex flex-col gap-4">
            <AnimatePresence initial={false}>
              {messages.map((m) => (
                <motion.li
                  key={m.id}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  className={m.role === 'user' ? 'flex justify-end' : 'flex justify-start'}
                >
                  {m.role === 'user' ? <UserBubble text={m.text} /> : <CoachAnswer message={m} />}
                </motion.li>
              ))}
            </AnimatePresence>
          </ol>
        )}
        {pending && <Thinking elapsed={elapsed} />}
        <div ref={endRef} />
      </div>

      <div className="shrink-0 pb-4 pt-2">
        {error && (
          <div className="mb-2">
            <ErrorNote>{error}</ErrorNote>
          </div>
        )}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            send(input);
          }}
          className="rounded-2xl bg-surface p-2 shadow-card ring-1 ring-hairline focus-within:ring-2 focus-within:ring-accent/40"
        >
          <label htmlFor={inputId} className="sr-only">
            Message the Coach
          </label>
          <textarea
            id={inputId}
            ref={inputRef}
            rows={2}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={onKeyDown}
            aria-describedby={counterId}
            aria-invalid={tooLong || undefined}
            placeholder="Ask the Coach… (Enter to send, Shift+Enter for a new line)"
            className="block max-h-48 min-h-[3rem] w-full resize-y bg-transparent px-2.5 py-2 text-sm text-ink placeholder:text-faint focus:outline-none"
          />
          <div className="flex items-center justify-between gap-3 px-1.5 pt-1">
            <span id={counterId} className={`text-xs tabular-nums ${tooLong ? 'text-danger' : 'text-faint'}`}>
              {input.length.toLocaleString()} / {COACH_MAX_CHARS.toLocaleString()}
              {tooLong && <span className="sr-only"> characters, too long</span>}
            </span>
            <Button type="submit" icon="send" disabled={!canSend} aria-label={pending ? 'Sending' : 'Send'}>
              Send
            </Button>
          </div>
        </form>
        <p className="mt-2 flex items-center justify-center gap-1.5 text-center text-xs text-faint">
          <Icon name="info" className="h-3.5 w-3.5 shrink-0" />
          Not medical advice. Air data is modelled on a 10–40 km grid. For symptoms, see a doctor.
        </p>
      </div>
    </div>
  );
}

function EmptyCoach({ onPick }) {
  return (
    <div className="flex flex-col items-center px-2 py-10 text-center sm:py-14">
      <span className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-accent/10 text-accent-ink">
        <Icon name="sparkle" className="h-6 w-6" />
      </span>
      <h2 className="text-base font-semibold text-ink">Ask the Coach</h2>
      <p className="mt-1.5 max-w-sm text-sm text-muted">
        It can read your saved routes and their history, check the air at a place, and look up health guidance.
      </p>
      <ul className="mt-6 flex w-full max-w-md flex-col gap-2" aria-label="Suggested questions">
        {SUGGESTIONS.map((q) => (
          <li key={q}>
            <button
              type="button"
              onClick={() => onPick(q)}
              className="w-full rounded-xl bg-surface px-4 py-3 text-left text-sm text-ink-2 shadow-card ring-1 ring-hairline transition-colors hover:bg-surface-2 hover:text-ink focus-visible:outline-2 focus-visible:outline-accent"
            >
              {q}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function UserBubble({ text }) {
  return (
    <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl rounded-br-md bg-accent px-4 py-2.5 text-sm text-on-accent">
      <span className="sr-only">You: </span>
      {text}
    </div>
  );
}

function CoachAnswer({ message }) {
  return (
    <div className="max-w-[92%] rounded-2xl rounded-bl-md bg-surface px-4 py-3 shadow-card ring-1 ring-hairline">
      <span className="sr-only">Coach: </span>
      <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-ink">{plainText(message.text)}</p>
      {message.tools.length > 0 && (
        <ul className="mt-3 flex flex-wrap gap-1.5" aria-label="What the Coach checked">
          {message.tools.map((t) => (
            <li key={t.id} className="inline-flex items-center gap-1 rounded-full bg-surface-2 px-2.5 py-1 text-xs text-muted">
              <Icon name="check" className="h-3 w-3" />
              {t.label}
            </li>
          ))}
        </ul>
      )}
      {message.citations.length > 0 && (
        <div className="mt-3 border-t border-line pt-2.5">
          <p className="text-xs font-medium text-faint">Sources</p>
          <ul className="mt-1 flex flex-col gap-1">
            {message.citations.map((c) => (
              <li key={c.url + c.title}>
                <a
                  href={c.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-xs text-accent-ink underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-accent"
                >
                  {c.title}
                  <Icon name="external" className="h-3 w-3" />
                  <span className="sr-only"> (opens in a new tab)</span>
                </a>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function Thinking({ elapsed }) {
  return (
    <div role="status" className="mt-4 flex items-center gap-2 text-sm text-muted">
      <span className="flex gap-1" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className="h-1.5 w-1.5 animate-pulse rounded-full bg-accent motion-reduce:animate-none"
            style={{ animationDelay: `${i * 150}ms` }}
          />
        ))}
      </span>
      Thinking…{elapsed >= SHOW_ELAPSED_AFTER_S ? ` ${elapsed} s` : ''}
    </div>
  );
}
