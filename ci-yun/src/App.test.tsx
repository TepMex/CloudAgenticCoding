import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";
import type { Cloud, Word } from "./lib/types";
import type { CloudStageProps } from "./scene/CloudStage";

function word(patch: Partial<Word> & Pick<Word, "id" | "simplified">): Word {
  return {
    traditional: patch.simplified,
    pinyin: "",
    meaning: "",
    meaningRu: "",
    pos: "",
    sources: ["hsk"],
    hsk: [],
    cluster: 0,
    x2: patch.id ?? 0,
    y2: 0,
    x: patch.id ?? 0,
    y: 1,
    z: 2,
    neighbors: [],
    ...patch,
  };
}

const cloud: Cloud = {
  model: "google/embeddinggemma-2",
  prompt: "Clustering",
  dim: 768,
  seed: 42,
  k: 2,
  silhouette: {},
  clusters: [
    { id: 0, name: "Хозяйство", color: "#d4a017", size: 2 },
    { id: 1, name: "Речь", color: "#7eb6d6", size: 1 },
  ],
  words: [
    word({
      id: 0,
      simplified: "经济",
      pinyin: "jīngjì",
      meaning: "economy",
      meaningRu: "экономика",
      pos: "существительное",
      cluster: 0,
      neighbors: [1],
    }),
    word({ id: 1, simplified: "经理", pinyin: "jīnglǐ", meaningRu: "менеджер", cluster: 0, neighbors: [0] }),
    word({
      id: 2,
      simplified: "我",
      pinyin: "wǒ",
      meaningRu: "я",
      cluster: 1,
      sources: ["refold"],
      hsk: ["HSK1"],
      neighbors: [],
    }),
  ],
};

function Probe(props: CloudStageProps) {
  return (
    <div
      data-testid="scene"
      data-mode={props.mode}
      data-focus={props.focusId ?? ""}
      data-cluster={props.activeCluster ?? ""}
    />
  );
}

describe("词云", () => {
  it("centers a listed word and switches projection", () => {
    const view = render(<App cloud={cloud} Stage={Probe} />);
    expect(view.getByRole("heading", { name: "Облако лексики" })).toBeInTheDocument();
    expect(view.getByTestId("scene")).toHaveAttribute("data-mode", "3d");

    fireEvent.change(view.getByRole("combobox"), { target: { value: "经济" } });
    fireEvent.click(view.getByRole("option", { name: /经济/ }));

    expect(view.getByTestId("scene")).toHaveAttribute("data-focus", "0");
    expect(view.getByTestId("scene")).toHaveAttribute("data-cluster", "");
    expect(view.getByText("экономика")).toBeInTheDocument();
    expect(view.container.querySelector("[data-centered='经济']")).not.toBeNull();

    fireEvent.click(view.getByRole("radio", { name: "Плоскость" }));
    expect(view.getByTestId("scene")).toHaveAttribute("data-mode", "2d");
    expect(view.getByTestId("scene")).toHaveAttribute("data-focus", "0");
  });

  it("reports a word that is not in the list", () => {
    const view = render(<App cloud={cloud} Stage={Probe} />);
    fireEvent.change(view.getByRole("combobox"), { target: { value: "компьютер" } });
    expect(view.getByText("Нет в списке")).toBeInTheDocument();
    expect(view.getByTestId("scene")).toHaveAttribute("data-focus", "");
  });

  it("centers a cluster and can return to the whole cloud", () => {
    const view = render(<App cloud={cloud} Stage={Probe} />);
    fireEvent.click(view.getByRole("button", { name: /Хозяйство/ }));
    expect(view.getByTestId("scene")).toHaveAttribute("data-cluster", "0");
    expect(view.getByTestId("scene")).toHaveAttribute("data-focus", "");

    fireEvent.click(view.getByRole("button", { name: "Всё облако" }));
    expect(view.getByTestId("scene")).toHaveAttribute("data-cluster", "");
    expect(view.getByTestId("scene")).toHaveAttribute("data-focus", "");
  });
});
