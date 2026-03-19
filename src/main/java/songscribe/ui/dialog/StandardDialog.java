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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.intellij.lang.annotations.MagicConstant;

import songscribe.Strings;
import songscribe.music.Composition;
import songscribe.ui.Dialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.util.UIUtils;

/**
 * The base class for dialogs that provide an OK, Apply, and Cancel button
 * and have associated data that is set by the dialog.
 */
public abstract class StandardDialog {

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
    protected JPanel buttonPanel = new JPanel(
        new FlowLayout(FlowLayout.RIGHT, 9, 0)
    );
    protected final JButton okButton;
    protected final JButton applyButton;
    protected final JButton cancelButton;
    private @Nullable Point savedLocation = null;
    private JDialog dialog;

    protected StandardDialog(String title) {
        this(title, true);
    }

    @SuppressWarnings("NullAway.Init")
    protected StandardDialog(String title, boolean isModal) {
        this.mainFrame = MainFrame.getInstance();
        var score = mainFrame.getScore();
        dialogTitle = title;
        this.isModal = isModal;
        okButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_OK));
        okButton.addActionListener(_ -> {
            setData();

            if (score != null) {
                score.repaint();
            }

            setVisible(false);
        });

        applyButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_APPLY));
        applyButton.addActionListener(_ -> {
            setData();

            if (score != null) {
                score.repaint();
            }
        });

        cancelButton = new JButton(Strings.get(Strings.DIALOG_BUTTON_CANCEL));
        cancelButton.addActionListener(_ -> setVisible(false));

        buttonPanel.setBorder(BorderFactory.createEmptyBorder(18, 0, 18, 6));
        buttonPanel.add(cancelButton);
        buttonPanel.add(applyButton);
        buttonPanel.add(okButton);
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
            dialog.getRootPane().setDefaultButton(okButton);

            try {
                getData();
                dialog.pack();
                dialog.setMinimumSize(dialog.getPreferredSize());

                if (savedLocation == null) {
                    Dialogs.positionDialog(dialog, mainFrame);
                } else {
                    dialog.setLocation(savedLocation);
                }

                dialog.setVisible(true);
            } catch (DoNotShowException e) {
                // Ignore
            }
        } else {
            savedLocation = new Point(dialog.getLocation());
            dialog.dispose();
        }
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

    protected abstract void getData() throws DoNotShowException;

    protected abstract void setData();

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
                BorderFactory.createEmptyBorder(topPadding, PADDING, 0, PADDING)
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

        private static final Insets INSETS = new Insets(31, 16, 16, 16);

        public StandardTitledBorder(String title) {
            super(title);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return INSETS;
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = INSETS.left;
            insets.right = INSETS.right;
            insets.top = INSETS.top;
            insets.bottom = INSETS.bottom;
            return insets;
        }
    }
}
