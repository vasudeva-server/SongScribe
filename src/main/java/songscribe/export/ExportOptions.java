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
package songscribe.export;


/**
 * Controls what content is included in an export (image, PDF, etc.).
 * Replaces the pattern of temporarily mutating Song to exclude
 * content during export.
 *
 * @param includeLyrics whether to include lyrics (underLyrics, translatedLyrics)
 * @param includeTitle whether to include the song title
 * @param includeAttribution whether to include the attribution/copyright line
 */
public record ExportOptions(
    boolean includeLyrics,
    boolean includeTitle,
    boolean includeAttribution
) {

    /** Include all content. */
    public static final ExportOptions ALL = new ExportOptions(true, true, true);

    /** Exclude all optional content. */
    public static final ExportOptions NONE = new ExportOptions(false, false, false);
}
