# Issue #605 — Replace raw constant values in javadocs
## Goal
Remove restated constant _values_ from javadocs, replacing them with `{@value}` (preferred) or `{@link}`, so docs cannot rot when a constant changes.
## Already done
`.agents/rules/development.md:26` now prefers `{@value #CONST}` over `{@link}`, with `{@link}` as the fallback for non-constant types and name-not-value prose. Issue #605's body still says `{@link}` throughout and needs amending to match.
## What the audit is actually about
The dense cases are **formula and table javadocs that restate real constant values** — not the incidental numbers I first assumed. The issue exempts formulas whose numbers are _invented for the example_; it does not exempt formulas built from the actual constants. Those are precisely the brittle ones:

```java
// NoteTypeMapping.java:51 — 480 IS DIVISIONS
* ElementType          <type>    base ticks (DIVISIONS = 480)
```

`{@value}` matters most here: in an aligned table, `{@link}` replaces a number the arithmetic depends on with a clickable name and wrecks the layout. `{@value #DIVISIONS}` renders `480` in place — readable and self-correcting.
## Detection
Script: `scratchpad/scan.py` (session scratchpad). Extracts each file's `static final` numeric constants, then flags javadoc lines restating those values in the same file. Excludes `0/1/2/-1`, license headers, and lines already using `{@value}`/`{@link}`.

Current yield: **136 hits across 45 files.**

**This is a floor, not a ceiling.** The scan misses:

- `String` constants
  
- constants with computed initializers (`A * B`, `new Color(...)`)
  
- **cross-file references** — a javadoc citing a constant declared in another class
  
## Work-list
| tier | files | hits | character |
| --- | --- | --- | --- |
| A — dense formula/table docs | 3   | 21  | `LineJustificationCalculatorTest` (11), `SlideMidiHelper` (7), `KeySignature` (5) — judgment-heavy |
| B — multi-hit | 21  | ~70 | 2–4 hits each, mostly `layout/`, `io/musicxml/`, `dom/` |
| C — single-hit | 21  | 21  | mechanical |
## Phases
### Phase 1 — Broaden detection
Extend `scan.py` to cover `String` constants, computed initializers, and cross-file matches (build a global value→constant index, then flag any javadoc restating a value whose constant is reachable from that file's imports). Re-run; the work-list will grow. Snapshot the resulting list before editing.

_Model: Opus, medium._ Index design and the cross-file ambiguity rule are the only real design work here.
### Phase 2 — Tier C + B mechanical pass
Fan out by package (`layout/`, `io/musicxml/`, `dom/`, `midi/`, `ui/`) — one agent per package, each given its file list and hit lines. Each agent:

1. Confirms the number really is the constant's value, not a coincidence
  
2. Rewrites as `{@value #CONST}` / `{@value Class#CONST}`, or `{@link}` where `{@value}` is illegal
  
3. Leaves genuinely invented example numbers alone, and reports them
  

_Model: Sonnet, medium._ Local, verifiable, high-volume.
### Phase 3 — Tier A judgment pass
The three dense files individually, one agent each. These need a real decision per number about example-vs-real, and `LineJustificationCalculatorTest`'s worked arithmetic must stay readable after substitution.

_Model: Opus, high._
### Phase 4 — Verify
- `./scripts/compile.sh` — catches every illegal `{@value}` (non-constant variable) and broken `{@link}` target
  
- `./scripts/test.sh unit` — no behavior should change; this guards against an edit straying outside a comment
  
- Spot-audit a sample of rewrites for false positives — a wrong `{@value}` silently misdocuments, which is worse than a missed literal
  
## Risks
- `{@value}` **on a non-constant variable** — `static final Color/Dimension/array/computed` is illegal. Compile catches it, but instruct agents up front to avoid generating them.
  
- **Coincidental value matches** — `0.5` matching two unrelated constants. Agents must verify semantic correspondence, not numeric equality.
  
- **Over-application to real examples** — the issue explicitly protects invented illustration numbers. When ambiguous, leave it and report rather than guess.
  
## Decisions

1. **Do not** amend issue #605's body — the rule file is the source of truth.
2. **Commit the scanner** to `scripts/find-javadoc-constants.py` as a regression check.
3. **Checkpoint after Phase 1** — the expanded work-list gets approved before any Phase 2 edits.
