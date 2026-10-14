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

package songscribe.layout;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import songscribe.dom.BeatChange;
import songscribe.dom.BeatUnit;
import songscribe.dom.CollisionRegion;
import songscribe.dom.DocumentScale;
import songscribe.dom.MetronomeAttachment;
import songscribe.dom.NoteValue;
import songscribe.dom.Tempo;
import songscribe.dom.TempoMarking;
import songscribe.font.TextMeasurement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * The positioned, measured content of a metronome marking, the single source of truth for every
 * depiction of one. Renderers draw its items without recomputing an advance or position, so the
 * measured box and the drawn ink cannot diverge. {@link #widthSs} is never 0.
 *
 * @param items   the drawable items, left to right, positioned relative to the content's
 *                top-left corner
 * @param regions one collision region per ink run, for vertical stacking
 */
public record MetronomeContent(
    List<TypesetItem> items, double widthSs, List<CollisionRegion> regions)
    implements DecorationContent {

    public static final String EQUALS_STR = " = ";

    public MetronomeContent {
        items = List.copyOf(items);
        regions = List.copyOf(regions);
    }

    public static SMuFLGlyph metronomeGlyphFor(NoteValue value) {
        return switch (value) {
            case WHOLE -> SMuFLGlyph.MET_NOTE_WHOLE;
            case HALF -> SMuFLGlyph.MET_NOTE_HALF_UP;
            case QUARTER -> SMuFLGlyph.MET_NOTE_QUARTER_UP;
            case EIGHTH -> SMuFLGlyph.MET_NOTE_8TH_UP;
            case SIXTEENTH -> SMuFLGlyph.MET_NOTE_16TH_UP;
            case THIRTY_SECOND -> SMuFLGlyph.MET_NOTE_32ND_UP;
        };
    }

    public static double dotAdvanceWidthSs() {
        return SMuFLMetadata.advanceWidthSs(SMuFLGlyph.MET_AUGMENTATION_DOT)
            * MetronomeAttachment.NOTE_SCALE;
    }

    /**
     * @param font the resolved annotation font, unscaled, in pixel units
     */
    public static MetronomeContent forBeatChange(BeatChange beatChange, Font font) {
        var builder = new Builder(font);
        builder.appendNote(beatChange.duration());
        builder.appendText(EQUALS_STR);
        builder.appendNote(beatChange.beat());

        return builder.build();
    }

    /**
     * @param font the resolved annotation font, unscaled, in pixel units
     */
    public static MetronomeContent forTempo(Tempo tempo, Font font) {
        var builder = new Builder(font);

        switch (tempo.marking()) {
            case TempoMarking.Metronome metronome -> {
                builder.appendNote(tempo.tempoType());
                // The "=" and the BPM/description are separate items because they are drawn as
                // two separate strings at two positions.
                builder.appendText(EQUALS_STR);
                builder.appendText(
                    bpmWithDescription(tempo.visibleTempo(), metronome.description()));
            }
            case TempoMarking.TextOnly textOnly -> builder.appendText(textOnly.description());
        }

        return builder.build();
    }

    /**
     * The separator is omitted when there is no description, so no trailing space widens the hit
     * box and stacking reservation.
     */
    private static String bpmWithDescription(int visibleTempo, String description) {
        if (description.isEmpty()) {
            return String.valueOf(visibleTempo);
        }

        return visibleTempo + " " + description;
    }

    /**
     * Gap advances count toward the total width but belong to no region.
     */
    private static final class Builder {

        private final Font font;
        private final Font scaledFont;
        private final double textBaselineOffsetSs;
        private final double textAscentSs;
        private final double textDescentSs;
        private final List<TypesetItem> items = new ArrayList<>();
        private final List<CollisionRegion> regions = new ArrayList<>();
        private double cursorSs = 0;

        /**
         * A gap advance owed to whatever is appended next. A debt still outstanding when the
         * content is built is dropped, so the total width never ends on empty space.
         */
        private double pendingGapSs = 0;

        private Builder(Font font) {
            this.font = font;
            scaledFont = DocumentScale.fontSizedInSs(font);
            // Text sits on the note cap-height baseline, so a marking's silhouette does not
            // change shape when its BPM changes from 120 to 132.
            textBaselineOffsetSs = MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS;
            // Ascent and descent come from the font, not from the characters, so they are
            // resolved once for every text item in this content.
            textAscentSs = TextMeasurement.fontAscentSs(font).value();
            textDescentSs = TextMeasurement.fontDescentSs(font).value();
        }

        private void appendNote(BeatUnit unit) {
            payPendingGap();

            var glyph = metronomeGlyphFor(unit.noteValue());
            var baselineOffsetSs =
                -SMuFLMetadata.bboxSs(glyph).topSs() * MetronomeAttachment.NOTE_SCALE;
            var noteStartSs = cursorSs;
            var noteAdvanceSs =
                SMuFLMetadata.advanceWidthSs(glyph) * MetronomeAttachment.NOTE_SCALE;
            items.add(new GlyphItem(glyph, cursorSs, baselineOffsetSs));
            cursorSs += noteAdvanceSs;

            var dotAdvanceSs = dotAdvanceWidthSs();

            if (unit.dotted()) {
                cursorSs += dotAdvanceSs;
                // The dot sits on the note's own drawing origin, so it repeats the note's
                // baseline offset rather than deriving one from the dot glyph's bounding box.
                items.add(
                    new GlyphItem(SMuFLGlyph.MET_AUGMENTATION_DOT, cursorSs, baselineOffsetSs));
                cursorSs += dotAdvanceSs;
            } else {
                pendingGapSs = dotAdvanceSs;
            }

            // The note and its dot are one continuous run of ink, so they share one region.
            regions.add(new CollisionRegion(
                noteStartSs, 0, cursorSs - noteStartSs,
                MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS));
        }

        private void appendText(String text) {
            if (text.isEmpty()) {
                return;
            }

            payPendingGap();

            var advanceSs = TextMeasurement.textWidthSs(font, text).value();
            items.add(new TextItem(text, scaledFont, cursorSs, textBaselineOffsetSs));
            // The text hangs from its baseline, so its region starts an ascent above that
            // baseline and reaches a descent below it.
            regions.add(new CollisionRegion(
                cursorSs,
                textBaselineOffsetSs - textAscentSs,
                advanceSs,
                textAscentSs + textDescentSs));
            cursorSs += advanceSs;
        }

        private void payPendingGap() {
            cursorSs += pendingGapSs;
            pendingGapSs = 0;
        }

        private MetronomeContent build() {
            return new MetronomeContent(items, cursorSs, regions);
        }
    }
}
