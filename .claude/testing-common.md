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

## Assertions

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
```

## Running Tests

Always use `./scripts/test.sh`. Never invoke `mvn test` directly.

**IMPORTANT:** Always capture the full test output (do NOT pipe through `tail` or `head`). You need to see the complete output including failure details, stack traces, and assertion messages — not just the summary at the end.

```bash
./scripts/test.sh                                    # Run all tests
./scripts/test.sh e2e                                # Run only e2e tests
./scripts/test.sh unit                               # Run only unit tests (excludes e2e)
./scripts/test.sh --debug e2e                        # Run e2e tests, pausing between each test
./scripts/test.sh --slow GlissandoTest               # Run with 1s pause between UI actions
./scripts/test.sh --fail-fast e2e                    # Stop after the first test failure
./scripts/test.sh SMuFLMetadataTest                  # Run specific test class (multiple space-separated classes and/or methods allowed)
./scripts/test.sh BeamingTest.testFlipStemDirection  # Run specific test method (multiple space-separated methods and/or classes allowed)
./scripts/test.sh 'GraceNoteTest$EdgeCases'          # Run all tests in a @Nested inner class (single quotes prevent shell $ expansion)
./scripts/test.sh 'GraceNoteTest$EdgeCases.testFoo'  # Run specific method in a @Nested inner class
./scripts/test.sh -Dtest=*Test                       # Run with Maven pattern
```
