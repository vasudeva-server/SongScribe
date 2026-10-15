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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;
import songscribe.dom.Direction;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.NoteElement;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;

/**
 * The fundamental horizontal spacing unit in the engraving system.
 * <p>
 * Construction is two-phase: {@link ElementColumnBuilder} creates the column and sets the syllable
 * gaps and {@link #noteheadWidthSs}, then {@link HorizontalSpacingCalculator} sets {@link #xSs}
 * once positions are computed. Every other field is immutable.
 */
public final class ElementColumn {

    private final StaffElement element;
    private final Extents extents;
    // The element's lyric for the script this column was built for — the song's active script, and
    // the one its width measures. Null when the element carries no lyric in that script. The
    // syllable text is read off the lyric rather than stored alongside it, so the text and the
    // script it came from cannot drift apart.
    private final @Nullable MeasuredLyric lyric;
    // The beam this column belongs to, or null when not beamed. Kept so the spacing calculator
    // can keep adjacent beam groups separate rather than merging them.
    private final @Nullable Beam beam;
    // Width of the syllable's first grapheme cluster — what a grace note centers on its notehead.
    // Measured by ElementColumnBuilder; falls back to the whole syllable width.
    private double syllableFirstGraphemeWidthSs;
    // Minimum required gap between this column's right edge and the next column's syllable
    // left edge. Always set by ElementColumnBuilder: lyric space width for non-hyphenated or
    // lyric-less columns, hyphen cell width for hyphenated ones.
    private double minGapToNextSyllableSs;
    // Hard collision floor for the gap to the next syllable — the closest two syllables may ever
    // come, honoured by the spring strut so lyrics never touch. Always set by ElementColumnBuilder:
    // one lyric space width for non-hyphenated or lyric-less columns (including melisma carriers),
    // the bare hyphen glyph width for hyphenated ones. Distinct from minGapToNextSyllableSs, which
    // is the wider comfortable/preferred gap the rest aims for.
    private double minCollisionGapToNextSyllableSs;
    private double xSs = 0;
    // Notehead-only width (grace-reduced or standard head) in staff spaces, excluding stem, flag,
    // and augmentation dots. Used for lyric centering so neither a flag nor a dot shifts the syllable
    // off the notehead. Defaults to the augmentation-excluded right extent — correct for non-notes,
    // and for synthetic test columns that carry no flag — but ElementColumnBuilder overrides it with
    // the true notehead width for note columns (whose right extent may be flag-inflated).
    private double noteheadWidthSs;

    /**
     * Use {@link ElementColumnBuilder} rather than calling this constructor directly.
     *
     * @param lyric null when the element carries none in the script being laid out
     * @param beam  null when the element is unbeamed
     */
    public ElementColumn(
        StaffElement element,
        Extents extents,
        @Nullable MeasuredLyric lyric,
        @Nullable Beam beam) {
        this.element = element;
        this.extents = extents;
        noteheadWidthSs = extents.rightExcludingAugmentationSs();
        this.lyric = lyric;
        syllableFirstGraphemeWidthSs = getSyllableWidthSs();
        this.beam = beam;
    }

    /**
     * The geometry extents of a column's element, all relative to the element head's left edge
     * (the glyph origin) horizontally and to the element's staff position vertically.
     *
     * @param stemTopSs    top of the stem when it points up, or the element head top when it has none
     * @param stemBottomSs bottom of the stem when it points down, or the element head bottom when it
     *                     has none
     */
    public record Extents(
        double leftSs,
        double rightSs,
        double rightExcludingAugmentationSs,
        double stemTopSs,
        double stemBottomSs) {}

    /**
     * @param widthSs 0 when the text is empty
     */
    public record MeasuredLyric(Lyric lyric, double widthSs) {}

    public StaffElement getElement() {
        return element;
    }

    public boolean isRest() {
        return element.getType().isRest();
    }

    public boolean isBarline() {
        return element.getType().isBarLine();
    }

    public boolean isGraceNote() {
        return element.getType().isGraceNote();
    }

    public boolean isKeyChange() {
        return element.getType().isKeyChange();
    }

    /**
     * The <em>drawn</em> stem direction ({@link StemGeometry#effectiveDirection}), so a grace note
     * reports {@code UP}, the only direction it ever renders.
     *
     * @return {@code null} when this column's element renders no stem
     */
    public @Nullable Direction stemDirection() {
        return element.getType().isNoteWithStem() && element instanceof NoteElement note
            ? StemGeometry.effectiveDirection(note)
            : null;
    }

    public double getLeftExtentSs() {
        return extents.leftSs();
    }

    public double getRightExtentSs() {
        return extents.rightSs();
    }

    public double getRightExtentExcludingAugmentationSs() {
        return extents.rightExcludingAugmentationSs();
    }

