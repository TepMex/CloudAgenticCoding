import type { PlotDefinition } from './data/model'
import type { CardState } from './learning'
import { isCardDue } from './learning'

type InfectionPlot = Pick<PlotDefinition, 'characters'>

/**
 * Infection is a live projection of unfinished memory work.  Progression
 * access deliberately has no role here: a locked plot still contains new
 * characters and therefore remains visually overgrown.
 */
export function plotInfection(
  plot: InfectionPlot,
  cards: Readonly<Record<string, CardState>>,
  now = new Date(),
): number {
  const totalWeight = plot.characters.reduce((sum, character) => sum + character.strokeCount, 0)
  const weedWeight = plot.characters.reduce(
    (sum, character) => sum + (isCardDue(cards[character.id], now) ? character.strokeCount : 0),
    0,
  )
  return totalWeight ? weedWeight / totalWeight : 0
}
