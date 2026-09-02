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

import org.jspecify.annotations.Nullable;

import songscribe.smufl.BBox;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Represents a dynamic marking attachment on a note.
 * <p>
 * Dynamics indicate volume levels (p, f, mf, etc.) and are typically
 * placed below the staff, centered on the note.
 */
public final class DynamicAttachment extends Attachment implements IntrinsicHeight {

    /**
     * Types of dynamic markings.
     * <p>
     * Every type here is one the editor can create and the renderer can draw. A dynamic with no
     * glyph would be invisible yet still reserve its footprint, silently pushing everything above
     * it outward, so the set is deliberately limited to those with one — {@code sfz} and
     * {@code fp} were declared without a glyph and are not supported (refs #510).
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
        FORTISSIMO("ff", SMuFLGlyph.DYNAMIC_FF, 1.00);

        private final String symbol;
        private final SMuFLGlyph glyph;
        private final double velocityFraction;

        DynamicType(String symbol, SMuFLGlyph glyph, double velocityFraction) {
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
         * Returns the {@link DynamicType} whose {@link #getSymbol() symbol} equals
         * {@code symbol}, or {@code null} if none matches. Inverse of
         * {@link #getSymbol()}.
         * <p>
         * A {@code null} answer is the normal result for a MusicXML file that carries a dynamic
         * this app does not support, such as {@code <sfz/>}; the reader drops the mark and imports
         * the rest of the note.
         */
        public static @Nullable DynamicType fromSymbol(String symbol) {
            for (var type : values()) {
                if (type.symbol.equals(symbol)) {
                    return type;
                }
            }

            return null;
        }

        /**
         * Returns the SMuFL glyph for rendering. Every type has one — see the class note above.
         */
        public SMuFLGlyph getGlyph() {
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
        setOwnerElement(parent);
        // The line pointer is not set here. StaffElement.addAttachment — which every
        // caller reaches immediately — routes through LineElement.addChild, and that
        // owns it.
    }

    @Override
    protected Attachment createCopy(StaffElement newOwner) {
        return new DynamicAttachment(newOwner, type);
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

    /** This dynamic's glyph box, the source of every dimension below. */
    private BBox glyphBBox() {
        return SMuFLMetadata.bboxSs(type.getGlyph());
    }

    /**
     * Returns the glyph's own ink width in staff-space units. This is the ink, not the advance
     * box a neighbouring hairpin pads away from — see {@link #getAdvanceWidthSs()}.
     */
    public double intrinsicWidthSs() {
        return glyphBBox().widthSs();
    }

    @Override
    public double intrinsicHeightSs() {
        return glyphBBox().heightSs();
    }

    /**
     * The glyph's advance width in staff-space units — the box the font declares for the
     * character, which for the italic dynamic glyphs is narrower than the ink the glyph actually
     * paints. This is the box a neighbouring hairpin pads away from; see
     * {@link #getLeftSideBearingSs()}.
     */
    public double getAdvanceWidthSs() {
        return SMuFLMetadata.advanceWidthSs(type.getGlyph());
    }

    /**
     * The signed distance from the glyph origin to the left edge of its ink, in staff spaces.
     * Negative for the dynamic glyphs whose swashes overhang to the left of their advance box
     * (Bravura's {@code dynamicForte} by {@code -0.564}), which is what makes the ink box and the
     * advance box differ. Callers holding an absolute ink left edge subtract this to recover the
     * glyph origin, and hence the advance box.
     */
    public double getLeftSideBearingSs() {
        return glyphBBox().leftSs();
    }

    /**
     * Returns the glyph's bottom edge relative to its text baseline, in staff-space units.
     * Positive, since a dynamic's descender drops below the baseline.
     * <p>
     * This says where the ink sits <em>around</em> the baseline, which {@link #intrinsicHeightSs()}
     * alone cannot: {@code p} has a descender and no ascender, {@code f} has both, and {@code mf} is
     * nearly all x-height, so a shared baseline is the only alignment that reads level across them.
     */
    public double getContentBottomSs() {
        return glyphBBox().bottomSs();
    }
}
