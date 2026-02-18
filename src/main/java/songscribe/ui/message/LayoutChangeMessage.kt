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

package songscribe.ui.message

import songscribe.music.Line

/**
 * Message posted to MessageCenter when a layout-affecting change occurs.
 *
 * @property section Which section changed
 * @property changeType The type of change (content, font, or size)
 * @property heightChanged Whether the change requires recalculation of sections below
 * @property line The specific line affected, or null if all lines are affected
 */
class LayoutChangeMessage @JvmOverloads constructor(
    val section: Section,
    val changeType: ChangeType,
    val heightChanged: Boolean,
    val line: Line? = null
) : Message() {

    enum class ChangeType {
        CONTENT,
        FONT,
        SIZE
    }

    enum class Section {
        TITLE,
        ATTRIBUTION,
        SCORE,
        LYRICS,
        BANGLA_LYRICS,
        TRANSLATION,
        FOOTNOTES
    }

    override fun toString(): String {
        return "${super.toString()}(section=$section, changeType=$changeType, heightChanged=$heightChanged, line=$line)"
    }
}
