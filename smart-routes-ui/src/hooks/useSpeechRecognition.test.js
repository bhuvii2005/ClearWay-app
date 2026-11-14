import { describe, expect, it, vi } from 'vitest';
import { FALLBACK_SPEECH_LANG, speechLang, speechRecognitionClass } from './useSpeechRecognition';
import { appendTranscript, speechErrorMessage } from '../lib/coach';

describe('speechLang', () => {
  it('keeps an English browser language and falls back to en-IN otherwise', () => {
    expect(speechLang('en-GB')).toBe('en-GB');
    expect(speechLang('EN-us')).toBe('EN-us');
    expect(speechLang('hi-IN')).toBe(FALLBACK_SPEECH_LANG);
    expect(speechLang('')).toBe('en-IN');
    expect(speechLang(null)).toBe('en-IN');
  });

  it("defaults to the browser's language, whatever the machine running the tests uses", () => {
    vi.stubGlobal('navigator', { language: 'en-AU' });
    expect(speechLang()).toBe('en-AU');
    vi.stubGlobal('navigator', { language: 'ta-IN' });
    expect(speechLang()).toBe('en-IN');
    vi.unstubAllGlobals();
  });
});

describe('speechRecognitionClass', () => {
  class Std {}
  class Webkit {}

  it('prefers the standard name, then the webkit one', () => {
    expect(speechRecognitionClass({ isSecureContext: true, SpeechRecognition: Std, webkitSpeechRecognition: Webkit })).toBe(Std);
    expect(speechRecognitionClass({ isSecureContext: true, webkitSpeechRecognition: Webkit })).toBe(Webkit);
  });

  it('is null without the API or outside a secure context', () => {
    expect(speechRecognitionClass({ isSecureContext: true })).toBeNull();
    expect(speechRecognitionClass({ isSecureContext: false, SpeechRecognition: Std })).toBeNull();
    expect(speechRecognitionClass(undefined)).toBeNull();
  });
});

describe('appendTranscript', () => {
  it('appends with one space and trims the transcript', () => {
    expect(appendTranscript('', '  hello  ')).toEqual({ text: 'hello', truncated: false });
    expect(appendTranscript('Hi', 'there')).toEqual({ text: 'Hi there', truncated: false });
    expect(appendTranscript('Hi\n', 'there')).toEqual({ text: 'Hi\nthere', truncated: false });
    expect(appendTranscript('Hi', '   ')).toEqual({ text: 'Hi', truncated: false });
  });

  it('cuts only the transcript at the limit, never the typed text', () => {
    expect(appendTranscript('abcd', 'efghij', 8)).toEqual({ text: 'abcd efg', truncated: true });
    expect(appendTranscript('abcdefgh', 'more', 8)).toEqual({ text: 'abcdefgh', truncated: true });
    // Typed text already over the limit stays as typed.
    expect(appendTranscript('abcdefghij', 'more', 8)).toEqual({ text: 'abcdefghij', truncated: true });
  });
});

describe('speechErrorMessage', () => {
  it('has no message for aborted or nothing, and a fallback for unknown codes', () => {
    expect(speechErrorMessage('aborted')).toBe('');
    expect(speechErrorMessage('')).toBe('');
    expect(speechErrorMessage('language-not-supported')).toBe('Voice input stopped. Please try again.');
  });
});
