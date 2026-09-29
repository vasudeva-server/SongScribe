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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.ChangeReport;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

/**
 * The controller behind {@link ReportDialog}. It hands the dialog one report and writes nothing:
 * the dialog shows the changes the song already made, and its OK only dismisses it.
 *
 * <p>Constructed per report, by {@link #show}.
 */
public final class ReportDialogController extends DialogController<ChangeReport, @Nullable Void> {

    private static final Logger LOG = LoggerFactory.getLogger(ReportDialogController.class);

    private final ChangeReport report;

    /**
     * @param report the changes to list
     */
    private ReportDialogController(ChangeReport report) {
        this.report = report;
    }

    /**
     * Shows the dialog for {@code report} and returns when the user dismisses it, unless dialogs
     * are suppressed.
     *
     * @param occasion what the report follows
     * @param report   the changes to list; the caller shows the dialog only for a report with at
     *                 least one entry
     */
    public static void show(ReportDialog.Occasion occasion, ChangeReport report) {
        if (OptionDialogs.isSuppressed()) {
            LOG.info("Report dialog suppressed: {} changes", report.changes().size());

            return;
        }

        var ops = new ReportDialogController(report).ops();
        new ReportDialog(MainFrame.getInstance(), occasion, ops).setVisible(true);
    }

    @Override
    protected ChangeReport read() {
        return report;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code false}: the dialog has nothing to write, so OK closes it without a commit
     */
    @Override
    protected boolean dataWasModified(@Nullable Void values) {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Never reached, since {@link #dataWasModified} answers {@code false}.
     */
    @Override
    protected void commit(@Nullable Void values) {
    }
}
