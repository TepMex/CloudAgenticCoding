import { useEffect, useState } from "react";
import { useAppStore } from "../app/store";

export function UpdatePrompt() {
  const updateAvailable = useAppStore((s) => s.updateAvailable);
  const setUpdateAvailable = useAppStore((s) => s.setUpdateAvailable);
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    const onControllerChange = () => { window.location.reload(); };
    navigator.serviceWorker.addEventListener("controllerchange", onControllerChange);
    const t = setInterval(async () => {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg?.waiting) setUpdateAvailable(true);
    }, 60000);
    return () => {
      clearInterval(t);
      navigator.serviceWorker.removeEventListener("controllerchange", onControllerChange);
    };
  }, [setUpdateAvailable]);

  if (!updateAvailable || hidden) return null;
  return (
    <div className="card" style={{ position: "fixed", bottom: 16, right: 16, zIndex: 100, maxWidth: 360 }}>
      <h3>Update available</h3>
      <p className="muted">A new version is ready. Reload to apply. Unfinished answers will be kept if you don't reload now.</p>
      <div className="row">
        <button className="primary" onClick={async () => {
          const reg = await navigator.serviceWorker.getRegistration();
          reg?.waiting?.postMessage({ type: "SKIP_WAITING" });
        }}>Reload</button>
        <button onClick={() => setHidden(true)}>Later</button>
      </div>
    </div>
  );
}
