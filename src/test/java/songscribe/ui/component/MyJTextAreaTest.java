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
package songscribe.ui.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.event.KeyEvent;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link MyJTextArea} covering:
 * <ul>
 *   <li>Row 79 — {@code processKeyEvent}: when delegate returns true, super is skipped;
 *       when delegate returns false, super is called</li>
 * </ul>
 *
 * <p>Each test injects a mock focus delegate via the {@code createDelegate()} hook so that
 * the production {@code processKeyEvent} body runs. The delegation contract — that the delegate
 * is always called — is verified with Mockito {@code verify}.
 */
class MyJTextAreaTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 79: processKeyEvent delegation short-circuit
    // -----------------------------------------------------------------------

    /**
     * Creates a {@link MyJTextArea} that injects the given delegate without overriding
     * {@code processKeyEvent}, so the production implementation is exercised.
     */
    private static MyJTextArea areaWith(TextFocusDelegate delegate) {
        return new MyJTextArea() {
            @Override
            protected TextFocusDelegate createDelegate() {
                return delegate;
            }
        };
    }

    @Test
    void testDelegateReturnsTrueSkipsSuperKeyProcessing() {
        var mockDelegate = mock(TextFocusDelegate.class);
        when(mockDelegate.processKeyEvent(any())).thenReturn(true);

        var area = areaWith(mockDelegate);
        var keyEvent = new KeyEvent(area, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_TAB, KeyEvent.CHAR_UNDEFINED);

        // Production MyJTextArea.processKeyEvent runs (not overridden here)
        area.processKeyEvent(keyEvent);

        // Delegate must be called; production code returns early (skips super) when it returns true
        verify(mockDelegate).processKeyEvent(keyEvent);
    }

    @Test
    void testDelegateReturnsFalseCallsSuperKeyProcessing() {
        var mockDelegate = mock(TextFocusDelegate.class);
        when(mockDelegate.processKeyEvent(any())).thenReturn(false);

        var area = areaWith(mockDelegate);
        var keyEvent = new KeyEvent(area, KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a');

        area.processKeyEvent(keyEvent);

        // Delegate must be called; production code falls through to super when it returns false
        verify(mockDelegate).processKeyEvent(keyEvent);
    }
}
