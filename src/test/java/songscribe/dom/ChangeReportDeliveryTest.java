/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.undo.MutationReplayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static songscribe.dom.BatchReplay.NO_FONT_CHANGES;
import static songscribe.dom.SongFactory.songOfQuavers;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * How an edit's {@link ChangeReport} reaches the bus: one edit delivers one
 * {@code SongDidChangeNotification} whose report holds the removal every kind of source made —
 * the span funnel, a displacement, and a rule that runs when the bracket closes — an edit that
 * changes nothing on its own delivers an empty report, and a replay delivers the report it is
 * given.
 *
 * <p>The edit is {@link #makeEveryKindOfRemoval}, on a line of quavers that holds a trill whose end
 * the edit deletes, a trill another trill replaces, and an ending whose opening repeat the edit
 * deletes.
 */
class ChangeReportDeliveryTest extends UnitTest {

    private static final int LINE_LENGTH = 20;
    private static final int FIRST_LINE = 0;

    /** The trill the funnel removes, when the edit deletes the element that ends it. */
    private static final ElementRange FUNNELED_TRILL = new ElementRange(0, 1);
    private static final int DELETED_INDEX = 1;

    /** The trill {@link #REPLACING_TRILL} replaces. */
    private static final ElementRange REPLACED_TRILL = new ElementRange(9, 11);
    private static final ElementRange REPLACING_TRILL = new ElementRange(10, 12);

    /**
     * The closing repeat that ends the section before {@link #OPENER_INDEX}, so that deleting the
     * opener leaves the ending's split closing no open section.
     */
    private static final int CLOSER_INDEX = 13;
    private static final int OPENER_INDEX = 14;

    /**
     * The ending the section rule removes: anchor, split (a closing repeat), one note, end. Its
     * split closes the section the opener at {@link #OPENER_INDEX} starts. A quaver stands
     * between the opener and the anchor, because a note anchor needs a note, a rest or a single
     * barline in front of it.
     */
    private static final ElementRange ENDING = new ElementRange(16, 19);
    private static final int ENDING_SPLIT_INDEX = 17;

    @Test
    void testOneEditDeliversOneReportHoldingEveryRemoval() {
        var song = fixtureSong();

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> makeEveryKindOfRemoval(song));

            assertThat(reports.reports()).singleElement().satisfies(ChangeReportDeliveryTest::assertHoldsEveryRemoval);
        });
    }

    @Test
    void testANestedEditDeliversOneReportWhenItsOutermostBracketCloses() {
        var song = fixtureSong();
        var line = song.getLine(FIRST_LINE);

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> {
                line.removeRange(ElementRange.single(OPENER_INDEX));
                song.withModification(() -> trillOver(line, REPLACING_TRILL).addTo());

                assertThat(reports.reports()).as("the inner bracket's close delivers nothing").isEmpty();

                line.removeRange(ElementRange.single(DELETED_INDEX));
            });

            assertThat(reports.reports()).singleElement().satisfies(ChangeReportDeliveryTest::assertHoldsEveryRemoval);
        });
    }

    @Test
    void testAnEditThatChangesNothingOnItsOwnDeliversAnEmptyReport() {
        var song = songOfQuavers(LINE_LENGTH);

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> song.getLine(FIRST_LINE).removeRange(ElementRange.single(DELETED_INDEX)));

            assertThat(reports.allReports()).singleElement().satisfies(report -> assertThat(report.isEmpty()).isTrue());
        });
    }

    /**
     * The changes in the order {@link #makeEveryKindOfRemoval} records them: the displacement
     * and the funnel record while the edit runs, in the order the edit makes them, and the rule
     * records last, when the bracket closes.
     */
    private static void assertHoldsEveryRemoval(List<ReportedChange> entries) {
        assertThat(entries)
            .extracting(ReportedChange::headingKey, ReportedChange::description)
            .containsExactly(
                tuple(Strings.REPORT_TRILLS_REMOVED,
                    Strings.get(Strings.REPORT_REASON_REPLACED)),
                tuple(Strings.REPORT_TRILLS_REMOVED,
                    Strings.get(Strings.REPORT_REASON_ENDPOINT_DELETED)),
                tuple(Strings.REPORT_ENDINGS_REMOVED,
                    Strings.get(Strings.REPORT_REASON_ENDING_NO_OPEN_SECTION)));
    }

    @Test
    void testUndoAndRedoDeliverNoReport() {
        var song = fixtureSong();
        var batches = new BatchRecorder();

        try {
            ReportRecorder.whileRecording(reports -> {
                song.withModification(() -> makeEveryKindOfRemoval(song));

                assertThat(reports.reports()).as("the edit delivers its own report").hasSize(1);

                var batch = batches.batches().getFirst();

                MutationReplayer.undoBatch(song, NO_FONT_CHANGES, batch, ChangeReport.EMPTY);
                MutationReplayer.redoBatch(song, NO_FONT_CHANGES, batch, ChangeReport.EMPTY);

                assertThat(reports.reports()).as("only the edit's report, none from the replays").hasSize(1);
            });
        } finally {
            batches.dispose();
        }
    }

    @Test
    void testReportNeitherThrowsNorRecordsWhileTrackingIsSuspended() {
        var song = fixtureSong();

        ReportRecorder.whileRecording(reports -> {
            song.withoutMutationTracking(() -> makeEveryKindOfRemoval(song));

            assertThat(reports.reports()).isEmpty();
        });
    }

    /**
     * A line of quavers holding the trill, the second trill and the ending the edit removes, each
     * placed before the edit runs. The closing repeat, the opening repeat and the ending's split
     * stand among the quavers at {@link #CLOSER_INDEX}, {@link #OPENER_INDEX} and
     * {@link #ENDING_SPLIT_INDEX}.
     */
    private static Song fixtureSong() {
        var song = songOfQuavers(LINE_LENGTH);
        var line = song.getLine(FIRST_LINE);

        song.withoutMutationTracking(() -> {
            line.setElement(CLOSER_INDEX, repeatRight());
            line.setElement(OPENER_INDEX, repeatLeft());
            line.setElement(ENDING_SPLIT_INDEX, repeatRight());
            trillOver(line, FUNNELED_TRILL).addTo();
            trillOver(line, REPLACED_TRILL).addTo();
            endingOver(line, ENDING).addTo();
        });

        return song;
    }

    /**
     * One edit that removes a span by each route: a deletion of a trill's end (the funnel), a
     * trill added over another (a displacement), and a deletion of the opening repeat that leaves
     * the ending's split closing no open section (a rule that runs when the bracket closes). The
     * deletion of the trill's end comes last, so the indices the other changes name are still the
     * fixture's.
     */
    private static void makeEveryKindOfRemoval(Song song) {
        var line = song.getLine(FIRST_LINE);

        line.removeRange(ElementRange.single(OPENER_INDEX));
        trillOver(line, REPLACING_TRILL).addTo();
        line.removeRange(ElementRange.single(DELETED_INDEX));
    }
}
