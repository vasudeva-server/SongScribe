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

import java.util.Dictionary;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.intellij.lang.annotations.MagicConstant;

import songscribe.music.Composition;
import songscribe.ui.Dialogs;
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

    // The standard horizontal and vertical padding used in the dialog
    protected static final Dimension HORIZONTAL_SPACER = new Dimension(5, 5);
    protected static final Dimension VERTICAL_SPACER = new Dimension(15, 15);

    private final MainFrame mainFrame;
    protected final String dialogTitle;
    protected final boolean isModal;
    protected final JPanel contentPanel = new JPanel(new BorderLayout());
    private @Nullable Point savedLocation = null;
    private JDialog dialog;

    protected BaseDialog(String title) {
        this(title, true);
    }

    @SuppressWarnings("NullAway.Init")
    protected BaseDialog(String title, boolean isModal) {
        this.mainFrame = MainFrame.getInstance();
        dialogTitle = title;
        this.isModal = isModal;
    }

    protected JTabbedPane createTabbedPane() {
        var pane = new JTabbedPane();

        // Add a little padding at the top, above the tabs
        pane.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
        return pane;
    }

    protected static void addLabeledField(
        JComponent container,
        String labelText,
        JComponent field,
        LabelPosition labelPosition
    ) {
        if (labelPosition == LabelPosition.LEFT) {
            var panel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, HORIZONTAL_SPACER.width, 0)
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

    protected static void configureSlider(
        JSlider slider,
        int majorTickSpacing,
        Dictionary<Integer, JLabel> labels
    ) {
        slider.setMajorTickSpacing(majorTickSpacing);
        slider.setSnapToTicks(true);
        slider.setPaintLabels(true);
        slider.setPaintTicks(true);
        slider.setLabelTable(labels);
    }

    public void setVisible(boolean visible) {
        if (visible) {
            dialog = new JDialog(mainFrame, dialogTitle, isModal);
            dialog.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        setVisible(false);
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

            dialog.pack();

            var minSize = dialog.getPreferredSize();
            minSize.width += getExtraWidth();
            dialog.setSize(minSize);
            dialog.setMinimumSize(minSize);

            if (savedLocation == null) {
                Dialogs.positionDialog(dialog, mainFrame);
            } else {
                dialog.setLocation(savedLocation);
            }

            dialog.setVisible(true);
        } else {
            savedLocation = new Point(dialog.getLocation());
            dialog.dispose();
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
     * Called when the dialog is about to be shown. Subclasses should
     * populate their controls from the current data/state.
     *
     * @return true to proceed with showing the dialog, false to cancel
     */
    protected abstract boolean getData();

    protected static class Tab extends JPanel {

        private static final int PADDING = 20;

        protected final GridBagConstraints constraints =
            new GridBagConstraints();

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

            initContents();

            // Add glue at the bottom that will force the contents to the top
            constraints.gridx = 0;
            constraints.weightx = 1.0;
            constraints.weighty = 1.0;
            constraints.fill = GridBagConstraints.BOTH;
            add(Box.createGlue(), constraints);
        }

        /**
         * This should be overridden by subclasses to add components to the tab.
         */
        protected void initContents() {}

        @Override
        public Component add(Component comp) {
            add(comp, constraints);
            return comp;
        }

        public void addSeparator() {
            add(Box.createVerticalStrut(VERTICAL_SPACER.height), constraints);
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
                add(Box.createVerticalStrut(VERTICAL_SPACER.height));
            } else {
                add(Box.createHorizontalStrut(HORIZONTAL_SPACER.width));
            }
        }
    }

    protected static class StandardTitledBorder extends TitledBorder {

        private static final Insets DEFAULT_INSETS = new Insets(31, 16, 16, 16);

        private Insets insets = DEFAULT_INSETS;

        public StandardTitledBorder(String title) {
            super(title);
        }

        public void setInsets(Insets insets) {
            this.insets = insets;
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
    }
}
