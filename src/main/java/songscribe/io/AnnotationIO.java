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

    public static class AnnotationReader {

        private static final Logger LOG = LoggerFactory.getLogger(AnnotationReader.class);

        // Staff-space coordinate of the legacy Annotation.ABOVE pixel constant (before Phase 1 refactor)
        private static final double LEGACY_ABOVE_SS = -2.0;
        private static final double LEGACY_ABOVE_PX = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE * LEGACY_ABOVE_SS;

        @Nullable
        private Annotation annotation = null;

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(20);

        public void startElement11(String qName) {
            if (qName.equals(XML_ANNOTATION)) {
                annotation = new Annotation("");
                lastTag = null;
            } else {
                lastTag = qName;
            }

            value.delete(0, value.length());
        }

        @Nullable
        public Annotation endElement11(String qName) {
            if (qName.equals(XML_ANNOTATION)) {
                return annotation;
            }

            //noinspection PointlessNullCheck -- required for NullAway
            if (lastTag != null && annotation != null && qName.equals(lastTag)) {
                var str = value.toString();

                switch (lastTag) {
                    case XML_NAME -> annotation.setAnnotation(str);
                    case XML_ALIGNMENT -> {
                        try {
                            annotation.setXAlignment(Float.parseFloat(str));
                        } catch (NumberFormatException e) {
                            LOG.warn("Corrupt document: malformed alignment: '{}', using default", str);
                        }
                    }
                    case XML_PLACEMENT -> {
                        try {
                            annotation.setPlacement(Annotation.Placement.valueOf(str));
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
                                annotation.setUserYOffsetSs(annotation.getUserYOffsetSs() + ypos - LEGACY_ABOVE_PX);
                            }
                        } catch (NumberFormatException e) {
                            LOG.warn("Corrupt document: malformed ypos: '{}', using default", str);
                        }
                    }
                    case XML_USER_Y_OFFSET -> {
                        try {
                            annotation.setUserYOffsetSs(Double.parseDouble(str));
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

        public void characters(char[] ch, int start, int length) {
            if (lastTag != null) {
                value.append(ch, start, length);
            }
        }
    }
}
