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
import songscribe.dom.AttachmentRemoval;
import songscribe.dom.Duration;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Exercises the contract of {@link TempoChangeBackEnd}, the domain half of the tempo-change
 * dialog.
 *
 * <p><b>Existing change</b> — the two states of the element: carrying a tempo change, and carrying
 * none. The value reported is the {@link Tempo}, not the attachment holding it, which is what
 * keeps the document graph off the dialog's side of the seam.
 *
 * <p><b>Apply</b> — one property covering both states: afterwards the element carries exactly the
 * applied tempo. The add and change paths differ only in the identity promise — an existing
 * attachment is reused rather than replaced — which has its own test.
 *
 * <p><b>Validate</b> — the contract says no tempo the dialog can build is refusable. One
 * representative suffices; unlike the beat change there is no finite domain to enumerate, since
 * the beats-per-minute is a number.
 *
 * <p><b>Remove</b> — the element carries no tempo change afterwards, whether or not it carried one
 * before, and the removal redefines the beat through the same chokepoint the commit path uses.
 *
 * <p><b>Detached elements</b> — the last test asserts against {@link AttachmentRemoval} rather
 * than this class, because it is no longer reachable through a back end: an
 * {@link AttachmentTarget} cannot be built for an element that is not in its line. It is kept
 * because the promise it names is real and still relied on — the removal helper works on an
 * element the score has already dropped.
 *
 * <p><b>Not tested here:</b> {@code DialogBackEnd.apply}'s promise that a commit is <em>one</em>
 * undoable step — see {@link BeatChangeBackEndTest} for why, and Phase 7 of
 * {@code plans/ui-dialog-seam.md} for where it lands. These cases were carried across the seam
 * from {@code TempoChangeDialogTest}, which asserted them against the dialog before the split.
 */
class TempoChangeBackEndTest extends UnitTest {

    private static final Tempo APPLIED = new Tempo(140, Duration.QUAVER, "Presto", true);

    private StaffElement element;
    private TempoChangeBackEnd backEnd;

    @BeforeEach
    void setUp() {
        var line = detachedLine();
        element = crotchet();
        line.addElement(element);
        backEnd = new TempoChangeBackEnd(new AttachmentTarget(line, element));
    }

    private record ExistingChangeCase(String description, @Nullable Tempo existing) {}

    static Stream<ExistingChangeCase> existingChangeCases() {
        return Stream.of(
            new ExistingChangeCase("element carrying a tempo change reports it",
                new Tempo(60, Duration.CROTCHET, "Largo", true)),
            new ExistingChangeCase("element carrying none reports null", null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testExistingChangeReportsTheTempoTheElementCarries(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        assertThat(backEnd.existingChange())
            .as("existingChange answers the tempo on the element, or null for none")
            .isSameAs(testCase.existing());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testApplyLeavesTheElementCarryingExactlyTheAppliedTempo(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        backEnd.apply(APPLIED);

        assertThat(backEnd.existingChange())
            .as("the element carries the applied tempo, whether one was there before or not")
            .isSameAs(APPLIED);
    }

    @Test
    void testApplyReusesTheExistingAttachmentRatherThanReplacingIt() {
        var original = new TempoChangeAttachment(element, new Tempo(60, Duration.CROTCHET, "Largo", true));
        element.addAttachment(original);

        backEnd.apply(APPLIED);

        assertThat(element.findAttachment(TempoChangeAttachment.class))
            .as("the attachment is updated in place, so anything holding it stays valid")
            .isSameAs(original);
    }

    @Test
    void testValidateAcceptsATempoTheDialogCanBuild() {
        assertThat(backEnd.validate(APPLIED).isValid())
            .as("no tempo assembled from the dialog's controls is refusable")
            .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testRemoveLeavesTheElementCarryingNoTempoChange(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        backEnd.remove();

        assertThat(backEnd.existingChange())
            .as("the element carries no tempo change, whether one was there before or not")
            .isNull();
    }

    @Test
    void testRemovalHelperStillWorksOnAnElementTheScoreHasDropped() {
        // A delete elsewhere can detach the element while a dialog is open. The back end can no
        // longer be reached in that state, but the helper it delegates to must not need a line.
        var line = detachedLine();
        var detached = crotchet();
        line.addElement(detached);
        detached.addAttachment(new TempoChangeAttachment(detached, APPLIED));
        line.removeElement(line.getElementIndex(detached));

        AttachmentRemoval.removeTempoChange(detached);

        assertThat(detached.findAttachment(TempoChangeAttachment.class))
            .as("the attachment is removed whether or not the element is still in a line")
            .isNull();
    }

    private void givenExistingChange(@Nullable Tempo existing) {
        if (existing != null) {
            element.addAttachment(new TempoChangeAttachment(element, existing));
        }
    }
}
