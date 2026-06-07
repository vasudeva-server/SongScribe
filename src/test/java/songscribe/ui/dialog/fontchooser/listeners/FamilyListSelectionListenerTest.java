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

package songscribe.ui.dialog.fontchooser.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Font;

import javax.swing.event.ListSelectionEvent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.ui.dialog.fontchooser.FontContainer;

class FamilyListSelectionListenerTest extends UnitTest {

    private static final String TIMES_FAMILY = "Times New Roman";
    // Old font size and new selected size are intentionally different so the
    // assertion can prove the new font's size comes from getSelectedSize(), not
    // from the old font.
    private static final int OLD_FONT_SIZE = 12;
    private static final int SELECTED_SIZE = 14;

    @Test
    void testValueChangedSkipsWhenAdjusting() {
        var container = mock(FontContainer.class);
        var listener = new FamilyListSelectionListener(container);
        var event = mock(ListSelectionEvent.class);
        when(event.getValueIsAdjusting()).thenReturn(true);

        listener.valueChanged(event);

        verify(container, never()).setSelectedFont(any());
        verify(container, never()).setPreviewFont(any());
    }

    @Test
    void testValueChangedBuildsCorrectFontOnNonAdjustingEvent() {
        var oldFont = new Font("Arial", Font.ITALIC, OLD_FONT_SIZE);
        var container = mock(FontContainer.class);
        when(container.getSelectedFont()).thenReturn(oldFont);
        when(container.getSelectedFamily()).thenReturn(TIMES_FAMILY);
        when(container.getSelectedSize()).thenReturn((float) SELECTED_SIZE);

        var listener = new FamilyListSelectionListener(container);
        var event = mock(ListSelectionEvent.class);
        when(event.getValueIsAdjusting()).thenReturn(false);

        listener.valueChanged(event);

        // Verify the new font was constructed from: selected family, old style, selected size.
        var captor = ArgumentCaptor.forClass(Font.class);
        verify(container).setSelectedFont(captor.capture());
        var newFont = captor.getValue();
        assertThat(newFont.getFamily()).isEqualTo(TIMES_FAMILY);
        assertThat(newFont.getStyle()).isEqualTo(oldFont.getStyle());
        assertThat(newFont.getSize()).isEqualTo(SELECTED_SIZE);

        // setPreviewFont must also be called with the same new font.
        verify(container).setPreviewFont(newFont);
    }
}
