export interface VadOptions {
  threshold?: number;
  silenceMs?: number;
  minSpeechMs?: number;
  maxSpeechMs?: number;
}

export class VoiceActivityDetector {
  private speaking = false;
  private startedAt = 0;
  private lastVoiceAt = 0;
  private readonly threshold: number;
  private readonly silenceMs: number;
  private readonly maxSpeechMs: number;

  constructor(options: VadOptions = {}) {
    this.threshold = options.threshold ?? 0.022;
    this.silenceMs = options.silenceMs ?? 480;
    this.maxSpeechMs = options.maxSpeechMs ?? 3_000;
  }

  process(samples: Float32Array, now: number): "start" | "continue" | "end" | "silence" {
    let energy = 0;
    for (let index = 0; index < samples.length; index += 1) energy += samples[index] * samples[index];
    const rms = Math.sqrt(energy / samples.length);
    if (rms >= this.threshold) {
      if (!this.speaking) {
        this.speaking = true;
        this.startedAt = now;
        this.lastVoiceAt = now;
        return "start";
      }
      this.lastVoiceAt = now;
      if (now - this.startedAt >= this.maxSpeechMs) {
        this.speaking = false;
        return "end";
      }
      return "continue";
    }
    if (this.speaking && now - this.lastVoiceAt >= this.silenceMs) {
      this.speaking = false;
      // Even a very short detected utterance must finish the matching engine
      // attempt. The recognizer can return an empty transcript for noise.
      return "end";
    }
    return this.speaking ? "continue" : "silence";
  }

  reset(): void {
    this.speaking = false;
  }
}
