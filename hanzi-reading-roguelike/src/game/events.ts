export const GAME_EVENTS = {
  enemySelected: "enemy-selected",
  enemyCleared: "enemy-cleared",
  statsUpdated: "stats-updated",
  feedback: "feedback",
  gameEnded: "game-ended"
} as const;

export type EnemySelectedPayload = {
  hanzi: string;
  hint?: string;
};

export type StatsPayload = {
  score: number;
  enemyCount: number;
};

export type FeedbackPayload = {
  text: string;
  isError?: boolean;
};

export type GameEndedPayload = {
  score: number;
};
