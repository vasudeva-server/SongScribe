# Design Pass 6 — Step 9: Judge the diagrams

## What it found

**The target holds no diagram.** Row 6 has no `docs/` document of its own. The Javadoc of the six target types (`Line`, `ElementChange`, `ProjectedElements`, `StaffElementRun`, `EditableRun`, `LineSpans`) holds no `<pre>` block, no box drawing, and no arrow chart.

**The pass added no diagram anywhere.** The diff from the pass's start commit `13075b18`, over `docs/` and `src/main`, adds no diagram. It removes three inline transition notes in `LyricRun` that used arrows (for example `CONTINUE → STOP`), and each sits in a comment or a Javadoc list. None of them is a diagram. `docs/lyrics.md`, which step 6 edited, has no diagram.

**One diagram touches this target's types, in another row's document.**`docs/key-changes.md:70–77` is a table of six lines. It shows each line's own key, mid-line change, inherited key, and running key at start and end. It shows where the key walk stops. That document is the design of row 0 (_Keys_, ✅). Row 6 excludes key state.

## What needs deciding

1. **The** `docs/key-changes.md:70–77` **table: judge it here, or leave it with row 0.** It is not this target's diagram, so this step does not judge it unless you say so. If you want a verdict, the verdict is **keep**. The table shows per-line state across six lines at once, and the prose under it (`:79–84`) reads its columns. Prose alone must state each cell one at a time. No contract states the walk's stop point as a picture.
  
  - <span id="rd-c1">**Leave it with row 0:** </span>step 9 records "no diagram" and closes.
  - **Judge it here:** step 9 records the verdict "keep" with the reason above, and changes nothing.

---
roughdraft: "1.0"
comments:
  rd-c1:
    body: leave it
    by: user
    at: "2026-09-21T18:53:50.448Z"
