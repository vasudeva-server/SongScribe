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
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link SpanLoadPass#run}: the one place a read hairpin becomes a {@link Hairpin}. A pending
 * hairpin that {@link Hairpin#spanning} accepts is added over the range it names, with the kind and
 * the two offsets the file stated; one it refuses is dropped and reported with its reason.
 */
class HairpinLoadPassTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int LINE = 0;

    /** The three elements every single-hairpin case spans. */
    private static final ElementRange SPAN = new ElementRange(0, 2);

    /** The range of a second hairpin that shares more than one element with one over {@link #SPAN}. */
    private static final ElementRange OVERLAPPING_SPAN = new ElementRange(1, 3);

    private static final String FOUR_NOTES = PITCH + " " + PITCH + " " + PITCH + " " + PITCH;

    private static final double X_OFFSET_SS = 0.75;
    private static final double Y_OFFSET_SS = 1.5;

    /**
     * One pending hairpin over {@link #SPAN} that the pass adds.
     *
     * @param description the case, as the test's display name
     * @param kind        the kind the file states
     */
    private record AddedCase(String description, Hairpin.Kind kind) {}

    /**
     * One pending crescendo over {@link #SPAN} that the pass drops.
     *
     * @param description the case, as the test's display name
     * @param notation    the song's one line, in compact notation
     * @param refusalKey  the reason reported for the dropped hairpin
     */
    private record DroppedCase(String description, String notation, String refusalKey) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("addedCases")
    void testAPendingHairpinIsAddedWithItsKindAndBothOffsets(AddedCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, PITCH + " " + PITCH + " " + PITCH);
        var line = song.getLine(LINE);
        var report = settle(song, List.of(pending(testCase.kind(), line, SPAN)));

        assertThat(report.isEmpty()).isTrue();
        assertThat(line.spanRegistry().findSpans(Hairpin.class)).singleElement().satisfies(hairpin -> {
            assertThat(hairpin.getKind()).isEqualTo(testCase.kind());
            assertThat(hairpin.range()).isEqualTo(SPAN);
            assertThat(hairpin.getUserXOffsetSs()).isEqualTo(X_OFFSET_SS);
            assertThat(hairpin.getUserYOffsetSs()).isEqualTo(Y_OFFSET_SS);
        });
    }

    static Stream<AddedCase> addedCases() {
        return Stream.of(Hairpin.Kind.values())
            .map(kind -> new AddedCase("a " + kind + " over three notes is added as the file states it", kind));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("droppedCases")
    void testAPendingHairpinTheFactoryRefusesIsDroppedWithItsReason(DroppedCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.notation());
        var line = song.getLine(LINE);
        var report = settle(song, List.of(pending(Hairpin.Kind.CRESCENDO, line, SPAN)));

        assertThat(line.spanRegistry().findSpans(Hairpin.class)).isEmpty();
        assertThat(report.changes()).containsExactly(new ReportedChange(
            Strings.REPORT_HAIRPINS_REMOVED,
            Strings.get(testCase.refusalKey()),
            ElementLocationRange.onLine(LINE, SPAN)));
    }

    static Stream<DroppedCase> droppedCases() {
        return Stream.of(
            new DroppedCase("a hairpin that ends on a rest is dropped",
                PITCH + " " + PITCH + " R", Strings.REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE)
        );
    }

    @ParameterizedTest(name = "{0} first")
    @MethodSource("oppositeKindOrders")
    void testOfTwoOverlappingPendingHairpinsOfOppositeKindsTheLaterIsDropped(Hairpin.Kind first) {
        var song = compactSong(Key.NO_ACCIDENTALS, FOUR_NOTES);
        var line = song.getLine(LINE);
        var second = first.opposite();

        var report = settle(song, List.of(pending(first, line, SPAN), pending(second, line, OVERLAPPING_SPAN)));

        assertThat(line.spanRegistry().findSpans(Hairpin.class)).singleElement().satisfies(hairpin -> {
            assertThat(hairpin.getKind()).isEqualTo(first);
            assertThat(hairpin.range()).isEqualTo(SPAN);
        });
        assertThat(report.changes()).containsExactly(new ReportedChange(
            Strings.REPORT_HAIRPINS_REMOVED,
            Strings.get(Strings.REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP),
            ElementLocationRange.onLine(LINE, OVERLAPPING_SPAN)));
    }

    static Stream<Hairpin.Kind> oppositeKindOrders() {
        return Stream.of(Hairpin.Kind.values());
    }

    /** Settles {@code song} with {@code hairpins} as the only pending spans the reader found. */
    private static ChangeReport settle(Song song, List<PendingHairpin> hairpins) {
        return new ParsedSong(song, List.of(), List.of(), hairpins, List.of()).settle();
    }

    private static PendingHairpin pending(Hairpin.Kind kind, Line line, ElementRange range) {
        return new PendingHairpin(
            kind, line.getElement(range.begin()), line.getElement(range.end()), X_OFFSET_SS, Y_OFFSET_SS);
    }
}
