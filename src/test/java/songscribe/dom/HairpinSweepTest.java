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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.ReportRecorder;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SongFactory.quavers;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.quaverRest;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * The sweep every edit runs through {@link Hairpin#outcomeFor}. An insertion or a replacement
 * removes a hairpin that the change leaves breaking rule 1, 2 or 3 of the class invariant. A
 * deletion pulls a lost endpoint in to the nearest element that meets rule 1 or 2, and removes a
 * hairpin left with no such element, with a surviving anchor that can no longer begin it, or too
 * narrow.
 */
class HairpinSweepTest extends UnitTest {

    private static final int FIRST_LINE = 0;

    private static final ElementRange WIDE = new ElementRange(1, 4);
    private static final ElementRange NARROW = new ElementRange(1, 2);

    private static final int ANCHOR_INDEX = 1;
    private static final int INTERIOR_INDEX = 2;
    private static final int REST_INDEX = 3;
    private static final int END_INDEX = 4;

    private static final int LINE_LENGTH = 6;

    /** The grace note that anchors the hairpin of the unpaired-grace-note case. */
    private static final int GRACE_INDEX = 0;

    /** The note that hosts the grace note. */
    private static final int HOST_INDEX = 1;

    /** The last note of the line of the unpaired-grace-note case: grace, host, barline, note, note. */
    private static final int GRACE_LINE_END_INDEX = 4;

    private static final ElementRange FROM_GRACE_NOTE = new ElementRange(GRACE_INDEX, GRACE_LINE_END_INDEX);

    /** The two notes after the barline, once the host is deleted: grace, barline, note, note. */
    private static final ElementRange PAST_THE_BARLINE = new ElementRange(2, 3);

    /** What one edit to a line holding a crescendo starts from. */
    private interface HairpinEdit {

        /** The edit, as the test's display name. */
        String description();

        /** Makes the elements of the line. */
        Supplier<List<StaffElement>> elements();

        /** The range the crescendo covers. */
        ElementRange hairpin();

        /** Makes the edit, inside a modification bracket. */
        Consumer<Line> edit();
    }

    /**
     * An edit that leaves the crescendo on the line, unreported.
     *
     * @param description the edit, as the test's display name
     * @param elements    makes the elements of the line
     * @param hairpin     the range the crescendo covers
     * @param edit        makes the edit, inside a modification bracket
     * @param kept        the range the crescendo covers after the edit
     */
    private record KeepCase(
        String description,
        Supplier<List<StaffElement>> elements,
        ElementRange hairpin,
        Consumer<Line> edit,
        ElementRange kept
    ) implements HairpinEdit {}

    /**
     * An edit that removes the crescendo and reports why.
     *
     * @param description the edit, as the test's display name
     * @param elements    makes the elements of the line
     * @param hairpin     the range the crescendo covers
     * @param edit        makes the edit, inside a modification bracket
     * @param reasonKey   the {@code Strings} key of the reason reported
     */
    private record RemoveCase(
        String description,
        Supplier<List<StaffElement>> elements,
        ElementRange hairpin,
        Consumer<Line> edit,
        String reasonKey
    ) implements HairpinEdit {}

    /**
     * An edit that deletes every element the crescendo covers, so the crescendo goes with them
     * and nothing is reported.
     *
     * @param description the edit, as the test's display name
     * @param elements    makes the elements of the line
     * @param hairpin     the range the crescendo covers
     * @param edit        makes the edit, inside a modification bracket
     */
    private record UnreportedRemovalCase(
        String description,
        Supplier<List<StaffElement>> elements,
        ElementRange hairpin,
        Consumer<Line> edit
    ) implements HairpinEdit {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("keepCases")
    void testAnEditThatLeavesTheInvariantIntactKeepsTheHairpinReshapedAndUnreported(KeepCase testCase) {
        editAndAssert(testCase, (line, reports) -> {
            assertThat(line.spanRegistry().findSpans(Hairpin.class)).extracting(Hairpin::range)
                .containsExactly(testCase.kept());
            assertThat(reports.reports()).as("a kept hairpin is not reported").isEmpty();
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("removeCases")
    void testAnEditThatBreaksTheInvariantRemovesTheHairpinAndReportsTheReason(RemoveCase testCase) {
        editAndAssert(testCase, (line, reports) -> {
            assertThat(line.spanRegistry().findSpans(Hairpin.class)).isEmpty();
            assertThat(reports.reports()).singleElement().satisfies(entries ->
                assertThat(entries).singleElement().satisfies(entry -> {
                    assertThat(entry.headingKey()).isEqualTo(Strings.DIALOG_REPORT_HAIRPINS_REMOVED);
                    assertThat(entry.description()).isEqualTo(Strings.get(testCase.reasonKey()));
                }));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreportedRemovalCases")
    void testADeletionOfEveryElementTheHairpinCoversRemovesItWithoutAReport(UnreportedRemovalCase testCase) {
        editAndAssert(testCase, (line, reports) -> {
            assertThat(line.spanRegistry().findSpans(Hairpin.class)).isEmpty();
            assertThat(reports.reports()).as("the user deleted the hairpin, so nothing is reported").isEmpty();
        });
    }

    /**
     * Puts a crescendo over {@code testCase}'s range on a real song, runs its edit in a
     * modification bracket, and hands the line and the reports the edit delivered to
     * {@code assertions}.
     */
    private static void editAndAssert(HairpinEdit testCase, BiConsumer<Line, ReportRecorder> assertions) {
        var elements = testCase.elements().get();
        var song = buildSong(line -> elements.forEach(line::addElement));
        var line = song.getLine(FIRST_LINE);

        addWithoutTracking(song, () -> hairpinOver(line, Hairpin.Kind.CRESCENDO, testCase.hairpin()));

        ReportRecorder.whileRecording(reports -> {
            song.withModification(() -> testCase.edit().accept(line));

            assertions.accept(line, reports);
        });
    }

    static Stream<KeepCase> keepCases() {
        return Stream.of(
            new KeepCase("replacing an interior note with a rest keeps it",
                () -> quavers(LINE_LENGTH), WIDE, line -> line.setElement(INTERIOR_INDEX, quaverRest()),
                WIDE),
            new KeepCase("an insertion inside it keeps it, one element wider",
                () -> quavers(LINE_LENGTH), WIDE, line -> line.addElement(INTERIOR_INDEX, quaver()),
                new ElementRange(WIDE.begin(), WIDE.end() + 1)),
            new KeepCase("deleting an interior note keeps it, one element narrower",
                () -> quavers(LINE_LENGTH), WIDE, line -> line.removeRange(ElementRange.single(INTERIOR_INDEX)),
                new ElementRange(WIDE.begin(), WIDE.end() - 1)),
            new KeepCase("deleting the anchor moves it to the next element that can anchor",
                () -> quavers(LINE_LENGTH), WIDE, line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                new ElementRange(WIDE.begin(), WIDE.end() - 1)),
            new KeepCase("deleting the end moves it to the last surviving note",
                () -> quavers(LINE_LENGTH), WIDE, line -> line.removeRange(ElementRange.single(END_INDEX)),
                new ElementRange(WIDE.begin(), WIDE.end() - 1)),
            new KeepCase("deleting the anchor skips a rest to the next note",
                HairpinSweepTest::quaversWithRestAfterAnchor, new ElementRange(ANCHOR_INDEX, END_INDEX),
                line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                new ElementRange(ANCHOR_INDEX + 1, END_INDEX - 1)),
            new KeepCase("deleting the end skips a rest to the last surviving note",
                HairpinSweepTest::quaversWithRestBeforeEnd, WIDE,
                line -> line.removeRange(ElementRange.single(END_INDEX)),
                new ElementRange(WIDE.begin(), REST_INDEX - 1)),
            new KeepCase("deleting the host of an unpaired grace note anchor moves it past the grace note",
                HairpinSweepTest::pairedGraceNoteBeforeBarline, FROM_GRACE_NOTE,
                line -> {
                    line.modifyElement(GRACE_INDEX, ElementField.SLIDE, line.noteAt(GRACE_INDEX)::removeSlide);
                    line.deleteRange(line.effectiveRange(ElementRange.single(HOST_INDEX)));
                },
                PAST_THE_BARLINE)
        );
    }

    static Stream<RemoveCase> removeCases() {
        return Stream.of(
            new RemoveCase("replacing the anchor with a rest removes it",
                () -> quavers(LINE_LENGTH), WIDE, line -> line.setElement(ANCHOR_INDEX, quaverRest()),
                Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID),
            new RemoveCase("replacing the end with a rest removes it",
                () -> quavers(LINE_LENGTH), WIDE, line -> line.setElement(END_INDEX, quaverRest()),
                Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID),
            new RemoveCase("replacing the anchor with a grace note that leaves one column removes it",
                () -> quavers(LINE_LENGTH), NARROW, line -> line.setElement(ANCHOR_INDEX, graceQuaver()),
                Strings.DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_NOTES),
            new RemoveCase("deleting all but one column removes it",
                () -> quavers(LINE_LENGTH), NARROW, line -> line.removeRange(ElementRange.single(ANCHOR_INDEX)),
                Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED)
        );
    }

    static Stream<UnreportedRemovalCase> unreportedRemovalCases() {
        return Stream.of(
            new UnreportedRemovalCase("deleting every element it covers removes it without a report",
                () -> quavers(LINE_LENGTH), NARROW, line -> line.removeRange(NARROW))
        );
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
        var elements = new ArrayList<>(quavers(LINE_LENGTH));
        elements.set(restIndex, quaverRest());

        return elements;
    }

    /**
     * A glissando grace note, its host, a single barline and two notes: the grace note and its
     * host are paired, and the barline stands between the host and the notes after it.
     */
    private static List<StaffElement> pairedGraceNoteBeforeBarline() {
        var grace = graceQuaver();
        grace.setSlide(Slide.GLISSANDO);

        return List.of(grace, quaver(), singleBarline(), quaver(), quaver());
    }
}
