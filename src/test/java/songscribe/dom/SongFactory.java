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

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import songscribe.engraving.StaffPosition;

import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.minimRest;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/** Shared factory methods for whole {@link Song}s used across test classes. */
public final class SongFactory {

    /** A tempo whose beat is a dotted crotchet: a song or an element that sets it has a compound beat. */
    public static final Tempo DOTTED_BEAT_TEMPO =
        new Tempo(Tempo.DEFAULT_BPM, BeatUnit.CROTCHET_DOTTED, Tempo.DEFAULT_MARKING);

    private SongFactory() {}

    /**
     * Adds a note to {@code line}, then a key change establishing {@code key} behind the barline
     * its position invariant requires, then another note — so the line holds a mid-line key
     * change with a note either side of it.
     *
     * @param line the line to populate
     * @param key the key the key change establishes
     */
    public static void notesAroundKeyChange(Line line, Key key) {
        line.addElement(crotchet());
        line.addElement(singleBarline());
        line.addElement(keyChange(key));
        line.addElement(crotchet());
    }

    /** Populates one line of a song under construction. */
    @FunctionalInterface
    public interface LineBuilder {
        void build(Line line);
    }

    /**
     * A song of the lines {@code builders} populate, with the inherited-key chain settled across
     * all of them and every note's accidental state decided.
     *
     * <p>The default initial line {@link Song#Song()} installs is replaced by the caller's lines.
     * Each builder's elements are added before its line is inserted into the song, so no builder
     * runs with its line as the song's last, and the terminal-slot auto-maintenance does not
     * reorder elements during construction.
     *
     * <p>Only the inheritance chain is settled, not the whole of {@link KeyInheritance#settleAfterParsing()} —
     * a caller may deliberately build a line whose key change a later edit is meant to strand, and
     * the load-time repair would take it away before the test could use it. A builder that sets no
     * key leaves its line in the key the line before it ends in.
     *
     * <p>Every note is then stamped by {@link Song#stampAccidentalsAfterParsing()}, as the load
     * pass leaves a file's notes: a note on a line is never {@link AccidentalState.Chosen Chosen} or
     * {@link AccidentalState.Adopting Adopting} outside a load, so a song a test edits or writes out
     * has to start stamped.
     *
     * @param builders one per line, in song order
     * @return the song, every note holding a {@link ChainedAccidental}
     */
    public static Song buildSong(LineBuilder... builders) {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            song.removeLine(0);

            for (var builder : builders) {
                var line = new Line(song);
                builder.build(line);
                song.addLine(line);
            }

            song.keyInheritance().rebuildAfterParsing();
        });

        song.stampAccidentalsAfterParsing();

        return song;
    }

    /**
     * Sets {@code song}'s line width without recording it, so a fit measurement sees the new width
     * and the song's undo history does not.
     *
     * @param song        the song to resize
     * @param lineWidthSs the line width, in staff spaces
     */
    public static void setLineWidth(Song song, double lineWidthSs) {
        song.withoutMutationTracking(() -> song.setLineWidthSs(new Ss(lineWidthSs)));
    }

    /** A note token: an optional grace marker, a staff position, and an optional accidental code. */
    private static final Pattern NOTE_TOKEN = Pattern.compile("(g)?(-?\\d+)(f|s|n|bf|ds)?");

    /** The prefix a key-change token carries before the {@link Key} constant's name. */
    private static final String KEY_CHANGE_PREFIX = "K:";

    /** The compact notation of a line that holds no elements. */
    private static final String EMPTY_LINE = "";

    /** The compact notation of one quaver. */
    private static final String QUAVER_TOKEN = "Q";

    /** The compact notation of one crotchet at the default staff position. */
    private static final String CROTCHET_TOKEN = "N";

    /** The compact notation that ties the last note added to the note after it. */
    private static final String TIE_TOKEN = "~";

    /**
     * A song of one line per element of {@code notations}, each parsed by
     * {@link #addCompactElements}, starting in {@code startingKey}.
     *
     * <p>Built with {@link #buildSong}, so a note the notation writes an accidental on sounds that
     * accidental and a bare note adopts what is in force, each then stamped with the state the
     * chain walk decides — the states a table of chain-walk cases asserts against.
     *
     * @param startingKey the key the first line establishes
     * @param notations   one compact notation per line, in song order; see
     *                    {@link #addCompactElements} for the grammar
     * @return the song, every note holding a {@link ChainedAccidental}
     */
    public static Song compactSong(Key startingKey, String... notations) {
        return buildSong(compactBuilders(startingKey, notations));
    }

    /**
     * A song of one line per element of {@code notations}, like {@link #compactSong(Key, String...)},
     * except that {@code reader} turns each token into its element. A test whose notation decorates
     * the grammar's tokens (a name to find the element by, an annotation, a mark) strips the
     * decoration in {@code reader} and passes the bare token to {@link #compactElement}.
     *
     * @param startingKey the key the first line establishes
     * @param reader      reads every token except {@code ~}
     * @param notations   one compact notation per line, in song order
     * @return the song, every note holding a {@link ChainedAccidental}
     */
    public static Song compactSong(Key startingKey, TokenReader reader, String... notations) {
        return buildSong(compactBuilders(startingKey, reader, notations));
    }

    /**
     * @param cursor the position of a note in {@code song}
     */
    public static NoteElement noteAt(Song song, Cursor cursor) {
        return (NoteElement) song.getLine(cursor.lineIndex()).getElement(cursor.index());
    }

    /**
     * A song of one line per list of {@code lines}, each holding exactly those elements.
     *
     * @param lines the elements of each line, in song order
     * @return the song
     */
    @SafeVarargs
    public static Song songOfLines(List<StaffElement>... lines) {
        var builders = Stream.of(lines)
                             .map(elements -> (LineBuilder) line -> elements.forEach(line::addElement))
                             .toArray(LineBuilder[]::new);

        return buildSong(builders);
    }

    /** Reads one token of a compact notation into the element it stands for. */
    @FunctionalInterface
    public interface TokenReader {

        /**
         * @param lineIndex the index of the line the token is on
         * @param line      the line the element will be added to, holding the elements before this token's
         * @param token     the token, as written in the notation
         * @return the element to add
         */
        StaffElement read(int lineIndex, Line line, String token);
    }

    private static LineBuilder[] compactBuilders(Key startingKey, String... notations) {
        return compactBuilders(startingKey, (lineIndex, line, token) -> compactElement(token), notations);
    }

    private static LineBuilder[] compactBuilders(Key startingKey, TokenReader reader, String... notations) {
        var builders = new LineBuilder[notations.length];

        for (var lineIndex = 0; lineIndex < notations.length; lineIndex++) {
            var notation = notations[lineIndex];
            var currentLineIndex = lineIndex;

            builders[lineIndex] = line -> {
                if (currentLineIndex == 0) {
                    line.setKey(startingKey);
                }

                addCompactElements(line, currentLineIndex, notation, reader);
            };
        }

        return builders;
    }

    /**
     * What a test's build hands {@link SongLoad#run}: the pending marks over the lines it built.
     *
     * @param marks the marks the load stages place
     */
    public record LoadBuild(PendingMarks marks) implements SongLoad.SongBuild.Built {}

    /**
     * Loads a song of the lines {@code builders} populate through {@link SongLoad#run}, with no
     * pending marks, so the load stages run over the raw song as a reader leaves it.
     *
     * @param builders one per line, in song order
     * @return the loaded song, its report, and the build
     * @throws StrandedKeyChangeException when a line holds a key change that restates the key in effect
     */
    public static SongLoad.LoadedSong<LoadBuild> loadSong(LineBuilder... builders)
        throws StrandedKeyChangeException {
        return loadSongWithMarks(song -> noMarks(), builders);
    }

    /**
     * Loads the song {@link #compactSong} would build, through {@link SongLoad#run}, with no
     * pending marks.
     *
     * @param startingKey the key the first line establishes
     * @param notations   one compact notation per line, in song order
     * @return the loaded song, its report, and the build
     * @throws StrandedKeyChangeException when a line holds a key change that restates the key in effect
     */
    public static SongLoad.LoadedSong<LoadBuild> loadCompactSong(Key startingKey, String... notations)
        throws StrandedKeyChangeException {
        return loadCompactSong(song -> noMarks(), startingKey, notations);
    }

    /**
     * Loads the song {@link #compactSong} would build, through {@link SongLoad#run}, with the pending
     * marks {@code marksOf} names over the song's elements.
     *
     * @param marksOf     names the marks over the lines, which exist once it is called
     * @param startingKey the key the first line establishes
     * @param notations   one compact notation per line, in song order
     * @return the loaded song, its report, and the build
     * @throws StrandedKeyChangeException when a line holds a key change that restates the key in effect
     */
    public static SongLoad.LoadedSong<LoadBuild> loadCompactSong(
        Function<Song, PendingMarks> marksOf, Key startingKey, String... notations
    ) throws StrandedKeyChangeException {
        return loadSongWithMarks(marksOf, compactBuilders(startingKey, notations));
    }

    /**
     * @param marksOf names the marks over the lines, which exist once it is called
     */
    private static SongLoad.LoadedSong<LoadBuild> loadSongWithMarks(
        Function<Song, PendingMarks> marksOf, LineBuilder... builders
    ) throws StrandedKeyChangeException {
        return SongLoad.<LoadBuild, RuntimeException, RuntimeException>run(song -> {
            addLines(song, builders);

            return new LoadBuild(marksOf.apply(song));
        });
    }

    /**
     * Adds to {@code song}, in order, one line for each of {@code builders}. A builder's elements
     * are added before its line is, so no builder runs with its line as the song's last.
     *
     * @param song     a song in a load, as {@link SongLoad.SongBuild#build} receives it
     * @param builders one per line, in song order
     */
    public static void addLines(Song song, LineBuilder... builders) {
        for (var builder : builders) {
            var line = new Line(song);
            builder.build(line);
            song.addLine(line);
        }
    }

    /** Marks of every kind, none of them present. */
    public static PendingMarks noMarks() {
        return new PendingMarks(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * An empty line of a real song, for a fixture that needs the song's repeat structure.
     *
     * <p>A test edits the line as a fixture builder does, inside
     * {@link Song#withoutMutationTracking}. {@link songscribe.UnitTest#detachedLine()} cannot
     * stand in: its song is a mock that knows no repeat section, so {@link Ending#spanning} would
     * refuse every ending on it. A test that exercises a bracket's close edits the line inside
     * {@link Song#withModification}.
     *
     * <p>An empty line follows the returned one, so the returned line is never the song's last.
     * On the last line, {@link Line#addElement(StaffElement)} inserts in front of a trailing
     * {@link ElementType#REPEAT_RIGHT} or {@link ElementType#FINAL_DOUBLE_BARLINE}, which it takes
     * for the song's terminal, so a fixture that appends a {@code :|} and then more elements
     * would not get the order it asks for.
     *
     * @param precedingNotations the compact notation of each line before the returned one, in song
     *                           order, starting in {@link Key#NO_ACCIDENTALS}; see
     *                           {@link #addCompactElements} for the grammar
     * @return the next-to-last line of the song, holding no elements
     */
    public static Line editableLine(String... precedingNotations) {
        var notations = Stream.concat(Arrays.stream(precedingNotations), Stream.of(EMPTY_LINE, EMPTY_LINE))
            .toArray(String[]::new);

        return compactSong(Key.NO_ACCIDENTALS, notations).getLine(precedingNotations.length);
    }

    /**
     * A song of one line of {@code length} quavers, each a note that can carry any span, built with
     * {@link #compactSong} and left with mutation tracking as the build leaves it. A test that adds
     * spans inside a modification bracket uses this song.
     *
     * @param length how many quavers the line holds
     * @return the song, every note holding a {@link ChainedAccidental}
     */
    public static Song songOfQuavers(int length) {
        return compactSong(Key.NO_ACCIDENTALS, quaverNotation(length));
    }

    /**
     * The first line of a song of {@code length} quavers, for a test that edits the line as
     * {@link #editableLine} states.
     *
     * <p>An empty line follows the returned one, so the returned line is never the song's last.
     *
     * @param length how many quavers the line holds
     * @return the first line of the song, holding {@code length} quavers
     */
    public static Line lineOfQuavers(int length) {
        return compactSong(Key.NO_ACCIDENTALS, quaverNotation(length), EMPTY_LINE).getLine(0);
    }

    /**
     * {@code count} new quavers, as loose elements in the order they are made, for a test that
     * hands them to a builder or a validator rather than to a line.
     *
     * @param count how many quavers to make
     * @return the quavers, belonging to no line
     */
    public static List<StaffElement> quavers(int count) {
        return Stream.generate(StaffElementFactory::quaver)
            .limit(count)
            .map(StaffElement.class::cast)
            .toList();
    }

    private static String quaverNotation(int length) {
        return (QUAVER_TOKEN + " ").repeat(length);
    }

    /**
     * Parses {@code notation}'s whitespace-separated tokens onto {@code line}. Package-visible so
     * a test that needs a line builder {@link #compactSong} cannot express — one that sets the
     * line's own key, say — can still reach the same grammar.
     *
     * <ul>
     *   <li>{@code <position>} — a bare note at that staff position, {@link AccidentalState.Adopting
     *       Adopting}</li>
     *   <li>{@code <position><code>} — a note {@link AccidentalState.Chosen Chosen} to sound the
     *       accidental {@code code} names: {@code f} flat, {@code s} sharp, {@code n} natural,
     *       {@code bf} double flat, {@code ds} double sharp</li>
     *   <li>{@code g} prefixed on either of the above — the same note, as a grace note</li>
     *   <li>{@code ~} — ties the last note added to whatever note follows it, on this line or the
     *       next; a {@code ~} after the song's last note ties it with nothing following</li>
     *   <li>{@code |}, {@code ||}, {@code |:}, {@code :|}, {@code :|:} — a plain barline, a double
     *       barline, a left repeat, a right repeat, a left-right repeat</li>
     *   <li>{@code R} — a crotchet rest; {@code R2} — a minim rest; {@code B} — a breath mark;
     *       {@code Q} — a quaver; {@code N} — a crotchet at the default staff position;
     *       {@code F} — a final double barline</li>
     *   <li>{@code K:<name>} — a key change establishing {@link Key#valueOf(String) Key.valueOf(name)}</li>
     * </ul>
     *
     * @param line     the line to populate
     * @param notation the line's compact notation
     */
    static void addCompactElements(Line line, String notation) {
        addCompactElements(line, 0, notation, (lineIndex, ignored, token) -> compactElement(token));
    }

    private static void addCompactElements(Line line, int lineIndex, String notation, TokenReader reader) {
        NoteElement previousNote = null;

        for (var token : compactTokens(notation)) {
            if (TIE_TOKEN.equals(token)) {
                if (previousNote != null) {
                    line.addTie(previousNote);
                }

                continue;
            }

            var element = reader.read(lineIndex, line, token);

            line.addElement(element);

            if (element instanceof NoteElement note) {
                previousNote = note;
            }
        }
    }

    /**
     * The whitespace-separated tokens of {@code notation}, in order.
     *
     * @param notation a compact notation, or a decorated one whose decoration holds no whitespace
     * @return its tokens, none empty
     */
    public static List<String> compactTokens(String notation) {
        return Arrays.stream(notation.trim().split("\\s+")).filter(token -> !token.isEmpty()).toList();
    }

    /**
     * The element one token of the compact grammar stands for, every token except {@code ~}; see
     * {@link #addCompactElements} for the grammar.
     *
     * @param token a bare token
     * @return the element
     * @throws IllegalArgumentException when {@code token} is not in the grammar
     */
    public static StaffElement compactElement(String token) {
        return switch (token) {
            case "|" -> singleBarline();
            case "||" -> doubleBarline();
            case "|:" -> repeatLeft();
            case ":|" -> repeatRight();
            case ":|:" -> repeatLeftRight();
            case "R" -> crotchetRest();
            case "R2" -> minimRest();
            case "B" -> breathMark();
            case "F" -> finalDoubleBarline();
            case QUAVER_TOKEN -> quaver();
            case CROTCHET_TOKEN -> crotchet();
            default -> token.startsWith(KEY_CHANGE_PREFIX)
                ? keyChange(Key.valueOf(token.substring(KEY_CHANGE_PREFIX.length())))
                : parseNote(token);
        };
    }

    private static NoteElement parseNote(String token) {
        var matcher = NOTE_TOKEN.matcher(token);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unrecognized compact notation token: " + token);
        }

        var note = (matcher.group(1) != null) ? graceQuaver() : crotchet();
        note.setStaffPosition(new StaffPosition(Integer.parseInt(matcher.group(2))));

        var accidental = accidentalOf(matcher.group(3));

        if (accidental != null) {
            note.choose(accidental);
        }

        return note;
    }

    private static @Nullable Accidental accidentalOf(@Nullable String code) {
        if (code == null) {
            return null;
        }

        return switch (code) {
            case "f" -> Accidental.FLAT;
            case "s" -> Accidental.SHARP;
            case "n" -> Accidental.NATURAL;
            case "bf" -> Accidental.DOUBLE_FLAT;
            case "ds" -> Accidental.DOUBLE_SHARP;
            default -> throw new IllegalArgumentException("Unknown accidental code: " + code);
        };
    }
}
