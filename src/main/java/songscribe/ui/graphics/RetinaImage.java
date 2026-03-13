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

import module java.desktop;

import songscribe.util.GraphicUtils;

/**
 * @author Konstantin Bulenkov
 * @author Aparajita Fishman
 */
public final class RetinaImage {

    private static final Component ourComponent = new Component() {};

    private RetinaImage() {}

    /**
     * Creates a Retina-aware wrapper over a raw image.
     * The raw image should be provided in scale of the Retina default scale factor (2x).
     * The wrapper will represent the raw image in the user coordinate space.
     *
     * @param image the raw image
     * @return the Retina-aware wrapper
     */
    public static Image createFrom(Image image) {
        return createFrom(
            image,
            GraphicUtils.getScreenScaleFactor(),
            ourComponent
        );
    }

    /**
     * Creates a Retina-aware wrapper over a raw image.
     * The raw image should be provided in the specified scale.
     * The wrapper will represent the raw image in the user coordinate space.
     *
     * @param image    the raw image
     * @param scale    the raw image scale
     * @param observer the raw image observer
     * @return the Retina-aware wrapper
     */
    public static Image createFrom(
        Image image,
        int scale,
        ImageObserver observer
    ) {
        var w = image.getWidth(observer);
        var h = image.getHeight(observer);

        return create(image, w / scale, h / scale, BufferedImage.TYPE_INT_ARGB);
    }

    public static BufferedImage create(int width, int height, int type) {
        return create(null, width, height, type);
    }

    private static BufferedImage create(
        Image image,
        int width,
        int height,
        int type
    ) {
        if (image == null) {
            return new HiDPIScaledImage(width, height, type);
        }
        return new HiDPIScaledImage(image, width, height, type);
    }
}
