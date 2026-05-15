# Logging

File-based logging via SLF4J backed by Logback.

## Logger declaration

One logger per class, `private static final`, named `LOG`, keyed to the declaring class:

```java
private static final Logger LOG = LoggerFactory.getLogger(MainFrame.class);
```

## Writing log messages

Use SLF4J parameterized messages with `{}` placeholders. Never build the message with
string concatenation (`+`) — it defeats level filtering and is the most common review reject.

```java
LOG.info("Song loaded: {}", file.getName());
LOG.warn("Invalid numeric value for key {}: {}", key, value);
```

To log an exception, pass the `Throwable` as the **last** argument. It does not get a `{}`
placeholder — Logback appends the stack trace automatically:

```java
LOG.error("Could not open '{}'", file.getName(), e);   // message has one {}, e is extra
LOG.warn("MIDI initialization failed", e);             // no {} at all, just the throwable
```

When the stack trace adds no value (expected, well-understood failures), log
`e.getMessage()` as a normal `{}` argument instead of passing the throwable:

```java
LOG.warn("Failed to switch theme: {}", e.getMessage());
```

## Log levels

Pick the level by audience and severity, consistent with existing usage:

- `error` — an operation the user asked for failed (file open/convert/save failed, dialog
  could not be shown). Almost always paired with a `Throwable`.
- `warn` — recoverable or degraded: a fallback was taken, an optional resource was missing,
  initialization of a non-critical subsystem failed.
- `info` — significant lifecycle milestones: app UI ready, song loaded/saved, theme switched.
  Keep these sparse; `info` is the default level shipped to users.
- `debug` / `trace` — developer diagnostics only; off by default. `trace` is used for the
  message-bus firehose. Do not rely on these being visible in normal runs.

## Configuration & log files

- Levels: root level is `${songscribe.log.level:-INFO}`. Override via the
  `--log-level=debug|info|warn|error|trace` flag on `./scripts/run.sh`.
- Configs: `src/main/resources/logback.xml` (file appender, normal runs) and
  `logback-console.xml` (console appender). Per-logger overrides go here — e.g. noisy
  third-party loggers are pinned to `ERROR`.
- Output: `songscribe.log`, rolled at 1 MB to `songscribe.1-7.log` (7 generations).
  Directory is `${songscribe.log.dir}`:
  - macOS — `~/Library/Logs/SongScribe/`
  - Windows — `%APPDATA%\SongScribe\Logs\`
  - Linux — `~/.songscribe/logs/`
