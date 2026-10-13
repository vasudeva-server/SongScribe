/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.lint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import songscribe.Strings;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Lyric;
import songscribe.dom.NoteElement;
import songscribe.dom.Slide;
import songscribe.dom.Song;
import songscribe.dom.SongFactory;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementFactory;

/**
 * Songs for the lint check tests, written as notation and read back as a flat list of elements.
 *
 * <p>A notation is whitespace-separated tokens, one element each:
 *
 * <ul>
 *   <li>{@code A} — a crotchet; {@code R} — a crotchet rest;</li>
 *   <li>{@code |}, {@code ||}, {@code |||} — a single, a double and a final double barline;</li>
 *   <li>{@code |:}, {@code :|}, {@code :|:} — a left, a right and a left-right repeat;</li>
 *   <li>a token followed by {@code @text} carries that text as its annotation, with each
 *       underscore standing for a space;</li>
 *   <li>a token ends in one {@value #VIOLATION_MARK} for each violation a check is expected to
 *       report on it; the mark takes no part in the song (see {@link #markedPositions}).</li>
 * </ul>
 *
 * <p>Several notations make a song of several lines, one line each.
 */
final class LintSongs {

    private static final char VIOLATION_MARK = '!';

    private static final String ANNOTATION_SEPARATOR = "@";

    private static final String TOKEN_SPACE = "_";

    private LintSongs() {
    }

    /**
     * A song of one line per notation.
     *
     * @param notations one notation per line, in song order
     * @return the song
     */
    static Song song(String... notations) {
        var builders = new SongFactory.LineBuilder[notations.length];

        for (var i = 0; i < notations.length; i++) {
            var notation = notations[i];
            builders[i] = line -> {
                for (var token : tokensOf(notation)) {
                    line.addElement(element(token));
                }
            };
        }

        return SongFactory.buildSong(builders);
    }

    /**
     * The elements of {@code song}, line by line, in the order a lint check walks them.
     *
     * @param song the song to read
     * @return every element of every line
     */
    static List<StaffElement> elementsInSongOrder(Song song) {
        var elements = new ArrayList<StaffElement>();

        for (var line : song.getLines()) {
            elements.addAll(line.getElements());
        }

        return elements;
    }

    /**
     * The position of {@code element} in {@code song}'s song order.
     *
     * @param song    the song holding the element
     * @param element an element of {@code song}, found by identity
     * @return its index in {@link #elementsInSongOrder}
     */
    static int positionOf(Song song, StaffElement element) {
        var elements = elementsInSongOrder(song);

        for (var i = 0; i < elements.size(); i++) {
            //noinspection ObjectEquality
            if (elements.get(i) == element) {
                return i;
            }
        }

        throw new IllegalArgumentException("not an element of the song: " + element);
    }

    /**
     * A crotchet that carries {@code text} as its syllable in {@code script}.
     *
     * @return the note
     */
    static NoteElement sung(Song.Script script, String text, Lyric.Extend extend, Lyric.Syllabic syllabic) {
        var note = StaffElementFactory.crotchet();
        note.putLyric(new Lyric(script, text, extend, syllabic, false));

        return note;
    }

    /**
     * A crotchet that carries a melisma carrier in {@code script}.
     *
     * @return the note
     */
    static NoteElement carrier(Song.Script script, Lyric.Extend extend) {
        var note = StaffElementFactory.crotchet();
        note.putLyric(Lyric.carrier(script, extend));

        return note;
    }

    /**
     * A grace note paired with the note after it, sung on {@code text} in {@code script}.
     *
     * @return the grace note
     */
    static NoteElement pairedGraceNote(Song.Script script, String text) {
        var grace = StaffElementFactory.graceQuaver();
        grace.setSlide(Slide.GLISSANDO);
        grace.putLyric(new Lyric(script, text, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));

        return grace;
    }

