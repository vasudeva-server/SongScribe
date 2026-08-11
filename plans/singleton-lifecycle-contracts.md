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
- **An object retired before the process ends.** Every document load retires a
  `Song`, and the codebase already knows what that costs.
  `Song.unsubscribeFromBus()` (`dom/Song.java:316`) exists, is public, is called
  by `ScoreView.setSong` (`:891`) on every replacement, and its Javadoc states the
  rule the tier-3 doc denies: *"MBassador holds listeners by weak reference, so a
  discarded Song keeps responding to broadcasts — and posting spurious undo steps
  against the dead document — until it is garbage-collected."* One class worked it
  out locally while `messages.md` told everyone else the opposite.

  The vivid case — `SVGConverter.java:60` and `PDFConverter.java:146` building a
  `ScoreView` per converted file and leaking its four subscribers — is **not**
  load-bearing here: those converters are to be redesigned and rewritten and are
  not in use until then. See §5.4, which records the requirement against the
  rewrite rather than wiring disposal into code about to be deleted. The
  document-load case above is live, in the interactive application, today.

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
| `deinitialize()` | undoes exactly what `initialize` established; the subsystem is unusable until `initialize` is called again | the inverse of `initialize` |
| `dispose()` | detaches an *instance* that attached itself in its constructor; the instance is unusable afterwards | an object implementing `Disposable` — see §5.7 |
| `reset()` | returns accumulated state to its as-constructed baseline; the object stays usable | state that grows during normal use |

**Not `shutdown()`.** It was the first choice and it is the wrong one here. This
codebase already spends that word: `songscribe.lifecycle.Shutdown` is the process
quit registry, so `Actions.shutdown()` would overload a term that already means
something specific one package away — and "shutdown" carries a
server-going-offline connotation that a subsystem you are about to re-initialize
does not have. `deinitialize()` mirrors `initialize()` morphologically, so the
pairing needs no explanation and `Shutdown` keeps its meaning.

`dispose()` is not merely a naming convention — §5.7 makes it an interface, so
the obligation is declared in the type system and the rule that creates it is
checkable rather than remembered.

### `deinitialize()` and `dispose()` are not the same promise

They are deliberately two words, and the choice between them is mechanical:
*is there an `initialize()` that can be called again?* → `deinitialize()`. *Is the
object discarded?* → `dispose()`.

**`dispose()` is terminal.** The instance is thrown away; every other method's
contract is void once it returns. **`deinitialize()` is reversible.** The class stays
loaded and `initialize()` brings the subsystem back — which is not hypothetical,
since `Actions.deinitialize()` in one test's teardown is followed by
`Actions.initialize(frame)` in the next test's setup, and §5.1 states that
re-initialization is permitted. One name for both would claim they behave alike
at the one point where they differ, and would break the pairing: `initialize` is
what `deinitialize` reverses, and nothing reverses `dispose`.

They compose rather than compete — `Actions.deinitialize()` walks its constants
calling `dispose()` on each, and the sentence reads the way the code runs.

**Open question for the `ui/action` and `undo` phases.** The second vocabulary
word exists only because `Actions`, `PlaybackController` and `UndoController` are
static holders. As instances they would implement `Disposable` and the
distinction would disappear. That is a much larger change than Phase 9 and
`.agents/guides/singletons.md` says not to retrofit existing singletons unless
the task calls for it — so it is recorded, not decided.

### The scoping rule

**Teardown undoes what initialization established, and nothing more.**

`PlaybackController.initialize(MainFrame)` establishes four action constants. It
does not establish the sequencer, the registered score, or the playback state —
those come from `register` and `play`. So `PlaybackController.deinitialize()`
unsubscribes the four constants and stops there. A teardown that reached into
playback state would be undoing something it never did, and no caller could
predict where it stops.

### On members that will have no production caller

`Actions.deinitialize()`, `PlaybackController.deinitialize()` and
`UndoController.deinitialize()` will have no production call site. The application
initializes each once and exits without tearing down; unsubscribing on the way
out of the process is work with no observable effect.

