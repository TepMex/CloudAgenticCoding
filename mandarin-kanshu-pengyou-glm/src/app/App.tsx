import { useEffect, useState } from "react";
import { useAppStore } from "./store";
import { Library } from "./views/Library";
import { ReaderView } from "./views/ReaderView";
import { SettingsView } from "./views/SettingsView";
import { StatsView } from "./views/StatsView";
import { MemoryView } from "./views/MemoryView";
import { UpdatePrompt } from "../offline/UpdatePrompt";
import { registerSw } from "../offline/sw-register";

type Route =
  | { name: "library" }
  | { name: "reader"; bookId: string }
  | { name: "settings" }
  | { name: "stats"; bookId: string }
  | { name: "memory"; bookId: string };

export function App() {
  const init = useAppStore((s) => s.init);
  const loaded = useAppStore((s) => s.loaded);
  const online = useAppStore((s) => s.online);
  const setOnline = useAppStore((s) => s.setOnline);
  const [route, setRoute] = useState<Route>({ name: "library" });

  useEffect(() => {
    init();
    registerSw();
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, [init, setOnline]);

  if (!loaded) return <div className="content"><p>Loading…</p></div>;

  return (
    <div className="app-shell">
      <nav className="sidebar" aria-label="Main navigation">
        <h2>看书朋友</h2>
        <button className={route.name === "library" ? "nav-active" : ""} onClick={() => setRoute({ name: "library" })}>📚 Library</button>
        {route.name === "reader" && (
          <>
            <button className="nav-active">📖 Reading</button>
            <button onClick={() => setRoute({ name: "stats", bookId: route.bookId })}>📊 Stats</button>
            <button onClick={() => setRoute({ name: "memory", bookId: route.bookId })}>🧠 Memory</button>
          </>
        )}
        <button className={route.name === "settings" ? "nav-active" : ""} onClick={() => setRoute({ name: "settings" })}>⚙️ Settings</button>
        <div style={{ marginTop: "auto", fontSize: 12, color: "var(--ink-soft)" }}>{online ? "● online" : "○ offline"}</div>
      </nav>
      <main className="main">
        <UpdatePrompt />
        {route.name === "library" && <Library onOpen={(id) => setRoute({ name: "reader", bookId: id })} />}
        {route.name === "reader" && <ReaderView bookId={route.bookId} />}
        {route.name === "settings" && <SettingsView />}
        {route.name === "stats" && <StatsView bookId={route.bookId} onBack={() => setRoute({ name: "reader", bookId: route.bookId })} />}
        {route.name === "memory" && <MemoryView bookId={route.bookId} onBack={() => setRoute({ name: "reader", bookId: route.bookId })} />}
      </main>
    </div>
  );
}
