---
paths: src/**/*.{java,kt}
---

## Java+Kotlin Code Style

These rules are written for Java, adjust for equivalent concepts in Kotlin.

### File Headers

All Java files must start with @../../../file-header.txt.

### Package & Import Organization

- One package declaration per file
- Imports grouped in order: `java.*` → `javax.*` → third-party → `org.jetbrains.annotations`
- Static imports grouped separately at the end
- No wildcard imports
- Example:

```
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
```

### Naming Conventions

**Classes & Interfaces**

- PascalCase (e.g., `Note`, `StartFrame`, `CannotUpdateException`)
- Enums follow class naming (e.g., `Control`, `NoteType`, `KeyType`)
- Suffixes for specialized classes:
    - `*Exception` for exception classes
    - `*Dialog` for UI dialogs
    - `*Message` for event/message classes
    - `*Toolbar` for toolbar components
    - `*Button` for custom button components

**Methods & Fields**

- camelCase for methods and variables (e.g., `getDescription()`, `isValid`, `glissando`)
- Private fields: `private Type fieldName`
- Constants: CONSTANT_CASE (e.g., `HOT_SPOT`, `NORMAL_IMAGE_WIDTH`, `MIDI_PITCHES`)
- Boolean fields/parameters: prefix with `is`, `has`, `can`, or `should`

**Constants**

- Use `public static final` for class constants
- Document purpose when not self-evident
- Example:

```
public static final Point HOT_SPOT = new Point(5, 27);
public static final int NORMAL_IMAGE_WIDTH = 18;
```

**Variables**

- Do **NOT** use explicit local variable types when type is obvious from context, use `var`
- Use descriptive names, avoid single-letter names except in loops
- Avoid abbreviations unless widely understood
- Example:

```
var noteList = new ArrayList<Note>();

for (var i = 0; i < 10; i++) {
    // ...
}

var tempDirectory = System.getProperty("java.io.tmpdir");
```

### Annotations

- Use `@NotNull` and `@Nullable` from `org.jetbrains.annotations` for nullability clarity
- Apply to method parameters, return types, and fields where nullability matters
- Use `@SuppressWarnings` sparingly, with explanation when necessary
- Example:

```
public Note getNote(@NotNull String name)

@Nullable
public String getOptionalValue()
```

### Class Structure Order

1. File header (license)
2. Package declaration
3. Imports (grouped as described above)
4. Class/interface declaration
5. Constants (public static final)
6. Static initializers (if needed)
7. Instance fields
8. Constructors
9. Public methods (organized logically, getters together, operations together)
10. Protected/private methods
11. Inner classes

### Formatting

**Indentation**

- Use spaces (not tabs)
- 4 spaces per indentation level
- Continuation lines: 8 spaces (double indent)

**Line Length**

- Keep lines under 120 characters when practical
- Break long lines at logical points (after operators, before method names)

**Braces**

- Java style (opening brace on same line):

```
public void method() {
    if (condition) {
        doSomething();
    }
}
```

**Spacing**

- One space around binary operators: `x = y + 1`
- No space between method name and parentheses: `method()` not `method ()`
- No space inside parentheses: `method(param)` not `method( param )`
- One blank line between methods
- One blank line surrounding control blocks (if, for, while), unless at the start/end of a block
- control structures (if, for, while) always followed by braces, even for single statements

```
// Good:
var condition = someMethodCall();

if (condition) {
    doSomething();
}

// Bad:
var condition=someMethodCall();  // No blank line before if!
if(condition) doSomething(); // No braces!
```

- Two blank lines between logically separate sections

**Comments**

- Use `//` for single-line comments
- Use `/* */` for block comments and file headers
- Use `/** */` for JavaDoc (public APIs only)
- TODO comments: `// TODO: Description of work needed`
- Explain *why*, not *what* (code shows what it does)

### Special Cases

**Arrays & Collections**

- Multi-line array initialization: indent contents, one element per line when >3 items

```
public static final Rectangle[] REAL_NATURAL_FLAT_SHARP_RECT =
    new Rectangle[]{
        new Rectangle(0, 17, 6, 22),
        new Rectangle(0, 15, 7, 19),
        new Rectangle(0, 17, 8, 22),
        new Rectangle(0, 23, 9, 10),
    };
```

**Method Chaining**

- Break chains across lines for readability
- Indent continuation lines

```
builder
    .setName("value")
    .setDescription("desc")
    .build();
```

---

## General Conventions

### Code Quality Principles

- **Single Responsibility**: Each class/method has one primary purpose
- **Clarity Over Cleverness**: Readable code > compact code
- **DRY**: Extract duplicated logic into helper methods
- **No Magic Numbers**: Use named constants for non-obvious values

### Error Handling

- Use specific exception types (e.g., `IllegalArgumentException`, `FileNotFoundException`)
- Create custom exceptions for domain-specific errors
- Always provide meaningful error messages
- Never silently swallow exceptions

### Testing

- Test files mirror source structure: `src/test/java/...` mirrors `src/main/java/...`
- Test class names: `*Test` (e.g., `TupletIntervalDataTest`)
- Test method names: `test*` + what-and-expected (e.g., `testValidIntervalReturnsTrue`)

---

## UI Component Guidelines

### Component Naming

- Custom Swing components use descriptive names:
    - `StickyToggleButton`: Toggle that maintains state
    - `PopupButton`: Button that triggers popups
    - `NumericTextField`: Text field for numeric input
    - `BorderPanel`: Panel with custom border

### Component Organization

- UI components live under `src/main/java/songscribe/ui/component/`
- Specialized components in subdirectories (e.g., `toolbar/`, `dialog/`)
- Keep components focused on single responsibility

### Messaging System

- Event messages located in `src/main/java/songscribe/ui/message/`
- Class names: `*Message` (e.g., `DurationSelectedMessage`, `BarSelectedMessage`)
- Messages represent UI events or state changes
- Immutable message design preferred

---

## Data & Model

### Constants Organization

- Enums for types: `NoteType`, `KeyType`, `Control`
- Constants grouped with related functionality
- Static initializers for complex constant setups

### Immutability

- Consider `final` for fields that shouldn't change
- Use value objects for domain concepts
- Defensive copying when returning mutable collections
