---
paths: src/**/*.{java,kt}
---

## CRITICAL: No Logic Duplication

**NEVER** duplicate code that embodies logic or an algorithm, even if it is a single expression. Before planning or implementing anything, aggressively search for existing code — **across the entire codebase, not just the current file or package** — that can be reused or refactored into a shared helper.

This applies to all granularities:
- A repeated arithmetic expression → extract a named constant or helper method
- A repeated sequence of statements → extract a method with parameters for the varying parts
- A repeated pattern across classes → extract a shared utility or base class

```java
// Bad — the same formula duplicated in two methods
int method1() {
    int bar = 27;
    return bar + getRectHeight() + SOME_CONSTANT * ANOTHER_CONSTANT / 2;
}

int method2() {
    int foo = 7;
    return foo + getRectHeight() + SOME_CONSTANT * ANOTHER_CONSTANT / 2;
}

// Good — the shared formula lives in one place
private static final int OFFSET1 = 27;
private static final int OFFSET2 = 7;

private int doSomeCalculation(int offset) {
    return offset + getRectHeight() + SOME_CONSTANT * ANOTHER_CONSTANT / 2;
}

int method1() {
    return doSomeCalculation(OFFSET1);
}

int method2() {
    return doSomeCalculation(OFFSET2);
}
```

When adding new code that resembles existing code, **refactor the existing code first** so both callers share the extracted helper.

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

**Unit Suffixes (pixels vs staff spaces)**

Any numeric value representing a spatial measurement MUST have a unit suffix:
- `Ss` for staff spaces (the layout unit; 1 ss = distance between adjacent staff lines)
- `Px` for pixels (device pixels; at default scale, 1 ss = 8 px)
- `Sp` for staff positions (integer index along the Y axis; each unit = one half staff-line spacing)

This applies to fields, parameters, local variables, method names, and constants:

```java
// Fields
private double middleLineYSs;
private int xOffsetPx;

// Parameters
void drawStem(double xSs, double topSs, double bottomSs)

// Local variables
double noteXPx = scaleContext.toPixels(noteXSs);

// Methods
double getMiddleLineYSs()
int getMiddleLineYPx()

// Constants
public static final double BEAM_THICKNESS_SS = 0.5;  // 4px
public static final double LYRICS_BASELINE_OFFSET_SS = 1.25;  // 10px
```

The `// NNpx` comment on constants is retained as a convenience for human readers, but the suffix is the authoritative indicator.

Dimensionless values (counts, ratios, pure indices) do not get a suffix:
```java
int staffLineCount;           // a count, not a measurement
double compressionRatio;      // a ratio
```

Note: staff positions are **not** dimensionless — use the `Sp` suffix:
```java
int positionSp;               // a staff-position measurement
```

When a method converts between units, both units should appear in the name:
```java
double toPixels(double ss)    // ScaleContext — input is ss, output is px
double fromPixels(double px)  // ScaleContext — input is px, output is ss
```

Method names and field names can be bulk-renamed using the Serena `rename_symbol` tool, which updates all references automatically. Update javadoc after renaming. Parameters and local variables are scoped to their method body, so renaming them requires no cross-file search: read the method body with `jet_brains_find_symbol(include_body=true)` and edit in place.

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

**Optional vs @Nullable**

Use `Optional` as a return type when the caller must explicitly handle the absent case and the allocation cost is acceptable (e.g., non-hot-path lookups). Avoid `Optional` in tight inner loops or allocation-sensitive rendering paths where `@Nullable` with a null check is cheaper.

```java
// Good: non-hot-path lookup, caller must handle Optional
public Optional<Note> findNoteByName(String name) { ... }

var note = findNoteByName("C4");
if (note.isPresent()) { ... }

// Good: hot path, allocation-sensitive rendering code
@Nullable
private Note getCachedNote(int index) {
    return cache[index];  // null if not cached
}

var note = getCachedNote(0);
if (note != null) { ... }
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
