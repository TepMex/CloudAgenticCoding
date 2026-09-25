# mock-br-lk — SPEC

## Purpose

Clickable mock-up of the student's personal cabinet ("личный кабинет") for **BRY Chinese**, an online Chinese school for children. A child registers, picks a hero (avatar), and travels across an illustrated map of China by completing short language tasks. There is no backend: everything lives in the browser. The mock exists to show the product idea and visual style to stakeholders.

## Requirements

1. Registration screen (name of the student, parent e-mail, password, consent checkbox, social buttons) matching reference 1.
2. Login screen: **any password is accepted**; an unknown e-mail silently gets a fresh account so the mock never dead-ends.
3. Social buttons (VK ID, Google, Apple, Яндекс ID) sign in to one demo account per provider.
4. Welcome screen after registration (reference 2) with "Создать своего героя" and "Пропустить знакомство" (skip picks the fox and opens the cabinet).
5. Hero choice screen (reference 3) with six generated heroes: Лис, Панда, Тигр, Дракон, Журавль, Обезьяна; 4-step progress indicator.
6. "Герой создан" step: portrait, story, editable hero name with random-name button.
7. Cabinet with a map of China (reference 4): 13 cities, dashed routes, locked/open/current/complete pins, hero avatar on the current city, city progress bar.
8. Top tabs: Карта, Герой, Задания, Характеристики, Награды, Сокровищница; side menu: Мой путь, Достижения, Коллекции, Друзья, События; coins, gems, mail and settings.
9. Every city has 6 words and 4 tasks (meaning, pinyin, hanzi-by-translation, listening via `speechSynthesis` zh-CN). A task passes with ≥3/4 correct answers and gives 25 XP + 10 coins once.
10. Levels: 100 XP per level. A city with level *N* opens when the hero reaches level *N − 1* (Beijing is open from the start; finishing Beijing opens Xi'an).
11. Rewards: daily chest (+20 coins once per calendar day) and 8 achievements claimable for gems.
12. Treasury: 8 items bought with coins or gems; up to 3 equipped items add to hero power.
13. Collection of word cards (flip to see pinyin/translation), friends leaderboard (mock classmates), events list with sign-up, mail with read state.
14. Settings: rename student, sound toggle, reset progress, log out.
15. All state persists in **IndexedDB**; reloading the page keeps the session and progress.
16. Russian UI; layout works on desktop, tablet and phone widths.

## Interfaces

- Static SPA built with Vite + React + TypeScript; run with Bun.
- Hash routes: `#/register`, `#/login`, `#/welcome`, `#/hero`, `#/hero-created`, `#/app/map`, `#/app/hero`, `#/app/tasks`, `#/app/tasks/<cityId>:<kind>`, `#/app/stats`, `#/app/rewards`, `#/app/treasury`, `#/app/collections`, `#/app/friends`, `#/app/events`.
- Route guard (`src/routes.ts`): guests see only register/login; onboarding steps cannot be skipped forward; finished students may revisit `#/hero` to change their hero.
- Deployed to GitHub Pages at `/<repo>/mock-br-lk/` (`GH_PAGES_PUBLIC_PATH`).

## Data model

IndexedDB database `mock-br-lk`, version 1:

- `accounts` (keyPath `email`, lower-cased parent e-mail): `Account` from `src/domain/account.ts` — student name, onboarding step (`welcome` → `hero` → `named` → `done`), hero id and name, XP, coins (start 120), gems, completed task ids, best score per task, inventory, equipped items, claimed achievements, read mail, joined events, last daily chest date, sound flag.
- `meta` — key `session` → e-mail of the signed-in account.

Static content (heroes, cities and words, shop items, achievements, mail, friends, events) lives in `src/domain/*`.

## UI / UX

Visual language taken from the references: bright 3D-cartoon Chinese landscapes, cream cards, deep-green buttons, dark translucent feature bar at the bottom, serif headings (Playfair Display), handwritten notes (Caveat), red banners with hanzi. Generated images live in `public/img/` (6 heroes, 3 scene backgrounds, map, fox logo).

Flow: Register → Welcome → Choose hero → Hero created → Map → City dialog → Task quiz → Result → back to map.

## Out of scope (v1)

- Real authentication, passwords, e-mail delivery, OAuth.
- Server, sync between devices, teacher/parent dashboards.
- Real lessons, video classes, payments.
- Hero clothing rendered on the character (items are icons with a power bonus).

## Acceptance criteria

- `bun test` passes (progression rules, quiz generation, route guard, IndexedDB persistence via `fake-indexeddb`).
- `bun run build` produces `dist/index.html`.
- Manually: register → choose hero → name it → map shows the hero on Beijing → complete all four Beijing tasks → level 1, Xi'an unlocks → reload the page and progress is still there → log out and log back in with any password.
