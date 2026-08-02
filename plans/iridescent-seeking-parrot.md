# Revalidate lyric chains when a Fragment is captured (#708) — COMPLETE

## Context

Copy and cut both build a `Fragment` via `Fragment.capture` (its only call site is
`ScoreViewController.handleCopy`). Capture cloned the selected elements verbatim, lyrics
included, so a syllable that continued into a neighbor outside the copied range kept saying
so.

Reported symptom: with `N1="A-"` (syllabic `BEGIN`) hyphenated to `N2="mi"`, copying only
`N1` and pasting it before some other `BEGIN` syllable drew a hyphen joining the two — the
pasted syllable adopted a word it was never part of. The melisma chain (`Lyric.Extend`) had
the same hole at both ends of a capture.

The paste path only repairs the *destination* (`repairNeighborsBeforeInsertion`,
`adjustSyllablesForSuccessorAfterInsertion`); by then the dangling `BEGIN` is
indistinguishable from a deliberate continuation. The repair belongs at capture.

## What was built

The repair a fragment needs is the repair an edit already performs: everything before and
after the run is gone, which is what a deletion produces. Only two things tied those repairs
to `Line` — `elements` was a field rather than a parameter, and every write went through
`modifyElement` to record an undo entry.

**`songscribe.dom.LyricRun`** (new interface) now holds the whole lyric-chain cluster —
~1000 lines and 36 methods moved verbatim out of `Line`, as `default`/`private` methods.
Its only abstract members are `getElement`, `elementCount`, `effectiveElementCount`,
`isPairedGraceNote` and `modifyElement`; `Line` already had all five with those exact
signatures, so `Line implements LyricRun` added no members and no call site changed.

**`songscribe.dom.DetachedLyricRun`** (new record) implements the same interface over a bare
`List<StaffElement>`, running each repair and recording nothing — a detached run is in no
document, so there is no bracket to record into.

**`LyricRun.endDanglingChains()`** (new) is the fragment entry point, composed entirely from
the existing deletion repairs, per verse:

- head: `cascadeClearExtend` when the first lyric is a carrier (its `START` was left behind),
  then `fixSuccessorSyllabic(-1, …)` — the missing-predecessor case
- tail: `adjustPrecedingForStopDeletion` (`CONTINUE`→`STOP`, `START`→`NONE`), then
  `endDanglingWord`, extracted from `adjustNeighborsForLyricDeletion` so both callers share
  one definition

`Fragment.capture` calls it on the clones after the clone loop; the source line is untouched.

Two incidental changes: `ExtendFix` became a local record inside its one user
(`adjustExtends`), and `StaffElement.isPairedGraceNote()` now holds the grace-note pairing
rule so `Line` and `DetachedLyricRun` share it rather than restating it.

## Files

- **New** `src/main/java/songscribe/dom/LyricRun.java`, `DetachedLyricRun.java`
- `src/main/java/songscribe/dom/Line.java` — cluster removed, `implements LyricRun`, unused
  logger and imports dropped
- `src/main/java/songscribe/dom/StaffElement.java` — `isPairedGraceNote()`
- `src/main/java/songscribe/ui/clipboard/Fragment.java` — the call, plus flow comment/Javadoc
- **New** `src/test/java/songscribe/dom/DetachedLyricRunTest.java` (16 tests)
- `src/test/java/songscribe/ui/clipboard/FragmentTest.java` — `@Nested LyricChains` (4 tests)
- `.agents/guides/lyrics.md` — new section on where chain repair lives

## Verification

- `./scripts/compile.sh` — SUCCESS
- `./scripts/test.sh unit` — 6788 passed, 1 skipped (6768 before, +20 new)
- The moved block was diffed against the original: the only differences are the two
  intentional edits above.
- The new `FragmentTest` cases were confirmed to fail with the `endDanglingChains` call
  disabled, and pass with it.

Remaining: a manual pass in the app (type `A-mi`, copy the first note alone, paste before a
`BEGIN` syllable; repeat with cut and with a melisma tail).
