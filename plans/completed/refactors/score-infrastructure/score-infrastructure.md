# Plan: Score.java Infrastructure Extraction

**Type:** Master Plan  <br>
**Created:** 2026-02-03  <br>
**Status:** Complete  <br>
**Category:** refactors  <br>
**Completed:** 2026-02-04

---

## Overview

Extract 6 infrastructure classes from Score.java (1,828 lines) to reduce it to ~1,190 lines. This continues the work from the completed score-cleanup plan, focusing on infrastructure concerns rather than domain logic.

## Status Dashboard

| Phase | Class | Package | Lines | Status |
|-------|-------|---------|-------|--------|
| 1 | [ScorePanel](#phase-1-scorepanel) | songscribe.ui.component.score | ~60 | ✅ Complete |
| 2 | [ScoreFocusController](#phase-2-scorefocuscontroller) | songscribe.ui.component | ~50 | ✅ Complete |
| 3 | [ComponentHierarchyNavigator](#phase-3-componenthierarchynavigator) | songscribe.ui.component | ~80 | ✅ Complete |
| 4 | [ScoreInitializer](#phase-4-scoreinitializer) | songscribe.ui.component | ~100 | ✅ Complete |
| 5 | [ScoreInputHandler](#phase-5-scoreinputhandler) | songscribe.ui.component | ~200 | ✅ Complete |
| 6 | [ScoreMessageCoordinator](#phase-6-scoremessagecoordinator) | songscribe.ui.message | ~150 | ✅ Complete |

---

## Phase 1: ScorePanel

**Status:** Complete  <br>
**Risk:** Very Low

**Extract inner class to top-level** (lines 1771-1827).

```java
// src/main/java/songscribe/ui/component/score/ScorePanel.java
public class ScorePanel extends JPanel implements Scrollable {
    private final Component content;

    public ScorePanel(Component content) { ... }
    // Move all Scrollable implementation
}
```

**Score changes**: Remove inner class, add import.

---

## Phase 2: ScoreFocusController

**Status:** Complete  <br>
**Risk:** Low

**Extract focus management** (lines 269-270, 1549-1551, 1579-1588, 1635-1647).

```java
// src/main/java/songscribe/ui/component/ScoreFocusController.java
public interface FocusRestorationCallback {
    void requestFocusInWindow();
}

public final class ScoreFocusController {
    private final ArrayList<Component> componentsAllowedToGainFocus;
    private final FocusRestorationCallback callback;

    public ScoreFocusController(@NotNull FocusRestorationCallback callback);
    public void focusGained(FocusEvent e);
    public void focusLost(FocusEvent e);
    public void allowFocusInComponent(Component component);
    // Move FocusLostThread as private inner class
}
```

**Score changes**: Implement `FocusRestorationCallback`, delegate FocusListener methods.

---

## Phase 3: ComponentHierarchyNavigator

**Status:** Complete  <br>
**Risk:** Low

**Extract component tree navigation** (lines 488-503, 627-647, 1427-1485).

```java
// src/main/java/songscribe/ui/component/ComponentHierarchyNavigator.java
public interface ComponentHierarchyProvider {
    @Nullable MainPanel getMainPanel();
    @NotNull Composition getComposition();
}

public final class ComponentHierarchyNavigator {
    public ComponentHierarchyNavigator(@NotNull ComponentHierarchyProvider provider);

    @Nullable public LineComponent getLineComponent(int lineIndex);
    public int getActualLineMiddleY(int lineIndex);
    public int findLineIndexAtPoint(int y);
    public void setupLineComponentState(BiPredicate<Integer, Integer> selectionProvider, Score score);
    public void updateLayoutFromComponents(Consumer<int[]> layoutUpdater);
}
```

**Score changes**: Implement `ComponentHierarchyProvider`, delegate hierarchy methods.

---

## Phase 4: ScoreInitializer

**Status:** Complete  <br>
**Risk:** Low-Medium

**Extract initialization logic** (lines 325-345, 355-374, 380-425, 505-525).

```java
// src/main/java/songscribe/ui/component/ScoreInitializer.java
public final class ScoreInitializer {
    // Static factory method
    public static void initialize(
        @NotNull Score score,
        @NotNull IMainFrame mainFrame,
        @NotNull SelectionManager selectionManager,
        @NotNull EditModeManager editModeManager
    );

    // Private helper methods
    private static void initKeys(Score score);
    private static void initEditPopup(Score score);
    private static JScrollPane initScorePanel(JPanel marginPanel);
    private static MainPanel initMainPanel(Composition composition);
    private static JPanel initMargin(Score score);
    private static void initAdjustments(Score score);
    private static void initView(Score score);
}
```

**Score changes**: Replace `init()` body with `ScoreInitializer.initialize(this, ...)`.

---

## Phase 5: ScoreInputHandler

**Status:** Complete  <br>
**Risk:** Medium

**Extract mouse/keyboard handling** (lines 1273-1575, 1732-1768).

```java
// src/main/java/songscribe/ui/component/ScoreInputHandler.java
public interface InputHandlerCallback {
    void repaint();
    void clearSelection();
    void selectionChanged();
    int getNoteYPos(int yPos, int line);
    Control getControl();
    Mode getMode();
    Composition getComposition();
    boolean isDragDisabled();
    JPopupMenu getEditPopup();
    int getRowHeight();
}

public final class ScoreInputHandler
    implements MouseListener, MouseMotionListener, KeyListener {

    private boolean shiftPressed = false;

    public ScoreInputHandler(
        @NotNull InputHandlerCallback callback,
        @NotNull SelectionManager selectionManager,
        @NotNull EditModeManager editModeManager
    );

    // All MouseListener methods
    // All MouseMotionListener methods
    // All KeyListener methods
    // KeyAction inner class
    // calculateSelection, updateSelection helpers
}
```

**Score changes**:
- Implement `InputHandlerCallback`
- Create inputHandler instance
- Delegate all listener methods
- Remove `implements MouseListener, MouseMotionListener, KeyListener`
- Register inputHandler as listener instead of `this`

---

## Phase 6: ScoreMessageCoordinator

**Status:** Complete  <br>
**Risk:** Medium

**Extract @Handler methods** (lines 732-949, 1013-1047, 1161-1186, 1617-1627, 1653-1729).

```java
// src/main/java/songscribe/ui/message/ScoreMessageCoordinator.java
public interface MessageCoordinatorCallback {
    void repaint();
    void clearSelection();
    void selectionChanged();
    void setMode(Mode mode);
    void setControl(Control control);
    void setEditNote(Note editNote);
    Composition getComposition();
    void setComposition(Composition composition);
    void requestFocusInWindow();
    boolean isFocusOwner();
    MainPanel getMainPanel();
    IMainFrame getMainFrame();
    HorizontalAdjustment getHorizontalAdjustment();
    VerticalAdjustment getVerticalAdjustment();
    LyricsAdjustment getLyricsAdjustment();
    Control getControl();
}

public final class ScoreMessageCoordinator {
    public ScoreMessageCoordinator(
        @NotNull MessageCoordinatorCallback callback,
        @NotNull Supplier<MusicEditOperations> operationsSupplier,
        @NotNull EditModeManager editModeManager,
        @NotNull SelectionManager selectionManager,
        @NotNull ClipboardManager clipboardManager
    ) {
        // Store dependencies
        MessageCenter.subscribe(this);  // CRITICAL: Subscribe here
    }

    // Move all 21 @Handler methods
    // Move helper methods: handleCut, handleCopy, handleDelete, handlePaste, updateEditNote
}
```

**Score changes**:
- Implement `MessageCoordinatorCallback`
- Remove `MessageCenter.subscribe(this)` from Score constructor
- Remove all @Handler methods
- Create messageCoordinator in `init()` after operations exists

---

## Critical Files

| File | Action |
|------|--------|
| `src/main/java/songscribe/ui/component/Score.java` | Refactor (remove ~640 lines) |
| `src/main/java/songscribe/ui/component/score/ScorePanel.java` | Create |
| `src/main/java/songscribe/ui/component/ScoreFocusController.java` | Create |
| `src/main/java/songscribe/ui/component/ComponentHierarchyNavigator.java` | Create |
| `src/main/java/songscribe/ui/component/ScoreInitializer.java` | Create |
| `src/main/java/songscribe/ui/component/ScoreInputHandler.java` | Create |
| `src/main/java/songscribe/ui/message/ScoreMessageCoordinator.java` | Create |

---

## Verification

After each phase:
1. Run `./scripts/compile.sh` to verify compilation
2. Run `./scripts/run-debug.sh` and test:
   - Note editing (mouse click to insert)
   - Selection (shift+click, drag)
   - Keyboard navigation
   - Focus behavior (click away and back)
   - Menu operations (beaming, ties, tuplets)
   - Playback start/stop
