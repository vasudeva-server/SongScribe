# Playback Edit Architecture Options

## Context

When playback is active, SongScribe's MIDI sequence is a static snapshot built at play time by `MidiSequenceBuilder`. Allowing score edits during playback creates a mismatch between the playing sequence and the visual score. This document analyzes the ramifications and design options.

## Architectural Facts

- **The sequence is a static snapshot.** `MidiSequenceBuilder` does a complete rebuild at `play()` time (or `buildSelectionSequence()`). There is no incremental update path. The sequence encodes not just pitches but meta messages at specific tick offsets that drive the playing-note highlight in `LineComponent`.
- **Note highlighting is tick-coupled.** `PlaybackController.updatePlayingNote()` fires from sequencer meta messages embedded at build time. If the score changes after build, those tick offsets now refer to wrong (or nonexistent) notes.
- **The PAUSED state only skips rebuilding if the selection is identical.** Pausing does not protect against a stale sequence if the selection changes.

## Current State

31 `UIAction` subclasses carry a `DISABLE_WHEN_PLAYING` flag. `UIAction.playbackStateDidChange()` enforces it whenever `PlaybackStateChangedMessage` fires. This provides partial protection but likely has gaps: text fields, dialogs opened before playback started, drag interactions, and undo/redo.

Playback settings (tempo %, instrument, loop, repeats) are intentionally allowed to change during playback — these use `AbstractAction` or custom message posting, not `UIAction`, so they fall outside the flag mechanism.

## Options

### Option 1: Complete Lockdown (Extend Current Mechanism)

Extend `DISABLE_WHEN_PLAYING` to cover every action that can touch the score — including any gaps that currently exist — so the score is effectively read-only during playback.

**Pros**
- Zero risk of sequence/score mismatch. What is playing is always what you see.
- The mental model is simple and consistent: you cannot edit while playing.
- The existing mechanism is already mostly there — gaps just need auditing and closing.
- No rebuild complexity. The sequence is always fresh at play start.

**Cons**
- Slightly disruptive for users who want to make quick text edits (title, lyrics) while listening. These don't affect the sequence at all but would be blocked.
- Requires a careful audit to identify every remaining gap (inline text editors, dialogs launched pre-playback, etc.).

---

### Option 2: Any Score Edit Stops Playback

Don't disable editing actions. Instead, intercept every score-modifying action and, before applying it, call `PlaybackController.stop()` so the sequence state is no longer live when the edit lands.

**Pros**
- Feels more fluid — users can edit at will without being blocked by a modal "you can't do that" experience.
- Simpler than rebuilding mid-flight: stop, edit, then press play again to get a fresh sequence.
- No sequence/score mismatch is possible, because playback is always stopped before any mutation commits.

**Cons**
- Stopping playback mid-song on every accidental keystroke (e.g., a mistyped note) is highly disruptive to listening workflows.
- The user loses their place in the score. There is no resume-from-position after a stop.
- Requires instrumentation in every action (or a central pre-execution interceptor), which is more invasive than flagging.
- May surprise users: clicking on a note to inspect it could accidentally stop playback if the click is misread as an edit intent.

---

### Option 3: Edit Triggers Pause + Rebuild + Resume

On any score mutation during playback: (1) record the current sequencer tick position, (2) stop the sequencer, (3) rebuild the sequence from scratch, (4) seek the new sequencer to the closest equivalent tick, (5) resume.

**Pros**
- The most "live" experience — edits ahead of the playhead would play correctly when the sequencer reaches them.
- Could be compelling for composers who want to hear the effect of a change immediately without restarting.

**Cons**
- Rebuilding is non-trivial: repeat markers, first/second endings, tempo changes, and tuplets all affect tick mapping. A note inserted before the playhead shifts all downstream ticks. Seeking to "the closest equivalent tick" after a structural change is genuinely hard — there is no stable identity for a tick position across rebuilds.
- The pause+rebuild cycle introduces a perceptible gap, even if short, especially for complex scores. This would feel glitchy.
- Resume from mid-sequence must re-initialize MIDI channel state (`reinitChannels` already does this at play start, but mid-sequence is different — sustain pedal state, running notes, etc. could be in arbitrary states).
- Significantly higher implementation cost and surface area for bugs, especially around the repeat/ending logic in `MidiSequenceBuilder.buildSequenceWithRepeats()`.

---

### Option 4: Tiered Locking — Score Locked, Metadata Editable

Distinguish between two categories:

- **Score-structural content** (notes, durations, articulations, key/time signatures, repeats, tempo markings embedded in the score): locked with `DISABLE_WHEN_PLAYING`.
- **Score metadata** (title, composer name, annotation text that doesn't affect MIDI, display settings): always editable.

This is a refinement of Option 1, not a fundamentally different architecture.

**Pros**
- Users can still edit the title, adjust lyrics/annotations, or tweak display settings while listening — which is a natural workflow.
- No sequence/score mismatch risk (metadata changes don't affect the sequence).
- Reinforces that the lock is about MIDI integrity, not a blunt UI block.

**Cons**
- Requires carefully classifying every editable element. Some things (like dynamics markings) affect MIDI and appearance simultaneously — they must stay locked. Others (like font size of annotations) don't — they can be unlocked.
- The classification is not always obvious and needs to be maintained as features are added.

---

## Recommendation

**Options 1 + 4 combined** is the right long-term direction, and the codebase is already most of the way there.

The `DISABLE_WHEN_PLAYING` mechanism is the correct architecture. The work needed is:

1. **Audit for gaps** — any score-structural editing path that doesn't go through a `UIAction` subclass with the flag: inline text editors in `LineComponent`, dialogs opened before playback, drag interactions, undo/redo.
2. **Disable undo/redo during playback** — undoing an edit made before playback started would still invalidate the running sequence.
3. **Classify pure-metadata editors** as editable-during-playback (title, composer, annotation display text) and exclude them from the flag.

Option 2 is the most tempting alternative but has a fundamental UX problem: stopping playback on every accidental edit is more disruptive than blocking the edit. Option 3 has intractable complexity around tick mapping across structural changes and is not worth the cost for this type of application.
