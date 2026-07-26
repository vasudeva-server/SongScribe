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

## Debug-gated tracing (per-subsystem env var flags)

Some subsystems produce `debug`-level tracing that is too noisy to enable
globally (e.g. beam scoring, tie layout, lyric editing). These are gated
behind a dedicated env var per subsystem, checked through `LogUtils`
(`src/main/java/songscribe/util/LogUtils.java`), rather than relying on the
logger's level alone.

### Adding a new gated subsystem

1. In `LogUtils`, add a `private static final String DEBUG_XXX_ENV_VAR`
   holding the env var name (e.g. `"DEBUG_BEAMS"`).
2. Add a `private static final boolean DEBUG_XXX_ENABLED =
   isEnvVarSet(DEBUG_XXX_ENV_VAR);` — the environment is read once at
   class-init time, not on every log call, and being `static final` lets the
   JIT fold a disabled subsystem's guards away as dead code.
3. Add a `public static boolean isTracingXxx(Logger logger)` that delegates
   to the private `isTracing(logger, DEBUG_XXX_ENABLED)`. Callers only ever
   see the `isTracingXxx` methods — the boolean flags are private so a
   subsystem's flag can't be passed to the wrong check by mistake.

Each `isTracingXxx` method returns `true` only when both the env var was set
(to `1` or `true`, case-insensitive) **and** the logger has `debug` enabled,
so the usual `--log-level=debug` flag on `./scripts/run.sh` still gates
output even when the env var is set.

### Using a gated subsystem at a call site

Guard every call with the matching `isTracingXxx` check before doing any
work to build the log message — this keeps expensive tracing (e.g.
rendering a whole line's state) from running when the subsystem is off:

```java
if (LogUtils.isTracingBeams(LOG)) {
    LOG.debug("beam candidate: {}", candidate);
}
```

For a subsystem with several call sites, add a private `trace` helper local
to the class instead of repeating the guard inline everywhere:

```java
private static void trace(String format, @Nullable Object... args) {
    if (LogUtils.isTracingLyrics(LOG)) {
        LOG.debug(format, args);
    }
}
```

See `LyricEditor.trace`/`LyricEditor.logState` and `Line.trace` for examples,
including a variant that takes the message as a `Supplier<String>` so an
expensive-to-build reason is never assembled when tracing is off.

Run with the gate enabled via the env var, e.g. `DEBUG_LYRICS=1
./scripts/run.sh --log-level=debug`.

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
