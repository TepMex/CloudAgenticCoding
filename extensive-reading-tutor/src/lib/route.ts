import type { Route } from "./types.ts";

const PHASES = new Set(["1", "2", "3", "4"]);

export function routeToHash(route: Route): string {
  switch (route.name) {
    case "library":
      return "/library";
    case "study":
      return "/study";
    case "profile":
      return "/profile";
    case "settings":
      return "/settings";
    case "knowledge":
      return `/knowledge/${route.tab}`;
    case "book":
      return `/book/${route.bookId}/${route.tab}`;
    case "lesson":
      return `/lesson/${route.bookId}/${route.phase}`;
  }
}

export function hashToRoute(hash: string): Route {
  const path = hash.replace(/^#/, "").replace(/\/+$/, "");
  const parts = path.split("/").filter(Boolean);
  const head = parts[0];
  if (head === "study") return { name: "study" };
  if (head === "profile") return { name: "profile" };
  if (head === "settings") return { name: "settings" };
  if (head === "knowledge") {
    const tab = parts[1];
    if (tab === "grammar" || tab === "skills" || tab === "words") return { name: "knowledge", tab };
    return { name: "knowledge", tab: "words" };
  }
  if (head === "book" && parts[1]) {
    const tab = parts[2];
    return {
      name: "book",
      bookId: decodeURIComponent(parts[1]),
      tab: tab === "chapters" || tab === "notes" ? tab : "progress",
    };
  }
  if (head === "lesson" && parts[1]) {
    const phase = parts[2] ?? "1";
    return {
      name: "lesson",
      bookId: decodeURIComponent(parts[1]),
      phase: PHASES.has(phase) ? (Number(phase) as 1 | 2 | 3 | 4) : 1,
    };
  }
  return { name: "library" };
}
