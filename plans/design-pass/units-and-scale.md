# Design Pass — `units-and-scale`

Run by `/design-pass 1` — register row `1`, *Units and scale*.

**Target:** `src/main/java/songscribe/dom/{Ss,DocPx,ViewPx,ScaleContext}.java`
**Tests:** none exist.
**Start commit:** `a1e3435c` · **Branch:** `develop`

**Status:** 🔄 in progress

| Step | Status | Plan | Notes |
|---|---|---|---|
| 1 Inventory | ✅ | — | 4 types, 19 public members, 0 tests, 0 `@Nullable`, 1 guard (unreachable) |
| 2 Unrepresentable states | ⏳ | [phases 1–2](../units-and-scale-execution.md) | D1, D3, D4 |
| 3 Extraction | ⏳ | [phases 3–5](../units-and-scale-execution.md) | D2, D5, D6 |
| 4 Contracts | ⏳ | [phase 6](../units-and-scale-execution.md) | per-member contracts sit in the phases that create them |
| 5 Test triage | ⏳ | [phase 7](../units-and-scale-execution.md) | nothing to triage — 0 tests exist |
| 6 Test-only surface | ✅ | — | none in the target |
| 7 Compile and run | ⏳ | [phase 8](../units-and-scale-execution.md) | |
| 8 Diagrams | ⏳ | [phase 8](../units-and-scale-execution.md) | |
| 9 Coverage | ⏳ | [phase 8](../units-and-scale-execution.md) | |
| 10 Mutation | ⏳ | — | opportunistic |

Resume by taking the first phase that
[`plans/units-and-scale-execution.md`](../units-and-scale-execution.md)'s Status
Dashboard does not mark ✅. Delete that plan with this record at step 11.

All contracts in this target classify as **mechanical** under
`reference/classification.md` — unit-conversion arithmetic, and AWT's font model
as an external standard. No domain checkpoint is owed.

## Numbers

| | Before | After |
|---|---:|---:|
| Test cases | 0 | |
| Main LOC | 213 | |
| Test LOC | 0 | |
| Ratio | — | |

Types changed: · Guards retired: · Contracts written: · Elapsed:

## Inventory

### The types and what they fail to carry

| Type | Shape | Invariant not carried |
|---|---|---|
| `Ss` | `record Ss(double value)`, no members | None missing. It is a pure tag, and `docs/zoom.md` states staff spaces deliberately have no integer form. |
| `DocPx` | `record DocPx(double value)` + `roundedPx()`, `ceilPx()` | — |
| `ViewPx` | `record ViewPx(double value)` + `roundedPx()`, `ceilPx()` — **bodies and Javadoc byte-identical to `DocPx`'s** | The size-rounds-up / position-rounds-nearest rule is written twice here, and a third time as `ScaleContext.ssToRoundedPx`. |
| `ScaleContext` | `final class`, private singleton `INSTANCE`, `private volatile double pixelsPerStaffSpace`, 13 static methods | **The document scale is fixed, and the type does not say so.** A mutable field with a public setter represents a document scale other than 8.0 px/ss — a state the rest of the design assumes cannot exist. |

### `@Nullable`

None in any of the four files.

### Guards

One: `ScaleContext.setPixelsPerStaffSpace` throws `IllegalArgumentException` on
`pxPerSs <= 0`. **Callers that can produce the rejected value: none — the method
has no callers at all.**

### Fan-in per public member

| Member | Call sites | Files |
|---|---:|---:|
| `ScaleContext.ssToPx` | 24 | 15 |
| `ScaleContext.ssToRoundedPx` | 19 | 12 |
| `ScaleContext.pxToSs` | 18 (+7 internal) | 9 |
| `ViewPx.roundedPx` | 14 | 8 |
| `ViewPx.ceilPx` | 7 | 4 |
| `ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE` | 5 | 4 |
| `ScaleContext.textWidthSs` | 5 | 3 |
| `DocPx.roundedPx` | 4 | 3 |
| `DocPx.ceilPx` | 4 | 1 |
| `ScaleContext.fontAscentSs` | 3 | 3 |
| `ScaleContext.scaleFont` | 3 | 3 |
| `ScaleContext.textHeightSs` | 2 | 2 |
| `ScaleContext.fontDescentSs` | 2 | 2 |
| `ScaleContext.getPixelsPerStaffSpace` | 2 | 2 — both `× viewScale.factor()` |
| `ScaleContext.ssToInches` | 1 | 1 |
| `ScaleContext.inchesToSs` | **0** (1 Javadoc reference in `LengthUnit`) | — |
| `ScaleContext.fontMaxAscentSs` | **0** | — |
| `ScaleContext.getScaleTransform` | **0** | — |
| `ScaleContext.setPixelsPerStaffSpace` | **0** | — |

### Documents describing this subsystem

`docs/zoom.md` (the three regimes, where zoom is applied), and
`.claude/guides/spatial-units.md` (suffix convention, rounding rules, the
paint-path boundary). Contracts cite these rather than restating them.

### Tests

