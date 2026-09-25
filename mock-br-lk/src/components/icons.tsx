import {
  BookOpen,
  Brush,
  Cloud,
  Compass,
  Crown,
  Drum,
  Fan,
  Flame,
  Flower2,
  Footprints,
  LampCeiling,
  Landmark,
  Map,
  Mountain,
  ScrollText,
  Shirt,
  Sprout,
  Star,
  Sun,
  Umbrella,
  Wind,
  type LucideIcon,
} from 'lucide-react'
import type { AchievementIcon } from '../domain/achievements'
import type { Hero } from '../domain/heroes'
import type { ItemIcon } from '../domain/shop'

export const TRAIT_ICONS: Record<Hero['traitIcon'], LucideIcon> = {
  compass: Compass,
  bamboo: Sprout,
  mountain: Mountain,
  cloud: Cloud,
  lotus: Flower2,
  sun: Sun,
}

export const ITEM_ICONS: Record<ItemIcon, LucideIcon> = {
  compass: Compass,
  lantern: LampCeiling,
  fan: Fan,
  hat: Umbrella,
  brush: Brush,
  kite: Wind,
  drum: Drum,
  scroll: ScrollText,
}

export const ACHIEVEMENT_ICONS: Record<AchievementIcon, LucideIcon> = {
  footprints: Footprints,
  landmark: Landmark,
  book: BookOpen,
  shirt: Shirt,
  map: Map,
  star: Star,
  flame: Flame,
  crown: Crown,
}

export function CoinIcon({ size = 22 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="11" fill="#f5b83d" stroke="#c98a14" strokeWidth="1.5" />
      <circle cx="12" cy="12" r="7.5" fill="none" stroke="#fff2c4" strokeWidth="1.2" />
      <rect x="9.5" y="9.5" width="5" height="5" fill="none" stroke="#b27610" strokeWidth="1.6" />
    </svg>
  )
}

export function GemIcon({ size = 22 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 1.5 20.5 7v10L12 22.5 3.5 17V7z" fill="#27b27a" stroke="#137a50" strokeWidth="1.4" />
      <path d="M12 1.5V22.5M3.5 7 12 12l8.5-5" fill="none" stroke="#9ff0cb" strokeWidth="1" opacity=".8" />
    </svg>
  )
}
