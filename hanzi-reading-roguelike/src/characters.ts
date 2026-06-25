export type CharacterId = "caishen" | "zhu-bajie" | "guan-yu" | "sun-wukong";

export type CharacterDef = {
  id: CharacterId;
  textureKey: CharacterId;
  /** Hanzi overlay offset from sprite center, in source-cell pixels (768×512). */
  hanziOffsetX: number;
  hanziOffsetY: number;
};

/** Source cell size for the 2×2 character grid. */
export const CHARACTER_CELL_WIDTH = 768;
export const CHARACTER_CELL_HEIGHT = 512;

export const CHARACTERS: CharacterDef[] = [
  { id: "caishen", textureKey: "caishen", hanziOffsetX: 68, hanziOffsetY: 38 },
  { id: "zhu-bajie", textureKey: "zhu-bajie", hanziOffsetX: -27, hanziOffsetY: 66 },
  { id: "guan-yu", textureKey: "guan-yu", hanziOffsetX: 49, hanziOffsetY: 66 },
  { id: "sun-wukong", textureKey: "sun-wukong", hanziOffsetX: -49, hanziOffsetY: 53 },
];

export function pickRandomCharacter(): CharacterDef {
  return CHARACTERS[Math.floor(Math.random() * CHARACTERS.length)]!;
}

export const CHARACTER_TEXTURE_URLS: Record<CharacterId, string> = {
  caishen: new URL("../assets/characters/caishen.png", import.meta.url).href,
  "zhu-bajie": new URL("../assets/characters/zhu-bajie.png", import.meta.url).href,
  "guan-yu": new URL("../assets/characters/guan-yu.png", import.meta.url).href,
  "sun-wukong": new URL("../assets/characters/sun-wukong.png", import.meta.url).href,
};
