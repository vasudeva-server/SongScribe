### 8D. `command` — `*Command` messages

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `CloseWindowCommand`, `DeselectCommand`, `FirstSecondEndingCommand`, `FlipStemDirectionCommand`, `NewFileCommand`, `PrintCommand`, `RemoveDynamicsCommand`, `SaveAsCommand`, `SaveCommand`, `SelectLineCommand`, `ShowOpenDialogCommand`, `ToggleBeamCommand`, `ToggleTieCommand`, `ToggleTrillCommand`, `UpdatePreviewElementCommand` | payloadless marker — no logic | none | — | adequate | no action |
| `AddDynamicsCommand` | single boolean getter `isCrescendo()` — pure data, no logic | none | `ScoreViewControllerCommandHandlerTest` (dispatch vehicle, no accessor assertion) | adequate | no action |
| `InsertLineCommand` | single int getter `getShift()` — pure data, no logic | none | — | adequate | no action |
| `OpenFileCommand` | single `File` getter — pure data, no defensive copy | none | — | adequate | no action (`File` is not safely immutable but no copy is idiomatic Java convention; note only) |
| `PasteboardOpCommand` | single enum getter `getOperation()` (`COPY/CUT/DELETE/PASTE`) — pure data, no logic | none | `DeleteLyricTest`, `EndingConfirmsTest` (dispatch only, `DELETE` op used; other ops never tested as dispatch) | adequate | no action on command class itself |
| `ToggleLoopPlaybackCommand` | implements `SelectableMessage.isSelected()` — trivial getter on stored boolean | none | — | adequate | no action |
| `TogglePlayWithRepeatsCommand` | implements `SelectableMessage.isSelected()` — trivial getter on stored boolean | none | — | adequate | no action |
| `ToggleTupletCommand` | `getTupletSize()` delegates to `action.getTuplet().getSize()` — computed/derived accessor; `toString()` override; payload is a full `TupletAction` object | unit | `ScoreViewControllerCommandHandlerTest.tupletCommand` constructs via mocked `TupletAction`, but asserts on handler mutations not on `getTupletSize()` directly | missing | add unit test: verify `getTupletSize()` returns `action.getTuplet().getSize()` for each `Tuplet` enum value |

**Notes.** The vast majority of the 22 commands are payloadless markers or trivial single-field carriers; none require tests of their own. The single exception is `ToggleTupletCommand`, which exposes a derived accessor `getTupletSize()` that delegates through two method calls on the injected `TupletAction`; that delegation chain is untested at the unit level (the handler test mocks the action but never asserts on the accessor). `PasteboardOpCommand` carries an enum payload but adds no logic, so it remains `none`. **Production observation:** `CloseWindowCommand.java` is syntactically invalid — the file contains only a package declaration and an unused `import songscribe.message.Message;` with no class body; however, the class has zero usages in production code, so it does not currently break compilation.

