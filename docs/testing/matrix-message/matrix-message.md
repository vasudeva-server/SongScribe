## 8. `message` (audited 2026-05-22)

Audited via five production-first sub-audits run in two waves: **8A** core message bus; **8B** mutation infrastructure & field-enum validation; **8C** structural mutation records; **8D** `command` messages; **8E** `notification` messages. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e. Scope: 82 production classes (5 core + 34 `mutation` + 22 `command` + 21 `notification`) + 4 `package-info`.

- [8A. core message bus — `Message`, `MessageCenter`, `MessageLogger`, `SelectableMessage`, `SongData`](8a-core-message-bus.md)
- [8B. mutation infrastructure & field-enum validation — `Mutation`, `LineScopedMutation`, `FieldTypeValidator`, field enums, validated-value records](8b-mutation-infrastructure-field-enum-validation.md)
- [8C. mutation records — structural add/remove/insert/delete holders](8c-mutation-records.md)
- [8D. `command` — `*Command` messages](8d-command.md)
- [8E. `notification` — `*DidChangeNotification` messages](8e-notification.md)

### §8 — summary

**79 behavior rows** across 82 production classes: by required level **53 unit / 0 e2e / 26 none**; of the 53 testable rows, **30 missing · 22 adequate · 1 inadequate · 0 wrong-level** (~57% of testable behavior dark). **Zero e2e in the entire package** — message classes are pure logic/data with no integration risk intrinsic to them, so nothing escalates. The 27 `none` rows are pure data holders; many carry incidental existing tests (8 adequate in 8C, 7 adequate in 8D), plus one `none`-level row whose existing guard is itself weak (the parameterized `SpanMutations` test).

**Defining shape: a small well-covered logic core embedded in a large trivial-holder mass.** Predicted exactly by the pre-audit triage — the 86-file count is dominated by pure carriers that collapse to `none`, while the genuine testable surface is ~53 rows concentrated in three places.

**Bright spot (the model for good coverage): `SongDidChangeNotification` (8E)** is thoroughly tested — all four `getLine()` outcomes (empty list, all-song-scoped, different-lines, shared-line), the lazy-cache contract, `hasMutationOf()` (present/absent/empty), and `getMutations()` unmodifiability. No gaps. This is what the rest of the package should look like.

**Highest-concentration gap: validation is structurally untested (8B).** `FieldTypeValidator`'s core contract — throwing `IllegalArgumentException` on a runtime type mismatch — has **zero tests across all four validated records** (`LineKeyChange`, `LineLayoutChange`, `MetadataChange`, `LayoutChange`); only the happy-path construction is exercised. Compounding it, the field-enum `getExpectedType()` mappings (`KeyField`, `LayoutField`, `LineLayoutField`, `MetadataField` — ~20 constants) have **no assertion of a single return value**, so a copy-paste slip to `int.class` would silently disable validation for that field with no test failure (see production observation 3). `ElementField.DURATION_AFFECTING` set membership (drives tuplet-removal policy) and `FontChange`'s song-scoped membership (omitted from the line-scope test) are the other 8B misses.

**Core bus dark (8A).** `MessageCenter` — synchronous dispatch, `@Handler` priority ordering, and `handlePublicationError` propagation — is entirely untested: every test in the suite uses it as a static conduit, none asserts its own behavior. `Message.toString()`, `MessageLogger.init()`/`onMessage`, and `SelectableMessage.isSelected()` are also missing (all unit, all small).

**Trivial mass, three real holdouts.** The `command` package (8D) and the structural `mutation` records (8C) are pure holders → `none`. Only three carriers have derivation worth a unit test: `ToggleTupletCommand.getTupletSize()` (delegates through `action.getTuplet().getSize()`), `ModeDidChangeNotification.isAdjustmentMode()` (`"adjust-"`-prefix predicate), and `MusicSelectionDidChangeNotification.hasLyricSelection()` (null-check derivation).

**`inadequate` (1 testable + 1 none-level guard):** `FieldTypeValidator` null-bypass (8B — records constructed with nulls but the null-pass-through is never explicitly asserted); and at `none` level, the parameterized `SpanMutations` test (8C) asserts `getLine()` identity for all ten span records but never the span payload accessor (`.beam()`/`.tie()`/etc.), so it cannot catch a wrong-field-storage bug.

**Dead/orphan code:** no dead *classes*. One orphan file — `CloseWindowCommand.java` declares no type (license + `package` + unused import only) and has zero usages (verified); see production observation 5.

### message — production observations (out of test-audit scope)

Filed as a tracked GitHub issue (#413; do not fix during audit). Recorded read-only:

1. **`MessageCenter.post()` has no EDT assertion** despite the documented "EDT-only" contract — an off-thread post silently succeeds, hiding threading bugs.
2. **`MessageLogger` double-`init()`** replaces the subscribed instance while the prior one remains weakly subscribed in MBassador, producing duplicate TRACE dispatches until GC reclaims it.
3. **`FieldTypeValidator` primitive-type latent risk** — `Class.isInstance` never matches primitives, so a field enum returning `int.class`/`double.class` would silently accept any value. Not a present bug (all enums use boxed types); nothing guards the regression.
4. **`ElementRangeDeletion` no record-level defensive copy** — accepts a mutable `List<StaffElement>`; the unmodifiability guarantee relies solely on the single `Line.removeRange` caller passing `List.copyOf`. Fragile if a new caller is added.
5. **`CloseWindowCommand.java` declares no type** — license header + `package` + an unused `import songscribe.message.Message` only; the command class does not exist and has zero usages (verified — `CloseWindowAction` does not reference it). Complete or delete.
6. **`MusicSelectionDidChangeNotification` captures live UI state in its constructor** (`ScoreView.getSelectionSize()`/`getController()`/etc.) rather than pre-extracted values, tightly coupling the message to the UI and complicating unit testing of `hasLyricSelection()`.
