---
paths: src/**/*.java
---

# Java code style

## Import Order

Group imports in this order, with a blank line between each group present
(`java.*` and `javax.*` count as one group with no blank line between them):

1. `import module` statements
2. `java.*` / `javax.*`
3. All other third-party packages (one group, no further splitting)
4. `songscribe.*`
5. `import static` statements

This matches the IDE's configured import layout, so don't manually reorder
existing imports — let the IDE do that. Just place new imports in this order.

## Variables

Always use `var` instead of explicit types, unless necessary for generic type inference. Use full descriptive names. Single letters are reserved for loop counters (`i`, `j`, `k`). Be sure not to shadow field names with local variables.

```java
// Bad
var lc = new LineComponent();
double dx = lc.getXSs();

// Good
var lineComponent = new LineComponent();
var dx = lineComponent.getXSs();
```

These abbreviations are recognized and may be used as-is: `midi`, `abc`, `pdf`,
`svg`, `dpi`, `pos`, `str`, `msg`, `min`, `max`, `len`, `err`. Ask before
introducing any other abbreviation.

## Braces

Always use braces for `if`, `else`, `for`, `while`, and `do` statements, even if the body is a single statement. This improves readability and  prevents bugs when adding new statements later.

```java// Bad — no braces
if (condition) doSomething();
    
// Good — braces
if (condition) {
    doSomething();
}
```

## Control structure vertical spacing

Separate control structures from surrounding lines with a blank line to improve readability, unless at the beginning or end of a containing block. This includes `if`, `else if`, `else`, `for`, `while`, and `do` statements.

```java
// Bad — no blank lines around control structures
var foo = 'foo';
if (condition) {
    doSomething();
}
doSomethingElse();

// Good — blank lines around control structures
var foo = 'foo';

if (condition) {
    doSomething();
}

doSomethingElse();

void doSomething() {
    if (condition) {
        doSomethingElse();
    }
}
```

## No nested ternaries

A single ternary is fine. Never nest a ternary inside another — use
`if` / `else if` / `else` instead.

```java
// Bad — nested ternary
var label = count == 0 ? "none" : count == 1 ? "one" : "many";

// OK — single ternary
var label = count == 0 ? "none" : "some";

// Good — branching logic as if/else if/else
String label;
if (count == 0) {
    label = "none";
} else if (count == 1) {
    label = "one";
} else {
    label = "many";
}
```

## Don't repeat accessor chains

If an accessor or accessor chain is evaluated more than once in a method, assign
it to a local variable and reuse that. This avoids redundant calls and makes the
code easier to read.

```java
// Bad — getCurrentLine() chain repeated three times
score.getSong().getCurrentLine().setHeightSs(heightSs);
var type = score.getSong().getCurrentLine().getSelectedElement().getType();
var height = score.getSong().getCurrentLine().getHeightSs();

// Good — evaluate the shared chain once
var line = score.getSong().getCurrentLine();
line.setHeightSs(heightSs);
var type = line.getSelectedElement().getType();
var height = line.getHeightSs();
```

Assign each intermediate result that is reused, not just the final value:

```java
var foo = component.getFoo();
var bar = foo.getBar();
var baz = foo.getBaz();
```

A chain used exactly once needs no local — don't introduce a variable just to
name an intermediate step.

## Boolean "type" parameters

Avoid using boolean parameters that represent a type, mode or state. Use an enum instead.

```java
// Bad — boolean parameter represents a mode
public void setMode(boolean isEditMode);

// Good - use an enum
public void setMode(Mode mode);
```

## Initialize static fields

Explicityly initialize static fields whenever possible, do not rely on the compiler default.

```java
private static int lastMouseScreenXPx = 0;
private static @Nullable Consumer<? super String> publicationErrorProbe = null;
private static boolean overflowWarningShown = false;
```

## Javadoc References to Constants

Never write a named constant's raw literal value in a Javadoc comment — the doc silently rots the moment the constant changes.

Prefer `{@value}`, which inlines the real value at render time, so the reader still sees the number without it being duplicated in the source:

- Same class: `{@value #MAX_ZOOM_PERCENT}`
- Another class: `{@value ViewScale#MAX_ZOOM_PERCENT}`

Use `{@link ClassName#CONSTANT_NAME}` when the prose refers to the constant *as a thing* rather than quoting its value ("clamped by {@link ViewScale#MAX_ZOOM_PERCENT}"), or when `{@value}` is not legal.

`{@value}` only works on a *constant variable* — `static final` of a primitive or `String` type, initialized with a compile-time constant expression. It does not work on `static final Color`, `Dimension`, arrays, enums, or anything computed at runtime; those must use `{@link}`.

Exception: illustrating an example calculation/formula, where literals are needed to show the math — reference the constant elsewhere in the same doc if possible.

## Contracts in Javadoc

**Which clause goes in which tag, and how much contract a method earns, are in
[Contracts](../guides/contracts.md).** Read it before writing one. Only the
Javadoc mechanics are here.

`invariant`, `effects` and `log` are custom tags, alongside the standard ones.
Nothing in the build renders or validates Javadoc, so a malformed tag fails
silently; the IDE knows the three through its additional-Javadoc-tags setting
(`ADDITIONAL_TAGS` in `.idea/inspectionProfiles/Project_Default.xml`).

Class and package invariants go on the class or `package-info.java` Javadoc, in
prose, never repeated on every method that relies on them.

## Signature Rules for Contracts

From the global rules (`~/.claude/rules/development.md`), in Java terms:

- **More than four parameters: a `record` parameter object is required.** Not
  a style preference — past four params a call site cannot be read against its
  signature without checking the declaration.
- **Two or more adjacent parameters of the same type: a `record` parameter
  object**, regardless of the total count, when a call site could transpose them
  without the compiler catching it.
- **A boolean parameter that selects a mode or type: an enum, not a
  `boolean`.** See [Boolean "type" parameters](#boolean-type-parameters) above;
  this is the same rule restated for contract signatures.

## Spelling

Use the American spelling "center" (and its variants: "centered", "centering") in comments and identifiers, not the British spelling "centre" and its variants.

## Generated Files

Never edit files in `build/generated-sources/`.
