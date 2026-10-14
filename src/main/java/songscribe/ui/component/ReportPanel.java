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
 * the rows of the other: the lint report is a state, the last {@link ChangeReport} an event that
 * every {@link SongDidChangeNotification} and load replaces (an undo or redo carries an empty
 * one).
 *
 * <p>A selected lint row always has its tooltip on screen: the selection clears when the score
 * dismisses the tooltip, when the panel is hidden, and when the pointer leaves the list.
 *
 * <p>It lives for the process, so it registers with the bus for the process and is never
 * disposed.
 */
public final class ReportPanel extends JPanel {

    private static final String LINT_BADGE = "lint-error.svg";

    private static final String CHANGE_BADGE = "change-report.svg";

    private static final int CHANGE_BADGE_SIZE = 20;

    private static final int LINT_BADGE_SIZE = 16;

    private static final int BADGE_GAP = 6;

    private static final String HIDE_ICON = "";

    private static final int LABEL_INSET = 8;

    private static final float HEADING_LINE_SPACING = 0f;

    /**
     * The heading does not use it: it cuts the heading's descenders.
     */
    private static final float BODY_LINE_SPACING = -0.2f;

    private static final int CELL_PADDING = 6;

    private static final int SEPARATOR_THICKNESS = 1;

    private static final int BEFORE_FIRST_LINE = -1;

    private static final int LINE_START = 0;

    private static final Comparator<Row> SONG_ORDER = Comparator.comparing(
        Row::position, Comparator.comparingInt(Position::lineIndex).thenComparingInt(Position::elementIndex));

    private final MainFrame mainFrame;
    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final JList<Row> list = new ReportList(model, Strings.get(Strings.REPORT_NONE));

    private ChangeReport changeReport = ChangeReport.EMPTY;

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

    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        changeReport = message.getReport();
        showRows(message.getSong());
    }

    @Handler
    public void activeScriptDidChange(ActiveScriptDidChangeNotification message) {
        showRows(message.getSong());
    }

    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        changeReport = message.getReport();
        showRows(message.getSong());

        if (!model.isEmpty()) {
            mainFrame.showMessagesPanel();
        }
    }

    @Handler
    public void lintCalloutWasDismissed(LintCalloutWasDismissedNotification message) {
        list.clearSelection();
    }

    private void showSelectedMark() {
        var mark = list.getSelectedValue() instanceof LintRow lintRow ? lintRow.mark() : null;
        MessageCenter.post(new LintMarkSelectionDidChangeNotification(mark));
    }

    /**
     * Rows at one position keep the order of their report.
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
     * @return {@code null} when the finding's element is in no line of its song
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

    private static String lineLocation(LineRange range) {
        var first = range.firstIndex() + 1;
        var last = range.lastIndex() + 1;

        if (first == last) {
            return Strings.get(Strings.REPORT_LOCATION_LINE, first);
        }

        return Strings.get(Strings.REPORT_LOCATION_LINE_RANGE, first, last);
    }

    private sealed interface Row permits ChangeRow, LintRow {

        String heading();

        /** {@code null} when it names none. */
        @Nullable String location();

        String description();

        Position position();
    }

    /**
     * @param location {@code null} for a change with no place
     */
    private record ChangeRow(String heading, @Nullable String location, String description, Position position)
        implements Row { }

    private record LintRow(String heading, String location, String description, Position position, LintMark mark)
        implements Row { }

    /**
     * @param lineIndex    0-based; {@value #BEFORE_FIRST_LINE} for a change with no place
     * @param elementIndex 0-based; {@value #LINE_START} for a change
     */
    private record Position(int lineIndex, int elementIndex) { }

    /**
     * Each text gets a new document: once a document holds text needing complex layout (such as a
     * character outside the Basic Multilingual Plane), it lays out all later text that way too.
     */
    private static final class WrappingText extends JTextPane {

        private final float lineSpacing;

        WrappingText(float lineSpacing) {
            this.lineSpacing = lineSpacing;
            setEditable(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            setAlignmentX(LEFT_ALIGNMENT);
        }

        /**
         * @param text {@code null} shows no text
         */
        @Override
        public void setText(@Nullable String text) {
            setStyledDocument(newDocument());
            super.setText(text == null ? null : text.replace('\n', ' '));
        }

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
     * Always as wide as its viewport, so its cells wrap their text to the panel's width.
     */
    private static final class ReportList extends JList<Row> {

        private final String emptyText;

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
         * The list UI measures cells again only when a list property changes, not when the width
         * does, yet a wrapped cell's height depends on width; setting and clearing a fixed cell
         * height is such a change.
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
     * Renders a report row as a cell.
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
         * A text pane prefers its text's one-line width, which is not the width of a cell.
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

        private void centerBadgeOnHeadingLine(Icon badge, int lineHeight) {
            var offset = (lineHeight - badge.getIconHeight()) / 2;
            badgeLabel.setBorder(BorderFactory.createEmptyBorder(Math.max(offset, 0), 0, 0, BADGE_GAP));
            textPanel.setBorder(BorderFactory.createEmptyBorder(Math.max(-offset, 0), 0, 0, 0));
        }
    }
}
