# Application and Object Lifecycle

## Startup is gated, not sequential

Three concerns compete at startup: the window should appear quickly, it must not
appear before the fonts it draws with are installed, and MIDI initialization is
slow and may fail.

They are reconciled by showing a splash immediately under a **minimal** theme —
one font face, enough to draw the splash in the right typeface rather than a
fallback — then doing the expensive work behind it: opening MIDI on a background
thread, installing the remaining font faces, and building the main window without
showing it.

A **gate** then holds the reveal until both a minimum splash duration has elapsed
and MIDI has either finished or run out of its allowance. The floor stops the
splash flashing past on a fast machine; the cap stops a broken MIDI device
holding the application hostage.

Errors raised during that window are collected rather than thrown. At the reveal,
a fatal one exits with its dialog shown over the splash — the window is never
revealed and the splash never hidden, so the user does not see a half-built
application. Non-fatal ones are shown as warnings after the window is up.

Whatever the user asked for — a file on the command line, the autoloaded
document, or the open dialog — is decided while the window is being built and run
last, once there is a window to run it in.

**One ordering constraint is worth knowing:** the action constants must exist
before anything reads them, which is what fixes the order in which the main
window wires itself up.

## Shutdown is one vetoable sequence

Every quit path the user can invoke funnels into a single ordered sequence:
confirm, then clean up. Confirmation runs in registration order and any
participant may veto, which is what makes "save your changes?" work regardless of
*how* the user asked to quit — the window's close button, the menu command, the
platform quit, or closing the last window.

Cleanup then runs in reverse registration order, so a component is torn down
before whatever it depends on.

A separate set of tasks is owned by a process-level shutdown hook, so
thread-safe cleanup still happens on paths that never reach the sequence at all —
a fatal error, a termination signal, or the last non-daemon thread ending. Each
task is wrapped so it runs **at most once** across both routes.

## Most objects are never torn down

The main window, the menus, the status bar and every action constant are created
at startup and released by process exit. Nothing tears them down, and nothing
should.

The exceptions are objects retired **while the process continues**, and they are
the ones that need disposal. An object registered with something process-global —
today, the message bus — stays registered after the last reference to it is
dropped, because the registry holds it weakly and the collector runs when it runs.
Until then it keeps handling messages on behalf of something nobody is using. See
[messages.md](messages.md).

Two live cases:

- **The document.** Every load replaces the installed song, and the outgoing one
  is disposed. Left subscribed, it keeps handling broadcast commands and recording
  undo steps against a document nobody has open.
- **Dialogs.** A dialog is built for one opening and retired when it closes, so
  hiding it disposes it: first every registered tab, then the dialog's own
  bindings. Tabs go first, so a tab's disposal runs while its bound controls are
  still whole. A tab disposes the actions its rows built, each of which subscribed
  itself in its constructor — that is what keeps a closed dialog's actions from
  handling messages for the rest of the run. Disposing the bindings cancels every
  observation the dialog declared, including those each derived value holds on its
  own dependencies, which is what releases the dialog and everything its
  transforms and effects captured. A derived value is created through the dialog's
  bindings rather than standing free precisely so that last part has an owner:
  one reading anything that outlives the dialog would otherwise keep the dialog
  reachable for the rest of the session.

## Four things end a set of registrations

None substitutes for another:

| | Ends | Reversed by |
|---|---|---|
| the quit sequence | the process | nothing |
| a static subsystem's teardown | that subsystem's current initialization | initializing it again |
| disposing an instance | one object, permanently | nothing |
| closing a bus scope | every subscription made on that bus | opening another scope |

There is no point unsubscribing on the way out of the process, and no point
running the quit sequence to discard a view. A subsystem's teardown is the odd one
out: it is the only reversible teardown, which is why it is named for the thing
that reverses it.

**Closing a bus scope is not disposal.** It covers the unsubscribing and nothing
else — disposal also cancels the observations an object declared and releases what
its transforms and effects captured, and discarding a bus does neither. Most
subscribers are on the application bus in any case, where no scope ever closes.
