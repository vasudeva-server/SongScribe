## Dialog Framework: BaseDialog & StandardDialog

All application dialogs extend `BaseDialog` (package `songscribe.ui.dialog`). Dialogs that commit user changes use `StandardDialog`, which adds OK/Apply/Cancel buttons and a validation/commit lifecycle.

### Class Hierarchy

```
BaseDialog (abstract)
├── StandardDialog (abstract) — OK/Apply/Cancel commit lifecycle
│   ├── CompositionSettingsDialog, LyricsDialog, ExportPDFDialog, ...
│   ├── AboutDialog (uses OK only, hides Apply/Cancel)
│   └── FontDialog — wraps FontChooser panel with OK/Cancel
├── PreferencesDialog — live-apply, no OK/Apply/Cancel
└── ProgressBarDialog — non-closable progress display (INFORMATIONAL)
```

### BaseDialog

`BaseDialog` is abstract and manages dialog creation, layout, lifecycle, and tab registration. It does **not** extend `JDialog` — it creates a `JDialog` on each `setVisible(true)` call and disposes it on `setVisible(false)`.

**Key fields and accessors:**

| Member | Purpose |
|--------|---------|
| `contentPanel` (`JPanel`, `BorderLayout`) | The content pane — subclasses add content to `CENTER` and buttons to `SOUTH` |
| `dialogTitle` | Window title string |
| `isModal` | Whether the dialog blocks input to the parent |
| `getMainFrame()` | Returns the `MainFrame` singleton |
| `getScore()` | Returns the current `Score` (nullable) |
| `getComposition()` | Returns the current `Composition` (requires non-null score) |

**Layout constants:**

| Constant | Value | Purpose |
|----------|-------|---------|
| `HORIZONTAL_MARGIN` | 5 | Standard horizontal spacing between components |
| `VERTICAL_MARGIN` | 5 | Standard vertical spacing between components |
| `SECTION_MARGIN` | 15 | Margin around titled sections |

**Constructors:**

```java
protected BaseDialog(String title)              // modal, OPERATIONAL (defaults)
protected BaseDialog(String title, boolean isModal)  // OPERATIONAL (default)
protected BaseDialog(String title, boolean isModal, DialogCategory category)
```

**Layout helpers (all static):**

| Method | Purpose |
|--------|---------|
| `addLabeledField(container, labelText, field, LabelPosition.LEFT)` | Label and field side-by-side in a `FlowLayout` row |
| `addLabeledField(container, labelText, field, LabelPosition.TOP)` | Label above field, both left-aligned |
| `addLabelToBox(box, text, gapHeight)` | Adds a left-aligned label and optional vertical strut to a Box panel |
| `configureSlider(slider, majorTickSpacing, labels)` | Configures snap-to-tick, paint labels/ticks |

**Lifecycle (`setVisible`):**

When `setVisible(true)` is called:
1. Creates a new `JDialog` with standard key bindings (Escape to close)
2. Sets the default button (from `getDefaultButton()`)
3. Calls `getData()` — if it returns `false`, the dialog is disposed without showing
4. Calls `tabWillShow()` on each registered tab
5. Packs, applies `getExtraWidth()`, sets minimum size, restores saved position if available or positions relative to `MainFrame`
6. Shows the dialog (blocks if modal)

When `setVisible(false)` is called:
1. Calls `tabWillHide()` on each registered tab
2. Saves the dialog location to a static map keyed by the dialog's class — position persists even for dialogs created fresh on each invocation
3. Disposes the `JDialog`

**Overridable hooks:**

| Method | Default | Override when |
|--------|---------|---------------|
| `getDefaultButton()` | `null` | Dialog has a default action button |
| `isResizable()` | `false` | Dialog should be resizable |
| `getExtraWidth()` | `0` | Dialog needs extra width beyond its packed size |
| `getExtraHeight()` | `0` | Dialog needs extra height beyond its packed size |
| `isClosable()` | `true` | Window-close (title-bar X, Escape, Cmd+W) should be blocked (e.g. during a long operation) |
| `getWindow()` | Current `JDialog` or `null` | Callers need a `Component` parent for nested dialogs (e.g. `OptionDialogs` from a background thread) |
| `getData()` | Resets tab pane to index 0, calls `getData()` on all tabs | Adding dialog-level data population (call `super`) |

### Dialog Categories

Every `BaseDialog` has a `DialogCategory` passed to the constructor (default: `OPERATIONAL`):

- `INFORMATIONAL` — read-only, never blocked (About, Help, WhatsNew)
- `EXCLUSIVE` — modifies global state, e.g. Preferences, CompositionSettings
- `OPERATIONAL` — modifies scoped state or runs a task (default)

Exclusive and operational dialogs are mutually exclusive — only one blocking dialog at a time. Informational dialogs are always allowed. `OptionDialogs` does not participate (it uses raw `JOptionPane`).

Actions that open blocking dialogs must set `UIAction.Flag.OPENS_DIALOG`. `DialogOpenAction` does not auto-set this flag.

### StandardDialog

Extends `BaseDialog` with OK/Apply/Cancel buttons and a validation-then-commit lifecycle.

**Fields:**

| Field | Purpose |
|-------|---------|
| `buttonPanel` | `JPanel` (right-aligned `FlowLayout`) containing the three buttons |
| `okButton` | Validates, commits, repaints score, closes dialog |
| `applyButton` | Validates, commits, repaints score, keeps dialog open |
| `cancelButton` | Closes dialog without committing |

**Button panel:** Add to your layout with `contentPanel.add(BorderLayout.SOUTH, buttonPanel)`.

**Constructors:**