    /**
     * The song positions that {@code notations} mark as violating: a token followed by one
     * {@value #VIOLATION_MARK} per violation it is expected to carry.
     *
     * @param notations the notations of {@link #song}, one per line
     * @return one position per mark, in song order
     */
    static List<Integer> markedPositions(String... notations) {
        var positions = new ArrayList<Integer>();
        var position = 0;

        for (var notation : notations) {
            for (var token : tokensOf(notation)) {
                var markCount = token.length() - stripMarks(token).length();

                for (var i = 0; i < markCount; i++) {
                    positions.add(position);
                }

                position++;
            }
        }

        return positions;
    }

    /**
     * A violation as a test reads it: where it sits in song order, the heading and the text it
     * says, and the kind of mark it carries.
     */
    record Found(int position, String heading, String description, Class<? extends LintMark> markType) {
    }

    /** What one expected violation says: the {@code Strings} keys of its heading and its text. */
    record Reason(String headingKey, String descriptionKey) {
    }

    /**
     * The violations as a test reads them.
     *
     * @param song       the song the violations were found in
     * @param violations the violations of a check
     * @return one {@link Found} per violation, in the order given
     */
    static List<Found> found(Song song, List<Violation> violations) {
        return violations.stream()
                         .map(violation -> new Found(
                             positionOf(song, violation.mark().element()),
                             violation.heading(),
                             violation.description(),
                             violation.mark().getClass()))
                         .toList();
    }

    /**
     * The violations that {@code notations} mark as expected, each with the text of its heading
     * and of its reason.
     *
     * @param markType the kind of mark every expected violation carries
     * @param reasons  the heading and reason of each violation, one per mark of {@code notations}
     *                 and in the same order
     * @param notations the notations of {@link #song}, one per line
     * @return one {@link Found} per mark
     * @throws IllegalArgumentException when the marks and the reasons differ in number
     */
    static List<Found> expected(Class<? extends LintMark> markType, List<Reason> reasons, String... notations) {
        var positions = markedPositions(notations);

        if (positions.size() != reasons.size()) {
            throw new IllegalArgumentException(
                positions.size() + " marks in " + Arrays.toString(notations) + " but " + reasons.size()
                    + " reasons");
        }

        var expected = new ArrayList<Found>();

        for (var i = 0; i < positions.size(); i++) {
            var reason = reasons.get(i);
            expected.add(new Found(positions.get(i),
                                   Strings.get(reason.headingKey()),
                                   Strings.get(reason.descriptionKey()),
                                   markType));
        }

        return expected;
    }

    private static List<String> tokensOf(String notation) {
        return Arrays.stream(notation.trim().split("\\s+")).filter(token -> !token.isEmpty()).toList();
    }

    private static String stripMarks(String token) {
        var end = token.length();

        while (end > 0 && token.charAt(end - 1) == VIOLATION_MARK) {
            end--;
        }

        return token.substring(0, end);
    }

    private static StaffElement element(String markedToken) {
        var token = stripMarks(markedToken);
        var separatorIndex = token.indexOf(ANNOTATION_SEPARATOR);
        var name = separatorIndex < 0 ? token : token.substring(0, separatorIndex);
        var element = switch (name) {
            case "A" -> StaffElementFactory.crotchet();
            case "R" -> StaffElementFactory.crotchetRest();
            case "|" -> StaffElementFactory.singleBarline();
            case "||" -> StaffElementFactory.doubleBarline();
            case "|||" -> StaffElementFactory.finalDoubleBarline();
            case "|:" -> StaffElementFactory.repeatLeft();
            case ":|" -> StaffElementFactory.repeatRight();
            case ":|:" -> StaffElementFactory.repeatLeftRight();
            default -> throw new IllegalArgumentException("Unrecognized lint notation token: " + token);
        };

        if (separatorIndex >= 0) {
            var text = token.substring(separatorIndex + ANNOTATION_SEPARATOR.length()).replace(TOKEN_SPACE, " ");
            element.addAttachment(new AnnotationAttachment(new Annotation(text)));
        }

        return element;
    }
}
