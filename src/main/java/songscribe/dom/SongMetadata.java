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
 * <pre>
 *  raw widget text / SongData ──► SongMetadata(compact constructor) ──► cleaned, canonical record
 *
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
        // place/composer/lyricist get trim + typographic substitution but no
        // short-A stripping (that is reserved for title/subtitle/lyrics).
        place = StringUtils.processText(place, false);
        year = year.trim();
        composer = Song.coercePerson(StringUtils.processText(composer, false));
        lyricist = Song.coercePerson(StringUtils.processText(lyricist, false));
        // month, day, lyricsSource, arrangement, unofficialTranslation: as-is
        // subtitle: normalized like the title (strip linefeeds, collapse spaces, trim)
        subtitle = normalizeTitle(subtitle);
        wordsYear = wordsYear.trim();
        // wordsMonth, wordsDay: as-is

        // A words-date equal to the composition date is redundant; the model
        // must never hold that state, so collapse it to empty here.
        if (wordsYear.equals(year) && wordsMonth == month && wordsDay == day) {
            wordsYear = "";
            wordsMonth = 0;
            wordsDay = 0;
        }
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

    /**
     * Returns the {@link Song.LyricsSource#LYRICIST} connector when the person is
     * Sri Chinmoy, otherwise the {@code lyricsSource} connector. The connector is
     * a function of the person, not the role.
     */
    public String connectorFor(String person) {
        if (person.equals(Song.SRI_CHINMOY)) {
            return Song.LyricsSource.LYRICIST.getConnector();
        }

        return lyricsSource.getConnector();
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

}
