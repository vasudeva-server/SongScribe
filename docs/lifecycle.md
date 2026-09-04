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

The main window, the menus, the status bar, the undo controller and every action
constant are created at startup and released by process exit. Nothing tears them
down, and nothing should. Such an object registers with the bus for the process
and holds nothing back.

The exceptions are objects retired **while the process continues**, and they are
the ones that need disposal. An object registered with something process-global —
today, the message bus — stays registered after the last reference to it is
dropped, because the registry holds it weakly and the collector runs when it runs.
Until then it keeps handling messages on behalf of something nobody is using. The
registration has an owner all the same, and disposing the owner is what ends it,
independent of when the collector runs. See [messages.md](messages.md).

Two live cases:

- **The document.** Every load replaces the installed song, and the outgoing one
  is disposed. Left subscribed, it keeps handling broadcast commands and recording
  undo steps against a document nobody has open.
- **Dialogs.** A dialog is built for one opening and retired when it closes, so
  hiding it disposes it, which disposes the dialog's own bindings. A tab acquires
  nothing of its own. Disposing the bindings cancels every
  observation the dialog declared, including those each derived value holds on its
  own dependencies, which is what releases the dialog and everything its
  transforms and effects captured. A derived value is created through the dialog's
  bindings rather than standing free precisely so that last part has an owner:
  one reading anything that outlives the dialog would otherwise keep the dialog
  reachable for the rest of the session.
- **Long-lived views.** A set of bindings belongs to whatever owns it, and a
  dialog is only the shortest-lived owner. A view that lives as long as the
  window owns one the same way, and disposes it when the view is disposed rather
  than on any close. The ownership rule is what matters, not the kind of owner:
  every observation has something whose disposal ends it.

## Two things end a set of registrations

Neither substitutes for the other:

| | Ends | Reversed by |
|---|---|---|
| the quit sequence | the process | nothing |
| disposing an instance | one object, permanently | nothing |

There is no point unsubscribing on the way out of the process, and no point
running the quit sequence to discard a view. No subsystem is torn down while the
process continues: a static subsystem is initialized once and its registrations
end with the process.
