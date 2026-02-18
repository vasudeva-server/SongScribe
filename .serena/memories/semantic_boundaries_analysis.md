# SongScribe Semantic Boundaries Analysis

## Complete Codebase Metrics
- **Total Java/Kotlin Files**: 394
- **Total Lines of Code**: 59,811
- **Token Estimate**: 44,858 tokens (lines × 0.75)

## Line of Code Distribution by Subsystem

### Primary Subsystems (Top-Level)
1. **songscribe.music** - 5,493 lines (9.2%)
   - Domain model: Note types, Composition, Line, Articulations, Attachments
   
2. **songscribe.ui** - 45,791 lines (76.5%) - LARGEST SUBSYSTEM
   - UI layout, rendering, components, messaging, actions, dialogs
   
3. **songscribe.converter** - 1,039 lines (1.7%)
   - CLI converter utilities for batch format conversion
   
4. **songscribe.export** - 299 lines (0.5%)
   - Export orchestration (PDFExporter, SVGExporter, ABCExporter, ImageExporter)
   
5. **songscribe.io** - 2,509 lines (4.2%)
   - File I/O, XML serialization, format migration
   
6. **songscribe.midi** - 327 lines (0.5%)
   - MIDI sequence building and playback settings
   
7. **songscribe.util** - 1,980 lines (3.3%)
   - General utilities (logging, graphics, file, string, UI utils)
   
8. **songscribe.data** - 900 lines (1.5%)
   - Preferences, page layout, intervals, file filters
   
9. **songscribe.font** - Not measured (small)
   - Font management and font chooser

10. **Root Level** - 214 lines (0.4%)
    - SongScribe (main entry), Version, MidiLister, MusicChangeListener

### UI Subsystems (Detailed Breakdown within 45,791 lines)

#### songscribe.ui.component - 8,803 lines (19.2% of UI)
- **Core Components**: MainFrame, Score, ScorePanel, MainPanel
- **Score Rendering**: LineComponent, StaffPanel, LinePanel, TitleComponent
- **Text/Lyrics**: LyricsPanel, TextPanel, LineLyricsComponent, TranslationComponent
- **Input Handling**: ScoreInputHandler, ScoreFocusController, InputUtils
- **Utilities**: StickyToggleButton, PopupButton, NumericTextField, BorderPanel, TipFrame

#### songscribe.ui.layout - 6,162 lines (13.5% of UI)
- **Legacy Layout System**: Staff, Bar, BeamGroup, Tuplet, Tie
- **Element Positioning**: NoteBounds, ElementBounds, NoteSpacing, Bounds
- **Attachments System**: Attachment, TempoAttachment, FermataAttachment, AnnotationAttachment
- **Layout Coordination**: LayoutAccumulator, MeasureContext, LayoutStylesheet

#### songscribe.ui.renderer - 6,557 lines (14.3% of UI)
- **Base Infrastructure**: BaseElementRenderer, RendererRegistry, RenderContext
- **Element Renderers**: NoteRenderer, RestRenderer, BeamGroupRenderer, TieRenderer
- **Notation Renderers**: StaffRenderer, KeySignatureRenderer, ClefRenderer
- **Attachment Renderers**: DynamicsRenderer, ArticulationRenderer, TempoRenderer, TrillRenderer

#### songscribe.ui.layout2 - 3,597 lines (7.9% of UI)
- **New Modern Layout Engine**: LayoutEngine, NoteColumnBuilder, HorizontalSpacingCalculator
- **Vertical Layout**: VerticalStackingCalculator, VerticalStackingResult
- **Justification**: LineJustificationCalculator, InsertionSpacingCalculator
- **Data Structures**: NoteColumn, LayoutResult, LayoutConstants

#### songscribe.ui.action - 4,647 lines (10.1% of UI)
- **Action Framework**: UIAction, StickyUIAction, Actions (registry)
- **Edit Actions**: DurationAction, NoteTypeAction, AccidentalAction, DotAction
- **Document Actions**: NewAction, OpenAction, SaveAction, SaveAsAction
- **Export Actions**: ExportPDFAction, ExportSVGAction, ExportABCAction, ExportImageAction, ExportMidiAction
- **Playback Actions**: PlayPauseAction, StopAction, LoopPlaybackAction
- **Score Actions**: ToggleTieAction, ToggleBeamAction, EditNoteAction, DeleteAction

