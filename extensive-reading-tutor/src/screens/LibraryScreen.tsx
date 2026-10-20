import { Plus, X } from "lucide-react";
import { useState } from "react";
import { Header, Cover, Progress, Segmented, Sheet } from "../components.tsx";
import { plural } from "../lib/text.ts";
import { useApp } from "../state.tsx";

type Filter = "reading" | "done" | "all";

export function LibraryScreen() {
  const { state, go, dismissBanner } = useApp();
  const [filter, setFilter] = useState<Filter>("reading");
  const [adding, setAdding] = useState(false);
  const reading = state.books.filter(book => book.status === "reading").length;
  const done = state.books.filter(book => book.status === "done").length;
  const books = state.books.filter(book => (filter === "all" ? true : book.status === filter));

  return (
    <div className="screen" data-screen="library">
      <Header
        large={
          <div className="title-row">
            <h1 className="large-title">Мои книги</h1>
            <button className="plus" type="button" aria-label="Добавить книгу" onClick={() => setAdding(true)}>
              <Plus />
            </button>
          </div>
        }
      />
      <div className="content">
        <Segmented
          value={filter}
          onChange={setFilter}
          options={[
            { id: "reading", label: `Читаю ${reading}` },
            { id: "done", label: `Завершено ${done}` },
            { id: "all", label: `Все ${state.books.length}` },
          ]}
        />
        {!state.bannerDismissed && (
          <div className="banner">
            <p>Образец занятия уже в «天官赐福». Свой фрагмент — кнопка «+». Модель подключается в профиле.</p>
            <button type="button" aria-label="Скрыть" onClick={dismissBanner}>
              <X size={16} />
            </button>
          </div>
        )}
        {books.map(book => (
          <button key={book.id} type="button" className="book-row" onClick={() => go({ name: "book", bookId: book.id, tab: "progress" })}>
            <Cover book={book} size="sm" />
            <div className="book-copy">
              <span className="book-top">
                <span className="book-title">{book.title}</span>
                <span className="book-pct">{book.progress}%</span>
              </span>
              <span className="book-author">{book.author}</span>
              <Progress value={book.progress} />
              <span className="book-session">
                Последнее занятие
                <b>{book.lastSession}</b>
              </span>
            </div>
          </button>
        ))}
        {books.length === 0 && <p className="muted">В этой полке пока пусто.</p>}
      </div>
      {adding && <AddBookSheet onClose={() => setAdding(false)} />}
    </div>
  );
}

function AddBookSheet({ onClose }: { onClose: () => void }) {
  const { addBook } = useApp();
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [text, setText] = useState("");
  const [error, setError] = useState("");

  return (
    <Sheet title="Новая книга" onClose={onClose}>
      <p className="help">
        Вставь фрагмент на китайском. Если в настройках указан OpenAI-compatible endpoint, занятие соберёт модель. Иначе откроется локальный шаблон вопросов.
      </p>
      <form
        onSubmit={event => {
          event.preventDefault();
          if (title.trim().length < 1) {
            setError("Нужно название.");
            return;
          }
          if (text.trim().length < 12) {
            setError("Фрагмент слишком короткий.");
            return;
          }
          void addBook({ title, author, text: text.trim() });
          onClose();
        }}
      >
        <label className="field">
          <span>Название</span>
          <input name="title" value={title} onChange={event => setTitle(event.target.value)} placeholder="例如：边城" />
        </label>
        <label className="field">
          <span>Автор</span>
          <input name="author" value={author} onChange={event => setAuthor(event.target.value)} placeholder="Необязательно" />
        </label>
        <label className="field">
          <span>Текст</span>
          <textarea name="passage" value={text} onChange={event => setText(event.target.value)} placeholder="Вставь китайский фрагмент" maxLength={1800} />
        </label>
        <label className="field">
          <span>Или файл .txt</span>
          <input
            type="file"
            accept=".txt,text/plain"
            onChange={event => {
              const file = event.target.files?.[0];
              if (!file) return;
              void file.text().then(contents => {
                setText(contents.trim().slice(0, 1800));
                if (!title) setTitle(file.name.replace(/\.txt$/i, ""));
              });
            }}
          />
        </label>
        {error ? <p className="status-bad">{error}</p> : null}
        <p className="muted">{text.trim().length} {plural(text.trim().length, "знак", "знака", "знаков")}</p>
        <div className="btn-row" style={{ marginTop: 12 }}>
          <button className="btn-primary" type="submit">
            Добавить и начать
          </button>
        </div>
      </form>
    </Sheet>
  );
}
