import type { GradedItem, Lesson, Question, Verdict } from "./types.ts";

export function verdictHeadline(verdict: Verdict): string {
  if (verdict === "correct") return "Верно";
  if (verdict === "partial") return "Частично верно";
  return "Нужно внимание";
}

function coaching(verdict: Verdict): string {
  if (verdict === "correct") return "";
  if (verdict === "partial") {
    return " Ответ по делу, но в нём не хватает точности: обопрись на конкретные слова фрагмента.";
  }
  return " Пока ответ слабо опирается на фрагмент.";
}

function keywordHits(question: Question, answer: string): string[] {
  const hay = answer.toLowerCase();
  return question.keywords.filter(keyword => hay.includes(keyword.toLowerCase()));
}

export function gradeQuestion(question: Question, raw: string): GradedItem {
  const answer = raw.trim();
  const examples = question.examples;

  if (!answer) {
    return {
      id: question.id,
      verdict: "attention",
      headline: verdictHeadline("attention"),
      explanation: `Ответа нет. ${question.explanation}`,
      examples,
    };
  }

  if (question.kind === "mcq") {
    const ok = answer === question.answerKey;
    const verdict: Verdict = ok ? "correct" : "attention";
    const lead = ok ? question.explanation : `Точнее вариант ${question.answerKey ?? ""}. ${question.explanation}`;
    return {
      id: question.id,
      verdict,
      headline: verdictHeadline(verdict),
      explanation: lead,
      examples,
    };
  }

  const hits = keywordHits(question, answer);
  let verdict: Verdict;
  if (question.opinion) {
    if (answer.length < 12) verdict = "attention";
    else if (answer.length < 20) verdict = "partial";
    else if (question.keywords.length === 0 || hits.length > 0) verdict = "correct";
    else verdict = "partial";
  } else if (question.keywords.length === 0) {
    verdict = answer.length >= 40 ? "correct" : answer.length >= 12 ? "partial" : "attention";
  } else if (hits.length === 0) {
    verdict = answer.length >= 18 ? "partial" : "attention";
  } else if (hits.length >= 2 || hits.length / question.keywords.length >= 0.5) {
    verdict = "correct";
  } else {
    verdict = "partial";
  }

  return {
    id: question.id,
    verdict,
    headline: verdictHeadline(verdict),
    explanation: `${question.explanation}${coaching(verdict)}`,
    examples,
  };
}

export function gradeLocal(lesson: Lesson, answers: Record<string, string>): GradedItem[] {
  return lesson.questions.map(question => gradeQuestion(question, answers[question.id] ?? ""));
}

export function tally(items: GradedItem[]): { correct: number; partial: number; attention: number } {
  return {
    correct: items.filter(item => item.verdict === "correct").length,
    partial: items.filter(item => item.verdict === "partial").length,
    attention: items.filter(item => item.verdict === "attention").length,
  };
}
