import Phaser from "phaser";
import "./style.css";
import { GAME_EVENTS } from "./game/events";
import { GameOverScene } from "./game/scenes/GameOverScene";
import { GameScene } from "./game/scenes/GameScene";
import { MainMenuScene } from "./game/scenes/MainMenuScene";

const GAME_WIDTH = 390;
const GAME_HEIGHT = 844;

const hud = document.getElementById("hud") as HTMLDivElement;
const answerForm = document.getElementById("answer-form") as HTMLFormElement;
const answerLabel = document.getElementById("answer-label") as HTMLLabelElement;
const pinyinInput = document.getElementById("pinyin-input") as HTMLInputElement;
const scorePill = document.getElementById("score-pill") as HTMLSpanElement;
const wavePill = document.getElementById("wave-pill") as HTMLSpanElement;
const toast = document.getElementById("toast") as HTMLDivElement;

let currentToastTimeout = 0;
let selectionExists = false;

const game = new Phaser.Game({
  type: Phaser.AUTO,
  parent: "game-root",
  backgroundColor: "#121d37",
  scale: {
    mode: Phaser.Scale.FIT,
    autoCenter: Phaser.Scale.CENTER_BOTH,
    width: GAME_WIDTH,
    height: GAME_HEIGHT
  },
  input: {
    activePointers: 2
  },
  scene: [MainMenuScene, GameScene, GameOverScene]
});

game.events.on(GAME_EVENTS.enemySelected, (payload: { hanzi: string; hint?: string }) => {
  selectionExists = true;
  const hintPart = payload.hint ? `Hint: ${payload.hint}` : "No hint this time";
  answerLabel.textContent = `Type pinyin for "${payload.hanzi}". ${hintPart}`;
  answerForm.classList.remove("hidden");
  pinyinInput.value = "";
  pinyinInput.focus();
});

game.events.on(GAME_EVENTS.enemyCleared, () => {
  selectionExists = false;
  answerForm.classList.add("hidden");
  pinyinInput.blur();
});

game.events.on(GAME_EVENTS.statsUpdated, (payload: { score: number; enemyCount: number }) => {
  scorePill.textContent = `Score: ${payload.score}`;
  wavePill.textContent = `Enemies: ${payload.enemyCount}`;
  hud.classList.remove("hidden");
});

game.events.on(GAME_EVENTS.feedback, (payload: { text: string; isError?: boolean }) => {
  showToast(payload.text, payload.isError);
});

game.events.on(GAME_EVENTS.gameEnded, (payload: { score: number }) => {
  showToast(`You lost. Final score: ${payload.score}`, true);
});

answerForm.addEventListener("submit", (event) => {
  event.preventDefault();
  if (!selectionExists) {
    return;
  }

  const scene = game.scene.getScene("GameScene") as GameScene;
  const accepted = scene.submitAnswer(pinyinInput.value);
  if (accepted) {
    pinyinInput.value = "";
  } else {
    pinyinInput.focus();
    pinyinInput.select();
  }
});

function showToast(message: string, isError = false): void {
  if (currentToastTimeout) {
    window.clearTimeout(currentToastTimeout);
  }
  toast.textContent = message;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", Boolean(isError));
  currentToastTimeout = window.setTimeout(() => {
    toast.classList.add("hidden");
  }, 1400);
}
