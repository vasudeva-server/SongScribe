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
package songscribe.ui.dialog;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Unit tests for {@link AttachmentEditor#edit}: pins the attachment-kind → dialog mapping and
 * the two "nothing to edit" outcomes — a fermata/dynamic with no dialog at all, and a
 * dialog-backed attachment with nothing to bind the dialog to.
 *
 * <p>Binding is now observed as the dialog being shown at all, rather than as a call that hands it
 * an element afterwards: the dialog seam moved binding into the constructor, so a dialog that was
 * built was built bound. What the binding then reports is asserted in the back ends' own tests.
 */
class AttachmentEditorTest extends MainFrameMockTest {

    // ── Dialog-backed kinds — edit() constructs the matching dialog, bound, and shows it ──

    @Test
    void testAnnotationAttachmentOpensAnnotationDialogBoundToOwner() {
        var element = crotchet();
        var line = detachedLine();
        line.addElement(element);
        var attachment = new AnnotationAttachment(element, new Annotation("Fine"));

        try (var construction = mockConstruction(AnnotationDialog.class)) {
            var result = AttachmentEditor.edit(mainFrame(), attachment, line);

            assertThat(construction.constructed())
                .as("exactly one AnnotationDialog was constructed")
                .hasSize(1);
            verify(construction.constructed().getFirst()).setVisible(true);
            assertThat(result).as("edit() returns true when a dialog is opened").isTrue();
        }
    }

    @Test
    void testBeatChangeAttachmentOpensBeatChangeDialogBoundToOwner() {
        var element = crotchet();
        var line = detachedLine();
        line.addElement(element);
        var attachment = new BeatChangeAttachment(element, new BeatChange(Duration.CROTCHET, Duration.MINIM));

        try (var construction = mockConstruction(BeatChangeDialog.class)) {
            var result = AttachmentEditor.edit(mainFrame(), attachment, line);

            assertThat(construction.constructed())
                .as("exactly one BeatChangeDialog was constructed")
                .hasSize(1);
            verify(construction.constructed().getFirst()).setVisible(true);
            assertThat(result).as("edit() returns true when a dialog is opened").isTrue();
        }
    }

    @Test
    void testTempoChangeAttachmentOpensTempoChangeDialogBoundToOwner() {
        var element = crotchet();
        var line = detachedLine();
        line.addElement(element);
        var attachment = new TempoChangeAttachment(element, new Tempo());

        try (var construction = mockConstruction(TempoChangeDialog.class)) {
            var result = AttachmentEditor.edit(mainFrame(), attachment, line);

            assertThat(construction.constructed())
                .as("exactly one TempoChangeDialog was constructed")
                .hasSize(1);
            verify(construction.constructed().getFirst()).setVisible(true);
            assertThat(result).as("edit() returns true when a dialog is opened").isTrue();
        }
    }

    // ── Kinds with no dialog — a fermata/dynamic double-click stays a silent no-op ──

    @Test
    void testAFermataHasNoDialogSoNothingOpens() {
        var element = crotchet();
        var line = detachedLine();
        line.addElement(element);
        var attachment = new FermataAttachment(element);

        try (var annotationConstruction = mockConstruction(AnnotationDialog.class);
             var beatChangeConstruction = mockConstruction(BeatChangeDialog.class);
             var tempoChangeConstruction = mockConstruction(TempoChangeDialog.class)) {
            var result = AttachmentEditor.edit(mainFrame(), attachment, line);

            assertThat(result).as("a fermata has no editable properties").isFalse();
            assertThat(annotationConstruction.constructed()).as("no AnnotationDialog opened").isEmpty();
            assertThat(beatChangeConstruction.constructed()).as("no BeatChangeDialog opened").isEmpty();
            assertThat(tempoChangeConstruction.constructed()).as("no TempoChangeDialog opened").isEmpty();
        }
    }

    @Test
    void testADynamicHasNoDialogSoNothingOpens() {
        var element = crotchet();
        var line = detachedLine();
        line.addElement(element);
        var attachment = new DynamicAttachment(element, DynamicAttachment.DynamicType.FORTE);

        try (var annotationConstruction = mockConstruction(AnnotationDialog.class);
             var beatChangeConstruction = mockConstruction(BeatChangeDialog.class);
             var tempoChangeConstruction = mockConstruction(TempoChangeDialog.class)) {
            var result = AttachmentEditor.edit(mainFrame(), attachment, line);

            assertThat(result).as("a dynamic has no editable properties").isFalse();
            assertThat(annotationConstruction.constructed()).as("no AnnotationDialog opened").isEmpty();
            assertThat(beatChangeConstruction.constructed()).as("no BeatChangeDialog opened").isEmpty();
            assertThat(tempoChangeConstruction.constructed()).as("no TempoChangeDialog opened").isEmpty();
        }
    }

    // ── Nothing to bind to — a dialog-backed attachment with no usable owner element ──

    @Test
    void testNullOwnerOpensNoDialogAvoidingNoElementSelectedException() {
        var line = detachedLine();
        var attachment = new TempoChangeAttachment(null, new Tempo());

        try (var construction = mockConstruction(TempoChangeDialog.class)) {
            var result = AttachmentEditor.edit(mainFrame(), attachment, line);

            assertThat(result).as("no owner element to bind the dialog to").isFalse();
            assertThat(construction.constructed()).as("no TempoChangeDialog opened").isEmpty();
        }
    }

    @Test
    void testOwnerNotInLineOpensNoDialogAvoidingNegativeIndexModifyElement() {
        var element = crotchet();
        // element is owned by the attachment but never added to the line passed to edit(), so
        // line.getElementIndex(element) answers −1.
        var line = detachedLine();
        var attachment = new TempoChangeAttachment(element, new Tempo());

        try (var construction = mockConstruction(TempoChangeDialog.class)) {
            var result = AttachmentEditor.edit(mainFrame(), attachment, line);

            assertThat(result).as("owner element is not in the passed line").isFalse();
            assertThat(construction.constructed()).as("no TempoChangeDialog opened").isEmpty();
        }
    }
}
