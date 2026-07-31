# Don't Allow Invalid Tuplets

Enforce tuplet validity at every point a tuplet can enter or change in the document: user editing, paste, file load, import, and edits to the beat. Invalid tuplets are prevented at creation and dropped on load, with a report to the user.

The validity model is defined relative to the **beat**. If the song has a tempo, its note value establishes the beat; otherwise the beat is a quarter note. The music is meterless — there is no time signature and no `Measure` object — so the beat is the only metric context available.

**Issue:** vasudeva-server/SongScribe#604
**Corpus evidence:** [abc-corpus-tuplet-outliers.md](../docs/abc-corpus-tuplet-outliers.md)
**Analysis scripts:** `scripts/` in the ABC corpus tree (`~/Documents/Centre/Music/SongScribe songs/ABC`)

* * *

## Validity Model

### Terms

| Term | Meaning |
|------|---------|
| `N` | actual-notes — the printed tuplet number |
| `M` | normal-notes — the count of `V`-notes whose time the group occupies |
| `V` | the tuplet's written note value (its unit) |
| `B` | the beat duration in effect at the tuplet's anchor |
| `S` | the summed written duration of the tuplet's non-grace elements |

A tuplet means: `N` **notes played in the time normally occupied by** `M` **notes of value** `V`**.**

### N is given; V is derived

`N` is **not** derived from the selection. It comes from outside:

- **editing** — the grade the user picks from the menu
- **load and import** — the printed number stored in the file

`V` is then derived: `V = S / N`.

This direction matters, and the corpus settles it. The alternative — deriving `V = gcd(durations)` and `N = S / V` — renumbers ordinary notation, because the greatest common divisor lands on the finest written value rather than on the tuplet's unit:

```
quarter quarter eighth eighth, printed 3
    V = S/N  ->  V = 288/3 = quarter    a quarter-note triplet, correct
    gcd      ->  V = eighth, N = 6      renumbered to a sextuplet

eighth + four sixteenths, printed 3
    V = S/N  ->  V = 144/3 = eighth     a triplet with split members, correct
    gcd      ->  N = 6                  renumbered
```

141 tuplets in the corpus are renumbered this way by gcd. 14.5% of all tuplets are mixed-value groups, so this is not an edge case. `gcd` is also incapable of detecting invalidity at all — it is notatable in 100% of cases by construction, whereas `S / N` is notatable in 99.3% and the 0.7% remainder is exactly the population this issue targets.

### Constraints

A tuplet is valid iff all of the following hold.

