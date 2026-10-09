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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.dom.ChangeReport;
import songscribe.dom.ElementLocationRange;
import songscribe.dom.ReportedChange;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.util.UIUtils;

/**
 * The Messages panel at the bottom of the main window: lists the changes the song made on its
 * own during the most recent edit, undo, redo or load.
 *
 * <p>Each edit and each load replaces the list, so it always describes the latest change to the
 * song; a change that made nothing on its own leaves the list empty. A load whose report is not
 * empty asks the main window to show the panel, without taking focus from the score.
 *
 * <p>It knows nothing about the kinds of change. It shows each of a {@link ChangeReport}'s
 * entries as one cell, under its heading and with its finished text at its location, and keeps
 * the entries that share a heading together; the code that made a change wrote the words.
 *
 * <p>It lives for the process, as the main window does, so it registers with the bus for the
 * process and is never disposed.
 */
public final class ReportPanel extends JPanel {

    /** Space above, below and to the left of the title above the list, in pixels. */
    private static final int LABEL_INSET = 8;

    /** Space above and below the text of a cell, in pixels. */
    private static final int CELL_PADDING = 6;

    /** Thickness of the separator line above every cell but the first, in pixels. */
    private static final int SEPARATOR_THICKNESS = 1;

    private final MainFrame mainFrame;
    private final DefaultListModel<ReportedChange> model = new DefaultListModel<>();
    private final JList<ReportedChange> list = new ReportList(model, Strings.get(Strings.REPORT_NONE));

    /**
     * @param mainFrame the window that holds the panel, which shows it after a load that changed
     *                  the song
     * @effects registers the panel with the message bus for the rest of the process
     */
    ReportPanel(MainFrame mainFrame) {
        super(new BorderLayout());
        this.mainFrame = mainFrame;

        list.setCellRenderer(new CellRenderer());

        var titleLabel = new JLabel(Strings.get(Strings.REPORT_TITLE));
        titleLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "semibold");
        titleLabel.setBorder(BorderFactory.createEmptyBorder(LABEL_INSET, LABEL_INSET, LABEL_INSET, 0));

        setBorder(UIUtils.spacingBorder(FlatLafKey.MESSAGES_PANEL_PADDING));

        add(titleLabel, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);

        MessageSubscription.addProcessListener(this);
    }

    /**
     * Shows the report of an edit, an undo or a redo; an empty report clears the list.
     */
    @Handler
    public void songDidChange(SongDidChangeNotification message) {
        showReport(message.getReport());
    }

    /**
     * Shows the report of a load, and shows the panel when the load changed the song.
     */
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        var report = message.getReport();
        showReport(report);

        if (!report.isEmpty()) {
            mainFrame.showMessagesPanel();
        }
    }

    /**
     * Replaces the list with the report's entries, one cell each, with the entries that share a
     * heading next to each other. An empty report leaves the list empty, which shows the
     * empty-list text in its place.
     *
     * @param report the changes to list
     */
    private void showReport(ChangeReport report) {
        model.clear();

        for (var changes : report.byHeading().values()) {
            model.addAll(changes);
        }
    }

    private static String detailText(ReportedChange change) {
        var where = change.where();

        if (where == null) {
            return change.description();
        }

        return Strings.get(Strings.REPORT_CHANGE, location(where), change.description());
    }

    /** The indices are stored 0-based and shown 1-based, as the user counts them. */
    private static String location(ElementLocationRange range) {
        var start = range.start();
        var end = range.end();

        if (start.lineIndex() != end.lineIndex()) {
            return Strings.get(
                Strings.REPORT_LOCATION_LINES,
                start.lineIndex() + 1,
                start.elementIndex() + 1,
                end.lineIndex() + 1,
                end.elementIndex() + 1);
        }

        if (start.elementIndex() == end.elementIndex()) {
            return Strings.get(
                Strings.REPORT_LOCATION_ELEMENT, start.lineIndex() + 1, start.elementIndex() + 1);
        }

        return Strings.get(
            Strings.REPORT_LOCATION_ELEMENTS,
            start.lineIndex() + 1,
            start.elementIndex() + 1,
            end.elementIndex() + 1);
    }

    /**
     * The list, which shows a line of text at its center while it has no cells.
     */
    private static final class ReportList extends JList<ReportedChange> {

        private final String emptyText;

        /**
         * @param model     the entries the list shows
         * @param emptyText the text shown at the center of the list while it has no cells
         */
        ReportList(DefaultListModel<ReportedChange> model, String emptyText) {
            super(model);
            this.emptyText = emptyText;

            // The panel is shown and hidden from the Window menu, and never holds focus.
            setFocusable(false);
        }

        // While empty, the list fills the viewport, so its center is the center of the list area.
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return isEmpty() || super.getScrollableTracksViewportWidth();
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
     * One change: its heading in semibold above its location and description, both at the
     * list's font size, with a separator line above every cell but the first.
     *
     * <p>The theme's fonts and colors are read on every call, so a theme change needs nothing
     * here.
     *
     * <p>A cell always has the list's normal colors and no focus border: a click still selects a
     * cell, but the list never draws a selection.
     */
    private static final class CellRenderer extends JPanel implements ListCellRenderer<ReportedChange> {

        private final JLabel headingLabel = new JLabel();
        private final JLabel detailLabel = new JLabel();

        CellRenderer() {
            super(new BorderLayout());
            add(headingLabel, BorderLayout.NORTH);
            add(detailLabel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
            JList<? extends ReportedChange> list,
            ReportedChange change,
            int index,
            boolean isSelected,
            boolean cellHasFocus
        ) {
            headingLabel.setText(Strings.get(change.headingKey()));
            detailLabel.setText(detailText(change));

            headingLabel.setFont(UIManager.getFont("semibold.font"));
            detailLabel.setFont(list.getFont());

            var foreground = list.getForeground();
            setBackground(list.getBackground());
            headingLabel.setForeground(foreground);
            detailLabel.setForeground(foreground);

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

            return this;
        }
    }
}
