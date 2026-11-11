import {
  ArrowLeft,
  ArrowUpDown,
  Bell,
  Bike,
  Bookmark,
  Car,
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  Clock,
  ExternalLink,
  Eye,
  Footprints,
  Info,
  Leaf,
  LocateFixed,
  LogOut,
  Map,
  MessageCircle,
  MapPin,
  Moon,
  PersonStanding,
  Route,
  Search,
  SendHorizontal,
  ShieldCheck,
  Sparkles,
  Sun,
  Trash2,
  Pencil,
  User,
  Wind,
  X,
} from 'lucide-react';

/** Names used across the app, mapped to Lucide icons so every icon shares one drawing style. */
const ICONS = {
  alert: CircleAlert,
  back: ArrowLeft,
  bell: Bell,
  bike: Bike,
  car: Car,
  chat: MessageCircle,
  check: Check,
  chevron: ChevronRight,
  chevronDown: ChevronDown,
  clock: Clock,
  edit: Pencil,
  external: ExternalLink,
  eye: Eye,
  info: Info,
  leaf: Leaf,
  locate: LocateFixed,
  logout: LogOut,
  map: Map,
  moon: Moon,
  pin: MapPin,
  route: Route,
  run: PersonStanding,
  save: Bookmark,
  search: Search,
  send: SendHorizontal,
  shield: ShieldCheck,
  sparkle: Sparkles,
  sun: Sun,
  swap: ArrowUpDown,
  trash: Trash2,
  user: User,
  walk: Footprints,
  wind: Wind,
  x: X,
};

export default function Icon({ name, className = 'h-5 w-5', strokeWidth = 1.9, title }) {
  const Glyph = ICONS[name] ?? Info;
  return (
    <Glyph
      className={className}
      strokeWidth={strokeWidth}
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
      aria-label={title}
    />
  );
}
