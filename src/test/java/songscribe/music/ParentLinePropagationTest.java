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

package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.Articulation;

/**
 * Verifies that {@code parentLine} is propagated correctly on attachments and
 * articulations in all three structurally relevant orderings:
 * <ol>
 *   <li>attachment already on element when element is added to a line</li>
 *   <li>attachment added to element that is already in a line</li>
 *   <li>attachment moved from one element to another in a different line</li>
 * </ol>
 */
class ParentLinePropagationTest extends UnitTest {

    private Composition composition;
    private Line line;

    @BeforeEach
    void setUp() {
        composition = new Composition();
        line = composition.getLine(0);
    }

    private void addToLine(Line targetLine, StaffElement element) {
        composition.withoutMutationTracking(() -> targetLine.addElement(element));
    }

    // -----------------------------------------------------------------------
    // Attachment already on element when addElement is called
    // -----------------------------------------------------------------------

    @Nested
    class WhenAttachmentPreexistsOnElement {

        @Test
        void testArticulationParentLineSetByAddElement() {
            var note = ElementType.QUAVER.newInstance();
            var articulation = new Articulation(note, ArticulationType.STACCATO);
            note.addArticulation(articulation);

            assertThat(articulation.getParentLine()).isNull();

            addToLine(line, note);

            assertThat(articulation.getParentLine()).isSameAs(line);
        }

        @Test
        void testAttachmentParentLineSetByAddElement() {
            var note = ElementType.QUAVER.newInstance();
            var attachment = new AnnotationAttachment("test");
            note.addAttachment(attachment);

            assertThat(attachment.getParentLine()).isNull();

            addToLine(line, note);

            assertThat(attachment.getParentLine()).isSameAs(line);
        }
    }

    // -----------------------------------------------------------------------
    // Attachment added to element that is already in a line
    // -----------------------------------------------------------------------

    @Nested
    class WhenElementAlreadyInLine {

        @Test
        void testAttachmentInheritsParentLineFromElement() {
            var note = ElementType.QUAVER.newInstance();
            addToLine(line, note);

            var attachment = new AnnotationAttachment("test");
            note.addAttachment(attachment);

            assertThat(attachment.getParentLine()).isSameAs(line);
        }
    }

    // -----------------------------------------------------------------------
    // Attachment moved from one element to another in a different line
    // -----------------------------------------------------------------------

    @Nested
    class WhenMovedBetweenElements {

        @Test
        void testParentLineFollowsAttachmentToNewElement() {
            var line2 = new Line();
            line2.setComposition(composition);

            var note1 = ElementType.QUAVER.newInstance();
            var note2 = ElementType.QUAVER.newInstance();
            var attachment = new AnnotationAttachment("test");

            addToLine(line, note1);
            addToLine(line2, note2);

            note1.addAttachment(attachment);
            assertThat(attachment.getParentLine()).isSameAs(line);

            note1.removeAttachment(attachment);
            note2.addAttachment(attachment);
            assertThat(attachment.getParentLine()).isSameAs(line2);
        }
    }
}
