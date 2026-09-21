# Design Pass 6 — Step 7: Test-only surface

## What it found

The search covered every member of the six target types (`Line`, `StaffElementRun`, `EditableRun`, `LineSpans`, `ElementChange`, `ProjectedElements`), and every production call in the target's test classes and the test classes this pass added or changed (`LineTest`, `LineDeletionTest`, `StrandedKeyChangeTest`, `ElementChangeTest`, `SpanTest`, `StaffElementRunTest`, `LineSpansTest`, `LyricRunTest`, `KeyChangeActionTest`, `MusicXmlStrandedKeyChangeTest`).

Two members exist for tests. Both are in `StaffElementRun`'s key queries. The record's *Findings raised* carried both from step 5.

| Member | Production callers | Test callers | Kind |
|---|---|---|---|
| `lastKeyChangeKey()` (`StaffElementRun.java:783`) | none | `MusicXmlStrandedKeyChangeTest:78`, `:114`, `:117` | Genuinely test-only |
| `strandedKeyChangeIndices(int, Key)` (`StaffElementRun.java:677`) | one: `redundantKeyChangeRanges(int, Key)` (`:720`), in the same interface | `StrandedKeyChangeTest:194–196` | Genuinely test-only, as public surface |

The other three kinds do not occur in the target:

- **Replaceable process-global.** No target test swaps a global. The only `@BeforeAll` and `@BeforeEach` methods build fixtures (`StaffElementRunTest:105–145`) or an action (`KeyChangeActionTest:53`).
- **Misnamed internal API.** No test calls a member under a name other than its concept.
- **Lifecycle.** `Line.attach` and `Line.detach` (`Line.java:392`, `:399`) are private and paired around each change of membership. No target type has an `initialize()` without a way back.

Reflection into production occurs in no target test. The only reflection in the test tree is `SubscriptionOwnershipTest`, which belongs to the message bus.

The interface members that only the interface uses (`isBarAt`, `isKeyChangeBarlineAt`, `isKeyChangeBehindBarline`) are already `private`.

## What needs deciding

### 1. Delete `lastKeyChangeKey()`

Kind: genuinely test-only. Nothing in production asks for it. `keyAtEndUnder(Key)` (`:797`) is the total form that production uses, and `lastKeyChangeKeyFrom(int)` (`:761`) is the partial form, with one production caller (`Line.java:333`) and one in `keyAtEndUnder(int, Key)` (`:813`).

The fix deletes the method. The three assertions in `MusicXmlStrandedKeyChangeTest` then assert through production surface:

- `:78` and `:117` assert that the line holds no key change after the read. The replacement is `assertThat(line.getElements()).noneMatch(KeyChangeElement.class::isInstance)`. This states the promise directly: the stranded key change is gone.
- `:114` asserts that the third line's key change survived. The replacement is `assertThat(line.getElements()).filteredOn(KeyChangeElement.class::isInstance)` with a single element whose key is `CHANGED_KEY`. `keyAtEndOfLine()` does not do this job, because it answers `CHANGED_KEY` also when the change is gone and the line inherits `CHANGED_KEY`.

`MusicXmlStrandedKeyChangeTest` is in `io/musicxml`, which a later pass owns. The change there is to the test only.

### 2. Take `strandedKeyChangeIndices` out of the public surface

Kind: genuinely test-only, as public surface. The method is a coherent unit, but its only production caller is `redundantKeyChangeRanges(int, Key)` in the same interface. `StrandedKeyChangeTest` calls it because it is public. It is public only because an interface method is public unless it is declared `private`.

Every promise in its contract is also in `redundantKeyChangeRanges`'s contract (`:696–718`): the forward pass, and that removing one stranded key change can neither create nor hide another. So the public promise that callers rely on is the range form, and the index form is a step inside it.

The fix:

- `StrandedKeyChangeTest`'s stranded cases (`strandedCases()`, `:131–158`) assert through `redundantKeyChangeRanges(fromIndex, keyInEffect)`. Each expected index `k` becomes the range `[k - 1, k]`, because every fixture puts the key change behind a barline. The case record's `expected` becomes a list of `ElementRange`. The class Javadoc (`:40–44`) says "two questions, asked one after the other". It changes to one question: which ranges a deletion of the stranded key changes covers.
- The index scan stops being public surface. There are two sound shapes, and the choice is yours:
  - **(a) A `private` interface method.** `strandedKeyChangeIndices` keeps its name and body. Its contract shrinks to one line, because a private helper with one caller earns no contract depth, and the promises it states already sit on `redundantKeyChangeRanges`. No real downside.
  - **(b) Inline it into `redundantKeyChangeRanges(int, Key)`.** The loop adds `effectiveRange(ElementRange.single(index))` where it now adds `index`, and the `stream().map` goes. No real downside. The difference from (a) is that the scan and the widening sit in one method, so the rule "the tracked key never advances past a stranded key change" is next to the ranges it produces.
- `lastKeyChangeKeyFrom`'s Javadoc (`:746`) links `{@link #strandedKeyChangeIndices}`. A Javadoc link to a private member does not resolve in the public docs, so under (a) and (b) the sentence names `redundantKeyChangeRanges` instead.
