# Bindings Outside Dialogs

Makes `songscribe.binding` the state layer for `Song`, `ScoreView` and the action
constants, replacing three mirrors, five hand-rolled derived-value caches and the
notification fan-in that keeps action enablement current.

Each phase compiles only at its end. Snapshot with
`git add -A && git stash store -m "Finished phase N" "$(git stash create)"` at each
phase boundary.

## Open decisions

Four answers are still needed. Each phase below is written to the recommendation;
when an answer differs, rewrite that phase to state it and delete the entry here.

1. **Storage form.** Recommendation: `ValueProperty` — the field is deleted and the
   property *is* the storage, giving one store and transition-only notification.
   The alternative, `ViewProperty` over the retained field, needs a manual
   `notifyObservers` on every write path, which is the mirror-and-forget defect this
   change exists to remove. `ViewProperty` stays correct only where the storage
   genuinely lives elsewhere: `Prefs`'s JSON store, a Swing control.
2. **Whether the mode, zoom and selection notifications survive.** Recommendation:
   they do. Their remaining subscribers are effects, and converting them means giving
   four more classes an owner and a `Bindings` for no gain. `Prefs` already runs a
   property channel and a notification channel side by side.
3. **`Tempo`.** Recommendation: leave it a plain field (Phase 2, task 5). Converting
   it to a record makes it bindable and consistent with `SongMetadata`, but touches
   every tempo mutation site; that count has not been taken.
4. **Whether Phase 6 lands in this pass.** It is the largest item and deletes the most
   code. Phases 1–5 stand on their own if it waits.

## Phase 1 — Owners

`Bindings` gains owners other than dialogs. Its code is unchanged; what changes is
who holds one and what the contract says. All three owners already have the lifetime
they need:

| Owner | Disposal point | Carries |
|---|---|---|
| `Song` | `Song.dispose()`, called by `ScoreView.setSong` on the outgoing document | `modified`, `metadata`, `activeVerse`, `lineWidthSs` |
| `Actions` | `deinitialize()`, called by `initialize()` on re-init | per-action enabled state, mode, rest mode |
| `ScoreView` | none — every instance is process-lifetime | mode, zoom, selection, `documentFonts` and everything derived from it |

1. `Song` — add `private final Bindings bindings = new Bindings();` and cancel it in
   `Song.dispose()`.
2. `Actions` — add a static `Bindings`, created in `initialize` and disposed in
   `deinitialize`, so a retired generation's bindings go with its actions.
3. `ScoreView` — add `private final Bindings bindings = new Bindings();` and **no**
   `dispose()`. Every `ScoreView` built today (`MainFrame:506`, `PDFConverter:137`,
   `SVGConverter:51`, `UIConverter:107`) lives for its process — the converters build
   one per batch, not per file — so a `dispose()` now would be unreachable. Add a
   `Lifecycle` clause to `ScoreView`'s Javadoc stating that, and pointing at the
   converter-rewrite paragraph in `docs/lifecycle.md`.
4. Rewrite the ownership clause in `Bindings`'s class Javadoc, `WriteGuard`'s Javadoc,
   `.claude/guides/bindings.md` and `docs/lifecycle.md` to read *one owner with a
   disposal point* rather than *one dialog*. State in `Bindings`'s Javadoc why an
   owner's field rather than a global registry: a registry entry is a key, not an
   owner, so nothing ties an entry's lifetime to the object's, a class-keyed entry
   collapses two live instances onto one binding set, and the registry holds every
   binding set — and everything its transforms and effects captured — for the run.
   What needs to be globally reachable is the value, not the binding set.

## Phase 2 — Song state

A field that anything outside `Song` needs to follow becomes a `ValueProperty`, which
*is* the storage; the field is deleted rather than mirrored. `ValueProperty` notifies
only on a real transition, so a repeated write of the same value notifies once.

1. `modified` → `ValueProperty<Boolean>`. `isModified()` and `setModified()` delegate;
   publish `ObservableValue<Boolean> modified()` — not `Property`, because no caller
   outside `Song` writes it.
2. Route the two writes that bypass the setter — `Song.java:353` (`documentWasSaved`)
   and `Song.java:421` (load) — through `setModified`. Assigning the field leaves
   every observer unnotified.
3. `metadata` → `ValueProperty<SongMetadata>`; publish `ObservableValue<SongMetadata>`.
4. `activeVerse` → `ValueProperty<Integer>`; `lineWidthSs` → `ValueProperty<Double>`.
5. `tempo` stays a plain field. `Tempo` is a mutable class, so a property over it would
   notify nobody when it is edited in place. State that in `Song.tempo`'s contract.
6. `MainFrame.updateTitle` becomes one binding on `MainFrame`'s own state plus
   `song.modified()`. Delete `songDidChange`, `documentDidLoad`, `documentWasSaved`
   and `undoStateDidChange` from `MainFrame`. The `updateUI` override still repaints
   the title from `UIManager` colours, which no property carries; keep that call.

## Phase 3 — Delete the mode mirror

Mode has two stores today: the `SELECT`/`EDIT` `ModeAction` pair's selected state, and
`ScoreViewState.mode`, written by `ScoreViewController.modeDidChange`.

