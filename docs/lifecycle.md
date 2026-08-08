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
          └─► Actions.initialize(this)  — populates all Actions.* constants
                └─► first constant use  — MenuController.init(this)
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
  UIConverter.windowClose -+      v             |
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
