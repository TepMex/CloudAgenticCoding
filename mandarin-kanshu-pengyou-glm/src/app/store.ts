import { create } from "zustand";
import type { Settings } from "../shared/domain";
import { DEFAULT_SETTINGS } from "../shared/domain";
import { getSettings, saveSettings } from "../db/database";

type ReaderUiState = {
  settings: Settings; loaded: boolean; online: boolean; updateAvailable: boolean;
  setSettings: (patch: Partial<Settings>) => Promise<void>;
  setOnline: (v: boolean) => void;
  setUpdateAvailable: (v: boolean) => void;
  init: () => Promise<void>;
};

export const useAppStore = create<ReaderUiState>((set, get) => ({
  settings: DEFAULT_SETTINGS, loaded: false, online: true, updateAvailable: false,
  async setSettings(patch) { set({ settings: { ...get().settings, ...patch } }); await saveSettings(patch); },
  setOnline(v) { set({ online: v }); },
  setUpdateAvailable(v) { set({ updateAvailable: v }); },
  async init() { const s = await getSettings(); set({ settings: s, loaded: true }); },
}));
