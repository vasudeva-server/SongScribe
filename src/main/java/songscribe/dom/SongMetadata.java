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

import songscribe.util.StringUtils;

/**
 * Immutable record holding the song's editable descriptive metadata.
 * <p>
 * The compact constructor normalizes every field so that all construction paths
 * — dialog commit, dialog preview, {@code loadFrom}, and {@code Converter} —
 * yield identical canonical values. Normalization is <em>idempotent</em>:
 * re-constructing a record from an already-normalized record is a no-op.
 *
 * <h3>Normalization pipeline</h3>
 * The compact constructor turns raw widget text (or {@code SongData}) into a cleaned, canonical
 * record, per field:
 * <pre>
 *     title    : stripLinefeeds -> processText(…, true)
 *                  (processText = trim + collapse-spaces + toTypographic + unconditional ă->a)
 *     place    : processText(…, false)  (trim + toTypographic; no ă->a)
 *     year     : trim
 *     number   : trim
 *     composer : coercePerson(processText(…, false))  (trim + toTypographic; empty -> SRI_CHINMOY)
 *     lyricist : coercePerson(processText(…, false))  (trim + toTypographic; empty -> SRI_CHINMOY)
 *     month / day / lyricsSource / arrangement / unofficialTranslation : as-is
 *
 *  Same factory feeds: dialog commit, dialog PREVIEW, loadFrom, Converter
 *  => preview == render (normalization parity guaranteed).
 * </pre>
 *
 * <h3>Words-date invariant</h3>
 * The words (lyrics) date can never explicitly equal the composition date: if
 * {@code wordsYear/wordsMonth/wordsDay} equal {@code year/month/day} after the
 * above normalization, the words-date components are reset to
 * {@code ("", 0, 0)}. An empty words-date is the sole canonical representation
 * of "lyrics date same as music date," which every consumer (formatters, IO
 * writers, equality checks) already assumes.
 */
public record SongMetadata(
    String title,
    String number,
    String place,
    String year,
    int month,
    int day,
    String composer,
    String lyricist,
    Song.LyricsSource lyricsSource,
    boolean arrangement,
    boolean unofficialTranslation,
    String subtitle,
    String wordsYear,
    int wordsMonth,
    int wordsDay
) {

    /**
     * Compact constructor — normalizes all fields on construction.
     */
    public SongMetadata {
        title = normalizeTitle(title);
        number = number.trim();
        // subtitle: normalized like the title (strip linefeeds, collapse spaces, trim)
        subtitle = normalizeTitle(subtitle);
        // lyricsSource, unofficialTranslation: as-is

        // The credit fields normalize themselves, including the collapse of a words-date
        // that merely repeats the composition date. Built here rather than repeated, so
        // the rules have one home and a metadata record and a bare attribution cannot
        // disagree about what the same entry means.
        var credits = new SongAttribution(
            place, year, month, day, composer, lyricist, lyricsSource, arrangement, wordsYear, wordsMonth, wordsDay
        );
        place = credits.place();
        year = credits.year();
        composer = credits.composer();
        lyricist = credits.lyricist();
        wordsYear = credits.wordsYear();
        wordsMonth = credits.wordsMonth();
        wordsDay = credits.wordsDay();
    }

    /**
     * Returns just the fields a credit block is built from.
     *
     * <p>{@link AttributionFormatter} takes this rather than the whole record, so the
     * title, number and subtitle it never reads are not part of what a caller has to
     * supply. Every field is already normalized, so building this re-normalizes nothing.
     *
     * @return this song's credits, place and dates
     */
    public SongAttribution attribution() {
        return new SongAttribution(
            place, year, month, day, composer, lyricist, lyricsSource, arrangement, wordsYear, wordsMonth, wordsDay
        );
    }

    /**
     * Returns a copy of this record with the title replaced. The new title is
     * normalized by the compact constructor; the other fields are already
     * normalized, so their re-normalization is a no-op.
     */
    public SongMetadata withTitle(String newTitle) {
        return new SongMetadata(
            newTitle, number, place, year, month, day,
            composer, lyricist, lyricsSource, arrangement, unofficialTranslation,
            subtitle, wordsYear, wordsMonth, wordsDay
        );
    }

    // -------------------------------------------------------------------------
    // Normalization helpers (package-visible for tests in this package)
    // -------------------------------------------------------------------------

    /**
     * Normalizes a title string: strips linefeeds, then applies
     * {@link StringUtils#processText} (which trims, collapses runs of multiple
     * spaces, substitutes typographic characters, and strips short-A).
     * <p>
     * Public so the song settings dialog can run its live title/subtitle preview
     * (and its focus-lost field normalization) through the same normalization the
     * compact constructor applies on commit, preserving preview == render parity.
     */
    public static String normalizeTitle(String text) {
        return StringUtils.processText(StringUtils.stripLinefeeds(text), true);
    }

    private static final int LYRICS_TITLE_BUFFER_CAPACITY = 50;

    /**
     * Builds a title out of the opening words of {@code lyrics}, the way a song with no title
     * of its own is conventionally named after its first line.
     *
     * <p>Each word's first letter is capitalized, and the words are separated by single
     * spaces however {@code lyrics} separated them: a space and a newline both end a word.
     *
     * <p>The lyrics arrive in the notation the score stores them in, not as prose, so two
     * marks are read rather than copied:
     * <ul>
     *   <li>an underscore is a melisma marker — a syllable held across notes — and
     *       contributes no character, so {@code "Hel_lo"} yields {@code "Hello"};</li>
     *   <li>a single hyphen joins the syllables of one word and disappears with it, while a
     *       double hyphen is a real hyphen and both survives and ends a word.</li>
     * </ul>
     *
     * <p>Fewer words than asked for is not an error: lyrics shorter than {@code maxWords}
     * yield all of them. Lyrics that yield no characters at all — every one of them a melisma
     * marker — yield an empty title rather than failing.
     *
     * <p>The result is not normalized; {@link #normalizeTitle} is still the step between this
     * and a committed title.
     *
     * @param lyrics   the song's lyrics, run together as {@code Song.getLyricsText} builds
     *                 them; may be empty
     * @param maxWords how many words to take, at least 1
     * @return the derived title, or an empty string when {@code lyrics} yields no characters
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
