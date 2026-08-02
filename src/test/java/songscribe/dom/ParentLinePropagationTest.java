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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

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

    private Song song;
    private Line line;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);
    }

    private void addToLine(Line targetLine, StaffElement element) {
        song.withoutMutationTracking(() -> targetLine.addElement(element));
    }

    // -----------------------------------------------------------------------
    // Attachment already on element when addElement is called
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
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

    @SuppressWarnings("PackageVisibleInnerClass")
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

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenMovedBetweenElements {

        @Test
        void testParentLineFollowsAttachmentToNewElement() {
            var line2 = new Line(song);

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

    // -----------------------------------------------------------------------
    // Removals detach: parentLine == null means "in no line"
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenRemovedFromLine {

        @Test
        void testRemoveElementDetaches() {
            var note = ElementType.QUAVER.newInstance();
            addToLine(line, note);

            song.withoutMutationTracking(() -> line.removeElement(line.getElementIndex(note)));

            assertThat(note.getParentLine()).isNull();
        }

        @Test
        void testRemoveRangeDetachesEveryElementInTheRange() {
            var first = ElementType.QUAVER.newInstance();
            var middle = ElementType.QUAVER.newInstance();
            var last = ElementType.QUAVER.newInstance();
            var survivor = ElementType.QUAVER.newInstance();
            addToLine(line, first);
            addToLine(line, middle);
            addToLine(line, last);
            addToLine(line, survivor);

            song.withoutMutationTracking(() -> line.removeRange(0, 2));

            assertThat(first.getParentLine()).isNull();
            assertThat(middle.getParentLine()).isNull();
            assertThat(last.getParentLine()).isNull();
            assertThat(survivor.getParentLine()).isSameAs(line);
        }

        @Test
        void testSetElementDetachesTheReplacedElementAndAttachesTheReplacement() {
            var replaced = ElementType.QUAVER.newInstance();
            addToLine(line, replaced);
            var replacement = ElementType.CROTCHET.newInstance();

            song.withoutMutationTracking(() -> line.setElement(0, replacement));

            assertThat(replaced.getParentLine()).isNull();
            assertThat(replacement.getParentLine()).isSameAs(line);
        }

        @Test
        void testSetElementReplacingAnElementWithItselfLeavesItAttached() {
            // Detach-first ordering: attach-then-detach would clear the pointer of an
            // element that is still in the line.
            var note = ElementType.QUAVER.newInstance();
            addToLine(line, note);

            song.withoutMutationTracking(() -> line.setElement(0, note));

            assertThat(note.getParentLine()).isSameAs(line);
        }

        @Test
        void testAttachingToAnotherLineBeforeRemovalWins() {
            // The `!= this` guard in detach: a re-parent that attached to line2 first must
            // survive the removal from the original line.
            var line2 = new Line(song);
            var note = ElementType.QUAVER.newInstance();
            addToLine(line, note);

            addToLine(line2, note);
            song.withoutMutationTracking(() -> line.removeElement(line.getElementIndex(note)));

            assertThat(note.getParentLine()).isSameAs(line2);
        }

        @Test
        void testArticulationsAndAttachmentsFollowTheElementInBothDirections() {
            var note = ElementType.QUAVER.newInstance();
            var articulation = new Articulation(note, ArticulationType.STACCATO);
            var attachment = new AnnotationAttachment("test");
            note.addArticulation(articulation);
            note.addAttachment(attachment);

            addToLine(line, note);

            assertThat(articulation.getParentLine()).isSameAs(line);
            assertThat(attachment.getParentLine()).isSameAs(line);

            song.withoutMutationTracking(() -> line.removeElement(line.getElementIndex(note)));

            assertThat(articulation.getParentLine()).isNull();
            assertThat(attachment.getParentLine()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // Copies are born detached; Line.attach is the only writer
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenCopied {

        @Test
        void testCloneIsDetachedIncludingItsChildren() {
            var note = ElementType.QUAVER.newInstance();
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
            note.addAttachment(new AnnotationAttachment("test"));
            addToLine(line, note);

            var clone = note.clone();

            assertThat(clone.getParentLine()).isNull();
            assertThat(clone.getChildren())
                .as("a clone's children must be as detached as the clone itself")
                .isNotEmpty()
                .allSatisfy(child -> assertThat(child.getParentLine()).isNull());
        }

        @Test
        void testCopyStateFromLeavesTheTargetsOwnParentLineIntact() {
            // Undo replay restores a live element from a detached snapshot; the snapshot's
            // null pointer must not travel with the state.
            var live = ElementType.QUAVER.newInstance();
            live.addArticulation(new Articulation(live, ArticulationType.STACCATO));
            addToLine(line, live);

            var snapshot = live.clone();
            live.copyStateFrom(snapshot);

            assertThat(live.getParentLine()).isSameAs(line);
            assertThat(live.getChildren())
                .isNotEmpty()
                .allSatisfy(child -> assertThat(child.getParentLine()).isSameAs(line));
        }
    }
}
