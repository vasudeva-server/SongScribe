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
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.formdev.flatlaf.util.FontUtils;
import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.util.UIUtils;

/**
 * The faces installed on the host system, and the resolution of a stored font name to one of them.
 * <p>
 * Every face handed out from here has kerning switched on, so text drawn anywhere in the
 * application is spaced the way its designer intended without each caller asking for it.
 * <p>
 * The set of installed faces is read once, on first use, and never re-read: a face installed
 * while the application is running is not seen. Local faces the application ships are registered
 * by {@link LocalFonts} before the first query, so they are part of the set.
 * <p>
 * Callable from any thread with no locking on the query path. The system is asked for its faces
 * inside a holder class, so the JVM's class-initialization lock lets exactly one thread perform
 * the read, and what it publishes is immutable. This matters because the class is entered both
 * from the EDT, when the font chooser lists the families, and from the document-load path, when a
 * stored font name is resolved.
 */
public final class InstalledFonts {

    private InstalledFonts() {}

    /**
     * Every installed face, kerned, at the size the system reports it in.
     *
     * @return the installed faces, in the order the graphics environment lists them
     * @effects when the system reports no fonts at all — which leaves the application unable to
     *          draw text — the failure is logged, shown to the user as a fatal error, and the
     *          application exits, so the method does not return in that case
     */
    public static List<Font> getAllFonts() {
        return Holder.INSTANCE.all();
    }

    private static Set<String> getFamilyNames() {
        return Holder.INSTANCE.familyNames();
    }

    private static Map<String, Font> getPSFonts() {
        return Holder.INSTANCE.byPsName();
    }

    /**
     * Resolves a stored font name to an installed face at the requested size.
     * <p>
     * A stored name is not guaranteed to name an installed face: a document may have been written
     * on a machine with fonts this one does not have, and a hand-edited or older document may
     * store a display family such as {@code SignPainter} where a PostScript name is expected. The
     * method therefore always answers with a usable face, trying in turn:
     * <ol>
     *   <li>the installed face whose PostScript name is {@code psName};</li>
     *   <li>the PLAIN face of the installed family named {@code psName};</li>
     *   <li>the Source Sans 3 face closest to the weight and slant {@code psName} describes;</li>
     *   <li>the look-and-feel's label font.</li>
     * </ol>
     * The substitution is silent, so a caller cannot tell which step answered; a caller that
     * needs to know whether the exact face exists must ask {@link #getAllFonts()} itself.
     *
     * @param psName the PostScript name stored for the font, or the family name in an older document
     * @param size   the point size the returned face is derived at
     * @return a face at {@code size}, never null and never a face the system cannot draw
     * @invariant the returned face carries kerning unless the label font is the one that answered
     */
    public static Font createFont(String psName, int size) {
        var font = getPSFonts().get(psName);

        if (font == null) {
            font = findFamilyFont(psName);
        }

        if (font == null) {
            font = findClosestSourceSans3Font(psName);
        }

        if (font == null) {
            font = UIUtils.getUIFont("Label.font");
        }

        return font.deriveFont((float) size);
    }

    /*
      Resolves a font by family name when the PS-name lookup misses — a hand-edited or
      older document may store a display family (e.g. "SignPainter") rather than a
      PostScript name. Returns a kerned PLAIN face of that family, or null when the family
      is not installed. Only the family is known here (weight/style are not carried), so
      the PLAIN face is used.
    */
    @Nullable
    private static Font findFamilyFont(String family) {
        if (!getFamilyNames().contains(family)) {
            return null;
        }

        return deriveKernedFont(new Font(family, Font.PLAIN, 1));
    }

