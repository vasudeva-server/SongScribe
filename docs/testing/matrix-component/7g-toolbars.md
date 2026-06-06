### 7G. Toolbars (toolbar/ + MainToolbarPanel)

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| `Toolbar` | Constructor wires `JToolBar` with floatable=false, rollover=true, zero margin — pure Swing config, no branching logic | none | — | adequate | No test warranted | — |
| `Toolbar` | `BUTTON_DIMENSION` constant (36×36) available to subclasses | none | — | adequate | Pure data constant, framework behavior | — |
| `MainToolbarPanel` | Constructor assembles tool sections (West) + strut (Center) + playback (East) — pure layout wiring, no branching | none | — | adequate | No test warranted | — |
| `MainToolbarPanel` | `updateUI()` sets background from `UIManager` key `"ToolBar.background"` — theme-aware repaint | none | — | adequate | Framework delegation, no app logic to assert | — |
| `MainToolbarPanel` | `createToolbarPanel(int borders)` computes left/right border widths from two bitmask flags (`LEFT_BORDER=1`, `RIGHT_BORDER=2`) — four combinations | unit | — | missing | Unit test: assert that each of the four flag combinations (`NO_BORDER`, `LEFT_BORDER`, `RIGHT_BORDER`, `LEFT_BORDER\|RIGHT_BORDER`) produces the correct inset widths on the returned panel's `MatteBorder` | ✅ |
| `AccidentalToolbar` | Constructor adds four `ToolbarToggleButton` wrappers for accidental actions — pure button-assembly wiring | none | — | adequate | No test warranted | — |
| `ArticulationToolbar` | Constructor adds two `ToolbarToggleButton` wrappers for articulation actions — pure wiring | none | — | adequate | No test warranted | — |
| `BarToolbar` | Constructor iterates `REPEAT_ACTIONS` then `BARLINE_ACTIONS`, adding a `StickyToggleButton` per action — pure wiring over fixed arrays | none | — | adequate | No test warranted | — |
| `DotRestToolbar` | Constructor adds two `ToolbarToggleButton` wrappers for dot/rest actions — pure wiring | none | — | adequate | No test warranted | — |
| `DurationToolbar` | Constructor scans `DURATION_ACTION_GROUP` actions and, when the quarter-note action is found, selects it as the default, then calls `perform()` — real initialization logic with a conditional | unit | `ActionsResetOnDocumentLoadTest` covers the post-load reset to quarter note but not the initial construction-time selection path; `LyricEditorActionAuditTest` only audits the `DISABLE_WHEN_EDITING_TEXT` flag | missing | Unit test: construct a `DurationToolbar` with a mocked `MainFrame` and assert that `Actions.DURATION_ACTION_GROUP.getSelected()` is `Actions.QUARTER_NOTE_ACTION` immediately after construction | ✅ |
| `DurationToolbar` | The `if (defaultButton != null)` guard protects the selection call — logically unreachable because `QUARTER_NOTE_ACTION` is always in the group, making it dead-code | none | — | adequate | Dead-code guard; no test value; could be removed as a separate cleanup | — |
| `ModifyNoteToolbar` | Constructor adds `ToolbarButton` and `TupletPopupButton` wrappers — pure wiring | none | — | adequate | No test warranted | — |
| `PlaybackToolbar` | Constructor adds two `ToolbarButton` and two `ToolbarToggleButton` wrappers for playback actions — pure wiring | none | — | adequate | No test warranted | — |

**7G notes (quality concerns):**

The subclasses that are pure button-assembly wiring (`AccidentalToolbar`, `ArticulationToolbar`, `BarToolbar`, `DotRestToolbar`, `ModifyNoteToolbar`, `PlaybackToolbar`) contain no branching logic and are correctly left untested. The two genuine logic gaps are `DurationToolbar`'s construction-time quarter-note selection (distinct from the post-load reset already covered by `ActionsResetOnDocumentLoadTest` — that test fires `DocumentDidLoadNotification`, not the constructor path) and `MainToolbarPanel.createToolbarPanel`'s bitmask-to-border-width conversion (four reachable flag combinations, no test). Everything else in the toolbar package is pure button-assembly wiring. **Scope note:** the original sub-audit also surfaced `DurationActionGroup.barWasSelected()` and several `ActionGroup` base-class branches (`setSelected(action,false)`, `selectNext()` wrap-around, `select()` idempotency, `getPreviousSelected()`); those classes live in `songscribe.ui.action` and were already audited in **§5A** (Session 5), so their rows are not repeated here to avoid double-counting.

