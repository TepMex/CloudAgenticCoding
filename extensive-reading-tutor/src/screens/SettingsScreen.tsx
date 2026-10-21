import { ChevronRight } from "lucide-react";
import { useState } from "react";
import { Header, Sheet } from "../components.tsx";
import { chatCompletionsUrl, providerLabel } from "../lib/openai.ts";
import { endpointReady, defaultSettings } from "../lib/settings.ts";
import { useApp } from "../state.tsx";
import type { Settings } from "../lib/types.ts";

const CHOICES = {
  hsk: ["HSK 3", "HSK 4", "HSK 5", "HSK 6"],
  explainLanguage: ["Русский", "English", "中文"],
  difficulty: ["Лёгкая", "Средняя", "Высокая"],
  depth: ["Кратко", "Обычно", "Подробно"],
} as const;

type ChoiceKey = keyof typeof CHOICES;

const CHOICE_LABEL: Record<ChoiceKey, string> = {
  hsk: "Целевой уровень",
  explainLanguage: "Язык объяснений",
  difficulty: "Предпочтительная сложность",
  depth: "Глубина объяснений",
};

export function SettingsScreen() {
  const { state, go, saveSettings, testConnection, resetData } = useApp();
  const [open, setOpen] = useState(() => !endpointReady(state.settings));
  const [showKey, setShowKey] = useState(false);
  const [picker, setPicker] = useState<ChoiceKey | null>(null);
  const [status, setStatus] = useState<{ ok: boolean; text: string } | null>(null);
  const [testing, setTesting] = useState(false);
  const settings = state.settings;
  const patch = (partial: Partial<Settings>) => saveSettings({ ...settings, ...partial });
  const connected = endpointReady(settings);
  let endpoint = "";
  if (settings.apiBaseUrl.trim()) {
    try {
      endpoint = chatCompletionsUrl(settings.apiBaseUrl);
    } catch {
      endpoint = "";
    }
  }
  const provider = connected ? providerLabel(settings.apiBaseUrl) : "OpenAI-compatible";
  const modelLine = connected ? settings.model || defaultSettings.model : `${settings.model || defaultSettings.model} · не подключено`;

  return (
    <div className="screen" data-screen="settings">
      <Header title="Настройки" onBack={() => go({ name: "profile" })} />
      <div className="content">
        <div className="section-label">ИИ-модель</div>
        <div className="settings-card">
          <button className="set-row" type="button" onClick={() => setOpen(value => !value)}>
            <span>
              <b>{provider}</b>
              <small>{modelLine}</small>
            </span>
            <span className="set-value">{open ? "Свернуть" : "Изменить"}</span>
          </button>
          {open && (
            <form
              onSubmit={event => {
                event.preventDefault();
                setTesting(true);
                setStatus(null);
                void testConnection()
                  .then(text => setStatus({ ok: true, text: `Соединение есть. Ответ: ${text.slice(0, 80)}` }))
                  .catch(error => setStatus({ ok: false, text: error instanceof Error ? error.message : "Ошибка" }))
                  .finally(() => setTesting(false));
              }}
            >
              <p className="help">
                Любой OpenAI-compatible Chat Completions: OpenAI, OpenRouter, Ollama или свой сервер. Запрос уходит из браузера. Адрес можно указать как https://api.openai.com, с /v1 на конце или целиком до /chat/completions — сегмент /v1 не дублируется. Ключ хранится только в этом браузере.
              </p>
              <label className="field">
                <span>Базовый URL</span>
                <input
                  name="apiBaseUrl"
                  value={settings.apiBaseUrl}
                  placeholder="https://api.openai.com"
                  inputMode="url"
                  onChange={event => patch({ apiBaseUrl: event.target.value })}
                />
              </label>
              <label className="field">
                <span>Ключ API</span>
                <input
                  name="apiKey"
                  value={settings.apiKey}
                  type={showKey ? "text" : "password"}
                  autoComplete="off"
                  placeholder="Можно оставить пустым"
                  onChange={event => patch({ apiKey: event.target.value })}
                />
              </label>
              <button className="linkish" type="button" onClick={() => setShowKey(value => !value)}>
                {showKey ? "Скрыть ключ" : "Показать ключ"}
              </button>
              <label className="field">
                <span>Модель</span>
                <input name="model" value={settings.model} placeholder="gpt-4o" onChange={event => patch({ model: event.target.value })} />
              </label>
              {endpoint ? <p className="muted endpoint">Запрос: {endpoint}</p> : null}
              <button className="btn-primary" type="submit" disabled={!endpointReady(settings) || testing}>
                {testing ? "Проверяю…" : "Проверить соединение"}
              </button>
              {status && <p className={status.ok ? "status-ok" : "status-bad"}>{status.text}</p>}
            </form>
          )}
        </div>

        <div className="section-label">Параметры обучения</div>
        <div className="settings-card">
          {(Object.keys(CHOICES) as ChoiceKey[]).map(key => (
            <button key={key} className="set-row" type="button" onClick={() => setPicker(key)}>
              <span>{CHOICE_LABEL[key]}</span>
              <span className="set-value">
                {settings[key]}
                <ChevronRight />
              </span>
            </button>
          ))}
          <div className="set-row">
            <span>
              <b>Больше вопросов на понимание</b>
            </span>
            <button type="button" className={settings.moreComprehension ? "switch on" : "switch"} aria-pressed={settings.moreComprehension} onClick={() => patch({ moreComprehension: !settings.moreComprehension })}>
              <i />
            </button>
          </div>
          <div className="set-row">
            <span>
              <b>Иногда проверять старые темы</b>
            </span>
            <button type="button" className={settings.reviewOld ? "switch on" : "switch"} aria-pressed={settings.reviewOld} onClick={() => patch({ reviewOld: !settings.reviewOld })}>
              <i />
            </button>
          </div>
        </div>
        <button className="btn-ghost" type="button" onClick={resetData}>
          Сбросить локальные данные
        </button>
      </div>
      {picker && (
        <Sheet title={CHOICE_LABEL[picker]} onClose={() => setPicker(null)}>
          <div className="stack-gap">
            {CHOICES[picker].map(option => (
              <button
                key={option}
                type="button"
                className={settings[picker] === option ? "opt on" : "opt"}
                onClick={() => {
                  patch({ [picker]: option });
                  setPicker(null);
                }}
              >
                {option}
              </button>
            ))}
          </div>
        </Sheet>
      )}
    </div>
  );
}
