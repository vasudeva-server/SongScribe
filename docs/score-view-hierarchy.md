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
