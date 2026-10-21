import { MINI_DICT, makeGloss } from "./dict.ts";
import { buildOfflineLesson } from "./lesson.ts";
import { tokenize } from "./text.ts";
import type { Book, Gloss, Lesson, Note, Question, Skill } from "./types.ts";

export const SEED_VERSION = 3;
export const HERO_BOOK_ID = "book-tgcf";

export const HERO_PASSAGE =
  "我推开那扇熟悉的门，一股陈旧的书香味扑面而来。这里曾是华深的办公室，后来空置了很久。房间里堆满了书，从地板一直堆到天花板，仿佛一座小小的图书馆。我一时有些心慌，心想：难道公司要把我安排在图书馆工作？";

const heroGlosses: Gloss[] = [
  makeGloss({
    hanzi: "扑面而来",
    pinyin: "pū miàn ér lái",
    gloss: "(запах, ветер и т.п.) резко, прямо на тебя, накрывает",
    kind: "idiom",
    example: "一阵花香扑面而来。",
    exampleGloss: "Внезапно на меня повеяло ароматом цветов.",
    status: "repeat",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "陈旧",
    pinyin: "chénjiù",
    gloss: "старый, затхлый; о вещах и о памяти",
    kind: "word",
    example: "陈旧的记忆忽然醒了。",
    exampleGloss: "Старые воспоминания вдруг ожили.",
    status: "learning",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "熟悉",
    pinyin: "shúxī",
    gloss: "знакомый, привычный",
    kind: "word",
    status: "known",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "空置",
    pinyin: "kòngzhì",
    gloss: "стоять пустым, не использоваться",
    kind: "word",
    status: "learning",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "堆满",
    pinyin: "duīmǎn",
    gloss: "быть заваленным, заполненным доверху",
    kind: "word",
    status: "learning",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "仿佛",
    pinyin: "fǎngfú",
    gloss: "словно, как будто",
    kind: "word",
    status: "known",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "心慌",
    pinyin: "xīnhuāng",
    gloss: "тревожно, сердце не на месте",
    kind: "word",
    status: "repeat",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "难道",
    pinyin: "nándào",
    gloss: "неужели; риторический вопрос, часто с сомнением",
    kind: "grammar",
    example: "难道你忘了？",
    exampleGloss: "Неужели ты забыл?",
    status: "learning",
    lastSeen: "сегодня",
  }),
  makeGloss({
    hanzi: "雷打不动",
    pinyin: "léi dǎ bù dòng",
    gloss: "непоколебимо, что бы ни случилось",
    kind: "idiom",
    status: "learning",
    lastSeen: "1 неделю назад",
  }),
  makeGloss({
    hanzi: "各管一摊",
    pinyin: "gè guǎn yī tān",
    gloss: "каждый занят своим участком работы",
    kind: "idiom",
    status: "known",
    lastSeen: "2 недели назад",
  }),
];

function dedupeGlosses(glosses: Gloss[]): Gloss[] {
  const map = new Map<string, Gloss>();
  for (const gloss of glosses) {
    if (!map.has(gloss.hanzi)) map.set(gloss.hanzi, gloss);
  }
  return [...map.values()];
}

export const seedGlosses: Gloss[] = dedupeGlosses([...heroGlosses, ...MINI_DICT]);

