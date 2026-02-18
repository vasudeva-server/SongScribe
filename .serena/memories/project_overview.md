# SongScribe Project Overview

## Project Purpose
SongScribe is a music notation application designed to support music composition, particularly works composed by Maestro Sri Chinmoy. It provides a comprehensive editor for creating, editing, and exporting music scores.

## Tech Stack
- **Languages**: Java 25+, Kotlin 2.3.0
- **Build System**: Maven
- **UI Framework**: Swing with FlatLaf (modern look and feel)
- **Version**: 2.0.0
- **License**: GNU General Public License v3

## Key Dependencies
- JFree (PDF, SVG rendering): org.jfree.pdf 2.0.1, org.jfree.svg 5.0.6
- Swing: com.formdev.flatlaf 3.7 (modern theming)
- MIDI: Built-in javax.sound.midi
- HTTP: org.apache.httpcomponents.client5 5.6
- Testing: JUnit 5, Mockito, Kotlin test
- Utilities: Gson 2.11.0, MBAssador 1.3.2 (event bus)

## Main Entry Point
- Class: `songscribe.SongScribe`
- Packaging: JAR with dependencies copied to `libs/` directory

## Java Version Requirement
Java 25+ is required. On macOS, use:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home)
```
