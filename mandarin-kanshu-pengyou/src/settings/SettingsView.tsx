import { useEffect, useState } from "react";
import { db, DEFAULT_SETTINGS } from "../db/database";
import type { AppSettings, ProviderProfile, TaskModelAssignments } from "../shared/domain";
import { createId } from "../shared/id";
import {
  forgetApiKey,
  getApiKey,
  secretReference,
  setApiKey,
} from "../providers/secrets";
import { testProviderConnection } from "../providers/client";
import { useUiStore } from "../app/ui-store";
import { safeErrorMessage } from "../security/redact";

const PRESETS: Omit<ProviderProfile, "id" | "apiKeyReference">[] = [
  {
    name: "Local OpenAI-compatible",
    baseUrl: "http://127.0.0.1:1234",
    model: "local-model",
    advanced: { temperature: 0.3, maxOutputTokens: 2048 },
  },
  {
    name: "Hosted lightweight",
    baseUrl: "https://api.openai.com",
    model: "gpt-4o-mini",
    advanced: { temperature: 0.3, maxOutputTokens: 2048 },
  },
  {
    name: "Stronger primary",
    baseUrl: "https://api.openai.com",
    model: "gpt-4o",
    advanced: { temperature: 0.2, maxOutputTokens: 4096 },
  },
];

