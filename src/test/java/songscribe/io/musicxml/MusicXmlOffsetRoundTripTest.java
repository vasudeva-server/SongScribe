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
package songscribe.io.musicxml;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.DocumentScale;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NoteValue;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.beamOver;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.SpanFactory.trillOver;
import static songscribe.dom.SpanFactory.tripletOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.X_OFFSET_PX;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

/**
 * How a hand-placed offset survives a write and a read, through the ss &rarr; tenths &rarr; ss
 * conversion: a note's horizontal nudge in {@code <relative-x>}, and each span kind's horizontal and
 * vertical offsets. The writer rounds a position to two decimals of a tenth,
 * {@link java.math.RoundingMode#HALF_UP}, so an offset that is a whole number of those survives
 * unchanged and any other comes back rounded to the nearest one. An axis the file carries no
 * attribute for reads back as zero.
 */
class MusicXmlOffsetRoundTripTest extends UnitTest {

    @Test
    void testAnElementsXOffsetSurvivesAWriteAndAReadThroughTenths() throws Exception {
        var xOffsetSs = DocumentScale.pxToSs(X_OFFSET_PX);
        var song = songWithXOffset(xOffsetSs);

        var restored = roundTrip(song).getLine(0).getElement(0);

        assertThat(restored.getUserXOffsetSs()).isEqualTo(xOffsetSs);
    }

    /**
     * A document without the {@code <supports element="note" attribute="relative-x">}
     * entry was written when {@code relative-x} carried the layout position, so the
     * reader must leave the offset at zero rather than take that position for a nudge.
     */
    @Test
    void testRelativeXIsIgnoredWhenTheDocumentDoesNotVouchForIt() throws Exception {
        var song = songWithXOffset(DocumentScale.pxToSs(X_OFFSET_PX));
        var xml = writeToString(song);

        var withoutDeclaration = NOTE_RELATIVE_X_SUPPORTS.matcher(xml).replaceAll("");
        assertThat(withoutDeclaration).isNotEqualTo(xml);

        var restored = parse(withoutDeclaration).getLine(0).getElement(0);

        assertThat(restored.getUserXOffsetSs()).isZero();
    }

    /** The emitted {@code <supports>} entry for note {@code relative-x}, whatever its attribute order. */
    private static final Pattern NOTE_RELATIVE_X_SUPPORTS = Pattern.compile(
        "<" + MusicXmlTags.SUPPORTS + "[^>]*" + MusicXmlTags.ATTR_RELATIVE_X + "[^>]*/>");

    /**
     * A hand-placed offset that is a fraction of a staff space, so the case fails if either side
     * of the tenths conversion rounds it.
     */
    private static final double FRACTIONAL_OFFSET_SS = 0.25;

    /** An offset the writer's precision cannot hold: 1.234 tenths, which it keeps as 1.23. */
    private static final double UNREPRESENTABLE_OFFSET_SS = 0.1234;
    private static final double ROUNDED_OFFSET_SS = 0.123;
    private static final double OFFSET_TOLERANCE_SS = 1e-9;
    private static final double NO_OFFSET_SS = 0.0;

    /** The span fixture's line: three notes, a closing repeat, two notes after it, and two quavers. */
    private static final int FIRST_NOTE = 0;
    private static final int SECOND_NOTE = 1;
    private static final int THIRD_NOTE = 2;
    private static final int NOTE_AFTER_REPEAT = 5;
    private static final int FIRST_QUAVER = 6;
    private static final int SECOND_QUAVER = 7;

    /** One of the two directions a span's hand-placed offset moves it. */
    private enum Axis {
        HORIZONTAL(Span::getUserXOffsetSs, Span::setUserXOffsetSs),
        VERTICAL(Span::getUserYOffsetSs, Span::setUserYOffsetSs);

        private final ToDoubleFunction<Span<?>> offset;
        private final ObjDoubleConsumer<Span<?>> setOffset;

        Axis(ToDoubleFunction<Span<?>> offset, ObjDoubleConsumer<Span<?>> setOffset) {
            this.offset = offset;
            this.setOffset = setOffset;
        }
    }

