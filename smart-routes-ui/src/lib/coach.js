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
