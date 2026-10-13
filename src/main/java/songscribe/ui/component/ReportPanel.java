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
package songscribe.ui.component;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ChangeReport;
import songscribe.dom.LineRange;
import songscribe.dom.ReportedChange;
import songscribe.dom.Song;
import songscribe.font.MusescoreIconFont;
import songscribe.lint.LintFinding;
import songscribe.lint.LintMark;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.ActiveScriptDidChangeNotification;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.LintCalloutWasDismissedNotification;
import songscribe.message.notification.LintMarkSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.util.GraphicUtils;
import songscribe.util.UIUtils;

/**
 * The Messages panel at the right side of the main window: lists what the song changed on its own
 * during the most recent edit, undo, redo or load, and what is wrong with the song now.
 *
 * <p>The list draws on two sources with different lifetimes, and an update to one never clears
 * the rows of the other:
 * <ul>
 *   <li>The lint report, a state: every {@link LintFinding} for the song as it is now. The panel
 *       reads it from {@code song.getLinter().getReport()} on every load, edit and script
 *       change, so its rows stay until a lint run no longer finds them.</li>
 *   <li>The last {@link ChangeReport}, an event: the changes of the latest edit, undo, redo or
 *       load. Every {@link SongDidChangeNotification} and every load replaces it, so its rows
 *       describe the latest change only; an undo or a redo carries an empty report and clears
 *       them.</li>
 * </ul>
 *
 * <p>A load after which either source is not empty asks the main window to show the panel,
 * without taking focus from the score.
 *
 * <p>It knows nothing about the kinds of finding or change. It shows each one as one cell: its
 * heading, its location, and its finished text, which the code that found the problem or made
 * the change wrote. Every change cell comes before every lint cell, and the cells of each source
 * are in song order, by line and then by element. A change names lines only, so it sorts as
 * element 0 of its first line, and a change with no place sorts before the first line. A badge at
 * the left of each cell shows its source.
 *
 * <p>At most one row is selected. Selecting a lint row scrolls its mark's item into view in the
 * score and shows the mark's tooltip there; clearing the selection, or selecting a change row,
 * hides the tooltip. The panel only announces the selected mark; the score decides how the
 * tooltip shows and hides. The selection also clears when the score dismisses the tooltip on its own,
 * when the panel is hidden, and when the pointer leaves the list, so
 * a selected lint row always has its tooltip on screen.
 *
 * <p>It lives for the process, as the main window does, so it registers with the bus for the
 * process and is never disposed.
 */
public final class ReportPanel extends JPanel {

    /** The badge of every lint cell, in {@code icons/}. */
    private static final String LINT_BADGE = "lint-error.svg";

    /** The badge of every change cell, in {@code icons/}. */
    private static final String CHANGE_BADGE = "change-report.svg";

    /** The width and height of a change report cell's badge, in pixels. */
    private static final int CHANGE_BADGE_SIZE = 20;

    /** The width and height of a lint report cell's badge, in pixels. */
    private static final int LINT_BADGE_SIZE = 16;

    /** Space between a cell's badge and its text, in pixels. */
    private static final int BADGE_GAP = 6;

    /** The {@link MusescoreIconFont} glyph on the button that hides the panel. */
    private static final String HIDE_ICON = "";

    /** Space around the header above the list, in pixels. */
    private static final int LABEL_INSET = 8;

    /** The extra space between two lines of the heading of a cell: none. */
    private static final float HEADING_LINE_SPACING = 0f;

    /**
     * The extra space between two lines of the location or the description of a cell, as a
     * fraction of the line height. The negative value draws the lines closer together. The
     * heading does not use it, because it cuts the descenders of the heading's glyphs.
     */
    private static final float BODY_LINE_SPACING = -0.2f;

    /** Space above and below the text of a cell, in pixels. */
    private static final int CELL_PADDING = 6;

    /** Thickness of the separator line above every cell but the first, in pixels. */
    private static final int SEPARATOR_THICKNESS = 1;

    /** The line a change with no place sorts on: before the first line of the song. */
    private static final int BEFORE_FIRST_LINE = -1;

