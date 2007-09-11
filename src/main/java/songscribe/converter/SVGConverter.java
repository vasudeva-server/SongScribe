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
import java.io.IOException;

import songscribe.ui.component.Score;
import songscribe.util.FileUtils;

@SuppressWarnings("FieldMayBeStatic")
public class SVGConverter {

    @ArgumentDescribe("Export SVG without lyrics under the song")
    public final boolean withoutLyrics = false;

    @ArgumentDescribe("Export SVG without song title")
    public final boolean withoutSongTitle = false;

    @FileArgument
    public File[] files = new File[0];

    public static void main(String[] args) {
        var reader = new ArgumentReader<>(args, SVGConverter.class);
        reader.getObj().convert();
    }

    public void convert() {
        var mainFrame = new ConverterMainFrame();
        var score = new Score(mainFrame);
        mainFrame.setScore(score);

        for (var file : files) {
            var composition = Converter.getCompositionFromFile(
                file,
                mainFrame,
                withoutLyrics,
                withoutSongTitle,
                false
            );

            try {
                var path = FileUtils.getPathWithoutExtension(file) + ".svg";
                score.createSVG(new File(path), false);
            } catch (IOException e) {
                System.out.println("Could not convert " + file.getName());
            }
        }
    }
}
