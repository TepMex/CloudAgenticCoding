# Memory Garden — Game & Technical Specification

## 1. Product Summary

**Working title:** Memory Garden / Сад памяти

Memory Garden is a single-player educational game for learning to **produce Chinese characters from meaning**.

The core tested skill is:

> **keyword / meaning → write the correct Hanzi from memory**

The player inherits a large magical Chinese garden. Forgotten or not-yet-learned characters manifest as hostile weeds. To remove a weed, the player must correctly write the corresponding Chinese character. Each correct stroke damages the weed; completing the full character destroys it.

The central metaphor is:

> **Memory is a garden that becomes overgrown when it is not maintained.**

Learning progress, spaced repetition, world state, and visual restoration are therefore one system rather than separate game and study modes.

## 2. Core Design Principles

1. **Production recall is primary.** The player is shown a meaning/keyword and must independently write the Hanzi.
2. **Writing is the combat mechanic.** There is no separate “flashcard UI” during play.
3. **The world is persistent.** The garden is one continuous world, not a sequence of runs.
4. **World progression and memory state are separate.** Unlocking territory is permanent; visual field health changes according to FSRS.
5. **The map is a visual memory dashboard.** The player can look at the world and intuitively see what needs attention.
6. **The game should remain readable without numbers.** Field state is communicated primarily through visuals.

## 3. Source Learning Structure

The game uses the RTH/RSH knowledge base as a structural source.

```ts
type RawRthEntry = {
  frame: number;
  hanzi: string;
  keyword: string;
  rth_list: string;
  lesson: number;
  strokes: number;
  primitives?: string[];
  mnemo_story?: string;
  rth_list_name?: string;
};
```

Use:

- RTH/RSH ordering;
- grouping into lists;
- frame number;
- keyword;
- Hanzi;
- stroke count;
- lesson/list structure.

Do not use the book mnemonic stories as part of the core learning mechanic. Long mnemonic prose should not be included in the production bundle.

## 4. World Structure

### 4.1 Fields

The full world contains **110 fields**, corresponding to the 110 unique RTH/RSH lists across books 1 and 2.

Each field:

- is a rectangular or square garden plot;
- contains one visually distinct cultivated plant theme;
- contains approximately the characters from one RTH list;
- is connected to neighboring fields;
- can be unlocked permanently;
- can become visually overgrown again as reviews become due.

### 4.2 Field Size

The visual area of a field should roughly correspond to the number of Hanzi in that list. Use a compressed scale rather than strict proportionality:

```text
fieldArea ∝ characterCount ^ 0.75
```

The exponent is tunable, approximately `0.65–0.85`.

Goals:

- larger lists visibly occupy more land;
- tiny lists remain clickable;
- the full map stays spatially coherent.

### 4.3 Field Data

```ts
type FieldDefinition = {
  id: string;
  rthListId: string;
  characterIds: string[];

  mapRect: {
    x: number;
    y: number;
    width: number;
    height: number;
  };

  plantStyle: string;
  seed: number;
  neighbors: string[];
};
```

`rthListId` must be globally unique. Do not identify fields only by numeric lesson number if books 1 and 2 can overlap.

## 5. Character Data

```ts
type CharacterDefinition = {
  id: string;
  hanzi: string;

  keyword: {
    ru: string;
    en?: string;
  };

  frame: number;
  fieldId: string;
  strokeCount: number;
  writingDataId: string;
};
```

Example:

```json
{
  "id": "rth1-0142",
  "hanzi": "猫",
  "keyword": {
    "ru": "кошка",
    "en": "cat"
  },
  "frame": 142,
  "fieldId": "rsh-book1-list-08",
  "strokeCount": 11,
  "writingDataId": "猫"
}
```

## 6. Persistent Progress Model

Two kinds of progress must remain independent.

### 6.1 World Progress

```ts
type FieldProgress = {
  unlocked: boolean;
  initiallyMastered: boolean;
};
```

A field may become `initiallyMastered` when every character in it has been successfully produced from memory at least once.

Once unlocked, a field never becomes locked again.

### 6.2 Memory State

Memory state is dynamic and driven by FSRS. A previously mastered field may later become partially or heavily overgrown because characters become due again.

```text
world progression != current visual health
```

