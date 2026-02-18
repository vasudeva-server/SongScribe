# Layout Manager Implementation Progress

## Status Overview

| Item | Status |
|------|--------|
| Phases 1-6 | Complete (Foundation → Footnotes & XML 1.3) |
| Phase 7 Integration | In Progress (5 of 6 checkpoints done) |
| Phase 8 Testing & Polish | Not Started |

---

## Architecture Overview

The vertical layout system replaces the old renderMap/RenderStep mechanism with a LayoutManager-based approach:

- **LayoutManager** (`src/main/java/songscribe/ui/layout/LayoutManager.java`) - Central layout calculation and coordinate management
- **LayoutChangeMessage** (`src/main/java/songscribe/ui/message/LayoutChangeMessage.kt`) - Event notification (Section enum: TITLE, ATTRIBUTION, SCORE, LYRICS, BANGLA_LYRICS, TRANSLATION, FOOTNOTES; ChangeType: CONTENT, FONT, SIZE)
- **Score integration** - Score calls `layoutManager.measure(Graphics2D)` during paintComponent, delegates coordinate queries to LayoutManager
- **Renderer methods** - Added `measureTitle()`, `measureAttribution()`, `measureScore()`, `measureLyrics()`, `measureBanglaLyrics()`, `measureTranslation()`, `measureFootnotes()`

All model changes (Composition, Line, VerticalAdjustment) post LayoutChangeMessage to trigger layout recalculation and repaint.

---

## Phase 7: Integration - In Progress

### Completed Checkpoints

**Checkpoints 1-4 completed:** LayoutManager.measure() implemented, Score methods migrated, renderMap/RenderStep removed, LayoutChangeMessage wired up throughout codebase.

### Remaining Checkpoints

#### Checkpoint 5: Debounced Repaint
- Implement 300ms debounce timer in `Score.onLayoutChanged()`
- Cancel previous timer on new changes to batch multiple rapid changes
- Prevents excessive repaints during rapid editing

#### Checkpoint 6: Export Path Updates
- Update `ExportPDFAction` to use LayoutManager bounds
- Update `Score.exportToSVG()` to use LayoutManager bounds
- Verify "export without title" option works with new layout system

---

## Phase 8: Testing & Polish

- [ ] Create synthetic test compositions for edge cases
- [ ] Unit tests for LayoutManager calculations
- [ ] Unit tests for Line.getRequiredHeight()
- [ ] Test hit-testing and selection
- [ ] Test export with/without title
- [ ] Test version 1.2 to 1.3 migration
- [ ] Visual regression tests
- [ ] Manual QA with real songs

---

## Key Files

**Core Layout System:**
- `src/main/java/songscribe/ui/layout/LayoutManager.java`
- `src/main/java/songscribe/ui/message/LayoutChangeMessage.kt`
- `src/main/java/songscribe/ui/component/Score.java`
- `src/main/java/songscribe/ui/renderer/Renderer.java`

**Model & Data:**
- `src/main/java/songscribe/music/Composition.java`
- `src/main/java/songscribe/music/Line.java`

**UI Adjustments & IO:**
- `src/main/java/songscribe/ui/adjustment/VerticalAdjustment.java`
- `src/main/java/songscribe/io/CompositionIO.java`
