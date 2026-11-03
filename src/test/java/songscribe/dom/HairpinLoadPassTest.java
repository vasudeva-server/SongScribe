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
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.loadCompactSong;

/**
 * {@link SpanLoadPass#run}: the one place a read hairpin becomes a {@link Hairpin}. A pending
 * hairpin that {@link Hairpin#spanning} accepts is added over the range it names, with the kind and
 * the two offsets the file stated; one it refuses is dropped and reported with its reason.
 */
class HairpinLoadPassTest extends UnitTest {

    private static final int PITCH = 3;

    /** The line under test: the second, behind a line of one note. */
    private static final int LINE = 1;
    private static final String LEADING_LINE = String.valueOf(PITCH);

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
    void testAPendingHairpinIsAddedWithItsKindAndBothOffsets(AddedCase testCase) throws StrandedKeyChangeException {
        var loaded = load(PITCH + " " + PITCH + " " + PITCH, line -> List.of(pending(testCase.kind(), line, SPAN)));
        var line = loaded.song().getLine(LINE);

        assertThat(loaded.report().isEmpty()).isTrue();
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
    void testAPendingHairpinTheFactoryRefusesIsDroppedWithItsReason(DroppedCase testCase)
        throws StrandedKeyChangeException {
        var loaded = load(testCase.notation(), line -> List.of(pending(Hairpin.Kind.CRESCENDO, line, SPAN)));

        assertThat(loaded.song().getLine(LINE).spanRegistry().findSpans(Hairpin.class)).isEmpty();
        assertThat(loaded.report().changes()).containsExactly(new ReportedChange(
            ChangeHeading.HAIRPINS_REMOVED,
            Strings.get(testCase.refusalKey()),
            new LineRange(LINE, LINE)));
    }

    static Stream<DroppedCase> droppedCases() {
        return Stream.of(
            new DroppedCase("a hairpin that ends on a rest is dropped",
                PITCH + " " + PITCH + " R", Strings.REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE)
        );
    }

    @ParameterizedTest(name = "{0} first")
    @MethodSource("oppositeKindOrders")
    void testOfTwoOverlappingPendingHairpinsOfOppositeKindsTheLaterIsDropped(Hairpin.Kind first)
        throws StrandedKeyChangeException {
        var second = first.opposite();

        var loaded = load(FOUR_NOTES,
            line -> List.of(pending(first, line, SPAN), pending(second, line, OVERLAPPING_SPAN)));

        assertThat(loaded.song().getLine(LINE).spanRegistry().findSpans(Hairpin.class)).singleElement().satisfies(hairpin -> {
            assertThat(hairpin.getKind()).isEqualTo(first);
            assertThat(hairpin.range()).isEqualTo(SPAN);
        });
        assertThat(loaded.report().changes()).containsExactly(new ReportedChange(
            ChangeHeading.HAIRPINS_REMOVED,
            Strings.get(Strings.REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP),
            new LineRange(LINE, LINE)));
    }

    static Stream<Hairpin.Kind> oppositeKindOrders() {
        return Stream.of(Hairpin.Kind.values());
    }

    /**
     * Loads a song whose {@link #LINE} is {@code notation}, behind a line of one note, with the
     * hairpins {@code hairpinsOn} names over that line as the only marks the reader found.
     */
    private static SongLoad.LoadedSong<SongFactory.LoadBuild> load(
        String notation, Function<Line, List<PendingHairpin>> hairpinsOn
    ) throws StrandedKeyChangeException {
        return loadCompactSong(
            song -> new PendingMarks(
                List.of(), List.of(), hairpinsOn.apply(song.getLine(LINE)), List.of(), List.of(), List.of()),
            Key.NO_ACCIDENTALS, LEADING_LINE, notation);
    }

    private static PendingHairpin pending(Hairpin.Kind kind, Line line, ElementRange range) {
        return new PendingHairpin(
            kind, line.getElement(range.begin()), line.getElement(range.end()), new UserOffset(X_OFFSET_SS, Y_OFFSET_SS));
    }
}
