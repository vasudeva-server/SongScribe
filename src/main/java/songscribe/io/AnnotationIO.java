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
import songscribe.dom.ScaleContext;

public final class AnnotationIO {

    // version 1.1
    static final String XML_ANNOTATION = "annotation";
    static final String XML_NAME = "name";
    static final String XML_ALIGNMENT = "alignment";
    private static final String XML_YPOS = "ypos";

    /**
     * The number a legacy document's {@code <alignment>} tag holds, for each alignment.
     *
     * <p>These numbers belong to the file format. The documents that carry them are already on
     * disk, so nothing here may change what those files mean.
     */
    private static final float LEGACY_LEFT = 0.0f;
    private static final float LEGACY_CENTER = 0.5f;
    private static final float LEGACY_RIGHT = 1.0f;

    // version 2.1 (Phase 11)
    private static final String XML_USER_Y_OFFSET = "useryoffset";

    // version 2.5 (annotation-placement-refactor)
    private static final String XML_PLACEMENT = "placement";

    private AnnotationIO() {}

    /**
     * @param text the text of a legacy document's {@code <alignment>} tag
     * @return the alignment that text names, or {@code null} when the text is not a number, or
     *         is a number no alignment uses. A null answer is what leaves
     *         {@link Annotation}'s own default in place.
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
     *
     * <p>The parts arrive one tag at a time and are accumulated here rather than written into an
     * {@link Annotation} as they come, because an annotation may not exist with blank text and the
     * text is not known until its own tag closes. Building at the end is also what lets a
     * text-less annotation be <em>dropped</em>: {@link #endElement11} answers null for it, and the
     * caller adds an attachment only for a non-null answer.
     */
    public static class AnnotationReader {

        private static final Logger LOG = LoggerFactory.getLogger(AnnotationReader.class);

        // Staff-space coordinate of the legacy Annotation.ABOVE pixel constant (before Phase 1 refactor)
        private static final double LEGACY_ABOVE_SS = -2.0;
        private static final double LEGACY_ABOVE_PX = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE * LEGACY_ABOVE_SS;

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
                        // Legacy int pixel field — fold the old migrator arithmetic at read time.
                        // ypos > 0 was below staff: absorb the offset delta into userYOffsetSs.
                        // ypos <= 0 was above staff: placement already defaults to ABOVE.
                        // Note: ypos and LEGACY_ABOVE_PX are both pixels; the pixel delta is
                        // intentionally stored into the staff-space userYOffsetSs field to
                        // reproduce the old migrator's behavior exactly (changing the units here
                        // would shift legacy documents that previously ran through that migrator).
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
         * @return the annotation the accumulated parts describe together with where the document
         *         placed it, or {@code null} when it has no text — an annotation with no text has
         *         nothing to draw, so it is not attached rather than imported
         * @log warn if the accumulated text is blank
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
