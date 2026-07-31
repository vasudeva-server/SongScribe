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

import com.formdev.flatlaf.ui.FlatTreeUI;
import com.formdev.flatlaf.ui.FlatUIUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.TupletLoadPass;
import songscribe.dom.TupletValidator;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.util.UIUtils;

/**
 * A palette-style window listing what the loader changed on its way in.
 *
 * <p>It is deliberately not a {@link BaseDialog}: this is a non-modal utility window the
 * user can leave open beside the score while walking through the changes, so it must not
 * take part in the blocking-dialog counter, and it carries no OK/Cancel lifecycle.
 *
 * <p>Both load routes reach this window. Dropping a tuplet removes musical content the user
 * put there, so it is reported whatever the file's format was; saving the corrected version
 * stays the user's decision, which is why the loader never writes to the file it opened.
 */
public final class MigrationWindow extends JDialog {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationWindow.class);

    /** Starting size of the scrolling tree, in pixels; the window is resizable. */
    private static final int TREE_WIDTH = 700;
    private static final int TREE_HEIGHT = 500;

    /** Extra space above and below a top-level row, in pixels. */
    private static final int ROW_PADDING = 10;

    /** The root is hidden, so the rows the user reads as top level are one level below it. */
    private static final int TOP_LEVEL = 1;

    private MigrationWindow(
        @Nullable Window owner, TupletLoadPass.Report report, boolean accidentalsConverted
    ) {
        super(owner, Strings.get(Strings.DIALOG_MIGRATION_TITLE));

        // A utility window floats beside the score rather than joining the window
        // cycle, which is what makes leaving it open while editing bearable.
        setType(Window.Type.UTILITY);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        var gap = FlatLafProps.getInt(FlatLafKey.DIALOG_COMPONENT_VERTICAL_EXTRA_GAP);
        var content = new JPanel(new BorderLayout(0, gap));
        content.setBorder(UIUtils.spacingBorder(FlatLafKey.DIALOG_STD_PADDING));
        content.add(new JLabel(Strings.get(Strings.DIALOG_MIGRATION_MESSAGE)), BorderLayout.NORTH);

        var tree = buildTree(report, accidentalsConverted);
        var scrollPane = new JScrollPane(tree);
        scrollPane.setPreferredSize(new Dimension(TREE_WIDTH, TREE_HEIGHT));
        content.add(scrollPane, BorderLayout.CENTER);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the window for {@code report}, unless dialogs are suppressed.
     *
     * @param owner                the window to center on, or null to center on the screen
     * @param report               what the tuplet pass did
     * @param accidentalsConverted whether retired accidentals were converted as well
     */
    public static void show(
        @Nullable Window owner, TupletLoadPass.Report report, boolean accidentalsConverted
    ) {
        if (OptionDialogs.isSuppressed()) {
            LOG.info("Migration window suppressed: {} tuplets dropped, {} updated, accidentals converted: {}",
                report.dropped(), report.migrated(), accidentalsConverted);

            return;
        }

        new MigrationWindow(owner, report, accidentalsConverted).setVisible(true);
    }

    /**
     * The change list as a tree: one group per kind of change, one leaf per change under
     * it, so a file with dozens of them opens as a short summary the user chooses to
     * unfold. Groups with nothing in them are left out rather than shown empty.
     */
    private static JTree buildTree(TupletLoadPass.Report report, boolean accidentalsConverted) {
        var root = new DefaultMutableTreeNode();
        var groups = new ArrayList<DefaultMutableTreeNode>();
        var removed = changesOfKind(report, TupletLoadPass.Change.Removed.class);
        var updated = changesOfKind(report, TupletLoadPass.Change.Updated.class);

        if (!removed.isEmpty()) {
            groups.add(addGroup(root, Strings.get(
                Strings.DIALOG_MIGRATION_TUPLETS_REMOVED, removed.size()), removed));
        }

        if (!updated.isEmpty()) {
            groups.add(addGroup(root, Strings.get(
                Strings.DIALOG_MIGRATION_TUPLETS_UPDATED, updated.size()), updated));
        }

        if (accidentalsConverted) {
            // A leaf: the conversion is reported as a whole, with nothing per occurrence.
            root.add(new DefaultMutableTreeNode(Strings.get(Strings.DIALOG_MIGRATION_ACCIDENTALS)));
        }

        var tree = new JTree(new DefaultTreeModel(root));
        tree.setUI(new SeparatorTreeUI());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new CellRenderer());

        for (var group : groups) {
            tree.expandPath(new TreePath(group.getPath()));
        }

        return tree;
    }

    private static DefaultMutableTreeNode addGroup(
        DefaultMutableTreeNode root, String title, List<? extends TupletLoadPass.Change> changes
    ) {
        var group = new DefaultMutableTreeNode(title);

        for (var change : changes) {
            group.add(new DefaultMutableTreeNode(describe(change)));
        }

        root.add(group);

        return group;
    }

    private static <T extends TupletLoadPass.Change> List<T> changesOfKind(
        TupletLoadPass.Report report, Class<T> kind
    ) {
        return report.changes().stream().filter(kind::isInstance).map(kind::cast).toList();
    }

    /** The indices are stored 0-based and shown 1-based, as the user counts them. */
    private static String describe(TupletLoadPass.Change change) {
        return Strings.get(
            Strings.DIALOG_MIGRATION_CHANGE,
            change.lineIndex() + 1,
            change.beginIndex() + 1,
            change.endIndex() + 1,
            detail(change));
    }

    private static String detail(TupletLoadPass.Change change) {
        return switch (change) {
            case TupletLoadPass.Change.Removed removed -> reasonText(removed.reason());
            case TupletLoadPass.Change.Updated updated -> Strings.get(
                Strings.DIALOG_MIGRATION_UPDATED_DETAIL, updated.grade(), updated.normalNotes());
        };
    }

    private static String reasonText(TupletValidator.@Nullable Reason reason) {
        if (reason == null) {
            return Strings.get(Strings.DIALOG_MIGRATION_REASON_UNKNOWN);
        }

        return Strings.get(switch (reason) {
            case EMPTY_SPAN -> Strings.DIALOG_MIGRATION_REASON_EMPTY_SPAN;
            case NOT_NOTATABLE -> Strings.DIALOG_MIGRATION_REASON_NOT_NOTATABLE;
            case BAD_RATIO -> Strings.DIALOG_MIGRATION_REASON_BAD_RATIO;
            case NO_CONVENTIONAL_SPAN -> Strings.DIALOG_MIGRATION_REASON_NO_CONVENTIONAL_SPAN;
            case POWER_OF_TWO_RATIO -> Strings.DIALOG_MIGRATION_REASON_POWER_OF_TWO;
            case FERMATA -> Strings.DIALOG_MIGRATION_REASON_FERMATA;
            case BEAT_BARRIER -> Strings.DIALOG_MIGRATION_REASON_BEAT_BARRIER;
            case STRUCTURAL_BOUNDARY -> Strings.DIALOG_MIGRATION_REASON_STRUCTURAL_BOUNDARY;
        });
    }

    /**
     * Gives the leaf rows a little air above and below.
     *
     * <p>The padding is added to the cell's preferred height rather than through a border:
     * the theme installs its own border on the renderer before every cell, and it only
     * keeps doing so while that border is the one still in place. The label centers its
     * text vertically, so the extra height lands as equal space above and below.
     */
    private static final class CellRenderer extends DefaultTreeCellRenderer {

        private boolean isLeaf;

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
                size.height += 2 * ROW_PADDING;
            }

            return size;
        }
    }


    /**
     * Draws a separator under every leaf row except the last one in its group.
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

                for (var row = 0; row < tree.getRowCount(); row++) {
                    var path = tree.getPathForRow(row);

                    // Only check leaf rows, not top level. Note that the top level rows
                    // are actually children of the root node, so they have a path count of 2.
                    if (path.getPathCount() > TOP_LEVEL_PATH_COUNT && !isLastLeaf(path)) {
                        var bounds = tree.getPathBounds(path);

                        if (bounds != null) {
                            var y = bounds.y + bounds.height - 1;
                            g2.drawLine(clip.x, y, clip.x + clip.width - 1, y);
                        }
                    }
                }
            } finally {
                g2.dispose();
            }
        }

        private boolean isLastLeaf(TreePath path) {
            var parent = path.getParentPath().getLastPathComponent();
            var child = path.getLastPathComponent();
            var childCount = treeModel.getChildCount(parent);

            return childCount > 0
                && treeModel.getChild(parent, childCount - 1) == child;
        }
    }
}
