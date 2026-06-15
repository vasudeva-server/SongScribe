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
 *
 * <p>The family and PostScript names are namespaced with {@code SongScribe} so the
 * bundled faces never collide with a copy of Source Sans 3 installed on the
 * host system (a PostScript-name collision makes {@code registerFont} silently
 * reject the bundled face).
 *
 * <p>All eight faces share the single family {@link #FAMILY}. The regular
 * ({@link #STYLE_REGULAR}), italic ({@link #STYLE_ITALIC}), bold
 * ({@link #STYLE_BOLD}), and bold-italic ({@link #STYLE_BOLD_ITALIC}) faces are
 * resolved by family and AWT style constant. The medium ({@link #STYLE_MEDIUM},
 * {@link #STYLE_MEDIUM_ITALIC}, weight 500) and semibold ({@link #STYLE_SEMIBOLD},
 * {@link #STYLE_SEMIBOLD_ITALIC}, weight 600) faces have no AWT
 * {@code Font.MEDIUM} / {@code Font.SEMIBOLD} equivalent and must be resolved
 * by their PostScript names (e.g. {@code SourceSans3SongScribe-Medium},
 * {@code SourceSans3SongScribe-SemiBold}).
 */
public final class SourceSans3Font {

    /**
     * Shared family name for all eight faces.
     * <p>
     * Usage:
     * <pre>{@code
     * new Font( SourceSans3Font.FAMILY, Font.PLAIN, 12 );
     * new Font( SourceSans3Font.FAMILY, Font.ITALIC, 12 );
     * }</pre>
     */
    public static final String FAMILY = "Source Sans 3 SongScribe";

    /**
     * PostScript-name prefix shared by all eight faces — {@link #FAMILY} with spaces
     * removed plus a hyphen (e.g. {@code SourceSans3SongScribe-SemiBold}). Derived from
     * {@link #FAMILY} so the two cannot drift.
     */
    public static final String PS_PREFIX = FAMILY.replace(" ", "") + "-";

    /** Regular (PLAIN) face filename. */
    public static final String STYLE_REGULAR = "source-sans-3-regular.ttf";

    /** Italic face filename. */
    public static final String STYLE_ITALIC = "source-sans-3-italic.ttf";

    /**
     * Medium (weight 500) face filename. Resolved by PostScript name
     * {@code SourceSans3SongScribe-Medium} — AWT has no {@code Font.MEDIUM} constant.
     */
    public static final String STYLE_MEDIUM = "source-sans-3-500.ttf";

    /**
     * Medium italic (weight 500) face filename. Resolved by PostScript name
     * {@code SourceSans3SongScribe-MediumItalic}.
     */
    public static final String STYLE_MEDIUM_ITALIC = "source-sans-3-500italic.ttf";

    /**
     * SemiBold (weight 600) face filename. Resolved by PostScript name
     * {@code SourceSans3SongScribe-SemiBold} — AWT has no {@code Font.SEMIBOLD} constant.
     */
    public static final String STYLE_SEMIBOLD = "source-sans-3-600.ttf";

    /**
     * SemiBold italic (weight 600) face filename. Resolved by PostScript name
     * {@code SourceSans3SongScribe-SemiBoldItalic}.
     */
    public static final String STYLE_SEMIBOLD_ITALIC = "source-sans-3-600italic.ttf";

    /** Bold (BOLD) face filename. */
    public static final String STYLE_BOLD = "source-sans-3-700.ttf";

    /** Bold italic (BOLD_ITALIC) face filename. */
    public static final String STYLE_BOLD_ITALIC = "source-sans-3-700italic.ttf";

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
            SourceSans3Font::install
        );
    }

    /**
     * Creates and registers all eight faces of {@link #FAMILY}.
     * <p>
     * When using FlatLaf, consider using {@link #installLazy()}.
     */
    public static void install() {
        MyFontUtils.installLocalFont(STYLE_REGULAR);
        MyFontUtils.installLocalFont(STYLE_ITALIC);
        MyFontUtils.installLocalFont(STYLE_MEDIUM);
        MyFontUtils.installLocalFont(STYLE_MEDIUM_ITALIC);
        MyFontUtils.installLocalFont(STYLE_SEMIBOLD);
        MyFontUtils.installLocalFont(STYLE_SEMIBOLD_ITALIC);
        MyFontUtils.installLocalFont(STYLE_BOLD);
        MyFontUtils.installLocalFont(STYLE_BOLD_ITALIC);
    }
}
