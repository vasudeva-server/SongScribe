### 10E — Font Chooser Panes & Listeners

| Class | Behavior | Required level | Existing test | Verdict | Action | done |
|---|---|---|---|---|---|---|
| FamilyPane, PreviewPane, StylePane | Widget assembly, layout wiring, listener delegation — no branching logic | none | none | n/a | none | — |
| SizePane | `initSizeListModel()` step-doubling loop (pure layout data); `getSelectedSize()` list-vs-spinner branch; spinner↔list sync listener — all Swing state delegation | none | none | n/a | none | — |
| SearchListener | `keyTyped`: lowercases text, delegates to `FamilyListModel.findFirst()`, calls `setSelectedFamily` if non-null — all logic lives in collaborators; listener itself is pure wiring (the `findFirst` search logic is audited under 10D) | none | none | n/a | none | — |
| StyleCellRenderer | `getListCellRendererComponent`: extracts `entry.getName()`, passes to super — pure delegation, no logic | none | none | n/a | none | — |
| StyleEntry | Constructor: delegates style derivation to `MyFontUtils.getStyleDescription()` — no independent logic | none | none | n/a | none | — |
| StyleEntry | `equals`: compares by `font.getPSName()` | unit | none | missing | Add `StyleEntryTest.testEqualsComparesOnPsName`: two entries same PS name → equal; different PS name → not equal | ✅ |
| StyleEntry | `hashCode`: delegates to `font.hashCode()` — inconsistent with `equals` (equals by PSName, hash by Font identity); breaks equals/hashCode contract when same PSName but different Font instances | unit | none | missing | Add `StyleEntryTest.testHashCodeConsistentWithEquals` (will expose the contract violation as a production bug) | ✅ |
| FamilyListSelectionListener | `valueChanged` guard: skips when `getValueIsAdjusting()`; builds new `Font(family, oldStyle, oldSize)` from container state; calls `setSelectedFont` + `setPreviewFont` | unit | none | missing | Add `FamilyListSelectionListenerTest`: adjusting event → no calls; non-adjusting → correct Font constructed and set on container | ✅ |
| SizeListSelectionListener | `valueChanged` guard: skips when `getValueIsAdjusting()`; derives font at new size; calls `setSelectedFont` + `setPreviewFont` | unit | none | missing | Add `SizeListSelectionListenerTest`: adjusting → no calls; non-adjusting → `deriveFont(newSize)` applied | ⬜ |
| StyleListSelectionListener | `valueChanged` guard: skips when `getValueIsAdjusting()`; derives font from `selectedStyle.getFont()` at current size; calls `setSelectedFont` + `setPreviewFont` | unit | none | missing | Add `StyleListSelectionListenerTest`: adjusting → no calls; non-adjusting → font derived from selected style at current size | ⬜ |

**Notes.**

Five behaviors warrant unit tests; all are missing. The three `*ListSelectionListener` classes share the same pattern (guard + font construction) and can be covered in a single test class each, mocking `FontContainer`. `StyleEntry.hashCode` is inconsistent with its `equals`: `equals` compares by `Font.getPSName()`, but `hashCode` delegates to `Font.hashCode()` — two `StyleEntry` instances with the same PS name but different `Font` objects will be `equals` yet have different hash codes, violating the Java contract; a test for this should be written (it will fail, exposing a production bug). `FamilyListModel.findFirst` is not in this slice but is the real logic behind `SearchListener`; it is untested and should be covered in a separate `FamilyListModelTest`. The `getStyleDescription` logic in `MyFontUtils` is complex but already partially tested in `MyFontUtilsTest`; that test covers `createFont` only and does not exercise style description derivation — a gap worth addressing in a future session.

