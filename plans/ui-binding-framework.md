# UI Binding Framework
## Status Dashboard
| Phase | Description | Status | Sub-plan |
| --- | --- | --- | --- |
| 1   | [Binding core](#-phase-1-binding-core) | ✅ Complete | —   |
| 2   | [Controls and Widgets adapters](#-phase-2-controls-and-widgets-adapters) | ✅ Complete | —   |
| 3   | [Dialogs are built per opening and disposed on close](#-phase-3-dialogs-are-built-per-opening-and-disposed-on-close) | ✅ Complete | —   |
| 4   | [setText delegation on owned text controls](#-phase-4-settext-delegation-on-owned-text-controls) | ✅ Complete | —   |
| 5   | [BaseDialog bindings field and Tab accessors](#-phase-5-basedialog-bindings-field-and-tab-accessors) | ✅ Complete | —   |
| 6   | [TempoSection field visibility](#-phase-6-temposection-field-visibility) | ✅ Complete | —   |
| 7   | [METRIC becomes a Units enum](#-phase-7-metric-becomes-a-units-enum) | ✅ Complete | —   |
| 8   | [Ss for the title preview wrap width](#-phase-8-ss-for-the-title-preview-wrap-width) | ✅ Complete | —   |
| 9   | [Convert SongSettingsTitleTab](#-phase-9-convert-songsettingstitletab) | ✅ Complete | —   |
| 10  | [Convert SongSettingsDateInputRow](#-phase-10-convert-songsettingsdateinputrow) | ✅ Complete | —   |
| 11  | [Compile the end state and write the tests](#-phase-11-compile-the-end-state-and-write-the-tests) | ✅ Complete | —   |
| 12  | [Manual UI verification](#-phase-12-manual-ui-verification) | ✅ Complete | —   |
| 13  | [Guides and contract tags](#-phase-13-guides-and-contract-tags) | ✅ Complete | —   |
| 14  | [Defects found by the verification pass](#-phase-14-defects-found-by-the-verification-pass) | ✅ Complete | —   |

* * *
## ✅ Phase 1: Binding core
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/binding/package-info.java, src/main/java/songscribe/ui/binding/Subscription.java, src/main/java/songscribe/ui/binding/ObservableValue.java, src/main/java/songscribe/ui/binding/WritableValue.java, src/main/java/songscribe/ui/binding/Property.java, src/main/java/songscribe/ui/binding/ValueProperty.java, src/main/java/songscribe/ui/binding/Computed.java, src/main/java/songscribe/ui/binding/DependencyTracker.java, src/main/java/songscribe/ui/binding/Binding.java, src/main/java/songscribe/ui/binding/Bindings.java, src/main/java/songscribe/ui/binding/Transform.java, src/main/java/songscribe/ui/binding/Timing.java  
**Recommended model/effort:** Opus 5, high — dependency-tracking algorithm, re-entrancy semantics, and the contracts every later phase derives from

This phase creates the package `songscribe.ui.binding`. Nothing in the codebase references it yet. Do not compile — Phase 11 is the single compile gate for the whole plan; a compile here would only report that later phases have not run.

Read `.claude/guides/contracts.md` and `.claude/rules/java.md` before writing any contract. Read `.claude/guides/null-handling.md` before writing any `@Nullable`.
### The design this phase implements
A **property graph**. A value is a _view_ onto storage that already exists — a Swing control, or a plain holder — never a second copy. Bindings declare _target ← source_. The framework owns observation, propagation and re-entrancy.

Three capabilities, three types, so the compiler refuses a nonsense edge:

| Type | Can be read | Can be observed | Can be written |
| --- | --- | --- | --- |
| `ObservableValue<T>` | ✅   | ✅   | ❌   |
| `WritableValue<T>` | ❌   | ❌   | ✅   |
| `Property<T>` (extends both) | ✅   | ✅   | ✅   |

A `computed` is an `ObservableValue`, so it cannot be a bind target. A Swing component's enabled state, font or preview is a `WritableValue`, so it cannot be a bind source — there is no Swing notification behind any of them, and a source that can never fire is a binding that silently never runs.

`computed(body)` is the sole derivation construct and works like Vue's `computed`: the body's dependency set is **discovered by running it**, not declared. Every read of an `ObservableValue` that executes during the run registers itself. The set is re-collected on every evaluation, so a body that reads a value only on some branches is subscribed to it only on those branches.

Evaluation is **lazy and cached**, as Vue's is: a dependency change marks the computed dirty and propagates the dirty flag eagerly, but the body runs only when the value is next read. `Binding` is the eager consumer that reads it, so a bound computed behaves eagerly while an unbound intermediate shared by two readers still evaluates once per change.
### Tasks
1. Create `package-info.java` carrying `@NullMarked`, matching the form used by every other package under `src/main/java/songscribe/`. Its Javadoc states the two package invariants: **every call in this package happens on the EDT**, and **values are replaced, never mutated** — an `ObservableValue<T>` observes replacement of `T` and cannot see a mutation inside it, so a mutable `T` silently fails to notify.
  
2. Write the contracts for `ObservableValue<T>`, `WritableValue<T>` and `Property<T>` before implementing any of them. These are the framework's highest fan-in types and earn full contracts: preconditions, postconditions, errors, boundary semantics, invariants. Use the custom Javadoc block tags `@invariant` (singular, repeatable — one clause per tag) and `@effects`, both introduced by this plan: `@invariant` carries result invariants and boundary semantics, `@effects` carries mutation, subscriptions taken or released, and threading. Neither tag is registered with any build tool, and none is needed — `build.gradle.kts` has no javadoc task and javac does not validate Javadoc tags without doclint.
  
3. Write `Subscription` — a single-method interface with `void cancel()`, idempotent, so cancelling twice is not an error.
  
4. Write `ObservableValue<T>`: `T get()`, `Subscription observe(Runnable onChange)`, and the static factory `static <T> ObservableValue<T> computed(Supplier<T> body)`. There is **no** `map`, no `combine`, and no fluent predicate algebra (`isNotBlank`, `and`, `not`). Single-source transformation is expressed at the bind site via the `Bindings.bind` overload taking a `Function`, and a multi-source boolean is a `computed`. A rule that a binding shares with a `validate` clause is a named domain function both call, never a method on this interface — a rule that lives inside the framework cannot be referenced by a controller.
  
5. Write `WritableValue<T>` — a single-method interface with `void set(T value)`. Its contract states that a `WritableValue` is a **sink**: it has no readable current value and no notification, so it can only ever be a bind target. That is the promise `Widgets` relies on in Phase 2.
  
6. Write `Property<T> extends ObservableValue<T>, WritableValue<T>`, adding no members of its own. State in the contract that this is the two-way case — a control the user edits and the framework also writes — and that a target needing only `set` should be typed `WritableValue` so the compiler refuses it as a source.
  
7. Write `DependencyTracker` (package-private, static members only). It holds one `ThreadLocal` recording set — a thread-local rather than a static field because nothing here is synchronized and the EDT invariant is what makes that safe. Two members: `static <T> T recording(Set<ObservableValue<?>> into, Supplier<T> body)` which installs the set, runs the body, and restores the previous set on the way out (in a `finally`, so a throwing body cannot leave recording armed); and `static void track(ObservableValue<?> read)` which adds to the current set when one is installed and does nothing when none is. Every `get()` implementation in this package calls `track(this)`.
  
8. Write `Computed<T> implements ObservableValue<T>` (package-private; reached only through `ObservableValue.computed`). It holds the body, the cached value, a dirty flag, and the current dependency set with their subscriptions. On evaluation it runs the body inside `DependencyTracker.recording`, then **cancels the subscriptions of dependencies not touched this run** and subscribes to newly touched ones — that unlink step is what makes a branching body correct. A dependency's change marks dirty and notifies observers; the body runs on the next `get()`.
  
9. Add cycle detection to `Computed`: a computed re-entered while its own evaluation is in progress throws with a message naming the computed. Do not write a test for it — a cyclic binding is a programming error, not a value any caller supplies.
  
10. Write `ValueProperty<T> implements Property<T>` — a plain holder for values with no control behind them. This is a deliberate carveout in a design whose properties are otherwise views: `SongSettingsAttributionTab:113-114` holds `attributionFont`/`subAttributionFont` as bare fields because a chosen font is displayed as a description string in a `JLabel`, not held by any control. `set` notifies observers **only when the new value differs** by `Objects.equals`; state that as an `@invariant`, because it is what makes `Bindings.onChange` fire on a real transition rather than on every write.
  
11. Write `Transform<A,B>` as a record of `Function<A,B> forward` and `Function<B,A> backward`. A record rather than two loose parameters at the bind site, because two adjacent same-shaped functions are transposable and the compiler would not catch it.
  
12. Write `Timing` — `WHILE_TYPING` and `ON_COMMIT`. Swing gives text-bearing controls three unrelated notification routes with different semantics, and the dialogs being converted deliberately use more than one. This enum is a parameter to the text factories in `Controls` only; it means nothing for a checkbox and must not appear on `bind`.
  
13. Write `Binding<T>` (package-private) — one edge. It holds its **own**`applying` flag. When the framework writes the target, the flag is set for the duration of that write and any notification arriving back on _this_ edge is dropped. The flag is per edge, never per `Bindings`: a dialog-wide flag swallows legitimate propagation through other edges, which is the defect `SongSettingsDateInputRow:69,92-95,106,122,170-174` currently has and the reason its `setValues` has to re-sync by hand at `:175`.
  
14. Give `Binding` an **unchanged-value stop**: it remembers the last value it wrote to its target and writes nothing when the newly computed value equals it by `Objects.equals`. The comparison is against the last written value, not against the target's current value, because a `WritableValue` target has no readable current value — `BaseTitleComponent` has `setPreview` and no getter, and `Widgets.preview` in Phase 2 is built over exactly that. A binding has no last-written value when it is created, so its first evaluation always writes, which is what task 17 requires. State the stop as an `@invariant` on every `bind` overload.
  
15. Write `Bindings implements Disposable` with exactly these six methods, and write each one's contract before its body:
  
  - `<T> void bind(WritableValue<T> target, ObservableValue<? extends T> source)`
    
  - `<S,T> void bind(WritableValue<T> target, ObservableValue<S> source, Function<S,T> transform)`
    
  - `<S,T> void bind(Property<T> target, ObservableValue<S> source, BiFunction<S,T,T> merge)`
    
  - `<T> void bindBidirectional(Property<T> a, Property<T> b)`
    
  - `<A,B> void bindBidirectional(Property<A> a, Property<B> b, Transform<A,B> transform)`
    
  - `void onChange(ObservableValue<?> source, Runnable action)`
    
  - `void dispose()` — cancels every subscription every edge holds
    
  
  The merge overload takes `Property<T>`, not `WritableValue<T>`, because merge folds the source into the target's **current** value and so needs to read it; state that in its contract as the reason the two three-argument overloads have different target types. They resolve unambiguously in any case because the lambdas differ in arity. The transform overloads subscribe to the named source directly and do **not** run the dependency tracker; only `computed` does.
  
16. `Bindings.onChange` is the framework's effect construct: it observes `source` and runs `action` when `source` notifies, holding the subscription so `dispose()` cancels it. State in the contract that it runs an effect and produces no value, and that firing on a real change rather than on every write is a property of the source — a `ValueProperty` notifies only on a change (task 10), a `Computed` notifies whenever a dependency changes. A caller that needs change-only semantics binds a `ValueProperty` from the computed and calls `onChange` on that `ValueProperty`; `SongSettingsTitleTab`'s subtitle repack in Phase 9 is the worked example, and the contract names it.
  
17. `bind` evaluates the source once at registration and writes the target, so a binding is settled the moment it is created. State that in the contract.
  
18. Read `docs/lifecycle.md` before writing `dispose()`. A `Bindings` belongs to one `BaseDialog`, which Phase 3 makes per-opening and disposes on close, so `dispose()` has a real caller and the `Disposable` marker is earned. State in the class Javadoc, under a `Lifecycle` heading, that `BaseDialog.dispose()` calls it.
  

* * *
## ✅ Phase 2: Controls and Widgets adapters
**Status:** Complete  
**BlockedBy:** 1  
**Files:** src/main/java/songscribe/ui/binding/Controls.java, src/main/java/songscribe/ui/binding/Widgets.java  
**Recommended model/effort:** Opus 5, high — each adapter picks the Swing notification route and the write route, and picking wrong is silent

Phase 1 creates `songscribe.ui.binding` with `ObservableValue<T>`, `WritableValue<T>`, `Property<T>`, `ValueProperty<T>`, `Timing` (`WHILE_TYPING` / `ON_COMMIT`), and a package-private `DependencyTracker` whose `track(ObservableValue<?>)` every `get()` must call. Do not compile — Phase 11 is the single compile gate.

`Controls` holds **sources**: two-way `Property` views over controls the user edits. `Widgets` holds **sinks**: `WritableValue` views over presentation state a dialog computes. That split is the class boundary, and the return types are what enforce it.
### Tasks
1. Write `Controls` — a factory class of static methods returning `Property` views over Swing controls. It has a private constructor and no state. Each returned property's `get()` calls `DependencyTracker.track(this)` before answering, so a `computed` body that reads it registers the dependency.
  
2. **Every adapter's contract names the Swing notification route it observes and whether that route fires on a programmatic write**, because picking the wrong route loses writes silently and nothing reports it. The routes that do fire on a programmatic write are `DocumentListener` (`setText` goes through the Document), `ActionListener` on `JComboBox` (`setSelectedItem` fires it), `ItemListener` on `AbstractButton` (`setSelected` fires it), and `ChangeListener` on a `SpinnerModel` (`setValue` fires it). The two that do not are `focusLost` and `ActionListener` on an `AbstractButton` — which is why task 6 observes items rather than actions.
  
3. `Controls.text(JTextComponent field, Timing timing)` and `Controls.text(JTextComponent field, Timing timing, UnaryOperator<String> normalizer)` return `Property<String>`. `WHILE_TYPING` observes via a `DocumentListener` (all three of `insertUpdate`, `removeUpdate`, `changedUpdate` fire the same change); `ON_COMMIT` observes via a `FocusAdapter.focusLost`. State as a precondition that the normalizer must be idempotent, since it is applied to text it has already produced.
  
4. `Timing` **and the normalizer have separate timings, and the contract says so.**`Timing` governs when the property **notifies**. The normalizer always runs on focus loss, whatever the `Timing`. Both overloads install at most one focus listener, and when a normalizer is present that one listener does three things in this order: normalize the field's text, write the normalized text back into the field, then notify. Registering the normalizer and an `ON_COMMIT` notification as two separate `FocusAdapter`s makes the notified value depend on listener registration order, and the losing order notifies with un-normalized text and never notifies again.
  
5. The normalizer overload is what replaces the six hand-written focus adapters in `SongSettingsTitleTab:167,173`, `SongSettingsAttributionTab:226,311,333` and `SongSettingsDateInputRow:84`. State in `Controls.text`'s contract that `ON_COMMIT` is the one route in this class that a programmatic `setText` does not reach, and that the delegating `setText` Phase 4 adds to `MyJTextField` and `MyJTextArea` is what closes it: a write to one of those two classes is routed into the associated property, so it notifies whatever the field's own listener would have missed. A plain `JTextComponent` this repo does not own — a `JSpinner`'s editor, for instance — has no such delegation, so an `ON_COMMIT` property over one goes stale on a programmatic write. Name that limitation in the contract rather than leaving it to be discovered.
  
6. `Controls.item(JComboBox<E> combo)` returns `Property<E>` over `getSelectedItem`/`setSelectedItem`, observing with an `ActionListener`, which `setSelectedItem` fires — so a programmatic selection propagates and needs no delegation. Every combo it is used with in this plan is uneditable, so the editor's text never diverges from the selected item; state that as a precondition.
  
7. `Controls.selected(AbstractButton button)` returns `Property<Boolean>` over `isSelected`/`setSelected`, observing with an `ItemListener`. An `ActionListener` here fires only on a user click and not on `setSelected`, so it would lose every programmatic write; the contract says so in one line.
  
8. `Controls.number(SpinnerModel model)` returns `Property<Number>` over `getValue`/`setValue`, observing with a `ChangeListener`.
  
9. `Controls.value(JSlider slider)` returns `Property<Integer>` over `getValue`/`setValue`, observing with a `ChangeListener`. State in the contract that it notifies throughout a drag, because `getValueIsAdjusting` is not consulted — a caller wanting drag-end only reads the slider directly.
  
10. `Controls.choice(EnumMap<E, AbstractButton> buttons)` returns `Property<E>` for a radio group: `get()` answers the constant whose button is selected, `set` selects that constant's button, and it observes each button with an `ItemListener`. **Reject a map that does not cover every constant of** `E`, in the factory, with a message naming the missing constants. That is a boundary conversion, not a dead guard: a caller genuinely can hand over a partial map, and converting here is what lets `get()` be total. Take an `EnumMap` rather than a `Map` so iteration order is the enum's own.
  
11. Write `Widgets` — the same shape as `Controls`, but returning `WritableValue<T>` over presentation state a dialog computes. Private constructor, no state. Nothing here reads or observes: state in the class Javadoc that Swing offers no notification for any of these, which is why they are sinks and why the compiler refuses one as a bind source.
  
12. `Widgets.enabled(JComponent)` and `Widgets.visible(JComponent)` return `WritableValue<Boolean>` over `setEnabled` / `setVisible`.
  
13. `Widgets.font(JComponent)` returns `WritableValue<Font>`; its `set` calls `setFont`, then `revalidate()` and `repaint()`, which is what `SongSettingsTitleTab:307-317` does by hand today.
  
14. `Widgets.labelText(JLabel)` returns `WritableValue<String>` over `setText`.
  
15. `Widgets.preview(BaseTitleComponent)` returns `WritableValue<BaseTitleComponent.Preview>` over the component's `setPreview`. `songscribe.ui.component.score.TitleComponent` and `SubtitleComponent` both extend `BaseTitleComponent`, so one factory serves both. It takes no other parameter: a dialog that must react to the preview's emptiness does so with `Bindings.onChange` over its own `ValueProperty<Boolean>`, which is Phase 9 task 6.
  
16. Confirm `songscribe.ui.component` and `songscribe.ui.binding` may reference one another. `src/test/java/songscribe/PackageDependencyTest.java` forbids only `dom → layout`, `dom → ui` and `layout → ui`, so no rule is at stake; record that you checked rather than assuming it.
  

* * *
## ✅ Phase 3: Dialogs are built per opening and disposed on close
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/dialog/BaseDialog.java, src/main/java/songscribe/ui/action/DialogOpenAction.java, src/main/java/songscribe/ui/component/MainFrame.java, src/main/java/songscribe/ui/component/score/BaseTitleComponent.java, src/main/java/songscribe/ui/dialog/FontSettingRow.java, src/main/java/songscribe/ui/dialog/SongSettingsFontTab.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java, src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java  
**Recommended model/effort:** Opus 5, high — a lifecycle change across the dialog framework, with a disposal obligation that has to reach every owner

Do not compile — Phase 11 is the single compile gate for the whole plan.

Read `docs/lifecycle.md` and `.claude/guides/dialogs.md` first, and `docs/messages.md` for the rule that a constructor-side subscription creates a disposal obligation.
### What is wrong now
`UIAction` subscribes itself to the message bus in its constructor (`UIAction.java:300`) and already has `dispose()` → `MessageCenter.unsubscribe` (`:313`). Nothing calls it for an action a dialog owns, because nothing disposes the dialog:

- `DialogOpenAction.getDialog()` (`DialogOpenAction.java:68-74`) builds the dialog on first use and holds it for the life of the process.
  
- `BaseDialog` has no `dispose()`. `setVisible(false)` disposes the `JDialog`, not the `BaseDialog`.
  
- `FontSettingRow.create` builds two `UIAction`s per row (`FontSettingRow.java:179,207`) and there are six call sites — `SongSettingsFontTab:92,105`, `SongSettingsTitleTab:249,276`, `SongSettingsAttributionTab:357,368`. With `SongSettingsTitleTab`'s `TakeFirstLyricsWordAction` (`:112`) that is thirteen actions handling every `@Handler` message for the rest of the run, on behalf of a window nobody has open. The font actions capture `onFontChosen` consumers that close over the tab, so a closed document's lyrics stay reachable from the bus.
  
- The per-gesture dialogs are worse: `AnnotationDialog`, `BeatChangeDialog` and `TempoChangeDialog` are constructed fresh at each gesture and dropped with nothing disposing them, so their subscriptions accumulate per gesture.
  

The pieces that make the fix possible already exist. `SAVED_GEOMETRY` is a static map keyed by `getClass()`, so the one piece of dialog state that must outlive a close is already outside the instance, and `getData()` reads `ops.read()` afresh on every opening, so nothing else in the instance is meant to survive one.
### Tasks
1. Add `public void dispose()` to `BaseDialog`, implementing `songscribe.lifecycle.Disposable`. It disposes every registered `Tab` and unsubscribes anything `BaseDialog` itself holds. Give the class Javadoc a `Lifecycle` heading naming `setVisible(false)` as the caller, per `docs/lifecycle.md`. Make it **idempotent** — `setVisible(false)` is reachable from `windowClosing` as well as from the OK/Cancel path, and a second call must not double-unsubscribe or throw.
  
2. Add `protected void dispose()` to the inner class `Tab`, defaulting to a no-op. A tab that owns a `Disposable` overrides it; a tab that owns none does not, and `Disposable`'s own contract is why an empty override must not be written.
  
3. Call `dispose()` from `setVisible(false)`, inside the existing `finally` block and **after** `dialog.dispose()` and the blocking-count decrement, so a handler reacting to `DialogVisibilityDidChangeNotification(false)` still sees a fully torn-down window. State in the method's contract that an instance is not reusable after a close.
  
4. Remove the cached `dialog` field from `DialogOpenAction` and build a fresh dialog in `performAction`. Rename `getDialog()` to `newDialog()` so no caller can read the name as "the one dialog"; its three call sites are `DialogOpenAction:64`, `BaseTitleComponent:126` and `MainFrame:842`, and all three build-and-show without retaining the instance.
  
5. Make `FontSettingRow.create` hand its two actions back to the caller so the owning tab can dispose them. Change its return type from `JPanel` to a nested `record Row(JPanel panel, Disposable chooseAction, Disposable resetAction)`, so the row's assembly stays in one place and no new field appears on `FontSettingRow`. Every call site adds `.panel()` where it adds the row to a section, and keeps the two actions in a field to dispose.
  
6. Override `Tab.dispose()` in `SongSettingsFontTab`, `SongSettingsTitleTab` and `SongSettingsAttributionTab` to dispose the font-row actions each holds, and in `SongSettingsTitleTab` also `takeAction`. `takeAction` stays a `UIAction`: its enablement travels through the same hook every other action in the application uses, and this phase is what releases it.
  
7. Check every other `BaseDialog` subclass — `PreferencesDialog`, `SongSettingsDialog`, `KeyChangeDialog`, `FontDialog`, `ProgressBarDialog`, `AttachmentDialog` and its three subclasses — and every other `Tab` subclass — `PreferencesDialog.GeneralTab`, `PlayTab`, `InstrumentsTab`, `SongSettingsMusicTab` — for a constructor-side `MessageCenter.subscribe` or a field whose type implements `Disposable`, using `jet_brains_find_referencing_symbols` per `.claude/rules/serena.md`. Report the full list. `PreferencesDialog.ScaleAction` is a plain `AbstractAction` (`PreferencesDialog.java:745`) and subscribes to nothing. If a class outside this phase's `Files` needs a `dispose()`, stop and report which rather than widening the phase.
  
8. `AboutDialog` subscribes itself at `AboutDialog.java:156` but extends `JDialog` directly, so it is outside this change. Confirm by reading the class that it unsubscribes on its own dismissal path, and report what you found.
  
9. **A non-modal dialog must not be built twice while one is up.**`PreferencesDialog` is the only non-modal `BaseDialog` (`PreferencesDialog.java:106`; `ProgressBarDialog:38` is modal, and `AboutDialog` and `MigrationWindow` extend `JDialog` directly). With the cache gone, `setVisible(true)` returns immediately for it and the caller drops its reference, so a second invocation would build and show a second Preferences window. `UIAction.Flag.OPENS_DIALOG` plus the blocking counter disables the menu action while a blocking dialog is visible, but `MainFrame.handlePrefs()` (`:842`) reaches the dialog directly and bypasses that. Keep one live instance per non-modal dialog while it is showing — reuse it and bring it to the front rather than building a second — and route `handlePrefs()` through the same path so both entry points obey it. State the rule in `BaseDialog`'s contract.
  
10. State in `BaseDialog`'s class Javadoc **why a non-modal dialog stays reachable after its opener returns**: `setVisible(true)` registers anonymous `WindowAdapter`s that capture `BaseDialog.this`, the `JDialog` holds those adapters, and AWT holds a showing window through its owner. Removing those listeners without replacing the reference would make the dialog collectible while the user is looking at it, which is why the reason is written down rather than left to be re-derived.
  

* * *
## ✅ Phase 4: setText delegation on owned text controls
**Status:** Complete  
**BlockedBy:** 1  
**Files:** src/main/java/songscribe/ui/component/MyJTextField.java, src/main/java/songscribe/ui/component/MyJTextArea.java  
**Recommended model/effort:** Opus 5, medium — the delegation is mutually recursive with the property's own write, it runs during `JTextField`'s own constructor, and two subclasses inherit the changed behaviour

Phase 1 creates `songscribe.ui.binding` with `Property<T>`. Do not compile — Phase 11 is the single compile gate.

A `Property<String>` over a text field observes it, and which route it observes decides whether a programmatic write is seen. `WHILE_TYPING` observes a `DocumentListener`, which `setText` does reach, so a stray write already propagates correctly — `NonBlankGuard` writes a bound field directly on a blank restore and the binding handles it. `ON_COMMIT` observes `focusLost`, which `setText` does **not** reach, so a programmatic write is lost until focus happens to leave. Routing `setText` into the associated property closes that, and does so without changing any call site: a stray write becomes a correct write rather than an error.

Reads need no such treatment. A `computed` body reads its inputs through properties — that is the rule Phase 13 writes into `.claude/guides/bindings.md` — so no override of `getText()` goes in. Leaving `getText()` alone keeps a core accessor free of side effects on every text field in the application, including `LyricEditor`, which extends `MyJTextField` and is a score-editing component rather than a dialog control.
### Tasks
1. Give `MyJTextField` an association between the field and the `Property<String>` that `Controls.text` created for it, if any. Use a `JComponent` client property keyed by a constant owned by the binding package. It must **not** be a new field on `MyJTextField`: `JTextField`'s own constructor calls `setText(text)`, which reaches the override below before any subclass field initializer has run, and a field read there would be null where a client-property read is safely absent.
  
2. Do the same for `MyJTextArea`.
  
3. Override `setText` on both classes to **delegate rather than reject**: when a `Property<String>` is associated with the field, route the write into that property's `set` instead of writing the document directly; when none is, call `super.setText`. This is not a trap — no call site changes and nothing throws. `NonBlankGuard`, which writes a bound field directly on a blank restore, keeps working and needs no knowledge of the binding framework.
  
4. The delegation is mutually recursive with the property's own write — the property writes the field, which calls `setText`, which calls the property — so guard it the same way `Binding` guards an edge: the property sets an `applying` flag on the field for the duration of its own write, and `setText` checks that flag and falls through to `super.setText` while it is set. Put the flag where the property association already lives so no new field is added. A missing guard here is an infinite recursion on the first write, not a subtle bug, and Phase 11 is the compile gate so it cannot be run here — the reasoning must stand on its own, and the phase report must state it explicitly.
  
5. `NumericTextField extends MyJTextField` and `LyricEditor extends MyJTextField` need no change of their own — state in the commit that both inherit the behaviour, so a reader does not go looking for further edits.
  
6. Do not extend the delegation to any control this repo does not own. A `JSpinner`'s editor and any plain `JTextComponent` keep Swing's behaviour, and `Controls.text`'s contract names that limitation — Phase 2 task 5 writes it.
  

* * *
## ✅ Phase 5: BaseDialog bindings field and Tab accessors
**Status:** Complete  
**BlockedBy:** 1, 3  
**Files:** src/main/java/songscribe/ui/dialog/BaseDialog.java  
**Recommended model/effort:** Sonnet 5, medium — four members added, no behaviour change, but the disposal order matters

Phase 1 creates `songscribe.ui.binding.Bindings`, which implements `Disposable` and owns every binding edge for one dialog. Phase 3 gives `BaseDialog` a `dispose()` called from `setVisible(false)` and `Tab` an overridable `dispose()`. Do not compile — Phase 11 is the single compile gate.

Read `.claude/guides/dialogs.md` first; it is the contract this class carries.

`BaseDialog.Tab` is a non-static inner class of `BaseDialog`, but its subclasses (`SongSettingsTitleTab`, `SongSettingsAttributionTab`) are separate top-level classes. They have an enclosing instance, supplied by `dialog.super(...)`, but no syntax to reach it — `BaseDialog.this` is legal only lexically inside `BaseDialog`, and `Tab` extends `JPanel`, so it inherits nothing from `BaseDialog`. That is why both tabs currently capture the dialog in a `private final SongSettingsDialog dialog` field. Adding the accessors inside `BaseDialog`, where the qualified `this` is legal, removes the need for that field in every tab in the tree.
### Tasks
1. Add a `private final Bindings bindings = new Bindings();` field to `BaseDialog`, and dispose it from the `dispose()` Phase 3 added. Dispose the tabs **first** and the bindings second, so a tab's own `dispose()` still runs with its edges intact.
  
2. Add three `protected final` members to the inner class `Tab`, each delegating through the qualified enclosing instance:
  
  - `Bindings bindings()` → `BaseDialog.this.bindings`
    
  - `MainFrame getMainFrame()` → `BaseDialog.this.getMainFrame()`
    
  - `void repackToContent()` → `BaseDialog.this.repackToContent()repackToContent()` is already `protected` on `BaseDialog` and is a no-op until the dialog is showing, so it stays safe to call from population code.
    
3. Give each a one-line contract naming what it answers. These are shallow — `BaseDialog.Tab` has few subclasses and the promise is "the owning dialog's".
  
4. Do not edit `.claude/guides/dialogs.md` here. Phase 13 owns every guide edit so that two phases never write the same document.
  

* * *
## ✅ Phase 6: TempoSection field visibility
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/ui/dialog/TempoSection.java  
**Recommended model/effort:** Haiku 4.5, low — four visibility keywords
### Tasks
1. In `src/main/java/songscribe/ui/dialog/TempoSection.java`, make the four fields at lines 52-55 — `tempoTypeCombo`, `tempoSpinnerModel`, `tempoDescriptionCombo`, `showOnlyDescriptionCheckBox` — `private`. They are currently package-private with no modifier.
  
2. Confirm before editing that nothing outside the class reads them, using `jet_brains_find_referencing_symbols` on each of the four (per `.claude/rules/serena.md`, which governs Java exploration in this repo). Every reference should be inside `TempoSection` itself. If any reference is outside, stop and report which — do not widen the change to fix the caller.
  
3. The class already exposes `getTempoType()`, `getVisibleTempo()`, `getTempoDescription()`, `isShowOnlyDescription()` and `setTempo(Tempo)`; those are the contract and need no change.
  

* * *
## ✅ Phase 7: METRIC becomes a Units enum
**Status:** Complete  
**BlockedBy:** —  
**Files:** src/main/java/songscribe/prefs/Units.java, src/main/java/songscribe/prefs/PrefsKey.java, src/main/java/songscribe/prefs/Prefs.java, src/main/resources/conf/user-defaults.json, src/main/java/songscribe/ui/dialog/PreferencesDialog.java  
**Recommended model/effort:** Sonnet 5, medium — a persisted-key rename with an obsolete-key cleanup step

Read `.claude/guides/prefs.md` before touching any of these files.

`PrefsKey.METRIC("metric")` is a boolean backing a two-radio group (`inchesRadio` / `centimetersRadio`) in `PreferencesDialog.GeneralTab` — read at `:287-288` and written at `:323-333`. Every other radio group in that class is backed by an enum. `.claude/rules/java.md` requires an enum, not a boolean, for a value that selects a mode, and `songscribe.prefs.StartupAction` is the existing precedent for an enum persisted as a string with a `valueOf` read guarded by `catch (IllegalArgumentException)`.
### Tasks
1. Create `src/main/java/songscribe/prefs/Units.java` — `public enum Units { INCHES, CENTIMETERS }`, in `songscribe.prefs` alongside `StartupAction`, which is the sibling this mirrors.
  
2. Replace the `METRIC("metric")` constant in `PrefsKey` with `UNITS("units")`.
  
3. In `src/main/resources/conf/user-defaults.json`, replace the entry `"metric": false` at line 10 with `"units": "INCHES"`. Per `.claude/guides/prefs.md` the `PrefsKey` and `user-defaults.json` key sets must stay in step — a scalar getter on a key with no default throws at runtime.
  
4. Remove the `Map.entry("metric", PrefsKey.METRIC)` entry from `MIGRATION_MAP` at `src/main/java/songscribe/prefs/Prefs.java:72`, and add the string `"metric"` to the `OBSOLETE_KEYS` list in the same file, which is what strips a removed key from an existing user's `prefs.json` on next launch.
  
5. Do **not** write a value migration mapping the old boolean to the new enum. A user who had chosen centimetres reverts to inches, and that costs nothing observable: `PrefsKey.METRIC` is write-only in the running application today — `plans/ui-dialog-interface-phase-10.md` §4 records that changing it is "expected to change nothing visible" until page setup lands. Adding a migration for a value nothing reads is work with no effect.
  
6. In `PreferencesDialog.GeneralTab`, replace the boolean read at `:287-288` and the `metricListener` at `:323-333` with reads and writes of `Units`, following the `StartupAction` shape already in the same class at `:296-306` and `:356-371`: `Units.valueOf(Prefs.getString(PrefsKey.UNITS))` guarded by `catch (IllegalArgumentException)` falling back to `Units.INCHES`, and `Prefs.put(PrefsKey.UNITS, units.name())` on change.
  
7. Drive the enum-to-radio selection from an exhaustive `switch` over `Units` with no `default` branch, as the appearance and startup switches in the same class already do, so adding a third unit fails to compile rather than silently selecting the wrong radio.
  
8. Find every other reader of `PrefsKey.METRIC` with `jet_brains_find_referencing_symbols` before editing, and convert each. The two known outside `PrefsKey` itself are `Prefs.MIGRATION_MAP:72` and the two `PreferencesDialog` sites above. Report the full list; if any reader is elsewhere, name it in the report. `conf/papertemplates` contains the unrelated word `metric` as a paper-template unit token and must not be touched.
  

* * *
## ✅ Phase 8: Ss for the title preview wrap width
**Status:** Complete  
**BlockedBy:** 3  
**Files:** src/main/java/songscribe/ui/component/score/BaseTitleComponent.java, src/main/java/songscribe/ui/dialog/SongSettingsInput.java, src/main/java/songscribe/ui/dialog/SongSettingsController.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java  
**Recommended model/effort:** Sonnet 5, medium — a type change through four files, with one arithmetic site

Do not compile — Phase 11 is the single compile gate for the whole plan.

Read `docs/unit-conversion.md` first. A staff-space measurement is an `Ss`, not a `double`; `BaseTitleComponent.lineWidthPx()` already wraps the raw value in `new Ss(...)` at the point of use, which is the double crossing two layers that the typed value removes.
### Tasks
1. Change `BaseTitleComponent.Preview` to `public record Preview(String text, Ss wrapWidthSs) {}`, updating the `@param wrapWidthSs` tag to name the type rather than "in staff spaces".
  
2. In `BaseTitleComponent.lineWidthPx()`, pass `currentPreview.wrapWidthSs()` straight to `toViewPx` instead of wrapping it. Leave the `theSong` branch as it is — `Song.getLineWidthSs()` still answers a `double`, and typing that is not this plan's work; report it as a finding.
  
3. Change `SongSettingsInput`'s `lineWidthSs` component from `double` to `Ss`, updating its `@param` tag and the sentence in `copy()`'s Javadoc that calls the width "a `double`" — an `Ss` is an immutable value, so the copy behaviour is unchanged and the reason it is not rebuilt is now its immutability.
  
4. Update `SongSettingsController:88`, which constructs the `SongSettingsInput`, to hand over an `Ss`.
  
5. Update `SongSettingsTitleTab:107,349,357,418` for the new type. The field stays a plain field in this phase; Phase 9 is what turns it into a property.
  

* * *
## ✅ Phase 9: Convert SongSettingsTitleTab
**Status:** Complete  
**BlockedBy:** 2, 4, 5, 8  
**Files:** src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java  
**Recommended model/effort:** Opus 5, high — replaces hand-wired listeners, an ordering constraint and a manual repack with a declarative graph

Do not compile — Phase 11 is the single compile gate for the whole plan.

The API this phase consumes, created by Phases 1, 2 and 5:

- `songscribe.ui.binding.ObservableValue.computed(Supplier<T>)` — a value recomputed whenever any value its body reads changes; the dependency set is discovered by running the body, never declared, and re-collected each run.
  
- `Property<T>` — `get`, `set`, `observe`. `WritableValue<T>` — `set` only.
  
- `Controls.text(JTextComponent, Timing)` and `Controls.text(JTextComponent, Timing, UnaryOperator<String>)` → `Property<String>`.
  
- `Widgets.font(JComponent)` → `WritableValue<Font>`; `Widgets.labelText(JLabel)` → `WritableValue<String>`; `Widgets.preview(BaseTitleComponent)` → `WritableValue<BaseTitleComponent.Preview>`.
  
- `ValueProperty<T>` — a holder for a value with no control behind it.
  
- `Bindings.bind(...)` and `Bindings.onChange(ObservableValue<?>, Runnable)`.
  
- `bindings()`, `getMainFrame()` and `repackToContent()` as `protected final` members inherited from `BaseDialog.Tab`.
  
- `Tab.dispose()`, which this class already overrides after Phase 3.
  
### Tasks
1. Delete the `private final SongSettingsDialog dialog` field at `:73` and every use of it, replacing `dialog.getMainFrame()` (`:250`, `:279`) with the inherited `getMainFrame()` and `dialog.repackToContent()` (`:366`) as described in task 6 below. The tab must no longer name `SongSettingsDialog` anywhere.
  
2. Replace the raw control fields with properties over them, keeping the controls themselves as fields because the layout code adds them: `number` over `numberField` (`WHILE_TYPING`), `title` over `titleField` (`WHILE_TYPING`, with `SongMetadata::normalizeTitle` as the normalizer), `subtitle` over `subtitleField` (`WHILE_TYPING`, same normalizer).
  
3. Replace `previewWrapWidthSs` (`:107`) with a `ValueProperty<Ss> wrapWidthSs`. Making the wrap width a property is what removes the ordering constraint documented at `:410-411` — "the preview width is set before any field, because writing to a field fires the preview updaters" — since setting it in any order now triggers the recompute. Delete that paragraph from `populate`'s contract.
  
4. Leave `lyricsText` (`:106`) a plain `String` field. Nothing observes it: the Take button's enablement is re-derived by `UIAction.enableFromSongState()` (`:454`) and `populate` calls `takeAction.updateEnabledState()`. Wrapping a value with no observer in a `ValueProperty` adds an indirection the graph never traverses.
  
5. Delete both anonymous `DocumentListener` classes (`:124-141` and `:145-161`), both `FocusAdapter`s (`:167-172`, `:173-178`), `updateTitlePreview()` (`:346-353`) and `updateSubtitlePreview()` (`:355-368`), replacing them with two bindings in the constructor. **Both computed bodies apply** `SongMetadata.normalizeTitle` **to the field text**, exactly as `:350` and `:356` do today: `Controls.text`'s normalizer runs on focus loss, so a body reading `title.get()` raw would show straight quotes while the user types where the score renders curly ones.
  
  - `bindings().bind(Widgets.preview(titlePreview), computed(() -> new BaseTitleComponent.Preview(Song.numberedTitle(number.get(), SongMetadata.normalizeTitle(title.get())), wrapWidthSs.get())))`
    
  - the same shape for `subtitlePreview` over `subtitle` and `wrapWidthSs`, whose text is `SongMetadata.normalizeTitle(subtitle.get())`
    
6. Replace the `subtitlePreviewEmpty` field (`:98-100`) and the repack at `:364-367` with a `ValueProperty<Boolean> subtitleEmpty`, then `bindings().bind(subtitleEmpty, computed(() -> SongMetadata.normalizeTitle(subtitle.get()).isEmpty()))` and `bindings().onChange(subtitleEmpty, this::repackToContent)`. `ValueProperty.set` notifies only on a real change, so the repack fires on the empty ↔ non-empty transition and on nothing else, which is what the deleted field tracked by hand.
  
7. Replace `applyTitleFont` / `applySubtitleFont` (`:307-317`) and the two `FontSettingRow.applyFont` calls in `populate` with `ValueProperty<Font>` for each font, bound to `Widgets.font(titlePreview)` / `Widgets.font(subtitlePreview)` and to `Widgets.labelText(titleFontLabel)` / `Widgets.labelText(subtitleFontLabel)` through the `Function` overload of `bind` with `MyFontUtils::getFullFontDescription`. `FontSettingRow.create` keeps its existing `Supplier`/`Consumer` parameters and the disposal shape Phase 3 gave it — reworking that factory is not this phase's work; pass `font::get` and `font::set`.
  
8. Rewrite `populate(SongSettingsInput)` (`:413-436`) to set properties instead of controls. It no longer calls `updateTitlePreview()` at the end and no longer depends on the order in which it writes. `titleBlankGuard.rememberCurrentText()` calls stay — `NonBlankGuard` is unchanged by this plan and remains the blank policy for this field.
  
9. Keep every existing accessor — `getTitleText()`, `getNumberText()`, `getSubtitleText()`, `getTitleFont()`, `getSubtitleFont()`, `getTitleField()`, `getSubtitleField()` — with the same signatures and the same answers, reading the properties internally. `getTitleFont()` and `getSubtitleFont()` read the two `ValueProperty<Font>` from task 7 rather than `titlePreview.getFont()`, because `Widgets.font` is a sink with no readable value. `SongSettingsDialog` calls all of them for `gather` and for `showTab` focus targets and must not need editing.
  
10. Leave `TakeFirstLyricsWordAction` (`:438-468`) in place, reading `lyricsText` as it does today and writing the title through `title.set(...)` rather than `titleField.setText(...)`.
  
11. Do not add any test. `.claude/guides/testing-common.md` classifies a dialog's populate–gather path as UI, verified by opening the window; Phase 12 covers it.
  

* * *
## ✅ Phase 10: Convert SongSettingsDateInputRow
**Status:** Complete  
**BlockedBy:** 2, 3, 4, 5  
**Files:** src/main/java/songscribe/ui/dialog/SongSettingsDateInputRow.java, src/main/java/songscribe/ui/dialog/SongSettingsAttributionTab.java, src/main/java/songscribe/ui/component/NumericTextField.java  
**Recommended model/effort:** Opus 5, high — removes a re-entrancy flag whose presence currently forces a manual re-sync

Do not compile — Phase 11 is the single compile gate for the whole plan.

The API this phase consumes, created by Phases 1, 2 and 5:

- `ObservableValue.computed(Supplier<T>)` — dependencies discovered by running the body, re-collected each run.
  
- `Controls.text(JTextComponent, Timing)`, `Controls.item(JComboBox<E>)`.
  
- `Widgets.enabled(JComponent)` → `WritableValue<Boolean>`.
  
- `bindings()`, `getMainFrame()`, `repackToContent()` inherited from `BaseDialog.Tab`.
  

`Bindings` suppresses re-entrancy **per edge**: a write the framework performs on one edge is dropped only on that edge, and still propagates through every other. That is what makes `adjustingDateFields` unnecessary rather than merely relocated.

`SongSettingsDateInputRow` is not a `Tab` and has no `bindings()` of its own. It takes one in its constructor from the tab that builds it, so the dialog still owns every edge and disposes them.
### Tasks
1. Add `public boolean isValidValue(String text)` to `NumericTextField` — the existing body of `hasValidValue()` with the field read replaced by the parameter — and make `hasValidValue()` `return isValidValue(getText())`. The range check is a function of this field's own min/max and some text, so it belongs on this type as a named operation. Without it a `computed` body has no way to ask the question through a property, because reading the control directly registers no dependency.
  
2. In `SongSettingsDateInputRow`, delete the `adjustingDateFields` field (`:69`) and its six touch points (`:92-95`, `:106`, `:122`, `:170-174`). Delete the manual `updateFieldStates(...)` call at `:175` with it — that call exists only because the dialog-wide flag swallowed the propagation that would otherwise have refreshed the combos.
  
3. Replace the `FocusAdapter` on `yearField` (`:84-104`) and the two `ActionListener`s on `monthCombo` (`:105-120`) and `dayCombo` (`:121-129`) with properties: `year` over `yearField` (`ON_COMMIT`, matching today's `focusLost`), `month` over `monthCombo`, `day` over `dayCombo`.
  
4. Replace `updateFieldStates(boolean)` (`:198-202`) and its no-argument overload (`:194-196`) with two bindings:
  
  - `bindings.bind(Widgets.enabled(monthCombo), computed(() -> yearField.isValidValue(year.get())))`
    
  - `bindings.bind(Widgets.enabled(dayCombo), computed(() -> dayEnabled(yearField.isValidValue(year.get()), month.get())))` Both read the year through the `year` property, never off the field, so the `computed` records the dependency.
    
5. Keep the static `dayEnabled(boolean, int)` (`:136-138`) exactly as it is. It is already a pure named function, and having the binding call the same function any other caller would is the point.
  
6. Add a `Bindings` parameter to the constructor and **keep the** `Runnable onChange` **parameter** (`:71`). `SongSettingsAttributionTab` is not otherwise converted by this plan — its eight `refreshPreview()` call sites and its own listener wiring stay imperative — so handing it an `ObservableValue` to observe would be the same callback with an extra hop. Wire `onChange` from inside the row with `bindings.onChange(...)` on each of the three properties, **preserving today's firing condition exactly**: `onChange` runs only when the year is valid (`:100-102`, `:117-119`, `:126-128`), and month selection 0 still resets the day (`:110-112`). Say in the constructor's contract when `onChange` runs, since it is now the row's only outward signal.
  
7. Update the two construction sites in `SongSettingsAttributionTab` (`:81`, `:91`) to pass `bindings()` alongside `this::refreshPreview`. They stay field initializers: `dialog.super(...)` establishes the enclosing instance as part of the superclass constructor invocation, which completes before any of this class's field initializers run, so the inherited `bindings()` already answers.
  
8. Keep `setValues(String, int, int)` (`:169-176`) and the three getters (`:178-188`) with their current signatures, reading and writing properties internally.
  
9. While in `SongSettingsAttributionTab`, delete its `private final SongSettingsDialog dialog` field at `:74` and replace `dialog.getMainFrame()` (`:355`) and `dialog.repackToContent()` (`:443`) with the `getMainFrame()` and `repackToContent()` members Phase 5 added to `BaseDialog.Tab`. Leave the `titleTab` field at `:77` in place — retiring that cross-tab reference belongs with the attribution conversion, which this plan does not cover.
  
10. Do not add any test; Phase 12 covers this as UI.
  

* * *
## ✅ Phase 11: Compile the end state and write the tests
**Status:** Complete  
**BlockedBy:** 1, 2, 3, 4, 5, 6, 7, 8, 9, 10  
**Files:** src/test/java/songscribe/ui/binding/, src/main/java/songscribe/  
**Recommended model/effort:** Opus 5, high — cases derived from contracts, and the first compile of the whole end state

This is the plan's **single compile gate**. Every earlier phase deliberately left the tree uncompiled, so the first run will report the call sites the design did not account for; that is information about the design, not a regression. The `Files` entry names `src/main/java/songscribe/` because fixing those errors may touch any production file.

Read `.claude/guides/testing-common.md` and `.claude/guides/testing-unit.md` before writing any test. Derive every case from the **contract** of the method under test, its signature, and the public API of its declaring class — never from the body. A case learned from an implementation does not become a test until it is in the contract; put it in the contract first, as a visible change.

The seven tests below pin the dependency tracker's re-collection and the per-edge re-entrancy rule — a real algorithm and an invariant spanning several calls, which are two of the three kinds on the testing floor. Write them, run them green, then move them to the vault as `.claude/guides/testing-common.md` requires. Do not propose additional tests. In particular do not test the `Controls` or `Widgets` adapters (wiring), `Transform` (a record of two functions), the cycle guard (a guard gets no test — a cyclic binding is a programming error, not a value a caller supplies), or either converted dialog (a populate–gather path is UI).
### Tasks
1. Run `./scripts/compile.sh` and fix every error before writing a line of test code. Never use `./gradlew`, `gradle`, `javac` or `java -cp`.
  
2. Create `src/test/java/songscribe/ui/binding/` mirroring the source package, and `package-info.java` matching the form other test packages use. Test classes extend `UnitTest` (`src/test/java/songscribe/UnitTest.java`).
  
3. Before writing each test method, check whether it will sit beside a sibling that exercises the same method in the same way, with only the input or expected value differing. If it will, both are rows in one `record`-based case table driven by `@MethodSource`, from the first such case — not a refactor applied after three have accumulated.
  
4. Arrange every case through the public API. `ValueProperty` is a real `Property` implementation, so no mocking is required anywhere in this phase and none should appear: no `MainFrame` mock, no `mockStatic`, no reflection. A case needing more than `ValueProperty`, `Bindings` and `computed` to arrange is a constructor-or-factory finding against the framework — report it rather than reaching past the public API.
  
5. Write the seven tests, each named for the contract case it asserts:
  
  - a `computed` observes exactly the values its body read, and is not notified by a value it did not read
    
  - a `computed` whose body branches re-collects on each evaluation, so a value that stops being read stops notifying it
    
  - a binding does not re-enter itself, and a change still propagates through every other edge
    
  - a merge binding whose function is a fixed point terminates, via the unchanged-value stop
    
  - a `computed` read by two consumers evaluates once per dependency change (arrange with a `Supplier` that increments a counter the test owns — a real object, not a mock)
    
  - `bindBidirectional` with a `Transform` round-trips and settles rather than oscillating
    
  - `Bindings.dispose()` stops propagation on every edge it owns
    
6. Run `./scripts/test.sh` — the unit suite only, never `./gradlew test`. Report green before the phase is done. Read any failure's output for the error and location; do not rerun with flags and do not assume a failure is pre-existing.
  

* * *
## ✅ Phase 12: Manual UI verification
**Status:** Complete  
**BlockedBy:** 11  
**Files:** plans/ui-binding-framework.md  
**Recommended model/effort:** Opus 5, high — the only check the two converted dialogs and the new dialog lifetime get

No test covers a dialog's populate–gather path; a window is verified by opening it. **Ask the user for permission before running the app.** `./scripts/run.sh` is never executed without it. Record each result in the table below by editing this file.
### Tasks
1. Ask the user for permission to run the application, then launch it with `./scripts/run.sh`.
  
2. Walk the checks below, recording Pass or Fail and a note for anything unexpected.
  
3. Write the results into the table in this file, plus the date and build tested, and report any failure with the dialog, the gesture, and what was expected versus observed.
  

Tested 2026-08-16 against the branch build.

| #  | Check                                                                                                                                                               | Result |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| 1  | Song Settings → Title: typing in the title field updates the title preview live                                                                                     | Pass — after the preview row was pinned to the line width; see Phase 14 |
| 2  | Typing in the number field updates the title preview live                                                                                                           | Pass |
| 3  | The title preview shows typographic substitution (curly quotes) as typed, matching what the commit saves                                                            | Pass |
| 4  | Leaving the title field replaces its text with the normalized version                                                                                               | Pass |
| 5  | Leaving the subtitle field does the same                                                                                                                            | Pass |
| 6  | Emptying the subtitle collapses its preview and the window re-packs to fit                                                                                          | Pass |
| 7  | Typing a subtitle back expands the preview and the window re-packs again                                                                                            | Pass |
| 8  | Choosing a title font updates both the description label and the preview                                                                                            | Pass |
| 9  | Reset on the title font row does the same                                                                                                                           | Pass |
| 10 | The Take button is disabled for a song with no lyrics and enabled for one with lyrics                                                                               | Pass |
| 11 | Take fills the title from the lyrics and the preview follows                                                                                                        | Pass |
| 12 | Blanking the title field disables OK; tabbing away restores the previous title with an alert, and leaving it padded with spaces strips them                         | Pass — after Phase 14 |
| 13 | Song Settings → Attribution: the month combo is disabled until a valid year is entered                                                                              | Pass |
| 14 | The day combo is disabled until a month is chosen                                                                                                                   | Pass |
| 15 | Clearing the year disables both combos and resets their selections                                                                                                  | Pass |
| 16 | Changing any date field refreshes the attribution preview                                                                                                           | Pass — after Phase 14 |
| 17 | OK commits every field on both tabs, and the score reflects it                                                                                                      | Pass |
| 18 | Cancel discards every change on both tabs                                                                                                                           | Pass |
| 19 | Closing and reopening Song Settings shows the current document's values, not the previous opening's                                                                 | Pass |
| 20 | Song Settings reopens with the size and position it was closed at                                                                                                   | Pass |
| 21 | Double-clicking a title on the score opens Song Settings on the Title tab with the caret in the right field                                                         | Pass |
| 22 | Preferences opens, closes and reopens; the units radios still toggle and persist across a restart                                                                   | Pass |
| 23 | Invoking Preferences from the menu and from the macOS application menu while it is already open brings the existing window forward rather than opening a second one | Pass |
| 24 | Annotation, Beat Change and Tempo Change dialogs each open, populate and commit twice in a row                                                                      | Pass |
| 25 | Font, Key Change and the progress dialog each open and close without error                                                                                          | Pass |
| 26 | Song Settings and Preferences appear without a perceptible delay on the second and later openings, now that each opening rebuilds the window                        | Pass |

* * *
## ✅ Phase 13: Guides and contract tags
**Status:** Complete  
**BlockedBy:** 11  
**Files:** .claude/guides/bindings.md, .claude/guides/contracts.md, .claude/guides/dialogs.md, .claude/rules/java.md, docs/lifecycle.md  
**Recommended model/effort:** Opus 5, high — these documents are the conventions every later change is held to

Read `~/.claude/guides/documents.md` before editing any of these. It governs what may appear in a guide at all: no history, no decision logs, no "what changed and why". When amending, rewrite so the file states the current decision — do not quote the superseded sentence and do not leave a dated marker. Where a dropped option is one someone might actively re-introduce, state the constraint in the present tense rather than narrating that it was removed.
### Tasks
1. Write `.claude/guides/bindings.md` covering the conventions the framework creates, each stated as a rule rather than an explanation:
  
  - every control is created through `Controls`
    
  - a `computed` body reads its inputs through `ObservableValue`s only; a direct control read is invisible to the dependency tracker and yields a stale value
    
  - values are replaced, never mutated — an `ObservableValue<T>` observes replacement of `T` and cannot see a mutation inside it
    
  - every call is on the EDT
    
  - a source is an `ObservableValue`, a sink is a `WritableValue`, and a control the user edits is a `Property`; a target typed `Property` where `WritableValue` would do lets a sink be passed as a source
    
  - a rule shared by a binding, an input guard and a controller's `validate` is a named domain function all three call, never a method on the framework
    
  - properties are views onto controls, not a store; `ValueProperty` is the carveout for a value with no control behind it, and a value with no observer stays a plain field
    
  - there is no fluent predicate algebra — a single-source transform is the `Function` overload of `bind`, and a multi-source boolean is a `computed`
    
  - an effect that must run on a real change binds a `ValueProperty` from the computed and calls `Bindings.onChange` on that `ValueProperty`; calling `onChange` on a `Computed` directly runs the effect on every dependency change, not on every value change
    
  - an adapter's contract names the Swing notification route it observes and whether that route fires on a programmatic write; `DocumentListener`, `ActionListener` on a combo, `ItemListener` on a button and a spinner model's `ChangeListener` all do, while `focusLost` and `ActionListener` on a button do not, so a button adapter observes items and never actions
    
  - `Controls.text`'s `Timing` governs when the property notifies; a normalizer always runs on focus loss, in the same listener, before the notification
    
  - `MyJTextField` and `MyJTextArea` route `setText` into the associated property, so a direct write to a bound field of either class propagates rather than being lost; a control this repo does not own has no such delegation, and an `ON_COMMIT` property over one goes stale on a programmatic write
    
2. In `.claude/guides/contracts.md`, change the _Javadoc form_ column of the "What belongs in a method contract" table for the three rows that currently read `prose`: **Boundary semantics** and **Result invariants** become `@invariant`, **Side effects** becomes `@effects`. State that `@invariant` is singular and repeatable — one clause per tag, the way `@param` and `@throws` repeat — so adding an invariant is a one-line diff. State that both tags are required on a contract when a test is derived from it, and adopted elsewhere as contracts are touched; there is no retrofit.
  
3. In `.claude/rules/java.md`, the sentence under _Writing a Contract in Javadoc_ reading "Boundary semantics and result invariants … have no dedicated tag; state them in prose in the method's doc body" is now wrong. Rewrite it to name `@invariant`, and rewrite the neighbouring "Side effects and relationships" bullet so side effects name `@effects` while relationships keep `{@link}`.
  
4. In `.claude/guides/dialogs.md`, rewrite the lifetime statements, which are now wrong in two places. The opening paragraph's "Creates a fresh `JDialog` on each `setVisible(true)`, disposes on `setVisible(false)`" must also say that the `BaseDialog` itself is built per opening and disposed on close, and that an instance is not reusable. The _Opening_ section's "The dialog is built lazily on first use and cached per action, which is why `read` is asked on each opening" must state that a fresh dialog is built per opening and that `read` is asked on each opening because a dialog takes no input at construction. State that a tab owning a `Disposable` overrides `Tab.dispose()`.
  
5. In the same file, add `bindings()`, `getMainFrame()` and `repackToContent()` to the `### Tab` section as the members a tab inherits, and state that a tab does not hold its dialog. The `### BaseDialog API surface` section's claim that there is no inherited route to the score is unaffected and must stay.
  
6. In the same file, the `### Tab` paragraph ending "Nothing in the tree needs this today: `SongSettingsTitleTab`'s and `AnnotationDialog`'s `NonBlankGuard`s are UI-only guards … with no `validate` counterpart to duplicate" — keep the rule, and rewrite the closing clause to state that a live check now expresses itself as a binding to the same named function `validate` calls.
  
7. In `docs/lifecycle.md`, add dialogs to the _Object lifecycle_ section as a live case alongside the document model: a `BaseDialog` is retired when it closes, so it disposes its tabs and its `Bindings`, and a tab disposes the `UIAction`s its font rows and its own buttons own. State that this is what keeps a closed dialog's actions off the message bus.
  
8. Register `@invariant` and `@effects` in IntelliJ's additional-Javadoc-tags setting so the IDE does not flag them. No build change is needed: `build.gradle.kts` has no javadoc task, and javac does not validate Javadoc tags without doclint. Report to the user that this is a manual IDE step.
  
9. Confirm that the seven binding tests have been moved to the vault, so nothing but `PackageDependencyTest` is left resident. Do not add a note about it to any guide beyond what `.claude/guides/testing-common.md` already states.

* * *
## ✅ Phase 14: Defects found by the verification pass
**Status:** Complete  
**BlockedBy:** 12  
**Files:** src/main/java/songscribe/util/StringUtils.java, src/main/java/songscribe/ui/dialog/SongSettingsTitleTab.java, src/main/java/songscribe/ui/dialog/SongSettingsDateInputRow.java, src/main/java/songscribe/ui/dialog/BaseDialog.java, src/main/java/songscribe/ui/dialog/StandardDialog.java, src/main/java/songscribe/ui/component/NonBlankGuard.java, src/main/java/songscribe/ui/component/NonBlankTextField.java, .claude/guides/dialogs.md, docs/unit-conversion.md, .claude/guides/flatlaf-props.md  
**Recommended model/effort:** Opus 5, high — one of these adds a member to the class every dialog extends

Phase 12 opened the two converted dialogs and found five defects. Each is fixed here rather than recorded, and each carries the contract stating the promise it establishes.
### Tasks
1. `StringUtils.wrapText` wraps greedily and then drags words backwards until every line carries three, which pushes lines past `maxWidth` and, through `removeAll` over a sublist of the line being edited, drops repeated words. Replace it with balanced wrapping: the fewest lines the width allows — the count greedy already achieves — split to minimise total squared slack over every line including the last. State as invariants that no line is empty, that only a single over-wide word may exceed `maxWidth`, that the line count is the minimum, and that ties break toward the longer first line. Write the test, run it, and vault it.
  
2. The title preview collapses when a wrapped title first fits on one line: each preview sizes itself to its text, and the page-coloured row sized itself to the preview, so a one-line title reaches the full line width — wider than the dialog, which does not re-pack while the user types. Give `SongSettingsTitleTab` a `PreviewRow` whose width is the line width in unscaled pixels, bound to the same `wrapWidthSs` the previews wrap at, so the wrap happens at an edge the user can see. Pad each end by `DIALOG_COMPONENT_HORIZONTAL_GAP` **outside** that width, never by narrowing it — narrowing would wrap the preview earlier than the score does.
  
3. `SongSettingsDateInputRow` does not refresh the attribution preview when the year is cleared: all three effects fire only while the year is valid, so losing a date reports nothing. Run `onChange` on every notification. State in the constructor's contract that clearing the year writes the month and day too, so one gesture signals more than once, and that the last run is what stands.
  
4. Blanking a guarded field and pressing OK alerts and then commits, because `NonBlankGuard.shouldYieldFocus` always yields and `StandardDialog` proceeds. Add validity to `BaseDialog`: `requireValid(ObservableValue<Boolean>)` accumulating conditions, `valid` as their conjunction, and `StandardDialog` binding its OK button's enabled state to it. Hold the conditions in a `ValueProperty` over an **immutable** list, replaced rather than appended to, so a condition contributed while a tab is built reaches an OK button bound before that tab existed. `SongSettingsTitleTab` requires a non-blank title. Do not add the same rule to `SongSettingsController.validate` — the field's contract is that it never yields blank, and a check there would guard an impossible condition.
  
5. Give the rule to the field rather than to its installer: `NonBlankTextField` carries its own `NonBlankGuard`. Both are named for `isBlank`, which is what they test. The guard strips leading and trailing whitespace on focus loss, writing back only when the text differs, and remembers and answers stripped text — so the promise is "never blank once focus has left, and never padded".
  
6. Three documents describe APIs that no longer exist and cost a compile error each time they are followed: `docs/unit-conversion.md` names `ScaleContext.getInstance()` where every member is static, and misses `inchesToSs`/`ssToInches`; `.claude/guides/flatlaf-props.md` names the generated enum `FlatLafKeys` where it is `FlatLafKey`. Correct both, and document validity in `.claude/guides/dialogs.md`: the lifecycle line, a section stating that a rule about one control's own value is a condition while `validate` is for the gathered values as a whole, and `requireValid` in the `BaseDialog` and `Tab` member lists.
