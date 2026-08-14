/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.ui.dialog;

import module java.desktop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.formdev.flatlaf.FlatClientProperties;

import net.engio.mbassy.listener.Handler;

import org.intellij.lang.annotations.MagicConstant;

import org.jspecify.annotations.Nullable;

import songscribe.message.MessageCenter;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ThemeAwareMatteBorder;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * The base class for all application dialogs, providing common layout
 * helpers, lifecycle management, and inner classes for tabbed content.
 */
public abstract class BaseDialog {

    // Used by addLabeledField to determine where to place the label
    public enum LabelPosition {
        LEFT,
        TOP,
    }

    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_WIDTH = "width";
    private static final String KEY_HEIGHT = "height";

    private static int visibleBlockingDialogCount = 0;
    private static final Map<Class<?>, DialogGeometry> SAVED_GEOMETRY = new HashMap<>();
    private static final GeometryResetSubscriber GEOMETRY_RESET_SUBSCRIBER = new GeometryResetSubscriber();

    private final MainFrame mainFrame;
    protected final String dialogTitle;
    protected final boolean isModal;
    private final DialogCategory category;
    protected final JPanel contentPanel = new JPanel(new BorderLayout());
    private final List<Tab> tabs = new ArrayList<>();

    /** The tab {@link #showTab} asked for, consumed by the next show. */
    private @Nullable Tab requestedTab = null;

    /** The control {@link #showTab} asked to focus, consumed with {@link #requestedTab}. */
    private @Nullable JComponent requestedFocus = null;

    // Declaration order matters: buildTabbedContent() reads tabListModel, tabList,
    // tabCards, and tabContentArea, so they must be initialized before tabbedContent.
    private final DefaultListModel<String> tabListModel = new DefaultListModel<>();
    private final JList<String> tabList = buildTabList();
    private final JPanel tabCards = new JPanel(new CardLayout());
    // The column to the right of the full-height sidebar: holds the tab cards and,
    // for a StandardDialog, its button panel — so the sidebar spans the full dialog
    // height while the buttons sit only under the content (macOS System Settings look).
    private final JPanel tabContentArea = new JPanel(new BorderLayout());
    private final JComponent tabbedContent = buildTabbedContent();
    private boolean hasSidebar;

    private @Nullable Component savedFocusOwner;

    @SuppressWarnings("NullAway.Init")
    private JDialog dialog;

    /**
     * Whether the dialog window exists and is on screen.
     * <p>
     * {@link #dialog} is deferred-init — null until the first {@code setVisible(true)} —
     * and code that populates tab content can run before that, so "is it showing" has to
     * answer false rather than throw for a dialog that was never built.
     */
    private boolean isShowing() {
        //noinspection ConstantValue — deferred-init field, genuinely null before first show
        return dialog != null && dialog.isShowing();
    }

    protected BaseDialog(MainFrame mainFrame, String title) {
        this(mainFrame, title, true);
    }

    protected BaseDialog(MainFrame mainFrame, String title, boolean isModal) {
        this(mainFrame, title, isModal, DialogCategory.OPERATIONAL);
    }

    protected BaseDialog(MainFrame mainFrame, String title, boolean isModal, DialogCategory category) {
        // Subscribed here rather than in a static initializer: geometry can only be
        // saved once a dialog exists, so first-construction subscription loses nothing,
        // merely loading the class cannot register the handler, and a test teardown
        // that unsubscribes it is healed by the next dialog construction. Subscribing
        // is idempotent, so repeated constructions are safe.
        MessageCenter.subscribe(GEOMETRY_RESET_SUBSCRIBER);

        this.mainFrame = mainFrame;
        dialogTitle = title;
        this.isModal = isModal;
        this.category = category;
    }

    public DialogCategory getCategory() {
        return category;
    }

    public static boolean isAnyBlockingDialogVisible() {
        return visibleBlockingDialogCount > 0;
    }

    static void resetVisibleBlockingDialogCount() {
        visibleBlockingDialogCount = 0;
    }

    static void resetSavedGeometry() {
        SAVED_GEOMETRY.clear();
    }

    private static void incrementBlockingCount() {
        visibleBlockingDialogCount++;

        if (visibleBlockingDialogCount == 1) {
            MessageCenter.post(new DialogVisibilityDidChangeNotification(true));
        }
    }

