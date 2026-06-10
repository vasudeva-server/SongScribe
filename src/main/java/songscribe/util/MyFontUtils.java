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
package songscribe.util;

import module java.desktop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.util.FontUtils;
import songscribe.error.RuntimeError;

public final class MyFontUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MyFontUtils.class);

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

    private static final Set<String> familyNames = new HashSet<>();
    private static final Map<String, Font> psFonts = new HashMap<>();
    private static List<Font> allFonts = List.of();


    @Nullable
    private static Font iconFont = null;

    record ParsedFontName(String family, String style) {}

    private MyFontUtils() {}

    // Clears the lazy font cache so tests can install fonts before the first load.
    static void resetFontCache() {
        allFonts = List.of();
        psFonts.clear();
        familyNames.clear();
    }

    public static List<Font> getAllFonts() {
        if (allFonts.isEmpty()) {
            var fonts = FontUtils.getAllFonts();

            if (fonts == null || fonts.length == 0) {
                throw RuntimeError.exit("Could not load system fonts");
            }

            var attributes = new HashMap<TextAttribute, Object>();
            attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
            var kernedFonts = new ArrayList<Font>(fonts.length);

            for (var font : fonts) {
                familyNames.add(font.getFamily());

                // We want kerning to be on for all fonts!
                var kernedFont = font.deriveFont(attributes);
                kernedFonts.add(kernedFont);
                psFonts.put(font.getPSName(), kernedFont);
            }

            allFonts = List.copyOf(kernedFonts);
        }

        return allFonts;
    }

    @SuppressWarnings("SameReturnValue")
    private static Set<String> getFamilyNames() {
        getAllFonts();
        return familyNames;
    }

    @SuppressWarnings("SameReturnValue")
    private static Map<String, Font> getPSFonts() {
        getAllFonts();
        return psFonts;
    }

    /*
      This method attempts to create a font from the given PS name and size. If the font is
      not found, the method will return the label font.
    */
    public static Font createFont(String psName, int size) {
        return createFont(psName, size, "Label.font");
    }

    /*
      This method attempts to create a font from the given PS name. If the font is not found,
      the method will return the font with the given UIManager key. If no font with that key
      exists, the default JLabel font is returned.
    */
    public static Font createFont(
        String psName,
        int size,
        String fallbackKey
    ) {
        // Is it a known PostScript name?
        var font = getPSFonts().get(psName);

        return (font != null ? font : getUIFont(fallbackKey))
            .deriveFont((float) size);
    }

    public static Font deriveKernedFont(Font font) {
        var attributes = new HashMap<TextAttribute, Object>();
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        return font.deriveFont(attributes);
    }

    public static Font getUIFont(String key) {
        var font = UIManager.getFont(key);

        if (font == null) {
            font = new JLabel().getFont();
        }

        return font;
    }


    public static Font getIconFont() {
        if (iconFont == null) {
            iconFont = getLocalFont("MusescoreIcon.otf", 20);
        }

        return iconFont;
    }

    public static Font deriveBaselineShiftedFont(
        Font font,
        int shift
    ) {
        if (shift == 0) {
            return font;
        }

        var transform = new AffineTransform();
        transform.translate(0, shift);
        return font.deriveFont(transform);
    }

    public static Font getLocalFont(String filename) {
        return getLocalFont(filename, 0);
    }

    /**
     * Loads but does not register a local font. If size == 0, the default 1pt size is returned.
     */
    public static Font getLocalFont(String filename, float size) {
        try (
            var stream =
                MyFontUtils.class.getResourceAsStream("/fonts/" + filename)
        ) {
            if (stream == null) {
                throw RuntimeError.exit("Font resource not found: " + filename);
            }

            var font = deriveKernedFont(
                Font.createFont(Font.TRUETYPE_FONT, stream)
            );

            if (size > 0) {
                return font.deriveFont(size);
            }

            return font;
        } catch (Exception e) {
            throw RuntimeError.exit("Could not load font: " + filename);
        }
    }

    public static void installLocalFont(String fontName) {
        installLocalFont(fontName, 0);
    }

    public static void installLocalFont(String fontName, float size) {
        var font = getLocalFont(fontName, size);
        var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        if (!ge.registerFont(font)) {
            LOG.warn("Could not register font (may already be registered): {}", fontName);
        }
    }

    // This method is used to parse the PostScript name of a font. The PostScript name is
    // usually in the form "FamilyName-StyleName" or "FamilyName_StyleName". The method
    // will return a pair of strings, the first being the family name and the second being
    // the style name. If the style name contains hyphens, they will be removed.
    // If no style name is found, the second string will be empty.
    public static ParsedFontName parsePSName(String psName) {
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

    public static String getFullFontDescription(Font font) {
        var family = font.getFamily();
        var style = getStyleDescription(font);
        return family + ' ' + style + ' ' + font.getSize() + " pt";
    }

    public static String getStyleDescription(Font font) {
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
        // of the style, e.g. the family is "Source Sans Pro Semibold" and the style is
        // "Semibold" or "Semibold Italic".
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

    // Reused scratch graphics for font-metric lookups. getFontMetrics(font) is a
    // read-only query that does not mutate the backing image, so a single shared
    // instance avoids allocating a BufferedImage on every call.
    private static final Graphics METRICS_GRAPHICS =
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics();

    public static FontMetrics getFontMetrics(Font font) {
        return METRICS_GRAPHICS.getFontMetrics(font);
    }

}
