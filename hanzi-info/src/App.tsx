import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BookOpen, Copy, Link2, Loader2, Search } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  canonicalHanziHash,
  firstGrapheme,
  loadHanziDatabase,
  parseHanziHash,
  radicalsForCharacter,
} from "@/lib/hanzi-db";
import type { HanziDatabase, HanziRow, PhoneticSeries } from "@/lib/hanzi-types";

import "./index.css";

function regularityLabel(scale: 1 | 2): string {
  return scale === 1
    ? "Closer phonetic family (HanziJS regularity_one — same source tier as HanziCraft set 1)"
    : "Broader phonetic family (HanziJS regularity_two — same source tier as HanziCraft set 2)";
}

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

function MemberCloud({ members }: { members: string[] }) {
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
          Showing {preview.length} of {members.length} characters.
        </p>
      ) : null}
    </div>
  );
}

function RowDetails({ row }: { row: HanziRow }) {
  return (
    <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-2">
      <div>
        <dt className="text-muted-foreground">Type</dt>
        <dd className="font-medium">{row.type}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">Reading</dt>
        <dd className="font-medium">{row.reading || "—"}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">Initial · final · tone</dt>
        <dd className="font-medium">
          {row.initiale || "—"} · {row.finale || "—"} · {row.tone > 0 ? row.tone : "—"}
        </dd>
      </div>
      <div className="sm:col-span-2">
        <dt className="text-muted-foreground">English gloss</dt>
        <dd className="text-pretty">{row.meaning_en || "—"}</dd>
      </div>
      {row.meaning_ru ? (
        <div className="sm:col-span-2">
          <dt className="text-muted-foreground">Russian gloss</dt>
          <dd className="text-pretty">{row.meaning_ru}</dd>
        </div>
      ) : null}
    </dl>
  );
}

function PhoneticSeriesCard({ db, series }: { db: HanziDatabase; series: PhoneticSeries }) {
  const compId = db.by_hanzi[series.component];
  const compRow = compId !== undefined ? db.hanzi[compId - 1] : undefined;
  return (
    <div className="rounded-xl border border-primary/25 bg-primary/5 p-4 shadow-sm">
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <span className="text-muted-foreground text-xs uppercase tracking-wide">Phonetic anchor</span>
        <span className="font-serif text-4xl leading-none">
          <Glyph ch={series.component} />
        </span>
        {compRow ? (
          <span className="text-muted-foreground text-sm">
            {compRow.reading ? `${compRow.reading} · ` : ""}
            {compRow.meaning_en ? compRow.meaning_en.slice(0, 80) + (compRow.meaning_en.length > 80 ? "…" : "") : ""}
          </span>
        ) : null}
      </div>
      <p className="text-muted-foreground mb-2 text-xs">{regularityLabel(series.regularity_scale)}</p>
      <p className="text-muted-foreground mb-2 font-mono text-[0.7rem]">set_key: {series.set_key}</p>
      <p className="mb-2 text-xs font-medium">Series members</p>
      <MemberCloud members={series.members} />
    </div>
  );
}

