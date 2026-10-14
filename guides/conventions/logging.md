# Logging

## Writing messages

- Declare one `private static final Logger LOG` per class, from `LoggerFactory.getLogger` with the
  declaring class.
- Write an SLF4J message with `{}` placeholders and never concatenate with `+`, because
  concatenation defeats level filtering.
- Pass a `Throwable` as the last argument with no placeholder of its own, because Logback appends
  the stack trace:

  ```java
  LOG.error("Could not open '{}'", file.getName(), e);
  ```

- Log `e.getMessage()` as an ordinary `{}` argument instead of the throwable when the stack trace
  adds nothing, as for an expected, well-understood failure.

## Levels

- Log at `error` when an operation the user asked for fails, and pass the `Throwable`.
- Log at `warn` for a recoverable or degraded path, such as a fallback taken, a missing optional
  resource, or a failed non-critical initialization.
- Log at `info` only for a significant lifecycle milestone, because `info` is the level shipped to
  users.
- Log developer diagnostics at `debug` or `trace`, and do not rely on them in a normal run.

## Gated tracing

Tracing too noisy to enable with the logger level alone sits behind one environment variable per
subsystem, read through `LogUtils`.

To add a gated subsystem:

1. Add a `DEBUG_XXX_ENV_VAR` constant to `LogUtils` that holds the variable name.
2. Add a `DEBUG_XXX_ENABLED` constant to `LogUtils` that reads the variable once, at class
   initialization.
3. Add a public `isTracingXxx(Logger)` that delegates to the private `isTracing`, and keep the
   flag private so that no caller can pass it to the wrong check.

Rules:

- Guard every tracing call with the matching `isTracingXxx` check before building the message,
  so that expensive tracing does not run while the subsystem is off.
- Give a class with several tracing call sites a private `trace` helper in place of repeating the
  guard inline.

## Finding the log

- Read `songscribe.log` in `${songscribe.log.dir}`: `~/Library/Logs/SongScribe/` on macOS,
  `%APPDATA%\SongScribe\Logs\` on Windows, `~/.songscribe/logs/` on Linux.
- Put a per-logger level override, such as pinning a noisy third-party logger, in
  `src/main/resources/logback.xml`; `logback-console.xml` configures the console appender.
