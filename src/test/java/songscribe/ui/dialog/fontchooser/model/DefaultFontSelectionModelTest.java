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

package songscribe.ui.dialog.fontchooser.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.awt.Font;

import javax.swing.event.ChangeListener;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class DefaultFontSelectionModelTest extends UnitTest {

    private static final Font ARIAL_PLAIN_12 = new Font("Arial", Font.PLAIN, 12);
    private static final Font ARIAL_BOLD_12 = new Font("Arial", Font.BOLD, 12);

    @Test
    void testSetSelectedFontFiresChangeEventWhenFontDiffers() {
        var model = new DefaultFontSelectionModel(ARIAL_PLAIN_12);
        var listener = mock(ChangeListener.class);
        model.addChangeListener(listener);

        model.setSelectedFont(ARIAL_BOLD_12);

        verify(listener).stateChanged(any());
    }

    @Test
    void testSetSelectedFontFiresNoEventWhenFontUnchanged() {
        var model = new DefaultFontSelectionModel(ARIAL_PLAIN_12);
        var listener = mock(ChangeListener.class);
        model.addChangeListener(listener);

        model.setSelectedFont(ARIAL_PLAIN_12);

        verify(listener, never()).stateChanged(any());
    }

    @Test
    void testGettersReturnCorrectValuesFromConstructorFont() {
        var font = new Font("Times New Roman", Font.ITALIC, 16);
        var model = new DefaultFontSelectionModel(font);

        assertThat(model.getSelectedFontName()).isEqualTo(font.getName());
        assertThat(model.getSelectedFontFamily()).isEqualTo(font.getFamily());
        assertThat(model.getSelectedFontSize()).isEqualTo(font.getSize());
    }
}
