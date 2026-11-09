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
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.engraving.StaffPosition;
import songscribe.font.DocumentFonts;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LineLayoutField;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;
import static songscribe.dom.SpanFactory.addWithoutTracking;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.semibreve;

/**
 * The four promises of {@link Tie}: where a tie from a note ends ({@link Tie#startingOn} and
 * {@link Tie#endingOn}), whether it holds ({@link Tie#judge}), which neighboring lines an edit at a
 * line's edge reaches ({@link Tie#reach}), and which side of the notes it is drawn on
 * ({@link Tie#arcSign}).
 */
class TieTest extends UnitTest {

    private static final int PITCH = 3;
    private static final int OTHER_PITCH = 4;

    /** In the treble clef, with no accidentals in force, E sharp sounds the F above it. */
    private static final int E_STAFF_POSITION = 4;
    private static final int F_STAFF_POSITION = 3;

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;
    private static final int FIRST_ELEMENT = 0;
    private static final int SECOND_ELEMENT = 1;
    private static final int THIRD_ELEMENT = 2;

    /**
     * A song whose first element is a note, and where a tie from it ends.
     *
     * @param description the case, as the test's display name
     * @param lines       the song's lines in compact notation, the first line's first element a
     *                    note
     * @param end         where the tie's end stands, or {@code null} when no tie starts on the note
     * @param farNote     when {@code end} is {@code null}, the note past the element that stops the
     *                    walk, which no tie ends on; {@code null} when there is no such note
     */
    private record WalkCase(String description, String[] lines, @Nullable Cursor end, @Nullable Cursor farNote) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("walkCases")
    void testATiesEndIsTheFirstElementPastTheSeparatorsWhenThatIsANote(WalkCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());
        var anchor = noteAt(song, new Cursor(FIRST_LINE, FIRST_ELEMENT));

        var tie = Tie.startingOn(anchor);
        var expected = testCase.end();

        if (expected == null) {
            assertThat(tie).isNull();

            var farNote = testCase.farNote();

            if (farNote != null) {
                assertThat(Tie.endingOn(noteAt(song, farNote))).as("no tie ends past the walk's stop").isNull();
            }

            return;
        }

        var end = noteAt(song, expected);

