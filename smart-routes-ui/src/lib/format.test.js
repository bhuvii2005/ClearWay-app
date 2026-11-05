import { describe, expect, it } from 'vitest';
import {
  exposureText,
  formatDistanceMeters,
  formatDuration,
  formatHour,
  formatScore,
  modeLabel,
  scoreBand,
  tradeOff,
  whoComparison,
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

describe('readable numbers', () => {
  it('compares readings with the WHO guideline', () => {
    expect(whoComparison('pm25', 12)).toMatchObject({ over: false, short: 'Within WHO' });
    expect(whoComparison('pm25', 36)).toMatchObject({ over: true, text: '2.4× WHO guideline' });
    expect(whoComparison('pm25', 30).short).toBe('2× WHO');
    expect(whoComparison('pm10', 900).short).toBe('20× WHO');
    expect(whoComparison('pm25', null)).toBeNull();
  });

  it('describes the trade-off against the fastest route', () => {
    const fastest = { fastest: true, travelTimeSeconds: 600, pollutionData: { pm25: 40 } };
    const slower = { travelTimeSeconds: 840, pollutionData: { pm25: 25 } };
    expect(tradeOff(slower, fastest)).toEqual({ time: '+4 min', pm25: '38% less PM2.5' });
    expect(tradeOff(fastest, fastest)).toEqual({ time: null, pm25: null });
    expect(tradeOff({ travelTimeSeconds: 610, pollutionData: { pm25: 44 } }, fastest)).toEqual({
      time: 'Same time',
      pm25: '10% more PM2.5',
    });
    expect(tradeOff(slower, undefined)).toEqual({ time: null, pm25: null });
  });
});
