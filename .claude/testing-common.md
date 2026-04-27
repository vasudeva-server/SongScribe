# Testing Common Conventions

## Frameworks

- **JUnit 5** (Jupiter) with deterministic ordering (class name, then method name)
- **AssertJ** for fluent assertions (`assertThat(...).isEqualTo(...)`)
- **Mockito** for mocking (`mock()`, `mockStatic()`, `when()`, `verify()`)
- **AssertJ Swing** for E2E GUI testing (Robot, FrameFixture)

## Base Classes

**`UnitTest`** (`src/test/java/songscribe/UnitTest.java`) — extend for all unit tests. Suppresses modal error dialogs via `@BeforeAll`.

**`E2ETest`** (`src/test/java/songscribe/e2e/E2ETest.java`) — extend for E2E tests. See `.claude/testing-e2e.md`.

## Naming Conventions

- Test classes: `*Test.java`, mirror the source package structure
- Test methods: `test*` prefix describing condition and expected outcome (e.g., `testApplyToNoteAppliesAccidental`)
- Use `@Nested` inner classes to group related tests — but only when there are multiple related tests to group. Do not wrap a single test method in a `@Nested` class.
- Alphabetize top-level test methods and `@Nested` class declarations within a test class. Alphabetize test methods within each `@Nested` class as well.

## Testability Over Encapsulation

When a private method is a self-contained unit worth testing directly, widen its visibility to package-private rather than trying to test it indirectly through public methods that require complex setup. Reconstructing application state through mocks, reflection, or elaborate wiring just to reach a private method makes tests fragile and hard to understand. The same goes for private constants, widen access instead of duplicating them. A simple visibility change is always preferable.

## Assertions

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
```

## Running Tests

Always use `./scripts/test.sh`. Never invoke `mvn test` directly.
