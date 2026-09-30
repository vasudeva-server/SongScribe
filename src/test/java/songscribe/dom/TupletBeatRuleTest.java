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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.SongFactory.LineBuilder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.DOTTED_BEAT_TEMPO;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.dupletOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * The beat rule of {@link Tuplet}, run when the outermost modification bracket closes: an edit
 * that redefines the beat removes, from the place it redefines it onward, each tuplet that
 * {@link TupletValidator.Strictness#STRICT} no longer accepts, and leaves the tuplets before that
 * place alone.
 *
 * <p>Under the song's default crotchet beat, three quavers under a 3 are a valid triplet; under a
 * dotted-crotchet beat they are not, because the number then says what the beat already says. A
 * duplet of two quavers is the other way round: valid under a dotted-crotchet beat, not under a
 * crotchet beat. Each case sets a beat so that exactly the tuplets after the edit stop being
 * valid.
 */
class TupletBeatRuleTest extends UnitTest {

    private static final int BPM_STEP = 10;

    private static final BeatChange DOTTED_BEAT_CHANGE = new BeatChange(BeatUnit.CROTCHET, BeatUnit.CROTCHET_DOTTED);

    private static final int FIRST_LINE = 0;

    /** The base fixture: one line of quavers holding two triplets, one after the other. */
    private static final int BASE_LENGTH = 6;
    private static final ElementRange FIRST_TRIPLET = new ElementRange(0, 2);
    private static final ElementRange SECOND_TRIPLET = new ElementRange(3, 5);

    /**
     * The carrier fixture: a line of quavers holding a triplet, then an element that carries a
     * dotted beat, then a duplet that is valid only under that beat.
     */
    private static final int CARRIER_LENGTH = 8;
    private static final ElementRange TRIPLET_BEFORE_CARRIER = new ElementRange(0, 2);
    private static final int CARRIER_INDEX = 3;
    private static final ElementRange DUPLET_AFTER_CARRIER = new ElementRange(4, 5);

    /**
     * The three-line carrier fixture: a line holding a triplet, a line of one quaver that carries
     * the dotted beat, and a line holding the duplet.
     */
    private static final int TRIPLET_LINE_LENGTH = 3;
    private static final int CARRIER_LINE = 1;
    private static final int DUPLET_LINE = 2;
    private static final ElementRange DUPLET_ON_ITS_LINE = new ElementRange(0, 1);

    /** A song, and the tuplets placed in it, in the order the case's expectations list them. */
    private record Fixture(Song song, List<Tuplet> tuplets) {}

    /**
     * One edit to a fixture, and whether each of its tuplets survives.
     *
     * @param description  the edit, as the test's display name
     * @param buildFixture builds a fresh song and the tuplets in it
     * @param edit         makes the edit, inside a modification bracket
     * @param kept         whether each of the fixture's tuplets, in order, is still in the song
     */
    private record BeatCase(
        String description, Supplier<Fixture> buildFixture, Consumer<Song> edit, List<Boolean> kept
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("beatCases")
    void testABeatDefiningEditRemovesTheTupletsTheBeatNoLongerValidatesFromThatPointOn(BeatCase testCase) {
        var fixture = testCase.buildFixture().get();
        var song = fixture.song();

        song.withModification(() -> testCase.edit().accept(song));

        var remaining = song.getLines().stream()
            .flatMap(line -> line.spanRegistry().findSpans(Tuplet.class).stream())
            .toList();

        for (var i = 0; i < fixture.tuplets().size(); i++) {
            assertThat(remaining.contains(fixture.tuplets().get(i)))
                .as("tuplet %d is kept", i)
                .isEqualTo(testCase.kept().get(i));
        }
    }

    static Stream<BeatCase> beatCases() {
        return Stream.of(
            new BeatCase("a song tempo with a new beat removes every tuplet",
                TupletBeatRuleTest::baseFixture,
                song -> song.setTempo(DOTTED_BEAT_TEMPO),
                List.of(false, false)),
            new BeatCase("a song tempo that keeps the beat removes none",
                TupletBeatRuleTest::baseFixture,
                song -> song.setTempo(new Tempo(
                    Tempo.DEFAULT_BPM + BPM_STEP, Tempo.DEFAULT_TYPE, Tempo.DEFAULT_MARKING)),
                List.of(true, true)),
            new BeatCase("a tempo change set on the second triplet's anchor removes it and keeps the first",
                TupletBeatRuleTest::baseFixture,
                song -> setAttachment(song.getLine(FIRST_LINE), SECOND_TRIPLET.begin(),
                    ElementField.TEMPO_CHANGE, new TempoChangeAttachment(DOTTED_BEAT_TEMPO)),
                List.of(true, false)),
            new BeatCase("a beat change set on the second triplet's anchor removes it and keeps the first",
                TupletBeatRuleTest::baseFixture,
                song -> setAttachment(song.getLine(FIRST_LINE), SECOND_TRIPLET.begin(),
                    ElementField.BEAT_CHANGE, new BeatChangeAttachment(DOTTED_BEAT_CHANGE)),
                List.of(true, false)),
            new BeatCase("removing a tempo change removes the duplet it made valid",
                () -> carrierFixture(new TempoChangeAttachment(DOTTED_BEAT_TEMPO)),
                song -> song.getLine(FIRST_LINE).modifyElement(CARRIER_INDEX, ElementField.TEMPO_CHANGE,
                    () -> AttachmentRemoval.removeTempoChange(song.getLine(FIRST_LINE).getElement(CARRIER_INDEX))),
                List.of(true, false)),
            new BeatCase("removing a beat change removes the duplet it made valid",
                () -> carrierFixture(new BeatChangeAttachment(DOTTED_BEAT_CHANGE)),
                song -> song.getLine(FIRST_LINE).modifyElement(CARRIER_INDEX, ElementField.BEAT_CHANGE,
                    () -> AttachmentRemoval.removeBeatChange(song.getLine(FIRST_LINE).getElement(CARRIER_INDEX))),
                List.of(true, false)),
            new BeatCase("deleting the element that carries the beat removes the duplet it made valid",
                () -> carrierFixture(new TempoChangeAttachment(DOTTED_BEAT_TEMPO)),
                song -> song.getLine(FIRST_LINE).removeRange(ElementRange.single(CARRIER_INDEX)),
                List.of(true, false)),
            new BeatCase("deleting the line that holds the beat removes the duplet it made valid",
                TupletBeatRuleTest::carrierLinesFixture,
                song -> song.removeLine(CARRIER_LINE),
                List.of(true, false))
        );
    }

    private static void setAttachment(Line line, int index, ElementField field, Attachment attachment) {
        line.modifyElement(index, field, () -> line.getElement(index).addAttachment(attachment));
    }

    /** {@code length} quavers, in one line. */
    private static LineBuilder quavers(int length) {
        return line -> {
            for (var i = 0; i < length; i++) {
                line.addElement(quaver());
            }
        };
    }

    private static Tuplet placeTriplet(Line line, ElementRange range) {
        return place(line, tripletOver(line, range, NoteValue.EIGHTH));
    }

    private static Tuplet placeDuplet(Line line, ElementRange range) {
        return place(line, dupletOver(line, range, NoteValue.EIGHTH));
    }

    private static Tuplet place(Line line, Tuplet tuplet) {
        tuplet.addTo(line);

        return tuplet;
    }

    /** Two triplets on a line of quavers, in the song's default crotchet beat. */
    private static Fixture baseFixture() {
        var song = buildSong(quavers(BASE_LENGTH));
        var line = song.getLine(FIRST_LINE);
        var tuplets = song.withoutMutationTrackingResult(() -> List.of(
            placeTriplet(line, FIRST_TRIPLET), placeTriplet(line, SECOND_TRIPLET)));

        return new Fixture(song, tuplets);
    }

    /**
     * A triplet, then an element carrying {@code carrier}, then a duplet, on one line: the duplet
     * is valid while {@code carrier} sets the dotted beat.
     */
    private static Fixture carrierFixture(Attachment carrier) {
        var song = buildSong(quavers(CARRIER_LENGTH));
        var line = song.getLine(FIRST_LINE);
        var tuplets = song.withoutMutationTrackingResult(() -> {
            line.getElement(CARRIER_INDEX).addAttachment(carrier);

            return List.of(
                placeTriplet(line, TRIPLET_BEFORE_CARRIER), placeDuplet(line, DUPLET_AFTER_CARRIER));
        });

        return new Fixture(song, tuplets);
    }

    /** A triplet, then a line whose one element carries the dotted beat, then a line holding a duplet. */
    private static Fixture carrierLinesFixture() {
        var song = buildSong(quavers(TRIPLET_LINE_LENGTH), quavers(1), quavers(DUPLET_ON_ITS_LINE.end() + 1));
        var tripletLine = song.getLine(FIRST_LINE);
        var dupletLine = song.getLine(DUPLET_LINE);
        var tuplets = song.withoutMutationTrackingResult(() -> {
            song.getLine(CARRIER_LINE).getElement(0).addAttachment(new TempoChangeAttachment(DOTTED_BEAT_TEMPO));

            return List.of(
                placeTriplet(tripletLine, TRIPLET_BEFORE_CARRIER), placeDuplet(dupletLine, DUPLET_ON_ITS_LINE));
        });

        return new Fixture(song, tuplets);
    }
}
