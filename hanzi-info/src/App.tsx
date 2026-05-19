import { type ChangeEvent, type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BookOpen, Copy, Link2, Loader2, Search, Settings } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  allHanziGraphemes,
  canonicalHanziHash,
  canonicalSettingsHash,
  isSettingsHash,
  loadHanziDatabase,
  parseHanziHash,
  radicalsForCharacter,
} from "@/lib/hanzi-db";
import { glossLabelKey, glossText } from "@/lib/gloss";
import type { HanziDatabase, HanziRow, PhoneticSeries } from "@/lib/hanzi-types";
import { loadAppLanguage, saveAppLanguage, type AppLanguage } from "@/lib/settings";
import { regularityDegreeText, t } from "@/lib/ui-strings";

import "./index.css";

const PREV_HASH_KEY = "hanzi_info_prev_hash";

function Glyph({ ch, className }: { ch: string; className?: string }) {
  return (
    <span
      className={["font-serif text-foreground tabular-nums", className].filter(Boolean).join(" ")}
      lang="zh-Hans"
    >
      {ch}
    </span>
  );
}

function MemberCloud({ members, lang }: { members: string[]; lang: AppLanguage }) {
  const preview = members.slice(0, 120);
  return (
    <div className="max-h-48 overflow-y-auto rounded-md border border-border/80 bg-muted/30 p-3 text-sm leading-relaxed">
      <div className="flex flex-wrap gap-1.5">
        {preview.map(h => (
          <span
            key={h}
            className="inline-flex min-w-[1.75rem] justify-center rounded bg-background px-1.5 py-0.5 font-serif shadow-sm"
            lang="zh-Hans"
          >
            {h}
          </span>
        ))}
      </div>
      {members.length > preview.length ? (
        <p className="text-muted-foreground mt-2 text-xs">
          {t(lang, "showingMembers")} {preview.length} {t(lang, "ofMembers")} {members.length}{" "}
          {t(lang, "charactersWord")}
        </p>
      ) : null}
    </div>
  );
}

function RowDetails({ row, lang }: { row: HanziRow; lang: AppLanguage }) {
  const gKey = glossLabelKey(lang, row);
  const gloss = glossText(lang, row);
  return (
    <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
      <div>
        <dt className="text-muted-foreground">{t(lang, "type")}</dt>
        <dd className="font-medium">{row.type}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">{t(lang, "reading")}</dt>
        <dd className="font-medium">{row.reading || "—"}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">{t(lang, "initialsFinalTone")}</dt>
        <dd className="font-medium">
          {row.initiale || "—"} · {row.finale || "—"} · {row.tone > 0 ? row.tone : "—"}
        </dd>
      </div>
      <div className="sm:col-span-2">
        <dt className="text-muted-foreground">{t(lang, gKey)}</dt>
        <dd className="text-pretty">{gloss}</dd>
      </div>
      {lang === "ru" && row.meaning_ru && row.meaning_en && row.meaning_ru !== row.meaning_en ? (
        <div className="sm:col-span-2">
          <dt className="text-muted-foreground">{t(lang, "glossEn")}</dt>
          <dd className="text-muted-foreground text-pretty text-sm">{row.meaning_en}</dd>
        </div>
      ) : null}
      {lang === "en" && row.meaning_ru ? (
        <div className="sm:col-span-2">
          <dt className="text-muted-foreground">{t(lang, "glossRu")}</dt>
          <dd className="text-pretty">{row.meaning_ru}</dd>
        </div>
      ) : null}
    </dl>
  );
}

function anchorSnippet(row: HanziRow, lang: AppLanguage): string {
  const text = lang === "ru" ? row.meaning_ru || row.meaning_en : row.meaning_en;
  if (!text) return "";
  return text.length > 80 ? `${text.slice(0, 80)}…` : text;
}

