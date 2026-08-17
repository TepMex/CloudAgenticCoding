import { toChineseMoney } from "../chinese/chineseNumber";

export function GameOver({ score, hits, missedAmount, onPlayAgain }: { score: number; hits: number; missedAmount: number; onPlayAgain(): void }) {
  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-labelledby="game-over-title">
      <section className="game-over">
        <div className="game-over__stamp">太慢了!</div>
        <p className="eyebrow">Run complete</p>
        <h2 id="game-over-title">Game over</h2>
        <div className="result-grid">
          <div><span>Score</span><strong>{score.toLocaleString()}</strong></div>
          <div><span>Hits</span><strong>{hits}</strong></div>
        </div>
        <div className="missed-answer">
          <span>You missed</span>
          <strong>¥{missedAmount}</strong>
          <b>{toChineseMoney(missedAmount)}</b>
        </div>
        <button className="primary-button" onClick={onPlayAgain}>Play again</button>
      </section>
    </div>
  );
}
