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
package songscribe;

import org.jetbrains.annotations.NotNull;

import songscribe.converter.AbcConverter;
import songscribe.converter.ImageConverter;
import songscribe.converter.MidiConverter;
import songscribe.converter.PDFConverter;
import songscribe.ui.component.MainFrame;
import songscribe.uiconverter.UIConverter;
import songscribe.util.Log;
import songscribe.util.UIUtils;

public final class SongScribe {

    private SongScribe() {}

    public static void main(@NotNull String[] args) {
        // Figure out which app to start. The default is Song Writer.
        String app;

        if (args.length > 0) {
            app = args[0];
        } else {
            var prop = System.getProperty("songscribe");
            app = (prop == null) ? "sw" : prop;
        }

        // Allow Swing components to handle a property change with a null property name,
        // which indicates more than one property has changed.
        System.setProperty("swing.actions.reconfigureOnNull", "true");

        switch (app) {
            case "image_converter" -> ImageConverter.main(args);
            case "midi_converter" -> MidiConverter.main(args);
            case "pdf_converter" -> PDFConverter.main(args);
            case "ui_converter" -> UIConverter.main(args);
            case "abc_converter" -> AbcConverter.main(args);
            default -> {
                Log.setNameWithoutExtension("songscribe-writer");

                // This has to be done before any Swing components are created
                UIUtils.initLaf();
                MainFrame.main(args);
            }
        }
    }
}
