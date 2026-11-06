import { motion } from 'motion/react';
import Icon from './Icon';

const ease = [0.22, 1, 0.36, 1];

// The two routes in the illustration: a fast one through a haze of traffic, a clean one past a park.
const FAST = 'M70 250 C 150 250, 190 150, 260 150 S 380 120, 430 70';
const CLEAN = 'M70 250 C 110 300, 200 300, 250 260 S 330 180, 360 150 S 410 90, 430 70';

/** A small animated map: the clean route draws itself, a dot travels it, and the trade-off pops up. */
export default function RouteIllustration({ className = 'mt-8' }) {
  return (
    <div className={`relative overflow-hidden rounded-3xl bg-surface shadow-float ${className}`}>
      <svg viewBox="0 0 500 330" className="block w-full" aria-hidden="true">
        {/* streets */}
        <g className="stroke-line" strokeWidth="10" strokeLinecap="round" fill="none">
          <path d="M0 110 H500" />
          <path d="M0 205 H500" />
          <path d="M120 0 V330" />
          <path d="M320 0 V330" />
          <path d="M0 300 L500 20" strokeWidth="6" />
        </g>
        {/* park and haze */}
        <rect x="180" y="225" width="120" height="80" rx="22" className="fill-good/15" />
        <circle cx="240" cy="150" r="62" className="fill-haze/20" />
        <circle cx="240" cy="150" r="34" className="fill-haze/20" />

        <motion.path
          d={FAST}
          fill="none"
          strokeWidth="5"
          strokeLinecap="round"
          strokeDasharray="2 10"
          className="stroke-faint"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.8, ease }}
        />
        <motion.path
          d={CLEAN}
          fill="none"
          strokeWidth="11"
          strokeLinecap="round"
          className="stroke-surface"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ delay: 0.4, duration: 1.4, ease }}
        />
        <motion.path
          d={CLEAN}
          fill="none"
          strokeWidth="6"
          strokeLinecap="round"
          className="stroke-accent"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: 1 }}
          transition={{ delay: 0.4, duration: 1.4, ease }}
        />
        <circle r="6" className="route-runner fill-surface stroke-accent" strokeWidth="3" style={{ offsetPath: `path('${CLEAN}')` }} />

        <circle cx="70" cy="250" r="9" className="fill-accent stroke-surface" strokeWidth="3" />
        <circle cx="430" cy="70" r="9" className="fill-haze stroke-surface" strokeWidth="3" />
      </svg>

      <motion.div
        initial={{ opacity: 0, y: 8, scale: 0.95 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ delay: 1.6, type: 'spring', stiffness: 300, damping: 22 }}
        className="absolute bottom-4 right-4 rounded-2xl bg-surface/95 px-3.5 py-2.5 shadow-pop backdrop-blur"
      >
        <p className="flex items-center gap-1.5 text-sm font-semibold text-good">
          <Icon name="leaf" className="h-4 w-4" /> 38% less PM2.5
        </p>
        <p className="mt-0.5 text-xs text-muted">+3 min vs the fastest route</p>
      </motion.div>
      <motion.span
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.2 }}
        className="absolute left-[40%] top-[30%] rounded-full bg-surface/90 px-2 py-0.5 text-[11px] font-medium text-warning shadow-card"
      >
        Traffic haze
      </motion.span>
    </div>
  );
}
