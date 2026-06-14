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
 * The Source Sans Pro font family. This is used as the UI font in SongScribe.
 *
 * <p>The family and PostScript names are namespaced with {@code SongScribe} so the
 * bundled faces never collide with a copy of Source Sans Pro installed on the
 * host system (a PostScript-name collision makes {@code registerFont} silently
 * reject the bundled face).
 *
 * <p>The regular, italic, bold and bold-italic faces form the RIBI family
 * {@link #FAMILY}. The semibold faces ({@link #STYLE_SEMIBOLD},
 * {@link #STYLE_SEMIBOLD_ITALIC}) carry no legacy family name, so they are not part
 * of that family; they are registered as additional document weights and resolved
 * by their PostScript names ({@code SourceSansProSongScribe-Semibold},
 * {@code SourceSansProSongScribe-SemiboldIt}) rather than by family and style.
 */
public final class SourceSansProFont {

    /**
     * Family name for basic styles (regular and italic).
     * <p>
     * Usage:
     * <pre>{@code
     * new Font( SourceSansProFont.FAMILY, Font.PLAIN, 12 );
     * new Font( SourceSansProFont.FAMILY, Font.ITALIC, 12 );
     * }</pre>
     */
    public static final String FAMILY = "Source Sans Pro SongScribe";

    /**
     * Use for MyFontUtils.installLocalFont to install single font style.
     */
    public static final String STYLE_REGULAR = "SourceSansProSongScribe-Regular.ttf";
    public static final String STYLE_ITALIC = "SourceSansProSongScribe-Italic.ttf";
    public static final String STYLE_BOLD = "SourceSansProSongScribe-Bold.ttf";
    public static final String STYLE_BOLD_ITALIC =
        "SourceSansProSongScribe-BoldItalic.ttf";

    /**
     * Semibold faces. These are not part of the RIBI {@link #FAMILY} (they carry no
     * legacy family name); they are registered so they are available as document
     * fonts, resolved by their PostScript names.
     */
    public static final String STYLE_SEMIBOLD = "SourceSansProSongScribe-Semibold.ttf";
    public static final String STYLE_SEMIBOLD_ITALIC =
        "SourceSansProSongScribe-SemiboldItalic.ttf";

    private SourceSansProFont() {}

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
            SourceSansProFont::installBasic
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
     * Creates and registers the regular, italic, bold and bold-italic faces of
     * {@link #FAMILY}, plus the semibold document faces.
     * <p>
     * When using FlatLaf, consider using {@link #installLazy()}.
     */
    public static void installBasic() {
        MyFontUtils.installLocalFont(STYLE_REGULAR);
        MyFontUtils.installLocalFont(STYLE_ITALIC);
        MyFontUtils.installLocalFont(STYLE_BOLD);
        MyFontUtils.installLocalFont(STYLE_BOLD_ITALIC);
        MyFontUtils.installLocalFont(STYLE_SEMIBOLD);
        MyFontUtils.installLocalFont(STYLE_SEMIBOLD_ITALIC);
    }
}