    private static void decrementBlockingCount() {
        visibleBlockingDialogCount--;

        if (visibleBlockingDialogCount == 0) {
            MessageCenter.post(new DialogVisibilityDidChangeNotification(false));
        }
    }

    protected List<Tab> getTabs() {
        return tabs;
    }

    /** Package-private: allows tests to read the sidebar/card composite without full dialog setup. */
    JComponent getTabbedContent() {
        return tabbedContent;
    }

    /**
     * The container a subclass's button panel should attach to. With a sidebar,
     * that is the content column to the right of the full-height sidebar, so the
     * sidebar reaches the bottom of the dialog while the buttons sit only under
     * the content. Without a sidebar, it is the content pane itself.
     */
    protected Container getButtonPanelContainer() {
        return hasSidebar ? tabContentArea : contentPanel;
    }

    /** Package-private: allows tests to read the sidebar list without full dialog setup. */
    JList<String> getTabList() {
        return tabList;
    }

    private JList<String> buildTabList() {
        var list = new JList<>(tabListModel) {
            @Override
            public void updateUI() {
                // Fired from JComponent's ctor (via setUI) BEFORE our init runs —
                // reference no instance fields; the static lookup is safe.
                super.updateUI();
                setBackground(FlatLafProps.getColor(FlatLafKey.DIALOG_SIDEBAR_BACKGROUND));
            }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(true);
        list.setFixedCellHeight(FlatLafProps.getInt(FlatLafKey.DIALOG_LIST_CELL_HEIGHT));
        list.setBorder(UIUtils.spacingBorder(FlatLafKey.DIALOG_SIDEBAR_PADDING));
        list.putClientProperty(
            FlatClientProperties.STYLE,
            "selectionArc: " + FlatLafProps.getInt(FlatLafKey.DIALOG_LIST_SELECTION_ARC)
        );
        return list;
    }

    private JComponent buildTabbedContent() {
        var sidebar = new SidebarPanel(tabList);

        tabContentArea.add(tabCards, BorderLayout.CENTER);

        var composite = new JPanel(new BorderLayout());
        composite.add(sidebar, BorderLayout.WEST);
        composite.add(tabContentArea, BorderLayout.CENTER);

        tabList.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }

            selectTab(tabList.getSelectedIndex());
        });

        return composite;
    }

    protected JComponent createTabbedContent() {
        hasSidebar = true;
        return tabbedContent;
    }

    /**
     * Registers a tab so the base class can call its lifecycle methods.
     */
    protected void registerTab(Tab tab) {
        tabs.add(tab);
    }

    /**
     * Adds a tab to the sidebar/card composite and registers it for lifecycle
     * callbacks. The tab's title (from {@link Tab#getTitle()}) becomes its
     * sidebar row; its card name is its insertion index.
     */
    protected void addTab(Tab tab) {
        tabCards.add(tab, String.valueOf(tabs.size()));
        tabListModel.addElement(tab.getTitle());
        registerTab(tab);
    }

    /**
     * The undo op the current commit performs, so each subclass can name itself
     * (e.g. {@code Add Tempo Change} / {@code Change Tempo Change} /
     * {@code Remove Tempo Change}).
     */
    protected enum DialogOp { ADD, EDIT, REMOVE }

    /** Where a show should land: which tab to select, and what to put the caret in. */
    private record ShowRequest(int tabIndex, @Nullable JComponent focus) {}

    /**
     * Shows the dialog with {@code tab} selected rather than the first tab, and
     * {@code focus} holding the caret — or the platform's default first focusable
     * control when {@code focus} is null.
     * <p>
     * The tab is named by object: {@link #addTab} is the sole definition of tab order, so
     * resolving through it is exact, and a caller cannot pass an index that quietly
     * means a different tab after one is inserted.
     * <p>
     * The caret target travels with the tab request rather than being parked on the tab,
     * so one read consumes the whole request and there is no per-tab state to leak into a
     * later open. A tab that always leads with the same control says so by overriding
     * {@link Tab#getInitialFocus} instead — that is a standing property of the tab, not a
     * request about one particular show.
     */
    protected void showTab(Tab tab, @Nullable JComponent focus) {
        requestedTab = tab;
        requestedFocus = focus;
        setVisible(true);
    }

    /**
     * Takes the pending {@link #showTab} request, clearing it, defaulting to the first tab
     * and no caret target when none was made.
     * <p>
     * Read at the very start of the show, before anything that can abort it. A show
     * cancelled by {@code getData()} — or by a tab throwing out of it — must still
     * consume the request, or it would survive into an unrelated later open.
     * <p>
     * A tab that is not registered falls back to the first tab rather than leaving the
     * dialog on no card at all, and drops the caret target with it: that control belongs
     * to a tab this dialog is not going to show.
     */
    private ShowRequest consumeShowRequest() {
        var tab = requestedTab;
        var focus = requestedFocus;
        requestedTab = null;
        requestedFocus = null;

        if (tab == null) {
            return new ShowRequest(0, focus);
        }

        var index = tabs.indexOf(tab);

        return index < 0 ? new ShowRequest(0, null) : new ShowRequest(index, focus);
    }

    /**
     * The standing caret target of the tab(s) this show puts on screen, or null when none
     * names one.
     * <p>
     * With a sidebar exactly one tab is on screen. Without one every tab is, so the first
     * that names a control wins — an arbitrary but stable choice, and no such dialog names
     * more than one today.
     */
    @Nullable
    private JComponent shownTabInitialFocus(int shownTabIndex) {
        if (hasSidebar) {
            return shownTabIndex < tabs.size() ? tabs.get(shownTabIndex).getInitialFocus() : null;
        }

        for (var tab : tabs) {
            var focus = tab.getInitialFocus();

            if (focus != null) {
                return focus;
            }
        }

        return null;
    }

    /**
     * Asks {@code component} to take the caret once the window is up, if there is one.
     * <p>
     * Posted rather than called: {@link JComponent#requestFocusInWindow} is a no-op while
     * the window is not showing, and a modal {@code setVisible(true)} blocks. Queuing it
     * means it runs once the window is up either way.
     */
    private static void requestFocusLater(@Nullable JComponent component) {
        if (component != null) {
            SwingUtilities.invokeLater(component::requestFocusInWindow);
        }
    }

    /**
     * Selects {@code index} in the sidebar as the dialog opens. A no-op
     * {@code setSelectedIndex} fires no event, so {@link #selectTab} is driven directly
     * in that case.
     */
    private void selectInitialTab(int index) {
        if (tabList.getSelectedIndex() == index) {
            selectTab(index);
        } else {
            tabList.setSelectedIndex(index);
        }
    }

    /*
     * Selection / lifecycle flow
     *
     * setVisible(true) with no sidebar fires tabWillShow on ALL tabs and applies the content
     * padding. With a sidebar it selects the tab consumeShowRequest() names — the one
     * requested through showTab, otherwise the first — consumed at the very start of the show,
     * before anything that can abort it: if the list is already on that index, selectTab(index)
     * is called directly, because a no-op setSelectedIndex fires no event; otherwise
     * setSelectedIndex(index) drives the ListSelectionListener, which returns early while
     * valueIsAdjusting and otherwise calls selectTab(idx).
     *
     * The same request carries the control to put the caret in. Failing one, the shown tab's
     * own getInitialFocus() supplies it. Either way requestFocusLater queues the request, so
     * it lands once the window is actually up.
     *
     * selectTab(index) returns immediately for a negative index. Otherwise, for each registered
     * tab t at position i it calls t.tabWillShow() when i == index and t.tabWillHide() otherwise
     * — so hide fires on ALL others, including already-hidden ones — then shows the matching
     * CardLayout card, named String.valueOf(index).
     *
     * setVisible(false) fires tabWillHide on ALL tabs (unchanged).
     *
     * INVARIANT (append-only): tabs-list index  ==  list-model index  ==  CardLayout card name (String.valueOf(index)).
     *   addTab only appends, keeping all three in lockstep. Removing/reordering a tab would break card lookup silently.
     * NOTE: selectTab(0) at initial show now fires tabWillHide on the non-selected tabs (unified show+hide path); the
     *   old code fired show-only at initial show. Hides are idempotent for the current tabs. Covered by test H (Phase 4).
     */
    private void selectTab(int index) {
        if (index < 0) {
            return;
        }

        for (var i = 0; i < tabs.size(); i++) {
            var tab = tabs.get(i);

            if (i == index) {
                tab.tabWillShow();
            } else {
                tab.tabWillHide();
            }
        }

        ((CardLayout) tabCards.getLayout()).show(tabCards, String.valueOf(index));

        // A tab appears either because the dialog is opening or because the user switched
        // to it. The show path handles the first case; here the window is already up and
        // that path has come and gone, so honor the tab's own leading control now. Read
        // after the card swap above, or the control is not yet displayable.
        if (isShowing()) {
            requestFocusLater(shownTabInitialFocus(index));
        }
    }

    public static void addLabeledField(
        JComponent container,
        String labelText,
        JComponent field,
        LabelPosition labelPosition
    ) {
        if (labelPosition == LabelPosition.LEFT) {
            addLabeledField(container, new JLabel(labelText), field);
        } else {
            var label = new JLabel(labelText);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Indent the title a bit to line up with the input field
            var indent = FlatLafProps.getInt(FlatLafKey.DIALOG_LABEL_INDENT);
            label.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Stack label above field in a dedicated panel rather than adding both directly to
            // container: container's layout manager is the caller's choice (BorderLayout,
            // BoxLayout, ...) and only BoxLayout-style managers stack bare children this way.
            var panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(label);
            panel.add(field);
            container.add(panel);
        }
    }

    /**
     * Adds a left-positioned {@code label + field} row to {@code container},
     * like {@link #addLabeledField} with {@link LabelPosition#LEFT} but taking a
     * pre-built label so the caller can column-align it (e.g. a fixed width set
     * by a shared measuring pass) before it is laid out.
     */
    public static void addLabeledField(JComponent container, JLabel label, JComponent field) {
        var panel = new JPanel(
            new FlowLayout(FlowLayout.LEFT, FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP), 0)
        );
        panel.setBorder(BorderFactory.createEmptyBorder());
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        label.setLabelFor(field);
        panel.add(label);
        panel.add(field);
        container.add(panel);
    }

    protected static void addLabelToBox(
        JPanel box,
        String text,
        int gapHeight
    ) {
        var label = new JLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(label);

        if (gapHeight > 0) {
            box.add(Box.createVerticalStrut(gapHeight));
        }
    }

    public static void addSeparator(JComponent container) {
        addSeparator(container, false);
    }

    public static void addLargeSeparator(JComponent container) {
        addSeparator(container, true);
    }

    /**
     * Adds the larger inter-section spacer used between stacked sections of a
     * {@link Tab} (sized from {@code DIALOG_SECTION_GAP}). The container's own
     * {@code add} applies any layout constraints (a {@link Tab} routes through
     * its constrained {@code add}).
     */
    public static void addSectionSeparator(JComponent container) {
        container.add(Box.createVerticalStrut(
            FlatLafProps.getInt(FlatLafKey.DIALOG_SECTION_GAP)
        ));
    }

    /**
     * Adds a spacer strut to {@code container}, sized from the FlatLaf gap
     * properties. The orientation follows the container's layout: an X-axis
     * {@link BoxLayout} gets a horizontal strut, anything else (Y-axis box or a
     * {@link Tab}'s {@link GridBagLayout}) gets a vertical one. The container's
     * own {@code add} applies any layout constraints (e.g. {@link Tab} routes
     * through its constrained {@code add}).
     */
    private static void addSeparator(JComponent container, boolean isLarge) {
        if (container.getLayout() instanceof BoxLayout boxLayout
                && boxLayout.getAxis() == BoxLayout.X_AXIS) {
            container.add(Box.createHorizontalStrut(FlatLafProps.getInt(
                isLarge
                    ? FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_EXTRA_GAP
                    : FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP
            )));
        } else {
            container.add(Box.createVerticalStrut(FlatLafProps.getInt(
                isLarge
                    ? FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP
                    : FlatLafKey.DIALOG_COMPONENT_VERTICAL_GAP
            )));
        }
    }

    public void setVisible(boolean visible) {
        if (visible) {
            // Consumed before anything can abort the show, so a request that never reached
            // the screen cannot survive into a later open.
            var showRequest = consumeShowRequest();

            dialog = new JDialog(mainFrame, dialogTitle, isModal);
            dialog.setResizable(isResizable());
            dialog.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        if (isClosable()) {
                            setVisible(false);
                        }
                    }
                }
            );
            dialog.addWindowFocusListener(
                new WindowAdapter() {
                    @Override
                    public void windowLostFocus(WindowEvent e) {
                        savedFocusOwner = dialog.getFocusOwner();
                    }

                    @Override
                    public void windowGainedFocus(WindowEvent e) {
                        if (savedFocusOwner != null) {
                            savedFocusOwner.requestFocusInWindow();
                            savedFocusOwner = null;
                        }
                    }
                }
            );

            UIUtils.addStandardDialogKeyBindings(dialog);

            dialog.setContentPane(contentPanel);

            var defaultButton = getDefaultButton();

            if (defaultButton != null) {
                dialog.getRootPane().setDefaultButton(defaultButton);
            }

            if (!getData()) {
                dialog.dispose();
                return;
            }

            if (hasSidebar) {
                selectInitialTab(showRequest.tabIndex());
            } else {
                for (var tab : tabs) {
                    tab.tabWillShow();
                }
            }

            // The caller asked for this control on this particular open, so it outranks the
            // shown tab's standing choice of leading control.
            var initialFocus = showRequest.focus() != null
                ? showRequest.focus()
                : shownTabInitialFocus(showRequest.tabIndex());

            if (!hasSidebar) {
                var paddingKey = getContentPaddingKey();

                if (paddingKey != null) {
                    contentPanel.setBorder(UIUtils.spacingBorder(paddingKey));
                }
            }

            dialog.pack();
            pinSizeToPreferred();

            var geometry = SAVED_GEOMETRY.get(getClass());

            if (geometry == null) {
                geometry = loadGeometryFromPrefs();
            }

            if (geometry != null) {
                applyGeometry(geometry);
            } else {
                UIUtils.positionDialog(dialog, mainFrame);
            }

            // Force layout commitment before showing to prevent flash on first display
            dialog.validate();

            if (category.isBlocking()) {
                incrementBlockingCount();
            }

            requestFocusLater(initialFocus);

            try {
                dialog.setVisible(true);
            } catch (Exception e) {
                // Dispose before decrementing, for the same reason as the close path below:
                // decrementBlockingCount() posts DialogVisibilityDidChangeNotification(false),
                // which handlers read as "the dialog window is gone".
                dialog.dispose();

                if (category.isBlocking()) {
                    decrementBlockingCount();
                }

                throw e;
            }
        } else {
            try {
                for (var tab : tabs) {
                    tab.tabWillHide();
                }

                var location = dialog.getLocation();
                var size = isResizable() ? dialog.getSize() : null;
                var closingGeometry = new DialogGeometry(location, size);
                SAVED_GEOMETRY.put(getClass(), closingGeometry);

                var valueMap = new HashMap<String, Object>();
                valueMap.put(KEY_X, location.x);
                valueMap.put(KEY_Y, location.y);

                if (size != null) {
                    valueMap.put(KEY_WIDTH, size.width);
                    valueMap.put(KEY_HEIGHT, size.height);
                }

                var simpleClassName = getClass().getSimpleName();
                Prefs.putMap(PrefsKey.DIALOG_GEOMETRY, Map.of(simpleClassName, valueMap));
            } finally {
                // Dispose first: decrementBlockingCount() posts DialogVisibilityDidChangeNotification(false),
                // which handlers rely on to mean the dialog is actually gone (e.g. Component.getMousePosition()
                // on a component the dialog was covering only returns a real value once disposal has removed
                // the dialog window) — posting before dispose() would have them react while it is still showing.
                dialog.dispose();

                if (category.isBlocking()) {
                    decrementBlockingCount();
                }
            }
        }
    }

    private @Nullable DialogGeometry loadGeometryFromPrefs() {
        var simpleClassName = getClass().getSimpleName();
        var allGeometry = Prefs.getMap(PrefsKey.DIALOG_GEOMETRY);
        var entry = allGeometry.get(simpleClassName);

        if (!(entry instanceof Map<?, ?> map)) {
            return null;
        }

        var rawX = map.get(KEY_X);
        var rawY = map.get(KEY_Y);

        if (!(rawX instanceof Number) || !(rawY instanceof Number)) {
            return null;
        }

        var location = new Point(((Number) rawX).intValue(), ((Number) rawY).intValue());
        Dimension size = null;

        var rawWidth = map.get(KEY_WIDTH);
        var rawHeight = map.get(KEY_HEIGHT);

        if (rawWidth instanceof Number && rawHeight instanceof Number) {
            size = new Dimension(((Number) rawWidth).intValue(), ((Number) rawHeight).intValue());
        }

        var geometry = new DialogGeometry(location, size);
        SAVED_GEOMETRY.put(getClass(), geometry);
        return geometry;
    }

    private void applyGeometry(DialogGeometry geometry) {
        var size = geometry.size();

        if (isResizable() && size != null) {
            // Floor semantics: max of packed size and restored size per dimension
            var packedSize = dialog.getSize();
            var width = Math.max(packedSize.width, size.width);
            var height = Math.max(packedSize.height, size.height);
            var bounds = new Rectangle(geometry.location(), new Dimension(width, height));
            var clamped = GraphicUtils.clampToScreen(bounds);
            dialog.setBounds(clamped);
        } else {
            dialog.setLocation(GraphicUtils.clampToScreen(geometry.location(), dialog.getSize()));
        }
    }

    /**
     * Returns the button that should be the default button for the dialog,
     * or null if there is no default button.
     */
    protected @Nullable JButton getDefaultButton() {
        return null;
    }

    /**
     * Returns whether the dialog should be resizable. Defaults to false.
     * Subclasses can override to allow resizing.
     */
    protected boolean isResizable() {
        return false;
    }

    /**
     * Returns extra width to add to the dialog beyond its packed size.
     * Subclasses can override this to make the dialog wider.
     */
    protected int getExtraWidth() {
        return 0;
    }

    /**
     * Returns extra height to add to the dialog beyond its packed size.
     * Subclasses can override this to make the dialog taller.
     */
    protected int getExtraHeight() {
        return 0;
    }

    /**
     * Returns whether the window-close action (title-bar X, Escape, Cmd+W)
     * should be honored. Defaults to true. Subclasses can override to
     * prevent the user from dismissing the dialog while an operation is in
     * progress.
     */
    protected boolean isClosable() {
        return true;
    }

    protected @Nullable FlatLafKey getContentPaddingKey() {
        return hasButtons() ? FlatLafKey.DIALOG_STD_BUTTONS_PADDING : FlatLafKey.DIALOG_STD_PADDING;
    }

    protected boolean hasButtons() {
        return false;
    }

    /**
     * Returns the underlying {@link JDialog}, or null if the dialog is not
     * currently visible. Useful when a caller needs a {@link Component}
     * parent for a nested dialog.
     */
    public @Nullable JDialog getWindow() {
        return dialog;
    }

    protected void pack() {
        dialog.pack();
    }

    /**
     * Pins the dialog's size to its current preferred size (plus any extra
     * width/height), so the window neither grows nor shrinks on its own.
     * Must be called immediately after {@link JDialog#pack()}.
     */
    private void pinSizeToPreferred() {
        var size = dialog.getPreferredSize();
        size.width += getExtraWidth();
        size.height += getExtraHeight();
        dialog.setPreferredSize(size);
        dialog.setSize(size);
        dialog.setMinimumSize(size);
    }

    /**
     * Re-packs the dialog to fit its current content. Use when a tab's content
     * changes height at runtime (e.g. a live preview) and the fixed size pinned
     * at show time would otherwise starve it. Clears that pin so {@link JDialog#pack()}
     * recomputes from the layout, then re-pins to the new preferred size,
     * preserving the dialog's location. A no-op until the dialog is showing, so
     * it is safe to call from content-population code that runs before display.
     */
    protected void repackToContent() {
        // Null until the first show: content-population code (document listeners on a
        // tab's fields, say) can run before the dialog is ever constructed.
        if (!isShowing()) {
            return;
        }

        var location = dialog.getLocation();
        dialog.setPreferredSize(null);
        dialog.setMinimumSize(null);
        dialog.pack();
        pinSizeToPreferred();
        dialog.setLocation(location);
    }

    /**
     * The window this dialog parents itself to.
     *
     * <p><strong>Window parenting only.</strong> It is not a route to the score: a dialog may not
     * query or modify state outside itself, and reaching the document through here is that rule
     * broken in a longer spelling. What a dialog needs from the document arrives as values, and
     * what it writes goes through a {@link DialogBackEnd} — see {@code .agents/guides/dialogs.md}.
     *
     * @return the application window, for parenting a window or an alert
     */
    protected MainFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * Called when the dialog is about to be shown. Populates controls
     * by iterating registered tabs. Subclasses may override to add
     * dialog-level population logic (call {@code super.getData()} to
     * run tab iteration). Subclasses with no registered tabs may override
     * without calling {@code super}.
     *
     * @return true to proceed with showing the dialog, false to cancel
     */
    protected boolean getData() {
        for (var tab : tabs) {
            if (!tab.getData()) {
                return false;
            }
        }

        return true;
    }

    private static final class SidebarPanel extends JPanel {
        // Full-height sidebar: opaque, theme-live background, 1px right divider.
        private SidebarPanel(JComponent tabList) {
            super(new BorderLayout());
            setOpaque(true);

            // The divider sits on the sidebar panel, outside the list's internal padding.
            setBorder(new ThemeAwareMatteBorder(
                0, 0, 0, 1, FlatLafKey.DIALOG_SIDEBAR_BORDER_COLOR.key()
            ));
            add(tabList, BorderLayout.CENTER);
        }

        @Override
        public void updateUI() {
            // Fired from JComponent's ctor (via setUI) BEFORE our init runs —
            // reference no instance fields; the static lookup is safe.
            super.updateUI();
            setBackground(UIManager.getColor("TabbedPane.background"));
        }
    }

    /**
     * One page of a tabbed dialog: a panel that owns its controls and its own layout.
     *
     * <p><strong>A tab populates and displays; it does not commit and it does not validate.</strong>
     * Both of those are the dialog's, because both are about the values as a whole — a rule that
     * spans tabs cannot be checked from inside one of them, and a commit split across tabs is
     * several undo steps where the user made one edit. A tab therefore contributes what its
     * controls say to the dialog's gathered values and stops there; see {@link CommitDialog}.
     */
    @SuppressWarnings("NoopMethodInAbstractClass")
    protected abstract class Tab extends JPanel {

        protected final GridBagConstraints constraints =
            new GridBagConstraints();

        private final String title;

        private boolean hasFillItem = false;

        protected Tab(String title) {
            this(title, hasButtons() ? FlatLafKey.DIALOG_STD_BUTTONS_PADDING : FlatLafKey.DIALOG_STD_PADDING);
        }

        protected Tab(String title, FlatLafKey paddingKey) {
            this.title = title;
            setLayout(new GridBagLayout());

            // Add inner padding to the panel
            setBorder(UIUtils.spacingBorder(paddingKey));

            // Items in the tab should be top/left-aligned, grow horizontally, and not vertically
            constraints.gridx = 0;
            constraints.gridy = GridBagConstraints.RELATIVE;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            constraints.weighty = 0;
        }

        String getTitle() {
            return title;
        }

        protected void build() {
            initContents();

            // Add glue at the bottom that will force the contents to the top,
            // unless a fill item was added (which already claims all extra space)
            if (!hasFillItem) {
                constraints.gridx = 0;
                constraints.weightx = 1.0;
                constraints.weighty = 1.0;
                constraints.fill = GridBagConstraints.BOTH;
                add(Box.createGlue(), constraints);
            }
        }

        /**
         * Subclasses add components to the tab here.
         */
        protected abstract void initContents();

        /**
         * Populate controls from the model. Called when the dialog is
         * about to be shown.
         *
         * @return true to proceed, false to cancel showing the dialog
         */
        @SuppressWarnings("SameReturnValue")
        protected boolean getData() {
            return true;
        }

        /**
         * Called after {@link #getData()} but before the dialog becomes
         * visible. Use for lazy loading, focus requests, etc.
         */
        protected void tabWillShow() {}

        /**
         * The control that should hold the caret whenever this tab appears, or null to
         * leave the platform's default first focusable control in charge.
         * <p>
         * A standing property of the tab, asked for afresh every time the tab is shown —
         * both when the dialog opens on it and when the user switches to it in a window
         * that is already up. A caller that wants a particular control for one particular
         * open passes it to {@link BaseDialog#showTab} instead, and that outranks this.
         */
        @Nullable
        protected JComponent getInitialFocus() {
            return null;
        }

        /**
         * Called before the dialog is disposed. Use for cleanup
         * (e.g., stopping playback).
         */
        protected void tabWillHide() {}

        @Override
        public Component add(Component comp) {
            add(comp, constraints);
            return comp;
        }

        /**
         * Adds a component that expands to fill available space in the given
         * direction ({@link GridBagConstraints#HORIZONTAL},
         * {@link GridBagConstraints#VERTICAL}, or
         * {@link GridBagConstraints#BOTH}). Use this instead of {@link #add}
         * when a component should grow beyond its preferred size. At most one
         * expanding component should be added per tab.
         */
        protected void addExpanding(
            Component comp,
            @MagicConstant(
                intValues = { GridBagConstraints.HORIZONTAL, GridBagConstraints.VERTICAL, GridBagConstraints.BOTH }
            ) int direction) {
            var fillsHorizontally = direction == GridBagConstraints.HORIZONTAL
                || direction == GridBagConstraints.BOTH;
            var fillsVertically = direction == GridBagConstraints.VERTICAL
                || direction == GridBagConstraints.BOTH;
            constraints.fill = direction;
            constraints.weightx = fillsHorizontally ? 1.0 : 0;
            constraints.weighty = fillsVertically ? 1.0 : 0;
            add(comp, constraints);
            hasFillItem = true;
        }
    }

    protected static class TitledSection extends JPanel {

        public TitledSection(String title) {
            this(title, BoxLayout.Y_AXIS);
        }

        public TitledSection(
            String title,
            @MagicConstant(
                intValues = { BoxLayout.X_AXIS, BoxLayout.Y_AXIS }
            ) int axis
        ) {
            setLayout(new BoxLayout(this, axis));
            setBorder(new StandardTitledBorder(title));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
    }

    protected static class StandardTitledBorder extends TitledBorder {

        private Insets insets = FlatLafProps.getInsets(FlatLafKey.DIALOG_TITLED_BORDER_PADDING);
        private final int labelIndent = FlatLafProps.getInt(FlatLafKey.DIALOG_LABEL_INDENT);

        public StandardTitledBorder(String title) {
            super(title);
        }

        public void setInsets(Insets insets) {
            this.insets = new Insets(insets.top, insets.left, insets.bottom, insets.right);
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            var textInset = UIManager.getInt("TitledBorder.textInset");

            if (textInset <= 0) {
                super.paintBorder(c, g, x, y, width, height);
                return;
            }

            var originalBorder = getBorder();

            // If the border came from UIManager (i.e. none was explicitly set), restore null
            // so future calls to getBorder() continue to pick up the current UIManager value.
            var uiManagerBorder = UIManager.getBorder("TitledBorder.border");
            var restoreBorder = originalBorder == uiManagerBorder ? null : originalBorder;

            setBorder(new LeftAdjustedBorder(originalBorder, textInset - labelIndent));

            try {
                super.paintBorder(c, g, x, y, width, height);
            } finally {
                setBorder(restoreBorder);
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return insets;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = this.insets.left;
            insets.right = this.insets.right;
            insets.top = this.insets.top;
            insets.bottom = this.insets.bottom;
            return insets;
        }

        private static class LeftAdjustedBorder extends AbstractBorder {

            @Nullable
            private final Border delegate;
            private final int adjustedLeft;

            LeftAdjustedBorder(@Nullable Border delegate, int adjustedLeft) {
                this.delegate = delegate;
                this.adjustedLeft = adjustedLeft;
            }

            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                if (delegate != null) {
                    delegate.paintBorder(c, g, x, y, width, height);
                }
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return getBorderInsets(c, new Insets(0, 0, 0, 0));
            }

            @Override
            public Insets getBorderInsets(Component c, Insets insets) {
                if (delegate != null) {
                    var delegateInsets = delegate.getBorderInsets(c);
                    insets.top = delegateInsets.top;
                    insets.bottom = delegateInsets.bottom;
                    insets.right = delegateInsets.right;
                }

                insets.left = adjustedLeft;
                return insets;
            }
        }
    }

    private static class GeometryResetSubscriber {

        @Handler
        public void prefsDidChange(PrefsDidChangeNotification notification) {
            var key = notification.getKey();

            if (key == PrefsKey.ALL || key == PrefsKey.DIALOG_GEOMETRY) {
                SAVED_GEOMETRY.clear();
            }
        }
    }
}
