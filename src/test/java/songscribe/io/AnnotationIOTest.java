/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Component;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import songscribe.UnitTest;
import songscribe.dom.Annotation;

@SuppressWarnings("PackageVisibleInnerClass")
class AnnotationIOTest extends UnitTest {

    // -- Helpers --

    private static String writeAnnotation(Annotation annotation) {
        var writer = new StringWriter();
        AnnotationIO.writeAnnotation(annotation, new PrintWriter(writer), 0);
        return writer.toString();
    }

    private static Annotation roundTripAnnotation(Annotation original) {
        var xml = writeAnnotation(original);
        var reader = new AnnotationIO.AnnotationReader();

        for (var line : xml.lines().toList()) {
            var trimmed = line.strip();

            if (trimmed.isEmpty()) {
                continue;
            }

            var tag = extractTag(trimmed);

            if (trimmed.startsWith("</")) {
                var result = reader.endElement11(tag);

                if (result != null) {
                    return result;
                }
            } else if (trimmed.startsWith("<") && !trimmed.endsWith("/>")) {
                reader.startElement11(tag);

                // Extract text content for elements like <name>text</name>
                var content = extractContent(trimmed);

                if (content != null) {
                    reader.characters(content.toCharArray(), 0, content.length());
                    reader.endElement11(tag);
                }
            }
        }

        throw new AssertionError("AnnotationReader did not return an Annotation");
    }

    /** Returns the tag name from a trimmed XML line. */
    private static String extractTag(String line) {
        var start = line.startsWith("</") ? 2 : 1;
        var end = line.indexOf('>');
        return end > start ? line.substring(start, end) : line.substring(start);
    }

    /**
     * Returns the text content of a same-line element like {@code <name>Hello</name>},
     * or null when no inline content is present (begin/end tags on separate lines).
     */
    private static @Nullable String extractContent(String line) {
        var closeOpen = line.indexOf("</");

        if (closeOpen < 0) {
            return null;
        }

        var tagEnd = line.indexOf('>');
        return tagEnd >= 0 && closeOpen > tagEnd ? line.substring(tagEnd + 1, closeOpen) : null;
    }

    // -- Test classes --

    @Nested
    class AnnotationReaderNullGuard {

        // Row 53: endElement11 before startElement11 must not NPE
        @Test
        void testEndElementBeforeStartElementDoesNotThrow() {
            var reader = new AnnotationIO.AnnotationReader();
            assertThatCode(() -> reader.endElement11(AnnotationIO.XML_ANNOTATION))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class WriteAnnotationSerialization {

        private static final int SAMPLE_Y_POS_PX = 42;
        private static final float CENTER_ALIGNMENT = Component.CENTER_ALIGNMENT;

        // Row 50: writeAnnotation emits <name>, <alignment>, <ypos>
        @Test
        void testWritesNameAlignmentAndYPos() {
            var annotation = new Annotation("Hello");
            annotation.setXAlignment(CENTER_ALIGNMENT);
            annotation.setYPosPx(SAMPLE_Y_POS_PX);

            var output = writeAnnotation(annotation);

            assertThat(output)
                .contains("<" + AnnotationIO.XML_NAME + ">Hello</" + AnnotationIO.XML_NAME + ">")
                .contains("<" + AnnotationIO.XML_ALIGNMENT + ">" + CENTER_ALIGNMENT + "</" + AnnotationIO.XML_ALIGNMENT + ">")
                .contains("<" + AnnotationIO.XML_YPOS + ">" + SAMPLE_Y_POS_PX + "</" + AnnotationIO.XML_YPOS + ">");
        }

        // Row 51 (branch 1): userYOffsetSs == 0 → <useryoffset> element absent
        @Test
        void testOmitsUserYOffsetWhenZero() {
            var annotation = new Annotation("Test");
            // default userYOffsetSs is 0; no explicit set needed

            var output = writeAnnotation(annotation);

            assertThat(output).doesNotContain(AnnotationIO.XML_USER_Y_OFFSET);
        }

        // Row 51 (branch 2): userYOffsetSs != 0 → <useryoffset> element present
        @Test
        void testEmitsUserYOffsetWhenNonZero() {
            var annotation = new Annotation("Test");
            var nonZeroOffset = 1.5;
            annotation.setUserYOffsetSs(nonZeroOffset);

            var output = writeAnnotation(annotation);

            assertThat(output).contains(
                "<" + AnnotationIO.XML_USER_Y_OFFSET + ">" + nonZeroOffset + "</" + AnnotationIO.XML_USER_Y_OFFSET + ">"
            );
        }
    }

    @Nested
    class RoundTripPerField {

        private static final float LEFT_ALIGNMENT = Component.LEFT_ALIGNMENT;
        private static final float CENTER_ALIGNMENT = Component.CENTER_ALIGNMENT;
        private static final float RIGHT_ALIGNMENT = Component.RIGHT_ALIGNMENT;

        // Row 52 + Row 54: write → AnnotationReader round-trip preserves each field
        @ParameterizedTest(name = "name=\"{0}\", alignment={1}, yPosPx={2}, userYOffsetSs={3}")
        @CsvSource({
            "Hello,  0.0,  10, 0.0",
            "World,  0.5,  -5, 0.0",
            "Foo,    1.0,  99, 0.0",
            "Bar,    0.0,  42, 1.5",
            "Baz,    0.5,   0, -2.25",
        })
        void testRoundTripPreservesAllFields(
            String text,
            float alignment,
            int yPosPx,
            double userYOffsetSs
        ) {
            var original = new Annotation(text);
            original.setXAlignment(alignment);
            original.setYPosPx(yPosPx);
            original.setUserYOffsetSs(userYOffsetSs);

            var restored = roundTripAnnotation(original);

            assertThat(restored.getAnnotation()).isEqualTo(text);
            assertThat(restored.getXAlignment()).isEqualTo(alignment);
            assertThat(restored.getYPosPx()).isEqualTo(yPosPx);
            assertThat(restored.getUserYOffsetSs()).isEqualTo(userYOffsetSs);
        }
    }

    /**
     * Tests for the NFE guards added to {@link AnnotationIO.AnnotationReader#endElement11} in Phase 3 (I-2).
     * Non-numeric alignment, ypos, and userYOffset must be soft-fails: load succeeds, WARN logged,
     * and the field is left at its initialised default.
     */
    @Nested
    class MalformedNumericFields {

        private static final float DEFAULT_ALIGNMENT = Component.LEFT_ALIGNMENT;
        private static final int DEFAULT_YPOS = Annotation.ABOVE;
        private static final double DEFAULT_USER_Y_OFFSET = 0.0;

        // I-2: non-numeric alignment → WARN logged
        @Test
        void testMalformedAlignmentLogsWarn() {
            var appender = attachAnnotationLogAppender();
            try {
                readAnnotationWithMalformedField(AnnotationIO.XML_ALIGNMENT, "notafloat");
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("alignment"));
            } finally {
                detachAnnotationLogAppender(appender);
            }
        }

        // I-2: non-numeric alignment → default value retained (also confirms load succeeds)
        @Test
        void testMalformedAlignmentRetainsDefault() {
            var annotation = readAnnotationWithMalformedField(
                AnnotationIO.XML_ALIGNMENT, "notafloat"
            );
            assertThat(annotation).isNotNull();

            //noinspection ConstantValue -- NullAway guard
            if (annotation == null) {
                return;
            }

            assertThat(annotation.getXAlignment()).isEqualTo(DEFAULT_ALIGNMENT);
        }

        // I-2: non-numeric ypos → WARN logged
        @Test
        void testMalformedYposLogsWarn() {
            var appender = attachAnnotationLogAppender();
            try {
                readAnnotationWithMalformedField(AnnotationIO.XML_YPOS, "notanint");
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("ypos"));
            } finally {
                detachAnnotationLogAppender(appender);
            }
        }

