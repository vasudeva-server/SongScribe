# The Score View

The containment tree the score is built and painted into, and how a click finds
its way to the right part of it.

## The tree

```
  scroll pane
  └── grey backdrop
      └── the page  (white, full page size, margins as a border)
          │
          ├── the lyric editor        absolute bounds, topmost, only while editing
          ├── line overlays × N       absolute bounds, above the score
          └── the score column        vertically stacked, centred
              ├── title
              ├── subtitle
              ├── the staff area
              │   └── one panel per line
              ├── the text block
              └── footnotes
```

The overlays are the part worth noticing: they are **siblings** of the score
column rather than descendants of the lines they cover, positioned absolutely over
the page. That is what makes hit resolution below need care.

Outside the page, the main window is an ordinary border layout — toolbar above,
this scroll pane in the middle, status bar below.

## Two dispatch routes, deliberately

Most components in the tree have no mouse listeners of their own, so the toolkit
retargets a click that lands on one of them up to the nearest ancestor that does:
the page, whose input handler is where component double-clicks are resolved.

**A staff line is the deliberate exception.** It registers as its own listener and
consumes its clicks, so a click on a staff never reaches the page's handler and
cannot be dispatched twice — once by the line and once as a component
double-click.

```
 double-click on the score
        │
        ▼
 the toolkit picks the deepest component that HAS listeners
        │
        ├── a staff line ──▶ handles it itself: grace mode, paste placement,
        │                    playback guard, lyric, staff-edit gesture,
        │                    attachment — consumed, never seen by the page
        │
        └── the page ──────▶ left double-click, not playing?
                                  │
                                  ├── no ──▶ cancel a pending placement,
                                  │          deselect, take focus
                                  │
                                  └── yes ─▶ resolve the target BY BOUNDS
                                                │
                                                ├── title / subtitle ──▶ open
                                                │   settings at that field
                                                └── anything else ──▶ deselect
```

## Resolving by bounds, not by stacking

The page re-resolves the click target itself rather than trusting the component
the event arrived on — that component is only ever the page, since that is what
the listener walk retargeted to.

Resolution descends the tree testing each child's own bounds, examining **every**
child that contains the point rather than stopping at the topmost. A stacking-order
lookup would answer with an overlay and never reach the score component beneath
it, silently ending the gesture wherever an overlay happened to cover a title.
Because the overlays are siblings rather than descendants, that is a real
possibility rather than a hypothetical one, and resolving by bounds steps past
them.

No overlay reaches the title band today, so nothing in the running application
exercises it; a test is what keeps a future one from quietly breaking the gesture.

## Editing text by double-clicking it

The title and the subtitle share one settings tab but are edited in different
fields, so each names its own section. The section chooses both the tab and the
field the caret lands in, so double-clicking a piece of text opens the dialog on
that very text.

A title component is sized to exactly the text it draws, so it needs no hit
testing — but by the same token an **empty** title or subtitle has no bounds and
so no hit area. Double-clicking where a missing subtitle would go does nothing;
that field is reached through the menu instead.

This is deliberate. A phantom target for empty text would put an invisible
click-swallowing strip above every score. The gesture reveals what is already on
the page rather than being the way to put something there.
