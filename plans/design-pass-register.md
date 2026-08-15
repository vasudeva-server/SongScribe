# Design pass register

**Type:** Register · long-lived  <br>
**Created:** 2026-08-14  <br>
**Status:** In progress

Which modules have been through the design regime in `~/.claude/guides/design.md`,
in what order the rest are taken, and what a finished pass found that a later
module owns.

## What this file is not

**It does not name findings in advance.** A design pass is not plannable in
detail, because each decision determines whether the next item exists at all —
dissolving `KeyChange` into `Key` deleted a review target that a prescriptive
plan had listed. The predecessor of this file tried to name findings per phase;
ten of its specifics were wrong, stale or superseded within one pass, while the
only part that held up was the ordering, because ordering is a dependency fact
rather than a prediction.

It carries no file lists and no task lists. `/design-pass <target>` owns the
procedure; the record it keeps at `plans/design-pass/<target>.md` owns the
detail of one module.

## The order, and why

**Each module is reviewed after whatever it takes its types from, and before
whatever consumes it.** A pass changes the types its consumers use, so a consumer
reviewed first is reviewed against types that are about to move.

Two consequences worth stating once:

- **Test rebuild is per module, as that module settles.** The unit suite was
  removed wholesale (tag `pre-test-reset`, archive worktree
  `../SongScribe-tests-archive`); tests come back only where the design cannot
  carry the promise, proposed before they are written.
- **Docs are corrected inside the pass that invalidates them**, never in a
  trailing phase. A doc left wrong while the code moves is how it got wrong.

## Register

| # | Module | Packages | Status | Notes |
|---|---|---|---|---|
| 0 | Key signatures *(feature-shaped)* | cuts `dom`, `layout`, `io/musicxml`, `ui` | 🔄 | Branch `776-key-signature`. Feature-shaped because the branch was; every pass after it is module-shaped. |
| 1 | Domain value types | `dom` (values) | 🔄 | `Key`, `KeyType`, `ElementType`, `ElementLocation`, `Ss`/`DocPx`/`ViewPx`, `ScaleContext`, `Duration`. Being done inside pass 0. |
| 2 | Glyph registry | `smufl` | ⏳ | Leaf; no dependencies. |
| 3 | Staff geometry | `engraving` | ⏳ | Depends on `smufl`. |
| 4 | Document model | `dom` (model) | ⏳ | `Song`, `Line`, elements, spans, ties. |
| 5 | Mutations | `message/mutation` | ⏳ | Shapes follow the model. |
| 6 | Message bus | `message`, `message/notification`, `message/command` | ⏳ | |
| 7 | Undo | `undo`, `lifecycle` | ⏳ | Replays mutations. |
| 8 | MusicXML I/O | `io/musicxml` | ⏳ | The live boundary — converts into domain types. |
| 9 | Legacy I/O | `io` | ⏳ | Read-only migration path. |
| 10 | Layout | `layout`, `layout/stacking`, `hit` | ⏳ | Consumes model + engraving. |
| 11 | Rendering | `ui/renderer` | ⏳ | Consumes layout. |
| 12 | Selection | `ui/selection` | ⏳ | |
| 13 | Clipboard | `ui/clipboard` | ⏳ | |
| 14 | Edit modes | `ui/edit` | ⏳ | |
| 15 | Dialogs | `ui/dialog`, `ui/dialog/backend`, `ui/dialog/fontchooser` | ⛔ | Blocked — see *Blockers*. |
| 16 | Actions | `ui/action` | ⏳ | Wires everything above. |
| 17 | Score components | `ui/component`, `ui/component/score`, `ui/component/toolbar` | ⏳ | |
| 18 | UI shell | `ui`, `ui/menu`, `ui/platform`, `ui/playback` | ⏳ | |
| 19 | MIDI | `midi` | ⏳ | |
| 20 | Export | `export` | ⏳ | |
| 21 | Converter | `converter`, `uiconverter` | ⏳ | Headless; a producer that reaches the model without the UI. |
| 22 | Leaf utilities | `util`, `error`, `shape`, `font`, `prefs` | ⏳ | Swept as encountered; a pass of their own only if one earns it. |

Legend: ⏳ not started · 🔄 in progress · ✅ complete · ⛔ blocked

## Carry-forward findings

Facts a completed or in-flight pass established that a later module owns. Nothing
speculative belongs here.

- **→ Module 4 (document model).** `Line.getRunningKey()` calls
  `RuntimeError.exit()` at `Line.java:346` — a model getter that terminates the
  application. Agreed replacement: `Line.ownKey` as the only key state, `Song`
  owning resolution in an `IdentityHashMap<Line, Key>` rebuilt wholesale,
  `Song.runningKeyAt(line)` as the total query, `Line.keyAt` bottoming out there,
  and `detach()` materialising the running key so a detached line answers for
  itself. Deletes `inheritedKey`, both its accessors,
  `propagateInheritedKeysFrom`, `rebuildInheritedKeysAfterParsing` and the exit.
- **→ Modules 1 and 4.** `dom` conflates value types and the document model in
  one package, which is why it appears twice in this register.
- **→ Module 1.** A position in a line is a `(Line, int)` pair with nothing
  binding them — `Line.keyAt`, `Line.getElement`,
  `KeyChangeElement.needsBarlineBefore`, `InsertionPointMode.Client`,
  `LayoutResult.findInsertionIndex`, `KeyChangeDialog.KeyChangeInput`.
  `ElementLocation` already exists as `(int lineIndex, int elementIndex)`, bound
  to no `Song`, with a `matches(int, int)` whose two arguments transpose
  silently. Whether the position type extends it or displaces it is undecided.
- **→ Modules 10 and 11.** `LayoutHitTester.hitTestCautionaryKeyEdit` and
  `KeySignatureRenderer.renderKeyChange` each build the accidental run twice —
  once for the list, once inside `Key.widthSsFrom`. Negligible cost, real
  duplication, introduced when `totalWidthSs(List)` was removed.
- **→ Module 22.** `strings.properties` has `dialog.song.settings.year` above
  `dialog.converter.converting`, breaking the within-group alphabetical order the
  strings guide requires.
- **→ Test rebuild.** `ui/dialog/SongSettingsDialogFixture` survived the reset as
  infrastructure but all three of its consumers are gone. Kept, not justified.

## Blockers

**Module 15 (dialogs) blocks on `dialog-redesign` landing on `develop`.**
`BaseDialog` lost `getScoreView()` and `requireScoreView()` there before five
dialogs were migrated off them, and another session is deleting three legacy
export dialogs with their actions and menu entries and migrating
`PreferencesDialog`. Reviewing dialogs here first means doing it twice, against a
`BaseDialog` that is about to change.

Order: `dialog-redesign` lands on `develop`, this branch rebases onto it
(`/update-branch`), then module 15 runs. **Expect one conflict, in `CLAUDE.md`:**
keep this branch's *Key signatures* required-reading row and take the `.claude/`
paths, which both sides now agree on. `d67f064a` will not drop out silently
despite already being on `develop`, because develop's copy of that row was
removed when it was cherry-picked.
