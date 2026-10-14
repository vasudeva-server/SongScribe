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


/**
 * The Source Sans 3 font family. This is used as the UI font in SongScribe.
 *
 * <p>The family and PostScript names are namespaced with {@code SongScribe} so the bundled faces
 * never collide with an installed copy of Source Sans 3 (a PostScript-name collision makes
 * {@code registerFont} silently reject the bundled face).
 *
 * <p>The medium and semibold faces have no AWT style constant and must be resolved by their
 * PostScript names.
 */
public final class SourceSans3Font {

    public static final String FAMILY = "Source Sans 3 SongScribe";

    public static final String PS_PREFIX = FAMILY.replace(" ", "") + '-';

    public static final String STYLE_REGULAR = "source-sans-3-regular.ttf";

    public static final String STYLE_ITALIC = "source-sans-3-italic.ttf";

    public static final String STYLE_MEDIUM = "source-sans-3-500.ttf";

    public static final String STYLE_MEDIUM_ITALIC = "source-sans-3-500italic.ttf";

    public static final String STYLE_SEMIBOLD = "source-sans-3-600.ttf";

    public static final String STYLE_SEMIBOLD_ITALIC = "source-sans-3-600italic.ttf";

    public static final String STYLE_BOLD = "source-sans-3-700.ttf";

    public static final String STYLE_BOLD_ITALIC = "source-sans-3-700italic.ttf";

    private SourceSans3Font() {}

    public static void install() {
        installRegular();
        installRemaining();
    }

    public static void installRegular() {
        LocalFonts.install(STYLE_REGULAR);
    }

    /**
     * Must be called after {@link #installRegular()} (or {@link #install()}).
     */
    public static void installRemaining() {
        LocalFonts.install(STYLE_ITALIC);
        LocalFonts.install(STYLE_MEDIUM);
        LocalFonts.install(STYLE_MEDIUM_ITALIC);
        LocalFonts.install(STYLE_SEMIBOLD);
        LocalFonts.install(STYLE_SEMIBOLD_ITALIC);
        LocalFonts.install(STYLE_BOLD);
        LocalFonts.install(STYLE_BOLD_ITALIC);
    }
}