This distinction is fundamental.

## 7. Field Infection / Garden Health

The field visually represents unfinished memory work.

Simple MVP formula:

```text
totalWeight = Σ strokeCount(character)

weedWeight = Σ strokeCount(character)
             for characters currently new or due

infection = weedWeight / totalWeight
```

Stroke count serves as a rough workload proxy.

For rendering:

```ts
weedLevel = Math.ceil(infection * 10);
```

Possible states:

```text
0  pristine
1  nearly pristine
...
5  visibly mixed
...
10 almost completely overgrown
```

The authoritative state is FSRS. `weedLevel` is only a rendering projection.

## 8. Map Presentation

The map should look like **one continuous magical garden divided into neighboring plots**, not a UI grid of cards.

Desired properties:

- top-down or lightly isometric view;
- fields share borders;
- stone walls, paths, irrigation channels, hedges, or terrain seams show boundaries;
- plant life fills plots up to those boundaries;
- several fields are visible at once;
- field health is determined visually;
- no percentages are required;
- no labels are required during normal play;
- clicking/tapping a field enters it.

The clean-field layer and weed layer should remain technically separable.

## 9. Field Art Architecture

Avoid authoring 110 × 11 fully unique field scenes.

Use composited layers:

```text
ground
↓
cultivated plant layer
↓
weed coverage mask
↓
weed sprites / vines / roots
↓
decay / desaturation / fog effects
↓
decorative props
```

Each field can be defined by a recipe:

```ts
type FieldVisualPreset = {
  id: string;
  ground: string;
  primaryPlant: string;
  secondaryPlants?: string[];
  decorations?: string[];
  palette?: string;
  weedFamily: string;
  seed: number;
};
```

The field `seed` should produce deterministic placement so a field always looks the same.

## 10. Core Gameplay Loop

```text
Garden Map
    ↓
Player notices an overgrown field
    ↓
Player selects the field
    ↓
Cleaning scene opens
    ↓
A weed corresponds to one due/new Hanzi
    ↓
Prompt shows meaning, e.g. "КОШКА"
    ↓
Player writes 猫 from memory
    ↓
Each accepted stroke damages the weed
    ↓
Full character destroys the weed
    ↓
FSRS is updated
    ↓
Next weed / character
    ↓
Field becomes progressively healthier
    ↓
Return to map
```

The player may choose between restoring old fields, finishing partially cleaned fields, and advancing into newly unlocked territory.

## 11. Battle / Cleaning Scene

The cleaning scene should contain almost no conventional UI.

Required elements:

- exit/back control;
- target keyword/meaning;
- central garden/weed target;
- invisible or nearly invisible writing input area.

Initially do **not** display:

- the target Hanzi;
- stroke order;
- an outline;
- answer choices;
- a visible handwriting grid.

The scene should preserve the established subdued ink-on-fabric Chinese fantasy aesthetic.

## 12. Writing Is Combat

For a character with `N` strokes:

```ts
weed.healthSegments = N;
```

Each accepted stroke:

```ts
weed.damage(1);
```

Possible feedback:

- a branch breaks;
- roots retract;
- part of the weed dissolves into ink;
- dry leaves fall;
- the weed recoils;
- nearby cultivated plants become more visible.

The weed geometry does not need to match the Hanzi geometry. The mapping is conceptual:

```text
correct stroke → attack
```

## 13. Handwriting Recognition Strategy

### 13.1 Do Not Use General OCR for MVP

The game does not need to recognize arbitrary unknown handwriting.

General OCR solves:

```text
unknown drawing
    ↓
classifier
    ↓
which Chinese character is this?
```

The game already knows the expected answer:

```ts
expectedCharacter = "猫";
expectedStrokeIndex = 6;
```

The actual problem is:

> Does the user's current gesture sufficiently match the expected canonical stroke?

This is a constrained stroke-validation problem, not general handwriting recognition.

## 14. Recommended MVP Handwriting Stack

Use:

- **Hanzi Writer**
- **hanzi-writer-data**
- stroke geometry derived from **Make Me a Hanzi**

Hanzi Writer already provides:

