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
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.intellij.lang.annotations.MagicConstant;

import songscribe.message.MessageCenter;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.music.Composition;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.util.UIUtils;

/**
 * The base class for all application dialogs, providing common layout
 * helpers, lifecycle management, and inner classes for tabbed content.
 */
public abstract class BaseDialog {

    // Used by addLabeledField to determine where to place the label
    protected enum LabelPosition {
        LEFT,
        TOP,
    }

    // The standard horizontal and vertical component spacing used in the dialog
    protected static final int HORIZONTAL_MARGIN = 5;
    protected static final int VERTICAL_MARGIN = 5;

    // The standard margin around titled sections
    protected static final int SECTION_MARGIN = 15;

    private static int visibleBlockingDialogCount = 0;

    private final MainFrame mainFrame;
    protected final String dialogTitle;
    protected final boolean isModal;
    private final DialogCategory category;
    protected final JPanel contentPanel = new JPanel(new BorderLayout());
    private final List<Tab> tabs = new ArrayList<>();
    private @Nullable JTabbedPane tabbedPane;
    private @Nullable Point savedLocation;
    private @Nullable Component savedFocusOwner;
    private JDialog dialog;

    protected BaseDialog(String title) {
        this(title, true);
    }

    protected BaseDialog(String title, boolean isModal) {
        this(title, isModal, DialogCategory.OPERATIONAL);
    }

    @SuppressWarnings("NullAway.Init")
    protected BaseDialog(String title, boolean isModal, DialogCategory category) {
        this.mainFrame = MainFrame.getInstance();
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

    private void incrementBlockingCount() {
        visibleBlockingDialogCount++;

        if (visibleBlockingDialogCount == 1) {
            MessageCenter.post(new DialogVisibilityDidChangeNotification(true));
        }
    }

    private void decrementBlockingCount() {
        visibleBlockingDialogCount--;

        if (visibleBlockingDialogCount == 0) {
            MessageCenter.post(new DialogVisibilityDidChangeNotification(false));
        }
    }

    protected List<Tab> getTabs() {
        return tabs;
    }

    protected JTabbedPane createTabbedPane() {
        tabbedPane = new JTabbedPane();
        var pane = tabbedPane;

        // Add a little padding at the top, above the tabs
        pane.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));

        pane.addChangeListener(_ -> {
            var selectedComponent = pane.getSelectedComponent();

            for (var tab : tabs) {
                if (tab == selectedComponent) {
                    tab.tabWillShow();
                } else {
                    tab.tabWillHide();
                }
            }
        });