#### songscribe.ui.dialog - 6,221 lines (13.6% of UI)
- **Settings Dialogs**: PreferencesDialog, CompositionSettingsDialog, LineWidthChangeDialog
- **Export Configuration**: ExportPDFDialog, ExportMidiDialog, ResolutionDialog
- **Editing Dialogs**: LyricsDialog, AnnotationDialog, BeatChangeDialog, KeySignatureChangeDialog
- **Information Dialogs**: AboutDialog, HelpDialog, TutorialDialog, UpdateDialog, WhatsNewDialog
- **Base Classes**: StandardDialog, PropertiesStateStore, ProcessDialog

#### songscribe.ui.message - 1,511 lines (3.3% of UI)
- **Event Bus**: MessageCenter (coordinator), Message (base Kotlin class)
- **Score Events**: MusicSelectionChangedMessage, LayoutChangeMessage
- **Edit Events**: UpdateEditNoteMessage, DurationSelectedMessage, NoteTypeSelectedMessage
- **State Events**: ModeChangedMessage, ControlChangedMessage, RestModeChangedMessage
- **File/UI Events**: SaveMessage, OpenFileMessage, NewFileMessage, PrintMessage
- **Playback Events**: PlaybackStateChangedMessage, PlaybackTempoChangedMessage
- **Debug/Monitoring**: MessageLogger, ScoreMessageCoordinator

#### songscribe.ui.playback - 1,300 lines (2.8% of UI)
- **Playback Orchestration**: PlaybackController (central coordinator)
- **MIDI Control**: MidiController, PlayNoteThread, MidiMetaMessageTypes
- **Playback Menu/Actions**: PlayMenu, PlayPauseAction, StopAction, LoopPlaybackAction, PlayWithRepeatsAction
- **Configuration**: InstrumentDialog, TempoChangeAction
- **Events**: PlaybackStateChangedMessage, PlaybackTempoChangedMessage, LoopPlaybackMessage

#### songscribe.ui.edit - 580 lines (1.3% of UI)
- **Edit State Management**: EditModeManager
- **Score Editing Operations**: ScoreActions

#### songscribe.ui.menu - 833 lines (1.8% of UI)
- **Menu Coordination**: MenuController (main orchestrator)
- **Specialized Menus**: NotesMenu, DurationMenu, ArticulationMenu, BarsMenu, InsertMenu
- **Utilities**: FermataMenuItem, AccidentalMenu, DotMenu, DebugState

#### songscribe.ui.adjustment - 1,647 lines (3.6% of UI)
- **Layout Adjustment**: HorizontalAdjustment, VerticalAdjustment
- **Lyrics Adjustment**: LyricsAdjustment
- **Base**: Adjustment (interface)

#### songscribe.ui.selection - 514 lines (1.1% of UI)
- **Selection Management**: SelectionManager (central coordinator)
- **Selection Types**: NoteSelection, TieContext

#### songscribe.ui.fontchooser - 1,851 lines (4.0% of UI)
- **Font Selection**: FontDialog, FontChooser, FontSelectionModel
- **Font Organization**: FontFamilies, FontFamiliesFactory, FontFamily
- **UI Components**: FamilyPane, StylePane, SizePane, PreviewPane
- **Model Layer**: DefaultFontSelectionModel, FamilyListModel
- **Listeners**: FamilyListSelectionListener, StyleListSelectionListener, SizeListSelectionListener

#### songscribe.ui.graphics - 722 lines (1.6% of UI)
- **High-DPI Support**: HiDPIScaledGraphics, HiDPIScaledImage, RetinaImage

#### songscribe.ui.clipboard - 133 lines (0.3% of UI)
- **Clipboard Operations**: ClipboardManager

#### songscribe.ui.debug - 263 lines (0.6% of UI)
- **Debug Inspector**: DebugInspector (extracted from Score)

#### songscribe.ui (Root) - Constants, Control, Mode, ProfileManager, MusicChangeListener

---

## SEMANTIC BOUNDARIES IDENTIFIED

### Tier 1: Core Domain (songscribe.music)
**Purpose**: Music data model representing compositions
**Key Contracts**: 
- Note subtypes define duration and pitch semantics
- Composition->Line->Note hierarchical structure
- Articulations/Attachments decorate notes with additional information
- BeamCalculator implements complex beam grouping logic
**Vocabulary**: Notes, Rests, Lines, Bars, Tempo, Articulations, Glissando
**Expertise Needed**: Music notation theory, duration calculations, pitch management
**Integration Points**: 
- Reads from songscribe.io (deserialization)
- Writes to songscribe.io (serialization)
- Consumed by UI for rendering and playback
- Consumed by converters for export