    /**
     * One span kind's offset on one axis, and what the file carries of it.
     *
     * @param description the offset, as the test's display name
     * @param kind        the span kind
     * @param place       builds the span over the fixture line's elements, not yet added
     * @param axis        the axis the offset is on
     * @param readBackSs  the offset the reader restores from {@link #FRACTIONAL_OFFSET_SS}: the
     *                    offset itself when the file has an attribute for it, zero when it has none
     */
    private record SpanOffsetCase(
        String description,
        Class<? extends Span<?>> kind,
        Function<Line, Span<?>> place,
        Axis axis,
        double readBackSs
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("spanOffsetCases")
    void testASpansFractionalOffsetSurvivesAWriteAndARead(SpanOffsetCase testCase) throws Exception {
        var read = readBackOffset(testCase.kind(), testCase.place(), testCase.axis(), FRACTIONAL_OFFSET_SS);

        assertThat(read).isEqualTo(testCase.readBackSs());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Axis.class)
    void testAnOffsetFinerThanTheWritersPrecisionComesBackRoundedToIt(Axis axis) throws Exception {
        var read = readBackOffset(Hairpin.class, MusicXmlOffsetRoundTripTest::crescendo, axis, UNREPRESENTABLE_OFFSET_SS);

        assertThat(read).isCloseTo(ROUNDED_OFFSET_SS, within(OFFSET_TOLERANCE_SS));
    }

    private static Span<?> crescendo(Line line) {
        return hairpinOver(line, Hairpin.Kind.CRESCENDO, new ElementRange(FIRST_NOTE, THIRD_NOTE));
    }

    @Test
    void testTheCasesCoverEverySpanKindOnBothAxes() {
        var cases = spanOffsetCases().toList();
        var covered = cases.stream().<Class<?>>map(SpanOffsetCase::kind).distinct().toList();

        assertThat(covered).containsExactlyInAnyOrder(Span.class.getPermittedSubclasses());
        assertThat(cases).hasSize(covered.size() * Axis.values().length);
    }

    static Stream<SpanOffsetCase> spanOffsetCases() {
        return Stream.of(
            rowsFor("a hairpin", Hairpin.class, MusicXmlOffsetRoundTripTest::crescendo, Axis.HORIZONTAL, Axis.VERTICAL),
            rowsFor("a trill", Trill.class,
                line -> trillOver(line, new ElementRange(FIRST_NOTE, SECOND_NOTE)), Axis.VERTICAL),
            rowsFor("a tuplet", Tuplet.class,
                line -> tripletOver(line, new ElementRange(FIRST_NOTE, THIRD_NOTE), NoteValue.QUARTER), Axis.VERTICAL),
            rowsFor("an ending", Ending.class,
                line -> endingOver(line, new ElementRange(SECOND_NOTE, NOTE_AFTER_REPEAT)), Axis.VERTICAL),
            rowsFor("a beam", Beam.class,
                line -> beamOver(line, new ElementRange(FIRST_QUAVER, SECOND_QUAVER)))
        ).flatMap(rows -> rows);
    }

    /**
     * A row for each axis of {@code kind}: the offset survives on the axes in {@code carried} and
     * reads back as zero on the others.
     */
    private static Stream<SpanOffsetCase> rowsFor(
        String kindName, Class<? extends Span<?>> kind, Function<Line, Span<?>> place, Axis... carried
    ) {
        var carriedAxes = List.of(carried);

        return Stream.of(Axis.values()).map(axis -> new SpanOffsetCase(
            kindName + "'s " + axis.name().toLowerCase(Locale.ROOT) + " offset",
            kind, place, axis, carriedAxes.contains(axis) ? FRACTIONAL_OFFSET_SS : NO_OFFSET_SS));
    }

    /** The fixture line's elements, in the order {@link #FIRST_NOTE} and the indices after it name. */
    private static Song fixtureSong() {
        return buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(crotchet());
            line.addElement(crotchet());
            line.addElement(repeatRight());
            line.addElement(crotchet());
            line.addElement(crotchet());
            line.addElement(quaver());
            line.addElement(quaver());
        });
    }

    /**
     * Sets {@code offsetSs} on {@code axis} of the span {@code place} builds over the fixture line,
     * writes and reads the song, and returns what the one span of {@code kind} reads back.
     */
    private static double readBackOffset(
        Class<? extends Span<?>> kind, Function<Line, Span<?>> place, Axis axis, double offsetSs
    ) throws Exception {
        var song = fixtureSong();

        // An ending is judged against the song's repeat sections, so the span is placed once the
        // line is in the song.
        var placed = place.apply(song.getLine(0));

        axis.setOffset.accept(placed, offsetSs);
        song.withoutMutationTracking(() -> placed.addTo());

        var read = roundTrip(song).getLine(0).spanRegistry().findSpans(kind);

        assertThat(read).hasSize(1);

        return axis.offset.applyAsDouble(read.getFirst());
    }

    /** A one-line song whose only note carries {@code xOffsetSs} as its hand-placed X nudge. */
    private static Song songWithXOffset(double xOffsetSs) {
        return buildSong(line -> {
            var note = crotchet();
            note.setUserXOffsetSs(xOffsetSs);
            line.addElement(note);
        });
    }
}
