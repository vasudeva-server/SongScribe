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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import com.formdev.flatlaf.FlatClientProperties;
import org.intellij.lang.annotations.MagicConstant;
import org.jspecify.annotations.Nullable;

import songscribe.lifecycle.Disposable;
import songscribe.message.MessageCenter;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.binding.Bindings;
import songscribe.binding.ObservableValue;
import songscribe.binding.ValueProperty;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ThemeAwareMatteBorder;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;


/**
 * The base class for all application dialogs.
 *
 * <p><strong>An instance serves one opening.</strong> The opener builds a dialog, shows it, and
 * drops it. The window's geometry outlives a close in {@link DialogGeometryStore}; nothing else
 * may be carried from one opening to the next.
 *
 * <p><strong>A non-modal dialog must not be built twice while one is up.</strong> The opener keeps
 * the dialog while {@link #isShowing()} answers true and answers a repeat invocation with
 * {@link #toFront()} — see {@link songscribe.ui.action.DialogOpenAction#open()}, which every entry
 * point to a non-modal dialog goes through.
 *
 * <p><strong>A non-modal dialog stays reachable after its opener returns</strong> through the
 * anonymous {@link WindowAdapter}s {@link #setVisible} registers, which the {@link JDialog}
 * holds. Removing them without an equivalent reference would make the dialog collectible
 * mid-use.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link #setVisible setVisible(false)} calls {@link #dispose()}, as does a show abandoned
 * before the window reached the screen. An instance is not reusable afterwards.
 */
public abstract class BaseDialog implements Disposable {

    // Used by addLabeledField to determine where to place the label
    public enum LabelPosition {
        LEFT,
        TOP,
    }

    private static int visibleDialogCount = 0;

    private final MainFrame mainFrame;
    protected final String dialogTitle;
    protected final Modality modality;
    protected final JPanel contentPanel = new JPanel(new BorderLayout());
    private final List<Tab> tabs = new ArrayList<>();
    private final Bindings bindings = new Bindings();

    // The conditions every one of which must hold for this dialog's values to be
    // committable. Held as an immutable list inside a property, and replaced rather than
    // added to, because that is what notifies: a condition contributed after `valid` is
    // already bound has to reach the binding, and a mutated list would not.
    private final ValueProperty<List<ObservableValue<Boolean>>> validityConditions =
        new ValueProperty<>(List.of());

    protected final ObservableValue<Boolean> valid =
        bindings.computed(() -> validityConditions.get().stream().allMatch(ObservableValue::get));

    private @Nullable Tab requestedTab = null;

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

    private boolean disposed = false;

    @SuppressWarnings("NullAway.Init")
    private JDialog dialog;

    /**
     * Answers false rather than throwing for a dialog that was never built.
     */
    public boolean isShowing() {
        //noinspection ConstantValue — deferred-init field, genuinely null before first show
        return dialog != null && dialog.isShowing();
    }

    public void toFront() {
        if (isShowing()) {
            dialog.toFront();
            dialog.requestFocus();
        }
    }

    /**
     * Every binding the dialog declares belongs to these, and {@link #dispose()} cancels them.
     */
    protected final Bindings bindings() {
        return bindings;
    }

    /**
     * Call it while building, not in response to an edit: a condition is a value that answers
     * over and over, not an answer.
     */
    protected final void requireValid(ObservableValue<Boolean> condition) {
        var conditions = new ArrayList<>(validityConditions.get());
        conditions.add(condition);
        validityConditions.set(List.copyOf(conditions));
    }

