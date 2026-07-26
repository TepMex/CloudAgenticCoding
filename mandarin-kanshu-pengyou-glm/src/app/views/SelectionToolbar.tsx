export function SelectionToolbar({ x, y, online, onExplain, onUnderstand, onCompanion, onCopy }: {
  x: number; y: number; online: boolean;
  onExplain: () => void; onUnderstand: () => void; onCompanion: () => void; onCopy: () => void;
}) {
  return (
    <div className="toolbar" style={{ left: x, top: y, transform: "translate(-50%, -100%)" }} role="toolbar">
      <button onClick={onExplain} disabled={!online} title="Explain">Explain</button>
      <button onClick={onUnderstand} disabled={!online} title="Understand">Understand</button>
      <button onClick={onCompanion} disabled={!online} title="Companion reaction">💭</button>
      <button onClick={onCopy} title="Copy">⧉</button>
    </div>
  );
}
