# Java 25 Feature Adoption Evaluation

## Context

SongScribe has already been upgraded to Java 25. This plan evaluates which Java 25 features can practically benefit the codebase and which are not applicable. The goal is to identify concrete, worthwhile changes -- not adopt features for their own sake.

---

## Feature-by-Feature Evaluation

### 1. Unnamed Variables (`_`) -- Already Adopted

**Status:** Standard since Java 22. Already used throughout the codebase.

Lambda parameters consistently use `_` (e.g., `closeButton.addActionListener(_ -> closeWindow())`). However, there are ~10 catch blocks with unused exception variables that could be cleaned up:

- `GraphicUtils.java:182,191` -- `catch (IOException e)` with no use of `e`
- `MyDesktop.java:71,82,92` -- `catch (Exception e)` with `// Ignore`
- `ForceArticulation.java:29` -- `catch (IllegalArgumentException e)` unused
- `TipFrame.java:99,115` -- `catch (IOException e1)` unused

**Recommendation:** Low-priority cleanup. Change these to `catch (IOException _)` when touching these files. Not worth a dedicated pass.

---

### 2. Flexible Constructor Bodies -- Not Applicable

**Status:** Second preview in Java 24; likely third preview or standard in Java 25.

Explored all subclass constructors. The codebase does not have the pattern this feature solves (validation/computation before `super()`). Constructors call `super()` first and initialize fields in proper order.

**Recommendation:** Skip. No current use case. Keep in mind for future class hierarchies.

---

### 3. Module Imports -- Full Codebase Pass

**Status:** Standard in Java 25.

A single `import module java.desktop` replaces all individual `java.awt.*`, `javax.swing.*`, `javax.sound.*`, `javax.imageio.*`, and `java.beans.*` imports. Similarly, `import module java.base` covers `java.util.*`, `java.io.*`, `java.nio.*`, `java.text.*`, `java.time.*`, `java.math.*`, `java.net.*`, `java.lang.reflect.*`, etc.

**Scope:** 394 total Java files in `src/main/java/`.

| Module | Files affected | Covers |
|---|---|---|
| `java.desktop` | 230 (58%) | `java.awt.*`, `javax.swing.*`, `javax.sound.*`, `javax.imageio.*`, `java.beans.*` |
| `java.base` | 122 (31%) | `java.util.*`, `java.io.*`, `java.nio.*`, `java.text.*`, `java.time.*`, `java.math.*`, `java.net.*`, `java.lang.reflect.*`, `java.util.regex.*`, `java.util.zip.*` |
| `java.logging` | 7 (2%) | `java.util.logging.*` |
| `java.xml` | 4 (1%) | `javax.xml.*`, `org.w3c.dom.*`, `org.xml.sax.*` |
| `java.prefs` | 3 (1%) | `java.util.prefs.*` |

**Approach:** For each Java file, replace all JDK imports with the appropriate `import module` statement(s), keeping `songscribe.*` and third-party imports unchanged. Order: module imports first, then third-party, then project-internal.

**Recommendation:** Do a full pass. This is a mechanical transformation that reduces import noise across the entire codebase. Since many files overlap modules (e.g., a UI file imports from both `java.desktop` and `java.base`), expect most files to end up with 1-2 module imports replacing 5-15 individual imports.

---

### 4. Stream Gatherers -- Limited Value

**Status:** Standard since Java 24.

Explored all 31 files with stream usage. The stream patterns found are straightforward:
- `filter` + `instanceof` + `map` cast (could use pattern-matching `instanceof` instead)
- `IntStream.range().filter().collect()` for index collection
- Simple `anyMatch`, `findFirst`, `filter` chains

Custom Gatherers shine for sliding windows, stateful accumulation, and complex sequential processing. The music data processing in SongScribe uses imperative loops for these patterns (e.g., syllable scanning in `LyricsRenderer`), and converting them to Gatherer-based streams would add complexity without clarity gains.

**Recommendation:** Skip. The existing stream usage is simple enough that Gatherers would be over-engineering. The imperative loops for complex music processing are clearer than stream equivalents would be.

---

### 5. Scoped Values -- Not Applicable

**Status:** Fourth preview in Java 24; likely still preview in Java 25.

No `ThreadLocal` usage exists in the codebase. The application is primarily single-threaded (EDT) with one simple `PlayThread` for MIDI note playback.

**Recommendation:** Skip. No ThreadLocal to replace, no multi-threaded context propagation patterns.

---

### 6. Structured Concurrency -- Marginal Value

**Status:** Fourth preview in Java 24; likely still preview in Java 25 (requires `--enable-preview`).

Sequential initialization during startup:
- Font loading: 6 fonts loaded one-by-one via `MyFontUtils.installLocalFont()`
- SMuFL metadata: JSON parse of `bravura_metadata.json`
- MIDI: Soundfont extraction + synthesizer initialization
- Preferences: JSON load + legacy migration

These are good candidates for parallelization in theory, but:
- Most load from bundled resources (JAR), which are fast
- MIDI init has ordering dependencies (soundfont must load before channel setup)
- Feature is still preview, adding `--enable-preview` burden
- Startup time has not been reported as a problem

**Recommendation:** Skip for now. If startup time becomes a concern, this could help, but the preview status and added complexity aren't justified for a desktop app with no reported startup latency issue.

---

### 7. Generational ZGC / Shenandoah -- Worth Evaluating

**Status:** Generational ZGC has been standard since Java 21. Shenandoah is available on OpenJDK builds.

Currently no GC configuration in any launch script (`run.sh`, `crun.sh`, `test.sh`) or `pom.xml`. The JVM defaults to G1GC.

For a music notation GUI app:
- **G1GC** (current default): Good general-purpose, but pauses can cause UI jank during complex score rendering
- **ZGC** (`-XX:+UseZGC`): Sub-millisecond pauses, ideal for interactive UI apps. Slightly higher memory overhead.
- **Shenandoah**: Similar low-pause characteristics, JDK-distribution dependent

**Recommendation:** Worth trying. Add `-XX:+UseZGC` to `run.sh` and `crun.sh`. This is a one-line, zero-risk change that could improve UI responsiveness during heavy rendering (large scores, rapid scrolling). No code changes needed.

**Files to modify:**
- `scripts/run.sh` -- add `-XX:+UseZGC` to java invocation
- `scripts/crun.sh` -- same
- Optionally `scripts/run-debug.sh`, `scripts/crun-debug.sh`

---

## Summary

| Feature | Applicable? | Action |
|---|---|---|
| Unnamed variables | Already adopted | Minor catch-block cleanup when files are touched |
| Flexible constructor bodies | No current use case | Skip |
| Module imports | Standard, 230+ files benefit | Full pass -- replace JDK imports with module imports |
| Stream Gatherers | Standard but low value here | Skip |
| Scoped Values | No ThreadLocal usage | Skip |
| Structured Concurrency | Preview, no startup problem | Skip |
| ZGC | Standard, zero-risk improvement | Add `-XX:+UseZGC` to run scripts |

## Verification

### Module imports pass
1. `./scripts/compile.sh` -- must compile cleanly with no errors
2. `./scripts/test.sh` -- all tests pass
3. Spot-check a few converted files to confirm import ordering is correct

### ZGC change
1. `./scripts/run.sh` -- app launches, no GC warnings in console
2. Open a complex score, scroll rapidly -- should feel smooth
