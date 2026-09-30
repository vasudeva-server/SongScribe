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

import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static songscribe.dom.BatchReplay.redo;
import static songscribe.dom.BatchReplay.undo;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * How an edit's {@link ChangeReport} reaches the bus: one edit delivers one
 * {@code ChangesWereReportedNotification} that holds the removal every kind of source made —
 * the span funnel, a displacement, and a rule that runs when the bracket closes — and an undo,
 * a redo and an edit under suspended tracking deliver none.
 *
 * <p>The edit is {@link #makeEveryKindOfRemoval}, on a line of quavers that holds a trill whose end
 * the edit deletes, a trill another trill replaces, and a triplet that a song tempo with a dotted
 * beat leaves invalid.
 */
class ChangeReportDeliveryTest extends UnitTest {

    private static final int LINE_LENGTH = 16;
    private static final int FIRST_LINE = 0;

    /** The trill the funnel removes, when the edit deletes the element that ends it. */
    private static final ElementRange FUNNELED_TRILL = new ElementRange(0, 1);
    private static final int DELETED_INDEX = 1;

    /** The trill {@link #REPLACING_TRILL} replaces. */
    private static final ElementRange REPLACED_TRILL = new ElementRange(9, 11);
    private static final ElementRange REPLACING_TRILL = new ElementRange(10, 12);

    /** The triplet the beat rule removes: three quavers, valid in a crotchet beat only. */
    private static final ElementRange TRIPLET = new ElementRange(13, 15);
    private static final int TRIPLET_NORMAL_NOTES = 2;

    private static final Tempo DOTTED_BEAT_TEMPO =
        new Tempo(Tempo.DEFAULT_BPM, BeatUnit.CROTCHET_DOTTED, Tempo.DEFAULT_MARKING);

    @Test
    void testOneEditDeliversOneReportHoldingEveryRemoval() {
        var song = fixtureSong();
        var reports = new ReportRecorder();

        try {
            song.withModification(() -> makeEveryKindOfRemoval(song));

            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries)
                    .extracting(ReportedChange::headingKey, ReportedChange::description)
                    .containsExactlyInAnyOrder(
                        tuple(Strings.DIALOG_REPORT_TRILLS_REMOVED,
                            Strings.get(Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED)),
                        tuple(Strings.DIALOG_REPORT_TRILLS_REMOVED,
                            Strings.get(Strings.DIALOG_REPORT_REASON_REPLACED)),
                        tuple(Strings.DIALOG_REPORT_TUPLETS_REMOVED,
                            Strings.get(TupletValidator.Reason.NO_CONVENTIONAL_SPAN.messageKey()))));
        } finally {
            reports.dispose();
        }
    }

    @Test
    void testUndoAndRedoDeliverNoReport() {
        var song = fixtureSong();
        var batches = new BatchRecorder();
        var reports = new ReportRecorder();

        try {
            song.withModification(() -> makeEveryKindOfRemoval(song));

            assertThat(reports.reports()).as("the edit delivers its own report").hasSize(1);

            var batch = batches.batches().getFirst();

            undo(song, batch);
            redo(song, batch);

            assertThat(reports.reports()).as("only the edit's report, none from the replays").hasSize(1);
        } finally {
            batches.dispose();
            reports.dispose();
        }
    }

    @Test
    void testAnEditUnderSuspendedTrackingDeliversNoReport() {
        var song = fixtureSong();
        var reports = new ReportRecorder();

        try {
            song.withoutMutationTracking(() -> makeEveryKindOfRemoval(song));

            assertThat(song.getLine(FIRST_LINE).spanRegistry().findSpans(Tuplet.class))
                .as("no rule runs while tracking is suspended, so the triplet the beat rule would "
                    + "remove stays, and nothing is reported for it")
                .hasSize(1);
            assertThat(reports.reports()).isEmpty();
        } finally {
            reports.dispose();
        }
    }

    /**
     * A line of quavers holding the trill, the second trill and the triplet the edit removes, each
     * placed before the edit runs.
     */
    private static Song fixtureSong() {
        var song = buildSong(line -> {
            for (var i = 0; i < LINE_LENGTH; i++) {
                line.addElement(quaver());
            }
        });
        var line = song.getLine(FIRST_LINE);
        var ratio = new TupletRatio(TRIPLET_NORMAL_NOTES, new NotatableValue(NoteValue.EIGHTH, Dots.NONE));

        song.withoutMutationTracking(() -> {
            trillOver(line, FUNNELED_TRILL).addTo(line);
            trillOver(line, REPLACED_TRILL).addTo(line);
            new Tuplet(
                line.getElement(TRIPLET.begin()), line.getElement(TRIPLET.end()), TupletGrade.TRIPLET, ratio)
                .addTo(line);
        });

        return song;
    }

    /**
     * One edit that removes a span by each route: a deletion of a trill's end (the funnel), a
     * trill added over another (a displacement), and a song tempo whose beat leaves the triplet
     * invalid (a rule that runs when the bracket closes). The deletion comes last, so the indices
     * the other changes name are still the fixture's.
     */
    private static void makeEveryKindOfRemoval(Song song) {
        var line = song.getLine(FIRST_LINE);

        song.setTempo(DOTTED_BEAT_TEMPO);
        trillOver(line, REPLACING_TRILL).addTo(line);
        line.removeRange(ElementRange.single(DELETED_INDEX));
    }

    private static Trill trillOver(Line line, ElementRange range) {
        return new Trill(line.getElement(range.begin()), line.getElement(range.end()));
    }
}
