# Findings — steps 1 and 2, pass 6b, _Accidentals_

Paths are under `src/main/java/songscribe/` unless they start with `src/test`.

## What it found

### Types

| Type | What it is | What it does not carry |
|---|---|---|
| `dom/Accidental` | Enum of five alterations, each with a semitone count and a glyph. | Nothing. `semitoneAdjustmentOf` takes a `@Nullable Accidental` (`:43`); see *Nullables*. |
| `dom/AccidentalState` | Sealed interface: `Stamped(ChainedAccidental)`, `Chosen(Accidental)`, `Adopting()`. | "A note on a line of a song is `Stamped`" (`:27–32`) is prose. `NoteElement.setAccidentalState` is `public` and has 14 production call sites in 9 classes. |
| `dom/ChainedAccidental` | Final class: an `Accidental` and a `ChainType`. Package-private constructor; only `AccidentalChain.Walk.decide` (`AccidentalChain.java:284`) calls it. | **Not in the register's row 6b, and in no other row.** `ChainType.decide` (`:79`) takes three adjacent `Accidental` parameters and a `boolean`; it has one caller. |
| `dom/AccidentalChain` | Static holder. `walk`, `walkSong`, the `Restamp` record, and the private `Cursor` and `Walk`. | `Restamp.state` is a `ChainedAccidental`. All five production consumers wrap it as `new AccidentalState.Stamped(restamp.state())`: `Song.java:1423`, `Song.java:1923`, `layout/AccidentalFitGate.java:146`, `ui/edit/GraceModeManager.java:572` and `:779`. |
| `dom/AccidentalChain.Walk` | One walk's state. | `key` is set to `Key.DEFAULT` in the constructor (`:211`) and replaced in `run()` (`:221`) before any read. `inForceIsKey` and `first` are booleans that record whether the walk passed a key change. |
| `dom/ChainEdit` | Record `(int lineIndex, int from, int to, Set<StaffPosition> vacated, boolean rekeys)`. | `@invariant 0 <= from <= to` (`:81`) is not checked by the compact constructor (`:85`). Three adjacent `int` components. The canonical constructor is `public`, and two callers use it past the factories: `AccidentalChain.walkSong` (`AccidentalChain.java:165`) and the `LineInsertion` arm of `of` (`:231`). |
| `dom/ProjectedEdit` | Final class: a live line, an index, the removed and inserted elements, and the projected element list. | Nothing found. It is the only constructor caller of `AccidentalScanView.Projected`. |
| `dom/AccidentalScanView` | Interface with public records `Live(Song)` and `Projected(Song, int, List)`. | `Projected` is `public`, so a caller can pair a projection of one line with a `ChainEdit` on another, which `ProjectedEdit`'s class comment says cannot happen (`ProjectedEdit.java:33–35`). `line(int)` hands out the live `Line`, and `Walk.keyBefore` reads `getRunningKey()` from it (`AccidentalChain.java:383`), outside the view. |

### Nullables

| Where | Answer in `design.md` |
|---|---|
| `Accidental.semitoneAdjustmentOf(@Nullable)` `:43`, and `DiatonicPitch.midi(@Nullable)` `DiatonicPitch.java:68` | No production caller passes null. `NoteElement.getPitch` (`:471`) and `PitchSpelling.soundingAlterFor` (`io/musicxml/PitchSpelling.java:94`) both pass `soundingAccidental()`, which is non-null. Only `PitchLatticeTest:69` passes null. |
| `AccidentalScanView.ownKey` `:82` | Honestly partial: a line inherits or has its own key. |
| `ChainEdit.of` `:214`, `ofModification` `:247` | Honestly partial: a mutation the walk does not read. One caller, `Song.applyChange`. |
| `Walk.spanEnd` `:196`, `existingAtOrAfter` `:419`, `firstUnskippedAtOrAfter` `:408` | Honestly partial: the end of the song. |
| `Walk.previous` `:202`, `previousBefore` `:396` | Partial: nothing before the note on its line. `intervenes` turns null into `true` (`:300`). |
| `Walk.lastKeyChangeBefore` `:386` | Partial lookup. One caller, `keyBefore`. |
| `stored` in `Walk.decide` `:281` | Local. Null for a note that is not `Stamped`. |