### Tier 2: Layout & Rendering (songscribe.ui.layout, songscribe.ui.layout2, songscribe.ui.renderer)
**Purpose**: Calculate positions and draw musical elements to screen/export
**Key Boundaries**:
- **Layout** (6,162 lines): Position calculation - where elements go
- **Layout2** (3,597 lines): NEW modern layout engine (migration in progress)
- **Renderer** (6,557 lines): Element drawing - how elements are rendered
**Key Contracts**:
- Layout produces Bounds, ElementBounds, NoteSpacing information
- Renderers consume layout results and draw via Graphics2D/export APIs
- LayoutEngine orchestrates complete pipeline: NoteColumnBuilder → HorizontalSpacing → VerticalStacking → Justification
**Vocabulary**: Bounds, Staff, BeamGroup, Attachment positioning, justification
**Expertise Needed**: Music engraving algorithms, graphics programming, layout mathematics
**Integration Points**:
- Input from songscribe.music (notes, bars, attachments)
- Output to UI rendering, export formats
- Layout2 bridges to new engine while legacy system remains
**COMPLEXITY HOTSPOT**: Two competing layout systems during migration

### Tier 3: UI Component Hierarchy (songscribe.ui.component)
**Purpose**: User interface components and score display
**Key Boundaries**:
- **Top-level**: MainFrame (window), MainPanel (content coordination)
- **Score Display**: ScorePanel, LinePanel, StaffPanel (rendering surface)
- **Text/Metadata**: TitleComponent, LyricsPanel, TranslationComponent
- **Utilities**: Input handling, focus delegation, toolbar/button components
**Key Contracts**:
- Score class: Central access point for composition data and operations
- Components receive layout/renderer output and compose final UI
- Input handling delegates to edit operations
**Vocabulary**: Panels, Components, Focus, Selection, Text input modes
**Expertise Needed**: Swing UI, event handling, focus management
**Integration Points**:
- Uses songscribe.music (data model)
- Uses songscribe.ui.layout2/layout + renderer (visual output)
- Publishes/receives songscribe.ui.message events

### Tier 4: Event/Message Bus (songscribe.ui.message)
**Purpose**: Decouple components via event-driven architecture
**Key Contracts**:
- All events inherit from Message.kt base class
- MessageCenter coordinates message routing
- Components publish state changes, others subscribe
**Vocabulary**: Message, Event, State change notification
**Key Events**: DurationSelected, LayoutChange, ModeChanged, MusicSelectionChanged, PlaybackState
**Integration Points**:
- Central hub: used by nearly all UI subsystems
- Enables loose coupling between components
**DESIGN PATTERN**: Event Bus/Publisher-Subscriber

### Tier 5: Actions/Commands (songscribe.ui.action)
**Purpose**: User-initiated operations (menu/toolbar/keyboard)
**Key Contracts**:
- All actions extend UIAction (which extends AbstractAction)
- Actions registered centrally in Actions class
- Actions emit messages to perform work
- Supports undo/redo
**Vocabulary**: Action, Command, Menu, Toolbar, Keyboard binding
**Integration Points**:
- Input from songscribe.ui.menu, songscribe.ui.component.toolbar
- Output: Messages to event bus, direct Score manipulation
- Uses songscribe.ui.edit (EditModeManager) for mode-specific behavior

### Tier 6: Dialogs/Windows (songscribe.ui.dialog)
**Purpose**: Modal and non-modal dialog windows for configuration/input
**Key Contracts**:
- StandardDialog base class provides common patterns
- PropertiesStateStore persists dialog settings
- Step-based wizards (Step interface)
**Categories**:
- Settings: Preferences, composition info, line width, key signature
- Export configuration: PDF, MIDI, image resolution
- Edit dialogs: Lyrics, annotations, tempo, beat changes
- Information: About, help, tutorials, updates
**Integration Points**:
- Triggered by actions
- Write settings to songscribe.data.Prefs
- Return data to performing action

