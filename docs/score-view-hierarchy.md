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

## One dispatch route

Every score component registers as its own mouse listener and handles its own
clicks. A click is never handed to an ancestor that then has to work out where it
really landed: the toolkit picks the deepest component under the point that has
listeners, and that component decides what the click means.

That scan walks past a listener-free sibling covering the point rather than
stopping at it, which is how a click over an overlay reaches the score component
beneath: an overlay carries no mouse listeners of its own, for the reason each
overlay class's Javadoc states. The overlays are siblings of the score column
positioned over the page, so this is the ordinary case rather than a corner one,
and the toolkit's scan relies on it.

A score component registers its mouse listener once, in its constructor —
see `ScoreComponent`'s class Javadoc for how a detached component (a preview
drawn inside a dialog) still receives events but acts on none of them.

A click that reaches the page landed on no score component. It does what a score
component does when nothing on it was editable — see `ScoreComponent.clicked`
for the exact rule, including how a double-click behaves.

```
 click on the score
        │
        ▼
 the toolkit picks the deepest component that HAS listeners
 (walking past overlays, which have none)
        │
        ├── a score component ──▶ handles it itself
        │      │
        │      ├── title / subtitle ──▶ double-click opens settings
        │      │                        at that field
        │      ├── a staff line ──────▶ grace mode, paste placement,
        │      │                        playback guard, lyric, staff-edit
        │      │                        gesture, attachment, attribution
        │      └── nothing editable ──▶ cancel a pending placement, deselect
        │
        └── the page ───────────▶ the click was on no component:
                                  cancel a pending placement, deselect,
                                  take focus
```

## Editing text by double-clicking it

Two routes open the song's settings on a double-click, and they answer different
questions.

**A component whose bounds *are* its text needs no hit testing.** The title and
the subtitle share one settings tab but are edited in different fields, so each
names its own section. The section chooses both the tab and the field the caret
lands in, so double-clicking a piece of text opens the dialog on that very text.

Because a title component is sized to exactly the text it draws, an **empty**
title or subtitle has no bounds and so no hit area. Double-clicking where a
missing subtitle would go does nothing; that field is reached through the menu
instead.

This is deliberate. A phantom target for empty text would put an invisible
click-swallowing strip above every score. The gesture reveals what is already on
the page rather than being the way to put something there.

**A region drawn inside another component's bounds does need it.** The song's
attribution block reaches the very same dialog, but it is not a component of its
own: it is drawn inside the first staff line's component, and the line resolves it
through the registry of clickable areas. The block can overlap the notation, and
only the registry can settle which of the two owns the point.

Two consequences follow that a reader cannot derive from the route itself. The
attribution gesture works in edit mode as well as select mode, matching the title
and the subtitle. And where the block hangs down into the range in which a note
could be inserted, the pending note wins, so those staff positions stay reachable.

Being addressable by a click is a separate question from being selectable; see
[selection.md](selection.md).

## One colour for everything the page cannot show

Each text component draws a fixed number of rows and no more. Text past that is
not shrunk, scrolled or elided: the leading rows draw and the rest does not.

What ties this to the rest of the score is the colour. Clipped title text takes
**the same colour** as the staff lines of a line holding more than the staff can
show, because it is the same situation — the page is showing less than the
document holds. That agreement is the part no single component can state, and it
is the whole of the signal: there is no alert and no badge.

Because the surplus is never drawn, a text component still sizes to exactly what
it draws, so everything the previous section says about hit testing holds
unchanged.
