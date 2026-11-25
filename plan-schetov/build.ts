const raw = process.env.GH_PAGES_PUBLIC_PATH ?? "./";
const publicPath = raw.endsWith("/") ? raw : `${raw}/`;

const result = await Bun.build({
  entrypoints: [new URL("./index.html", import.meta.url).pathname],
  outdir: new URL("./dist", import.meta.url).pathname,
  minify: true,
  publicPath,
  target: "browser",
});

if (!result.success) {
  console.error(result.logs);
  process.exit(1);
}
