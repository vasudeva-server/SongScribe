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
package songscribe.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.command.ToggleLoopPlaybackCommand;
import songscribe.message.command.TogglePlayWithRepeatsCommand;

class SelectableMessageTest extends UnitTest {

    // -------------------------------------------------------------------------
    // SelectableMessage.isSelected() — row 12
    // -------------------------------------------------------------------------

    @Test
    void testToggleLoopPlaybackCommandReturnsSelectedValue() {
        assertThat(new ToggleLoopPlaybackCommand(true).isSelected()).isTrue();
        assertThat(new ToggleLoopPlaybackCommand(false).isSelected()).isFalse();
    }

    @Test
    void testTogglePlayWithRepeatsCommandReturnsSelectedValue() {
        assertThat(new TogglePlayWithRepeatsCommand(true).isSelected()).isTrue();
        assertThat(new TogglePlayWithRepeatsCommand(false).isSelected()).isFalse();
    }
}
