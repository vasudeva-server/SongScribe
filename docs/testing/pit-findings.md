# PIT Findings Backlog

> Mutants that PIT surfaced but the suite does not catch, captured for later
> remediation. This is a **standalone backlog**, separate from the matrix section
> files (the `done`-column source of truth) and the generated `remediation-ledger.md`.
> When a finding is addressed, kill the mutant, re-run the scoped PIT target, and
> strike the row here (or delete it).
>
> Scope note: these came from **ad-hoc, class-scoped** runs, not the formal
> per-package PIT checkpoints described in `REMEDIATION.md` decision 4. The
> package-level checkpoint still owes a full pass.
>
> Excluded as non-findings: `currentTerminalType` (Song.java:1022) reports two
> `RUN_ERROR` mutants — these flip a fatal-invariant guard whose body calls
> `RuntimeError.exit` → `System.exit(-1)`, which kills the PIT minion. PIT marks
> them `detected='true'`; they are a harness artifact, not coverage holes.

---

## Song — run 2026-05-25 (`./scripts/mutation-test.sh 'songscribe.dom.Song'`)

Result: 311 mutations, 277 killed (89%), test strength 94%. **18 survived, 14 no-coverage**
after `getTempoAt` was strengthened (all 9 `getTempoAt` mutants now killed — not listed below).

### Survived — a covering test runs but does not detect the mutant

| line | method | mutator(s) | what survives / missing assertion | matrix row |
|---|---|---|---|---|
| 1294, 1298, 1306 | `tempoDidChange` (`@Handler`) | VoidMethodCall | removing the clone-before-mutate field copies (`setTempoType`, `setVisibleTempo`, `setShowTempo`) survives — the handler's whole point is to copy fields onto a cloned `Tempo`, yet no test asserts the resulting field values | **r44** ✅ (handler tests exist but are weak) |
| 1293 | `tempoDidChange` (`@Handler`) | NegateConditionals | a conditional in the tempo-sync lambda is unpinned | **r44** ✅ |
| 1470 | `calculateAttributionStartY` | Math (`+`→`-`, `/`→`*`, `*`→`/`), PrimitiveReturns (→`0.0`) | the attribution-Y arithmetic has **zero** assertions — any of 4 mutants survives | (no row; ctor sets `attributionStartYSs`) |
| 1045 | `replaceTerminal` | NegateConditionals | negating the `!incomingType.isValidTerminal()` guard survives — the IAE-path test does not pin the guard's polarity | **r35** ✅ |
| 223, 224 | ctor `lambda$new$0` | VoidMethodCall | removing the initial line's `setKeyAccidentalCount(default…)` / `setKeyType(default…)` survives — no test asserts the **initial line** carries the default key | **r5** (marked *adequate/keep* — contradicted) |
| 330 | `getLine` | VoidMethodCall | removing the `applyLineDefaults(...)` call survives — the effect is tested via `addLine`, but `getLine`'s trigger is unverified | **r24** ✅ |
| 950 | `isModifying` | BooleanTrueReturnVals, ConditionalsBoundary | the predicate has no direct assertion (both mutants survive) | rows 37–40 (`withModification` family) |
| 959 | `isInAutoMaintenance` | BooleanFalseReturnVals | the predicate has no direct assertion | rows 28/30 (terminal maintenance) |
| 232, 241, 257 | ctor `<init>` | VoidMethodCall | removing each `MessageCenter.subscribe(...)` survives — no test asserts the `Song` subscribes to tempo/key/layout/metadata changes | (no row; bus wiring) |

### No coverage — no test exercises the line at all

| line | method | mutator(s) | note | matrix row |
|---|---|---|---|---|
| 1049, 1054, 1056 | `replaceTerminal` (+lambda) | NegateConditionals, Math (`-`→`+`), VoidMethodCall (`Line.setElement`, `withModification`) | the same-type no-op branch and the actual replacement path are untested (only the IAE error path is) | **r35** ✅ |
| 1242, 1246, 1258 | `metadataDidChange` `lambda$…$0` | VoidMethodCall (`setNumber`, `setAttribution`, `setUnofficialTranslation`) | specific field dispatches in the metadata handler are uncovered | **r47** (marked *adequate*) |
| 434 | `getLanguage` | NullReturnVals | return never asserted | (trivial getter) |
| 562 | `hasBeenDynamicallyLaidOut` | BooleanFalse/TrueReturnVals | predicate uncovered | (no row) |
| 566 | `getFormatVersion` | PrimitiveReturns | getter uncovered | (no row) |
| 558 | `getLineWidthPx` | PrimitiveReturns | matches matrix verdict | **r26** (`none` — trivial delegation) |
| 1155 | `postWithModification` (+lambda) | VoidMethodCall (`MessageCenter.post`, `withModification`) | matches matrix verdict | **r49** (`none` — trivial delegation) |

### Priority guidance

- **High:** `tempoDidChange` clone-before-mutate copies (1294/1298/1306) and `calculateAttributionStartY` arithmetic (1470) — both are real logic with no observing assertion, in core `dom`.
- **Medium:** `replaceTerminal` guard + replacement path (1045/1049/1054/1056), ctor initial-line key defaults (223/224), `metadataDidChange` dispatch (1242/1246/1258) — all sit in rows already marked ✅/adequate.
- **Low:** `getLine` trigger (330), `isModifying`/`isInAutoMaintenance` predicates (950/959), ctor bus subscriptions (232/241/257).
- **Matches existing `none` verdict (likely no action):** `getLineWidthPx` (558), `postWithModification` (1155), and the trivial getters `getLanguage`/`getFormatVersion`/`hasBeenDynamicallyLaidOut`.
