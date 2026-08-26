# Design Pass — `units-and-scale`

Run by `/design-pass 1` — register row `1`, *Units and scale*.

**Target:** `src/main/java/songscribe/dom/{Ss,DocPx,ViewPx,ScaleContext}.java`
**Tests:** none exist.
**Start commit:** `a1e3435c` · **Branch:** `develop`

**Status:** 🔄 in progress

| Step | Status | Notes |
|---|---|---|
| 1 Inventory | ✅ | 4 types, 19 public members, 0 tests, 0 `@Nullable`, 1 guard (unreachable) |
| 2 Unrepresentable states | ⏳ | |
| 3 Extraction | ⏳ | |
| 4 Contracts | ⏳ | |
| 5 Test triage | ⏳ | |
| 6 Test-only surface | ⏳ | |
| 7 Compile and run | ⏳ | |
| 8 Diagrams | ⏳ | |
| 9 Coverage | ⏳ | |
| 10 Mutation | ⏳ | opportunistic |

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

## Domain contracts confirmed

## Triage outcome

Kept: · Rewritten: · Discarded: · Added:

## Coverage

## Findings claimed

None. No carry-forward item in the register was tagged for pass 1.

## Findings raised
