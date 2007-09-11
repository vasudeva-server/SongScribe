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

package songscribe.ui.graphics;

import java.awt.*;
import java.awt.image.*;

import songscribe.util.GraphicUtils;

/**
 * @author Konstantin Bulenkov
 */
public class HiDPIScaledImage extends BufferedImage {

    private static final int scale = GraphicUtils.getScreenScaleFactor();
    private final Image myImage;

    public HiDPIScaledImage(int width, int height, int imageType) {
        this(null, scale * width, scale * height, imageType);
    }

    public HiDPIScaledImage(Image image, int width, int height, int imageType) {
        super(width, height, imageType);
        myImage = image;
    }

    public Image getDelegate() {
        return myImage;
    }

    @Override
    public Graphics2D createGraphics() {
        var g = super.createGraphics();

        if (myImage == null) {
            return new HiDPIScaledGraphics(g, this);
        }

        return g;
    }
}
