# SongScribe Development Guide

This document contains build, compilation, and run instructions for SongScribe development.

## Building

### Compile Java and Kotlin Sources

```bash
./scripts/compile.sh
```

This ensures the correct compile invocation is used.

### Build Full Project

```bash
mvn clean package
```

## Running

DO NOT attempt to use the `run.sh` or `run-debug.sh` scripts when debug logging has been added during a bug fix. Request the user to run the application and provide logs instead.

### From Compiled Classes

After compiling with Maven:

Run the application in production mode:
```bash
./scripts/run.sh
```

Run the application in development mode with additional logging:
```bash
./scripts/run-debug.sh
```

### From JAR

After building with `mvn clean package`:

```bash
java -jar target/SongScribe-*.jar
```

## Development Workflow

### Quick Compile and Run

For rapid iteration during development:

```bash
./scripts/compile.sh && ./scripts/run-debug.sh
```

### Rebuild After Changes

```bash
./scripts/compile.sh
```

## Maven Tips

### View Dependencies

```bash
mvn dependency:tree
```

## Java Version

The project requires Java 25+. For macOS, the `java_home` utility can locate it:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home)
```

This command finds Java 25 with a fallback to the default installed JDK.

## Common Issues

### Font Registration Warnings

Log messages like "Could not register font: *.ttf" are non-fatal. The application will continue running with fallback fonts.

### Module Access Warnings

Some Java 25+ features may generate module access warnings. These are typically non-fatal and the application will continue running normally.
