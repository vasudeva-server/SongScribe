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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Accidental;
import songscribe.dom.ChainedAccidental.ChainType;
import songscribe.dom.ElementLocation;
import songscribe.dom.ElementLocationRange;
import songscribe.dom.Hairpin;
import songscribe.dom.NoteElement;
import songscribe.dom.NoteValue;
import songscribe.dom.ParsedSong;
import songscribe.dom.PendingEnding;
import songscribe.dom.PendingHairpin;
import songscribe.dom.PendingTuplet;
import songscribe.dom.ReportedChange;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.TupletGrade;
import songscribe.dom.TupletNormalTime;
import songscribe.dom.TupletValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.ratioOf;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * {@link ParsedSong#settle}'s promises for the load passes. The accidental stamp: a note the file
 * writes an accidental on sounds that accidental, a bare note adopts what is in force at its
 * position — barlines included — and parentheses are ignored, on {@code accidental-load.musicxml},
 * a fixture this program itself wrote under the barline-reset policy this test pins the repair
 * against. The report: one entry for each change the passes and the reader made to the song.
 */
class LoadPassesTest extends UnitTest {

    private static final String ACCIDENTAL_FIXTURE = "accidental-load";

    private static final int LINE_INDEX = 0;

    /** The notes of the accidental fixture's line, in line order. */
    private static final int LEADING_FLAT_INDEX = 0;
    private static final int AFTER_BARLINE_INDEX = 1;
    private static final int PLAIN_FLAT_INDEX = 2;
    private static final int PARENTHESIZED_FLAT_INDEX = 3;

    private static final double NO_OFFSET_SS = 0.0;

    /** The three notes of the tuplet the file did not give a ratio. */
    private static final ElementRange DERIVED_TUPLET = new ElementRange(0, 2);

    /** The three notes of the tuplet whose stated ratio the validator rejects. */
    private static final ElementRange DROPPED_TUPLET = new ElementRange(3, 5);

    /** Two notes with no split inside them, which is no ending. */
    private static final ElementRange DROPPED_ENDING = new ElementRange(6, 7);

    private static final int BARLINE_INDEX = 8;
    private static final int BREATH_MARK_INDEX = 9;

    /** A note and the rest after it: a hairpin cannot end on a rest. */
    private static final ElementRange DROPPED_HAIRPIN = new ElementRange(BREATH_MARK_INDEX + 1, BREATH_MARK_INDEX + 2);

    @Test
    void testTheReportHoldsOneEntryForEachChangeTheLoadMade() {
        var song = buildSong(line -> {
            for (var i = 0; i < BARLINE_INDEX; i++) {
                line.addElement(crotchet());
            }

            line.addElement(singleBarline());
            line.addElement(breathMark());
            line.addElement(crotchet());
            line.addElement(crotchetRest());
        });
        var line = song.getLine(LINE_INDEX);
        var grade = TupletGrade.TRIPLET;
        var sameCount = ratioOf(grade.number(), NoteValue.QUARTER);
        var derivedTuplet = pendingTuplet(line.getElements(), DERIVED_TUPLET, grade, null);
        var droppedTuplet = pendingTuplet(line.getElements(), DROPPED_TUPLET, grade, sameCount);
        var droppedEnding = new PendingEnding(
            line.getElement(DROPPED_ENDING.begin()), line.getElement(DROPPED_ENDING.end()), NO_OFFSET_SS,
            NO_OFFSET_SS);
        var droppedHairpin = new PendingHairpin(
            Hairpin.Kind.CRESCENDO, line.getElement(DROPPED_HAIRPIN.begin()), line.getElement(DROPPED_HAIRPIN.end()),
            NO_OFFSET_SS, NO_OFFSET_SS);

        var report = new ParsedSong(song, List.of(derivedTuplet, droppedTuplet), List.of(droppedEnding),
            List.of(droppedHairpin), List.of(ParsedSong.accidentalsConvertedEntry())).settle();

        assertThat(report.changes()).containsExactly(
            new ReportedChange(
                Strings.DIALOG_REPORT_TUPLETS_UPDATED,
                Strings.get(Strings.DIALOG_REPORT_UPDATED_DETAIL, grade.number(), TRIPLET_NORMAL_NOTES),
                locationOf(DERIVED_TUPLET)),
            new ReportedChange(
                Strings.DIALOG_REPORT_TUPLETS_REMOVED,
                Strings.get(Strings.DIALOG_REPORT_REASON_NUMBER_EQUALS_NORMAL_NOTES),
                locationOf(DROPPED_TUPLET)),
            new ReportedChange(
                Strings.DIALOG_REPORT_ENDINGS_REMOVED,
                Strings.get(Strings.DIALOG_REPORT_REASON_ENDING_NOT_VALID),
                locationOf(DROPPED_ENDING)),
            new ReportedChange(
                Strings.DIALOG_REPORT_HAIRPINS_REMOVED,
                Strings.get(Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE),
                locationOf(DROPPED_HAIRPIN)),
            new ReportedChange(
                Strings.DIALOG_REPORT_BREATH_MARKS_REMOVED,
                Strings.get(Strings.DIALOG_REPORT_REASON_BREATH_MARK_HOST),
                ElementLocationRange.single(new ElementLocation(LINE_INDEX, BREATH_MARK_INDEX))),
            new ReportedChange(
                Strings.DIALOG_REPORT_ACCIDENTALS_CONVERTED,
                Strings.get(Strings.DIALOG_REPORT_ACCIDENTALS_DETAIL),
                null));
    }

    @Test
    void testACleanLoadGivesAnEmptyReport() {
        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(singleBarline());
            line.addElement(crotchet());
        });

        var report = new ParsedSong(song, List.of(), List.of(), List.of(), List.of()).settle();

        assertThat(report.isEmpty()).isTrue();
    }

    private static PendingTuplet pendingTuplet(
        List<StaffElement> elements, ElementRange span, TupletGrade grade, @Nullable TupletNormalTime statedRatio
    ) {
        return new PendingTuplet(
            elements.get(span.begin()), elements.get(span.end()), grade, statedRatio, NO_OFFSET_SS);
    }

    private static ElementLocationRange locationOf(ElementRange span) {
        return new ElementLocationRange(
            new ElementLocation(LINE_INDEX, span.begin()), new ElementLocation(LINE_INDEX, span.end()));
    }

    /** The notes of the accidental fixture's first line, loaded once for the class. */
    private List<NoteElement> accidentalNotes;

    @BeforeAll
    void loadAccidentalNotes() throws Exception {
        accidentalNotes = loadFixture(ACCIDENTAL_FIXTURE).getLine(LINE_INDEX).getElements().stream()
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .toList();
    }

    @Test
    void testABareNoteAfterABarlineSoundsWhatIsInForceAndIsARepetition() {
        var leadingFlat = accidentalNotes.get(LEADING_FLAT_INDEX);
        var afterBarline = accidentalNotes.get(AFTER_BARLINE_INDEX);

        assertThat(leadingFlat.chainedAccidental().type()).isEqualTo(ChainType.ANCHOR);
        assertThat(afterBarline.chainedAccidental().accidental())
            .as("the barline written between the two notes does not reset what is in force")
            .isEqualTo(leadingFlat.chainedAccidental().accidental());
        assertThat(afterBarline.chainedAccidental().type()).isEqualTo(ChainType.REPETITION);
    }

    @Test
    void testAParenthesizedAccidentalReadsAsAPlainOne() {
        var plainFlat = accidentalNotes.get(PLAIN_FLAT_INDEX);
        var parenthesizedFlat = accidentalNotes.get(PARENTHESIZED_FLAT_INDEX);

        assertThat(parenthesizedFlat.chainedAccidental().accidental()).isEqualTo(Accidental.FLAT);
        assertThat(parenthesizedFlat.chainedAccidental().type())
            .as("sounding the same flat right after the plain one, with nothing between them, it is implicit — "
                + "exactly as a plain repeated accidental would be")
            .isEqualTo(ChainType.IMPLICIT);
        assertThat(plainFlat.chainedAccidental().type()).isEqualTo(ChainType.ANCHOR);
    }
}