They are correct anyway, and this is exactly D5's position: a class that attaches
itself to a process-global bus and offers no way to detach has an incomplete
contract, tests or no tests. What makes them legitimate is that they complete a
stated lifecycle, not that a test wanted them.

**Consequence for the `check` skill.** Phase 5 item 4 makes "every reference
resolves under `src/test/`" a hard finding on the Contract & API axis. These
three members will trip it permanently, and so will `UIAction.dispose()`, whose
only callers are the two `deinitialize()`s. The axis needs one stated exception: *a
documented inverse of a documented initializer — or an implementation of
`Disposable` (§5.7) — on a class whose Javadoc states the lifecycle contract, is
not test-only surface.* Without that exception Phase 9's output is flagged
forever and the finding stops meaning anything. Recorded here for Phase 13's
revision pass.

**The exception needs no judgment**, which matters because an exception a
reviewer has to weigh is one that erodes. Both halves are checkable:

- an instance member — *does the class implement `Disposable`?* A type check.
- a static member — *is it named `deinitialize()` on a class that declares
  `initialize(...)`?* A name check, and only because the rename made the pair
  morphological. `shutdown()` would have required the reviewer to decide whether
  a given teardown counted as an inverse.

`SelectionCoordinator.dispose()` needs no exception — §5.4 gives it production
callers.

---

## 3. Summary of dispositions

