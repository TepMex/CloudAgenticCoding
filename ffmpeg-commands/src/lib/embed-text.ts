import { bareTitle, clip } from "./html-text.ts";

const FAMILY_LABEL: Record<string, string> = {
  "audio-filter": "аудиофильтр FFmpeg / audio filter",
  "audio-source": "источник звука FFmpeg / audio source",
  "audio-sink": "приёмник звука FFmpeg / audio sink",
  "video-filter": "видеофильтр FFmpeg / video filter",
  "video-source": "источник видео FFmpeg / video source",
  "video-sink": "приёмник видео FFmpeg / video sink",
  "multimedia-filter": "мультимедиа фильтр FFmpeg / multimedia filter",
  "multimedia-source": "мультимедиа источник FFmpeg / multimedia source",
  "video-encoder": "видеокодировщик FFmpeg / video encoder",
  "audio-encoder": "аудиокодировщик FFmpeg / audio encoder",
  "subtitle-encoder": "кодировщик субтитров FFmpeg / subtitle encoder",
  "video-decoder": "видеодекодер FFmpeg / video decoder",
  "audio-decoder": "аудиодекодер FFmpeg / audio decoder",
  "subtitle-decoder": "декодер субтитров FFmpeg / subtitle decoder",
  demuxer: "демуксер FFmpeg / demuxer",
  muxer: "муксер FFmpeg / muxer",
  protocol: "протокол FFmpeg / protocol",
  "bitstream-filter": "битовый фильтр FFmpeg / bitstream filter",
  "input-device": "устройство ввода FFmpeg / input device",
  "output-device": "устройство вывода FFmpeg / output device",
  writer: "формат отчёта ffprobe / ffprobe writer",
  option: "опция командной строки FFmpeg / CLI option",
  example: "пример команды FFmpeg / command example",
  overview: "раздел документации FFmpeg",
};

const HINTS: { re: RegExp; text: string }[] = [
  { re: /\b(libx264|-crf\b|h\.?264)\b/i, text: "сжать видео, уменьшить размер файла, compress video, mp4, h264" },
  { re: /\b(libx265|hevc|h\.?265)\b/i, text: "сжать видео h265, hevc, меньший файл" },
  { re: /(^|\s)-ss\b|-sseof\b/i, text: "вырезать фрагмент, обрезать по времени, seek, trim, cut" },
  { re: /(^|\s)-to\b|(^|\s)-t\b/i, text: "длительность фрагмента, обрезать до, duration" },
  { re: /-c(?::[vas])?\s+copy\b|streamcopy/i, text: "без перекодирования, remux, copy streams" },
  { re: /\bscale\b/i, text: "изменить разрешение, уменьшить кадр, resize, 720p, 1080p" },
  { re: /\bcrop\b/i, text: "обрезать кадр, crop" },
  { re: /\bvolume\b/i, text: "громкость, сделать тише, сделать громче, audio volume" },
  { re: /\b(loudnorm|alimiter|dynaudnorm)\b/i, text: "нормализовать громкость, loudness" },
  { re: /\b(subtitles|libass|mov_text)\b/i, text: "наложить субтитры, вжечь субтитры, burn subtitles" },
  { re: /\bconcat\b/i, text: "склеить ролики, объединить видео, concatenate" },
  { re: /\boverlay\b/i, text: "наложить картинку или видео, watermark, picture in picture" },
  { re: /\b(hflip|vflip|transpose|rotate)\b/i, text: "повернуть, отразить, rotate, flip" },
  { re: /\b(setpts|atempo)\b/i, text: "ускорить, замедлить, speed up, slow motion" },
  { re: /\b(gif|palettegen|paletteuse)\b/i, text: "сделать gif из видео" },
  { re: /\b(libmp3lame|\bmp3\b)\b/i, text: "извлечь звук, сохранить аудио в mp3" },
  { re: /\b-vn\b/, text: "убрать видео, только звук, extract audio" },
  { re: /\b-an\b/, text: "убрать звук, mute" },
  { re: /\b(drawtext)\b/i, text: "текст на видео, надпись" },
  { re: /-frames:v\b|-vframes\b|\bthumbnail\b/i, text: "сохранить кадр, скриншот, screenshot, thumbnail" },
  { re: /\b-map\b/, text: "выбрать дорожку, аудиодорожка, stream map" },
  { re: /\bffprobe\b/, text: "информация о файле, кодеки, длительность, metadata" },
  { re: /\b(hqdn3d|nlmeans|atadenoise|denoise)\b/i, text: "убрать шум, denoise" },
  { re: /\b(yadif|bwdif|deinterlace)\b/i, text: "деинтерлейс, interlaced" },
  { re: /\b(fade|afade)\b/i, text: "затухание, fade in, fade out" },
  { re: /\b(zoompan)\b/i, text: "зум, ken burns" },
  { re: /\b(silenceremove)\b/i, text: "вырезать тишину, remove silence" },
  { re: /\b(reverse|areverse)\b/i, text: "проиграть задом наперёд, reverse" },
  { re: /\b(chromakey|colorkey)\b/i, text: "хромакей, убрать зелёный фон, green screen" },
  { re: /\b(amix|amerge|pan|join)\b/i, text: "смешать звук, моно, стерео, mix audio" },
  { re: /\b(hls|segment)\b/i, text: "нарезать на части, hls, сегменты" },
  { re: /\b(fps|framerate)\b/i, text: "частота кадров, fps" },
  { re: /\b(aresample|-ar\b|-ac\b)\b/i, text: "частота дискретизации, каналы, sample rate, mono, stereo" },
];

