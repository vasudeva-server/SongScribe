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
package songscribe.dom;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That an element's copy owns its children rather than sharing the original's.
 *
 * <p>An element carries attachments and a note carries articulations, and both are mutable
 * objects the notator moves by hand. Four routes build an element from another one:
 * {@link StaffElement#clone()}, {@link StaffElement#copyStateFrom} — which undo replay uses to
 * restore state onto the live instance — {@link StaffElement#convertedFrom} and
 * {@link NoteElement#copyArticulationsFrom}. Each of them copies every child rather than adding
 * the source's own.
 *
 * <p>Sharing instead would pass every test that asks a child what it carries, because a shared
 * child carries exactly what it was asked to. It shows up only later, when something moves one:
 * an undo would drag the restored element's attachment away from where the notator put it, on an
 * element the edit was never supposed to reach. So the promise is not what the copy holds but
 * whose it is, and moving a child of the copy is what asks that question.
 *
 * <p>{@link AttachmentCopyTest} pins the other half — that an attachment's own {@code copy}
 * carries the state {@code Attachment} holds. Together they say a copied attachment arrives
 * intact and arrives separate.
 */
class ElementCopyIsDeepTest extends UnitTest {

    private static final double PLACED_OFFSET_SS = 1.5;
    private static final double MOVED_OFFSET_SS = -3.25;

    /**
     * One copy route and one kind of child to follow through it.
     *
     * @param description what the route is, which doubles as the case name
     * @param copy builds a copy of the note the route's way
     * @param children the children of an element to compare across the copy
     */
    private record CopyCase(
        String description,
        Function<NoteElement, StaffElement> copy,
        Function<StaffElement, List<? extends LineElement>> children
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    private static final Function<StaffElement, List<? extends LineElement>> ATTACHMENTS =
        StaffElement::getAttachments;

    private static final Function<StaffElement, List<? extends LineElement>> ARTICULATIONS =
        element -> ((NoteElement) element).getArticulations();

    static Stream<CopyCase> copyCases() {
        return Stream.of(
            new CopyCase("clone, attachment", NoteElement::clone, ATTACHMENTS),
            new CopyCase("clone, articulation", NoteElement::clone, ARTICULATIONS),
            new CopyCase("copyStateFrom, attachment", ElementCopyIsDeepTest::restored, ATTACHMENTS),
            new CopyCase(
                "copyStateFrom, articulation", ElementCopyIsDeepTest::restored, ARTICULATIONS),
            new CopyCase("convertedFrom, attachment", ElementCopyIsDeepTest::converted, ATTACHMENTS),
            new CopyCase(
                "convertedFrom, articulation", ElementCopyIsDeepTest::converted, ARTICULATIONS),
            new CopyCase(
                "copyArticulationsFrom", ElementCopyIsDeepTest::articulationsOnly, ARTICULATIONS)
        );
    }

    /**
     * @param source the note to restore from
     * @return a bare note of the same type, filled in the way undo replay fills a live element
     */
    private static NoteElement restored(NoteElement source) {
        var target = new NoteElement(source.getType());
        target.copyStateFrom(source);

        return target;
    }

    /**
     * @param source the note being converted
     * @return the same note as a quaver, the way an element-type change builds one
     */
    private static StaffElement converted(NoteElement source) {
        return StaffElement.convertedFrom(ElementType.QUAVER, source);
    }

    /**
     * @param source the note whose articulations to take
     * @return a bare note of the same type carrying only the source's articulations
     */
    private static NoteElement articulationsOnly(NoteElement source) {
        var target = new NoteElement(source.getType());
        target.copyArticulationsFrom(source);

        return target;
    }

    /**
     * @return a note in a line, carrying one attachment and one articulation, each moved off the
     *         position it would have been given by default
     */
    private static NoteElement placedNote() {
        var note = (NoteElement) lineWith(ElementType.CROTCHET).getElement(0);

        var attachment = new AnnotationAttachment(new Annotation("dolce"));
        note.addAttachment(attachment);
        attachment.setUserXOffsetSs(PLACED_OFFSET_SS);

        var articulation = new Articulation(ArticulationType.STACCATO);
        note.addArticulation(articulation);
        articulation.setUserXOffsetSs(PLACED_OFFSET_SS);

        return note;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("copyCases")
    void testMovingAChildOfTheCopyLeavesTheOriginalsChildWhereItWas(CopyCase testCase) {
        var source = placedNote();
        var sourceChild = testCase.children().apply(source).getFirst();
        var copiedChild = testCase.children().apply(testCase.copy().apply(source)).getFirst();

        copiedChild.setUserXOffsetSs(MOVED_OFFSET_SS);

        assertThat(copiedChild).isNotSameAs(sourceChild);
        assertThat(sourceChild.getUserXOffsetSs()).isEqualTo(PLACED_OFFSET_SS);
    }
}