1. **Positive integers, and meaningful.** `N` and `M` are positive integers with `N != M`, and `N <= 7`. A ratio where `N == M` changes nothing and is not a tuplet. `N > 7` is uncreatable in this editor and cannot be expressed exactly in MusicXML at the chosen `DIVISIONS` (see [§9](#9-musicxml)), so a file carrying one is rejected.

2. `V` **is notatable.** `S / N` is an exact integer and equals some `ElementType` base duration multiplied by 1, 1.5, or 1.75 (0, 1, or 2 dots). This is the check that rejects genuinely malformed tuplets. `S == 0` — a span containing only grace notes — fails here, since `V = 0` is not a note value.

3. **Performed span.** The performed duration is `M * V`. Each element's performed duration is its written duration scaled by `M / N`.

4. **No fermata.** No element in the span carries a `FermataAttachment`. A fermata inflates `getDuration()` by half again, which would break the exact-span property in playback; excluding them keeps written and performed durations in an exact ratio.

5. `M` **is conventional, and the ratio re-times.** `M` is one of the conventional regular spans for `V` under `B` (see below), and `N / M` is **not** a power of two.

6. **No beat barrier inside the span.** The tuplet does not span a beat barrier. Otherwise the beat changes partway through and `M` is ambiguous.

7. **No structural boundary.** The span does not cross a barline (`isBarLine()`), a repeat (`isRepeat()`), or a `BREATH_MARK` — except a breath mark immediately following the last note, which selections already sweep in (commit `6bcc1c6b`) and which must not disable the control on ordinary selections.

Constraints 1–4 are intrinsic. Constraints 5–7 are editor policy and apply asymmetrically — see [Where Each Constraint Applies](#where-each-constraint-applies).

Grace notes ride along inside the span and contribute **no** duration. Rests count toward `S` exactly as notes do.

`getDuration()`'s fermata inflation must not reach the validator — a fermata is a performance instruction, not a change to the written rhythm. The validator uses `getDefaultDurationWithDots()` (`dom/StaffElement.java:707`). With constraint 4 in place, playback and validation now sum the same values.

### Conventional regular spans

The spans a tuplet may conventionally replace, measured in `V`-units:

- **binary divisions** of `B` — of `B / 3` when `B` is dotted, since a dotted beat divides into three before subdividing binarily
- **binary multiples** of `B` — `B`, `2B`, `4B`, `8B`

expressed in `V`-units, keeping only those that divide exactly, and excluding `M <= 1`. `M = 1` is never useful and excluding it is what makes a compound duplet infer 2:3 rather than the meaningless 2:1.

### Choosing M

```
M = the largest conventional span strictly below N
    if none exists and B is dotted and N == 2:
        M = the smallest conventional span above N
    otherwise the tuplet is redundant

then: if N / M is a power of two, the tuplet is redundant
```

The fallback is restricted to **compound-beat duplets**, which is what produces the invalid diagonal.

Two distinct reasons a number can say nothing, both ending in rejection:

- **No span below `N`.** `N * V` is itself the smallest conventional span — the notes already fill a natural grouping. This is `N = 2` at a simple beat and `N = 3` at a compound one.
- **A power-of-two ratio.** The group is exactly expressible at the next finer written value with no bracket at all, so the number is a renotation instruction rather than a re-timing. `4:2` over four eighths at a quarter beat means "read these as sixteenths"; `6:3` at a compound beat means the same. This generalizes the `M > 1` exclusion above, which is the `2:1` case of the same rule.

Resulting ratios at a quarter beat, by `V`:

| N | V = quarter | V = eighth | V = 16th |
| --- | --- | --- | --- |
| 2 | _redundant_ | _redundant_ | _redundant_ |
| 3 | 3:2 | 3:2 | 3:2 |
| 4 | _redundant_ | _redundant_ | _redundant_ |
| 5 | 5:4 | 5:4 | 5:4 |
| 6 | 6:4 | 6:4 | 6:4 |
| 7 | 7:4 | 7:4 | 7:4 |

At a dotted-quarter beat:

| N | V = quarter | V = eighth | V = 16th |
| --- | --- | --- | --- |
| 2 | 2:3 | 2:3 | 2:6 |
| 3 | _redundant_ | _redundant_ | 3:2 |
| 4 | 4:3 | 4:3 | _redundant_ |
| 5 | 5:3 | 5:3 | 5:2 |
| 6 | _redundant_ | _redundant_ | 6:2 |
| 7 | 7:6 | 7:6 | 7:6 |

Spans, in `V`-units: at a quarter beat they are `{2, 4, 8, 16, …}` for every `V`, which is why all three columns agree. At a dotted-quarter beat they are `{3, 6, 12, …}` when `V` is the beat's third or coarser, and `{2, 6, 12, …}` when `V` is finer — the sub-beat divides binarily, but nothing between one division and the whole beat is conventional.

A sixteenth triplet occupies half a beat under a quarter beat — the loose policy, not whole beats.

The `V` = 16th column at a dotted-quarter beat has a gap: spans jump from 2 to 6, which is why 5:2 and 6:2 compress harder than a musician would expect. Four sixteenths — two of the beat's three divisions — is writable but not a conventional grouping, so it is not a span. Deferred to **#703**; the corpus has no example either way.

### Evidence

Measured over 22,818 ABC files, 7,500 tuplet markings — the repertoire this app is designed to import:

| | count | share |
|---|---|---|
| conform to the model above | 7,352 | **98.03%** |
| `V = S/N` not a note value | 44 | 0.59% |
| no conventional span below `N` | 80 | 1.07% |
| `N / M` is a power of two — renotation | 21 | 0.28% |
| `V` dotted with no conventional span under `B` at all | 3 | 0.04% |

The 80 with no span below `N` are 41 duplets at a simple beat and 39 markings whose `N` equals the compound beat's division count.

The 21 power-of-two rejections are all `N = 4`: 17 with `V` = eighth at a quarter beat, 2 with `V` = 16th at a dotted-quarter beat, 1 with `V` = 16th at a quarter beat, and 1 with `V` = eighth at a half beat. There are no `N = 6` or `N = 8` cases in the corpus, so the practical effect of this constraint is entirely `N = 4`.

Of the 897 tuplets that state `M` explicitly, the convention differs in 97 — but **83 of those have `q == r`**, the group size written into the ratio slot, a known data-entry error. After discounting those, the convention disagrees with authored data in **4 cases out of 897**.

`docs/abc-corpus-tuplet-outliers.md` enumerates file, line and source text for 79 of the 127 outliers (44 in section A, 35 in section C); its other tables are summary-only, and its counts are stale relative to the current scanner. Completing and regenerating it is **#704**; do not cite it for per-file evidence until that lands.

Grades above 3 are rare but real — 3.2% of the corpus — so all six grades stay. `V` is dotted in 3 tuplets, which is why `V` must carry a dot count.

* * *

## Where Each Constraint Applies

Validation is asymmetric. Creation is strict; load and import reject only what cannot be represented. The corpus records what files contain; the editor's job is to stop producing more of it.

| | edit | paste | load | import |
|---|---|---|---|---|
| source of `N` | menu grade | source tuplet | file | file |
| source of `M` | convention | convention | file, or convention | convention |
| 1. `N`, `M` positive, `N != M`, `N <= 7` | ✓ | ✓ | ✓ | ✓ |
| 2. `V = S/N` notatable | ✓ | ✓ | ✓ | ✓ |
| 4. no fermata in the span | ✓ | ✓ | ✓ | ✓ |
| 5. `M` conventional, ratio re-times | ✓ | ✓ | — accept the file's `M` | — |
| 6. no beat barrier in the span | ✓ | ✓ | — | — |
| 7. no barline / repeat / breath | ✓ | ✓ | — | — |

One validator, one strictness flag. Edit and paste are STRICT; load and import are LENIENT.

Redundancy is a property of **derivation**, not of the tuplet. When `M` is derived and the number says nothing, the tuplet is dropped. When `M` is stated in a file and is otherwise valid, it is kept even if non-conventional — a file that explicitly says 3:2 for three eighths under a dotted-quarter beat is asking for a real re-timing, and a third-party 7:8 must survive.

* * *

## Scope

### In scope

- `Tuplet` stores `N`, `M`, and `V`.
- A validator shared by five call sites: **editing**, **paste**, **load**, **import** (none exists yet; the seam must be there for it), and **beat edits**.
- Beat resolution from the song's tempo, `TempoChangeAttachment`, and `BeatChangeAttachment`, folded into the existing `Song.getTempoAt`.
- Menu items filtered by validity through the existing per-action enabled state, with the existing grade shown as a checked radio item.
- Load-time validation as a post-load pass owned by both readers: drop invalid tuplets, mark the song modified, report to the user.
- Removal of tuplets invalidated by a beat edit, funnelled through one `Song` chokepoint, with an alert.
- Removal of tuplets invalidated by a paste into a different beat context.
- Rests permitted inside newly created tuplets.
- Playback uses the stored `M/N` instead of deriving its own ratio.
- MusicXML round-trips all three fields, including `<normal-type>` and `<normal-dot>`.
- A single migration report covering both dropped tuplets and the existing retired-accidental conversion.

### Out of scope

- **Malformed spans.** A dangling `<tuplet type="start">`, an orphan stop, or a span covering fewer than two non-rest notes is structurally broken markup, not a semantically invalid tuplet. These keep throwing `SAXException` via `DocumentValidation.corrupt` (`io/LineIO.java:452`, `io/musicxml/RangeSpanResolver.java:210`). `Tuplet.hasValidSpan` is untouched.
- **Nested tuplets.** Already impossible: `Line.addTuplet` (`dom/Line.java:1879`) calls `removeOverlappingTuplets`, so a new tuplet deletes any tuplet it overlaps.
- **Preserving a tuplet through a subdivision edit.** Splitting a note inside a tuplet destroys it, because insertion calls `removeOverlappingTuplets` (`dom/Line.java:1377`, `:1417`) and `PreviewElementManager.java:2065-2070` removes a containing tuplet when a note's type or dot count changes. The user can re-select the resulting run and re-apply the grade — mixed-value groups are creatable from the UI both before and after this change, since `canToggleTuplet` performs no duration check and the new validator accepts them by construction. What is missing is preservation *through* the edit. Tracked as **#702**.
- **Ratio reduction.** 6:4 is not reduced to 3:2. A sextuplet of sixteenths and a triplet of eighths occupy the same time but carry different written values, so the printed number is informative.
- **Boundary crossing as a load rule.** Barlines, repeats, and breath marks constrain _creation_ only; loaded tuplets spanning them are left alone.
- **Replacing `Tuplet.REMOVE`** with direct tuplet selection plus delete. Planned, not part of this change.
- **The compound-beat sixteenth span gap.** Tracked as **#703**.

* * *

## Current State

### Model

`dom/Tuplet.java` extends `RangeElement` and stores exactly one tuplet-specific field beyond the bracket offset:

- `grade` (`:75`) — the printed number. No `M`, no `V`.
- `verticalPositionSs` (`:76`) — user Y-offset.
- `createCopy` (`:91`) copies `grade` and `verticalPositionSs`.
- `getElementCount()` (`:98`) overrides `RangeElement.getElementCount()` (`:94`, documented "the number of elements in this range") to return `grade` instead. It has **no production callers** — only `TupletTest:99` and `:108` pin the behavior.

`StaffElement` has no tuplet field; membership is derived by scanning `Line.rangeElements` (`findTupletAt`, `dom/Line.java:1708`).

Durations: `ElementType` carries `defaultDuration` in PPQ ticks (`PPQ = 96`, `midi/MidiSequenceBuilder.java:48`); `StaffElement.getDefaultDurationWithDots()` (`:707`) applies `DOTTED_DURATION = {1.0f, 1.5f, 1.75f}`; `getDuration()` (`:711`) additionally multiplies by 1.5 for a fermata. The shortest value is a 32nd (12 ticks).

### Creation

`ui/action/TupletAction.java:41-59` offers fixed grades: `REMOVE(0)`, `DUPLET(2)`, `TRIPLET(3)`, `QUADRUPLET(4)`, `QUINTUPLET(5)`, `SEXTUPLET(6)`, `SEPTUPLET(7)`. None carries a keyboard accelerator.

**The per-selection wiring already exists.** `TupletAction` subscribes to `MusicSelectionDidChangeNotification`, `SongDidChangeNotification` and `DocumentDidLoadNotification` (`:121-152`) and sets its own enabled state in `handleChange` (`:154-183`). `ScoreViewController.warmTupletCache` (`:140`) runs at `TUPLET_INFO_CACHE_PRIORITY` ahead of those handlers for all three notifications (`:289`, `:294`, `:338`), so `canToggleTuplet()` executes **once** per dispatch, not once per action.

Both surfaces consume the same shared singletons in `Actions.TOGGLE_TUPLET_ACTIONS` (`:286`): `NotationMenu.createTupletMenu` (`:140-150`) builds `new JMenuItem(action)`, and `TupletPopupButton` (`:35-38`) builds its popup from the same list, disabling itself when `anyMatch(UIAction::isEnabled)` is false (`:57`). A property set on the action therefore reaches both surfaces with no rebuild API.

`handleChange:178-182` currently **disables** the action matching the existing tuplet's grade ("clicking it would be a no-op").

Dispatch: `TupletAction.performAction` → `ToggleTupletCommand` → `ScoreViewController.handleToggleTuplet` (`:225`) → `MusicEditOperations.toggleTuplet` (`:180-240`).

`ui/selection/LineSelectionState.canToggleTuplet()` (`:636-672`) returns `TupletToggleInfo(canToggle, existing, coversExisting)`. It requires at least two non-grace elements and rejects any non-grace element that is not `isPitchedNote()` (`:657`), so **rests cannot be in a newly created tuplet today**. Grace notes are skipped (`:653`). **No duration or ratio check is performed at all** — any run of pitched notes can be grouped as any grade.

The loop already rejects any selection whose elements do not all share the same tuplet, so a selection extending beyond a tuplet is disabled today. A selection that is a strict *portion* of a tuplet is not.

### Load

- **MusicXML.** `MusicXmlNoteReader.java:212-217` reads `<actual-notes>` into the grade; `:219-225` reads `<normal-notes>` and **discards it**. There is no `normal-type` constant in `MusicXmlTags.java`. `RangeSpanResolver.resolveTuplet` (`:194-224`) builds the `Tuplet` per line, mid-parse. `<tuplet type="start"/>`/`"stop"` are read at `:257`.
- **Legacy** `.mssw`**.** `LineIO.parseTupletData` (`:404-433`) reads `anchorIndex,endIndex[,grade[,verticalPositionSs]]`, defaulting grade to 3; `createTupletsFromPending` (`:439-461`) builds tuplets at end-of-line.
- Both run before the song is complete, so **neither can resolve a beat** that depends on tempo changes in earlier or later lines.
- Load runs under `Song.withoutMutationTracking`, which records nothing and does not set the modified flag.
- There are two independent entry points: `SongLoader.load` (`io/SongLoader.java:50`, documented as the **headless** path used for MIDI export) and `MusicXmlReader.read` (`:280-301`).

### Writer

`MusicXmlNoteWriter.writeTimeModification` (`:227`, called at `:119`) emits `<actual-notes>` = grade and `<normal-notes>` = `largestPowerOfTwoBelowGrade(grade)` (`:243`): 2→1, 3→2, 4→2, 5→4, 6→4, 7→4. So every file on disk asserts a ratio the reader ignores and the model does not hold, and a duplet is written as 2:1 — which cannot express the compound duplet 2:3.

`<tuplet type="start"/>` / `"stop"` are written by `MusicXmlNotationsWriter.java:118,122` via `writeNumberedMarker`, carrying the vertical position. The span markup round-trips correctly today.

`<duration>` (`MusicXmlNoteWriter.java:83`) is written as the **written** duration, unscaled by the tuplet. MusicXML requires the performed duration there.

`NoteTypeMapping.DIVISIONS = 480` (`:80`) is a compile-time constant, aliased by `MusicXmlUnits.DIVISIONS` (`:39`), used to build a private static ticks table (`:86-91`), documented in three comment blocks (`:51-66`, `:73-79`, `:227-241`) and an exception message (`:261`), and emitted at `MusicXmlMeasureWriter.java:77`. `MusicXmlMeasureWriter.java:84-86` writes `<senza-misura/>` unconditionally.

### Playback

`midi/LineTrackBuilder.getTupletFactor(int, Tempo)` (`:63`) **ignores** `grade` **entirely**. It sums each spanned element's `getDuration()`, divides by the tempo's reference note, snaps to a power of two, and returns `newDuration / tupletDuration`. `getElementDurationWithTuplet` (`:52`) rounds each element independently and `:306` accumulates, so a 7:4 of sixteenths at PPQ 96 produces 7 × round(13.71) = 98 ticks instead of 96 and everything after the tuplet shifts.

### Reporting

`io/SongLoadResult.java` — `Success(Song, DocumentFonts, @Nullable LoadWarning warning, boolean accidentalsConverted)`. `io/LoadWarning.java` is `record(Type type, String description)` with a `Type` enum of exactly one member, `INVALID_LYRICS_DATE`. It is a **single nullable slot**, so it cannot carry more than one problem, and `MusicXmlReader` never populates it. There are four construction sites: `SongLoader.java:62`, `SongLoadResult.java:40`, `MusicXmlReader.java:285`, `ScoreViewTest.java:932`.

`accidentalsConverted` is the existing silent-migration precedent: `ScoreView.java:452-458` re-marks the song modified **after** installing it, with the comment noting that `setSong` clears the modified flag internally. Any flag set during the load pass is therefore wiped unless it rides out on `Success`.

### Beat model

- `dom/Tempo.java` — `visibleTempo`, `tempoType` (a `Duration`), `tempoDescription`, `showTempo`. `tempoType` is the note value the BPM refers to.
- `dom/Duration.java` — `SEMI_BREVE, MINIM_DOTTED, MINIM, CROTCHET_DOTTED, CROTCHET, QUAVER_DOTTED, QUAVER`. The dotted members really are dotted (`setDotCount(1)` in the static initializer), so this is the simple/compound signal.
- `dom/TempoChangeAttachment.java` — carries a whole `Tempo`, so it can change BPM without changing `tempoType`.
- `dom/BeatChangeAttachment.java` / `dom/BeatChange.java:22` — `record BeatChange(Duration duration, Duration beat)`, a metric-modulation marking. **Display-only today**; nothing reads it.
- **`Song.getTempoAt(lineIndex, noteIndex)` (`dom/Song.java:452`) already performs the backward, cross-line walk**, with the `lastLine` partial-scan handling at `:456-463`, falling back to `getEffectiveTempo()` (`:439`). Its only production caller is `MidiSequenceBuilder.java:90`; `SongDefaultsTest` touches it.

Beat-defining state is written in at least these places:

```
Song.setTempo                        dom/Song.java:743
Song tempo update (setTempoType)     dom/Song.java:1576
Song.clearTempoIfOrphaned            dom/Song.java:758
TempoChangeAttachment.setTempo       dom/TempoChangeAttachment.java:59
BeatChangeDialog add                 ui/dialog/BeatChangeDialog.java:104
initial-tempo attach                 dom/Line.java:1346
paste (copyForOwner)                 dom/TempoChangeAttachment.java:52
                                     dom/BeatChangeAttachment.java:65
line split displaces a tempo
  attachment to another element      dom/Line.java:284
```

* * *

## Design

### 1. Model — `dom/Tuplet.java`

```java
private int grade;               // N, existing
private int normalNotes;         // M
private ElementType noteValue;   // V's base type
private int noteValueDots;       // V's dot count
```

`V` is stored as a base `ElementType` plus a dot count rather than as a tick duration. This makes constraint 2 true by construction, and it is what `<normal-type>` plus `<normal-dot/>` needs on the way out — a tick duration would have to be decomposed at write time anyway.

`createCopy` (`:91`) must copy all four fields — it currently copies only `grade` and `verticalPositionSs`, and undo/redo round-trips through it.

The constructor takes `N`, `M`, and `V`. There is no setter-only path to a half-built tuplet.

**Delete the `getElementCount()` override** (`:98`) and its two tests (`TupletTest:99`, `:108`). It contradicts the base contract, has no production callers, and becomes a trap once `getNormalNotes()` sits beside it.

`Tuplet.toIndexString()` (`:263`) needs **no** new fields. It is reached via `LineIO.rangeElementsToString` (`:171`) from `LineIO`'s write at `:126`, called only by `SongIO.writeSong`, whose sole production caller is `uiconverter/ConvertAction.java:161` — the batch UIConverter, which round-trips `.mssw` → `.mssw` before converting. The post-load pass re-derives `M` and `V` for every `.mssw` file regardless.

### 2. Beat resolution — extend `Song.getTempoAt`

Do **not** write a second backward walker. Widen the existing one into:

```java
record BeatAt(Duration beat, int lineIndex, int elementIndex) {}

BeatAt resolveBeatAt(int lineIndex, int elementIndex)
```

- Include `BeatChangeAttachment.beatChange().beat()` alongside `TempoChangeAttachment.tempo().tempoType()` in the scan.
- **Precedence is positional, not by type.** The nearest preceding beat-defining event wins regardless of which kind it is. Type precedence would contradict the barrier definition below, which presumes a running "in effect" value.
- Fall back to `Song`'s own `Tempo.tempoType()`, and to a quarter note when the song has no tempo.
- Return the defining position so callers can identify barriers.

`getTempoAt` becomes a thin wrapper or keeps its signature; `MidiSequenceBuilder.java:90` must keep working unchanged.

```
resolveBeatAt(line 3, element 5)

  line 0   [e0 e1 e2 ... eN]   <- scanned in full, backward
  line 1   [e0 e1 e2 ... eN]   <- scanned in full, backward
  line 2   [e0 e1 e2 ... eN]   <- scanned in full, backward
  line 3   [e0 e1 e2 e3 e4 e5] <- scanned backward from e5 only
                            ^
                            anchor

  first hit wins, whichever kind it is:
      BeatChangeAttachment  -> beatChange().beat()
      TempoChangeAttachment -> tempo().tempoType()
  no hit -> song tempo -> quarter note
```

**A beat barrier** is an element carrying:

- a `BeatChangeAttachment`, or
- a `TempoChangeAttachment` whose beat value **differs** from the one in effect at that point.

A tempo change that alters only BPM is not a barrier. The anchor element is exempt — a barrier there defines the tuplet's beat rather than splitting it. Anywhere from the second element through the last is a crossing.

Cost is O(elements before the anchor), with no cache. `attachInitialTempoIfNeeded` guarantees a hit at line 0 element 0, so the walk always terminates there at worst. **Record this bound in the method's Javadoc**; a maintained index on `Song` would trade microseconds for an invalidation invariant on every structural mutation, and a stale index produces exactly the silent wrong-beat failure this change exists to prevent.

### 3. Validator — shared by five call sites

A model-level unit in `dom/` (no UI or IO dependencies). It needs the song (for beat resolution), the line, the element range, and a candidate `N`. It computes `S` from `getDefaultDurationWithDots()` over non-grace elements, derives `V = S / N`, checks notatability, derives `M` from the convention, applies the power-of-two redundancy test, and applies constraints 1–7 according to the strictness flag.

Its Javadoc records three decisions for a future reader:

- **ABC import derives `M` rather than trusting the file's stated ratio** — see [§10](#10-abc-import). That is where a future importer will look.
- **Why `N / M` being a power of two is redundancy** — the group is exactly expressible at the next finer written value with no bracket.
- **The O(document) cost of beat resolution**, and why there is no cache.

**Bulk validation is a forward walk.** For the load pass ([§6](#6-load)) and beat-edit revalidation ([§5](#5-beat-edits-invalidate-tuplets)), do not call `resolveBeatAt` once per tuplet — that is quadratic and re-derives per tuplet what a single pass knows continuously. Walk forward carrying the running beat; barriers fall out of the walk for free.

```
forward pass, carrying the running beat

  element ->  is it beat-defining?  --yes-->  update running beat
                     |                        record a barrier here
                     no
                     |
                     v
              inside a tuplet span?  --yes-->  accumulate S,
                     |                         note fermatas/barriers
                     no
                     |
                     v
              span ends?  --yes-->  V = S/N, derive M, apply constraints
                                    -> verdict for that tuplet
```

### 4. Creation

`TupletToggleInfo` gains the set of grades valid for the current selection:

```java
record TupletToggleInfo(
    boolean canToggle,
    Set<Integer> validGrades,
    @Nullable Tuplet existing,
    boolean coversExisting
)
```

- `canToggleTuplet` (`:657`) stops requiring `isPitchedNote()` — rests are allowed and contribute to `S`; grace notes stay skipped and contribute nothing.
- **Partial coverage is disabled.** `existing != null && !coversExisting` returns `canToggle = false`. The extends-beyond case is already rejected by the existing `currentTuplet != firstTuplet` check.
- Validity is computed once per selection change for all six candidate `N` values. **`S` and the resolved beat are each computed once and reused** across the six — the beat walk is the only unbounded operation here, and `warmTupletCache` runs on every document edit, not only on selection changes.

**Menu items are filtered by enabled state, not rebuilt.** Both surfaces already consume the shared `TupletAction` singletons, so this is one added condition in `handleChange` (`ui/action/TupletAction.java:154-183`):

```java
} else if (existing == null) {
    setEnabled(info.validGrades().contains(tuplet.getSize()));
```

No rebuild API on `PopupButton`, no subscription in `NotationMenu`, no new caching.

**The existing grade is shown checked.** Replace the `handleChange:178-182` disable-the-current-grade logic with `Action.SELECTED_KEY`, and make the grade items `JRadioButtonMenuItem`s in both surfaces with a none-selected state. Availability (`setEnabled`) and selection (`SELECTED_KEY`) are separate properties on the same singletons, so both surfaces still get both for free. Re-picking the checked grade does nothing.

The existing tuplet's grade is always shown and checked even when it is not currently creatable — otherwise a selection that visibly *is* a tuplet would show nothing checked. When no grade is valid and no tuplet exists, every grade action is disabled and `TupletPopupButton:57` disables the whole control on its own.

**The toolbar button's direct-click action is context-dependent:** Remove when the selection is exactly an existing tuplet, otherwise the first valid grade. Press-and-hold shows the popup. This is visually free — every `TupletAction` is constructed with the same icon (`"@"`, size 18) and `configureButtonFromAction` pins a fixed tooltip, so the button face does not change when the default action does.

`Remove` stays enabled whenever a tuplet exists.

### 5. Beat edits invalidate tuplets

Because a barrier is defined *relative* to the beat in effect, editing the song's `Tempo.tempoType` or an earlier tempo change can turn an attachment already sitting inside a tuplet's span into a barrier, without anything being inserted near that tuplet. Splitting a line can displace a tempo attachment onto an element inside a span (`dom/Line.java:284`) — moving a barrier without editing it. So the check cannot live in the add-attachment path, and it cannot be a list of call sites maintained by hand.

**Rule:** every beat-defining write funnels through one `Song` bracket helper. It opens the bracket, runs the edit, re-validates tuplets forward from that point, and emits removals as companions.

- A `SongDidChangeNotification` subscriber **cannot** implement this: per [mutations.md](../.agents/guides/mutations.md), the notification fires after the outermost bracket closes, so removals would land in a second undo step. The work must happen inside the bracket.
- Companion removals are emitted **before** the primary mutation, per the companion-ordering rule (`mutations.md:68-72`), so reverse-order undo restores the primary first.
- This follows the `Line.addTuplet` → `removeOverlappingTuplets` precedent (`dom/Line.java:1879`), and inherits its replay suppression: `mutations.md:138` confirms tuplet auto-removal is already suppressed under `withReplay` at the helper level.
- The `Song` paths already funnel through `withModification(() -> applyChange(...))` (`dom/Song.java:1572`), so the shape fits what is there. Make the underlying setters private or package-private so bypassing the helper is not possible.
- When one or more tuplets are removed, show a warning **once per edit**, not once per tuplet:

```properties
alert.title.tuplets.removed = Tuplets Removed
alert.tuplets.removed = The change to the beat caused one or more tuplets to no longer be valid musically, so they were removed.
```

```java
OptionDialogs.showWarningMessage(
    mainFrame,
    Strings.ALERT_TITLE_TUPLETS_REMOVED,
    Strings.ALERT_TUPLETS_REMOVED
);
```

Keys go in the `alert` group in sorted position. The wording avoids pluralization, so this is a plain `Strings.get(key)` — no MessageFormat pattern, and no apostrophe trap. `OptionDialogs` auto-suppresses in headless and test contexts.

### 6. Load

A **post-load pass** over the complete song, after all lines exist. It cannot live in `RangeSpanResolver.resolveTuplet` or `LineIO.createTupletsFromPending`, both of which run mid-parse.

**Both readers own it.** A static entry point in `dom/`, called from `SongLoader.load` (`io/SongLoader.java:62`) and `MusicXmlReader.read` (`:285`) immediately before constructing `Success`. Placing it in `ScoreView.openFile` instead would leave the headless MIDI-export route (`SongLoader` is documented for exactly that) playing tuplets the UI had dropped — two different MIDI files from one document, with no error on either.

Structured as the forward walk from [§3](#3-validator--shared-by-five-call-sites). For each tuplet:

- **`<normal-type>` present** — the file was written by the new writer, or by another application that emits a complete `<time-modification>`. Trust its `M`. Validate constraints 1, 2 and 4 only.
- **`<normal-type>` absent** — the file predates this change, or is legacy `.mssw`. Derive `M` from the convention using the running beat.
- Validate. **Invalid tuplets are always dropped, never repaired** — the notes survive, only the bracket and number go.
- Count the drops and the migrations separately, and return both.

The pass runs under suspension so it emits no mutations — running it outside suspension would put tuplet removals into the undo history, making a freshly opened song undoable back to its invalid state.

**The modified flag cannot be set inside the pass.** `ScoreView.setSong` clears it internally, which is why `accidentalsConverted` re-marks the song *after* installing it (`ScoreView.java:452-458`). The migration counts must ride out on `Success` and be applied on the same footing.

Because the reader discards `<normal-notes>` today, **no existing file carries an `M`**. Every song containing a tuplet is migrated on first load and marked modified, not just the legacy `.mssw` ones.

### 7. Reporting

Widen the carrier so more than one problem can be reported and so MusicXML has a path at all:

```java
record Success(Song song, DocumentFonts fonts,
               List<LoadWarning> warnings,   // was @Nullable LoadWarning
               boolean accidentalsConverted,
               int tupletsDropped,
               int tupletsMigrated)
```

`MusicXmlReader.read` starts populating it (currently hardcodes `null`, `:280-301`). All four `Success` construction sites and the existing `INVALID_LYRICS_DATE` path are touched.

**One migration report covers every migration**, including the retired-accidental conversion that is silent today. A single dialog with a header and one bullet per migration type, replacing the bare dirty flag at `ScoreView.java:452-458`:

```properties
alert.song.migrated = The following modifications were made during migration of this song:\n{0}
alert.song.migrated.accidentals = - Retired accidentals were converted.
alert.song.migrated.tuplets.dropped = - {0,choice,1#One tuplet was removed|1<{0} tuplets were removed} as musically invalid.
alert.song.migrated.tuplets.updated = - {0,choice,1#One tuplet was updated|1<{0} tuplets were updated} to record the printed ratio.
alert.title.song.migrated = Song Updated
```

Assemble the applicable bullets, join with `\n`, and pass the result as `{0}` to `alert.song.migrated`. Each bullet key with a count is read through the varargs `Strings.get(key, count)`; the wrapper likewise. Keys go in the `alert` group in sorted position. No apostrophes appear in any pattern, so the MessageFormat apostrophe trap does not arise. Surfaced through `OptionDialogs.showWarningMessage`.

Add a `LoadWarning.Type` member for dropped tuplets so the condition is also available as structured data to non-UI callers.

### 8. Playback

`LineTrackBuilder.getTupletFactor` becomes `M / N` from the stored ratio. The power-of-two snapping, the `Tempo` parameter, and the beat dependency all go.

Per-element rounding (`:52`, accumulated at `:306`) is replaced by rounding **absolute positions within the tuplet**:

```
endOffset = round(cumulativeWrittenTicks * M / N)
duration  = endOffset - previousEndOffset
```

No rational-arithmetic class is needed — the only denominator is `N`, so long arithmetic suffices. The group closes exactly, because `S * M / N = M * V` is an integer by construction. With constraint 4 excluding fermatas from tuplets, nothing perturbs this.

This is the payoff for storing `M`: what is printed is what is heard, and a tempo edit can no longer re-time an existing tuplet.

### 9. MusicXML

- Add `NORMAL_TYPE` and `NORMAL_DOT` to `MusicXmlTags.java`; write `<normal-type>` and, for a dotted `V`, `<normal-dot/>`. Three corpus tuplets have a dotted `V` and cannot round-trip without it.
- Stop discarding `<normal-notes>` (`MusicXmlNoteReader.java:219-225`). `<normal-type>` presence is the trust discriminator — see [§6](#6-load).
- `writeTimeModification` takes the `Tuplet` and emits the stored `M`. `largestPowerOfTwoBelowGrade` is deleted.
- `<duration>` (`:83`) becomes the **performed** duration.
- **`NoteTypeMapping.DIVISIONS` becomes 13440.** It stays a compile-time constant; there is no per-song prescan.

`DIVISIONS` is ticks per quarter note. Every written value is `base × dotFactor`, and every base is an integer multiple of the 32nd, so it suffices that the 32nd works. With `u = DIVISIONS / 8`, the performed duration `u × f × M/N` must be integral for `f ∈ {1, 3/2, 7/4}` and `N ∈ {2..7}`. `M` shares no guaranteed factor with `N`, so require `u × f` divisible by `lcm(2..7) = 420` in each case:

```
f = 1      u        divisible by 420   ->  u divisible by 420   (2^2·3·5·7)
f = 3/2    3u/2     divisible by 420   ->  u divisible by 280   (2^3·5·7)
f = 7/4    7u/4     divisible by 420   ->  u divisible by 240   (2^4·3·5)

u = lcm(420, 280, 240) = 2^4·3·5·7 = 1680
DIVISIONS = 8u                        = 13440   (2^7·3·5·7)
```

Verification:

```
                    ticks    /2     /3     /4     /5     /6     /7
32nd                 1680    840    560    420    336    280    240
dotted 32nd          2520   1260    840    630    504    420    360
double-dotted 32nd   2940   1470    980    735    588    490    420
```

All exact; everything coarser is an integer multiple of 1680 and inherits this. A whole note is 53760 ticks, far inside `int` range.

Do **not** use 3360 (= 480 × 7): `u = 420`, so a double-dotted 32nd is 735, odd, and `735 × 3/2 = 1102.5` inside a duplet. The required factor depends on the **dot count** as well as the ratio, which is why inspecting only the ratios present cannot find it. Today's 480 already has this defect independently of septuplets.

This is exact for `N <= 7`, which constraint 1 now enforces.

Update the tick tables in the `NoteTypeMapping:51-66`, `:73-79`, `:227-241` comment blocks, the exception message at `:261`, and `MusicXmlUnits:35-39` — they spell out 480-derived values longhand and are stale the moment the constant changes. Writer-output test expectations churn; reader-input fixtures such as `MusicXmlReaderLenienceTest`'s `<divisions>480</divisions>` do **not**, because the reader reconstructs durations from `<type>`/`<dot>` and never consults `<divisions>`.

When a file's stored `V` disagrees with `S / N`, the file contradicts itself and the tuplet is dropped, consistent with "always drop, never repair".

### 10. ABC import

No importer exists yet; this records the decision for when one is written, and the validator's Javadoc carries it.

**Derive `M` from the convention. Do not trust the ratio stated in the file.** ABC's `q` is an optional hand-written field with a documented default that producers get wrong. Of 897 authored ratios in the corpus, 83 are wrong from writing the group size into the ratio slot; the convention is wrong in 4. Deriving fixes 83 and costs 4.

The failure mode is visible in the four `(4:2:2` markings at `2000/1811.abc:17`:

```
(4:2:2A{/A}c3 (4:2:2c!~(!c3!~)! (4:2:2AA3 (4:2:2GG3 EE2EDE F/G/F2-F3:|

each group = 8th + dotted quarter = 48 + 144 = 192t = four 8th-units
printed N = 4,  V = S/N = 48 = eighth,  beat = dotted quarter
file says q = 2   convention derives M = 3   ->  4:3, one compound beat
```

Each group holds two notes spanning four eighth-units, and `q = 2` is simply ABC's documented default for an even `p`, emitted mechanically. The convention's `M = 3` is the musically correct reading. (All four are on one line of one file, so this is one transcriber's habit, not four independent corroborations.)

This is deliberately the opposite of the MusicXML policy, and the distinction is principled: in MusicXML, `actual-notes`/`normal-notes` is a required machine-written pair; in ABC, `q` is optional, hand-written and demonstrably unreliable.

`N` still comes from the file — ABC's printed number is reliable, and `V = S / N` is notatable for 99.3% of the corpus.

### 11. Paste

`Fragment` (`ui/clipboard/Fragment.java:70`) captures `spans[]`, so tuplets travel through the clipboard. Pasting a tuplet into a different beat context can produce a tuplet that violates constraints 5–7 without any other call site firing — it would then survive, render and play until the next save-and-reopen, and vanish there with no explanation the user can connect to the paste.

Validate each pasted tuplet against the target context under STRICT, and drop the invalid ones inside the paste's own modification bracket, using the same companion pattern as [§5](#5-beat-edits-invalidate-tuplets). The notes survive; only the bracket goes.

The ASCII diagram at `ui/clipboard/Fragment.java:45` documents the capture/paste flow and must be updated in the same commit.

### 12. Legacy `.mssw`

Read-only per `AGENTS.md`, so `parseTupletData` keeps its existing format and `M`/`V` are derived in the post-load pass.

### 13. Undo and mutations

Per [mutations.md](../.agents/guides/mutations.md):

- `TupletAddition` / `TupletRemoval` carry the `Tuplet`; `createCopy` must copy the new fields or undo round-trips lose them.
- The post-load pass runs under suspension, so it emits no mutations.
- Beat-edit removals batch with the triggering edit (see [§5](#5-beat-edits-invalidate-tuplets)); paste removals batch with the paste (see [§11](#11-paste)).
- `withReplay` already suppresses tuplet auto-removal; the new validation must not fire during replay either.

* * *

## Phases

Each phase ends with `./scripts/compile.sh` and `./scripts/test.sh unit`, not counted in the task totals.

### Phase 1 — Model and beat resolution

1. Add `normalNotes`, `noteValue`, `noteValueDots` to `dom/Tuplet.java`; constructor takes `N`, `M`, `V`.
2. Update `createCopy` (`:91`) to copy all four fields.
3. Delete the `getElementCount()` override (`:98`) and its two tests (`TupletTest:99`, `:108`).
4. Extend `Song.getTempoAt` (`dom/Song.java:452`) into `resolveBeatAt(lineIndex, elementIndex)` returning beat plus defining position, including `BeatChangeAttachment`, with positional precedence.
5. Add the backward-walk ASCII diagram and the O(document) bound to the method's Javadoc.
6. Tests: a dedicated `resolveBeatAt` class — BeatChange hit, TempoChange hit, positional precedence with both present, cross-line walk, first-line partial scan, no-event fallback, no-song-tempo fallback, returned defining position — plus a regression case pinning `MidiSequenceBuilder.java:90`.

### Phase 2 — Validator core

BlockedBy: Phase 1

1. New validator in `dom/`, with a strictness flag.
2. Compute `S` from `getDefaultDurationWithDots()` over non-grace elements; rests count, grace notes do not; `S == 0` is invalid.
3. Derive `V = S / N`; reject a non-integer, a non-notatable value, and `N > 7`.
4. Implement conventional spans for `V` under `B`, and the `M` choice including the compound-duplet fallback.
5. Apply the power-of-two redundancy test to `N / M`.
6. Tests: `V` not notatable ("3" over 16th + 16th + eighth); mixed values accepted (quarter + quarter + eighth + eighth as `N = 3`; eighth + four sixteenths as `N = 3`); no span below `N` (`N = 2` simple, `N = 3` with `V` = eighth compound); power-of-two redundancy (`N = 4` simple, `N = 6` compound); compound duplet 2:3; sixteenth triplet 3:2 at half a beat; `S == 0`; `N = 8` from a file.

### Phase 3 — Validator constraints and strictness

BlockedBy: Phase 2

1. Constraint 1 (`N`, `M` positive, `N != M`) and constraint 4 (no fermata in the span), both strictnesses.
2. Constraints 5, 6 and 7 under STRICT only, including the anchor-element barrier exemption and the trailing-breath-mark exemption from commit `6bcc1c6b`.
3. Add the forward-walk bulk entry point carrying the running beat.
4. Validator Javadoc: the ABC derive-`M` decision, the power-of-two rationale, the beat-resolution cost bound.
5. Tests: fermata rejected; beat-barrier crossing rejected; BPM-only tempo change accepted; barline / repeat / breath crossing rejected; trailing breath accepted.
6. Tests: paired strict/lenient cases — for each of constraints 5, 6 and 7, one tuplet rejected under STRICT and the same one accepted under LENIENT.

### Phase 4 — Creation

BlockedBy: Phase 3

1. `TupletToggleInfo` gains `Set<Integer> validGrades`.
2. `LineSelectionState.canToggleTuplet` (`:657`) stops requiring `isPitchedNote()`; partial coverage returns `canToggle = false`.
3. Compute `S` and the resolved beat once, reuse across all six candidate grades.
4. `TupletAction.handleChange` (`:154-183`): enabled state gains `info.validGrades().contains(tuplet.getSize())`; replace the disable-the-current-grade branch with `Action.SELECTED_KEY`.
5. Make the grade items `JRadioButtonMenuItem`s with a none-selected state in `NotationMenu.createTupletMenu` (`:140`) and `TupletPopupButton` (`:35`); make the toolbar default action Remove-or-first-valid-grade.
6. Tests: rests permitted in a new tuplet; partial coverage disabled; `validGrades` contents per selection; existing grade reported as selected.

### Phase 5 — Beat-edit chokepoint

BlockedBy: Phase 3

1. Add the `Song` bracket helper that runs a beat-defining edit and re-validates forward inside the same bracket.
2. Route every write in the Current State list through it; make the underlying setters private or package-private.
3. Emit removals as companions before the primary mutation, per `mutations.md:68-72`.
4. Add `alert.title.tuplets.removed` and `alert.tuplets.removed` in sorted position; show once per edit via `OptionDialogs.showWarningMessage`.
5. Tests: inserting a `BeatChangeAttachment` inside a tuplet removes it; one undo restores both; a barrier on the anchor element does not remove it; editing the song tempo so an in-span tempo change becomes a barrier removes it; a line split that displaces a tempo attachment into a span removes it.
6. Tests: no removals means no alert; the alert fires once per edit, not once per tuplet; nothing fires under `withReplay`.

### Phase 6 — Load pass

BlockedBy: Phase 3

1. Static post-load entry point in `dom/`, structured as the forward walk, returning drop and migration counts.
2. Call it from `SongLoader.load` (`:62`) and `MusicXmlReader.read` (`:285`) immediately before constructing `Success`.
3. Trust the file's `M` when `<normal-type>` is present (constraints 1, 2, 4 only); derive it otherwise.
4. Drop a tuplet whose stored `V` disagrees with `S / N`.
5. Run under suspension; do not set the modified flag inside the pass.
6. Tests: reader parity — one fixture with an invalid tuplet loaded through both readers, asserting identical tuplet sets and drop counts; `<normal-type>` present trusts `M`; absent derives it; stored `V` disagreeing drops.

### Phase 7 — Reporting

BlockedBy: Phase 6

1. Widen `SongLoadResult.Success` to `List<LoadWarning> warnings` plus `tupletsDropped` and `tupletsMigrated`; update all four construction sites.
2. Add the dropped-tuplet `LoadWarning.Type` member.
3. Add the five `alert.song.migrated*` keys in sorted position.
4. Assemble the bullets and surface the report in `ScoreView` (`:452-458`), absorbing the existing `accidentalsConverted` path into it.
5. Re-mark the song modified after `setSong`, not inside the pass.
6. Tests: migration marks the song modified; a song with drops reports them; a pure migration reports without a drop bullet; the accidentals bullet appears alongside the tuplet bullets.

### Phase 8 — Playback

BlockedBy: Phase 1

1. `LineTrackBuilder.getTupletFactor` (`:63`) returns `M / N`; delete the snapping, the `Tempo` parameter and the beat dependency.
2. Replace per-element rounding (`:52`, `:306`) with absolute-position rounding within the tuplet.
3. Tests: a 7:4 of sixteenths at PPQ 96 closes on exactly 96 ticks; the element after the tuplet starts where it should.
4. Tests: undo round-trip — create a tuplet with a non-default `M` and a dotted `V`, mutate, undo, redo, assert all four fields survive each transition; plus a direct `createCopy` field-completeness assertion in `TupletTest`.

### Phase 9 — MusicXML

BlockedBy: Phase 1

1. Add `NORMAL_TYPE` and `NORMAL_DOT` to `MusicXmlTags.java`.
2. `writeTimeModification` (`:227`) takes the `Tuplet` and emits the stored `M`, `<normal-type>`, and `<normal-dot/>` when `V` is dotted; delete `largestPowerOfTwoBelowGrade` (`:243`).
3. Stop discarding `<normal-notes>` (`MusicXmlNoteReader.java:219-225`).
4. `<duration>` (`:83`) becomes the performed duration.
5. `NoteTypeMapping.DIVISIONS = 13440`; update the comment tables at `:51-66`, `:73-79`, `:227-241`, the exception message at `:261`, and `MusicXmlUnits:35-39`.
6. Tests: all three fields survive write/read including a dotted `V` via `<normal-dot/>`; a septuplet survives exactly; a file without `<normal-type>` has `M` derived; a table-driven `NoteTypeMappingTest` case over every `ElementType` × {0, 1, 2 dots} × `N` ∈ {2..7} asserting an integral performed duration.

### Phase 10 — Paste

BlockedBy: Phase 4, Phase 5

1. Validate each pasted tuplet against the target context under STRICT.
2. Drop the invalid ones inside the paste's modification bracket, as companions.
3. Update the ASCII diagram at `ui/clipboard/Fragment.java:45`.
4. Tests: copy a triplet from a quarter-beat passage, paste under a dotted-quarter beat, assert the bracket is dropped and the notes survive; a paste into a compatible context keeps the bracket.

* * *

## Existing tests to update

`TupletTest`, `ToggleTupletCommandTest`, `LineTrackBuilderTest`, `MutationRecordsTest`, `MutationReplayerRoundTripTest`, `MusicXmlSpanRoundTripTest`, `MusicXmlWriterOutputTest`, `NoteTypeMappingTest`, `LineIOTest`, `PasteSpanReconciliationTest`, `FragmentTest`, `SongDefaultsTest`.
