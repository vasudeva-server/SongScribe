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

package songscribe.prefs;

/**
 * An enum whose constants are what a preference holds.
 *
 * <p>A stored preference is a string, and every enum stored in one needs the same two
 * things: the string a constant is written as, and the constant a stored string reads
 * back to. This interface supplies the first, and {@link Prefs#getChoice} derives the
 * second from it, so no enum writes its own decoder and no caller writes a conversion at
 * a bind site.
 *
 * <p><b>This is the stored format, not a display string.</b> It is written into
 * {@code prefs.json} and read back from files written by earlier versions, so changing
 * one is a file migration rather than a rename — which is why the stored values are not
 * uniform in style across implementations and should not be made so. Nothing here is
 * shown to the user; a constant's label comes from {@code Strings}.
 *
 * <p>Named for the value rather than the key, because {@link PrefsKey#key} is already
 * the other half of the same JSON entry: a key names the preference, this names what the
 * preference holds.
 */
public interface PrefsValue {

    /**
     * @return the string this constant is stored as, matched case-insensitively when
     *     read back, and unique among the constants of its enum
     */
    String storedValue();
}
