import { useState } from "react";

export function MockConsole({ onSubmit }: { onSubmit(transcript: string): void }) {
  const [value, setValue] = useState("");
  return (
    <form className="mock-console" onSubmit={(event) => {
      event.preventDefault();
      if (!value.trim()) return;
      onSubmit(value);
      setValue("");
    }}>
      <label htmlFor="mock-transcript">Mock transcript</label>
      <div>
        <input id="mock-transcript" value={value} onChange={(event) => setValue(event.target.value)} placeholder="e.g. 三百元" autoComplete="off" />
        <button type="submit">Send</button>
      </div>
    </form>
  );
}
