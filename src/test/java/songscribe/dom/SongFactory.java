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

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import songscribe.engraving.StaffPosition;

import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;

/** Shared factory methods for whole {@link Song}s used across test classes. */
public final class SongFactory {

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
     * @return the song, every note {@link AccidentalState.Stamped Stamped}
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

    /** A note token: an optional grace marker, a staff position, and an optional accidental code. */
    private static final Pattern NOTE_TOKEN = Pattern.compile("(g)?(-?\\d+)(f|s|n|bf|ds)?");

    /** The prefix a key-change token carries before the {@link Key} constant's name. */
    private static final String KEY_CHANGE_PREFIX = "K:";

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
     * @return the song, every note {@link AccidentalState.Stamped Stamped}
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
     *   <li>{@code ~} — ties the note just added to the next note token added</li>
     *   <li>{@code |}, {@code |:}, {@code :|} — a plain barline, a left repeat, a right repeat</li>
     *   <li>{@code R} — a crotchet rest; {@code B} — a breath mark</li>
     *   <li>{@code K:<name>} — a key change establishing {@link Key#valueOf(String) Key.valueOf(name)}</li>
     * </ul>
     *
     * @param line     the line to populate
     * @param notation the line's compact notation
     */
    static void addCompactElements(Line line, String notation) {
        NoteElement previousNote = null;
        var tieNext = false;

        for (var token : notation.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }

            switch (token) {
                case "|" -> line.addElement(singleBarline());
                case "|:" -> line.addElement(repeatLeft());
                case ":|" -> line.addElement(repeatRight());
                case "R" -> line.addElement(crotchetRest());
                case "B" -> line.addElement(breathMark());
                case "~" -> tieNext = true;
                default -> {
                    if (token.startsWith(KEY_CHANGE_PREFIX)) {
                        line.addElement(keyChange(Key.valueOf(token.substring(KEY_CHANGE_PREFIX.length()))));
                        continue;
                    }

                    var note = parseNote(token);
                    line.addElement(note);

                    if (tieNext && previousNote != null) {
                        new Tie(previousNote, note).addTo(line);
                        tieNext = false;
                    }

                    previousNote = note;
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
            note.setAccidentalState(new AccidentalState.Chosen(accidental));
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
