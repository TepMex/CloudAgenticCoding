import { BookOpen, Check, ChevronLeft, GraduationCap, Languages, UserRound, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect } from "react";
import { useApp } from "./state.tsx";
import type { Book, GlossKind, Route, WordStatus } from "./lib/types.ts";

export function statusLabel(status: WordStatus): string {
  if (status === "repeat") return "Нужно повторить";
  if (status === "known") return "Знаю";
  return "В процессе";
}

export function kindLabel(kind: GlossKind): string {
  if (kind === "idiom") return "идиома";
  if (kind === "grammar") return "грамматика";
  return "слово";
}

export function Header({
  title,
  large,
  onBack,
  right,
}: {
  title?: string;
  large?: ReactNode;
  onBack?: () => void;
  right?: ReactNode;
}) {
  const bar = Boolean(onBack || title || right);
  return (
    <header className="header">
      {bar && (
        <div className="header-bar">
          {onBack ? (
            <button className="icon-btn" type="button" onClick={onBack} aria-label="Назад">
              <ChevronLeft />
            </button>
          ) : (
            <span />
          )}
          {title ? <h1 className="header-title">{title}</h1> : <span />}
          <div className="header-right">{right}</div>
        </div>
      )}
      {large}
    </header>
  );
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T;
  options: { id: T; label: string }[];
  onChange: (id: T) => void;
}) {
  return (
    <div className="segmented" role="tablist">
      {options.map(option => (
        <button key={option.id} type="button" role="tab" aria-selected={option.id === value} className={option.id === value ? "on" : ""} onClick={() => onChange(option.id)}>
          {option.label}
        </button>
      ))}
    </div>
  );
}

export function Cover({ book, size }: { book: Book; size: "sm" | "lg" }) {
  return (
    <div className={`cover cover-${book.cover} ${size}`} aria-hidden>
      <span className="cover-title">
        {Array.from(book.title.slice(0, 4)).map((char, index) => (
          <span key={index}>{char}</span>
        ))}
      </span>
      <span className="cover-author">{book.author.slice(0, 4)}</span>
    </div>
  );
}

export function Progress({ value }: { value: number }) {
  return (
    <div className="bar" aria-hidden>
      <span style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </div>
  );
}

export function Sheet({ title, onClose, children }: { title?: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className="overlay" onClick={onClose}>
      <div className="sheet" role="dialog" aria-modal="true" aria-label={title} onClick={event => event.stopPropagation()}>
        <div className="handle" />
        {title ? <h2>{title}</h2> : null}
        {children}
      </div>
    </div>
  );
}

const TABS: { id: Route["name"]; label: string; icon: LucideIcon }[] = [
  { id: "library", label: "Книги", icon: BookOpen },
  { id: "study", label: "Учёба", icon: GraduationCap },
  { id: "knowledge", label: "Словарь", icon: Languages },
  { id: "profile", label: "Профиль", icon: UserRound },
];

export function TabBar() {
  const { state, go } = useApp();
  const current = state.route.name === "knowledge" ? "knowledge" : state.route.name;
  return (
    <nav className="tabbar" aria-label="Разделы">
      {TABS.map(tab => {
        const Icon = tab.icon;
        const on = current === tab.id;
        return (
          <button
            key={tab.id}
            type="button"
            className={on ? "on" : ""}
            aria-current={on ? "page" : undefined}
            onClick={() => go(tab.id === "knowledge" ? { name: "knowledge", tab: "words" } : { name: tab.id as "library" | "study" | "profile" })}
          >
            <Icon strokeWidth={on ? 2.4 : 2} />
            {tab.label}
          </button>
        );
      })}
    </nav>
  );
}

export function CheckDot({ on }: { on: boolean }) {
  return (
    <span className={on ? "check" : "check off"} aria-hidden>
      <Check />
    </span>
  );
}
