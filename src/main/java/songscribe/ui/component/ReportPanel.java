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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ListCellRenderer;
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
import songscribe.font.MusescoreIconFont;
import songscribe.lint.LintFinding;
import songscribe.lint.LintMark;
import songscribe.lint.LintReport;
import songscribe.lint.Linter;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.HoveredLintMarkDidChangeNotification;
import songscribe.message.notification.LintReportDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.util.UIUtils;

/**
 * The Messages panel at the right side of the main window: lists what the song changed on its own
 * during the most recent edit, undo, redo or load, and what is wrong with the song now.
 *
 * <p>The list draws on two sources with different lifetimes, and an update to one never clears
 * the rows of the other:
 * <ul>
 *   <li>The lint report, a state: every {@link LintFinding} for the song as it is now. The panel
 *       reads it from {@link Linter#getReport()} on every
 *       {@link LintReportDidChangeNotification} and every load, so its rows stay until a lint
 *       run no longer finds them.</li>
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
 * element 0 of its first line, and a change with no place sorts before the first line. A lint
 * heading starts with {@value #LINT_HEADING_PREFIX} and a change heading with
 * {@value #CHANGE_HEADING_PREFIX}.
 *
 * <p>While the pointer is on a lint mark in the score, the rows of that mark's findings are
 * selected, also while the panel is hidden. A click on a row selects it too. While the list has
 * focus, the pointer leaving the list clears the selection.
 *
 * <p>It lives for the process, as the main window does, so it registers with the bus for the
 * process and is never disposed.
 */
public final class ReportPanel extends JPanel {

    /** The start of every lint row's heading. */
    private static final String LINT_HEADING_PREFIX = "❗ ";

    /** The start of every change row's heading. */
    private static final String CHANGE_HEADING_PREFIX = "💬 ";

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
    private LintReport lintReport = LintReport.EMPTY;

    /**
     * @param mainFrame the window that holds the panel, which shows it after a load that left
     *                  something to list
     * @effects registers the panel with the message bus for the rest of the process
     */
    ReportPanel(MainFrame mainFrame) {
        super(new BorderLayout());
        this.mainFrame = mainFrame;

        list.setCellRenderer(new CellRenderer());

        // Only while the list has focus: a hover in the score selects rows while focus stays in
        // the score, and the pointer leaves the list on its way to the hovered mark.
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (list.hasFocus()) {
                    list.clearSelection();
                }
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
     * clears them. The lint rows stay.
     */
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        changeReport = message.getReport();
        showRows();
    }

    /**
     * Replaces the lint rows with the linter's current report. The change rows stay.
     */
    @Handler
    public void lintReportDidChange(LintReportDidChangeNotification message) {
        lintReport = Linter.getReport();
        showRows();
    }

    /**
     * Selects the row of every finding with the hovered mark, and scrolls the first into view; with
     * no mark hovered, selects nothing. The selection lasts until the next hover change or the next
     * replacement of the rows.
     */
    @Handler
    public void hoveredLintMarkDidChange(HoveredLintMarkDidChangeNotification message) {
        list.clearSelection();
        var mark = message.getMark();

        if (mark == null) {
            return;
        }

        var indices = new ArrayList<Integer>();

        for (var index = 0; index < model.size(); index++) {
            if (mark.equals(model.get(index).mark())) {
                indices.add(index);
            }
        }

        if (indices.isEmpty()) {
            return;
        }

        list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
        list.ensureIndexIsVisible(indices.getFirst());
    }

    /**
     * Replaces both sources with those of the loaded song, and shows the panel when either is not
     * empty.
     */
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        changeReport = message.getReport();
        lintReport = Linter.getReport();
        showRows();

