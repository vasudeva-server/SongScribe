# Singleton Lifecycle Contracts — Phase 8 Decisions

Output of Phase 8 of [`contract-driven-rollout.md`](./contract-driven-rollout.md).
Phase 9 applies §5 of this document. Everything else is recorded here so the
package phases that own it can act on it.

Input: discussion doc §6.3, which lists ~11 members that exist because singletons
hold static mutable state and MBassador subscriptions production never tears
down, and resolves them as *incomplete lifecycle contracts* rather than as
test-only surface to delete (D5).

**That resolution holds for 6 of the 11 and does not hold for the rest.** §6
records the reclassifications with their real destinations.

---

## 1. The finding that reframes all of this

`docs/messages.md:10` states the tier-3 contract for the message bus:

> **Weak references.** MBassador holds subscribers weakly. Every subscriber MUST
> be reachable via a strong reference (static field, instance field on a
> long-lived owner) for its intended lifetime. **There is NO need to unsubscribe;**
> when the subscriber is garbage collected, it will no longer receive messages.

The emphasized clause is false, and it is why every detach method in this
inventory is labelled *test-only* instead of *the other half of the lifecycle*.
It holds for one case only: a subscriber whose lifetime is exactly its owner's,
where the owner is itself never retired. It fails in two shapes that both occur
in production:

- **A subscriber held in a static field that is reassigned.** `Actions.initialize`
  replaces every action constant. The outgoing generation loses its strong
  reference but stays registered until a GC that may never come, and MBassador
  keeps delivering to it. `Actions.unsubscribeForTest`'s own Javadoc describes
  the consequence — *"linger as weakly-held zombie subscribers and fire their
  `@Handler` logic against a stale mock"* — as if it were a test phenomenon. It
  is a property of the bus.
- **An object retired before the process ends.** `SVGConverter.java:60` and
  `PDFConverter.java:146` construct a `ScoreView` per converted file. Each one
  constructs a `SelectionCoordinator`, which subscribes itself and constructs an
  `ActionReflector`, which subscribes itself. Nothing detaches either. Across a
  batch conversion these accumulate and keep handling notifications on behalf of
  views the converter has finished with.

**Decision:** `docs/messages.md` gains a *Detaching* section stating the
obligation and when it applies. Draft in §5.6. Without it, every teardown method
Phase 9 writes contradicts the tier-3 doc it is supposed to follow.

---

## 2. Vocabulary

Three distinct operations are currently spelled `resetForTest` /
`unsubscribeForTest` / `resetOverlaysForTest` interchangeably. They are not the
same operation, and the names must stop implying that they are.

| Name | Means | Applies to |
|---|---|---|
| `initialize(...)` | establishes the subsystem: constructs what it owns, attaches it to the bus | a static subsystem holder |
| `shutdown()` | undoes exactly what `initialize` established; the subsystem is unusable until `initialize` is called again | the inverse of `initialize` |
| `dispose()` | detaches an *instance* that attached itself in its constructor; the instance is unusable afterwards | an object with a constructor-side subscription |
| `reset()` | returns accumulated state to its as-constructed baseline; the object stays usable | state that grows during normal use |

`shutdown()` does not collide with `songscribe.lifecycle.Shutdown` at a call
site: `Actions.shutdown()` and `Shutdown.now()` read distinctly, and the two
concern different things — `Shutdown` is the process quit registry, `shutdown()`
is one subsystem's teardown.

### The scoping rule

**Teardown undoes what initialization established, and nothing more.**

`PlaybackController.initialize(MainFrame)` establishes four action constants. It
does not establish the sequencer, the registered score, or the playback state —
those come from `register` and `play`. So `PlaybackController.shutdown()`
unsubscribes the four constants and stops there. A teardown that reached into
playback state would be undoing something it never did, and no caller could
predict where it stops.

### On members that will have no production caller

`Actions.shutdown()`, `PlaybackController.shutdown()` and
`UndoController.shutdown()` will have no production call site. The application
initializes each once and exits without tearing down; unsubscribing on the way
out of the process is work with no observable effect.

They are correct anyway, and this is exactly D5's position: a class that attaches
itself to a process-global bus and offers no way to detach has an incomplete
contract, tests or no tests. What makes them legitimate is that they complete a
stated lifecycle, not that a test wanted them.

