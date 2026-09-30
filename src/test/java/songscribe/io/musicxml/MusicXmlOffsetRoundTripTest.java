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

import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.DocumentScale;
import songscribe.dom.Dots;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.NotatableValue;
import songscribe.dom.NoteValue;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.dom.TupletGrade;
import songscribe.dom.TupletRatio;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.buildSong;
import static songscribe.dom.SpanFactory.endingOver;
import static songscribe.dom.SpanFactory.hairpinOver;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.X_OFFSET_PX;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

/**
 * How a hand-placed offset survives a write and a read, through the ss &rarr; tenths &rarr; ss
 * conversion: a note's horizontal nudge in {@code <relative-x>}, and each span's offsets, exactly
 * and without rounding.
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

    /** The span fixture's line: three notes, a closing repeat, and two notes after it. */
    private static final int FIRST_NOTE = 0;
    private static final int SECOND_NOTE = 1;
    private static final int THIRD_NOTE = 2;
    private static final int NOTE_AFTER_REPEAT = 5;

    private static final int TRIPLET_NORMAL_NOTES = 2;
    private static final TupletRatio TWO_CROTCHETS =
        new TupletRatio(TRIPLET_NORMAL_NOTES, new NotatableValue(NoteValue.QUARTER, Dots.NONE));

    /**
     * One span on the fixture line, and one of its hand-placed offsets.
     *
     * @param description the offset, as the test's display name
     * @param place       builds the span over the fixture line's elements, not yet added
     * @param offset      reads the offset
     * @param setOffset   sets it
     */
    private record SpanOffsetCase(
        String description,
        Function<Line, Span<?>> place,
        ToDoubleFunction<Span<?>> offset,
        ObjDoubleConsumer<Span<?>> setOffset
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("spanOffsetCases")
    void testASpansFractionalOffsetSurvivesAWriteAndARead(SpanOffsetCase testCase) throws Exception {
        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(crotchet());
            line.addElement(crotchet());
            line.addElement(repeatRight());
            line.addElement(crotchet());
            line.addElement(crotchet());
        });
        var fixtureLine = song.getLine(0);

        // An ending is judged against the song's repeat sections, so the span is placed once the
        // line is in the song.
        var placed = testCase.place().apply(fixtureLine);

        testCase.setOffset().accept(placed, FRACTIONAL_OFFSET_SS);
        song.withoutMutationTracking(() -> placed.addTo(fixtureLine));

        var kind = placed.getClass();

        var read = roundTrip(song).getLine(0).spanRegistry().findSpans(kind);

        assertThat(read).singleElement()
            .satisfies(span -> assertThat(testCase.offset().applyAsDouble(span)).isEqualTo(FRACTIONAL_OFFSET_SS));
    }

    static Stream<SpanOffsetCase> spanOffsetCases() {
        Function<Line, Span<?>> crescendo =
            line -> hairpinOver(line, Hairpin.Kind.CRESCENDO, new ElementRange(FIRST_NOTE, THIRD_NOTE));

        return Stream.of(
            new SpanOffsetCase("a hairpin's vertical offset",
                crescendo, Span::getUserYOffsetSs, Span::setUserYOffsetSs),
            new SpanOffsetCase("a hairpin's start shift",
                crescendo, span -> ((Hairpin) span).getX1ShiftSs(), (span, shift) -> ((Hairpin) span).setX1ShiftSs(shift)),
            new SpanOffsetCase("a hairpin's end shift",
                crescendo, span -> ((Hairpin) span).getX2ShiftSs(), (span, shift) -> ((Hairpin) span).setX2ShiftSs(shift)),
            new SpanOffsetCase("a trill's vertical offset",
                line -> new Trill(line.getElement(FIRST_NOTE), line.getElement(SECOND_NOTE)),
                Span::getUserYOffsetSs, Span::setUserYOffsetSs),
            new SpanOffsetCase("a tuplet's vertical offset",
                line -> new Tuplet(
                    line.getElement(FIRST_NOTE), line.getElement(THIRD_NOTE), TupletGrade.TRIPLET, TWO_CROTCHETS),
                Span::getUserYOffsetSs, Span::setUserYOffsetSs),
            new SpanOffsetCase("an ending's vertical offset",
                line -> endingOver(line, new ElementRange(SECOND_NOTE, NOTE_AFTER_REPEAT)),
                Span::getUserYOffsetSs, Span::setUserYOffsetSs)
        );
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
