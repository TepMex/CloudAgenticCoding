import { VoiceActivityDetector } from "./vad";

interface MicrophoneCallbacks {
  onSpeechStart(at: number): void;
  onSpeechEnd(samples: Float32Array, startedAt: number, endedAt: number, sampleRate: number): void;
  onError(error: Error): void;
}

export class MicrophoneCapture {
  private context?: AudioContext;
  private stream?: MediaStream;
  private source?: MediaStreamAudioSourceNode;
  private processor?: ScriptProcessorNode;
  private chunks: Float32Array[] = [];
  private speechStartedAt = 0;
  private readonly vad = new VoiceActivityDetector();

  constructor(private readonly callbacks: MicrophoneCallbacks) {}

  async start(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error("Microphone capture is not supported by this browser.");
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 } });
      this.context = new AudioContext();
      await this.context.resume();
      this.source = this.context.createMediaStreamSource(this.stream);
      this.processor = this.context.createScriptProcessor(2048, 1, 1);
      this.processor.onaudioprocess = (event) => this.process(event.inputBuffer.getChannelData(0));
      this.source.connect(this.processor);
      this.processor.connect(this.context.destination);
    } catch (reason) {
      throw new Error(reason instanceof DOMException && reason.name === "NotAllowedError"
        ? "Microphone permission was denied. Allow microphone access and try again."
        : "The microphone could not be opened.", { cause: reason });
    }
  }

  async stop(): Promise<void> {
    this.processor?.disconnect();
    this.source?.disconnect();
    this.stream?.getTracks().forEach((track) => track.stop());
    await this.context?.close();
    this.processor = undefined;
    this.source = undefined;
    this.stream = undefined;
    this.context = undefined;
    this.chunks = [];
    this.vad.reset();
  }

  private process(input: Float32Array): void {
    const samples = new Float32Array(input);
    const now = performance.now();
    const state = this.vad.process(samples, now);
    if (state === "start") {
      this.chunks = [samples];
      this.speechStartedAt = now;
      this.callbacks.onSpeechStart(now);
    } else if (state === "continue") {
      this.chunks.push(samples);
    } else if (state === "end") {
      this.chunks.push(samples);
      const merged = new Float32Array(this.chunks.reduce((total, chunk) => total + chunk.length, 0));
      let offset = 0;
      for (const chunk of this.chunks) {
        merged.set(chunk, offset);
        offset += chunk.length;
      }
      this.callbacks.onSpeechEnd(merged, this.speechStartedAt, now, this.context?.sampleRate ?? 16_000);
      this.chunks = [];
    }
  }
}
