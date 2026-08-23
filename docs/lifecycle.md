# Application Lifecycle

Startup and shutdown sequences. `MainFrame` and `Shutdown` carry prose summaries and
point here.

---

## Startup

Three threads participate: the main thread, the EDT, and two background threads
(`"midi-init"` and `"startup-gate"`).

```
[main thread]                          [EDT]                         [bg threads]
SongScribe.main
  set macOS props
  invokeLater(MainFrame.main) ──────►  initMinimalTheme():
                                         install Source Sans 3 Regular only
                                         setPreferredFontFamily(FAMILY)
                                         registerCustomDefaultsSource + AppearanceManager.init
                                       showSplash(); force first paint
                                         (splash JLabels now render in Source Sans, not fallback)
                                       splashShownAtMs = now
                                       midiReadyLatch =
                                         openMidiAsync() ───────────► "midi-init":
                                                                        openMidi() (capped via await)
                                                                        finally latch.countDown()
                                       installEagerFonts():
                                         remaining Source Sans faces + TiroBangla (~1.1 s)
                                       build main window (initFrame, NOT shown)
                                       pendingStartupAction = <autoload|arg|select>
                                       startStartupGate() ──────────► "startup-gate":
                                                                        sleep(remainingFloor)
                                                                        latch.await(remainingCap)
                                                                        invokeLater(reveal) ─┐
                                                                                             │
                            reveal (EDT): ◄──────────────────────────────────────────────────┘
                              drainStartupErrors():
                                fatal present → throw RuntimeError.exit(fatal.message())
                                                (logs + shows fatal dialog over splash + System.exit;
                                                 splash NOT hidden, window NOT revealed)
                              hideSplash(); setVisible(true)
                              preWarmDialogPeer / ActivationGate.install / requestFocusInWindow
                              for each non-fatal: showWarning(...)
                              maybeShowWhatsNew()
                              pendingStartupAction.run()
```

### Action-constant initialization order

`Actions.*` constants must exist before anything reads them, which fixes this order:

```
  MainFrame.getInstance()        — constructs the singleton via InstanceHolder
    └─► MainFrame.initFrame()    — wires the UI; called from main()
          ├─► Actions.initialize(this)  — populates all Actions.* constants
          │     └─► first constant use  — MenuController.init(this)
          ├─► PlaybackController.initialize(this)
          └─► PreviewElementManager.initialize()  — attaches the hover-preview singleton
```

---

## Shutdown

`Shutdown` is the process-global registry that funnels every user-invoked quit path
through one ordered, vetoable sequence, and owns the JVM shutdown hook so thread-safe
cleanup runs even on emergency exits.

```
  User-invoked quit paths              Emergency exit paths
  -------------------------            --------------------
  QuitAction              -+           RuntimeError.exit()
  MainFrame.windowClosing -+           SIGTERM
  CloseWindowAction       -+-> now()   last non-daemon thread ends
  Desktop quit (macOS)    -+      |             |
                                  v             |
                             Confirm phase      |
                             (reg. order,       |
                              vetoable, EDT)    |
                                   |            |
                             veto? | no         |
                                   v            |
                             EDT cleanup        |
                             (LIFO, EDT)        |
                                   |            |
                                   v            |
                             JVM cleanup        |
                             (LIFO, EDT)        |
                                   |            |
                                   v            |
                             System.exit(0) ----+
                                                v
                                      Registry-owned JVM
                                      shutdown hook runs
                                      JVM tasks LIFO on
                                      hook thread. The
                                      CleanupTask wrapper
                                      guarantees at-most-
                                      once across paths.
```

---

## Object lifecycle

Most objects in this application live as long as the process. The main window,
the menu controller, the status bar and every action constant are created at
startup and released by process exit; nothing tears them down, and nothing
should.

The exceptions are objects retired while the process continues, and they are
the ones that need disposal. An object that registers itself with something
process-global — today, the message bus — stays registered after the last
reference to it is dropped, because the registry holds it weakly and the
collector runs when it runs. Until then it keeps handling messages on behalf of
something nobody is using.

Such a class implements `songscribe.lifecycle.Disposable` and its class Javadoc
names, under a `Lifecycle` heading, who calls `dispose()`.

One live case is the document model. Every document load replaces the `Song`
installed in the `ScoreView`, and `ScoreView.setSong` disposes the outgoing one.
A `Song` left subscribed keeps handling broadcast commands and posting undo
steps against a document nobody has open.

The other is dialogs. A `BaseDialog` is built for one opening and retired when
it closes, so `setVisible(false)` disposes it: first every registered `Tab`,
then the dialog's own `Bindings`. Tabs go first, so a tab's `dispose()` runs
while its bound controls are still whole. A tab disposes the `UIAction`s its
font rows built and any it built for a button of its own; each of those
subscribed itself to the message bus in its constructor, and disposing them is
what keeps a closed dialog's actions from handling messages for the rest of the
run. Disposing the `Bindings` cancels every observation the dialog declared —
its bindings, its effects, and the observations each `computed` holds on its own
dependencies — which is what releases the dialog, its controls and everything
its transforms, derivations and effects captured. A `computed` is created
through `Bindings` rather than as a free-standing value precisely so that last
part has an owner: a derivation reading anything that outlives the dialog would
otherwise keep the dialog reachable for the rest of the session.

A further case is coming rather than present: a `ScoreView` built for one
conversion, with its controller, its `SelectionCoordinator` and that
coordinator's `ActionReflector`, is finished with when the conversion is. The
`MessageBusScope` a headless conversion runs inside does not settle it: closing
a scope unsubscribes, and unsubscribing on the way out of a process buys
nothing. The converters are being redesigned; whatever replaces them owes the
disposal, and `ScoreView` acquires `dispose()` then — not before, because the
rewrite decides whether a converter builds a view at all.

Four things end a set of registrations, and none substitutes for another:

| | Ends | Reversed by |
|---|---|---|
| `Shutdown.now()` | the process | nothing |
| `Foo.deinitialize()` | a static subsystem's current initialization | `Foo.initialize(...)` |
| `foo.dispose()` | one instance, permanently | nothing |
| `scope.close()` | every subscription made on that bus | opening another scope |

There is no point unsubscribing on the way out of the process, and no point
running the quit sequence to discard a view. `deinitialize()` is the odd one:
it is the only teardown you can undo, which is why it is named for the thing
that undoes it.

**Closing a `MessageBusScope` is not disposal.** It covers the unsubscribe half
and nothing else: `dispose()` also cancels the `Bindings` observations an object
declared and releases what its transforms and effects captured, and discarding a
bus does neither. Most subscribers are on the application bus in any case, where
no scope ever closes. See [messages.md](messages.md).
