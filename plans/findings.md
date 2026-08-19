# Review Findings — test-only-surface working tree

Nothing here is producing a user-visible failure today. Three findings are latent
defects that will fire the first time someone uses the mechanism the way its own
documentation invites, one is a live off-thread defect in a standalone entry
point, and the rest are dead members, unearned visibility and contract gaps.

---

## 1. Design: the application bus lives inside the stack of temporary buses

**Where:** `src/main/java/songscribe/message/MessageCenter.java:31–86`.

**What the code does now.** The message bus is how everything in the application
talks to everything else: an object *subscribes* to it, and any code can *post* a
notification that every subscriber hears. Until this change there was exactly one
bus, held in a `static final` field.

The change made it a stack. `MessageCenter` now holds an `ArrayDeque` of buses.
`post`, `subscribe` and `unsubscribe` all go through a private `bus()` method that
looks at the top of the stack, and — if the stack is empty — builds the
application bus and pushes it. A `MessageBusScope` pushes a second bus on top for
the duration of a conversion, and popping it restores the one underneath.

**What's wrong with it.** The application bus and the temporary scopes are two
different things with two different lifetimes, and putting them in one container
forces the code to keep telling them apart:

- **The one-time-creation guarantee is gone.** A `static final` field is
  initialized by the JVM under a lock, exactly once, and the result is visible to
  every thread before any of them can use it. "Peek, and build one if the stack is
  empty" has neither property. Two threads arriving at the first `post` or
  `subscribe` together can each see an empty stack, each build a bus, and each
  push onto a deque that is not safe for concurrent modification. The result is
  two buses: some listeners registered on one, messages posted to the other, no
  exception anywhere — just a part of the window that silently stops updating.
  Nothing in the running application is known to hit this today, because the main
  window subscribes on the event thread before any background thread starts. It is
  a guarantee that was traded away for nothing.
- **The reason given for the laziness is not correct.** The comment on `bus()`
  says it builds on demand "so that the bus does not depend on when this class
  happens to be loaded." Loading a class does not run its field initializers;
  *using* it does, and the first use is the first `post` or `subscribe` — the same
  moment `bus()` would build it. The laziness buys nothing.
- **The stack has to be told which entry is sacred.** `pushBus` opens with a bare
  call to `bus()` and a two-line comment explaining that it must "materialize the
  application bus first so a scope always has one beneath it, which is what lets
  `popBus` tell a scope from the bus it must never discard." `popBus` then encodes
  "do not discard the application bus" as `if (BUS_STACK.size() <= 1)`. Two pieces
  of arithmetic standing in for a fact a separate field would simply state.
- **Every headless conversion builds a bus it never uses.** In a converter nothing
  has subscribed before the scope opens, so that materialize-first call constructs
  a whole MBassador — with its dispatch machinery — purely so the size check comes
  out right, and then immediately covers it up.
- **The guard reports through the path the scope exists to avoid.** When `popBus`
  finds nothing to pop it calls `RuntimeError.exit`, which puts up the fatal-error
  dialog that a headless converter cannot display — the exact problem
  `MessageBusScope` was built to solve.

**The corrected design.** Take the application bus out of the stack:

```java
private static final MBassador<Message> APPLICATION_BUS =
    new MBassador<>(MessageCenter::exitOnPublicationError);
private static final Deque<MBassador<Message>> SCOPE_STACK = new ArrayDeque<>();

private static MBassador<Message> bus() {
    var scoped = SCOPE_STACK.peek();
    return scoped != null ? scoped : APPLICATION_BUS;
}
```

`pushBus` pushes onto `SCOPE_STACK` and nothing else. `popBus` asks the plain
question "is the scope stack empty?"

**Symptoms this accounts for.** The lazy null check in `bus()`, the
materialize-first call and its comment in `pushBus`, the `size() <= 1` sentinel in
`popBus`, the throwaway bus every conversion builds, and the class Javadoc's claim
that the stack is "never emptied below the application bus" — which stops being a
rule someone must maintain and becomes true by construction.

**What it touches.** One file. Two field declarations, three method bodies. No
call site changes; `MessageBusScope`, `Converter` and the test helper are
untouched.

