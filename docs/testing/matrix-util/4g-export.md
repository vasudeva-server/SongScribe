### 4G. `export`

Audited by reading all seven production class bodies; stub/IO-dispatch classes have no branching logic worth testing, but `PageLayoutData.applyMarginOverrides` and `PDFExporter.createPDF` contain real conditional computation.

| class | behavior | required level | existing test | verdict | action |
|---|---|---|---|---|---|
| `ExportOptions` | record construction; ALL / NONE constants encode correct boolean triples | unit | none | missing | add unit: verify ALL=(true,true,true), NONE=(false,false,false), and round-trip equality |
| `PageLayoutData` | `applyMarginOverrides`: default applied to all four margins when overrides are -1 | unit | none | missing | add unit: call with all -1 overrides, assert all four fields equal defaultMargin |
| `PageLayoutData` | `applyMarginOverrides`: per-edge override replaces default when value > -1 | unit | none | missing | add unit: supply distinct per-edge values, assert each field independently |
| `PageLayoutData` | `applyMarginOverrides`: boundary — value exactly 0 overrides (> -1) | unit | none | missing | add unit: value=0 should override (currently: 0 > -1 is true) |
| `PDFExporter` | `createPDF`: returns early (no crash) when `data.scoreView` is null | unit | none | missing | add unit: construct PageLayoutData with scoreView=null, call createPDF, assert no exception |
| `PDFExporter` | `createPDF`: scale = min(horizontalScale, verticalScale) — horizontal-constrained branch | unit | none | missing | add unit with mock ScoreView; verify scale and leftMargin under each branch |
| `PDFExporter` | `createPDF`: leftMargin redistribution when `horizontalScale >= verticalScale` | unit | none | missing | same unit as above; assert leftMargin = scaledMargin * (leftInner / (leftInner + rightOuter)) |
| `ABCExporter` | `createABC`: stub — shows error dialog, no logic | none | none | — | no test warranted (pure dialog dispatch, no computation) |
| `ImageExporter` | `createImageForExport[0]`: image dimensions = (sheetWidthPx * scale + borderWidth, sheetHeightPx(opts) * scale + borderHeight) | unit | none | missing | add unit with mock ScoreView and border; assert BufferedImage dimensions |
| `ImageExporter` | `createImageForExport[1]`: renders without exception (stub body, but dimensions/type are real) | none | none | — | body is a stub ("not yet implemented" drawString); no assertion value until implemented |
| `SVGExporter` | `createSVG`: stub — shows error dialog | none | none | — | no test warranted |
| `ExportUtils` | `openExportedFile`: Swing dialog dispatch; no computation | none | none | — | no test warranted |

**4G notes (quality concerns):**

The highest-risk dark gaps are `PageLayoutData.applyMarginOverrides` (four independent conditional branches, all untested — any off-by-one in the threshold guard or a field assignment to the wrong variable would survive indefinitely) and `PDFExporter.createPDF` (non-trivial margin redistribution math under the `horizontalScale >= verticalScale` branch, also completely untested). The `ExportOptions` record is trivial but its constants are contract-defining — a future edit that silently flips a boolean in ALL or NONE would have no safety net. `ImageExporter.createImageForExport[0]` does compute the output image dimensions from scale and border, making the dimension formula testable right now even though the rendering body is a stub. `ABCExporter`, `SVGExporter`, and `ExportUtils` are pure dialog-dispatch stubs with no branching logic and correctly classify as none. There is no dead code in this package: all classes are reachable from UI action paths.