export function App() {
  const [db, setDb] = useState<HanziDatabase | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState<string | null>(null);
  const [copyHint, setCopyHint] = useState<string | null>(null);

  useEffect(() => {
    loadHanziDatabase()
      .then(setDb)
      .catch(e => setLoadError(e instanceof Error ? e.message : String(e)));
  }, []);

  const applyHash = useCallback((hash: string) => {
    const raw = parseHanziHash(hash);
    const g = raw ? firstGrapheme(raw) : null;
    setActive(g);
    setQuery(g ?? "");
    if (g) {
      const want = canonicalHanziHash(g);
      if (typeof window !== "undefined" && window.location.hash !== want) {
        window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}${want}`);
      }
    }
  }, []);

  useEffect(() => {
    applyHash(window.location.hash);
    const onHash = () => applyHash(window.location.hash);
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, [applyHash]);

  const activeRow = useMemo(() => {
    if (!db || !active) return undefined;
    const id = db.by_hanzi[active];
    if (id === undefined) return undefined;
    return db.hanzi[id - 1];
  }, [db, active]);

  const phonetic = db && active ? db.phonetic_series_by_hanzi[active] : undefined;
  const radicalParts = db && active ? radicalsForCharacter(db, active) : [];

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    const g = firstGrapheme(query);
    if (!g) return;
    window.location.hash = canonicalHanziHash(g);
  };

  const shareUrl = useMemo(() => {
    if (!active) return "";
    const { origin, pathname } = window.location;
    return `${origin}${pathname}${canonicalHanziHash(active)}`;
  }, [active]);

  const copyShare = async () => {
    if (!shareUrl) return;
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopyHint("Link copied.");
      setTimeout(() => setCopyHint(null), 2000);
    } catch {
      setCopyHint("Could not copy automatically.");
      setTimeout(() => setCopyHint(null), 2500);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-background to-muted/40">
      <div className="mx-auto flex max-w-3xl flex-col gap-8 px-4 py-10">
        <header className="space-y-2 text-center sm:text-left">
          <div className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card px-3 py-1 text-xs text-muted-foreground shadow-sm">
            <BookOpen className="size-3.5" aria-hidden />
            Hanzi phonetic lookup
          </div>
          <h1 className="font-serif text-3xl font-semibold tracking-tight sm:text-4xl">Hanzi Info</h1>
          <p className="text-muted-foreground max-w-2xl text-pretty text-sm leading-relaxed">
            Explore which phonetic component ties a character to its sound family, using the same HanziJS phonetic-set
            lists that power{" "}
            <a
              className="text-primary underline-offset-4 hover:underline"
              href="https://hanzicraft.com/lists/phonetic-set"
            >
              HanziCraft&apos;s phonetic set
            </a>
            . English glosses come from CC-CEDICT via HanziJS.
          </p>
        </header>

        <Card>
          <CardHeader>
            <CardTitle className="font-serif text-xl">Look up a character</CardTitle>
            <CardDescription>
              Type one Hanzi or open a shareable URL such as <code className="text-xs">#/hanzi/我</code>.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-3 sm:flex-row" onSubmit={onSearch}>
              <div className="flex flex-1 flex-col gap-2">
                <Label htmlFor="hanzi-input">Character</Label>
                <Input
                  id="hanzi-input"
                  placeholder="例如：清"
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
                  Search
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        {loadError ? (
          <Card className="border-destructive/50 bg-destructive/5">
            <CardHeader>
              <CardTitle>Could not load database</CardTitle>
              <CardDescription>{loadError}</CardDescription>
            </CardHeader>
          </Card>
        ) : null}

        {!db && !loadError ? (
          <div className="text-muted-foreground flex items-center justify-center gap-2 py-16 text-sm">
            <Loader2 className="size-5 animate-spin" />
            Loading dictionary…
          </div>
        ) : null}

        {db && active && !activeRow ? (
          <Card>
            <CardHeader>
              <CardTitle className="font-serif text-2xl">Not in local index</CardTitle>
              <CardDescription>
                <Glyph ch={active} className="text-3xl" /> is outside the bundled coverage (HanziJS frequency list plus
                phonetic-set participants and their decomposition parts).
              </CardDescription>
            </CardHeader>
          </Card>
        ) : null}

        {db && activeRow ? (
          <div className="flex flex-col gap-6">
            <Card>
              <CardHeader className="flex flex-row items-start justify-between gap-4">
                <div>
                  <CardTitle className="font-serif text-5xl font-normal leading-none">
                    <Glyph ch={activeRow.hanzi} />
                  </CardTitle>
                  <CardDescription className="mt-3">Identifier #{activeRow.id}</CardDescription>
                </div>
                <div className="flex shrink-0 flex-col items-stretch gap-2 sm:items-end">
                  <Button type="button" variant="outline" size="sm" onClick={copyShare} className="gap-1.5">
                    <Copy className="size-3.5" />
                    Copy link
                  </Button>
                  {copyHint ? <span className="text-muted-foreground text-xs">{copyHint}</span> : null}
                  <span className="text-muted-foreground flex items-center gap-1 text-xs break-all">
                    <Link2 className="size-3 shrink-0" />
                    {shareUrl}
                  </span>
                </div>
              </CardHeader>
              <CardContent className="space-y-6">
                <RowDetails row={activeRow} />

                <div className="border-t pt-6">
                  <h3 className="mb-2 text-sm font-semibold">Phonetic component</h3>
                  {!phonetic || phonetic.length === 0 ? (
                    <p className="text-muted-foreground text-sm leading-relaxed">
                      No phonetic component in the HanziCraft phonetic-set index for this character. The character is
                      treated as <strong>ideographic</strong> here (sound-shape link not listed in HanziJS phonetic
                      sets).
                    </p>
                  ) : (
                    <div className="flex flex-col gap-6">
                      {phonetic.map(s => (
                        <PhoneticSeriesCard key={`${s.set_key}-${s.regularity_scale}`} db={db} series={s} />
                      ))}
                    </div>
                  )}
                </div>

                <div className="border-t pt-6">
                  <h3 className="mb-2 text-sm font-semibold">Structural parts (IDS decomposition)</h3>
                  {radicalParts.length === 0 ? (
                    <p className="text-muted-foreground text-sm">No separate parts returned for this character.</p>
                  ) : (
                    <ul className="flex flex-col gap-3">
                      {radicalParts.map(r => (
                        <li
                          key={r.id}
                          className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg border border-border/70 bg-muted/20 px-3 py-2"
                        >
                          <span className="font-serif text-2xl">
                            <Glyph ch={r.hanzi} />
                          </span>
                          <span className="text-muted-foreground text-xs">
                            {r.type}
                            {r.reading ? ` · ${r.reading}` : ""}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                  <p className="text-muted-foreground mt-3 text-xs leading-relaxed">
                    These pieces come from HanziJS <code className="text-[0.7rem]">decompose()</code> (graphical /
                    radical-style split). They are linked in the <code className="text-[0.7rem]">hanzi2radicals</code>{" "}
                    table as perspective schema support.
                  </p>
                </div>
              </CardContent>
            </Card>

            <p className="text-muted-foreground text-center text-xs leading-relaxed">{db.about}</p>
          </div>
        ) : null}

        {db && !active ? (
          <p className="text-muted-foreground text-center text-sm">Enter a character above to begin.</p>
        ) : null}
      </div>
    </div>
  );
}