**Recommendation: make this change.** It puts back the guarantee the old field
gave for free, and it deletes the special-casing that was added to work around
losing it.

### 1a. `unsubscribe` can silently fail to undo a `subscribe`

Same file, same cause. All three operations act on whichever bus is on top. An
object that subscribed on the application bus and is disposed while a scope is in
force sends its `unsubscribe` to the scope's bus, where it matches nothing — and
stays subscribed on the application bus forever.

Nothing reaches this today: the only production scope is a converter with nothing
subscribed beneath it. But `docs/messages.md` states "subscribe in your
constructor, unsubscribe in `dispose()`" as a mechanical rule for the whole
codebase, and that rule now has a case where it quietly does nothing. Worth
deciding whether `unsubscribe` should search the whole stack, or whether the
documentation should say plainly that disposal inside a scope is not supported.

### 1b. `MessageBusScope.close()` can discard someone else's bus

`src/main/java/songscribe/message/MessageBusScope.java`. The scope object keeps no
reference to the bus it pushed — `close()` just discards whatever is on top. Two
consequences, neither stated:

- **It must be called exactly once.** `AutoCloseable`'s own contract encourages
  implementers to make `close()` idempotent, so a caller is entitled to assume it
  is. Here a second call discards the *enclosing* scope's bus, or, with no scope
  left, terminates the process.
- **Scopes must close in the reverse order they opened.** The class Javadoc says
  "Nesting is allowed; interleaving is not" as advice. `try`-with-resources
  enforces it; `MessageCenterTestHelper`, which opens and closes from separate
  JUnit lifecycle methods, does not — and it is the only caller that uses the type
  that way.

Both are fixable in the type rather than in prose: have the scope store the bus it
pushed and have `popBus` take that bus and verify it is the one on top. Closing out
of order then fails immediately and says which scope was wrong, instead of quietly
discarding another scope's bus.

### 1c. Nesting has no caller and is not enforced

`MessageBusScope` is constructed in exactly two places — `Converter.run` and the
unit-test base class — and neither nests. The stack exists to support nesting that
nobody does, while the constraint that actually matters (only one scope at a time
in a live application) is a paragraph of prose that nothing checks.

---

## 2. Design: `subscribed` is a second copy of a fact the bus already owns

**Where:** `src/main/java/songscribe/ui/component/score/PreviewElementManager.java:107`
and `:118–127`; the same shape at `src/main/java/songscribe/undo/UndoController.java:161`
and `:172–177`.

**What the code does now.** `PreviewElementManager` is the class that draws the
ghost note following your mouse in edit mode. It used to attach itself to the
message bus from a `static { }` block; this change replaced that with a public
`initialize()` called from `MainFrame`'s startup, guarded by a private
`static boolean subscribed` that is set to `true` on the first call and never set
back. The Javadoc calls the method "Idempotent."

**What's wrong with it.** The Javadoc explains why the static block had to go: as
a static initializer, the subscription "could land inside a scope and be discarded
when that scope closed, leaving the singleton permanently unsubscribed with no
static initializer left to run again." The boolean latch reproduces that outcome
exactly. If `initialize()` ever runs while a bus scope is open, it subscribes to
the scope's bus, sets the flag, and loses the subscription when the scope closes —
and because the flag stays `true`, every later call does nothing. The hover
preview goes dead for the rest of the process, with nothing logged. The word
"Idempotent" is what makes it unrecoverable.

The deeper problem is that the flag means *"`initialize()` has run at least
once"*, while what the code needs to know is *"the singleton is on the bus that is
in force"*. Those are two different facts, and the bus is the one that owns the
second.

**The correcting fact: the bus already does this.** MBassador's `subscribe`
refuses a listener it already holds — `AbstractConcurrentSet.insert` checks
membership before inserting. Subscribing twice is already a no-op. So the flag
guards nothing, and the copy it keeps is the one that can go stale.

**The corrected design.** Delete the `subscribed` field and the `if`, and call
`MessageCenter.subscribe(INSTANCE)` unconditionally. `initialize()` becomes
genuinely idempotent — because the bus makes it so — and it also *heals*: calling
it after a bus change re-subscribes rather than silently refusing to.

