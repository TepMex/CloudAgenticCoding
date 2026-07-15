import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";

function visibleCount(container: HTMLElement): number {
  const text = container.querySelector(".map-status")?.textContent ?? "";
  return Number(text.match(/[\d,]+/)?.[0].replaceAll(",", ""));
}

function buttonWithText(container: Element, selector: string, text: string): HTMLButtonElement {
  const match = [...container.querySelectorAll<HTMLButtonElement>(selector)].find((button) => button.textContent === text);
  if (!match) throw new Error(`Button ${text} was not found.`);
  return match;
}

describe("Map of Chinese interface", () => {
  it("covers tone, HSK, polyphonic search, drawer keyboard, and mobile controls", () => {
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 320 });
    const { container } = render(<App />);
    const controls = container.querySelector(".control-deck")!;

    const allCount = visibleCount(container);
    const secondTone = buttonWithText(controls, ".tone-segments button", "2nd");
    fireEvent.click(secondTone);
    expect(secondTone).toHaveAttribute("aria-pressed", "true");
    expect(visibleCount(container)).toBeLessThan(allCount);
    fireEvent.click(buttonWithText(controls, ".tone-segments button", "All"));

    const hskSelect = controls.querySelector<HTMLSelectElement>('select[aria-label="HSK system"]')!;
    fireEvent.change(hskSelect, { target: { value: "hsk2" } });
    const oldLevel2 = buttonWithText(controls, ".level-buttons button", "2");
    fireEvent.click(oldLevel2);
    const exact = visibleCount(container);
    fireEvent.click(controls.querySelector<HTMLInputElement>('input[type="checkbox"]')!);
    expect(visibleCount(container)).toBeGreaterThan(exact);

    fireEvent.change(hskSelect, { target: { value: "hsk3" } });
    expect(controls.textContent).toContain("Official recognition-character list");
    expect(buttonWithText(controls, ".level-buttons button", "7–9")).toBeInTheDocument();

    const search = container.querySelector<HTMLInputElement>("#global-search")!;
    fireEvent.change(search, { target: { value: "行" } });
    expect(container.querySelectorAll(".matrix-cell.highlighted").length).toBeGreaterThan(1);
    const result = container.querySelector<HTMLButtonElement>(".search-results button")!;
    expect(result.textContent).toContain("行");
    fireEvent.click(result);
    expect(container.querySelector(".details-panel")).toBeInTheDocument();
    fireEvent.keyDown(window, { key: "Escape" });
    expect(container.querySelector(".details-panel")).not.toBeInTheDocument();

    fireEvent.change(search, { target: { value: "lu:4" } });
    expect(container.querySelectorAll(".search-results button").length).toBeGreaterThan(0);

    expect(controls.querySelector('button[aria-label="Decrease map density"]')).toBeInTheDocument();
    expect(controls.querySelector('button[aria-label="Increase map density"]')).toBeInTheDocument();
    expect(buttonWithText(controls, ".segments button", "Basic 3500")).toBeInTheDocument();
    expect(container.querySelector('[aria-label="Pinyin initial and final matrix"]')).toBeInTheDocument();
  });
});
