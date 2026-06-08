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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.jspecify.annotations.Nullable;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.selection.ReflectionTestHelper;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;

/**
 * Tests for MusicEditOperations null-state guards (state == null paths) and
 * the toggleTuplet canToggle==false guard.
 *
 * <p>Each operation checks {@code coordinator.getActiveSelection()} on entry and
 * either returns a safe default or returns early without emitting any mutation.
 * These branches are dark in the main test suite; this class covers them directly.
 */
class MusicEditOperationsNullStateTest extends UnitTest {

    // Minimum notes for a selection that bypasses the null-state guard.
    private static final int ACTIVE_STATE_NOTE_COUNT = 3;

    private Song song;
    @Nullable private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        song = new Song();
    }

    @AfterEach
    void tearDown() {
        if (messageCenterMock != null) {
            messageCenterMock.close();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Creates a MusicEditOperations whose coordinator has no active line,
     * so getActiveSelection() returns null.
     * MessageCenter is mocked after construction.
     */
    private MusicEditOperations opsWithNullState() {
        // A coordinator with no registered/activated line returns null from getActiveSelection().
        var coordinator = new SelectionCoordinator(() -> song);
        var ops = new MusicEditOperations(song, coordinator);
        messageCenterMock = mockStatic(MessageCenter.class);
        return ops;
    }

    /**
     * Creates a MusicEditOperations with an active selection (line with notes),
     * used when the null guard must NOT fire but a later guard must.
     * MessageCenter is mocked after construction.
     */
    private MusicEditOperations opsWithActiveState() {
        var line = new Line(song);
        song.withoutMutationTracking(() -> {
            for (var i = 0; i < ACTIVE_STATE_NOTE_COUNT; i++) {
                line.addElement(crotchet());
            }
            song.addLine(line);
        });
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        ReflectionTestHelper.selectRange(coordinator, 0, ACTIVE_STATE_NOTE_COUNT - 1);
        var ops = new MusicEditOperations(song, coordinator);
        messageCenterMock = mockStatic(MessageCenter.class);
        return ops;
    }

    /** Asserts that no SongDidChangeNotification was posted to MessageCenter. */
    private void verifyNoChangeNotification() {
        var mock = messageCenterMock;

        if (mock == null) {
            throw new IllegalStateException("messageCenterMock not set — call opsWithNullState() first");
        }

        mock.verify(() -> MessageCenter.post(any(SongDidChangeNotification.class)), never());
    }

    // -----------------------------------------------------------------------
    // canToggleBeaming — null state
    // -----------------------------------------------------------------------

    @Test
    void testCanToggleBeamingReturnsFalseWhenStateNull() {
        assertThat(opsWithNullState().canToggleBeaming())
            .as("canToggleBeaming() with null state must return false")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // toggleBeaming — null state guard
    // -----------------------------------------------------------------------

    @Test
    void testToggleBeamingEmitsNoNotificationWhenStateNull() {
        opsWithNullState().toggleBeaming();
        verifyNoChangeNotification();
    }

    // -----------------------------------------------------------------------
    // canToggleTie — null state
    // -----------------------------------------------------------------------

    @Test
    void testCanToggleTieReturnsFalseWhenStateNull() {
        assertThat(opsWithNullState().canToggleTie())
            .as("canToggleTie() with null state must return false")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // toggleTie — null state guard
    // -----------------------------------------------------------------------

    @Test
    void testToggleTieEmitsNoNotificationWhenStateNull() {
        opsWithNullState().toggleTie();
        verifyNoChangeNotification();
    }

    // -----------------------------------------------------------------------
    // canToggleTuplet — null state
    // -----------------------------------------------------------------------

    @Test
    void testCanToggleTupletReturnsFalseInfoWhenStateNull() {
        var info = opsWithNullState().canToggleTuplet();
        assertThat(info.canToggle())
            .as("canToggle must be false when state is null")
            .isFalse();
        assertThat(info.existing())
            .as("existing must be null when state is null")
            .isNull();
        assertThat(info.coversExisting())
            .as("coversExisting must be false when state is null")
            .isFalse();
    }

    // -----------------------------------------------------------------------
    // toggleTuplet — null state guard
    // -----------------------------------------------------------------------

    @Test
    void testToggleTupletEmitsNoNotificationWhenStateNull() {
        // canToggle=true, existing=null so the null-state guard fires before any other check.
        // (If canToggle were false the IllegalStateException guard would fire first on a real
        // state, but here state is null so the method returns early before reaching that check.)
        var info = new TupletToggleInfo(true, null, false);
        opsWithNullState().toggleTuplet(3, info);
        verifyNoChangeNotification();
    }

    // -----------------------------------------------------------------------
    // toggleTuplet — canToggle == false throws IllegalStateException
    // -----------------------------------------------------------------------

    @Test
    void testToggleTupletThrowsWhenInfoCannotToggle() {
        // An active state is required — the null guard must not fire.
        // The canToggle == false guard fires immediately after the null check.
        var info = new TupletToggleInfo(false, null, false);
        var ops = opsWithActiveState();

        assertThatThrownBy(() -> ops.toggleTuplet(3, info))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("canToggle() == false");
    }
}
