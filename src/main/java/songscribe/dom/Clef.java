/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents the treble clef at the start of a staff line.
 * <p>
 * The Clef is positioned absolutely at the line start and does not contribute
 * to Staff's bounds. The gap from here to the key signature belongs to the layout,
 * not to this object — see {@code StaffHeaderMetrics.CLEF_GAP_SS}.
 * <p>
 * Note: This application only uses treble clef.
 */
public class Clef extends LineElement {

    /** Content width of the treble clef glyph in staff spaces, from SMuFL metadata. */
    private static final double CONTENT_WIDTH_SS;

    /** Content height of the treble clef glyph in staff spaces, from SMuFL metadata. */
    private static final double CONTENT_HEIGHT_SS;

    static {
        var bbox = SMuFLMetadata.requireBBox(SMuFLGlyph.G_CLEF);
        CONTENT_WIDTH_SS = bbox.width();
        CONTENT_HEIGHT_SS = bbox.height();
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.ssToPx(CONTENT_WIDTH_SS);
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.ssToPx(CONTENT_HEIGHT_SS);
    }
}
