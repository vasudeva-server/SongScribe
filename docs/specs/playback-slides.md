# Playback Slides (Glissando MIDI Playback)

refs #13

## Overview

Add MIDI pitch bend generation during playback when a note has a glissando, producing an audible pitch slide between the source note and the glissando's target pitch.

## Current State

- `Note.Glissando` stores a `type` field (`Type.CONNECTED` or `Type.SLIDE_OUT`) and visual adjustments (`x1Translate`, `x2Translate`).
- `GlissandoRenderer` draws the visual glissando line.
- `Line.addNoteMessages()` ignores glissando data entirely during MIDI sequence building.
- There is no pitch bend code anywhere in the codebase.

## Timing Model

Both `CONNECTED` and `SLIDE_OUT` glissandos use the same timing split:

- **First 2/3 of the note's written duration:** The note sustains at its original pitch (no pitch bend).
- **Last 1/3 of the note's written duration:** Pitch bend ramps from center (source pitch) to the target pitch.

The split ratio (2/3 sustain, 1/3 slide) is a hardcoded constant, designed so it can be made configurable later.

## Pitch Bend Curve

The slide uses a **quadratic ease-in** curve (`progress = t^2`, where `t` goes from 0.0 to 1.0). This produces slow initial movement that accelerates toward the target pitch, giving a natural musical feel.

## Two Behavioral Cases

### Case 1: Connected Glissando (`Type.CONNECTED`)

**Condition:** The glissando's `type` is `CONNECTED`. There must be a following note in the line.

**Target pitch:** The next note's MIDI pitch.

**Behavior:**
- The note plays for 2/3 of its duration at the original pitch.
- The pitch bend slide occupies the remaining 1/3, sliding toward the next note's pitch.
- **Seamless legato transition:** The source note's `NOTE_OFF` and the next note's `NOTE_ON` occur at the same tick (the end of the written duration). No retriggering gap.
- The pitch bend arrives at center (no bend) at the exact tick the next note begins.
- **Staccato and `noteDurationPercent` are ignored** -- the note extends to its full written duration to enable the seamless transition.

### Case 2: Slide Out (`Type.SLIDE_OUT`)

**Condition:** The glissando's `type` is `SLIDE_OUT`.

**Target pitch:** 4 semitones below the source note's MIDI pitch.