function PhoneticSeriesCard({ db, series, lang }: { db: HanziDatabase; series: PhoneticSeries; lang: AppLanguage }) {
  const compId = db.by_hanzi[series.component];
  const compRow = compId !== undefined ? db.hanzi[compId - 1] : undefined;
  return (
    <div className="rounded-xl border border-primary/25 bg-primary/5 p-4 shadow-sm">
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <span className="text-muted-foreground text-xs uppercase tracking-wide">{t(lang, "phoneticAnchor")}</span>
        <span className="font-serif text-4xl leading-none">
          <Glyph ch={series.component} />
        </span>
        {compRow ? (
          <span className="text-muted-foreground text-sm">
            {compRow.reading ? `${compRow.reading} · ` : ""}
            {anchorSnippet(compRow, lang)}
          </span>
        ) : null}
      </div>
      <p className="text-muted-foreground mb-2 text-xs">
        {regularityDegreeText(lang, series.regularity_scale)}
      </p>
      <p className="text-muted-foreground mb-2 font-mono text-[0.7rem]">
        {t(lang, "setKey")}: {series.set_key}
      </p>
      <p className="mb-2 text-xs font-medium">{t(lang, "seriesMembers")}</p>
      <MemberCloud members={series.members} lang={lang} />
    </div>
  );
}

