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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;

class TempoChangeAttachmentTest extends UnitTest {

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ComputeContentMetrics {

        // -------------------------------------------------------------------------
        // Row 30 — showTempo=true → glyph+text regions, both widths > 0
        // -------------------------------------------------------------------------

        @Test
        void testShowTempoTrueProducesNonZeroGlyphAndTextWidths() {
            var tempo = new Tempo(120, Duration.CROTCHET, "Allegro", true);
            var attachment = new TempoChangeAttachment(tempo);
            var font = DocumentFonts.defaultFonts().getAttributionFont();

            var metrics = attachment.computeContentMetrics(font);

            // Two regions: glyph region and text region
            assertThat(metrics.regions()).hasSize(2);

            var glyphRegion = metrics.regions().get(0);
            var textRegion = metrics.regions().get(1);

            assertThat(glyphRegion.widthSs()).isGreaterThan(0.0);
            assertThat(textRegion.widthSs()).isGreaterThan(0.0);

            // Total width must equal glyph width + text width
            assertThat(metrics.widthSs())
                .isEqualTo(glyphRegion.widthSs() + textRegion.widthSs());
        }

        // -------------------------------------------------------------------------
        // Row 31 — showTempo=false + non-empty description → text-only, glyph width 0
        // -------------------------------------------------------------------------

        @Test
        void testShowTempoFalseWithDescriptionProducesTextOnlyRegion() {
            var tempo = new Tempo(120, Duration.CROTCHET, "Andante", false);
            var attachment = new TempoChangeAttachment(tempo);
            var font = DocumentFonts.defaultFonts().getAttributionFont();

            var metrics = attachment.computeContentMetrics(font);

            // Only one region: text (no glyph region)
            assertThat(metrics.regions()).hasSize(1);

            var textRegion = metrics.regions().getFirst();

            assertThat(textRegion.widthSs()).isGreaterThan(0.0);

            // Total width equals the text region width (glyph contributes 0)
            assertThat(metrics.widthSs()).isEqualTo(textRegion.widthSs());
        }

        // -------------------------------------------------------------------------
        // Row 32 — showTempo=false + empty description → zero width, no regions
        // -------------------------------------------------------------------------

        @Test
        void testShowTempoFalseWithEmptyDescriptionProducesZeroWidthAndNoRegions() {
            var tempo = new Tempo(120, Duration.CROTCHET, "", false);
            var attachment = new TempoChangeAttachment(tempo);
            var font = DocumentFonts.defaultFonts().getAttributionFont();

            var metrics = attachment.computeContentMetrics(font);

            assertThat(metrics.regions()).isEmpty();
            assertThat(metrics.widthSs()).isEqualTo(0.0);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Copy {

        @Test
        void testCopyReturnsDistinctInstanceWithNewOwnerAndPreservesTempo() {
            var tempo = new Tempo(120, Duration.CROTCHET, "Allegro", true);
            var originalOwner = crotchet();
            var newOwner = quaver();
            var original = new TempoChangeAttachment(originalOwner, tempo);

            var copy = original.copy(newOwner);

            assertThat(copy).isNotSameAs(original);
            assertThat(copy).isExactlyInstanceOf(TempoChangeAttachment.class);
            assertThat(copy.getOwnerElement()).isSameAs(newOwner);
            // The tempo must be deep-copied, not shared, so copy() means one thing across
            // the Attachment hierarchy regardless of whether a mutator exists today.
            var copiedTempo = ((TempoChangeAttachment) copy).getTempo();
            assertThat(copiedTempo).isNotSameAs(tempo);
            assertThat(copiedTempo.getVisibleTempo()).isEqualTo(tempo.getVisibleTempo());
            assertThat(copiedTempo.getTempoType()).isEqualTo(tempo.getTempoType());
            assertThat(copiedTempo.getTempoDescription()).isEqualTo(tempo.getTempoDescription());
            assertThat(copiedTempo.shouldShowTempo()).isEqualTo(tempo.shouldShowTempo());
        }
    }
}
