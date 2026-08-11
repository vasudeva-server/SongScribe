# ScoreView Page Layout Hierarchy

The Swing containment tree `songscribe.ui.component.ScoreView` builds and paints into.
The class itself carries a prose summary and points here.

```
  JScrollPane
  └── ScorePanel [GridBagLayout, gray background]
      └── ScoreView [BorderLayout, white background, full page size]
          │  EmptyBorder: top/bottom = 0.5", left/right = horizontal margin
          ├── LyricEditor            (absolute bounds, topmost; only while editing a lyric)
          ├── LineOverlayComponent × N (absolute bounds, above the score, below the editor)
          └── MainPanel [BoxLayout Y_AXIS, CENTER]
              ├── TitleComponent
              ├── SubtitleComponent
              ├── ScoreMarginStrut
              ├── StaffPanel [StaffLinesLayout]
              │   └── LinePanel × N
              ├── TextPanel
              └── FootnotesComponent
```

`MainFrame`'s own content pane is a plain `BorderLayout`: the toolbar in `NORTH`, the
`ScoreView` scroll pane in `CENTER`, and the status bar in `SOUTH`.

## Mouse dispatch

Most of the components in the tree above — `MainPanel`, `ScorePanel`,
`TitleComponent`, `SubtitleComponent`, `FootnotesComponent`, `TranslationComponent`,
and the lyrics components — have no mouse listeners of their own, so Swing's
`LightweightDispatcher` retargets a click that lands on one of them up to the nearest
ancestor that does: `ScoreView`, whose `ScoreInputHandler` is where component
double-clicks are dispatched. `LineComponent` is the deliberate exception: it
registers itself as its own `MouseListener` and consumes its clicks, so a click on a
staff line never reaches `ScoreInputHandler` and cannot be double-dispatched.

```
 double-click on the score
        │
        ▼
 Swing LightweightDispatcher picks the deepest component WITH mouse listeners
        │
        ├── LineComponent ──► its own mouseClicked
        │                     grace → paste → playback guard → lyric →
        │                     isStaffEditGesture → attachment
        │                     CONSUMED; never reaches ScoreInputHandler
        │
        └── ScoreView ──────► ScoreInputHandler.mouseClicked
                                   │
                              BUTTON1? ──no──► return
                                   │yes
                    UIUtils.isLeftDoubleClick(e)
                      && !PlaybackController.isPlaying()
                                   │
                        ┌──────────┴──────────┐
                       yes                    no  (click 1 of the pair,
                        │                     │    or playing)
                        ▼                     │
              scoreComponentAt(e)             │
              depth-first search by BOUNDS,   │
              NOT by z-order and NOT by       │
              listener presence               │
                        │                     │
         ┌──────────────┼──────────────┐      │
         ▼              ▼              ▼      │
   Title/Subtitle   other Score    null       │
         │          Component    (MainPanel   │
         │              │         or a gap)   │
         ▼              ▼                     │
   openEditor()    openEditor()      │        │
   opens Song      → false ──────────┼────────┤
   Settings at its     │             │        │
   editorSection()     │             │        │
         │             │             │        │
         ▼             │             │        │
      CONSUMED         │             │        │
       return          └─────────────┴────────┤
                                              ▼
                          cancel paste mode ► post DeselectCommand ► request focus
                                  (the existing path, unchanged)
```

`scoreComponentAt` re-resolves the click target itself rather than trusting the
component the event arrived on — that component is only ever `ScoreView`, since that is
what the listener walk retargeted to.

It resolves by **bounds**, not by stacking order, and deliberately does not use
`SwingUtilities.getDeepestComponentAt`. The overlays in this tree are *siblings* of the
score components rather than descendants of them: `LyricEditor` and the
`LineOverlayComponent`s are absolutely positioned children of `ScoreView`, laid over the
`MainPanel` that holds the score. A stacking lookup would therefore answer with the
overlay and never reach the score component beneath it, silently ending the gesture
wherever an overlay happens to cover a title. Instead the search descends the tree
testing each child's own `contains`, examining *every* child that holds the point rather
than stopping at the topmost, so a non-score component lying over a score component is
stepped past. `PreviewElementManager.retargetMouseLine()` resolves by bounds for exactly
the same reason.

No overlay reaches the title band today, so nothing in the running application exercises
that; `ScoreInputHandlerTest`'s sibling-overlay test is what keeps a future one from
quietly breaking the gesture.

The title and the subtitle share the Song Settings *Title* tab but are edited in
different fields, so each names its own section: `TitleComponent.editorSection()`
returns `Section.TITLE`, `SubtitleComponent.editorSection()` returns
`Section.SUBTITLE`. The section chooses the tab and the field the caret lands in, so
double-clicking a piece of text opens the dialog on that very text.

Because a title component is sized to exactly the text it draws, it needs no hit
testing — but by the same token an *empty* title or subtitle has no bounds and so no
hit area. Double-clicking where a missing subtitle would go does nothing; that field
is reached through the menu. This is deliberate: a phantom target for empty text would
put an invisible click-swallowing strip above every score. The gesture reveals what is
already on the page rather than being the way to put something there.