The identical latch in `UndoController.subscribeToBus` should go the same way.
`UndoController.deinitialize()` stays (it also clears the undo and redo stacks);
only the `INSTANCE.subscribed = false` line inside it goes.

**What it touches.** Two files, one field and one branch deleted from each, one
line deleted from `UndoController.deinitialize()`, and the word "Idempotent."
stays in `PreviewElementManager`'s Javadoc because it becomes true.

**Recommendation: make this change.** Two agents independently proposed adding a
`deinitialize()` to `PreviewElementManager` to reset the flag. That would work,
but it adds a method to keep two facts in step when deleting the second fact makes
them one. The rest of that Javadoc — including the genuinely useful point that a
headless conversion now never subscribes a preview handler at all — stays as is.

**Note on the lifecycle question.** `PreviewElementManager` subscribes for the
life of the process on the application bus, and `docs/lifecycle.md` says outright
that there is no point unsubscribing on the way out of a process. With the flag
gone it needs no `deinitialize()`. It does still owe the class-Javadoc *Lifecycle*
heading naming `MainFrame.initFrame` as its caller, which `docs/lifecycle.md`
requires of anything registering with something process-global, and
`docs/lifecycle.md`'s own startup section still shows only `Actions.initialize(this)`
where three initializers now run in sequence.

---

## 3. Design: six "takes its inputs explicitly" methods are the deleted tests' injection points, relabelled

**Where:** `src/main/java/songscribe/SongScribe.java:47–120` (four methods) and
`src/main/java/songscribe/smufl/SMuFLMetadata.java:92–129` (two).

**What the code does now.** Each is one of a pair: a real method, plus an overload
that takes as parameters the things the real one would otherwise look up for
itself.

- `configureLogging()` calls `configureLogging(env, consoleLogUrl)`
- `truncateLogIfRequested()` calls `truncateLogIfRequested(env)`
- `resolveLogDir(env)` calls `resolveLogDir(env, isMacOS, isWindows)`
- `getAdvanceWidth(glyph)` calls `getAdvanceWidth(map, glyph)`, whose whole body is `map.get(glyph)`
- `getAdvanceWidthOrZero(glyph)` calls `getAdvanceWidthOrZero(map, glyph)`

The change kept all of them and rewrote the comment on each. Where they said
"Package-private for testing: accepts explicit platform flags so tests can
exercise Windows and 'other OS' branches", they now say "Takes the platform flags
explicitly, so the directory choice is a pure function of its arguments."

**What's wrong with it.** Purity is a property, not a purpose. Every one of these
overloads has exactly one caller — its own wrapper, in its own class — and every
one of them is always passed the same fixed values. Nothing in the application
supplies a different environment, a different platform, or a different glyph map,
and nothing can. The parameter exists so something *outside* could vary it, and
the only thing that ever did was a test that no longer exists. Rewording the
comment does not change what the member is; it removes the evidence a later reader
would need to recognise it. The plan's own rule is the test these fail: judge a
restructured member on whether it is a coherent unit with its own contract.

