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
    var foo = 7;
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

## CRITICAL: No Nullable Fallbacks for Critical Objects

**NEVER** mark a parameter or dependency `@Nullable` when it is required for correct behavior, and **NEVER** write fallback logic that silently degrades when a critical object is null. Under `@NullMarked`, parameters are non-null by default — leave them unannotated.

If null is truly impossible to prevent further up the call chain, catch the condition **as early as possible** and call `RuntimeError.exit`. A null critical object means the application is in an unstable state — silent degradation only masks the bug and produces incorrect results downstream.

```java
// Bad — fallback hides a bug and produces wrong results
private static double getRightExtentSs(
    Note note,
    @Nullable LayoutResult layoutResult   // nullable "just in case"
) {
    var noteColumn = layoutResult != null ? layoutResult.getNoteColumn(note) : null;

    if (noteColumn != null) {
        return noteColumn.getRightExtentSs();
    }

    return someWrongFallbackValue;  // silently wrong
}

// Good — non-null by default, callers must guarantee it
private static double getRightExtentSs(
    Note note,
    LayoutResult layoutResult
) {
    return layoutResult.getNoteColumn(note).getRightExtentSs();
}
```

---

## Java+Kotlin Code Style

These rules are written for Java, adjust for equivalent concepts in Kotlin.

### File Headers

All Java files must start with @../../../file-header.txt.

### Package & Import Organization

- One package declaration per file
- Imports grouped in order: `java.*` → `javax.*` → third-party → `org.jspecify.annotations`
- Static imports grouped separately at the end
- No wildcard imports
- Example:

```
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;

import org.jspecify.annotations.Nullable;
```

### Naming Conventions

**Classes & Interfaces**

- PascalCase (e.g., `Note`, `StartFrame`, `CannotUpdateException`)
- Enums follow class naming (e.g., `Control`, `NoteType`, `KeyType`)
- Suffixes for specialized classes:
    - `*Exception` for exception classes
    - `*Dialog` for UI dialogs
    - `*Notification` / `*Command` for message classes (see Messaging System below)
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
// Bad — explicit types
int x = scaleContext.toPixels(noteXSs);
double foo = someMethodCall();
ArrayList<Note> noteList = new ArrayList<>();

// Good — var for local variables
var x = scaleContext.toPixels(noteXSs);
var foo = someMethodCall();
var noteList = new ArrayList<Note>();

for (var i = 0; i < 10; i++) {
    // ...
}

var tempDirectory = System.getProperty("java.io.tmpdir");
```

### Nullability (NullAway + jspecify)

The project uses [NullAway](https://github.com/uber/NullAway) for compile-time null safety, with [jspecify](https://jspecify.dev/) annotations.

- Every package has a `package-info.java` with `@NullMarked`, so **all types are non-null by default**.
- There is no `@NotNull` annotation — non-null is the default; do not annotate it.
- Use `@Nullable` from `org.jspecify.annotations` when null is a valid value for a parameter, return type, or field.
- Use `@SuppressWarnings("NullAway")` only as an absolute last resort with an explanation when NO OTHER technique will work.

```java
// Non-null by default — no annotation needed
public Note getNote(String name)

// Nullable must be explicit
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

### No Nested Ternaries

Never nest ternary expressions. Use `if`/`else if`/`else` instead.

```java
// Bad
var result = a ? x : b ? y : z;

// Good
if (a) {
    result = x;
} else if (b) {
    result = y;
} else {
    result = z;
}
```

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
- Default to explaining *why*, not *what* — well-named identifiers already show what the code does. Document *what* only when the algorithm or logic is non-obvious (a multi-step transform, a subtle invariant, a branching rule that isn't apparent from the structure).

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

- All messages extend `songscribe.message.Message`
- Notifications in `songscribe/message/notification/`, commands in `songscribe/message/command/`
- Class names: `*Notification` for state-change events, `*Command` for action requests
- Messages represent UI events or state changes
- Immutable message design preferred

### Handler Methods

#### Mbassador `@Handler` methods

`@Handler` methods always take a subclass of `Message` as their first parameter:
- If the message class has a "Notification" suffix, the method name is the class name minus the suffix
- If the message class has a "Command" suffix, the method name is "handle" + class name minus the suffix
- If the method is a catch-all handler, the method name begins with "on"
- If the same message class must be handled at different priorities, follow the above rules, but append a suffix indicating the purpose, e.g. `musicSelectionDidChangeSaveRestoreActionStates`/`musicSelectionDidChangeReflectSelection`

#### Non-`@Handler` methods

Only methods that are listener callbacks for built-in notification systems (e.g. property change listeners) or are directly invoked by an action should use the "on" prefix, e.g. `onActionPropertyChanged`.
