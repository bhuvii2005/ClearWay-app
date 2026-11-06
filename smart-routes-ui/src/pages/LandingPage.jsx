import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { Logo } from '../components/Layout';
import Icon from '../components/Icon';
import ThemeSwitch from '../components/ThemeSwitch';
import RouteIllustration from '../components/RouteIllustration';
import { AirRing, BandChip, PollutantReading } from '../components/ui';
import { buttonClass } from '../components/buttonStyles';
import { formatHour, scoreBand, whoComparison } from '../lib/format';

const ease = [0.22, 1, 0.36, 1];

/** Fades a block up into place the first time it scrolls into view. */
function Reveal({ children, delay = 0, className = '' }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 18 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-60px' }}
      transition={{ duration: 0.6, delay, ease }}
      className={className}
    >
      {children}
    </motion.div>
  );
}

const STEPS = [
  { icon: 'search', title: 'Say where you’re going', text: 'Pick a start and a destination, and how you’re travelling.' },
  { icon: 'route', title: 'Compare by the air', text: 'Each route is scored on the air along its whole length, not just at the ends.' },
  { icon: 'bell', title: 'Save it and relax', text: 'ClearWay checks saved routes every 15 minutes and tells you when the air turns poor.' },
];

export default function LandingPage() {
  return (
    <div className="min-h-dvh bg-canvas text-ink">
      <header className="sticky top-0 z-50 bg-canvas/80 shadow-[0_1px_0_var(--color-hairline)] backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
          <Logo className="text-xl" />
          <nav aria-label="Page" className="hidden items-center gap-7 text-sm font-medium text-muted md:flex">
            <a href="#how" className="transition-colors hover:text-ink">How it works</a>
            <a href="#features" className="transition-colors hover:text-ink">Features</a>
            <a href="#data" className="transition-colors hover:text-ink">The data</a>
          </nav>
          <div className="flex items-center gap-2">
            <ThemeSwitch />
            <Link to="/login" className={buttonClass('ghost', 'hidden sm:inline-flex')}>
              Sign in
            </Link>
            <Link to="/signup" className={buttonClass('primary')}>
              Get started
            </Link>
          </div>
        </div>
      </header>

      <main>
        {/* Hero */}
        <section className="relative overflow-hidden">
          <div
            className="pointer-events-none absolute inset-0"
            style={{ background: 'radial-gradient(50% 60% at 20% 10%, color-mix(in srgb, var(--color-accent) 12%, transparent), transparent 70%)' }}
            aria-hidden="true"
          />
          <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 pb-16 pt-14 sm:px-6 lg:grid-cols-[1.15fr_1fr] lg:pb-24 lg:pt-20">
            <div>
              <motion.p
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, ease }}
                className="inline-flex items-center gap-2 rounded-full bg-surface px-3 py-1 text-xs font-medium text-ink-2 shadow-card"
              >
                <span className="relative flex h-2 w-2">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-good opacity-60 motion-reduce:animate-none" />
                  <span className="relative inline-flex h-2 w-2 rounded-full bg-good" />
                </span>
                Live air quality on every route
              </motion.p>
              <motion.h1
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, delay: 0.05, ease }}
                className="mt-5 font-display text-[40px] font-bold leading-[1.05] tracking-tight sm:text-[50px] lg:text-[46px] xl:text-[50px]"
              >
                <span className="sm:whitespace-nowrap">The cleanest way there,</span>
                <br />
                <span className="text-accent-ink">not just the fastest.</span>
              </motion.h1>
              <motion.p
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, delay: 0.12, ease }}
                className="mt-5 max-w-md text-[17px] leading-relaxed text-muted"
              >
                ClearWay compares routes by the air you’ll breathe on them, so you can walk, run, cycle or drive through
                less pollution. Often for only a minute or two more.
              </motion.p>
              <motion.div
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.7, delay: 0.2, ease }}
                className="mt-8 flex flex-wrap items-center gap-3"
              >
                <Link to="/signup" className={buttonClass('primary', 'h-12 px-6 text-[15px]')}>
                  Create a free account <Icon name="chevron" className="h-4 w-4" />
                </Link>
                <Link to="/login" className={buttonClass('secondary', 'h-12 px-6 text-[15px]')}>
                  Sign in
                </Link>
              </motion.div>
              <ul className="mt-8 flex flex-wrap gap-x-5 gap-y-2 text-[13px] text-muted">
                {['Free to use', 'Walk, jog, cycle or drive', 'Light and dark mode'].map((t) => (
                  <li key={t} className="flex items-center gap-1.5">
                    <Icon name="check" className="h-4 w-4 text-good" /> {t}
                  </li>
                ))}
              </ul>
            </div>
            <RouteIllustration className="" />
          </div>
        </section>

        {/* How it works */}
        <section id="how" className="scroll-mt-20 border-y border-line bg-surface/60">
          <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:py-20">
            <Reveal>
              <p className="text-sm font-semibold text-accent-ink">How it works</p>
              <h2 className="mt-2 max-w-xl font-display text-3xl font-bold tracking-tight sm:text-[36px]">
                Three steps to breathing a little easier.
              </h2>
            </Reveal>
            <ol className="mt-10 grid gap-6 md:grid-cols-3">
              {STEPS.map((s, i) => (
                <Reveal key={s.title} delay={i * 0.08}>
                  <li className="h-full rounded-3xl bg-surface p-6 shadow-card ring-1 ring-hairline">
                    <div className="flex items-center justify-between">
                      <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-accent/10 text-accent-ink">
                        <Icon name={s.icon} className="h-5 w-5" />
                      </span>
                      <span className="font-data text-sm font-semibold text-faint">0{i + 1}</span>
                    </div>
                    <h3 className="mt-5 text-base font-semibold">{s.title}</h3>
                    <p className="mt-1.5 text-sm leading-relaxed text-muted">{s.text}</p>
                  </li>
                </Reveal>
              ))}
            </ol>
          </div>
        </section>

        {/* Features */}
        <section id="features" className="mx-auto max-w-6xl scroll-mt-20 space-y-20 px-4 py-20 sm:px-6 lg:space-y-28 lg:py-28">
          <Feature
            eyebrow="Compare"
            title="Every route, ranked by the air."
            text="See each option’s air score next to its travel time. The coloured strip shows where along the way the air gets worse, and ClearWay spells out the trade-off for you."
            visual={<CompareMock />}
          />
          <Feature
            flip
            eyebrow="Timing"
            title="Know the best hour to go."
            text="Saved routes build up a history. ClearWay turns it into a simple 24-hour view so you can see when your run or commute is usually at its cleanest."
            visual={<BestHourMock />}
          />
          <Feature
            eyebrow="Alerts"
            title="A heads-up when your commute turns poor."
            text="Pick the routes to watch. When the air on one of them crosses into poor, you get an alert straight away, on any page."
            visual={<AlertMock />}
          />
        </section>

        {/* Data */}
        <section id="data" className="scroll-mt-20 bg-surface/60">
          <div className="mx-auto grid max-w-6xl gap-10 px-4 py-20 sm:px-6 lg:grid-cols-2 lg:items-center">
            <Reveal>
              <p className="text-sm font-semibold text-accent-ink">The data</p>
              <h2 className="mt-2 font-display text-3xl font-bold tracking-tight sm:text-[36px]">Numbers you can read, held to WHO guidelines.</h2>
              <p className="mt-4 max-w-md leading-relaxed text-muted">
                Air readings come from Open-Meteo’s air-quality model and are sampled along the whole route. Each pollutant
                is shown against the World Health Organization’s guideline, so “42” actually means something.
              </p>
              <p className="mt-4 max-w-md text-sm leading-relaxed text-muted">
                You can also tell ClearWay what affects you, such as PM2.5 or ozone, and it weighs those more heavily.
              </p>
            </Reveal>
            <Reveal delay={0.1}>
              <div className="rounded-3xl bg-surface p-6 shadow-float ring-1 ring-hairline">
                <div className="flex items-center justify-between">
                  <p className="text-sm font-semibold">Pollutants on this route</p>
                  <span className="rounded-full bg-surface-2 px-2 py-0.5 text-[11px] font-medium text-muted">Example</span>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-2.5">
                  {[
                    ['PM2.5', 'pm25', 36],
                    ['PM10', 'pm10', 52],
                    ['NO₂', 'no2', 18],
                    ['Ozone', 'ozone', 64],
                  ].map(([label, key, value]) => (
                    <PollutantReading key={key} label={label} value={value} who={whoComparison(key, value)} large />
                  ))}
                </dl>
                <p className="mt-4 flex items-center gap-1.5 text-xs text-faint">
                  <span className="inline-block h-2.5 w-0.5 rounded-full bg-ink" /> marks the WHO guideline
                </p>
              </div>
            </Reveal>
          </div>
        </section>

        {/* Final call to action */}
        <section className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <Reveal>
            <div className="relative overflow-hidden rounded-[32px] bg-accent px-6 py-14 text-center text-on-accent sm:px-12">
              <div
                className="pointer-events-none absolute inset-0 opacity-50"
                style={{ background: 'radial-gradient(60% 80% at 50% 0%, rgb(255 255 255 / 0.25), transparent 70%)' }}
                aria-hidden="true"
              />
              <h2 className="relative font-display text-3xl font-bold tracking-tight sm:text-[38px]">Take the cleaner way tomorrow.</h2>
              <p className="relative mx-auto mt-3 max-w-md opacity-90">Plan your first route in under a minute. It’s free.</p>
              <div className="relative mt-8 flex flex-wrap justify-center gap-3">
                <Link
                  to="/signup"
                  className="inline-flex h-12 items-center gap-2 rounded-xl bg-white px-6 text-[15px] font-semibold text-[#0b1f17] shadow-card transition-transform hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                >
                  Get started <Icon name="chevron" className="h-4 w-4" />
                </Link>
                <Link
                  to="/login"
                  className="inline-flex h-12 items-center rounded-xl px-6 text-[15px] font-semibold ring-1 ring-white/40 transition-colors hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                >
                  Sign in
                </Link>
              </div>
            </div>
          </Reveal>
        </section>
      </main>

      <footer className="border-t border-line">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-8 text-xs text-faint sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <Logo className="text-base" />
          <p>Air quality by Open-Meteo · Routing by TomTom · Maps by OpenFreeMap and OpenStreetMap</p>
        </div>
      </footer>
    </div>
  );
}

