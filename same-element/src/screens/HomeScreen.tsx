import { categories } from '../data/catalog'

export function HomeScreen({ onOpen }: { onOpen: (categoryId: string) => void }) {
  return (
    <main className="screen home-screen">
      <header className="masthead">
        <p className="eyebrow">тренировка различения</p>
        <h1>Общий элемент</h1>
        <p className="lede">
          Выберите знаки с одним и тем же элементом. На листе он уже написан — допишите то, чем они отличаются.
        </p>
      </header>
      <ul className="category-grid">
        {categories.map((category) => (
          <li key={category.id}>
            <button className="category-card" onClick={() => onOpen(category.id)}>
              <span className="element-glyph" lang="zh">{category.element}</span>
              <span className="category-copy">
                <strong>{category.name_ru}</strong>
                <small>{category.count} знаков</small>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </main>
  )
}