### Guards

| Guard | Who can produce the value |
|---|---|
| `Walk.run`, `cursor == null` returns no restamps (`:217`) | `ChainEdit.lineRemoved` for the last line, and a removal at the end of the last line. Reachable. |
| `NoteElement.soundingAccidental` throws on `Adopting`; `Walk.inForceBeforeStart` calls it (`:363`) | A note before the edit's start that is still `Adopting`. `Song.applyChange` walks after every recorded change, so no such note exists then. Under suspended tracking `GraceModeManager` walks by hand, and the load pass uses `walkSong`, which starts at the first element. No producer found. |

### Fan-in, production callers only

| Member | Callers |
|---|---|
| `AccidentalChain.walk` | 4: `Song.applyChange`, `ProjectedEdit.walk`, `GraceModeManager.enterGraceNote`, `GraceModeManager.abort`; plus `walkSong` |
| `AccidentalChain.walkSong` | 1: `Song.stampAccidentalsAfterParsing` |
| `AccidentalChain.Restamp` | 5 consumers, listed above |
| `ChainEdit.of` | 1: `Song.applyChange` |
| `ChainEdit.inserted` | `of`, the `LineInsertion` arm, `GraceModeManager.enterGraceNote` |
| `ChainEdit.removed` | `of`, `GraceModeManager.abort` |
| `ChainEdit.replaced` | `of`, `ProjectedEdit.walk` |
| `ChainEdit.modified`, `lineRekeyed`, `lineRemoved` | `of` only |
| `ProjectedEdit.inserting` | 2: `PreviewElementInserter.walkAndCalculateInsertion`, `ScoreViewController.tryInsertFragment` |
| `ProjectedEdit.replacing` | 3: `PreviewElementInserter.replacementFits`, `SelectionActionApplier.fitsWithChainsWalked`, `ScoreViewController.tryInsertFragment` |
| `ProjectedEdit.elements()` | 1: `PreviewElementInserter.replacementFits` `:318` |
| `ProjectedEdit.walk` | 4, the callers of the two factories |
| `AccidentalScanView.Live` | 4: `Song.applyChange`, `Song.stampAccidentalsAfterParsing`, `GraceModeManager.enterGraceNote`, `GraceModeManager.abort` |
| `AccidentalScanView.Projected` | 1: `ProjectedEdit.walk` |
| `ChainType.decide` | 1: `Walk.decide` |
| `Accidental.semitoneAdjustmentOf` | 2: `DiatonicPitch.midi`, `PitchSpelling.soundingAlterFor` |

### One sequence in four places

"Walk, then stamp each restamp's note plainly" is written out in `Song.stampAccidentalsAfterParsing` (`Song.java:1922–1924`), `GraceModeManager.abort` (`:775–780`), `GraceModeManager.enterGraceNote` (`:563–573`, which also saves the prior state) and `AccidentalFitGate.stamp` (`:141–149`, which also saves the prior state). `Song.applyChange` does the recorded form (`:1420–1430`).

### The policy is stated three times

`docs/accidentals.md`, the class comment of `AccidentalChain` (`:40–66`) and the class comment of `ChainedAccidental` each state what is in force, what intervenes and the five states.

### Docs

`docs/accidentals.md` is the overview of this system. `docs/key-changes.md` and `docs/mutations.md` describe the two systems it reads.

### Tests

| Class | Passing cases | What it covers |
|---|---|---|
| `src/test/.../dom/AccidentalChainTest` | 16: 8 rows of `testWalkSongStampsEveryNoteAsThePolicySays`, and 8 edit tests | `walkSong` against the policy over a case table; `walk` for the three worked edits in `docs/accidentals.md`, three deletions, an adopting move and a key change insertion. It builds `ChainEdit` by hand. |
| `src/test/.../dom/SongAccidentalWalkTest` | 5 | That `Song.applyChange` walks after an edit and that undo restores every state: a note insertion, a range deletion, a pitch change to `Adopting`, a key change insertion, a line deletion. |

