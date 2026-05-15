---
paths: src/**/*.java
---

# Java code style

## Variable names

Use full descriptive names. Single letters are reserved for loop counters (`i`, `j`, `k`).

```java
// Bad
var lc = new LineComponent();

// Good
var lineComponent = new LineComponent();
```

These abbreviations are recognized and may be used as-is: `midi`, `abc`, `pdf`,
`svg`, `dpi`, `pos`, `str`, `msg`, `min`, `max`, `len`, `err`. Ask before
introducing any other abbreviation.

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