- stroke-order quiz logic;
- input capture;
- current expected stroke tracking;
- stroke validation;
- correct-stroke callbacks;
- mistake callbacks;
- completion callbacks;
- backward-stroke checking;
- configurable leniency.

For MVP, use Hanzi Writer as an invisible handwriting-validation layer while the game engine renders the actual battle scene.

## 15. Canonical Stroke Data

Conceptually:

```json
{
  "character": "猫",
  "strokes": [
    "M ...",
    "M ...",
    "M ..."
  ],
  "medians": [
    [[0, 0], [1, 1]],
    [[0, 0], [1, 1]]
  ]
}
```

### `strokes`

SVG outlines for canonical strokes. Useful for rendering references, hints, debugging, and masks.

### `medians`

Center-line polylines describing stroke trajectories. Useful for spatial, direction, and shape matching.

## 16. What a Stroke Matcher Should Check

Validation should combine multiple signals.

### 16.1 Spatial Position

Measure the average distance between the player's stroke and the expected canonical stroke.

### 16.2 Start and End Position

Compare:

```text
user.start ↔ expected.start
user.end   ↔ expected.end
```

### 16.3 Direction

Compare user and reference direction vectors using cosine similarity. This distinguishes left-to-right from right-to-left gestures even when geometry overlaps.

### 16.4 Length

Reject gestures that are much too short or inconsistent with the expected stroke.

### 16.5 Shape

Normalize both paths and compare their curve shapes. A Fréchet-distance-style comparison is appropriate because it respects point order along curves. Allow small rotation tolerance.

### 16.6 Stroke Order

When stroke `N` is expected, optionally compare the gesture against later strokes. If it strongly matches stroke `N+1` or `N+2`, classify it as a probable stroke-order error.

### 16.7 Reverse Direction

Reject geometrically correct strokes performed backwards when correct direction is part of the learning goal.

Recommended:

```ts
acceptBackwardsStrokes = false;
```

## 17. MVP Integration with Hanzi Writer

```ts
const writer = HanziWriter.create(element, "猫", {
  showCharacter: false,
  showOutline: false,

  acceptBackwardsStrokes: false,
  showHintAfterMisses: false,

  leniency: 1.0,
  highlightOnComplete: false
});

writer.quiz({
  onCorrectStroke(data) {
    battleSystem.damageWeed({
      strokeIndex: data.strokeNum
    });
  },

  onMistake(data) {
    battleSystem.rejectStroke({
      strokeIndex: data.strokeNum
    });
  },

  onComplete() {
    battleSystem.destroyWeed();
    learningSystem.finishReview();
  }
});
```

Hanzi Writer should not render the game's weed, particles, camera, or environment.

## 18. Rendering Architecture

```text
┌───────────────────────────────────────┐
│ Game Renderer                         │
│                                       │
│ field                                 │
│ cultivated plants                     │
│ weed                                  │
│ particles                             │
│ animations                            │
│ camera                                │
│                                       │
├───────────────────────────────────────┤
│ Transparent handwriting input layer   │
│ Hanzi Writer / Stroke Engine          │
│                                       │
│ pointer input                         │
│ expected stroke                       │
│ validation                            │
└───────────────────────────────────────┘
```

## 19. Stroke Input Capture

If matching is later moved in-house, use Pointer Events:

```text
pointerdown → start stroke
pointermove → append points
pointerup   → finish stroke → validate
```

```ts
type InputPoint = {
  x: number;
  y: number;
  t: number;
  pressure?: number;
};
```

Pressure should not affect correctness. It may be used for ink width only.

## 20. Input Preprocessing

Before matching:

```text
raw pointer samples
↓
remove duplicates
↓
remove microscopic jitter
↓
resample along curve length
↓
transform to canonical coordinate system
↓
match
```

A practical approach is to resample to roughly `32–48` points. Do not over-smooth because hooks and corners carry information.

## 21. Stroke Completion Timing

Do not validate continuously while the pointer is down. Capture freely and validate only on `pointerup`.

## 22. Correctness Philosophy

The game validates structural correctness, not calligraphic beauty.

Generally accept:

- slightly imperfect handwriting;
- mild rotation;
- small placement errors;
- finger jitter;
- modest length variation.

Generally reject:

- wrong stroke;
- wrong stroke order;
- backwards stroke;
- clearly wrong location;
- missing stroke;
- multiple strokes merged into one gesture;
- meaningless tiny gesture.

Initial recommendation:

```ts
leniency = 1.0;
```

Tune only after collecting real handwriting samples.

## 23. Custom Stroke Matcher — Post-MVP

Once the core loop is proven, vendor/fork or replace the matcher with a custom engine.

```ts
type StrokeMatch = {
  accepted: boolean;
  expectedStrokeIndex: number;

  score: number;
  positionScore: number;
  directionScore: number;
  shapeScore: number;
  lengthScore: number;

  backwards: boolean;
  probableWrongStroke?: number;
};
```

This enables richer feedback and telemetry.

## 24. Hints and Error Handling

Do not reveal the answer after one mistake.

Suggested progression:

- first mistake: ink fades, no weed damage;
- second mistake: same;
- after ~3 failed attempts on the same stroke: briefly show only the next expected stroke as a faint ink ghost for ~500–700 ms.

Do not reveal the full character unless the player explicitly asks for a stronger hint or reaches a failure state.

## 25. Review Grading

Combat completion and FSRS grading must remain separate.

Bad rule:

```text
weed destroyed → FSRS Good
```

Recommended MVP grading:

```ts
function gradeReview(result: ReviewAttempt): Rating {
  if (
    result.hintUsed ||
    result.revealedStroke ||
    result.totalMistakes >= 3
  ) {
    return Rating.Again;
  }

  return Rating.Good;
}
```

For MVP, primarily use `Again` and `Good`.

Do not infer `Easy` from writing speed because mouse, touch, stylus, screen size, and motor ability vary substantially.

## 26. Review Event Storage

```ts
type ReviewEvent = {
  id: string;
  characterId: string;
  timestamp: number;

  rating: "again" | "hard" | "good" | "easy";

  totalMistakes: number;
  strokeMistakes: number[];

  hintUsed: boolean;
  durationMs: number;

  inputDevice: "mouse" | "touch" | "pen";
};
```

This data is useful for matcher tuning and future personalization.

## 27. FSRS

Use an existing FSRS implementation rather than writing FSRS from scratch.

Recommended TypeScript library:

```text
ts-fsrs
```

FSRS owns:

- stability;
- difficulty;
- due dates;
- scheduling state;
- review history.

The game layer projects these states into weeds and garden health.

## 28. State Separation

```text
                GameApp
                   │
       ┌───────────┴───────────┐
       │                       │
   GameWorld               LearningCore
       │                       │
       │                 ┌─────┴─────┐
       │                 │           │
   Renderer          StrokeEngine   FSRS
       │                 │           │
 ┌─────┴─────┐       HanziData   ReviewStore
 │           │
MapScene  CleaningScene
```

Rules:

```text
GameWorld must not implement spaced repetition.
FSRS must not know anything about weeds.
StrokeEngine must not decide world unlocks.
Renderer must not own learning state.
```

Use explicit adapters between systems.

## 29. Suggested Technology Stack

| Area | Recommended Technology |
|---|---|
| Language | TypeScript |
| Build | Vite |
| Game rendering | Phaser |
| Handwriting MVP | Hanzi Writer |
| Stroke data | hanzi-writer-data / Make Me a Hanzi |
| Spaced repetition | ts-fsrs |
| Local database | IndexedDB |
| IndexedDB wrapper | Dexie |
| UI | Minimal HTML/CSS or React outside the game renderer |
| Mobile packaging later | Capacitor |

### Why Phaser

Use the game renderer for:

- world map;
- garden animation;
- weed animation;
- particles;
- camera;
- transitions;
- input effects;
- scene management.

React/DOM can still be used for settings, developer tools, account UI, and accessibility.

## 30. Offline-First Design

Bundle character stroke data locally:

```text
/assets
  /characters
    猫.json
    水.json
    我.json
    ...
```

Recommended rule:

> No CDN dependency is required to start or complete a review session.

## 31. Stroke-Order Variants

Canonical stroke order can vary by region or educational standard. Define one default standard, likely the PRC simplified-character convention used by the selected dataset.

Provide an override mechanism:

```ts
type CharacterWritingOverride = {
  character: string;
  acceptedVariants?: StrokeVariant[];
};
```

