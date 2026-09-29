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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.formdev.flatlaf.ui.FlatTreeUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ChangeReport;
import songscribe.dom.ElementLocationRange;
import songscribe.dom.ReportedChange;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.MainFrame;

/**
 * Lists the changes the song made on its own, from a load or from an edit, with a single OK
 * that dismisses it.
 *
 * <p>It shows and never edits, so it is {@link DialogCategory#INFORMATIONAL} and gathers
 * nothing: its output type is {@link Void}, and OK closes it without a commit.
 *
 * <p>It knows nothing about the kinds of change. It groups a {@link ChangeReport}'s entries
 * under their headings and shows each entry's finished text at its location; the code that
 * made a change wrote the words.
 */
public final class ReportDialog extends StandardDialog<ChangeReport, @Nullable Void> {

    /** What a report follows, which decides the title and the sentence above the list. */
    public enum Occasion {
        EDIT(Strings.DIALOG_REPORT_EDIT_TITLE, Strings.DIALOG_REPORT_EDIT_MESSAGE),
        LOAD(Strings.DIALOG_REPORT_LOAD_TITLE, Strings.DIALOG_REPORT_LOAD_MESSAGE);

        private final String titleKey;
        private final String messageKey;

        /**
         * @param titleKey   the {@code Strings} key of the dialog title, read as plain text
         * @param messageKey the {@code Strings} key of the sentence above the list, read as plain text
         */
        Occasion(String titleKey, String messageKey) {
            this.titleKey = titleKey;
            this.messageKey = messageKey;
        }
    }

    /** Starting size of the scrolling tree, in pixels; the dialog is resizable. */
    private static final int TREE_WIDTH = 700;
    private static final int TREE_HEIGHT = 500;

    /** Extra space below a leaf row, in pixels. */
    private static final int ROW_PADDING = 10;

    /** The root is hidden, so the rows the user reads as top level are one level below it. */
    private static final int TOP_LEVEL = 1;

