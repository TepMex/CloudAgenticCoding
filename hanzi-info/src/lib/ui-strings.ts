import type { AppLanguage } from "./settings";

export function regularityDegreeText(lang: AppLanguage, scale: 1 | 2): string {
  if (lang === "ru") {
    return scale === 1
      ? "Степень регулярности 1 — то же произношение, включая тон (формулировка HanziCraft)"
      : "Степень регулярности 2 — тот же слог, другой тон (формулировка HanziCraft)";
  }
  return scale === 1
    ? "Regularity degree one — exact same pronunciation including tone (HanziCraft wording)"
    : "Regularity degree two — exact same syllable but different tone (HanziCraft wording)";
}

const UI = {
  en: {
    htmlDescription:
      "Look up Hanzi phonetic components using HanziCraft phonetic-sets rules (degrees 1–2, sets with 3+ characters) via HanziJS.",
    badgeTagline: "Hanzi phonetic lookup",
    pageTitle: "Hanzi Info",
    heroIntro:
      "Explore which phonetic component ties a character to its sound family, using the same published phonetic-set lists as",
    heroIntroLink: "HanziCraft's phonetic sets",
    heroIntroTail:
      "(regularity degrees 1–2, only sets with more than two characters). English glosses come from CC-CEDICT via HanziJS.",
    heroIntroTailRuGloss:
      "Russian glosses combine machine translation of those definitions with single-character Wikipedia interwiki titles (open-dict-data).",
    cardLookupTitle: "Look up characters",
    cardLookupDesc:
      "Enter one or more Hanzi (a word or phrase) or open a shareable URL such as",
    cardLookupDescExample: "Each character is shown in its own card.",
    labelCharacters: "Characters",
    inputPlaceholder: "例如：清 · 你好",
    search: "Search",
    loadingDictionary: "Loading dictionary…",
    loadErrorTitle: "Could not load database",
    emptyPrompt: "Enter one or more Hanzi above to begin.",
    copyLink: "Copy link",
    linkCopied: "Link copied.",
    copyFailed: "Could not copy automatically.",
    settings: "Settings",
    settingsTitle: "Settings",
    settingsDesc: "Choose interface language and how character glosses are shown.",
    labelUiLanguage: "Language",
    langEnglish: "English",
    langRussian: "Russian",
    backToLookup: "Back to lookup",
    notInIndexTitle: "Not in local index",
    notInIndexDescBefore: "is outside the bundled coverage (HanziJS frequency list plus phonetic-set participants (degrees 1–2, 3+ characters per set) and their decomposition parts).",
    identifier: "Identifier",
    type: "Type",
    reading: "Reading",
    initialsFinalTone: "Initial · final · tone",
    glossEn: "English gloss",
    glossRu: "Russian gloss",
    glossPrimary: "Gloss",
    phoneticComponent: "Phonetic component",
    phoneticNone:
      "No phonetic component in the HanziCraft phonetic-sets index for this character (under the same filters: degrees 1–2, more than two characters per set). The character is treated as",
    ideographic: "ideographic",
    phoneticHere: "here.",
    phoneticAnchor: "Phonetic anchor",
    setKey: "set_key",
    seriesMembers: "Series members",
    showingMembers: "Showing",
    ofMembers: "of",
    charactersWord: "characters.",
    structuralParts: "Structural parts (IDS decomposition)",
    structuralNone: "No separate parts returned for this character.",
    radicalGlossNote:
      "These pieces come from HanziJS decompose() (graphical / radical-style split). They are linked in the hanzi2radicals table as perspective schema support. Radical glosses follow HanziJS Kangxi-style English names.",
  },
  ru: {
    htmlDescription:
      "Поиск фонетических компонентов иероглифов по правилам списков HanziCraft (степени 1–2, наборы из 3+ знаков) через HanziJS.",
    badgeTagline: "Фонетический разбор иероглифов",
    pageTitle: "Hanzi Info",
    heroIntro:
      "Показываем фонетический компонент и «звуковое семейство» по тем же открытым спискам, что и",
    heroIntroLink: "phonetic sets на HanziCraft",
    heroIntroTail:
      "(степени регулярности 1–2, только наборы из более чем двух иероглифов). Английские толкования — из CC-CEDICT через HanziJS.",
    heroIntroTailRuGloss:
      "Русские толкования: машинный перевод этих определений плюс односимвольные заголовки из межъязыковых ссылок Википедии (open-dict-data).",
    cardLookupTitle: "Поиск по иероглифам",
    cardLookupDesc: "Введите один или несколько иероглифов (слово или фразу) или откройте URL вида",
    cardLookupDescExample: "Каждый знак показывается отдельной карточкой.",
    labelCharacters: "Иероглифы",
    inputPlaceholder: "Например: 清 · 你好",
    search: "Найти",
    loadingDictionary: "Загрузка словаря…",
    loadErrorTitle: "Не удалось загрузить базу",
    emptyPrompt: "Введите иероглифы выше, чтобы начать.",
    copyLink: "Копировать ссылку",
    linkCopied: "Ссылка скопирована.",
    copyFailed: "Не удалось скопировать автоматически.",
    settings: "Настройки",
    settingsTitle: "Настройки",
    settingsDesc: "Язык интерфейса и язык толкований в карточках.",
    labelUiLanguage: "Язык",
    langEnglish: "English",
    langRussian: "Русский",
    backToLookup: "К поиску",
    notInIndexTitle: "Нет в локальном индексе",
    notInIndexDescBefore:
      "вне охвата собранной базы (частотный список HanziJS, участники фонетических наборов степеней 1–2 с 3+ знаками и части их декомпозиции).",
    identifier: "Идентификатор",
    type: "Тип",
    reading: "Чтение",
    initialsFinalTone: "Инициаль · финаль · тон",
    glossEn: "Толкование (англ.)",
    glossRu: "Толкование (рус.)",
    glossPrimary: "Толкование",
    phoneticComponent: "Фонетический компонент",
    phoneticNone:
      "Для этого иероглифа нет фонетического компонента в индексе HanziCraft (те же фильтры: степени 1–2, более двух знаков в наборе). Здесь знак считается",
    ideographic: "идеографическим",
    phoneticHere: ".",
    phoneticAnchor: "Фонетический якорь",
    setKey: "set_key",
    seriesMembers: "Знаки набора",
    showingMembers: "Показано",
    ofMembers: "из",
    charactersWord: "иероглифов.",
    structuralParts: "Структурные части (декомпозиция IDS)",
    structuralNone: "Для этого знака отдельные части не возвращены.",
    radicalGlossNote:
      "Части получены из HanziJS decompose() (графический / ключевой разбор). Связи в таблице hanzi2radicals. Названия ключей — в английской традиции Kangxi из HanziJS.",
  },
} as const;

export type UiKey = keyof typeof UI.en;

export function t(lang: AppLanguage, key: UiKey): string {
  return UI[lang][key];
}
