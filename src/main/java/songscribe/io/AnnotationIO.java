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
package songscribe.io;

import org.audiveris.proxymusic.AboveBelow;
import org.audiveris.proxymusic.LeftCenterRight;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Annotation;
import songscribe.dom.DocumentScale;

public final class AnnotationIO {

    // version 1.1
    static final String XML_ANNOTATION = "annotation";
    static final String XML_NAME = "name";
    static final String XML_ALIGNMENT = "alignment";
    private static final String XML_YPOS = "ypos";

    /**
     * File-format numbers: changing one changes what existing documents mean.
     */
    private static final float LEGACY_LEFT = 0.0f;
    private static final float LEGACY_CENTER = 0.5f;
    private static final float LEGACY_RIGHT = 1.0f;

    // version 2.1
    private static final String XML_USER_Y_OFFSET = "useryoffset";

    // version 2.5 (annotation-placement-refactor)
    private static final String XML_PLACEMENT = "placement";

    private AnnotationIO() {}

    /**
     * @return {@code null} when the text is not a number or names no alignment, which leaves
     *         {@link Annotation}'s default in place
     */
    private static @Nullable LeftCenterRight alignmentFor(String text) {
        float number;

        try {
            number = Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return null;
        }

        if (number == LEGACY_LEFT) {
            return LeftCenterRight.LEFT;
        }

        if (number == LEGACY_CENTER) {
            return LeftCenterRight.CENTER;
        }

        if (number == LEGACY_RIGHT) {
            return LeftCenterRight.RIGHT;
        }

        return null;
    }

    /**
     * Reads one {@code <annotation>} element of a legacy document.
     */
    public static class AnnotationReader {

        private static final Logger LOG = LoggerFactory.getLogger(AnnotationReader.class);

        // Staff-space coordinate of the legacy Annotation.ABOVE pixel constant
        private static final double LEGACY_ABOVE_SS = -2.0;
        private static final double LEGACY_ABOVE_PX = DocumentScale.PIXELS_PER_STAFF_SPACE * LEGACY_ABOVE_SS;

        private boolean inAnnotation = false;
        private String text = "";
        private @Nullable LeftCenterRight alignment = null;
        private @Nullable AboveBelow placement = null;
        private double userYOffsetSs = 0;

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(20);

        public void startElement11(String qName) {
            if (qName.equals(XML_ANNOTATION)) {
                inAnnotation = true;
                text = "";
                alignment = null;
                placement = null;
                userYOffsetSs = 0;
                lastTag = null;
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        @Nullable
        public ReadAnnotation endElement11(String qName) {
            if (qName.equals(XML_ANNOTATION)) {
                inAnnotation = false;
                return build();
            }

            var tag = lastTag;

            if (tag != null && inAnnotation && qName.equals(tag)) {
                var str = value.toString();

                switch (tag) {
                    case XML_NAME -> text = str;
                    case XML_ALIGNMENT -> {
                        alignment = alignmentFor(str);

                        if (alignment == null) {
                            LOG.warn("Corrupt document: malformed alignment: '{}', using default", str);
                        }
                    }
                    case XML_PLACEMENT -> {
                        try {
                            placement = AboveBelow.valueOf(str);
                        } catch (IllegalArgumentException e) {
                            LOG.warn("Corrupt document: malformed placement: '{}', using default", str);
                        }
                    }
                    case XML_YPOS -> {
                        // ypos is a legacy int pixel field. ypos <= 0 was above the staff, which is
                        // the default placement. ypos and LEGACY_ABOVE_PX are both pixels, and the
                        // pixel delta is deliberately stored into the staff-space userYOffsetSs:
                        // converting the units would shift legacy documents.
                        try {
                            var ypos = Integer.parseInt(str);

                            if (ypos > 0) {
                                userYOffsetSs += ypos - LEGACY_ABOVE_PX;
                            }
                        } catch (NumberFormatException e) {
                            LOG.warn("Corrupt document: malformed ypos: '{}', using default", str);
                        }
                    }
                    case XML_USER_Y_OFFSET -> {
                        try {
                            userYOffsetSs = Double.parseDouble(str);
                        } catch (NumberFormatException e) {
                            LOG.warn("Corrupt document: malformed userYOffset: '{}', using default", str);
                        }
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
            return null;
        }

        /**
         * @return {@code null} when it has no text; such an annotation is not attached
         */
        @Nullable
        private ReadAnnotation build() {
            if (text.isBlank()) {
                LOG.warn("Corrupt document: annotation with no text, dropping it");
                return null;
            }

            // Only what the document actually carried is applied; the rest takes Annotation's own
            // defaults rather than a second copy of them here.
            var readAlignment = alignment == null ? Annotation.DEFAULT_ALIGNMENT : alignment;
            var readPlacement = placement == null ? Annotation.DEFAULT_PLACEMENT : placement;

            return new ReadAnnotation(
                new Annotation(text, readAlignment, readPlacement), userYOffsetSs);
        }

        public void characters(char[] ch, int start, int length) {
            if (lastTag != null) {
                value.append(ch, start, length);
            }
        }
    }
}
