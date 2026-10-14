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

import java.util.stream.Collectors;

import songscribe.util.StringUtils;

/**
 * Immutable record holding the song's editable descriptive metadata.
 * <p>
 * The compact constructor normalizes every field, idempotently, so all construction paths yield
 * identical canonical values. A {@code wordsDate} equal to {@code date} is reset to
 * {@link PartialDate.EmptyDate}, the sole representation of "lyrics date same as music date".
 */
public record SongMetadata(
    String title,
    String number,
    String place,
    PartialDate date,
    String composer,
    String lyricist,
    Song.LyricsSource lyricsSource,
    boolean arrangement,
    boolean unofficialTranslation,
    String subtitle,
    PartialDate wordsDate
) {

    public static final int MAX_TITLE_LINES = 2;

    public static final int MAX_SUBTITLE_LINES = 1;

    public SongMetadata {
        title = normalizeTitle(title);
        number = number.trim();
        subtitle = normalizeSubtitle(subtitle);
        // lyricsSource, unofficialTranslation: as-is

        // The credit fields normalize themselves, including the collapse of a words-date
        // that merely repeats the composition date. Built here rather than repeated, so
        // the rules have one home and a metadata record and a bare attribution cannot
        // disagree about what the same entry means.
        var credits = new SongAttribution(
            place, date, composer, lyricist, lyricsSource, arrangement, wordsDate
        );
        place = credits.place();
        composer = credits.composer();
        lyricist = credits.lyricist();
        wordsDate = credits.wordsDate();
    }

    public SongAttribution attribution() {
        return new SongAttribution(
            place, date, composer, lyricist, lyricsSource, arrangement, wordsDate
        );
    }

    // -------------------------------------------------------------------------
    // Normalization helpers
    // -------------------------------------------------------------------------

    private static String normalizeLine(String text) {
        return StringUtils.processText(text, true);
    }

    /**
     * Normalizes a title, keeping the break a two-line title is written with.
     *
     * <p>The text is reduced to at most {@value #MAX_TITLE_LINES} lines by
     * {@link StringUtils#foldSurplusLineBreaks}, which spends that budget on lines
     * holding something rather than on whichever break comes first. Each surviving line
     * is then cleaned the way any single-line field is — trimmed, its space runs
     * collapsed, its punctuation made typographic, its short-A stripped.
     *
     * <p>The blank last line a half-typed title carries is dropped here. The fold keeps
     * it so the row the caret sits on survives while the user is still typing; a
     * committed title has no such row.
     *
     * <p>The fold happens here, and nowhere downstream, because the compact
     * constructor runs on every construction path — dialog commit, dialog preview,
     * loading a file, importing a header, the batch converter. A title of three lines
     * is therefore unrepresentable in the model, whatever a hand-edited file holds,
     * and no consumer has to defend against one.
     *
     * <p>Public so the song settings dialog can run its live title preview (and its
     * focus-lost field normalization) through the same normalization the compact
     * constructor applies on commit, preserving preview == render parity.
     *
     * @param text the raw title text
     * @return the normalized title
     * @invariant idempotent: normalizing an already-normalized title returns it unchanged
     * @invariant the result holds at most {@value #MAX_TITLE_LINES} lines
     * @invariant no line of the result is blank
     * @invariant no line of the result carries leading or trailing whitespace
     * @invariant no carriage return survives
     * @invariant a title of nothing but whitespace yields the empty string
     */
    public static String normalizeTitle(String text) {
        return StringUtils.LF_PATTERN
            .splitAsStream(StringUtils.foldSurplusLineBreaks(text, MAX_TITLE_LINES))
            .map(SongMetadata::normalizeLine)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.joining("\n"));
    }

    /**
     * Normalizes a subtitle, which is always one line.
     *
     * <p>A subtitle is folded to {@value #MAX_SUBTITLE_LINES} line rather than merely
     * stripped of linefeeds, because the fold also removes carriage returns. Stripping
     * linefeeds alone leaves a bare {@code \r} in the value, which then reaches the
     * MusicXML credit words and the page.
     *
     * <p>Public for the same reason {@link #normalizeTitle} is: the song settings
     * dialog previews and commits through one normalization, not two.
     *
     * @param text the raw subtitle text
     * @return the normalized subtitle
     * @invariant idempotent: normalizing an already-normalized subtitle returns it unchanged
     * @invariant the result is a single line — it holds no line break and no carriage return
     * @invariant the result carries no leading or trailing whitespace
     */
    public static String normalizeSubtitle(String text) {
        return normalizeLine(StringUtils.foldSurplusLineBreaks(text, MAX_SUBTITLE_LINES));
    }

    private static final int LYRICS_TITLE_BUFFER_CAPACITY = 50;

    /**
     * The result is not normalized; {@link #normalizeTitle} is still the step between this and a
     * committed title.
     *
     * @param maxWords at least 1
     */
    public static String titleFromLyrics(String lyrics, int maxWords) {
        var words = new StringBuilder(LYRICS_TITLE_BUFFER_CAPACITY);
        var wordCount = 0;
        var firstLetter = false;
        var lastHyphen = false;

        goThruString:
        for (var i = 0; i < lyrics.length(); i++) {
            switch (lyrics.charAt(i)) {
                case ' ', '\n' -> {
                    wordCount++;

                    if (wordCount >= maxWords) {
                        break goThruString;
                    }

                    words.append(' ');
                    firstLetter = true;
                }
                case '-' -> {
                    if (lastHyphen) {
                        words.append('-');
                        wordCount++;
                        firstLetter = true;
                    }

                    lastHyphen = !lastHyphen;
                }
                case '_' -> {
                }
                default -> {
                    if (firstLetter) {
                        words.append(
                            String.valueOf(lyrics.charAt(i)).toUpperCase()
                        );
                        firstLetter = false;
                    } else {
                        words.append(lyrics.charAt(i));
                    }

                    lastHyphen = false;
                }
            }
        }

        // Lyrics made up only of separators (e.g. all underscores) leave the buffer empty;
        // guard before indexing the last character so the trim does not throw on an empty
        // buffer.
        if (!words.isEmpty() && !Character.isLetter(words.charAt(words.length() - 1))) {
            words.deleteCharAt(words.length() - 1);
        }

        return words.toString();
    }

}