    /*
      Derives the closest Source Sans 3 SongScribe PostScript name from an arbitrary
      font name that was not found in the PS fonts map. The style is normalized via
      FontDescription so that abbreviations like "It" become "Italic".
      Returns null if no Source Sans 3 SongScribe face is registered (e.g. during tests
      before install() has been called).
    */
    @Nullable
    private static Font findClosestSourceSans3Font(String psName) {
        var parsed = FontDescription.parsePSName(psName);
        var normalizedStyle = FontDescription.parseStyle(parsed.style()).toLowerCase();
        var suffix = resolveSourceSans3Suffix(normalizedStyle);
        return getPSFonts().get(SourceSans3Font.PS_PREFIX + suffix);
    }

    /*
      Maps a normalized (lower-case) style description to the appropriate
      Source Sans 3 SongScribe PostScript suffix. Weight precedence: semibold >
      medium > bold > italic-only > regular. When italic is present alongside a
      weight, "Italic" is appended.
    */
    private static String resolveSourceSans3Suffix(String normalizedStyle) {
        var isSemiBold = normalizedStyle.contains("semibold");
        var isMedium = normalizedStyle.contains("medium");
        // "semibold" also contains "bold", so exclude it to keep the flags independent
        // of the branch order below.
        var isBold = normalizedStyle.contains("bold") && !isSemiBold;
        var isItalic = normalizedStyle.contains("italic");

        String weightSuffix;

        if (isSemiBold) {
            weightSuffix = "SemiBold";
        } else if (isMedium) {
            weightSuffix = "Medium";
        } else if (isBold) {
            weightSuffix = "Bold";
        } else if (isItalic) {
            // Italic with no weight variant maps to the Italic face.
            return "Italic";
        } else {
            weightSuffix = "Regular";
        }

        if (isItalic) {
            return weightSuffix + "Italic";
        }

        return weightSuffix;
    }

    /**
     * The same face with kerning switched on. Package-private because kerning is a promise this
     * package makes about every face it hands out, not a choice a caller elsewhere makes.
     *
     * @param font the face to derive from
     * @return a kerned derivation of {@code font}
     */
    static Font deriveKernedFont(Font font) {
        var attributes = new HashMap<TextAttribute, Object>();
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        return font.deriveFont(attributes);
    }

    /*
      Asks the graphics environment for its faces and builds the two indexes over them in the
      same pass, so the answer is complete before anything can be read from it.
    */
    private static Faces readInstalledFaces() {
        var fonts = FontUtils.getAllFonts();

        if (fonts == null || fonts.length == 0) {
            throw RuntimeError.exit(
                "Could not load system fonts",
                "SongScribe could not load the fonts installed on your system and must quit."
            );
        }

        var families = new HashSet<String>();
        var fontsByPsName = new HashMap<String, Font>();
        var kernedFonts = new ArrayList<Font>(fonts.length);

        for (var font : fonts) {
            families.add(font.getFamily());
            var kernedFont = deriveKernedFont(font);
            kernedFonts.add(kernedFont);
            fontsByPsName.put(font.getPSName(), kernedFont);
        }

        return new Faces(
            List.copyOf(kernedFonts),
            Set.copyOf(families),
            Map.copyOf(fontsByPsName)
        );
    }

    /**
     * The installed faces together with the two indexes over them, so that no query can see one
     * of the three ahead of the others.
     *
     * @param all         every installed face, kerned, in the order the graphics environment
     *                    lists them
     * @param familyNames the family name of each installed face
     * @param byPsName    each installed face under its PostScript name
     * @invariant all three are immutable, and the values of {@code byPsName} are exactly the
     *            elements of {@code all}
     */
    private record Faces(List<Font> all, Set<String> familyNames, Map<String, Font> byPsName) {}

    /**
     * Initialization-on-demand holder: the JVM's class-initialization lock makes the read of the
     * installed faces lazy and thread-safe without synchronizing every query. Both the EDT and
     * the document-load path reach it, and a partially built index would answer with the wrong
     * face rather than fail, so the read must happen once.
     */
    private static final class Holder {
        static final Faces INSTANCE = readInstalledFaces();
    }
}