### Tier 7: Playback & MIDI (songscribe.ui.playback, songscribe.midi)
**Purpose**: MIDI playback orchestration and sequence generation
**Key Boundaries**:
- **Playback** (1,300 lines): Controller, menu, UI actions
- **MIDI** (327 lines): Sequence building, settings
**Key Contracts**:
- PlaybackController orchestrates: build sequence → send to sequencer → coordinate UI
- MidiController manages MIDI device access
- MidiSequenceBuilder transforms Composition → MIDI Sequence
- PlaybackSettings configure playback parameters
**Vocabulary**: Playback, Tempo, Sequencer, Meta messages, Instrument
**Expertise Needed**: MIDI protocol, sequencing, threading
**Integration Points**:
- Input: songscribe.music.Composition
- Input: songscribe.ui.component.Score (current selection)
- Output: Publishes PlaybackStateChangedMessage, PlaybackTempoChangedMessage
- Receives keyboard/UI input from songscribe.ui.action

### Tier 8: File I/O & Persistence (songscribe.io)
**Purpose**: Composition serialization, file loading/saving, format migration
**Key Classes**:
- **CompositionIO**: Main entry point for load/save
- **XML**: XML utilities
- **FormatMigrator**: Handle version migrations
- **NoteIO, LineIO, AnnotationIO**: Element-specific serialization
**Key Contracts**:
- Read/write Composition to/from files
- Support format versioning and backward compatibility
- Migrate old formats to current schema
**Vocabulary**: Composition, Note, Line, XML, Format version
**Integration Points**:
- Input: songscribe.music (Composition, Note, Line)
- Output: File system
- Used by: songscribe.ui.action (Open/Save), songscribe.converter

### Tier 9: Export/Conversion (songscribe.export, songscribe.converter)
**Purpose**: Export compositions to PDF, SVG, PNG, ABC, MIDI
**Key Boundaries**:
- **Export** (299 lines): Format-specific exporters (PDFExporter, SVGExporter, etc.)
- **Converter** (1,039 lines): CLI tool for batch conversion
**Key Contracts**:
- Exporters: Composition + Settings → File format
- Pipeline: Composition → Layout calculation → Element rendering → Format-specific output
- Converter: Higher-level API for batch operations
**Vocabulary**: Export, Format conversion, PDF, SVG, ABC, Image
**Expertise Needed**: Format specifications (PDF/SVG/ABC), export APIs
**Integration Points**:
- Input: songscribe.music (Composition)
- Uses: songscribe.ui.layout2/layout + songscribe.ui.renderer (to position/draw elements)
- Output: Files in various formats

### Tier 10: Data & Configuration (songscribe.data)
**Purpose**: Persistent preferences, application state, utility data structures
**Key Classes**:
- **Prefs**: User preferences (fonts, page layout, UI state)
- **PageLayoutData**: Page/staff layout configuration
- **Interval, IntervalSet**: Music range utilities (for dynamics, tuplets)
- **FileExtensions**: File filter definitions
**Vocabulary**: Preferences, Page layout, Intervals, Configuration
**Integration Points**:
- Read by: All UI subsystems
- Written by: dialogs, actions
- Not considered "domain" - pure data structures

### Tier 11: Utilities (songscribe.util)
**Purpose**: General-purpose helper functions
**Key Classes**:
- **Log**: Logging utility
- **FileUtils, StringUtils**: String/file operations
- **UIUtils**: Swing/UI helpers, desktop integration
- **GraphicUtils, MyFontUtils**: Graphics and font utilities
**Vocabulary**: Helpers, Utilities, Infrastructure
**Integration Points**: Used across all subsystems

### Tier 12: Font Management (songscribe.font + songscribe.ui.fontchooser)
**Purpose**: Font registration, font selection UI
**Key Classes**:
- **SourceSans3Font**: Font handling
- **FontChooser, FontDialog**: Font selection widget
- **FontFamilies, FontFamily**: Font organization/discovery
**Vocabulary**: Fonts, Font selection, Font families
**Integration Points**:
- Consumed by rendering system
- Provides dialog for user font selection

---

## RECOMMENDED HIERARCHY

### 3-Level Recommended Structure:

**Level 1 - Core Domains**
```
/music          - Domain model (5.5K lines)
/ui             - User interface (45.8K lines) [subdivide below]
/export         - Format export (0.3K lines)
/io             - File persistence (2.5K lines)
/midi           - MIDI generation (0.3K lines)
/converter      - CLI batch conversion (1.0K lines)
/util           - Cross-cutting utilities (2.0K lines)
/data           - Configuration/preferences (0.9K lines)
/font           - Font management (0.1K lines, small)
```

