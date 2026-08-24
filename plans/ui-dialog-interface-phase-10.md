# Phase 10: Manual UI Verification Checklist

Source: `plans/ui-dialog-interface.md`, Phase 10. This is the only place any
dialog's populate-gather-ops wiring is confirmed — no test covers it. The
agent prepares this checklist and records results; the user drives the app.

**Before running the app:** the agent must ask the user for permission.
`./scripts/run.sh` is never executed without it.

---

## How to use this checklist

Work through each dialog once. For every row, note **Pass**, **Fail**, or
**Expected** (Phase 5's known gesture-3 defect only) in the Result column, and
add a note for anything unexpected. A failure anywhere in this track is a
defect in this track, not a new finding — record it, do not fix it inline.

---

## 1. Every dialog: open, geometry, OK, Cancel, invalid input

Cover every dialog in `ui.dialog`, not only the ones this track rewrote.

| Dialog                  | Opens correctly | Geometry / tab selection as before | OK commits and the model changes | Cancel discards | Invalid input shows the right message | Result |
|-------------------------|-----------------|------------------------------------|----------------------------------|-----------------|---------------------------------------|--------|
| `SongSettingsDialog`    | x               | x                                  | x                                | x               | x                                     | OK     |
| `FontDialog`            | x               | x                                  | x                                | x               | x                                     | OK     |
| `AnnotationDialog`      | x               | x                                  | x                                | x               |                                       | OK     |
| `BeatChangeDialog`      |                 |                                    |                                  |                 |                                       |        |
| `TempoChangeDialog`     |                 |                                    |                                  |                 |                                       |        |
| `KeyChangeDialog`       |                 |                                    |                                  |                 |                                       |        |
| `PreferencesDialog`\*\* |                 | n/a                                | n/a                              | n/a             |                                       |        |

\* `PreferencesDialog` is non-modal with no OK/Cancel/button row (Phase 6).
Verify only that it opens and closes correctly; see §3 for its own items.

`ProgressBarDialog` is not on this list. It is deleted, along with the
standalone `UIConverter` entry point that was its only caller and the temporary
DEBUG menu item that made it exercisable.

---

## 2. High-risk cases (Phase 10 task 3)

These are the cases most likely to expose a wiring mistake.

- [x] **`SongSettingsDialog`'s cross-tab lyrics-fit failure.** Set a lyrics
      font on the Music/Title tab that would overflow a line carrying
      lyrics; confirm the refusal message appears and nothing commits.
- [x] **Add-vs-Modify button label** on each of the three attachment dialogs
      (`AnnotationDialog`, `BeatChangeDialog`, `TempoChangeDialog`): opening
      on an empty slot shows "Add"; opening on an existing attachment shows
      "Modify" (or equivalent OK-button wording).
- [x] **Remove button** on each of the three attachment dialogs. It is now
      rendered by `StandardDialog` itself (`ops.remove() != null`). Confirm
      it appears only when there is something to remove, and that pressing
      it removes the attachment in one undo step.
- [x] **`SongSettingsDialog.show(Section)`** opens the right tab with the
      right field focused, for each caller/section that opens it directly
      (not just the default entry point).

---

## 3. All four key-change gestures (Phase 10 task 4)

Exercise all four `KeyChangeDialogController` entry points. Also confirm the
key combo's fifths ordering (…F, C, G, D, A, E, B… by accidental count) and
that the correct sharp/flat glyphs render for each selection.

| # | Gesture | Entry point | Expected opening key | Expected commit | Result |
|---|---|---|---|---|---|
| 1 | Header double-click | `editLineKey(frame, line)` | `line.getRunningKey()` | `changeLineKey` | |
| 2 | Cautionary double-click | `editLineKey(frame, nextLine)` | same key, on the **next** line | `changeLineKey` | |
| 3 | Mid-line signature double-click | `editKeyChange(frame, line, signature)` | the clicked element's own key | `insertKeyChange` — **⚠ expected to insert a second signature, not edit the first** | Expected |
| 4 | `KeyChangeAction.insertionPointChosen` | `addKeyChange(frame, line, index)` | `line.keyAt(index)` | `insertKeyChange` | |

**Gesture 3 note:** double-clicking a mid-line key signature is known to
insert a second signature in front of the clicked one rather than changing
it (`plans/ui-dialog-interface.md` Phase 5, item ⚠). The score will read
`♯♯♯ ♭♭`-style with two signatures and the music will still read in the old
key. This is the one item on this checklist whose failure is **not** a
defect in this track — mark it Expected, not Fail. Do not attempt to fix it
here; the fix is tracked separately in `plans/design-pass/keys.md` group C
item 6.

---

## 4. PreferencesDialog (Phase 10 task 5)

`PreferencesDialog`'s live effects travel through `PrefsDidChangeNotification`
rather than a direct call, so each of these must be checked as a side effect,
not as a return value from the dialog.

- [ ] Switching appearance (theme) retints the UI immediately, with the
      dialog still open.
- [ ] Changing page size takes effect without reopening the score/document.
- [ ] Changing units (metric radio) takes effect without reopening —
      **expected to change nothing visible today**; `PrefsKey.METRIC` is
      write-only until page setup lands (Phase 6 item 5). Confirm the radio
      itself still toggles and persists, but do not expect a visual change.
- [ ] All three Play sliders (or however many the build exposes) snap to
      their tick stops when dragged.
- [ ] Selecting an instrument in the Instruments tab auditions a note.
- [ ] The Scale button toggles playback, and restarts on a new instrument
      selection while already playing.
- [ ] Leaving the Instruments tab stops the scale playback.
- [ ] **Play the score after changing instrument, tempo, and note
      duration** (three separate edits, then play). These plus loop/repeat
      are the four keys `ScoreViewController.prefsDidChange`'s widened
      handler now carries (Phase 6 item 2) — a handler that misses one
      fails silently, so each must be checked, not just the aggregate.

---

## 5. Recording the result (Phase 10 task 6)

After running through §1–§4, record here:

- **Date run / build tested:**
- **Overall result:** Pass / Fail (list failures)
- **Failures found (if any):** file, dialog, gesture, and what was expected
  vs. observed. Each is a defect in this track.
- **Expected non-defects observed:** gesture 3's duplicate signature (§3),
  and anything else already called out above as expected.
- **Anything not exercised and why.**