export function familyLabel(family: string): string {
  return FAMILY_LABEL[family] ?? FAMILY_LABEL.overview ?? family;
}

export function intentHints(blob: string): string[] {
  const hints: string[] = [];
  for (const hint of HINTS) {
    if (hint.re.test(blob)) hints.push(hint.text);
    if (hints.length === 3) break;
  }
  return hints;
}

/** Short commands for components whose manual example is missing or huge. */
const CANONICAL: Record<string, string> = {
  volume: "ffmpeg -i input.wav -af volume=0.5 output.wav",
  loudnorm: "ffmpeg -i input.wav -af loudnorm=I=-16:TP=-1.5:LRA=11 output.wav",
  silenceremove: "ffmpeg -i input.wav -af silenceremove=start_periods=1:stop_periods=1 output.wav",
  scale: "ffmpeg -i input.mp4 -vf scale=1280:720 output.mp4",
  crop: "ffmpeg -i input.mp4 -vf crop=iw:ih/2:0:0 output.mp4",
  pad: "ffmpeg -i input.mp4 -vf pad=1920:1080:(ow-iw)/2:(oh-ih)/2 output.mp4",
  subtitles: "ffmpeg -i input.mp4 -vf subtitles=subs.srt output.mp4",
  overlay: "ffmpeg -i base.mp4 -i logo.png -filter_complex overlay=10:10 output.mp4",
  concat: "ffmpeg -f concat -safe 0 -i list.txt -c copy output.mp4",
  setpts: "ffmpeg -i input.mp4 -vf setpts=0.5*PTS -af atempo=2.0 output.mp4",
  atempo: "ffmpeg -i input.mp4 -filter:a atempo=2.0 -filter:v setpts=0.5*PTS output.mp4",
  fps: "ffmpeg -i input.mp4 -vf fps=30 output.mp4",
  hflip: "ffmpeg -i input.mp4 -vf hflip output.mp4",
  vflip: "ffmpeg -i input.mp4 -vf vflip output.mp4",
  transpose: "ffmpeg -i input.mp4 -vf transpose=1 output.mp4",
  rotate: "ffmpeg -i input.mp4 -vf rotate=PI/2 output.mp4",
  fade: "ffmpeg -i input.mp4 -vf fade=t=in:st=0:d=1 output.mp4",
  afade: "ffmpeg -i input.wav -af afade=t=in:st=0:d=1 output.wav",
  drawtext: "ffmpeg -i input.mp4 -vf drawtext=text='Hello':fontsize=48:x=10:y=10 output.mp4",
  reverse: "ffmpeg -i input.mp4 -vf reverse -af areverse output.mp4",
  areverse: "ffmpeg -i input.wav -af areverse output.wav",
  palettegen: "ffmpeg -i input.mp4 -vf palettegen palette.png",
  paletteuse: "ffmpeg -i input.mp4 -i palette.png -lavfi paletteuse output.gif",
  yadif: "ffmpeg -i input.mp4 -vf yadif output.mp4",
  hqdn3d: "ffmpeg -i input.mp4 -vf hqdn3d output.mp4",
  libx264: "ffmpeg -i input.mp4 -c:v libx264 -crf 23 -preset medium -c:a aac output.mp4",
  libx265: "ffmpeg -i input.mp4 -c:v libx265 -crf 28 -c:a aac output.mp4",
  libmp3lame: "ffmpeg -i input.mp4 -vn -c:a libmp3lame -q:a 2 output.mp3",
  aac: "ffmpeg -i input.wav -c:a aac -b:a 192k output.m4a",
  gif: "ffmpeg -i input.mp4 -vf fps=10,scale=480:-1:flags=lanczos output.gif",
};

