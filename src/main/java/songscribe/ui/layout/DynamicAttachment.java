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

import org.jspecify.annotations.Nullable;

import songscribe.music.StaffElement;

/**
 * Represents a dynamic marking attachment on a note.
 * <p>
 * Dynamics indicate volume levels (p, f, mf, etc.) and are typically
 * placed below the staff, centered on the note.
 */
public class DynamicAttachment extends Attachment {

    /** Default width for dynamic markings. */
    private static final double DEFAULT_WIDTH = 20.0;

    /** Default height for dynamic markings. */
    private static final double DEFAULT_HEIGHT = 14.0;

    /**
     * Types of dynamic markings.
     */
    public enum DynamicType {
        /** Pianissimo - very soft (pp) */
        PIANISSIMO("pp"),
        /** Piano - soft (p) */
        PIANO("p"),
        /** Mezzo-piano - moderately soft (mp) */
        MEZZO_PIANO("mp"),
        /** Mezzo-forte - moderately loud (mf) */
        MEZZO_FORTE("mf"),
        /** Forte - loud (f) */
        FORTE("f"),
        /** Fortissimo - very loud (ff) */
        FORTISSIMO("ff"),
        /** Sforzando - sudden accent (sfz) */
        SFORZANDO("sfz"),
        /** Fortepiano - loud then soft (fp) */
        FORTEPIANO("fp");

        private final String symbol;

        DynamicType(String symbol) {
            this.symbol = symbol;
        }

        /**
         * Returns the text symbol for this dynamic.
         */
        public String getSymbol() {
            return symbol;
        }
    }

    /** The dynamic type. */
    private DynamicType type;

    /**
     * Creates a dynamic attachment with the specified type.
     *
     * @param type The dynamic type
     */
    public DynamicAttachment(DynamicType type) {
        this.type = type;
        setAlignment(Alignment.CENTER);
    }

    /**
     * Creates a dynamic attachment attached to a note.
     *
     * @param parent The parent note
     * @param type   The dynamic type
     */
    public DynamicAttachment(@Nullable StaffElement parent, DynamicType type) {
        this.type = type;
        setOwnerElement(parent);
        setAlignment(Alignment.CENTER);

        if (parent != null) {
            setOwnerElement(parent);
            setParentLine(parent.getParentLine());
        }
    }

    /**
     * Returns the dynamic type.
     */
    public DynamicType getType() {
        return type;
    }

    /**
     * Sets the dynamic type.
     */
    public void setType(DynamicType type) {
        this.type = type;
    }

    /**
     * Returns the symbol text for this dynamic.
     */
    public String getSymbol() {
        return type.getSymbol();
    }

    @Override
    public double getContentWidth() {
        // Width varies by dynamic type; this is an estimate
        return DEFAULT_WIDTH;
    }

    @Override
    public double getContentHeight() {
        return DEFAULT_HEIGHT;
    }
}
