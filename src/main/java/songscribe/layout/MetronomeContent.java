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
import songscribe.dom.CollisionRegion;
import songscribe.dom.ElementType;
import songscribe.dom.MetronomeAttachment;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoMarking;
import songscribe.error.RuntimeError;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * The positioned, measured content of a metronome marking — the single source of truth for
 * metronome typesetting. Layout builds it, {@link LayoutResult.DecorationLayout} carries it,
 * and the renderers draw its items without recomputing an advance, re-resolving a font or
 * deciding a position, so the measured box and the drawn ink cannot diverge.
 * <p>
 * The content is stated once for every depiction of a metronome marking: the per-note
 * {@code BeatChangeAttachment}, the per-note {@code TempoChangeAttachment} and the song-level
 * {@code SongTempoMark} at the first line's staff header.
 * <p>
 * A {@link TempoMarking.TextOnly} tempo omits the metronome glyph and the BPM, and draws its
 * description alone. It is not a hidden tempo: {@link #widthSs} is never 0, because that case
 * always carries text and the {@link TempoMarking.Metronome} case always begins with a glyph.
 * <p>
 * Every measurement here is in staff spaces and therefore zoom-invariant:
 * {@code ScaleContext.setPixelsPerStaffSpace} is never called in production, and zoom is
 * applied by {@code ViewScale} and the paint transform.
 *
 * @param items    the drawable items, left to right, positioned relative to the content's
 *                 top-left corner
 * @param widthSs  the total advance width of the content, in staff spaces
 * @param regions  one collision region per ink run, for vertical stacking
 */
public record MetronomeContent(
    List<Item> items, double widthSs, List<CollisionRegion> regions) {

    /** " = " in tempo markings and beat changes. */
    public static final String EQUALS_STR = " = ";

    public MetronomeContent {
        items = List.copyOf(items);
        regions = List.copyOf(regions);
    }

    /**
     * Returns the SMuFL metronome glyph for the given element type.
     */
    public static SMuFLGlyph metronomeGlyphFor(ElementType type) {
        return switch (type) {
            case SEMIBREVE -> SMuFLGlyph.MET_NOTE_WHOLE;
            case MINIM -> SMuFLGlyph.MET_NOTE_HALF_UP;
            case CROTCHET -> SMuFLGlyph.MET_NOTE_QUARTER_UP;
            case QUAVER -> SMuFLGlyph.MET_NOTE_8TH_UP;
            case SEMIQUAVER -> SMuFLGlyph.MET_NOTE_16TH_UP;
            case DEMI_SEMIQUAVER -> SMuFLGlyph.MET_NOTE_32ND_UP;
            // glyph absent from the font => missing resource, not an unmapped-type bug
            default -> throw RuntimeError.missingResource(
                "No metronome glyph for element type: " + type);
        };
    }

    /**
     * Returns the advance width in staff spaces of one augmentation dot step (gap or dot itself).
     * A dotted note spends two of these: one gap before the dot, one for the dot.
     */
    public static double dotAdvanceWidthSs() {
        return SMuFLMetadata.requireAdvanceWidth(SMuFLGlyph.MET_AUGMENTATION_DOT)
            * MetronomeAttachment.NOTE_SCALE;
    }

    /**
     * One drawable piece of a metronome marking, positioned relative to the content's
     * top-left corner. An item carries everything needed to draw it, so the renderer decides
     * no position of its own on either axis.
     */
    public sealed interface Item permits GlyphItem, TextItem {

        /** Offset from the content's left edge, in staff spaces. */
        double xSs();

        /**
         * Offset from the content's top edge down to this item's drawing origin (its
         * baseline), in staff spaces.
         */
        double baselineOffsetSs();
    }

    /**
     * A SMuFL metronome glyph drawn in the metronome note font.
     *
     * @param baselineOffsetSs an augmentation dot carries the same value as the note it
     *                         follows, because the dot sits on the note's drawing origin
     */
    public record GlyphItem(SMuFLGlyph glyph, double xSs, double baselineOffsetSs)
        implements Item {}

    /**
     * A run of text drawn in the resolved annotation font.
     *
     * @param scaledFont the annotation font already scaled for the staff-space transform, so
     *                   the renderer sets it verbatim rather than deriving it on every paint
     */
    public record TextItem(String text, Font scaledFont, double xSs, double baselineOffsetSs)
        implements Item {}

    /**
     * Builds the content for a beat change: the duration note, the "=", and the beat note.
     *
     * @param beatChange the beat change being depicted
     * @param font       the resolved annotation font, unscaled, in pixel units
     */
    public static MetronomeContent forBeatChange(BeatChange beatChange, Font font) {
        var builder = new Builder(font);
        builder.appendNote(beatChange.duration().getNote());
        builder.appendText(EQUALS_STR);
        builder.appendNote(beatChange.beat().getNote());

        return builder.build();
    }

    /**
     * Builds the content for a tempo marking: the metronome glyph, the "=", the BPM and the
     * description — or the description alone, according to the tempo's {@link TempoMarking}.
     *
     * @param tempo the tempo being depicted
     * @param font  the resolved annotation font, unscaled, in pixel units
     * @return the content, whose width is never zero — a metronome marking always begins with a
     *         glyph, and a text-only marking always carries text
     */
    public static MetronomeContent forTempo(Tempo tempo, Font font) {
        var builder = new Builder(font);

        switch (tempo.marking()) {
            case TempoMarking.Metronome metronome -> {
                builder.appendNote(tempo.tempoType().getNote());
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
     * Joins the BPM to the description, omitting the separator when there is no description —
     * a trailing space would otherwise widen the marking's hit box and its stacking
     * reservation by a space with no ink under it.
     */
    private static String bpmWithDescription(int visibleTempo, String description) {
        if (description.isEmpty()) {
            return String.valueOf(visibleTempo);
        }

        return visibleTempo + " " + description;
    }

    /**
     * Walks the advance sequence once, accumulating positioned items and one collision region
     * per ink run. Gap advances count toward the total width but belong to no region.
     */
    private static final class Builder {

        private final Font font;
        private final Font scaledFont;
        private final double textBaselineOffsetSs;
        private final double textAscentSs;
        private final double textDescentSs;
        private final List<Item> items = new ArrayList<>();
        private final List<CollisionRegion> regions = new ArrayList<>();
        private double cursorSs = 0;

        /**
         * A gap advance owed to whatever is appended next. An undotted note leaves one dot
         * step owed, which becomes the space before the text that follows it; a dotted note
         * has already spent that step as the space before its own dot, so it owes nothing.
         * Whatever is appended next pays the debt before positioning itself, and a debt still
         * outstanding when the content is built is simply dropped — which is what keeps the
         * total width from ever ending on empty space.
         */
        private double pendingGapSs = 0;

        private Builder(Font font) {
            this.font = font;
            scaledFont = ScaleContext.scaleFont(font);
            // Text sits on the note cap-height baseline, so a marking's silhouette does not
            // change shape when its BPM changes from 120 to 132.
            textBaselineOffsetSs = MetronomeAttachment.QUARTER_NOTE_HEIGHT_SS;
            // Ascent and descent come from the font, not from the characters, so they are
            // resolved once for every text item in this content.
            textAscentSs = ScaleContext.fontAscentSs(font).value();
            textDescentSs = ScaleContext.fontDescentSs(font).value();
        }

        private void appendNote(StaffElement note) {
            payPendingGap();

            var glyph = metronomeGlyphFor(note.getType());
            var baselineOffsetSs =
                -SMuFLMetadata.requireBBox(glyph).top() * MetronomeAttachment.NOTE_SCALE;
            var noteStartSs = cursorSs;
            var noteAdvanceSs =
                SMuFLMetadata.requireAdvanceWidth(glyph) * MetronomeAttachment.NOTE_SCALE;
            items.add(new GlyphItem(glyph, cursorSs, baselineOffsetSs));
            cursorSs += noteAdvanceSs;

            var dotAdvanceSs = dotAdvanceWidthSs();

            if (note.getDotCount() > 0) {
                cursorSs += dotAdvanceSs;
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

            var advanceSs = ScaleContext.textWidthSs(font, text).value();
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
