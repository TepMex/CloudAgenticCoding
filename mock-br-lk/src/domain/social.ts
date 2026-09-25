import type { HeroId } from './heroes'

export interface Letter {
  id: string
  from: string
  subject: string
  body: string
  date: string
}

export const MAIL: Letter[] = [
  {
    id: 'welcome',
    from: 'Лаоши Анна',
    subject: 'Добро пожаловать в BRY Chinese!',
    body: 'Привет! Я твой преподаватель. Начни путешествие с Пекина — там тебя ждут первые слова: 你好 и 谢谢. Встретимся на уроке в среду в 17:00. 加油!',
    date: 'сегодня',
  },
  {
    id: 'tip',
    from: 'Лис Линь',
    subject: 'Секрет путешественника',
    body: 'Выполняй хотя бы одно задание каждый день и открывай ежедневный сундук в разделе «Награды». Так монеты копятся быстрее!',
    date: 'сегодня',
  },
  {
    id: 'parents',
    from: 'Команда BRY',
    subject: 'Для родителей: отчёт о прогрессе',
    body: 'Каждое воскресенье мы присылаем на e-mail родителя короткий отчёт: сколько слов выучено и какие темы стоит повторить.',
    date: 'вчера',
  },
]

export interface Friend {
  name: string
  heroId: HeroId
  heroName: string
  level: number
  city: string
  online: boolean
}

export const FRIENDS: Friend[] = [
  { name: 'Маша', heroId: 'panda', heroName: 'Панда Бао', level: 3, city: 'Шанхай', online: true },
  { name: 'Артём', heroId: 'tiger', heroName: 'Тигр Ху', level: 5, city: 'Чунцин', online: false },
  { name: 'Соня', heroId: 'crane', heroName: 'Журавль Хэ', level: 2, city: 'Сиань', online: true },
  { name: 'Лёва', heroId: 'monkey', heroName: 'Обезьяна Укун', level: 1, city: 'Пекин', online: false },
  { name: 'Ева', heroId: 'dragon', heroName: 'Дракон Лун', level: 7, city: 'Ханчжоу', online: false },
]

export interface SchoolEvent {
  id: string
  title: string
  hanzi: string
  date: string
  description: string
  kind: 'lesson' | 'festival' | 'contest'
}

export const EVENTS: SchoolEvent[] = [
  { id: 'lesson-1', title: 'Групповой урок: «Моя семья»', hanzi: '家', date: 'Ср, 17:00', description: 'Онлайн-урок с преподавателем. Возьми фотографию своей семьи!', kind: 'lesson' },
  { id: 'moon', title: 'Праздник середины осени', hanzi: '中秋节', date: '6 октября', description: 'Узнаем легенду о Чанъэ, нарисуем лунные пряники и получим праздничный значок.', kind: 'festival' },
  { id: 'contest', title: 'Конкурс каллиграфии', hanzi: '书法', date: '20 октября', description: 'Напиши иероглиф 福 кистью и пришли фото. Лучшие работы получат 5 кристаллов.', kind: 'contest' },
  { id: 'lesson-2', title: 'Разговорный клуб: «В магазине»', hanzi: '买', date: 'Сб, 11:00', description: 'Учимся спрашивать «多少钱?» и торговаться как настоящие путешественники.', kind: 'lesson' },
]
