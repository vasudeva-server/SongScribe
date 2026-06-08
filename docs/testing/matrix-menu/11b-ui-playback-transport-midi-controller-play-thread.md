### 11B — `ui/playback` (Transport, MIDI Controller & Play Thread)

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| `PlaybackController` | `selectionDidChange` — does nothing when not paused (PLAYING state) | unit | `PlaybackControllerTest.testDoesNothingWhenPlaying` | adequate | — | — |
| `PlaybackController` | `selectionDidChange` — does nothing when not paused (STOPPED state) | unit | `PlaybackControllerTest.testDoesNothingWhenStopped` | adequate | — | — |
| `PlaybackController` | `selectionDidChange` — clears highlight and updates `activeSelection` when paused with new selection | unit | `PlaybackControllerTest.testClearsHighlightAndUpdatesSelectionWhenPausedWithSelection` | adequate | — | — |
| `PlaybackController` | `selectionDidChange` — stops when selection cleared (null) while paused | unit | `PlaybackControllerTest.testStopsWhenSelectionClearedWhilePaused` | adequate | — | — |
| `PlaybackController` | `togglePlayPause` — transitions STOPPED → PLAYING (calls `play(null)`) | unit | none | missing | Add unit test: mock sequencer, verify state becomes PLAYING and `PlaybackStateDidChangeNotification` posted | ✅ |
| `PlaybackController` | `togglePlayPause` — transitions PLAYING → PAUSED (calls `playbackDidPause`) | unit | none | missing | Add unit test: mock sequencer, assert state becomes PAUSED | ✅ |
| `PlaybackController` | `togglePlayPause` — PAUSED with same selection calls `resume()` | unit | none | missing | Add unit test: confirm resume path taken (tick position restored) | ✅ |
| `PlaybackController` | `togglePlayPause` — PAUSED with changed selection calls `play(newSelection)` | unit | none | missing | Add unit test: verify `activeSelection` updated to new selection | ✅ |
| `PlaybackController` | `playbackDidStart` — sets state to PLAYING and posts `PlaybackStateDidChangeNotification` | unit | none | missing | Add unit test: assert state and notification | ✅ |
| `PlaybackController` | `playbackDidPause` — sets state to PAUSED, saves tick position, posts notification | unit | none | missing | Add unit test: mock sequencer tick, verify saved `pausedTickPosition` | ✅ |
| `PlaybackController` | `stop` — sets state to STOPPED, clears `activeSelection` and `pausedTickPosition`, posts notification | unit | none | missing | Add unit test via `stop()` directly | ✅ |
| `PlaybackController` | `rewindToBeginning` — while PLAYING: clears highlight and seeks sequencer to tick 0 | unit | none | missing | Add unit test: mock sequencer, verify `setTickPosition(0)` called | ✅ |
| `PlaybackController` | `rewindToBeginning` — while PAUSED: calls stop (state becomes STOPPED) | unit | none | missing | Add unit test: set state PAUSED, assert state becomes STOPPED | ✅ |
| `PlaybackController` | `rewindToBeginning` — while STOPPED: no-op | unit | none | missing | Add unit test: state remains STOPPED, no exceptions | ✅ |
| `PlaybackController` | `handleMetaMessage` — SEQUENCE_NUMBER message decodes line/note indices and calls `updatePlayingNote` | unit | none | missing | Add unit test: construct a `MetaMessage` with packed line+note bytes, mock `ScoreView`, verify `setPlayingIndices` called correctly | ✅ |
| `PlaybackController` | `handleMetaMessage` — END_OF_TRACK message calls `stop()` | unit | none | missing | Add unit test: assert state becomes STOPPED and notification posted | ✅ |
| `PlaybackController` | `updatePlayingNote` — clears previous line highlight when line changes | unit | none | missing | Add unit test: set `previousPlayingLine`, call `updatePlayingNote` with different line, verify old `setPlayingIndices(-1,-1)` | ✅ |
| `PlaybackController` | `updatePlayingNote` — does not clear previous line when line unchanged | unit | none | missing | Add unit test: same line index, verify previous line component NOT cleared | ✅ |
| `PlaybackController` | `applyPrefsDuringPlayback` — does nothing when not PLAYING | unit | none | missing | Add unit test: set state STOPPED or PAUSED, assert no sequencer interaction | ✅ |
| `PlaybackController` | `applyPrefsDuringPlayback` — while PLAYING: stops, rebuilds sequence, restores tick, restarts | unit | none | missing | Add unit test: mock sequencer, verify stop/setSequence/setTickPosition/start sequence | ✅ |
| `PlaybackController` | `setLoopSequence` — sets loop continuously when pref LOOP_PLAYBACK=true and selection is not a single note | unit | none | missing | Add unit test: mock `Prefs.getBoolean`, verify `setLoopCount(Sequencer.LOOP_CONTINUOUSLY)` | ✅ |
| `PlaybackController` | `setLoopSequence` — does not loop when selection is a single note (begin==end), even if pref is true | unit | none | missing | Add unit test: selection with begin==end, assert `setLoopCount(0)` | ✅ |
| `PlaybackController` | `buildSequenceForSelection` — null selection builds full sequence | unit | none | missing | Add unit test: verify `MidiSequenceBuilder.buildFullSequence()` path | ✅ |
| `PlaybackController` | `buildSequenceForSelection` — non-null selection builds from note to end | unit | none | missing | Add unit test: verify `buildFromNoteToEnd(lineIndex, begin)` path | ✅ |
| `PlaybackController` | `getPlaybackSettings` / `applySettings` round-trip preserves all fields | unit | none | missing | Add unit test: set fields, `getPlaybackSettings()`, `applySettings()`, verify fields restored | ✅ |
| `PlaybackController` | `applyVolumeFromPrefs` — delegates to `MidiController.setPlaybackVolume` with pref value | unit | none | missing | Add unit test: mock `Prefs.getInt` and `MidiController`, verify forwarding | ✅ |
| `MidiController` | `setPlaybackVolume` — percent 50..100 linearly scales to MIDI CC7 values ~64..127 (boundary/midpoint values) | unit | none | missing | Pure arithmetic: add unit test for boundary values (50→64, 100→127, 75→~96) | ✅ |
| `MidiController` | `setPlaybackVolume` — percent below 50 clamps to 50; above 100 clamps to 100 | unit | none | missing | Add unit test for out-of-range inputs | ✅ |
| `MidiController` | `setPlaybackInstrument` — sends PROGRAM_CHANGE on channel 0 with clamped program number | unit | none | missing | Add unit test: mock `Receiver`, verify `ShortMessage.PROGRAM_CHANGE` with correct channel and data | ✅ |
| `MidiController` | `isPlaying` — returns false when sequencer is null | unit | none | missing | Add unit test: null sequencer path | ✅ |
| `MidiController` | `isPlaying` — delegates to `sequencer.isRunning()` when sequencer is non-null | unit | none | missing | Add unit test: mock sequencer | ✅ |
| `MidiController` | `closeMidi` — idempotent: second call does not close resources again | unit | none | missing | Add unit test: call twice, verify `midiReceiver.close()` called exactly once | ✅ |
| `MidiController` | `openMidi` / `openSynthesizerWithSoundbank` / `loadBundledSoundbank` / `extractSoundfontToTempFile` — full MIDI init path requires real MIDI hardware | none | — | none | Real hardware I/O; cannot be meaningfully mocked in unit or e2e context | — |
| `MidiController` | `initChannels` / `initChannel` / `reinitChannels` — GM reset + CC setup; all wired to real `Receiver` | none | — | none | Side-effect-only hardware output; no pure-logic testable path | — |
| `PlayThread` | `run` — when `playNoteOn=true` sends NOTE_ON, waits `NOTE_DURATION_MS`, sends NOTE_OFF | unit | none | missing | Add unit test: mock `MidiController.midiReceiver`, run thread, verify message sequence | ✅ |
| `PlayThread` | `run` — when `playNoteOn=false` skips NOTE_ON but still sends NOTE_OFF after delay | unit | none | missing | Add unit test: same setup, verify only NOTE_OFF sent | ✅ |
| `PlayThread` | `sendNoteOn` — no-op when `midiReceiver` is null | unit | none | missing | Add unit test: null receiver, no exception | ✅ |
| `PlayThread` | `sendNoteOff` — no-op when `midiReceiver` is null | unit | none | missing | Add unit test: null receiver, no exception | ✅ |
| `PlayThread` | `sendNoteOn` — sends bank-select + program-change + NOTE_ON messages with correct pitch and velocity | unit | none | missing | Add unit test: mock receiver, verify message types and values | ✅ |
| `PlayThread` | `sendNoteOff` — sends NOTE_OFF with correct pitch | unit | none | missing | Add unit test: mock receiver, verify NOTE_OFF message | ✅ |
| `PlayPauseAction` | `actionPerformed` — toggles action icon/name then calls `PlaybackController.togglePlayPause()` | unit | none | missing | Add unit test: verify both icon toggle and `togglePlayPause` called | ⬜ |
| `PlayPauseAction` | `playbackStateDidChange` (STOPPED) — calls `toggleToPlay` (sets play name/icon/tooltip) | unit | none | missing | Add unit test: set state to PAUSE name, post STOPPED notification, verify name reverts to PLAY_NAME | ⬜ |
| `PlayPauseAction` | `toggleAction` — when name is PLAY_NAME switches to pause labels; when pause name switches back | unit | none | missing | Add unit test: call toggleAction twice, verify round-trip | ⬜ |
| `PlayPauseAction` | `DISABLE_WHEN_PLAYING` flag not set — action stays enabled during playback (it is the pause button) | unit | `LyricEditorActionAuditTest.testAllToolbarActionsCarryDisableWhenEditingTextFlag` | inadequate | Audit test only checks `DISABLE_WHEN_EDITING_TEXT`; no test verifies the action remains enabled during PLAYING state | ⬜ |
| `RewindAction` | `actionPerformed` — calls `PlaybackController.rewindToBeginning()` (thin dispatcher) | unit | none | missing | Add unit test: mock `PlaybackController`, verify `rewindToBeginning()` called | ⬜ |
| `LoopPlaybackAction` | `actionPerformed` — posts `ToggleLoopPlaybackCommand` with `isSelected()` value | unit | none | missing | Add unit test: mock `MessageCenter`, invoke action, verify command posted with correct payload | ⬜ |
| `PlayWithRepeatsAction` | `actionPerformed` — posts `TogglePlayWithRepeatsCommand` with `isSelected()` value | unit | none | missing | Add unit test: same pattern | ⬜ |
| `SequencerAction` | constructor delegation to `UIAction` | none | — | none | Pure super-call delegation with no own logic | — |
| `MidiMetaMessageTypes` | Constants hold correct MIDI spec hex values | none | — | none | Pure constants holder; no logic | — |

