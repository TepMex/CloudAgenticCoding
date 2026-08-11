GOAL: Memory Garden v2 — zoomable world map, 220 half-RTH plots, 15 garden cultures, SRS statistics

Repository:
rth-agriculture/

Implement the next version of the existing Memory Garden MVP.

IMPORTANT GENERAL RULES

- Preserve the existing learning/battle mechanic:
  meaning -> user writes Hanzi -> correct strokes damage weed -> completion updates FSRS.
- Do not rewrite the handwriting system.
- Keep Hanzi Writer and ts-fsrs.
- Keep existing character IDs stable so existing FSRS card data can survive migration.
- Do not introduce a large new game engine/framework for this goal.
  Implement this using the existing React/TypeScript architecture.
- Keep the app offline-first.
- Desktop and mobile/touch must both work.
- Do not add text or labels directly onto the garden artwork.
- The map itself should remain primarily visual.

============================================================
1. NEW WORLD MODEL
============================================================

Replace the current model:

    110 RTH lists
    -> 110 gameplay fields

with:

    110 RTH lists
    -> split each list into 2 ordered halves
    -> 220 gameplay plots

Terminology in code:

    GardenRegion
        one of the 15 large visually distinct garden/culture areas

    PlotDefinition
        one gameplay-clearing unit
        contains approximately half of one RTH list

    CharacterDefinition
        one Hanzi
        belongs to exactly one PlotDefinition


Suggested types:

type GardenRegion = {
  id: string
  index: number

  culture:
    | 'bamboo'
    | 'rice'
    | 'lotus'
    | 'tea'
    | 'blossom'
    | 'peony'
    | 'chrysanthemum'
    | 'pine'
    | 'persimmon'
    | 'orchid'
    | 'berries'
    | 'rapeseed'
    | 'wheat'
    | 'wisteria'
    | 'herbs'

  // location of this large region in normalized map coordinates
  mapRect: {
    x: number
    y: number
    width: number
    height: number
  }
}

type GridCell = {
  x: number
  y: number
}

type PlotDefinition = {
  id: string

  sourceRthListId: string
  sourceHalf: 0 | 1

  gardenId: string

  characterIds: string[]
  characters: CharacterDefinition[]

  // one normal plot occupies one base cell;
  // the first five plots occupy two cells each
  cells: GridCell[]

  neighbors: string[]

  seed: number
}

Update:

type CharacterDefinition = {
  ...
  plotId: string
}

Remove the gameplay meaning of CharacterDefinition.fieldId.

============================================================
2. SPLITTING RTH LISTS
============================================================

For every unique RTH/RSH list:

1. preserve original RTH/frame ordering;
2. sort characters by frame if necessary;
3. split the list into two contiguous halves;
4. use a simple midpoint split.

Example:

    const splitIndex = Math.ceil(characters.length / 2)

    firstHalf  = characters.slice(0, splitIndex)
    secondHalf = characters.slice(splitIndex)

The exact side receiving the odd extra character is not important.

Do NOT:
- shuffle characters;
- balance by random assignment;
- duplicate characters.

Acceptance invariants:

- exactly 110 unique source lists;
- exactly 220 PlotDefinition objects;
- every Hanzi belongs to exactly one plot;
- all 2974 current characters remain represented;
- the two halves of an RTH list differ in character count by at most 1;
- original frame ordering remains unchanged inside both halves.

============================================================
3. WORLD GEOMETRY
============================================================

Use the new reference artwork as ONE continuous map.

The visual world consists of:

    5 GardenRegions horizontally
    x
    3 GardenRegions vertically

Total:

    15 GardenRegions

Each normal GardenRegion represents:

    3 columns
    x
    5 rows

of base gameplay cells.

Therefore the whole estate logically forms:

    15 columns
    x
    15 rows

= 225 base cells.

Do NOT render 225 visible UI cards.
These cells exist only for geometry/hit areas/progression.

------------------------------------------------------------
SPECIAL FIRST GARDEN
------------------------------------------------------------

There are only 220 gameplay plots but 225 base cells.

Use the 5 extra cells to make the first five gameplay plots
approximately twice as large.

The first GardenRegion contains 15 base cells but only 10 plots.

Recommended layout:

For each of its five rows:

    [ BIG PLOT spanning 2 cells ][ normal plot ]

