# Sub-plan: Extract MIDI Sequence Building

**Parent:** [score-cleanup.md](score-cleanup.md)
**Phase:** 1
**Status:** ✅ Complete
**Created:** 2026-02-02
**Completed:** 2026-02-03

---

## Goal

Extract MIDI sequence building from Score.java with a cleaner architecture where:
- **Line** is responsible for adding its own notes to a track (owns its data and operations)
- **MidiSequenceBuilder** coordinates the process, iterating through lines
- **Composition** provides tempo queries (it owns all lines)

## Current State

Score.java contains ~350 lines of MIDI sequence building code (lines 2163-2549) where Score reaches into Lines to extract data and build the sequence. This violates the principle that Line should be responsible for its own data and operations.

## Target Architecture

```
MidiSequenceBuilder (coordinator)
    │
    ├── createSequence() - creates empty MIDI sequence
    ├── buildFullSequence() - iterates lines, delegates to each
    └── buildSelectionSequence() - builds from selection point
            │
            ▼
Composition
    │
    ├── getTempoAt(lineIndex, noteIndex) - effective tempo at any position
    └── getTempo() - default composition tempo
            │
            ▼
Line
    │
    ├── addToTrack(track, ticks, initialTempo, settings) → returns (ticks, endingTempo)
    ├── getNoteDurationWithTuplet(noteIndex, tempo) - tuplet-adjusted duration
    └── getTupletFactor(noteIndex, tempo) - calculates tuplet scaling
```

## Implementation Steps

### Step 1: Create PlaybackSettings Record

**Model:** Haiku
**Testing:** No tests needed (simple record class)

Create `src/main/java/songscribe/midi/PlaybackSettings.java`:

```java
package songscribe.midi;

public record PlaybackSettings(
    int instrument,
    int tempoChangePercent,   // percentage: e.g., 100 = normal, 120 = 20% faster
    int noteDurationPercent,  // percentage: 1-100
    boolean colorizeNotes
) {}
```

### Step 2: Add Duration Methods to Line

**Model:** Sonnet
**Testing:** Unit tests recommended (tuplet factor calculation is non-trivial math - test various tuplet configurations)

Add to `Line.java`:

```java
/**
 * Returns the duration of a note adjusted for tuplet membership.
 *
 * @param noteIndex Index of the note
 * @param referenceTempo The tempo providing the reference note duration
 * @return Duration in ticks, adjusted for tuplet if applicable
 */
public int getNoteDurationWithTuplet(int noteIndex, Tempo referenceTempo) {
    return Math.round(getNote(noteIndex).getDuration() * getTupletFactor(noteIndex, referenceTempo));
}

/**
 * Calculates the tuplet scaling factor for a note.
 *
 * @param noteIndex Index of the note
 * @param referenceTempo The tempo providing the reference note duration
 * @return Scaling factor (1.0 if not in a tuplet)
 */
private float getTupletFactor(int noteIndex, Tempo referenceTempo) {
    var tupletInt = tuplets.findInterval(noteIndex);
    if (tupletInt == null) {
        return 1f;
    }

    // Sum durations of all notes in the tuplet
    var tupletDuration = 0f;
    for (var i = tupletInt.getStart(); i <= tupletInt.getEnd(); i++) {
        tupletDuration += getNote(i).getDuration();
    }

    // Calculate scaling factor based on reference tempo's note duration
    tupletDuration /= referenceTempo.getTempoType().getNote().getDuration();
    // ... rest of calculation (same logic as current Score.java)

    return newDuration / tupletDuration;
}
```

### Step 3: Add addToTrack Method to Line

**Model:** Sonnet
**Testing:** Defer (tested via MidiSequenceBuilder integration tests in Step 5)

Add to `Line.java`:

```java
/**
 * Adds this line's notes to a MIDI track.
 *
 * @param track The MIDI track to add to
 * @param lineIndex This line's index in the composition (for colorize messages)
 * @param startTicks Starting tick position
 * @param initialTempo Tempo at the start of this line
 * @param settings Playback settings
 * @return Pair of (ending tick position, ending tempo)
 */
public Pair<Integer, Tempo> addToTrack(
    Track track,
    int lineIndex,
    int startTicks,
    Tempo initialTempo,
    PlaybackSettings settings
) throws InvalidMidiDataException {
    var ticks = startTicks;
    var currentTempo = initialTempo;

    for (var i = 0; i < noteCount(); i++) {
        var note = getNote(i);

        // Add tempo change if present
        if (note.getTempoChange() != null) {
            currentTempo = note.getTempoChange();
            // Add tempo meta message to track...
        }

        // Add colorize message if enabled
        if (settings.colorizeNotes()) {
            // Add meta message with (lineIndex, noteIndex)...
        }

        // Add note on/off messages
        ticks = addNoteToTrack(track, i, ticks, currentTempo, settings);
    }

    return new Pair<>(ticks, currentTempo);
}
```

### Step 4: Rename getLastTempo to getTempoAt in Composition

**Model:** Haiku
**Testing:** No new tests needed (rename only, existing behavior preserved)

Rename for clarity in `Composition.java`:

```java
/**
 * Returns the effective tempo at a given position in the composition.
 * Walks backwards through lines and notes to find the most recent tempo change,
 * or returns the default composition tempo if none found.
 */
public Tempo getTempoAt(int lineIndex, int noteIndex) {
    // Same logic as current getLastTempo(), but takes indices instead of Line
}
```

### Step 5: Create MidiSequenceBuilder

**Model:** Sonnet
**Testing:** Integration tests required - test full sequence building (full composition, selection, with tempo changes, tuplets, grace notes)

Create `src/main/java/songscribe/midi/MidiSequenceBuilder.java`:

```java
package songscribe.midi;

public class MidiSequenceBuilder {

    private static final int PPQ = 30;
    // ... other constants (velocities, etc.)

    private final Composition composition;
    private final PlaybackSettings settings;

    public MidiSequenceBuilder(Composition composition, PlaybackSettings settings) {
        this.composition = composition;
        this.settings = settings;
    }

    public Sequence buildFullSequence() throws InvalidMidiDataException {
        return buildSequence(0, 0, -1, -1, composition.getTempo());
    }

    public Sequence buildSelectionSequence(int lineIndex, int startNote, int endNote)
            throws InvalidMidiDataException {
        var startTempo = composition.getTempoAt(lineIndex, startNote);
        return buildSequence(lineIndex, startNote, lineIndex, endNote, startTempo);
    }

    private Sequence buildSequence(
        int startLine, int startNote,
        int endLine, int endNote,
        Tempo initialTempo
    ) throws InvalidMidiDataException {
        var sequence = new Sequence(Sequence.PPQ, PPQ, 0);
        var track = sequence.createTrack();

        // Add program change for instrument
        addProgramChange(track, settings.instrument());

        // Add initial tempo
        addTempoMessage(track, 0, initialTempo);

        var ticks = 0;
        var currentTempo = initialTempo;
        var lines = composition.getLines();

        for (var i = startLine; i < lines.size(); i++) {
            var line = lines.get(i);

            // Delegate to Line to add its notes
            var result = line.addToTrack(track, i, ticks, currentTempo, settings);
            ticks = result.first();
            currentTempo = result.second();

            if (i == endLine) break;
        }

        return sequence;
    }

    // Helper methods for MIDI messages...
}
```

### Step 6: Move Playback Settings to PlaybackController

**Model:** Haiku
**Testing:** No new tests needed (simple field relocation)

Move playback settings fields from Score to PlaybackController:

```java
// In PlaybackController.java
private static int instrument = 0;
private static int tempoChangePercent = 100;
private static int noteDurationPercent = 100;
private static boolean colorizeNotes = false;

public static void setInstrument(int value) { instrument = value; }
public static void setTempoChangePercent(int value) { tempoChangePercent = value; }
public static void setNoteDurationPercent(int value) { noteDurationPercent = value; }
public static void setColorizeNotes(boolean value) { colorizeNotes = value; }

public static PlaybackSettings getPlaybackSettings() {
    return new PlaybackSettings(
        instrument,
        tempoChangePercent,
        noteDurationPercent,
        colorizeNotes
    );
}

public static Sequence buildSequence(Composition composition) throws InvalidMidiDataException {
    return new MidiSequenceBuilder(composition, getPlaybackSettings()).buildFullSequence();
}

public static Sequence buildSelectionSequence(
    Composition composition,
    int lineIndex,
    int startNote,
    int endNote
) throws InvalidMidiDataException {
    return new MidiSequenceBuilder(composition, getPlaybackSettings())
        .buildSelectionSequence(lineIndex, startNote, endNote);
}
```

Update Score.musicDidChange() to delegate setting values to PlaybackController:

```java
@Override
public void musicDidChange(@NotNull Properties props) {
    // ... other properties ...
    PlaybackController.setInstrument(Integer.parseInt(
        props.getProperty(Constants.INSTRUMENT_PROP)
    ));
    PlaybackController.setTempoChangePercent(Integer.parseInt(
        props.getProperty(Constants.TEMPO_CHANGE_PROP)
    ));
    PlaybackController.setNoteDurationPercent(Integer.parseInt(
        props.getProperty(Constants.PLAYBACK_NOTE_DURATION_PROP)
    ));
    PlaybackController.setColorizeNotes(
        props.getProperty(Constants.COLORIZE_NOTE).equals(Constants.TRUE_VALUE)
    );
}
```

### Step 7: Remove MIDI Code from Score.java

**Model:** Haiku
**Testing:** No tests needed (code deletion)

Remove from Score.java:
- Remove `getSequence()` method entirely
- Remove `getSelectedSequence()` method entirely
- Remove playback settings fields: `instrument`, `manualTempoChange`, `playbackNoteDuration`, `colorizeNote`
- Remove all extracted private MIDI methods
- Keep MIDI constants (PPQ, velocities) - still referenced by Note classes and other components

Update callers of Score.getSequence() to use PlaybackController.buildSequence() instead.
Update callers of Score.getSelectedSequence() to use PlaybackController.buildSelectionSequence() instead.

## Files to Modify

- **Create:** `src/main/java/songscribe/midi/PlaybackSettings.java`
- **Create:** `src/main/java/songscribe/midi/MidiSequenceBuilder.java`
- **Modify:** `src/main/java/songscribe/music/Line.java` - add duration and track methods
- **Modify:** `src/main/java/songscribe/music/Composition.java` - rename getLastTempo → getTempoAt
- **Modify:** `src/main/java/songscribe/ui/playback/PlaybackController.java` - add playback settings, coordinate MIDI sequence building
- **Modify:** `src/main/java/songscribe/ui/component/Score.java` - remove MIDI methods and fields, update musicDidChange to delegate to PlaybackController
- **Update Callers:** Any code calling Score.getSequence() or Score.getSelectedSequence() must be updated to call PlaybackController methods

## Verification

1. Compile: `./scripts/compile.sh`
2. Run: `./scripts/run-debug.sh`
3. Test playback:
   - Play entire composition from beginning
   - Play from middle (pause and resume)
   - Play selected notes
   - Verify note colorization during playback
   - Test with tempo changes mid-composition
   - Test with grace notes and tuplets
4. Verify loop playback works
5. Verify play with repeats works

## Notes

- **Line** becomes responsible for its own MIDI contribution (data locality)
- **MidiSequenceBuilder** is a coordinator, not a data extractor
- **PlaybackController** owns playback settings and coordinates sequence building
- **Score** is no longer involved in MIDI sequence building - it's a UI component, not a playback component
- Tempo flows through: Composition provides initial → Line receives and returns ending
- The `colorizeNotes` flag controls whether meta messages are embedded for playback highlighting
- These meta messages are consumed by Phase 2's notification mechanism (also handled by PlaybackController)