    /** The element a change sorts on, because a change names lines only. */
    private static final int LINE_START = 0;

    /** Song order: by line, then by element. */
    private static final Comparator<Row> SONG_ORDER = Comparator.comparing(
        Row::position, Comparator.comparingInt(Position::lineIndex).thenComparingInt(Position::elementIndex));

    private final MainFrame mainFrame;
    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final JList<Row> list = new ReportList(model, Strings.get(Strings.REPORT_NONE));

    private ChangeReport changeReport = ChangeReport.EMPTY;

    /**
     * @param mainFrame the window that holds the panel, which shows it after a load that left
     *                  something to list
     * @effects registers the panel with the message bus for the rest of the process
     */
    ReportPanel(MainFrame mainFrame) {
        super(new BorderLayout());
        this.mainFrame = mainFrame;

        list.setCellRenderer(new CellRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedMark();
            }
        });

        // A hidden panel shows no selected row, so it holds none.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                list.clearSelection();
            }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                list.clearSelection();
            }
        });

        var titleLabel = new JLabel(Strings.get(Strings.REPORT_TITLE));
        titleLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "semibold");

        var hideButton = new JButton(HIDE_ICON);
        hideButton.setFont(MusescoreIconFont.font());
        hideButton.setToolTipText(Strings.get(Strings.REPORT_HIDE));
        hideButton.addActionListener(_ -> mainFrame.hideMessagesPanel());

        // The button never takes keyboard focus.
        hideButton.setFocusable(false);

        // Outside a JToolBar, FlatLaf draws a button with a filled background; this style draws
        // it flat, with a background on hover and press only, as it is drawn in a toolbar.
        hideButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);

        var headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(LABEL_INSET, LABEL_INSET, LABEL_INSET, LABEL_INSET));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(hideButton, BorderLayout.EAST);

        setBorder(UIUtils.spacingBorder(FlatLafKey.MESSAGES_PANEL_PADDING));

        add(headerPanel, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);

        MessageSubscription.addProcessListener(this);
    }

    /**
     * Replaces the change rows with the report of an edit, an undo or a redo; an empty report
     * clears them. The lint rows are rebuilt from the song's report, which the notification
     * has already refreshed.
     */
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        changeReport = message.getReport();
        showRows(message.getSong());
    }

    /**
     * Rebuilds the lint rows from the song's report, which the notification has already
     * refreshed. The change rows stay.
     */
    @Handler
    public void activeScriptDidChange(ActiveScriptDidChangeNotification message) {
        showRows(message.getSong());
    }

    /**
     * Replaces both sources with those of the loaded song, and shows the panel when either is not
     * empty.
     */
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        changeReport = message.getReport();
        showRows(message.getSong());

        if (!model.isEmpty()) {
            mainFrame.showMessagesPanel();
        }
    }

    /** Clears the selection, whose mark the score no longer shows. */
    @Handler
    public void lintCalloutWasDismissed(LintCalloutWasDismissedNotification message) {
        list.clearSelection();
    }

    /** Announces the mark of the selected lint row, or no mark for a change row or no row. */
    private void showSelectedMark() {
        var mark = list.getSelectedValue() instanceof LintRow lintRow ? lintRow.mark() : null;
        MessageCenter.post(new LintMarkSelectionDidChangeNotification(mark));
    }

    /**
     * Replaces the list with one row per change entry and per lint finding. With both sources
     * empty the list is empty, which shows the empty-list text in its place.
     *
     * @effects every change row comes before every lint row, and the rows of each source are in
     *     {@link #SONG_ORDER}, rows at one position in the order of their report; a lint finding
     *     whose element is no longer in the song has no row
     */
    private void showRows(Song song) {
        var selected = list.getSelectedValue();
        var changeRows = new ArrayList<Row>();

        for (var change : changeReport.changes()) {
            changeRows.add(changeRow(change));
        }

        var lintRows = new ArrayList<Row>();

        for (var finding : song.getLinter().getReport().findings()) {
            var row = lintRow(finding);

            if (row != null) {
                lintRows.add(row);
            }
        }

        // List.sort is stable, so rows at one position keep the order of their report.
        changeRows.sort(SONG_ORDER);
        lintRows.sort(SONG_ORDER);

        model.clear();
        model.addAll(changeRows);
        model.addAll(lintRows);

        if (selected != null) {
            var index = model.indexOf(selected);

            if (index >= 0) {
                list.setSelectedIndex(index);
            }
        }
    }

    /**
     * Returns the row of a change entry.
     *
     * @return a row headed with the entry's heading, with no location when the entry has no lines
     */
    private static Row changeRow(ReportedChange change) {
        var heading = Strings.get(change.headingKey());
        var where = change.where();

        if (where == null) {
            return new ChangeRow(heading, null, change.description(), new Position(BEFORE_FIRST_LINE, LINE_START));
        }

        return new ChangeRow(
            heading, lineLocation(where), change.description(), new Position(where.firstIndex(), LINE_START));
    }

    /**
     * Returns the row of a lint finding, its element's position resolved in the song as it is
     * now.
     *
     * @return a row headed with the finding's heading, or {@code null} when the finding's element is in no line of its song: an edit removed it,
     *     and the next lint run replaces the report
     */
    private static @Nullable Row lintRow(LintFinding finding) {
        var mark = finding.mark();
        var element = mark.element();
        var line = element.getParentLine();

        if (line == null || !line.getSong().contains(line)) {
            return null;
        }

        var lineIndex = line.index();
        var elementIndex = line.getElementIndex(element);

        // Indices are 0-based in the model and shown 1-based, as the user counts them.
        var location = Strings.get(Strings.REPORT_LOCATION_ELEMENT, lineIndex + 1, elementIndex + 1);
        return new LintRow(finding.heading(), location, finding.description(), new Position(lineIndex, elementIndex), mark);
    }

    /** The indices are stored 0-based and shown 1-based, as the user counts them. */
    private static String lineLocation(LineRange range) {
        var first = range.firstIndex() + 1;
        var last = range.lastIndex() + 1;

        if (first == last) {
            return Strings.get(Strings.REPORT_LOCATION_LINE, first);
        }

        return Strings.get(Strings.REPORT_LOCATION_LINE_RANGE, first, last);
    }

    /**
     * One cell of the list: a change entry or a lint finding, as finished text, and the position
     * it sorts on. A reader that needs more than these switches over the two records.
     */
    private sealed interface Row permits ChangeRow, LintRow {

        /** The first line of the cell. */
        String heading();

        /** The line or element the row names, shown 1-based; {@code null} when it names none. */
        @Nullable String location();

        /** What changed or what is wrong. */
        String description();

        /** Where the row sorts. */
        Position position();
    }

    /**
     * The row of a change entry.
     *
     * @param location {@code null} for a change with no place
     */
    private record ChangeRow(String heading, @Nullable String location, String description, Position position)
        implements Row { }

    /**
     * The row of a lint finding.
     *
     * @param mark the finding's mark, which selecting the row shows in the score
     */
    private record LintRow(String heading, String location, String description, Position position, LintMark mark)
        implements Row { }

    /**
     * The place a row sorts on.
     *
     * @param lineIndex    the 0-based line; {@value #BEFORE_FIRST_LINE} for a change with no place
     * @param elementIndex the 0-based element; {@value #LINE_START} for a change
     */
    private record Position(int lineIndex, int elementIndex) { }

    /**
     * A text pane that shows text the way a label does, but wraps it at word boundaries: not
     * editable, not opaque, and with no border or margin.
     *
     * <p>Each text gets a new document. Once a document holds text that needs complex layout,
     * such as a character outside the Basic Multilingual Plane, it lays out all later text with
     * complex layout too. A pane that one renderer shares by all cells would then lay out each
     * cell after such a cell differently from the same cell shown first.
     */
    private static final class WrappingText extends JTextPane {

        private final float lineSpacing;

        /**
         * @param lineSpacing the extra space between two of its lines, as a fraction of the line
         *                    height
         */
        WrappingText(float lineSpacing) {
            this.lineSpacing = lineSpacing;
            setEditable(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setAlignmentX(LEFT_ALIGNMENT);
        }

        /**
         * Shows {@code text} in a new document, with each line break shown as a space. A line
         * break in a string places a line of a tooltip; this pane wraps the text to its own width.
         *
         * @param text the text to show; {@code null} shows no text
         */
        @Override
        public void setText(@Nullable String text) {
            setStyledDocument(newDocument());
            super.setText(text == null ? null : text.replace('\n', ' '));
        }

        /**
         * Returns an empty document whose text draws with this pane's own font, not one rebuilt
         * from its family and size, and whose paragraphs have this pane's line spacing.
         */
        private DefaultStyledDocument newDocument() {
            var document = new DefaultStyledDocument(new StyleContext() {
                @Override
                public Font getFont(AttributeSet attributes) {
                    return WrappingText.this.getFont();
                }
            });

            // The default style is the base of every paragraph, so the spacing applies to the text
            // that setText inserts.
            var defaultStyle = document.getStyle(StyleContext.DEFAULT_STYLE);
            StyleConstants.setLineSpacing(defaultStyle, lineSpacing);
            return document;
        }
    }

    /**
     * The list, which is always as wide as its viewport so that its cells wrap their text to the
     * panel's width, and which shows a line of text at its center while it has no cells.
     */
    private static final class ReportList extends JList<Row> {

        private final String emptyText;

        /**
         * @param model     the rows the list shows
         * @param emptyText the text shown at the center of the list while it has no cells
         */
        ReportList(DefaultListModel<Row> model, String emptyText) {
            super(model);
            this.emptyText = emptyText;

            addComponentListener(new ComponentAdapter() {
                private int lastWidth = 0;

                @Override
                public void componentResized(ComponentEvent e) {
                    var width = getWidth();

                    if (width != lastWidth) {
                        lastWidth = width;
                        recomputeCellHeights();
                    }
                }
            });
        }

        /**
         * Makes the list measure every cell again. The list UI caches each cell's height and
         * measures again only when a list property changes, not when the list's width changes,
         * yet a wrapped cell's height depends on that width. Setting a fixed cell height and
         * clearing it again is such a property change.
         */
        private void recomputeCellHeights() {
            setFixedCellHeight(0);
            setFixedCellHeight(-1);
        }

        // The cells wrap to the list's width, so the list never grows wider than the viewport.
        // While empty, it also fills the viewport, so its center is the center of the list area.
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return isEmpty() || super.getScrollableTracksViewportHeight();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (!isEmpty()) {
                return;
            }

            var g2 = (Graphics2D) g.create();

            try {
                FlatUIUtils.setRenderingHints(g2);
                g2.setFont(getFont());
                g2.setColor(UIManager.getColor("Label.disabledForeground"));

                var metrics = g2.getFontMetrics();
                var x = (getWidth() - metrics.stringWidth(emptyText)) / 2;
                var y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.drawString(emptyText, x, y);
            } finally {
                g2.dispose();
            }
        }

        private boolean isEmpty() {
            return getModel().getSize() == 0;
        }
    }

    /**
     * One row: the badge of its source at the left, centered on the first line of the heading, and
     * to its right a column with its heading
     * in semibold, then its location, then its description, each starting on its own line at the
     * list's font size with no space between the lines, and a separator line above every cell but
     * the first. A row with no location shows no location line.
     *
     * <p>Each part wraps at word boundaries to the width of the column, so a cell is as tall as
     * its wrapped text, or its badge when that is taller, and never wider than the list.
     *
     * <p>The theme's fonts and colors are read on every call, so a theme change needs nothing
     * here.
     *
     * <p>A selected cell has the theme's active selection colors, whether or not the list has
     * focus, and every other cell the list's normal colors. No cell has a focus border.
     */
    private static final class CellRenderer extends JPanel implements ListCellRenderer<Row> {

        private final WrappingText headingText = new WrappingText(HEADING_LINE_SPACING);
        private final WrappingText locationText = new WrappingText(BODY_LINE_SPACING);
        private final WrappingText descriptionText = new WrappingText(BODY_LINE_SPACING);
        private final List<WrappingText> parts = List.of(headingText, locationText, descriptionText);
        private final JPanel textPanel = new JPanel();
        private final JLabel badgeLabel = new JLabel();
        private final Icon lintBadge = GraphicUtils.getScaledSVGIcon(LINT_BADGE, LINT_BADGE_SIZE);

        // The themed badge takes the color of the label's foreground, so it follows the selection.
        private final Icon changeBadge = GraphicUtils.getScaledSVGIcon(CHANGE_BADGE,
            CHANGE_BADGE_SIZE, true);

        private int cellWidth = 0;

        CellRenderer() {
            super(new BorderLayout());

            badgeLabel.setVerticalAlignment(SwingConstants.TOP);

            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);
            parts.forEach(textPanel::add);

            add(badgeLabel, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        /**
         * A cell is as wide as the list. A text pane prefers the width of its text on one line,
         * so the width the layout computes is not the width of a cell.
         */
        @Override
        public Dimension getPreferredSize() {
            var size = super.getPreferredSize();
            size.width = cellWidth;
            return size;
        }

        @Override
        public Component getListCellRendererComponent(
            JList<? extends Row> list,
            Row row,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            var location = row.location();
            headingText.setText(row.heading());
            locationText.setText(location);
            locationText.setVisible(location != null);
            descriptionText.setText(row.description());

            var badge = switch (row) {
                case LintRow _ -> lintBadge;
                case ChangeRow _ -> changeBadge;
            };

            badgeLabel.setIcon(badge);

            var headingFont = UIManager.getFont("semibold.font");
            centerBadgeOnHeadingLine(badge, headingText.getFontMetrics(headingFont).getHeight());

            var font = list.getFont();
            // The active selection colors come from the theme, not from the list: FlatLaf sets the
            // list's own selection colors to the inactive ones while it lacks focus.
            var foreground = isSelected ? UIManager.getColor("List.selectionForeground") : list.getForeground();
            setBackground(isSelected ? UIManager.getColor("List.selectionBackground") : list.getBackground());
            badgeLabel.setForeground(foreground);

            // A wrapped text area answers its preferred height for the width it has, so each part
            // is given the width it is drawn at before the list asks the cell for its size.
            cellWidth = list.getWidth();

            var textWidth = Math.max(0, cellWidth - 2 * LABEL_INSET - badgeLabel.getPreferredSize().width);

            for (var part : parts) {
                part.setFont(font);
                part.setForeground(foreground);
                part.setSize(textWidth, Short.MAX_VALUE);
            }

            headingText.setFont(headingFont);

            // The side space matches the title's, so the cell text lines up with it.
            var padding = BorderFactory.createEmptyBorder(CELL_PADDING, LABEL_INSET, CELL_PADDING, LABEL_INSET);

            // The first cell has nothing above it to separate from.
            if (index == 0) {
                setBorder(padding);
            } else {
                var separator = BorderFactory.createMatteBorder(
                    SEPARATOR_THICKNESS, 0, 0, 0, UIManager.getColor("Separator.foreground"));
                setBorder(BorderFactory.createCompoundBorder(separator, padding));
            }

            // The list measures its cells one after the other and never validates this panel between
            // them. Swing then does not discard the sizes that the box layout cached for the previous
            // cell, so a cell would get the height of an earlier cell. Invalidating the text column
            // also invalidates this panel.
            textPanel.invalidate();

            return this;
        }

        /**
         * Moves the badge or the text column down, so that the center of the badge is at the center
         * of the heading's first line. The badge moves down when it is shorter than that line, and
         * the text column when the badge is taller.
         *
         * @param badge      the badge the label shows
         * @param lineHeight the height of one line of the heading, in pixels
         */
        private void centerBadgeOnHeadingLine(Icon badge, int lineHeight) {
            var offset = (lineHeight - badge.getIconHeight()) / 2;
            badgeLabel.setBorder(BorderFactory.createEmptyBorder(Math.max(offset, 0), 0, 0, BADGE_GAP));
            textPanel.setBorder(BorderFactory.createEmptyBorder(Math.max(-offset, 0), 0, 0, 0));
        }
    }
}