Thus:

    plots 001-005:
        span two adjacent cells each

    plots 006-010:
        span one cell each

This consumes:

    5 * 2 + 5 = 15 cells

All remaining 14 GardenRegions contain exactly 15 normal plots:

    14 * 15 = 210

Total gameplay plots:

    10 + 210 = 220

Total occupied base cells:

    15 * 15 = 225

The gameplay content of the large plots is NOT doubled.
They still contain only one half-RTH list.

Their visual territory is doubled so early clearing causes a
larger and more satisfying visible restoration.

============================================================
4. GARDEN CULTURES
============================================================

Assign these cultures in map order, left-to-right/top-to-bottom:

Row 1:
1. bamboo
2. rice
3. lotus
4. tea
5. blossom orchard

Row 2:
6. peony
7. chrysanthemum
8. pine
9. persimmon
10. orchid

Row 3:
11. berries
12. rapeseed
13. wheat
14. wisteria
15. medicinal herbs

GardenRegion is visual/world metadata only.

FSRS does not know anything about cultures.

============================================================
5. MAP ART
============================================================

Use a single clean map image and a single overgrown version.

Expected assets:

    public/assets/garden-map.webp
    public/assets/garden-map_negative.webp

They are two states of the SAME map and must use the same
coordinate system/aspect ratio.

Do not generate/use separate images per garden or per plot.

The implementation must support replacing these files later
with true high-resolution versions without code changes.

Prefer a real high-resolution 4:3 source image.
For close zoom to remain visually useful, target approximately
8K-class artwork if available.

Never use CSS background-size: cover for the actual interactive
map because cropping would break hotspot coordinates.

Instead render the whole artwork inside a fixed world coordinate
container and transform that container.

Layers:

    camera viewport
        |
        +-- world transform
              |
              +-- clean map image
              +-- negative/weed layer
              +-- transparent plot hotspots
              +-- optional debug geometry

Every layer must use exactly the same transform.

============================================================
6. CLEAN / OVERGROWN MAP BLENDING
============================================================

Preserve the current important concept:

    clean map
    +
    overgrown map
    +
    learning-state-dependent reveal

The authoritative value remains:

    plotInfection(plot, cards)

using the existing stroke-weighted formula:

    totalWeight =
        sum(strokeCount of all plot characters)

    weedWeight =
        sum(strokeCount of characters which are new or due)

    infection =
        weedWeight / totalWeight

Rename/refactor fieldInfection() to plotInfection().

The negative map should disappear progressively from a plot as
its infection approaches 0.

A fully clean plot reveals the clean image across its entire
geometry.

A fully infected/new plot shows the negative version.

Large plots 001-005 must reveal both of their base cells together.

IMPORTANT PERFORMANCE RULE:

Pan/zoom must NOT cause the weed compositing algorithm to rerun.

Recalculate the negative-map mask only when:
- card state changes;
- map asset/layout changes;
- resize requires rebuilding backing geometry.

Camera movement itself should only change a CSS transform.

The existing GardenMap masking implementation may be refactored
rather than discarded.

============================================================
7. CAMERA / RTS MAP NAVIGATION
============================================================

Remove the current selected-field UI model.

There must no longer be:

- selectedId;
- selected field panel;
- "click selected field again";
- separate field-selection state;
- fixed 11x10 card grid presentation.

The map is now one large freely navigable world.

Camera state:

type CameraState = {
  x: number
  y: number
  zoom: number
}

Use CSS transforms such as:

    translate3d(...) scale(...)

for smooth movement.

------------------------------------------------------------
DESKTOP / MOUSE
------------------------------------------------------------

Mouse wheel:
    zoom in/out.

Zoom must be centered around the mouse cursor, not around the
center of the screen.

Mouse drag:
    pan the map.

Cursor:
    grab / grabbing while dragging.

------------------------------------------------------------
TOUCH
------------------------------------------------------------

One finger:
    pan.

Two fingers:
    pinch to zoom.

Pinch zoom must preserve the world point under the gesture
midpoint.

Do not allow the handwriting input implementation to interfere
with map gestures because map and BattleScreen are different
screens.

------------------------------------------------------------
TRACKPAD
------------------------------------------------------------

Support native browser trackpad gestures reasonably:

- pinch/zoom should zoom;
- pointer drag must always allow panning;
- do not allow the browser page itself to scroll while the user
  is manipulating the fullscreen map.

