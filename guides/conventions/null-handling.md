# Null handling

## Annotations

- Annotate only what can be null, with `org.jspecify.annotations.Nullable`, because the codebase is
  `@NullMarked`. Never annotate a non-null field, parameter or return, and never use `@NotNull`.
- Place `@Nullable` as a type-use annotation, after the modifiers and immediately before the type:

  ```java
  private static @Nullable String value;
  ```

- Do not mark a required dependency `@Nullable` to silence an error, and do not degrade silently
  when something is unexpectedly null. Catch it early and `throw RuntimeError.exit(...)`.

## Deferred-init fields

- Mark the one method that assigns a field after construction `@Initializer`
  (`com.uber.nullaway.annotations.Initializer`) when it is guaranteed to run before any read, and
  leave the field a plain non-null declaration.
- Put `@SuppressWarnings("NullAway.Init")` on each field that no single method initializes, such as
  one populated by a UI builder, reflection or a framework callback, and use it only there.
- Never put `@SuppressWarnings` on a constructor.
- Put the broad `@SuppressWarnings("NullAway")` only on a field assigned inside a `static { }`
  block, or on an individual test method, never on a test class.
- Give every `@SuppressWarnings("NullAway"...)` an inline comment on the annotation line that says
  why, because it is a last resort.
- Give a field that is `@Nullable` only because it is set after construction a `requireXxx()`
  accessor that throws `RuntimeError.exit` on null, so that callers never null-check it.

## Reacting to null

- Throw `RuntimeError.exit(...)` on a fatal or impossible state.
- Throw `IllegalArgumentException` on a bad argument.
- Throw `IllegalStateException` on a bad object state.
- Guard with an early return or a default for an optional value that has a fallback.
- Never use `Objects.requireNonNull` or `Objects.requireNonNullElse`; use a null guard.
- Never use `Optional` for a field or parameter, and avoid it as a return except where a stream or
  functional API forces it.
