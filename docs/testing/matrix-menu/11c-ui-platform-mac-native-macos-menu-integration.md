### 11C — `ui/platform/mac` (Native macOS Menu Integration)

| Class | Behavior | Required level | Existing test | Verdict | Action |
|---|---|---|---|---|---|
| `MacNativeMenuController` | Constructor subscribes to `MessageCenter` and stores strong reference so the MBassador weak-ref rule is satisfied | unit | none | missing | Add unit test: construct, post `DialogVisibilityDidChangeNotification`, verify `setEnabled` called on each managed item — use Mockito `@Mock NSMenuItem` injected via a constructor overload or reflective field set |
| `MacNativeMenuController` | `dialogVisibilityDidChange` sets all `managedItems` to `!notification.isVisible()` (enables on hide, disables on show) | unit | none | missing | Test with a small list of mock `NSMenuItem`s: post notification with `isVisible=true` → verify `setEnabled(false)`; `isVisible=false` → verify `setEnabled(true)` |
| `MacNativeMenuController` | `dialogVisibilityDidChange` iterates all managed items, not just the first | unit | none | missing | Post one notification with two mock items in the list; verify both receive `setEnabled` |
| `MacNativeMenuController` | `discoverNativeItems` returns empty list and logs warning when `appMenuItem.hasSubmenu()` is false | unit | none | missing | Mock the Rococoa chain (or factor discovery behind an interface) to return a top-level item with `hasSubmenu()=false`; assert result is empty |
| `MacNativeMenuController` | `discoverNativeItems` matches each `AppMenuAction` by `startsWith` prefix against item titles; unmatched actions produce a warning log | unit | none | missing | Provide mock `NSMenuItem`s with known titles, one matching and one not; verify matched item is in result, unmatched triggers LOG.warn |
| `MacNativeMenuController` | `discoverNativeItems` wraps the whole native call sequence in a broad `try/catch(Exception)`; any exception returns empty list | unit | none | missing | Have `NSApplication.sharedApplication()` throw a `RuntimeException`; assert no exception propagates and the returned list is empty |
| `MacNativeMenuController` | `discoverNativeItems` calls `setAutoenablesItems(false)` on the app menu before iterating items | unit | none | missing | Verify this side-effect on the mock `NSMenu` |
| `MacNativeMenuController` | `Actions.getAppMenuActions()` result is fetched correctly (correct count, correct native titles) | unit | `ActionsAppMenuTest.testGetAppMenuActionsReturnsExpectedActions`, `testAppMenuActionsHaveCorrectNativeTitles` | adequate | — (already covered at the right level in `ActionsAppMenuTest`; not a `MacNativeMenuController` behavior per se, but the dependency is tested) |
| `NSApplication` | `sharedApplication()` delegates to `CLASS.sharedApplication()` (pure Rococoa pass-through) | none | none | none | Native JNI/Rococoa bridge — no assertable logic on our side without a live macOS runtime |
| `NSApplication` | `mainMenu()` abstract method (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenu` | `numberOfItems()`, `itemAtIndex()`, `setAutoenablesItems()` abstract methods (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenu` | `title()` abstract method | none | none | none | Pure native pass-through — `title()` has no callers in production code (see Dead code) |
| `NSMenu` | `itemWithTitle()` abstract method | none | none | none | Pure native pass-through — no callers in production code (see Dead code) |
| `NSMenuItem` | `title()`, `hasSubmenu()`, `submenu()`, `setEnabled()` abstract methods (pure Rococoa pass-through) | none | none | none | Pure native pass-through |
| `NSMenuItem` | `isEnabled()` abstract method | none | none | none | Pure native pass-through — `isEnabled()` has no callers in production code (see Dead code) |

**Notes:**

`NSApplication`, `NSMenu`, and `NSMenuItem` are thin Rococoa abstract-class wrappers. Every method on them is `abstract` and is dispatched directly to the native Objective-C runtime via the Rococoa/JNA bridge — there is no Java-side logic, no branching, and no transformation. These are correctly classified `none`: they cannot be unit-asserted without a live macOS runtime, and testing that Rococoa calls the right native method would be testing the Rococoa framework, not our code.

All testable behavior lives in `MacNativeMenuController`. The two most important gaps are (1) the `dialogVisibilityDidChange` handler — this is the entire runtime purpose of the controller and has zero test coverage — and (2) the `discoverNativeItems` discovery logic, which contains several distinct branches (no-submenu guard, prefix-`startsWith` matching, exception swallowing) that can all be exercised by mocking the NS* interfaces. The `MacNativeMenuController` constructor takes no parameters today, making injection of mock NS* objects awkward; the recommended approach is either a package-private constructor that accepts a pre-built `List<NSMenuItem>` for testing, or extracting the NS* chain calls behind a narrow functional interface. The OS-conditional path in `MenuController.initMenus` (wraps construction in `if (SystemInfo.isMacOS)`) is in a different class and out of scope here; note it also swallows the `Throwable` silently in headless mode, which means test runs on non-macOS CI will never exercise the controller at all — reinforcing the need for injectable mocking.

`BaseDialogCounterTest` exercises `DialogVisibilityDidChangeNotification` dispatch thoroughly at the sender side (verifying the message is posted on first-open and last-close). That is the upstream dependency of `dialogVisibilityDidChange`; what is missing is the handler side — verifying that the controller correctly reacts to those notifications by enabling/disabling the managed native items.

**Tally:** 15 rows — 1 adequate · 7 missing · 0 inadequate · 0 wrong-level · 7 none · 0 redundant.

**Dead code:**
- `NSMenu._Class.alloc()` — declared but never called anywhere in `src/main` or `src/test`. Likely copied from a template; the factory pattern is unused because `NSMenu` instances are obtained only via `NSApplication.mainMenu()` and `NSMenuItem.submenu()`.
- `NSMenuItem._Class.alloc()` — same situation; never called.
- `NSMenu.CLASS` field — never read (the `NSMenu._Class` factory is never invoked, so the Rococoa-registered class object is unused).
- `NSMenuItem.CLASS` field — same.
- `NSMenu.title()` — no callers in `src/main` or `src/test`.
- `NSMenu.itemWithTitle(String)` — no callers in `src/main` or `src/test`.
- `NSMenuItem.isEnabled()` — no callers in `src/main` or `src/test`.

Note: these symbols are in an OS-conditional package, but the dead-code determination is based on verified reference searches across all of `src/main` and `src/test`, not just conditional paths. There is no reflective usage of these specific methods found.

**Production observations:**
- `NSMenu.CLASS` is annotated `@SuppressWarnings("unused")` and `NSMenuItem.CLASS` likewise, indicating the authors are aware these fields have no callers — but the `_Class.alloc()` methods and the `title()`/`itemWithTitle()`/`isEnabled()` methods have no such suppression, suggesting they were included speculatively for future use.
- `MacNativeMenuController` is not a singleton per the project's `singletons.md` pattern (no `private static final INSTANCE`). It is instead held as a `@Nullable private static` field on `MenuController` with a `@SuppressWarnings({"FieldCanBeLocal", "unused"})` annotation to prevent GC — this is a deliberate strong-reference anchor. The pattern deviates from the singleton guide but is intentional (the field only exists to prevent the MBassador weak-reference from being collected).

