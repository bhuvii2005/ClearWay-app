import { useCallback, useEffect, useRef, useState } from 'react';

/** Language used when the browser's own language isn't English. Change it here. */
export const FALLBACK_SPEECH_LANG = 'en-IN';

/** Listening stops after this long, even if the browser keeps going. */
export const MAX_LISTEN_MS = 30_000;

/** After asking the browser to stop, give up waiting for its final result after this long. */
const STOP_GRACE_MS = 2_000;

/** The browser's language if it is English (en-GB, en-US, en-IN…), otherwise {@link FALLBACK_SPEECH_LANG}. */
export function speechLang(language = typeof navigator === 'undefined' ? '' : navigator.language) {
  return typeof language === 'string' && language.toLowerCase().startsWith('en') ? language : FALLBACK_SPEECH_LANG;
}

/**
 * The browser's speech recognition class, or null. The Web Speech API only works in a secure
 * context (HTTPS or localhost), so it counts as unsupported anywhere else.
 */
export function speechRecognitionClass(win = typeof window === 'undefined' ? undefined : window) {
  if (!win || !win.isSecureContext) return null;
  return win.SpeechRecognition || win.webkitSpeechRecognition || null;
}

const HANDLERS = ['onstart', 'onresult', 'onerror', 'onend'];

/**
 * Speech-to-text with the browser's Web Speech API (no backend, no AI quota).
 *
 * Recognised text is handed to {@code onFinal}; nothing is ever sent from here. While listening,
 * {@code interimText} holds the words heard so far. {@code error} is the recognition error code
 * ("not-allowed", "no-speech"…); "aborted" is never reported because the user or the app chose it.
 *
 * {@code stop()} ends listening and keeps what was said; {@code abort()} ends it and throws the rest
 * away. Listening also ends after {@link MAX_LISTEN_MS}, when the tab is hidden, and on unmount.
 */
export function useSpeechRecognition({ onFinal, lang = speechLang() } = {}) {
  const [Recognition] = useState(() => speechRecognitionClass());
  const [listening, setListening] = useState(false);
  const [interimText, setInterimText] = useState('');
  const [error, setError] = useState('');
  const recRef = useRef(null);
  const timersRef = useRef([]);
  const mountedRef = useRef(true);
  const onFinalRef = useRef(onFinal);

  useEffect(() => {
    onFinalRef.current = onFinal;
  }, [onFinal]);

  /** Detach the current recognition, clear timers and go back to idle. */
  const finish = useCallback((rec) => {
    if (!rec || recRef.current !== rec) return;
    HANDLERS.forEach((h) => {
      rec[h] = null;
    });
    timersRef.current.forEach(clearTimeout);
    timersRef.current = [];
    recRef.current = null;
    if (mountedRef.current) {
      setListening(false);
      setInterimText('');
    }
  }, []);

  const abort = useCallback(() => {
    const rec = recRef.current;
    if (!rec) return;
    finish(rec);
    try {
      rec.abort();
    } catch {
      // already stopped
    }
  }, [finish]);

  const stop = useCallback(() => {
    const rec = recRef.current;
    if (!rec) return;
    try {
      rec.stop();
    } catch {
      abort();
      return;
    }
    // The final result and "end" normally follow; don't wait for them forever.
    timersRef.current.push(setTimeout(() => recRef.current === rec && abort(), STOP_GRACE_MS));
  }, [abort]);

  const start = useCallback(() => {
    if (!Recognition || recRef.current) return;
    const rec = new Recognition();
    rec.continuous = false;
    rec.interimResults = true;
    rec.maxAlternatives = 1;
    rec.lang = lang;

    rec.onresult = (event) => {
      if (recRef.current !== rec) return;
      let finalText = '';
      let interim = '';
      for (let i = event.resultIndex ?? 0; i < event.results.length; i += 1) {
        const result = event.results[i];
        const transcript = result?.[0]?.transcript ?? '';
        if (result.isFinal) finalText += transcript;
        else interim += transcript;
      }
      if (finalText.trim()) {
        setInterimText('');
        onFinalRef.current?.(finalText.trim());
      } else {
        setInterimText(interim.trim());
      }
    };
    rec.onerror = (event) => {
      if (recRef.current !== rec) return;
      if (event?.error && event.error !== 'aborted') setError(event.error);
    };
    rec.onend = () => finish(rec);

    recRef.current = rec;
    setError('');
    setInterimText('');
    setListening(true);
    timersRef.current.push(setTimeout(() => recRef.current === rec && stop(), MAX_LISTEN_MS));
    try {
      rec.start();
    } catch {
      finish(rec);
      setError('start-failed');
    }
  }, [Recognition, lang, finish, stop]);

  const clearError = useCallback(() => setError(''), []);

  // A hidden tab stops listening (the microphone shouldn't stay on in the background).
  useEffect(() => {
    if (!Recognition) return undefined;
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') abort();
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [Recognition, abort]);

  // Leaving the page: stop the microphone, and never touch state again.
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      abort();
    };
  }, [abort]);

  return { supported: Boolean(Recognition), listening, interimText, error, start, stop, abort, clearError };
}
