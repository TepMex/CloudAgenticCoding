export function canSpeak(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

export function speakChinese(text: string): void {
  if (!canSpeak()) return
  window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(text)
  u.lang = 'zh-CN'
  u.rate = 0.8
  const voice = window.speechSynthesis.getVoices().find((v) => v.lang.toLowerCase().startsWith('zh'))
  if (voice) u.voice = voice
  window.speechSynthesis.speak(u)
}
