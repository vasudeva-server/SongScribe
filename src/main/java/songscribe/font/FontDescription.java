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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A face named the way a person reads it, rather than the way a font file spells it.
 * <p>
 * A font file's own names are written for machines: styles arrive abbreviated
 * ({@code It}, {@code Cn}), run together in camel case ({@code SemiBoldItalic}), and repeat a
 * weight the family name already carries ({@code Source Sans 3 SemiBold SemiBold}). What this
 * class produces is what belongs in a label beside a font chooser.
 * <p>
 * The names are for display only. Nothing here round-trips: a description cannot be turned back
 * into the PostScript name it came from, and a stored font name is resolved by
 * {@link InstalledFonts#createFont} instead.
 */
public final class FontDescription {

    private static final Pattern STYLE_PATTERN = Pattern.compile(
        "^(?:(?:Extra)?Light|Regular|Medium|Semi[Bb]old|(?:Extra)?Bold|Black|(?:Extra)" +
        "?Heavy|Thin|Italic" +
        "|Oblique|Condensed).*"
    );
    private static final Pattern OSF_PATTERN = Pattern.compile("OsF");
    private static final Pattern COMPOUND_STYLE_PATTERN = Pattern.compile(
        "\\b([SD]emi|Ultra|Extra)(Bold|Light|Heavy)"
    );
    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile(
        "(\\p{Ll})(\\p{Lu})"
    );

    private record Abbreviation(Pattern pattern, String expansion) {}

    private static final List<Abbreviation> ABBREVIATIONS =
        Arrays.asList(
            new Abbreviation(Pattern.compile("\\bIta?\\b"), "Italic"),
            new Abbreviation(Pattern.compile("\\bSC\\b"), "Small Caps"),
            new Abbreviation(Pattern.compile("\\b(?:Cn|Cond)\\b"), "Condensed"),
            new Abbreviation(Pattern.compile("\\bExt\\b"), "Extended"),
            new Abbreviation(Pattern.compile("\\bExp\\b"), "Expanded"),
            new Abbreviation(Pattern.compile("\\bDB\\b"), "Demi Bold"),
            new Abbreviation(Pattern.compile("\\bSupp\\b"), "Supplemental")
        );

    record ParsedFontName(String family, String style) {}

    private FontDescription() {}

    /**
     * The whole of a face named for a person: family, style and point size.
     *
     * @param font the face to describe
     * @return the family, then the {@link #style} description, then the size followed by
     *         {@code pt}, separated by single spaces
     */
    public static String full(Font font) {
        var family = font.getFamily();
        var style = style(font);
        return family + ' ' + style + ' ' + font.getSize() + " pt";
    }

    /**
     * The style of a face, named for a person and normalized.
     * <p>
     * Normalization is what makes two faces from different foundries comparable in a list:
     * abbreviations are expanded to whole words ({@code It} to {@code Italic}), run-together
     * camel case is split into words while compound weights stay one word ({@code SemiBoldIt}
     * to {@code Semibold Italic}), and a weight the family name already ends with is not said
     * twice — the {@code Source Sans 3 SemiBold} family's own face is {@code Regular}, not
     * {@code SemiBold}.
     *
     * @param font the face to describe
     * @return the normalized style description
     * @invariant the result is never empty; a face whose file declares no style, or whose style
     *            is entirely absorbed by the family name, is described as {@code Regular}
     */
    public static String style(Font font) {
        var parsed = parsePSName(font.getPSName());
        var psFamily = parsed.family();
        var psStyle = parsed.style();

        /*
            If there is no style, usually the family is the family name alone,
            in which case we return "Regular" for the style. But we need to handle cases
            like this:

            Damascus (Damascus)
            Damascus (DamascusBold)
            Damascus (DamascusLight)
            Damascus (DamascusMedium)
            Damascus (DamascusSemiBold)
        */
        if (psStyle.isEmpty()) {
            var fontFamily = font.getFamily();

            if (psFamily.equals(fontFamily)) {
                return "Regular";
            }

            if (psFamily.startsWith(fontFamily)) {
                // Check to see if the portion after the font family name is a style.
                // If so, we will parse it. Otherwise, assume it's part of the family name.
                var afterFamily = psFamily.substring(fontFamily.length());

                if (STYLE_PATTERN.matcher(afterFamily).matches()) {
                    psStyle += afterFamily;
                } else {
                    return "Regular";
                }
            } else {
                // There are no discernible styles
                return "Regular";
            }
        }

        var styles = parseStyle(psStyle);

        // Deal with the case where the last word of the family is the first word
        // of the style, e.g. the family is "Source Sans 3 SemiBold" and the style is
        // "SemiBold" or "SemiBold Italic".
        var familyWords = font.getFamily().split(" ");
        var styleWords = styles.split(" ");

        if ((familyWords.length > 0) && (styleWords.length > 0)) {
            if (
                familyWords[familyWords.length - 1].equalsIgnoreCase(
                        styleWords[0]
                    )
            ) {
                // If the style has only one word, change it to "Regular".
                if (styleWords.length == 1) {
                    styles = "Regular";
                } else {
                    // If it has more than one word, remove the first word.
                    styles = String.join(
                        " ",
                        Arrays.copyOfRange(styleWords, 1, styleWords.length)
                    );
                }
            }
        }

        return styles;
    }

    // This method is used to parse the PostScript name of a font. The PostScript name is
    // usually in the form "FamilyName-StyleName" or "FamilyName_StyleName". The method
    // will return a pair of strings, the first being the family name and the second being
    // the style name. If the style name contains hyphens, they will be removed.
    // If no style name is found, the second string will be empty.
    static ParsedFontName parsePSName(String psName) {
        // First split on "_". Everthing before the first "_" is the family name,
        // and everything after is the style name.
        var parts = Arrays.asList(psName.split("_"));

        if (parts.size() > 1) {
            // If there are hyphens in the style name, we need to remove them
            parts.set(1, parts.get(1).replace("-", ""));
            return new ParsedFontName(parts.getFirst(), parts.get(1));
        }

        // Split on hyphen. The last part is the style name.
        parts = Arrays.asList(psName.split("-"));

        /*
            If there is only one part, usually it's the family name alone,
            in which case we return "Regular". But we need to handle cases
            like this:

            Damascus (Damascus)
            Damascus (DamascusBold)
            Damascus (DamascusLight)
            Damascus (DamascusMedium)
            Damascus (DamascusSemiBold)
        */
        if (parts.size() == 1) {
            return new ParsedFontName(parts.getFirst(), "");
        }

        // There may actually be a hyphen in the family name, only the last part is the styles
        return new ParsedFontName(
            String.join("-", parts.subList(0, parts.size() - 1)),
            parts.getLast()
        );
    }

    static String parseStyle(String style) {
        // Replace some camel case style names that should not be separated
        var result = OSF_PATTERN.matcher(style).replaceAll("Oldstyle Figures");

        // Normalize the capitalization of compond styles like "SemiBold" to "Semibold"
        // so that they don't become multiple words.
        result = COMPOUND_STYLE_PATTERN.matcher(result).replaceAll(
            matchResult ->
                matchResult.group(1) + matchResult.group(2).toLowerCase()
        );

        // Convert camel case to separate words
        result = CAMEL_CASE_PATTERN.matcher(result).replaceAll("$1 $2");

        // Convert abbreviations to full words
        for (var abbr : ABBREVIATIONS) {
            result = abbr.pattern().matcher(result).replaceAll(abbr.expansion());
        }

        return result;
    }
}
