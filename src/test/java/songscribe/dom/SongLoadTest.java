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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.BatchReplay.BatchRecorder;
import songscribe.dom.SongFactory.LoadBuild;
import songscribe.dom.SongFactory.LineBuilder;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static songscribe.dom.SongFactory.addLines;
import static songscribe.dom.SongFactory.loadCompactSong;
import static songscribe.dom.SongFactory.loadSong;
import static songscribe.dom.SongFactory.notesAroundKeyChange;
import static songscribe.dom.SpanFactory.TRIPLET_NORMAL_NOTES;
import static songscribe.dom.SpanFactory.ratioOf;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * {@link SongLoad#run}: the report it returns, the order its stages run in, the refusal of a song
 * that holds a stranded key change, and the song it hands back.
 */
class SongLoadTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;

    /** The line the report test's changes are made on, behind a line of its own. */
    private static final int REPORTED_LINE_INDEX = 1;

    private static final LineRange THE_LINE = new LineRange(REPORTED_LINE_INDEX, REPORTED_LINE_INDEX);

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

    private static final double NO_OFFSET_SS = 0.0;

    private static final int PITCH = 3;

    /** The staff position of the first sharp of a sharp key: a bare note there sounds the key's sharp. */
    private static final int FIRST_SHARPED_POSITION = -4;

    private static final Key DOCUMENT_KEY = Key.NO_ACCIDENTALS;

    /** A key a line establishes for itself, and that a key change on it then restates. */
    private static final Key OWN_KEY = Key.TWO_SHARPS;

    /** A key a mid-line key change really changes to, and that a later key change then restates. */
    private static final Key CHANGED_KEY = Key.THREE_FLATS;

    private static final String TIED_QUAVERS = "Q Q";


    private static final String SYLLABLE = "a";
    private static final String NEXT_SYLLABLE = "b";

    private static final List<ElementType> TWO_NOTES_AND_TERMINAL =
        List.of(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.FINAL_DOUBLE_BARLINE);

    /** Loads a case's song. */
    @FunctionalInterface
    interface SongSource {
        SongLoad.LoadedSong<LoadBuild> load() throws StrandedKeyChangeException;
    }

    /** Asserts what a case's load must have done. */
    @FunctionalInterface
    interface Verification {
        void verify(SongLoad.LoadedSong<LoadBuild> loaded);
    }

    @Test
    void testTheReportHoldsTheBuildsEntriesAndThenTheStagesEntriesEachInTheOrderRecorded()
        throws StrandedKeyChangeException {
        var grade = TupletGrade.TRIPLET;
        var sameCount = ratioOf(grade.number(), NoteValue.QUARTER);

        var loaded = SongLoad.<LoadBuild, RuntimeException, RuntimeException>run(song -> {
            addLines(song,
                leadingLine -> leadingLine.addElement(crotchet()),
                line -> {
                    for (var i = 0; i < BARLINE_INDEX; i++) {
                        line.addElement(crotchet());
                    }

                    line.addElement(singleBarline());
                    line.addElement(breathMark());
                    line.addElement(crotchet());
                    line.addElement(crotchetRest());
                });
            song.report(RecordedChange.accidentalsConverted());

            var elements = song.getLine(REPORTED_LINE_INDEX).getElements();

            return new LoadBuild(new PendingMarks(
                List.of(pendingTuplet(elements, DERIVED_TUPLET, grade, null),
                    pendingTuplet(elements, DROPPED_TUPLET, grade, sameCount)),
                List.of(new PendingEnding(
                    elements.get(DROPPED_ENDING.begin()), elements.get(DROPPED_ENDING.end()),
                    UserOffset.NONE)),
                List.of(new PendingHairpin(
                    Hairpin.Kind.CRESCENDO, elements.get(DROPPED_HAIRPIN.begin()),
                    elements.get(DROPPED_HAIRPIN.end()), UserOffset.NONE)),
                List.of(), List.of(), List.of()));
        });

        assertThat(loaded.report().changes()).containsExactly(
            new ReportedChange(
                ChangeHeading.ACCIDENTALS_CONVERTED,
                Strings.get(Strings.REPORT_ACCIDENTALS_DETAIL),
                null),
            new ReportedChange(
                ChangeHeading.TUPLETS_UPDATED,
                Strings.get(Strings.REPORT_UPDATED_DETAIL, grade.number(), TRIPLET_NORMAL_NOTES),
                THE_LINE),
            new ReportedChange(
                ChangeHeading.TUPLETS_REMOVED,
                Strings.get(Strings.REPORT_REASON_NUMBER_EQUALS_NORMAL_NOTES),
                THE_LINE),
            new ReportedChange(
                ChangeHeading.ENDINGS_REMOVED,
                Strings.get(Strings.REPORT_REASON_ENDING_NOT_VALID),
                THE_LINE),
            new ReportedChange(
                ChangeHeading.HAIRPINS_REMOVED,
                Strings.get(Strings.REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE),
                THE_LINE),
            new ReportedChange(
                ChangeHeading.BREATH_MARKS_REMOVED,
                Strings.get(Strings.REPORT_REASON_BREATH_MARK_HOST),
                THE_LINE));
    }

    @Test
    void testACleanLoadGivesAnEmptyReport() throws StrandedKeyChangeException {
        var loaded = loadSong(line -> {
            line.addElement(crotchet());
            line.addElement(singleBarline());
            line.addElement(crotchet());
        });

        assertThat(loaded.report().isEmpty()).isTrue();
    }

    /**
     * One ordering constraint above {@code SongLoad.STAGES}, and what a load that keeps it
     * leaves.
     *
     * @param description  the constraint, as the test's display name
     * @param source       loads a song that only a load keeping the constraint settles as asserted
     * @param verification asserts the settled song and its report
     */
    private record OrderCase(String description, SongSource source, Verification verification) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("orderCases")
    void testEachOrderingConstraintOfTheStagesHoldsAfterALoad(OrderCase testCase) throws StrandedKeyChangeException {
        testCase.verification().verify(testCase.source().load());
    }

    static Stream<OrderCase> orderCases() {
        return Stream.of(
            new OrderCase("a tie between two notes with no written accidental holds",
                () -> loadCompactSong(
                    song -> marksOf(List.of(), List.of(new PendingTie(song.getLine(FIRST_LINE).getElement(0)))),
                    DOCUMENT_KEY, PITCH + " " + PITCH),
                loaded -> {
                    assertThat(loaded.report().isEmpty()).isTrue();
                    assertThat(loaded.song().getLine(FIRST_LINE).noteAt(0).isTiedToNext()).isTrue();
                }),

            new OrderCase("a beam over a tied pair is refused and reported while the tie stays",
                () -> loadCompactSong(
                    song -> {
                        var line = song.getLine(FIRST_LINE);

                        return marksOf(
                            List.of(new PendingBeam(line.getElement(0), line.getElement(1))),
                            List.of(new PendingTie(line.getElement(0))));
                    },
                    DOCUMENT_KEY, TIED_QUAVERS),
                loaded -> {
                    var line = loaded.song().getLine(FIRST_LINE);

                    assertThat(line.noteAt(0).isTiedToNext()).isTrue();
                    assertThat(line.spanRegistry().findSpans(Beam.class)).isEmpty();
                    assertThat(loaded.report().changes()).containsExactly(new ReportedChange(
                        ChangeHeading.BEAMS_REMOVED,
                        Strings.get(Strings.REPORT_REASON_BEAM_TIED_NOTES),
                        new LineRange(FIRST_LINE, FIRST_LINE)));
                }),

            new OrderCase("a song that starts with a breath mark and a barline loses both",
                () -> loadCompactSong(DOCUMENT_KEY, "B | " + PITCH + " " + PITCH),
                loaded -> {
                    assertThat(loaded.song().getLine(FIRST_LINE).getElements().stream().map(StaffElement::getType))
                        .containsExactlyElementsOf(TWO_NOTES_AND_TERMINAL);
                    assertThat(loaded.report().changes()).extracting(ReportedChange::heading)
                        .containsExactly(ChangeHeading.BREATH_MARKS_REMOVED, ChangeHeading.LEADING_BARS_REMOVED);
                }),

            new OrderCase("the final accidental stamp sees a removed final double barline",
                () -> loadCompactSong(DOCUMENT_KEY, PITCH + "f F " + PITCH),
                loaded -> {
                    var adopting = loaded.song().getLine(FIRST_LINE).noteAt(1).chainedAccidental();

                    assertThat(adopting.sounding()).isEqualTo(Accidental.FLAT);
                    assertThat(adopting.type())
                        .as("nothing stands between the two notes once the barline is gone")
                        .isEqualTo(ChainedAccidental.ChainType.IMPLICIT);
                }),

            new OrderCase("the syllabic backfill sees the grace-host melisma repair's result",
                () -> loadSong(
                    line -> {
                        line.setKey(DOCUMENT_KEY);
                        line.addElement(pairedGrace());
                        line.addElement(withLyric(crotchet(), SYLLABLE, Lyric.Syllabic.BEGIN));
                        line.addElement(withLyric(crotchet(), NEXT_SYLLABLE, Lyric.Syllabic.END));
                    }),
                loaded -> {
                    var next = loaded.song().getLine(FIRST_LINE).getElement(2);

                    assertThat(next.getLyricForScript(Song.Script.LATIN)).isNotNull()
                        .extracting(Lyric::syllabic)
                        .as("the host holds the melisma's carrier after the repair, and a carrier does not "
                            + "continue a word, so the syllable after it stands alone")
                        .isEqualTo(Lyric.Syllabic.SINGLE);
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strandedCases")
    void testALoadRefusesASongWithAKeyChangeRestatingTheKeyInEffectAndNamesItsLine(StrandedCase testCase) {
        assertThatThrownBy(() -> loadSong(testCase.lines()))
            .isInstanceOfSatisfying(StrandedKeyChangeException.class, thrown ->
                assertThat(thrown.lineNumber()).isEqualTo(testCase.lineNumber()));
    }

    /**
     * A song that holds a stranded key change.
     *
     * @param description the case, as the test's display name
     * @param lines       builds the song's lines
     * @param lineNumber  the 1-based number of the line that holds the first stranded key change
     */
    private record StrandedCase(String description, LineBuilder[] lines, int lineNumber) {}

    static Stream<StrandedCase> strandedCases() {
        return Stream.of(
            new StrandedCase("a key change restating its own line's key",
                new LineBuilder[] {
                    line -> {
                        line.setKey(DOCUMENT_KEY);
                        line.addElement(crotchet());
                    },
                    line -> {
                        line.setKey(OWN_KEY);
                        notesAroundKeyChange(line, OWN_KEY);
                    }},
                2),
            new StrandedCase("a key change restating the key the line before it left off in",
                new LineBuilder[] {
                    line -> {
                        line.setKey(DOCUMENT_KEY);
                        line.addElement(crotchet());
                    },
                    line -> notesAroundKeyChange(line, CHANGED_KEY),
                    line -> notesAroundKeyChange(line, CHANGED_KEY)},
                3));
    }

    @Test
    void testAKeyChangeThatChangesTheKeyInEffectSurvivesTheLoad() throws StrandedKeyChangeException {
        var loaded = loadSong(
            line -> {
                line.setKey(DOCUMENT_KEY);
                line.addElement(crotchet());
            },
            line -> notesAroundKeyChange(line, CHANGED_KEY));

        assertThat(loaded.song().getLine(SECOND_LINE).getElements())
            .filteredOn(KeyChangeElement.class::isInstance)
            .singleElement()
            .isInstanceOfSatisfying(KeyChangeElement.class, keyChange ->
                assertThat(keyChange.getKey()).isEqualTo(CHANGED_KEY));
    }

    @Test
    void testALineWhoseOwnKeyEqualsItsInheritedKeyLoadsWithNoKeyOfItsOwn() throws StrandedKeyChangeException {
        var loaded = loadSong(
            line -> {
                line.setKey(DOCUMENT_KEY);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(DOCUMENT_KEY);
                line.addElement(crotchet());
            },
            line -> {
                line.setKey(OWN_KEY);
                line.addElement(crotchet());
            });

        assertThat(loaded.song().getLine(FIRST_LINE).getKey()).isEqualTo(DOCUMENT_KEY);
        assertThat(loaded.song().getLine(SECOND_LINE).getKey()).isNull();
        assertThat(loaded.song().getLine(THIRD_LINE).getKey()).isEqualTo(OWN_KEY);
        assertThat(loaded.report().isEmpty()).isTrue();
    }

    /** A leading barline, two notes, a right repeat and two notes. */
    private static final String ENDING_ON_LEADING_BAR = "| 3 3 :| 3 3";

    /** The last note of {@link #ENDING_ON_LEADING_BAR}. */
    private static final int ENDING_END_INDEX = 5;

    /** Two notes, two breath marks and a note. */
    private static final String TUPLET_AROUND_BREATH_MARKS = "3 3 B B 3";

    /** The last note of {@link #TUPLET_AROUND_BREATH_MARKS}. */
    private static final int TUPLET_END_INDEX = 4;

    /** Two notes, a barline, two notes, a right repeat, two notes and a final double barline. */
    private static final String ENDING_ON_FINAL_BAR = "3 3 | 3 3 :| 3 3 F";

    /** The barline of {@link #ENDING_ON_FINAL_BAR}. */
    private static final int FINAL_ENDING_ANCHOR_INDEX = 2;

    /** The final double barline of {@link #ENDING_ON_FINAL_BAR}. */
    private static final int FINAL_ENDING_END_INDEX = 8;

    /**
     * A song with a span that one load stage's removal takes, and the headings the report holds.
     *
     * @param description the case, as the test's display name
     * @param source      loads the song
     * @param headings    the heading of each report entry, in order
     */
    private record RemovedSpanCase(String description, SongSource source, List<ChangeHeading> headings) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("removedSpanCases")
    void testASpanAStageRemovalTakesIsReportedAfterTheEntryForThatRemoval(RemovedSpanCase testCase)
        throws StrandedKeyChangeException {
        var loaded = testCase.source().load();

        assertThat(loaded.report().changes()).extracting(ReportedChange::heading)
            .containsExactlyElementsOf(testCase.headings());
    }

    static Stream<RemovedSpanCase> removedSpanCases() {
        return Stream.of(
            new RemovedSpanCase("an ending on a removed leading barline",
                () -> loadCompactSong(
                    song -> {
                        var line = song.getLine(FIRST_LINE);

                        return new PendingMarks(
                            List.of(),
                            List.of(new PendingEnding(line.getElement(0), line.getElement(ENDING_END_INDEX),
                                UserOffset.NONE)),
                            List.of(), List.of(), List.of(), List.of());
                    },
                    DOCUMENT_KEY, ENDING_ON_LEADING_BAR),
                List.of(ChangeHeading.LEADING_BARS_REMOVED, ChangeHeading.ENDINGS_REMOVED)),

            new RemovedSpanCase("a tuplet holding a removed breath mark",
                () -> loadCompactSong(
                    song -> {
                        var line = song.getLine(FIRST_LINE);

                        return new PendingMarks(
                            List.of(new PendingTuplet(line.getElement(0), line.getElement(TUPLET_END_INDEX),
                                TupletGrade.TRIPLET, ratioOf(TupletGrade.TRIPLET.number() - 1, NoteValue.QUARTER),
                                NO_OFFSET_SS)),
                            List.of(), List.of(), List.of(), List.of(), List.of());
                    },
                    DOCUMENT_KEY, TUPLET_AROUND_BREATH_MARKS),
                List.of(ChangeHeading.BREATH_MARKS_REMOVED, ChangeHeading.TUPLETS_REMOVED)),

            new RemovedSpanCase("an ending on a removed final double barline",
                () -> loadCompactSong(
                    song -> {
                        var line = song.getLine(FIRST_LINE);

                        return new PendingMarks(
                            List.of(),
                            List.of(new PendingEnding(line.getElement(FINAL_ENDING_ANCHOR_INDEX),
                                line.getElement(FINAL_ENDING_END_INDEX), UserOffset.NONE)),
                            List.of(), List.of(), List.of(), List.of());
                    },
                    DOCUMENT_KEY, ENDING_ON_FINAL_BAR, "3 3"),
                List.of(ChangeHeading.FINAL_BARLINES_REMOVED, ChangeHeading.ENDINGS_REMOVED)));
    }

    @Test
    void testTheLoadPostsNothingAndLeavesTheSongUnmodifiedAndAnEditAfterwardsPostsANotification()
        throws StrandedKeyChangeException {
        var recorder = new BatchRecorder();

        try {
            var loaded = loadCompactSong(DOCUMENT_KEY, PITCH + " " + PITCH);
            var song = loaded.song();

            assertThat(recorder.batches()).as("the load itself posts nothing").isEmpty();
            assertThat(song.isModified()).as("the load leaves the modified flag unset").isFalse();

            song.withModification(() -> song.getLine(FIRST_LINE).addElement(0, crotchet()));

            assertThat(recorder.batches()).singleElement().satisfies(mutations ->
                assertThat(mutations).isNotEmpty());
        } finally {
            recorder.dispose();
        }
    }

    private static PendingMarks marksOf(List<PendingBeam> beams, List<PendingTie> ties) {
        return new PendingMarks(List.of(), List.of(), List.of(), beams, List.of(), ties);
    }

    private static NoteElement pairedGrace() {
        var grace = graceQuaver();
        grace.setSlide(Slide.GLISSANDO);

        return grace;
    }

    private static StaffElement withLyric(StaffElement element, String text, Lyric.Syllabic syllabic) {
        element.putLyric(new Lyric(Song.Script.LATIN, text, Lyric.Extend.NONE, syllabic, false));

        return element;
    }

    private static PendingTuplet pendingTuplet(
        List<StaffElement> elements, ElementRange span, TupletGrade grade, @Nullable TupletNormalTime statedRatio
    ) {
        return new PendingTuplet(
            elements.get(span.begin()), elements.get(span.end()), grade, statedRatio, NO_OFFSET_SS);
    }
}
