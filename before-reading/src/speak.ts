export function canSpeak(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

/** Word first, then the context phrase when it adds something. */
export function speechLines(expression: string, example: string): string[] {
  const word = expression.trim()
  const phrase = example.trim()
  if (!word) return phrase ? [phrase] : []
  if (!phrase || phrase === word) return [word]
  return [word, phrase]
}

type UtteranceHandlers = {
  onend: () => void
  onerror: (reason: string) => void
}

export type SpeechEngine = {
  cancel: () => void
  speak: (text: string, handlers: UtteranceHandlers) => void
}

let token = 0

/** Speak each line after the previous one finishes. A later call replaces the chain. */
export function playLines(lines: readonly string[], engine: SpeechEngine): void {
  const current = ++token
  const texts = lines.map((line) => line.trim()).filter(Boolean)
  engine.cancel()
  let index = 0
  const step = () => {
    if (current !== token) return
    const text = texts[index]
    if (!text) return
    index += 1
    engine.speak(text, {
      onend: () => {
        if (current === token) step()
      },
      onerror: (reason) => {
        if (current !== token) return
        if (reason === 'interrupted' || reason === 'canceled') return
        step()
      },
    })
  }
  step()
}

export function stopLines(): void {
  token += 1
}

export function speakSequence(parts: readonly string[]): void {
  const engine = browserEngine()
  if (!engine) return
  playLines(parts, engine)
}

export function speakChinese(text: string): void {
  speakSequence([text])
}

export function stopSpeaking(): void {
  stopLines()
  if (!canSpeak()) return
  window.speechSynthesis.cancel()
}

let live: SpeechSynthesisUtterance[] = []

function browserEngine(): SpeechEngine | null {
  if (!canSpeak()) return null
  return {
    cancel() {
      live = []
      const synth = window.speechSynthesis
      if (synth.speaking || synth.pending) synth.cancel()
    },
    speak(text, handlers) {
      const utter = new SpeechSynthesisUtterance(text)
      utter.lang = 'zh-CN'
      utter.rate = 0.9
      const voice = window.speechSynthesis
        .getVoices()
        .find((item) => item.lang.toLowerCase().replace('_', '-').startsWith('zh'))
      if (voice) utter.voice = voice
      utter.onend = () => handlers.onend()
      utter.onerror = (event) => handlers.onerror(event.error)
      live.push(utter)
      window.speechSynthesis.speak(utter)
      if (window.speechSynthesis.paused) window.speechSynthesis.resume()
    },
  }
}
