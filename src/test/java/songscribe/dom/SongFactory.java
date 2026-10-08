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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import songscribe.engraving.StaffPosition;

import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.minimRest;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
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
     * <p>Only the inheritance chain is settled, not the whole of {@code settleKeysAfterParsing} —
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

            song.rebuildInheritedKeysAfterParsing();
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
        var builders = new LineBuilder[notations.length];

        for (var lineIndex = 0; lineIndex < notations.length; lineIndex++) {
            var notation = notations[lineIndex];
            var isFirstLine = lineIndex == 0;

            builders[lineIndex] = line -> {
                if (isFirstLine) {
                    line.setKey(startingKey);
                }

                addCompactElements(line, notation);
            };
        }

        return buildSong(builders);
    }

    /**
     * An empty line of a real song, for a fixture that needs the song's repeat structure
     * but edits its line directly, outside any modification bracket.
     *
     * <p>Mutation tracking stays suspended for the rest of the test, so a test edits the line as
     * a fixture builder does. {@link songscribe.UnitTest#detachedLine()} cannot stand in: its song is a mock
     * that knows no repeat section, so {@link Ending#spanning} would refuse every ending on it.
     * A test that exercises a bracket's close builds its song with {@link #compactSong} and edits
     * it inside {@link Song#withModification}.
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

        var song = compactSong(Key.NO_ACCIDENTALS, notations);

        song.beginSuspendMutationTracking();

        return song.getLine(precedingNotations.length);
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
     * The first line of a song of {@code length} quavers, with mutation tracking suspended for the
     * rest of the test, as {@link #editableLine} leaves it. A test that adds spans to the line
     * directly, outside any bracket, uses this line.
     *
     * <p>An empty line follows the returned one, so the returned line is never the song's last.
     *
     * @param length how many quavers the line holds
     * @return the first line of the song, holding {@code length} quavers
     */
    public static Line lineOfQuavers(int length) {
        var song = compactSong(Key.NO_ACCIDENTALS, quaverNotation(length), EMPTY_LINE);

        song.beginSuspendMutationTracking();

        return song.getLine(0);
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
     *   <li>{@code |}, {@code |:}, {@code :|} — a plain barline, a left repeat, a right repeat</li>
     *   <li>{@code R} — a crotchet rest; {@code R2} — a minim rest; {@code B} — a breath mark;
     *       {@code Q} — a quaver</li>
     *   <li>{@code K:<name>} — a key change establishing {@link Key#valueOf(String) Key.valueOf(name)}</li>
     * </ul>
     *
     * @param line     the line to populate
     * @param notation the line's compact notation
     */
    static void addCompactElements(Line line, String notation) {
        NoteElement previousNote = null;

        for (var token : notation.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }

            switch (token) {
                case "|" -> line.addElement(singleBarline());
                case "|:" -> line.addElement(repeatLeft());
                case ":|" -> line.addElement(repeatRight());
                case "R" -> line.addElement(crotchetRest());
                case "R2" -> line.addElement(minimRest());
                case "B" -> line.addElement(breathMark());
                case QUAVER_TOKEN -> line.addElement(quaver());
                case "~" -> {
                    if (previousNote != null) {
                        line.addTie(previousNote);
                    }
                }
                default -> {
                    if (token.startsWith(KEY_CHANGE_PREFIX)) {
                        line.addElement(keyChange(Key.valueOf(token.substring(KEY_CHANGE_PREFIX.length()))));
                        continue;
                    }

                    previousNote = parseNote(token);
                    line.addElement(previousNote);
                }
            }
        }
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
