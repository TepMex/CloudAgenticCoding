export type AnswerResult =
  | { ok: true; answer: string }
  | { ok: false; reason: "cancel" };

export type AnswerPromptOptions = {
  hanzi: string;
  promptLabel: string;
  placeholder?: string;
  initialValue?: string;
};

/**
 * Shows the DOM answer modal, focuses the input (mobile keyboard),
 * resolves on submit or cancel.
 */
export function promptAnswer(options: AnswerPromptOptions): Promise<AnswerResult> {
  const modal = document.getElementById("answer-modal");
  const labelEl = document.getElementById("answer-modal-hanzi");
  const promptEl = document.getElementById("answer-modal-label");
  const input = document.getElementById("answer-modal-input") as HTMLInputElement | null;
  const btnCancel = document.getElementById("answer-modal-cancel");
  const btnSubmit = document.getElementById("answer-modal-submit");

  if (!modal || !labelEl || !promptEl || !input || !btnCancel || !btnSubmit) {
    return Promise.resolve({ ok: false, reason: "cancel" });
  }

  return new Promise((resolve) => {
    let settled = false;
    const finish = (r: AnswerResult) => {
      if (settled) return;
      settled = true;
      cleanup();
      modal.classList.add("hidden");
      modal.setAttribute("aria-hidden", "true");
      resolve(r);
    };

    const onSubmit = () => {
      finish({ ok: true, answer: input.value });
    };

    const onCancel = () => {
      finish({ ok: false, reason: "cancel" });
    };

    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Enter") {
        e.preventDefault();
        onSubmit();
      } else if (e.key === "Escape") {
        e.preventDefault();
        onCancel();
      }
    };

    const cleanup = () => {
      btnSubmit.removeEventListener("click", onSubmit);
      btnCancel.removeEventListener("click", onCancel);
      input.removeEventListener("keydown", onKey);
    };

    labelEl.textContent = options.hanzi;
    promptEl.textContent = options.promptLabel;
    input.placeholder = options.placeholder ?? "";
    input.value = options.initialValue ?? "";
    modal.classList.remove("hidden");
    modal.setAttribute("aria-hidden", "false");

    btnSubmit.addEventListener("click", onSubmit);
    btnCancel.addEventListener("click", onCancel);
    input.addEventListener("keydown", onKey);

    requestAnimationFrame(() => {
      input.focus();
      input.select();
    });
  });
}

/** @deprecated use promptAnswer */
export function promptPinyin(hanzi: string, initialValue = "") {
  return promptAnswer({
    hanzi,
    promptLabel: "Type the pinyin (no tones)",
    initialValue,
  });
}

export { normalizePinyin } from "./answerMatching";