function Feature({ eyebrow, title, text, visual, flip = false }) {
  return (
    <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-16">
      <Reveal className={flip ? 'lg:order-2' : ''}>
        <p className="text-sm font-semibold text-accent-ink">{eyebrow}</p>
        <h2 className="mt-2 font-display text-3xl font-bold tracking-tight sm:text-[36px]">{title}</h2>
        <p className="mt-4 max-w-md leading-relaxed text-muted">{text}</p>
      </Reveal>
      <Reveal delay={0.1} className={flip ? 'lg:order-1' : ''}>
        {visual}
      </Reveal>
    </div>
  );
}

/** A soft panel that holds each example UI. */
function Stage({ children }) {
  return (
    <div className="relative rounded-[28px] bg-surface-2/70 p-5 ring-1 ring-hairline sm:p-8">
      <span className="absolute right-4 top-4 rounded-full bg-surface px-2 py-0.5 text-[11px] font-medium text-muted shadow-card">Example</span>
      {children}
    </div>
  );
}

const gradient = (scores) =>
  `linear-gradient(90deg, ${scores.map((s, i) => `${scoreBand(s).color} ${((i / (scores.length - 1)) * 100).toFixed(0)}%`).join(', ')})`;

function CompareMock() {
  const routes = [
    { name: 'Cleanest', icon: 'leaf', time: '24 min', score: 28, strip: [24, 22, 30, 34, 26, 22], chips: ['+3 min', '38% less PM2.5'] },
    { name: 'Fastest', icon: 'clock', time: '21 min', score: 57, strip: [30, 48, 66, 72, 58, 40], chips: [] },
  ];
  return (
    <Stage>
      <div className="space-y-3 pt-4">
        {routes.map((r, i) => (
          <div
            key={r.name}
            className={`rounded-2xl bg-surface p-4 shadow-card ${i === 0 ? 'ring-2 ring-accent' : 'ring-1 ring-hairline'}`}
          >
            <div className="flex items-center gap-4">
              <div className="min-w-0 flex-1">
                <p className={`flex items-center gap-1.5 text-xs font-semibold ${i === 0 ? 'text-good' : 'text-muted'}`}>
                  <Icon name={r.icon} className="h-3.5 w-3.5" /> {r.name}
                </p>
                <p className="mt-0.5 font-display text-2xl font-bold tracking-tight">{r.time}</p>
              </div>
              <AirRing score={r.score} size={52} label={`${r.name} route score`} />
            </div>
            <span className="mt-3 block h-1.5 rounded-full" style={{ background: gradient(r.strip) }} aria-hidden="true" />
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <BandChip score={r.score} />
              {r.chips.map((c) => (
                <span key={c} className="rounded-full bg-good/10 px-2 py-0.5 text-[11px] font-medium text-good">
                  {c}
                </span>
              ))}
            </div>
          </div>
        ))}
      </div>
    </Stage>
  );
}