No test sends a `LineKeyChange`, an `ElementReplacement`, or a `LineInsertion` with its own key through `ChainEdit.of`. That is the claimed carry-forward finding.

## What needs deciding

### The shape today

```
Song.applyChange ──ChainEdit.of(mutation)──┐
Song.stampAccidentalsAfterParsing          │
GraceModeManager.enterGraceNote/abort ─────┼─► AccidentalChain.walk(AccidentalScanView, ChainEdit)
ProjectedEdit.walk ──Projected + replaced──┘         │  reads Line.getRunningKey() past the view
                                                     ▼
                                          List<Restamp(Line, NoteElement, ChainedAccidental)>
                                                     │
                     every consumer: new AccidentalState.Stamped(restamp.state())
```

What varies is the song the walk reads: live, or one line projected. `AccidentalScanView` is shaped around that and nothing else. The edit does not vary; it is data. What each consumer does with a restamp varies three ways — recorded through `Line.modifyElement`, set plainly, set plainly with the prior state saved — and is not abstracted; each consumer writes its loop. The policy itself does not vary and has one implementation.

### 1. `ChainedAccidental` is in no register row

`dom/ChainedAccidental.java` is the type the walk produces and `Stamped` wraps. Row 6b does not list it, and no other row does. Every item below that touches it needs it to be this pass's. **Question:** <span id="rd-c1">**add it to row 6b**</span>**?** If not, item 2 is carried forward to whichever row takes it.

### 2. Two types for one decided note: `AccidentalState.Stamped` and `ChainedAccidental`

`Stamped` holds one field, a `ChainedAccidental`, and nothing else (`AccidentalState.java:45`). Every consumer of a `Restamp` writes `new AccidentalState.Stamped(restamp.state())` (five sites, listed above), and `Walk.decide` unwraps `stamped.chained()` twice (`AccidentalChain.java:271`, `:280`). One concept, a note the walk decided, has two types.

<span id="rd-c2">**Option A.**</span> `ChainedAccidental implements AccidentalState`**;** `Stamped` **is deleted.** The sealed interface permits `ChainedAccidental`, `Chosen` and `Adopting`. `Restamp.state` is then an `AccidentalState` a consumer sets directly.

- Pro: the package-private constructor stays, so the only thing that can put a decided state on a note is still the walk.
- Pro: the five wrapping sites and the two unwrapping sites disappear.
- Con: `ChainedAccidental` is a class, not a record, and a `switch` over `AccidentalState` cannot deconstruct it with a record pattern; the `Stamped(var chained)` patterns in `NoteElement` (`:407`, `:424`, `:440`) become type patterns with accessor calls.

**Option B.** `Stamped(Accidental sounding, ChainType type)`**;** `ChainedAccidental` **is dissolved into it.** `ChainType` moves to `AccidentalState` or to its own file.

- Pro: one record, deconstructible.
- Con: a record's canonical constructor is as public as the record (JLS §8.10.4), so any caller can build a `Stamped` from an accidental and a type it chose. The invariant that only the walk decides a note, which `ChainedAccidental`'s constructor carries today, would be gone.

**Option C. Keep both.**

- Con: the duplication stays at the seven sites named.

### 3. A place in the song has a private type and a public spelling

`AccidentalChain.Cursor(int lineIndex, int index)` (`:176`) is "a place in the song, ordered in song order". `ChainEdit(int lineIndex, int from, int to, …)` (`ChainEdit.java:83`) spells the same concept as three adjacent `int`s, and `@invariant 0 <= from <= to` is prose. `AccidentalScanView`'s every method takes `(int lineIndex, int index)`.

