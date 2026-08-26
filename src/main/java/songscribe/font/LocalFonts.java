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
import java.awt.GraphicsEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.error.RuntimeError;

/**
 * The font files the application ships with, under {@code /fonts/} on the classpath.
 * <p>
 * A shipped face can be used two ways, and they are separate decisions. {@link #load} hands the
 * face straight to a caller that draws with it — the notation and icon faces, which no document
 * ever names. {@link #install} registers the face with the graphics environment, which is what
 * makes it resolvable by name, so a document can store it and
 * {@link InstalledFonts#createFont} can find it again.
 * <p>
 * A shipped face is expected to be present: its absence is a broken installation, not a
 * condition a caller handles.
 */
public final class LocalFonts {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFonts.class);

    private LocalFonts() {}

    /**
     * Loads a shipped face without registering it, so nothing can resolve it by name.
     *
     * @param filename the file's name under {@code /fonts/}, extension included
     * @param size     the point size to derive the face at; a size of zero or less leaves the
     *                 face at the 1pt size {@code Font.createFont} produces, for a caller that
     *                 derives its own sizes
     * @return the kerned face
     * @throws songscribe.error.RuntimeError if the file is missing from the classpath or cannot
     *                                       be read as a font, either of which means a broken
     *                                       installation
     */
    public static Font load(String filename, float size) {
        try (
            var stream =
                LocalFonts.class.getResourceAsStream("/fonts/" + filename)
        ) {
            if (stream == null) {
                throw RuntimeError.missingResource("Font resource not found: " + filename);
            }

            var font = InstalledFonts.deriveKernedFont(
                Font.createFont(Font.TRUETYPE_FONT, stream)
            );

            if (size > 0) {
                return font.deriveFont(size);
            }

            return font;
        } catch (Exception e) {
            throw RuntimeError.missingResource("Could not load font: " + filename);
        }
    }

    /**
     * Registers a shipped face with the graphics environment at its natural size, so that it can
     * be resolved by family or PostScript name from then on.
     *
     * @param fontName the file's name under {@code /fonts/}, extension included
     * @effects the face becomes resolvable by name for the life of the process
     * @log warns when the graphics environment refuses the face, which normally means it is
     *      already registered
     */
    public static void install(String fontName) {
        install(fontName, 0);
    }

    /**
     * Registers a shipped face with the graphics environment, so that it can be resolved by
     * family or PostScript name from then on.
     *
     * @param fontName the file's name under {@code /fonts/}, extension included
     * @param size     the point size to register the face at; zero or less registers it at its
     *                 natural size
     * @effects the face becomes resolvable by name for the life of the process
     * @log warns when the graphics environment refuses the face, which normally means it is
     *      already registered
     */
    public static void install(String fontName, float size) {
        var font = load(fontName, size);
        var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        if (!ge.registerFont(font)) {
            LOG.warn("Could not register font (may already be registered): {}", fontName);
        }
    }
}