**Consequence for the `check` skill.** Phase 5 item 4 makes "every reference
resolves under `src/test/`" a hard finding on the Contract & API axis. These
three members will trip it permanently. The axis needs one stated exception: *a
documented inverse of a documented initializer, on a class whose class Javadoc
states the lifecycle contract, is not test-only surface.* Without that exception
Phase 9's output is flagged forever and the finding stops meaning anything.
Recorded here for Phase 13's revision pass.

`SelectionCoordinator.dispose()` needs no exception — §5.4 gives it production
callers.

---

## 3. Summary of dispositions

| Member | Disposition | Phase |
|---|---|---|
| `Actions.resetForTest` + `unsubscribeForTest` | merge → `shutdown()` | 9 |
| `PlaybackController.unsubscribeForTest` | → `shutdown()` | 9 |
| `UndoController.unsubscribeForTest` | → `shutdown()` | 9 |
| `UndoController.resetForTest` | → `reset()`, shared with `documentDidLoad` | 9 |
| `SelectionCoordinator.unsubscribeForTest` | → `dispose()`, wired to `ScoreView.dispose()` | 9 |
| `Prefs.removeObsoleteKeysForTest` + 3 siblings | not lifecycle → extract `PrefsStore` | `prefs` |
| `Prefs.parseJsonValueForTest` | not lifecycle → promote to `JsonValues.toJavaValue` | 9 |
| `Prefs.getRawStored` ×2, `putRawStored` | not in the §6 inventory; same restructure | `prefs` |
| `RecentDocumentsManager.resetForTest` | delete — `clear()` already exists | `prefs` |
| `RecentDocumentsManager.reloadForTest` | delete — extract `readRecents` | `prefs` |
| `MainFrame.clearStartupErrorsForTest` | not lifecycle → extract `StartupErrorQueue` | `ui/component` |
| `PreviewElementManager.resetOverlaysForTest` | not lifecycle → host-owned overlays | `ui/component` |
| `PreferencesDialog.resetInstrumentsForTesting` | lifecycle, but of the *synthesizer* → move to `MidiController` | `ui/playback` |

---

## 4. Facts the decisions rest on

- `UIAction`'s constructor calls `MessageCenter.subscribe(this)`
  (`UIAction.java:297`). Every constant `Actions.initialize` and
  `PlaybackController.initialize` construct is therefore a live subscriber from
  the moment it exists.
- `SelectionCoordinator`'s constructor subscribes (`SelectionCoordinator.java:122`);
  the `ActionReflector` it constructs subscribes itself (`ActionReflector.java:84`).
- `ScoreView.selectionCoordinator` is `final` and never replaced
  (`ScoreView.java:188,243`). One coordinator per view, for the view's lifetime.
- `Actions.initialize` is called once in production (`MainFrame.initFrame`,
  `MainFrame.java:490`), immediately followed by `PlaybackController.initialize`
  (`:491`). Tests call both per test.
- `UndoController.initialize()` is called from `Actions.initialize`
  (`Actions.java:205`) and from two undo tests.
- `MainFrame.drainStartupErrors()` iterates `STARTUP_ERRORS` and never clears it.
- `Prefs`'s constructor runs `loadStore` → `removeObsoleteKeys` →
  `removeSystemDefaultKeysFromStore` → `migrate` (`Prefs.java:113-116`), once per
  JVM. The test harness redirects the file via the `songscribe.prefsDir` system
  property (`resolvePrefsFile`).
- `PreferencesDialog.ensureInstrumentsLoaded` reads `MidiController.synthesizer`,
  which `openMidi()` sets once at startup — `openMidi` has exactly one caller,
  `openMidiAsync`.

---

## 5. The lifecycle contracts — Phase 9 applies these

Each entry gives the class Javadoc to write, the member changes, and the call
sites that move. Rename with `jet_brains_rename` so call sites follow.

### 5.1 `Actions` — `src/main/java/songscribe/ui/action/Actions.java`

**Lifecycle contract (append to the class Javadoc):**

