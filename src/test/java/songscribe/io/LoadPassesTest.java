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

package songscribe.io;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Accidental;
import songscribe.dom.ChainedAccidental.ChainType;
import songscribe.dom.Dots;
import songscribe.dom.ElementLocation;
import songscribe.dom.ElementLocationRange;
import songscribe.dom.NotatableValue;
import songscribe.dom.NoteElement;
import songscribe.dom.NoteValue;
import songscribe.dom.PendingEnding;
import songscribe.dom.PendingTuplet;
import songscribe.dom.ReportedChange;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.TupletGrade;
import songscribe.dom.TupletRatio;
import songscribe.dom.TupletValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * {@link LoadPasses#run}'s promises for the load passes. The accidental stamp: a note the file
 * writes an accidental on sounds that accidental, a bare note adopts what is in force at its
 * position — barlines included — and parentheses are ignored, on {@code accidental-load.musicxml},
 * a fixture this program itself wrote under the barline-reset policy this test pins the repair
 * against. The report: one entry for each change the passes and the reader made to the song.
 */
class LoadPassesTest extends UnitTest {

    private static final int LINE_INDEX = 0;

    private static final int TRIPLET_NORMAL_NOTES = 2;

    private static final double NO_OFFSET_SS = 0.0;

    /** The three notes of the tuplet the file did not give a ratio. */
    private static final ElementRange DERIVED_TUPLET = new ElementRange(0, 2);

    /** The three notes of the tuplet whose stated ratio the validator rejects. */
    private static final ElementRange DROPPED_TUPLET = new ElementRange(3, 5);

    /** Two notes with no split inside them, which is no ending. */
    private static final ElementRange DROPPED_ENDING = new ElementRange(6, 7);

    private static final int BARLINE_INDEX = 8;
    private static final int BREATH_MARK_INDEX = 9;

    @Test
    void testTheReportHoldsOneEntryForEachChangeTheLoadMade() {
        var song = buildSong(line -> {
            for (var i = 0; i < BARLINE_INDEX; i++) {
                line.addElement(crotchet());
            }

            line.addElement(singleBarline());
            line.addElement(breathMark());
            line.addElement(crotchet());
        });
        var line = song.getLine(LINE_INDEX);
        var grade = TupletGrade.TRIPLET;
        var sameCount = new TupletRatio(grade.number(), new NotatableValue(NoteValue.QUARTER, Dots.NONE));
        var derivedTuplet = pendingTuplet(line.getElements(), DERIVED_TUPLET, grade, null);
        var droppedTuplet = pendingTuplet(line.getElements(), DROPPED_TUPLET, grade, sameCount);
        var droppedEnding = new PendingEnding(
            line.getElement(DROPPED_ENDING.begin()), line.getElement(DROPPED_ENDING.end()), NO_OFFSET_SS);

        var report = LoadPasses.run(song, new LoadPasses.Input(
            List.of(derivedTuplet, droppedTuplet), List.of(droppedEnding), List.of(), true));

        assertThat(report.changes()).containsExactly(
            new ReportedChange(
                Strings.DIALOG_REPORT_TUPLETS_UPDATED,
                Strings.get(Strings.DIALOG_REPORT_UPDATED_DETAIL, grade.number(), TRIPLET_NORMAL_NOTES),
                locationOf(DERIVED_TUPLET)),
            new ReportedChange(
                Strings.DIALOG_REPORT_TUPLETS_REMOVED,
                Strings.get(TupletValidator.Reason.BAD_RATIO.messageKey()),
                locationOf(DROPPED_TUPLET)),
            new ReportedChange(
                Strings.DIALOG_REPORT_ENDINGS_REMOVED,
                Strings.get(Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),
                locationOf(DROPPED_ENDING)),
            new ReportedChange(
                Strings.DIALOG_REPORT_BREATH_MARKS_REMOVED,
                Strings.get(Strings.DIALOG_REPORT_REASON_BREATH_MARK_HOST),
                ElementLocationRange.single(new ElementLocation(LINE_INDEX, BREATH_MARK_INDEX))),
            new ReportedChange(
                Strings.DIALOG_REPORT_ACCIDENTALS_CONVERTED,
                Strings.get(Strings.DIALOG_REPORT_ACCIDENTALS_DETAIL),
                null));
    }

    private static PendingTuplet pendingTuplet(
        List<StaffElement> elements, ElementRange span, TupletGrade grade, @Nullable TupletRatio statedRatio
    ) {
        return new PendingTuplet(
            elements.get(span.begin()), elements.get(span.end()), grade, statedRatio, NO_OFFSET_SS);
    }

    private static ElementLocationRange locationOf(ElementRange span) {
        return new ElementLocationRange(
            new ElementLocation(LINE_INDEX, span.begin()), new ElementLocation(LINE_INDEX, span.end()));
    }

    @Test
    void testABareNoteAfterABarlineSoundsWhatIsInForceAndIsARepetition() throws Exception {
        var song = loadFixture("accidental-load");
        var notes = song.getLine(0).getElements().stream()
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .toList();

        var leadingFlat = notes.get(0);
        var afterBarline = notes.get(1);

        assertThat(leadingFlat.chainedAccidental().type()).isEqualTo(ChainType.ANCHOR);
        assertThat(afterBarline.chainedAccidental().accidental())
            .as("the barline written between the two notes does not reset what is in force")
            .isEqualTo(leadingFlat.chainedAccidental().accidental());
        assertThat(afterBarline.chainedAccidental().type()).isEqualTo(ChainType.REPETITION);
    }

    @Test
    void testAParenthesizedAccidentalReadsAsAPlainOne() throws Exception {
        var song = loadFixture("accidental-load");
        var notes = song.getLine(0).getElements().stream()
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .toList();

        var plainFlat = notes.get(2);
        var parenthesizedFlat = notes.get(3);

        assertThat(parenthesizedFlat.chainedAccidental().accidental()).isEqualTo(Accidental.FLAT);
        assertThat(parenthesizedFlat.chainedAccidental().type())
            .as("sounding the same flat right after the plain one, with nothing between them, it is implicit — "
                + "exactly as a plain repeated accidental would be")
            .isEqualTo(ChainType.IMPLICIT);
        assertThat(plainFlat.chainedAccidental().type()).isEqualTo(ChainType.ANCHOR);
    }
}