        return pane;
    }

    /**
     * Registers a tab so the base class can call its lifecycle methods.
     */
    protected void registerTab(Tab tab) {
        tabs.add(tab);
    }

    /**
     * Convenience method that adds a tab to a {@link JTabbedPane} and
     * registers it for lifecycle callbacks.
     */
    protected void addTab(JTabbedPane pane, String title, Tab tab) {
        pane.addTab(title, tab);
        registerTab(tab);
    }

    protected static void addLabeledField(
        JComponent container,
        String labelText,
        JComponent field,
        LabelPosition labelPosition
    ) {
        if (labelPosition == LabelPosition.LEFT) {
            var panel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, HORIZONTAL_MARGIN, 0)
            );
            panel.setBorder(BorderFactory.createEmptyBorder());
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            var label = new JLabel(labelText);
            label.setLabelFor(field);
            panel.add(label);
            panel.add(field);
            container.add(panel);
        } else {
            var label = new JLabel(labelText);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Indent the title a bit to line up with the input field
            label.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
            container.add(label);
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            container.add(field);
        }
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

    public void setVisible(boolean visible) {
        if (visible) {
            dialog = new JDialog(mainFrame, dialogTitle, isModal);
            dialog.setResizable(isResizable());
            dialog.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        setVisible(false);
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

            if (tabbedPane != null) {
                var selectedTab = tabbedPane.getSelectedComponent();

                for (var tab : tabs) {
                    if (tab == selectedTab) {
                        tab.tabWillShow();
                    }
                }
            } else {
                for (var tab : tabs) {
                    tab.tabWillShow();
                }
            }

            dialog.pack();

            var minSize = dialog.getPreferredSize();
            minSize.width += getExtraWidth();
            dialog.setSize(minSize);
            dialog.setMinimumSize(minSize);

            if (savedLocation == null) {
                OptionDialogs.positionDialog(dialog, mainFrame);
            } else {
                dialog.setLocation(savedLocation);
            }

            if (category.isBlocking()) {
                incrementBlockingCount();
            }

            try {
                dialog.setVisible(true);
            } catch (Exception e) {
                if (category.isBlocking()) {
                    decrementBlockingCount();
                }

                dialog.dispose();
                throw e;
            }
        } else {
            if (dialog == null) {
                return;
            }

            try {
                for (var tab : tabs) {
                    tab.tabWillHide();
                }

                savedLocation = dialog.getLocation();
            } finally {
                if (category.isBlocking()) {
                    decrementBlockingCount();
                }

                dialog.dispose();
            }
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

    protected void pack() {
        dialog.pack();
    }

    protected MainFrame getMainFrame() {
        return mainFrame;
    }

    protected @Nullable Score getScore() {
        return mainFrame.getScore();
    }

    protected Composition getComposition() {
        return Objects.requireNonNull(mainFrame.getScore()).getComposition();
    }

    public static Insets getStandardStackedLabelInsets() {
        return new Insets(0, 4, 2, 0);
    }

    /**
     * Called when the dialog is about to be shown. Populates controls
     * by iterating registered tabs. Subclasses may override to add
     * dialog-level population logic (call {@code super.getData()} to
     * run tab iteration).
     *
     * @return true to proceed with showing the dialog, false to cancel
     */
    protected boolean getData() {
        if (tabbedPane != null) {
            tabbedPane.setSelectedIndex(0);
        }

        for (var tab : tabs) {
            if (!tab.getData()) {
                return false;
            }
        }

        return true;
    }

    protected static class Tab extends JPanel {

        private static final int PADDING = 20;

        protected final GridBagConstraints constraints =
            new GridBagConstraints();

        private boolean hasFillItem = false;

        protected Tab() {
            this(PADDING);
        }

        protected Tab(int topPadding) {
            setLayout(new GridBagLayout());

            // Add inner padding to the panel
            setBorder(
                BorderFactory.createEmptyBorder(topPadding, PADDING, PADDING, PADDING)
            );

            // Items in the tab should be top/left-aligned, grow horizontally, and not vertically
            constraints.gridx = 0;
            constraints.gridy = GridBagConstraints.RELATIVE;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            constraints.weighty = 0;
        }

        protected final void build() {
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
         * This should be overridden by subclasses to add components to the tab.
         */
        protected void initContents() {}

        /**
         * Populate controls from the model. Called when the dialog is
         * about to be shown.
         *
         * @return true to proceed, false to cancel showing the dialog
         */
        protected boolean getData() {
            return true;
        }

        /**
         * Write control values back to the model. Called when the user
         * clicks OK or Apply.
         */
        protected void setData() {}

        /**
         * Returns true if the tab's current field values are valid.
         * Called before {@link #setData()} to block commits on invalid input.
         */
        protected boolean isValidData() {
            return true;
        }

        /**
         * Called after {@link #getData()} but before the dialog becomes
         * visible. Use for lazy loading, focus requests, etc.
         */
        protected void tabWillShow() {}

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

        public void addSeparator() {
            add(Box.createVerticalStrut(SECTION_MARGIN), constraints);
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

        public void addSeparator() {
            var layout = (BoxLayout) getLayout();

            if (layout.getAxis() == BoxLayout.Y_AXIS) {
                add(Box.createVerticalStrut(VERTICAL_MARGIN));
            } else {
                add(Box.createHorizontalStrut(HORIZONTAL_MARGIN));
            }
        }
    }

    protected static class StandardTitledBorder extends TitledBorder {

        // Matches the package-private TitledBorder.TEXT_INSET_H constant
        private static final int TEXT_INSET_H = 5;

        private static final Insets DEFAULT_INSETS = new Insets(31, 16, 16, 16);

        private Insets insets = DEFAULT_INSETS;

        public StandardTitledBorder(String title) {
            super(title);
        }

        public void setInsets(Insets insets) {
            this.insets = insets;
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

            setBorder(new LeftAdjustedBorder(originalBorder, textInset - TEXT_INSET_H));

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
