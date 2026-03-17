## Logging

### Logger Declarations

Every class that logs uses a per-class SLF4J logger:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger LOG = LoggerFactory.getLogger(ClassName.class);
```

### Bootstrap Ordering Constraint

Logback configuration depends on system properties set by `SongScribe.configureLogging()`, which runs at the start of `main()`. The `static final Logger` field in each class triggers SLF4J/Logback initialization when that class is first loaded.

**Do NOT add a logger to `SongScribe.java` itself.** Because `SongScribe` is the entry-point class, its static fields initialize before `main()` runs — a logger declared there would trigger Logback before the bootstrap properties are set.

If `SongScribe` needs to log, use `System.out.println` for the few lines before `configureLogging()` completes, and obtain a logger instance locally after bootstrap:

```java
Logger log = LoggerFactory.getLogger(SongScribe.class);
log.info("...");
```
