/** Resolve a file that lives next to the page.
 * Dev serves the app at `/`. GitHub Pages serves the same files at
 * `/CloudAgenticCoding/ffmpeg-commands/`. Building the URL from the document
 * base keeps both locations working without a second code path.
 */
export function resolveAppUrl(documentBase: string, assetPath: string): string {
  const relative = assetPath.replace(/^\/+/, "");
  return new URL(relative, documentBase).href;
}

export function appUrl(assetPath: string): string {
  return resolveAppUrl(document.baseURI, assetPath);
}
