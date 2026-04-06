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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.SongScribe;

@SuppressWarnings("FieldMayBeStatic")
public class ImageConverter {

    private static final Logger LOG = LoggerFactory.getLogger(ImageConverter.class);

    @ArgumentDescribe("The type of resulting image. Values: [ jpg | png ]")
    public static final String type = "png";

    @ArgumentDescribe("Resolution in DPI")
    public static final int resolution = 100;

    @ArgumentDescribe("Suffix to add to filename")
    public static final String suffix = "";

    @ArgumentDescribe("Export image without lyrics under the song")
    public final boolean withoutLyrics = false;

    @ArgumentDescribe("Export image without song title")
    public final boolean withoutSongTitle = false;

    @ArgumentDescribe("Export image without song copyright info and date")
    public final boolean withoutCopyright = false;

    @ArgumentDescribe("Margin around the image in pixels")
    public final int margin = 10;

    @ArgumentDescribe(
        "Top margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public final int topmargin = -1;

    @ArgumentDescribe(
        "Left margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public final int leftmargin = -1;

    @ArgumentDescribe(
        "Bottom margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public final int bottommargin = -1;

    @ArgumentDescribe(
        "Right margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public final int rightmargin = -1;

    @FileArgument
    public final File[] files = new File[0];

    public static void main(String[] args) {
        SongScribe.configureLogging();
        var reader = new ArgumentReader<>(args, ImageConverter.class);
        var converter = reader.getObj();

        if (converter != null) {
            converter.convert();
        }
    }

    @SuppressWarnings("ConstantValue")
    private void convert() {
        // Image export not yet implemented with component-based rendering
        LOG.warn("Image conversion is not yet implemented");
    }
}
