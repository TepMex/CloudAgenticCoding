import { useEffect, useRef, useState } from "react";
import { CommandCard } from "./components/CommandCard.tsx";
import { searchCommands, startSearch, subscribeStatus, type Status } from "./lib/search.ts";
import type { SearchResult } from "./lib/types.ts";

const SUGGESTIONS = [
  "Сделать тише",
  "Сжать mp4 без заметной потери качества",
  "Вырезать фрагмент с 1:20 длительностью 30 секунд",
  "Извлечь звук в mp3",
  "Уменьшить разрешение до 1280×720",
  "Наложить субтитры на видео",
  "Склеить несколько роликов",
  "Ускорить видео в два раза",
  "Сделать gif из фрагмента",
];

type Turn =
  | { id: string; role: "user"; text: string }
  | { id: string; role: "assistant"; result: SearchResult }
  | { id: string; role: "error"; text: string };

export function App() {
  const [status, setStatus] = useState<Status>({ phase: "corpus" });
  const [draft, setDraft] = useState("");
  const [turns, setTurns] = useState<Turn[]>([]);
  const [busy, setBusy] = useState(false);
  const scroller = useRef<HTMLDivElement>(null);
  const latestTurn = useRef<HTMLElement>(null);
  const ready = status.phase === "ready";

  useEffect(() => subscribeStatus(setStatus), []);
  useEffect(() => {
    void startSearch();
  }, []);
  useEffect(() => {
    latestTurn.current?.scrollIntoView({ block: "start", behavior: "smooth" });
  }, [turns]);

  async function ask(text: string) {
    const query = text.trim();
    if (!query || busy || !ready) return;
    setDraft("");
    setTurns(current => [...current, { id: crypto.randomUUID(), role: "user", text: query }]);
    setBusy(true);
    try {
      const result = await searchCommands(query);
      setTurns(current => [...current, { id: crypto.randomUUID(), role: "assistant", result }]);
    } catch (error) {
      setTurns(current => [
        ...current,
        { id: crypto.randomUUID(), role: "error", text: error instanceof Error ? error.message : "Поиск не удался" },
      ]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <header className="border-b border-line px-4 py-3">
        <div className="mx-auto flex w-full max-w-3xl items-start justify-between gap-4">
          <div>
            <p className="font-mono text-[11px] tracking-[0.22em] text-acid">FFMPEG</p>
            <h1 className="text-xl font-semibold">Команды по документации</h1>
          </div>
          <p className="max-w-[16rem] text-right text-xs leading-relaxed text-muted" aria-live="polite">
            {statusLabel(status)}
          </p>
        </div>
        {status.phase === "download" ? (
          <div className="mx-auto mt-3 h-1 w-full max-w-3xl overflow-hidden rounded bg-line">
            <div
              className="h-full bg-acid"
              style={{ width: `${Math.min(100, Math.round((status.loaded / Math.max(status.total, 1)) * 100))}%` }}
            />
          </div>
        ) : null}
        {status.phase === "index" ? (
          <div className="mx-auto mt-3 h-1 w-full max-w-3xl overflow-hidden rounded bg-line">
            <div
              className="h-full bg-amber"
              style={{ width: `${Math.min(100, Math.round((status.done / Math.max(status.total, 1)) * 100))}%` }}
            />
          </div>
        ) : null}
      </header>

      <div ref={scroller} className="min-h-0 flex-1 overflow-y-auto px-4 py-6">
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
          {turns.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-line bg-card/70 px-5 py-6">
              <p className="max-w-xl text-sm leading-relaxed text-muted">
                Опишите, что нужно сделать с медиафайлом. Ответ — ранжированный список команд из документации FFmpeg.
                Сходство считает EmbeddingGemma 2 в браузере. Модель скачивается с Hugging Face и не входит в приложение.
              </p>
              <div className="mt-4 flex flex-wrap gap-2">
                {SUGGESTIONS.map(suggestion => (
                  <button
                    key={suggestion}
                    type="button"
                    disabled={!ready || busy}
                    onClick={() => void ask(suggestion)}
                    className="rounded-full border border-line px-3 py-1.5 text-left text-sm text-fg enabled:hover:border-acid enabled:hover:text-acid disabled:opacity-50"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {turns.map((turn, index) => {
            const ref = index === turns.length - 1 ? latestTurn : undefined;
            if (turn.role === "user") {
              return (
                <div key={turn.id} ref={ref} className="flex scroll-mt-3 justify-end">
                  <p className="max-w-[85%] rounded-2xl rounded-br-md border border-line bg-card-2 px-4 py-2 text-sm leading-relaxed">
                    {turn.text}
                  </p>
                </div>
              );
            }
            if (turn.role === "error") {
              return (
                <p key={turn.id} ref={ref} className="scroll-mt-3 rounded-xl border border-amber/40 bg-card px-4 py-3 text-sm text-amber">
                  {turn.text}
                </p>
              );
            }
            const top = turn.result.hits[0]?.score ?? 1;
            return (
              <div key={turn.id} ref={ref} className="flex scroll-mt-3 flex-col gap-2">
                <p className="text-sm text-muted">
                  {turn.result.hits.length
                    ? "Ближайшие команды по косинусному сходству эмбеддингов."
                    : "Ничего подходящего не нашлось."}
                  {turn.result.partial ? " Индекс документации ещё дополняется." : ""}
                </p>
                {turn.result.hits.map((hit, index) => (
                  <CommandCard key={hit.id} hit={hit} rank={index + 1} topScore={top} />
                ))}
              </div>
            );
          })}
          {busy ? <p className="text-sm text-muted">Считаю эмбеддинг запроса…</p> : null}
        </div>
      </div>

      <form
        className="border-t border-line px-4 py-3"
        onSubmit={event => {
          event.preventDefault();
          void ask(draft);
        }}
      >
        <div className="mx-auto flex w-full max-w-3xl items-end gap-2">
          <label className="sr-only" htmlFor="task">
            Задача для FFmpeg
          </label>
          <textarea
            id="task"
            rows={2}
            value={draft}
            placeholder="Что сделать с файлом?"
            disabled={busy}
            onChange={event => setDraft(event.target.value)}
            onKeyDown={event => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                void ask(draft);
              }
            }}
            className="min-h-[3.25rem] flex-1 resize-none rounded-xl border border-line bg-card px-3 py-2 text-sm outline-none placeholder:text-muted focus:border-acid"
          />
          <button
            type="submit"
            disabled={!ready || busy || !draft.trim()}
            className="rounded-xl bg-acid px-4 py-3 text-sm font-semibold text-ink disabled:opacity-40"
          >
            Найти
          </button>
        </div>
      </form>
    </div>
  );
}

function statusLabel(status: Status): string {
  switch (status.phase) {
    case "corpus":
      return "Читаю корпус документации…";
    case "download": {
      const loaded = formatMb(status.loaded);
      const total = formatMb(status.total);
      return `Скачиваю EmbeddingGemma 2 · ${loaded} / ${total} МБ`;
    }
    case "engine":
      return "Загружаю модель в LiteRT…";
    case "check":
      return "Сверяю эмбеддинги с моделью в браузере…";
    case "index":
      return `Считаю эмбеддинги документации · ${status.done} / ${status.total}`;
    case "ready":
      return status.partial
        ? `Можно искать · досчитываю индекс (${status.backend})`
        : `Готово · ${status.count} фрагментов · ${status.backend}${status.shipped ? "" : " · локальный индекс"}`;
    case "error":
      return status.message;
  }
}

function formatMb(bytes: number): string {
  return (bytes / (1024 * 1024)).toFixed(0);
}
