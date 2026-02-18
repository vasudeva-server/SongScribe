# SongScribe Codebase Structure

## Main Source Directory: `src/main/java/songscribe/`

### Core Packages

**songscribe/** - Root package
- `SongScribe.java` - Main entry point
- `Version.java` - Auto-generated version info
- `MidiLister.java` - MIDI device management
- `MusicChangeListener.java` - Event listener interface

**songscribe/ui/** - User interface layer (largest package)
- `Control.java` - UI control definitions
- `Mode.java` - Editor mode management
- `Constants.java` - UI constants
- `ProfileManager.java` - Profile management

**songscribe/ui/component/** - UI components
- `MainFrame.java` - Main application window
- `Score.java` - Score display component
- `ScorePanel.java` - Score rendering panel
- **toolbar/** - Various toolbars (Duration, Articulation, etc.)
- **score/** - Score sub-components (StaffPanel, LineComponent, etc.)
- Dialog and frame components

**songscribe/ui/layout/** - Layout system for score rendering
- Staff, Bar, Line, and note layout structures
- Attachment system for dynamics, articulations, etc.
- Bounds and spacing calculation
- **layout2/** - New layout engine (in migration)
  - `LayoutEngine.java` - New rendering layout engine
  - Spacing and justification calculators

**songscribe/ui/renderer/** - Element rendering
- Renders individual music notation elements
- `BaseElementRenderer.java`, `ElementRenderer.java`
- Specialized renderers for notes, rests, beams, ties, etc.

**songscribe/ui/adjustment/** - Layout adjustment system
- `Adjustment.java` - Base adjustment interface
- Horizontal and vertical adjustment logic

**songscribe/ui/action/** - Action system (menu/toolbar actions)
- Extends UIAction for application operations
- Covers all user actions (save, export, play, edit, etc.)

**songscribe/ui/dialog/** - Dialog windows
- Preferences, composition settings, export dialogs
- Property state storage

**songscribe/ui/menu/** - Menu construction and control
- MenuController coordinates menus
- Specialized menu builders

**songscribe/ui/edit/** - Edit mode management
- `EditModeManager.java` - Edit mode state
- `ScoreActions.java` - Score editing actions

**songscribe/ui/message/** - Message/event system (Kotlin-based)
- `Message.kt` - Base message type
- Various message classes for UI events
- `MessageCenter.kt` - Central message coordinator
- `MessageLogger.java` - Debug logging

**songscribe/ui/selection/** - Selection management
- `SelectionManager.java` - Manages note/element selection
- `NoteSelection.java` - Note-specific selection

**songscribe/ui/playback/** - MIDI playback
- `PlaybackController.java` - Playback orchestration
- `MidiController.java` - MIDI device control
- Playback actions and state

**songscribe/ui/debug/** - Debug tools
- `DebugInspector.java` - Debug interface

**songscribe/music/** - Music data model
- Note types: `Note.java`, `Crotchet.java`, `Quaver.java`, etc.
- `Composition.java` - Top-level music structure
- `Line.java` - Line of music
- Articulations, grace notes, rests, etc.
- `BeamCalculator.java` - Beam grouping logic

**songscribe/converter/** - Format conversion
- Export formats: PDF, SVG, Image, ABC, MIDI
- `PDFConverter.java`, `SVGConverter.java`, `ImageConverter.java`, etc.

**songscribe/export/** - Export operations
- `PDFExporter.java`, `SVGExporter.java`, `ImageExporter.java`, `ABCExporter.java`

**songscribe/io/** - File I/O
- `CompositionIO.java` - Composition serialization
- `XML.java` - XML utilities
- `FormatMigrator.java` - Format version migration

**songscribe/midi/** - MIDI operations
- `MidiSequenceBuilder.java` - Build MIDI sequences
- `PlaybackSettings.java` - Playback configuration

**songscribe/data/** - Data structures
- `Prefs.java` - Preferences storage
- `PageLayoutData.java` - Page layout info
- `Interval.java` - Interval data structures

**songscribe/util/** - Utility functions
- `Log.java` - Logging utility
- `FileUtils.java`, `StringUtils.java`, `UIUtils.java`
- Graphics utilities

**songscribe/font/** - Font management
- `SourceSans3Font.java` - Font handling
- **fontchooser/** - Font selection dialog

## Resource Directories
- `src/main/resources/` - Resource files
- `src/main/config/` - Configuration templates
- `src/main/c/` - Native C code (if any)
- `src/main/plist/` - Platform property lists

## Test Structure: `src/test/java/`
- Mirrors main source structure
- JUnit 5 and Mockito for testing