1. `ScoreView` gains `ValueProperty<Mode>`; publish `Property<Mode> mode()`.
2. Delete `ScoreViewState`. `ScoreView.getMode`/`setMode` delegate to the property.
3. Delete the mirror write in `ScoreViewController.modeDidChange`.
4. Bind `SELECT_MODE_ACTION` and `EDIT_MODE_ACTION`'s selected state to the property in
   `Actions.initialize`, so the toggle and the view state cannot disagree.
5. `ModeDidChangeNotification` stays. Its remaining subscribers — `PreviewElementManager`,
   `EditModeManager`, `ModeCycleButton` — are effects, not values.
6. `Actions.REST_ACTION.isSelected()`, read as state by `UIAction.enableInRestMode`, gets
   the same treatment: a `ValueProperty<Boolean>` on `Actions` that the action's selected
   state binds to.

## Phase 4 — Zoom observability

Reads stay on demand through `ScoreComponent.getViewScale()`; what is added is
notification, so the effects riding `ZoomDidChangeNotification` become bindings.

1. `ViewScale.zoomPercent` → `ValueProperty<Integer>`; `getZoomPercent`/`setZoomPercent`
   delegate; publish `ObservableValue<Integer> zoomPercent()`. `ViewScale.IDENTITY` is
   never written, so its property never notifies.
2. Convert `ZoomAction.updateEnabledState`, `ZoomStatusBarPanel` and
   `ScoreView.zoomDidChangeRefreshOverlayBounds` to bindings on that value.
3. `ScoreView.zoomDidChangeApplyZoom` stays a handler — it performs the change rather
   than reacting to it.
4. Delete the handler-priority requirement from `ZoomDidChangeNotification`'s contract
   and from `docs/zoom.md` once no reader depends on handler ordering. A `computed`
   derives its dependency order; the priority integer asserts one.

## Phase 5 — Derived values

Five hand-rolled caches become `computed`s. Each currently pairs a stored result with a
manual invalidation and an equality guard against the input it was built from.

1. `DocumentFontManager.lyricRenderMetrics` →
   `bindings.computed(() -> LyricRenderMetrics.forFont(fonts.get().getLyricsFont()))`.
   Delete `rebuildLyricRenderMetrics`, the equality guard at line 180, the
   `@Nullable findLyricRenderMetrics()` accessor and the `RuntimeError.exit` in
   `getLyricRenderMetrics`. A `computed` evaluates on first read, so there is no state
   in which the metrics are unbuilt and no second accessor for it.
2. Delete the `rebuildLyricRenderMetrics` call from `StaffPanel.ensureAllLineLayouts`,
   and the ordering obligation the field comment places on it.
3. `documentFonts` → `ValueProperty<DocumentFonts>`. It is immutable with a real
   `equals`, so it satisfies replace-never-mutate.
4. `SelectionCoordinator`'s content cache (`contentCacheSelection`, `hasDurations`,
   `hasRests`) and applicability cache (`applicabilityCacheSelection`,
   `applicabilityCache`) → `computed`s over a `ValueProperty<@Nullable Selection>`.
   `Selection` is a sealed interface of two records.

## Phase 6 — Action enablement

`UIAction.updateEnabledState` is a conjunction of fourteen predicates over ten state
sources, recomputed by twelve `@Handler` methods that each call it with no argument.
Every predicate short-circuits on `hasFlag(...)` before reading state, so under the
dependency tracker each action subscribes to exactly the state its own flags consult —
which no notification handler can express.

1. Add `Widgets.enabled(Action)` returning `WritableValue<Boolean>` over `setEnabled`.
   Its contract names `Action.setEnabled` as the write route and states that it reports
   nothing back, so the view is write-only.
2. Rename `updateEnabledState()` to `computeEnabled()` returning the conjunction without
   calling `setEnabled`. Subclasses that override it keep chaining `super`.
3. In `UIAction`'s constructor, bind `Widgets.enabled(this)` to
   `Actions.bindings().computed(this::computeEnabled)`.
4. Fold the post-hoc writes in `TempoChangeAction:62`, `StemDirectionAction:107` and
   `ToggleNotationAction:206` into their `computeEnabled` bodies. A `setEnabled` written
   after the computation is overwritten by the binding's next evaluation.
5. Delete the twelve `@Handler` methods in `UIAction` that exist only to recompute, and
   the `@Handler` overrides in subclasses that do the same.
6. `enableFromMidiState` reads `MidiController.isAvailable()`, which is not observable.
   Give `MidiController` a `ValueProperty<Boolean>` for it, or state in `computeEnabled`'s
   contract that MIDI availability is read once at binding time.

## Phase 7 — Tests

Three tests, pending the user's veto; nothing here is written until that is given.

| Test | Kind | What would make it unnecessary |
|---|---|---|
| A `computed` over `documentFonts` yields metrics for the current lyrics font after a font replacement | invariant spanning several calls | nothing — this is the promise Phase 5 replaces a manual guard with |
| An action whose flags exclude playback acquires no dependency on playback state | invariant spanning several calls | nothing — per-action dependency sets are the point of Phase 6 and no type carries them |
| `setModified(false)` twice notifies once | invariant spanning several calls | nothing, though `BindingsTest` may already assert this of `ValueProperty`; check before adding |

Two further tests are deliberately absent, and the design is why. Do not add them:

- **Mode written through the property is what `getMode()` answers.** Phase 3 leaves one
  store, so there is no disagreement to catch.
- **The title string for a modified and an unmodified document.** Phase 2 leaves one
  binding, and a test over it pins the binding's own plumbing rather than a promise.
