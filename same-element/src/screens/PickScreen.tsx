import { useMemo, useState } from 'react'
import { foldPinyin, type ElementCategory } from '../data/catalog'
import { loadMemory, loadPick, savePick, type CharacterMemory } from '../storage'
import { shuffle } from '../shuffle'

export function PickScreen({
  category,
  onBack,
  onStart,
}: {
  category: ElementCategory
  onBack: () => void
  onStart: (hanzi: string[]) => void
}) {
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<string[]>(() => {
    const saved = loadPick(category.id)
    const known = new Set(category.characters.map((character) => character.hanzi))
    return saved.filter((hanzi) => known.has(hanzi))
  })
  const [memory] = useState<Record<string, CharacterMemory>>(() => loadMemory())

  const visible = useMemo(() => {
    const needle = foldPinyin(query.trim())
    if (!needle) return category.characters
    return category.characters.filter((character) => {
      const haystack = `${character.hanzi} ${foldPinyin(character.pinyin)} ${character.ru.toLowerCase()}`
      return haystack.includes(needle)
    })
  }, [category, query])

  const selectedSet = useMemo(() => new Set(selected), [selected])

  const toggle = (hanzi: string) => {
    const next = selectedSet.has(hanzi)
      ? selected.filter((item) => item !== hanzi)
      : [...selected, hanzi]
    setSelected(next)
    savePick(category.id, next)
  }

  const replaceSelection = (hanzi: string[]) => {
    setSelected(hanzi)
    savePick(category.id, hanzi)
  }

  const start = () => {
    if (selected.length < 2) return
    const order = new Map(category.characters.map((character, index) => [character.hanzi, index]))
    const sorted = selected.slice().sort((a, b) => (order.get(a) ?? 0) - (order.get(b) ?? 0))
    onStart(sorted)
  }

  return (
    <main className="screen pick-screen">
      <header className="topbar">
        <button className="text-button" onClick={onBack}>Назад</button>
        <p className="topbar-title" lang="zh">{category.element}</p>
        <span className="topbar-meta">{selected.length}</span>
      </header>
      <div className="pick-intro">
        <h1>{category.name_ru}</h1>
        <p>{category.description}</p>
      </div>
      <div className="pick-tools">
        <input
          className="search"
          value={query}
          placeholder="иероглиф, пиньинь или значение"
          onChange={(event) => setQuery(event.target.value)}
          aria-label="Фильтр списка"
        />
        <div className="tool-row">
          <button
            className="chip"
            onClick={() => replaceSelection([...new Set([...selected, ...visible.map((character) => character.hanzi)])])}
          >
            Все на экране
          </button>
          <button className="chip" onClick={() => replaceSelection([])}>Снять</button>
          <button
            className="chip"
            onClick={() => replaceSelection(shuffle(category.characters).slice(0, 8).map((character) => character.hanzi))}
          >
            Случайные 8
          </button>
        </div>
      </div>
      <ul className="hanzi-list">
        {visible.map((character) => {
          const checked = selectedSet.has(character.hanzi)
          const stats = memory[character.hanzi]
          return (
            <li key={character.hanzi}>
              <label className={checked ? 'hanzi-row is-checked' : 'hanzi-row'}>
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => toggle(character.hanzi)}
                />
                <span className="hanzi-glyph" lang="zh">{character.hanzi}</span>
                <span className="hanzi-gloss">
                  <strong>{character.pinyin}</strong>
                  <small>{character.ru}</small>
                </span>
                {stats ? (
                  <span className="memory-mark" title="Чисто / с ошибкой">
                    {stats.clean}/{stats.missed}
                  </span>
                ) : null}
              </label>
            </li>
          )
        })}
      </ul>
      <footer className="dock">
        <button className="primary" disabled={selected.length < 2} onClick={start}>
          {selected.length < 2 ? 'Выберите хотя бы два знака' : `Тренировка · ${selected.length}`}
        </button>
      </footer>
    </main>
  )
}
