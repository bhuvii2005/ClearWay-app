import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { formatDateTime, formatHour, formatScore, scoreBand } from '../lib/format';
import { useTheme } from '../context/contexts';
import { prefersReducedMotion } from '../lib/mapUtils';

// Recessive chart chrome for each theme; text uses ink colours, never series colours.
const CHROME = {
  light: { grid: '#e3e9f0', axis: '#cbd5e1', text: '#5e6b7e', guide: '#94a3b8', line: '#0369a1', surface: '#ffffff', hover: 'rgba(15,27,45,0.05)' },
  dark: { grid: '#1e2a3a', axis: '#2c3a4d', text: '#9aa8ba', guide: '#475569', line: '#5eead4', surface: '#0d131d', hover: 'rgba(148,163,184,0.08)' },
};
const ALERT_LEVEL = 75;

function useChrome() {
  const { theme } = useTheme();
  const c = CHROME[theme] ?? CHROME.light;
  const axisProps = { stroke: c.grid, tick: { fill: c.text, fontSize: 12 }, tickLine: false, axisLine: { stroke: c.axis } };
  return { c, axisProps };
}

function TooltipBox({ title, score }) {
  const band = scoreBand(score);
  return (
    <div className="rounded-xl bg-surface/95 px-3 py-2 shadow-pop ring-1 ring-hairline backdrop-blur">
      <p className="text-xs text-muted">{title}</p>
      <p className="mt-0.5 flex items-center gap-2 text-sm font-semibold text-ink">
        <span className="h-2 w-2 rounded-full" style={{ backgroundColor: band.color }} aria-hidden="true" />
        {formatScore(score)} · {band.label}
      </p>
    </div>
  );
}

/** Exposure score for each reading in the chosen period. */
export function ScoreTrendChart({ points, days }) {
  const { c, axisProps } = useChrome();
  const data = points
    .filter((p) => p.pollutionScore != null)
    .map((p) => ({ t: new Date(p.timestamp).getTime(), score: p.pollutionScore }));

  const tickFormat = (t) =>
    new Date(t).toLocaleString(undefined, days <= 1 ? { hour: 'numeric' } : { day: 'numeric', month: 'short' });
  const maxScore = Math.max(ALERT_LEVEL + 10, ...data.map((d) => d.score));

  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: -12 }}>
        <CartesianGrid stroke={c.grid} vertical={false} />
        <XAxis
          dataKey="t"
          type="number"
          scale="time"
          domain={['dataMin', 'dataMax']}
          tickFormatter={tickFormat}
          minTickGap={40}
          {...axisProps}
        />
        <YAxis domain={[0, Math.ceil(maxScore / 10) * 10]} allowDecimals={false} width={44} {...axisProps} />
        <ReferenceLine
          y={ALERT_LEVEL}
          stroke={c.guide}
          strokeDasharray="4 4"
          label={{ value: 'Alert level', position: 'insideTopRight', fill: c.text, fontSize: 11 }}
        />
        <Tooltip
          cursor={{ stroke: c.guide, strokeWidth: 1 }}
          content={({ active, payload }) =>
            active && payload?.length ? (
              <TooltipBox title={formatDateTime(payload[0].payload.t)} score={payload[0].payload.score} />
            ) : null
          }
        />
        <Line
          type="monotone"
          dataKey="score"
          stroke={c.line}
          strokeWidth={2}
          dot={data.length < 30 ? { r: 3, fill: c.line, strokeWidth: 0 } : false}
          activeDot={{ r: 5, fill: c.line, stroke: c.surface, strokeWidth: 2 }}
          isAnimationActive={!prefersReducedMotion()}
          animationDuration={900}
          animationEasing="ease-out"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

/** Average exposure for each hour of the day; bar colour follows the air-quality band. */
export function HourlyChart({ hourlyAverages, bestHour }) {
  const { c, axisProps } = useChrome();
  const data = Array.from({ length: 24 }, (_, hour) => ({
    hour,
    score: hourlyAverages?.[hour] ?? hourlyAverages?.[String(hour)] ?? null,
  }));

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={data} margin={{ top: 12, right: 8, bottom: 0, left: -12 }} barCategoryGap={2}>
        <CartesianGrid stroke={c.grid} vertical={false} />
        <XAxis dataKey="hour" tickFormatter={formatHour} interval={2} {...axisProps} />
        <YAxis allowDecimals={false} width={44} {...axisProps} />
        <Tooltip
          cursor={{ fill: c.hover }}
          content={({ active, payload }) =>
            active && payload?.length && payload[0].payload.score != null ? (
              <TooltipBox
                title={`${formatHour(payload[0].payload.hour)}${payload[0].payload.hour === bestHour ? ' · cleanest hour' : ''}`}
                score={payload[0].payload.score}
              />
            ) : null
          }
        />
        <Bar dataKey="score" radius={[4, 4, 0, 0]} isAnimationActive={!prefersReducedMotion()} animationDuration={700} animationEasing="ease-out">
          {data.map((d) => (
            <Cell key={d.hour} fill={scoreBand(d.score).color} fillOpacity={d.hour === bestHour ? 1 : 0.75} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