    /**
     * Callers judging whether a gap needs widening <em>for lyrics</em> must subtract this from the
     * comfortable rest first, or a stem flip would silently change the lyric lift the line gets.
     */
    public double getFlagExtentSs() {
        return getRightExtentExcludingAugmentationSs() - noteheadWidthSs;
    }

    /**
     * How far this column's ink reaches right of its origin as met by a neighbour whose
     * left-facing band is {@code [neighbourTopYSs, neighbourBottomYSs]}: {@link #getRightExtentSs()}
     * except that a flag hanging entirely clear of that band is not charged. Once augmentation is
     * present the flag no longer sets the right edge, so the full extent stands.
     *
     * @param neighbourTopYSs    top (higher, numerically smaller) of the neighbour's vertical span
     * @param neighbourBottomYSs bottom (lower, numerically larger) of the neighbour's vertical span
     */
    public double getRightExtentFacingSs(double neighbourTopYSs, double neighbourBottomYSs) {
        var direction = stemDirection();
        var rightExtentSs = getRightExtentSs();

        if (getFlagExtentSs() <= 0
            || rightExtentSs > getRightExtentExcludingAugmentationSs()
            || !(element instanceof NoteElement note)
            || direction == null) {
            return rightExtentSs;
        }

        var flagBoundsSs = StemGeometry.flagBBoxLocalSs(note, direction, beaming());

        if (flagBoundsSs == null) {
            return rightExtentSs;
        }

        // The bbox is note-local; getPositionSs() lifts it onto the same absolute axis as the span.
        var positionSs = getPositionSs();
        var flagTopYSs = positionSs + flagBoundsSs.getMinY();
        var flagBottomYSs = positionSs + flagBoundsSs.getMaxY();

        if (flagBottomYSs <= neighbourTopYSs || flagTopYSs >= neighbourBottomYSs) {
            return noteheadWidthSs;
        }

        return rightExtentSs;
    }

    public double getWidthSs() {
        return Math.abs(getLeftExtentSs()) + getRightExtentSs();
    }

    /**
     * @return meaningful only after the spacing calculator has assigned this column its X
     */
    public double getLeftEdgeXSs() {
        return xSs + getLeftExtentSs();
    }

    /**
     * @return meaningful only after the spacing calculator has assigned this column its X
     */
    public double getRightEdgeXSs() {
        return xSs + getRightExtentSs();
    }

    /** Lyric centering uses this, so neither a flag nor a dot shifts the syllable off the notehead. */
    public double getNoteheadWidthSs() {
        return noteheadWidthSs;
    }

    public void setNoteheadWidthSs(double noteheadWidthSs) {
        this.noteheadWidthSs = noteheadWidthSs;
    }

    /**
     * Only valid after X position has been set by the spacing calculator. Agrees with
     * {@link NoteGeometry#getNoteheadCenterXSs} for every type.
     */
    public double getNoteheadCenterXSs() {
        return xSs + noteheadWidthSs / 2.0;
    }

    /**
     * Anchors melisma extenders, so a terminating flag or dot never shifts where the extender
     * ends. Only valid after X position has been set by the spacing calculator.
     */
    public double getNoteheadRightEdgeXSs() {
        return xSs + noteheadWidthSs;
    }

    public double getStemTopSs() {
        return extents.stemTopSs();
    }

    public double getStemBottomSs() {
        return extents.stemBottomSs();
    }

    /** Screen-down: a more negative value is higher on the staff. The absolute vertical anchor. */
    public double getPositionSs() {
        return ElementSeat.seatSs(element);
    }

    public double getAbsoluteTopYSs() {
        return getPositionSs() + getStemTopSs();
    }

    /**
     * The top of this column's <em>left-facing</em> vertical span: the ink a neighbour approaching
     * from the left can meet, not the whole span. An up-stem hangs off the notehead's right edge,
     * so the span starts at the notehead top in every case; a down-stem is covered by
     * {@link #getLeftFacingBottomYSs()}.
     */
    public double getLeftFacingTopYSs() {
        return getPositionSs() + element.getType().getNoteheadTopOffsetSs();
    }

    /** A down-stem sits on the notehead's left edge, so the span runs to the stem tip. */
    public double getLeftFacingBottomYSs() {
        return getAbsoluteBottomYSs();
    }

    public double getAbsoluteBottomYSs() {
        return getPositionSs() + getStemBottomSs();
    }

    /**
     * @return null if no syllable
     */
    public @Nullable String getSyllable() {
        return lyric != null ? lyric.lyric().text() : null;
    }

    public boolean hasSyllable() {
        var syllable = getSyllable();
        return syllable != null && !syllable.isEmpty();
    }

    /**
     * @return 0 if no syllable
     */
    public double getSyllableWidthSs() {
        return lyric != null ? lyric.widthSs() : 0;
    }

