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
package songscribe.converter;

import java.io.File;
import java.io.PrintWriter;

import songscribe.ui.action.ExportABCAction;
import songscribe.ui.component.Score;
import songscribe.util.Log;

public class AbcConverter {

    @FileArgument
    public File file = null;

    public static void main(String[] args) {
        Log.setNameWithoutExtension("abc-converter");
        var reader = new ArgumentReader<>(args, AbcConverter.class);
        reader.getObj().convert(new PrintWriter(System.out));
    }

    public void convert(PrintWriter writer) {
        var mainFrame = new ConverterMainFrame();
        var score = new Score(mainFrame);
        mainFrame.setScore(score);

        score.setComposition(null);
        score.openFile(mainFrame, file, false);

        ExportABCAction.writeABC(score.getComposition(), writer);
        writer.close();
    }
}