```java
protected StandardDialog(String title)              // modal, OPERATIONAL (defaults)
protected StandardDialog(String title, boolean isModal)  // OPERATIONAL (default)
protected StandardDialog(String title, boolean isModal, DialogCategory category)
```

**Lifecycle on OK/Apply click:**

1. `isValidData()` — if `false`, stops (dialog stays open)
2. `setData()` — writes control values back to the model
3. `score.repaint()` — refreshes the score display
4. OK only: `setVisible(false)` — closes the dialog

**Overridable hooks:**

| Method | Default | Override when |
|--------|---------|---------------|
| `isValidData()` | Iterates tabs, calls `tab.isValidData()` | Adding dialog-level validation (call `super`) |
| `setData()` | Iterates tabs, calls `tab.setData()` | Adding dialog-level commit logic (call `super`) |

### Tab (BaseDialog inner class)

`Tab` extends `JPanel` with `GridBagLayout` and provides a standard component layout for tabbed content. Components are top/left-aligned and grow horizontally by default.

**Construction pattern:**

```java
private final class MyTab extends Tab {  // or StandardDialog.Tab from a StandardDialog subclass

    private final JTextField nameField = new JTextField(20);

    private MyTab() {
        build();  // MUST call build() at the end of the constructor
    }

    @Override
    protected void initContents() {
        add(createSomeSection());
        addSeparator();              // adds SECTION_MARGIN vertical space
        add(createAnotherSection());
    }
}
```

**Key methods:**

| Method | Purpose |
|--------|---------|
| `build()` | Must be called at the end of the subclass constructor. Calls `initContents()`, then adds bottom glue |
| `initContents()` | Override to add components. Use `add(component)` which auto-applies `GridBagConstraints` |
| `add(component)` | Overridden — adds with pre-configured constraints (top-left, horizontal fill) |
| `addSeparator()` | Adds `SECTION_MARGIN` vertical strut between groups |
| `addExpanding(component, direction)` | Adds a component that fills available space (`HORIZONTAL`, `VERTICAL`, or `BOTH`). Use at most once per tab |

**Tab lifecycle methods (override as needed):**

| Method | Called when | Purpose |
|--------|------------|---------|
| `getData()` | Dialog is about to show | Populate controls from model. Return `false` to cancel showing |
| `setData()` | User clicks OK or Apply (StandardDialog only) | Write control values back to model |
| `isValidData()` | Before `setData()` | Return `false` to block commit on invalid input |
| `tabWillShow()` | After `getData()`, before dialog visible | Lazy loading, focus requests |
| `tabWillHide()` | Before dialog disposed | Cleanup (stop playback, etc.) |

### TitledSection (BaseDialog inner class)

A `JPanel` with `BoxLayout` and a `StandardTitledBorder`. Used to group related controls within a tab.

```java
var section = new TitledSection("Section Title");             // vertical (default)
var section = new TitledSection("Section Title", BoxLayout.X_AXIS);  // horizontal

section.add(someComponent);
section.addSeparator();   // adds VERTICAL_MARGIN or HORIZONTAL_MARGIN strut depending on axis
section.add(anotherComponent);
```

Set `alignmentX = LEFT_ALIGNMENT` automatically. Components added to the section should also set `LEFT_ALIGNMENT` when appropriate.

### Tab registration

Tabs must be registered with the dialog for lifecycle callbacks to work:

```java
// Method 1: addTab (preferred) — adds to JTabbedPane AND registers
var tabbedPane = createTabbedPane();
addTab(tabbedPane, "Tab Title", new MyTab());

// Method 2: registerTab — registers without adding to a pane (rare)
registerTab(tab);
```

### Complete StandardDialog example (tabbed)

```java
public class MySettingsDialog extends StandardDialog {

    public MySettingsDialog() {
        super(Strings.get(Strings.DIALOG_MY_SETTINGS_TITLE));

        var tabbedPane = createTabbedPane();
        addTab(tabbedPane, "General", new GeneralTab());
        addTab(tabbedPane, "Advanced", new AdvancedTab());

        contentPanel.add(BorderLayout.CENTER, tabbedPane);
        contentPanel.add(BorderLayout.SOUTH, buttonPanel);
    }

    private final class GeneralTab extends Tab {

        private final JTextField nameField = new JTextField(20);

        private GeneralTab() {
            build();
        }

        @Override
        protected void initContents() {
            var section = new TitledSection("Name");
            addLabeledField(section, "Name:", nameField, LabelPosition.LEFT);
            add(section);
        }

        @Override
        protected boolean getData() {
            nameField.setText(getComposition().getTitle());
            return true;
        }

        @Override
        protected boolean isValidData() {
            if (nameField.getText().isBlank()) {
                OptionDialogs.showErrorMessage(...);
                return false;
            }
            return true;
        }

        @Override
        protected void setData() {
            getComposition().setTitle(nameField.getText());
        }
    }
}
```

### Complete BaseDialog example (non-standard, no OK/Apply/Cancel)

```java
public class PreferencesDialog extends BaseDialog {

    public PreferencesDialog() {
        super(Strings.get(Strings.DIALOG_PREFERENCES_TITLE), false);  // non-modal

        var tabbedPane = createTabbedPane();
        addTab(tabbedPane, "General", new GeneralTab());
        // ... more tabs

        contentPanel.add(BorderLayout.CENTER, tabbedPane);
        // No buttonPanel — preferences apply changes live
    }
}
```

### Opening dialogs

Dialogs are opened via `DialogOpenAction`, which lazily instantiates the dialog and calls `setVisible(true)`:

```java
new DialogOpenAction<>(actionName, MyDialog.class)
```

The action creates the dialog instance on first use via reflection (no-arg constructor required) and reuses it for subsequent opens.
