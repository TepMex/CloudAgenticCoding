export type HanziEntry = {
  hanzi: string;
  pinyin: string;
  image: string;
};

/** Seed vocabulary — first 20 enemies (pinyin without tone marks). */
export const HANZI_DATA: HanziEntry[] = [
  { hanzi: "我", pinyin: "wo", image: "我.png" },
  { hanzi: "你", pinyin: "ni", image: "你.png" },
  { hanzi: "好", pinyin: "hao", image: "好.png" },
  { hanzi: "是", pinyin: "shi", image: "是.png" },
  { hanzi: "不", pinyin: "bu", image: "不.png" },
  { hanzi: "了", pinyin: "le", image: "了.png" },
  { hanzi: "的", pinyin: "de", image: "的.png" },
  { hanzi: "一", pinyin: "yi", image: "一.png" },
  { hanzi: "二", pinyin: "er", image: "二.png" },
  { hanzi: "三", pinyin: "san", image: "三.png" },
  { hanzi: "人", pinyin: "ren", image: "人.png" },
  { hanzi: "大", pinyin: "da", image: "大.png" },
  { hanzi: "小", pinyin: "xiao", image: "小.png" },
  { hanzi: "中", pinyin: "zhong", image: "中.png" },
  { hanzi: "天", pinyin: "tian", image: "天.png" },
  { hanzi: "水", pinyin: "shui", image: "水.png" },
  { hanzi: "火", pinyin: "huo", image: "火.png" },
  { hanzi: "山", pinyin: "shan", image: "山.png" },
  { hanzi: "上", pinyin: "shang", image: "上.png" },
  { hanzi: "下", pinyin: "xia", image: "下.png" },
];
