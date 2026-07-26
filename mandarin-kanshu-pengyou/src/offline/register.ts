import { registerSW } from "virtual:pwa-register";
import { useUiStore } from "../app/ui-store";

export function initOffline(): void {
  const sync = () => useUiStore.getState().setOffline(!navigator.onLine);
  window.addEventListener("online", sync);
  window.addEventListener("offline", sync);
  sync();

  if (import.meta.env.PROD) {
    registerSW({
      immediate: true,
      onNeedRefresh() {
        useUiStore.getState().setUpdateAvailable(true);
      },
      onOfflineReady() {
        // silent — reading works offline
      },
    });
  }
}

export function reloadForUpdate(): void {
  // Activate waiting worker by reloading after explicit user action
  window.location.reload();
}
