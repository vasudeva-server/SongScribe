# Test-Only Production Surface

Handoff. The inventory below is preliminary — completing it is the first task.

No production member exists to serve a test. A member whose only callers are
tests is deleted, or the code is restructured so production supplies what the
test needed. Widening visibility, adding a `*ForTest` accessor, or branching
production code on a test flag are all the same violation.

`ui/dialog/AttachmentDialogController.java:55` cites this file as the rule it
obeys.

## What checks the work

Tests are resident in `src/test/`, so both halves of each fix are verifiable:

- `jet_brains_find_referencing_symbols` on the member shows every caller,
  production and test alike. A member with only test callers is the finding; a
  member with none is dead either way.
- `./scripts/compile.sh --test` catches a removal the tests still depend on.
  Run it after each fix, and rewrite the affected test to the new shape in the
  same change.

Judge a restructured member on whether it is a coherent unit with its own
contract, never on whether it is convenient to call from a test. A shape adopted
because it suits an existing test is the original violation rearranged.

## Task 1: complete the inventory

The sweep below used two searches, and **neither alone is sufficient** — members
like `ActionReflector.hasSavedActionStates` carry no telltale name and appear
only in the comment search, while a declaration with no comment appears only in
the name search.

```bash
# names
PROSE=1 rg -n "ForTest|ForTesting|VisibleForTesting" src/main/java/

# justifications written in comments
PROSE=1 rg -n "for test|for tests|package-private for test|package-visible for test|in unit tests|so a test|that a test|test setup|test teardown|test isolation|test injection|test inspection|for testability" src/main/java/
```

Both miss a member that is widened silently, with neither a naming convention
nor a comment admitting why. Those are found only by asking of each
package-private or non-private member: **which production code calls this?** Use
`jet_brains_find_referencing_symbols`; a member with no production caller in this
worktree is a candidate regardless of how it is named.

The preliminary sweep hit roughly **30 files**. Record the full list before
fixing anything, because the fixes cluster — several files share one root cause
and one change retires them together.

## Task 2: fix them

### Confirmed declarations

Verified as declarations, not comments. Line numbers as of this writing.

| File | Member | Line |
|---|---|---|
| `ui/playback/MidiController.java` | `failForTesting`, and the branch reading it | 61, 81 |
| `error/RuntimeError.java` | `setExitHandlerForTesting`, `resetAlertShownForTesting` | 135, 140 |
| `message/MessageCenter.java` | `setPublicationErrorProbeForTesting`, `setSubscriptionProbeForTesting` | 29, 34 |
| `prefs/RecentDocumentsManager.java` | `resetForTest`, `reloadForTest` | 109, 119 |
| `ui/component/LyricEditor.java` | `setFocusedForTesting`, `setSuppressDismissAdjustmentForTesting` | 1742, 1747 |
| `ui/component/MainFrame.java` | `clearStartupErrorsForTest` — **`public`** | 189 |
| `ui/component/score/PreviewElementManager.java` | `resetOverlaysForTest` | 587 |
| `ui/dialog/PreferencesDialog.java` | `resetInstrumentsForTesting` | 206 |
| `lifecycle/Shutdown.java` | `reset()` | 195 |

### Comment-flagged, members not yet read

Each of these carries a comment admitting a test justification. Read the member
before deciding anything; some will turn out to be like the two settled cases
below, where the comment is wrong and the member is legitimate.

`SongScribe.java` (46, 53, 86, 116) · `dom/ModificationSession.java` (165) ·
`dom/StaffElement.java` (603) · `smufl/SMuFLMetadata.java` (105, 125) ·
`ui/OptionDialogs.java` (59) · `ui/component/BorderPanel.java` (70, 125, 130) ·
`ui/component/score/FootnotesComponent.java` (100) ·
`ui/component/score/LineComponent.java` (146, 150, 1219, 1222) ·
`ui/component/score/LineRenderer.java` (203, 317, 343, 461, 505) ·
`ui/component/score/LineSelectionHandler.java` (63) ·
`ui/component/score/PreviewOverlayRegistry.java` (207) ·
`ui/component/score/StaffPanel.java` (183, 210) ·
`ui/component/score/TextPanel.java` (186) ·
`ui/dialog/SongSettingsLayout.java` (37) ·
`ui/dialog/fontchooser/model/FamilyListModel.java` (42) ·
`ui/edit/EditModeManager.java` (90) · `ui/edit/GraceModeManager.java` (296) ·
`ui/edit/InsertionPointMode.java` (206) ·
`ui/playback/PlaybackController.java` (221, 237, 531, 546) ·
`ui/selection/ActionReflector.java` (257) ·
`ui/selection/SelectionDragTracker.java` (87)

### Already settled — do not re-investigate

Both are stale comments on legitimate members. Fix the comment, leave the code.

- **`lifecycle/Shutdown.java:185`**, `runJVMTasksFromHook`. The comment says
  "Package-private for tests", but the registry-owned JVM shutdown hook calls it.
  It is a real production entry point. Drop the test clause from the comment.
  (`Shutdown.reset()` at 195 is a genuine violation and is in the table above.)
- **`dom/SongMetadata.java:131`**, the header reading "Normalization helpers
  (package-visible for tests in this package)". The two members under it —
  `normalizeTitle` (143) and `titleFromLyrics` (177) — are both `public` with
  production callers. Delete the parenthetical.

### Choosing the fix

Three kinds, and the fix differs:

- **Genuinely test-only** — delete it, or move the logic somewhere production
  constructs directly. When a test cannot arrange the state it needs, the answer
  is a constructor or factory taking that state, used by production too.
- **A misnamed internal API** — it takes explicit arguments and returns a value,
  so it is already a coherent unit. Rename it to the concept and write its
  contract.
- **An incomplete lifecycle contract** — a class with `initialize()` and no way
  back has a missing half, tests or no tests. Name and document the teardown
  rather than deleting the member. Most of the `reset*` / `setInstance` group is
  this: a process-global with no way to put it back, so every test needs a back
  door to undo the last one. Fixing the lifecycle retires several at once.

### Start with `MidiController`

`failForTesting` (61) is not surface, it is a branch. It is a mutable static that
`openMidi()` — production code, on every launch — reads, and when set the method
throws `MidiUnavailableException("forced failure for testing")`. So what a test
exercises is not what ships, and anything leaving the flag set poisons audio for
the rest of the process. Take it first.

`MainFrame.clearStartupErrorsForTest` (189) is next: it is `public`, so it is
application-wide API existing for a test rather than a widened internal.

## The pattern to follow

`prefs/PrefsUpgrade.java` is the worked example, from removing seven such members
from `Prefs`.

`Prefs` had `getRawStored` ×2, `putRawStored`, `removeObsoleteKeysForTest`,
`removeSystemDefaultKeysFromStoreForTest`, `writeTypedForTest` and
`migrateForTest`. All seven existed for one reason: the startup transformations
were private methods on a singleton mutating its private store, and nothing could
obtain a `Prefs` whose store it chose. None of them needed the singleton.

The four transformations moved to `PrefsUpgrade`, a package-private class taking
the store, the defaults and the system-default keys, with one
`apply(oldPropsFile)` running them in the order they depend on and reporting
whether the store changed. `Prefs` builds one over its own store and saves once if
it says so.

Two things to copy from it:

- **Extract the logic to a type constructed with the state it works on**, rather
  than exposing the singleton's internals. That is what made all seven members
  unnecessary at once instead of one at a time.
- **Keep the steps private.** `apply` is the promise; the four steps are not.
  Exposing them so a test can drive each one individually is the same mistake one
  level out.