    /**
     * Idempotent: {@code setVisible(false)} is reachable from both the OK/Cancel path and
     * {@code windowClosing}.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        disposed = true;
        bindings.dispose();
    }

    protected BaseDialog(MainFrame mainFrame, String title) {
        this(mainFrame, title, Modality.MODAL);
    }

    protected BaseDialog(MainFrame mainFrame, String title, Modality modality) {
        this.mainFrame = mainFrame;
        dialogTitle = title;
        this.modality = modality;
    }

    public static boolean isAnyDialogVisible() {
        return visibleDialogCount > 0;
    }

    private static void incrementVisibleDialogCount() {
        visibleDialogCount++;

        if (visibleDialogCount == 1) {
            MessageCenter.post(new DialogVisibilityDidChangeNotification(true));
        }
    }

    private static void decrementVisibleDialogCount() {
        visibleDialogCount--;

        if (visibleDialogCount == 0) {
            MessageCenter.post(new DialogVisibilityDidChangeNotification(false));
        }
    }

    protected List<Tab> getTabs() {
        return tabs;
    }

    protected Container getButtonPanelContainer() {
        return hasSidebar ? tabContentArea : contentPanel;
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

    protected void registerTab(Tab tab) {
        tabs.add(tab);
    }

    protected void addTab(Tab tab) {
        tabCards.add(tab, String.valueOf(tabs.size()));
        tabListModel.addElement(tab.getTitle());
        registerTab(tab);
    }

    private record ShowRequest(int tabIndex, @Nullable JComponent focus) {}

    /**
     * {@code focus} holds the caret, or the platform's default first focusable control when it
     * is null. A tab that always leads with the same control overrides
     * {@link Tab#getInitialFocus} instead.
     */
    protected void showTab(Tab tab, @Nullable JComponent focus) {
        requestedTab = tab;
        requestedFocus = focus;
        setVisible(true);
    }

    /**
     * Read at the very start of the show, before anything that can abort it, or a cancelled show
     * would leave the request to survive into an unrelated later open.
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
     * Null when none names one. Without a sidebar every tab is on screen, so the first that
     * names a control wins.
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
     * Posted because {@link JComponent#requestFocusInWindow} is a no-op while the window is not
     * showing, and a modal {@code setVisible(true)} blocks.
     */
    private static void requestFocusLater(@Nullable JComponent component) {
        if (component != null) {
            SwingUtilities.invokeLater(component::requestFocusInWindow);
        }
    }

    /**
     * A no-op {@code setSelectedIndex} fires no event, so {@link #selectTab} is driven directly.
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
     * NOTE: selectTab(0) at initial show fires tabWillHide on the non-selected tabs (unified show+hide path).
     *   Hides are idempotent for the current tabs.
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
     * Takes a pre-built label so the caller can column-align it before it is laid out.
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

    /**
     * For a field taller than one row, use {@link #addScrolledFieldRow} instead.
     *
     * @param label the row's label, already aligned or width-matched by the caller
     */
    public static void addFilledFieldRow(JComponent container, JLabel label, JComponent field) {
        label.setLabelFor(field);
        container.add(filledRow(label, field));
    }

    /**
     * A pass that also writes the label's geometry must run after this.
     *
     * @param label the row's label, already aligned or width-matched by the caller
     */
    public static void addScrolledFieldRow(JComponent container, JLabel label, JComponent field) {
        var scroll = new JScrollPane(field);

        // BorderLayout.WEST stretches a label to the full height of the field beside it,
        // which would center the text against the middle of a multi-row field. Top-align
        // it and nudge it down onto the first text line, past the scroll pane's border and
        // the field's own top inset.
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setBorder(BorderFactory.createEmptyBorder(
            scroll.getInsets().top + field.getInsets().top, 0, 0, 0
        ));

        // The field, not the scroll pane: a viewport takes no focus and no caret.
        label.setLabelFor(field);
        container.add(filledRow(label, scroll));
    }

    private static JPanel filledRow(JLabel label, JComponent filler) {
        var horizontalGap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_HORIZONTAL_GAP);
        var row = new JPanel(new BorderLayout(horizontalGap, 0));

