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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link HairpinLoadPass#run}: the one place a read hairpin becomes a {@link Hairpin}. A pending
 * hairpin that {@link Hairpin#spanning} accepts is added with the shifts and offset the file
 * stated; one it refuses is dropped and reported with its reason.
 */
class HairpinLoadPassTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int LINE = 0;

    /** The three elements every case's hairpin spans. */
    private static final ElementRange SPAN = new ElementRange(0, 2);

    private static final double X1_SHIFT_SS = 0.75;
    private static final double X2_SHIFT_SS = -0.5;
    private static final double Y_OFFSET_SS = 1.5;

    /**
     * One pending crescendo over {@link #SPAN}, and what the pass makes of it.
     *
     * @param description the case, as the test's display name
     * @param notation    the song's one line, in compact notation
     * @param refusalKey  the reason reported for a dropped hairpin, or null when it is added
     */
    private record LoadCase(String description, String notation, @Nullable String refusalKey) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("loadCases")
    void testAPendingHairpinIsAddedWithItsShiftsAndOffsetOrDroppedWithItsReason(LoadCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.notation());
        var line = song.getLine(LINE);
        var report = new ChangeReport();
        var pending = new PendingHairpin(
            Hairpin.Kind.CRESCENDO, line.getElement(SPAN.begin()), line.getElement(SPAN.end()),
            X1_SHIFT_SS, X2_SHIFT_SS, Y_OFFSET_SS);

        HairpinLoadPass.run(song, List.of(pending), report);

        var hairpins = line.spanRegistry().findSpans(Hairpin.class);
        var refusalKey = testCase.refusalKey();

        if (refusalKey != null) {
            assertThat(hairpins).isEmpty();
            assertThat(report.changes()).containsExactly(new ReportedChange(
                Strings.DIALOG_REPORT_HAIRPINS_REMOVED, Strings.get(refusalKey), ElementLocationRange.onLine(LINE, SPAN)));
            return;
        }

        assertThat(report.isEmpty()).isTrue();
        assertThat(hairpins).singleElement().satisfies(hairpin -> {
            assertThat(hairpin.getKind()).isEqualTo(Hairpin.Kind.CRESCENDO);
            assertThat(hairpin.range()).isEqualTo(SPAN);
            assertThat(hairpin.getX1ShiftSs()).isEqualTo(X1_SHIFT_SS);
            assertThat(hairpin.getX2ShiftSs()).isEqualTo(X2_SHIFT_SS);
            assertThat(hairpin.getUserYOffsetSs()).isEqualTo(Y_OFFSET_SS);
        });
    }

    static Stream<LoadCase> loadCases() {
        return Stream.of(
            new LoadCase("a hairpin over three notes is added as the file states it",
                PITCH + " " + PITCH + " " + PITCH, null),
            new LoadCase("a hairpin that ends on a rest is dropped",
                PITCH + " " + PITCH + " R", Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE)
        );
    }
}
