import type { Chunk } from "./types.ts";

type Recipe = {
  id: string;
  title: string;
  summary: string;
  command: string;
  url: string;
  tasks: string;
};

const RECIPES: Recipe[] = [
  {
    id: "recipe:quieter",
    title: "Сделать тише",
    summary: "Уменьшить громкость вдвое.",
    command: "ffmpeg -i input.wav -af volume=0.5 output.wav",
    url: "https://ffmpeg.org/ffmpeg-filters.html#volume",
    tasks: "сделать тише, уменьшить громкость, quieter, lower volume, too loud",
  },
  {
    id: "recipe:louder",
    title: "Сделать громче",
    summary: "Поднять громкость вдвое.",
    command: "ffmpeg -i input.wav -af volume=2.0 output.wav",
    url: "https://ffmpeg.org/ffmpeg-filters.html#volume",
    tasks: "сделать громче, увеличить громкость, louder, boost volume",
  },
  {
    id: "recipe:compress",
    title: "Сжать видео",
    summary: "Перекодировать в H.264 с CRF 23: меньше файл без заметной потери картинки.",
    command: "ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset medium -c:a aac output.mp4",
    url: "https://ffmpeg.org/ffmpeg-codecs.html#libx264_002c-libx264rgb",
    tasks: "сжать видео, сжать mp4, уменьшить размер файла без заметной потери качества, compress video, h264, crf",
  },
  {
    id: "recipe:cut",
    title: "Вырезать фрагмент",
    summary: "Взять 30 секунд начиная с 1:20, без перекодирования.",
    command: "ffmpeg -ss 00:01:20 -i input.mp4 -t 30 -c copy output.mp4",
    url: "https://ffmpeg.org/ffmpeg.html#Main-options",
    tasks: "вырезать фрагмент, обрезать по времени, с 1:20 длительностью 30 секунд, trim, cut clip, without reencoding",
  },
  {
    id: "recipe:mp3",
    title: "Извлечь звук в mp3",
    summary: "Сохранить только аудиодорожку в mp3.",
    command: "ffmpeg -i input.mp4 -vn -c:a libmp3lame -q:a 2 output.mp3",
    url: "https://ffmpeg.org/ffmpeg-codecs.html#libmp3lame-1",
    tasks: "извлечь звук, вытащить аудио, сохранить в mp3, extract audio, mp3",
  },
  {
    id: "recipe:scale",
    title: "Уменьшить разрешение",
    summary: "Привести кадр к 1280×720.",
    command: "ffmpeg -i input.mp4 -vf scale=1280:720 output.mp4",
    url: "https://ffmpeg.org/ffmpeg-filters.html#scale-1",
    tasks: "уменьшить разрешение, изменить размер кадра, 1280x720, 720p, resize, scale",
  },
  {
    id: "recipe:subs",
    title: "Наложить субтитры",
    summary: "Вжечь файл субтитров в картинку.",
    command: "ffmpeg -i input.mp4 -vf subtitles=subs.srt output.mp4",
    url: "https://ffmpeg.org/ffmpeg-filters.html#subtitles-1",
    tasks: "наложить субтитры, вжечь субтитры, burn subtitles, srt",
  },
  {
    id: "recipe:concat",
    title: "Склеить ролики",
    summary: "Склеить файлы из списка list.txt без перекодирования. В списке строки вида file 'a.mp4'.",
    command: "ffmpeg -f concat -safe 0 -i list.txt -c copy output.mp4",
    url: "https://ffmpeg.org/ffmpeg-formats.html#concat-1",
    tasks: "склеить несколько роликов, объединить видео, concatenate, join clips",
  },
  {
    id: "recipe:speed",
    title: "Ускорить в два раза",
    summary: "Ускорить картинку и звук вдвое.",
    command: "ffmpeg -i input.mp4 -vf setpts=0.5*PTS -af atempo=2.0 output.mp4",
    url: "https://ffmpeg.org/ffmpeg-filters.html#setpts_002c-asetpts",
    tasks: "ускорить видео в два раза, speed up 2x, slow down, замедлить",
  },
  {
    id: "recipe:gif",
    title: "Сделать gif",
    summary: "Собрать gif с палитрой из фрагмента видео.",
    command: "ffmpeg -ss 00:00:02 -t 4 -i input.mp4 -vf fps=10,scale=480:-1:flags=lanczos,palettegen palette.png && ffmpeg -ss 00:00:02 -t 4 -i input.mp4 -i palette.png -lavfi fps=10,scale=480:-1:flags=lanczos[x];[x][1:v]paletteuse output.gif",
    url: "https://ffmpeg.org/ffmpeg-filters.html#palettegen-1",
    tasks: "сделать gif из фрагмента видео, animated gif",
  },
  {
    id: "recipe:probe",
    title: "Информация о файле",
    summary: "Показать контейнер, кодеки и длительность.",
    command: "ffprobe -print_format json -show_format -show_streams input.mp4",
    url: "https://ffmpeg.org/ffprobe.html#Main-options",
    tasks: "показать кодеки и длительность файла, информация о медиа, metadata, ffprobe",
  },
  {
    id: "recipe:rotate",
    title: "Повернуть на 90 градусов",
    summary: "Повернуть картинку по часовой стрелке.",
    command: "ffmpeg -i input.mp4 -vf transpose=1 output.mp4",
    url: "https://ffmpeg.org/ffmpeg-filters.html#transpose-1",
    tasks: "повернуть видео на 90 градусов, rotate clockwise, transpose",
  },
  {
    id: "recipe:frame",
    title: "Сохранить кадр",
    summary: "Взять один кадр на второй секунде.",
    command: "ffmpeg -ss 00:00:02 -i input.mp4 -frames:v 1 frame.png",
    url: "https://ffmpeg.org/ffmpeg.html#Main-options",
    tasks: "сохранить кадр, скриншот из видео, screenshot, thumbnail",
  },
];

export function recipeChunks(): Chunk[] {
  return RECIPES.map(recipe => ({
    id: recipe.id,
    kind: "example" as const,
    priority: 0 as const,
    title: recipe.title,
    summary: recipe.summary,
    command: recipe.command,
    signature: null,
    synthesized: true,
    url: recipe.url,
    page: "ffmpeg",
    family: "example",
    embedText: `${recipe.tasks.split(",")[0]?.trim()}\n${recipe.tasks}\nCommand: ${recipe.command}\n${recipe.summary}`,
  }));
}