Do not manually curate thousands of variants before launch. Add targeted overrides when QA or user reports identify a real problem.

## 32. Anti-Cheese Rules

Recommended:

- only one active pointer contributes to handwriting;
- multi-touch does not draw;
- require minimum meaningful path length;
- require a minimum number of useful samples;
- `pointerup` always ends a stroke;
- accepted strokes cannot be undone during the current answer;
- do not use speed as a correctness signal;
- rely on position, shape, direction, length, and order.

## 33. Handwriting Telemetry

Basic prototype event:

```json
{
  "character": "猫",
  "stroke": 4,
  "accepted": false,
  "inputDevice": "touch",
  "pointCount": 34,
  "durationMs": 428
}
```

After adopting a custom matcher:

```json
{
  "position": 0.91,
  "shape": 0.82,
  "direction": 0.97,
  "length": 0.93
}
```

This enables objective threshold tuning rather than subjective tweaking.

## 34. Handwriting Technical Prototype

Before producing a large amount of content, build a standalone handwriting validation prototype.

Test approximately 100 characters spanning:

- 1–5 strokes;
- 6–12 strokes;
- 13–20 strokes;
- 20+ strokes;
- dots;
- hooks;
- long sweeps;
- dense characters;
- similar neighboring strokes;
- complex spatial layouts.

For each character test:

```text
correct handwriting
slightly sloppy handwriting
wrong stroke
wrong order
reverse direction
neighboring stroke
merged strokes
very short gesture
```

Suggested MVP target:

```text
obviously correct writing: ≥95% accepted
clearly incorrect strokes: ≤2–3% false accepts
```

If handwriting does not feel reliable, do not build deeper game systems first. The game's core fantasy depends on trusting stroke validation.

## 35. Weed Rendering

Each weed corresponds to one character.

```ts
type WeedInstance = {
  characterId: string;
  totalSegments: number;
  remainingSegments: number;
};
```

Initialization:

```ts
weed.totalSegments = character.strokeCount;
weed.remainingSegments = character.strokeCount;
```

On correct stroke:

```ts
weed.remainingSegments -= 1;
```

On final stroke:

```text
remainingSegments = 0
→ destruction animation
→ review completion
```

Visual segment mapping can be artistic rather than literal.

## 36. Visual Damage Progression

Example:

```text
stroke 1 → thin branch snaps
stroke 2 → thorn cluster falls
stroke 3 → root mass retracts
...
final stroke → core collapses → weed dissolves like ink
```

Feedback should be satisfying but restrained.

Established visual direction:

- muted;
- pastel;
- ink-like;
- textile/paper texture;
- subdued Chinese fantasy painting;
- low saturation;
- limited glowing effects.

## 37. Map Weed Rendering

Weed growth should be composited over the clean cultivated field.

Avoid treating infection as only a transparent dark filter.

Better:

```text
clean vegetation
      ↓
coverage mask expands
      ↓
healthy vegetation is suppressed in covered areas
      ↓
weed material replaces it
```

## 38. Weed Coverage Levels

Maintain approximately ten visual degradation levels. They can share deterministic organic masks.

Example:

```text
level 1  → highest-value 10% of mask
level 2  → highest-value 20%
...
level 10 → nearly all of mask
```

This ensures weeds expand from existing patches instead of teleporting randomly between updates.

## 39. Save Data

```ts
type SaveGame = {
  version: number;
  fields: Record<string, FieldProgress>;
  cards: Record<string, FsrsCardState>;
  reviewEvents: ReviewEvent[];
  settings: GameSettings;
};
```

Use IndexedDB via Dexie.

Do not persist visual state that can be derived from learning state, such as field darkness or weed percentage.

## 40. Versioning and Migration

Every save includes:

```ts
version: number;
```

Implement migrations early:

```ts
type SaveMigration = {
  from: number;
  to: number;
  migrate(data: unknown): unknown;
};
```

Learning applications accumulate valuable long-term history, so save compatibility matters.

## 41. Debug Tools

The development build should support:

```text
select any field
set all cards due
set all cards clean
jump weed level 0–10
force unlock
force lock
inspect character
show expected stroke geometry
show user path
show stroke match score
show FSRS card state
simulate review date
```