> <h2>Lifecycle</h2>
> {@link #initialize(MainFrame)} establishes a <em>generation</em>: every action
> constant is constructed against one owner frame, each subscribing itself to the
> message bus, together with this class's document-load reset handler. The
> app-menu cache is invalidated so {@link #getAppMenuActions()} rebuilds from the
> new generation.
>
> <p>Re-initialization is permitted. Each call retires the previous generation
> first — the constants of a retired generation are off the bus and must not be
> used. Without that step a replaced generation would keep receiving
> notifications: the bus holds subscribers weakly, so dropping the static
> reference does not detach them (see {@code docs/messages.md}).
>
> <p>{@link #shutdown()} retires the current generation and clears the owner.
> Nothing survives it. The constants are {@code @NonNull} static fields, so they
> are not nulled; after {@code shutdown()} they still reference retired actions
> and must not be read until {@link #initialize} is called again.

**Members:**

- `public static void resetForTest()` and `public static void unsubscribeForTest()`
  → **one `public static void shutdown()`**. Body is the union: unsubscribe
  `RESET_HANDLER`, unsubscribe every action constant, `mainFrame = null`,
  `appMenuActions = null`. They are two halves of one operation and
  `MainFrameMockTest` already calls both in the same teardown.

  ```java
  /**
   * Retires the current generation of action constants and clears the owner.
   *
   * <p>Every action constant and the document-load reset handler are removed from
   * the message bus, and the app-menu cache is dropped. Idempotent: calling it
   * without a preceding {@link #initialize} is a no-op.
   *
   * <p>After this returns, no constant in this class may be read until
   * {@link #initialize} is called again — the fields are non-null but reference
   * retired actions.
   */
  public static void shutdown()
  ```

- `initialize(MainFrame)` retires the previous generation before constructing the
  new one — `if (mainFrame != null) { shutdown(); }` as its first statement. This
  is the production fix; the current Javadoc promises *"Calling this method again
  replaces all constants with freshly constructed instances"* while leaving the
  replaced ones subscribed. Its Javadoc gains the retire step.

- Extract the reflection loop duplicated between `getAppMenuActions()` and the
  unsubscribe path — both walk `Actions.class.getDeclaredFields()` filtering
  `PUBLIC | STATIC` and both log the same `IllegalAccessException` warning — into
  `private static void forEachActionConstant(Consumer<@Nullable Object> action)`.

**Call sites:** `MainFrameMockTest.tearDownMainFrameMock` (two calls collapse to
one), `UnitTest.unsubscribeActionSubscribers`, `MainFrameTest.InitFrameOrderingContract`,
`GraceModeManagerTest` (8 nested-class teardowns), `MenuControllerTest.WithController`.

### 5.2 `PlaybackController` — `src/main/java/songscribe/ui/playback/PlaybackController.java`

**Lifecycle contract (class Javadoc — the class currently has none):**

> <h2>Lifecycle</h2>
> {@link #initialize(MainFrame)} establishes the four playback action constants
> against one owner frame; each subscribes itself to the message bus.
> {@link #shutdown()} removes them. Re-initialization is permitted and retires the
> previous four first.
>
> <p>Playback state — the registered score, the transport state, the sequencer —
> is established by {@link #register} and {@link #play}, not by
> {@code initialize}, and is therefore not {@code shutdown}'s to undo. Stopping
> playback is {@link #stop()}.

**Members:**

- `public static void unsubscribeForTest()` → `public static void shutdown()`,
  same body.
- `initialize(MainFrame)` retires the previous four constants first, for the same
  reason as `Actions.initialize`.

**Call sites:** `MainFrameMockTest.tearDownMainFrameMock`.

### 5.3 `UndoController` — `src/main/java/songscribe/undo/UndoController.java`

**Lifecycle contract (append to the class Javadoc):**

> <h2>Lifecycle</h2>
> {@link #initialize()} attaches the singleton to the message bus and is
> idempotent — subscription is deliberately not a constructor side effect, since
> the singleton is also constructed lazily the first time
> {@link Song#beginModification} reads the pending op-name.
>
> <p>{@link #reset()} returns the stacks and clean markers to the empty baseline
> while leaving the controller attached and recording; a document load does
> exactly this. {@link #shutdown()} detaches it, after which it records nothing
> until {@link #initialize()} is called again.

**Members:**

- `static void resetForTest()` → `public static void reset()`. Clears both
  stacks, `cleanStep = BASELINE`, `cleanValid = true`, `pendingOpName = null`,
  and posts `UndoStateDidChangeNotification`. It **does not subscribe** —
  attaching is `initialize()`'s job, and the current method conflating the two is
  what forced its Javadoc to explain itself in terms of test teardown.

  ```java
  /**
   * Returns undo state to the empty baseline: both stacks cleared, the document
   * marked clean, no pending op-name. Posts {@link UndoStateDidChangeNotification}
   * so the Edit menu follows.
   *
   * <p>Does not attach or detach the controller — see {@link #initialize()} and
   * {@link #shutdown()}. Loading a document performs exactly this reset.
   */
  public static void reset()
  ```

- `public static void unsubscribeForTest()` → `public static void shutdown()`.
  Unsubscribes the singleton, clears both stacks and the pending op-name, sets
  `subscribed = false`. Posts nothing — the bus is being left, and a
  notification about state nobody is listening for is noise.

- `documentDidLoad(DocumentDidLoadNotification)` is rewritten to call `reset()`.
  Its five-line body currently duplicates `resetForTest`'s.

  **Contract change, stated explicitly:** `documentDidLoad` will now also clear
  `pendingOpName`, which it does not today. A pending op-name belongs to an edit
  in the outgoing document; carrying it into the incoming one would label the
  first edit of the new document with the last intent of the old. This is a
  correction, and it is why the two bodies should never have been separate copies.

**Call sites:** `UnitTest.unsubscribeActionSubscribers`;
`UndoOpNameLabelTest.setUp` and `SongSettingsCoalesceTest.setUp`, which become
`UndoController.initialize(); UndoController.reset();`.

### 5.4 `SelectionCoordinator` — `src/main/java/songscribe/ui/selection/SelectionCoordinator.java`

**Lifecycle contract (class Javadoc):**

> <h2>Lifecycle</h2>
> Constructing a coordinator puts <em>two</em> listeners on the message bus: the
> coordinator itself and the {@link ActionReflector} it owns. {@link #dispose()}
> removes both. The owning {@link ScoreView} calls it when the view is retired;
> a coordinator must not be used after it.
>
> <p>A view that outlives the process — the interactive main window — never
> disposes. A view created for one conversion and discarded must, or its two
> listeners keep handling notifications on behalf of a view nobody is looking at.

**Members:**

- `public void unsubscribeForTest()` → `public void dispose()`, same body, plus
  the idempotence note in its Javadoc.

**Production wiring — required, not optional.** Without it the rename relabels
test-only surface instead of removing it:

- `ScoreView` gains `public void dispose()`, calling `selectionCoordinator.dispose()`.
- `SVGConverter.java:60` and `PDFConverter.java:146` construct a `ScoreView` as a
  local per conversion — both call `dispose()` when finished with it.
- `UIConverter.java:86` holds one `ScoreView` for its window's lifetime; it
  disposes when the window closes, if it has such a hook. If it does not, record
  it rather than inventing one.

Whether `ScoreViewController` and the other per-view subscribers also belong in
`ScoreView.dispose()` is the `ui/component` phase's call. Phase 9 adds the method
with the coordinator in it and states in the Javadoc that the list is not yet
complete.

**Call sites:** `BeamToggleTest.loadFixtureData`, `TieToggleTest.loadFixtureData`,
`MusicEditOperationsSlideToggleTest.setUp`,
`SelectionCoordinatorRangeTest.testPitchShiftThatCollapsesAGraceNoteIntoItsHostPreservesTheAnchor`.

### 5.5 `Prefs.parseJsonValueForTest` — promote, do not rename

Phase 9 already owns this member (rollout §6.2, "misnamed internal API"). The
disposition is not a rename of the wrapper.

`parseJsonValue(JsonElement)` is a pure coercion from a Gson element to the Java
value the store holds. It reads no `Prefs` state and is not about preferences at
all. It is §2.7's "distinct concept with a stable contract", so it is promoted to
one:

- New file `src/main/java/songscribe/prefs/JsonValues.java`, with
  `public static @Nullable Object toJavaValue(JsonElement element)` carrying a
  full contract — what each JSON kind maps to, and what `null` means.
- `Prefs.parseJsonValue` and `Prefs.parseJsonValueForTest` both go; `Prefs` calls
  `JsonValues.toJavaValue`.

The three `SMuFLMetadata` members in rollout §6.2 are unaffected by this document
— they are not lifecycle and Phase 9's existing instruction for them stands.

### 5.6 `docs/messages.md` — the tier-3 amendment

Phase 9 replaces the last sentence of the **Weak references** paragraph and adds:

> ### Detaching
>
> Weak references are not a substitute for unsubscribing. Until the collector
> runs, a subscriber that has lost its last strong reference is still registered
> and still receives every message — and the collector may never run.
>
> A subscriber whose lifetime is its owner's, where the owner lives as long as
> the process, never needs to detach. Two cases do:
>
> - **A static field that gets reassigned.** `Actions.initialize` replaces every
>   action constant; each replaced action stays subscribed. `initialize` retires
>   the outgoing generation for exactly this reason.
> - **An object retired before the process ends.** A `ScoreView` built for one
>   conversion, and the `SelectionCoordinator` and `ActionReflector` it owns, are
>   finished with when the conversion is. `ScoreView.dispose()` detaches them.
>
> The obligation belongs to whoever subscribed. A class that subscribes in its
> constructor states its detach method in its class Javadoc, under a `Lifecycle`
> heading, and names who calls it.

---

## 6. Reclassified — not lifecycle

Phase 8 task 3: where a member fits no legitimate lifecycle contract, say so.
These five do not. Phase 9 does not touch them.

### 6.1 `Prefs` — four wrappers plus three the name sweep missed

`removeObsoleteKeysForTest`, `removeSystemDefaultKeysFromStoreForTest`,
`writeTypedForTest` and `migrateForTest` are one-line delegations to private
instance methods. Their Javadoc says so: *"Exposes `removeObsoleteKeys()` for
direct invocation in tests."* That is visibility relaxation for private helpers
(§2.7), not a lifecycle gap — `Prefs` has no `initialize` and needs no teardown.

The name sweep also missed three members of the same kind, all documented
"Package-private for test use only": `getRawStored(PrefsKey)`,
`getRawStored(String)` and `putRawStored(String, Object)`.

The real defect is one level up. `removeObsoleteKeys`, `removeSystemDefaultKeysFromStore`
and `migrate` are steps of a load pipeline welded to the constructor of a
process-global singleton (`Prefs.java:113-116`), so the pipeline can run **once
per JVM**. A test cannot arrange an input for it, which is §4.5 exactly: the
pressure is in arranging state, and the answer is a constructor that takes it.

**Correct structure:** extract `PrefsStore` — constructed from a `Path`, running
load → obsolete-key removal → system-default pruning → migration, exposing the
resulting map and the raw get/put that the three unflagged members reach for.
`Prefs` holds one. All seven members disappear, and the pipeline gets tested
directly against files a test writes.

→ `prefs` foundations phase.

### 6.2 `RecentDocumentsManager` — both members delete

`resetForTest()` clears the in-memory list and calls `Prefs.reset(RECENT_FILES)`.
The public `clear()` already empties the list; the differences are that `clear()`
persists an empty list rather than removing the key, and posts
`RecentDocumentsDidChangeNotification`. Both leave `getRecents()` empty, which is
all the teardown needs. **Delete; tests call `clear()`.** Triage note: a test
asserting that nothing was posted would need adjusting.

`reloadForTest()` exposes the constructor's `loadFromPrefs()`, and
`loadFromPrefs` exists in the first place only to be exposed — its own Javadoc
says *"Extracted from the constructor so that `reloadForTest()` can exercise the
same logic without recreating the singleton."* Production has no reload: the
manager is the only writer of `RECENT_FILES` within a process.

The three tests that call it — `testReloadStripsNonExistentPathsAndPersists`,
`testReloadSkipsMalformedPathStrings`, `testReloadWithEmptyPrefsLoadsNothing` —
are testing one function: stored strings in, existing paths out. Extract it as
`static List<Path> readRecents(List<String> stored)` (skips unparseable entries,
drops paths that no longer exist), have `loadFromPrefs` call it and persist if it
pruned, and **delete `reloadForTest`**. The tests then call `readRecents`
directly with no singleton involved.

→ `prefs` foundations phase.

### 6.3 `MainFrame.clearStartupErrorsForTest` — and a defect underneath it

`STARTUP_ERRORS` is a process-global `ConcurrentLinkedQueue` with `enqueue`,
`firstFatal` and a `drainStartupErrors()` that **never empties it**. Items go in;
nothing takes them out. The member exists to compensate.

Two things:

1. **`drainStartupErrors()` must consume the queue.** Its name and its Javadoc
   both say "drains" and neither is true. Production has two call sites
   (`MainFrame.main`'s catch block and `reveal()`) that are mutually exclusive
   today, so nothing observes it — the promise is unkept rather than broken.
2. The queue is static global state with an incomplete API. Extract
   `StartupErrorQueue` — `enqueue`, `firstFatal`, `drain` — so a test constructs
   its own instead of clearing a global. `MainFrame` holds the one the
   application uses.

→ `ui/component` phase.

### 6.4 `PreviewElementManager.resetOverlaysForTest`

`installOverlay(OverlayHost)` exists; nothing uninstalls. The overlays live in
`PreviewOverlayRegistry`'s statics and the pending dwell in `PreviewCursorHider`'s.
There is no production event corresponding to "no overlays installed" — the state
this method produces exists only before the first install, at class load. So it
is not a missing lifecycle half; it is process-global mutable state that tests
have to scrub.

**Recommended:** the overlays belong to the `OverlayHost` that owns them, not to
a static registry. Each `ScoreView` then has its own and a discarded host takes
them with it, and no reset exists to name.

**Alternative:** add `uninstallOverlay(OverlayHost)` and call it from the
`ScoreView.dispose()` that §5.4 introduces. Smaller, and it leaves the statics —
so two conversions running in one process still share overlay state.

→ `ui/component` phase.

### 6.5 `PreferencesDialog.resetInstrumentsForTesting` — lifecycle, wrong class

This one *is* a lifecycle member, but the lifecycle is the synthesizer's and the
state is on a dialog. `instrumentsLoaded` / `instrumentStrings` /
`instrumentPrograms` are a process-global cache of `MidiController.synthesizer`'s
loaded soundbank, memoized on first dialog open and never invalidated, held by
the UI that happens to display it.

**Correct structure:** the list belongs to the MIDI layer, which owns the
synthesizer. `MidiController` exposes `List<Instrument> getInstruments()` over a
`record Instrument(String name, int program)`, populated when the synthesizer
opens and cleared when MIDI closes. The lifecycle is then real and stated: the
instruments are exactly those of the open synthesizer, and empty when none is
open.

That also removes a defect the current shape carries: `instrumentStrings` and
`instrumentPrograms` are parallel arrays kept in correspondence by index, which
is why `ensureInstrumentsLoaded` builds `Map.Entry` pairs, sorts them, and splits
them apart again. A `List<Instrument>` is sorted once and cannot desynchronize.

`ensureInstrumentsLoaded`, `getInstrumentStrings`, `getInstrumentPrograms` and
`resetInstrumentsForTesting` all leave `PreferencesDialog`.

→ `ui/playback` (the new API) and `ui/dialog` (the caller). Not Phase 9 — it is a
move plus a type change, not a rename.

---

## 7. Production defects found

Each is independent of any test. Listed for a decision on when, not whether.

1. **`Actions.initialize` and `PlaybackController.initialize` leak the generation
   they replace.** Both promise in Javadoc that calling again replaces the
   constants; both leave the replaced ones subscribed. Production calls each once,
   so it does not bite today. Fixed by §5.1 and §5.2.
2. **The converters leak two bus subscribers per converted file.**
   `SVGConverter.java:60` and `PDFConverter.java:146` build a `ScoreView` per
   conversion; its `SelectionCoordinator` and `ActionReflector` subscribe and are
   never detached. Fixed by §5.4's wiring.
3. **`MainFrame.drainStartupErrors()` does not drain.** §6.3.
4. **`docs/messages.md:10` is wrong** about unsubscription never being needed, and
   is the reason this whole category was mislabelled. §1, fixed by §5.6.
5. **`Prefs.resetAll()` has no production caller** — only `PrefsTest`. Either a
   "reset all preferences" feature that was never wired to a menu item, or dead
   public API. Needs a decision, not a rename.
6. **`Actions` runs the same reflection loop twice** — `getAppMenuActions` and
   `unsubscribeForTest` both walk the declared fields filtering `PUBLIC | STATIC`
   with the same catch and the same log line. Fixed by §5.1's extraction.
7. **`MidiController.synthesizer` is a public static mutable field** that
   `PreferencesDialogTest.cleanUpSynthesizer` assigns, and
   **`MidiController.failForTesting` is a public static test flag read by
   production code** at `MidiController.java:74`. Both are test-only surface the
   name sweep half-missed — `failForTesting` is a field, not a method, and
   `synthesizer` has no telltale name at all. Phase 10's sweep will find them
   independently; recorded here so it is not the first time anyone sees them.

---

## 8. What Phase 9 does

1. Apply §5.1–§5.5: five classes, the renames and merges above, each with its
   class-level `Lifecycle` Javadoc and a full contract on every renamed member.
   Use `jet_brains_rename` so call sites follow.
2. Apply §5.4's production wiring — `ScoreView.dispose()` and its three converter
   call sites. The rename is not finished without it.
3. Apply §5.6 to `docs/messages.md`.
4. Also in Phase 9, per the rollout: the three `SMuFLMetadata` members from
   rollout §6.2. Unaffected by this document.
5. `./scripts/compile.sh`, then `./scripts/test.sh`.

Phase 9 does **not** touch §6. Those go to the phases named there.
