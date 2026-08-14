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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.BeatChange;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Duration;
import songscribe.dom.StaffElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;

/**
 * Exercises the contract of {@link BeatChangeBackEnd}, the domain half of the beat-change dialog.
 *
 * <p><b>Existing change</b> — the two states of the element: carrying a beat change, and carrying
 * none. Both are asserted through {@link BeatChangeBackEnd#existingChange()}, which is what the
 * dialog reads to decide whether it is adding or modifying.
 *
 * <p><b>Apply</b> — the postcondition is the same in both states, so it is asserted as one
 * property: afterwards the element carries exactly the applied change. The add and change paths
 * differ only in a promise about identity — an existing attachment is reused rather than replaced
 * — which has its own test.
 *
 * <p><b>Validate</b> — the contract says no beat change is refusable because both of its durations
 * come from {@link Duration}. Enumerated over that enum rather than sampled, so a new note value
 * reaches this test on its own.
 *
 * <p><b>Remove</b> — the element carries no beat change afterwards, whether or not it carried one
 * before.
 *
 * <p><b>Not tested here:</b> {@code DialogBackEnd.apply}'s promise that a commit is
 * <em>one</em> undoable step. These fixtures run on {@link UnitTest#detachedLine()}, whose song
 * has mutation tracking suspended, so no bracket closes and no notification is posted. Asserting
 * it needs a real {@code Song}, and belongs with the rest of the derived-from-contract cases in
 * Phase 7 of {@code plans/ui-dialog-seam.md}. These cases were carried across the seam from
 * {@code BeatChangeDialogTest}, which asserted them against the dialog before the split.
 */
class BeatChangeBackEndTest extends UnitTest {

    private static final BeatChange APPLIED = new BeatChange(Duration.MINIM, Duration.QUAVER);

    private StaffElement element;
    private BeatChangeBackEnd backEnd;

    @BeforeEach
    void setUp() {
        var line = detachedLine();
        element = crotchet();
        line.addElement(element);
        backEnd = new BeatChangeBackEnd(new AttachmentTarget(line, element));
    }

    private record ExistingChangeCase(String description, @Nullable BeatChange existing) {}

    static Stream<ExistingChangeCase> existingChangeCases() {
        return Stream.of(
            new ExistingChangeCase("element carrying a beat change reports it",
                new BeatChange(Duration.MINIM, Duration.CROTCHET)),
            new ExistingChangeCase("element carrying none reports null", null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testExistingChangeReportsWhatTheElementCarries(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        assertThat(backEnd.existingChange())
            .as("existingChange answers the beat change on the element, or null for none")
            .isEqualTo(testCase.existing());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testApplyLeavesTheElementCarryingExactlyTheAppliedChange(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        backEnd.apply(APPLIED);

        assertThat(backEnd.existingChange())
            .as("the element carries the applied change, whether one was there before or not")
            .isEqualTo(APPLIED);
    }

    @Test
    void testApplyReusesTheExistingAttachmentRatherThanReplacingIt() {
        var original = new BeatChangeAttachment(element, new BeatChange(Duration.CROTCHET, Duration.CROTCHET));
        element.addAttachment(original);

        backEnd.apply(APPLIED);

        assertThat(element.findAttachment(BeatChangeAttachment.class))
            .as("the attachment is updated in place, so anything holding it stays valid")
            .isSameAs(original);
    }

    @ParameterizedTest
    @EnumSource(Duration.class)
    void testValidateAcceptsEveryBeatChangeTheDurationsCanForm(Duration duration) {
        assertThat(backEnd.validate(new BeatChange(duration, duration)).isValid())
            .as("no combination of note values is refusable")
            .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingChangeCases")
    void testRemoveLeavesTheElementCarryingNoBeatChange(ExistingChangeCase testCase) {
        givenExistingChange(testCase.existing());

        backEnd.remove();

        assertThat(backEnd.existingChange())
            .as("the element carries no beat change, whether one was there before or not")
            .isNull();
    }

    private void givenExistingChange(@Nullable BeatChange existing) {
        if (existing != null) {
            element.addAttachment(new BeatChangeAttachment(element, existing));
        }
    }
}
