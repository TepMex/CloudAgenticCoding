export interface Word {
  hanzi: string
  pinyin: string
  ru: string
}

export interface City {
  id: string
  name: string
  hanzi: string
  /** Hero level needed to enter is `level - 1`: Beijing (level 1) is open from the start. */
  level: number
  /** Marker position on the map illustration, in percent. */
  x: number
  y: number
  theme: string
  fact: string
  words: Word[]
}

export const CITIES: City[] = [
  {
    id: 'beijing',
    name: 'Пекин',
    hanzi: '北京',
    level: 1,
    x: 63,
    y: 31,
    theme: 'Знакомство и приветствия',
    fact: 'Запретный город в Пекине — самый большой дворцовый комплекс в мире: в нём почти 1000 зданий.',
    words: [
      { hanzi: '你好', pinyin: 'nǐ hǎo', ru: 'привет' },
      { hanzi: '谢谢', pinyin: 'xièxie', ru: 'спасибо' },
      { hanzi: '再见', pinyin: 'zàijiàn', ru: 'до свидания' },
      { hanzi: '我', pinyin: 'wǒ', ru: 'я' },
      { hanzi: '你', pinyin: 'nǐ', ru: 'ты' },
      { hanzi: '名字', pinyin: 'míngzi', ru: 'имя' },
    ],
  },
  {
    id: 'xian',
    name: 'Сиань',
    hanzi: '西安',
    level: 2,
    x: 29,
    y: 58,
    theme: 'Числа',
    fact: 'Рядом с Сианем нашли Терракотовую армию — более 8000 глиняных воинов, и все с разными лицами.',
    words: [
      { hanzi: '一', pinyin: 'yī', ru: 'один' },
      { hanzi: '二', pinyin: 'èr', ru: 'два' },
      { hanzi: '三', pinyin: 'sān', ru: 'три' },
      { hanzi: '四', pinyin: 'sì', ru: 'четыре' },
      { hanzi: '五', pinyin: 'wǔ', ru: 'пять' },
      { hanzi: '十', pinyin: 'shí', ru: 'десять' },
    ],
  },
  {
    id: 'shanghai',
    name: 'Шанхай',
    hanzi: '上海',
    level: 3,
    x: 88,
    y: 40,
    theme: 'Семья',
    fact: 'Шанхайская башня — второй по высоте небоскрёб в мире, 632 метра.',
    words: [
      { hanzi: '妈妈', pinyin: 'māma', ru: 'мама' },
      { hanzi: '爸爸', pinyin: 'bàba', ru: 'папа' },
      { hanzi: '哥哥', pinyin: 'gēge', ru: 'старший брат' },
      { hanzi: '姐姐', pinyin: 'jiějie', ru: 'старшая сестра' },
      { hanzi: '家', pinyin: 'jiā', ru: 'дом, семья' },
      { hanzi: '朋友', pinyin: 'péngyou', ru: 'друг' },
    ],
  },
  {
    id: 'chengdu',
    name: 'Чэнду',
    hanzi: '成都',
    level: 4,
    x: 42,
    y: 47,
    theme: 'Животные',
    fact: 'В Чэнду находится знаменитый центр разведения больших панд.',
    words: [
      { hanzi: '熊猫', pinyin: 'xióngmāo', ru: 'панда' },
      { hanzi: '猫', pinyin: 'māo', ru: 'кошка' },
      { hanzi: '狗', pinyin: 'gǒu', ru: 'собака' },
      { hanzi: '鱼', pinyin: 'yú', ru: 'рыба' },
      { hanzi: '鸟', pinyin: 'niǎo', ru: 'птица' },
      { hanzi: '马', pinyin: 'mǎ', ru: 'лошадь' },
    ],
  },
  {
    id: 'chongqing',
    name: 'Чунцин',
    hanzi: '重庆',
    level: 5,
    x: 60,
    y: 50,
    theme: 'Еда',
    fact: 'Чунцин — родина острого хого, «огненного котла».',
    words: [
      { hanzi: '米饭', pinyin: 'mǐfàn', ru: 'рис' },
      { hanzi: '面条', pinyin: 'miàntiáo', ru: 'лапша' },
      { hanzi: '水', pinyin: 'shuǐ', ru: 'вода' },
      { hanzi: '茶', pinyin: 'chá', ru: 'чай' },
      { hanzi: '吃', pinyin: 'chī', ru: 'есть, кушать' },
      { hanzi: '喝', pinyin: 'hē', ru: 'пить' },
    ],
  },
  {
    id: 'dunhuang',
    name: 'Дуньхуан',
    hanzi: '敦煌',
    level: 6,
    x: 45,
    y: 21,
    theme: 'Цвета',
    fact: 'В пещерах Могао в Дуньхуане сохранились фрески, которым больше 1500 лет.',
    words: [
      { hanzi: '红色', pinyin: 'hóngsè', ru: 'красный' },
      { hanzi: '黄色', pinyin: 'huángsè', ru: 'жёлтый' },
      { hanzi: '蓝色', pinyin: 'lánsè', ru: 'синий' },
      { hanzi: '绿色', pinyin: 'lǜsè', ru: 'зелёный' },
      { hanzi: '白色', pinyin: 'báisè', ru: 'белый' },
      { hanzi: '黑色', pinyin: 'hēisè', ru: 'чёрный' },
    ],
  },
  {
    id: 'hangzhou',
    name: 'Ханчжоу',
    hanzi: '杭州',
    level: 7,
    x: 79,
    y: 55,
    theme: 'Природа',
    fact: 'Озеро Сиху в Ханчжоу вдохновляло китайских поэтов больше тысячи лет.',
    words: [
      { hanzi: '山', pinyin: 'shān', ru: 'гора' },
      { hanzi: '水', pinyin: 'shuǐ', ru: 'вода' },
      { hanzi: '花', pinyin: 'huā', ru: 'цветок' },
      { hanzi: '树', pinyin: 'shù', ru: 'дерево' },
      { hanzi: '湖', pinyin: 'hú', ru: 'озеро' },
      { hanzi: '月亮', pinyin: 'yuèliang', ru: 'луна' },
    ],
  },
  {
    id: 'kunming',
    name: 'Куньмин',
    hanzi: '昆明',
    level: 8,
    x: 47,
    y: 69,
    theme: 'Погода',
    fact: 'Куньмин называют «городом вечной весны» — там тепло круглый год.',
    words: [
      { hanzi: '天气', pinyin: 'tiānqì', ru: 'погода' },
      { hanzi: '太阳', pinyin: 'tàiyáng', ru: 'солнце' },
      { hanzi: '下雨', pinyin: 'xià yǔ', ru: 'идёт дождь' },
      { hanzi: '热', pinyin: 'rè', ru: 'жарко' },
      { hanzi: '冷', pinyin: 'lěng', ru: 'холодно' },
      { hanzi: '春天', pinyin: 'chūntiān', ru: 'весна' },
    ],
  },
  {
    id: 'urumqi',
    name: 'Урумчи',
    hanzi: '乌鲁木齐',
    level: 9,
    x: 19,
    y: 27,
    theme: 'Путешествия',
    fact: 'Урумчи — самый удалённый от моря крупный город на Земле.',
    words: [
      { hanzi: '去', pinyin: 'qù', ru: 'идти, ехать' },
      { hanzi: '来', pinyin: 'lái', ru: 'приходить' },
      { hanzi: '火车', pinyin: 'huǒchē', ru: 'поезд' },
      { hanzi: '飞机', pinyin: 'fēijī', ru: 'самолёт' },
      { hanzi: '路', pinyin: 'lù', ru: 'дорога' },
      { hanzi: '地图', pinyin: 'dìtú', ru: 'карта' },
    ],
  },
  {
    id: 'harbin',
    name: 'Харбин',
    hanzi: '哈尔滨',
    level: 10,
    x: 80,
    y: 11,
    theme: 'Зима и праздники',
    fact: 'Каждую зиму в Харбине строят целый город из льда и снега.',
    words: [
      { hanzi: '冬天', pinyin: 'dōngtiān', ru: 'зима' },
      { hanzi: '雪', pinyin: 'xuě', ru: 'снег' },
      { hanzi: '新年', pinyin: 'xīnnián', ru: 'Новый год' },
      { hanzi: '快乐', pinyin: 'kuàilè', ru: 'радостный' },
      { hanzi: '礼物', pinyin: 'lǐwù', ru: 'подарок' },
      { hanzi: '冰', pinyin: 'bīng', ru: 'лёд' },
    ],
  },
  {
    id: 'lhasa',
    name: 'Лхаса',
    hanzi: '拉萨',
    level: 11,
    x: 17,
    y: 39,
    theme: 'Тело',
    fact: 'Лхаса — один из самых высокогорных городов мира: 3650 метров над уровнем моря.',
    words: [
      { hanzi: '头', pinyin: 'tóu', ru: 'голова' },
      { hanzi: '手', pinyin: 'shǒu', ru: 'рука' },
      { hanzi: '眼睛', pinyin: 'yǎnjing', ru: 'глаза' },
      { hanzi: '口', pinyin: 'kǒu', ru: 'рот' },
      { hanzi: '耳朵', pinyin: 'ěrduo', ru: 'уши' },
      { hanzi: '脚', pinyin: 'jiǎo', ru: 'нога, стопа' },
    ],
  },
  {
    id: 'guangzhou',
    name: 'Гуанчжоу',
    hanzi: '广州',
    level: 12,
    x: 66,
    y: 72,
    theme: 'Покупки',
    fact: 'Гуанчжоу — город кантонской кухни и знаменитых димсамов.',
    words: [
      { hanzi: '买', pinyin: 'mǎi', ru: 'покупать' },
      { hanzi: '钱', pinyin: 'qián', ru: 'деньги' },
      { hanzi: '多少', pinyin: 'duōshao', ru: 'сколько' },
      { hanzi: '商店', pinyin: 'shāngdiàn', ru: 'магазин' },
      { hanzi: '贵', pinyin: 'guì', ru: 'дорогой' },
      { hanzi: '便宜', pinyin: 'piányi', ru: 'дешёвый' },
    ],
  },
  {
    id: 'hainan',
    name: 'о. Хайнань',
    hanzi: '海南',
    level: 13,
    x: 90,
    y: 81,
    theme: 'Отдых и море',
    fact: 'Хайнань называют «китайскими Гавайями» — там пальмы и тёплое море.',
    words: [
      { hanzi: '海', pinyin: 'hǎi', ru: 'море' },
      { hanzi: '游泳', pinyin: 'yóuyǒng', ru: 'плавать' },
      { hanzi: '玩', pinyin: 'wán', ru: 'играть' },
      { hanzi: '休息', pinyin: 'xiūxi', ru: 'отдыхать' },
      { hanzi: '开心', pinyin: 'kāixīn', ru: 'весёлый' },
      { hanzi: '船', pinyin: 'chuán', ru: 'лодка, корабль' },
    ],
  },
]

/** Dashed routes between cities, drawn on the map. */
export const ROUTES: [string, string][] = [
  ['beijing', 'xian'],
  ['beijing', 'shanghai'],
  ['xian', 'chengdu'],
  ['chengdu', 'chongqing'],
  ['beijing', 'dunhuang'],
  ['shanghai', 'hangzhou'],
  ['chongqing', 'kunming'],
  ['dunhuang', 'urumqi'],
  ['beijing', 'harbin'],
  ['chengdu', 'lhasa'],
  ['kunming', 'guangzhou'],
  ['hangzhou', 'guangzhou'],
  ['guangzhou', 'hainan'],
]

export function getCity(id: string): City | undefined {
  return CITIES.find((c) => c.id === id)
}
