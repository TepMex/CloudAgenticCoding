import { expect, test } from 'bun:test'
import { playLines, speechLines, stopLines, type SpeechEngine } from '../src/speak'

test('the word is spoken before the context phrase', () => {
  expect(speechLines('保佑', '列祖列宗保佑')).toEqual(['保佑', '列祖列宗保佑'])
  expect(speechLines('  中  ', '  大典中  ')).toEqual(['中', '大典中'])
  expect(speechLines('古月', '古月')).toEqual(['古月'])
  expect(speechLines('中', '')).toEqual(['中'])
  expect(speechLines('  ', '  ')).toEqual([])
})

test('playback waits for the word to finish before the phrase', () => {
  const sink = fakeSpeech()
  playLines(['保佑', '列祖列宗保佑'], sink.engine)
  expect(sink.spoken).toEqual(['保佑'])
  sink.finish()
  expect(sink.spoken).toEqual(['保佑', '列祖列宗保佑'])
  sink.finish()
  expect(sink.spoken).toEqual(['保佑', '列祖列宗保佑'])
})

test('stopping or starting over does not continue the old phrase', () => {
  const sink = fakeSpeech()
  playLines(['保佑', '列祖列宗保佑'], sink.engine)
  stopLines()
  sink.interrupt()
  expect(sink.spoken).toEqual(['保佑'])

  playLines(['中', '大典中'], sink.engine)
  expect(sink.spoken).toEqual(['保佑', '中'])
  sink.interrupt()
  expect(sink.spoken).toEqual(['保佑', '中'])
})

test('a synthesis error still tries the next line', () => {
  const sink = fakeSpeech()
  playLines(['保佑', '列祖列宗保佑'], sink.engine)
  sink.fail('synthesis-failed')
  expect(sink.spoken).toEqual(['保佑', '列祖列宗保佑'])
})

function fakeSpeech() {
  const spoken: string[] = []
  let handlers: { onend: () => void; onerror: (reason: string) => void } | null = null
  const engine: SpeechEngine = {
    cancel() {
      const previous = handlers
      handlers = null
      previous?.onerror('interrupted')
    },
    speak(text, next) {
      spoken.push(text)
      handlers = next
    },
  }
  return {
    engine,
    spoken,
    finish() {
      const current = handlers
      handlers = null
      current?.onend()
    },
    interrupt() {
      const current = handlers
      handlers = null
      current?.onerror('interrupted')
    },
    fail(reason: string) {
      const current = handlers
      handlers = null
      current?.onerror(reason)
    },
  }
}
