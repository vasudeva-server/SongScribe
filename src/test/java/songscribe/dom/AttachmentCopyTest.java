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

import java.util.function.Function;
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
    private static final double MARGIN_TOP_SS = 0.25;
    private static final double MARGIN_RIGHT_SS = 0.5;
    private static final double MARGIN_BOTTOM_SS = 0.75;
    private static final double MARGIN_LEFT_SS = 1.25;

    /** An alignment no attachment kind's constructor establishes, so a copy must carry it. */
    private static final Attachment.Alignment ALIGNMENT = Attachment.Alignment.RIGHT;

    /**
     * One attachment kind and how to build one.
     *
     * @param kind    the concrete type, which pins the table to the sealed hierarchy
     * @param builder builds an attachment of that kind owned by the given element
     */
    private record AttachmentCase(
        Class<? extends Attachment> kind, Function<StaffElement, Attachment> builder
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
                owner -> new AnnotationAttachment(owner, new Annotation("dolce"))),
            new AttachmentCase(
                BeatChangeAttachment.class,
                owner -> new BeatChangeAttachment(
                    owner, new BeatChange(Duration.CROTCHET_DOTTED, Duration.CROTCHET))),
            new AttachmentCase(
                TempoChangeAttachment.class,
                owner -> new TempoChangeAttachment(owner, new Tempo())),
            new AttachmentCase(
                FermataAttachment.class, FermataAttachment::new),
            new AttachmentCase(
                DynamicAttachment.class,
                owner -> new DynamicAttachment(owner, DynamicAttachment.DynamicType.FORTE))
        );
    }

    /**
     * @param testCase the kind to build
     * @return an attachment of that kind, on its own note, with every piece of shared state set to
     *         something no constructor would leave behind
     */
    private static Attachment placedAttachment(AttachmentCase testCase) {
        var owner = lineWith(ElementType.CROTCHET).getElement(0);
        var attachment = testCase.builder().apply(owner);

        attachment.setAlignment(ALIGNMENT);
        attachment.setUserXOffsetSs(USER_X_OFFSET_SS);
        attachment.setUserYOffsetSs(USER_Y_OFFSET_SS);
        attachment.setMarginSs(MARGIN_TOP_SS, MARGIN_RIGHT_SS, MARGIN_BOTTOM_SS, MARGIN_LEFT_SS);

        return attachment;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attachmentCases")
    void testACopyKeepsTheOffsetsAndMarginsTheOriginalWasPlacedAt(AttachmentCase testCase) {
        var newOwner = lineWith(ElementType.CROTCHET).getElement(0);

        var copy = placedAttachment(testCase).copy(newOwner);

        assertThat(copy.getUserXOffsetSs()).isEqualTo(USER_X_OFFSET_SS);
        assertThat(copy.getUserYOffsetSs()).isEqualTo(USER_Y_OFFSET_SS);
        assertThat(copy.getMarginTopSs()).isEqualTo(MARGIN_TOP_SS);
        assertThat(copy.getMarginRightSs()).isEqualTo(MARGIN_RIGHT_SS);
        assertThat(copy.getMarginBottomSs()).isEqualTo(MARGIN_BOTTOM_SS);
        assertThat(copy.getMarginLeftSs()).isEqualTo(MARGIN_LEFT_SS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attachmentCases")
    void testACopyKeepsTheAlignmentAndIsOfTheSameKind(AttachmentCase testCase) {
        var newOwner = lineWith(ElementType.CROTCHET).getElement(0);

        var copy = placedAttachment(testCase).copy(newOwner);

        assertThat(copy).isInstanceOf(testCase.kind());
        assertThat(copy.getAlignment()).isEqualTo(ALIGNMENT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attachmentCases")
    void testACopyIsOwnedByTheElementItWasCopiedFor(AttachmentCase testCase) {
        var newOwner = lineWith(ElementType.CROTCHET).getElement(0);

        var copy = placedAttachment(testCase).copy(newOwner);

        assertThat(copy.getOwnerElement()).isSameAs(newOwner);
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
