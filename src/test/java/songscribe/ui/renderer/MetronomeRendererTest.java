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

package songscribe.ui.renderer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.BeatChange;
import songscribe.dom.Duration;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.MetronomeContent;

/**
 * Tests for {@link MetronomeRenderer#drawContent}, which paints a metronome marking that
 * {@link MetronomeContent} already typeset.
 * <p>
 * The method's entire job is to replay each item's stored position and font faithfully, so these
 * tests assert exactly that — the string drawn, both coordinates, and the font in force — rather
 * than counting draw calls. Counting alone would stay green while the renderer drew the wrong
 * glyph, at the wrong height, in the wrong font.
 */
class MetronomeRendererTest extends UnitTest {

    private static final float COORDINATE_TOLERANCE = 1e-4f;

    /** One captured {@code drawString} call: what was drawn, where, and in which font. */
    private record DrawnString(String text, float xSs, float ySs, Font font) {}

    /** Builds the content for a beat change between the given note durations. */
    private static MetronomeContent beatChangeContent(Duration durationNote, Duration beatNote) {
        var font = DocumentFonts.defaultFonts().getFont(FontKey.ANNOTATION);
        return MetronomeContent.forBeatChange(new BeatChange(durationNote, beatNote), font);
    }

    /** Covers both the dotted and undotted left-note advance paths. */
    private static Stream<Arguments> beatChangeShapes() {
        return Stream.of(
            Arguments.of(Duration.CROTCHET, Duration.CROTCHET),
            Arguments.of(Duration.CROTCHET_DOTTED, Duration.CROTCHET)
        );
    }

    private static List<DrawnString> captureDrawnStrings(
        MetronomeContent content, double xSs, double ySs) {

        var g2Spy = spy(RenderContextTestHelper.realG2());
        var drawn = new ArrayList<DrawnString>();

        doAnswer(invocation -> {
            drawn.add(new DrawnString(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                g2Spy.getFont()));
            return null;
        }).when(g2Spy).drawString(anyString(), anyFloat(), anyFloat());

        MetronomeRenderer.drawContent(g2Spy, content, xSs, ySs, Color.BLACK);

        return drawn;
    }

    /**
     * The structural guarantee behind issue #735, now on both axes: every coordinate the renderer
     * hands to {@code drawString} is the item's own stored offset shifted by the content origin.
     * The renderer computes no position of its own, so the ink and the measured box cannot
     * diverge.
     */
    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("beatChangeShapes")
    void testDrawContentDrawsEveryItemAtItsOwnOffsetOnBothAxes(
        Duration durationNote, Duration beatNote) {

        var content = beatChangeContent(durationNote, beatNote);
        var xSs = 7.0;
        var ySs = -3.0;

        var drawn = captureDrawnStrings(content, xSs, ySs);

        assertThat(drawn).hasSize(content.items().size());

        for (var i = 0; i < drawn.size(); i++) {
            var item = content.items().get(i);

            assertThat(drawn.get(i).xSs())
                .as("item %d horizontal offset", i)
                .isCloseTo((float) (xSs + item.xSs()), within(COORDINATE_TOLERANCE));
            assertThat(drawn.get(i).ySs())
                .as("item %d baseline", i)
                .isCloseTo((float) (ySs + item.baselineOffsetSs()), within(COORDINATE_TOLERANCE));
        }
    }

    /**
     * A glyph item draws its own SMuFL codepoint and a text item its own string, in order. A
     * renderer that drew the right number of strings in the wrong order, or substituted one
     * glyph for another, would pass a draw-count check and fail this one.
     */
    @Test
    void testDrawContentDrawsEachItemsOwnStringInOrder() {
        var content = beatChangeContent(Duration.CROTCHET_DOTTED, Duration.CROTCHET);

        var drawn = captureDrawnStrings(content, 0.0, 0.0);
        var expectedTexts = content.items().stream()
            .map(item -> switch (item) {
                case MetronomeContent.GlyphItem glyphItem -> glyphItem.glyph().asString();
                case MetronomeContent.TextItem textItem -> textItem.text();
            })
            .toList();

        assertThat(drawn).extracting(DrawnString::text).isEqualTo(expectedTexts);
    }

    /**
     * Glyphs draw in the metronome note font; text draws in the font the content resolved and
     * pre-scaled. Drawing the "=" in the note font would render it as a wrong glyph entirely.
     */
    @Test
    void testDrawContentDrawsGlyphsAndTextInTheirOwnFonts() {
        var content = beatChangeContent(Duration.CROTCHET, Duration.CROTCHET);

        var drawn = captureDrawnStrings(content, 0.0, 0.0);

        for (var i = 0; i < drawn.size(); i++) {
            var expectedFont = switch (content.items().get(i)) {
                case MetronomeContent.GlyphItem _ -> MetronomeRenderer.TEMPO_NOTE_FONT;
                case MetronomeContent.TextItem textItem -> textItem.scaledFont();
            };

            assertThat(drawn.get(i).font())
                .as("item %d font", i)
                .isEqualTo(expectedFont);
        }
    }
}
