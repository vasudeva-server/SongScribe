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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.font.DocumentFonts;
import songscribe.music.ElementType;
import songscribe.music.Song;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AnnotationAttachmentTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ComputeContentHeightSs {

        @Test
        void testUsesProvidedFont() {
            var song = new Song();
            var line = song.getLine(0);
            var note = ElementType.QUAVER.newInstance();
            var attachment = new AnnotationAttachment("test");
            note.addAttachment(attachment);
            song.withoutMutationTracking(() -> line.addElement(note));

            var font = DocumentFonts.defaultsFromPrefs().getAnnotationFont();
            var expected = ScaleContext.getInstance().textHeightSs(font);

            assertThat(attachment.computeContentHeightSs(font)).isCloseTo(expected, within(EPSILON));
        }

    }

}
