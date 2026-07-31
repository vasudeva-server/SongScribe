# Don't Allow Invalid Tuplets
**Issue:** vasudeva-server/SongScribe#604  
**Spec:** [../specs/604-invalid-tuplets.md](../specs/604-invalid-tuplets.md)  
**Status:** In Progress  
**Created:** 2026-07-30

* * *
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1 | [User-Facing Strings](#-phase-1-user-facing-strings) | ✅ Complete | —   |
| 2 | [Beat Resolution](#-phase-2-beat-resolution) | ✅ Complete | —   |
| 3 | [MusicXML Divisions](#-phase-3-musicxml-divisions) | ✅ Complete | —   |
| 4 | [Validator Core](#-phase-4-validator-core) | ✅ Complete | —   |
| 5 | [Validator Constraints and Strictness](#-phase-5-validator-constraints-and-strictness) | ✅ Complete | —   |
| 6 | [Tuplet Model and Construction Sites](#-phase-6-tuplet-model-and-construction-sites) | ✅ Complete | —   |
| 7 | [Playback](#-phase-7-playback) | ✅ Complete | —   |
| 8 | [MusicXML Time Modification](#-phase-8-musicxml-time-modification) | ✅ Complete | —   |
| 9 | [Load Pass](#-phase-9-load-pass) | ✅ Complete | —   |
| 10 | [Reporting](#-phase-10-reporting) | ✅ Complete | —   |
| 11 | [Creation UI](#-phase-11-creation-ui) | ✅ Complete | —   |
| 12 | [Beat Edit Chokepoint](#-phase-12-beat-edit-chokepoint) | ✅ Complete | —   |
| 13 | [Paste](#-phase-13-paste) | ✅ Complete | —   |
| 14  | [Manual UI Verification](#-phase-14-manual-ui-verification) | ⏳ Pending | —   |
| 15  | [UI Tests](#-phase-15-ui-tests) | ⏸️ Blocked by 14 | —   |

* * *
## Shared Reference
**Every phase must read this section before starting.** It is the shared vocabulary, API contract and house-rule digest that the phases assume. Do not re-derive any of it.
### Terms
| Term | Meaning |
|------|---------|
| `N` | actual-notes — the printed tuplet number, called `grade` in existing code |
| `M` | normal-notes — the count of `V`-notes whose time the group occupies |
| `V` | the tuplet's written note value: an `ElementType` base plus a dot count |
| `B` | the beat duration in effect at the tuplet's anchor element |
| `S` | the summed written duration of the tuplet's non-grace elements, in PPQ ticks |

A tuplet means: `N` notes played in the time normally occupied by `M` notes of value `V`.

`N` is given from outside (menu grade when editing; the printed number when loading). `V` is derived: `V = S / N`. Never derive `V` as `gcd(durations)` — that renumbers ordinary mixed-value notation into sextuplets.
### Duration arithmetic in this codebase
- `ElementType` carries `defaultDuration` in PPQ ticks, `PPQ = 96` (`songscribe/dom/ElementType.java:43-48`). Quarter = 96, eighth = 48, 16th = 24, 32nd = 12, half = 192, whole = 384.
  
- `StaffElement.getDefaultDurationWithDots()` (`dom/StaffElement.java:707`) applies `DOTTED_DURATION = {1.0f, 1.5f, 1.75f}`. **The validator uses this**, never `getDuration()`, which inflates by 1.5 for a fermata.
  
- `Duration.getNote()` (`dom/Duration.java:46`) returns a **clone** of a `StaffElement`. `Duration.CROTCHET_DOTTED.getNote().getDefaultDurationWithDots()` is 144, and `.getDotCount()` is 1. This is how a phase gets `B` in ticks and learns whether the beat is compound.
  
- Notatable tick values (base × dot factor), with no collisions across the table:
  
  ```
  32nd   12  18  21      16th  24  36  42      eighth 48  72  84
  quarter 96 144 168     half 192 288 336      whole 384 576 672
  ```
  
### Constraints (spec §Validity Model)
1. `N` and `M` are positive integers, `N != M`, `N <= 7`.
  
2. `V = S / N` is an exact integer and is notatable (a base value × 1, 1.5 or 1.75). `S == 0` fails here.
  
3. Performed duration is `M * V`; each element scales by `M / N`.
  
4. No element in the span carries a `FermataAttachment`.
  
5. `M` is one of the conventional regular spans for `V` under `B`, and `N / M` is not a power of two.
  
6. No beat barrier strictly inside the span.
  
7. The span crosses no barline (`ElementType.isBarLine()`), repeat (`isRepeat()`), or `BREATH_MARK` — except a breath mark immediately following the last note, which selections already sweep in (commit `6bcc1c6b`).
  

Grace notes ride along and contribute no duration. Rests count toward `S` exactly as notes do.
### Conventional spans and choosing M
Spans a tuplet may conventionally replace, measured in `V`-units:

- binary divisions of `B` — of `B / 3` when `B` is dotted, since a dotted beat divides into three before subdividing binarily
  
- binary multiples of `B` — `B`, `2B`, `4B`, `8B`
  

expressed in `V`-units, keeping only those that divide exactly, and excluding `M <= 1`.

```
M = the largest conventional span strictly below N
    if none exists and B is dotted and N == 2:
        M = the smallest conventional span above N
    otherwise the tuplet is redundant

then: if N / M is a power of two, the tuplet is redundant
```

Expected results at a **quarter** beat, for `V` ∈ {quarter, eighth, 16th} — all three columns agree because the spans are `{2, 4, 8, 16, …}` in `V`-units:

| N   | M   |
| --- | --- |
| 2   | redundant |
| 3   | 2   |
| 4   | redundant (4/2 is a power of two) |
| 5   | 4   |
| 6   | 4   |
| 7   | 4   |

At a **dotted-quarter** beat the spans are `{3, 6, 12, …}` when `V` is the beat's third or coarser, and `{2, 6, 12, …}` when `V` is finer:

| N   | V = quarter | V = eighth | V = 16th |
| --- | --- | --- | --- |
| 2   | 3   | 3   | 6   |
| 3   | redundant | redundant | 2   |
| 4   | 3   | 3   | redundant |
| 5   | 3   | 3   | 2   |
| 6   | redundant | redundant | 2   |
| 7   | 6   | 6   | 6   |
### Strictness
One validator, one strictness flag. Edit and paste are `STRICT`; load and import are `LENIENT`.

|     | STRICT | LENIENT |
| --- | --- | --- |
| 1. `N`, `M` positive, `N != M`, `N <= 7` | ✓   | ✓   |
| 2. `V = S/N` notatable | ✓   | ✓   |
| 4. no fermata in the span | ✓   | ✓   |
| 5. `M` conventional, ratio re-times | ✓   | ✓   |
| 6. no beat barrier in the span | ✓   | —   |
| 7. no barline / repeat / breath | ✓   | —   |

Redundancy is a property of **derivation**, not of the tuplet. When `M` is derived and the number says nothing, the tuplet is dropped. When `M` is stated in a file and is otherwise valid, it is kept even if non-conventional.

Constraint 5 therefore does **not** relax under `LENIENT`, and the table above says so — a correction to an earlier draft that marked it strict-only. Reaching constraint 5 at all means `M` is being derived, and accepting a derived `M` that lands on no conventional span would not preserve information, it would invent a re-timing: three eighths under a dotted-quarter beat would load as 3:6 and play at half speed. A ratio the file *states* is protected by taking the `validateStated` path, which never consults the conventional spans at all. Constraints 6 and 7 remain the genuinely creation-only pair.
### API contract agreed across phases
Phases 4, 5, 6, 9, 11, 12 and 13 all depend on these exact shapes. Do not rename or re-shape them; if a phase finds one of them genuinely unworkable, adapt the implementation rather than the signature.

`src/main/java/songscribe/dom/TupletValidator.java` (created in Phase 4, extended in Phase 5):

```java
public final class TupletValidator {

    public enum Strictness { STRICT, LENIENT }

    public enum Reason {
        EMPTY_SPAN,             // S == 0
        NOT_NOTATABLE,          // V = S/N is not an exact notatable value
        BAD_RATIO,              // N or M not a positive integer, N == M, or N > 7
        NO_CONVENTIONAL_SPAN,   // no conventional span below N (and no fallback)
        POWER_OF_TWO_RATIO,     // N / M is a power of two — renotation, not re-timing
        FERMATA,                // an element in the span carries a FermataAttachment
        BEAT_BARRIER,           // a beat barrier lies strictly inside the span
        STRUCTURAL_BOUNDARY     // barline, repeat, or interior breath mark
    }

    /**
     * The verdict plus the derived (or accepted) ratio. When {@code valid} is false,
     * {@code normalNotes} is 0 and {@code noteValue} is null.
     */
    public record Result(
        boolean valid,
        @Nullable Reason reason,
        int normalNotes,
        @Nullable ElementType noteValue,
        int noteValueDots
    ) {}

    /**
     * Everything about a span that does not depend on N, computed once so the six
     * candidate grades can be tested without repeating the O(document) beat walk.
     */
    public record SpanContext(
        int writtenTicks,               // S
        int beatTicks,                  // B, including its dot
        boolean beatDotted,
        boolean hasFermata,
        boolean crossesBeatBarrier,     // barrier strictly after the anchor element
        boolean crossesStructuralBoundary
    ) {}

    public static SpanContext describeSpan(
        Song song, Line line, int lineIndex, int beginIndex, int endIndex);

    public static Result validate(SpanContext context, int grade, Strictness strictness);

    /** Convenience: describeSpan + validate. */
    public static Result validateDerived(
        Song song, Line line, int lineIndex, int beginIndex, int endIndex,
        int grade, Strictness strictness);

    /**
     * Load path for a file that stated its own ratio. Applies constraints 1, 2 and 4
     * only, and accepts the stated M without the conventional-span or redundancy test.
     * The tuplet is dropped when the stated V disagrees with S / N.
     */
    public static Result validateStated(
        Line line, int beginIndex, int endIndex,
        int grade, int normalNotes, ElementType noteValue, int noteValueDots);

    /** Bulk forward walk — see Phase 5 task 3. */
    public record Verdict(int lineIndex, Tuplet tuplet, Result result) {}

    public static List<Verdict> validateFrom(
        Song song, int fromLineIndex, int fromElementIndex, Strictness strictness);
}
```

`src/main/java/songscribe/dom/Song.java` (Phase 2):

```java
public record BeatAt(Duration beat, int lineIndex, int elementIndex) {}

public BeatAt resolveBeatAt(int lineIndex, int elementIndex);
```

`src/main/java/songscribe/dom/Tuplet.java` (Phase 6):

```java
public static final int UNRESOLVED_NORMAL_NOTES = 0;

public Tuplet(StaffElement anchorElement, StaffElement endElement,
              int grade, int normalNotes, ElementType noteValue, int noteValueDots);

/** M and V pending; only the dom-package load pass may complete it. */
public static Tuplet withUnresolvedRatio(
        StaffElement anchorElement, StaffElement endElement, int grade);

public int getNormalNotes();
public @Nullable ElementType getNoteValue();
public int getNoteValueDots();
public boolean isResolved();

/** Package-private — Phase 9's load pass is the only caller. */
void resolveRatio(int normalNotes, ElementType noteValue, int noteValueDots);
```

`src/main/java/songscribe/dom/TupletLoadPass.java` (Phase 9):

```java
public record Counts(int dropped, int migrated) {}

public static Counts run(Song song);
```
### Project rules every phase must follow
- **Exploration and refactoring:** use `serena` `jet_brains_*` tools for all Java work; read `.agents/rules/serena.md`. Use `jet_brains_find_referencing_symbols` — never `rg "foo("` — to find callers.
  
- **Compile:** `./scripts/compile.sh` exactly. Never `./gradlew`, `gradle`, `javac`.
  
- **Tests:** `./scripts/test.sh unit` (or `./scripts/test.sh ClassName`). Never `./gradlew test`. Compile first whenever anything under `src/main/` changed. Do not run e2e tests.
  
- **Never invoke the** `/run` **or** `/verify` **skill** in this project.
  
- **No magic numbers.** Every numeric literal other than `0`, `1`, `-1` and ×/÷ 2 must be a named constant conveying intent — in production code and tests alike.
  
- **Null handling:** never `Objects.requireNonNull` / `requireNonNullElse`; never `Optional`. Annotate nullable fields, parameters and returns `@Nullable`.
  
- **Javadoc:** never write a named constant's literal value in prose — use `{@value}` or `{@link}`. Exception: illustrating an example calculation.
  
- **Spelling:** American — "center", not "centre".
  
- **Comments** explain _why_, not _what_.
  
- **Strings:** all user-facing text lives in `src/main/resources/songscribe/strings.properties`; `Strings.java` is generated into `build/generated-sources/` by the build and must never be edited. Read `.agents/guides/strings.md` before touching strings.
  
- **Mutations/undo:** read `.agents/guides/mutations.md` before touching modification brackets, companions, replay, or suspension.
  
- **Alerts:** read `.agents/guides/option-dialogs.md` before adding a `JOptionPane` alert.
  
- Each phase ends by running `./scripts/compile.sh` and, unless the phase says otherwise, `./scripts/test.sh unit`, and must report SUCCESS / green.
  
### Explicitly out of scope for the whole plan
Do not implement, refactor, or "fix" any of these, even if they look adjacent:

- Malformed spans (dangling `<tuplet type="start">`, orphan stop, fewer than two non-rest notes). These keep throwing `SAXException`; `Tuplet.hasValidSpan` is untouched.
  
- Nested tuplets — already impossible via `Line.removeOverlappingTuplets`.
  
- Preserving a tuplet through a subdivision edit (issue #702).
  
- Ratio reduction — 6:4 is not reduced to 3:2.
  
- Boundary crossing as a load rule — constraints 6 and 7 are creation-only.
  
- Replacing `TupletAction.Tuplet.REMOVE` with tuplet selection plus delete.
  
- The compound-beat sixteenth span gap (issue #703).
  
- Writing an ABC importer. Phase 5 only records the decision in Javadoc.
  
- Regenerating `docs/abc-corpus-tuplet-outliers.md` (issue #704).
  

* * *
## ✅ Phase 1: User-Facing Strings
**Status:** Complete
**BlockedBy:** —  
**Files:** src/main/resources/songscribe/strings.properties  
**Recommended model/effort:** Haiku 4.5, low effort — seven property lines inserted in sorted position

Read `## Shared Reference` in this file first, and `.agents/guides/strings.md`.

This phase adds every user-facing string the plan needs, up front, so that later phases never contend over `strings.properties`. Phase 10 and Phase 12 consume these keys; they will not add any of their own.
### Tasks
1. Open `src/main/resources/songscribe/strings.properties` and locate the `alert` group (it currently runs from `alert.conversion.complete` through `alert.title.sound`). Keys inside a group are alphabetized; insert each new key in sorted position. The existing group is **not** perfectly sorted — do **not** reorder or reformat any existing line.
  
2. Add the migration-report keys, between `alert.number.out.of.range` and `alert.sound.init.failed`:
  
  ```properties
  alert.song.migrated = The following modifications were made during migration of this song:\n{0}
  alert.song.migrated.accidentals = - Retired accidentals were converted.
  alert.song.migrated.tuplets.dropped = - {0,choice,1#One tuplet was removed|1<{0} tuplets were removed} as musically invalid.
  alert.song.migrated.tuplets.updated = - {0,choice,1#One tuplet was updated|1<{0} tuplets were updated} to record the printed ratio.
  ```
  
3. Add the two title keys in sorted position within the run of `alert.title.*` keys:
  
  ```properties
  alert.title.song.migrated = Song Updated
  alert.title.tuplets.removed = Tuplets Removed
  ```
  
4. Add the beat-edit warning after the last `alert.title.*` key (`alert.title.sound`), since `tuplets` sorts after `title`:
  
  ```properties
  alert.tuplets.removed = The change to the beat caused one or more tuplets to no longer be valid musically, so they were removed.
  ```
  
5. Confirm no value contains an apostrophe (none of the above do), because a MessageFormat pattern would then need it doubled. Confirm every value uses literal Unicode characters, never `\uNNNN`.
  
6. Run `./scripts/compile.sh` and confirm SUCCESS — this regenerates `build/generated-sources/songscribe/Strings.java` with the constants `STRINGS.ALERT_SONG_MIGRATED`, `ALERT_SONG_MIGRATED_ACCIDENTALS`, `ALERT_SONG_MIGRATED_TUPLETS_DROPPED`, `ALERT_SONG_MIGRATED_TUPLETS_UPDATED`, `ALERT_TITLE_SONG_MIGRATED`, `ALERT_TITLE_TUPLETS_REMOVED`, `ALERT_TUPLETS_REMOVED`. Then run `./scripts/test.sh unit` and confirm green.
  

* * *
## ✅ Phase 2: Beat Resolution
**Status:** Complete
**BlockedBy:** —  
**Files:** src/main/java/songscribe/dom/Song.java, src/test/java/songscribe/dom/SongBeatResolutionTest.java  
**Recommended model/effort:** Opus 4.8, medium effort — widening a cross-line backward walk with a new precedence rule and a returned position

Read `## Shared Reference` in this file first.

The music is meterless — there is no time signature and no `Measure` object — so the beat is the only metric context available for deciding tuplet validity. This phase makes the beat resolvable at any position.
### Tasks
1. Read `Song.getTempoAt(int lineIndex, int noteIndex)` at `src/main/java/songscribe/dom/Song.java:452`. It already performs the backward, cross-line walk, including the `lastLine` partial-scan handling, falling back to `getEffectiveTempo()`. **Do not write a second backward walker** — widen this one.
  
2. Add a nested public record to `Song`:
  
  ```java
  public record BeatAt(Duration beat, int lineIndex, int elementIndex) {}
  ```
  
  `lineIndex`/`elementIndex` are the position of the event that defined the beat, so callers can identify beat barriers. When the beat comes from the song's own tempo or from the quarter-note default, both are `-1`.
  
3. Add `public BeatAt resolveBeatAt(int lineIndex, int elementIndex)` implementing the same backward walk, but hitting on **either** of:
  
  - `BeatChangeAttachment` → `beatChange().beat()` (`dom/BeatChangeAttachment.java`, `dom/BeatChange.java:22` — `record BeatChange(Duration duration, Duration beat)`)
    
  - `TempoChangeAttachment` → `tempo().tempoType()` (`dom/TempoChangeAttachment.java`)
    
  
  **Precedence is positional, not by type**: the nearest preceding beat-defining event wins regardless of which kind it is. If one element carries both, prefer the `BeatChangeAttachment`, since a metric-modulation marking is the more specific statement about the beat; document that tie-break in the method.
  
  Fall back to the song's own `Tempo.tempoType()`, and to `Duration.CROTCHET` when the song has no tempo at all. `getTempo()` is `@Nullable`; guard it and return early rather than using `Optional` or `Objects.requireNonNullElse`.
  
4. Keep `getTempoAt` working with its current signature and current behavior — its production caller `src/main/java/songscribe/midi/MidiSequenceBuilder.java:90` must not change. `getTempoAt` returns a whole `Tempo` (BPM included) and `resolveBeatAt` returns only a `Duration`, so they cannot be collapsed into one another; implement `resolveBeatAt` alongside it and share a private walk helper if that comes out cleanly, but do not force it.
  
5. Write the Javadoc for `resolveBeatAt`, including this ASCII diagram and the cost bound:
  
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
  
  State that the cost is O(elements before the anchor) with no cache, that `attachInitialTempoIfNeeded` guarantees a hit at line 0 element 0 so the walk always terminates, and that a maintained index on `Song` was rejected: it would trade microseconds for an invalidation invariant on every structural mutation, and a stale index produces exactly the silent wrong-beat failure this change exists to prevent.
  
6. Create `src/test/java/songscribe/dom/SongBeatResolutionTest.java`. Mirror the conventions of the existing `src/test/java/songscribe/dom/SongDefaultsTest.java` and read `.agents/guides/testing-unit.md` and `.agents/guides/testing-common.md` first. Cover: a `BeatChangeAttachment` hit; a `TempoChangeAttachment` hit; positional precedence with both kinds present at different positions (nearer wins, whichever kind it is); the cross-line backward walk; the first-line partial scan (an event _after_ the anchor on the anchor's own line is not seen); fallback to the song tempo when no event precedes; fallback to `Duration.CROTCHET` when the song has no tempo; and the returned `lineIndex`/`elementIndex` naming the defining event (and `-1`/`-1` for both fallbacks).
  
7. Add a regression case in the same class pinning that `getTempoAt` still returns the nearest preceding `TempoChangeAttachment`'s full `Tempo` (BPM included), so the `MidiSequenceBuilder` contract is guarded.
  
8. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 3: MusicXML Divisions
**Status:** Complete
**BlockedBy:** —  
**Files:** src/main/java/songscribe/io/musicxml/NoteTypeMapping.java, src/main/java/songscribe/io/musicxml/MusicXmlUnits.java, src/test/java/songscribe/io/musicxml/  
**Recommended model/effort:** Sonnet 4.6, medium effort — one constant change plus mechanical comment-table and test-expectation updates

Read `## Shared Reference` in this file first.
### Tasks
1. Change `NoteTypeMapping.DIVISIONS` from `480` to `13440` in `src/main/java/songscribe/io/musicxml/NoteTypeMapping.java`. It stays a compile-time `public static final int`; there is no per-song prescan.
  
  The reason, which belongs in the comment block that currently justifies 480: `DIVISIONS` is ticks per quarter note. Every written value is `base × dotFactor`, and every base is an integer multiple of the 32nd, so it suffices that the 32nd works. With `u = DIVISIONS / 8`, the performed duration `u × f × M/N` must be integral for `f ∈ {1, 3/2, 7/4}` and `N ∈ {2..7}`. `M` shares no guaranteed factor with `N`, so `u × f` must be divisible by `lcm(2..7) = 420` in each case:
  
  ```
  f = 1      u        divisible by 420   ->  u divisible by 420   (2^2·3·5·7)
  f = 3/2    3u/2     divisible by 420   ->  u divisible by 280   (2^3·5·7)
  f = 7/4    7u/4     divisible by 420   ->  u divisible by 240   (2^4·3·5)
  
  u = lcm(420, 280, 240) = 2^4·3·5·7 = 1680
  DIVISIONS = 8u                        = 13440   (2^7·3·5·7)
  ```
  
  Verification table to include:
  
  ```
                      ticks    /2     /3     /4     /5     /6     /7
  32nd                 1680    840    560    420    336    280    240
  dotted 32nd          2520   1260    840    630    504    420    360
  double-dotted 32nd   2940   1470    980    735    588    490    420
  ```
  
  Also record why 3360 (= 480 × 7) is **not** enough: `u = 420`, so a double-dotted 32nd is 735, odd, and `735 × 3/2 = 1102.5` inside a duplet. The required factor depends on the dot count as well as the ratio, which is why inspecting only the ratios present cannot find it — today's 480 already has this defect independently of septuplets. A whole note is 53760 ticks, far inside `int` range.
  
2. Update every place in `NoteTypeMapping.java` that spells out 480-derived values longhand: the class-level ASCII table (`ElementType → <type> token + base-tick factor`, currently annotated "DIVISIONS = 480" with 1920/960/480/240/120/60 and the "smallest fraction: double-dotted 32nd = 60 × 7/4 = 105" line), the `DIVISIONS` comment block above the constant, the trailing `// 1920` / `// 960` / … comments on `WHOLE_TICKS` … `THIRTY_SECOND_TICKS`, the `ticks(ElementType, int)` Javadoc (which quotes "60 × 7 / 4 = 105"), and the `ArithmeticException` message in `ticks`. Recompute each value for 13440: whole 53760, half 26880, quarter 13440, eighth 6720, 16th 3360, 32nd 1680, double-dotted 32nd 2940.
  
3. Update the stale `DIVISIONS` comment block in `src/main/java/songscribe/io/musicxml/MusicXmlUnits.java` (above `static final int DIVISIONS = NoteTypeMapping.DIVISIONS;`), which repeats the 480-based justification.
  
4. Extend `src/test/java/songscribe/io/musicxml/NoteTypeMappingTest.java` with a table-driven case covering every `ElementType` that has a tick mapping × dot counts {0, 1, 2} × `N ∈ {2..7}`, asserting that `ticks(type, dots) × M / N` is an exact integer for every conventional `M` that pairs with that `N`. Since `M` is not known to this class, assert the stronger, sufficient property instead: for every `M ∈ {1..7}` with `M != N`, `ticks(type, dots) * M % N == 0`. Use named constants for the bounds — no bare numeric literals.
  
5. Run `./scripts/compile.sh`, then `./scripts/test.sh unit`. Expect failures in MusicXML writer-output and corpus tests whose expectations spell out `<divisions>` and `<duration>` values (`MusicXmlWriterOutputTest`, and any of `MusicXmlNoteRoundTripTest`, `MusicXmlSpanRoundTripTest`, `MusicXmlDocumentRoundTripTest`, `MusicXmlCorpusLosslessnessTest`, `MusicXmlCorpusGenerator` that hard-code tick values). Update those expectations to the 13440-derived values.
  
  **Reader-input fixtures do not change.** `MusicXmlReaderLenienceTest`'s `<divisions>480</divisions>` and any other parsed-input fixture stay exactly as they are, because the reader reconstructs durations from `<type>`/`<dot>` and never consults `<divisions>`. If a reader test fails, the fix is in production code, not the fixture.
  
6. Re-run `./scripts/compile.sh` (SUCCESS) and `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 4: Validator Core
**Status:** Complete
**BlockedBy:** 2  
**Files:** src/main/java/songscribe/dom/TupletValidator.java, src/test/java/songscribe/dom/TupletValidatorCoreTest.java  
**Recommended model/effort:** Opus 4.8, high effort — the derivation rules are the conceptual heart of the change and every branch is a boundary decision

Read `## Shared Reference` in this file first — especially **Terms**, **Duration arithmetic in this codebase**, **Conventional spans and choosing M**, and the **API contract** block, which fixes the signatures this phase creates.

This phase builds the derivation half of the validator: `S`, `V`, the conventional spans, the choice of `M`, and the redundancy test. Phase 5 adds the remaining constraints and the strictness flag on top of the same class. Write the class so Phase 5 can add to it without restructuring: `validate(SpanContext, int, Strictness)` already takes the strictness flag in this phase even though only constraints 1 and 2 consult it.

This is a model-level unit in `songscribe.dom` with **no UI and no IO dependencies**.
### Tasks
1. Create `src/main/java/songscribe/dom/TupletValidator.java` with the `Strictness`, `Reason`, `Result` and `SpanContext` shapes exactly as given in the **API contract** block of `## Shared Reference`. Make the class `final` with a private constructor — it is a static utility, not a singleton (do not follow `.agents/guides/singletons.md`; nothing here holds state).
  
2. Implement `describeSpan(Song, Line, int lineIndex, int beginIndex, int endIndex)` for the fields this phase owns:
  
  - `writtenTicks` (`S`) — sum `StaffElement.getDefaultDurationWithDots()` over every element in `[beginIndex, endIndex]` whose `getType().isGraceNote()` is false. Rests count exactly as notes do. **Never** call `getDuration()`, which inflates by 1.5 for a fermata; a fermata is a performance instruction, not a change to the written rhythm.
    
  - `beatTicks` and `beatDotted` — from `song.resolveBeatAt(lineIndex, beginIndex).beat()`, then `beat.getNote().getDefaultDurationWithDots()` and `beat.getNote().getDotCount() == 1`. `Duration.getNote()` returns a clone, so this is safe.
    
  
  Leave `hasFermata`, `crossesBeatBarrier` and `crossesStructuralBoundary` as `false` in this phase; Phase 5 fills them in. Add a `// Phase 5 fills these` style comment only if it explains _why_ they are constant here — otherwise leave them plain.
  
3. Implement notatability. Build a private lookup from tick value to `(ElementType, dotCount)` covering every `ElementType` with a non-zero `defaultDuration` × dot counts 0, 1, 2. The table has no collisions (see the tick table in `## Shared Reference`). Derive `V` as `S / N`:
  
  - `S == 0` → invalid with `Reason.EMPTY_SPAN` (a span of only grace notes).
    
  - `S % N != 0` → invalid with `Reason.NOT_NOTATABLE`.
    
  - `S / N` not in the table → invalid with `Reason.NOT_NOTATABLE`.
    
4. Implement `Reason.BAD_RATIO`: `N <= 0`, `N > 7`, or (once `M` is known) `M <= 0` or `N == M`. `N > 7` is uncreatable in this editor and cannot be expressed exactly in MusicXML at the chosen `DIVISIONS`, so a file carrying one is rejected under both strictnesses.
  
5. Implement the conventional spans for `V` under `B`, exactly as specified in `## Shared Reference`:
  
  - binary divisions of `B` — or of `B / 3` when `B` is dotted, since a dotted beat divides into three before subdividing binarily
    
  - binary multiples of `B` — `B`, `2B`, `4B`, `8B`
    
  
  expressed in `V`-units, keeping only those that divide `V` exactly, and excluding `M <= 1`. Excluding `M = 1` is what makes a compound duplet infer 2:3 rather than the meaningless 2:1. Cap the multiples at `8B` and the divisions at the shortest representable value (32nd = 12 ticks) so the set is finite; use named constants.
  
6. Implement the choice of `M`:
  
  ```
  M = the largest conventional span strictly below N
      if none exists and B is dotted and N == 2:
          M = the smallest conventional span above N
      otherwise -> Reason.NO_CONVENTIONAL_SPAN
  
  then: if N / M is a power of two -> Reason.POWER_OF_TWO_RATIO
  ```
  
  The fallback is restricted to compound-beat duplets; that restriction is what produces the invalid diagonal in the expected-results tables. Note that `N / M` is only tested for power-of-two-ness when `N` is an exact multiple of `M`.
  
7. Wire `validate(SpanContext, int grade, Strictness)` and the convenience `validateDerived(...)`. On success return `Result(true, null, M, V, dots)`; on failure return `Result(false, reason, 0, null, 0)`. Also implement `validateStated(Line, int, int, int grade, int normalNotes, ElementType noteValue, int noteValueDots)` applying constraints 1 and 2 only — recompute `S` over the span, require `S / N` to equal the stated `V`'s tick value, and accept the stated `M` without any conventional-span or redundancy test. (Constraint 4, the fermata check, is added to `validateStated` by Phase 5.)
  
8. Create `src/test/java/songscribe/dom/TupletValidatorCoreTest.java`. Read `.agents/guides/testing-unit.md` and `.agents/guides/testing-common.md` first, and mirror the setup helpers in `src/test/java/songscribe/dom/LineTupletTest.java` for building a `Song`/`Line` with specific element types. Cover, with all numbers as named constants:
  
  - `V` not notatable: `N = 3` over 16th + 16th + eighth (`S = 96`, `96/3 = 32`, not a note value) → `NOT_NOTATABLE`.
    
  - Mixed values accepted: quarter + quarter + eighth + eighth as `N = 3` derives `V = quarter`, `M = 2`; eighth + four sixteenths as `N = 3` derives `V = eighth`, `M = 2`. These are the cases a `gcd`-based derivation would renumber to 6.
    
  - No conventional span below `N`: `N = 2` at a quarter beat; `N = 3` with `V = eighth` at a dotted-quarter beat → `NO_CONVENTIONAL_SPAN`.
    
  - Power-of-two redundancy: `N = 4` with `V = eighth` at a quarter beat; `N = 6` with `V = eighth` at a dotted-quarter beat → `POWER_OF_TWO_RATIO`.
    
  - Compound duplet: `N = 2`, `V = eighth`, dotted-quarter beat → `M = 3`.
    
  - Sixteenth triplet occupying half a beat under a quarter beat: `N = 3`, `V = 16th` → `M = 2`.
    
  - `S == 0` (a span of only grace notes) → `EMPTY_SPAN`.
    
  - `N = 8` arriving from a file → `BAD_RATIO` under both strictnesses.
    
  - A table-driven case reproducing both expected-results tables in `## Shared Reference` (quarter beat and dotted-quarter beat, `V` ∈ {quarter, eighth, 16th}, `N` ∈ {2..7}).
    
9. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 5: Validator Constraints and Strictness
**Status:** Complete
**BlockedBy:** 4  
**Files:** src/main/java/songscribe/dom/TupletValidator.java, src/test/java/songscribe/dom/TupletValidatorConstraintsTest.java  
**Recommended model/effort:** Opus 4.8, high effort — barrier definition, the anchor exemption and the strict/lenient split are all boundary decisions

Read `## Shared Reference` in this file first — especially **Constraints** and **Strictness**.

Phase 4 created `src/main/java/songscribe/dom/TupletValidator.java` with `Strictness`, `Reason`, `Result`, `SpanContext`, `describeSpan`, `validate`, `validateDerived` and `validateStated`, and implemented constraints 1 and 2 plus the derivation of `M` and `V`. `SpanContext.hasFermata`, `crossesBeatBarrier` and `crossesStructuralBoundary` are currently hard-coded `false`. This phase fills them in and enforces constraints 4–7.
### Tasks
1. Fill in `SpanContext.hasFermata` in `describeSpan`: true when any element in `[beginIndex, endIndex]` carries a `FermataAttachment` (`element.findAttachment(FermataAttachment.class) != null` — `src/main/java/songscribe/dom/FermataAttachment.java`). Enforce constraint 4 in `validate` under **both** strictnesses, and add the same check to `validateStated`. `Reason.FERMATA`.
  
  The reason belongs in the Javadoc: `getDuration()` inflates a fermata's duration by half again, which would break the exact-span property in playback. Excluding fermatas keeps written and performed durations in an exact ratio, so playback and validation sum the same values.
  
2. Fill in `SpanContext.crossesBeatBarrier`. **A beat barrier** is an element carrying:
  
  - a `BeatChangeAttachment`, or
    
  - a `TempoChangeAttachment` whose beat value (`tempo().tempoType()`) **differs** from the one in effect at that point.
    
  
  A tempo change that alters only BPM is not a barrier. **The anchor element is exempt** — a barrier there defines the tuplet's beat rather than splitting it. Elements from the second through the last are crossings. Enforce under `STRICT` only; `Reason.BEAT_BARRIER`.
  
  Implement this by walking `[beginIndex + 1, endIndex]` carrying the beat resolved at `beginIndex` (which `describeSpan` already has), updating the running beat at each beat-defining element.
  
3. Fill in `SpanContext.crossesStructuralBoundary`: true when any element in the span satisfies `getType().isBarLine()`, `getType().isRepeat()`, or is `ElementType.BREATH_MARK` — **except** a `BREATH_MARK` at `endIndex` that immediately follows the last note. Selections already sweep a trailing breath mark in (commit `6bcc1c6b`), so treating it as a boundary would disable the control on ordinary selections. Enforce under `STRICT` only; `Reason.STRUCTURAL_BOUNDARY`.
  
4. Gate constraint 5 (`M` conventional, `N / M` not a power of two — the `NO_CONVENTIONAL_SPAN` and `POWER_OF_TWO_RATIO` reasons Phase 4 implemented) on `Strictness.STRICT`. Under `LENIENT`, `validate` must accept a derived `M` even when it is not the conventional one and even when `N / M` is a power of two, provided constraints 1, 2 and 4 hold.
  
  Record in the Javadoc that redundancy is a property of **derivation**, not of the tuplet: when `M` is derived and the number says nothing, the tuplet is dropped; when `M` is stated in a file and is otherwise valid, it is kept even if non-conventional, because a file that explicitly says 3:2 for three eighths under a dotted-quarter beat is asking for a real re-timing, and a third-party 7:8 must survive.
  
5. Add the bulk forward-walk entry point:
  
  ```java
  public record Verdict(int lineIndex, Tuplet tuplet, Result result) {}
  
  public static List<Verdict> validateFrom(
      Song song, int fromLineIndex, int fromElementIndex, Strictness strictness);
  ```
  
  It returns a verdict for every tuplet whose **anchor** is at or after `(fromLineIndex, fromElementIndex)`, in document order. Pass `0, 0` to cover the whole song.
  
  **This must be a single forward walk, not a call to** `describeSpan` **per tuplet** — the latter is quadratic and re-derives per tuplet what one pass knows continuously. Carry the running beat; barriers fall out of the walk for free:
  
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
  
  The walk must reproduce the anchor exemption from task 2: a barrier landing on a tuplet's own anchor element is not a crossing for that tuplet.
  
  `Line.findTupletAt(int)` (`dom/Line.java:1708`) and the line's range elements are the source of tuplet spans; tuplets never nest (`Line.addTuplet` calls `removeOverlappingTuplets`), so at most one tuplet is open at a time.
  
6. Write the class-level Javadoc for `TupletValidator`, recording three decisions for a future reader:
  
  - **ABC import derives** `M` **rather than trusting the file's stated ratio.** No ABC importer exists yet; this is where one will look. ABC's `q` is an optional hand-written field with a documented default that producers get wrong: of 897 authored ratios in a 22,818-file corpus, 83 are wrong from writing the group size into the ratio slot, while the convention is wrong in 4. Deriving fixes 83 and costs 4. `N` still comes from the file — ABC's printed number is reliable. This is deliberately the opposite of the MusicXML policy, and the distinction is principled: in MusicXML, `actual-notes`/`normal-notes` is a required machine-written pair; in ABC, `q` is optional, hand-written and demonstrably unreliable.
    
  - **Why** `N / M` **being a power of two is redundancy**: the group is exactly expressible at the next finer written value with no bracket at all, so the number is a renotation instruction rather than a re-timing. `4:2` over four eighths at a quarter beat means "read these as sixteenths".
    
  - **The O(document) cost of beat resolution and why there is no cache** — refer to `Song.resolveBeatAt`, and state that `validateFrom` is the way to avoid paying it per tuplet.
    
7. Create `src/test/java/songscribe/dom/TupletValidatorConstraintsTest.java`, mirroring the setup helpers used by `src/test/java/songscribe/dom/TupletValidatorCoreTest.java` (created in Phase 4). All numbers as named constants. Cover:
  
  - A fermata anywhere in the span → `FERMATA`, under both strictnesses and via `validateStated`.
    
  - A `BeatChangeAttachment` on an interior element → `BEAT_BARRIER` under `STRICT`.
    
  - The same attachment on the **anchor** element → accepted (anchor exemption).
    
  - A `TempoChangeAttachment` inside the span that changes BPM only → accepted.
    
  - A `TempoChangeAttachment` inside the span that changes `tempoType` → `BEAT_BARRIER`.
    
  - A barline, a repeat, and an interior breath mark inside the span → `STRUCTURAL_BOUNDARY`.
    
  - A breath mark immediately after the last note → accepted.
    
  - Paired strict/lenient cases: for each of constraints 5, 6 and 7, one tuplet rejected under `STRICT` and the same one accepted under `LENIENT`.
    
  - `validateFrom` over a song with several tuplets: verdicts in document order, one per tuplet, with the running beat changing mid-song so a later tuplet is judged against a different beat than an earlier one; and `fromLineIndex`/ `fromElementIndex` correctly excluding tuplets anchored before that point.
    
8. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 6: Tuplet Model and Construction Sites
**Status:** Complete
**BlockedBy:** 3, 5  
**Files:** src/main/java/songscribe/dom/Tuplet.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/io/musicxml/RangeSpanResolver.java, src/main/java/songscribe/io/LineIO.java, src/test/java/songscribe/  
**Recommended model/effort:** Opus 4.8, medium effort — the model design is fixed by the plan, but it fans out to roughly thirty test files and two reader paths that must stay compiling

Read `## Shared Reference` in this file first — especially the **API contract** block, which fixes the `Tuplet` shape this phase creates.

`Tuplet` currently stores only `grade` and `verticalPositionSs` (`src/main/java/songscribe/dom/Tuplet.java`). This phase adds `M` and `V` and migrates every construction site. It writes broadly across `src/test/java/songscribe/` because the constructor signature changes; later phases that also touch those test files are ordered after this one.
### Tasks
1. Add to `Tuplet` the fields `normalNotes` (`M`), `noteValue` (`V`'s base `ElementType`, `@Nullable`) and `noteValueDots` (`V`'s dot count). Store `V` as a base `ElementType` plus a dot count rather than as a tick duration: this makes notatability true by construction, and it is exactly what `<normal-type>` plus `<normal-dot/>` needs on the way out — a tick duration would have to be decomposed at write time anyway.
  
  Replace the `Tuplet(anchor, end, grade)` constructor with the canonical `Tuplet(anchor, end, grade, normalNotes, noteValue, noteValueDots)`, and add:
  
  ```java
  public static final int UNRESOLVED_NORMAL_NOTES = 0;
  
  /**
   * A tuplet read from a file that did not state its ratio. M and V are pending
   * until the load pass either resolves or drops it — see TupletLoadPass.
   */
  public static Tuplet withUnresolvedRatio(
          StaffElement anchorElement, StaffElement endElement, int grade);
  
  public boolean isResolved();     // noteValue != null && normalNotes > 0
  
  /** Package-private: the dom-package load pass is the only caller. */
  void resolveRatio(int normalNotes, ElementType noteValue, int noteValueDots);
  ```
  
  Plus `getNormalNotes()`, `getNoteValue()` (`@Nullable`), `getNoteValueDots()`. There is no public setter for the ratio: the only way to obtain a half-built tuplet is `withUnresolvedRatio`, and only same-package code can complete it. Document that invariant on both members.
  
2. Update `createCopy` to copy **all** of `grade`, `normalNotes`, `noteValue`, `noteValueDots` and `verticalPositionSs`. Undo/redo round-trips through this method (`TupletAddition`/`TupletRemoval` carry the `Tuplet`), so a missed field is a silent undo data loss. Preserve the unresolved state faithfully — a copy of an unresolved tuplet is unresolved.
  
3. Delete the `getElementCount()` override in `Tuplet` and its two tests at `src/test/java/songscribe/layout/TupletTest.java:98` and `:107`. It returns `grade` while the base `RangeElement.getElementCount()` is documented as "the number of elements in this range", it has no production callers, and it becomes a trap once `getNormalNotes()` sits beside it. Confirm the absence of production callers with `jet_brains_find_referencing_symbols` before deleting.
  
  Leave `Tuplet.toIndexString()` unchanged — it needs no new fields. It is reached only from `LineIO.rangeElementsToString` for the batch UIConverter's `.mssw` → `.mssw` round-trip, and the load pass re-derives `M` and `V` for every `.mssw` file regardless.
  
4. Update the two production construction sites in `src/main/java/songscribe/ui/MusicEditOperations.java` (inside `toggleTuplet`, currently around `:218` and `:234`) to derive the ratio through the validator:
  
  ```java
  var result = TupletValidator.validateDerived(
      song, line, lineIndex, beginIndex, endIndex, tupletSize,
      TupletValidator.Strictness.STRICT);
  ```
  
  `toggleTuplet` already throws `IllegalStateException` for states the UI is supposed to have gated; follow that precedent and throw when `result.valid()` is false, with the `Reason` in the message. Phase 11 makes the UI gate this, so reaching the throw indicates a caller bug. Obtain `lineIndex` from the song/line the operation already holds; if the line's index is not directly available, use `song.getLineIndex(line)`-equivalent lookup that already exists in `Song` rather than adding a new API.
  
5. Update the two reader construction sites to `Tuplet.withUnresolvedRatio(...)`:
  
  - `src/main/java/songscribe/io/musicxml/RangeSpanResolver.java:207` (inside `resolveTuplet`)
    
  - `src/main/java/songscribe/io/LineIO.java:449` (inside `createTupletsFromPending`)
    
  
  Both run mid-parse, before the song is complete, so neither can resolve a beat that depends on tempo changes in earlier or later lines. Phase 9 adds the post-load pass that resolves or drops them; Phase 8 makes the MusicXML reader supply a stated ratio when the file carries one. Leave the surrounding `hasValidSpan` / `SAXException` handling exactly as it is — malformed spans stay out of scope.
  
6. Migrate every test construction site. Run `rg -n "new Tuplet\(" src/test` to enumerate them (roughly thirty files under `src/test/java/songscribe/`). For each:
  
  - Tests that do **not** exercise the ratio — layout, stacking, rendering, selection, mutation-label, invalidation, preview and clipboard-shape tests — switch to `Tuplet.withUnresolvedRatio(anchor, end, grade)`.
    
  - Tests that **do** exercise the ratio or persistence — at minimum `src/test/java/songscribe/midi/LineTrackBuilderTest.java`, `src/test/java/songscribe/io/musicxml/MusicXmlSpanRoundTripTest.java`, `src/test/java/songscribe/io/musicxml/MusicXmlWriterOutputTest.java`, `src/test/java/songscribe/io/musicxml/MusicXmlCorpusGenerator.java`, `src/test/java/songscribe/io/LineIOTest.java`, `src/test/java/songscribe/message/mutation/MutationRecordsTest.java`, `src/test/java/songscribe/undo/MutationReplayerRoundTripTest.java`, `src/test/java/songscribe/ui/clipboard/FragmentTest.java`, `src/test/java/songscribe/ui/clipboard/PasteSpanReconciliationTest.java`, `src/test/java/songscribe/layout/TupletTest.java` — switch to the canonical constructor with an explicit `M` and `V`. For a triplet of quarters at a quarter beat that is `grade = 3, normalNotes = 2, noteValue = ElementType.CROTCHET, noteValueDots = 0`. Use named constants, never bare literals.
    
7. Add a `createCopy` field-completeness assertion to `src/test/java/songscribe/layout/TupletTest.java`: build a tuplet with a non-default `M` and a **dotted** `V`, copy it, and assert all five copied fields (`grade`, `normalNotes`, `noteValue`, `noteValueDots`, `verticalPositionSs`) match. Add a second case asserting an unresolved tuplet copies as unresolved.
  
8. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green). Some existing MusicXML round-trip assertions may now fail because the reader produces unresolved tuplets while the writer still emits the old power-of-two `<normal-notes>`; Phase 8 fixes the writer/reader pair. If a failure is provably in that category, relax the assertion to what this phase can guarantee (the grade and the span) and leave a `// Phase 8` note — do **not** paper over any other failure.
  

* * *
## ✅ Phase 7: Playback
**Status:** Complete
**BlockedBy:** 6  
**Files:** src/main/java/songscribe/midi/LineTrackBuilder.java, src/test/java/songscribe/midi/LineTrackBuilderTest.java  
**Recommended model/effort:** Opus 4.8, medium effort — replacing per-element rounding with absolute-position rounding changes the accumulation model, not just a formula

Read `## Shared Reference` in this file first.

Phase 6 gave `Tuplet` the stored ratio: `getGrade()` is `N`, `getNormalNotes()` is `M`, `getNoteValue()` / `getNoteValueDots()` are `V`. This phase makes playback use it.

Today `LineTrackBuilder.getTupletFactor(int, Tempo)` (`src/main/java/songscribe/midi/LineTrackBuilder.java:63`) **ignores** the grade entirely: it sums each spanned element's `getDuration()`, divides by the tempo's reference note, snaps to a power of two, and returns `newDuration / tupletDuration`. `getElementDurationWithTuplet` (`:52`) then rounds each element independently and the caller at `:306` accumulates, so a 7:4 of sixteenths at PPQ 96 produces `7 × round(13.71) = 98` ticks instead of 96 and everything after the tuplet shifts.
### Tasks
1. Rewrite `getTupletFactor` to return `M / N` from the stored ratio, returning 1 when the element is not in a tuplet. Delete the power-of-two snapping, the `Tempo` parameter and the beat dependency. Update every caller found via `jet_brains_find_referencing_symbols` — if the `Tempo` parameter becomes unused on `getElementDurationWithTuplet` too, remove it there and at its call sites rather than leaving a dead parameter.
  
2. Replace the per-element rounding at `:52`, accumulated at `:306`, with rounding of **absolute positions within the tuplet**:
  
  ```
  endOffset = round(cumulativeWrittenTicks * M / N)
  duration  = endOffset - previousEndOffset
  ```
  
  where `cumulativeWrittenTicks` is the sum of `getDuration()` over the tuplet's elements from its anchor through the current element inclusive. No rational-arithmetic class is needed — the only denominator is `N`, so `long` arithmetic suffices. The group closes exactly, because `S * M / N = M * V` is an integer by construction; with constraint 4 excluding fermatas from tuplets, nothing perturbs this.
  
  Keep the change local to `LineTrackBuilder`. If the cleanest shape is a small private helper that, given an element index inside a tuplet, returns that element's tuplet-adjusted duration by differencing two rounded absolute offsets, do that.
  
3. Add a Javadoc note on the new factor method that this is the payoff for storing `M`: what is printed is what is heard, and a tempo edit can no longer re-time an existing tuplet.
  
4. Update `src/test/java/songscribe/midi/LineTrackBuilderTest.java`. Phase 6 already migrated its `new Tuplet(...)` calls to the canonical constructor with explicit `M` and `V`; existing expectations that were computed from the old snapping behavior will need recomputing from `M / N`. Add:
  
  - A 7:4 of sixteenths at PPQ 96 closing on exactly 96 ticks, with the element immediately after the tuplet starting exactly there.
    
  - A 3:2 of quarters and a 2:3 compound duplet, each closing exactly.
    
  - A tuplet containing a rest, confirming the rest consumes its scaled share.
    
  
  All numbers as named constants.
  
5. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green), paying attention to `MidiSequenceBuilderTest` as well as `LineTrackBuilderTest`.
  

* * *
## ✅ Phase 8: MusicXML Time Modification
**Status:** Complete
**BlockedBy:** 6  
**Files:** src/main/java/songscribe/io/musicxml/MusicXmlTags.java, src/main/java/songscribe/io/musicxml/MusicXmlNoteWriter.java, src/main/java/songscribe/io/musicxml/MusicXmlNoteReader.java, src/main/java/songscribe/io/musicxml/NoteAccumulator.java, src/main/java/songscribe/io/musicxml/RangeSpanResolver.java, src/test/java/songscribe/io/musicxml/  
**Recommended model/effort:** Opus 4.8, high effort — a reader/writer pair plus a semantic change to `<duration>`, threaded through the SAX accumulator

Read `## Shared Reference` in this file first.

Phase 3 already set `NoteTypeMapping.DIVISIONS = 13440`. Phase 6 already gave `Tuplet` its `normalNotes` / `noteValue` / `noteValueDots` fields and made `RangeSpanResolver.resolveTuplet` build tuplets via `Tuplet.withUnresolvedRatio(...)`.

Today every file on disk asserts a ratio the reader ignores and the model does not hold: `MusicXmlNoteWriter.writeTimeModification` emits `<normal-notes>` = `largestPowerOfTwoBelowGrade(grade)` (2→1, 3→2, 4→2, 5→4, 6→4, 7→4), and `MusicXmlNoteReader.handleEndNormalNotes` discards the value. A duplet is written as 2:1, which cannot express the compound duplet 2:3.
### Tasks
1. Add `NORMAL_TYPE` (`"normal-type"`) and `NORMAL_DOT` (`"normal-dot"`) to `src/main/java/songscribe/io/musicxml/MusicXmlTags.java`, following the file's existing naming and ordering conventions.
  
2. Change `MusicXmlNoteWriter.writeTimeModification` to take the `Tuplet` (its call site already has it — `spanMarkers.tuplet()`) and emit `<actual-notes>` = `N`, `<normal-notes>` = the stored `M`, `<normal-type>` = the `<type>` token for the stored `V` (via `NoteTypeMapping`), and one `<normal-dot/>` per dot on `V`. Element order must follow the MusicXML schema: actual-notes, normal-notes, normal-type, normal-dot*. Delete `largestPowerOfTwoBelowGrade` and rewrite the method Javadoc, which currently documents the write-forward-only behavior that is going away. Three corpus tuplets have a dotted `V` and cannot round-trip without `<normal-dot/>`.
  
3. Make `<duration>` (`MusicXmlNoteWriter`, currently around `:83`) the **performed** duration, which is what MusicXML requires. For a note inside a tuplet that is `NoteTypeMapping.ticks(type, dotCount) * M / N`; outside a tuplet it is unchanged. Phase 3's `DIVISIONS = 13440` guarantees this is an exact integer for every `ElementType` × dot count × `N ∈ {2..7}`; assert exactness rather than rounding, and let it throw if it is ever not (mirroring `NoteTypeMapping.ticks`'s existing `ArithmeticException`).
  
4. Stop discarding `<normal-notes>`. In `src/main/java/songscribe/io/musicxml/MusicXmlNoteReader.java`, replace the deliberate discard in `handleEndNormalNotes` with a `note.setNormalNotes(...)`, and add handlers for `<normal-type>` and `<normal-dot/>` in the same `Where.TIME_MODIFICATION` state. Thread the three values through `src/main/java/songscribe/io/musicxml/NoteAccumulator.java` alongside the existing `actualNotes` field — add them to the reset in `reset()`, to the `SpanMarkers` record, and to its construction site — mirroring exactly how `actualNotes` is handled today.
  
5. In `RangeSpanResolver.resolveTuplet`, capture the stated ratio on `markers.tupletStart()` alongside `markers.actualNotes()`, and on `markers.tupletStop()` build the tuplet with the canonical constructor when `<normal-type>` was present, or `Tuplet.withUnresolvedRatio(...)` when it was not. `<normal-type>` **presence is the trust discriminator**: a file that carries it was written by the new writer or by another application emitting a complete `<time-modification>`, so its `M` is trusted; a file without it predates this change and its `M` is derived by the load pass. Map the `<normal-type>` token back to an `ElementType` via `NoteTypeMapping`; an unrecognized token means an unresolved tuplet, not an exception.
  
6. Update the tests under `src/test/java/songscribe/io/musicxml/`. In `MusicXmlSpanRoundTripTest`, assert all three fields survive write/read, including a tuplet whose `V` is dotted (exercising `<normal-dot/>`), a septuplet surviving exactly, and a fixture without `<normal-type>` producing an unresolved tuplet. In `MusicXmlWriterOutputTest`, update the expected `<time-modification>` block and the performed `<duration>` values. Undo any `// Phase 8` relaxations Phase 6 left behind. All numbers as named constants.
  
7. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 9: Load Pass
**Status:** Complete
**BlockedBy:** 8  
**Files:** src/main/java/songscribe/dom/TupletLoadPass.java, src/main/java/songscribe/io/SongLoader.java, src/main/java/songscribe/io/musicxml/MusicXmlReader.java, src/test/java/songscribe/dom/TupletLoadPassTest.java, src/test/java/songscribe/io/TupletLoadParityTest.java  
**Recommended model/effort:** Opus 4.8, high effort — placement, suspension and the trust discriminator each have a silent-failure mode if got wrong

Read `## Shared Reference` in this file first.

Phase 5 built `TupletValidator.validateFrom(song, 0, 0, Strictness.LENIENT)` — the single forward walk carrying the running beat. Phase 6 gave `Tuplet` the package-private `resolveRatio(...)` and the `isResolved()` predicate. Phase 8 made the MusicXML reader produce a resolved tuplet when the file carried `<normal-type>` and an unresolved one otherwise; `LineIO` always produces unresolved ones.
### Tasks
1. Create `src/main/java/songscribe/dom/TupletLoadPass.java`:
  
  ```java
  public record Counts(int dropped, int migrated) {}
  
  public static Counts run(Song song);
  ```
  
  It must live in `songscribe.dom` because `Tuplet.resolveRatio` is package-private. It cannot live in `RangeSpanResolver.resolveTuplet` or `LineIO.createTupletsFromPending`, both of which run mid-parse before the song is complete and so cannot resolve a beat that depends on tempo changes in other lines.
  
2. Implement `run` as the forward walk, using `TupletValidator.validateFrom(song, 0, 0, TupletValidator.Strictness.LENIENT)`:
  
  - **Tuplet already resolved** (the file stated `<normal-type>`) — trust its `M`. Validate constraints 1, 2 and 4 only, via `TupletValidator.validateStated`. If the stored `V` disagrees with `S / N`, the file contradicts itself and the tuplet is dropped, consistent with "always drop, never repair".
    
  - **Tuplet unresolved** — derive `M` and `V` from the convention using the running beat, then call `resolveRatio(...)`. Count it as **migrated**.
    
  - **Invalid either way** — drop the tuplet (`Line.removeTuplet`). The notes survive; only the bracket and number go. Count it as **dropped**. Never repair.
    
  
  A tuplet that is both unresolved and invalid counts as dropped, not migrated.
  
3. Run the whole pass inside `Song.withoutMutationTracking` so it emits no mutations. Running it outside suspension would put tuplet removals into the undo history, making a freshly opened song undoable back to its invalid state. Read `.agents/guides/mutations.md` for the suspension semantics before writing this.
  
  **Do not set the modified flag inside the pass.** `ScoreView.setSong` clears it internally (see the comment at `src/main/java/songscribe/ui/component/ScoreView.java:452-458`, which is why `accidentalsConverted` re-marks the song _after_ installing it). The counts ride out on the return value; Phase 10 applies them.
  
4. Call `TupletLoadPass.run(song)` from **both** readers, immediately before constructing `SongLoadResult.Success`:
  
  - `src/main/java/songscribe/io/SongLoader.java:62`
    
  - `src/main/java/songscribe/io/musicxml/MusicXmlReader.java:285`
    
  
  Both must own it. Placing it in `ScoreView.openFile` instead would leave the headless MIDI-export route — `SongLoader` is documented for exactly that — playing tuplets the UI had dropped, producing two different MIDI files from one document with no error on either.
  
  `SongLoadResult.Success` does not yet carry the counts; Phase 10 widens it. In this phase, compute the counts, log them at info level, and discard them at the construction site with a `// Phase 10 carries these out on Success` comment.
  
5. Record in `TupletLoadPass`'s Javadoc that because the reader discarded `<normal-notes>` before this change, **no existing file carries an** `M` — every song containing a tuplet is migrated on first load, not just the legacy `.mssw` ones.
  
6. Create `src/test/java/songscribe/dom/TupletLoadPassTest.java` covering: a stated ratio being trusted even when non-conventional; an absent ratio being derived; a stored `V` disagreeing with `S / N` causing a drop; a tuplet failing constraint 1, 2 or 4 being dropped while its notes survive; the counts distinguishing dropped from migrated; and the pass leaving the song's modified flag and undo history untouched.
  
7. Create `src/test/java/songscribe/io/TupletLoadParityTest.java` asserting reader parity: one document containing both a valid and an invalid tuplet, expressed once as `.mssw` and once as MusicXML, loaded through `SongLoader.load` and `MusicXmlReader.read` respectively, yielding identical surviving tuplet sets and identical drop counts. Follow the fixture conventions already used by `src/test/java/songscribe/io/SongLoaderTest.java` and `src/test/java/songscribe/io/musicxml/MusicXmlReaderLenienceTest.java`.
  
8. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 10: Reporting
**Status:** Complete
**BlockedBy:** 1, 9  
**Files:** src/main/java/songscribe/io/SongLoadResult.java, src/main/java/songscribe/io/LoadWarning.java, src/main/java/songscribe/io/SongLoader.java, src/main/java/songscribe/io/musicxml/MusicXmlReader.java, src/main/java/songscribe/ui/component/ScoreView.java, src/test/java/songscribe/io/SongLoadResultTest.java, src/test/java/songscribe/ui/component/ScoreViewTest.java  
**Recommended model/effort:** Opus 4.8, medium effort — a record widening that fans out to four construction sites plus a dialog that absorbs an existing silent path

Read `## Shared Reference` in this file first, plus `.agents/guides/option-dialogs.md` and `.agents/guides/strings.md`.

**Write no new tests in this phase beyond what is needed to keep existing tests compiling.** The user verifies the migration dialog manually in Phase 14; Phase 15 writes the tests.

> **As-built correction — the single migration alert this phase describes was replaced.** Seeing it in Phase 14, the user rejected both the wording and the design. "One tuplet was updated to record the printed ratio" is unreadable to a musician, and because no file ever written by this program carries an `M`, that bullet fires on nearly every legacy song — a modal dialog reporting invisible bookkeeping the user cannot act on. It now splits by format:
>
> - **MusicXML reports nothing.** `SongFileLoader` rewrites the file in place instead (scratch sibling → `ATOMIC_MOVE`, original permissions carried over, failure logged and silenced), so the next open finds nothing to migrate. Any such file predates full tuplet storage and was a development artifact.
> - **`.mssw` gets a `Window.Type.UTILITY` palette window** (`ui/dialog/MigrationWindow`) with a `JTree`: top-level nodes "N tuplets were removed" / "N tuplets were updated" / "Natural + accidental was converted to plain accidental", each expanding to one node per change reading `Line N, elements a–b:` plus the reason. This is why `TupletLoadPass` returns `Report(List<Change>)` rather than `Counts(int, int)` — the per-tuplet reason was being computed and thrown away. `LoadWarning.Type.TUPLETS_DROPPED` was deleted as a second source of the same fact. The `alert.song.migrated*` keys are gone; the `dialog.migration.*` group replaces them.

Phase 1 already added every string key this phase needs. Phase 9 added `TupletLoadPass.run(song)` returning `Counts(dropped, migrated)`, called from `SongLoader.load` and `MusicXmlReader.read` with the counts currently discarded.

`SongLoadResult.Success` today is `Success(Song, DocumentFonts, @Nullable LoadWarning warning, boolean accidentalsConverted)` — a **single nullable slot**, so it cannot carry more than one problem, and `MusicXmlReader` never populates it.
### Tasks
1. Widen `src/main/java/songscribe/io/SongLoadResult.java`:
  
  ```java
  record Success(Song song, DocumentFonts fonts,
                 List<LoadWarning> warnings,
                 boolean accidentalsConverted,
                 int tupletsDropped,
                 int tupletsMigrated) implements SongLoadResult
  ```
  
  Keep the `Success.of(song, fonts)` convenience factory, defaulting to an empty warning list, `false`, `0`, `0`. Make the compact constructor defensively copy `warnings` with `List.copyOf`, matching the immutability convention used by `songscribe/ui/clipboard/Fragment.java`.
  
2. Add a member to `LoadWarning.Type` for dropped tuplets (the enum currently has exactly one member, `INVALID_LYRICS_DATE`), so the condition is available as structured data to non-UI callers. Update the enum's Javadoc, which currently describes it as a place for new ISO-8601 date fields.
  
3. Update all four `Success` construction sites: `src/main/java/songscribe/io/SongLoader.java:62`, the factory inside `SongLoadResult.java`, `src/main/java/songscribe/io/musicxml/MusicXmlReader.java:285`, and `src/test/java/songscribe/ui/component/ScoreViewTest.java:932`. `SongLoader` wraps its existing `INVALID_LYRICS_DATE` warning in the list; both readers now pass the `TupletLoadPass.Counts` through, and both add a dropped-tuplet `LoadWarning` when `dropped > 0`. `MusicXmlReader` currently hardcodes `null` for the warning — it starts populating the list.
  
4. Replace the bare dirty-flag handling at `src/main/java/songscribe/ui/component/ScoreView.java:452-458` with a single migration report covering **every** migration, including the retired-accidental conversion that is silent today. Assemble the applicable bullets from `Strings.ALERT_SONG_MIGRATED_ACCIDENTALS`, `Strings.ALERT_SONG_MIGRATED_TUPLETS_DROPPED` and `Strings.ALERT_SONG_MIGRATED_TUPLETS_UPDATED`, join them with `\n`, and pass the result as `{0}` to `Strings.ALERT_SONG_MIGRATED`. Each bullet key that carries a count is read through the varargs `Strings.get(key, count)`; the wrapper likewise. Surface it with:
  
  ```java
  OptionDialogs.showWarningMessage(
      mainFrame, Strings.ALERT_TITLE_SONG_MIGRATED, Strings.ALERT_SONG_MIGRATED, bullets);
  ```
  
  Show nothing when there was no migration of any kind. Leave the existing `INVALID_LYRICS_DATE` alert as its own separate dialog — it reports a data problem, not a migration — but adapt it to iterate the new `warnings` list instead of reading a single nullable slot.
  
5. Re-mark the song modified **after** `setSong`, not inside the load pass, when any migration occurred (accidentals converted, tuplets dropped, or tuplets migrated) — `setSong` clears the flag internally. This generalizes the existing `accidentalsConverted` re-marking; keep its explanatory comment and widen it.
  
6. Update `src/test/java/songscribe/io/SongLoadResultTest.java` and any other test that constructs or destructures `Success` so the suite compiles. Do not add new assertions about the dialog or the report wording — Phase 15 owns those.
  
7. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 11: Creation UI
**Status:** Complete
**BlockedBy:** 6  
**Files:** src/main/java/songscribe/ui/selection/TupletToggleInfo.java, src/main/java/songscribe/ui/selection/LineSelectionState.java, src/main/java/songscribe/ui/MusicEditOperations.java, src/main/java/songscribe/ui/action/TupletAction.java, src/main/java/songscribe/ui/menu/NotationMenu.java, src/main/java/songscribe/ui/component/TupletPopupButton.java, src/main/java/songscribe/ui/component/ScoreViewController.java, src/test/java/songscribe/ui/MusicEditOperationsNullStateTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerTest.java  
**Recommended model/effort:** Opus 4.8, high effort — enabled-vs-selected state across two surfaces, a context-dependent default action, and a per-selection cost budget

Read `## Shared Reference` in this file first.

**Write no new tests in this phase.** Update `MusicEditOperationsNullStateTest` and `ScoreViewControllerTest` only as far as needed to keep the suite compiling — they construct `TupletToggleInfo` directly. The user verifies this UI manually in Phase 14; Phase 15 writes the tests.

**The per-selection wiring already exists and must not be rebuilt.** `TupletAction` subscribes to `MusicSelectionDidChangeNotification`, `SongDidChangeNotification` and `DocumentDidLoadNotification` and sets its own enabled state in `handleChange`. `ScoreViewController.warmTupletCache` runs at `TUPLET_INFO_CACHE_PRIORITY` ahead of those handlers for all three notifications, so `canToggleTuplet()` executes **once** per dispatch, not once per action. Both surfaces consume the same shared singletons in `Actions.TOGGLE_TUPLET_ACTIONS`: `NotationMenu.createTupletMenu` builds `new JMenuItem(action)`, and `TupletPopupButton` builds its popup from the same list, disabling itself when `anyMatch(UIAction::isEnabled)` is false. A property set on the action therefore reaches both surfaces with **no rebuild API**.
### Tasks
1. Widen `src/main/java/songscribe/ui/selection/TupletToggleInfo.java`:
  
  ```java
  public record TupletToggleInfo(
      boolean canToggle,
      Set<Integer> validGrades,
      @Nullable Tuplet existing,
      boolean coversExisting
  )
  ```
  
  Defensively copy `validGrades` in the compact constructor. Update the two production construction sites in `src/main/java/songscribe/ui/MusicEditOperations.java` and the ones in `src/main/java/songscribe/ui/selection/LineSelectionState.java`, plus the test construction sites in `src/test/java/songscribe/ui/MusicEditOperationsNullStateTest.java` and `src/test/java/songscribe/ui/component/ScoreViewControllerTest.java`.
  
2. In `LineSelectionState.canToggleTuplet()`, stop requiring `isPitchedNote()` — rests are allowed inside a newly created tuplet and contribute to `S`; grace notes stay skipped and contribute nothing. Keep the existing rejection of a selection whose elements do not all share the same tuplet.
  
3. Make partial coverage disable creation: when `existing != null && !coversExisting`, return `canToggle = false`. (The extends-beyond case is already rejected by the existing `currentTuplet != firstTuplet` check.)
  
4. Populate `validGrades` by validating all six candidate grades (2, 3, 4, 5, 6, 7 — `TupletAction.Tuplet`'s non-`REMOVE` members) through `TupletValidator` under `Strictness.STRICT`. **Compute the span description once and reuse it across all six**: call `TupletValidator.describeSpan(song, line, lineIndex, beginIndex, endIndex)` a single time, then `TupletValidator.validate(context, grade, STRICT)` six times. The beat walk inside `describeSpan` is the only unbounded operation here, and `warmTupletCache` runs on every document edit, not only on selection changes.
  
5. In `TupletAction.handleChange` (currently the `if (tuplet == Tuplet.REMOVE) … else if (existing == null) … else if (!info.coversExisting()) … else …` chain):
  
  - `Remove` stays enabled whenever a tuplet exists.
    
  - The `existing == null` branch becomes `setEnabled(info.validGrades().contains(tuplet.getSize()))`.
    
  - Replace the final branch's disable-the-current-grade logic ("clicking it would be a no-op") with `Action.SELECTED_KEY`: the existing grade is shown **checked**, and re-picking it does nothing. Availability (`setEnabled`) and selection (`SELECTED_KEY`) are separate properties on the same singleton, so both surfaces get both for free.
    
  - The existing tuplet's grade is always shown and checked even when it is not currently creatable — otherwise a selection that visibly _is_ a tuplet would show nothing checked.
    
  - Clear `SELECTED_KEY` on every grade action when there is no existing tuplet, so the group has a genuine none-selected state.
    
  **As-built correction.** "Re-picking it does nothing" needs a change in the *model* as well as the action, and the first implementation made only the action half. `MusicEditOperations.toggleTuplet` still carried toggle-off semantics — an existing tuplet plus a matching grade removed it — so the grade showed correctly checked and then deleted the tuplet when clicked. `toggleTuplet` now returns without mutating in that case, and `TupletAction.Tuplet.REMOVE` is the only path that deletes. Caught in Phase 14 checklist item 2.
    
  
  When no grade is valid and no tuplet exists, every grade action ends up disabled and `TupletPopupButton`'s existing `anyMatch(UIAction::isEnabled)` check disables the whole control on its own — no extra code.
  
6. Make the grade items `JRadioButtonMenuItem`s with a none-selected state in both surfaces: `NotationMenu.createTupletMenu` (which builds `new JMenuItem(action)` in a loop) and `TupletPopupButton` (which passes `Actions.TOGGLE_TUPLET_ACTIONS` to `PopupButton`, whose constructor currently chooses between `JCheckBoxMenuItem` and `JMenuItem` based on `UIAction.Selectable`). Extend `PopupButton` minimally rather than duplicating its popup construction, and leave the `REMOVE_TUPLET_ACTION` item and the separator as plain items in both surfaces.
  
7. Make the toolbar button's default action context-dependent: `Remove` when the selection is exactly an existing tuplet, otherwise the first valid grade. This is visually free — every `TupletAction` is constructed with the same `"@"` icon at size 18, and `TupletPopupButton.configureButtonFromAction` pins a fixed tooltip, so the button face does not change when the default action does.

  **As-built correction.** This task was written assuming `PopupButton` has a direct-click-invokes-default versus press-and-hold-shows-popup split. It does not: `PopupButton.actionPerformed` always opens the popup, and `currentAction` only drives the button's appearance. The default action is therefore set through `currentAction` and no click behavior was invented. Building a genuine click/hold split would change a component shared by every popup button in the app and was ruled out of scope.
  
8. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green). Existing tuplet UI tests (`src/test/java/songscribe/ui/action/TupletActionTest.java`, `src/test/java/songscribe/ui/selection/LineSelectionStateTest.java`) that assert the old disable-the-current-grade behavior or the old rests-rejected behavior will fail; adjust those assertions to the new behavior, but add no new cases.
  

* * *
## ✅ Phase 12: Beat Edit Chokepoint
**Status:** Complete
**BlockedBy:** 1, 6  
**Files:** src/main/java/songscribe/dom/Song.java, src/main/java/songscribe/dom/Line.java, src/main/java/songscribe/dom/TempoChangeAttachment.java, src/main/java/songscribe/dom/BeatChangeAttachment.java, src/main/java/songscribe/ui/dialog/BeatChangeDialog.java, src/test/java/songscribe/dom/SongBeatEditTupletTest.java  
**Recommended model/effort:** Opus 4.8, high effort — one bracket must contain the edit and its companions, with replay suppression and companion ordering both easy to get subtly wrong

Read `## Shared Reference` in this file first, and **read** `.agents/guides/mutations.md` **in full before writing any code** — companion ordering, replay suppression and bracket nesting are all decided there.

Phase 1 already added `alert.title.tuplets.removed` and `alert.tuplets.removed`. Phase 5 built `TupletValidator.validateFrom(song, fromLineIndex, fromElementIndex, Strictness)`.

**Why this cannot be a list of call sites maintained by hand.** Because a barrier is defined _relative_ to the beat in effect, editing the song's `Tempo.tempoType` or an earlier tempo change can turn an attachment already sitting inside a tuplet's span into a barrier, without anything being inserted near that tuplet. Splitting a line can displace a tempo attachment onto an element inside a span (`src/main/java/songscribe/dom/Line.java:284`) — moving a barrier without editing it.

**Why this cannot be a** `SongDidChangeNotification` **subscriber.** Per `.agents/guides/mutations.md`, the notification fires after the outermost bracket closes, so removals would land in a second undo step. The work must happen inside the bracket.

Write only the model-level tests listed in task 6. The alert's firing behavior is verified by the user in Phase 14 and tested in Phase 15.
### Tasks
1. Add one `Song` bracket helper that runs a beat-defining edit and re-validates tuplets forward from that point inside the **same** bracket. The `Song` paths already funnel through `withModification(() -> applyChange(...))` (see `src/main/java/songscribe/dom/Song.java:1572`), so the shape fits what is there. The helper opens the bracket, emits removals for tuplets that `TupletValidator.validateFrom(song, editLineIndex, editElementIndex, STRICT)` now reports invalid, then runs the primary edit.
  
  Re-validate **forward from the edit position**, not over the whole song: a beat-defining event only affects positions after it.
  
2. Emit the companion removals **before** the primary mutation, per the companion-ordering rule in `.agents/guides/mutations.md`. Reverse-order undo then restores the primary first. This follows the existing `Line.addTuplet` → `removeOverlappingTuplets` precedent (`src/main/java/songscribe/dom/Line.java:1879`) and inherits its replay suppression — `.agents/guides/mutations.md` confirms tuplet auto-removal is already suppressed under `withReplay` at the helper level. Verify that the new validation likewise does not fire during replay.
  
3. Route every beat-defining write through the helper:
  
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
  
  Confirm this list is still complete with `jet_brains_find_referencing_symbols` on `Tempo.tempoType`-writing members and on `BeatChangeAttachment`'s constructor/setter before wiring; if a write exists that the list misses, route it too and note it in the phase's final report.
  
4. Make the underlying setters private or package-private so the helper cannot be bypassed. Where a setter must stay public for an unrelated reason, say so in its Javadoc and point at the helper.
  
5. Show the warning **once per edit**, not once per tuplet, when one or more tuplets were removed:
  
  ```java
  OptionDialogs.showWarningMessage(
      mainFrame,
      Strings.ALERT_TITLE_TUPLETS_REMOVED,
      Strings.ALERT_TUPLETS_REMOVED
  );
  ```
  
  The wording avoids pluralization, so this is a plain `Strings.get(key)` — no MessageFormat pattern and no apostrophe trap. `OptionDialogs` auto-suppresses in headless and test contexts. Because `dom` must not depend on UI, surface the alert from the UI side (the call site that initiated the edit, or a thin notification the UI already subscribes to) rather than importing `OptionDialogs` into `Song`; read `.agents/guides/option-dialogs.md` and `.agents/guides/messages.md` before choosing.
  
6. Create `src/test/java/songscribe/dom/SongBeatEditTupletTest.java` covering the model behavior only: inserting a `BeatChangeAttachment` inside a tuplet's span removes the tuplet; a single undo restores both the attachment removal and the tuplet; a barrier landing on the tuplet's **anchor** element does not remove it; editing the song tempo so an in-span tempo change becomes a barrier removes the tuplet; a line split that displaces a tempo attachment into a span removes the tuplet; a beat edit that invalidates nothing removes nothing; and nothing fires under `Song.withReplay`.
  
7. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ✅ Phase 13: Paste
**Status:** Complete
**BlockedBy:** 11, 12  
**Files:** src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java, src/main/java/songscribe/ui/clipboard/Fragment.java, src/test/java/songscribe/ui/clipboard/PasteSpanReconciliationTest.java  
**Recommended model/effort:** Opus 4.8, medium effort — one new rule inside an existing reconciliation pass, but it must land in the paste's own bracket

Read `## Shared Reference` in this file first, and `.agents/guides/mutations.md`.

`Fragment` captures `spans[]`, so tuplets travel through the clipboard. Pasting a tuplet into a different beat context can produce a tuplet that violates constraints 5–7 without any other call site firing — it would then survive, render and play until the next save-and-reopen, and vanish there with no explanation the user could connect to the paste.

`src/main/java/songscribe/ui/clipboard/PasteSpanReconciliation.java` already drops tuplets that straddle the paste boundary (see its class-level table and the `case Tuplet tuplet ->` branch). This phase adds a second, independent reason to drop.
### Tasks
1. In `PasteSpanReconciliation`, after the existing straddle handling, validate each surviving pasted tuplet against the **target** context with `TupletValidator.validateDerived(song, line, lineIndex, beginIndex, endIndex, tuplet.getGrade(), TupletValidator.Strictness.STRICT)` and drop the invalid ones. The notes survive; only the bracket goes. Keep the two drop reasons distinguishable in the class's existing accounting so the straddle rule's behavior is unchanged.
  
2. Emit the drops inside the paste's own modification bracket, as companions **before** the primary paste mutation, matching the pattern the beat-edit chokepoint uses (see Phase 12 and `.agents/guides/mutations.md`). One undo must restore the paste and its dropped brackets together.
  
3. Update the class-level table in `PasteSpanReconciliation` to record the new rule, and update the ASCII diagram at `src/main/java/songscribe/ui/clipboard/Fragment.java:45` — it documents the capture/paste flow and must not go stale in the same commit that changes what paste does to spans.
  
4. Extend `src/test/java/songscribe/ui/clipboard/PasteSpanReconciliationTest.java`: copy a triplet from a quarter-beat passage, paste it under a dotted-quarter beat, and assert the bracket is dropped while the notes survive; paste the same fragment into a compatible context and assert the bracket is kept; assert one undo restores both the paste and a dropped bracket. Existing straddle cases must still pass unchanged. All numbers as named constants.
  
5. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
  

* * *
## ⏳ Phase 14: Manual UI Verification
**Status:** Pending  
**BlockedBy:** 10, 11, 12, 13  
**Files:** —  
**Recommended model/effort:** — user-driven; no model executes this phase

This phase is the user's. Do not write code or tests for it, and do not launch the app without explicit permission (`./scripts/run.sh`, per `.agents/rules/development.md`).

Present the user with this checklist and wait for their confirmation before Phase 15 begins. If the user reports a defect, fix it in the owning phase's files and re-run this checklist rather than working around it in tests.
### Checklist
1. **Menu and toolbar filtering.** Select a run of notes that supports only some grades. The Notation → Tuplet menu shows exactly the valid grades enabled and the rest disabled; the toolbar tuplet popup shows the same. A selection where no grade is valid disables the whole toolbar control.
  
2. **Checked grade.** Select an existing tuplet exactly. Its grade shows as a checked radio item in both the menu and the popup, and is checked even when that grade would not currently be creatable. Re-picking it does nothing. With no tuplet selected, nothing is checked.
  
3. **Partial coverage.** Select a strict sub-range of an existing tuplet: only Remove is available.
  
4. **Rests.** Select a run containing a rest and create a tuplet — it succeeds, and the rest sits inside the bracket.
  
5. **Toolbar popup.** Clicking the toolbar tuplet button opens the popup, and the grades offered there match the menu.

  ~~With an existing tuplet exactly selected, a direct click removes it. With an ordinary selection, a direct click applies the first valid grade. Press-and-hold still opens the popup.~~ **Dropped.** `PopupButton` has no click-versus-press-and-hold split — a click always opens the popup, and `currentAction` only drives the button's appearance. Adding a real split is new behavior on a component shared by every popup button in the app, which is out of scope for this issue. Phase 11 sets the context-dependent default action for appearance only.
  
6. **Beat edit removes tuplets.** With a valid tuplet in the score, change the song's tempo note value (or insert a beat change inside the tuplet's span) so the tuplet becomes invalid. The tuplet disappears, the "Tuplets Removed" warning appears exactly once, and a single undo restores both the beat change and the tuplet.
  
7. **Paste across beat contexts.** Copy a triplet from a quarter-beat passage and paste it under a dotted-quarter beat: the notes land, the bracket does not. Paste into a compatible context: the bracket survives.
  
8. **Migration reporting.** Open an existing `.musicxml` file containing tuplets: **nothing is reported**, and the file is rewritten in place so a second open finds nothing to migrate. Confirm the file on disk now carries `<normal-type>`, and that its permissions are unchanged. Then open a `.mssw` file with a deliberately invalid tuplet: the **Migration** palette window appears, its tree lists the removed and updated tuplets with a line/element reference and a reason for each, the accidentals node appears when applicable, the notes survive, and the document is marked modified.
  
9. **Playback.** Play a passage containing a 7:4 and a compound duplet and confirm nothing after the tuplet drifts.
  

* * *
## ⏸️ Phase 15: UI Tests
**Status:** Pending  
**BlockedBy:** 14  
**Files:** src/test/java/songscribe/ui/action/TupletActionTest.java, src/test/java/songscribe/ui/selection/LineSelectionStateTest.java, src/test/java/songscribe/ui/component/ScoreViewControllerTest.java, src/test/java/songscribe/ui/component/ScoreViewTest.java, src/test/java/songscribe/dom/SongBeatEditTupletAlertTest.java, src/test/java/songscribe/message/command/ToggleTupletCommandTest.java  
**Recommended model/effort:** Sonnet 4.6, medium effort — the behavior is settled and manually verified; this is test authoring against a fixed target

Read `## Shared Reference` in this file first, plus `.agents/guides/testing-unit.md` and `.agents/guides/testing-common.md`. Do not run e2e tests.

The user has confirmed the UI behavior in Phase 14. Write the tests that pin it. Use named constants for every number. Do not change production code in this phase — if a test reveals a defect, report it rather than fixing it silently.
### Tasks
1. In `src/test/java/songscribe/ui/selection/LineSelectionStateTest.java`, cover `canToggleTuplet()`'s new behavior: rests permitted in a new tuplet; a strict sub-range of an existing tuplet returning `canToggle = false`; and `validGrades` contents for representative selections at a quarter beat and at a dotted-quarter beat (matching the expected-results tables in `## Shared Reference`).
  
2. In `src/test/java/songscribe/ui/action/TupletActionTest.java`, cover: a grade action enabled iff `validGrades` contains its size; the existing tuplet's grade reported through `Action.SELECTED_KEY` even when that grade is not currently creatable; nothing selected when there is no existing tuplet; `Remove` enabled whenever a tuplet exists; and every grade action disabled when no grade is valid and no tuplet exists.
  
3. In `src/test/java/songscribe/ui/component/ScoreViewControllerTest.java`, pin that `warmTupletCache` still computes `canToggleTuplet()` exactly once per dispatch with the widened `TupletToggleInfo`.
  
4. Create `src/test/java/songscribe/dom/SongBeatEditTupletAlertTest.java` covering the alert contract from Phase 12: no removals means no alert; the alert fires once per edit, not once per tuplet; nothing fires under `Song.withReplay`.
  
5. Cover the migration reporting as rebuilt (see the as-built note on Phase 10). In `src/test/java/songscribe/io/SongFileLoaderTest.java`: a `.musicxml` file whose tuplets migrate is rewritten in place and comes back with an empty report and `accidentalsConverted == false`; a second load of the rewritten file migrates nothing; a `.musicxml` needing no migration is not rewritten (compare last-modified time); a failed rewrite still returns a silenced `Success` with the original file intact; and the rewritten file keeps its original POSIX permissions. In `src/test/java/songscribe/ui/component/ScoreViewTest.java`: a `.mssw` load with drops and updates shows the Migration window, a load with no migration shows nothing, and any migration marks the song modified after `setSong`. Add a `MigrationWindowTest` covering the tree it builds from a `TupletLoadPass.Report` — group nodes present only when non-empty, one child per change in document order, 1-based line/element display, and the reason text chosen per `TupletValidator.Reason`.
  
6. Update `src/test/java/songscribe/message/command/ToggleTupletCommandTest.java` for the new `TupletToggleInfo` shape and the validator-derived ratio, and confirm the remaining classes named in the spec's "Existing tests to update" list are all green: `TupletTest`, `LineTrackBuilderTest`, `MutationRecordsTest`, `MutationReplayerRoundTripTest`, `MusicXmlSpanRoundTripTest`, `MusicXmlWriterOutputTest`, `NoteTypeMappingTest`, `LineIOTest`, `PasteSpanReconciliationTest`, `FragmentTest`, `SongDefaultsTest`.
  
7. Run `./scripts/compile.sh` (SUCCESS) then `./scripts/test.sh unit` (green).