Use touch-action appropriately.

------------------------------------------------------------
ZOOM RANGE
------------------------------------------------------------

zoom = 1:
    whole estate visible as bird's-eye overview.

maximum zoom:
    enough that one individual gameplay plot can occupy a
    significant part of the viewport.

Recommended initial range:

    MIN_ZOOM = 1
    MAX_ZOOM = 12

These should be constants and easy to tune.

Use smooth interpolation for wheel zoom.

Clamp camera movement so the map cannot be lost completely
outside the viewport.

Allow a small visual overscroll margin if it improves feel.

============================================================
8. ENTERING A PLOT
============================================================

There is no intermediate GardenRegion screen.

Navigation becomes:

    global map
      -> zoom/pan around same map
      -> interact with individual Plot hotspot
      -> existing BattleScreen

At low zoom, individual plot hotspots should not produce noisy UI.

Recommended behavior:

If plot is clicked/tapped while camera is far away:

    smoothly zoom/focus camera toward that plot.

If plot is clicked/tapped while already sufficiently close:

    if unlocked:
        enter BattleScreen(plot)

    if locked:
        do not enter;
        give subtle visual locked feedback.

Suggested threshold:

    ENTER_ZOOM_THRESHOLD ~= 4-5

Make it a constant.

Do not open a side panel.

Do not place permanent labels over the artwork.

Optional close-zoom hover feedback:
- very subtle highlight;
- cursor change;
- no large card border.

============================================================
9. PROGRESSION / UNLOCKING
============================================================

Preserve the existing distinction:

    permanent world progression
    !=
    dynamic FSRS garden health

A plot becomes initially mastered when every character in that
plot has been successfully produced at least once.

Once unlocked, it never becomes locked again.

After mastering a plot, unlock its adjacent PlotDefinition
neighbors.

Calculate adjacency from occupied base cells.

Two plots are neighbors if at least one cell belonging to plot A
shares a horizontal or vertical edge with a cell belonging to
plot B.

Adjacency must be symmetrical.

Large plots use the union of both occupied cells when calculating
neighbors.

Locked plots remain visibly overgrown.
Do not hide them behind fog.

============================================================
10. SAVE GAME MIGRATION
============================================================

Current saves contain Field-based progress.

Create SaveGame version 2.

Suggested shape:

type SaveGame = {
  id: 'main'
  version: 2

  unlockedPlotIds: string[]
  masteredPlotIds: string[]

  seenCharacterIds: string[]

  cards: Record<string, CardState>
  reviewEvents: ReviewEvent[]

  updatedAt: number
}

Preserve CardState objects exactly.

Character IDs must remain stable, therefore existing FSRS data
must survive migration.

Migration v1 -> v2:

For each old field/RTH-list:

    old field -> new half A plot + half B plot

If old field was unlocked:
    unlock both new half-plots.

If old field was mastered:
    mark both new half-plots mastered.

Preserve:
- seenCharacterIds;
- cards;
- reviewEvents.

Do not wipe an existing user's learning history.

Initial new save:

    unlock plot-001 only.

============================================================
11. STATISTICS SCREEN
============================================================

Add a third main screen:

    type Screen = 'map' | 'battle' | 'stats'

Add a Statistics button/icon to the persistent map UI.

The visual reference is WaniKani's dense kanji progress wall:

    https://smartprogress.do/uploadImages/000248305.jpg

Do not copy WaniKani branding or exact styling.

Use the concept:

    many Hanzi
    densely packed
    each Hanzi represented by a colored tile/circle
    color = SRS memory stage

------------------------------------------------------------
STATISTICS HEADER
------------------------------------------------------------

Show at least:

    Изучено: X / 2974
    На повторение сейчас: X
    Закреплено: X

Also show stage counts.

No historical charts are required for this goal.

------------------------------------------------------------
CHARACTER WALL
------------------------------------------------------------

Render all characters in original RTH frame order.

Each item:

- Hanzi centered inside a compact circle/rounded tile;
- stage color as background;
- readable foreground contrast;
- responsive wrapping;
- dense layout similar in spirit to WaniKani.

Unseen characters:
    neutral gray.

Hover / keyboard focus / tap can show:

- Hanzi;
- Russian keyword;
- RTH frame;
- SRS stage name;
- next review date if one exists.

