import { useCallback, useEffect, useMemo, useState } from "react";
import { BookOpen, Loader2, Settings2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { parseEpub, type EpubChapter } from "@/lib/epub";
import { loadSettings, saveSettings, type UserSettings } from "@/lib/settings";
import {
  compareAnswer,
  detectBlockEnd,
  generateQA,
  initialSessionState,
  type SessionState,
} from "@/lib/socratic";
import {
  deleteChapterRecord,
  loadChapterRecord,
  makeBookPersistenceId,
  nextPersistedChunkIndex,
  saveChunkBounds,
  saveCompletedRound,
  type ChapterPersisted,
} from "@/lib/socratic-db";

import "./index.css";

function isSettingsReady(s: UserSettings): boolean {
  return Boolean(s.apiBaseUrl.trim() && s.apiKey.trim());
}

export function App() {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settings, setSettings] = useState<UserSettings>(() => loadSettings());
  const [chapters, setChapters] = useState<EpubChapter[]>([]);
  const [bookTitle, setBookTitle] = useState<string>("");
  const [selectedChapterId, setSelectedChapterId] = useState<string>("");
  const [parseError, setParseError] = useState<string | null>(null);
  const [uploadBusy, setUploadBusy] = useState(false);
  const [currentBookId, setCurrentBookId] = useState<string | null>(null);
  const [persistedChapter, setPersistedChapter] = useState<ChapterPersisted | null>(null);

  const [session, setSession] = useState<SessionState | null>(null);
  const [userAnswer, setUserAnswer] = useState("");

  const selectedChapter = useMemo(
    () => chapters.find(c => c.id === selectedChapterId) ?? null,
    [chapters, selectedChapterId],
  );

  useEffect(() => {
    if (!currentBookId || !selectedChapterId) {
      setPersistedChapter(null);
      return;
    }
    let cancelled = false;
    void loadChapterRecord(currentBookId, selectedChapterId).then(rec => {
      if (!cancelled) setPersistedChapter(rec);
    });
    return () => {
      cancelled = true;
    };
  }, [currentBookId, selectedChapterId]);

  const persistSettings = useCallback((next: UserSettings) => {
    setSettings(next);
    saveSettings(next);
  }, []);

  const handleEpub = async (file: File | null) => {
    if (!file) return;
    setParseError(null);
    setUploadBusy(true);
    setChapters([]);
    setSelectedChapterId("");
    setSession(null);
    setCurrentBookId(makeBookPersistenceId(file.name, file.size));
    setBookTitle(file.name.replace(/\.epub$/i, ""));
    try {
      const list = await parseEpub(file);
      setChapters(list);
      setSelectedChapterId(list[0]?.id ?? "");
    } catch (e) {
      setParseError(e instanceof Error ? e.message : "Failed to read EPUB");
    } finally {
      setUploadBusy(false);
    }
  };

  const runBlockPipeline = useCallback(
    async (base: SessionState) => {
      const { apiBaseUrl, apiKey, model } = settings;
      if (!isSettingsReady(settings)) {
        setSession({
          ...base,
          phase: { kind: "error", message: "Configure API base URL and API key in Settings first." },
        });
        return;
      }

      try {
        setSession({ ...base, phase: { kind: "loading_block" } });
        const p = base.persistence;

        if (p) {
          const record = await loadChapterRecord(p.bookId, p.chapterId);
          const reuseIdx = nextPersistedChunkIndex(record);
          if (record && reuseIdx < record.chunks.length) {
            const bounds = record.chunks[reuseIdx];
            if (bounds) {
              const block = base.chapterText.slice(bounds.start, bounds.end).trim();
              if (block) {
                setSession({
                  ...base,
                  phase: { kind: "loading_qa" },
                  currentBlock: block,
                  persistence: { ...p, chunkIndex: reuseIdx },
                });

                const qa = await generateQA({
                  baseUrl: apiBaseUrl,
                  apiKey,
                  model,
                  passage: block,
                });

                setSession({
                  ...base,
                  phase: { kind: "llm_question", question: qa.question },
                  currentBlock: block,
                  currentQA: qa,
                  pendingNextCursor: bounds.end,
                  persistence: { ...p, chunkIndex: reuseIdx },
                });
                return;
              }
            }
          }
        }

        const { block, nextCursor } = await detectBlockEnd({
          baseUrl: apiBaseUrl,
          apiKey,
          model,
          chapterText: base.chapterText,
          cursor: base.cursor,
        });

        if (!block.trim()) {
          setSession({
            ...base,
            phase: { kind: "chapter_done" },
          });
          return;
        }

        let chunkIndexForSession = 0;
        if (p) {
          const recordAfter = await loadChapterRecord(p.bookId, p.chapterId);
          chunkIndexForSession = recordAfter?.chunks.length ?? 0;
          await saveChunkBounds(p.bookId, p.chapterId, chunkIndexForSession, {
            start: base.cursor,
            end: nextCursor,
          });
        }

        setSession({
          ...base,
          phase: { kind: "loading_qa" },
          currentBlock: block,
          persistence: p ? { ...p, chunkIndex: chunkIndexForSession } : null,
        });

        const qa = await generateQA({
          baseUrl: apiBaseUrl,
          apiKey,
          model,
          passage: block,
        });

        setSession({
          ...base,
          phase: { kind: "llm_question", question: qa.question },
          currentBlock: block,
          currentQA: qa,
          pendingNextCursor: nextCursor,
          persistence: p ? { ...p, chunkIndex: chunkIndexForSession } : null,
        });
      } catch (e) {
        setSession({
          ...base,
          phase: { kind: "error", message: e instanceof Error ? e.message : "Unknown error" },
        });
      }
    },
    [settings],
  );

  const startSession = () => {
    if (!selectedChapter) return;
    const persistence =
      currentBookId !== null
        ? { bookId: currentBookId, chapterId: selectedChapter.id, chunkIndex: 0 }
        : null;
    void runBlockPipeline(initialSessionState(selectedChapter.text, persistence));
  };

  const pipelineLoading =
    session?.phase.kind === "loading_block" ||
    session?.phase.kind === "loading_qa" ||
    session?.phase.kind === "loading_feedback";

  const canStartSession =
    Boolean(selectedChapter) &&
    isSettingsReady(settings) &&
    (!session || session.phase.kind === "error" || session.phase.kind === "chapter_done");

  const submitAnswer = async () => {
    if (!session?.currentQA || !session.currentBlock) return;
    const answer = userAnswer.trim();
    if (!answer) return;

    const { apiBaseUrl, apiKey, model } = settings;
    setSession({ ...session, phase: { kind: "loading_feedback" } });

    try {
      const feedback = await compareAnswer({
        baseUrl: apiBaseUrl,
        apiKey,
        model,
        question: session.currentQA.question,
        correctAnswer: session.currentQA.correctAnswer,
        userAnswer: answer,
        passage: session.currentBlock,
        includeLlmOpinion: settings.includeLlmOpinion,
      });

      const persist = session.persistence;
      if (persist && session.currentQA) {
        await saveCompletedRound(persist.bookId, persist.chapterId, persist.chunkIndex, {
          question: session.currentQA.question,
          correctAnswer: session.currentQA.correctAnswer,
          userAnswer: answer,
          passageGroundedAnalysis: feedback.passageGroundedAnalysis,
          llmOpinion: feedback.llmOpinion,
        });
        void loadChapterRecord(persist.bookId, persist.chapterId).then(setPersistedChapter);
      }

      setSession({
        ...session,
        phase: {
          kind: "show_passage",
          passage: session.currentBlock,
          passageGroundedAnalysis: feedback.passageGroundedAnalysis,
          llmOpinion: feedback.llmOpinion,
        },
      });
      setUserAnswer("");
    } catch (e) {
      setSession({
        ...session,
        phase: { kind: "error", message: e instanceof Error ? e.message : "Unknown error" },
      });
    }
  };

  const continueAfterPassage = () => {
    if (!session) return;
    const nextCursor = session.pendingNextCursor ?? session.cursor;
    if (nextCursor >= session.chapterText.length) {
      setSession({ ...session, cursor: nextCursor, phase: { kind: "chapter_done" }, currentBlock: null, currentQA: null });
      return;
    }

    const next: SessionState = {
      ...session,
      cursor: nextCursor,
      currentBlock: null,
      currentQA: null,
      pendingNextCursor: undefined,
      persistence: session.persistence,
    };
    void runBlockPipeline(next);
  };

  const resetSession = () => {
    if (currentBookId && selectedChapterId) {
      setPersistedChapter(null);
      void deleteChapterRecord(currentBookId, selectedChapterId);
    }
    setSession(null);
    setUserAnswer("");
  };

  const phase = session?.phase;

  return (
    <div className="min-h-screen w-full max-w-3xl mx-auto px-4 py-10 text-left relative z-10">
      <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Socratic Reading Agent</h1>
          <p className="text-muted-foreground mt-2 max-w-xl text-sm leading-relaxed">
            <span className="font-medium text-foreground">Socratus</span> is your reading tutor—an AI-assisted helper
            that runs entirely in your browser. Bring your own API key and OpenAI-compatible endpoint, upload an EPUB,
            pick a chapter, and answer Socratus&apos;s questions <em>before</em> each passage is revealed.
          </p>
        </div>
        <Button variant="outline" size="sm" className="shrink-0 gap-2" onClick={() => setSettingsOpen(o => !o)}>
          <Settings2 className="size-4" />
          Settings
        </Button>
      </header>

      {settingsOpen ? (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle>API configuration</CardTitle>
            <CardDescription>
              Stored only in this browser&apos;s <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">localStorage</code>.
              Use an OpenAI-compatible base URL (for example{" "}
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">https://api.openai.com</code>).
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="grid gap-2">
              <Label htmlFor="base">Base URL</Label>
              <Input
                id="base"
                placeholder="https://api.openai.com"
                value={settings.apiBaseUrl}
                onChange={e => persistSettings({ ...settings, apiBaseUrl: e.target.value })}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="key">API key</Label>
              <Input
                id="key"
                type="password"
                autoComplete="off"
                placeholder="sk-…"
                value={settings.apiKey}
                onChange={e => persistSettings({ ...settings, apiKey: e.target.value })}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="model">Model</Label>
              <Input
                id="model"
                placeholder="gpt-4o-mini"
                value={settings.model}
                onChange={e => persistSettings({ ...settings, model: e.target.value })}
              />
            </div>
            <div className="flex items-start gap-3 rounded-lg border border-border/80 bg-muted/30 p-3">
              <input
                id="llm-opinion"
                type="checkbox"
                className="mt-1 size-4 shrink-0 rounded border-input accent-primary"
                checked={settings.includeLlmOpinion}
                onChange={e => persistSettings({ ...settings, includeLlmOpinion: e.target.checked })}
              />
              <div className="grid gap-1">
                <Label htmlFor="llm-opinion" className="font-medium leading-snug">
                  Include Socratus&apos;s own opinion
                </Label>
                <p className="text-muted-foreground text-xs leading-relaxed">
                  When enabled, feedback adds a <strong className="text-foreground font-medium">separate</strong> section
                  with Socratus&apos;s personal or pedagogical view. The main analysis stays grounded in the book passage
                  only.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <BookOpen className="size-5" />
              Book
            </CardTitle>
            <CardDescription>Upload a reflowable EPUB. Chapters follow the book spine.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="grid gap-2">
              <Label htmlFor="epub">EPUB file</Label>
              <Input
                id="epub"
                type="file"
                accept=".epub,application/epub+zip"
                disabled={uploadBusy}
                onChange={e => void handleEpub(e.target.files?.[0] ?? null)}
              />
              {uploadBusy ? (
                <p className="text-muted-foreground flex items-center gap-2 text-sm">
                  <Loader2 className="size-4 animate-spin" />
                  Reading book…
                </p>
              ) : null}
              {parseError ? <p className="text-destructive text-sm">{parseError}</p> : null}
            </div>

            {chapters.length > 0 ? (
              <div className="grid gap-2">
                <Label>Chapter</Label>
                <Select value={selectedChapterId} onValueChange={setSelectedChapterId}>
                  <SelectTrigger className="w-full max-w-full">
                    <SelectValue placeholder="Choose chapter" />
                  </SelectTrigger>
                  <SelectContent>
                    {chapters.map(c => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-muted-foreground text-xs">
                  Loaded: <span className="font-medium text-foreground">{bookTitle}</span> · {chapters.length}{" "}
                  sections
                </p>
                {persistedChapter && persistedChapter.chunks.length > 0 ? (
                  <p className="text-muted-foreground text-xs">
                    Saved in this browser: {persistedChapter.chunks.length} segment
                    {persistedChapter.chunks.length === 1 ? "" : "s"}
                    {persistedChapter.chunkRounds.some(r => r.length > 0)
                      ? ` · ${persistedChapter.chunkRounds.reduce((n, r) => n + r.length, 0)} completed Q/A round(s)`
                      : null}
                    .
                  </p>
                ) : null}
              </div>
            ) : null}

            <div className="flex flex-wrap gap-2">
              <Button onClick={startSession} disabled={!canStartSession || pipelineLoading}>
                Start session
              </Button>
              <Button variant="outline" onClick={resetSession} disabled={!session}>
                Reset session
              </Button>
            </div>
            {!isSettingsReady(settings) ? (
              <p className="text-amber-600 text-sm dark:text-amber-400">Open Settings and add your API credentials to begin.</p>
            ) : null}
          </CardContent>
        </Card>

        {session ? (
          <Card>
            <CardHeader>
            <CardTitle>Session with Socratus</CardTitle>
            <CardDescription>
              {selectedChapter?.title ?? "Chapter"} · position {Math.min(session.cursor, session.chapterText.length)} /{" "}
              {session.chapterText.length} characters
            </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4">
              {phase?.kind === "loading_block" || phase?.kind === "loading_qa" || phase?.kind === "loading_feedback" ? (
                <div className="text-muted-foreground flex items-center gap-2 text-sm">
                  <Loader2 className="size-4 animate-spin" />
                  {phase.kind === "loading_block"
                    ? "Finding the next reading segment…"
                    : phase.kind === "loading_qa"
                      ? "Socratus is preparing a pre-reading question…"
                      : "Socratus is reflecting on your answer…"}
                </div>
              ) : null}

              {phase?.kind === "error" ? <p className="text-destructive text-sm">{phase.message}</p> : null}

              {phase?.kind === "chapter_done" ? (
                <p className="text-sm">You reached the end of this chapter&apos;s guided flow.</p>
              ) : null}

              {phase?.kind === "llm_question" ? (
                <div className="grid gap-3">
                  <div>
                    <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">Socratus</p>
                    <p className="text-muted-foreground mt-0.5 text-xs">
                      Answer from your own reasoning—you have not seen this passage yet.
                    </p>
                    <p className="mt-2 text-base leading-relaxed">{phase.question}</p>
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="answer">Your answer</Label>
                    <Textarea
                      id="answer"
                      rows={4}
                      value={userAnswer}
                      onChange={e => setUserAnswer(e.target.value)}
                      placeholder="Type your answer…"
                    />
                  </div>
                  <Button onClick={() => void submitAnswer()} disabled={!userAnswer.trim()}>
                    Submit answer
                  </Button>
                </div>
              ) : null}

              {phase?.kind === "show_passage" ? (
                <div className="grid gap-4">
                  <div>
                    <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
                      Analysis vs. the text
                    </p>
                    <div className="prose prose-sm dark:prose-invert mt-1 max-w-none whitespace-pre-wrap text-sm leading-relaxed">
                      {phase.passageGroundedAnalysis}
                    </div>
                  </div>
                  {phase.llmOpinion ? (
                    <div className="border-t pt-4">
                      <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
                        Socratus&apos;s own view
                      </p>
                      <p className="text-muted-foreground mt-1 text-xs italic">
                        Optional perspective you enabled in settings—not a substitute for what the passage says.
                      </p>
                      <div className="prose prose-sm dark:prose-invert mt-2 max-w-none whitespace-pre-wrap text-sm leading-relaxed">
                        {phase.llmOpinion}
                      </div>
                    </div>
                  ) : null}
                  <div>
                    <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">Passage</p>
                    <div className="bg-muted/40 mt-2 max-h-[28rem] overflow-y-auto rounded-lg border p-4 text-sm leading-relaxed whitespace-pre-wrap">
                      {phase.passage}
                    </div>
                  </div>
                  <Button onClick={continueAfterPassage}>Continue</Button>
                </div>
              ) : null}
            </CardContent>
          </Card>
        ) : null}
      </div>
    </div>
  );
}

export default App;