    private final JTree tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode()));

    /**
     * @param mainFrame the application window, for parenting
     * @param occasion  what the report follows, for the title and the sentence above the list
     * @param ops       the operations this dialog reads the report from
     */
    ReportDialog(MainFrame mainFrame, Occasion occasion, DialogOps<ChangeReport, ? super @Nullable Void> ops) {
        super(mainFrame, Strings.get(occasion.titleKey), ops, DialogCategory.INFORMATIONAL);

        tree.setUI(new SeparatorTreeUI());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new CellRenderer());

        var scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(TREE_WIDTH, TREE_HEIGHT));

        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);
        var content = new JPanel(new BorderLayout(0, gap));
        content.add(new JLabel(Strings.get(occasion.messageKey)), BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(content, BorderLayout.CENTER);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only OK: there is nothing to cancel.
     */
    @Override
    protected Object modifyButtonPanel() {
        buttonPanel.remove(cancelButton);
        return super.modifyButtonPanel();
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The change list as a tree: one group per heading, one leaf per change under it, so a
     * report with dozens of changes opens as a short summary the user chooses to unfold.
     *
     * @param values the changes to list
     */
    @Override
    protected void populate(ChangeReport values) {
        var root = new DefaultMutableTreeNode();

        for (var entry : values.byHeading().entrySet()) {
            var changes = entry.getValue();
            var group = new DefaultMutableTreeNode(Strings.get(entry.getKey(), changes.size()));

            for (var change : changes) {
                group.add(new DefaultMutableTreeNode(leafText(change)));
            }

            root.add(group);
        }

        tree.setModel(new DefaultTreeModel(root));

        for (var i = 0; i < root.getChildCount(); i++) {
            var group = (DefaultMutableTreeNode) root.getChildAt(i);
            tree.expandPath(new TreePath(group.getPath()));
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code null}: the dialog has no controls that say anything
     */
    @Override
    protected @Nullable Void gather() {
        return null;
    }

    private static String leafText(ReportedChange change) {
        var where = change.where();

        if (where == null) {
            return change.description();
        }

        return Strings.get(Strings.DIALOG_REPORT_CHANGE, location(where), change.description());
    }

    /** The indices are stored 0-based and shown 1-based, as the user counts them. */
    private static String location(ElementLocationRange range) {
        var start = range.start();
        var end = range.end();

        if (start.lineIndex() != end.lineIndex()) {
            return Strings.get(
                Strings.DIALOG_REPORT_LOCATION_LINES,
                start.lineIndex() + 1,
                start.elementIndex() + 1,
                end.lineIndex() + 1,
                end.elementIndex() + 1);
        }

        if (start.elementIndex() == end.elementIndex()) {
            return Strings.get(
                Strings.DIALOG_REPORT_LOCATION_ELEMENT, start.lineIndex() + 1, start.elementIndex() + 1);
        }

        return Strings.get(
            Strings.DIALOG_REPORT_LOCATION_ELEMENTS,
            start.lineIndex() + 1,
            start.elementIndex() + 1,
            end.elementIndex() + 1);
    }

    /**
     * Gives the leaf rows a little air below, so a leaf sits directly under its group heading.
     *
     * <p>The padding is added to the cell's preferred height rather than through a border:
     * the theme installs its own border on the renderer before every cell, and it only
     * keeps doing so while that border is the one still in place. The label aligns its
     * text to the top, so the extra height lands below the text.
     */
    private static final class CellRenderer extends DefaultTreeCellRenderer {

        private boolean isLeaf;

        CellRenderer() {
            setVerticalAlignment(SwingConstants.TOP);
        }

        @Override
        public Component getTreeCellRendererComponent(
            JTree tree,
            Object value,
            boolean sel,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
        ) {
            isLeaf = value instanceof DefaultMutableTreeNode node && node.getLevel() > TOP_LEVEL;

            var component = super.getTreeCellRendererComponent(
                tree, value, sel, expanded, leaf, row, hasFocus);

            // The renderer is reused for every row, so the group rows' weight has to be
            // cleared again for the leaf rows rather than only set.
            if (component instanceof JComponent c) {
                var font = (isLeaf
                    ? UIManager.getFont("Tree.font")
                    : UIManager.getFont("large.font"));
                c.setFont(FlatUIUtils.nonUIResource(font));
            }

            return component;
        }

        @Override
        public Dimension getPreferredSize() {
            var size = super.getPreferredSize();

            if (isLeaf) {
                size.height += ROW_PADDING;
            }

            return size;
        }
    }


    /**
     * Draws a separator above every top-level row except the first.
     *
     * <p>It extends the theme's tree UI rather than {@link BasicTreeUI}: the theme paints
     * the selection itself in {@code paintRow} and strips the default node icons off the
     * renderer, so a plain {@code BasicTreeUI} loses both.
     */
    private static final class SeparatorTreeUI extends FlatTreeUI {

        private static final int TOP_LEVEL_PATH_COUNT = 2;

        @Override
        public void paint(Graphics g, JComponent c) {
            super.paint(g, c);

            var g2 = (Graphics2D) g.create();

            try {
                var color = UIManager.getColor("Separator.foreground");

                if (color == null) {
                    return;
                }

                g2.setColor(color);

                var clip = g.getClipBounds();

                // The first top-level row has nothing above it to separate from.
                for (var row = 1; row < tree.getRowCount(); row++) {
                    var path = tree.getPathForRow(row);

                    // The top level rows are children of the hidden root node, so they
                    // have a path count of 2.
                    if (path.getPathCount() == TOP_LEVEL_PATH_COUNT) {
                        var bounds = tree.getPathBounds(path);

                        if (bounds != null) {
                            g2.drawLine(clip.x, bounds.y, clip.x + clip.width - 1, bounds.y);
                        }
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
