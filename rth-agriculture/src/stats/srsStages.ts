import type { CardState } from '../learning'

export type SrsStageId =
  | 'new' | 'step-1' | 'step-2' | 'step-3' | 'novice'
  | 'apprentice' | 'guru' | 'master' | 'enlightened' | 'rooted'

export type SrsStage = {
  id: SrsStageId
  index: number
  label: string
  shortLabel: string
  color: string
}

export const SRS_STAGES: readonly SrsStage[] = [
  { id: 'new', index: 0, label: 'Новый', shortLabel: 'Новый', color: '#68706d' },
  { id: 'step-1', index: 1, label: 'Шаг 1', shortLabel: '1', color: '#4eb9e9' },
  { id: 'step-2', index: 2, label: 'Шаг 2', shortLabel: '2', color: '#35d2db' },
  { id: 'step-3', index: 3, label: 'Шаг 3', shortLabel: '3', color: '#41c9ad' },
  { id: 'novice', index: 4, label: 'Новичок', shortLabel: 'Новичок', color: '#5aaf79' },
  { id: 'apprentice', index: 5, label: 'Ученик', shortLabel: 'Ученик', color: '#467dcc' },
  { id: 'guru', index: 6, label: 'Гуру', shortLabel: 'Гуру', color: '#855dc2' },
  { id: 'master', index: 7, label: 'Мастер', shortLabel: 'Мастер', color: '#c456a2' },
  { id: 'enlightened', index: 8, label: 'Просветлённый', shortLabel: 'Просветл.', color: '#df626d' },
  { id: 'rooted', index: 9, label: 'Укоренившийся', shortLabel: 'Корни', color: '#caa44b' },
] as const

export function scheduledIntervalDays(card: CardState): number {
  if (card.scheduled_days > 0) return card.scheduled_days
  if (!card.last_review) return 0
  const due = new Date(card.due).getTime()
  const reviewed = new Date(card.last_review).getTime()
  return Number.isFinite(due - reviewed) ? Math.max(0, (due - reviewed) / 86_400_000) : 0
}

/** A display-only projection; FSRS scheduling and due dates remain untouched. */
export function getSrsStage(card?: CardState): SrsStage {
  if (!card) return SRS_STAGES[0]
  const interval = scheduledIntervalDays(card)
  if (interval < 1) return SRS_STAGES[1]
  if (interval < 2) return SRS_STAGES[2]
  if (interval < 4) return SRS_STAGES[3]
  if (interval < 7) return SRS_STAGES[4]
  if (interval < 14) return SRS_STAGES[5]
  if (interval < 30) return SRS_STAGES[6]
  if (interval < 90) return SRS_STAGES[7]
  if (interval < 180) return SRS_STAGES[8]
  return SRS_STAGES[9]
}
