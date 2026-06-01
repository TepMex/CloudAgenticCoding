import { useEffect, useState } from "react";
import { SettingsIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { normalizeApiToken } from "@/lib/api-token";
import {
  type AppSettings,
  DEFAULT_SETTINGS,
  formatModelList,
  loadSettings,
  parseModelList,
  saveSettings,
} from "@/lib/settings";

type SettingsDialogProps = {
  onSettingsChange: (settings: AppSettings) => void;
};

export function SettingsDialog({ onSettingsChange }: SettingsDialogProps) {
  const [open, setOpen] = useState(false);
  const [baseUrl, setBaseUrl] = useState(DEFAULT_SETTINGS.baseUrl);
  const [token, setToken] = useState("");
  const [embeddingModelsText, setEmbeddingModelsText] = useState(
    formatModelList(DEFAULT_SETTINGS.embeddingModels),
  );
  const [llmModelsText, setLlmModelsText] = useState(
    formatModelList(DEFAULT_SETTINGS.llmModels),
  );

  useEffect(() => {
    if (!open) return;
    const s = loadSettings();
    setBaseUrl(s.baseUrl);
    setToken(s.token);
    setEmbeddingModelsText(formatModelList(s.embeddingModels));
    setLlmModelsText(formatModelList(s.llmModels));
  }, [open]);

  const handleSave = () => {
    const settings: AppSettings = {
      baseUrl: baseUrl.trim() || DEFAULT_SETTINGS.baseUrl,
      token: normalizeApiToken(token),
      embeddingModels: parseModelList(embeddingModelsText),
      llmModels: parseModelList(llmModelsText),
    };
    saveSettings(settings);
    onSettingsChange(settings);
    setOpen(false);
  };

  const handleClearToken = () => {
    setToken("");
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <SettingsIcon data-icon="inline-start" />
          Settings
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Settings</DialogTitle>
          <DialogDescription>
            Configure your OpenAI-compatible API for LLM analysis and Hugging Face model IDs for
            local embeddings.
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="base-url">Base URL</Label>
            <Input
              id="base-url"
              type="url"
              placeholder="https://api.openai.com"
              value={baseUrl}
              onChange={e => setBaseUrl(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="token">API token</Label>
            <Input
              id="token"
              type="password"
              placeholder="sk-..."
              value={token}
              onChange={e => setToken(e.target.value)}
              autoComplete="off"
            />
            <p className="text-muted-foreground text-xs">
              Paste your provider API key here, not the text you want to analyze.
            </p>
            <Button type="button" variant="ghost" size="sm" onClick={handleClearToken}>
              Clear token
            </Button>
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="embedding-models">Embedding models (WASM, one per line)</Label>
            <Textarea
              id="embedding-models"
              placeholder="Xenova/all-MiniLM-L6-v2"
              value={embeddingModelsText}
              onChange={e => setEmbeddingModelsText(e.target.value)}
              className="min-h-[80px] font-mono text-sm"
            />
          </div>
          <div className="flex flex-col gap-2">
            <Label htmlFor="llm-models">LLM models (one per line)</Label>
            <Textarea
              id="llm-models"
              placeholder="gpt-4o-mini"
              value={llmModelsText}
              onChange={e => setLlmModelsText(e.target.value)}
              className="min-h-[80px] font-mono text-sm"
            />
          </div>
        </div>
        <DialogFooter>
          <Button type="button" onClick={handleSave}>
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
