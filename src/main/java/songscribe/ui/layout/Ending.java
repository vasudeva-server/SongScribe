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

package songscribe.ui.layout;

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents a first or second ending bracket above a repeated section.
 * <p>
 * Endings are the "1." and "2." brackets drawn above the staff to indicate
 * which measures to play on each repetition. They can span multiple measures.
 */
public class Ending extends RangeElement {

    /** Notehead width for span width calculation. */
    private static final double NOTE_HEAD_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).width();

    /**
     * The type of ending (first or second).
     */
    public enum Type {
        FIRST,
        SECOND
    }

    private @Nullable StaffElement endNote;
    private Type type = Type.FIRST;
    private int yPositionSs = 0;

    /**
     * Creates an ending bracket.
     *
     * @param anchorNote The first note of the ending
     * @param endNote    The last note of the ending
     * @param type       Whether this is a first or second ending
     */
    public Ending(StaffElement anchorNote, StaffElement endNote, Type type) {
        setAnchorElement(anchorNote);
        this.endNote = endNote;
        this.type = type;
    }

    @Override
    public @Nullable StaffElement getEndElement() {
        return endNote;
    }

    /**
     * Sets the end note of this ending.
     */
    public void setEndNote(@Nullable StaffElement endNote) {
        this.endNote = endNote;
    }

    @Override
    public int getNoteCount() {
        var anchor = getAnchorElement();

        if (anchor == null || endNote == null) {
            return 0;
        }

        int startIndex = getAnchorElementIndex();
        int endIndex = getEndElementIndex();

        if (startIndex < 0 || endIndex < 0) {
            return 0;
        }

        return endIndex - startIndex + 1;
    }

    @Override
    public boolean isAbove() {
        // Endings are always above the staff
        return true;
    }

    /**
     * Returns the ending type (first or second).
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the ending type.
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Returns the user-adjustable Y offset for this ending bracket.
     */
    public int getYPositionSs() {
        return yPositionSs;
    }

    /**
     * Sets the user-adjustable Y offset for this ending bracket.
     */
    public void setYPositionSs(int yPositionSs) {
        this.yPositionSs = yPositionSs;
    }

    /**
     * Returns the label text for this ending ("1." or "2.").
     */
    public String getLabel() {
        return type == Type.FIRST ? "1." : "2.";
    }

    /**
     * Returns the height of the volta bracket in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return LayoutStylesheet.VOLTA_TICK_HEIGHT_SS;
    }

    /**
     * Returns the horizontal span width for collision detection.
     *
     * @param anchorXSs X position of the anchor note in staff-space units
     * @param endXSs    X position of the end note in staff-space units
     * @return span width in staff-space units
     */
    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(NOTE_HEAD_WIDTH_SS, endXSs - anchorXSs + NOTE_HEAD_WIDTH_SS);
    }

    @Override
    public double getContentWidthPx() {
        var anchor = getAnchorElement();

        if (anchor == null || endNote == null) {
            return 0;
        }

        return Math.abs(endNote.getXSs() - anchor.getXSs()) + endNote.getContentWidthPx();
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getContentHeightSs());
    }
}
