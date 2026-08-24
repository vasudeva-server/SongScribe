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

import java.util.stream.Stream;

import org.audiveris.proxymusic.LeftCenterRight;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Annotation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The legacy {@code <annotation>} reader: which stored number names which alignment, and what it
 * does with an element it cannot turn into an annotation.
 *
 * <p>The numbers below are written out rather than read from {@code AnnotationIO}'s own
 * constants, and that is the point of the test. Documents holding these numbers are already
 * on disk, so the mapping cannot be changed — a test that asked the production code which
 * number means which alignment would agree with a transposed pair and report a pass.
 */
class AnnotationIOTest extends UnitTest {

    private static final String ANNOTATION_TEXT = "Fine";

    /** One legacy {@code <alignment>} value and the alignment it names. */
    private record LegacyAlignmentCase(String description, String number, LeftCenterRight expected) {

        @Override
        public String toString() {
            return description;
        }
    }

    /** One legacy {@code <alignment>} value that names no alignment. */
    private record UnusableAlignmentCase(String description, String text) {

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("legacyAlignmentCases")
    void testEachLegacyNumberReadsAsTheAlignmentItNames(LegacyAlignmentCase testCase) {
        assertThat(readAligned(testCase.number()).alignment()).isEqualTo(testCase.expected());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unusableAlignmentCases")
    void testAnAlignmentNamingNoAlignmentLeavesTheDefault(UnusableAlignmentCase testCase) {
        assertThat(readAligned(testCase.text()).alignment()).isEqualTo(Annotation.DEFAULT_ALIGNMENT);
    }

    @Test
    void testAnAnnotationWithNoTextIsDroppedRatherThanImported() {
        assertThat(readAnnotation("", null)).isNull();
    }

    static Stream<LegacyAlignmentCase> legacyAlignmentCases() {
        return Stream.of(
            new LegacyAlignmentCase("0.0 is left", "0.0", LeftCenterRight.LEFT),
            new LegacyAlignmentCase("0.5 is center", "0.5", LeftCenterRight.CENTER),
            new LegacyAlignmentCase("1.0 is right", "1.0", LeftCenterRight.RIGHT)
        );
    }

    static Stream<UnusableAlignmentCase> unusableAlignmentCases() {
        return Stream.of(
            new UnusableAlignmentCase("a number no alignment uses", "0.3"),
            new UnusableAlignmentCase("text that is not a number", "left"),
            new UnusableAlignmentCase("an empty tag", "")
        );
    }

    /**
     * @param alignment the value of the {@code alignment} tag
     * @return the annotation a legacy element with that alignment and ordinary text reads as
     */
    private static Annotation readAligned(String alignment) {
        var annotation = readAnnotation(ANNOTATION_TEXT, alignment);
        assertThat(annotation).isNotNull();

        return annotation;
    }

    /**
     * Feeds one complete {@code <annotation>} element, tag by tag, the way the SAX handlers in
     * {@link SongIO} and {@link StaffElementIO} do.
     *
     * @param text the value of the {@code name} tag
     * @param alignment the value of the {@code alignment} tag, or {@code null} to write no
     *        {@code alignment} tag at all
     * @return the annotation the closing tag answers, or {@code null} when it answers none
     */
    private static @Nullable Annotation readAnnotation(String text, @Nullable String alignment) {
        var read = readAnnotationAndOffset(text, alignment);

        return read == null ? null : read.annotation();
    }

    /**
     * Feeds the same element as {@link #readAnnotation} and answers what the reader answers whole.
     *
     * @param text the value of the {@code name} tag
     * @param alignment the value of the {@code alignment} tag, or {@code null} to write no
     *        {@code alignment} tag at all
     * @return the annotation and its offset, or {@code null} when the closing tag answers none
     */
    private static @Nullable ReadAnnotation readAnnotationAndOffset(
            String text, @Nullable String alignment) {
        var reader = new AnnotationIO.AnnotationReader();
        reader.startElement11(AnnotationIO.XML_ANNOTATION);

        writeValue(reader, AnnotationIO.XML_NAME, text);

        if (alignment != null) {
            writeValue(reader, AnnotationIO.XML_ALIGNMENT, alignment);
        }

        return reader.endElement11(AnnotationIO.XML_ANNOTATION);
    }

    /** Feeds one child tag of the annotation element: open, characters, close. */
    private static void writeValue(AnnotationIO.AnnotationReader reader, String tag, String value) {
        reader.startElement11(tag);
        reader.characters(value.toCharArray(), 0, value.length());
        reader.endElement11(tag);
    }
}
