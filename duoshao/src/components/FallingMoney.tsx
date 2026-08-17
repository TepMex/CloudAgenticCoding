import type { FallingTarget } from "../game/types";

export function FallingMoney({ target, now }: { target: FallingTarget; now: number }) {
  const sequence = Number(target.id.split("-")[1]) || 0;
  const lane = (sequence * 37 + target.amount * 13) % 72 + 8;
  const elapsed = Math.max(0, now - target.spawnedAt);
  const style = {
    "--fall-duration": `${target.fallDurationMs}ms`,
    "--fall-delay": `${-elapsed}ms`,
    left: `${lane}%`,
  } as React.CSSProperties;
  return (
    <div className={`money money--${target.state}`} style={style} aria-label={`${target.amount} yuan`}>
      <span className="money__symbol">¥</span>
      <strong>{target.amount}</strong>
      <span className="money__shine" />
    </div>
  );
}
