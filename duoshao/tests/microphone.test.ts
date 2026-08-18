import { describe, expect, test } from "bun:test";
import { MicrophoneCapture } from "../src/audio/microphone";

interface CaptureInternals {
  context: { sampleRate: number };
  process(input: Float32Array): void;
}

describe("MicrophoneCapture push-to-talk", () => {
  test("captures only between explicit begin and end calls", () => {
    const starts: number[] = [];
    const utterances: Float32Array[] = [];
    const capture = new MicrophoneCapture({
      onSpeechStart: (at) => starts.push(at),
      onSpeechEnd: (samples) => utterances.push(samples),
      onError: () => {},
    });
    const internals = capture as unknown as CaptureInternals;
    internals.context = { sampleRate: 16_000 };

    internals.process(new Float32Array([9, 9]));
    capture.beginUtterance();
    capture.beginUtterance();
    internals.process(new Float32Array([1, 2]));
    internals.process(new Float32Array([3]));
    capture.endUtterance();
    capture.endUtterance();
    internals.process(new Float32Array([8, 8]));

    expect(starts).toHaveLength(1);
    expect(utterances).toHaveLength(1);
    expect([...utterances[0]]).toEqual([1, 2, 3]);
  });

  test("a tap shorter than one audio callback produces a valid silent utterance", () => {
    let samples: Float32Array | undefined;
    const capture = new MicrophoneCapture({
      onSpeechStart: () => {},
      onSpeechEnd: (value) => { samples = value; },
      onError: () => {},
    });
    (capture as unknown as CaptureInternals).context = { sampleRate: 16_000 };

    capture.beginUtterance();
    capture.endUtterance();

    expect(samples).toEqual(new Float32Array([0]));
  });
});
