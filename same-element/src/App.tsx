import { useState } from 'react'
import { categoryById } from './data/catalog'
import { HomeScreen } from './screens/HomeScreen'
import { PickScreen } from './screens/PickScreen'
import { SummaryScreen } from './screens/SummaryScreen'
import { TrainScreen, type RoundResult } from './screens/TrainScreen'
import { shuffle } from './shuffle'

type Route =
  | { name: 'home' }
  | { name: 'pick'; categoryId: string }
  | { name: 'train'; categoryId: string; hanzi: string[] }
  | { name: 'summary'; categoryId: string; hanzi: string[]; results: RoundResult[] }

export default function App() {
  const [route, setRoute] = useState<Route>({ name: 'home' })

  if (route.name === 'home') {
    return <HomeScreen onOpen={(categoryId) => setRoute({ name: 'pick', categoryId })} />
  }

  const category = categoryById(route.categoryId)
  if (!category) {
    return <HomeScreen onOpen={(categoryId) => setRoute({ name: 'pick', categoryId })} />
  }

  if (route.name === 'pick') {
    return (
      <PickScreen
        category={category}
        onBack={() => setRoute({ name: 'home' })}
        onStart={(hanzi) => setRoute({ name: 'train', categoryId: category.id, hanzi: shuffle(hanzi) })}
      />
    )
  }

  if (route.name === 'train') {
    const queue = route.hanzi
      .map((hanzi) => category.characters.find((character) => character.hanzi === hanzi))
      .filter((character) => character !== undefined)
    return (
      <TrainScreen
        categoryId={category.id}
        queue={queue}
        onExit={() => setRoute({ name: 'pick', categoryId: category.id })}
        onFinish={(results) => setRoute({
          name: 'summary',
          categoryId: category.id,
          hanzi: route.hanzi,
          results,
        })}
      />
    )
  }

  return (
    <SummaryScreen
      results={route.results}
      onBack={() => setRoute({ name: 'pick', categoryId: category.id })}
      onRetryAll={() => setRoute({ name: 'train', categoryId: category.id, hanzi: shuffle(route.hanzi) })}
      onRetryMisses={() => setRoute({
        name: 'train',
        categoryId: category.id,
        hanzi: shuffle(route.results.filter((result) => result.mistakes > 0 || result.revealed).map((result) => result.hanzi)),
      })}
    />
  )
}
