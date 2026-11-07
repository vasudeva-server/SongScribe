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
import static songscribe.dom.BatchReplay.NO_FONT_CHANGES;
import static songscribe.dom.SongFactory.compactSong;

/**
 * The song-start rule ({@link SongStart}) as {@link SongStartRule} keeps it when an edit's
 * modification bracket closes: deleting the first note before a barline removes the barline too,
 * moves the key change behind it onto the line and reports the removal, and undo restores the
 * note, the barline and the line's key together.
 */
class SongStartRuleTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int FIRST_NOTE_INDEX = 0;

    private static final List<ElementType> BEFORE_THE_DELETION = List.of(
        ElementType.CROTCHET, ElementType.SINGLE_BARLINE, ElementType.KEY_CHANGE,
        ElementType.CROTCHET, ElementType.CROTCHET);

    private static final List<ElementType> AFTER_THE_DELETION =
        List.of(ElementType.CROTCHET, ElementType.CROTCHET);

    @Test
    void testDeletingTheFirstNoteBeforeABarlineRemovesTheBarlineMovesItsKeyAndUndoRestoresBoth() {
        var song = compactSong(Key.NO_ACCIDENTALS, "3 | K:TWO_SHARPS 3 3");
        var line = song.getLine(FIRST_LINE);
        var recorder = new BatchRecorder();

        try {
            ReportRecorder.whileRecording(reports -> {
                song.withModification(() ->
                    line.deleteRange(line.effectiveRange(ElementRange.single(FIRST_NOTE_INDEX))));

                assertThat(typesOf(line)).containsExactlyElementsOf(AFTER_THE_DELETION);
                assertThat(line.getKey()).isEqualTo(Key.TWO_SHARPS);
                assertThat(reports.reports()).singleElement().isEqualTo(List.of(new ReportedChange(
                    ChangeHeading.LEADING_BARS_REMOVED,
                    Strings.get(Strings.REPORT_REASON_LEADING_BAR_KEY),
                    new LineRange(FIRST_LINE, FIRST_LINE))));
            });

            var batch = recorder.batches().getFirst();

            MutationReplayer.undoBatch(song, NO_FONT_CHANGES, batch);

            assertThat(typesOf(line)).containsExactlyElementsOf(BEFORE_THE_DELETION);
            assertThat(line.getKey()).isEqualTo(Key.NO_ACCIDENTALS);

            MutationReplayer.redoBatch(song, NO_FONT_CHANGES, batch);

            assertThat(typesOf(line)).containsExactlyElementsOf(AFTER_THE_DELETION);
            assertThat(line.getKey()).isEqualTo(Key.TWO_SHARPS);
        } finally {
            recorder.dispose();
        }
    }

    private static List<ElementType> typesOf(Line line) {
        return line.getElements().stream().map(StaffElement::getType).toList();
    }
}
