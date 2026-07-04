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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.shape.AccentShape;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents an articulation marking on a staff element.
 * <p>
 * Articulations modify how an element is played (staccato, accent, etc.).
 * They are drawn opposite the stem, outward from the staff:
 * <ul>
 *   <li>For downward stems: articulations go above the staff</li>
 *   <li>For upward stems: articulations go below the staff</li>
 * </ul>
 */
public class Articulation extends LineElement {

    // SMuFL bbox-derived dimensions in staff-space units
    private static final double STACCATO_WIDTH_SS;
    private static final double STACCATO_HEIGHT_SS;

    // AccentShape is a custom (non-SMuFL) wedge, so its dimensions come from its own bounds.
    private static final double ACCENT_WIDTH_SS;
    private static final double ACCENT_HEIGHT_SS;

    static {
        var staccatoBBox = SMuFLMetadata.requireBBox(SMuFLGlyph.ARTIC_STACCATO_ABOVE);
        STACCATO_WIDTH_SS = staccatoBBox.width();
        STACCATO_HEIGHT_SS = staccatoBBox.height();

        var accentBounds = AccentShape.accent().getBounds2D();
        ACCENT_WIDTH_SS = accentBounds.getWidth();
        ACCENT_HEIGHT_SS = accentBounds.getHeight();
    }

    /** The staff element this articulation belongs to. */
    private @Nullable StaffElement ownerElement;

    /** The type of articulation (STACCATO, ACCENT, etc.). */
    private ArticulationType type;

    /**
     * Creates a new articulation of the specified type.
     *
     * @param type The articulation type
     */
    public Articulation(ArticulationType type) {
        this.type = type;
    }

    /**
     * Creates a new articulation attached to a staff element.
     *
     * @param parent The owner element
     * @param type   The articulation type
     */
    public Articulation(@Nullable StaffElement parent, ArticulationType type) {
        ownerElement = parent;
        this.type = type;

        if (parent != null) {
            setParentElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Returns the staff element this articulation belongs to.
     */
    public @Nullable StaffElement getOwnerElement() {
        return ownerElement;
    }

    /**
     * Sets the staff element this articulation belongs to.
     */
    public void setOwnerElement(@Nullable StaffElement ownerElement) {
        this.ownerElement = ownerElement;
    }

    /**
     * Returns the articulation type.
     */
    public ArticulationType getType() {
        return type;
    }

    /**
     * Sets the articulation type.
     */
    public void setType(ArticulationType type) {
        this.type = type;
    }

    /**
     * Returns whether this is a staccato articulation.
     */
    public boolean isStaccato() {
        return type == ArticulationType.STACCATO;
    }

    /**
     * Returns whether this is an accent articulation.
     */
    public boolean isAccent() {
        return type == ArticulationType.ACCENT;
    }

    /**
     * Returns the content width in staff-space units, derived from SMuFL bounding box data.
     */
    @Override
    public double getContentWidthSs() {
        return isStaccato() ? STACCATO_WIDTH_SS : ACCENT_WIDTH_SS;
    }

    /**
     * Returns the content height in staff-space units, derived from SMuFL bounding box data.
     */
    @Override
    public double getContentHeightSs() {
        return isStaccato() ? STACCATO_HEIGHT_SS : ACCENT_HEIGHT_SS;
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.ssToPx(getContentWidthSs());
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.ssToPx(getContentHeightSs());
    }
}