| Member | Disposition | Phase |
|---|---|---|
| `Actions.resetForTest` + `unsubscribeForTest` | merge → `deinitialize()` | 9 |
| `PlaybackController.unsubscribeForTest` | → `deinitialize()` | 9 |
| `UndoController.unsubscribeForTest` | → `deinitialize()` | 9 |
| `UndoController.resetForTest` | → `reset()`, shared with `documentDidLoad` | 9 |
| `SelectionCoordinator.unsubscribeForTest` | → `dispose()`; view-level wiring deferred to the converter rewrite | 9 |
| — new `songscribe.lifecycle.Disposable` | declares the detach obligation in the type system | 9 |
| `Song.unsubscribeFromBus` | → `dispose()`, joins the interface | 9 |
| `UIAction` | gains `dispose()`; the two `deinitialize()`s call it | 9 |
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
> <p>{@link #deinitialize()} retires the current generation and clears the owner.
> Nothing survives it. The constants are {@code @NonNull} static fields, so they
> are not nulled; after {@code deinitialize()} they still reference retired actions
> and must not be read until {@link #initialize} is called again.

**Members:**

- `public static void resetForTest()` and `public static void unsubscribeForTest()`
  → **one `public static void deinitialize()`**. Body is the union: unsubscribe
  `RESET_HANDLER`, call `dispose()` on every action constant, `mainFrame = null`,
  `appMenuActions = null`. They are two halves of one operation and
  `MainFrameMockTest` already calls both in the same teardown.

  `UIAction` implements `Disposable` (§5.7), so the walk calls `dispose()` rather
  than reaching for `MessageCenter.unsubscribe` itself — the action knows what it
  subscribed, and the walker should not have to.

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
  public static void deinitialize()
  ```

- `initialize(MainFrame)` retires the previous generation before constructing the
  new one — `if (mainFrame != null) { deinitialize(); }` as its first statement. This
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
> {@link #deinitialize()} removes them. Re-initialization is permitted and retires the
> previous four first.
>
> <p>Playback state — the registered score, the transport state, the sequencer —
> is established by {@link #register} and {@link #play}, not by
> {@code initialize}, and is therefore not {@code deinitialize}'s to undo. Stopping
> playback is {@link #stop()}.

**Members:**

- `public static void unsubscribeForTest()` → `public static void deinitialize()`,
  calling `dispose()` on each of the four constants instead of unsubscribing them
  by hand.
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
> exactly this. {@link #deinitialize()} detaches it, after which it records nothing
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
   * {@link #deinitialize()}. Loading a document performs exactly this reset.
   */
  public static void reset()
  ```

- `public static void unsubscribeForTest()` → `public static void deinitialize()`.
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
> removes both, and a coordinator must not be used after it.
>
> <p>The owner calls it when the coordinator's {@link ScoreView} is retired.
> Nothing retires a view today — the interactive main window outlives the process,
> and the converters are pending a rewrite — so the only callers are tests. The
> obligation is the constructor's, not the caller's: an object that puts two
> listeners on a process-global bus owes a way to take them off.

**Members:**

- `public void unsubscribeForTest()` → `public void dispose()`, same body, plus
  the idempotence note in its Javadoc. The class implements `Disposable` (§5.7),
  as does the `ActionReflector` it disposes.

**No production wiring in Phase 9 — decided.** An earlier draft of this section
required `ScoreView.dispose()` and its converter call sites, on the grounds that
`SVGConverter.java:60` and `PDFConverter.java:146` build a view per converted file
and leak its subscribers. **That justification is withdrawn: the converters are to
be completely redesigned and rewritten, and are not in use until then.** Wiring
disposal into them would be wiring into code about to be deleted.

The stronger reason not to build the seam now is that the rewrite decides its
shape. A headless converter arguably should not construct a Swing `ScoreView` at
all; if the rewrite drops it from the conversion path, a `ScoreView.dispose()`
written today would have no owner ever — permanent test-only surface, the exact
defect this exercise exists to remove. Designing a disposal graph against an
unknown replacement is guessing.

Phase 9 therefore renames the coordinator's method and stops. `ScoreView` and
`ScoreViewController` do not implement `Disposable` yet.

**Requirement recorded against the converter rewrite.** Whatever replaces
`SVGConverter`, `PDFConverter` and `UIConverter` must leave no bus subscribers
behind. If it still builds a `ScoreView` per conversion, that view gains
`dispose()` — releasing itself (`ScoreView.java:308`), its `ScoreViewController`
(`ScoreViewController.java:168`), its `SelectionCoordinator`
(`SelectionCoordinator.java:122`), that coordinator's `ActionReflector`
(`ActionReflector.java:84`) and the installed `Song`, which `setSong` detaches
only when *replacing* it (`ScoreView.java:890-892`) — and the converter calls it.
If it builds no view, the requirement is discharged by construction.

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
> - **An object retired before the process ends.** Loading a document replaces the
>   `Song` in the `ScoreView`; the outgoing one is finished with, and
>   `ScoreView.setSong` disposes it. Left subscribed, it keeps handling broadcast
>   commands and posting undo steps against a document nobody has open.
>
> **A class that calls `MessageCenter.subscribe(this)` in its constructor
> implements {@link songscribe.lifecycle.Disposable}.** That is the whole rule,
> and it is mechanical: the subscribe call is the trigger, the interface is the
> obligation. A class that does not subscribe does not implement it. See
> `docs/lifecycle.md` for what disposal promises and who calls it.

The `Detaching` section states the bus-specific instance. The general obligation
— which objects are retired before the process ends and who disposes them —
belongs one level up, in `docs/lifecycle.md` (§5.8); `messages.md` links rather
than duplicating.

### 5.7 `Disposable` — a new interface in `songscribe.lifecycle`

`dispose()` as a naming convention leaves the rule in prose, which means it holds
only for as long as the next author has read the prose. As an interface it is
declared in the type system and the rule that creates the obligation becomes a
grep: *does this class call `MessageCenter.subscribe(this)` in its constructor,
and does it implement `Disposable`?* That is the same shape as the test-only
surface check on `check`'s Contract & API axis, and it fails in review rather
than relying on recall.

**New file:** `src/main/java/songscribe/lifecycle/Disposable.java`, beside
`Shutdown` — the package already holds the application's lifecycle machinery.

```java
/**
 * Implemented by a class that acquires something in its constructor which must be
 * released before the instance is discarded — today, always a message-bus
 * subscription.
 *
 * <p>Implement this if and only if there is real work to do. An empty
 * {@code dispose()} is indistinguishable from an unimplemented one, so it costs
 * the marker the only thing it is for: a class that implements this interface has
 * something to release, and a class that does not, does not.
 *
 * <p><b>Never implement this on a {@link java.awt.Window} subclass.</b>
 * {@code Window} already declares {@code dispose()} with its own meaning —
 * release the native peer — and Swing calls it on paths the application does not
 * control. Implementing the interface there silently merges two unrelated
 * operations. {@code MainFrame} is the case that would hit this, and it is
 * process-lifetime, so it does not implement this.
 *
 * <p>See {@code docs/lifecycle.md} for who calls {@code dispose()} for each
 * implementor, and {@code docs/messages.md} for the rule that a constructor-side
 * subscription creates this obligation.
 */
public interface Disposable {

    /**
     * Releases everything this instance acquired in its constructor, and disposes
     * the {@code Disposable}s it owns.
     *
     * <p>Idempotent — a second call is a no-op, never an error. The instance must
     * not be used afterwards; the contract of every other method on it is void
     * once this has been called.
     *
     * <p>Called by the owner that retires the instance, named in the implementing
     * class's {@code Lifecycle} Javadoc. An instance that lives as long as the
     * process is never disposed, which is why a process-lifetime class does not
     * implement this interface at all.
     */
    void dispose();
}
```

**Implementors, and only these:**

| Class | `dispose()` releases | Disposed by |
|---|---|---|
| `Song` | itself | `ScoreView.setSong` on every document load — the one live production caller |
| `SelectionCoordinator` | itself and its `ActionReflector` | tests today; the view's owner once §5.4's requirement lands |
| `ActionReflector` | itself | `SelectionCoordinator.dispose()` |
| `UIAction` | itself | `Actions.deinitialize()` / `PlaybackController.deinitialize()` |

`ScoreView` and `ScoreViewController` are **deferred** — see §5.4. They hold real
subscriptions, but nothing retires a view until the converter rewrite decides
whether a converter builds one at all, and an interface implemented against a
guess is worse than one implemented late.

That leaves one implementor with a live production caller (`Song`) and three
justified by contract completeness. The interface earns its place on the first
argument in §5.7 rather than the second: it makes the subscribe-implies-detach
rule checkable. If that argument did not hold, four implementors would not carry
an interface on their own.

`Song.unsubscribeFromBus()` (`dom/Song.java:316`) is renamed to `dispose()` and
its existing Javadoc — which already states the weak-reference reasoning better
than `messages.md` did — moves to the class's `Lifecycle` section. Its one
production caller, `ScoreView.setSong` (`:891`), follows the rename.

`UIAction.dispose()` replaces the per-constant `MessageCenter.unsubscribe(...)`
calls inside `Actions.deinitialize()` and `PlaybackController.deinitialize()`. Those two
walk the constants and call `dispose()` on each; the base class knows what it
subscribed, which the walker should not have to.

The remaining ~19 `MessageCenter.subscribe` sites in `src/main` are
process-lifetime objects — `MainFrame`, `MenuController`, `StatusBar`,
`ZoomStatusBarPanel`, `ModeCycleButton`, `TupletPopupButton`, `LyricEditor`,
`EditModeManager`, `MacNativeMenuController`, `MessageLogger`,
`PreviewElementManager`, the action groups, and `BaseDialog`'s geometry
subscriber. None implements `Disposable`. Dialogs are cached per action
(`DialogOpenAction.getDialog()` creates one lazily and reuses it), so they are
not retired per open.

### 5.8 `docs/lifecycle.md` — the tier-3 home

The document currently covers application startup and shutdown. Object disposal
is the same subject one scale down, and it is where the general obligation
belongs — `messages.md` states the bus instance and links here.

Phase 9 adds a section:

> ## Object lifecycle
>
> Most objects in this application live as long as the process. The main window,
> the menu controller, the status bar and every action constant are created at
> startup and released by process exit; nothing tears them down, and nothing
> should.
>
> The exceptions are objects retired while the process continues, and they are
> the ones that need disposal. An object that registers itself with something
> process-global — today, the message bus — stays registered after the last
> reference to it is dropped, because the registry holds it weakly and the
> collector runs when it runs. Until then it keeps handling messages on behalf of
> something nobody is using.
>
> Such a class implements `Disposable` and its class Javadoc names, under a
> `Lifecycle` heading, who calls `dispose()`.
>
> The live case is the document model. Every document load replaces the `Song`
> installed in the `ScoreView`, and `ScoreView.setSong` disposes the outgoing one.
> A `Song` left subscribed keeps handling broadcast commands and posting undo
> steps against a document nobody has open.
>
> A second case is coming rather than present: a `ScoreView` built for one
> conversion, with its controller, its `SelectionCoordinator` and that
> coordinator's `ActionReflector`, is finished with when the conversion is. The
> converters are being redesigned; whatever replaces them owes the disposal, and
> `ScoreView` acquires `dispose()` then — not before, because the rewrite decides
> whether a converter builds a view at all.
>
> Three teardowns exist and none substitutes for another:
>
> | | Ends | Reversed by |
> |---|---|---|
> | `Shutdown.now()` | the process | nothing |
> | `Foo.deinitialize()` | a static subsystem's current initialization | `Foo.initialize(...)` |
> | `foo.dispose()` | one instance, permanently | nothing |
>
> There is no point unsubscribing on the way out of the process, and no point
> running the quit sequence to discard a view. `deinitialize()` is the odd one:
> it is the only teardown you can undo, which is why it is named for the thing
> that undoes it.

### 5.9 `CLAUDE.md` — a required-reading trigger

The required-reading table has no entry routing anyone to `lifecycle.md`. Phase 9
adds one:

> - **Disposing an object, or writing a class that registers itself with anything
>   process-global** (`Disposable`, `dispose()`, a constructor-side
>   `MessageCenter.subscribe`): [Application and Object Lifecycle](docs/lifecycle.md).

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
   **Decided: Phase 9 fixes this**, ahead of and independently of the extraction
   below. See §7 finding 3.
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

**Alternative:** add `uninstallOverlay(OverlayHost)` and call it from a
`ScoreView.dispose()`. Smaller, but it depends on a method §5.4 has deferred to
the converter rewrite, and it leaves the statics — so two conversions running in
one process still share overlay state. The recommended option depends on nothing
outside the `ui/component` phase, which is a second reason to prefer it.

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

Each is independent of any test. Every one carries a disposition — a finding with
no owner is a finding that dies quietly.

| # | Finding | Disposition |
|---|---|---|
| 1 | `initialize` leaks the replaced generation | Phase 9, §5.1/§5.2 |
| 2 | converter subscriber leak | withdrawn — requirement on the rewrite, §5.4 |
| 3 | `drainStartupErrors()` does not drain | Phase 9 — decided |
| 4 | `docs/messages.md:10` is wrong | Phase 9, §5.6 |
| 5 | `Prefs.resetAll()` has no production caller | delete, with all of `PrefsKey.ALL` → `prefs` phase |
| 6 | duplicate reflection loop in `Actions` | Phase 9, §5.1 |
| 7 | `MidiController` static test surface | Phase 10 sweep, then `ui/playback` |

1. **`Actions.initialize` and `PlaybackController.initialize` leak the generation
   they replace.** Both promise in Javadoc that calling again replaces the
   constants; both leave the replaced ones subscribed. Production calls each once,
   so it does not bite today. Fixed by §5.1 and §5.2.
2. ~~**The converters leak four bus subscribers per converted file.**~~ **Not a
   defect to act on.** `SVGConverter.java:60` and `PDFConverter.java:146` do build
   a `ScoreView` per conversion and leak the view, its `SelectionCoordinator`,
   that coordinator's `ActionReflector` and the installed `Song` — but the
   converters are to be completely redesigned and rewritten and are not in use
   until then, so nothing observes it. Recorded as a requirement on the rewrite in
   §5.4, not as work for this rollout.
3. **`MainFrame.drainStartupErrors()` does not drain.** **Decided: fixed in
   Phase 9.** One statement — clear the queue after showing the warnings — which
   makes the method keep the promise its name and Javadoc already make. It does
   not depend on §6.3's `StartupErrorQueue` extraction, which stays in the
   `ui/component` phase. Nothing observes the defect today (the two call sites are
   mutually exclusive, `main`'s being in a catch block that exits), so this is
   about not leaving a stated promise unkept, not about a live bug.

   The member's Javadoc gains the consumption clause: draining empties the queue,
   so a second call shows nothing. `clearStartupErrorsForTest` still goes to the
   `ui/component` phase with the extraction — a consuming drain does not remove
   every test's need for an empty queue, only the need to clear one the test
   itself drained.
4. **`docs/messages.md:10` is wrong** about unsubscription never being needed, and
   is the reason this whole category was mislabelled. §1, fixed by §5.6.
5. **`Prefs.resetAll()` has no production caller** — only `PrefsTest`.
   **Decided: delete it and its test.** No "reset all preferences" affordance is
   planned, so it is dead public API. Tests that `Prefs` turns out to need fall
   out of its contract when the `prefs` phase writes one, not out of what happens
   to exist now.

   **The deletion is larger than the method**, because `resetAll` is the only
   producer of `PrefsKey.ALL`. `Prefs.save(PrefsKey)` is the single site that
   posts `PrefsDidChangeNotification` (`Prefs.java:392`) and every other caller
   names a specific key, so removing `resetAll` makes `ALL` unproducible. Keeping
   the sentinel with no producer is the incoherent middle — two handlers would go
   on branching on an input that can never arrive — so the whole concept goes:

   - `PrefsKey.ALL`, and the clause naming it in `PrefsDidChangeNotification.getKey()`'s Javadoc
   - `BaseDialog.GeometryResetSubscriber.prefsDidChange` (`:1108`) — the `key == PrefsKey.ALL ||` disjunct
   - `ScoreViewController.prefsDidChange` (`:474`) — `all` and both effects it guards; its comment already records the dependency: *"PrefsKey.ALL fires on resetAll()"*
   - `PrefsTest.testAllKeysExistInDefaults` (skips `ALL`), `PrefsTest.testGetDefaultThrowsForUnknownKey` (uses `ALL` as a key with no default), `BaseDialogTabsTest.testAllKeyClearsSavedGeometry`, `ScoreViewControllerTest.testPrefsDidChangeAllCallsBothEffects`
   - `prefs.md:95` in both guide copies, which currently tells every handler author to *"always also check for `PrefsKey.ALL`"* — advice to write a dead branch once the producer is gone

   No bulk mutation exists or is planned, and one would bring its own sentinel
   back with it in a few lines if it ever arrives. Delete the concept.

   → `prefs` foundations phase, not Phase 9: it reaches `ui/component`,
   `ui/dialog` and the guides.
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

1. **Add `Disposable`** (§5.7) — the interface and its Javadoc first, since §5.1,
   §5.2 and §5.4 are written against it.
2. Apply §5.1–§5.5: five classes, the renames and merges above, each with its
   class-level `Lifecycle` Javadoc and a full contract on every renamed member.
   Use `jet_brains_rename` so call sites follow.
3. Implement `Disposable` on the four classes in §5.7's table — including the
   `Song.unsubscribeFromBus` → `dispose()` rename and the new `UIAction.dispose()`
   — and on nothing else. `ScoreView` and `ScoreViewController` are deferred.
4. **Make `MainFrame.drainStartupErrors()` consume the queue** (§7 finding 3), and
   state the consumption in its Javadoc. One statement; independent of everything
   else in this phase and of §6.3's extraction.
5. Documentation: §5.6 to `docs/messages.md`, §5.8 to `docs/lifecycle.md`, §5.9 to
   `CLAUDE.md`.
6. Also in Phase 9, per the rollout: the three `SMuFLMetadata` members from
   rollout §6.2. Unaffected by this document.
7. `./scripts/compile.sh`, then `./scripts/test.sh`.

**Not in Phase 9:** any change to `SVGConverter`, `PDFConverter`, `UIConverter` or
`ScoreView` (§5.4); the `Prefs.resetAll` / `PrefsKey.ALL` deletion (§7 finding 5),
which reaches `ui/component`, `ui/dialog` and both guide copies and belongs to the
`prefs` phase; and §6.3's `StartupErrorQueue` extraction, which stays in
`ui/component`.

Steps 1–3 are one coherent change and compile together; committing per step (D11)
means step 1 lands with the interface unimplemented, which compiles fine.

Phase 9 does **not** touch §6. Those go to the phases named there.
