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
package songscribe.ui.dialog.backend;

import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Exercises the contract of {@link AnnotationBackEnd}, the domain half of the annotation dialog.
 *
 * <p><b>Existing change</b> — the two states of the element: carrying an annotation, and carrying
 * none.
 *
 * <p><b>Apply</b> — one property covering both states: afterwards the element carries exactly the
 * applied annotation. The add and change paths differ only in the identity promise — an existing
 * attachment is reused rather than replaced — which has its own test.
 *
 * <p><b>Validate</b> — the contract says nothing the dialog can assemble is refusable, so
 * {@code validate} is left at its interface default.
 *
 * <p><b>No blank-text case</b>, in either direction. Removal has its own method, and
 * {@link Annotation} has no blank state to be in, so there is nothing here to reject and no
 * emptiness to read as a deletion request. The invariant is asserted where it lives, in
 * {@code AnnotationTest}.
 *
 * <p><b>Remove</b> — the element carries no annotation afterwards, whether or not it carried one
 * before.
 *
 * <p><b>Not tested here:</b> {@code DialogBackEnd.apply}'s promise that a commit is <em>one</em>
 * undoable step, and the removal label an empty-text apply records — see
 * {@link BeatChangeBackEndTest} for why, and Phase 7 of {@code plans/ui-dialog-seam.md} for where
 * they land. These cases were carried across the seam from {@code AnnotationDialogTest}, which
 * asserted them against the dialog before the split.
 */
class AnnotationBackEndTest extends UnitTest {

    private static final Annotation APPLIED = new Annotation("fine");

    private StaffElement element;
    private AnnotationBackEnd backEnd;

    @BeforeEach
    void setUp() {
        var line = detachedLine();
        element = crotchet();
        line.addElement(element);
        backEnd = new AnnotationBackEnd(new AttachmentTarget(line, element));
    }

    private record ExistingChangeCase(String description, @Nullable Annotation existing) {}

    static Stream<ExistingChangeCase> existingChangeCases() {
        return Stream.of(
            new ExistingChangeCase("element carrying an annotation reports it", new Annotation("old")),
            new ExistingChangeCase("element carrying none reports null", null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testExistingChangeReportsTheAnnotationTheElementCarries(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        assertThat(backEnd.existingChange())
            .as("existingChange answers the annotation on the element, or null for none")
            .isSameAs(testCase.existing());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testApplyLeavesTheElementCarryingExactlyTheAppliedAnnotation(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        backEnd.apply(APPLIED);

        assertThat(backEnd.existingChange())
            .as("the element carries the applied annotation, whether one was there before or not")
            .isSameAs(APPLIED);
    }

    @Test
    void testApplyReusesTheExistingAttachmentRatherThanReplacingIt() {
        var original = new AnnotationAttachment(element, new Annotation("old"));
        element.addAttachment(original);

        backEnd.apply(APPLIED);

        assertThat(element.findAttachment(AnnotationAttachment.class))
            .as("the attachment is updated in place, so anything holding it stays valid")
            .isSameAs(original);
    }

    @Test
    void testValidateAcceptsAnAnnotationTheDialogCanBuild() {
        assertThat(backEnd.validate(APPLIED).isValid())
            .as("no annotation assembled from the dialog's controls is refusable")
            .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testRemoveLeavesTheElementCarryingNoAnnotation(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        backEnd.remove();

        assertThat(backEnd.existingChange())
            .as("the element carries no annotation, whether one was there before or not")
            .isNull();
    }

    private void givenExistingChange(@Nullable Annotation existing) {
        if (existing != null) {
            element.addAttachment(new AnnotationAttachment(element, existing));
        }
    }
}
