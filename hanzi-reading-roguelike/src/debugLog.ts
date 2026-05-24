type DebugPayload = {
  hypothesisId: string;
  location: string;
  message: string;
  data: Record<string, unknown>;
  timestamp: number;
};

export function logDebugEvent(payload: Omit<DebugPayload, "timestamp">): void {
  const line: DebugPayload = { ...payload, timestamp: Date.now() };
  const body = `${JSON.stringify(line)}\n`;
  if (navigator.sendBeacon) {
    navigator.sendBeacon("/__debug-log", new Blob([body], { type: "application/x-ndjson" }));
    return;
  }
  void fetch("/__debug-log", {
    method: "POST",
    headers: { "content-type": "application/x-ndjson" },
    body
  });
}
