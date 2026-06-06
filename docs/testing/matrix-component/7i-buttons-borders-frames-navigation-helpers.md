### 7I. Buttons, borders, frames & navigation helpers

| class | behavior | required level | existing test | verdict | action | done |
|---|---|---|---|---|---|---|
| `BaseLabel` | Constructor: index == -1 uses list background (not selection background), otherwise uses selection/normal background | unit | none | missing | Add `BaseLabel` unit tests: index==-1 path, isSelected==true path, isSelected==false path | ✅ |
| `BaseLabel` | `paintComponent` — pure rendering (fills background rect, sets foreground) | none | — | — | — | — |
| `BorderPanel` | `getMyBorder()` returns `MyBorder(borderSpinner.getValue())` when in simple mode | unit | none | missing | Unit-test `getMyBorder` in simple and expert modes (requires Swing components, but logic is pure construction) | ✅ |
| `BorderPanel` | `getMyBorder()` returns per-edge `MyBorder(top,bottom,left,right)` when in expert mode | unit | none | missing | (same test class as above) | ✅ |
| `BorderPanel` | `setExpertBorder(true)` copies uniform spinner value into all four edge spinners | unit | none | missing | Assert that toggling to expert mode populates edge spinners from the uniform value | ✅ |
| `BorderPanel` | `setExpertBorder(false/true)` toggles panel visibility and button label | none | — | — | Pure wiring/rendering, no computed value to assert | — |
| `MyBorder` | Constructor `(size)` sets all four edges to `size` | unit | none | missing | Parameterized unit tests for all four constructors and `withOverrides` | ✅ |
| `MyBorder` | Constructor `(horizontal, vertical)` sets left/right = horizontal, top/bottom = vertical | unit | none | missing | (same class) | ✅ |
| `MyBorder` | Constructor `(top, bottom, left, right)` sets each edge independently | unit | none | missing | (same class) | ✅ |
| `MyBorder` | `withOverrides(defaultSize, top, left, bottom, right)`: override applied when value > -1, skipped when == -1 | unit | none | missing | Test that -1 leaves edge at defaultSize, non-negative value overrides | ✅ |
| `MyBorder` | `getWidth()` returns `left + right`; `getHeight()` returns `top + bottom` | unit | none | missing | (same class) | ✅ |
| `ThemeAwareMatteBorder` | `getBorderInsets()` returns correct insets matching constructor arguments | unit | none | missing | Construct with known values, assert insets are correct | ✅ |
| `ThemeAwareMatteBorder` | `paintBorder` falls back to `DEFAULT_COLOR` when UIManager key not found | none | — | — | Pure rendering; color selection logic is trivial one-liner, no computable state to assert without Graphics mock | — |
| `ThemeAwareMatteBorder` | `isBorderOpaque()` always returns true | none | — | — | Trivial constant return, framework behavior | — |
| `ModeCycleButton` | `modeDidChange`: button NOT updated when `isAdjustmentMode()` is true | unit | none | missing | Mock `ModeDidChangeNotification`; verify `updateButton` is skipped in adjustment mode | ✅ |
| `ModeCycleButton` | `modeDidChange`: button IS updated when `isAdjustmentMode()` is false | unit | none | missing | (same test class) | ✅ |
| `ModeCycleButton` | `playbackStateDidChange`: button disabled when playing, enabled otherwise | unit | none | missing | Mock `PlaybackController.isPlaying()`; assert `isEnabled()` | ✅ |
| `ModeCycleButton` | `graceModeStateDidChange`: button disabled when grace mode active | unit | none | missing | Mock `GraceModeManager.isActive()`; assert `isEnabled()` | ✅ |
| `StickyToggleButton` | `actionPerformed`: when button is NOT selected after click, reselects it (sticky behavior) | unit | none | missing | Call `actionPerformed` with button in unselected state; verify `isSelected()` is re-set to true and action is NOT performed | ✅ |
| `StickyToggleButton` | `actionPerformed`: when button IS selected after click, marks action selected and fires `actionPerformed` | unit | none | missing | (same test class) | ✅ |
| `PopupButton` | `setCurrentAction(null)` is a no-op (no NPE, currentAction set to null, no configureButtonFromAction call) | unit | none | missing | Pass null; assert method returns without throwing | ✅ |
| `PopupButton` | `setCurrentAction(non-null Selectable)` calls `setSelected(true)` on the action and deselects button | unit | none | missing | Mock a `UIAction.Selectable`; verify `setSelected(true)` is called and button becomes deselected | ✅ |
| `PopupButton` | `actionPerformed`: `popupWasCanceledByButton` true → clears flag, deselects button, popup not shown again | unit | none | missing | Set flag via `popupMenuCanceled`, fire `actionPerformed`; verify flag reset and `isSelected()==false` | ✅ |
| `PopupButton` | `actionPerformed`: popup already visible → hides popup | none | — | — | Requires real Swing popup visibility; e2e cost exceeds value for this sub-branch | — |
| `PopupButton` | `popupMenuWillBecomeInvisible`: deselects button | unit | none | missing | Call listener method directly; assert `isSelected()==false` | ✅ |
| `PopupButton` | `popupMenuCanceled`: sets `popupWasCanceledByButton` correctly based on component under mouse | none | — | — | Requires real Swing mouse position; cannot be meaningfully unit-tested | — |
| `StaffAnnotationPopupButton` | `musicSelectionDidChange`: button enabled iff at least one `STAFF_ANNOTATION_ACTIONS` action is enabled | unit | none | missing | Mock actions: all disabled → button disabled; one enabled → button enabled | ✅ |
| `TupletPopupButton` | `musicSelectionDidChange`: button disabled when no tuplet action is enabled | unit | none | missing | Same pattern as `StaffAnnotationPopupButton` | ✅ |
| `TupletPopupButton` | `configureButtonFromAction` overrides tooltip to fixed tuplet string regardless of action | unit | none | missing | Call `setCurrentAction` with a mock action; assert tooltip is the tuplet fixed string | ✅ |
| `TickSlider` | Change listener: `tickDidChange` fired only when new value is in `stopSet` AND differs from `lastCommittedValue` | unit | none | missing | Construct concrete subclass; programmatically fire `setValue()` to a stop value, then same value again; verify callback count | ⬜ |
| `TickSlider` | `setSnappedValue`: selects the nearest stop when given an exact hit | unit | none | missing | Assert that `getValue()` equals the nearest stop; assert no spurious `tickDidChange` fires | ⬜ |
| `TickSlider` | `setSnappedValue`: selects the nearest stop when given an off-stop value | unit | none | missing | (same class — off-stop input) | ⬜ |
| `TickSlider` | `setSnappedValue`: updates `lastCommittedValue` to suppress a spurious `tickDidChange` on the subsequent `setValue` call | unit | none | missing | Verify no callback fires after `setSnappedValue` even if the value changes | ⬜ |
| `ComponentNames` | `line(index)` concatenates `LINE_PREFIX` + index correctly | unit | none | missing | Assert `line(0).equals("line-0")`, `line(3).equals("line-3")` — guards against future constant changes | ⬜ |
| `ComponentHierarchyNavigator` | `getLineComponent(index)`: returns null when `mainPanel` is null | unit | none | missing | Mock provider returning null; assert null result | ⬜ |
| `ComponentHierarchyNavigator` | `getLineComponent(index)`: returns matching `LineComponent` when found | unit | none | missing | Mock panel hierarchy with known line index; assert correct component returned | ⬜ |
| `ComponentHierarchyNavigator` | `getActualLineMiddleYPx`: returns 0 when `mainPanel` is null | unit | none | missing | (same test class — null panel path) | ⬜ |
| `ComponentHierarchyNavigator` | `getActualLineMiddleYPx`: sums Y offsets from mainPanel + staffPanel + linePanel + lineComponent + middleLineY | unit | none | missing | Mock all contributors with known Y values; assert sum | ⬜ |
| `ComponentHierarchyNavigator` | `findLineIndexAtPoint`: uses formula when `mainPanel` is null | unit | none | missing | Mock null panel + mock song with `topPaddingSs`; verify fallback formula | ⬜ |
| `ComponentHierarchyNavigator` | `findLineIndexAtPoint`: finds correct line panel containing the Y coordinate | unit | none | missing | Mock panels with known bounds; assert correct index | ⬜ |
| `ComponentHierarchyNavigator` | `findLineIndexAtPoint`: returns -1 when Y is outside all panels | unit | none | missing | (same class) | ⬜ |
| `ComponentHierarchyNavigator` | `updateLayoutFromComponents`: single panel fallback uses height + margin | unit | none | missing | Mock one panel; assert `rowHeightPx` uses the single-line formula | ⬜ |
| `ComponentHierarchyNavigator` | `updateLayoutFromComponents`: with >= 2 panels, rowHeight = midY[1] - midY[0] | unit | none | missing | Mock two panels with known midpoints; assert difference | ⬜ |
| `ActivationGate` | `activate()` makes glass pane visible; `deactivate()` hides it and stops timer | unit | none | missing | Call `install` with a real (hidden) `JFrame`; call `activate()`/`deactivate()` and assert glass pane visibility + timer state | ⬜ |
| `ActivationGate` | `appRaisedToForeground()` restarts the cmd+Tab timer | unit | none | missing | (same test class — assert timer restarts) | ⬜ |
| `ToolbarButton` | `propertyChange`: updates button from action when `FONT_ICON_KEY` or `FONT_KEY` changes | none | — | — | Pure Swing property dispatch wiring; no computable value beyond delegation | — |
| `ToolbarToggleButton` | `configurePropertiesFromAction`: delegates to `UIUtils` for `UIAction`, Swing default otherwise | none | — | — | Pure delegation wiring | — |
| `SplashWindow` | `loadSplashImage` throws `RuntimeError.exit` when image resource not found | none | — | — | Tests that interact with resource loading or `RuntimeError.exit` are impractical in unit context | — |
| `SplashWindow` | `closeSplash` is threadsafe: calls `invokeAndWait` when off EDT | none | — | — | Swing threading; not practically unit-testable without real EDT | — |
| `StartFrame` | `startFrame` — pure Swing frame construction and wiring | none | — | — | No logic beyond Swing setup | — |
| `TipFrame` | `showTip` reads tips file sequentially, wraps index to 0 when buffer is empty (end of file) | unit | none | missing | Supply a fixture tips file; verify wrap-around and index advancement | ⬜ |
| `TipFrame` | `previousButton` handler decrements `index` by 2 before calling `showTip` (and guards index > 1) | unit | none | missing | (same test class; exercise boundary at index==1 and index==2) | ⬜ |
| `TipFrame` | `closeWindow` persists `showTip` checkbox state to `Prefs` | unit | none | missing | Mock `Prefs`; assert correct key/value written | ⬜ |

**7I notes (quality concerns):**

The package has zero dedicated unit tests for any of the classes in scope — every class is untested. The highest-priority gaps are the pure-logic classes that carry genuine branching or computed state: `MyBorder` (constructor and `withOverrides` branching is completely unguarded), `TickSlider` (the stop-filtering and `setSnappedValue` snap-and-suppress logic are the whole point of the class and have no coverage), `ComponentHierarchyNavigator` (null-panel fallbacks, coordinate sum, bounds search — all missing), and `StickyToggleButton`/`PopupButton` (interaction-state machines with non-trivial branching). `ActivationGate` has stateful glass-pane and timer logic that is unit-testable with a hidden `JFrame` and deserves tests. `TipFrame.showTip` and the wrap-around logic are file-I/O-coupled but could be extracted or tested with a fixture file. `ComponentNames.line()` is a one-liner but its constant is referenced by e2e tests by name, making a change invisible at compile time — a single unit test is warranted to prevent silent string drift.

