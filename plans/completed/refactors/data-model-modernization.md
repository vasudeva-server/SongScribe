# Plan: Data Model Modernization

**Type:** Master Plan
**Created:** 2026-02-02
**Status:** Done

---

## Status Dashboard

| Phase | Description | Status | Sub-plan |
|-------|-------------|--------|----------|
| 1 | [Typed Interval Subclasses](#phase-1-typed-interval-subclasses) | ✅ Done | — |
| 2 | [Line Interval Accessors](#phase-2-line-interval-accessors) | ✅ Done | clever-enchanting-zephyr.md |
| 3 | [IO Layer Conversion](#phase-3-io-layer-conversion) | ✅ Done | clever-enchanting-zephyr.md |

**Last Updated:** 2026-02-19

---

## Goal

Replace stringly-typed `Interval.data` usage with proper typed subclasses while maintaining file format compatibility. The IO layer handles conversion between legacy string format and typed objects.

## Current State

`Interval` is a generic class with a `String data` field used as a catch-all:

```java
public class Interval {
    public int start, end;
    public String data;  // "3" or "3,15" for tuplets, other formats for dynamics, etc.
}
```

Utility classes like `TupletIntervalData` and `DynamicsIntervalData` parse this string on every access:

```java
public static int getGrade(Interval tupletInterval) {
    var data = tupletInterval.getData().split(SEPARATOR);
    return Integer.parseInt(data[0]);  // Parse every time
}
```

## Target Architecture

```
Interval (base class)
├── TupletInterval      - grade: int, verticalPosition: int
├── DynamicsInterval    - type: DynamicsType, ...
├── EndingInterval      - endingNumber: int
└── (plain Interval for beams, ties, slurs - no extra data)

LineIO
├── On load: parse string → create typed subclass
└── On save: typed subclass → format string
```

---

## Phase 1: Typed Interval Subclasses

**Status:** Done  <br>
**Recommended Model:** Haiku  <br>
**Priority:** High  <br>
**Risk:** Low (additive change)  <br>
**Testing:** Unit tests for each subclass  <br>
**Estimated Lines:** 150-200

### Create Typed Subclasses

Create in `songscribe.data` package:

**TupletInterval.java**
```java
public class TupletInterval extends Interval {
    private int grade;
    private int verticalPosition;

    public TupletInterval(int start, int end, int grade) {
        super(start, end, null);
        this.grade = grade;
        this.verticalPosition = 0;
    }

    public int getGrade() { return grade; }
    public void setGrade(int grade) { this.grade = grade; }
    public int getVerticalPosition() { return verticalPosition; }
    public void setVerticalPosition(int pos) { this.verticalPosition = pos; }
    public boolean isVerticallyAdjusted() { return verticalPosition != 0; }
}
```

**DynamicsInterval.java** - analyze `DynamicsIntervalData` for required fields

**EndingInterval.java** - for first/second endings if they have data

### Notes
- Keep `Interval.data` for backwards compatibility during transition
- Subclasses can override `getData()`/`setData()` to sync if needed

---

## Phase 2: Line Interval Accessors

**Status:** Done  <br>
**Recommended Model:** Sonnet  <br>
**Priority:** High  <br>
**Risk:** Medium (API change)  <br>
**Testing:** Integration tests for Line accessors and caller updates  <br>
**Estimated Lines:** 100-150

### Update Line Class

Change `Line` to return typed intervals:

```java
// Before
public IntervalSet getTuplets() { ... }

// After
public IntervalSet<TupletInterval> getTuplets() { ... }
// Or return List<TupletInterval> directly
```

### Update Callers

Replace utility class usage:
```java
// Before
var grade = TupletIntervalData.getGrade(interval);

// After
var grade = tupletInterval.getGrade();
```

### Delete Utility Classes

Once all callers are migrated:
- Delete `TupletIntervalData.java`
- Delete `DynamicsIntervalData.java`

---

## Phase 3: IO Layer Conversion

**Status:** Done  <br>
**Recommended Model:** Sonnet  <br>
**Priority:** High  <br>
**Risk:** Medium (serialization change)  <br>
**Testing:** File I/O round-trip tests, format compatibility verification  <br>
**Estimated Lines:** 150-200

### Update LineIO

**Loading:**
```java
private static void stringToTupletIntervalSet(IntervalSet<TupletInterval> is, String str) {
    // Parse "start,end,grade" or "start,end,grade,vertPos"
    // Create TupletInterval instead of plain Interval
}
```

**Saving:**
```java
private static String tupletIntervalToString(IntervalSet<TupletInterval> is) {
    // Format TupletInterval fields back to "start,end,grade,vertPos"
}
```

### File Format

No changes - the string format remains identical:
- Tuplets: `start,end,grade` or `start,end,grade,verticalPos`
- Dynamics: existing format preserved
- etc.

---

## Verification

After each phase:
1. Compile: `./scripts/compile.sh`
2. Run: `./scripts/run-debug.sh`
3. Test file operations:
   - Open existing .mss files with tuplets, dynamics, endings
   - Save and reopen - verify no data loss
   - Create new tuplets/dynamics, save, reopen
4. Test playback (tuplet duration calculations)
5. Test rendering (tuplet brackets, dynamics marks)

---

## Dependencies

- None for Phase 1 (additive)
- Phase 2 depends on Phase 1
- Phase 3 depends on Phase 2

## Notes

- This plan can proceed independently of score-cleanup
- Consider whether `IntervalSet` should become generic `IntervalSet<T extends Interval>`
- The `getTupletFactor()` method (currently in Score.java) should move to `TupletInterval` or a calculator class after this modernization
