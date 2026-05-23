## 13. e2e reconciliation (audited 2026-05-22)

The final, read-only audit session. Unlike §1–§12 (which enumerated behaviors from
production code), §13 reconciles the **existing e2e test methods** against the matrix:
for each of the **51** e2e `@Test` methods (the baseline's "79" was a miscount — see the
corrected inventory above), decide whether the test is genuine integration risk
(**keep**), asserts logic that belongs at unit level (**wrong-level**), duplicates
already-adequate coverage (**redundant**), or maps to no live behavior (**orphan**). Four
parallel sub-audits (13A `ElementInsertionTest`; 13B `SelectionTest`; 13C
`NoteConnectionTest` + `DynamicsMarkingTest`; 13D `DialogsTest` + `ShutdownTest`), each run
in reconcile + spot-verify mode. The verdict applies the same rule used throughout: a
behavior is e2e **only** when the risk *is* the Swing integration; if the asserted state
is reachable with the singleton mocked, it is wrong-level. Tallies below are recomputed
directly from the verdict columns (sub-agent prose tallies were unreliable, as in
Sessions 11/12).

- [13A. `ElementInsertionTest` (17 tests)](13a-elementinsertiontest.md)
- [13B. `SelectionTest` (15 tests)](13b-selectiontest.md)
- [13C. `NoteConnectionTest` (8) + `DynamicsMarkingTest` (3)](13c-noteconnectiontest.md)
- [13D. `DialogsTest` (5) + `ShutdownTest` (3)](13d-dialogstest.md)

### 13 — summary

**51 e2e `@Test` methods reconciled: 38 keep · 8 wrong-level · 4 redundant · 1
orphan/over-broad.** (Recomputed from the verdict columns; authoritative. The pre-audit
baseline of "79" was an artifact of a loose `@Test` grep that also counted
`@TestClassOrder` / `@TestInstance` / `@TestMethodOrder` — corrected to 51 in the
inventory.)

Shape of the e2e suite: **the large majority (38/51) is genuine integration risk that
must stay e2e** — real mouse/keyboard dispatch, drag gestures, dialog round-trips, and
quit/window-close entry-point wiring that the unit suite structurally bypasses. The
reconciliation surfaced four removal/relocation themes for the remediation phase:

- **Drop as redundant (4):** `DialogsTest` `testCloseWithYesNoOptionReturnsNoOption`,
  `testCloseWithYesNoCancelOptionReturnsCancelOption`, `InputDialog.testReturnsTypedText`
  (all already covered by unit `songscribe/ui/DialogsTest.WhenNotSuppressed`'s
  CLOSED_OPTION / input-return tests); `ElementInsertionTest.testSameTypeClickKeepsDecorations`
  (articulation carry-over already adequate in `PreviewElementManagerAttachmentTest`).
- **Relocate to unit — wrong-level (8):** `DialogsTest.testClickYesReturnsYesOption`
  (YES_OPTION is a pure framework return, no production mapping); the `NoteConnectionTest`
  `GlissandoSelection` trio (`selectGlissando`/`isElementSelected` are `LineSelectionState`
  logic — §7C lines 1993/1997 — and two carry vacuous unmodified-fixture assertions); and
  the `ElementInsertionTest` `PreviewElementManager.modifyExistingElement` group
  (`testReplaceHostWithPitchedNotePreservesGlissando`, `testVerifyElementTypesAndAutoBeam`,
  `testInsertBetweenAndVerifyShift`, `testClickOnNoteReplacesIt`) — all assert pure model
  state and collapse into a parametrized `PreviewElementManager` unit test.
- **Orphan (1):** `ElementInsertionTest.InsertElementTypes.testBuildSong` is a
  no-assertion `@Test` (setup-only) — convert to `@BeforeAll` fixture or fold into its
  verify sibling. Plus a class-level orphan (no per-method verdict):
  `DynamicsMarkingTest`'s stale "Covers serialization (E6)" javadoc + no-op
  `@Order(5)`/`@TestClassOrder` + never-called `roundTrip` helper.
- **Keep but fix in place:** `SelectionTest.testDragSelect` keeps its e2e level but its
  `isGreaterThanOrEqualTo(3)` assertion must become `isEqualTo(3)` (independently flagged
  in §6A line 1979 and §7C line 2368).

**Provisional keeps (sole coverage of a matrix-`missing` unit path).** Seven keeps exist
only because the corresponding unit test is recorded as missing; once remediation writes
those unit tests, re-evaluate for downgrade to redundant: `ElementInsertionTest`
`testReplaceHostWithRestRemovesGrace` + `testClickWithRestSelectedReplacesWithRest`
(`makePreviewElement` rest-conversion / `modifyExistingElement` rest path, §6C/§7C);
`NoteConnectionTest` `testInsertConnectedGlissando`, `testInsertSlideOut`,
`testDeleteSelectedGlissando`, `testDeleteSourceNoteRemovesGlissando`,
`testDeleteTargetNoteRemovesGlissando` (`handleClick` glissando / `handleDelete`
element + glissando routing, §7A/§7C). These are correctly **keep** for now per the audit
rule (missing unit ⇒ the e2e is the only coverage), but they are integration-level proxies
for unit-level logic.

**No production observations.** §13 audits test code, not production; its findings are the
remediation actions above (no new GitHub issue filed). Carry-forward still open: §12A's
`CloseWindowAction` quit entry-point has no e2e (distinct from the covered `QuitAction`),
and the Sessions-5/7 data-loss guard (`MainFrame.showSaveDialog()` + Save/Don't-Save
branches) remains dark — both belong to the remediation phase.

**Audit complete.** All production packages (§1–§12) plus the e2e suite (§13) are now
audited. Next: the remediation phase (rewrite + PIT verification), and promotion of the
rubric (matrix.md lines 29–91) into `.agents/guides/testing-common.md`, after which this
scaffolding (`matrix.md`, `handoff.md`) is archived/deleted.
