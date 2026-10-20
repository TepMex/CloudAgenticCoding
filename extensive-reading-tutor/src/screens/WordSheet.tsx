import { Star, Volume2 } from "lucide-react";
import { useState } from "react";
import { Sheet, kindLabel, statusLabel } from "../components.tsx";
import { endpointReady } from "../lib/settings.ts";
import { useApp } from "../state.tsx";

export function WordSheet() {
  const app = useApp();
  const [more, setMore] = useState(false);
  if (!app.word && !app.lookup) return null;
  const gloss = app.word;
  const hanzi = gloss?.hanzi ?? app.lookup?.hanzi ?? "";
  const sentence = app.lookup?.sentence ?? "";

  return (
    <Sheet onClose={app.closeWord}>
      <div className="answer-line">
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
          {gloss.example ? (
            <p className="help" lang="zh">
              {gloss.example}
              {gloss.exampleGloss ? <span lang="ru"> — {gloss.exampleGloss}</span> : null}
            </p>
          ) : null}
          <p className="muted">Статус: {statusLabel(gloss.status)}. Звезда переключает его.</p>
          <button className="btn-ghost" type="button" onClick={() => setMore(value => !value)}>
            {more ? "Скрыть подробности" : "Смотреть подробнее"}
          </button>
          {more && (
            <div>
              <p>Последнее: {gloss.lastSeen}</p>
              {!endpointReady(app.state.settings) ? (
                <p className="help">Чтобы переспросить модель, открой профиль и укажи endpoint.</p>
              ) : (
                <button className="btn-small" type="button" onClick={() => void app.askWord(gloss.hanzi, gloss.example || gloss.hanzi)}>
                  Уточнить у модели
                </button>
              )}
            </div>
          )}
        </>
      ) : (
        <>
          <p className="help">Нет карточки в локальном словаре.</p>
          <button className="btn-primary" type="button" onClick={() => void app.askWord(hanzi, sentence || hanzi)}>
            Спросить модель
          </button>
        </>
      )}
    </Sheet>
  );
}
