import { Info } from "lucide-react";
import { useState } from "react";
import { CheckDot, Header, kindLabel, statusLabel } from "../components.tsx";
import { tally } from "../lib/grade.ts";
import { isHan, passageOf, plural } from "../lib/text.ts";
import { useApp } from "../state.tsx";
import type { Difficulty, Lesson, Question, QuestionCategory } from "../lib/types.ts";

type TextTab = "text" | "notes";
type ReviewTab = "questions" | "moments" | "words";

export function LessonScreen({ bookId }: { bookId: string }) {
  const app = useApp();
  const lesson = app.state.lessons.find(item => item.bookId === bookId);
  const attempt = app.state.attempts[bookId];
  const phase = app.state.route.name === "lesson" ? app.state.route.phase : attempt?.phase ?? 1;
  const [textTab, setTextTab] = useState<TextTab>("text");
  const [filter, setFilter] = useState<"all" | QuestionCategory>("all");
  const [reviewTab, setReviewTab] = useState<ReviewTab>("questions");
  const [noteTitle, setNoteTitle] = useState("");
  const [noteBody, setNoteBody] = useState("");

  if (!lesson || !attempt) {
    return (
      <div className="screen">
        <Header title="Занятие" onBack={() => app.go({ name: "book", bookId, tab: "progress" })} />
        <div className="content">
          <p className="muted">Занятие ещё не открыто.</p>
          <button className="btn-primary" type="button" onClick={() => app.openLesson(bookId)}>
            Открыть
          </button>
        </div>
      </div>
    );
  }

  const back = () => {
    if (phase === 1) app.go({ name: "book", bookId, tab: "progress" });
    else app.setPhase(bookId, (phase - 1) as 1 | 2 | 3);
  };
  const passage = passageOf(lesson.tokens);
  const glosses = app.state.glosses.filter(gloss => lesson.tokens.some(token => token.glossId === gloss.id));
  const activeGlossId = app.word?.id;

  return (
    <div className="screen" data-screen="lesson">
      <Header
        title={`${lesson.chapterTitle} · ${lesson.fragmentTitle}`}
        onBack={back}
        right={<span className="phase-no">{phase}/4</span>}
      />
      <div className="content">
        {phase === 1 && (
          <PhaseText
            lesson={lesson}
            passage={passage}
            tab={textTab}
            onTab={setTextTab}
            notes={app.state.notes.filter(note => note.bookId === bookId)}
            noteTitle={noteTitle}
            noteBody={noteBody}
            setNoteTitle={setNoteTitle}
            setNoteBody={setNoteBody}
            onSaveNote={() => {
              if (!noteTitle.trim()) return;
              app.addNote({ bookId, kind: "note", title: noteTitle.trim(), body: noteBody.trim(), meta: lesson.chapterTitle });
              setNoteTitle("");
              setNoteBody("");
            }}
            onBookmark={() =>
              app.addNote({
                bookId,
                kind: "bookmark",
                title: `${lesson.chapterTitle} · ${lesson.fragmentTitle}`,
                body: passage.slice(0, 80),
                meta: "Сейчас",
              })
            }
            activeGlossId={activeGlossId}
            onToken={(hanzi, glossId) => {
              if (glossId) app.openWord(glossId);
              else if (isHan(hanzi)) app.openLookup(hanzi, passage);
            }}
          />
        )}
        {phase === 2 && (
          <PhaseQuestions
            lesson={lesson}
            answers={attempt.answers}
            filter={filter}
            onFilter={setFilter}
            setAnswer={(questionId, value) => app.setAnswer(bookId, questionId, value)}
          />
        )}
        {phase === 3 && (
          <PhaseCheck
            lesson={lesson}
            answers={attempt.answers}
            difficulty={attempt.difficulty}
            comment={attempt.comment}
            onEdit={() => app.setPhase(bookId, 2)}
            onDifficulty={value => app.setDifficulty(bookId, value)}
            onComment={value => app.setComment(bookId, value)}
          />
        )}
        {phase === 4 && attempt.review && (
          <PhaseReview
            lesson={lesson}
            review={attempt.review}
            tab={reviewTab}
            onTab={setReviewTab}
            glosses={glosses}
            onWord={app.openWord}
          />
        )}
        {phase === 4 && !attempt.review && <p className="muted">Разбор появится после отправки ответов.</p>}
      </div>
      <div className="dock">
        {phase === 1 && (
          <button className="btn-primary" type="button" onClick={() => app.setPhase(bookId, 2)}>
            К вопросам
          </button>
        )}
        {phase === 2 && (
          <button className="btn-primary" type="button" onClick={() => app.setPhase(bookId, 3)}>
            К ответам
          </button>
        )}
        {phase === 3 && (
          <button className="btn-primary" type="button" disabled={app.busy !== null} onClick={() => void app.submitReview(bookId)}>
            Отправить ответы
          </button>
        )}
        {phase === 4 && (
          <div className="stack-gap">
            <button className="btn-primary" type="button" onClick={() => app.go({ name: "book", bookId, tab: "progress" })}>
              К книге
            </button>
            <button className="btn-ghost" type="button" onClick={() => app.resetAttempt(bookId)}>
              Пройти снова
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function PhaseText({
  lesson,
  passage,
  tab,
  onTab,
  notes,
  noteTitle,
  noteBody,
  setNoteTitle,
  setNoteBody,
  onSaveNote,
  onBookmark,
  onToken,
  activeGlossId,
}: {
  lesson: Lesson;
  passage: string;
  tab: TextTab;
  onTab: (tab: TextTab) => void;
  notes: { id: string; title: string; body: string; meta: string }[];
  noteTitle: string;
  noteBody: string;
  setNoteTitle: (value: string) => void;
  setNoteBody: (value: string) => void;
  onSaveNote: () => void;
  onBookmark: () => void;
  onToken: (hanzi: string, glossId?: string) => void;
  activeGlossId?: string;
}) {
  return (
    <>
      <div className="today">
        <Info size={18} />
        <div>
          <h2>Сегодня</h2>
          <p>{lesson.intro}</p>
        </div>
      </div>
      {lesson.hints.length > 0 && (
        <details className="hint">
          <summary>Небольшие подсказки перед чтением</summary>
          {lesson.hints.map(hint => (
            <p key={hint}>{hint}</p>
          ))}
        </details>
      )}
      <div className="segmented" role="tablist">
        <button type="button" className={tab === "text" ? "on" : ""} onClick={() => onTab("text")}>
          Текст
        </button>
        <button type="button" className={tab === "notes" ? "on" : ""} onClick={() => onTab("notes")}>
          Заметки
        </button>
      </div>
      {tab === "text" ? (
        <>
          <p className="passage" lang="zh">
            {lesson.tokens.map((token, index) =>
              isHan(token.t) ? (
                <button
                  key={index}
                  type="button"
                  className={token.glossId && token.glossId === activeGlossId ? "token on" : token.glossId ? "token gloss" : "token"}
                  onClick={() => onToken(token.t, token.glossId)}
                >
                  {token.t}
                </button>
              ) : (
                <span key={index}>{token.t}</span>
              ),
            )}
          </p>
          <button className="btn-ghost" type="button" onClick={onBookmark}>
            В закладки
          </button>
        </>
      ) : (
        <>
          <form
            onSubmit={event => {
              event.preventDefault();
              onSaveNote();
            }}
          >
            <input className="search" name="noteTitle" value={noteTitle} onChange={event => setNoteTitle(event.target.value)} placeholder="Заголовок" />
            <textarea className="answer-box" name="noteBody" value={noteBody} onChange={event => setNoteBody(event.target.value)} placeholder="Что хочешь запомнить из фрагмента" />
            <button className="btn-ghost" type="submit">
              Сохранить
            </button>
          </form>
          {notes.map(note => (
            <div key={note.id} className="note-row">
              <span className="note-ico">·</span>
              <span>
                <h3>{note.title}</h3>
                <p>{note.body}</p>
                <p className="muted">{note.meta}</p>
              </span>
            </div>
          ))}
        </>
      )}
    </>
  );
}

function PhaseQuestions({
  lesson,
  answers,
  filter,
  onFilter,
  setAnswer,
}: {
  lesson: Lesson;
  answers: Record<string, string>;
  filter: "all" | QuestionCategory;
  onFilter: (filter: "all" | QuestionCategory) => void;
  setAnswer: (questionId: string, value: string) => void;
}) {
  const counts = {
    all: lesson.questions.length,
    sense: lesson.questions.filter(question => question.category === "sense").length,
    words: lesson.questions.filter(question => question.category === "words").length,
    grammar: lesson.questions.filter(question => question.category === "grammar").length,
  };
  const visible = lesson.questions.filter(question => filter === "all" || question.category === filter);
  return (
    <>
      <h2 className="large-title" style={{ fontSize: 28 }}>
        Вопросы
      </h2>
      <p className="help">Попробуй ответить самостоятельно. Не перечитывай, если что-то не уверенно — разберём дальше.</p>
      <div className="chips">
        {(
          [
            ["all", `Все (${counts.all})`],
            ["sense", `Смысл (${counts.sense})`],
            ["words", `Слова (${counts.words})`],
            ["grammar", `Грамматика (${counts.grammar})`],
          ] as const
        ).map(([id, label]) => (
          <button key={id} type="button" className={filter === id ? "chip on" : "chip"} onClick={() => onFilter(id)}>
            {label}
          </button>
        ))}
      </div>
      {visible.map(question => (
        <QuestionCard key={question.id} question={question} index={lesson.questions.indexOf(question) + 1} value={answers[question.id] ?? ""} onChange={value => setAnswer(question.id, value)} />
      ))}
    </>
  );
}

function QuestionCard({ question, index, value, onChange }: { question: Question; index: number; value: string; onChange: (value: string) => void }) {
  return (
    <article className="q-card" id={`q-${question.id}`}>
      <h3>
        <span className="q-no">{index}.</span>
        {question.title}
      </h3>
      <p>{question.prompt}</p>
      {question.kind === "mcq" ? (
        question.options?.map(option => (
          <button key={option.key} type="button" className={value === option.key ? "opt on" : "opt"} onClick={() => onChange(option.key)}>
            <span className="letter">{option.key}</span>
            <span>{option.text}</span>
          </button>
        ))
      ) : (
        <textarea className="answer-box" name={question.id} value={value} placeholder="Напиши своими словами…" onChange={event => onChange(event.target.value)} />
      )}
    </article>
  );
}

function PhaseCheck({
  lesson,
  answers,
  difficulty,
  comment,
  onEdit,
  onDifficulty,
  onComment,
}: {
  lesson: Lesson;
  answers: Record<string, string>;
  difficulty: Difficulty;
  comment: string;
  onEdit: () => void;
  onDifficulty: (value: Difficulty) => void;
  onComment: (value: string) => void;
}) {
  const empty = lesson.questions.filter(question => !(answers[question.id] ?? "").trim()).length;
  return (
    <>
      <h2 className="large-title" style={{ fontSize: 28 }}>
        Твои ответы
      </h2>
      <p className="help">Проверь, все ли ответы заполнены.</p>
      {empty > 0 && (
        <p className="warn">
          Пустых ответов: {empty}. Они попадут в «нужно внимание».
        </p>
      )}
      {lesson.questions.map((question, index) => {
        const raw = (answers[question.id] ?? "").trim();
        const shown = question.kind === "mcq" ? (question.options?.find(option => option.key === raw)?.text ?? (raw ? raw : "Нет ответа")) : raw || "Нет ответа";
        return (
          <article className="q-card" key={question.id}>
            <div className="answer-line">
              <h3>
                <span className="q-no">{index + 1}.</span>
                {question.title}
              </h3>
              <CheckDot on={Boolean(raw)} />
            </div>
            <div className="answer-line">
              <p>{shown}</p>
              <button className="linkish" type="button" onClick={onEdit}>
                Изменить
              </button>
            </div>
          </article>
        );
      })}
      <div className="section-label">Как тебе этот фрагмент?</div>
      <div className="diff">
        {(
          [
            ["easy", "Слишком легко", "😌"],
            ["ok", "Подходит", "🙂"],
            ["hard", "Сложно", "😣"],
          ] as const
        ).map(([id, label, face]) => (
          <button key={id} type="button" className={difficulty === id ? "on" : ""} onClick={() => onDifficulty(id)}>
            <span className="face" aria-hidden>
              {face}
            </span>
            {label}
          </button>
        ))}
      </div>
      <label className="field" style={{ marginTop: 12 }}>
        <span>Дополнительный комментарий (необязательно)</span>
        <textarea className="answer-box" name="comment" value={comment} placeholder="Например, вопрос, который остался, или слово, которое зацепило." onChange={event => onComment(event.target.value)} />
      </label>
    </>
  );
}

function PhaseReview({
  lesson,
  review,
  tab,
  onTab,
  glosses,
  onWord,
}: {
  lesson: Lesson;
  review: NonNullable<ReturnType<typeof useApp>["state"]["attempts"][string]>["review"];
  tab: ReviewTab;
  onTab: (tab: ReviewTab) => void;
  glosses: { id: string; hanzi: string; gloss: string; kind: "word" | "idiom" | "grammar"; status: "repeat" | "learning" | "known" }[];
  onWord: (id: string) => void;
}) {
  if (!review) return null;
  const scores = tally(review.items);
  const moments = review.keyMoments && review.keyMoments.length > 0 ? review.keyMoments : lesson.keyMoments;
  return (
    <>
      <h2 className="large-title" style={{ fontSize: 28 }}>
        Разбор
      </h2>
      <p className="help">Отличная работа. Давай разберём твои ответы и посмотрим важные моменты.</p>
      {review.warning ? <p className="warn">{review.warning}</p> : null}
      {review.source === "model" ? <p className="source-line">Разбор модели</p> : null}
      <div className="scores">
        <div className="score correct">
          <b>{scores.correct}</b>
          <span>{plural(scores.correct, "правильно", "правильно", "правильно")}</span>
        </div>
        <div className="score partial">
          <b>{scores.partial}</b>
          <span>частично</span>
        </div>
        <div className="score attention">
          <b>{scores.attention}</b>
          <span>требует внимания</span>
        </div>
      </div>
      <div className="tabs-line">
        <button type="button" className={tab === "questions" ? "on" : ""} onClick={() => onTab("questions")}>
          По вопросам
        </button>
        <button type="button" className={tab === "moments" ? "on" : ""} onClick={() => onTab("moments")}>
          Ключевые моменты
        </button>
        <button type="button" className={tab === "words" ? "on" : ""} onClick={() => onTab("words")}>
          Слова ({glosses.length})
        </button>
      </div>
      {tab === "questions" &&
        review.items.map(item => {
          const question = lesson.questions.find(entry => entry.id === item.id);
          return (
            <article className="review-card" key={item.id}>
              <div className="review-head">
                <h3>{question?.title ?? item.id}</h3>
                <span className={`badge ${item.verdict}`}>{item.headline}</span>
              </div>
              <p>{item.explanation}</p>
              {item.examples.length > 0 && (
                <>
                  <p className="section-label">Примеры:</p>
                  <ul className="examples">
                    {item.examples.map(example => (
                      <li key={example}>{example}</li>
                    ))}
                  </ul>
                </>
              )}
            </article>
          );
        })}
      {tab === "moments" && (
        <ol className="examples">
          {moments.map(moment => (
            <li key={moment}>{moment}</li>
          ))}
        </ol>
      )}
      {tab === "words" &&
        glosses.map(gloss => (
          <button key={gloss.id} type="button" className="know-row" onClick={() => onWord(gloss.id)}>
            <span className="know-top">
              <h3>{gloss.hanzi}</h3>
              <span className={`badge ${gloss.kind === "word" ? gloss.status : gloss.kind}`}>
                {gloss.kind === "word" ? statusLabel(gloss.status) : kindLabel(gloss.kind)}
              </span>
            </span>
            <p>{gloss.gloss}</p>
          </button>
        ))}
    </>
  );
}
