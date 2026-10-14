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

package songscribe.font;

import java.awt.Font;

/**
 * The MuseScore icon font, whose glyphs stand in for icons on buttons and in labels throughout
 * the interface.
 */
public final class MusescoreIconFont {

    private static final String FONT_FILE = "MusescoreIcon.otf";

    private static final float SIZE_PT = 20.0f;

    private MusescoreIconFont() {}

    /**
     * Callers needing another size should {@code deriveFont()} from it.
     */
    public static Font font() {
        return Holder.INSTANCE;
    }

    /**
     * Initialization-on-demand holder: callers reach the font from static initializers on any
     * thread, and a load failure is fatal, so two threads must not enter the load at once.
     */
    private static final class Holder {
        static final Font INSTANCE = LocalFonts.load(FONT_FILE, SIZE_PT);
    }
}
