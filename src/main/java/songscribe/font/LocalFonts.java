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
 * {@link #load} hands a face straight to a caller that draws with it; {@link #install}
 * registers it so {@link InstalledFonts#createFont} can resolve it by name.
 * <p>
 * A shipped face is expected to be present: its absence is a broken installation, not a
 * condition a caller handles.
 */
public final class LocalFonts {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFonts.class);

    private LocalFonts() {}

    /**
     * A missing or unreadable file is a broken installation: the failure is shown as a fatal
     * error and the application exits, so the method does not return in that case.
     *
     * @param size a size of zero or less leaves the face at the 1pt size
     *             {@code Font.createFont} produces
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
            throw RuntimeError.missingResource("Could not load font: " + filename, e);
        }
    }

    /**
     * The face becomes resolvable by name for the life of the process.
     */
    public static void install(String fontName) {
        install(fontName, 0);
    }

    /**
     * The face becomes resolvable by name for the life of the process.
     *
     * @param size zero or less registers the face at its natural size
     */
    public static void install(String fontName, float size) {
        var font = load(fontName, size);
        var ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        if (!ge.registerFont(font)) {
            LOG.warn("Could not register font (may already be registered): {}", fontName);
        }
    }
}
