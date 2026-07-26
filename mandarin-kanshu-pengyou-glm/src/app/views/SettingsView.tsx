import { useEffect, useState } from "react";
import { db, getTaskAssignments, saveTaskAssignments } from "../../db/database";
import { secrets } from "../../providers/secrets";
import { testConnection } from "../../providers/client";
import { uuid } from "../../shared/util";
import { useAppStore } from "../store";
import type { ProviderProfile, TaskModelAssignments, ProviderCapabilities } from "../../shared/domain";

const PRESETS: Omit<ProviderProfile, "id">[] = [
  { name: "Local small", baseUrl: "http://localhost:11434", apiKeyReference: "", model: "qwen2.5:7b", advanced: {} },
  { name: "Hosted lite", baseUrl: "https://api.openai.com/v1", apiKeyReference: "", model: "gpt-4o-mini", advanced: {} },
  { name: "Strong primary", baseUrl: "https://api.openai.com/v1", apiKeyReference: "", model: "gpt-4o", advanced: { temperature: 0.4 } },
];

export function SettingsView() {
  const { settings, setSettings } = useAppStore();
  const [profiles, setProfiles] = useState<ProviderProfile[]>([]);
  const [editing, setEditing] = useState<ProviderProfile | null>(null);
  const [keyInput, setKeyInput] = useState("");
  const [testResult, setTestResult] = useState<ProviderCapabilities | null>(null);
  const [testing, setTesting] = useState(false);
  const [assignments, setAssignments] = useState<TaskModelAssignments | null>(null);

  useEffect(() => {
    (async () => {
      setProfiles(await db.providerProfiles.toArray());
      const a = await getTaskAssignments("_global");
      if (a) setAssignments(a);
    })();
  }, []);

  async function saveProfile(p: ProviderProfile) {
    let apiKeyRef = p.apiKeyReference;
    if (keyInput) {
      try { apiKeyRef = await secrets.put(new URL(p.baseUrl).host, keyInput); }
      catch { apiKeyRef = await secrets.put("custom", keyInput); }
    }
    const toSave: ProviderProfile = { ...p, apiKeyReference: apiKeyRef || p.apiKeyReference };
    if (profiles.find((x) => x.id === p.id)) await db.providerProfiles.put(toSave);
    else { toSave.id = uuid(); await db.providerProfiles.add(toSave); }
    setProfiles(await db.providerProfiles.toArray());
    setEditing(null); setKeyInput("");
  }

  async function runTest(p: ProviderProfile) {
    setTesting(true); setTestResult(null);
    const key = secrets.get(p.apiKeyReference) || keyInput;
    if (!key) {
      setTestResult({ ok: false, supportsStructuredOutput: false, supportsJsonMode: false, supportsTokenUsage: false, testedAt: Date.now(), notes: "No API key." });
      setTesting(false); return;
    }
    const r = await testConnection({ baseUrl: p.baseUrl, apiKey: key, model: p.model, ...p.advanced });
    const caps: ProviderCapabilities = { ...r, testedAt: Date.now() };
    setTestResult(caps);
    await db.providerProfiles.put({ ...p, capabilities: caps });
  }

  async function saveAssignments(a: TaskModelAssignments) {
    setAssignments(a);
    await saveTaskAssignments("_global", a);
  }

  return (
    <div className="content">
      <h1>Settings</h1>
      <section className="card">
        <h3>Reading</h3>
        <div className="grid2">
          <div className="field"><label>Theme</label><select value={settings.theme} onChange={(e) => setSettings({ theme: e.target.value as "light" | "dark" })}><option value="light">Light (warm paper)</option><option value="dark">Dark (charcoal)</option></select></div>
          <div className="field"><label>Font size: {settings.fontSize}px</label><input type="range" min={14} max={28} value={settings.fontSize} onChange={(e) => setSettings({ fontSize: +e.target.value })} /></div>
          <div className="field"><label>Line height: {settings.lineHeight}</label><input type="range" min={1.4} max={2.6} step={0.1} value={settings.lineHeight} onChange={(e) => setSettings({ lineHeight: +e.target.value })} /></div>
          <div className="field"><label>Content width: {settings.contentWidth}px</label><input type="range" min={560} max={920} step={20} value={settings.contentWidth} onChange={(e) => setSettings({ contentWidth: +e.target.value })} /></div>
          <div className="field"><label>HSK level</label><input type="number" min={1} max={9} value={settings.hskLevel} onChange={(e) => setSettings({ hskLevel: +e.target.value })} /></div>
          <div className="field"><label>Learner language</label><select value={settings.learnerLanguage} onChange={(e) => setSettings({ learnerLanguage: e.target.value })}><option value="ru">Russian</option><option value="en">English</option><option value="uk">Ukrainian</option><option value="de">German</option><option value="fr">French</option><option value="es">Spanish</option><option value="ja">Japanese</option><option value="ko">Korean</option></select></div>
          <div className="field"><label><input type="checkbox" checked={settings.rememberApiKeys} onChange={(e) => { const v = e.target.checked; secrets.setRememberApiKeys(v); setSettings({ rememberApiKeys: v }); }} /> Remember API keys (stored in IndexedDB)</label><p className="muted">Off = session only. IndexedDB is not protected from malicious same-origin scripts.</p></div>
          <div className="field"><label><input type="checkbox" checked={settings.reduceMotion} onChange={(e) => setSettings({ reduceMotion: e.target.checked })} /> Reduce motion</label></div>
        </div>
      </section>

      <section className="card">
        <h3>Provider profiles</h3>
        <p className="muted">OpenAI-compatible endpoints. Your configured endpoint receives selected passages, nearby context, book memory, and your answers.</p>
        <div className="row" style={{ margin: "10px 0" }}>
          {PRESETS.map((p) => <button key={p.name} onClick={() => { setEditing({ ...p, id: uuid() }); setKeyInput(""); setTestResult(null); }}>+ {p.name}</button>)}
          <button onClick={() => { setEditing({ id: uuid(), name: "", baseUrl: "", apiKeyReference: "", model: "", advanced: {} }); setKeyInput(""); setTestResult(null); }}>+ Custom</button>
        </div>
        {profiles.length === 0 && <p className="muted">No profiles yet.</p>}
        {profiles.map((p) => (
          <div key={p.id} className="card" style={{ background: "var(--paper)" }}>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <strong>{p.name}</strong>
              <div className="row">
                <button onClick={() => { setEditing(p); setKeyInput(""); setTestResult(p.capabilities ?? null); }}>Edit</button>
                <button className="danger" onClick={async () => { secrets.forget(p.apiKeyReference); await db.providerProfiles.delete(p.id); setProfiles(await db.providerProfiles.toArray()); }}>Delete</button>
              </div>
            </div>
            <p className="muted">{p.baseUrl} · {p.model}</p>
            {p.capabilities && <p className="muted" style={{ fontSize: 12 }}>{p.capabilities.ok ? "✓ connected" : "✗ failed"} · {p.capabilities.supportsStructuredOutput ? "structured" : p.capabilities.supportsJsonMode ? "json mode" : "plain text"} · {p.capabilities.supportsTokenUsage ? "usage" : "no usage"} · {p.capabilities.notes}</p>}
          </div>
        ))}
      </section>

      {editing && (
        <section className="card">
          <h3>{profiles.find((p) => p.id === editing.id) ? "Edit profile" : "New profile"}</h3>
          <div className="grid2">
            <div className="field"><label>Profile name</label><input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></div>
            <div className="field"><label>Base URL</label><input value={editing.baseUrl} onChange={(e) => setEditing({ ...editing, baseUrl: e.target.value })} placeholder="https://api.openai.com/v1" /></div>
            <div className="field"><label>Model</label><input value={editing.model} onChange={(e) => setEditing({ ...editing, model: e.target.value })} /></div>
            <div className="field"><label>API key{!settings.rememberApiKeys && " (session only)"}</label><input type="password" value={keyInput} onChange={(e) => setKeyInput(e.target.value)} placeholder={editing.apiKeyReference ? "•••• (stored)" : "paste key"} /></div>
          </div>
          <details><summary>Advanced</summary>
            <div className="grid2">
              <div className="field"><label>Temperature</label><input type="number" step="0.1" value={editing.advanced.temperature ?? ""} onChange={(e) => setEditing({ ...editing, advanced: { ...editing.advanced, temperature: e.target.value ? +e.target.value : undefined } })} /></div>
              <div className="field"><label>Max output tokens</label><input type="number" value={editing.advanced.maxOutputTokens ?? ""} onChange={(e) => setEditing({ ...editing, advanced: { ...editing.advanced, maxOutputTokens: e.target.value ? +e.target.value : undefined } })} /></div>
              <div className="field"><label>Chat completions path</label><input value={editing.advanced.chatCompletionsPath ?? ""} onChange={(e) => setEditing({ ...editing, advanced: { ...editing.advanced, chatCompletionsPath: e.target.value || undefined } })} placeholder="/v1/chat/completions" /></div>
            </div>
          </details>
          <div className="row" style={{ marginTop: 10 }}>
            <button className="primary" onClick={() => saveProfile(editing)}>Save</button>
            <button onClick={() => runTest(editing)} disabled={testing}>{testing ? "Testing…" : "Test connection"}</button>
            {testResult && <span style={{ fontSize: 13 }}>{testResult.ok ? "✓" : "✗"} {testResult.notes}</span>}
            <button onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </section>
      )}

      <section className="card">
        <h3>Task assignments</h3>
        <p className="muted">Assign different provider profiles to each task.</p>
        <div className="grid2">
          {(["explainProfileId", "assessProfileId", "memoryProfileId", "fallbackProfileId"] as const).map((k) => (
            <div className="field" key={k}>
              <label>{k.replace("ProfileId", "")}</label>
              <select value={assignments?.[k] ?? ""} onChange={(e) => saveAssignments({ ...(assignments ?? { explainProfileId: "", assessProfileId: "", memoryProfileId: "" }), [k]: e.target.value } as TaskModelAssignments)}>
                <option value="">—</option>
                {profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <h3>API keys</h3>
        <div className="row"><button onClick={() => secrets.forgetAll()}>Forget all keys</button></div>
        <p className="muted" style={{ marginTop: 8, fontSize: 12 }}>Keys are never included in logs, errors, exports, URLs, or debug views. IndexedDB is not secure from malicious same-origin JavaScript.</p>
      </section>
    </div>
  );
}
