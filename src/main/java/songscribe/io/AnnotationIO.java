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

import java.io.PrintWriter;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Annotation;
import songscribe.dom.ScaleContext;

public final class AnnotationIO {

    // version 1.1
    public static final String XML_ANNOTATION = "annotation";
    public static final String XML_NAME = "name";
    public static final String XML_ALIGNMENT = "alignment";
    public static final String XML_YPOS = "ypos";

    // version 2.1 (Phase 11)
    public static final String XML_USER_Y_OFFSET = "useryoffset";

    // version 2.5 (annotation-placement-refactor)
    public static final String XML_PLACEMENT = "placement";

    private AnnotationIO() {}

    public static void writeAnnotation(
        Annotation a,
        PrintWriter pw,
        int indent
    ) {
        XML.setIndent(indent);
        XML.writeBeginTag(pw, XML_ANNOTATION);
        XML.setIndent(indent + 2);
        XML.writeValue(pw, XML_NAME, a.getAnnotation());
        XML.writeValue(pw, XML_ALIGNMENT, Float.toString(a.getXAlignment()));
        XML.writeValue(pw, XML_PLACEMENT, a.getPlacement().name());

        // Write userYOffset if non-zero (Phase 11)
        if (a.getUserYOffsetSs() != 0) {
            XML.writeValue(pw, XML_USER_Y_OFFSET, Double.toString(a.getUserYOffsetSs()));
        }

        XML.setIndent(indent);
        XML.writeEndTag(pw, XML_ANNOTATION);
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
        private @Nullable Float xAlignment = null;
        private Annotation.@Nullable Placement placement = null;
        private double userYOffsetSs = 0;

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(20);

        public void startElement11(String qName) {
            if (qName.equals(XML_ANNOTATION)) {
                inAnnotation = true;
                text = "";
                xAlignment = null;
                placement = null;
                userYOffsetSs = 0;
                lastTag = null;
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        @Nullable
        public Annotation endElement11(String qName) {
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
                        try {
                            xAlignment = Float.parseFloat(str);
                        } catch (NumberFormatException e) {
                            LOG.warn("Corrupt document: malformed alignment: '{}', using default", str);
                        }
                    }
                    case XML_PLACEMENT -> {
                        try {
                            placement = Annotation.Placement.valueOf(str);
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
         * @return the annotation the accumulated parts describe, or {@code null} when it has no
         *         text — a blank annotation is dropped rather than imported, since it could draw
         *         nothing and {@link Annotation} does not permit one
         */
        @Nullable
        private Annotation build() {
            if (text.isBlank()) {
                LOG.warn("Corrupt document: annotation with no text, dropping it");
                return null;
            }

            var annotation = new Annotation(text);
            var alignment = xAlignment;
            var readPlacement = placement;

            // Only what the document actually carried is applied; the rest keeps Annotation's own
            // defaults rather than a second copy of them here.
            if (alignment != null) {
                annotation.setXAlignment(alignment);
            }

            if (readPlacement != null) {
                annotation.setPlacement(readPlacement);
            }

            annotation.setUserYOffsetSs(userYOffsetSs);
            return annotation;
        }

        public void characters(char[] ch, int start, int length) {
            if (lastTag != null) {
                value.append(ch, start, length);
            }
        }
    }
}
