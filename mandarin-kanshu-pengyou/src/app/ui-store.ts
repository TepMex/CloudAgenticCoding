import { create } from "zustand";
import type { AssistanceLevel, Appearance } from "../shared/domain";
import type { ExpandedSelection } from "../reader/selection/sentences";

export type AppView = "library" | "reader" | "settings" | "memory" | "stats";

export type ActiveRequest = {
  id: string;
  kind: "explain" | "understand" | "memory" | "companion" | "test" | "initial_memory";
  controller: AbortController;
};

export type AssistanceCardState = {
  id: string;
  annotationId?: string;
  kind: "explain" | "understand" | "companion" | "error";
  collapsed: boolean;
  passageInView: boolean;
  title: string;
  body?: string;
  level?: AssistanceLevel;
  explanationId?: string;
  attemptId?: string;
  rawError?: string;
  loading?: boolean;
};

type UiState = {
  view: AppView;
  activeBookId: string | null;
  activeSpineItemId: string | null;
  offline: boolean;
  updateAvailable: boolean;
  appearance: Appearance;
  selection: ExpandedSelection | null;
  selectionChapterText: string;
  showLongPassageWarning: boolean;
  toolbarVisible: boolean;
  toolbarX: number;
  toolbarY: number;
  cards: AssistanceCardState[];
  activeRequest: ActiveRequest | null;
  panelOpen: boolean;
  revealNativeQuestion: boolean;
  errorBanner: string | null;
  setView: (v: AppView) => void;
  openBook: (bookId: string, spineItemId: string) => void;
  setSpine: (spineItemId: string) => void;
  setOffline: (v: boolean) => void;
  setUpdateAvailable: (v: boolean) => void;
  setAppearance: (a: Appearance) => void;
  setSelection: (sel: ExpandedSelection | null, chapterText: string) => void;
  setToolbar: (visible: boolean, x?: number, y?: number) => void;
  upsertCard: (card: AssistanceCardState) => void;
  updateCard: (id: string, patch: Partial<AssistanceCardState>) => void;
  setActiveRequest: (req: ActiveRequest | null) => void;
  cancelActiveRequest: () => void;
  setPanelOpen: (v: boolean) => void;
  setRevealNativeQuestion: (v: boolean) => void;
  setErrorBanner: (msg: string | null) => void;
};

export const useUiStore = create<UiState>((set, get) => ({
  view: "library",
  activeBookId: null,
  activeSpineItemId: null,
  offline: !navigator.onLine,
  updateAvailable: false,
  appearance: "light",
  selection: null,
  selectionChapterText: "",
  showLongPassageWarning: false,
  toolbarVisible: false,
  toolbarX: 0,
  toolbarY: 0,
  cards: [],
  activeRequest: null,
  panelOpen: false,
  revealNativeQuestion: false,
  errorBanner: null,
  setView: (view) => set({ view }),
  openBook: (bookId, spineItemId) =>
    set({ view: "reader", activeBookId: bookId, activeSpineItemId: spineItemId }),
  setSpine: (spineItemId) => set({ activeSpineItemId: spineItemId }),
  setOffline: (offline) => set({ offline }),
  setUpdateAvailable: (updateAvailable) => set({ updateAvailable }),
  setAppearance: (appearance) => set({ appearance }),
  setSelection: (selection, selectionChapterText) =>
    set({
      selection,
      selectionChapterText,
      showLongPassageWarning: Boolean(selection?.exceedsSoftLimit),
    }),
  setToolbar: (toolbarVisible, toolbarX = 0, toolbarY = 0) =>
    set({ toolbarVisible, toolbarX, toolbarY }),
  upsertCard: (card) =>
    set((s) => {
      const idx = s.cards.findIndex((c) => c.id === card.id);
      if (idx >= 0) {
        const cards = [...s.cards];
        cards[idx] = { ...cards[idx], ...card };
        return { cards, panelOpen: true };
      }
      return { cards: [...s.cards, card], panelOpen: true };
    }),
  updateCard: (id, patch) =>
    set((s) => ({
      cards: s.cards.map((c) => (c.id === id ? { ...c, ...patch } : c)),
    })),
  setActiveRequest: (activeRequest) => set({ activeRequest }),
  cancelActiveRequest: () => {
    const req = get().activeRequest;
    req?.controller.abort();
    set({ activeRequest: null });
  },
  setPanelOpen: (panelOpen) => set({ panelOpen }),
  setRevealNativeQuestion: (revealNativeQuestion) => set({ revealNativeQuestion }),
  setErrorBanner: (errorBanner) => set({ errorBanner }),
}));
