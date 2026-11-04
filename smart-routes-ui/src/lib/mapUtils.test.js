import { describe, expect, it } from 'vitest';
import { airStops, boundsOf, gradientExpression } from './mapUtils';
import { scoreBand } from './format';

const line = [[77.0, 28.0], [77.01, 28.0], [77.02, 28.0], [77.03, 28.0], [77.04, 28.0]];

describe('boundsOf', () => {
  it('returns west-south and east-north corners', () => {
    expect(boundsOf([[77.2, 28.6], [77.1, 28.7]])).toEqual([[77.1, 28.6], [77.2, 28.7]]);
    expect(boundsOf([])).toBeNull();
  });
});

describe('airStops', () => {
  it('places each air sample at its point along the route', () => {
    const stops = airStops(line, [[77.0, 28.0, 10], [77.02, 28.0, 70], [77.04, 28.0, 120]], 60);
    const [a, b, c] = stops.map(([at]) => at);
    expect(a).toBe(0);
    expect(b).toBeCloseTo(0.5, 6);
    expect(c).toBe(1);
    expect(stops.map(([, c]) => c)).toEqual([scoreBand(10).color, scoreBand(70).color, scoreBand(120).color]);
  });

  it('uses one colour from the route score when there are no samples', () => {
    const stops = airStops(line, undefined, 95);
    expect(stops).toEqual([[0, scoreBand(95).color], [1, scoreBand(95).color]]);
  });
});

describe('gradientExpression', () => {
  const stops = [[0, '#0f0'], [0.5, '#ff0'], [1, '#f00']];
  const positions = (expr) => expr.slice(3).filter((_, i) => i % 2 === 0);

  it('draws the full route when fully revealed', () => {
    const expr = gradientExpression(stops, 1);
    expect(expr.slice(0, 3)).toEqual(['interpolate', ['linear'], ['line-progress']]);
    expect(positions(expr)).toEqual([0, 0.5, 1]);
  });

  it('hides the rest of the route while it animates in', () => {
    const expr = gradientExpression(stops, 0.25);
    expect(expr.at(-1)).toBe('rgba(0,0,0,0)');
    const pos = positions(expr);
    expect(pos.every((p, i) => i === 0 || p > pos[i - 1])).toBe(true); // strictly increasing, as MapLibre requires
    expect(pos.at(-1)).toBe(1);
  });

  it('is valid at the very start of the animation', () => {
    const pos = positions(gradientExpression(stops, 0));
    expect(pos.length).toBeGreaterThanOrEqual(2);
    expect(pos.every((p, i) => i === 0 || p > pos[i - 1])).toBe(true);
  });
});