<span id="rd-c3">**Option A.** `Cursor` **becomes a public** `dom` **type;** `ChainEdit` **carries** `Cursor start` **and** `int length`</span>**, or** `Cursor start, Cursor end`**.** `from <= to` is then either unrepresentable (a length) or checked once in `Cursor` arithmetic. `AccidentalScanView.elementAt(Cursor)` follows.

- Pro: the transposition of `lineIndex`, `from` and `to` at a call site becomes a compile error. The two call sites that build a `ChainEdit` without a factory (`AccidentalChain.java:165`, `ChainEdit.java:231`) are the ones exposed today.
- Con: `ChainEdit.of` reads `line.index()` and `index()` off every mutation, so the mutation records keep the two-int spelling; the conversion sits in the factories.

**Option B. Keep the ints; check** `from <= to` **in the compact constructor.**

- Con: a guard whose producer is only the two non-factory call sites named, both inside this target. `design.md` says a guard nobody can trip does not go in, so the answer is to close the constructor, which is item 4.

### 4. `ChainEdit`'s canonical constructor is a second way to build one

Two call sites bypass the six factories: `walkSong`'s restart edit (`AccidentalChain.java:165`) and the `LineInsertion` arm's rebuild with `rekeys = true` (`ChainEdit.java:229–231`). Both express an edit the factories have no name for: "start here, and walk to the next key change". A record's canonical constructor cannot be closed (JLS §8.10.4).

<span id="rd-c4">**Option A. Add the missing factories and make** `ChainEdit` **a final class with a private constructor**</span>**.** `ChainEdit.rekeyingFrom(lineIndex, index)` for `walkSong`, and `ChainEdit.lineInserted(Line line)` that carries the own-key rekey itself, so `of`'s arm calls one factory.

- Pro: every `ChainEdit` in existence came from a factory whose Javadoc states what it means, and `from <= to` holds by construction.
- Con: `AccidentalChainTest:189`, `:207` and `:222` call the canonical constructor for a pitch change; they call `ChainEdit.modified` instead.

**Option B. Add the two factories, keep the record.**

- Pro: `of` reads as one factory per arm.
- Con: the constructor stays open, so the invariant is a convention.

### 5. `AccidentalScanView.Projected` is public, and so is the pairing it must not allow

`ProjectedEdit`'s class comment (`:31–35`) says it exists so the walk "is never handed a projection of one line and an edit on another". `Projected` is a public record on a public interface (`AccidentalScanView.java:126`), so any caller can hand it exactly that. Its only constructor call is `ProjectedEdit.walk` (`ProjectedEdit.java:111`). The three other `walk` callers all pass `Live`.

<span id="rd-c5">**Option A.** </span>`AccidentalChain.walk(Song, ChainEdit)` **is the public entry; the view is an implementation detail.** `AccidentalScanView` becomes package-private, `Projected` moves into `ProjectedEdit` as a private nested class, and `ProjectedEdit.walk` calls a package-private `AccidentalChain.walk(AccidentalScanView, ChainEdit)`.

- Pro: the pairing invariant is carried by `ProjectedEdit` alone, and no public signature names the view.
- Con: `AccidentalFitGateTest` and `AccidentalChainTest` build views today; they build songs and `ProjectedEdit`s instead.

**Option B. Keep** `Projected` **public.**

- Con: the invariant `ProjectedEdit` was written to carry stays a convention.

### 6. `rekeys` is a mode written as a boolean

`ChainEdit.rekeys` decides how far the walk goes: to the first untouched note at each open position, or to the next key change (`AccidentalChain.java:312`). The Java rules say a boolean that selects a mode is an enum.

<span id="rd-c6">**Option A. An enum**</span>**,** `Reach { TO_UNTOUCHED_NOTE, TO_NEXT_KEY_CHANGE }` **or a name the review settles.**

- Pro: a factory's rekey argument reads as what it means at the call site.
- Con: none real. The factories compute it from `anyRekeys`, which then returns the enum.

**Option B. Keep the boolean.**

