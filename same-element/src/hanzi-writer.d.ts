declare module 'hanzi-writer' {
  export type StrokeQuizData = {
    character: string
    strokeNum: number
    mistakesOnStroke: number
    totalMistakes: number
    strokesRemaining: number
  }

  export type HanziWriterInstance = {
    quiz: (options: {
      leniency?: number
      acceptBackwardsStrokes?: boolean
      showHintAfterMisses?: number | false
      highlightOnComplete?: boolean
      quizStartStrokeNum?: number
      onCorrectStroke?: (data: StrokeQuizData) => void
      onMistake?: (data: StrokeQuizData) => void
      onComplete?: (summary: { character: string; totalMistakes: number }) => void
    }) => void
    cancelQuiz: () => void
    updateDimensions: (dimensions: { width: number; height: number }) => void
    highlightStroke: (strokeNum: number) => void
    showOutline: (options?: { duration?: number }) => void
    hideOutline: (options?: { duration?: number }) => void
    showCharacter: (options?: { duration?: number }) => void
    hideCharacter: (options?: { duration?: number }) => void
    updateColor: (colorName: string, colorVal: string, options?: { duration?: number }) => void
  }

  type HanziWriterStatic = {
    create: (
      element: HTMLElement | string,
      character: string,
      options?: Record<string, unknown>,
    ) => HanziWriterInstance
  }

  const HanziWriter: HanziWriterStatic
  export default HanziWriter
}
