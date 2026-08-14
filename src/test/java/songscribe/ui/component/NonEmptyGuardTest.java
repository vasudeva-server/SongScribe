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
package songscribe.ui.component;

import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the contract of {@link NonEmptyGuard}.
 *
 * <p><b>The class invariant</b> — the field is never blank once focus has left it — is what every
 * case here asserts, from a different starting state: text the caller populated and the user
 * emptied, text the user typed and then emptied, a field populated blank, and a field whose caller
 * never announced a value at all. The last two are what the fallback exists for, and they are
 * separate cases because only one of them calls {@link NonEmptyGuard#rememberCurrentText()}.
 *
 * <p><b>What counts as blank</b> — the empty string and whitespace alike, since a field holding
 * only spaces is one the user has emptied. Both are covered, together with a non-blank value that
 * must be left exactly as it is.
 *
 * <p><b>Always yields</b> — the guard never traps the caret. Asserted alongside the restoration
 * rather than on its own, because yielding while still blank would be the failure worth catching
 * and no single assertion sees both.
 *
 * <p><b>Fallback</b> — a blank fallback is refused at construction, because a guard that could
 * restore a blank value cannot keep its own promise.
 */
class NonEmptyGuardTest extends UnitTest {

    private static final String FALLBACK = "Untitled";
    private static final String FOUND = "Invocation";

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void testEmptyingAValueTheUserFoundPutsThatValueBack(String blanked) {
        var field = new JTextField(FOUND);
        var guard = new NonEmptyGuard(field, FALLBACK);
        guard.rememberCurrentText();

        field.setText(blanked);

        assertThat(guard.shouldYieldFocus(field, new JButton()))
            .as("the guard releases the caret rather than stranding the user in the field")
            .isTrue();
        assertThat(field.getText())
            .as("the value the user found on arriving is the value put back")
            .isEqualTo(FOUND);
    }

    @Test
    void testEmptyingAValueTheUserTypedPutsThatValueBack() {
        var field = new JTextField(FOUND);
        var guard = new NonEmptyGuard(field, FALLBACK);
        guard.rememberCurrentText();

        // A committed edit becomes the value worth restoring, not the one it replaced.
        field.setText("Ecstasy's Heights");
        guard.shouldYieldFocus(field, new JButton());
        field.setText("");

        guard.shouldYieldFocus(field, new JButton());

        assertThat(field.getText())
            .as("the most recent good value is restored, not the one before it")
            .isEqualTo("Ecstasy's Heights");
    }

    @Test
    void testAFieldPopulatedBlankFallsBackToTheSuppliedValue() {
        var field = new JTextField("");
        var guard = new NonEmptyGuard(field, FALLBACK);
        guard.rememberCurrentText();

        guard.shouldYieldFocus(field, new JButton());

        assertThat(field.getText())
            .as("a value that was already blank is not a previous value worth restoring")
            .isEqualTo(FALLBACK);
    }

    @Test
    void testAFieldNeverPopulatedAtAllFallsBackToTheSuppliedValue() {
        var field = new JTextField("");
        var guard = new NonEmptyGuard(field, FALLBACK);

        guard.shouldYieldFocus(field, new JButton());

        assertThat(field.getText())
            .as("the fallback covers a caller that never announced a value")
            .isEqualTo(FALLBACK);
    }

    @ParameterizedTest
    @MethodSource("keptValues")
    void testANonBlankValueIsLeftExactlyAsItIs(String typed) {
        var field = new JTextField(FOUND);
        var guard = new NonEmptyGuard(field, FALLBACK);
        guard.rememberCurrentText();

        field.setText(typed);

        assertThat(guard.shouldYieldFocus(field, new JButton()))
            .as("a valid field yields")
            .isTrue();
        assertThat(field.getText())
            .as("the guard touches nothing when the field holds something")
            .isEqualTo(typed);
    }

    static Stream<String> keptValues() {
        return Stream.of("Ecstasy's Heights", " padded ", "x");
    }

    @Test
    void testABlankFallbackIsRefusedBecauseItCouldNotKeepThePromise() {
        assertThatThrownBy(() -> new NonEmptyGuard(new JTextField(), " "))
            .as("a guard whose fallback is blank could restore a blank value")
            .isInstanceOf(IllegalArgumentException.class);
    }

}
