## 4. `midi` + `converter` + `util` + `smufl` + `prefs` + `font` + `export` + `uiconverter` (audited 2026-05-21)

Audited in two waves of parallel production-first sub-audits (Wave 1: midi, converter, smufl; Wave 2: util, prefs, font, export, uiconverter). Read-only; e2e assessed from source only; coverage checked across unit (mirrored + cross-package) and e2e.

- [4A. `midi`](4a-midi.md)
- [4C. `util`](4c-util.md)
- [4D. `smufl`](4d-smufl.md)
- [4E. `prefs`](4e-prefs.md)
- [4F. `font`](4f-font.md)

### §4 — summary

Audited all ~50 production classes across the eight packages (excl. `package-info`) in two waves of parallel production-first sub-audits. Dominant patterns to drive remediation:

1. **Whole packages / subsystems are dark.** `prefs.RecentDocumentsManager` (full MRU logic) has zero tests, and `Prefs`' five existing methods cover only the `getMap`/`putMap` family — every scalar getter, `put` overload, `parseJsonValue`, `writeTyped`, `migrate`, and `removeObsoleteKeys` are untested. Most of `util`'s pure-logic helpers and all of `smufl`'s geometry/lookup logic are untested.
2. **Pure computation is the biggest blind spot — and it is high-risk.** `midi.LineTrackBuilder.getTupletFactor` (log2 tuplet timing, 3 branches), `MidiSequenceBuilder.buildSequenceWithRepeats` (repeat state machine + first/second endings), `MidiEventFactory.addTempoEvent` (3-byte big-endian SET_TEMPO — byte-order mutation = silent wrong tempo), `smufl.BBox` (`union`/`translateX`/`fromSMuFL` Y-flip), `util.StringUtils.wrapText` + `GraphicUtils` px↔unit conversions + `Utils.getPlatformKeyStrokeString`, and `Prefs` typed coercion + `migrate` — all dark.
3. **Weak-but-green / self-referential tests persist (same pattern as Sessions 1/3).** Self-referential oracles: `EngravingTest.testGClefWidthMatchesSmuflAdvanceWidth` and the cross-package `requireBBox`-as-its-own-oracle usages. Hollow assertions: midi `isNotEmpty()`/`hasSizeGreaterThanOrEqualTo(4)` on bend/CC events, `MyFontUtilsTest` (`isNotNull()`+`getSize()` only), `Prefs` map tests (`containsKey`-only + a name-mismatch on the "missing key" test), `DocumentFonts.defaultsFromPrefs` (size-only, family unverified), `setFont`-by-name (`isNotEmpty()`). Mocked-out-and-hollow: `UIUtils.positionDialog` is "covered" by `BaseDialogPositionTest`, which stubs `UIUtils` entirely — the 3/8 + `SCREEN_MARGIN_PX` arithmetic is never asserted.
4. **Name/behavior mismatch:** `GlissandoMidiIntegrationTest.testNoPitchBendWithoutGlissando` reads only the fixture model and never builds a MIDI track — MIDI mutations leave it green.
5. **Untested error/throw paths:** `smufl` `require*` absent-glyph throws (all four) + `GlyphAnchors` null guards, `converter.ArgumentReader` unknown-flag/file-not-found/`System.exit` paths, `Prefs.getDefault` IAE for unknown key.
6. **Dead code surfaced (delete in remediation, don't test), verified zero refs:** `util.StringUtils.removeSyllabifyMarkings` (+ its `HYPHEN_UNDERSCORE_PATTERN`/`IN_PARENTHESES_PATTERN`) and `util.MyFontUtils.getXHeight`.
7. **Production observations filed as a tracked issue (#409):** `Prefs.parseJsonValue` maps JSON arrays to `null`, silently dropping any array-valued default in `defaults.json`; `Prefs.getStringList` ignores defaults (asymmetric with scalar getters).
