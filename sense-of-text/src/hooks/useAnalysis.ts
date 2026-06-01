import { useCallback, useRef, useState } from "react";
import { analyzeWithEmbeddings, terminateEmbeddingWorker } from "@/lib/embeddings/wasm";
import { minMaxNormalize } from "@/lib/math";
import { analyzeWithLlm } from "@/lib/llm/client";
import {
  allModels,
  getModelKind,
  loadSettings,
  resolveModelName,
  type AppSettings,
} from "@/lib/settings";
import {
  exceedsTokenLimit,
  tokenize,
  tokenLimitMessage,
  type Token,
} from "@/lib/tokenize";

export type AnalysisStatus = "idle" | "loading" | "done" | "error";

export function useAnalysis(settings: AppSettings) {
  const [status, setStatus] = useState<AnalysisStatus>("idle");
  const [progress, setProgress] = useState({ current: 0, total: 0 });
  const [progressLabel, setProgressLabel] = useState("");
  const [scores, setScores] = useState<number[]>([]);
  const [tokens, setTokens] = useState<Token[]>([]);
  const [error, setError] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    terminateEmbeddingWorker();
    setStatus("idle");
    setProgress({ current: 0, total: 0 });
    setProgressLabel("");
  }, []);

  const runAnalysis = useCallback(
    async (text: string, selectedModel: string) => {
      setError(null);
      setScores([]);

      const trimmed = text.trim();
      if (!trimmed) {
        setError("Enter some text to analyze.");
        return;
      }

      if (!selectedModel) {
        setError("Select a model.");
        return;
      }

      const currentSettings = loadSettings();
      const kind = getModelKind(selectedModel, currentSettings);
      const modelName = resolveModelName(selectedModel);
      if (!kind || !modelName) {
        setError("Selected model is not in your settings lists.");
        return;
      }

      const tok = tokenize(trimmed);
      if (tok.length === 0) {
        setError("No analyzable tokens found in text.");
        return;
      }
      if (exceedsTokenLimit(tok)) {
        setError(tokenLimitMessage(tok.length));
        return;
      }

      setTokens(tok);
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      setStatus("loading");
      setProgress({ current: 0, total: tok.length + 1 });
      setProgressLabel("Starting…");

      try {
        let result: number[];
        if (kind === "embedding") {
          result = await analyzeWithEmbeddings(
            trimmed,
            tok,
            modelName,
            (current, total, label) => {
              setProgress({ current, total });
              setProgressLabel(label);
            },
            controller.signal,
          );
        } else {
          setProgressLabel("Calling LLM…");
          const raw = await analyzeWithLlm(
            trimmed,
            tok,
            modelName,
            currentSettings,
            controller.signal,
          );
          result = minMaxNormalize(raw);
          setProgress({ current: 1, total: 1 });
        }

        if (controller.signal.aborted) return;

        setScores(result);
        setStatus("done");
      } catch (err) {
        if (err instanceof DOMException && err.name === "AbortError") {
          setStatus("idle");
          return;
        }
        setError(err instanceof Error ? err.message : String(err));
        setStatus("error");
      } finally {
        if (abortRef.current === controller) {
          abortRef.current = null;
        }
      }
    },
    [settings],
  );

  const models = allModels(settings);

  return {
    status,
    progress,
    progressLabel,
    scores,
    tokens,
    error,
    models,
    runAnalysis,
    cancel,
    setError,
  };
}
