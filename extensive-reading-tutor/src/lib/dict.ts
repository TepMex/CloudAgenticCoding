import { glossIdFor } from "./text.ts";
import type { Gloss, GlossKind, WordStatus } from "./types.ts";

export function makeGloss(
  partial: {
    hanzi: string;
    pinyin: string;
    gloss: string;
    kind?: GlossKind;
    example?: string;
    exampleGloss?: string;
    status?: WordStatus;
    lastSeen?: string;
  },
): Gloss {
  return {
    kind: partial.kind ?? "word",
    example: partial.example ?? "",
    exampleGloss: partial.exampleGloss ?? "",
    status: partial.status ?? "learning",
    lastSeen: partial.lastSeen ?? "ещё не встречалось",
    hanzi: partial.hanzi,
    pinyin: partial.pinyin,
    gloss: partial.gloss,
    id: glossIdFor(partial.hanzi),
  };
}

/** Frequent words so a pasted passage still has something to tap offline. */
export const MINI_DICT: Gloss[] = [
  ["忽然", "hūrán", "вдруг"],
  ["觉得", "juéde", "чувствовать, казаться"],
  ["只有", "zhǐyǒu", "только, всего лишь"],
  ["人们", "rénmen", "люди"],
  ["已经", "yǐjīng", "уже"],
  ["时候", "shíhou", "момент, когда"],
  ["好像", "hǎoxiàng", "как будто"],
  ["地方", "dìfang", "место"],
  ["日子", "rìzi", "дни, жизнь"],
  ["后来", "hòulái", "потом, впоследствии"],
  ["一直", "yìzhí", "всё время, непрерывно"],
  ["房间", "fángjiān", "комната"],
  ["办公室", "bàngōngshì", "кабинет, офис"],
  ["公司", "gōngsī", "компания"],
  ["工作", "gōngzuò", "работа"],
  ["宇宙", "yǔzhòu", "вселенная"],
  ["沉默", "chénmò", "молчание"],
  ["学会", "xuéhuì", "научиться"],
  ["寒风", "hánfēng", "холодный ветер"],
  ["噪声", "zàoshēng", "шум, шорох помех"],
  ["火车", "huǒchē", "поезд"],
  ["行李", "xíngli", "багаж"],
  ["数字", "shùzì", "цифра, число"],
  ["安心", "ānxīn", "быть спокойным"],
  ["老人", "lǎorén", "пожилой человек"],
  ["耳机", "ěrjī", "наушники"],
  ["熟悉", "shúxī", "знакомый"],
  ["陈旧", "chénjiù", "старый, затхлый"],
  ["空置", "kòngzhì", "пустовать"],
  ["堆满", "duīmǎn", "быть заваленным"],
  ["地板", "dìbǎn", "пол"],
  ["天花板", "tiānhuābǎn", "потолок"],
  ["仿佛", "fǎngfú", "словно"],
  ["图书馆", "túshūguǎn", "библиотека"],
  ["心慌", "xīnhuāng", "тревожно на душе"],
  ["安排", "ānpái", "назначить, устроить"],
  ["观测", "guāncè", "наблюдение"],
  ["进站", "jìnzhàn", "прибывать на станцию"],
].map(([hanzi, pinyin, gloss]) => makeGloss({ hanzi: hanzi!, pinyin: pinyin!, gloss: gloss! }));
