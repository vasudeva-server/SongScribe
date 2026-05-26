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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;

class EndingValidationResultTest extends UnitTest {

    private static final int SPAN_START = 3;
    private static final int SPAN_END = 7;

    @Test
    void testInvalidReturnsIsValidFalse() {
        var result = EndingValidationResult.invalid();

        assertThat(result.isValid()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(EndingValidationResult.PrecedingAction.class)
    void testValidReturnsIsValidTrueAndAccessorsMatchInputs(
        EndingValidationResult.PrecedingAction action
    ) {
        var result = EndingValidationResult.valid(action, SPAN_START, SPAN_END);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPrecedingAction()).isEqualTo(action);
        assertThat(result.getSpanStart()).isEqualTo(SPAN_START);
        assertThat(result.getSpanEnd()).isEqualTo(SPAN_END);
    }
}
