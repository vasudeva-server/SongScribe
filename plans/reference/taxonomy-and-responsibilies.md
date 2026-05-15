# SongScribe Class Taxonomy and Layers of Responsibility

## Architectural Layer Diagram

```
+------------------------------------------------------------------+
|                        APPLICATION SHELL                          |
|  MainFrame, StartFrame, SplashWindow, ProfileManager, Version    |
+------------------------------------------------------------------+
        |              |              |              |
        v              v              v              v
+-------------+ +-------------+ +----------+ +------------+
|   MENUS &   | |   DIALOGS   | | TOOLBARS | |  ACTIONS   |
|   MenuCtrl  | | Standard-   | | Duration | | UIAction   |
|   DebugState| | Dialog      | | Dot/Rest | | StickyUI-  |
|             | | Properties- | | Accid.   | |  Action    |
|             | | Dialog      | | Artic.   | | Insertion- |
|             | | ExportPDF-  | | Connect. | | NoteAction |
|             | | Dialog ...  | | Bar ...  | | Pasteboard |
+-------------+ +-------------+ +----------+ +------------+
        |              |              |              |
        +-------+------+--------------+--------------+
                |
                v
+------------------------------------------------------------------+
|                      SCORE COMPONENT LAYER                        |
|  Score (central facade)                                          |
|  ScorePanel > LinePanel > LineComponent (per staff line)          |
|  TitleComponent, FootnotesComponent, TranslationComponent        |
|  LineLyricsComponent, UnderLyricsComponent, BanglaLyricsComponent|
|  ScoreInputHandler, SelectionHandler, InsertionNoteManager       |
+------------------------------------------------------------------+
        |              |              |
        v              v              v
+-------------+ +-------------+ +------------------+
| SELECTION   | | EDIT MODES  | |    CLIPBOARD      |
| Selection-  | | EditMode-   | | ClipboardManager  |
| Coordinator | | Manager     | |                   |
| NoteSelect- | | ScoreActions| |                   |
| ion, Line-  | | (interface) | |                   |
| SelectState | |             | |                   |
+-------------+ +-------------+ +------------------+
        |              |              |
        +-------+------+--------------+
                |
                v
+------------------------------------------------------------------+
|                      MESSAGE / EVENT BUS                          |
|  MessageCenter (Java singleton, mbassy)                          |
|  ScoreViewController (@Handler, HIGH_PRIORITY)               |
|  MessageLogger                                                   |
|  Messages: ModeChanged, MusicSelectionChanged, LayoutChange,     |
|    DurationSelected, ToggleBeam/Tie/Tuplet/Trill, NewFile,       |
|    OpenFile, Save, Print, PasteboardOp, InsertLine, Deselect ... |
+------------------------------------------------------------------+
        |              |
        v              v
+------------------+ +---------------------------------------------+
|    RENDERING     | |              LAYOUT ENGINE                   |
| ElementRenderer  | | LayoutEngine, LayoutResult                  |
|  (interface)     | | NoteColumn, NoteColumnBuilder                |
| BaseElement-     | | HorizontalSpacingCalculator                 |
|  Renderer        | | VerticalStackingCalculator                  |
|  NoteRenderer    | | LineJustificationCalculator                 |
|  RestRenderer    | | InsertionSpacingCalculator                  |
|  GraceNote-      | | LayoutStylesheet, LayoutConstants           |
|   Renderer       | | CollisionDetector, LayoutAccumulator        |
|  BeamGroup-      | | Bounds, ElementBounds, NoteBounds           |
|   Renderer       | | Margin, MarginReference, Size               |
|  Articulation-   | +---------------------------------------------+
|   Renderer       |          |
|  BarRenderer     |          v
|  StaffRenderer   | +---------------------------------------------+
|  ClefRenderer    | |           LAYOUT ELEMENTS                   |
|  KeySignature-   | | LineElement (abstract base)                 |
|   Renderer       | |   Attachment: Tempo, BeatChange, Fermata,   |
|  TempoRenderer   | |     Annotation, Dynamic                     |
|  BeatChange-     | |   RangeElement: Tie, Trill, Tuplet,         |
|   Renderer       | |     Ending, Crescendo, Diminuendo           |
|  AnnotationR.    | |   Articulation, BeamGroup, Staff, Clef,     |
|  DynamicsR.      | |     KeySignature, Attribution               |
|  TieRenderer     | +---------------------------------------------+
|  TupletRenderer  |
|  EndingRenderer  |
|  TrillRenderer   |
|  FermataRenderer |
|  LyricsRenderer  |
|  GlissandoR.     |
| RendererRegistry |
| RenderContext    |
| GraphicsState    |
+------------------+
        |
        v
+------------------------------------------------------------------+
|                     DOMAIN / MUSIC MODEL                          |
|  Composition > Line > Note                                       |
|                                                                  |
|  Note (abstract)                                                 |
|  +-- Semibreve, Minim, Crotchet, Quaver, Semiquaver,            |
|  |   Demisemiquaver                                              |
|  +-- SemibreveRest, MinimRest, CrotchetRest, QuaverRest,        |
|  |   SemiquaverRest, DemisemiquaverRest                         |
|  +-- GraceQuaver, GraceSemiQuaver                                |
|  +-- NonNote (abstract)                                          |
|      +-- SingleBarLine, DoubleBarLine, FinalDoubleBarLine        |
|      +-- RepeatLeft, RepeatRight, RepeatLeftRight                |
|      +-- BreathMark, GlissandoNote, PasteNote                   |
|                                                                  |
|  Enums: NoteType, KeyType, ArticulationType, ForceArticulation,  |
|         DurationArticulation, BeatChange                         |
|  Support: Tempo, Annotation, BeamCalculator, LyricsProcessor,    |
|           MusicEditOperations                                    |
+------------------------------------------------------------------+
        |              |              |
        v              v              v
+-------------+ +-------------+ +------------------+
| PERSISTENCE | |   EXPORT    | | MIDI / PLAYBACK  |
| Composition | | PDFExporter | | MidiSequence-    |
|  IO         | | SVGExporter | |  Builder          |
| LineIO      | | ImageExport | | PlaybackCtrl     |
| NoteIO      | | ABCExporter | | MidiController   |
| AnnotationIO| |             | | PlaybackSettings |
| TempoIO     | |             | |                  |
| ViewIO      | |             | |                  |
| FormatMigr. | |             | |                  |
| XML utils   | |             | |                  |
+-------------+ +-------------+ +------------------+
        |
        v
+------------------------------------------------------------------+
|                     UTILITIES & DATA                              |
|  GraphicUtils, UIUtils, MyFontUtils                              |
|  Utils, FileUtils, StringUtils, Log                              |
|  Interval, IntervalSet, TupletIntervalData                       |
|  Prefs, FileExtensions, MyFileFilter                             |
+------------------------------------------------------------------+
```

