import { MicIndicator } from "./MicIndicator";
import type { MicrophoneStatus } from "../speech/types";

export function Hud({ score, hits, level, micStatus, isMock }: { score: number; hits: number; level: number; micStatus: MicrophoneStatus; isMock: boolean }) {
  return (
    <header className="hud">
      <div className="hud__metric"><span>Score</span><strong>{score.toLocaleString()}</strong></div>
      <div className="hud__metric"><span>Hits</span><strong>{hits}</strong></div>
      <div className="hud__metric"><span>Level</span><strong>{level}</strong></div>
      <MicIndicator status={micStatus} isMock={isMock} />
    </header>
  );
}
