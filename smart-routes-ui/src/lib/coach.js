import { errorMessage } from './api';

export const COACH_MAX_CHARS = 2000;

/** Friendly names for the Coach's tools, shown as chips under an answer. */
export const TOOL_LABELS = {
  listMyRoutes: 'Checked your routes',
  getRouteHistory: 'Read route history',
  getCurrentAirQuality: 'Checked live air quality',
  getMySensitivities: 'Checked your sensitivities',
  searchKnowledgeBase: 'Searched health guidance',
};

export const SUGGESTIONS = [
  'When is the best time of day to walk my saved routes?',
  'What is the WHO guideline level for PM2.5?',
  'Is it OK to go for a run when the air quality is poor?',
];

/** Tool chips for an answer: friendly labels, each tool once, unknown tools left out. */
export function toolChips(toolsUsed = []) {
  return [...new Set(toolsUsed)].filter((t) => TOOL_LABELS[t]).map((t) => ({ id: t, label: TOOL_LABELS[t] }));
}

/** Only http(s) links are shown; anything else is dropped. */
export function safeCitations(citations = []) {
  return citations.filter((c) => {
    try {
      const url = new URL(c?.url);
      return (url.protocol === 'https:' || url.protocol === 'http:') && typeof c.title === 'string';
    } catch {
      return false;
    }
  });
}

/**
 * The model sometimes writes light Markdown. The page shows plain text only, so the markers are
 * removed rather than rendered: **bold** and `code` lose their marks, list bullets become "•".
 */
export function plainText(answer = '') {
  return answer
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/__(.+?)__/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^(\s*)[*-]\s+/gm, '$1• ')
    .replace(/^#{1,6}\s+/gm, '');
}

/**
 * Appends spoken text to what's already typed, with a space between, never past {@code max}.
 * Typed text is never cut: only the transcript is trimmed to fit.
 *
 * @returns {{ text: string, truncated: boolean }}
 */
export function appendTranscript(current = '', transcript = '', max = COACH_MAX_CHARS) {
  const spoken = transcript.trim();
  if (!spoken) return { text: current, truncated: false };
  const separator = current && !/\s$/.test(current) ? ' ' : '';
  const room = Math.max(0, max - current.length - separator.length);
  if (room === 0) return { text: current, truncated: true };
  const added = spoken.slice(0, room);
  return { text: current + separator + added, truncated: added.length < spoken.length };
}

export const VOICE_SUPPORT_NOTE = 'Voice input works in Chrome, Edge and Safari.';
export const VOICE_PRIVACY_NOTE = "Voice uses your browser's speech service; in Chrome, audio is sent to Google to transcribe.";

const SPEECH_ERRORS = {
  'not-allowed': "Microphone access is blocked. Allow it in your browser's site settings.",
  'service-not-allowed': "Microphone access is blocked. Allow it in your browser's site settings.",
  'audio-capture': 'No microphone found.',
  'no-speech': "Didn't catch that. Try again.",
  network: 'Speech service unreachable. Check your connection.',
};

/** Friendly text for a speech recognition error code; '' for "aborted" (the user or the app stopped it). */
export function speechErrorMessage(code) {
  if (!code || code === 'aborted') return '';
  return SPEECH_ERRORS[code] ?? 'Voice input stopped. Please try again.';
}

/** errorMessage() for the Coach, with the cases that only make sense here. */
export function coachErrorMessage(error) {
  if (!error?.response) {
    if (error?.code === 'ECONNABORTED') return 'That took too long. Please try again.';
    return errorMessage(error);
  }
  if (error.response.status === 503 && !error.response.data?.code) {
    return 'Coach is offline right now. Please try again later.';
  }
  return errorMessage(error, 'Coach could not answer. Please try again.');
}