        if (!changeReport.isEmpty() || !lintReport.isEmpty()) {
            mainFrame.showMessagesPanel();
        }
    }

    /**
     * Replaces the list with one row per change entry and per lint finding. With both sources
     * empty the list is empty, which shows the empty-list text in its place.
     *
     * @effects every change row comes before every lint row, and the rows of each source are in
     *     {@link #SONG_ORDER}, rows at one position in the order of their report; a lint finding
     *     whose element is no longer in the song has no row
     */
    private void showRows() {
        var changeRows = new ArrayList<Row>();

        for (var change : changeReport.changes()) {
            changeRows.add(changeRow(change));
        }

        var lintRows = new ArrayList<Row>();

        for (var finding : lintReport.findings()) {
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
    }

    /**
     * Returns the row of a change entry.
     *
     * @return a row headed {@value #CHANGE_HEADING_PREFIX} and the entry's heading, with no
     *     location when the entry has no lines
     */
    private static Row changeRow(ReportedChange change) {
        var heading = CHANGE_HEADING_PREFIX + Strings.get(change.headingKey());
        var where = change.where();

        if (where == null) {
            return new Row(heading, null, change.description(), new Position(BEFORE_FIRST_LINE, LINE_START), null);
        }

        return new Row(
            heading, lineLocation(where), change.description(), new Position(where.firstIndex(), LINE_START), null);
    }

    /**
     * Returns the row of a lint finding, its element's position resolved in the song as it is
     * now.
     *
     * @return a row headed {@value #LINT_HEADING_PREFIX} and the finding's heading, or
     *     {@code null} when the finding's element is in no line of its song: an edit removed it,
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
        var heading = LINT_HEADING_PREFIX + Strings.get(finding.headingKey());

        return new Row(heading, location, finding.description(), new Position(lineIndex, elementIndex), mark);
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
     * it sorts on.
     *
     * @param heading     the first line of the cell, starting with the prefix of its source
     * @param location    the line or element the row names, shown 1-based; {@code null} for a
     *                    change with no place
     * @param description what changed or what is wrong
     * @param position    where the row sorts
     * @param mark        the finding's mark, which a hover in the score selects the row by;
     *                    {@code null} for a change
     */
    private record Row(
        String heading,
        @Nullable String location,
        String description,
        Position position,
        @Nullable LintMark mark
    ) { }

    /**
     * The place a row sorts on.
     *
     * @param lineIndex    the 0-based line; {@value #BEFORE_FIRST_LINE} for a change with no place
     * @param elementIndex the 0-based element; {@value #LINE_START} for a change
     */
    private record Position(int lineIndex, int elementIndex) { }

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
     * One row: its heading in semibold, then its location, then its description, each starting on
     * its own line at the list's font size with no space between the lines, and a separator line
     * above every cell but the first. A row with no location shows no location line.
     *
     * <p>Each part wraps at word boundaries to the list's width, so a cell is as tall as its
     * wrapped text and never wider than the list.
     *
     * <p>The theme's fonts and colors are read on every call, so a theme change needs nothing
     * here.
     *
     * <p>A selected cell has the theme's active selection colors, whether or not the list has
     * focus, and every other cell the list's normal colors. No cell has a focus border.
     */
    private static final class CellRenderer extends JPanel implements ListCellRenderer<Row> {

        private final JTextPane headingText = wrappingText(HEADING_LINE_SPACING);
        private final JTextPane locationText = wrappingText(BODY_LINE_SPACING);
        private final JTextPane descriptionText = wrappingText(BODY_LINE_SPACING);
        private final List<JTextPane> parts = List.of(headingText, locationText, descriptionText);

        private int cellWidth = 0;

        CellRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            parts.forEach(this::add);
        }

        /**
         * Returns a text pane that shows text the way a label does, but wraps it at word
         * boundaries: not editable, not opaque, and with no border or margin.
         *
         * @param lineSpacing the extra space between two of its lines, as a fraction of the line
         *                    height
         */
        private static JTextPane wrappingText(float lineSpacing) {
            var text = new JTextPane();

            // The text draws with the pane's own font, not one rebuilt from its family and size.
            text.setStyledDocument(new DefaultStyledDocument(new StyleContext() {
                @Override
                public Font getFont(AttributeSet attributes) {
                    return text.getFont();
                }
            }));
            text.setEditable(false);
            text.setOpaque(false);
            text.setBorder(BorderFactory.createEmptyBorder());
            text.setAlignmentX(LEFT_ALIGNMENT);

            // The default style is the base of every paragraph, so the spacing outlasts setText.
            var defaultStyle = text.getStyledDocument().getStyle(StyleContext.DEFAULT_STYLE);
            StyleConstants.setLineSpacing(defaultStyle, lineSpacing);
            return text;
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

            var font = list.getFont();
            // The active selection colors come from the theme, not from the list: FlatLaf sets the
            // list's own selection colors to the inactive ones while it lacks focus, and a hover in
            // the score selects rows while focus stays in the score.
            var foreground = isSelected ? UIManager.getColor("List.selectionForeground") : list.getForeground();
            setBackground(isSelected ? UIManager.getColor("List.selectionBackground") : list.getBackground());

            // A wrapped text area answers its preferred height for the width it has, so each part
            // is given the width it is drawn at before the list asks the cell for its size.
            cellWidth = list.getWidth();

            var textWidth = Math.max(0, cellWidth - 2 * LABEL_INSET);

            for (var part : parts) {
                part.setFont(font);
                part.setForeground(foreground);
                part.setSize(textWidth, Short.MAX_VALUE);
            }

            headingText.setFont(UIManager.getFont("semibold.font"));

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
            // cell, so a cell would get the height of an earlier cell.
            invalidate();

            return this;
        }
    }
}
