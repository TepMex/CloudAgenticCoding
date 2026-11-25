import { useState } from "react";
import type { SearchHit } from "../lib/types.ts";

const FAMILY_RU: Record<string, string> = {
  example: "пример из документации",
  option: "опция",
  "audio-filter": "аудиофильтр",
  "video-filter": "видеофильтр",
  "audio-source": "источник звука",
  "video-source": "источник видео",
  "multimedia-filter": "фильтр",
  "video-encoder": "видеокодировщик",
  "audio-encoder": "аудиокодировщик",
  muxer: "муксер",
  demuxer: "демуксер",
  protocol: "протокол",
  "bitstream-filter": "битовый фильтр",
  "input-device": "устройство ввода",
  "output-device": "устройство вывода",
  writer: "отчёт ffprobe",
};

export function CommandCard({ hit, rank, topScore }: { hit: SearchHit; rank: number; topScore: number }) {
  const [copied, setCopied] = useState(false);
  const line = hit.command ?? hit.signature;
  const width = topScore > 0 ? Math.max(8, Math.round((hit.score / topScore) * 100)) : 8;
  const kind = FAMILY_RU[hit.family] ?? hit.family;

  async function copy() {
    if (!line) return;
    await navigator.clipboard.writeText(line);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1400);
  }

  return (
    <article className="rounded-xl border border-line bg-card px-4 py-3">
      <div className="mb-2 flex items-baseline gap-3">
        <span className="w-6 font-mono text-lg text-acid">{rank}</span>
        <h3 className="min-w-0 flex-1 truncate text-base font-semibold">{hit.title}</h3>
        <span className="font-mono text-xs text-muted">{hit.score.toFixed(2)}</span>
      </div>
      <div className="mb-3 h-1 overflow-hidden rounded bg-line">
        <div className="h-full bg-acid" style={{ width: `${width}%` }} />
      </div>
      {line ? (
        <div className="flex items-start gap-2 rounded-lg bg-black/50 px-3 py-2">
          <pre className="min-w-0 flex-1 overflow-x-auto whitespace-pre-wrap break-all font-mono text-[13px] leading-relaxed text-acid">
            {line}
          </pre>
          <button
            type="button"
            onClick={() => void copy()}
            className="shrink-0 rounded-md border border-line px-2 py-1 text-xs text-fg hover:border-acid hover:text-acid"
          >
            {copied ? "Скопировано" : "Копировать"}
          </button>
        </div>
      ) : null}
      {hit.summary ? <p className="mt-3 text-sm leading-relaxed text-muted">{hit.summary}</p> : null}
      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
        <span>{kind}</span>
        <span>{hit.synthesized ? "шаблон по разделу документации" : "формулировка из документации"}</span>
        <a className="text-amber underline-offset-2 hover:underline" href={hit.url} target="_blank" rel="noreferrer">
          Открыть раздел
        </a>
      </div>
    </article>
  );
}
