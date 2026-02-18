# SongScribe Development Guide

This document contains build, compilation, and run instructions for SongScribe development.

## Building

### Compile Java and Kotlin Sources

```bash
./scripts/compile.sh
```

This ensures Java 21 is used, with a fallback to the default JDK if Java 21 is not available.

### Build Full Project

```bash
mvn clean package
```

## Running

### From Compiled Classes

After compiling with Maven, run the application:

```bash
./scripts/run.sh
```

### From JAR

After building with `mvn clean package`:

```bash
java --enable-preview -XX:+UnlockPreviewFeatures -jar target/SongScribe-*.jar
```

## Development Workflow

### Quick Compile and Run

For rapid iteration during development:

```bash
./scripts/compile.sh && ./scripts/run.sh
```

### Rebuild After Changes

```bash
./scripts/compile.sh
```

## Maven Tips

### Skip gmaven Plugin Issues

If you encounter gmaven plugin errors, use the compile script which uses direct compiler goals with Java 21:

```bash
./scripts/compile.sh
```

### View Dependencies

```bash
mvn dependency:tree
```

## Java Version

The project requires Java 21+. For macOS, the `java_home` utility can locate it:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)
```

This command finds Java 21 with a fallback to the default installed JDK.

## Code Style

Refer to `CLAUDE.md` for code style guidelines, naming conventions, and project-specific patterns.

## Common Issues

### Font Registration Warnings

Log messages like "Could not register font: *.ttf" are non-fatal. The application will continue running with fallback fonts.

### Module Access Warnings

Java 17+ preview features may generate module access warnings. These are normal and can be suppressed with the `--enable-preview` flag shown above.