**Level 2 - UI Subdivisions (Required - too large for single node)**
```
ui/
├── component/      - UI components, main frames (8.8K)
├── layout/         - Legacy layout system (6.2K)
├── layout2/        - New layout engine (3.6K)
├── renderer/       - Element drawing (6.6K)
├── action/         - User commands/actions (4.6K)
├── dialog/         - Modal windows (6.2K)
├── message/        - Event bus (1.5K)
├── playback/       - MIDI playback UI (1.3K)
├── edit/           - Edit mode management (0.6K)
├── adjustment/     - Layout adjustments (1.6K)
├── menu/           - Menu structure (0.8K)
├── selection/      - Selection management (0.5K)
├── fontchooser/    - Font selection UI (1.9K)
├── graphics/       - HiDPI support (0.7K)
├── clipboard/      - Clipboard ops (0.1K)
└── debug/          - Debug tools (0.3K)
```

**Level 3 - Further Subdivisions (Selected)**
```
ui/component/
├── score/          - Score display components
├── toolbar/        - Toolbar components
└── [general UI components]

ui/action/
├── [all action types grouped logically]

ui/dialog/
├── [all dialog types grouped logically]

ui/fontchooser/
├── panes/          - Font selection UI panes
└── model/          - Font data models
```

---

## AREAS THAT DON'T NEED THEIR OWN NODE

1. **songscribe.font** (small, specialized) 
   - Could remain at Level 1, or roll up into ui/fontchooser

2. **songscribe.ui.clipboard** (133 lines)
   - Single class ClipboardManager - utility, not enough for own context
   - Consider merging into songscribe.ui

3. **songscribe.ui.debug** (263 lines)
   - Single class DebugInspector - consider rolling into ui/component or util

4. **songscribe.ui.graphics** (722 lines)
   - Utility-only, 4 small classes
   - Consider merging into songscribe.util as graphics utilities

5. **songscribe.ui.selection** (514 lines)
   - Small, specialized; could merge into ui/component/score as it's score-specific

6. **songscribe.converter** (1,039 lines)
   - CLI tool separate from main app; could be own module but not critical for UI context splitting
   - Keep separate if building modular/plugin architecture

---

## TOKEN SIZE ESTIMATES (lines × 0.75)

| Boundary | Files | Lines | Est. Tokens |
|----------|-------|-------|------------|
| **Root/Core** |
| songscribe (root) | 4 | 214 | 161 |
| **Domain Model** |
| music | 45 | 5,493 | 4,120 |
| **UI - Major** |
| ui.component | 30 | 8,803 | 6,602 |
| ui.layout | 30 | 6,162 | 4,622 |
| ui.layout2 | 10 | 3,597 | 2,698 |
| ui.renderer | 18 | 6,557 | 4,918 |
| ui.action | 35 | 4,647 | 3,485 |
| ui.dialog | 20 | 6,221 | 4,666 |
| **UI - Medium** |
| ui.message | 20 | 1,511 | 1,133 |
| ui.adjustment | 4 | 1,647 | 1,235 |
| ui.playback | 11 | 1,300 | 975 |
| ui.fontchooser | 13 | 1,851 | 1,388 |
| ui.menu | 9 | 833 | 625 |
| **UI - Small** |
| ui.edit | 2 | 580 | 435 |
| ui.selection | 3 | 514 | 386 |
| ui.graphics | 4 | 722 | 542 |
| ui.clipboard | 1 | 133 | 100 |
| ui.debug | 1 | 263 | 197 |
| **Export/Conversion** |
| export | 4 | 299 | 224 |
| converter | 7 | 1,039 | 779 |
| **Persistence** |
| io | 8 | 2,509 | 1,882 |
| midi | 2 | 327 | 245 |
| **Data & Utils** |
| data | 11 | 900 | 675 |
| util | 6 | 1,980 | 1,485 |
| font | 2 | ~100 | 75 |
| **TOTAL** | **390** | **59,811** | **44,858** |

---

## KEY INTEGRATION PATTERNS

### 1. **Linear Pipeline: Model → Layout → Render**
```
songscribe.music.Composition/Line/Note
    ↓
songscribe.ui.layout2.LayoutEngine OR songscribe.ui.layout (legacy)
    ↓
songscribe.ui.renderer.*Renderer
    ↓
Java Graphics2D (screen) OR Export formatters
```

### 2. **Event-Driven UI Updates**
```
User Input (songscribe.ui.component or songscribe.ui.action)
    ↓
songscribe.ui.action.UIAction subclass
    ↓
Publishes songscribe.ui.message.Message subclass
    ↓
songscribe.ui.message.MessageCenter broadcasts
    ↓
Subscribers (ui.component, ui.renderer, ui.playback, etc.)
    ↓
UI updates via Swing repaint/refresh
```

