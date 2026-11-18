export function canSpeak(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

export function speakChinese(text: string): void {
  if (!canSpeak()) return
  const trimmed = text.trim()
  if (!trimmed) return
  const utter = new SpeechSynthesisUtterance(trimmed)
  utter.lang = 'zh-CN'
  utter.rate = 0.9
  const voice = window.speechSynthesis
    .getVoices()
    .find((item) => item.lang.toLowerCase().replace('_', '-').startsWith('zh'))
  if (voice) utter.voice = voice
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(utter)
}

export function stopSpeaking(): void {
  if (!canSpeak()) return
  window.speechSynthesis.cancel()
}
