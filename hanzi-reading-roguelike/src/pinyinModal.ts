export type PinyinResult =
  | { ok: true; answer: string }
  | { ok: false; reason: "cancel" };

/**
 * Shows the DOM modal, focuses the input (mobile keyboard), resolves on submit or cancel.
 */
export function promptPinyin(hanzi: string, initialValue = ""): Promise<PinyinResult> {
  const modal = document.getElementById("pinyin-modal");
  const labelEl = document.getElementById("pinyin-modal-hanzi");
  const input = document.getElementById("pinyin-modal-input") as HTMLInputElement | null;
  const btnCancel = document.getElementById("pinyin-modal-cancel");
  const btnSubmit = document.getElementById("pinyin-modal-submit");

  if (!modal || !labelEl || !input || !btnCancel || !btnSubmit) {
    return Promise.resolve({ ok: false, reason: "cancel" });
  }

  return new Promise((resolve) => {
    let settled = false;
    const finish = (r: PinyinResult) => {
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

    labelEl.textContent = hanzi;
    input.value = initialValue;
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

/** Normalize typed pinyin for comparison (ASCII, lowercase, trim). */
export function normalizePinyin(s: string): string {
  return s
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");
}
