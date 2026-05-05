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
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents a dynamic marking attachment on a note.
 * <p>
 * Dynamics indicate volume levels (p, f, mf, etc.) and are typically
 * placed below the staff, centered on the note.
 */
public class DynamicAttachment extends Attachment {

    /** Default width for dynamic markings in staff-space units. */
    private static final double DEFAULT_WIDTH_SS = 2.5;  // 20px

    /** Default height for dynamic markings in staff-space units. */
    private static final double DEFAULT_HEIGHT_SS = 1.75;  // 14px

    /**
     * Types of dynamic markings.
     */
    public enum DynamicType {
        /** Pianissimo - very soft (pp) */
        PIANISSIMO("pp", SMuFLGlyph.DYNAMIC_PP, 0.25),
        /** Piano - soft (p) */
        PIANO("p", SMuFLGlyph.DYNAMIC_PIANO, 0.40),
        /** Mezzo-piano - moderately soft (mp) */
        MEZZO_PIANO("mp", SMuFLGlyph.DYNAMIC_MP, 0.53),
        /** Mezzo-forte - moderately loud (mf) */
        MEZZO_FORTE("mf", SMuFLGlyph.DYNAMIC_MF, 0.63),
        /** Forte - loud (f) */
        FORTE("f", SMuFLGlyph.DYNAMIC_FORTE, 0.78),
        /** Fortissimo - very loud (ff) */
        FORTISSIMO("ff", SMuFLGlyph.DYNAMIC_FF, 1.00),
        /** Sforzando - sudden accent (sfz) */
        SFORZANDO("sfz", null, 0.90),
        /** Fortepiano - loud then soft (fp) */
        FORTEPIANO("fp", null, 0.78);

        private final String symbol;
        private final @Nullable SMuFLGlyph glyph;
        private final double velocityFraction;

        DynamicType(String symbol, @Nullable SMuFLGlyph glyph, double velocityFraction) {
            this.symbol = symbol;
            this.glyph = glyph;
            this.velocityFraction = velocityFraction;
        }

        /**
         * Returns the text symbol for this dynamic.
         */
        public String getSymbol() {
            return symbol;
        }

        /**
         * Returns the SMuFL glyph for rendering, or null for types without a UI glyph.
         */
        public @Nullable SMuFLGlyph getGlyph() {
            return glyph;
        }

        /**
         * Returns the velocity fraction (0.0–1.0) for MIDI playback.
         */
        public double getVelocityFraction() {
            return velocityFraction;
        }
    }

    /** The dynamic type. */
    private final DynamicType type;

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
     * Returns the symbol text for this dynamic.
     */
    public String getSymbol() {
        return type.getSymbol();
    }

    /**
     * Returns the content width in staff-space units.
     */
    @Override
    public double getContentWidthSs() {
        var glyph = type.getGlyph();

        if (glyph == null) {
            return DEFAULT_WIDTH_SS;
        }

        return SMuFLMetadata.getInstance().requireBBox(glyph).width();
    }

    /**
     * Returns the content height in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        var glyph = type.getGlyph();

        if (glyph == null) {
            return DEFAULT_HEIGHT_SS;
        }

        return SMuFLMetadata.getInstance().requireBBox(glyph).height();
    }

    @Override
    public double getContentWidthPx() {
        return ScaleContext.getInstance().toPixels(getContentWidthSs());
    }

    @Override
    public double getContentHeightPx() {
        return ScaleContext.getInstance().toPixels(getContentHeightSs());
    }
}