export function canonicalCommand(name: string | null): string | null {
  if (!name) return null;
  return CANONICAL[name] ?? null;
}

export function buildEmbedText(input: {
  family: string;
  title: string;
  summary: string;
  command: string | null;
  extraCommands?: string[];
  signature?: string | null;
}): string {
  const title = bareTitle(input.title) || input.title;
  const commands = [input.command, ...(input.extraCommands ?? [])].filter((command): command is string => Boolean(command));
  const unique = [...new Set(commands)].slice(0, 2);
  const hints = intentHints(`${title}\n${input.signature ?? ""}\n${unique.join("\n")}`);
  const lines = [`${familyLabel(input.family)}: ${title}`];
  if (input.signature && input.signature !== title) lines.push(`Option: ${input.signature}`);
  if (unique[0]) lines.push(`Command: ${unique[0]}`);
  if (unique[1]) lines.push(`Also: ${unique[1]}`);
  if (hints.length) lines.push(hints.join("; "));
  const head = lines.join("\n");
  const room = 900 - head.length - 1;
  if (input.summary && room > 80) lines.push(clip(input.summary, room));
  return lines.join("\n").slice(0, 900);
}

const FFMPEG_OPTION_EXAMPLES: Record<string, string> = {
  "-ss": "ffmpeg -ss 00:01:20 -i input.mp4 -t 30 -c copy output.mp4",
  "-sseof": "ffmpeg -sseof -20 -i input.mp4 -c copy output.mp4",
  "-t": "ffmpeg -i input.mp4 -t 30 -c copy output.mp4",
  "-to": "ffmpeg -ss 00:01:00 -i input.mp4 -to 00:02:00 -c copy output.mp4",
  "-vn": "ffmpeg -i input.mp4 -vn -c:a libmp3lame output.mp3",
  "-an": "ffmpeg -i input.mp4 -an -c copy output.mp4",
  "-sn": "ffmpeg -i input.mkv -sn -c copy output.mkv",
  "-c:v": "ffmpeg -i input.mov -c:v libx264 -crf 23 output.mp4",
  "-c:a": "ffmpeg -i input.mp4 -vn -c:a libmp3lame -q:a 2 output.mp3",
  "-c:s": "ffmpeg -i input.mkv -c:s mov_text output.mp4",
  "-map": "ffmpeg -i input.mkv -map 0:v:0 -map 0:a:0 -c copy output.mkv",
  "-vf": "ffmpeg -i input.mp4 -vf scale=1280:720 output.mp4",
  "-af": "ffmpeg -i input.wav -af volume=0.5 output.wav",
  "-r": "ffmpeg -i input.mp4 -r 30 output.mp4",
  "-s": "ffmpeg -i input.mp4 -s 1280x720 output.mp4",
  "-b:v": "ffmpeg -i input.mp4 -c:v libx264 -b:v 2M output.mp4",
  "-b:a": "ffmpeg -i input.wav -b:a 192k output.mp3",
  "-crf": "ffmpeg -i input.mp4 -c:v libx264 -crf 23 output.mp4",
  "-preset": "ffmpeg -i input.mp4 -c:v libx264 -preset slow -crf 23 output.mp4",
  "-movflags": "ffmpeg -i input.mp4 -c copy -movflags +faststart output.mp4",
  "-frames:v": "ffmpeg -i input.mp4 -frames:v 1 frame.png",
  "-pix_fmt": "ffmpeg -i input.mp4 -pix_fmt yuv420p output.mp4",
  "-ar": "ffmpeg -i input.wav -ar 44100 output.wav",
  "-ac": "ffmpeg -i input.wav -ac 1 output.wav",
  "-q:a": "ffmpeg -i input.wav -c:a libmp3lame -q:a 2 output.mp3",
  "-shortest": "ffmpeg -i video.mp4 -i audio.wav -shortest -c:v copy -c:a aac output.mp4",
  "-stream_loop": "ffmpeg -stream_loop -1 -i input.mp4 -t 30 -c copy output.mp4",
  "-itsoffset": "ffmpeg -i video.mp4 -itsoffset 1.5 -i audio.wav -c copy output.mkv",
  "-metadata": "ffmpeg -i input.mp4 -metadata title=Name -c copy output.mp4",
  "-filter_complex": "ffmpeg -i base.mp4 -i logo.png -filter_complex overlay=10:10 output.mp4",
};