`resolveLogDir(env, isMacOS, isWindows)` additionally takes two adjacent
`boolean`s that a call site can transpose with no complaint from the compiler,
which the project's Java rules forbid outright — and `(isMacOS=true,
isWindows=true)` is a state the signature permits and the world does not.

**The corrected design.** Collapse each pair into the one method the application
actually has: `resolveLogDir()`, `configureLogging()` and `truncateLogIfRequested()`
read `SystemInfo` and `System.getenv` directly; `getAdvanceWidth(glyph)` and
`getAdvanceWidthOrZero(glyph)` do `instance().advanceWidths.get(glyph)` directly
and the two-argument overloads disappear, since neither does anything a `Map.get`
does not.

`getAdvanceWidth(SMuFLGlyph)` has **no caller at all** (verified: one Javadoc link
and nothing else), so the `SMuFLMetadata` cluster collapses to a single method:

```java
public static double getAdvanceWidthOrZero(SMuFLGlyph glyph) {
    var width = instance().advanceWidths.get(glyph);
    return width != null ? width : 0.0;
}
```

**What it touches.** Two files, six methods deleted, four bodies inlined into
their wrappers. No call site outside those two classes changes.

**Recommendation: make this change.** These are the largest remaining pocket of
test-shaped production code, and they are the ones most likely to be mistaken for
deliberate design, because the change gave each of them a design-sounding
sentence.

---

## 4. Contract change: `Converter.run` claims a benefit its only caller does not get

**This one alters an existing promise, so it needs a decision rather than a fix.**

**Where:** `src/main/java/songscribe/converter/Converter.java:26–44`, and the same
sentence in `docs/messages.md`.

**What it promises now.** The Javadoc gives two reasons for running a conversion
inside a bus scope. The first: "Its object graph — the score view a conversion
builds, its controller, and everything those subscribe — is discarded wholesale
when the scope closes, rather than staying subscribed for the rest of the
process."

**What's wrong with it.** For the four headless converters, "the rest of the
process" is microseconds. `PDFConverter` builds one score view for the whole run
and the scope closes immediately before the process exits.
`docs/lifecycle.md` states the point directly: there is no point unsubscribing on
the way out of the process. So the disposal half of what a scope promises has no
production reader. Its real reader is the test suite, and tests are not consumers.

The second reason is real and is the whole justification: a throwing `@Handler` on
the application bus is treated as fatal and puts up a dialog, which a headless
process cannot show, so a converter supplies an error handler that logs instead.

**What it should promise instead.** Cut the disposal claim from both
`Converter.run`'s Javadoc and `docs/messages.md`, leaving the error handler as the
stated reason. Keep the disposal *mechanism* — the test suite depends on it, and
`MessageBusScope`'s own Javadoc is the right place to describe it — but stop
telling a reader of `Converter.run` that it buys that caller something it does not.

---

## 5. Contract gaps in the new API

- **`MessageCenter.describe(PublicationError)`** (`MessageCenter.java:95`) — public,
  returns a `String`, has a doc comment and no `@return`. The tag is what the IDE
  shows at the call site; without it a caller sees no answer to "what do I get
  back?" The body also calls `whichHandlerThrew(error)` in both branches of one
  ternary — assign it to a local first, per the project's rule against repeating a
  call inside a method.
- **`Converter.run`** — no `@param <T>`; no statement of the precondition on
  `type` (`ArgumentReader` reflects over it, so `T` must have a no-argument
  constructor and annotated public fields, and a caller who passes anything else
  finds out at runtime); and no `@effects` for two process-global side effects —
  it reconfigures logging for the whole process and replaces the application's
  message bus for the duration.
- **`MessageCenter.popBus()`** — terminates the process via `RuntimeError.exit`
  when there is no scope in force, with no `@throws` naming the condition.
- **`Converter.loadSong(File, ScoreView)`** — has `@return`, has neither `@param`.
- **`Converter.applyExportExclusions(Song, boolean withoutLyrics, boolean withoutSongTitle)`**
  (`Converter.java:107`) — two adjacent booleans a call site can transpose with no
  compiler complaint, called from `PDFConverter` and `SVGConverter`. The project's
  rules require a `record` or an enum here. It also has no `@param` tags at all.
- **`MessageCenter.post` / `subscribe` / `unsubscribe`** — the highest-fan-in API
  in the codebase (67 post call sites in 45 files; 25 subscribe call sites in 23
  files) and all three have no Javadoc at all. Two promises callers most need are
  written down only in `docs/messages.md`, which nothing links to: `post` is
  synchronous, so every handler runs on the calling thread and finishes before
  `post` returns; and the bus holds subscribers weakly, so a listener nothing
  keeps strongly reachable is silently collected and stops receiving messages. A
  one-line Javadoc on each plus a pointer from the class Javadoc to
  `docs/messages.md` closes it, and this change already rewrote that class
  Javadoc, which is when it is cheapest.

---

## 6. Correctness

### 6a. Two members with no caller anywhere

- `src/main/java/songscribe/ui/selection/SelectionDragTracker.java:85` —
  `getGlobalMouseReleasedListener()`. A reference lookup returns nothing: no
  production caller, no test caller, no Javadoc link. The sweep missed it because
  the "package-private for tests" comment sat on the neighbouring
  `getDraggingLine()` that *was* deleted. `ui/selection` has no test package, so a
  member with no callers is simply dead. Delete it.
- `src/main/java/songscribe/smufl/SMuFLMetadata.java:96` —
  `getAdvanceWidth(SMuFLGlyph)`, covered under finding 3.

### 6b. A builder default nothing can reach, that hands back a plausible wrong answer

**Where:** `src/main/java/songscribe/ui/renderer/LineInvariants.java:638–646`. Not
in the diff; found on the way through.

`setViewScale` carries the Javadoc "Defaults to `ViewScale.IDENTITY` (natural
size) when not set, e.g. in tests." Its only production caller,
`LineRenderer.buildInvariants`, always sets it, and the tests named in the comment
are the deleted suite. `build()` already refuses to produce an object when
`layoutResult` or `lyricRenderMetrics` are missing, but says nothing about zoom.

So a future caller who forgets the zoom gets a line rendered at 100% while the
rest of the view is zoomed, with no error to say so. That is the shape
`~/.claude/guides/design.md` singles out as the one that must never be written: an
arbitrary default that masquerades as success. Check `viewScale` in `build()`
alongside the other two required fields, or take it in the `Builder`'s
constructor, and delete the sentence about tests.

### 6c. The standalone folder converter drives Swing from a background thread

**Where:** `src/main/java/songscribe/uiconverter/ConvertAction.java:88` and its
inner `ConvertThread.run()`. Not in the diff; found while tracing which threads
reach the message bus.

`ConvertAction` starts a plain `Thread` and, from it, calls
`scoreView.openFile(...)` for each song, then writes files, builds images and
advances a progress dialog. `openFile` goes on to `setSong`, which constructs a
`ScoreViewController`, which subscribes to the message bus in its constructor and
posts synchronously. All of that — Swing component construction, Swing mutation
and synchronous handler dispatch — happens off the event thread.

Scope, corrected from the agent's report: `UIConverter` is a **separate process
mode** (`SongScribe.main` dispatches `case "ui_converter"`), so there is no live
main window whose handlers this reaches. The damage is confined to the converter's
own object graph. It is still an off-thread Swing violation, of the kind that
usually works and occasionally produces a corrupted repaint, a stale-state
exception in a handler that assumed the event thread, or a hang.

This is not something the current change caused, and it is not something the
current change makes worse. It is worth naming because the change just built the
right mechanism for exactly this shape of work — `Converter.run`'s scope — and
`ConvertAction` is the one remaining conversion path that does not use it.
Deciding what to do here belongs with the converter redesign
`docs/lifecycle.md` already anticipates.

---

## 7. Twenty-three members kept their test-widened visibility after the comments were deleted

`plans/test-only-surface.md` records these as "verified live and corrected in
place rather than deleted." The verification asked *does this have a production
caller?* — which each does. The question the plan's own rule requires is *does it
have a production caller **outside its own class**?*

A reference sweep over the plan's list found that **23 of the 26 named members are
referenced only inside the class that declares them.** I spot-checked six of these
directly (`Shutdown.runJVMTasksFromHook`, `StaffPanel.layOutLines`,
`ActionReflector.triggerReflection`, both `SMuFLMetadata` overloads, and
`SelectionDragTracker.getGlobalMouseReleasedListener`) and each held. They should
be `private`, and the compiler proves each one.

| Class | Members with no caller outside the declaring class |
|---|---|
| `SongScribe` | `resolveLogDir` ×2, `configureLogging(env, url)`, `truncateLogIfRequested(env)` |
| `SMuFLMetadata` | `getAdvanceWidth(map, glyph)`, `getAdvanceWidthOrZero(map, glyph)` |
| `LineRenderer` | `drawStaffLines`, `getElementColor`, `computeOverrideXSs`, `renderKeyChanges`, `renderWithPreviewShiftIfNeeded` |
| `PlaybackController` | `handleMetaMessage`, `updatePlayingNote`, `setLoopSequence`, `buildSequenceForSelection` |
| `AttributionPane` | `LINE_BOX_REFERENCE`, `MeasuredCache`, `measure` |
| `LineComponent` | `layoutResult`, `layoutDirty` |
| `TextPanel` | `calculateUnionWidth` |
| `StaffPanel` | `layOutLines` |
| `FootnotesComponent` | `calculateRenderX` |
| `LineSelectionHandler` | `HEADER_GAP_PX` |
| `ScoreView` | `scoreKeyBindings` |
| `PlayStopAction` | `PLAY_ICON`, `STOP_ICON` |
| `ActionReflector` | `triggerReflection` |

Three from the plan's list are genuinely package-private and correct:
`LineComponent.readyLayout` (called by `LineSelectionHandler`),
`StaffPanel.ensureAllLineLayouts` (called by `StaffLinesLayout`), and
`MeasureBuilder.buildMeasure` (called by `ScorePartwiseBuilder`).

Three more, not on the plan's list, are in the same state and carry Javadoc that
describes the visibility as intentional:

- `Shutdown.shutdown()` — only caller is `Shutdown.now()`, and its Javadoc still
  reads "Package-private — production code calls `now()`."
- `Shutdown.runJVMTasksFromHook()` — only referenced by the method reference in
  `Shutdown`'s own constructor, so `private` is correct.
- `MidiController.closed` — only read and written by `MidiController.closeMidi`.

**Why it matters.** Nothing breaks today. What it costs is a signal: package-private
tells the next reader "something else in this package depends on this, so changing
its shape is not a local decision." Twenty-six false signals make the three true
ones unreadable, and the next person who wants to widen a member for a test has a
pile of precedent to point at.

---

## 8. Test conformance

### 8a. `UndoController.deinitialize()` is test-only production surface

**Where:** `src/main/java/songscribe/undo/UndoController.java:411`.

A reference lookup returns exactly one caller: `UnitTest.teardown()` in the test
tree. Unlike `Actions.deinitialize()` and `PlaybackController.deinitialize()`,
which are called by their own `initialize()` on the re-entry path, nothing in
production calls this one — `UndoController.initialize()` does not.

This is the plan's own rule ("No production member exists to serve a test") with
one surviving exception it did not catch. It is not simply deletable: the test
suite needs it, because `UndoController` is a static singleton whose undo and redo
stacks outlive a test even though its bus subscription no longer does. That makes
the honest fix a design question about the singleton, not a deletion — which is
why I am reporting it rather than proposing one. Worth a decision on whether it
belongs in the design-pass register.

### 8b. The teardown comment explains one of the two calls it sits above

**Where:** `src/test/java/songscribe/UnitTest.java:90–96`.

The rewritten comment says the calls retire the action singletons and release the
mocked main window they captured. That is accurate for `Actions.deinitialize()`.
The next line calls `UndoController.deinitialize()`, which captures no main window
— its reason is that it clears the undo and redo stacks, which the bus scope
closing cannot do. As written, the comment documents the easy-to-see reason and
omits the one that would explain a confusing failure if the call were ever
removed.

### 8c. The new bus-scope behavior has no test

This change replaced a single static bus with a stack carrying real invariants: a
scope replaces rather than layers, closing discards everything subscribed inside
it, popping below the application bus is refused. None of the six surviving test
classes asserts any of that; it is exercised only incidentally, as plumbing, by
every test's setup and teardown. The project's own rule is that changing a
contract or an implementation requires a test in the same change, and this is the
kind of multi-call invariant the testing floor exists for.

I am not proposing a list here — the project requires the proposed tests to be
seen and approved before any are written. Flagging the gap at the change that
created it.

---

## 9. Documentation and comments still pointing at a suite that no longer exists

- **`UndoController.initialize()`** (`UndoController.java:181–188`) — its Javadoc
  says subscription is explicit because lazy construction "in tests can occur
  while the message bus is mocked — subscribing then would register the listener
  against a mock and corrupt its later real subscription." There is no mocked bus
  any more; tests get a real bus in a scope. Rewrite to the reason that still
  holds, which is the one `PreviewElementManager` now states.
- **`BaseDialog`'s constructor** (`BaseDialog.java:285–289`) — "a test teardown
  that unsubscribes it is healed by the next dialog construction." Test teardown
  no longer unsubscribes individual listeners; it discards the bus.
- **`SongSettingsLayout`'s class Javadoc** (`SongSettingsLayout.java:32–38`) —
  describes its contents as distinct from "the pure-logic helpers
  `SongSettingsDialog` hosts for testability", which is a claim about a suite that
  does not exist.
- **`docs/lifecycle.md`** was not updated. Its closing table, introduced as "Three
  teardowns exist and none substitutes for another", does not mention that closing
  a bus scope now also ends a set of registrations — the paragraph
  `docs/messages.md` gained about a scope not discharging the disposal obligation
  belongs in the table that claims to be exhaustive. Its startup section also
  still shows only `Actions.initialize(this)` where three initializers now run.
- **`plans/design-pass-register.md:191`** states that `MidiController.synthesizer`
  "is still test-only surface on the same class." It is not:
  `PreferencesDialog.ensureInstrumentsLoaded` reads it. It is over-visible (a
  `public static volatile` field written only inside `MidiController`) and the
  register's own pass-26 fix removes it anyway, but the characterisation would
  mislead whoever picks that pass up.
- **`RuntimeError.setExitHandlerForTesting`**, **`resetAlertShownForTesting`** and
  **`OptionDialogs.setSuppressDialogs`** are the only surviving hits for the
  plan's own `ForTesting` name sweep. The register owns them under pass 30, so
  this is not an oversight — but they are live test-only production surface, and
  the plan's opening line currently has three exceptions.

---

## 10. Formatting left behind by the deletions

- A paragraph tag with nothing after it, immediately before the tag block — the
  paragraph it opened was the deleted sentence. Renders as a stray empty
  paragraph: `FootnotesComponent.java:99`, `TextPanel.java:185`,
  `StaffPanel.java:206`. These three are the only occurrences in `src/main/java`,
  and all three came from this change.
- A blank line between the last member and the class's closing brace, where a
  deleted member used to be: `BeamScoring.java`, `AppearanceManager.java`,
  `PreviewOverlayRegistry.java`, `MyFontUtils.java`.
- A doubled blank line at `MyFontUtils.java:90`, where `resetFontCache` was.

---

## Duplication

`Converter.java:72–78` and `src/test/java/songscribe/message/MessageCenterTestHelper.java:59–64`
each build the same thing: a synchronized `List<String>`, a lambda appending
`MessageCenter.describe(error)` to it, and an emptiness check — including two
hand-written copies of the comment explaining why the list is synchronized. A
small `PublicationErrorLog implements IPublicationErrorHandler`, holding the list
and exposing whether it is empty and what it collected, removes both copies and
gives the "swallowed handler error" idea one name. It has a production caller, so
it is not test-only surface.

---

## Checked and clean

- `Converter.run` reproduces what each converter's `main` did before: same
  ordering, and exit codes are untouched because they come from `System.exit(-1)`
  inside `ArgumentReader`, which runs before `run` checks its result.
  `ImageConverter` gains a log line on the rare reflection-failure parse path
  where it previously returned silently.
- No orphaned members from the deletions: `AppearanceManager.unregisterOsListener`
  still has its production caller (`switchTheme`), `PreviewOverlayRegistry.getOverlay`
  and `PreviewCursorHider.discard` still have production callers, and all four
  converters' `LOG` fields are still used.
- `describe` / `whichHandlerThrew` / `exitOnPublicationError` are a faithful
  decomposition of the old `handlePublicationError` — same null handling, same
  fatal-exit logic.
- The unit suite runs in a single non-forked JVM with no parallel execution
  configured, so `MessageCenterTestHelper`'s static scope and error list are safe
  as written.
- JUnit runs superclass `@BeforeEach` first and subclass `@AfterEach` first, so
  `MainFrameMockTest`'s subscriptions land inside the test's scope and its mock is
  closed before the scope is discarded. That ordering is correct.
- The new API adds no test-only surface of its own — every new member has at least
  one production caller, verified by reference lookup.