## 42. Accessibility / Input Devices

Target:

```text
mouse
touch
pen/stylus
```

Do not assume a stylus.

Correctness thresholds should be validated primarily on touch, because finger input is likely the noisiest common case.

Possible visual behavior:

```text
pen   → pressure affects ink width
touch → fixed or velocity-smoothed ink width
mouse → fixed width
```

Correctness remains device-independent.

## 43. Performance Targets

Map:

- 110 fields across a zoomable world;
- avoid 110 independent full-resolution animated scenes;
- use atlases, tile layers, and batching where practical;
- animate only visible/near-visible plots.

Battle:

- target 60 FPS on typical modern mobile hardware;
- handwriting input remains responsive under effects;
- matcher runs after `pointerup`, not continuously every frame.

## 44. Licensing Checklist

Before distribution, verify and include licenses for:

- Hanzi Writer;
- hanzi-writer-data;
- Make Me a Hanzi derived assets;
- fonts;
- generated or third-party art;
- any RTH-derived grouping/keyword data whose distribution requires permission.

Suggested structure:

```text
/third-party-licenses
  HANZI_WRITER.txt
  HANZI_DATA.txt
  ...
```

Licensing review should happen before commercial release.

## 45. MVP Scope

Required:

- 110-field map;
- one RTH list per field;
- field sizes roughly based on character count;
- field unlock graph;
- permanent unlock state;
- FSRS card for each Hanzi;
- new/due characters represented as weeds;
- meaning → write Hanzi prompt;
- stroke-by-stroke validation;
- correct stroke damages weed;
- full character destroys weed;
- field health derived from due/new workload;
- clean field art plus weed overlay;
- local save data;
- return to map after cleaning.

Explicitly out of MVP:

- XP;
- currencies;
- crafting;
- equipment;
- base building;
- character classes;
- quests;
- roguelike runs;
- combat statistics;
- large RPG progression;
- large resource economy.

## 46. Development Order

### Phase 0 — Data Validation

- normalize all RTH/RSH records;
- verify 110 unique lists;
- assign stable field IDs;
- verify every required Hanzi has stroke data;
- report missing or problematic characters.

### Phase 1 — Handwriting Prototype

Build only:

```text
keyword
+
blank writing area
+
stroke matching
+
debug overlay
```

Success criterion:

> writing feels trustworthy and pleasant.

### Phase 2 — Battle Prototype

Add:

```text
weed
stroke → damage
mistake feedback
completion animation
```

### Phase 3 — FSRS

Add:

```text
review state
Again / Good mapping
due dates
review persistence
```

### Phase 4 — Single Field

Implement one complete field with multiple weeds and visible restoration.

### Phase 5 — World Map

Implement:

```text
110 fields
field sizing
unlock graph
visual infection state
navigation
```

### Phase 6 — Content and Polish

Add:

```text
110 cultivated plant identities
weed families
map transitions
sound
ink effects
performance optimization
```

## 47. Primary Technical Risk

The largest risk is not the map, FSRS, or art. It is:

> **Can stroke validation feel accurate enough that the player believes a correct handwritten stroke reliably causes a successful attack?**

A false rejection breaks both the learning contract and the combat fantasy.

Therefore handwriting validation should be prototyped and measured before major content production.

## 48. Core Technical Decision

> **The game does not recognize arbitrary handwritten Chinese characters. It validates a known target character stroke-by-stroke against canonical vector stroke data.**

This provides:

- reliable stroke order;
- immediate per-stroke feedback;
- lightweight local computation;
- offline operation;
- no OCR service dependency;
- direct mapping from correct writing to combat.

Recommended evolution:

```text
MVP
↓
Hanzi Writer
+
hanzi-writer-data
+
existing quiz callbacks

Validation of core gameplay
↓
vendor/fork matcher logic

V2
↓
custom StrokeEngine
+
custom ink renderer
+
custom scoring
+
telemetry-driven thresholds
```

## 49. Product Thesis

The complete product loop should feel like:

> I do not study flashcards to earn resources that improve a garden. Remembering the characters **is** how I restore the garden.

The game succeeds when these become the same action:

```text
remember
=
write
=
fight
=
clean
=
restore world
=
maintain memory
```