const heroQuestions: Question[] = [
  {
    id: "q-1",
    category: "sense",
    title: "Основной смысл",
    prompt: "Какое из следующих утверждений лучше всего описывает, что произошло?",
    kind: "mcq",
    options: [
      { key: "A", text: "Герой впервые пришёл в компанию." },
      { key: "B", text: "Герой вернулся в знакомое место." },
      { key: "C", text: "Герой искал книги для работы." },
      { key: "D", text: "Герой пытался найти Хуа Шэня." },
    ],
    answerKey: "B",
    explanation:
      "Герой открывает уже знакомую дверь и узнаёт комнату, которая когда-то была кабинетом. Это возвращение, а не первый визит и не поиск конкретной книги.",
    examples: [],
    keywords: [],
  },
  {
    id: "q-2",
    category: "words",
    title: "Лексика: 扑面而来",
    prompt: "Как ты понимаешь выражение 扑面而来 в этом контексте?",
    kind: "open",
    explanation:
      "扑面而来 буквально «наброситься на лицо и прийти». Старый запах книг не просто висит в комнате — он резко встречается с героем у двери. В ответе нужны и ощущение (запах, воздух), и направление «на тебя».",
    examples: ["一阵花香扑面而来。 — Волна цветочного аромата накрыла меня.", "海风扑面而来。 — Морской ветер ударил в лицо."],
    keywords: ["запах", "лиц", "накры", "резк", "воздух", "вет"],
  },
  {
    id: "q-3",
    category: "grammar",
    title: "Грамматика",
    prompt: "Что делает 难道 в фразе «难道公司要把我安排在图书馆工作»?",
    kind: "open",
    explanation:
      "难道 превращает догадку в риторический вопрос: герой почти не верит своей мысли и спрашивает себя «неужели…?». Это тревога, а не нейтральное любопытство.",
    examples: ["难道你忘了？ — Неужели ты забыл?"],
    keywords: ["неужел", "риторическ", "вопрос", "сомнен", "разве"],
  },
  {
    id: "q-4",
    category: "words",
    title: "Детали текста",
    prompt: "Какими деталями текст показывает, что комната стала похожа на библиотеку?",
    kind: "open",
    explanation:
      "Опора — книги от пола до потолка и сравнение 仿佛一座小小的图书馆. Важны сами детали, а не общее «там было много вещей».",
    examples: [],
    keywords: ["пола", "потолка", "книг", "библиотек"],
  },
  {
    id: "q-5",
    category: "sense",
    title: "Собственное мнение",
    prompt: "Как ты думаешь, почему герою стало не по себе? Прямого ответа в тексте нет.",
    kind: "open",
    opinion: true,
    explanation:
      "Текст даёт опору: знакомое место опустело, а герой сам додумывает, что компания может посадить его сюда. Сильный ответ связывает 心慌 с этой догадкой.",
    examples: [],
    keywords: ["тревог", "работ", "компани", "пуст", "измен", "не по себе"],
  },
];

const heroLesson: Lesson = {
  id: "lesson-book-tgcf",
  bookId: HERO_BOOK_ID,
  chapterTitle: "Глава 12",
  fragmentTitle: "Фрагмент 3",
  intro:
    "В этом фрагменте герой возвращается в старое место. Обрати внимание на атмосферу, на то, как комната изменилась, и на устойчивые выражения.",
  hints: [
    "扑面而来 — запах не просто есть в комнате, он движется на героя. 难道 превращает последнюю фразу в тревожный вопрос к самому себе.",
  ],
  tokens: tokenize(HERO_PASSAGE, heroGlosses),
  questions: heroQuestions,
  keyMoments: [
    "Герой узнаёт дверь: это возвращение, не первый визит.",
    "Кабинет опустел и зарос книгами — от пола до потолка.",
    "心慌 появляется вместе с догадкой про новую работу.",
    "扑面而来 передаёт резкость запаха, 难道 — сомнение в собственной догадке.",
  ],
};

const SANTI_PASSAGE = "林夏站在观测台的寒风里，耳机里只有沙沙的噪声。她忽然觉得，宇宙并不沉默，只是人们还没有学会听。";
const WEICHENG_PASSAGE = "火车进站的时候，天已经黑了。我数了数行李，又数了一遍，好像数字能让人安心。";
const HUOZHE_PASSAGE = "老人把牛停在田埂上，看着很远的地方。他说，日子还在，人就还要走。";

const santi = buildOfflineLesson({
  bookId: "book-santi",
  chapterTitle: "Глава 4",
  fragmentTitle: "Фрагмент 1",
  passage: SANTI_PASSAGE,
  pool: seedGlosses,
  intro: "Короткий фрагмент про холод, шум в наушниках и ощущение, что вселенная не молчит.",
});
const weicheng = buildOfflineLesson({
  bookId: "book-weicheng",
  chapterTitle: "Глава 1",
  fragmentTitle: "Фрагмент 1",
  passage: WEICHENG_PASSAGE,
  pool: seedGlosses,
  intro: "Прибытие поезда и привычка пересчитывать багаж, чтобы успокоиться.",
});
const huozhe = buildOfflineLesson({
  bookId: "book-huozhe",
  chapterTitle: "Глава 26",
  fragmentTitle: "Фрагмент 2",
  passage: HUOZHE_PASSAGE,
  pool: seedGlosses,
  intro: "Финал уже прочитан. Этот фрагмент можно открыть ещё раз и сказать его смысл своими словами.",
});

export const seedLessons: Lesson[] = [heroLesson, santi.lesson, weicheng.lesson, huozhe.lesson];

