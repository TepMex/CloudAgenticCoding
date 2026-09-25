import { CITIES, getCity, type City, type Word } from './cities'

export type TaskKind = 'meaning' | 'pinyin' | 'reverse' | 'listen'

export interface Task {
  id: string
  cityId: string
  kind: TaskKind
  title: string
  subtitle: string
}

export interface Question {
  prompt: string
  /** Shown under the prompt, e.g. pinyin for `meaning` tasks. */
  hint?: string
  /** Text spoken aloud for `listen` tasks. */
  speak?: string
  options: string[]
  answer: string
  word: Word
}

export const TASK_KINDS: Record<TaskKind, { title: string; subtitle: string; skill: string }> = {
  meaning: { title: 'Что это значит?', subtitle: 'Выбери перевод иероглифа', skill: 'Словарный запас' },
  pinyin: { title: 'Как это читается?', subtitle: 'Подбери пиньинь к иероглифу', skill: 'Произношение' },
  reverse: { title: 'Найди иероглиф', subtitle: 'Выбери иероглиф по переводу', skill: 'Иероглифика' },
  listen: { title: 'Слушай внимательно', subtitle: 'Выбери слово, которое услышишь', skill: 'Аудирование' },
}

export const QUESTIONS_PER_TASK = 4
/** Correct answers required for the task to count as done. */
export const PASS_SCORE = 3

const KIND_ORDER: TaskKind[] = ['meaning', 'pinyin', 'reverse', 'listen']

export function tasksForCity(city: City): Task[] {
  return KIND_ORDER.map((kind) => ({
    id: `${city.id}:${kind}`,
    cityId: city.id,
    kind,
    title: TASK_KINDS[kind].title,
    subtitle: TASK_KINDS[kind].subtitle,
  }))
}

export const ALL_TASKS: Task[] = CITIES.flatMap(tasksForCity)

export function getTask(id: string): Task | undefined {
  return ALL_TASKS.find((t) => t.id === id)
}

export function shuffle<T>(items: T[], rng: () => number = Math.random): T[] {
  const a = [...items]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function field(kind: TaskKind, w: Word): { prompt: string; hint?: string; speak?: string; value: string } {
  switch (kind) {
    case 'meaning':
      return { prompt: w.hanzi, hint: w.pinyin, value: w.ru }
    case 'pinyin':
      return { prompt: w.hanzi, value: w.pinyin }
    case 'reverse':
      return { prompt: w.ru, value: w.hanzi }
    case 'listen':
      return { prompt: 'Нажми, чтобы послушать', hint: w.pinyin, speak: w.hanzi, value: w.hanzi }
  }
}

export function buildQuiz(task: Task, rng: () => number = Math.random): Question[] {
  const city = getCity(task.cityId)
  if (!city) return []
  const picked = shuffle(city.words, rng).slice(0, QUESTIONS_PER_TASK)
  return picked.map((word) => {
    const { prompt, hint, speak, value } = field(task.kind, word)
    const distractors = shuffle(
      city.words.filter((w) => w !== word).map((w) => field(task.kind, w).value),
      rng,
    )
      .filter((v) => v !== value)
      .slice(0, 3)
    return { prompt, hint, speak, word, answer: value, options: shuffle([value, ...distractors], rng) }
  })
}
