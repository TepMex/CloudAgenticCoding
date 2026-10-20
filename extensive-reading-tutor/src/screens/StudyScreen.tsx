import { Header, statusLabel } from "../components.tsx";
import { useApp } from "../state.tsx";

export function StudyScreen() {
  const { state, openLesson, openWord, go } = useApp();
  const current = state.books.find(book => book.status === "reading") ?? state.books[0];
  const lesson = current ? state.lessons.find(item => item.bookId === current.id) : undefined;
  const phase = current ? (state.attempts[current.id]?.phase ?? 1) : 1;
  const due = state.glosses.filter(gloss => gloss.status === "repeat").slice(0, 8);

  return (
    <div className="screen" data-screen="study">
      <Header large={<h1 className="large-title">Учёба</h1>} />
      <div className="content">
        {current && lesson && (
          <article className="q-card">
            <div className="kicker">Сейчас</div>
            <h2 className="book-title" style={{ fontSize: 22 }}>
              {current.title}
            </h2>
            <p className="help" style={{ marginBottom: 0 }}>
              {lesson.chapterTitle} · {lesson.fragmentTitle} · фаза {phase} из 4
            </p>
            <p>{lesson.intro}</p>
            <div className="legend" aria-hidden>
              {["Текст", "Вопросы", "Ответы", "Разбор"].map((label, index) => (
                <span key={label}>
                  <i className={index + 1 < phase ? "done" : index + 1 === phase ? "current" : ""} />
                  {label}
                </span>
              ))}
            </div>
            <button className="btn-primary" type="button" style={{ marginTop: 12 }} onClick={() => openLesson(current.id)}>
              Продолжить занятие
            </button>
          </article>
        )}
        <div className="section-label">Нужно повторить</div>
        <div className="chips">
          {due.map(gloss => (
            <button key={gloss.id} type="button" className="chip" onClick={() => openWord(gloss.id)}>
              {gloss.hanzi}
            </button>
          ))}
          {due.length === 0 && <p className="muted">Нет слов со статусом «{statusLabel("repeat")}».</p>}
        </div>
        <div className="section-label">Книги в работе</div>
        {state.books
          .filter(book => book.status === "reading")
          .map(book => (
            <button key={book.id} type="button" className="chapter-row" onClick={() => go({ name: "book", bookId: book.id, tab: "progress" })}>
              <i className="dot current" />
              <span>
                <strong className="book-title" style={{ fontSize: 16 }}>
                  {book.title}
                </strong>
                <div className="muted">
                  {book.place} · {book.progress}%
                </div>
              </span>
            </button>
          ))}
      </div>
    </div>
  );
}
