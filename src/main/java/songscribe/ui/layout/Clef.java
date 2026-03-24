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

package songscribe.ui.layout;

/**
 * Represents the treble clef at the start of a staff line.
 * <p>
 * The Clef is positioned absolutely at the line start and does not contribute
 * to Staff's bounds. It has its own margin for spacing from KeySignature.
 * <p>
 * Note: This application only uses treble clef.
 */
public class Clef extends LineElement {

    /** Default width of the treble clef glyph in pixels. */
    private static final double DEFAULT_WIDTH_PX = 28.0;

    /** Default height of the treble clef glyph in pixels. */
    private static final double DEFAULT_HEIGHT_PX = 64.0;

    /** Width of the clef glyph. */
    private double widthPx = DEFAULT_WIDTH_PX;

    /** Height of the clef glyph. */
    private double heightPx = DEFAULT_HEIGHT_PX;

    /**
     * Creates a clef with default dimensions.
     */
    public Clef() {
        // Default margin from clef to key signature
        setMarginRightSs(0.5);
    }

    /**
     * Creates a clef with the specified dimensions.
     *
     * @param width  Width of the clef glyph in pixels
     * @param height Height of the clef glyph in pixels
     */
    public Clef(double widthPx, double heightPx) {
        this();
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    @Override
    public double getContentWidthPx() {
        return widthPx;
    }

    @Override
    public double getContentHeightPx() {
        return heightPx;
    }

    /**
     * Sets the dimensions of the clef glyph.
     *
     * @param width  Width in pixels
     * @param height Height in pixels
     */
    public void setDimensions(double widthPx, double heightPx) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }
}