function BestHourMock() {
  // A typical city day: cleaner overnight and mid-afternoon, worse at the rush hours.
  const hours = Array.from({ length: 24 }, (_, h) =>
    Math.round(30 + 22 * Math.exp(-((h - 8.5) ** 2) / 4) + 26 * Math.exp(-((h - 19) ** 2) / 5) - 8 * Math.exp(-((h - 14) ** 2) / 6)),
  );
  const best = hours.slice(6, 22).reduce((b, s, i) => (s < hours[b] ? i + 6 : b), 6);
  return (
    <Stage>
      <div className="rounded-2xl bg-surface p-5 shadow-card ring-1 ring-hairline">
        <p className="text-sm font-semibold">Best time to go</p>
        <p className="mt-0.5 text-[13px] text-muted">
          Usually cleanest around <span className="font-semibold text-good">{formatHour(best)}</span>
        </p>
        <div className="mt-5 flex h-28 items-end gap-[3px]" aria-hidden="true">
          {hours.map((s, h) => (
            <motion.span
              key={h}
              initial={{ scaleY: 0 }}
              whileInView={{ scaleY: 1 }}
              viewport={{ once: true }}
              transition={{ delay: 0.2 + h * 0.025, duration: 0.5, ease }}
              className={`flex-1 origin-bottom rounded-[4px] ${h === best ? 'ring-2 ring-ink ring-offset-2 ring-offset-surface' : ''}`}
              style={{ height: `${Math.max(18, s * 1.2)}%`, backgroundColor: scoreBand(s).color }}
            />
          ))}
        </div>
        <div className="mt-2 flex justify-between text-[11px] text-faint">
          <span>12 AM</span>
          <span>6 AM</span>
          <span>12 PM</span>
          <span>6 PM</span>
          <span>11 PM</span>
        </div>
      </div>
    </Stage>
  );
}

