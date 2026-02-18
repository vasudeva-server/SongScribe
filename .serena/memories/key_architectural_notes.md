# SongScribe Key Architectural Notes

## Current State and Ongoing Work

### Layout2 Migration (In Progress)
- New modern layout engine in `songscribe/ui/layout2/`
- More maintainable and composable design
- Currently being integrated alongside legacy layout system
- Recent work: InsertionSpacingCalculator bridge utility

### Score Infrastructure Extraction
- Recent refactoring extracted Score-related functionality
- DebugInspector extracted from Score class
- EditModeManager handles edit state
- Export operations moved to dedicated package

## Important Design Patterns

### 1. Message System (Event Bus Pattern)
- All UI events inherit from `Message.kt`
- `MessageCenter.kt` coordinates routing
- Decouples components from direct dependencies
- Examples: `SaveMessage`, `LayoutChangeMessage`, `ModeChangedMessage`

### 2. Action System (Command Pattern)
- `UIAction` base class extends AbstractAction
- Centrally registered in `Actions` class
- Used by menus and toolbars
- Provides undo/redo capability

### 3. Renderer Architecture (Strategy Pattern)
- `BaseElementRenderer` provides common functionality
- Specific renderers for each element type
- `RendererRegistry` manages renderer instances
- Enables flexible rendering logic

### 4. Layout Calculation
- Separation of concerns:
  - `NoteColumnBuilder` - Column structure
  - `HorizontalSpacingCalculator` - Horizontal layout
  - `VerticalStackingCalculator` - Vertical arrangement
  - `LineJustificationCalculator` - Line justification

## Music Data Model

### Composition -> Line -> Bar -> Note Structure
- `Composition` - Top-level document
- `Line` - Musical staff line
- Implicit bars from note grouping
- `Note` subclasses: `Crotchet`, `Quaver`, `Demisemiquaver`, etc.
- `NonNote` for rests and other elements

### Attachments System
- Elements can have attachments (dynamics, articulations, etc.)
- `Attachment` base class
- Specialized: `TempoAttachment`, `FermataAttachment`, etc.
- Layout system positions attachments relative to notes

## UI Component Hierarchy

### MainFrame
- Main application window
- Delegates to MainPanel
- Handles window-level events

### MainPanel
- Coordinates toolbars, menus, score display
- MainToolbarPanel - Toolbar management
- ScorePanel - Score display area

### ScorePanel
- Renders the actual music notation
- LinePanel - Individual staff lines
- StaffPanel - Rendering surface
- TextPanel - Text input areas

## Selection System

### SelectionManager
- Tracks current selection state
- Broadcasts selection changed messages
- Handles multiple selection types

### TieContext
- Special handling for tie selection
- Maintains context about tied notes

## MIDI and Playback

### PlaybackController
- Orchestrates playback sequence
- Manages playback state
- Coordinates with UI

### MidiController
- Low-level MIDI device management
- Sequence building and playback
- Tempo changes during playback

## Export System

### Exporter Classes
- `PDFExporter` - PDF document generation
- `SVGExporter` - SVG vector graphics
- `ImageExporter` - PNG/JPG images
- `ABCExporter` - ABC notation format

### Conversion Flow
1. Composition -> Internal representation
2. Layout calculation
3. Element rendering
4. Format-specific output

## Key Dependencies

### Message Bus
- MBAssador library for event dispatching
- Thread-safe message delivery

### Rendering
- JFree libraries for PDF/SVG output
- Java 2D Graphics for screen rendering
- FlatLaf for modern UI theming

### Persistence
- Gson for JSON serialization
- Custom XML utilities for composition files

## Known Limitations and Workarounds

### Font Registration
- Non-fatal warnings: "Could not register font: *.ttf"
- Application continues with fallback fonts

### Module Access Warnings
- Java 25+ may generate module access warnings
- Typically non-fatal
- Application continues running normally

## Testing Approach

### Unit Tests
- Focus on music model and layout logic
- Mockito for mocking dependencies
- JUnit 5 framework

### Integration Tests
- Minimal (mostly manual UI testing)
- Some converter tests

### Manual Testing
- Visual verification of score rendering
- Playback testing
- Export validation

## Performance Considerations

### Layout Calculations
- Incremental updates preferred
- Caching of layout results where possible
- Beam calculation complexity O(n log n)

### Rendering
- Double-buffering for smooth display
- HiDPI support via scaling utilities
- Lazy initialization of components

## Future Architecture Goals

- Complete migration to layout2 system
- Further component extraction
- Enhanced testing infrastructure
- Performance optimization for large compositions
