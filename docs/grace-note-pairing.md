# Grace Note Pairing State Machine

`songscribe.ui.edit.GraceModeManager` drives the multi-click interaction that places a
grace note and pairs it with a host note. This document is the reference for that state
machine; the class itself carries only a prose summary.

## States

| State | Meaning |
| ----- | ------- |
| `INACTIVE` | No grace interaction in progress. The resting state. |
| `GRACE_NOTE` | A grace note has been placed and the manager is waiting to learn whether it pairs with an existing note, a note the user is about to insert, or nothing. |
| `GRACE_NOTE_INSERT` | The user dragged right with no next note present, so the manager is previewing the insertion of a new host note. |
| `GRACE_NOTE_PAIRED` | A host note was chosen (or inserted) and the pairing is established. |
| `FINISH` | Terminal step that tears the interaction down, either cancelling or committing, and returns to `INACTIVE`. |

## Transitions

```
                         mouseDown + grace action selected
                         + room check passes
                    ┌─────────────────────────┐
                    │                         ▼
              ┌──────────┐            ┌─────────────┐
              │ INACTIVE │            │ GRACE_NOTE  │
              └──────────┘            └──────┬──────┘
                    ▲                        │
                    │              ┌─────────┼──────────┐
                    │              │         │          │
                    │         drag-left   click/    drag-right
                    │         or Esc     no next    + next note
                    │              │      note       exists
                    │              │         │          │
                    │              │         ▼          │
                    │              │  ┌────────────┐    │
                    │              │  │ GRACE_NOTE │    │
                    │              │  │  _INSERT   │    │
                    │              │  └─────┬──────┘    │
                    │              │        │           │
                    │              │   click│left-click │
                    │              │   Esc  │line change│
                    │              │   ┌────┼────┐      │
                    │              │   │    │    │      │
                    │              ▼   ▼    ▼    │      ▼
                    │         ┌──────────┐  ┌──────────────┐
                    │         │ FINISH   │  │ GRACE_NOTE   │
                    │         │ (cancel) │  │   _PAIRED    │
                    │         └────┬─────┘  └──────┬───────┘
                    │              │               │
                    │              │               ▼
                    │              │         ┌──────────┐
                    │              └────────►│ FINISH   │
                    │                        │(success) │
                    │                        └────┬─────┘
                    │                             │
                    └─────────────────────────────┘
```

### Edge-by-edge

- **`INACTIVE` → `GRACE_NOTE`** — a mouse-down while a grace action is selected, provided
  the room check passes (there is horizontal space on the line for the grace note).
- **`GRACE_NOTE` → `FINISH` (cancel)** — the user drags left, or presses Escape.
- **`GRACE_NOTE` → `GRACE_NOTE_PAIRED`** — the user drags right and a next note already
  exists to pair with.
- **`GRACE_NOTE` → `GRACE_NOTE_INSERT`** — the user clicks, or drags right where there is
  no next note, so a host note has to be created. Entering this state computes and caches
  the host-note insertion preview; it stays fixed until the user clicks or cancels.
- **`GRACE_NOTE_INSERT` → `FINISH` (cancel)** — a click that misses, or Escape.
- **`GRACE_NOTE_INSERT` → `GRACE_NOTE_PAIRED`** — a left-click that commits the inserted
  host note, or a line change.
- **`GRACE_NOTE_PAIRED` → `FINISH` (success)** — the pairing is committed.
- **`FINISH` → `INACTIVE`** — teardown, in both the cancel and success cases.
