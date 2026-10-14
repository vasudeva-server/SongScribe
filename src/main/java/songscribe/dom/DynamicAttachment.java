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
 * A dynamic marking attached to a note.
 */
public final class DynamicAttachment extends Attachment implements IntrinsicHeight {

    /**
     * Limited to types with a glyph: a dynamic with none would be invisible yet still reserve
     * its footprint, pushing everything above it outward.
     */
    public enum DynamicType {
        PIANISSIMO("pp", SMuFLGlyph.DYNAMIC_PP, 0.25),
        PIANO("p", SMuFLGlyph.DYNAMIC_PIANO, 0.40),
        MEZZO_PIANO("mp", SMuFLGlyph.DYNAMIC_MP, 0.53),
        MEZZO_FORTE("mf", SMuFLGlyph.DYNAMIC_MF, 0.63),
        FORTE("f", SMuFLGlyph.DYNAMIC_FORTE, 0.78),
        FORTISSIMO("ff", SMuFLGlyph.DYNAMIC_FF, 1.00);

        private final String symbol;
        private final SMuFLGlyph glyph;
        private final double velocityFraction;

        DynamicType(String symbol, SMuFLGlyph glyph, double velocityFraction) {
            this.symbol = symbol;
            this.glyph = glyph;
            this.velocityFraction = velocityFraction;
        }

        public String getSymbol() {
            return symbol;
        }

        /**
         * A {@code null} answer is the normal result for a dynamic this app does not support,
         * such as {@code <sfz/>}; the reader drops the mark and imports the rest of the note.
         */
        public static @Nullable DynamicType fromSymbol(String symbol) {
            for (var type : values()) {
                if (type.symbol.equals(symbol)) {
                    return type;
                }
            }

            return null;
        }

        public SMuFLGlyph getGlyph() {
            return glyph;
        }

        public double getVelocityFraction() {
            return velocityFraction;
        }
    }

    private final DynamicType type;

    public DynamicAttachment(DynamicType type) {
        this.type = type;
    }

    @Override
    protected Attachment createCopy() {
        return new DynamicAttachment(type);
    }

    public DynamicType getType() {
        return type;
    }

    public String getSymbol() {
        return type.getSymbol();
    }

    private BBox glyphBBox() {
        return SMuFLMetadata.bboxSs(type.getGlyph());
    }

    /**
     * The ink, not the advance box — see {@link #getAdvanceWidthSs()}.
     */
    public double intrinsicWidthSs() {
        return glyphBBox().widthSs();
    }

    @Override
    public double intrinsicHeightSs() {
        return glyphBBox().heightSs();
    }

    /**
     * The box the font declares, which a neighbouring hairpin pads away from; for the italic
     * dynamic glyphs it is narrower than the ink.
     */
    public double getAdvanceWidthSs() {
        return SMuFLMetadata.advanceWidthSs(type.getGlyph());
    }

    public double getLeftSideBearingSs() {
        return glyphBBox().leftSs();
    }

    /**
     * Positive, since a dynamic's descender drops below the baseline. Aligning on a shared
     * baseline is the only alignment that reads level across {@code p}, {@code f} and {@code mf}.
     */
    public double getContentBottomSs() {
        return glyphBBox().bottomSs();
    }
}
