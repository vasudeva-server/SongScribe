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

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That copying an attachment carries the state {@link Attachment} itself holds — where the notator
 * put it, and how it sits against its note — and not only the value its own kind carries.
 *
 * <p>Copying is not a clipboard-only path. {@code StaffElement.copyStateFrom} rebuilds an
 * element's attachments by copying each one, and that is how undo and redo restore a note. So an
 * attachment kind that drops its offset here loses a hand-placed position the first time the
 * notator edits that note and presses Undo — with no warning, and with no way back, because the
 * snapshot the edit took had already lost it.
 *
 * <p>Every kind is asked, from {@link Attachment}'s permitted subclasses, so a sixth kind fails
 * {@link #testCasesCoverEveryAttachmentKind} rather than quietly going untested. The promise is
 * one {@code Attachment} makes for all of them — {@code copy} is final and applies this state
 * itself — so this pins that arrangement rather than five separate implementations.
 */
class AttachmentCopyTest extends UnitTest {

    private static final double USER_X_OFFSET_SS = 1.5;
    private static final double USER_Y_OFFSET_SS = -2.25;

    /**
     * One attachment kind and how to build one.
     *
     * @param kind    the concrete type, which pins the table to the sealed hierarchy
     * @param builder builds an attachment of that kind
     */
    private record AttachmentCase(
        Class<? extends Attachment> kind, Supplier<Attachment> builder
    ) {

        @Override
        public String toString() {
            return kind.getSimpleName();
        }
    }

    static Stream<AttachmentCase> attachmentCases() {
        return Stream.of(
            new AttachmentCase(
                AnnotationAttachment.class,
                () -> new AnnotationAttachment(new Annotation("dolce"))),
            new AttachmentCase(
                BeatChangeAttachment.class,
                () -> new BeatChangeAttachment(
                    new BeatChange(BeatUnit.CROTCHET_DOTTED, BeatUnit.CROTCHET))),
            new AttachmentCase(
                TempoChangeAttachment.class,
                () -> new TempoChangeAttachment(new Tempo())),
            new AttachmentCase(
                FermataAttachment.class, FermataAttachment::new),
            new AttachmentCase(
                DynamicAttachment.class,
                () -> new DynamicAttachment(DynamicAttachment.DynamicType.FORTE))
        );
    }

    /**
     * @param testCase the kind to build
     * @return an attachment of that kind, on its own note, with every piece of shared state set to
     *         something no constructor would leave behind
     */
    private static Attachment placedAttachment(AttachmentCase testCase) {
        var attachment = testCase.builder().get();
        lineWith(ElementType.CROTCHET).getElement(0).addAttachment(attachment);

        attachment.setUserXOffsetSs(USER_X_OFFSET_SS);
        attachment.setUserYOffsetSs(USER_Y_OFFSET_SS);

        return attachment;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attachmentCases")
    void testACopyKeepsTheOffsetsTheOriginalWasPlacedAt(AttachmentCase testCase) {
        var copy = placedAttachment(testCase).copy();

        assertThat(copy.getUserXOffsetSs()).isEqualTo(USER_X_OFFSET_SS);
        assertThat(copy.getUserYOffsetSs()).isEqualTo(USER_Y_OFFSET_SS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attachmentCases")
    void testACopyIsOfTheSameKind(AttachmentCase testCase) {
        var copy = placedAttachment(testCase).copy();

        assertThat(copy).isInstanceOf(testCase.kind());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attachmentCases")
    void testACopyIsOnNoElementUntilOneAddsIt(AttachmentCase testCase) {
        var copy = placedAttachment(testCase).copy();

        assertThat(copy.owner()).isNull();
    }

    @Test
    void testCasesCoverEveryAttachmentKind() {
        assertThat(attachmentCases().<Class<?>>map(AttachmentCase::kind))
            .containsExactlyInAnyOrderElementsOf(concreteAttachmentKinds());
    }

    /**
     * @return every concrete attachment type, walking through {@link MetronomeAttachment}, which is
     *         itself sealed and abstract rather than a kind an element can carry
     */
    private static java.util.List<Class<?>> concreteAttachmentKinds() {
        return Stream.of(Attachment.class.getPermittedSubclasses())
            .flatMap(kind -> kind.isSealed()
                ? Stream.of(kind.getPermittedSubclasses())
                : Stream.of(kind))
            .toList();
    }
}
