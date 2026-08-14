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
package songscribe.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Arrays;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeySignatureElement;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/** Tests for the prompt shown before a deletion carries away half of a barline / key-signature pair. */
class KeySignatureConfirmsTest extends UnitTest {

    /** The button index {@code showOptionDialog} reports for Yes. */
    private static final int YES_INDEX = 0;

    /** The button index {@code showOptionDialog} reports for No. */
    private static final int NO_INDEX = 1;

    /** Line 0 of a fresh song, holding {@code elements} in the order given. */
    private static Line lineOf(StaffElement... elements) {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
        });

        return line;
    }

    private static StaffElement crotchet() {
        return ElementType.CROTCHET.newInstance();
    }

    private static StaffElement singleBarline() {
        return ElementType.SINGLE_BARLINE.newInstance();
    }

    private static KeySignatureElement keySignature() {
        return new KeySignatureElement(Key.DEFAULT);
    }

    /** A line whose key signature at index 2 sits behind the barline at index 1. */
    private static Line pairedLine() {
        return lineOf(crotchet(), singleBarline(), keySignature(), crotchet());
    }

    @ParameterizedTest
    @EnumSource(value = Line.KeyPairDeletion.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void testEveryDirectionHasItsOwnMessage(Line.KeyPairDeletion answer) {
        var otherKeys = Arrays.stream(Line.KeyPairDeletion.values())
            .filter(other -> other != answer && other != Line.KeyPairDeletion.NONE)
            .map(KeySignatureConfirms::messageKeyFor)
            .collect(Collectors.toSet());

        assertThat(KeySignatureConfirms.messageKeyFor(answer))
            .as("%s must name what it carries in a message of its own, not one shared with "
                + "another direction", answer)
            .isNotIn(otherKeys);
    }

    @Test
    void testNothingToCarryHasNoMessage() {
        assertThatIllegalArgumentException()
            .as("NONE names no elements, so asking for its prompt is a caller bug")
            .isThrownBy(() -> KeySignatureConfirms.messageKeyFor(Line.KeyPairDeletion.NONE));
    }

    @Test
    void testADeletionCarryingNothingPairedIsNotConfirmed() {
        var line = lineOf(crotchet(), singleBarline(), crotchet());

        try (var dialogs = mockStatic(OptionDialogs.class)) {
            assertThat(KeySignatureConfirms.confirmPairedDeletion(null, line, 0, 1))
                .as("a deletion that reaches nothing the user did not select proceeds unasked")
                .isTrue();

            dialogs.verify(
                () -> OptionDialogs.showOptionDialog(
                    any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any()),
                never());
        }
    }

    @Test
    void testDeletingTheBarlineAsksWithTheMessageForTheKeySignatureItCarries() {
        try (var dialogs = mockStatic(OptionDialogs.class)) {
            dialogs.when(() -> OptionDialogs.showOptionDialog(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any())
            ).thenReturn(YES_INDEX);

            assertThat(KeySignatureConfirms.confirmPairedDeletion(null, pairedLine(), 0, 1)).isTrue();

            dialogs.verify(() -> OptionDialogs.showOptionDialog(
                any(),
                eq(Strings.CONFIRM_TITLE_KEY_SIGNATURE),
                eq(KeySignatureConfirms.messageKeyFor(Line.KeyPairDeletion.KEY_SIGNATURE_AFTER)),
                anyInt(), anyInt(), any(), any(), any(), any()));
        }
    }

    @Test
    void testDeletingTheKeySignatureAsksWithTheMessageForTheBarlineItCarries() {
        try (var dialogs = mockStatic(OptionDialogs.class)) {
            dialogs.when(() -> OptionDialogs.showOptionDialog(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any())
            ).thenReturn(YES_INDEX);

            assertThat(KeySignatureConfirms.confirmPairedDeletion(null, pairedLine(), 2, 3)).isTrue();

            dialogs.verify(() -> OptionDialogs.showOptionDialog(
                any(),
                eq(Strings.CONFIRM_TITLE_KEY_SIGNATURE),
                eq(KeySignatureConfirms.messageKeyFor(Line.KeyPairDeletion.BARLINE_BEFORE)),
                anyInt(), anyInt(), any(), any(), any(), any()));
        }
    }

    @Test
    void testDecliningRefusesTheDeletion() {
        try (var dialogs = mockStatic(OptionDialogs.class)) {
            dialogs.when(() -> OptionDialogs.showOptionDialog(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any())
            ).thenReturn(NO_INDEX);

            assertThat(KeySignatureConfirms.confirmPairedDeletion(null, pairedLine(), 2, 3))
                .as("declining must stop the deletion")
                .isFalse();
        }
    }

    @Test
    void testADismissedDialogRefusesTheDeletion() {
        try (var dialogs = mockStatic(OptionDialogs.class)) {
            dialogs.when(() -> OptionDialogs.showOptionDialog(
                any(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any())
            ).thenReturn(JOptionPane.CLOSED_OPTION);

            assertThat(KeySignatureConfirms.confirmPairedDeletion(null, pairedLine(), 2, 3))
                .as("a dialog dismissed without an answer must stop the deletion, as a suppressed "
                    + "one does in a headless run")
                .isFalse();
        }
    }
}
