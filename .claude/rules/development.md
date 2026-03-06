# SongScribe Development Guide

## Mandatory Rules

### Use the Provided Scripts

NEVER invoke `mvn compile`, `mvn test`, `javac`, `kotlinc`, `java -cp`, `mvn exec:java`, or any other raw build/run/test commands. ALWAYS use the provided shell scripts described below.

### Debug Scripts

The `-debug` script variants (`crun-debug.sh`, `run-debug.sh`) set the `DEBUG` environment variable. This only affects code that explicitly checks for it (debug drawing, `Log.fine()` output, etc.).

**NEVER use `-debug` variants unless the user has requested debug output OR `DEBUG`-dependent output is needed to diagnose a problem.** The default is ALWAYS the non-debug variant.

## Development Workflow

After making code changes, compile first to verify, then run separately:

```bash
./scripts/compile.sh    # Compile and verify
./scripts/run.sh        # Run the application
```

If you have not already compiled and want to compile and run in one step:

```bash
./scripts/crun.sh
```

### All Available Scripts

| Script | Purpose |
|---|---|
| `./scripts/compile.sh` | Compile Java/Kotlin sources |
| `./scripts/run.sh` | Run the application (MUST compile first) |
| `./scripts/run-debug.sh` | Run with DEBUG env var (use only when debug output is needed) |
| `./scripts/crun.sh` | Compile and run in one step |
| `./scripts/crun-debug.sh` | Compile and run with DEBUG env var (use only when debug output is needed) |
| `./scripts/test.sh` | Run tests (see examples below) |

### Running Tests

ALWAYS use `./scripts/test.sh` to run tests. NEVER invoke `mvn test` directly.

```bash
./scripts/test.sh                     # Run all tests
./scripts/test.sh e2e                 # Run only e2e tests
./scripts/test.sh unit                # Run only unit tests (excludes e2e)
./scripts/test.sh --debug e2e         # Run e2e tests, pausing between each test
./scripts/test.sh SMuFLMetadataTest   # Run specific test class
./scripts/test.sh -Dtest=*Test        # Run with Maven pattern
```

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

## GitHub Issue Workflow

- Use `refs #<number>` in commit messages to reference an issue without closing it.
- Only use `Closes #<number>` when the user explicitly says to close the issue as part of a commit.
- Use `gh` CLI for all GitHub operations (issues, PRs, etc.).

## Common Issues

### Font Registration Warnings

Log messages like "Could not register font: *.ttf" are non-fatal. The application will continue running with fallback fonts.

### Module Access Warnings

Some Java 25+ features may generate module access warnings. These are typically non-fatal and the application will continue running normally.