        // I-2: non-numeric ypos → default value retained (also confirms load succeeds)
        @Test
        void testMalformedYposRetainsDefault() {
            var annotation = readAnnotationWithMalformedField(
                AnnotationIO.XML_YPOS, "notanint"
            );
            assertThat(annotation).isNotNull();

            //noinspection ConstantValue -- NullAway guard
            if (annotation == null) {
                return;
            }

            assertThat(annotation.getYPosPx()).isEqualTo(DEFAULT_YPOS);
        }

        // I-2: non-numeric userYOffset → WARN logged
        @Test
        void testMalformedUserYOffsetLogsWarn() {
            var appender = attachAnnotationLogAppender();
            try {
                readAnnotationWithMalformedField(AnnotationIO.XML_USER_Y_OFFSET, "notadouble");
                assertThat(appender.list)
                    .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("userYOffset"));
            } finally {
                detachAnnotationLogAppender(appender);
            }
        }

        // I-2: non-numeric userYOffset → default value retained (also confirms load succeeds)
        @Test
        void testMalformedUserYOffsetRetainsDefault() {
            var annotation = readAnnotationWithMalformedField(
                AnnotationIO.XML_USER_Y_OFFSET, "notadouble"
            );
            assertThat(annotation).isNotNull();

            //noinspection ConstantValue -- NullAway guard
            if (annotation == null) {
                return;
            }

            assertThat(annotation.getUserYOffsetSs()).isEqualTo(DEFAULT_USER_Y_OFFSET);
        }

        /**
         * Drives an {@link AnnotationIO.AnnotationReader} with a valid annotation that
         * has one field ({@code tag}) set to a malformed (non-numeric) string.
         */
        @Nullable
        private static Annotation readAnnotationWithMalformedField(String tag, String badValue) {
            var reader = new AnnotationIO.AnnotationReader();
            reader.startElement11(AnnotationIO.XML_ANNOTATION);

            // Feed the annotation name (required to produce a non-null result)
            reader.startElement11(AnnotationIO.XML_NAME);
            reader.characters("Test".toCharArray(), 0, "Test".length());
            reader.endElement11(AnnotationIO.XML_NAME);

            // Feed the malformed field
            reader.startElement11(tag);
            reader.characters(badValue.toCharArray(), 0, badValue.length());
            reader.endElement11(tag);

            return reader.endElement11(AnnotationIO.XML_ANNOTATION);
        }

        private static ListAppender<ILoggingEvent> attachAnnotationLogAppender() {
            var logger = (Logger) LoggerFactory.getLogger(AnnotationIO.AnnotationReader.class);
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);
            return appender;
        }

        private static void detachAnnotationLogAppender(ListAppender<ILoggingEvent> appender) {
            var logger = (Logger) LoggerFactory.getLogger(AnnotationIO.AnnotationReader.class);
            logger.detachAppender(appender);
        }
    }
}
