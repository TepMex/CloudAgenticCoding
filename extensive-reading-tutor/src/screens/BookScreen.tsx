import { Bookmark, MessageSquare, MoreHorizontal } from "lucide-react";
import { useState } from "react";
import { CheckDot, Cover, Header, Progress, Segmented } from "../components.tsx";
import { plural, readingMap } from "../lib/text.ts";
import { useApp } from "../state.tsx";

export function BookScreen({ bookId }: { bookId: string }) {
  const { state, go, openLesson, resetAttempt, addNote, toastMessage } = useApp();
  const book = state.books.find(item => item.id === bookId);
  const route = state.route.name === "book" ? state.route : null;
  const [menu, setMenu] = useState(false);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  if (!book || !route || route.name !== "book") {
    return (
      <div className="screen">
        <Header title="Книга" onBack={() => go({ name: "library" })} />
      </div>
    );
  }
  const tab = route.tab;
  const notes = state.notes.filter(note => note.bookId === book.id && note.kind === "note");
  const marks = state.notes.filter(note => note.bookId === book.id && note.kind === "bookmark");
  const cells = readingMap(book.progress);

  return (
    <div className="screen" data-screen="book">
      <Header
        onBack={() => go({ name: "library" })}
        right={
          <button className="icon-btn" type="button" aria-label="Ещё" onClick={() => setMenu(value => !value)}>
            <MoreHorizontal />
          </button>
        }
      />
      {menu && (
        <div className="menu">
          <button
            type="button"
            onClick={() => {
              setMenu(false);
              resetAttempt(book.id);
            }}
          >
            Пройти фрагмент снова
          </button>
          <button
            type="button"
            onClick={() => {
              setMenu(false);
              go({ name: "book", bookId: book.id, tab: "notes" });
            }}
          >
            К заметкам
          </button>
        </div>
      )}
      <div className="content">
        <div className="book-hero">
          <Cover book={book} size="lg" />
          <div>
            <div className="book-top">
              <h1 className="book-title">{book.title}</h1>
              <span className="book-pct">{book.progress}%</span>
            </div>
            <div className="book-author">{book.author}</div>
            <Progress value={book.progress} />
          </div>
        </div>
        <Segmented
          value={tab}
          onChange={next => go({ name: "book", bookId: book.id, tab: next })}
          options={[
            { id: "progress", label: "Прогресс" },
            { id: "chapters", label: "Главы" },
            { id: "notes", label: "Заметки" },
          ]}
        />
        {tab === "progress" && (
          <>
            <div className="place-card">
              <div>
                <div className="kicker">Текущее место</div>
                <strong>{book.place}</strong>
              </div>
              <button className="btn-small" type="button" onClick={() => openLesson(book.id)}>
                {book.status === "done" ? "Повторить" : "Продолжить"}
              </button>
            </div>
            <div className="stat3">
              <div>
                <b>{book.words}</b>
                <span>новых слов</span>
              </div>
              <div>
                <b>{book.chars}</b>
                <span>знаков</span>
              </div>
              <div>
                <b>{book.observations}</b>
                <span>{plural(book.observations, "наблюдение", "наблюдения", "наблюдений")}</span>
              </div>
            </div>
            <div className="section-label">Карта чтения</div>
            <div className="map" aria-hidden>
              {cells.map((cell, index) => (
                <i key={index} className={cell === "unread" ? "" : cell} />
              ))}
            </div>
            <div className="legend">
              <span><i className="done" /> Завершено</span>
              <span><i className="current" /> Текущее</span>
              <span><i /> Не пройдено</span>
            </div>
            <div className="section-label">Последние занятия</div>
            {book.recent.map(row => (
              <div className="session" key={row.title}>
                <CheckDot on={row.done} />
                <div>
                  <strong>{row.title}</strong>
                  <div className="muted">{row.when}</div>
                </div>
              </div>
            ))}
          </>
        )}
        {tab === "chapters" &&
          book.chapters.map(chapter => (
            <button
              key={chapter.id}
              type="button"
              className="chapter-row"
              onClick={() => {
                if (chapter.status === "unread") toastMessage("Этот фрагмент ещё не открыт.");
                else openLesson(book.id);
              }}
            >
              <i className={`dot ${chapter.status === "unread" ? "" : chapter.status}`} />
              <span>
                <strong>{chapter.title}</strong>
                <div className="muted">{chapter.detail}</div>
              </span>
            </button>
          ))}
        {tab === "notes" && (
          <NotesPane
            notes={notes.length}
            marks={marks.length}
            items={state.notes.filter(note => note.bookId === book.id)}
            title={title}
            body={body}
            setTitle={setTitle}
            setBody={setBody}
            onSave={() => {
              if (!title.trim()) return;
              addNote({ bookId: book.id, kind: "note", title: title.trim(), body: body.trim(), meta: book.place });
              setTitle("");
              setBody("");
            }}
          />
        )}
      </div>
    </div>
  );
}

function NotesPane({
  notes,
  marks,
  items,
  title,
  body,
  setTitle,
  setBody,
  onSave,
}: {
  notes: number;
  marks: number;
  items: { id: string; kind: "note" | "bookmark"; title: string; body: string; meta: string }[];
  title: string;
  body: string;
  setTitle: (value: string) => void;
  setBody: (value: string) => void;
  onSave: () => void;
}) {
  const [tab, setTab] = useState<"note" | "bookmark">("note");
  const visible = items.filter(item => item.kind === tab);
  return (
    <>
      <Segmented
        value={tab}
        onChange={setTab}
        options={[
          { id: "note", label: `Мои заметки (${notes})` },
          { id: "bookmark", label: `Закладки (${marks})` },
        ]}
      />
      {tab === "note" && (
        <form
          onSubmit={event => {
            event.preventDefault();
            onSave();
          }}
        >
          <input className="search" name="noteTitle" value={title} onChange={event => setTitle(event.target.value)} placeholder="Заголовок" />
          <textarea className="answer-box" name="noteBody" value={body} onChange={event => setBody(event.target.value)} placeholder="Заметка к фрагменту" />
          <button className="btn-ghost" type="submit">
            Сохранить заметку
          </button>
        </form>
      )}
      {visible.map(item => (
        <div className="note-row" key={item.id}>
          <span className={item.kind === "bookmark" ? "note-ico mark" : "note-ico"}>{item.kind === "bookmark" ? <Bookmark size={18} /> : <MessageSquare size={18} />}</span>
          <span>
            <h3>{item.title}</h3>
            <p>{item.body}</p>
            <p className="muted">{item.meta}</p>
          </span>
        </div>
      ))}
      {visible.length === 0 && <p className="muted">Пока пусто.</p>}
    </>
  );
}