## Key Inheritance Hierarchies

### Note Hierarchy
```
Note (abstract)
├── Semibreve, Minim, Crotchet, Quaver, Semiquaver, Demisemiquaver
├── SemibreveRest, MinimRest, CrotchetRest, QuaverRest, ...
├── GraceQuaver, GraceSemiQuaver
└── NonNote (abstract)
    ├── SingleBarLine, DoubleBarLine, FinalDoubleBarLine
    ├── RepeatLeft, RepeatRight, RepeatLeftRight
    └── BreathMark, GlissandoNote, PasteNote
```

### Renderer Hierarchy
```
ElementRenderer<T> (interface)
└── BaseElementRenderer<T> (abstract)
    ├── NoteRenderer, RestRenderer, GraceNoteRenderer
    ├── BeamGroupRenderer, ArticulationRenderer
    ├── BarRenderer, StaffRenderer, ClefRenderer, KeySignatureRenderer
    ├── TempoRenderer, BeatChangeRenderer, AnnotationRenderer
    ├── DynamicsRenderer, TieRenderer, TupletRenderer
    ├── EndingRenderer, TrillRenderer, FermataRenderer
    ├── LyricsRenderer, GlissandoRenderer
```

### Action Hierarchy
```
UIAction (extends AbstractAction)
├── StickyUIAction ── DurationAction, BarAction
├── InsertionNoteAction ── AccidentalAction, DotAction, FermataAction, ...
├── PasteboardAction ── CutAction, CopyAction, PasteAction, DeleteAction
├── NewAction, OpenAction, SaveAction, PrintAction, ...
├── ToggleTieAction, ToggleBeamAction, ToggleTrillAction, ...
└── ExportPDFAction, ExportMidiAction, ExportABCAction, ...
```

### Layout Element Hierarchy
```
LineElement (abstract)
├── Attachment (abstract) ── Tempo, BeatChange, Fermata, Annotation, Dynamic
├── RangeElement (abstract) ── Tie, Trill, Tuplet, Ending, Crescendo, Diminuendo
├── Articulation, BeamGroup, Staff, Clef, KeySignature, Attribution
```

### UI Component Hierarchy
```
Score (JComponent) ── central facade
ScoreComponent (abstract, JComponent)
├── LineComponent ── per-line rendering + input
├── TitleComponent, TextPanel
├── LineLyricsComponent, UnderLyricsComponent, BanglaLyricsComponent
├── TranslationComponent, FootnotesComponent
```

### Dialog Hierarchy
```
StandardDialog (abstract)
├── PropertiesDialog, TutorialDialog, KeyMapDialog
├── UpdateDialog, WhatsNewDialog, ExportPDFDialog
├── LineWidthChangeDialog, ...
```

## Design Patterns in Use

| Pattern | Where                                                                            |
|---------|----------------------------------------------------------------------------------|
| MVC | Model=Composition/Line/Note, View=Score/Renderers, Controller=Actions/InputHandler |
| Strategy | ElementRenderer hierarchy (one renderer per element type)                        |
| Observer | MessageCenter event bus with @Handler annotations                                |
| Command | UIAction hierarchy encapsulates user operations                                  |
| Composite | Composition > Line > Note tree; LineElement tree                                 |
| Registry | RendererRegistry maps element types to renderers                                 |
| Builder | NoteColumnBuilder, MidiSequenceBuilder, LayoutResult.builder()                   |
| Template Method | BaseElementRenderer, StandardDialog                                              |
| Facade | Score as the central access point for score state                                |
| Singleton | MessageCenter, RendererRegistry                                                  |
