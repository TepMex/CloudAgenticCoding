import { ChevronRight, Star, Volume2 } from "lucide-react";
import { useEffect, useState } from "react";
import { kindLabel, statusLabel } from "../components.tsx";
import { endpointReady } from "../lib/settings.ts";
import { passageOf } from "../lib/text.ts";
import { useApp } from "../state.tsx";

function sentenceAround(passage: string, word: string): string {
  const chunks = passage
    .split(/(?<=[。！？!?])/)
    .map(part => part.trim())
    .filter(Boolean);
  return chunks.find(chunk => chunk.includes(word)) ?? passage;
}

function Marked({ text, word }: { text: string; word: string }) {
  const at = word ? text.indexOf(word) : -1;
  if (at < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, at)}
      <mark>{word}</mark>
      {text.slice(at + word.length)}
    </>
  );
}

export function WordSheet() {
  const app = useApp();
  const [more, setMore] = useState(false);
  const gloss = app.word;
  const hanzi = gloss?.hanzi ?? app.lookup?.hanzi ?? "";

  useEffect(() => {
    setMore(false);
  }, [hanzi]);

  useEffect(() => {
    if (!hanzi) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") app.closeWord();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [hanzi, app]);

  if (!gloss && !app.lookup) return null;

  const passage =
    app.state.lessons.map(lesson => passageOf(lesson.tokens)).find(text => text.includes(hanzi)) ?? app.lookup?.sentence ?? "";
  const sentence = passage ? sentenceAround(passage, hanzi) : "";
  const ready = endpointReady(app.state.settings);

  return (
    <div className="overlay float" onClick={app.closeWord}>
      <div className="word-card" role="dialog" aria-modal="true" aria-label={hanzi} onClick={event => event.stopPropagation()}>
        {sentence ? (
          <p className="context-line" lang="zh">
            <Marked text={sentence} word={hanzi} />
          </p>
        ) : null}
        <div className="word-head">
          <div className="hanzi-xl" lang="zh">
            {hanzi}
          </div>
          <div className="word-tools">
            <button className="icon-btn" type="button" aria-label="Произнести" onClick={() => app.speak(hanzi)}>
              <Volume2 />
            </button>
            {gloss && (
              <button className="icon-btn" type="button" aria-label="Сменить статус" onClick={() => app.toggleGloss(gloss.id)}>
                <Star fill={gloss.status === "repeat" ? "currentColor" : "none"} color={gloss.status === "repeat" ? "#c47a00" : "currentColor"} />
              </button>
            )}
          </div>
        </div>
        {gloss ? (
          <>
            {gloss.pinyin ? <p className="pinyin">{gloss.pinyin}</p> : null}
            <span className={`badge ${gloss.kind}`}>{kindLabel(gloss.kind)}</span>
            <p style={{ marginTop: 10 }}>{gloss.gloss}</p>
            {gloss.example ? <p className="example-zh" lang="zh">{gloss.example}</p> : null}
            {gloss.exampleGloss ? <p className="help">{gloss.exampleGloss}</p> : null}
            <button className="more-btn" type="button" onClick={() => setMore(value => !value)}>
              {more ? "Скрыть подробности" : "Смотреть подробнее"}
              <ChevronRight />
            </button>
            {more && (
              <div>
                <p>Статус: {statusLabel(gloss.status)}. Звезда переключает его.</p>
                <p className="muted">Последнее: {gloss.lastSeen}</p>
                {ready ? (
                  <button className="btn-small" type="button" style={{ marginTop: 10 }} onClick={() => void app.askWord(gloss.hanzi, gloss.example || sentence || gloss.hanzi)}>
                    Уточнить у модели
                  </button>
                ) : (
                  <p className="help">Чтобы переспросить модель, укажите OpenAI-compatible endpoint в профиле.</p>
                )}
              </div>
            )}
          </>
        ) : (
          <>
            <p className="help">В локальном словаре этой карточки нет.</p>
            <button className="btn-primary" type="button" onClick={() => void app.askWord(hanzi, sentence || hanzi)}>
              Спросить модель
            </button>
            {!ready && <p className="help">Нужен OpenAI-compatible endpoint: базовый URL и идентификатор модели.</p>}
          </>
        )}
      </div>
    </div>
  );
}
