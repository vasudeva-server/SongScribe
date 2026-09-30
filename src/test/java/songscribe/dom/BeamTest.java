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

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;

/**
 * The sweep every edit runs through {@link Beam#outcomeFor}: an edit that deletes an endpoint of a
 * beam, or puts an element that is not beamable inside it, removes the beam and reports the
 * reason, and every other edit keeps it.
 */
class BeamTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    /** The beam every case edits around: an anchor, two interior members and an end. */
    private static final ElementRange BEAMED = new ElementRange(1, 4);

    private static final int ANCHOR_INDEX = BEAMED.begin();
    private static final int INTERIOR_INDEX = ANCHOR_INDEX + 1;
    private static final int END_INDEX = BEAMED.end();

    /** A note past the beam's end. */
    private static final int OUTSIDE_INDEX = END_INDEX + 1;

    private static final int LINE_LENGTH = OUTSIDE_INDEX + 1;

    /**
     * One edit to a line of quavers holding the beam over {@link #BEAMED}, and what it does to
     * the beam.
     *
     * @param description the edit, as the test's display name
     * @param edit        makes the edit, inside a modification bracket
     * @param reasonKey   the {@code Strings} key of the reason reported when the edit removes the
     *                    beam, or {@code null} when the beam is kept
     */
    private record SweepCase(String description, Consumer<Line> edit, @Nullable String reasonKey) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("sweepCases")
    void testAnEditKeepsOrRemovesTheBeamAsTheInvariantRequires(SweepCase testCase) {
        var song = buildSong(line -> {
            for (var i = 0; i < LINE_LENGTH; i++) {
                line.addElement(quaver());
            }
        });
        var line = song.getLine(FIRST_LINE);
        var beam = song.withoutMutationTrackingResult(() -> {
            var built = beamOver(line, BEAMED);
            built.addTo(line);

            return built;
        });
        var reports = new ReportRecorder();

        try {
            song.withModification(() -> testCase.edit().accept(line));

            var reasonKey = testCase.reasonKey();

            if (reasonKey == null) {
                assertThat(line.spanRegistry().findSpans(Beam.class)).containsExactly(beam);
                assertThat(reports.reports()).isEmpty();
                return;
            }

            assertThat(line.spanRegistry().findSpans(Beam.class)).isEmpty();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.DIALOG_REPORT_BEAMS_REMOVED);
                    assertThat(entry.description()).isEqualTo(Strings.get(reasonKey));
                }));
        } finally {
            reports.dispose();
        }
    }

    static Stream<SweepCase> sweepCases() {
        return Stream.of(
            new SweepCase("deleting the anchor removes it",
                line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED),
            new SweepCase("deleting the end removes it",
                line -> line.removeRange(ElementRange.single(END_INDEX)),
                Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED),
            new SweepCase("deleting an interior member keeps it",
                line -> line.removeRange(ElementRange.single(INTERIOR_INDEX)),
                null),
            new SweepCase("replacing an interior note with a crotchet removes it",
                line -> line.setElement(INTERIOR_INDEX, crotchet()),
                Strings.DIALOG_REPORT_REASON_BEAM_NOT_BEAMABLE),
            new SweepCase("replacing a note outside the beam keeps it",
                line -> line.setElement(OUTSIDE_INDEX, crotchet()),
                null),
            new SweepCase("an insertion inside the beam keeps it",
                line -> line.addElement(INTERIOR_INDEX, quaver()),
                null),
            new SweepCase("a modification of a member keeps it",
                line -> line.modifyElement(INTERIOR_INDEX, EnumSet.of(ElementField.DOT_COUNT),
                    () -> ((NoteElement) line.getElement(INTERIOR_INDEX)).setDots(Dots.ONE)),
                null)
        );
    }
}
