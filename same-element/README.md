# Общий элемент

Train the difference between characters that share a component. Pick a list — all 氵, or just the ones you mix up — then write them. The shared element is already on the sheet; Hanzi Writer grades only the strokes that set the characters apart.

The same UI is the web app and the offline Android APK (`../same-element-android`).

## Develop

```bash
bun install
bun test
bun run dev
```

`bun run dev` and `bun run build` regenerate `public/hanzi/*.json` from `hanzi-writer-data` (gitignored). Stroke order data is Make Me a Hanzi.

## Requirements

See [SPEC.md](./SPEC.md).
