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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.music.ArticulationType;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.DynamicAttachment.DynamicType;

/**
 * Tests that decorations on an existing element (fermata, trill, articulations, dynamic
 * attachments) are carried over to the replacement element when
 * {@link PreviewElementManager#handleClick} triggers a modify-existing operation.
 */
class PreviewElementManagerAttachmentTest extends PreviewElementManagerTestBase {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void replaceAt(int index, StaffElement preview) {
        editModeManagerMock.when(EditModeManager::getPreviewElement).thenReturn(preview);
        PreviewElementManager.setXPosSsMatchesElement(true);
        PreviewElementManager.setCurrentXIndex(index);
        PreviewElementManager.handleClick(lc);
    }

    // -----------------------------------------------------------------------
    // modifyExistingElement — decoration preservation
    // -----------------------------------------------------------------------

    @SuppressWarnings({ "PackageVisibleInnerClass", "DataFlowIssue" })
    @Nested
    class ModifyExistingElement {

        @Test
        void testDynamicAttachmentPreserved() {
            var note = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                note.addAttachment(new DynamicAttachment(note, DynamicType.FORTE));
                line.addElement(note);
            });

            replaceAt(0, ElementType.CROTCHET.newInstance());

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).as("dynamic attachment preserved").isNotNull();
            //noinspection ConstantValue -- need for NullAway
            assertThat(dynamic == null ? null : dynamic.getType())
                .as("dynamic type preserved").isEqualTo(DynamicType.FORTE);
        }

        @Test
        void testDynamicAttachmentPreservedAcrossDurationChange() {
            var note = ElementType.CROTCHET.newInstance();
            song.withoutMutationTracking(() -> {
                note.addAttachment(new DynamicAttachment(note, DynamicType.PIANO));
                line.addElement(note);
            });

            replaceAt(0, ElementType.QUAVER.newInstance());

            var dynamic = line.getElement(0).findAttachment(DynamicAttachment.class);
            assertThat(dynamic).as("dynamic attachment survives duration change").isNotNull();
            //noinspection ConstantValue -- need for NullAway
            assertThat(dynamic == null ? null : dynamic.getType())
                .as("dynamic type preserved").isEqualTo(DynamicType.PIANO);
        }

        @Test
        void testFermataPreserved() {
            song.withoutMutationTracking(() -> {
                var note = ElementType.CROTCHET.newInstance();
                note.setFermata(true);
                line.addElement(note);
            });

            replaceAt(0, ElementType.QUAVER.newInstance());

            assertThat(line.getElement(0).isFermata()).as("fermata preserved").isTrue();
        }

        @Test
        void testTrillPreserved() {
            song.withoutMutationTracking(() -> {
                var note = ElementType.CROTCHET.newInstance();
                note.setTrill(true);
                line.addElement(note);
            });

            replaceAt(0, ElementType.CROTCHET.newInstance());

            assertThat(line.getElement(0).isTrill()).as("trill preserved").isTrue();
        }

        @Test
        void testArticulationPreserved() {
            song.withoutMutationTracking(() -> {
                var note = ElementType.CROTCHET.newInstance();
                note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
                line.addElement(note);
            });

            replaceAt(0, ElementType.CROTCHET.newInstance());

            var articulations = line.getElement(0).getArticulations();
            assertThat(articulations).as("articulation preserved").hasSize(1);
            assertThat(articulations.getFirst().getType())
                .as("articulation type preserved").isEqualTo(ArticulationType.STACCATO);
        }

        @Test
        void testNoteWithNoDecorationsRemainsClean() {
            song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));

            replaceAt(0, ElementType.QUAVER.newInstance());

            var element = line.getElement(0);
            assertThat(element.findAttachment(DynamicAttachment.class))
                .as("no spurious dynamic attachment").isNull();
            assertThat(element.isFermata()).as("no spurious fermata").isFalse();
            assertThat(element.isTrill()).as("no spurious trill").isFalse();
            assertThat(element.getArticulations()).as("no spurious articulations").isEmpty();
        }
    }
}