const FFPROBE_OPTION_EXAMPLES: Record<string, string> = {
  "-show_format": "ffprobe -show_format -print_format json input.mp4",
  "-show_streams": "ffprobe -show_streams -print_format json input.mp4",
  "-show_entries": "ffprobe -show_entries format=duration -of default=nw=1:nk=1 input.mp4",
  "-print_format": "ffprobe -print_format json -show_format input.mp4",
  "-select_streams": "ffprobe -select_streams v:0 -show_entries stream=width,height input.mp4",
  "-show_chapters": "ffprobe -show_chapters -print_format json input.mp4",
  "-count_frames": "ffprobe -count_frames -select_streams v:0 -show_entries stream=nb_read_frames input.mp4",
};

const FFPLAY_OPTION_EXAMPLES: Record<string, string> = {
  "-ss": "ffplay -ss 00:01:00 input.mp4",
  "-vf": "ffplay -vf scale=1280:720 input.mp4",
  "-af": "ffplay -af volume=0.5 input.wav",
  "-autoexit": "ffplay -autoexit input.mp4",
  "-loop": "ffplay -loop 0 input.mp4",
};

export function optionExample(page: string, flag: string): string | null {
  if (page === "ffprobe") return FFPROBE_OPTION_EXAMPLES[flag] ?? null;
  if (page === "ffplay") return FFPLAY_OPTION_EXAMPLES[flag] ?? null;
  if (page === "ffmpeg") return FFMPEG_OPTION_EXAMPLES[flag] ?? null;
  return null;
}

export function templateCommand(family: string, name: string | null): string | null {
  if (!name) return null;
  switch (family) {
    case "audio-filter":
      return `ffmpeg -i input.wav -af ${name} output.wav`;
    case "audio-source":
      return `ffmpeg -f lavfi -i ${name} output.wav`;
    case "audio-sink":
      return `ffmpeg -i input.wav -af ${name} -f null -`;
    case "video-filter":
      return `ffmpeg -i input.mp4 -vf ${name} output.mp4`;
    case "video-source":
      return `ffmpeg -f lavfi -i ${name} output.mp4`;
    case "video-sink":
      return `ffmpeg -i input.mp4 -vf ${name} -f null -`;
    case "multimedia-filter":
    case "multimedia-source":
      return family === "multimedia-source"
        ? `ffmpeg -f lavfi -i ${name} output.mp4`
        : `ffmpeg -i input.mp4 -filter_complex ${name} output.mp4`;
    case "video-encoder":
      return `ffmpeg -i input.mp4 -c:v ${name} output.mp4`;
    case "audio-encoder":
      return `ffmpeg -i input.wav -c:a ${name} output.m4a`;
    case "subtitle-encoder":
      return `ffmpeg -i input.mp4 -c:s ${name} output.mkv`;
    case "video-decoder":
      return `ffmpeg -c:v ${name} -i input output.mp4`;
    case "audio-decoder":
      return `ffmpeg -c:a ${name} -i input output.wav`;
    case "subtitle-decoder":
      return `ffmpeg -c:s ${name} -i input output.mkv`;
    case "demuxer":
      return `ffmpeg -f ${name} -i input output.mp4`;
    case "muxer":
      return `ffmpeg -i input.mp4 -c copy -f ${name} output`;
    case "protocol":
      return null;
    case "bitstream-filter":
      return `ffmpeg -i input.mp4 -c copy -bsf:v ${name} output.mp4`;
    case "input-device":
      return `ffmpeg -f ${name} -i input output.mp4`;
    case "output-device":
      return `ffmpeg -i input.mp4 -f ${name} output`;
    case "writer":
      return `ffprobe -print_format ${name} -show_format -show_streams input`;
    default:
      return null;
  }
}

export function componentName(title: string): string | null {
  const bare = bareTitle(title);
  const first = bare.split(",")[0]?.trim() ?? "";
  if (!first || first.includes(" ")) return null;
  if (!/^[A-Za-z0-9_@.+-]{1,64}$/.test(first)) return null;
  return first;
}