**Notes:**

The `PlaybackController` class is the highest-risk gap in the entire package. It is a static-method singleton implementing a multi-state transport machine (STOPPED/PAUSED/PLAYING) with six distinct state-transition paths (`togglePlayPause` alone has four branches) and a non-trivial meta-message callback that decodes packed binary data into line/note indices. Not one of these behaviors has a unit test. The four `selectionDidChange` tests that exist are the only coverage. Every state transition, every notification post, every highlight-coordination sequence, and the `applyPrefsDuringPlayback` spin-wait restart path are completely untested. Because all public methods are static and all dependencies (`MidiController.sequencer`, `registeredScore`) are settable via test-visible setters/statics, these are straightforward unit targets — no e2e or real hardware is required.

`MidiController.setPlaybackVolume` contains a concrete arithmetic formula (`Math.round(Math.clamp(percent, 50, 100) / 100f * 127)`) whose boundary behavior (50%→64, 100%→127) and clamping could silently regress. Similarly, `PlayThread.sendNoteOn`/`sendNoteOff` are static utility methods that can be unit-tested by injecting a mock `Receiver` into `MidiController.midiReceiver`. The action thin-dispatcher gap is present for all four `*Action` classes: `actionPerformed` on `PlayPauseAction`, `RewindAction`, `LoopPlaybackAction`, and `PlayWithRepeatsAction` each contain dispatch logic (icon toggle, command post, direct controller call) that is never exercised by any test.

