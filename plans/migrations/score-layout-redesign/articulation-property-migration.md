# Plan: Articulation Property Migration

**Type:** Sub-plan  <br>
**Parent:** plans/migrations/score-layout-redesign/score-layout-redesign.md → Phase 3  <br>
**Captured:** 2026-02-13  <br>
**Pre-planned:** No  <br>
**Status:** Completed

---

## Context

Phase 3 of the Score Layout Redesign introduced a new `Articulation` class (extending `LineElement`) and an `ArticulationType` enum, but only partially migrated. The old enum-based properties (`Note.forceArticulation`, `Note.durationArticulation`) remain the source of truth, while the new `List<Articulation>` on `Note` is only populated in tests.

This causes a rendering bug: `LineRenderer.renderAttachments()` guards with `!note.getArticulations().isEmpty()` (new system), but `ArticulationRenderer` reads the old properties. Articulations on newly inserted notes never render because the new list is never populated.

This plan completes the migration so the `Articulation` objects become the single source of truth.

---

## Status Dashboard

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | [Enrich ArticulationType](#-phase-1-enrich-articulationtype) | ✅ Complete |
| 2 | [Add Note convenience methods](#-phase-2-add-note-convenience-methods) | ✅ Complete |
| 3 | [Dual-write: populate Articulation objects everywhere](#-phase-3-dual-write) | ✅ Complete |
| 4 | [Switch all readers to new system](#-phase-4-switch-readers) | ✅ Complete |
| 5 | [Remove old properties and enums](#-phase-5-remove-old-properties) | ✅ Complete |

---

## ✅ Phase 1: Enrich ArticulationType

**Risk:** None (additive)

Add MIDI duration data to `ArticulationType` to replace `DurationArticulation.getDuration()`.

### `ArticulationType.java`
- Add `midiDurationPercent` field (int, -1 = no override)
- `STACCATO(33)`, `ACCENT(-1)`
- Add `getMidiDurationPercent()` and `hasMidiDurationOverride()` methods

---

## ✅ Phase 2: Add Note Convenience Methods

**Risk:** None (additive)

### `Note.java`
- `hasArticulation(ArticulationType type)` — returns true if any Articulation in the list matches
- `findMidiDurationOverride()` — returns the MIDI duration % from the first articulation that has one, or -1 if none

---

## ✅ Phase 3: Dual-Write

**Risk:** Medium — if any write path is missed, the two systems fall out of sync

Every code path that sets old properties also populates the new `Articulation` list.

### `EditModeManager.decorateNote()` (`src/main/java/songscribe/ui/edit/EditModeManager.java`)
- After setting old properties, also call `note.clearArticulations()` then `note.addArticulation(...)` for each active articulation
- Clear-then-rebuild is simpler than diffing

### `NoteIO.NoteReader.endElement11()` (`src/main/java/songscribe/io/NoteIO.java`)
- After setting old properties from XML, also call `note.addArticulation(new Articulation(note, ArticulationType.XXX))`
- Include the backward-compat path (`"volume"/"LOUDER"` -> ACCENT)

### `Note` copy constructor (`src/main/java/songscribe/music/Note.java`)
- Deep-copy: for each `Articulation` in source, create `new Articulation(this, art.getType())` and add
- Remove the "NOT deep-copied" comment

---

## ✅ Phase 4: Switch Readers

**Risk:** Medium — each change is small and independently testable

Switch all consumers from old properties to new `Articulation` list queries.

### `ArticulationRenderer` (`src/main/java/songscribe/ui/renderer/ArticulationRenderer.java`)
- `renderAccent()`: change guard from `getForceArticulation() != ACCENT` to `!note.hasArticulation(ACCENT)`
- `renderStaccato()`: change guard from `getDurationArticulation() != STACCATO` to `!note.hasArticulation(STACCATO)`

### `LineRenderer.renderInsertionNote()` (`src/main/java/songscribe/ui/component/score/LineRenderer.java`)
- Change guard to `!editNote.getArticulations().isEmpty()` (matches `renderAttachments`)

### `Line` MIDI methods (`src/main/java/songscribe/music/Line.java`)
- `addNoteOn()`/`addNoteOff()`: change to `note.hasArticulation(ACCENT)`
- `addNoteMessages()`: change staccato duration to use `note.findMidiDurationOverride()`

### `ExportABCAction.translateDecorations()` (`src/main/java/songscribe/ui/action/ExportABCAction.java`)
- Iterate `note.getArticulations()` and check `isAccent()`/`isStaccato()` instead of old properties

### `NoteIO.writeNote()` (`src/main/java/songscribe/io/NoteIO.java`)
- Serialize from `note.getArticulations()` instead of old properties
- Keep same XML element names for file format compatibility

---

## ✅ Phase 5: Remove Old Properties

**Risk:** Low — compiler catches any missed references

### Delete
- `ForceArticulation.java` enum
- `DurationArticulation.java` enum
- `ForceArticulationAction.java` (replace with unified `ArticulationAction` holding `ArticulationType`)
- `DurationArticulationAction.java` (same)

### Modify
- **`Note.java`**: remove `forceArticulation`/`durationArticulation` fields, getters, setters, and copy-constructor lines
- **`NonNote.java`**: remove `getForceArticulation()`/`getDurationArticulation()` overrides (empty list inherited naturally)
- **`EditModeManager.decorateNote()`**: remove old property setter calls, keep only `addArticulation()` calls
- **`NoteIO`**: remove old property setter calls in reader, keep XML tag constants for reading legacy files
- **`Actions.java`**: update `ACCENT_ACTION` and `STACCATO_ACTION` to use new unified action class with `ArticulationType`

---

## Verification

After each phase:
1. `./scripts/compile.sh` — must succeed
2. After Phase 3: run app, insert notes with accent/staccato selected — articulations should render
3. After Phase 4: same manual test, plus verify MIDI playback (accented notes louder, staccato notes shorter)
4. After Phase 5: `./scripts/compile.sh` confirms no dangling references; repeat manual tests
