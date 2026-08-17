export interface ParaformerConfig {
  runtimeUrl: string;
  modelUrl: string;
  tokensUrl: string;
  modelConfigUrl?: string;
}

export function readParaformerConfig(): ParaformerConfig {
  const config = {
    runtimeUrl: import.meta.env.VITE_SHERPA_RUNTIME_URL ?? "",
    modelUrl: import.meta.env.VITE_PARAFORMER_MODEL_URL ?? "",
    tokensUrl: import.meta.env.VITE_PARAFORMER_TOKENS_URL ?? "",
    modelConfigUrl: import.meta.env.VITE_PARAFORMER_CONFIG_URL || undefined,
  };
  if (!config.runtimeUrl && config.modelUrl && config.tokensUrl) {
    throw new Error("Paraformer weights are installed, but the sherpa WebAssembly runtime bridge is not configured. Run bun run setup:speech, or open ?stt=mock.");
  }
  if (!config.runtimeUrl || !config.modelUrl || !config.tokensUrl) {
    throw new Error("Speech model is not configured. Set the VITE_SHERPA_RUNTIME_URL, VITE_PARAFORMER_MODEL_URL and VITE_PARAFORMER_TOKENS_URL variables, or open ?stt=mock.");
  }
  return config;
}
