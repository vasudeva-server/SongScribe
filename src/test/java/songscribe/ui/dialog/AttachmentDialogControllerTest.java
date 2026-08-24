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

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.TempoMarking;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That every attachment controller answers {@link DialogController#dataWasModified} by <em>value</em>.
 *
 * <p>"Every" is kept true by {@link #testCasesCoverEveryAttachmentController}, which pins the case
 * table below to {@link AttachmentDialogController}'s permitted subclasses. A fourth controller
 * fails that test rather than quietly going untested.
 *
 * <p>Each case hands the controller a <b>freshly built</b> value rather than the element's own
 * instance, so a controller comparing by identity fails here instead of passing on a shared
 * reference. That is the whole point: identity is what {@code equals} silently degrades to
 * for a type declaring no {@code equals}, and it degrades without failing.
 *
 * <p>What rides on the answer is whether the commit happens at all. A wrong {@code true} performs
 * a commit that writes nothing — an undo step and a dirtied document, and for the two
 * beat-defining kinds a {@code STRICT} tuplet re-validation that can drop a tuplet a lenient file
 * load accepted.
 */
class AttachmentDialogControllerTest extends UnitTest {

    private static final String ANNOTATION_TEXT = "dolce";
    private static final String OTHER_ANNOTATION_TEXT = "cresc.";
    private static final String TEMPO_DESCRIPTION = "Moderate";
    private static final int BPM = 100;
    private static final int OTHER_BPM = 132;

    private static Annotation annotation(String text) {
        return new Annotation(text);
    }

    private static BeatChange beatChange(Duration duration) {
        return new BeatChange(duration, Duration.CROTCHET);
    }

    private static Tempo tempo(int bpm) {
        return new Tempo(bpm, Duration.CROTCHET, new TempoMarking.Metronome(TEMPO_DESCRIPTION));
    }

    /**
     * One attachment kind's answer to the shared rule.
     *
     * @param description           the kind, naming the parameterized case
     * @param controllerType        the controller this case covers, which pins the table to the
     *                              sealed hierarchy
     * @param attachExisting        puts the value the element already carries onto it
     * @param askWithEqualValue     asks the kind's controller about a value equal to that one
     * @param askWithDifferentValue asks it about a value that differs
     */
    private record ModificationCase(
        String description,
        Class<? extends AttachmentDialogController<?>> controllerType,
        Consumer<StaffElement> attachExisting,
        Predicate<AttachmentTarget> askWithEqualValue,
        Predicate<AttachmentTarget> askWithDifferentValue
    ) {

        @Override
        public String toString() {
            return description;
        }
    }

    static Stream<ModificationCase> modificationCases() {
        return Stream.of(
            new ModificationCase(
                "annotation",
                AnnotationController.class,
                element -> element.addAttachment(
                    new AnnotationAttachment(element, annotation(ANNOTATION_TEXT))),
                target -> new AnnotationController(target)
                    .dataWasModified(annotation(ANNOTATION_TEXT)),
                target -> new AnnotationController(target)
                    .dataWasModified(annotation(OTHER_ANNOTATION_TEXT))
            ),
            new ModificationCase(
                "beat change",
                BeatChangeController.class,
                element -> element.addAttachment(
                    new BeatChangeAttachment(element, beatChange(Duration.CROTCHET_DOTTED))),
                target -> new BeatChangeController(target)
                    .dataWasModified(beatChange(Duration.CROTCHET_DOTTED)),
                target -> new BeatChangeController(target)
                    .dataWasModified(beatChange(Duration.QUAVER))
            ),
            new ModificationCase(
                "tempo change",
                TempoChangeController.class,
                element -> element.addAttachment(
                    new TempoChangeAttachment(element, tempo(BPM))),
                target -> new TempoChangeController(target)
                    .dataWasModified(tempo(BPM)),
                target -> new TempoChangeController(target)
                    .dataWasModified(tempo(OTHER_BPM))
            )
        );
    }

    /**
     * A one-note line whose note carries what {@code attachExisting} puts on it.
     *
     * @param attachExisting the attachment to place
     * @return a target naming that note, as an opened dialog would be bound to it
     */
    private static AttachmentTarget targetCarrying(Consumer<StaffElement> attachExisting) {
        var line = lineWith(ElementType.CROTCHET);
        var element = line.getElement(0);

        attachExisting.accept(element);

        return new AttachmentTarget(line, element);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modificationCases")
    void testAValueEqualToTheOneOnTheElementIsNotAModification(ModificationCase testCase) {
        var target = targetCarrying(testCase.attachExisting());

        assertThat(testCase.askWithEqualValue().test(target)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modificationCases")
    void testAValueDifferingFromTheOneOnTheElementIsAModification(ModificationCase testCase) {
        var target = targetCarrying(testCase.attachExisting());

        assertThat(testCase.askWithDifferentValue().test(target)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("modificationCases")
    void testAnElementCarryingNothingYetIsModifiedByAnyValue(ModificationCase testCase) {
        var target = targetCarrying(element -> {});

        assertThat(testCase.askWithEqualValue().test(target)).isTrue();
    }

    @Test
    void testCasesCoverEveryAttachmentController() {
        assertThat(modificationCases().<Class<?>>map(ModificationCase::controllerType))
            .containsExactlyInAnyOrder(AttachmentDialogController.class.getPermittedSubclasses());
    }
}