        assertThat(tie).isNotNull();
        assertThat(tie.end()).isSameAs(end);
        assertThat(Tie.endingOn(end)).as("the end answers the same tie from its side").isEqualTo(tie);
    }

    static Stream<WalkCase> walkCases() {
        return Stream.of(
            new WalkCase("the next note",
                new String[] {PITCH + " " + PITCH}, new Cursor(FIRST_LINE, SECOND_ELEMENT), null),
            new WalkCase("the first note of the next line",
                new String[] {"" + PITCH, "" + PITCH}, new Cursor(SECOND_LINE, FIRST_ELEMENT), null),
            new WalkCase("past a barline that starts the next line",
                new String[] {"" + PITCH, "| " + PITCH}, new Cursor(SECOND_LINE, SECOND_ELEMENT), null),
            new WalkCase("none when the walk reaches a rest",
                new String[] {PITCH + " R " + PITCH}, null, new Cursor(FIRST_LINE, THIRD_ELEMENT)),
            new WalkCase("none when the walk reaches a grace note",
                new String[] {PITCH + " g" + PITCH + " " + PITCH}, null, new Cursor(FIRST_LINE, THIRD_ELEMENT)),
            new WalkCase("none when the next line holds only separators",
                new String[] {"" + PITCH, "|", "" + PITCH}, null, new Cursor(THIRD_LINE, FIRST_ELEMENT)),
            new WalkCase("none when the song runs out",
                new String[] {"" + PITCH}, null, null)
        );
    }

    /** The non-duration types a tie's walk stops at, so that no tie reaches past one. */
    private static final Set<ElementType> BLOCKING_SEPARATORS =
        EnumSet.of(ElementType.FINAL_DOUBLE_BARLINE, ElementType.KEY_CHANGE);

    static Stream<ElementType> separatorTypes() {
        return Stream.of(ElementType.values()).filter(ElementType::isNonDuration);
    }

    private static StaffElement separator(ElementType type) {
        if (type.isBar()) {
            return StaffElementFactory.bar(type);
        }

        return switch (type) {
            case BREATH_MARK -> StaffElementFactory.breathMark();
            case KEY_CHANGE -> StaffElementFactory.keyChange(Key.ONE_SHARP);
            default -> throw new AssertionError("no separator is built for " + type);
        };
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("separatorTypes")
    void testATieWalksPastEveryNonDurationTypeThatIsALegalSeparator(ElementType separatorType) {
        var song = SongFactory.buildSong(
            line -> {
                line.setKey(Key.NO_ACCIDENTALS);
                SongFactory.addCompactElements(line, "" + PITCH);
                line.addElement(separator(separatorType));
                SongFactory.addCompactElements(line, "" + PITCH);
            },
            line -> SongFactory.addCompactElements(line, "" + PITCH));
        var line = song.getLine(FIRST_LINE);
        var anchor = (NoteElement) line.getElement(FIRST_ELEMENT);
        var noteAfter = (NoteElement) line.getElement(THIRD_ELEMENT);

        var tie = Tie.startingOn(anchor);

        if (BLOCKING_SEPARATORS.contains(separatorType)) {
            assertThat(tie).isNull();
            assertThat(Tie.endingOn(noteAfter)).as("no tie ends past the walk's stop").isNull();
            return;
        }

        assertThat(tie).isNotNull();
        assertThat(tie.end()).isSameAs(noteAfter);
        assertThat(Tie.endingOn(noteAfter)).as("the end answers the same tie from its side").isEqualTo(tie);
    }

    /** What {@link Tie#judge} must answer for one song's first note. */
    private sealed interface Expected permits Held, BrokenAs {}

    /** The tie holds, ending on the note at {@code end}. */
    private record Held(Cursor end) implements Expected {}

    /** The tie is broken for {@code reason}, placed on the lines from {@code firstLine} to {@code lastLine}. */
    private record BrokenAs(Tie.Judgment.Reason reason, int firstLine, int lastLine) implements Expected {}

    /**
     * A song whose first element is a note, and the judgment of a tie from it.
     *
     * @param description the case, as the test's display name
     * @param song        builds the song
     * @param expected    the judgment
     */
    private record JudgeCase(String description, Supplier<Song> song, Expected expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("judgeCases")
    void testATieHoldsOnlyBetweenNotesAtOneStaffPositionSoundingOneAlteration(JudgeCase testCase) {
        var song = testCase.song().get();
        var anchor = noteAt(song, new Cursor(FIRST_LINE, FIRST_ELEMENT));

        var judgment = Tie.judge(anchor);

        switch (testCase.expected()) {
            case Held(var end) -> assertThat(judgment).isInstanceOfSatisfying(Tie.Judgment.Holds.class,
                holds -> assertThat(holds.tie().end()).isSameAs(noteAt(song, end)));
            case BrokenAs(var reason, var firstLine, var lastLine) -> assertThat(judgment).isEqualTo(
                new Tie.Judgment.Broken(reason, new ChangeLines(song.getLine(firstLine), song.getLine(lastLine))));
        }
    }

    static Stream<JudgeCase> judgeCases() {
        return Stream.of(
            new JudgeCase("two bare notes at one staff position hold",
                notated(PITCH + " " + PITCH), new Held(new Cursor(FIRST_LINE, SECOND_ELEMENT))),
            new JudgeCase("two notes at one staff position with other durations and other stems hold",
                () -> SongFactory.buildSong(line -> {
                    line.setKey(Key.NO_ACCIDENTALS);
                    line.addElement(quaver(PITCH, new Stem.Forced(Direction.UP)));
                    line.addElement(note(PITCH, new Stem.Forced(Direction.DOWN)));
                }),
                new Held(new Cursor(FIRST_LINE, SECOND_ELEMENT))),
            new JudgeCase("an anchor that sounds another alteration is not held",
                notated(PITCH + "s " + PITCH + "n"), new BrokenAs(Tie.Judgment.Reason.NOT_HELD, FIRST_LINE, FIRST_LINE)),
            new JudgeCase("an end that sounds another alteration is not held",
                notated(PITCH + " " + PITCH + "s"), new BrokenAs(Tie.Judgment.Reason.NOT_HELD, FIRST_LINE, FIRST_LINE)),
            new JudgeCase("an end at another staff position is not held",
                notated(PITCH + " " + OTHER_PITCH), new BrokenAs(Tie.Judgment.Reason.NOT_HELD, FIRST_LINE, FIRST_LINE)),
            new JudgeCase("E sharp to F, one pitch at two staff positions, is not held",
                notated(E_STAFF_POSITION + "s " + F_STAFF_POSITION),
                new BrokenAs(Tie.Judgment.Reason.NOT_HELD, FIRST_LINE, FIRST_LINE)),
            new JudgeCase("a walk that reaches a rest has no following note, on the anchor's line",
                notated(PITCH + " R " + PITCH),
                new BrokenAs(Tie.Judgment.Reason.NO_FOLLOWING_NOTE, FIRST_LINE, FIRST_LINE)),
            new JudgeCase("a walk that reaches a grace note has no following note",
                notated(PITCH + " g" + PITCH + " " + PITCH),
                new BrokenAs(Tie.Judgment.Reason.NO_FOLLOWING_NOTE, FIRST_LINE, FIRST_LINE)),
            new JudgeCase("a walk that reaches a rest on the next line has no following note, naming both lines",
                notated("" + PITCH, "R " + PITCH),
                new BrokenAs(Tie.Judgment.Reason.NO_FOLLOWING_NOTE, FIRST_LINE, SECOND_LINE)),
            new JudgeCase("a walk that finds nothing on the next line has no following note, on the anchor's line",
                notated("" + PITCH, "|"),
                new BrokenAs(Tie.Judgment.Reason.NO_FOLLOWING_NOTE, FIRST_LINE, FIRST_LINE)),
            new JudgeCase("across a line break, an end at another staff position is not held, naming both lines",
                notated("" + PITCH, "" + OTHER_PITCH),
                new BrokenAs(Tie.Judgment.Reason.NOT_HELD, FIRST_LINE, SECOND_LINE)),
            new JudgeCase("across a line break, an end at the anchor's staff position holds",
                notated("" + PITCH, "" + PITCH), new Held(new Cursor(SECOND_LINE, FIRST_ELEMENT)))
        );
    }

    /**
     * One mutation on a song, and the lines it reaches.
     *
     * @param description the mutation, as the test's display name
     * @param lines       the song's lines in compact notation
     * @param mutation    builds the mutation against the song
     * @param reached     the indices of the lines {@link Tie#reach} names, in any order
     */
    private record ReachCase(String description, String[] lines, Function<Song, Mutation> mutation, List<Integer> reached) {}

    /** A middle line whose first non-separator is its second element and whose last is its fourth. */
    private static final String[] SEPARATED_MIDDLE =
        {"" + PITCH, "| " + PITCH + " " + PITCH + " " + PITCH + " |", "" + PITCH};

    /** The index, in {@link #SEPARATED_MIDDLE}'s middle line, of its last non-separator. */
    private static final int LAST_NOTE = 3;

    /** Four quavers that a beam can cover, then a line holding a note. */
    private static final String[] BEAMABLE_LINE_THEN_NOTE = {"Q Q Q Q", "" + PITCH};

    /** The indices, in {@link #BEAMABLE_LINE_THEN_NOTE}'s first line, of its last two quavers. */
    private static final int PENULTIMATE_QUAVER = 2;
    private static final int LAST_QUAVER = 3;

    /** The index, in {@link #SEPARATED_MIDDLE}'s middle line, of its closing barline. */
    private static final int CLOSING_BARLINE = 4;

    private static final String TEXT = "la";

    @ParameterizedTest(name = "{0}")
    @MethodSource("reachCases")
    void testAnEditAtALinesEdgeReachesTheLineBesideIt(ReachCase testCase) {
        var song = compactSong(Key.NO_ACCIDENTALS, testCase.lines());

        assertThat(Tie.reach(testCase.mutation().apply(song)))
            .containsExactlyInAnyOrderElementsOf(testCase.reached().stream().map(song::getLine).toList());
    }

    @Test
    void testTheReachCasesCoverEveryMutationKind() {
        var covered = reachCases()
            .<Class<?>>map(testCase -> testCase.mutation().apply(compactSong(Key.NO_ACCIDENTALS, testCase.lines())).getClass())
            .collect(Collectors.toSet());

        assertThat(covered).containsExactlyInAnyOrderElementsOf(leavesOf(Mutation.class).collect(Collectors.toSet()));
    }

    static Stream<ReachCase> reachCases() {
        return Stream.of(
            new ReachCase("before the first non-separator reaches the previous line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, FIRST_ELEMENT), List.of(FIRST_LINE)),
            new ReachCase("at the first non-separator reaches the previous line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, SECOND_ELEMENT), List.of(FIRST_LINE)),
            new ReachCase("between the edges reaches no line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, THIRD_ELEMENT), List.of()),
            new ReachCase("at the last non-separator reaches the next line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, LAST_NOTE), List.of(THIRD_LINE)),
            new ReachCase("past the last non-separator reaches the next line",
                SEPARATED_MIDDLE, song -> insertion(song, SECOND_LINE, CLOSING_BARLINE), List.of(THIRD_LINE)),
            new ReachCase("a deletion is asked at its first index",
                SEPARATED_MIDDLE,
                song -> new ElementDeletion(song.getLine(SECOND_LINE),
                    new ElementRange(FIRST_ELEMENT, SECOND_ELEMENT), List.of()),
                List.of(FIRST_LINE)),
            new ReachCase("a line holding only separators reaches both neighbors",
                new String[] {"" + PITCH, "|", "" + PITCH},
                song -> insertion(song, SECOND_LINE, FIRST_ELEMENT), List.of(FIRST_LINE, THIRD_LINE)),
            new ReachCase("the first line has no previous line to reach",
                new String[] {PITCH + " " + PITCH + " " + PITCH, "" + PITCH},
                song -> insertion(song, FIRST_LINE, FIRST_ELEMENT), List.of()),
            new ReachCase("a tie removal whose anchor has left its line reaches no line",
                SEPARATED_MIDDLE, song -> new TieRemoval(song.getLine(SECOND_LINE), crotchet()), List.of()),
            new ReachCase("a line insertion names no position",
                SEPARATED_MIDDLE, song -> new LineInsertion(SECOND_LINE, song.getLine(SECOND_LINE)), List.of()),
            new ReachCase("a line deletion names no position",
                SEPARATED_MIDDLE, song -> new LineDeletion(SECOND_LINE, song.getLine(SECOND_LINE)), List.of()),
            new ReachCase("a line key change names no position",
                SEPARATED_MIDDLE, song -> new LineKeyChange(song.getLine(SECOND_LINE), null, Key.ONE_SHARP), List.of()),
            new ReachCase("a line layout change names no position",
                SEPARATED_MIDDLE,
                song -> new LineLayoutChange(song.getLine(SECOND_LINE), LineLayoutField.LYRICS_Y_POS_SS, 0.0, 1.0),
                List.of()),
            new ReachCase("a metadata change names no position",
                SEPARATED_MIDDLE, song -> new MetadataChange(MetadataField.FOOTNOTES, "", TEXT), List.of()),
            new ReachCase("a font change names no position",
                SEPARATED_MIDDLE,
                song -> new FontChange(DocumentFonts.defaultFonts(), DocumentFonts.defaultFonts()), List.of()),
            new ReachCase("a layout change names no position",
                SEPARATED_MIDDLE, song -> new LayoutChange(LayoutField.LINE_WIDTH_SS, 0.0, 1.0), List.of()),
            new ReachCase("a lyrics change names no position",
                SEPARATED_MIDDLE, song -> new LyricsChange(LyricsField.UNDER, "", TEXT), List.of()),
            new ReachCase("a replacement at the first non-separator reaches the previous line",
                SEPARATED_MIDDLE,
                song -> new ElementReplacement(song.getLine(SECOND_LINE), SECOND_ELEMENT, crotchet(), crotchet()),
                List.of(FIRST_LINE)),
            new ReachCase("a modification at the last non-separator reaches the next line",
                SEPARATED_MIDDLE,
                song -> new ElementModification(song.getLine(SECOND_LINE), LAST_NOTE,
                    EnumSet.of(ElementField.PITCH), crotchet(), crotchet()),
                List.of(THIRD_LINE)),
            new ReachCase("a tie addition on the last non-separator reaches the next line",
                SEPARATED_MIDDLE,
                song -> new TieAddition(song.getLine(SECOND_LINE), noteAt(song, new Cursor(SECOND_LINE, LAST_NOTE))),
                List.of(THIRD_LINE)),
            new ReachCase("a tie removal on the first non-separator reaches the previous line",
                SEPARATED_MIDDLE,
                song -> new TieRemoval(song.getLine(SECOND_LINE), noteAt(song, new Cursor(SECOND_LINE, SECOND_ELEMENT))),
                List.of(FIRST_LINE)),
            new ReachCase("a beam toggled over the last notes of a line reaches the next line",
                BEAMABLE_LINE_THEN_NOTE,
                song -> {
                    var line = song.getLine(FIRST_LINE);

                    return new SpanAddition(line, beamOver(line, lastTwoQuavers()));
                },
                List.of(SECOND_LINE)),
            new ReachCase("a beam removed from the last notes of a line reaches the next line",
                BEAMABLE_LINE_THEN_NOTE,
                song -> {
                    var line = song.getLine(FIRST_LINE);

                    return new SpanRemoval(line, beamOver(line, lastTwoQuavers()));
                },
                List.of(SECOND_LINE)),
            new ReachCase("a span other than a beam reaches no line",
                BEAMABLE_LINE_THEN_NOTE,
                song -> {
                    var line = song.getLine(FIRST_LINE);

                    return new SpanAddition(line, hairpinOver(line, Hairpin.Kind.CRESCENDO, lastTwoQuavers()));
                },
                List.of()),
            new ReachCase("a pitch change on an inner member of a beam that ends the line reaches the next line",
                BEAMABLE_LINE_THEN_NOTE,
                song -> {
                    var line = song.getLine(FIRST_LINE);

                    addWithoutTracking(song, () -> beamOver(line, new ElementRange(FIRST_ELEMENT, LAST_QUAVER)));

                    return new ElementModification(line, SECOND_ELEMENT,
                        EnumSet.of(ElementField.PITCH), quaver(), quaver());
                },
                List.of(SECOND_LINE))
        );
    }

    /** The stem a note of {@link #arcCases} carries. */
    private enum NoteStem {
        UP, DOWN, NONE;

        NoteElement at(int staffPosition) {
            return switch (this) {
                case UP -> note(staffPosition, new Stem.Forced(Direction.UP));
                case DOWN -> note(staffPosition, new Stem.Forced(Direction.DOWN));
                case NONE -> {
                    var stemless = semibreve();
                    stemless.setStaffPosition(new StaffPosition(staffPosition));

                    yield stemless;
                }
            };
        }
    }

    /** The side a tie is drawn on, with the {@link Tie#arcSign()} that stands for it. */
    private enum TieSide {
        ABOVE(-1), BELOW(1);

        private final int arcSign;

        TieSide(int arcSign) {
            this.arcSign = arcSign;
        }
    }

    private static final int ABOVE_THE_MIDDLE_LINE = -2;
    private static final int ON_THE_MIDDLE_LINE = 0;
    private static final int BELOW_THE_MIDDLE_LINE = 2;

    /**
     * Two notes at one staff position, and the side of the tie between them.
     *
     * @param description   the case, as the test's display name
     * @param left          the anchor's stem
     * @param right         the end's stem
     * @param staffPosition where both notes sit
     * @param side          the side the tie is drawn on
     */
    private record ArcCase(String description, NoteStem left, NoteStem right, int staffPosition, TieSide side) {}

    /**
     * The corpus is the arc-side rule: when both notes have a stem, the tie is below when both
     * stems are up and above otherwise; when one note has a stem, the tie is on the side away from
     * that stem; when neither has one, the tie is above a note above or on the middle line and
     * below a note below it.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("arcCases")
    void testATiesSideFollowsTheStemsAndThenTheStaffPosition(ArcCase testCase) {
        var left = testCase.left().at(testCase.staffPosition());
        var right = testCase.right().at(testCase.staffPosition());

        SongFactory.buildSong(line -> {
            line.setKey(Key.NO_ACCIDENTALS);
            line.addElement(left);
            line.addElement(right);
        });

        var tie = Tie.startingOn(left);

        assertThat(tie).isNotNull();
        assertThat(tie.arcSign()).isEqualTo(testCase.side().arcSign);
        assertThat(tie.isAbove()).isEqualTo(testCase.side() == TieSide.ABOVE);
    }

    @Test
    void testTheArcCasesCoverEveryPairOfStems() {
        var pairs = Stream.of(NoteStem.values())
            .flatMap(left -> Stream.of(NoteStem.values()).map(right -> List.of(left, right)))
            .collect(Collectors.toSet());

        assertThat(arcCases().map(testCase -> List.of(testCase.left(), testCase.right())).collect(Collectors.toSet()))
            .isEqualTo(pairs);
    }

    static Stream<ArcCase> arcCases() {
        return Stream.of(
            new ArcCase("both stems up: below", NoteStem.UP, NoteStem.UP, PITCH, TieSide.BELOW),
            new ArcCase("both stems down: above", NoteStem.DOWN, NoteStem.DOWN, PITCH, TieSide.ABOVE),
            new ArcCase("stem up, then stem down: above", NoteStem.UP, NoteStem.DOWN, PITCH, TieSide.ABOVE),
            new ArcCase("stem down, then stem up: above", NoteStem.DOWN, NoteStem.UP, PITCH, TieSide.ABOVE),
            new ArcCase("only the anchor's stem, up: below", NoteStem.UP, NoteStem.NONE, PITCH, TieSide.BELOW),
            new ArcCase("only the anchor's stem, down: above", NoteStem.DOWN, NoteStem.NONE, PITCH, TieSide.ABOVE),
            new ArcCase("only the end's stem, up: below", NoteStem.NONE, NoteStem.UP, PITCH, TieSide.BELOW),
            new ArcCase("only the end's stem, down: above", NoteStem.NONE, NoteStem.DOWN, PITCH, TieSide.ABOVE),
            new ArcCase("no stem, above the middle line: above",
                NoteStem.NONE, NoteStem.NONE, ABOVE_THE_MIDDLE_LINE, TieSide.ABOVE),
            new ArcCase("no stem, on the middle line: above",
                NoteStem.NONE, NoteStem.NONE, ON_THE_MIDDLE_LINE, TieSide.ABOVE),
            new ArcCase("no stem, below the middle line: below",
                NoteStem.NONE, NoteStem.NONE, BELOW_THE_MIDDLE_LINE, TieSide.BELOW)
        );
    }

    private static Supplier<Song> notated(String... lines) {
        return () -> compactSong(Key.NO_ACCIDENTALS, lines);
    }

    private static Stream<Class<?>> leavesOf(Class<?> type) {
        var permitted = type.getPermittedSubclasses();

        return permitted == null ? Stream.of(type) : Stream.of(permitted).flatMap(TieTest::leavesOf);
    }

    private static NoteElement noteAt(Song song, Cursor cursor) {
        return (NoteElement) song.getLine(cursor.lineIndex()).getElement(cursor.index());
    }

    private static ElementRange lastTwoQuavers() {
        return new ElementRange(PENULTIMATE_QUAVER, LAST_QUAVER);
    }

    private static Mutation insertion(Song song, int lineIndex, int index) {
        return new ElementInsertion(song.getLine(lineIndex), index, crotchet());
    }
}