function HanziCharacterCard({ db, ch, lang }: { db: HanziDatabase; ch: string; lang: AppLanguage }) {
  const activeRow = useMemo(() => {
    const id = db.by_hanzi[ch];
    if (id === undefined) return undefined;
    return db.hanzi[id - 1];
  }, [db, ch]);

  const phonetic = db.phonetic_series_by_hanzi[ch];
  const radicalParts = radicalsForCharacter(db, ch);

  if (!activeRow) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="font-serif text-2xl">{t(lang, "notInIndexTitle")}</CardTitle>
          <CardDescription>
            <Glyph ch={ch} className="text-3xl" /> {t(lang, "notInIndexDescBefore")}
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <div>
          <CardTitle className="font-serif text-5xl font-normal leading-none">
            <Glyph ch={activeRow.hanzi} />
          </CardTitle>
          <CardDescription className="mt-3">
            {t(lang, "identifier")} #{activeRow.id}
          </CardDescription>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        <RowDetails row={activeRow} lang={lang} />

        <div className="border-t pt-6">
          <h3 className="mb-2 text-sm font-semibold">{t(lang, "phoneticComponent")}</h3>
          {!phonetic || phonetic.length === 0 ? (
            <p className="text-muted-foreground text-sm leading-relaxed">
              {t(lang, "phoneticNone")} <strong>{t(lang, "ideographic")}</strong> {t(lang, "phoneticHere")}
            </p>
          ) : (
            <div className="flex flex-col gap-6">
              {phonetic.map(s => (
                <PhoneticSeriesCard key={`${s.set_key}-${s.regularity_scale}`} db={db} series={s} lang={lang} />
              ))}
            </div>
          )}
        </div>

        <div className="border-t pt-6">
          <h3 className="mb-2 text-sm font-semibold">{t(lang, "structuralParts")}</h3>
          {radicalParts.length === 0 ? (
            <p className="text-muted-foreground text-sm">{t(lang, "structuralNone")}</p>
          ) : (
            <ul className="flex flex-col gap-3">
              {radicalParts.map(r => {
                const radicalLabel = r.radical_name_en || (r.type === "Radical" ? r.meaning_en : "");
                return (
                  <li
                    key={r.id}
                    className="flex flex-wrap items-start justify-between gap-2 rounded-lg border border-border/70 bg-muted/20 px-3 py-2"
                  >
                    <span className="font-serif text-2xl">
                      <Glyph ch={r.hanzi} />
                    </span>
                    <div className="min-w-0 flex flex-1 flex-col items-end gap-0.5 text-right sm:max-w-[min(100%,28rem)]">
                      {radicalLabel ? (
                        <span className="text-foreground text-sm font-medium leading-snug">{radicalLabel}</span>
                      ) : null}
                      <span className="text-muted-foreground text-xs leading-snug">
                        {r.type}
                        {r.reading ? ` · ${r.reading}` : ""}
                      </span>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
          <p className="text-muted-foreground mt-3 text-xs leading-relaxed">{t(lang, "radicalGlossNote")}</p>
        </div>
      </CardContent>
    </Card>
  );
}

function ShareLookupBar({
  shareUrl,
  copyHint,
  onCopy,
  lang,
}: {
  shareUrl: string;
  copyHint: string | null;
  onCopy: () => void;
  lang: AppLanguage;
}) {
  if (!shareUrl) return null;
  return (
    <div className="w-full min-w-0 rounded-xl border border-border/80 bg-card p-4 shadow-sm">
      <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
        <div className="text-muted-foreground flex min-w-0 flex-1 gap-2 text-xs leading-relaxed">
          <Link2 className="size-3.5 shrink-0 translate-y-0.5" aria-hidden />
          <span className="min-w-0 break-all font-mono">{shareUrl}</span>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={onCopy} className="shrink-0 gap-1.5 sm:self-center">
          <Copy className="size-3.5" />
          {t(lang, "copyLink")}
        </Button>
      </div>
      {copyHint ? <p className="text-muted-foreground mt-2 text-xs">{copyHint}</p> : null}
    </div>
  );
}

function SettingsPanel({
  lang,
  onLanguageChange,
  onBack,
}: {
  lang: AppLanguage;
  onLanguageChange: (l: AppLanguage) => void;
  onBack: () => void;
}) {
  const onSelect = (e: ChangeEvent<HTMLSelectElement>) => {
    const v = e.currentTarget.value;
    onLanguageChange(v === "ru" ? "ru" : "en");
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="font-serif text-xl">{t(lang, "settingsTitle")}</CardTitle>
        <CardDescription>{t(lang, "settingsDesc")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <div className="flex flex-col gap-2">
          <Label htmlFor="ui-lang">{t(lang, "labelUiLanguage")}</Label>
          <select
            id="ui-lang"
            value={lang}
            onChange={onSelect}
            className="border-input bg-background ring-offset-background focus-visible:ring-ring flex h-10 w-full max-w-xs rounded-md border px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none"
          >
            <option value="en">{t(lang, "langEnglish")}</option>
            <option value="ru">{t(lang, "langRussian")}</option>
          </select>
        </div>
        <div>
          <Button type="button" variant="outline" onClick={onBack}>
            {t(lang, "backToLookup")}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

export function App() {
  const [db, setDb] = useState<HanziDatabase | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [activeGraphemes, setActiveGraphemes] = useState<string[]>([]);
  const [copyHint, setCopyHint] = useState<"copied" | "failed" | null>(null);
  const [lang, setLang] = useState<AppLanguage>(() => loadAppLanguage());
  const [settingsOpen, setSettingsOpen] = useState(false);

  useEffect(() => {
    document.documentElement.lang = lang === "ru" ? "ru" : "en";
  }, [lang]);

  useEffect(() => {
    const meta = document.querySelector('meta[name="description"]');
    if (meta) meta.setAttribute("content", t(lang, "htmlDescription"));
  }, [lang]);

  useEffect(() => {
    loadHanziDatabase()
      .then(setDb)
      .catch(e => setLoadError(e instanceof Error ? e.message : String(e)));
  }, []);

  const applyHanziHash = useCallback((hash: string) => {
    const raw = parseHanziHash(hash);
    const gs = raw ? allHanziGraphemes(raw) : [];
    setActiveGraphemes(gs);
    setQuery(raw ?? "");
    if (gs.length > 0) {
      const want = canonicalHanziHash(gs.join(""));
      if (typeof window !== "undefined" && window.location.hash !== want) {
        window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}${want}`);
      }
    }
  }, []);

  const syncRouteFromHash = useCallback(() => {
    const h = window.location.hash;
    if (isSettingsHash(h)) {
      setSettingsOpen(true);
      return;
    }
    setSettingsOpen(false);
    applyHanziHash(h);
  }, [applyHanziHash]);

  useEffect(() => {
    syncRouteFromHash();
    window.addEventListener("hashchange", syncRouteFromHash);
    return () => window.removeEventListener("hashchange", syncRouteFromHash);
  }, [syncRouteFromHash]);

  const openSettings = () => {
    sessionStorage.setItem(PREV_HASH_KEY, window.location.hash || canonicalHanziHash(""));
    window.location.hash = canonicalSettingsHash();
  };

  const closeSettings = () => {
    window.location.hash = sessionStorage.getItem(PREV_HASH_KEY) || "#/hanzi/";
  };

  const onLanguageChange = (l: AppLanguage) => {
    setLang(l);
    saveAppLanguage(l);
  };

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    const gs = allHanziGraphemes(query);
    if (gs.length === 0) return;
    window.location.hash = canonicalHanziHash(gs.join(""));
  };

  const shareUrl = useMemo(() => {
    if (activeGraphemes.length === 0) return "";
    const { origin, pathname } = window.location;
    return `${origin}${pathname}${canonicalHanziHash(activeGraphemes.join(""))}`;
  }, [activeGraphemes]);

  const copyShare = async () => {
    if (!shareUrl) return;
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopyHint("copied");
      setTimeout(() => setCopyHint(null), 2000);
    } catch {
      setCopyHint("failed");
      setTimeout(() => setCopyHint(null), 2500);
    }
  };

  const copyHintText =
    copyHint === "copied" ? t(lang, "linkCopied") : copyHint === "failed" ? t(lang, "copyFailed") : null;

  return (
    <div className="min-h-screen bg-gradient-to-b from-background to-muted/40">
      <div className="mx-auto flex min-w-0 max-w-3xl flex-col gap-8 px-4 py-10">
        <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
          <div className="space-y-2 text-center sm:min-w-0 sm:flex-1 sm:text-left">
            <div className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card px-3 py-1 text-xs text-muted-foreground shadow-sm">
              <BookOpen className="size-3.5" aria-hidden />
              {t(lang, "badgeTagline")}
            </div>
            <h1 className="font-serif text-3xl font-semibold tracking-tight sm:text-4xl">{t(lang, "pageTitle")}</h1>
            <p className="text-muted-foreground max-w-2xl text-pretty text-sm leading-relaxed">
              {t(lang, "heroIntro")}{" "}
              <a
                className="text-primary underline-offset-4 hover:underline"
                href="https://hanzicraft.com/lists/phonetic-sets"
              >
                {t(lang, "heroIntroLink")}
              </a>{" "}
              {t(lang, "heroIntroTail")}
              {lang === "ru" ? <> {t(lang, "heroIntroTailRuGloss")}</> : null}
            </p>
          </div>
          <div className="flex justify-center sm:shrink-0 sm:pt-1">
            <Button type="button" variant="outline" size="sm" className="gap-2" onClick={openSettings}>
              <Settings className="size-4" aria-hidden />
              {t(lang, "settings")}
            </Button>
          </div>
        </header>

        {settingsOpen ? (
          <SettingsPanel lang={lang} onLanguageChange={onLanguageChange} onBack={closeSettings} />
        ) : (
          <Card>
            <CardHeader>
              <CardTitle className="font-serif text-xl">{t(lang, "cardLookupTitle")}</CardTitle>
              <CardDescription>
                {t(lang, "cardLookupDesc")}{" "}
                <code className="text-xs">#/hanzi/语言</code>. {t(lang, "cardLookupDescExample")}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form className="flex flex-col gap-3 sm:flex-row" onSubmit={onSearch}>
                <div className="flex flex-1 flex-col gap-2">
                  <Label htmlFor="hanzi-input">{t(lang, "labelCharacters")}</Label>
                  <Input
                    id="hanzi-input"
                    placeholder={t(lang, "inputPlaceholder")}
                    value={query}
                    onChange={e => setQuery(e.target.value)}
                    autoComplete="off"
                    spellCheck={false}
                    className="font-serif text-lg tracking-wide"
                  />
                </div>
                <div className="flex items-end gap-2">
                  <Button type="submit" className="w-full sm:w-auto">
                    <Search className="size-4" />
                    {t(lang, "search")}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        )}

        {loadError ? (
          <Card className="border-destructive/50 bg-destructive/5">
            <CardHeader>
              <CardTitle>{t(lang, "loadErrorTitle")}</CardTitle>
              <CardDescription>{loadError}</CardDescription>
            </CardHeader>
          </Card>
        ) : null}

        {!db && !loadError ? (
          <div className="text-muted-foreground flex items-center justify-center gap-2 py-16 text-sm">
            <Loader2 className="size-5 animate-spin" />
            {t(lang, "loadingDictionary")}
          </div>
        ) : null}

        {db && !settingsOpen && activeGraphemes.length > 0 ? (
          <div className="flex min-w-0 flex-col gap-6">
            <ShareLookupBar shareUrl={shareUrl} copyHint={copyHintText} onCopy={copyShare} lang={lang} />
            {activeGraphemes.map((ch, i) => (
              <HanziCharacterCard key={`${i}-${ch}`} db={db} ch={ch} lang={lang} />
            ))}
            <p className="text-muted-foreground text-center text-xs leading-relaxed">{db.about}</p>
          </div>
        ) : null}

        {db && !settingsOpen && activeGraphemes.length === 0 ? (
          <p className="text-muted-foreground text-center text-sm">{t(lang, "emptyPrompt")}</p>
        ) : null}
      </div>
    </div>
  );
}