export function SettingsView() {
  const setView = useUiStore((s) => s.setView);
  const setAppearance = useUiStore((s) => s.setAppearance);
  const offline = useUiStore((s) => s.offline);
  const [settings, setSettings] = useState<AppSettings>(DEFAULT_SETTINGS);
  const [profiles, setProfiles] = useState<ProviderProfile[]>([]);
  const [assignments, setAssignments] = useState<TaskModelAssignments>({
    explainProfileId: "",
    assessProfileId: "",
    memoryProfileId: "",
  });
  const [draftKeys, setDraftKeys] = useState<Record<string, string>>({});
  const [showAdvanced, setShowAdvanced] = useState<Record<string, boolean>>({});
  const [status, setStatus] = useState<string | null>(null);

  const reload = async () => {
    const s = (await db.settings.get("app")) ?? DEFAULT_SETTINGS;
    setSettings(s);
    setAppearance(s.appearance);
    setProfiles(await db.providerProfiles.toArray());
    const a = await db.taskModelAssignments.get("default");
    if (a) setAssignments(a);
  };

  useEffect(() => {
    void reload();
  }, []);

  const saveSettings = async (patch: Partial<AppSettings>) => {
    const next = { ...settings, ...patch, id: "app" as const };
    setSettings(next);
    await db.settings.put(next);
    if (patch.appearance) setAppearance(patch.appearance);
  };

  const addPreset = async (preset: (typeof PRESETS)[number]) => {
    const id = createId("prov");
    const ref = secretReference(preset.baseUrl, "");
    const profile: ProviderProfile = {
      id,
      apiKeyReference: ref,
      ...preset,
    };
    await db.providerProfiles.put(profile);
    await reload();
  };

  const saveProfile = async (profile: ProviderProfile) => {
    await db.providerProfiles.put(profile);
    const key = draftKeys[profile.id];
    if (key !== undefined) {
      const ref = secretReference(profile.baseUrl, key || "empty");
      const updated = { ...profile, apiKeyReference: ref };
      await db.providerProfiles.put(updated);
      if (key) {
        await setApiKey(ref, profile.baseUrl, key, settings.rememberApiKeys);
      }
    }
    await reload();
    setStatus("Profile saved");
  };

  const runTest = async (profile: ProviderProfile) => {
    if (offline) {
      setStatus("Offline — cannot test connection");
      return;
    }
    const key = draftKeys[profile.id] ?? (await getApiKey(profile.apiKeyReference));
    if (!key) {
      setStatus("Enter an API key first");
      return;
    }
    const ref = secretReference(profile.baseUrl, key);
    await setApiKey(ref, profile.baseUrl, key, settings.rememberApiKeys);
    const updated = { ...profile, apiKeyReference: ref };
    await db.providerProfiles.put(updated);
    setStatus("Testing…");
    try {
      const caps = await testProviderConnection(updated);
      setStatus(
        caps.corsOk
          ? `OK · JSON ${caps.jsonTextOk ? "yes" : "no"} · usage ${caps.tokenUsageAvailable ? "yes" : "unavailable"}`
          : `CORS/network failed: ${caps.lastError ?? "unknown"}`,
      );
      await reload();
    } catch (e) {
      setStatus(safeErrorMessage(e));
    }
  };

  return (
    <div className="page settings-page">
      <header className="page-header">
        <div>
          <p className="brand">看书朋友</p>
          <h1>Settings</h1>
        </div>
        <button type="button" className="ghost" onClick={() => setView("library")}>
          Back
        </button>
      </header>

      <section className="settings-section">
        <h2>Reading</h2>
        <label>
          Appearance
          <select
            value={settings.appearance}
            onChange={(e) =>
              void saveSettings({ appearance: e.target.value as "light" | "dark" })
            }
          >
            <option value="light">Day</option>
            <option value="dark">Dark</option>
          </select>
        </label>
        <label>
          Font size ({settings.fontSizePx}px)
          <input
            type="range"
            min={14}
            max={32}
            value={settings.fontSizePx}
            onChange={(e) => void saveSettings({ fontSizePx: Number(e.target.value) })}
          />
        </label>
        <label>
          Line height ({settings.lineHeight})
          <input
            type="range"
            min={1.4}
            max={2.2}
            step={0.05}
            value={settings.lineHeight}
            onChange={(e) => void saveSettings({ lineHeight: Number(e.target.value) })}
          />
        </label>
        <label>
          Content width ({settings.contentWidthCh}ch)
          <input
            type="range"
            min={28}
            max={64}
            value={settings.contentWidthCh}
            onChange={(e) =>
              void saveSettings({ contentWidthCh: Number(e.target.value) })
            }
          />
        </label>
        <label>
          Learner language (BCP-47 / name)
          <input
            value={settings.learnerLanguage}
            onChange={(e) => void saveSettings({ learnerLanguage: e.target.value })}
          />
        </label>
        <label>
          HSK level target
          <input
            type="number"
            min={1}
            max={6}
            value={settings.hskLevel}
            onChange={(e) => void saveSettings({ hskLevel: Number(e.target.value) })}
          />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={settings.companionEnabled}
            onChange={(e) => void saveSettings({ companionEnabled: e.target.checked })}
          />
          Enable companion reactions
        </label>
      </section>

      <section className="settings-section">
        <h2>API keys</h2>
        <p className="hint">
          Keys stay in this browser session by default. IndexedDB is not safe from malicious
          same-origin scripts — only opt in if you accept that risk.
        </p>
        <p className="hint">
          Your configured endpoint receives selected passages, nearby paragraphs, compact book
          memory, and comprehension answers. Only endpoints that allow direct browser (CORS)
          access are supported.
        </p>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={settings.rememberApiKeys}
            onChange={(e) => void saveSettings({ rememberApiKeys: e.target.checked })}
          />
          Remember API keys on this device
        </label>
      </section>

      <section className="settings-section">
        <h2>Provider profiles</h2>
        <div className="preset-row">
          {PRESETS.map((p) => (
            <button key={p.name} type="button" className="ghost" onClick={() => void addPreset(p)}>
              Add “{p.name}”
            </button>
          ))}
        </div>

        {profiles.map((p) => (
          <div key={p.id} className="profile-editor">
            <label>
              Name
              <input
                value={p.name}
                onChange={(e) =>
                  setProfiles((list) =>
                    list.map((x) => (x.id === p.id ? { ...x, name: e.target.value } : x)),
                  )
                }
              />
            </label>
            <label>
              Base URL
              <input
                value={p.baseUrl}
                onChange={(e) =>
                  setProfiles((list) =>
                    list.map((x) => (x.id === p.id ? { ...x, baseUrl: e.target.value } : x)),
                  )
                }
              />
            </label>
            <label>
              API key
              <input
                type="password"
                autoComplete="off"
                placeholder="••••••••"
                value={draftKeys[p.id] ?? ""}
                onChange={(e) =>
                  setDraftKeys((k) => ({ ...k, [p.id]: e.target.value }))
                }
              />
            </label>
            <label>
              Model
              <input
                value={p.model}
                onChange={(e) =>
                  setProfiles((list) =>
                    list.map((x) => (x.id === p.id ? { ...x, model: e.target.value } : x)),
                  )
                }
              />
            </label>
            <button
              type="button"
              className="ghost"
              onClick={() =>
                setShowAdvanced((s) => ({ ...s, [p.id]: !s[p.id] }))
              }
            >
              {showAdvanced[p.id] ? "Hide" : "Show"} advanced
            </button>
            {showAdvanced[p.id] && (
              <>
                <label>
                  Temperature
                  <input
                    type="number"
                    step={0.1}
                    value={p.advanced.temperature ?? 0.3}
                    onChange={(e) =>
                      setProfiles((list) =>
                        list.map((x) =>
                          x.id === p.id
                            ? {
                                ...x,
                                advanced: {
                                  ...x.advanced,
                                  temperature: Number(e.target.value),
                                },
                              }
                            : x,
                        ),
                      )
                    }
                  />
                </label>
                <label>
                  Max output tokens
                  <input
                    type="number"
                    value={p.advanced.maxOutputTokens ?? 2048}
                    onChange={(e) =>
                      setProfiles((list) =>
                        list.map((x) =>
                          x.id === p.id
                            ? {
                                ...x,
                                advanced: {
                                  ...x.advanced,
                                  maxOutputTokens: Number(e.target.value),
                                },
                              }
                            : x,
                        ),
                      )
                    }
                  />
                </label>
                <label>
                  Chat completions path
                  <input
                    value={p.advanced.chatCompletionsPath ?? "/v1/chat/completions"}
                    onChange={(e) =>
                      setProfiles((list) =>
                        list.map((x) =>
                          x.id === p.id
                            ? {
                                ...x,
                                advanced: {
                                  ...x.advanced,
                                  chatCompletionsPath: e.target.value,
                                },
                              }
                            : x,
                        ),
                      )
                    }
                  />
                </label>
              </>
            )}
            {p.capabilities && (
              <p className="hint">
                Last test: CORS {p.capabilities.corsOk ? "ok" : "fail"} · structured{" "}
                {p.capabilities.structuredOutputOk ? "ok" : "no"} · usage{" "}
                {p.capabilities.tokenUsageAvailable ? "ok" : "unavailable"}
                {p.capabilities.lastError ? ` · ${p.capabilities.lastError}` : ""}
              </p>
            )}
            <div className="card-actions">
              <button type="button" onClick={() => void saveProfile(p)}>
                Save
              </button>
              <button type="button" onClick={() => void runTest(p)}>
                Test connection
              </button>
              <button
                type="button"
                className="ghost"
                onClick={() => void forgetApiKey(p.apiKeyReference).then(reload)}
              >
                Forget key
              </button>
              <button
                type="button"
                className="ghost danger"
                onClick={() =>
                  void db.providerProfiles.delete(p.id).then(reload)
                }
              >
                Remove profile
              </button>
            </div>
          </div>
        ))}
      </section>

      <section className="settings-section">
        <h2>Task model assignments</h2>
        {(
          [
            ["explainProfileId", "Explain"],
            ["assessProfileId", "Assess"],
            ["memoryProfileId", "Memory"],
            ["fallbackProfileId", "Fallback (optional)"],
          ] as const
        ).map(([key, label]) => (
          <label key={key}>
            {label}
            <select
              value={(assignments as Record<string, string | undefined>)[key] ?? ""}
              onChange={(e) => {
                const next = { ...assignments, [key]: e.target.value || undefined };
                setAssignments(next as TaskModelAssignments);
                void db.taskModelAssignments.put({ id: "default", ...next });
              }}
            >
              <option value="">—</option>
              {profiles.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({p.model})
                </option>
              ))}
            </select>
          </label>
        ))}
      </section>

      {status && <p className="status-line" role="status">{status}</p>}
    </div>
  );
}
