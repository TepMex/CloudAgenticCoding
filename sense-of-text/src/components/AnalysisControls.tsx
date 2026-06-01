import { Loader2Icon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Progress } from "@/components/ui/progress";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { AnalysisStatus } from "@/hooks/useAnalysis";
import {
  allModelOptions,
  getModelKind,
  parseModelOptionId,
  type AppSettings,
} from "@/lib/settings";

type AnalysisControlsProps = {
  settings: AppSettings;
  selectedModel: string;
  onModelChange: (model: string) => void;
  onAnalyze: () => void;
  onCancel: () => void;
  status: AnalysisStatus;
  progress: { current: number; total: number };
  progressLabel: string;
  disabled: boolean;
};

export function AnalysisControls({
  settings,
  selectedModel,
  onModelChange,
  onAnalyze,
  onCancel,
  status,
  progress,
  progressLabel,
  disabled,
}: AnalysisControlsProps) {
  const isLoading = status === "loading";
  const progressPercent =
    progress.total > 0 ? Math.round((progress.current / progress.total) * 100) : 0;
  const modelOptions = allModelOptions(settings);
  const kind = selectedModel ? getModelKind(selectedModel, settings) : null;
  const selectedLabel = selectedModel
    ? (parseModelOptionId(selectedModel)?.model ?? selectedModel)
    : undefined;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex min-w-[200px] flex-1 flex-col gap-2">
          <Label htmlFor="model">Model</Label>
          <Select value={selectedModel} onValueChange={onModelChange} disabled={isLoading}>
            <SelectTrigger id="model" className="w-full">
              <SelectValue placeholder="Select model">{selectedLabel}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {modelOptions.some(o => o.kind === "embedding") && (
                <SelectGroup>
                  <SelectLabel>Embeddings (WASM)</SelectLabel>
                  {modelOptions
                    .filter(o => o.kind === "embedding")
                    .map(o => (
                      <SelectItem key={o.id} value={o.id}>
                        {o.model}
                      </SelectItem>
                    ))}
                </SelectGroup>
              )}
              {modelOptions.some(o => o.kind === "llm") && (
                <SelectGroup>
                  <SelectLabel>LLM</SelectLabel>
                  {modelOptions
                    .filter(o => o.kind === "llm")
                    .map(o => (
                      <SelectItem key={o.id} value={o.id}>
                        {o.model}
                      </SelectItem>
                    ))}
                </SelectGroup>
              )}
            </SelectContent>
          </Select>
        </div>
        {kind && (
          <Badge variant="secondary">{kind === "embedding" ? "WASM" : "LLM"}</Badge>
        )}
        {isLoading ? (
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
        ) : (
          <Button type="button" onClick={onAnalyze} disabled={disabled}>
            Analyze
          </Button>
        )}
      </div>
      {isLoading && (
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2Icon className="animate-spin" />
            {progressLabel}
          </div>
          <Progress value={progressPercent} />
        </div>
      )}
    </div>
  );
}
