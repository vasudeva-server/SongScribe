# Pass 7, Spans — step 7: test-only surface

Test paths are under `src/test/java/songscribe/`. Production paths are under `src/main/java/songscribe/`. HEAD is `e139a4af`, tree clean.

## What it found

### Scope and method

The scope is every production file that the pass added or changed since `e40e512f`. That is the scope that step 6 used. For each non-private member of those files, I looked for a caller in production outside the member's own class. The IDE reference lookup confirmed each candidate. I also searched the tests for reflection into production code, and the changed files for mutable statics, static setters and `init` methods.

No test uses reflection on a target class. No constructor of a target type is test-only. Every constructor has a production caller.

### Three members that only tests use

1. `Ending.isValidEnd` (`dom/Ending.java:549`) is `public static`. Its one production caller is in its own class, at `Ending.java:194`. Its one caller outside `Ending` is `EndingBracketGeometryTest.reachableAlignments` (`layout/EndingBracketGeometryTest.java:291`), which filters `CLOSING_TYPES` through it. Step 6 added `EndingTest.testSpanningAcceptsAnEndOnlyOnANoteARestOrAnEndingTerminal` (`dom/EndingTest.java:415`). That test pins the accepted end types over every bar type, a note, a rest and a grace note.
2. `Span.location` (`dom/Span.java:322`) is `public`. Its one production caller is `Span.reportedAs` (`:341`), in its own class. Its one other caller is `EndingSectionRuleTest` (`dom/EndingSectionRuleTest.java:135`), which calls it to build the location it expects in the report. `reportedAs` gets the report's location from the same call. Thus the assertion cannot fail if `location` gives a wrong answer.
3. `StaffElementRun.nextNonGraceIndex` (`dom/StaffElementRun.java:673`) has no production caller. Step 2's `Beam` rework (`9c82271c`) removed its three callers in `Beam.java`. Four rows of `StaffElementRunTest.neighborCases` (`dom/StaffElementRunTest.java:172`–`:179`) and one link in that class comment (`:46`) keep it alive. `StaffElementRun` belongs to row 6, which is done. This pass removed the callers, so the dead method is a defect of this pass.

### Surface outside the target that the scan found

- `UndoController.setPendingOpName` (`undo/UndoController.java:411`) is `public static`. Its only callers are the two calls in `withPendingOpNameResult` (`:454`, `:459`), which set the name and restore it. The setter's own Javadoc tells a caller to prefer that scope. `UndoController` belongs to row 15, which has not run.
- `ChainEdit.lineInserted` **and** `ChainEdit.lineRemoved` (`dom/ChainEdit.java:237`, `:257`) are `public static`. Their only caller is `ChainEdit.of` (`:297`, `:298`). No test calls them. `SongAccidentalWalkTest` links them in three comments (`:194`, `:209`, `:253`). `ChainEdit` belongs to row 6b, which is done.

The scan found three more process-level statics in the changed files. Each has a production caller and a stated lifetime, so none is a finding. They are `Song.setDefaultLineWidthProvider` (set one time by `MainFrame.main`), `UndoController.initialize` and `ScoreView.init`.

## What needs deciding

### 1. Make `Ending.isValidEnd` private

**Kind: genuinely test-only.** The fix is to make it `private` and shorten its Javadoc to one line. `EndingBracketGeometryTest` then needs another way to choose its closing types. The question is which way:

<span id="rd-c1">**Option A:** </span>`CLOSING_TYPES` **lists the accepted closing types as literals**: a single barline, a double barline, a final double barline, a left repeat and a crotchet. `reachableAlignments` loses its filter. `BAR_TYPES` stays for the run-on rows.

- Pro: the rows are visible in the test. `EndingTest` owns the accepted set, so the geometry test does not state the rule again.
- Con: if `Ending` starts to accept another bar type, the geometry test gets no row for it until someone adds the type to the list.

**Option B:** `reachableAlignments` **keeps each alignment that** `Ending.spanning` **accepts on the fixture line.**

- Pro: the rows follow the rule. A newly accepted end type gets a geometry row with no edit to the test.
- Con: a fixture that `spanning` refuses for a reason other than the end type removes the row with no failure. For example, a wrong opening element removes every row that opens on it.

### 2. Make `Span.location` private

**Kind: genuinely test-only.** The fix is to make it `private`. `EndingSectionRuleTest.SectionCase` then states, for each removal row, the location that the report must hold. Each value is the ending's line index and range after the edit:

| Row | Expected location |
| --- | --- |
| deleting the opening repeat | line 0, elements 2 to 5 |
| inserting a closing repeat after the opening repeat | line 0, elements 4 to 7 |
| inserting a double barline after the opening repeat | line 0, elements 4 to 7 |
| replacing the opening repeat with a single barline | line 0, elements 3 to 6 |
| deleting the line that holds the opening repeat | line 1, elements 1 to 4 |

The kept row has no report. `SectionCase`'s `boolean removed` becomes a `@Nullable ElementLocationRange reportedAt`: a value means the ending is removed and reported at that location, and `null` means the ending is kept. The test then checks the location against a value that the production code did not compute.

### 3. Delete `StaffElementRun.nextNonGraceIndex`

**Kind: genuinely test-only.** This is a defect, so the fix is made in this pass after the gate. The fix deletes the method, the four rows at `StaffElementRunTest.java:172`–`:179`, and its link in the class comment at `:46`. `previousNonGraceIndex` and `nonGraceRange` have production callers and stay.

### 4. `UndoController.setPendingOpName`

**Kind: a replaceable process-global.** The scope already exists: `withPendingOpNameResult` sets the name and restores it, and its production callers justify it. The public setter is a way around that scope with no restore. The correct structure is a `private` setter, so that the scope is the only way to set the name.

**The question inside this item.** `UndoController` belongs to row 15. <span id="rd-c2">Do I make the setter private in this pass</span>, or carry the finding to row 15 in the register?

### 5. `ChainEdit.lineInserted` and `ChainEdit.lineRemoved`

**Kind: misnamed internal API**, by visibility only. The names and contracts are correct. The two factories are internal steps of `ChainEdit.of`, so the correct structure is `private`. The three comments in `SongAccidentalWalkTest` then name the rekey clause through `ChainEdit#of`, because a link from another class to a private member does not resolve.

**The question inside this item.** Row 6b is done, so no later pass reaches `ChainEdit`. <span id="rd-c3">Do I make the two factories private in this pass</span>, or carry the finding to the register with no row?

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: this
    by: user
    at: "2026-09-30T03:24:36.631Z"
  rd-c2:
    body: yes
    by: user
    at: "2026-09-30T03:25:45.904Z"
  rd-c3:
    body: yes
    by: user
    at: "2026-09-30T03:26:15.577Z"
