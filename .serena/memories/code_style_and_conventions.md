# SongScribe Code Style and Conventions

## Language Mix
- **Java**: Primary language for core logic and UI
- **Kotlin**: Used for type-safe infrastructure
  - Message system (Message.kt, MessageCenter.kt)
  - Action groups (ActionGroup.kt)
  - Increasingly used in new components

## Naming Conventions

### Java Classes
- PascalCase: `MainFrame`, `ScorePanel`, `CompositionIO`
- Inner classes follow parent naming: `Parent$Inner`

### Kotlin Classes
- PascalCase: `Message`, `ActionGroup`

### Methods and Variables
- camelCase: `getScore()`, `setSelection()`, `noteCount`
- Constants: UPPER_SNAKE_CASE (rarely used)

### Package Naming
- Lowercase with dots: `songscribe.ui.component`, `songscribe.music`

## Architecture Patterns

### Message System (Event-Driven)
- Base: `Message.kt` (sealed class)
- All events extend Message
- Examples: `SaveMessage`, `ModeChangedMessage`, `LayoutChangeMessage`
- Coordinator: `MessageCenter.kt` for routing

### Action System (Menu/Toolbar)
- Base: `UIAction` (extends AbstractAction)
- Specialized: `StickyUIAction` for toggle actions
- Groups: `ActionGroup` for related actions
- Registry: `Actions` class maintains action catalog

### Component Hierarchy
- `MainFrame` - Top-level window
- `MainPanel` - Central content area
- `ScorePanel` - Score display
- Various specialized components (TextPanel, LyricsPanel, etc.)

### Layout System (Two Versions)
- **Legacy Layout**: Old system in `songscribe/ui/layout/`
- **Layout2** (New): Modern system in `songscribe/ui/layout2/`
  - More composable and maintainable
  - Gradual migration in progress

### Renderer System
- `BaseElementRenderer` - Base class
- Specific renderers: `NoteRenderer`, `RestRenderer`, `BeamGroupRenderer`, etc.
- `RendererRegistry` - Registry pattern for renderers
- `RenderContext` - Shared rendering state

### Selection System
- `SelectionManager` - Centralized selection tracking
- `NoteSelection` - Specific selection type
- `TieContext` - Context for tie selection

## Code Organization

### Imports
- Standard Java imports first
- Third-party imports grouped
- Internal package imports last
- No wildcard imports (IDE configured)

### Method Organization
- Constructors first
- Public methods
- Protected/package-private methods
- Private methods and inner classes last

### File Naming
- Match class names: `MainFrame.java`
- UI form files: `.form` extension (IntelliJ IDEA forms)
- Kotlin files: `.kt` extension

## Type Hints and Null Safety

### Java
- Use `@Nullable` and `@NotNull` annotations (from JetBrains)
- Explicit null checks
- Optional pattern for return values

### Kotlin
- Nullable types: `Type?`
- Non-null types: `Type` (enforced at compile time)
- Elvis operator: `foo ?: default`

## Documentation

### Javadoc
- Public API classes and methods
- Focus on "why" not "what"
- Example:
  ```java
  /**
   * Calculates beam groups for the given notes.
   * Notes must be consecutive and properly ordered.
   */
  public void calculateBeams(List<Note> notes)
  ```

### Comments
- Explain complex logic
- Mark TODO/FIXME with context
- Avoid redundant comments

## Error Handling

### Exceptions
- Use typed exceptions when possible
- Custom exceptions in specific packages:
  - `CannotUpdateException` - Dialog errors
  - `NewLineException` - IO errors
  - `DoNotShowException` - UI flow control

### Logging
- Use `Log` utility class from `songscribe.util.Log`
- Levels: debug, info, warn, error
- No System.out.println in production code

## Testing

### Test Structure
- Mirrors source structure
- Naming: `ClassNameTest` or `ClassNameTests`
- Use JUnit 5 annotations
- Mockito for mocking

### Test Example
```java
@Test
public void testSaveComposition() {
  // Arrange
  Composition composition = new Composition();
  
  // Act
  composition.save();
  
  // Assert
  assertTrue(composition.isSaved());
}
```

## Git Conventions

### Commit Messages
- Type prefix: `feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `test:`, `chore:`
- Imperative mood: "add feature" not "added feature"
- Under 72 characters
- Reference issue numbers: `fix: resolve issue #123`

### Branch Names
- Feature: `feature/description-with-dashes`
- Bug fix: `fix/description-with-dashes`

## Build and Compilation

### Maven Configuration
- Java/Kotlin mixed compilation
- Kotlin maven plugin runs before Java compiler
- JVM target: 25
- Encoding: UTF-8

### Compiler Settings
- Source: 25
- Target: 25
- Verbose output enabled
- Fork enabled for reliability