Do not display 2974 permanently expanded text labels.

============================================================
12. HUMAN SRS STAGES
============================================================

Do NOT change FSRS scheduling.

The stages below are only a visual classification function.

Create:

    getSrsStage(card?: CardState): SrsStage

Derive the current scheduled interval in days.

Prefer the interval represented by the card itself.
If needed derive it from:

    due - last_review

Do not modify due dates to force a stage.

Suggested stages:

0. NEW
   no CardState
   label: "Новый"
   color: neutral gray

1. STEP_1
   interval < 1 day
   short label: "1"
   bright light blue

2. STEP_2
   interval < 2 days
   short label: "2"
   cyan

3. STEP_3
   interval < 4 days
   short label: "3"
   turquoise

4. NOVICE
   interval < 7 days
   label: "Новичок"
   green/teal

5. APPRENTICE
   interval < 14 days
   label: "Ученик"
   blue

6. GURU
   interval < 30 days
   label: "Гуру"
   violet

7. MASTER
   interval < 90 days
   label: "Мастер"
   pink/magenta

8. ENLIGHTENED
   interval < 180 days
   label: "Просветлённый"
   warm pink/red

9. ROOTED
   interval >= 180 days
   label: "Укоренившийся"
   gold

The exact colors should be visually strong and clearly distinct,
but they must fit the existing Chinese-fantasy UI.

Keep colors in one exported configuration object.

A lapse/Again may move a character back to a lower visual stage.
There is intentionally no irreversible "Burned" state because
FSRS remains authoritative and old knowledge can become due again.

============================================================
13. STATISTICS LEGEND
============================================================

Above/beside the Hanzi wall show a compact responsive legend.

Each stage entry:

    color swatch
    stage name
    character count

On narrow mobile screens the legend may wrap.

Color must not be the only accessible indication of the stage:
tooltips/focus labels must contain the textual stage name.

============================================================
14. MAP UI
============================================================

Keep the main map visually clean.

Persistent UI may include:

top-left:
    Memory Garden / Сад памяти brand

top-right:
    learned count
    due count
    Statistics button

Do NOT retain the old selected-field side panel.

At close zoom an optional small unobtrusive hint can appear when
hovering/focusing an available plot, but the artwork should remain
dominant.

============================================================
15. RESPONSIVENESS
============================================================

Desktop:
- fullscreen map;
- wheel zoom;
- drag pan;
- header overlays map.

Tablet/mobile:
- fullscreen map;
- one-finger pan;
- pinch zoom;
- safe-area aware controls;
- no page scrolling underneath map.

Statistics:
- responsive dense character grid;
- usable down to small phone width.

Battle screen:
- do not regress current responsive behavior.

============================================================
16. MAP LAYOUT CONFIGURATION
============================================================

Do not scatter map geometry constants through components.

Create something similar to:

    src/data/mapLayout.ts

It should contain:

- map aspect ratio;
- 15 GardenRegion normalized rectangles;
- internal 3x5 cell geometry;
- plot-to-cell assignment;
- zoom constants;
- optional per-region inset/gap corrections.

The generated artwork has perspective and decorative paths, so
exact hotspot alignment may require manual tuning.

Add a development-only debug mode, for example:

    ?debugMap=1

that renders:

- GardenRegion outlines;
- base cell outlines;
- plot outlines;
- plot IDs.

This debug overlay must not appear normally.

============================================================
17. RECOMMENDED COMPONENT REFACTOR
============================================================

Suggested organization:

src/
  App.tsx

  map/
    WorldMap.tsx
    MapCamera.tsx
    MapWeedOverlay.tsx
    MapDebugOverlay.tsx
    cameraMath.ts

  stats/
    StatisticsScreen.tsx
    CharacterWall.tsx
    srsStages.ts

  data/
    model.ts
    mapLayout.ts

  garden.ts
  learning.ts
  db.ts

This is a recommendation rather than a mandatory exact file
layout, but avoid growing App.tsx further if possible.

============================================================
18. CAMERA IMPLEMENTATION DETAILS
============================================================

Keep camera math independent from React where possible.

Functions should be unit-testable:

    clampZoom()
    screenToWorld()
    worldToScreen()
    zoomAroundPoint()
    clampCamera()

For cursor-centered zoom:

