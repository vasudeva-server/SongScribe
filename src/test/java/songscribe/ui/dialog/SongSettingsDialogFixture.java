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
package songscribe.ui.dialog;

import java.awt.Font;

import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.ui.action.MockEnvHelper;

import static org.mockito.Mockito.when;

/**
 * Assembles a real {@link SongSettingsDialog} over a mocked environment — the setup every
 * test class needs that exercises the dialog itself rather than the package-private static
 * helpers {@link SongSettingsDialogTest} covers.
 */
final class SongSettingsDialogFixture {

    private SongSettingsDialogFixture() {}

    /**
     * A built dialog together with the {@link DocumentFonts} the mocked score reports it,
     * which a test that changes the fonts under the dialog needs a handle on.
     */
    record Built(SongSettingsDialog dialog, DocumentFonts fonts) {}

    /**
     * Points the mocked score at {@code song} and at fonts with every role set to
     * {@code font}, then builds the dialog.
     * <p>
     * Every role has to be populated: the dialog's tabs read the title, subtitle,
     * attribution and sub-attribution fonts as they build, and an unset role throws. A test
     * that cares about only one of them still has to supply the rest.
     */
    static Built build(MockEnvHelper.MockEnv env, Song song, Font font) {
        var fonts = new DocumentFonts();

        for (var key : FontKey.values()) {
            fonts.setFont(key, font);
        }

        var scoreView = env.score();
        when(scoreView.getSong()).thenReturn(song);
        when(scoreView.getDocumentFonts()).thenReturn(fonts);

        BaseDialogTestHelper.configureMockFrame(env.frame());
        BaseDialog.resetSavedGeometry();

        return new Built(new SongSettingsDialog(env.frame()), fonts);
    }
}