### 3. **Playback Pipeline**
```
songscribe.music.Composition
    ↓
songscribe.midi.MidiSequenceBuilder
    ↓
javax.sound.midi.Sequencer (native MIDI)
    ↓
songscribe.ui.playback.PlaybackController monitors
    ↓
Publishes playback state messages
```

### 4. **Export Pipeline**
```
songscribe.music.Composition + songscribe.ui.dialog.ExportDialog config
    ↓
songscribe.export.*Exporter
    ↓
Uses songscribe.ui.layout2 OR songscribe.ui.layout for positioning
    ↓
Uses songscribe.ui.renderer for element drawing
    ↓
Format-specific output (PDF/SVG/PNG/ABC)
```

### 5. **File Persistence**
```
User File Action (Open/Save)
    ↓
songscribe.io.CompositionIO
    ↓
songscribe.music domain objects serialized/deserialized
    ↓
Disk file in SongScribe XML format
```

---

## ARCHITECTURAL CONCERNS & HOTSPOTS

### 1. **Layout System Migration (MAJOR)**
- **Blocker**: Two competing systems running in parallel
  - Legacy: `ui.layout` (6.2K lines, complex)
  - New: `ui.layout2` (3.6K lines, being integrated)
- **Risk**: Both systems used during transition; divergent logic
- **Recommendation**: Complete migration to layout2 to consolidate

### 2. **Score Class (Large Hub)**
- `ui.component.Score` appears to be central coordinator
- Likely accumulating too many responsibilities
- Recent refactoring extracted DebugInspector, EditModeManager
- **Recommendation**: Continue extraction pattern for other responsibilities

### 3. **UI.Component Size (8.8K lines)**
- Largest single subsystem in UI
- Likely contains mixed concerns: frames, panels, components, utilities
- **Recommendation**: Further subdivide beyond current score/ subdirectory

### 4. **Action System Coupling**
- Actions directly manipulate Score and publish messages
- Could benefit from clearer separation of command → effect pattern
- **Recommendation**: Consider Command pattern with centralized executor

### 5. **Dialog Proliferation (6.2K lines, 20 files)**
- Many specialized dialogs
- PropertiesStateStore pattern is good for persistence
- **Recommendation**: Consider grouping by domain (export dialogs, edit dialogs, prefs dialogs)

---

## VOCABULARY/DOMAIN SHIFTS

1. **Music Domain** → Rhythms, Pitches, Articulations, Tempos
2. **Layout Domain** → Bounds, Spacing, Positions, Staff lines, Justification
3. **Rendering Domain** → Graphics2D, Paths, Images, Coordinates
4. **Export Domain** → PDF, SVG, ABC, PNG formats
5. **UI Domain** → Components, Panels, Events, Actions, Dialogs
6. **Playback Domain** → MIDI, Sequencer, Meta messages, Tempo changes
7. **File/Config Domain** → XML, Serialization, Preferences, Versioning

---

## RECOMMENDED CONTEXT NODE STRUCTURE

For Claude Code/Context documentation:

**Must Have (Top Priority)**
- songscribe/music - domain model (4.1K tokens)
- songscribe/ui/component - UI components (6.6K tokens)
- songscribe/ui/layout2 - new layout engine (2.7K tokens)
- songscribe/ui/renderer - element rendering (4.9K tokens)
- songscribe/ui/action - command system (3.5K tokens)
- songscribe/ui/message - event bus (1.1K tokens)

**Should Have (Core functionality)**
- songscribe/ui/dialog - settings/export (4.7K tokens)
- songscribe/ui/playback - MIDI playback (1.0K tokens)
- songscribe/io - file I/O (1.9K tokens)
- songscribe/export - format export (0.2K tokens)

**Nice to Have (Supporting)**
- songscribe/ui/layout - legacy layout (4.6K tokens) - decreasing importance as layout2 matures
- songscribe/ui/fontchooser - font selection (1.4K tokens)
- songscribe/ui/adjustment - layout tuning (1.2K tokens)
- songscribe/util - utilities (1.5K tokens)

**Optional (Smaller scope)**
- songscribe/midi - MIDI building (0.2K tokens)
- songscribe/data - preferences (0.7K tokens)
- songscribe/converter - CLI tool (0.8K tokens)
- songscribe/ui/edit - edit manager (0.4K tokens)
- songscribe/ui/menu - menus (0.6K tokens)
- songscribe/ui/selection - selection mgmt (0.4K tokens)
