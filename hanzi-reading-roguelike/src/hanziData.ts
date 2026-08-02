import rshPayload from "./data/rshLists.json";

export type HanziEntry = {
  frame: number;
  hanzi: string;
  keyword: string;
  /** Toneless pinyin readings accepted as correct answers. */
  pinyin: string[];
};

export type RthList = {
  id: string;
  name: string;
  label: string;
  lesson: number;
  entries: HanziEntry[];
};

type RshPayload = {
  lists: RthList[];
};

const payload = rshPayload as RshPayload;

/** Ordered RSH/RTH lists used for progressive enemy spawning. */
export const RTH_LISTS: RthList[] = payload.lists;

/** Flat seed used by tests / fallbacks — first list only. */
export const HANZI_DATA: HanziEntry[] = RTH_LISTS[0]?.entries ?? [];

export function getRthList(index: number): RthList | undefined {
  return RTH_LISTS[index];
}

export function rthListCount(): number {
  return RTH_LISTS.length;
}
