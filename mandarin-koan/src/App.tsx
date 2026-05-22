import { useCallback, useEffect, useState } from "react";
import { BookOpen, Loader2, Settings2, Sparkles } from "lucide-react";

import { DeployFooter } from "@/components/DeployFooter";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { evaluateClozeAnswer, generateClozeStory, type ClozeVerdict, type FeedbackResult } from "@/lib/koan-llm";
import { DEFAULT_STORY_PROMPT, isSettingsReady, loadSettings, saveSettings, type KoanSettings } from "@/lib/settings";
import { encodeVocabForUrl, parseVocabHash } from "@/lib/vocab-url";

import "./index.css";

type SessionPhase =
  | { kind: "idle" }
  | { kind: "loading_story" }
  | { kind: "story_ready" }
  | { kind: "loading_feedback" }
  | { kind: "feedback_done"; result: FeedbackResult }
  | { kind: "error"; message: string };

function verdictLabel(v: ClozeVerdict): string {
  if (v === "correct") return "Correct";
  if (v === "synonym") return "Synonym";
  return "Clearly wrong";
}

function verdictStyles(v: ClozeVerdict): string {
  if (v === "correct") return "border-emerald-500/50 bg-emerald-500/10 text-emerald-900 dark:text-emerald-100";
  if (v === "synonym") return "border-amber-500/50 bg-amber-500/10 text-amber-950 dark:text-amber-50";
  return "border-destructive/40 bg-destructive/10 text-destructive";
}

function practiceUrlForVocab(vocab: string): string {
  const enc = encodeVocabForUrl(vocab);
  const { origin, pathname } = window.location;
  return `${origin}${pathname}#vocab/${enc}`;
}

