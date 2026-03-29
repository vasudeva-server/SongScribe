# SongScribe Development Guide

## Branch Topology

Feature branches are based on `develop`, not `main`. Before any operation that references a base branch (diff, checkout, rebase, PR creation), verify the actual parent with `git log --oneline --graph` or `git merge-base`. Never assume `main`.

## Mandatory Rules

### Null Handling

**NEVER use `Objects.requireNonNull` or `Objects.requireNonNullElse`.** These throw generic `NullPointerException` with no context and bypass graceful recovery. Use context-appropriate null handling instead:

| Situation | Pattern |
|-----------|---------|
| Fatal invariant violation (corrupt state, impossible condition) | `throw RuntimeError.exit("reason")` |
| Programming error (bad argument) | `throw new IllegalArgumentException("reason")` |
| Programming error (bad state) | `throw new IllegalStateException("reason")` |
| Optional value with graceful fallback | Null guard + early return / default value |
| Null-coalescing (`requireNonNullElse`) | Plain `x != null ? x : fallback` or `Optional.ofNullable(x).orElse(fallback)` |

For domain objects that are always required (e.g. a `Score`'s bounding box), add a dedicated `requireXxx()` helper that throws `RuntimeError.exit` with a descriptive message rather than inlining the check.

### Use the Provided Scripts

NEVER invoke `mvn compile`, `mvn test`, `javac`, `kotlinc`, `java -cp`, `mvn exec:java`, or any other raw build/run/test commands. ALWAYS use the provided shell scripts described below.

## Development Workflow

After making code changes, if there is no need to run the application for verification, compile:

```bash
./scripts/compile.sh    # Compile and verify
```

`compile.sh` will output "SUCCESS" or "FAILURE" to indicate whether compilation succeeded. If compilation fails, fix the errors before proceeding.

If you **do** need to run the application:

```bash
# If you have not already compiled
./scripts/crun.sh

# If you have already compiled and just want to run
./scripts/run.sh
```

### All Available Scripts

| Script                 | Purpose                                  |
|------------------------|------------------------------------------|
| `./scripts/compile.sh` | Compile Java/Kotlin sources              |
| `./scripts/run.sh`     | Run the application (MUST compile first) |
| `./scripts/crun.sh`    | Compile and run in one step              |
| `./scripts/test.sh`    | Run tests (see examples below)           |

`run.sh` and `crun.sh` accept these options:

| Option | Effect |
|---|---|
| `--log-level=debug\|info\|warn\|error\|trace` | Override the log level for this run |
| `--truncate-log` | Truncate the log file before logging begins |

To enable UI debug features (FlatLaf inspector, debug drawing), set `DEBUG=1` manually:

```bash
DEBUG=1 ./scripts/run.sh
```

### Running Tests

ALWAYS use `./scripts/test.sh` to run tests. NEVER invoke `mvn test` directly.

**IMPORTANT:** Never run e2e tests without waiting for the user's approval; they will take control of the user's mouse and keyboard, and may cause unintended consequences if run without the user's knowledge.

To determine the pass/fail status of tests, run `test.sh`. If there are failures, run `test.sh --verbose` to determine which test caused a failure.

```bash
./scripts/test.sh                                    # Run all tests
./scripts/test.sh e2e                                # Run only e2e tests
./scripts/test.sh unit                               # Run only unit tests (excludes e2e)
./scripts/test.sh --debug e2e                        # Run e2e tests, pausing between each test
./scripts/test.sh --slow GlissandoTest               # Run with 1s pause between UI actions
./scripts/test.sh --fail-fast e2e                    # Stop after the first test failure
./scripts/test.sh --verbose e2e                      # Show tree-style output for each test
./scripts/test.sh SMuFLMetadataTest                  # Run specific test class
./scripts/test.sh BeamingTest.testFlipStemDirection  # Run specific test method
./scripts/test.sh 'GraceNoteTest$EdgeCases'          # Run all tests in a @Nested inner class (single quotes prevent shell $ expansion)
./scripts/test.sh 'GraceNoteTest$EdgeCases.testFoo'  # Run specific method in a @Nested inner class
./scripts/test.sh SMuFLMetadataTest BeamingTest      # Multiple classes (space-separated)
./scripts/test.sh -Dtest=*Test                       # Run with Maven pattern
```

### Test Failure Investigation

**IMPORTANT:** If tests fail:
- NEVER assume the failures are pre-existing just because you didn't touch the failing code.
- Always investigate test failures:
  - Always read the full stack trace, including the `Caused by:` section — the root cause is almost always there. If the output was truncated, ask the user to capture the full output rather than trying to deduce the cause through code analysis. 
  - If the bug is not immediately apparent from the stack trace, ask the user for permission to run the failing test with the `--debug` option.
- Fix tests failures before proceeding with new changes.

### Writing Tests

Before writing tests, read the appropriate guide (these are NOT auto-loaded):

- **Common conventions:** [testing-common.md](../.claude/testing-common.md)
- **Unit tests:** [testing-unit.md](../.claude/testing-unit.md) — mocking patterns, ReflectionTestHelper, MainFrame singleton mocking
- **E2E tests:** [testing-e2e.md](../.claude/testing-e2e.md) — user simulation helpers, coordinate conversion, layout sync

### Build Full Project

For a full Maven build (e.g. to produce a JAR):

```bash
mvn clean package
```

Then run from JAR:

```bash
java -jar target/SongScribe-*.jar
```

## Java Version

The project requires Java 25+. To set `JAVA_HOME` correctly:

```bash
source ./scripts/set-java-home.sh
```

## Generated Files

Do not edit files in `target/generated-sources/`. Key generated files:
- `Strings.java` — generated from `strings.properties` (see [strings rules](strings.md))
- `Version.java` — generated from `pom.xml` version during build

## Debugging

When debugging UI interactions and state management issues, favor adding `System.out.println` debug statements over static code analysis. UI state flows through event handlers, message buses, and callbacks in ways that are difficult to reason about statically. Running the application with debug prints and reading the actual execution trace is faster and more reliable than trying to mentally simulate the flow.

Always remove debug prints after the issue is resolved.