function AlertMock() {
  return (
    <Stage>
      <div className="space-y-3 pt-4">
        <motion.div
          initial={{ opacity: 0, x: 24 }}
          whileInView={{ opacity: 1, x: 0 }}
          viewport={{ once: true }}
          transition={{ delay: 0.3, type: 'spring', stiffness: 260, damping: 24 }}
          className="flex items-start gap-3 rounded-2xl bg-surface p-4 shadow-pop ring-1 ring-hairline"
        >
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-warning/15 text-warning">
            <Icon name="alert" className="h-5 w-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="flex items-center justify-between gap-2 text-sm font-semibold">
              Morning commute <span className="text-[11px] font-normal text-faint">just now</span>
            </p>
            <p className="mt-0.5 text-[13px] text-muted">Air on this route is now poor (score 68). Consider leaving later or taking the park route.</p>
          </div>
        </motion.div>
        <div className="flex items-start gap-3 rounded-2xl bg-surface/70 p-4 ring-1 ring-hairline">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-good/10 text-good">
            <Icon name="leaf" className="h-5 w-5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="flex items-center justify-between gap-2 text-sm font-semibold text-ink-2">
              Evening run <span className="text-[11px] font-normal text-faint">watching</span>
            </p>
            <p className="mt-0.5 text-[13px] text-muted">Fair right now. Checked 4 minutes ago.</p>
          </div>
        </div>
      </div>
    </Stage>
  );
}
