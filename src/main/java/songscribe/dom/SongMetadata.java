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

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
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
 *     title    : stripLinefeeds -> collapseMultipleSpaces -> processText
 *                  (processText = trim + optional ă->a replacement per STRIP_SHORT_A pref)
 *     place    : trim
 *     year     : trim
 *     number   : trim
 *     composer : coercePerson  (trim; empty -> SRI_CHINMOY)
 *     lyricist : coercePerson  (trim; empty -> SRI_CHINMOY)
 *     month / day / lyricsSource / arrangement / unofficialTranslation : as-is
 *
 *  Same factory feeds: dialog commit, dialog PREVIEW, loadFrom, Converter
 *  => preview == render (normalization parity guaranteed).
 * </pre>
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
    String subtitle
) {

    // Used to replace the characters "ă" and "Ă" with "a" and "A" respectively
    private static final Pattern SHORT_A_PATTERN = Pattern.compile("[ăĂ]");

    /**
     * Compact constructor — normalizes all fields on construction.
     */
    public SongMetadata {
        title = normalizeTitle(title);
        number = number.trim();
        place = place.trim();
        year = year.trim();
        composer = Song.coercePerson(composer);
        lyricist = Song.coercePerson(lyricist);
        // month, day, lyricsSource, arrangement, unofficialTranslation: as-is
        // subtitle: normalized like the title (strip linefeeds, collapse spaces, trim)
        subtitle = normalizeTitle(subtitle);
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
            subtitle
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
     * Normalizes a title string: strips linefeeds, collapses multiple spaces,
     * then applies {@link #processText}.
     */
    static String normalizeTitle(String text) {
        return processText(StringUtils.collapseMultipleSpaces(StringUtils.stripLinefeeds(text)));
    }

    /**
     * Trims the text and, when the {@link PrefsKey#STRIP_SHORT_A} preference is
     * set, replaces {@code ă}/{@code Ă} with {@code a}/{@code A}.
     * <p>
     * Package-visible so {@link Song} can share this single copy for its
     * under-lyrics normalization.
     */
    static String processText(String text) {
        var strip = Prefs.getBoolean(PrefsKey.STRIP_SHORT_A);

        if (strip && SHORT_A_PATTERN.matcher(text).find()) {
            return text.replace("ă", "a").replace("Ă", "A");
        }

        return text.trim();
    }
}