- Con: the mode is read at `:277` and `:312` as a bare flag.

### 7. `ChainType.decide` takes three adjacent `Accidental`s

`ChainedAccidental.java:79`: `decide(Accidental sounding, Accidental inForce, Accidental keyAlteration, boolean intervenes)`. One caller. The rules say two or more adjacent parameters of one type need a parameter record. The three values are the walk's three facts about a note.

**Option A. A record of the context a note is decided in,** `(Accidental inForce, Accidental keyAlteration, boolean intervenes)`**, with** `decide(sounding, context)`**.**

- Pro: `sounding` cannot be swapped with `inForce`.
- Con: none real; `Walk.decide` already computes the three values on adjacent lines (`:270`, `:279`).

<span id="rd-c7">**Option B. Keep the signature**</span>**.**

- Con: a transposition of `sounding` and `inForce` compiles and inverts `ANCHOR` and `CANCEL`.

### 8. `semitoneAdjustmentOf` and `DiatonicPitch.midi` accept null nothing sends

`Accidental.semitoneAdjustmentOf(@Nullable Accidental)` (`:43`) and `DiatonicPitch.midi(@Nullable Accidental)` (`:68`). Both production callers pass `soundingAccidental()`, which is total. Only `PitchLatticeTest:69` passes null. The Javadoc at `:34–37` states a rule about "no accidental" that no production caller relies on.

<span id="rd-c8">**Option A. Drop the nullable**</span>**:** `midi(Accidental)` **and a public** `Accidental.semitones()`**; delete** `semitoneAdjustmentOf`**.**

- Pro: the "null and NATURAL both answer 0" rule, which nothing uses, is gone with its Javadoc.
- Con: `io/musicxml/AccidentalMapping.java:32`'s `{@link}` and `PitchLatticeTest:69` are updated.

**Option B. Keep it.**

- Con: a `@Nullable` whose invariant, "every note sounds something", already holds at every caller.

### Ownership of what step 1 found uncarried

| Invariant | Owner | Handled |
|---|---|---|
| Only the walk decides a note | `ChainedAccidental`'s constructor | Items 1, 2 |
| `0 <= from <= to`, and no edit built outside a factory | `ChainEdit` | Items 3, 4 |
| A projected view and its edit describe one line | `ProjectedEdit` | Item 5 |
| A note on a line of a song is `Stamped` | `NoteElement` and `Line`, via `setAccidentalState`'s 14 call sites | Not this target's type. Raised to the register for the row that owns `NoteElement`. |
| "Walk, then stamp plainly" written four times | `AccidentalChain` or `Restamp` | Step 4's axis; in the record's *Findings raised*. |
| The policy stated in three places | `docs/accidentals.md` and two class comments | Step 5's axis; in the record's *Findings raised*. |

### Deliberately not abstracted

- How a consumer applies a restamp. Three behaviors, four callers. Step 4 asks whether one method on `AccidentalChain` serves them; the class design does not need a type for it.
- `Walk.keyBefore` reads `getRunningKey()` from the live line, past the view (`AccidentalChain.java:383`). `AccidentalScanView`'s Javadoc (`:32–35`) states why every view gives the same answer. That stays.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: yes
    by: user
    at: "2026-09-22T00:29:31.274Z"
  rd-c2:
    body: this
    by: user
    at: "2026-09-22T00:32:45.325Z"
  rd-c3:
    body: this
    by: user
    at: "2026-09-22T00:35:25.997Z"
  rd-c4:
    body: this
    by: user
    at: "2026-09-22T00:35:47.148Z"
  rd-c5:
    body: this
    by: user
    at: "2026-09-22T00:36:34.324Z"
  rd-c6:
    body: this
    by: user
    at: "2026-09-22T00:37:05.796Z"
  rd-c7:
    body: this
    by: user
    at: "2026-09-22T00:37:35.285Z"
  rd-c8:
    body: this
    by: user
    at: "2026-09-22T00:37:54.545Z"
