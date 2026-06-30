# SongScribe

SongScribe is a music notation editor designed specifically for streamlined input and editing of the songs written by spiritual Master Sri Chinmoy. It is a cross-platform desktop application written in Java.

## Building SongScribe from Sources

SongScribe requires Java 25.

To compile, run this command from the project root:

```sh
${MAIN_DIR}> ./scripts/compile.sh
```

This prints SUCCESS or FAILURE to indicate the result.

## Running SongScribe

To launch the application, run:

```sh
${MAIN_DIR}> ./scripts/run.sh
```

Options:

- `--log-level=<level>` — Override the log level (trace, debug, info, warn, error)
- `--truncate-log` — Delete the log file before starting so the session begins fresh
- `--profile` — Attach async-profiler in wall-clock mode; the flame graph is written to
  `build/songscribe-profile.html` when you quit (requires: `brew install async-profiler`)

To enable UI debug features (FlatLaf inspector, debug drawing):

```sh
${MAIN_DIR}> DEBUG=1 ./scripts/run.sh
```