**Behavior:**
- The note plays for 2/3 of its duration at the original pitch.
- The pitch bend slide occupies the remaining 1/3, sliding down 4 semitones.
- `NOTE_OFF` occurs at the end of the slide (at the note's sounding duration, which respects staccato and `noteDurationPercent`).
- Pitch bend resets to center at the same tick as `NOTE_OFF`.

## Target Pitch Resolution

- **`Type.CONNECTED`:** The target MIDI pitch is the next note's `getPitch()` value. No resolution step is needed.
- **`Type.SLIDE_OUT`:** The target MIDI pitch is `sourcePitch - 4` (4 semitones below the source note).

## Pitch Bend Sensitivity (RPN 0)

MIDI pitch bend has a configurable range via RPN 0 (Pitch Bend Sensitivity). The implementation dynamically sets this per glissando:

1. Calculate the semitone interval between the source note's MIDI pitch and the resolved target MIDI pitch.
2. Set the pitch bend sensitivity to cover that interval (via RPN 0 CC messages: CC 101=0, CC 100=0, CC 6=semitones, CC 38=0).
3. Track the current sensitivity as state during sequence building. Only emit RPN messages when the needed sensitivity differs from the current value.
4. After a glissando, leave the sensitivity as-is until a subsequent glissando needs a different range.

## Pitch Bend Step Resolution

Use a **tick-based interval** for pitch bend messages. Insert one pitch bend message every 2-4 ticks (at PPQ=96). This directly maps to MIDI resolution, adapts naturally to tempo, and produces smooth slides without requiring real-time tempo conversion.

The exact interval should be chosen to balance smoothness against track event density. At PPQ=96, a quarter note has 96 ticks; the 1/3 slide portion is 32 ticks, yielding 8-16 pitch bend steps per quarter-note slide -- sufficient for smooth output.

## Pitch Bend Reset

After a glissando note ends:
- **`CONNECTED`:** Pitch bend arrives at center (0) at the legato transition tick. No explicit reset needed.
- **`SLIDE_OUT`:** Pitch bend resets to center at the same tick as `NOTE_OFF`.

## Channel Considerations

Pitch bend is per-channel in MIDI. For V1, no special channel isolation is needed -- SongScribe lines are monophonic, so overlapping notes within a line are not expected. If polyphonic playback is added in the future, notes with glissandos would need to be routed to a dedicated channel.

## Tie Interaction

Tied notes cannot have glissandos. This will be enforced in the data model (separate from this feature). The playback code does not need to handle this case.

## Articulation Interactions

| Articulation | `CONNECTED` | `SLIDE_OUT` |
|---|---|---|
| Staccato | Ignored (full duration used) | Respected (slide fits within sounding duration) |
| Accent | Velocity increase applies normally | Velocity increase applies normally |
| Fermata | Duration extension applies, then 2/3 + 1/3 split | Duration extension applies, then 2/3 + 1/3 split |

## Code Architecture

### New Class: `GlissandoMidiHelper`

A separate utility class responsible for all glissando-related MIDI generation. Located in the `songscribe.midi` package.

**Responsibilities:**
- Resolve glissando target MIDI pitch based on type (`CONNECTED`: next note's pitch; `SLIDE_OUT`: source pitch minus `SLIDE_OUT_SEMITONES`).
- Calculate required pitch bend sensitivity.
- Generate pitch bend messages with the quadratic ease-in curve.
- Emit RPN sensitivity CC messages when needed.
- Handle `CONNECTED` vs. `SLIDE_OUT` type-specific logic.

**Called from:** `Line.addNoteMessages()`, which detects `note.getGlissando() != Note.NO_GLISSANDO` and delegates to the helper.

### Changes to Existing Code

- **`Line.addNoteMessages()`:** Add a check for glissando presence. When detected, call `GlissandoMidiHelper` to generate pitch bend events on the track. For `CONNECTED` glissandos, adjust NOTE_OFF timing to enable seamless legato.
- **`MidiController`:** No changes needed. Pitch bend messages are embedded in the sequence track, not sent in real-time.
- **`MidiSequenceBuilder`:** No changes needed unless the builder needs to pass additional context (e.g., key signature) to line processing.

## Testing

### Unit Tests for `GlissandoMidiHelper`

- Verify pitch bend values are calculated correctly for various semitone intervals.
- Verify step count calculation for different note durations.
- Verify RPN sensitivity values for various glissando ranges.
- Verify quadratic ease-in curve produces expected bend values at key points (0%, 25%, 50%, 75%, 100%).
- Verify `CONNECTED` type uses the next note's pitch as the target.
- Verify `SLIDE_OUT` type uses source pitch minus 4 semitones as the target.

### Manual Testing

- Play a note with a glissando and verify the audible slide.
- Test `CONNECTED` glissandos for seamless transitions to the next note.
- Test `SLIDE_OUT` glissandos for proper cutoff and bend reset.
- Test with various intervals: small (2 semitones), medium (5-7), large (12+).
- Test with staccato on both `CONNECTED` and `SLIDE_OUT` cases.
- Test with fermata to verify the extended duration is split correctly.
- Test at different tempos to verify the slide sounds smooth.

## Constants

| Constant | Value | Description |
|---|---|---|
| `SUSTAIN_RATIO` | 2.0/3.0 | Fraction of note duration at original pitch |
| `SLIDE_RATIO` | 1.0/3.0 | Fraction of note duration for pitch bend slide |
| `BEND_STEP_TICKS` | 3 | Ticks between pitch bend messages |
| `PITCH_BEND_CENTER` | 8192 | MIDI pitch bend center (no bend) |
| `PITCH_BEND_MAX` | 16383 | MIDI pitch bend maximum value |
| `CURVE_EXPONENT` | 2.0 | Quadratic ease-in (t^2) |
| `SLIDE_OUT_SEMITONES` | 4 | Semitones to slide down for `SLIDE_OUT` type |
