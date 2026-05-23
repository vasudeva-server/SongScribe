## 7. `ui/component` (audited 2026-05-22)

Audited via nine production-first sub-audits run in three waves of three: **7A** control plane (`ScoreViewController` + input dispatch); **7B** `ScoreView`; **7C** hit-test / drag / selection / preview routing; **7D** `MainFrame` (window singleton, save & data-loss guard); **7E** line/score rendering geometry; **7F** score panels & text components; **7G** toolbars; **7H** input & text widgets; **7I** buttons/borders/frames & navigation helpers. Scope was 62 production classes (+3 `package-info`). Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.

**Two scope corrections during assembly:** (1) `ScoreComponent` was audited by both 7E and 7F — its rows are kept under **7E** only (more complete; includes `setMargin`) and removed from 7F. (2) The 7G sub-audit over-reached into `songscribe.ui.action` collaborators referenced by toolbar constructors — `DurationActionGroup.barWasSelected()` and the `ActionGroup` base-class branches (`setSelected(action,false)`, `selectNext()` wrap-around, `select()` idempotency, `getPreviousSelected()`) plus `LyricEditorActionAuditTest` — those classes belong to `ui/action` and were already audited in **§5A**; their rows were trimmed here to avoid double-counting (carry-forward already recorded in §5).

- [7A. Score-view control plane (ScoreViewController + input dispatch)](7a-score-view-control-plane.md)
- [7B. ScoreView](7b-scoreview.md)
- [7C. Hit-test, drag, selection & preview routing](7c-hit-test-drag-selection-preview-routing.md)
- [7D. MainFrame (window singleton, save & data-loss guard)](7d-mainframe.md)
- [7E. Line/score rendering geometry (LineComponent, LineRenderer, ScoreComponent)](7e-line-score-rendering-geometry.md)
- [7F. Score panels & text components](7f-score-panels-text-components.md)
- [7G. Toolbars (toolbar/ + MainToolbarPanel)](7g-toolbars.md)
- [7H. Input & text widgets (LyricEditor, text fields, caret, focus)](7h-input-text-widgets.md)
- [7I. Buttons, borders, frames & navigation helpers](7i-buttons-borders-frames-navigation-helpers.md)

### 7 summary

**387 behavior rows** across 62 production classes: by required level **319 unit / 15 e2e / 53 none**; of the 334 testable rows, **231 missing · 88 adequate · 8 wrong-level · 7 inadequate**. The package is ~69% dark — a few dense, genuinely-adequate clusters embedded in a large untested mass.

**Defining shape: the riskiest user-facing guarantees in the app are dark, while the tested clusters are narrow.** Confirmed highest-risk gaps, in order:

1. **Data-loss guard untested (7D).** `MainFrame.showSaveDialog()` and the entire `save()`/`saveCurrentFile()`/`saveAsNewFile()` chain — the sole guard against silently discarding the user's work, plus `IOException` handling, the `isModified` clear, and the `RecentDocumentsManager` side-effect — have **zero direct tests** at any level. `ShutdownTest` (e2e) only confirms shutdown wiring and forces the CLOSED_OPTION answer, so the "Don't Save" and "Save→propagate result" branches are entirely unexercised; those branches are `wrong-level` (unit-testable with `OptionDialogs`/`SaveAction` mocked).
2. **Paste is a confirmed silent no-op (7A).** `ScoreViewController.handlePaste()` is a body-only TODO, so every `PasteboardOpCommand(PASTE)` is swallowed — the root cause of the Session-5/6 `PasteAction` no-op. This is a production defect, not just a test gap.
3. **px↔staff-space coordinate chain dark (7E).** `LineComponent.staffPositionToYPx` / `getMiddleLineYPx` / `calculateMiddleLineYSs` — the complete layout-result→screen-Y path, exactly the issue-#411 territory — have no unit tests; e2e uses them only as opaque coordinate helpers.
4. **Hit-test & selection geometry dark (7C).** `ElementHitTest` (symmetric-expansion rect math) and `LineSelectionHandler` (5-branch `hitTest` cascade + staff-radius compare) have no unit tests; `SelectionTest.testDragSelect` uses the recurring weak `isGreaterThanOrEqualTo(3)` assertion.
5. **Score-panel layout invariants dark (7F).** `TextPanel.calculateUnionWidth`/`paintComponent` centering, `StaffPanel.getLayoutResults` cross-line lyric-continuation threading, `MainPanel`'s conditional `scoreMarginTop` gap, and `ScorePanel.getPreferredScrollableViewportSize` (documented viewport-feedback-loop guard) — all untested computed geometry.
6. **Widget pure-logic dark (7H/7I).** `TextFocusDelegate`'s `ignoreTabKey` first-Tab guard; `NonEmptyGuard`'s two validation modes + option-dialog index arithmetic; `InputUtils` document filters (also surfaced via `NumericTextField`); `MyBorder` constructors + `withOverrides` -1 sentinel; `TickSlider` snap-and-suppress; `ComponentHierarchyNavigator` (7 behaviors incl. null-panel fallbacks + multi-level Y-sum); `DurationListCellRenderer.noteGlyphFor` glyph-mapping switch.

**Genuinely-adequate clusters (the bright spots):** `LyricEditor` (7H) is the standout — three test classes deliver dense, exact-value coverage of commit semantics, the full navigation-key matrix, and all five boundary-character state machines; gaps are narrow. `NoteDragHandler` and the `PreviewElementManager*` family (7C), `ScoreView.setFonts` (7B), and `ScoreViewController`'s command-handlers + `deleteNote` (7A) are also adequate.

**Cross-cutting `wrong-level` (8):** mouse-event routing in `LineComponent` (`mouseClicked`/`mousePressed` branches driven by mockable state) and the `showSaveDialog` answer branches — covered only via e2e but unit-testable with collaborators mocked. Reinforces the Session-5/6 themes (thin-dispatcher dispatch untested; real logic stranded in e2e happy-paths).

**`inadequate` (7):** `ScoreView.installDocumentFonts` (fixture-only, contract never asserted); `ScoreViewController` `DeleteLyric` (raw `java.lang.reflect` instead of package-private; one of five branches) and `handleRemoveDynamics` (`isNotEmpty()` only); `LyricEditor` hyphen-break (weak `>=2`, target element unasserted) and `commitAndDismiss` parent=null guard; `SelectionTest.testDragSelect` (weak `>=3`); the `MainFrame` window-close-cancel row (only CLOSED_OPTION exercised).

**Dead code:** no dead *classes* found this session (contrast Sessions 3–5). One dead *branch*: `DurationToolbar`'s `if (defaultButton != null)` guard is logically unreachable (`QUARTER_NOTE_ACTION` is always in the group) — candidate cleanup, not unreachable-class removal.

**Production observations filed as a tracked GitHub issue (#412; do not fix during audit):** (a) `ScoreViewController.handlePaste()` TODO-only stub → paste is completely non-functional; (b) `DurationToolbar` dead-code guard; (c) `LineComponent` coordinate chain is adjacent to the already-filed #411 px↔Ss class of bug (read-only assessment; severity needs a repro during remediation).