export const seedBooks: Book[] = [
  {
    id: HERO_BOOK_ID,
    title: "天官赐福",
    author: "墨香铜臭",
    cover: "gold",
    status: "reading",
    progress: 15,
    lastSession: "Глава 12 · Сегодня",
    place: "Глава 12",
    words: 24,
    chars: 520,
    observations: 42,
    chapters: [
      { id: "c10", title: "Глава 10", status: "done", detail: "6 фрагментов · пройдено" },
      { id: "c11", title: "Глава 11", status: "done", detail: "5 фрагментов · пройдено" },
      { id: "c12", title: "Глава 12", status: "current", detail: "Фрагмент 3 из 4 · сейчас" },
      { id: "c13", title: "Глава 13", status: "unread", detail: "Ещё не открыта" },
    ],
    recent: [
      { title: "Глава 12 · Фрагмент 3", when: "Сегодня · 5 новых слов", done: true },
      { title: "Глава 12 · Фрагмент 2", when: "Вчера · 6 новых слов", done: true },
    ],
  },
  {
    id: "book-santi",
    title: "三体",
    author: "刘慈欣",
    cover: "navy",
    status: "reading",
    progress: 8,
    lastSession: "Глава 4 · 3 дня назад",
    place: "Глава 4",
    words: 11,
    chars: 180,
    observations: 6,
    chapters: [
      { id: "s3", title: "Глава 3", status: "done", detail: "Пройдено" },
      { id: "s4", title: "Глава 4", status: "current", detail: "Фрагмент 1 · сейчас" },
      { id: "s5", title: "Глава 5", status: "unread", detail: "Ещё не открыта" },
    ],
    recent: [{ title: "Глава 4 · Фрагмент 1", when: "3 дня назад", done: false }],
  },
  {
    id: "book-weicheng",
    title: "围城",
    author: "钱钟书",
    cover: "ink",
    status: "reading",
    progress: 2,
    lastSession: "Глава 1 · 1 неделю назад",
    place: "Глава 1",
    words: 4,
    chars: 64,
    observations: 1,
    chapters: [
      { id: "w1", title: "Глава 1", status: "current", detail: "Фрагмент 1 · сейчас" },
      { id: "w2", title: "Глава 2", status: "unread", detail: "Ещё не открыта" },
    ],
    recent: [{ title: "Глава 1 · Фрагмент 1", when: "1 неделю назад", done: false }],
  },
  {
    id: "book-huozhe",
    title: "活着",
    author: "余华",
    cover: "paper",
    status: "done",
    progress: 100,
    lastSession: "Глава 26 · завершено",
    place: "Глава 26",
    words: 40,
    chars: 2400,
    observations: 18,
    chapters: [
      { id: "h25", title: "Глава 25", status: "done", detail: "Пройдено" },
      { id: "h26", title: "Глава 26", status: "done", detail: "Книга дочитана" },
    ],
    recent: [{ title: "Глава 26 · Фрагмент 2", when: "2 недели назад", done: true }],
  },
];

export const seedNotes: Note[] = [
  {
    id: "note-1",
    bookId: HERO_BOOK_ID,
    kind: "note",
    title: "扑面而来",
    body: "Разобрать примеры использования",
    meta: "Глава 12 · Сегодня",
  },
  {
    id: "note-2",
    bookId: HERO_BOOK_ID,
    kind: "note",
    title: "陈旧",
    body: "Похожее слово: 陈旧的记忆",
    meta: "Глава 12 · Сегодня",
  },
  {
    id: "note-3",
    bookId: HERO_BOOK_ID,
    kind: "note",
    title: "Интересное описание офиса",
    body: "Похоже на живую деталь о характере места, а не просто фон.",
    meta: "Глава 12 · Сегодня",
  },
  {
    id: "note-4",
    bookId: HERO_BOOK_ID,
    kind: "bookmark",
    title: "Глава 12 · Фрагмент 3",
    body: "仿佛一座小小的图书馆",
    meta: "Сегодня",
  },
  {
    id: "note-5",
    bookId: HERO_BOOK_ID,
    kind: "bookmark",
    title: "Глава 12 · Фрагмент 2",
    body: "我推开那扇熟悉的门",
    meta: "Вчера",
  },
];

export const seedSkills: Skill[] = [
  { id: "sk-1", title: "Догадка по контексту", detail: "Обходить незнакомое слово и возвращаться к нему", status: "learning" },
  { id: "sk-2", title: "Основная мысль абзаца", detail: "Сказать, что произошло, не переводя дословно", status: "known" },
  { id: "sk-3", title: "Устойчивые выражения", detail: "Замечать четырёхсложные обороты вроде 扑面而来", status: "repeat" },
];

export const seedActivity: Record<string, 1 | 2> = {
  "2026-10-20": 2,
  "2026-10-19": 2,
  "2026-10-18": 1,
  "2026-10-14": 2,
  "2026-10-11": 1,
  "2026-10-08": 2,
  "2026-10-03": 1,
  "2026-09-28": 2,
  "2026-09-22": 1,
  "2026-09-16": 2,
  "2026-09-09": 1,
  "2026-08-30": 2,
  "2026-08-21": 2,
  "2026-08-12": 1,
  "2026-08-04": 2,
};

export function newId(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}${Date.now().toString(36).slice(-4)}`;
}
