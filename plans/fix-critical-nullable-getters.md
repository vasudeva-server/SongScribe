# Fix Critical @Nullable Getters (Issue #55)

## Context

Issue #55 forbids "fallback" code that silently degrades when a critical object is null. The original issue used a `getBBox` null-check fallback as its example; that specific case has already been resolved. However, the core problem remains: several getters that return **critical domain objects** — objects whose absence means the app is in a broken state — are still declared `@Nullable`. This forces every caller to either null-check or use `Objects.requireNonNull()` defensively.

With NullAway + `@NullMarked` now in place, the fix is clean and compiler-verified: removing `@Nullable` from a getter causes NullAway to flag every caller that treated the return value as nullable, surfacing exactly what needs to be updated.

## Primary Target: `Line.getComposition()`

### Why it's critical

Every `Line` belongs to a `Composition`. Lines are created only by the composition parser and by user actions — neither path leaves a line without a parent composition. The only reason the field is `@Nullable private Composition composition = null` is that Java fields start as null before assignment; this does not mean null is a valid runtime value once the object is in use.

The existing caller `LineIO.writeLine()` already asserts this with `Objects.requireNonNull(l.getComposition())`, confirming the intent.

### Changes

**`src/main/java/songscribe/music/Line.java`**
- Remove `@Nullable` from the field declaration: `private Composition composition = null;`
- Remove `@Nullable` from the getter: `public Composition getComposition()`
- The field initializer stays `= null` (it is assigned before any Line is used)

**`src/main/java/songscribe/io/LineIO.java`** (line 66)
- Remove the `Objects.requireNonNull()` wrapper:
  `var composition = l.getComposition();`

**All other callers** (54 files reference `getComposition()`)
- After the above changes, run `./scripts/compile.sh`. NullAway will flag every site that still treats the return value as nullable (null checks, conditional dereferences, requireNonNull wrappers). Fix each flagged site by removing the null guard.
- For any site that genuinely needs to handle an uninitialized-line case (e.g., elements under construction in tests), document why and consider a separate method or assertion.

## Secondary Target: `StaffElement.getLine()`

`@Nullable protected Line line = null` — same pattern as `composition` above: a field that starts null but is assigned before any element is used in the running app.

### Investigation first

Before making this change, run:
```
jet_brains_find_referencing_symbols(name_path="StaffElement/getLine")
```
Look for callers that pass the result to code that expects a non-null `Line`. If every caller either asserts non-null or uses it unconditionally, proceed with the same fix as above.

### Changes (if confirmed critical)

**`src/main/java/songscribe/music/StaffElement.java`**
- Remove `@Nullable` from field and getter
- Run compile; fix NullAway-flagged callers

## Completed

| Getter | Fix |
|--------|-----|
| `Composition.getAttribution()` | Field initialized to `""`, `@Nullable` removed. Callers in `CompositionIO`, `VerticalAdjustment`, and `ExportABCAction` updated to remove null guards. |
| `Composition.getBanglaFont()` / `getBanglaFontMetrics()` / `getFootnoteFont()` / `getFootnoteFontMetrics()` | `@Nullable` removed from all four fields and getters. |
| `MyFontUtils.getLocalFont()` | Made non-null: calls `RuntimeError.exit()` on null stream or any exception rather than returning null. All `Objects.requireNonNull()` wrappers at call sites removed (`getNoteFont`, `getIconFont`, `BaseElementRenderer` static init). Null guard in `installLocalFont` removed. |
| `Composition.getDefaultKeyType()` | Field initialized to `DEFAULT_KEY_TYPE`, `@Nullable` removed. `Objects.requireNonNullElse` wrapper removed in `ExportABCAction`. |
| `ElementType.getInstance()` | `@Nullable` removed (field suppressed with comment — static initializer guarantees all constants are set). `Objects.requireNonNull` wrappers removed from `newInstance()`. |
| `Line.getComposition()` | `@Nullable` removed from field and getter (field suppressed — set before use). `Objects.requireNonNull()` wrappers removed in `LineIO`, `LyricsProcessor`, `SelectionCoordinator`. Null guards removed in `GraceModeManager`, `InsertionSpacingCalculator`, `InsertionElementManager`. Obsolete test `testNullCompositionReturnsFalse` deleted. |
| `StaffElement.getLine()` | `@Nullable` removed from field and getter (field suppressed — set by `Line.addElement()` before use). Null guards removed in `TempoChangeDialog`, `RangeElement` (`getAnchorElementIndex`, `getEndElementIndex`), `FormatMigrator`. |

## Out of Scope

These getters are **legitimately @Nullable** — their null value carries meaning (the feature is absent):

| Getter | Reason |
|--------|--------|
| `StaffElement.getAccidental()` | Note has no accidental |
| `StaffElement.getGlissando()` | Note has no glissando |
| `StaffElement.getTempoChange()` | Note has no tempo change |
| `StaffElement.getBeatChange()` | Note has no beat change |
| `StaffElement.getAnnotation()` | Note has no annotation |
| `ElementType.getName()` / `getTip()` | `PASTE` is explicitly constructed with a null name — legitimately absent |

The `Objects.requireNonNull()` calls in `StaffElementIO.java` for `getGlissando()` (lines 404, 406) are **correct** — they assert structural invariants within a specific XML parsing path (glissando must have been set before its sub-elements appear), not a general contract that glissando is never null.

## Verification

```bash
./scripts/compile.sh          # Must compile clean with no NullAway errors
./scripts/test.sh unit        # All unit tests must pass
```

If tests pass, run the application and open/save a composition to exercise the serialization path through `LineIO.writeLine()`.