        // Carry the leading gap on the row rather than the label so the label's width
        // stays exactly the shared column width, matching the leading inset
        // addLabeledField's FlowLayout gives a neighbouring row.
        row.setBorder(BorderFactory.createEmptyBorder(0, horizontalGap, 0, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(label, BorderLayout.WEST);
        row.add(filler, BorderLayout.CENTER);

        return row;
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

    public static void addSectionSeparator(JComponent container) {
        container.add(Box.createVerticalStrut(
            FlatLafProps.getInt(FlatLafKey.DIALOG_SECTION_GAP)
        ));
    }

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

    /**
     * <strong>An instance is not reusable after a close.</strong> {@code setVisible(false)}
     * ends this dialog's life, as does a show abandoned before the window reached the screen.
     */
    public void setVisible(boolean visible) {
        if (visible) {
            // Consumed before anything can abort the show, so a request that never reached
            // the screen cannot survive into a later open.
            var showRequest = consumeShowRequest();

            // Owned even when modeless. An unowned window carries no menu bar, so the menus
            // go away for as long as it is frontmost. The cost of ownership is that AWT keeps
            // an owned window above its owner for as long as it exists, so a modeless dialog
            // cannot be pushed behind the score — that is the lesser of the two.
            dialog = new JDialog(mainFrame, dialogTitle, modality.isModal());
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
                // The window never reached the screen, so the close path that would
                // normally retire this instance is never reached — retire it here.
                dialog.dispose();
                dispose();
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

            var geometry = DialogGeometryStore.load(getClass());

            if (geometry != null) {
                applyGeometry(geometry);
            } else {
                UIUtils.positionDialog(dialog, mainFrame);
            }

            // Force layout commitment before showing to prevent flash on first display
            dialog.validate();

            incrementVisibleDialogCount();

            requestFocusLater(initialFocus);

            try {
                dialog.setVisible(true);
            } catch (Exception e) {
                // Dispose before decrementing, for the same reason as the close path below:
                // decrementVisibleDialogCount() posts DialogVisibilityDidChangeNotification(false),
                // which handlers read as "the dialog window is gone".
                dialog.dispose();
                decrementVisibleDialogCount();
                dispose();
                throw e;
            }
        } else {
            try {
                for (var tab : tabs) {
                    tab.tabWillHide();
                }

                var location = dialog.getLocation();
                var size = isResizable() ? dialog.getSize() : null;
                DialogGeometryStore.save(getClass(), new DialogGeometry(location, size));
            } finally {
                // Dispose first: decrementVisibleDialogCount() posts DialogVisibilityDidChangeNotification(false),
                // which handlers rely on to mean the dialog is actually gone (e.g. Component.getMousePosition()
                // on a component the dialog was covering only returns a real value once disposal has removed
                // the dialog window) — posting before dispose() would have them react while it is still showing.
                dialog.dispose();
                decrementVisibleDialogCount();

                // Last, so a handler reacting to DialogVisibilityDidChangeNotification(false)
                // sees a fully torn-down window rather than one mid-teardown.
                dispose();
            }
        }
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
     * {@code null} if there is no default button.
     */
    protected @Nullable JButton getDefaultButton() {
        return null;
    }

    protected boolean isResizable() {
        return false;
    }

    protected int getExtraWidth() {
        return 0;
    }

    protected int getExtraHeight() {
        return 0;
    }

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
     * {@code null} if the dialog is not currently visible.
     */
    public @Nullable JDialog getWindow() {
        return dialog;
    }

    protected void pack() {
        dialog.pack();
    }

    /**
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
     * For a tab whose content changes height at runtime, which the size pinned at show time
     * would otherwise starve.
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
     * <strong>Window parenting only.</strong> It is not a route to the score; see
     * {@code .claude/guides/dialogs.md}.
     */
    protected MainFrame getMainFrame() {
        return mainFrame;
    }

    /**
     * Call {@code super.getData()} to run tab iteration; subclasses with no registered tabs may
     * override without it.
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
     * <strong>A tab populates and displays; it does not commit and it does not validate.</strong>
     * A rule that spans tabs cannot be checked from inside one, and a commit split across tabs is
     * several undo steps for one edit. See {@link StandardDialog}.
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

        protected final Bindings bindings() {
            return BaseDialog.this.bindings;
        }

        protected final void requireValid(ObservableValue<Boolean> condition) {
            BaseDialog.this.requireValid(condition);
        }

        protected final MainFrame getMainFrame() {
            return BaseDialog.this.getMainFrame();
        }

        protected final void repackToContent() {
            BaseDialog.this.repackToContent();
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

        protected abstract void initContents();

        @SuppressWarnings("SameReturnValue")
        protected boolean getData() {
            return true;
        }

        protected void tabWillShow() {}

        /**
         * Null leaves the platform's default first focusable control in charge. A control passed
         * to {@link BaseDialog#showTab} outranks this.
         */
        @Nullable
        protected JComponent getInitialFocus() {
            return null;
        }

        protected void tabWillHide() {}

        @Override
        public Component add(Component comp) {
            add(comp, constraints);
            return comp;
        }

        /**
         * At most one expanding component may be added per tab.
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

}
