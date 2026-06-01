import { useCallback, useEffect, useMemo, useState } from "react";
import { AnalysisControls } from "@/components/AnalysisControls";
import { HighlightedText } from "@/components/HighlightedText";
import { SettingsDialog } from "@/components/SettingsDialog";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { Textarea } from "@/components/ui/textarea";
import { useAnalysis } from "@/hooks/useAnalysis";
import {
  allModels,
  loadSettings,
  type AppSettings,
} from "@/lib/settings";
import {
  exceedsTokenLimit,
  MAX_TOKENS,
  tokenize,
  tokenLimitMessage,
} from "@/lib/tokenize";
import "./index.css";

export function App() {
  const [settings, setSettings] = useState<AppSettings>(() => loadSettings());
  const [text, setText] = useState("");
  const [selectedModel, setSelectedModel] = useState("");

  const {
    status,
    progress,
    progressLabel,
    scores,
    tokens,
    error,
    runAnalysis,
    cancel,
    setError,
  } = useAnalysis(settings);

  const models = useMemo(() => allModels(settings), [settings]);
  const previewTokens = useMemo(() => tokenize(text.trim()), [text]);
  const overLimit = exceedsTokenLimit(previewTokens);

  useEffect(() => {
    if (selectedModel && models.includes(selectedModel)) return;
    const first = models[0];
    if (first) setSelectedModel(first);
    else setSelectedModel("");
  }, [models, selectedModel]);

  const handleAnalyze = useCallback(() => {
    void runAnalysis(text, selectedModel);
  }, [text, selectedModel, runAnalysis]);

  const analyzeDisabled =
    !text.trim() ||
    !selectedModel ||
    overLimit ||
    status === "loading" ||
    models.length === 0;

  return (
    <div className="container mx-auto max-w-3xl p-6">
      <div className="mb-6 flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Sense of Text</h1>
          <p className="text-sm text-muted-foreground">
            Highlight words by how much they shape meaning (embeddings or LLM).
          </p>
        </div>
        <SettingsDialog onSettingsChange={setSettings} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Input</CardTitle>
          <CardDescription>
            Paste text (max {MAX_TOKENS} tokens). Chinese text is split per character (hanzi).
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="input-text">Text</Label>
            <Textarea
              id="input-text"
              placeholder="Paste or type text here…"
              value={text}
              onChange={e => {
                setText(e.target.value);
                setError(null);
              }}
              className="min-h-[160px] resize-y"
              disabled={status === "loading"}
            />
            {text.trim() && (
              <p className={`text-xs ${overLimit ? "text-destructive" : "text-muted-foreground"}`}>
                {overLimit
                  ? tokenLimitMessage(previewTokens.length)
                  : `${previewTokens.length} token${previewTokens.length === 1 ? "" : "s"}`}
              </p>
            )}
          </div>

          <AnalysisControls
            settings={settings}
            selectedModel={selectedModel}
            onModelChange={setSelectedModel}
            onAnalyze={handleAnalyze}
            onCancel={cancel}
            status={status}
            progress={progress}
            progressLabel={progressLabel}
            disabled={analyzeDisabled}
          />

          {error && (
            <p className="text-sm text-destructive" role="alert">
              {error}
            </p>
          )}

          {models.length === 0 && (
            <p className="text-sm text-muted-foreground">
              Add at least one embedding or LLM model in Settings.
            </p>
          )}
        </CardContent>
      </Card>

      {status === "done" && scores.length > 0 && tokens.length > 0 && (
        <>
          <Separator className="my-6" />
          <Card>
            <CardHeader>
              <CardTitle>Highlighted result</CardTitle>
              <CardDescription>
                Stronger highlight = greater impact on meaning when removed.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <HighlightedText text={text.trim()} tokens={tokens} scores={scores} />
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

export default App;
