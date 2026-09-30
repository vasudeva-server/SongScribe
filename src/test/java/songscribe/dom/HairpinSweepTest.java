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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.quaverRest;

/**
 * The sweep every edit runs through {@link Hairpin#outcomeFor}. An insertion or a replacement
 * removes a hairpin that the change leaves breaking rule 1, 2 or 3 of the class invariant. A
 * deletion pulls a lost endpoint in to the nearest element that meets rule 1 or 2, and removes a
 * hairpin left with no such element or too narrow.
 */
class HairpinSweepTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    private static final ElementRange WIDE = new ElementRange(1, 4);
    private static final ElementRange NARROW = new ElementRange(1, 2);

    private static final int ANCHOR_INDEX = 1;
    private static final int INTERIOR_INDEX = 2;
    private static final int REST_INDEX = 3;
    private static final int END_INDEX = 4;

    /**
     * One edit to a line holding a crescendo, and what it does to the crescendo.
     *
     * @param description the edit, as the test's display name
     * @param elements    makes the elements of the line
     * @param hairpin     the range the crescendo covers
     * @param edit        makes the edit, inside a modification bracket
     * @param kept        the range the crescendo covers after the edit, or {@code null} when the
     *                    edit removes it
     * @param reasonKey   the {@code Strings} key of the reason reported when the edit removes the
     *                    crescendo, or {@code null} when the edit removes it unreported because
     *                    it deletes every element the crescendo covers
     */
    private record EditCase(
        String description,
        Supplier<List<StaffElement>> elements,
        ElementRange hairpin,
        Consumer<Line> edit,
        @Nullable ElementRange kept,
        @Nullable String reasonKey
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("editCases")
    void testAnEditReshapesOrRemovesTheHairpinAsTheClassInvariantRequires(EditCase testCase) {
        var elements = testCase.elements().get();
        var song = buildSong(line -> elements.forEach(line::addElement));
        var line = song.getLine(FIRST_LINE);

        song.withoutMutationTracking(
            () -> hairpinOver(line, Hairpin.Kind.CRESCENDO, testCase.hairpin()).addTo(line));

        var reports = new ReportRecorder();

        try {
            song.withModification(() -> testCase.edit().accept(line));

            var kept = testCase.kept();

            if (kept != null) {
                assertThat(line.spanRegistry().findSpans(Hairpin.class)).extracting(Hairpin::range)
                    .containsExactly(kept);
                return;
            }

            var reasonKey = testCase.reasonKey();

            assertThat(line.spanRegistry().findSpans(Hairpin.class)).isEmpty();

            if (reasonKey == null) {
                assertThat(reports.reports()).isEmpty();
                return;
            }

            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.DIALOG_REPORT_HAIRPINS_REMOVED);
                    assertThat(entry.description()).isEqualTo(Strings.get(reasonKey));
                }));
        } finally {
            reports.dispose();
        }
    }

    static Stream<EditCase> editCases() {
        return Stream.of(
            new EditCase("replacing the anchor with a rest removes it",
                HairpinSweepTest::quavers, WIDE, line -> line.setElement(ANCHOR_INDEX, quaverRest()),
                null, Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID),
            new EditCase("replacing the end with a rest removes it",
                HairpinSweepTest::quavers, WIDE, line -> line.setElement(END_INDEX, quaverRest()),
                null, Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID),
            new EditCase("replacing the anchor with a grace note that leaves one column removes it",
                HairpinSweepTest::quavers, NARROW, line -> line.setElement(ANCHOR_INDEX, graceQuaver()),
                null, Strings.DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_COLUMNS),
            new EditCase("replacing an interior note with a rest keeps it",
                HairpinSweepTest::quavers, WIDE, line -> line.setElement(INTERIOR_INDEX, quaverRest()),
                WIDE, null),
            new EditCase("an insertion inside it keeps it, one element wider",
                HairpinSweepTest::quavers, WIDE, line -> line.addElement(INTERIOR_INDEX, quaver()),
                new ElementRange(WIDE.begin(), WIDE.end() + 1), null),

            new EditCase("deleting an interior note keeps it, one element narrower",
                HairpinSweepTest::quavers, WIDE, line -> line.removeRange(ElementRange.single(INTERIOR_INDEX)),
                new ElementRange(WIDE.begin(), WIDE.end() - 1), null),
            new EditCase("deleting the anchor moves it to the next element that can anchor",
                HairpinSweepTest::quavers, WIDE, line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                new ElementRange(WIDE.begin(), WIDE.end() - 1), null),
            new EditCase("deleting the end moves it to the last surviving note",
                HairpinSweepTest::quavers, WIDE, line -> line.removeRange(ElementRange.single(END_INDEX)),
                new ElementRange(WIDE.begin(), WIDE.end() - 1), null),
            new EditCase("deleting the anchor skips a rest to the next note",
                HairpinSweepTest::quaversWithRestAfterAnchor, new ElementRange(ANCHOR_INDEX, END_INDEX),
                line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                new ElementRange(ANCHOR_INDEX + 1, END_INDEX - 1), null),
            new EditCase("deleting the end skips a rest to the last surviving note",
                HairpinSweepTest::quaversWithRestBeforeEnd, WIDE,
                line -> line.removeRange(ElementRange.single(END_INDEX)),
                new ElementRange(WIDE.begin(), REST_INDEX - 1), null),
            new EditCase("deleting all but one column removes it",
                HairpinSweepTest::quavers, NARROW, line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                null, Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED),
            new EditCase("deleting every element it covers removes it without a report",
                HairpinSweepTest::quavers, NARROW, line -> line.removeRange(NARROW),
                null, null)
        );
    }

    private static final int LINE_LENGTH = 6;

    private static List<StaffElement> quavers() {
        return Stream.generate(StaffElementFactory::quaver)
            .limit(LINE_LENGTH)
            .map(StaffElement.class::cast)
            .toList();
    }

    /** Quavers, with a rest right after the anchor of {@link #WIDE}. */
    private static List<StaffElement> quaversWithRestAfterAnchor() {
        return withRestAt(ANCHOR_INDEX + 1);
    }

    /** Quavers, with a rest right before the end of {@link #WIDE}. */
    private static List<StaffElement> quaversWithRestBeforeEnd() {
        return withRestAt(REST_INDEX);
    }

    private static List<StaffElement> withRestAt(int restIndex) {
        var elements = new ArrayList<>(quavers());
        elements.set(restIndex, quaverRest());

        return elements;
    }
}
