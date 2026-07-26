import { useEffect } from "react";
import { ensureDefaults, db } from "./db/database";
import { useUiStore } from "./app/ui-store";
import { LibraryView } from "./books/LibraryView";
import { ReaderView } from "./reader/ReaderView";
import { SettingsView } from "./settings/SettingsView";
import { MemoryView } from "./memory/MemoryView";
import { StatsView } from "./statistics/StatsView";
import { initOffline, reloadForUpdate } from "./offline/register";
import { applyCspMeta } from "./security/csp";

export default function App() {
  const view = useUiStore((s) => s.view);
  const updateAvailable = useUiStore((s) => s.updateAvailable);
  const appearance = useUiStore((s) => s.appearance);
  const setAppearance = useUiStore((s) => s.setAppearance);
  const offline = useUiStore((s) => s.offline);

  useEffect(() => {
    applyCspMeta();
    initOffline();
    void ensureDefaults().then(async () => {
      const s = await db.settings.get("app");
      if (s) setAppearance(s.appearance);
    });
  }, [setAppearance]);

  useEffect(() => {
    document.documentElement.dataset.appearance = appearance;
  }, [appearance]);

  return (
    <div className={`app-shell appearance-${appearance}`}>
      {updateAvailable && (
        <div className="update-banner" role="status">
          <span>Update available. Reload when you finish your current answer.</span>
          <button type="button" onClick={reloadForUpdate}>
            Reload
          </button>
        </div>
      )}
      {offline && view !== "reader" && (
        <div className="offline-banner" role="status">
          You are offline. Reading and past assistance still work; LLM actions are disabled.
        </div>
      )}
      {view === "library" && <LibraryView />}
      {view === "reader" && <ReaderView />}
      {view === "settings" && <SettingsView />}
      {view === "memory" && <MemoryView />}
      {view === "stats" && <StatsView />}
    </div>
  );
}