Before zoom:
    worldPoint = screenToWorld(cursor)

After changing zoom:
    change translation so the same worldPoint remains under cursor.

For pinch:
    use the pinch midpoint using the same principle.

Use requestAnimationFrame for high-frequency transform updates
where appropriate.

Avoid triggering React renders for every pointermove if it causes
jank.

============================================================
19. TESTS
============================================================

Add/modify automated tests.

Required unit tests:

DATA MODEL

- produces exactly 220 plots;
- produces exactly 15 GardenRegions;
- every character occurs once and only once;
- source list ordering is preserved;
- every source RTH list produces exactly 2 plots;
- halves differ by <= 1 character;
- exactly 225 base cells are occupied;
- no base cell overlaps two different plots;
- no cell is outside the 15x15 grid;
- plot 001-005 occupy exactly 2 cells each;
- all other plots occupy exactly 1 cell;
- first GardenRegion contains 10 plots;
- every other GardenRegion contains 15;
- adjacency is symmetrical.

INFECTION

- plotInfection behaves like previous fieldInfection;
- new plot is fully infected;
- no due cards => infection 0;
- stroke count remains the weight.

SAVE MIGRATION

- v1 CardState data survives untouched;
- old unlocked field unlocks both halves;
- old mastered field masters both halves;
- seen IDs and review events survive.

SRS STAGES

Test every interval boundary.

CAMERA

Test:
- zoom clamping;
- cursor-centered zoom;
- pan clamping;
- screen/world conversions.

============================================================
20. E2E / INTERACTION ACCEPTANCE
============================================================

Using Playwright or existing test infrastructure verify at least:

DESKTOP

1. map starts at bird's-eye view;
2. wheel increases/decreases zoom;
3. map stays under cursor while zooming;
4. drag pans;
5. camera cannot disappear into empty space;
6. clicking a plot while far away focuses/zooms it;
7. clicking unlocked close plot opens BattleScreen;
8. Back returns to the SAME map/camera position;
9. statistics button opens StatisticsScreen;
10. Back from statistics preserves map camera position.

MOBILE/TOUCH where practical:

1. one pointer pans;
2. two-pointer gesture changes zoom;
3. page itself does not scroll during map manipulation.

============================================================
21. BACKWARD COMPATIBILITY
============================================================

Do not regress:

- Hanzi Writer validation;
- Again/Good grading;
- hints;
- battle animation;
- local character data;
- IndexedDB persistence;
- GitHub Pages relative asset paths;
- Android/file:// compatibility.

Do not introduce CDN requirements.

============================================================
22. CLEANUP OLD IMPLEMENTATION
============================================================

Remove or replace assumptions tied to:

    110 fields
    11 columns
    10 rows

Examples include:

- row * 11 + column;
- repeat(11, ...);
- repeat(10, ...);
- "из 110";
- current FieldDefinition generation;
- field-selected side panel logic.

Search the entire rth-agriculture project for these assumptions.

Do not leave two competing world models alive.

============================================================
23. DOCUMENTATION
============================================================

Update GAME_SPEC.md to describe:

- 15 GardenRegions;
- 220 gameplay plots;
- half-RTH grouping;
- 15x15 logical base grid;
- five double-area starting plots;
- continuous zoomable map;
- no intermediate garden selection screen;
- statistics/SRS stage projection.

Update README.md "Что реализовано".

============================================================
24. DEFINITION OF DONE
============================================================

The goal is complete when:

- the game contains 220 playable half-RTH plots;
- all 2974 characters remain present exactly once;
- first five plots clean approximately twice the visual map area;
- the world uses the 15-culture reference layout;
- the user can smoothly zoom from whole-world overview to an
  individual plot without switching map screens;
- mouse, touch and trackpad interaction remain practical;
- plot state is still projected from FSRS;
- clean/negative full-map artwork blends according to learning
  state;
- old saves migrate without losing CardState;
- statistics shows every Hanzi in a WaniKani-inspired colored
  SRS wall;
- all build, lint and automated tests pass;
- existing BattleScreen behavior is not regressed.

Before finishing:
1. run unit tests;
2. run existing battle tests;
3. run lint;
4. run production build;
5. manually smoke-test desktop zoom/pan;
6. manually smoke-test narrow mobile layout;
7. report the changed files and any map-layout constants that may
   need artistic fine tuning.