**Zero.** No test class names any of the four types as its subject. Two test
support classes call the conversions as helpers — `E2ETest` (`ssToPx`,
`ssToRoundedPx`) and `MusicXmlRoundTripSupport` (`ssToRoundedPx`).

## Decisions

Approved by the user before step 2 began. Each was raised with file and line, the
correct structure, and a recommendation; these are the answers.

### D1 — The document scale becomes a constant

**Approved: collapse it.** `ScaleContext.java:43–64` — delete `INSTANCE`, the
`private volatile double pixelsPerStaffSpace` field, `setPixelsPerStaffSpace`
(0 callers), its `pxPerSs <= 0` guard, `getPixelsPerStaffSpace` (2 callers,
both `× viewScale.factor()`), and `getScaleTransform` (0 callers).
`DEFAULT_PIXELS_PER_STAFF_SPACE` becomes `PIXELS_PER_STAFF_SPACE` — "default"
implies an override that will no longer exist.

Why: the field can only ever hold `8.0`, so the mutability is fiction, and
`~/.claude/guides/design.md` *§Scope a global* rules out "a setter that replaces
the global" outright. The fiction is already being paid for in prose —
`MetronomeContent.java:53–55` asserts "`ScaleContext.setPixelsPerStaffSpace` is
never called in production" to justify its own zoom-invariance, and
`docs/zoom.md`'s "the document scale is fixed" is true only by convention. The
class Javadoc at `:38–39` still promises per-view instances "when zoom support is
added"; zoom support was added, as `ViewScale`.

Follow-ups this forces: `LineInvariants.java:227` and `LineComponent.java:298`
read the constant; `MetronomeContent`'s defensive paragraph goes; `docs/zoom.md`
can state the fixed scale as structural.

### D2 — `ScaleContext` → `DocumentScale`

**Approved: rename.** ~25 importing files re-pointed with `jet_brains_rename`.
Once D1 lands, nothing about the class is a *context* — it is a constant and five
conversions. `DocumentScale` in `dom` pairs with `ViewScale` in `ui`, which is
exactly the relationship `docs/zoom.md` describes: a view folds zoom on top of the
document scale.

### D3 — One rounding rule, stated once

**Approved: sealed `PixelDistance`, keeping the names `roundedPx`/`ceilPx`.**
`DocPx.java:15–29` and `ViewPx.java:13–27` are byte-identical bodies with
byte-identical Javadoc, and `ScaleContext.ssToRoundedPx:72` is a third statement
of "positions round to nearest".

New `dom/PixelDistance` — sealed, `double value()`, `default int roundedPx()`,
`default int ceilPx()` — permitting `DocPx` and `ViewPx`. The two types stay
distinct so no call site can transpose them. `Ss` does **not** implement it, which
makes "staff spaces have no integer form" structural rather than conventional.
`ssToRoundedPx` delegates instead of re-rounding. No call sites change.

Considered and **rejected**: renaming to `positionPx`/`sizePx` so the call site
states its intent rather than the operation. Not taken.

### D4 — Uncalled members

- **`fontMaxAscentSs` — delete.** 0 callers.
- **`inchesToSs` — keep, and document why it is uncalled.** `LengthUnit.java:31`
  names it as the layout-side consumer of the user's unit choice, and `LengthUnit`
  is itself documented "Do not remove either as dead code" with
  `specs/184b-page-setup.md` open. Nothing on `inchesToSs` currently tells a
  reader that; add the note `LengthUnit` already carries.

Consequence that made D4 bigger than it looked: `fontMaxAscentSs` is the **only**
caller of `MyFontUtils.getFontMetrics`, which is fed by
`MyFontUtils.java:443–448`, a second 1×1 scratch-graphics ruler built with **no
rendering hints**. `GraphicUtils.MEASURING_GRAPHICS:143` is the first, carries
`setRenderingHints`, and its Javadoc argues that a second ruler produces advances
that "wrap a paragraph at a different word than the paint pass would… text
clipped at paint time. One ruler for measuring and drawing makes it impossible."
Deleting `fontMaxAscentSs` therefore retires the disagreeing ruler: **delete
`MyFontUtils.getFontMetrics` and `METRICS_GRAPHICS` in the same step.**

### D5 — The whole text-measurement consolidation, now

**Approved: the full move, not the `ScaleContext` half.**

