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

import com.formdev.flatlaf.util.FontUtils;

import songscribe.util.MyFontUtils;

/**
 * The Source Sans 3 font family. This is used as the UI font in SongScribe.
 */
public final class SourceSans3Font {

    /**
     * Family name for basic styles (regular, italic and bold).
     * <p>
     * Usage:
     * <pre>{@code
     * new Font( SourceSans3Font.FAMILY, Font.PLAIN, 12 );
     * new Font( SourceSans3Font.FAMILY, Font.ITALIC, 12 );
     * new Font( SourceSans3Font.FAMILY, Font.BOLD, 12 );
     * new Font( SourceSans3Font.FAMILY, Font.BOLD | Font.ITALIC, 12 );
     * }</pre>
     */
    public static final String FAMILY = "Source Sans 3";

    // We don't use this, but it's here for completeness.
    public static final String FAMILY_LIGHT = "Source Sans 3";

    /**
     * Family name for semibold styles.
     * <p>
     * Usage:
     * <pre>{@code
     * new Font( SourceSans3Font.FAMILY_SEMIBOLD, Font.PLAIN, 12 );
     * new Font( SourceSans3Font.FAMILY_SEMIBOLD, Font.ITALIC, 12 );
     * }</pre>
     */
    public static final String FAMILY_SEMIBOLD = "Source Sans 3";

    /**
     * Family name for bold styles.
     * <p>
     * Usage:
     * <pre>{@code
     * new Font( SourceSans3Font.FAMILY_BOLD, Font.PLAIN, 12 );
     * new Font( SourceSans3Font.FAMILY_BOLD, Font.ITALIC, 12 );
     * }</pre>
     */
    public static final String FAMILY_BOLD = "Source Sans 3";

    /**
     * Use for MyFontUtils.installLocalFont to install single font style.
     */
    public static final String STYLE_REGULAR = "SourceSans3-Regular.ttf";
    public static final String STYLE_ITALIC = "SourceSans3-Italic.ttf";
    public static final String STYLE_SEMIBOLD = "SourceSans3-SemiBold.ttf";
    public static final String STYLE_SEMIBOLD_ITALIC =
        "SourceSans3-SemiBoldItalic.ttf";
    public static final String STYLE_BOLD = "SourceSans3-Bold.ttf";
    public static final String STYLE_BOLD_ITALIC = "SourceSans3-BoldItalic.ttf";

    private SourceSans3Font() {}

    /**
     * Registers the fonts for lazy loading via
     * {@link FontUtils#registerFontFamilyLoader(String, Runnable)}.
     * <p>
     * This is the preferred method (when using FlatLaf) to avoid unnecessary loading of maybe
     * unused fonts.
     * <p>
     * <strong>Note</strong>: When using '{@code new Font(...)}', you need to first invoke
     * {@link FontUtils#loadFontFamily(String)} to ensure that the font family is loaded.
     * When FlatLaf loads a font, or when using
     * {@link FontUtils#getCompositeFont(String, int, int)},
     * this is done automatically.
     */
    public static void installLazy() {
        FontUtils.registerFontFamilyLoader(
            FAMILY,
            SourceSans3Font::installBasic
        );
    }

    /**
     * Creates and registers the fonts for all styles.
     * <p>
     * When using FlatLaf, consider using {@link #installLazy()}.
     */
    public static void install() {
        installBasic();
    }

    /**
     * Creates and registers the fonts for basic styles (regular, italic and bold).
     * <p>
     * When using FlatLaf, consider using {@link #installLazy()}.
     */
    public static void installBasic() {
        MyFontUtils.installLocalFont(STYLE_REGULAR);
        MyFontUtils.installLocalFont(STYLE_ITALIC);
        MyFontUtils.installLocalFont(STYLE_SEMIBOLD);
        MyFontUtils.installLocalFont(STYLE_SEMIBOLD_ITALIC);
        MyFontUtils.installLocalFont(STYLE_BOLD);
        MyFontUtils.installLocalFont(STYLE_BOLD_ITALIC);
    }
}
