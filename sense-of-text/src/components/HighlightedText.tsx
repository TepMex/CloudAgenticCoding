import type { ReactNode } from "react";
import type { Token } from "@/lib/tokenize";
import { cn } from "@/lib/utils";

type HighlightedTextProps = {
  text: string;
  tokens: Token[];
  scores: number[];
  className?: string;
};

function highlightAlpha(score: number): number {
  return 0.15 + score * 0.75;
}

export function HighlightedText({ text, tokens, scores, className }: HighlightedTextProps) {
  if (!text) return null;

  const segments: ReactNode[] = [];
  let cursor = 0;

  tokens.forEach((token, i) => {
    if (token.start > cursor) {
      segments.push(
        <span key={`gap-${cursor}`}>{text.slice(cursor, token.start)}</span>,
      );
    }
    const score = scores[i] ?? 0;
    const alpha = highlightAlpha(score);
    segments.push(
      <span
        key={`tok-${token.start}`}
        className={cn("rounded-sm px-0.5")}
        style={{ backgroundColor: `hsl(45 93% 47% / ${alpha})` }}
        title={`Importance: ${(score * 100).toFixed(0)}%`}
      >
        {token.text}
      </span>,
    );
    cursor = token.end;
  });

  if (cursor < text.length) {
    segments.push(<span key={`tail-${cursor}`}>{text.slice(cursor)}</span>);
  }

  return (
    <p className={cn("whitespace-pre-wrap text-base leading-relaxed", className)}>
      {segments}
    </p>
  );
}
