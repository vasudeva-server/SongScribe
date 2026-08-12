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
package songscribe.ui.dialog.backend;

import java.awt.Font;

/**
 * A proposed change of lyrics font: the one in effect and the one being offered.
 *
 * <p>The two are a record rather than two parameters because they are the same type and
 * adjacent, so a call site could hand them over the wrong way round with nothing to catch it —
 * and the two directions answer opposite questions, since
 * {@link SongSettingsRules#lyricsFit} refuses a change that makes a fitting line overflow and
 * allows the reverse.
 *
 * <p>The two may be equal: an OK that changed only the title still asks the question, and
 * gets an immediate yes.
 *
 * @param current   the lyrics font the document is laid out with now
 * @param candidate the lyrics font the dialog would commit
 */
public record LyricsFontChange(Font current, Font candidate) {}
