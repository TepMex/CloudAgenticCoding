import type { FieldDefinition } from './data/model'
import type { CardState } from './learning'
import { isCardDue } from './learning'

type InfectionField = Pick<FieldDefinition, 'characters'>

/**
 * Infection reflects unfinished memory work only. Field access deliberately
 * does not participate in this calculation: a locked field is still full of
 * new characters, so it remains visibly overgrown until it is opened and
 * cleared.
 */
export function fieldInfection(
  field: InfectionField,
  cards: Readonly<Record<string, CardState>>,
  now = new Date(),
): number {
  const totalWeight = field.characters.reduce((sum, character) => sum + character.strokeCount, 0)
  const weedWeight = field.characters.reduce(
    (sum, character) => sum + (isCardDue(cards[character.id], now) ? character.strokeCount : 0),
    0,
  )

  return totalWeight ? weedWeight / totalWeight : 0
}
