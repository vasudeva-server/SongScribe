## 1. `dom` (audited 2026-05-21)

Audited via six parallel production-first sub-audits: **Song**; **Line**; **element/note core**; **element-type & pitch system**; **attachment family**; **ornaments/dynamics/misc**. Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.

- [1A. `Song`](1a-song.md)
- [1B. `Line`](1b-line.md)
- [1C. element/note core — `StaffElement`, `LineElement`, `NoteBounds`, `AccidentalBounds`, `Beam`, `Tie`](1c-element-note-core.md)
- [1D. element typing & pitch system — `ElementType`, `RangeElement`, `KeySignature`, `ScaleContext`, `StructuralElement`, `Clef`, `Duration`, `KeyType`, `ElementLocation`](1d-element-typing-pitch-system.md)
- [1E. attachment family — `Attachment`, `Annotation`, `AnnotationAttachment`, `DynamicAttachment`, `FermataAttachment`, `MetronomeAttachment`, `BeatChangeAttachment`, `TempoChangeAttachment`, `BeatChange`, `Tempo`](1e-attachment-family.md)
- [1F. ornaments / dynamics / misc — `Articulation`, `ArticulationType`, `Hairpin`, `Trill`, `Tuplet`, `Crescendo`, `Diminuendo`, `Lyric`, `Attribution`, `EndingValidationResult`, `CollisionRegion`](1f-ornaments-dynamics-misc.md)

### dom — production observations (out of test-audit scope)

- **`FermataAttachment(@Nullable StaffElement)`** calls `setOwnerElement(parent)` twice (lines 59 and 63): once unconditionally, then again inside the `if (parent != null)` block. The second call is a redundant no-op (idempotent for the same owner) — harmless, not behavior-affecting, so no regression test is warranted. **RESOLVED 2026-05-22:** redundant call removed.

### dom — summary

Audited all 38 production classes (excl. `package-info`). Dominant patterns to drive remediation:

1. **Pure conversion/geometry math is the biggest blind spot.** `ScaleContext` (ssToPx/pxToSs/rounding) and the `getSpanWidthSs`/`get*Px` clamp-and-convert methods across `Beam`/`Tie`/`Hairpin`/`Trill`/`Tuplet`/`NoteBounds` are exercised only as collaborators in higher-level tests — never asserted directly. These are cheap, high-value unit tests.
2. **"Weak-but-green" tests give false confidence:** relative-only pitch assertions (`getPitch`), `>0`/`>=` assertions where exact values matter (`ElementType` width/height, `Fermata`/`Trill` dimensions), self-referential oracles (`terminalFlushRightXSs` in `LayoutEngineTest`), and tautologies (`isNotNull()` on non-null enum fields; `isCloseTo` calling the production method). These are PIT-detectable (see methodology note below).
3. **Untested branch/error paths:** `Song` `@Handler` methods + `getTempoAt` + REPEAT_RIGHT terminal carry-over; `Line` beam/tie/hairpin **merge** logic + grace-pair predicates; `BeatChange.fromLegacyName` happy paths; numerous IAE/ISE guards.
4. **`copy()` contracts** verify presence but not new-object identity or owner re-wiring across the attachment family.
5. **Misfiled-but-adequate:** `getLedgerLineCount` (tested in `ui/renderer`), several `dom` classes tested under `layout/` (`RangeElementInvalidationTest`, `KeySignatureTest`) — relocate during rewrite, not re-test.