    /**
     * Defaults to the whole syllable width until {@link ElementColumnBuilder} measures it.
     *
     * @return 0 if no syllable
     */
    public double getSyllableFirstGraphemeWidthSs() {
        return syllableFirstGraphemeWidthSs;
    }

    void setSyllableFirstGraphemeWidthSs(double syllableFirstGraphemeWidthSs) {
        this.syllableFirstGraphemeWidthSs = syllableFirstGraphemeWidthSs;
    }

    /**
     * Null if the element carries none in the script this column was built for. Every later pass
     * reads the lyric from the column, so it stays on the script the width was measured for.
     */
    public @Nullable Lyric getLyric() {
        return lyric != null ? lyric.lyric() : null;
    }

    public double getMinGapToNextSyllableSs() {
        return minGapToNextSyllableSs;
    }

    void setMinGapToNextSyllableSs(double minGapToNextSyllableSs) {
        this.minGapToNextSyllableSs = minGapToNextSyllableSs;
    }

    /**
     * The hard collision floor for the gap to the next syllable. Kept distinct from
     * {@link #getMinGapToNextSyllableSs()} though currently equal: this one floors the spring
     * strut so lyrics never touch, that one feeds the rest the lyric lift aims at.
     */
    public double getMinCollisionGapToNextSyllableSs() {
        return minCollisionGapToNextSyllableSs;
    }

    void setMinCollisionGapToNextSyllableSs(double minCollisionGapToNextSyllableSs) {
        this.minCollisionGapToNextSyllableSs = minCollisionGapToNextSyllableSs;
    }

    public Beaming beaming() {
        return Beaming.of(beam);
    }

    /** Two unbeamed columns do not count as sharing a group. */
    public boolean sharesBeamGroupWith(ElementColumn other) {
        return beam != null && beam == other.beam;
    }

    public boolean hasGlissando() {
        return element instanceof NoteElement note && note.hasGlissando();
    }

    public double getXSs() {
        return xSs;
    }

    void setXSs(double xSs) {
        this.xSs = xSs;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder(200);
        sb.append("ElementColumn{");
        sb.append("element=").append(element.getType());

        sb.append(", extent=[").append(getLeftExtentSs()).append(", ").append(getRightExtentSs()).append(']');

        if (hasSyllable()) {
            sb.append(", syllable='").append(getSyllable()).append('\'');
            sb.append(", syllableWidth=").append(getSyllableWidthSs());
        }

        if (beaming() == Beaming.BEAMED) {
            sb.append(", beamed");
        }

        sb.append(", x=").append(xSs);
        sb.append('}');

        return sb.toString();
    }

    /**
     * @return {@code null} if the span cannot be placed
     */
    @Nullable
    public static SpanColumns resolveSpan(
        Span<?> span, Map<StaffElement, ElementColumn> columnsByElement) {
        return resolveEndpoints(span.getAnchorElement(), span.getEndElement(), columnsByElement);
    }

    private static @Nullable SpanColumns resolveEndpoints(
        StaffElement anchor, StaffElement end, Map<StaffElement, ElementColumn> columnsByElement) {
        var anchorColumn = columnsByElement.get(anchor);
        var endColumn = columnsByElement.get(end);

        if (anchorColumn == null || endColumn == null) {
            return null;
        }

        return new SpanColumns(anchor, end, anchorColumn, endColumn);
    }

    public record SpanColumns(
        StaffElement anchor, StaffElement end, ElementColumn anchorColumn, ElementColumn endColumn) {}

    /**
     * A tie is laid out once in each line that holds one of its notes, and each line draws the
     * part next to the note it holds.
     *
     * @return the whole tie when {@code placement} has no open edge, the half next to the one
     *     note the line holds otherwise, or {@code null} when a note the line holds has no column
     */
    @Nullable
    public static TieColumns resolveTie(
        Line.TiePlacement placement, Map<StaffElement, ElementColumn> columnsByElement) {
        var tie = placement.tie();
        var openEdge = placement.openEdge();

        if (openEdge == null) {
            var columns = resolveEndpoints(tie.anchor(), tie.end(), columnsByElement);

            return columns == null ? null : new TieColumns.Whole(columns);
        }

        var attached = switch (openEdge) {
            case START -> tie.end();
            case END -> tie.anchor();
        };
        var attachedColumn = columnsByElement.get(attached);

        return attachedColumn == null ? null : new TieColumns.Half(attached, attachedColumn, openEdge);
    }

    public sealed interface TieColumns {

        record Whole(SpanColumns columns) implements TieColumns {}

        /** There is deliberately no column for the far note: it belongs to another line's layout. */
        record Half(StaffElement attached, ElementColumn attachedColumn, Line.Edge openEdge)
            implements TieColumns {}
    }
}
