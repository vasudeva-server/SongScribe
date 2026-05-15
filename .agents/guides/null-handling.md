# Null Handling

## Defaults

The codebase is `@NullMarked`: non-null is the default. Do not annotate non-null
fields, parameters, or returns — and never use `@NotNull`.

Annotate anything that can be null with `@Nullable` (from `org.jspecify.annotations`).
Placement is type-use: after modifiers, immediately before the type.

```java
private static @Nullable String value;
private @Nullable Accidental getAccidental(Line line) { ... }
```

Do not `@Nullable` a required dependency to avoid an error, and do not silently
degrade when something is unexpectedly null. If null is genuinely impossible to
prevent upstream, catch it early and `throw RuntimeError.exit(...)`.

## Deferred-init fields

A field that is non-null in normal use but assigned after construction. Two
patterns, depending on whether a single method does the assignment:

**Pattern 1 — `@Initializer` method.** When one post-construction method is
guaranteed to assign the field before any read, mark that method `@Initializer`
(`com.uber.nullaway.annotations.Initializer`). The field stays a plain non-null
declaration — **no annotation on the field is needed**.

```java
protected Line line;                       // no @Nullable, no @SuppressWarnings

@Initializer
public void setLine(Line line) {
    this.line = line;
    ...
}
```

**Pattern 2 — `@SuppressWarnings("NullAway.Init")` on the field.** When no single
method owns initialization — fields populated by a UI builder, reflection, or a
framework callback NullAway can't see — suppress on each field. Use this only
here; see `BorderPanel`, `ResolutionDialog` for the pattern.

```java
@SuppressWarnings("NullAway.Init")
private JPanel mainPanel;
```

`@SuppressWarnings` rules:
- Never on a constructor.
- Broad `@SuppressWarnings("NullAway")` (not `.Init`) only for a field assigned
  inside a `static { }` block, or on an individual test method — never a test class.
- Any `@SuppressWarnings("NullAway"...)` is a last resort and needs an inline
  comment on the annotation line explaining why.

The `static { }` block case — `ElementType.instance` is the canonical example.
NullAway can't see that the enum's static initializer assigns the field, so the
field carries the broad suppression with an explaining comment:

```java
@SuppressWarnings("NullAway") // instance is initialized in static block, but NullAway doesn't track that
private StaffElement instance;
```

## `requireXxx()` accessors

For a field that is `@Nullable` only because it is set after construction but is
expected to be non-null whenever real code reads it, add a `requireXxx()` helper
that exits fatally on null. Callers get a non-null value and never null-check.

```java
public Score requireScore() {
    var result = score;

    if (result == null) {
        throw RuntimeError.exit("score not initialized");
    }

    return result;
}
```

`MainFrame.requireScore()` is the canonical example.

## Reacting to null

| Situation                          | Do                                              |
|------------------------------------|-------------------------------------------------|
| Fatal / impossible state           | `throw RuntimeError.exit("...")`                |
| Bad argument                       | `throw new IllegalArgumentException(...)`       |
| Bad object state                   | `throw new IllegalStateException(...)`          |
| Optional value with a fallback     | null guard + early return / default value       |

Always `throw RuntimeError.exit(...)` — never call it bare. It returns a
`RuntimeException` so the `throw` marks the code after it unreachable for both
the compiler and NullAway. The two-arg overload `exit(message, cause)` preserves
a triggering exception.

## Prohibited

- `Objects.requireNonNull` / `Objects.requireNonNullElse` — use a null guard.
- `Optional` for fields or parameters. Avoid `Optional` returns too, except
  where a stream or functional API forces it.