The concept has four homes and no name. The axis none of them state — and which
`GraphicUtils.visualBounds:596–612` already explains — is *what the answer is
measured against*: the **advance** (how far the pen moves), the **ink** (where the
marks land, which overshoots the advance for italic descenders and undershoots it
for a "W"'s left bearing), or the **font's own vertical design** (ascent, descent,
leading). Choosing wrongly between the first two is a clipped glyph, or a box that
stutters as zoom sweeps pixel boundaries.

New `songscribe.font.TextMeasurement` — one class, **one** ruler, queries grouped
and named by which of the three questions they answer. Returns `Ss` where the
caller works in staff spaces, raw pixels only where it feeds the toolkit back.

| Direction | Members |
|---|---|
| In, from `ScaleContext` | `scaleFont`, `textWidthSs`, `textHeightSs`, `fontAscentSs`, `fontDescentSs` |
| In, from `GraphicUtils` | `SCREEN_FRC`, `MEASURING_GRAPHICS`, `fontMetrics`, `getTextBlockWidth`, `getTextBlockHeight`, `visualBounds`, `inkHeight`, `extraInkAbove`, `extraInkBelow` |
| Deleted, from `MyFontUtils` | `getFontMetrics`, `METRICS_GRAPHICS` (see D4) |
| Stays put | `LyricRenderMetrics` (a cached per-font layout record built *from* measurements — becomes a caller); `GraphicUtils`'s images, SVG icons, DPI, strokes, screen clamping |

Also collapses a duplication: `font.createGlyphVector(SCREEN_FRC, text).getVisualBounds()`
is hand-inlined at six sites — `LyricRenderMetrics:211`, `EndingBracketGeometry:58`
and `:62`, `Tuplet:66` and `:84`, `KeyCellRenderer:150` — while `visualBounds`
exists to say exactly that, with the null-on-empty case stated once.

### D6 — `MyFontUtils` dissolves into four homes and is deleted

**Approved: four homes.** The user proposed a single `songscribe.font.Resolver`;
the read showed the file holds five concepts, of which only one is resolution.

| Concept | Goes to | Call sites / files |
|---|---|---|
| Resolve a stored name to an installed face — `getAllFonts`, `createFont`, `findFamilyFont`, `findClosestSourceSans3Font`, `resolveSourceSans3Suffix`, the 3 caches | **`font.InstalledFonts`** | 2 / 2 — `DocumentFonts.setFont:84`, `FontFamiliesFactory.create:30` |
| Describe a face to the user — `getFullFontDescription`, `getStyleDescription`, `parsePSName`, `parseStyle`, `ParsedFontName`, 4 regex patterns, 7 abbreviations | **`font.FontDescription`**, as `full(Font)` / `style(Font)` | 7 / 4 — three Song Settings tabs, `StyleEntry:33` |
| Load/register a bundled font — `getLocalFont`, `installLocalFont` | **`font.LocalFonts`** | 15 / 8 — `BravuraFont`, `Tuplet`, `EndingBracketGeometry`, `StatusBar`, `SourceSans3Font`, `UIUtils`, `KeyCellRenderer`, `PreferencesDialog` |
| `getIconFont` | **`font.MusescoreIconFont`**, on `BravuraFont`'s holder idiom | included above |
| `getUIFont` — a `UIManager` lookup with a `JLabel` fallback | **`util.UIUtils`** | 1 external — `KeyDisplay:73` |
| `deriveKernedFont` | private, wherever it lands | internal only |
| `deriveBaselineShiftedFont` | with its one caller's home | 1 — `UIUtils:707` |
| `getFontMetrics`, `METRICS_GRAPHICS` | deleted (D4) | 0, after D4 |

`MyFontUtils.iconFont:83–84` is a `@Nullable` lazily-initialised static doing what
`BravuraFont.Holder.INSTANCE:79–80` does with the holder idiom and no nullable —
*constructible before usable* in `design.md`'s triage. `font.MusescoreIconFont`
retires the nullable and puts all three bundled fonts under one shape.

`getUIFont` leaves `font` because a look-and-feel lookup is Swing, not
fonts-as-a-domain.

### Scope, in counts

6 new classes — `font/TextMeasurement`, `font/InstalledFonts`,
`font/FontDescription`, `font/LocalFonts`, `font/MusescoreIconFont`,
`dom/PixelDistance`. 1 deleted — `util/MyFontUtils`. 1 renamed —
`dom/ScaleContext` → `dom/DocumentScale`. 9 members leave `GraphicUtils`.
~40 caller files touched, nearly all by `jet_brains_rename` / `jet_brains_move`.
6 hand-inlined `visualBounds` sites collapsed.

## Domain contracts confirmed

## Triage outcome

Kept: · Rewritten: · Discarded: · Added:

## Coverage

## Findings claimed

None. No carry-forward item in the register was tagged for pass 1.

## Findings raised

- **→ Pass 18 (`layout`).** `PageModel.getDefaultLineWidthSs():133` routes inches
  → *whole* pixels → staff spaces: `getContentAreaWidthPx():118` rounds the page
  width and both margins through `GraphicUtils.Unit.INCH.convertToPixels` before
  `pxToSs` divides. That is the coarse path `ScaleContext.inchesToSs`'s own
  Javadoc (`:84–89`) warns against — whole-pixel rounding "can push a line that
  only just fits past the staff margin" — and `inchesToSs` is the precise pair it
  says to use instead. Not fixed here: `layout` is pass 18's, and the question of
  whether the *default* line width is a place the exact length matters is that
  pass's to settle with page setup in hand.
- **→ Pass 30 (`util`).** `PageModel` is Javadoc'd as a "Singleton" at `:30` but
  is a private-constructor static utility with no instance. It also carries a
  blanket `@SuppressWarnings("SameReturnValue")` at `:34` covering the whole
  class rather than the two constant-returning accessors that provoke it.
