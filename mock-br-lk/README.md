# mock-br-lk · BRY Chinese student cabinet (mock)

Clickable mock-up of a children's Chinese school cabinet: registration, hero choice, a map of China with cities to unlock, mini-quizzes, rewards and a shop. No backend — everything is stored in IndexedDB, and any password works.

See [SPEC.md](./SPEC.md) for requirements and data model.

```bash
bun install
bun run dev      # http://localhost:5173
bun test         # domain rules + IndexedDB persistence
bun run build    # dist/
```

Images in `public/img/` were generated for this mock (heroes, backgrounds, map).