export function App() {
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settings, setSettings] = useState<KoanSettings>(() => loadSettings());
  const [linkVocabInput, setLinkVocabInput] = useState("");
  const [copyHint, setCopyHint] = useState<string | null>(null);

  const [targetVocab, setTargetVocab] = useState<string | null>(null);
  const [phase, setPhase] = useState<SessionPhase>({ kind: "idle" });
  const [storyText, setStoryText] = useState<string | null>(null);
  const [guess, setGuess] = useState("");

  const persistSettings = useCallback((next: KoanSettings) => {
    setSettings(next);
    saveSettings(next);
  }, []);

  const applyHash = useCallback((hash: string) => {
    const parsed = parseVocabHash(hash);
    if (!parsed) {
      setTargetVocab(null);
      setPhase({ kind: "idle" });
      setStoryText(null);
      setGuess("");
      return;
    }
    if (typeof window !== "undefined" && window.location.hash !== parsed.canonicalHash) {
      window.history.replaceState(
        null,
        "",
        `${window.location.pathname}${window.location.search}${parsed.canonicalHash}`,
      );
    }
    setTargetVocab(parsed.vocab);
    setPhase({ kind: "idle" });
    setStoryText(null);
    setGuess("");
  }, []);

  useEffect(() => {
    applyHash(window.location.hash);
    const onHash = () => applyHash(window.location.hash);
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, [applyHash]);

  const loadStory = useCallback(async () => {
    if (!targetVocab) return;
    if (!isSettingsReady(settings)) {
      setPhase({ kind: "error", message: "Fill in the API base URL and key in Settings first." });
      return;
    }
    setPhase({ kind: "loading_story" });
    try {
      const { story } = await generateClozeStory({ settings, targetVocab });
      setStoryText(story);
      setPhase({ kind: "story_ready" });
    } catch (e) {
      setStoryText(null);
      setPhase({ kind: "error", message: e instanceof Error ? e.message : "Failed to generate the story." });
    }
  }, [settings, targetVocab]);

  useEffect(() => {
    if (!targetVocab) return;
    void loadStory();
  }, [targetVocab, loadStory]);

  const submitGuess = useCallback(async () => {
    if (!targetVocab || !storyText) return;
    if (phase.kind !== "story_ready" && phase.kind !== "feedback_done") return;
    const g = guess.trim();
    if (!g) return;
    if (!isSettingsReady(settings)) {
      setPhase({ kind: "error", message: "Fill in the API base URL and key in Settings first." });
      return;
    }
    setPhase({ kind: "loading_feedback" });
    try {
      const result = await evaluateClozeAnswer({
        settings,
        targetVocab,
        storyWithBlank: storyText,
        userAnswer: g,
      });
      setPhase({ kind: "feedback_done", result });
    } catch (e) {
      setPhase({ kind: "error", message: e instanceof Error ? e.message : "Failed to grade your answer." });
    }
  }, [guess, phase.kind, settings, storyText, targetVocab]);

  const resetRound = useCallback(() => {
    setGuess("");
    void loadStory();
  }, [loadStory]);

  const goPractice = useCallback(() => {
    const v = linkVocabInput.trim();
    if (!v) return;
    window.location.hash = `vocab/${encodeVocabForUrl(v)}`;
  }, [linkVocabInput]);

  const copyPracticeLink = useCallback(async () => {
    const v = linkVocabInput.trim();
    if (!v) return;
    const url = practiceUrlForVocab(v);
    try {
      await navigator.clipboard.writeText(url);
      setCopyHint("Link copied.");
    } catch {
      setCopyHint("Copy failed — copy the URL from the address bar manually.");
    }
    window.setTimeout(() => setCopyHint(null), 2500);
  }, [linkVocabInput]);

  const showStory =
    storyText &&
    (phase.kind === "story_ready" || phase.kind === "loading_feedback" || phase.kind === "feedback_done");
  const showGuessForm =
    Boolean(storyText) && (phase.kind === "story_ready" || phase.kind === "loading_feedback");
  const storyBusy = phase.kind === "loading_story";
  const feedbackBusy = phase.kind === "loading_feedback";

  return (
    <div className="mx-auto min-h-screen max-w-2xl px-4 py-10">
      <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Mandarin Koan</h1>
          <p className="text-muted-foreground mt-2 max-w-xl text-sm leading-relaxed">
            The model writes a short Mandarin story and turns one word into a blank (___). When you open a link with{" "}
            <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">#vocab/</code>
            , the target word stays out of the address bar in plain text (Base64url-encoded fragment).
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
            <CardTitle>API and prompts</CardTitle>
            <CardDescription>
              Same idea as Socratus: stored only in this browser&apos;s{" "}
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">localStorage</code>. Use an OpenAI-compatible
              base URL (for example{" "}
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">https://api.openai.com</code>
              ).
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
            <div className="grid gap-2">
              <Label htmlFor="story-prompt">Story prompt (use {"{{VOCAB}}"} for the hidden word)</Label>
              <Textarea
                id="story-prompt"
                rows={12}
                className="min-h-[220px] font-mono text-xs leading-relaxed"
                value={settings.storyPrompt}
                onChange={e => persistSettings({ ...settings, storyPrompt: e.target.value })}
              />
              <p className="text-muted-foreground text-xs leading-relaxed">
                The model must return JSON:{" "}
                <code className="rounded bg-muted px-1 py-0.5 font-mono text-[11px]">{"{ \"story\": \"...___...\" }"}</code>
              </p>
            </div>
            <Button type="button" variant="secondary" size="sm" className="w-fit" onClick={() => persistSettings({ ...settings, storyPrompt: DEFAULT_STORY_PROMPT })}>
              Reset story prompt to default
            </Button>
          </CardContent>
        </Card>
      ) : null}

      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Sparkles className="size-5" />
              Start practice
            </CardTitle>
            <CardDescription>Enter a target word, start the cloze, or copy an encoded practice link.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3">
            <div className="grid gap-2">
              <Label htmlFor="vocab-link">Target word (often Chinese)</Label>
              <Input
                id="vocab-link"
                placeholder="Type the word to practice"
                value={linkVocabInput}
                onChange={e => setLinkVocabInput(e.target.value)}
              />
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={goPractice} disabled={!linkVocabInput.trim()}>
                Open cloze
              </Button>
              <Button type="button" variant="outline" onClick={() => void copyPracticeLink()} disabled={!linkVocabInput.trim()}>
                Copy encoded link
              </Button>
            </div>
            {copyHint ? <p className="text-muted-foreground text-sm">{copyHint}</p> : null}
            {!isSettingsReady(settings) ? (
              <p className="text-amber-600 text-sm dark:text-amber-400">Open Settings and add your API details before generating a story.</p>
            ) : null}
          </CardContent>
        </Card>

        {targetVocab ? (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <BookOpen className="size-5" />
                Cloze practice
              </CardTitle>
              <CardDescription>The practice word is encoded in the URL; it is not shown here.</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4">
              {phase.kind === "error" ? (
                <div className="grid gap-2">
                  <p className="text-destructive text-sm">{phase.message}</p>
                  <Button type="button" variant="secondary" size="sm" className="w-fit" onClick={() => void loadStory()}>
                    Retry generation
                  </Button>
                </div>
              ) : null}

              {storyBusy ? (
                <p className="text-muted-foreground flex items-center gap-2 text-sm">
                  <Loader2 className="size-4 animate-spin" />
                  Generating story…
                </p>
              ) : null}

              {showStory && storyText ? (
                <div>
                  <p className="text-muted-foreground text-xs font-medium tracking-wide uppercase">Story</p>
                  <div className="bg-muted/40 mt-2 rounded-lg border p-4 text-lg leading-relaxed tracking-wide">
                    {storyText}
                  </div>
                </div>
              ) : null}

              {showGuessForm ? (
                <div className="grid gap-2">
                  <Label htmlFor="guess">Your answer</Label>
                  <Input
                    id="guess"
                    value={guess}
                    onChange={e => setGuess(e.target.value)}
                    placeholder="Type the word you think fits…"
                    disabled={feedbackBusy}
                    onKeyDown={e => {
                      if (e.key === "Enter") void submitGuess();
                    }}
                  />
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" onClick={() => void submitGuess()} disabled={!guess.trim() || feedbackBusy}>
                      Submit and get feedback
                    </Button>
                    <Button type="button" variant="outline" onClick={resetRound} disabled={storyBusy || feedbackBusy}>
                      New story
                    </Button>
                  </div>
                </div>
              ) : null}

              {feedbackBusy ? (
                <p className="text-muted-foreground flex items-center gap-2 text-sm">
                  <Loader2 className="size-4 animate-spin" />
                  Grading…
                </p>
              ) : null}

              {phase.kind === "feedback_done" ? (
                <div className={`rounded-lg border p-4 ${verdictStyles(phase.result.verdict)}`}>
                  <p className="text-xs font-semibold tracking-wide uppercase">{verdictLabel(phase.result.verdict)}</p>
                  <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed">{phase.result.feedback}</p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Button type="button" variant="secondary" size="sm" onClick={resetRound}>
                      Practice again (new story)
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        setGuess("");
                        setPhase({ kind: "story_ready" });
                      }}
                    >
                      Guess again
                    </Button>
                  </div>
                </div>
              ) : null}
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>No word selected yet</CardTitle>
              <CardDescription>
                Enter a word above and click &quot;Open cloze&quot;, or open a link that includes <code className="rounded bg-muted px-1 py-0.5 font-mono text-xs">#vocab/</code>.
              </CardDescription>
            </CardHeader>
          </Card>
        )}

        <DeployFooter />
      </div>
    </div>
  );
}

export default App;