The `LyricEditorActionAuditTest` (T25) provides coverage of `DISABLE_WHEN_EDITING_TEXT` for all four playback actions, which is a useful structural audit. `NoteDragHandlerTest` references `PlayThread` and `MidiController` only as mocked-out infrastructure to suppress side effects — no behavior of those classes is validated. The midi-package tests (`GlissandoMidiHelperTest`, `VelocityMapTest`, `GlissandoMidiIntegrationTest`) are well-structured and test adjacent MIDI logic adequately, but they do not touch any class in this package.

**Tally:** 49 rows — 4 adequate · 40 missing · 1 inadequate · 0 wrong-level · 4 none · 0 redundant.

**Dead code:** none found. All classes and public methods have verified callers in `src/main` or `src/test`.

**Production observations:** `PlaybackController.setSequenceToPlayFromSelection` uses identity comparison (`sequence != sequencer.getSequence()`) guarded by `//noinspection ObjectEquality` — this is correct for reference equality on `Sequence` objects but is easy to misread; worth a clarifying comment. `PlayThread` extends `Thread` directly rather than implementing `Runnable`; minor style issue but not a bug. The `setupInstrument()` method in `PlayThread` throws `RuntimeError.exit(...)` when `midiReceiver` is null, making it fatal in a path that `sendNoteOn` already guards with a null check and silent return — the guard in `sendNoteOn` makes the fatal path unreachable, but it is confusing.

