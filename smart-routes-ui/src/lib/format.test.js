import { describe, expect, it } from 'vitest';
import {
  exposureText,
  formatDistanceMeters,
  formatDuration,
  formatHour,
  formatScore,
  modeLabel,
  scoreBand,
} from './format';

describe('scoreBand', () => {
  it('follows the European AQI bands', () => {
    expect(scoreBand(10).label).toBe('Good');
    expect(scoreBand(20).label).toBe('Good');
    expect(scoreBand(35).label).toBe('Fair');
    expect(scoreBand(55).label).toBe('Moderate');
    expect(scoreBand(75).label).toBe('Poor');
    expect(scoreBand(95).label).toBe('Very poor');
    expect(scoreBand(140).label).toBe('Extremely poor');
  });

  it('uses four status colours, never one per band', () => {
    const colours = new Set([10, 35, 55, 75, 95, 140].map((s) => scoreBand(s).color));
    expect(colours.size).toBe(4);
  });

  it('handles missing scores', () => {
    expect(scoreBand(null).label).toBe('No data');
    expect(scoreBand(undefined).status).toBe('none');
    expect(formatScore(null)).toBe('–');
  });
});

describe('exposureText', () => {
  it('labels the fastest route', () => {
    expect(exposureText({ fastest: true, exposureSavingPercent: 0 })).toBe('Fastest route');
  });

  it('describes savings and extra exposure without a minus sign', () => {
    expect(exposureText({ fastest: false, exposureSavingPercent: 12.4 })).toBe('12% less exposure than the fastest');
    expect(exposureText({ fastest: false, exposureSavingPercent: -3.6 })).toBe('4% more exposure than the fastest');
    expect(exposureText({ fastest: false, exposureSavingPercent: 0.2 })).toBe('Same exposure as the fastest');
  });
});

describe('formatting', () => {
  it('formats distances', () => {
    expect(formatDistanceMeters(640)).toBe('640 m');
    expect(formatDistanceMeters(3840)).toBe('3.8 km');
  });

  it('formats durations', () => {
    expect(formatDuration(20)).toBe('1 min');
    expect(formatDuration(17 * 60)).toBe('17 min');
    expect(formatDuration(80 * 60)).toBe('1 h 20 min');
    expect(formatDuration(120 * 60)).toBe('2 h');
  });

  it('formats hours of the day', () => {
    expect(formatHour(0)).toBe('12 AM');
    expect(formatHour(9)).toBe('9 AM');
    expect(formatHour(12)).toBe('12 PM');
    expect(formatHour(17)).toBe('5 PM');
  });

  it('shows the backend "commute" type as driving', () => {
    expect(modeLabel('commute')).toBe('Drive');
    expect(modeLabel('cycling')).toBe('Cycle');
  });
});
