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
 * <p>
 * It is a text font rather than an image set, so an icon in a label is laid out, sized and
 * baseline-aligned with the words beside it.
 */
public final class MusescoreIconFont {

    /** The font resource, loaded from {@code /fonts/}. */
    private static final String FONT_FILE = "MusescoreIcon.otf";

    /** The size the icons are drawn at unless a caller derives another. */
    private static final float SIZE_PT = 20.0f;

    private MusescoreIconFont() {}

    /**
     * The icon font at {@link #SIZE_PT}. Callers needing another size should
     * {@code deriveFont()} from it.
     *
     * @return the icon font
     */
    public static Font font() {
        return Holder.INSTANCE;
    }

    /**
     * Initialization-on-demand holder: the JVM's class-initialization lock makes this lazy and
     * thread-safe without synchronizing every call. Callers reach the font from their own static
     * initializers, on whatever thread touches them first, and a failure to load it is fatal —
     * {@link LocalFonts#load} reports it through {@code RuntimeError.missingResource}, which
     * shows a dialog and exits — so two threads must not enter the load at once.
     */
    private static final class Holder {
        static final Font INSTANCE = LocalFonts.load(FONT_FILE, SIZE_PT);
    }
}
