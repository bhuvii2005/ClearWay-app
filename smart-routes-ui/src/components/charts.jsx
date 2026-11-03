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

// Recessive chart chrome on the dark surface; text uses ink tokens, never series colours.
const GRID = '#1e293b';
const AXIS_TEXT = '#94a3b8';
const LINE = '#38bdf8';
const ALERT_LEVEL = 75;

const axisProps = {
  stroke: GRID,
  tick: { fill: AXIS_TEXT, fontSize: 12 },
  tickLine: false,
  axisLine: { stroke: '#334155' },
};

function TooltipBox({ title, score }) {
  const band = scoreBand(score);
  return (
    <div className="rounded-lg border border-slate-700 bg-slate-900/95 px-3 py-2 shadow-xl">
      <p className="text-xs text-slate-400">{title}</p>
      <p className="mt-0.5 flex items-center gap-2 text-sm font-semibold text-slate-100">
        <span className="h-2 w-2 rounded-full" style={{ backgroundColor: band.color }} aria-hidden="true" />
        {formatScore(score)} · {band.label}
      </p>
    </div>
  );
}

/** Exposure score for each reading in the chosen period. */
export function ScoreTrendChart({ points, days }) {
  const data = points
    .filter((p) => p.pollutionScore != null)
    .map((p) => ({ t: new Date(p.timestamp).getTime(), score: p.pollutionScore }));

  const tickFormat = (t) =>
    new Date(t).toLocaleString(undefined, days <= 1 ? { hour: 'numeric' } : { day: 'numeric', month: 'short' });
  const maxScore = Math.max(ALERT_LEVEL + 10, ...data.map((d) => d.score));

  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={data} margin={{ top: 12, right: 12, bottom: 0, left: -12 }}>
        <CartesianGrid stroke={GRID} vertical={false} />
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
          stroke="#475569"
          strokeDasharray="4 4"
          label={{ value: 'Alert level', position: 'insideTopRight', fill: AXIS_TEXT, fontSize: 11 }}
        />
        <Tooltip
          cursor={{ stroke: '#475569', strokeWidth: 1 }}
          content={({ active, payload }) =>
            active && payload?.length ? (
              <TooltipBox title={formatDateTime(payload[0].payload.t)} score={payload[0].payload.score} />
            ) : null
          }
        />
        <Line
          type="monotone"
          dataKey="score"
          stroke={LINE}
          strokeWidth={2}
          dot={data.length < 30 ? { r: 3, fill: LINE, strokeWidth: 0 } : false}
          activeDot={{ r: 5, fill: LINE, stroke: '#0f172a', strokeWidth: 2 }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

/** Average exposure for each hour of the day; bar colour follows the air-quality band. */
export function HourlyChart({ hourlyAverages, bestHour }) {
  const data = Array.from({ length: 24 }, (_, hour) => ({
    hour,
    score: hourlyAverages?.[hour] ?? hourlyAverages?.[String(hour)] ?? null,
  }));

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={data} margin={{ top: 12, right: 8, bottom: 0, left: -12 }} barCategoryGap={2}>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis dataKey="hour" tickFormatter={formatHour} interval={2} {...axisProps} />
        <YAxis allowDecimals={false} width={44} {...axisProps} />
        <Tooltip
          cursor={{ fill: 'rgba(148,163,184,0.08)' }}
          content={({ active, payload }) =>
            active && payload?.length && payload[0].payload.score != null ? (
              <TooltipBox
                title={`${formatHour(payload[0].payload.hour)}${payload[0].payload.hour === bestHour ? ' · cleanest hour' : ''}`}
                score={payload[0].payload.score}
              />
            ) : null
          }
        />
        <Bar dataKey="score" radius={[4, 4, 0, 0]} isAnimationActive={false}>
          {data.map((d) => (
            <Cell key={d.hour} fill={scoreBand(d.score).color} fillOpacity={d.hour === bestHour ? 1 : 0.75} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
