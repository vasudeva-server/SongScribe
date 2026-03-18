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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.SongScribe;
import songscribe.ui.component.Score;
import songscribe.util.FileUtils;

@SuppressWarnings("FieldMayBeStatic")
public class SVGConverter {

    private static final Logger LOG = LoggerFactory.getLogger(SVGConverter.class);

    @ArgumentDescribe("Export SVG without lyrics under the song")
    public final boolean withoutLyrics = false;

    @ArgumentDescribe("Export SVG without song title")
    public final boolean withoutSongTitle = false;

    @FileArgument
    public File[] files = new File[0];

    public static void main(String[] args) {
        SongScribe.configureLogging();
        var reader = new ArgumentReader<>(args, SVGConverter.class);
        var converter = reader.getObj();

        if (converter == null) {
            LOG.error("Failed to parse arguments");
            return;
        }

        converter.convert();
    }

    public void convert() {
        var score = new Score(null);

        for (var file : files) {
            Converter.loadComposition(file, score);
            Converter.applyExportExclusions(score.getComposition(), withoutLyrics, withoutSongTitle);

            try {
                var path = FileUtils.getPathWithoutExtension(file) + ".svg";
                score.createSVG(new File(path));
                LOG.info("Converted {} to SVG", file.getName());
            } catch (IOException e) {
                LOG.error("Could not convert {}", file.getName(), e);
            }
        }
    }
}
