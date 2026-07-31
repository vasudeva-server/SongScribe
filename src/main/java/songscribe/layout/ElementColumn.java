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

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Lyric;
import songscribe.dom.RangeElement;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;

/**
 * The fundamental horizontal spacing unit in the engraving system.
 * <p>
 * An ElementColumn represents a vertical slice of the staff containing:
 * <ul>
 *   <li>A primary element (note, rest, or barline)</li>
 *   <li>Optional grace notes (which borrow space from the left)</li>
 *   <li>Horizontal extents (left edge including accidentals/grace notes; right edge including augmentation for minimum spacing, excluding augmentation for comfortable spacing)</li>
 *   <li>Stem information (top/bottom for beam group coordination)</li>
 *   <li>Associated lyric syllable (which drives horizontal spacing per Gould/Ross)</li>
 *   <li>Beam membership flag (for internal spacing coordination)</li>
 * </ul>
 * <p>
 * Construction is two-phase: {@link ElementColumnBuilder} creates the column and immediately sets
 * {@link #minGapToNextSyllableSs}, {@link #minCollisionGapToNextSyllableSs},
 * {@link #noteheadWidthSs} (note columns only) and {@link #beamGroupId}, then
 * {@link HorizontalSpacingCalculator} sets {@link #xSs} once positions are computed.
 * Every other field is immutable. Use {@link ElementColumnBuilder} to create instances.
 */
public final class ElementColumn {

    /** Beam-group id for a column that is not part of any beam group. */
    public static final int NO_BEAM_GROUP = -1;

    private final StaffElement element;
    private final List<StaffElement> graceNotes;
    private final double leftExtentSs;
    private final double rightExtentSs;
    private final double rightExtentExcludingAugmentationSs;
    private final double stemTopSs;
    private final double stemBottomSs;
    // The element's lyric for the verse this column was built for — the song's active verse, and
    // the one syllableWidthSs measures. Null when the element carries no lyric in that verse.
    // The syllable text is read off this rather than stored alongside it, so the text and the
    // verse it came from cannot drift apart.
    private final @Nullable Lyric lyric;
    private final double syllableWidthSs;
    private final boolean beamed;
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
    // Identifies which beam group this column belongs to (the beam's anchor element index),
    // or NO_BEAM_GROUP when not beamed. Set by ElementColumnBuilder so the spacing calculator
    // can keep adjacent beam groups separate rather than merging them.
    private int beamGroupId = NO_BEAM_GROUP;
    // Computed X position of element head left edge (set by HorizontalSpacingCalculator)
    private double xSs = 0;
    // Notehead-only width (grace-reduced or standard head) in staff spaces, excluding stem, flag,
    // and augmentation dots. Used for lyric centering so neither a flag nor a dot shifts the syllable
    // off the notehead. Defaults to the augmentation-excluded right extent — correct for non-notes,
    // and for synthetic test columns that carry no flag — but ElementColumnBuilder overrides it with
    // the true notehead width for note columns (whose right extent may be flag-inflated).
    private double noteheadWidthSs;

    /**
     * Creates a new ElementColumn.
     * <p>
     * Use {@link ElementColumnBuilder} rather than calling this constructor directly.
     *
     * @param element                    The primary element (note, rest, barline, etc.)
     * @param graceNotes                 Grace notes anchored to this element (empty list if none)
     * @param leftExtentSs               Left extent relative to element head left edge; 0.0 without accidental, negative with one
     * @param rightExtentSs                    Right extent relative to element head left edge; equals element head width, plus augmentation if any
     * @param rightExtentExcludingAugmentationSs Right extent excluding augmentation (dots and fall); used for comfortable spacing
     * @param stemTopSs                  Top of stem (if stem up), or element head top if no stem
     * @param stemBottomSs               Bottom of stem (if stem down), or element head bottom if no stem
     * @param lyric                      The element's lyric for the verse being laid out (null if none)
     * @param syllableWidthSs            Measured width of syllable text in staff spaces (0 if no syllable)
     * @param beamed                     Whether this element is part of a beam group
     */
    public ElementColumn(
        StaffElement element,
        List<StaffElement> graceNotes,
        double leftExtentSs,
        double rightExtentSs,
        double rightExtentExcludingAugmentationSs,
        double stemTopSs,
        double stemBottomSs,
        @Nullable Lyric lyric,
        double syllableWidthSs,
        boolean beamed) {
        this.element = element;
        this.graceNotes = List.copyOf(graceNotes);
        this.leftExtentSs = leftExtentSs;
        this.rightExtentSs = rightExtentSs;
        this.rightExtentExcludingAugmentationSs = rightExtentExcludingAugmentationSs;
        noteheadWidthSs = rightExtentExcludingAugmentationSs;
        this.stemTopSs = stemTopSs;
        this.stemBottomSs = stemBottomSs;
        this.lyric = lyric;
        this.syllableWidthSs = syllableWidthSs;
        syllableFirstGraphemeWidthSs = syllableWidthSs;
        this.beamed = beamed;
    }

    /**
     * Creates a new ElementColumn where the right extent excluding augmentation equals the right extent.
     * <p>
     * Package-private so it is reachable only from tests that do not care about augmentation-driven
     * spacing. Production callers must use the full constructor and pass the correct
     * {@code rightExtentExcludingAugmentationSs}.
     */
    ElementColumn(
        StaffElement element,
        List<StaffElement> graceNotes,
        double leftExtentSs,
        double rightExtentSs,
        double stemTopSs,
        double stemBottomSs,
        @Nullable Lyric lyric,
        double syllableWidthSs,
        boolean beamed) {
        this(element, graceNotes, leftExtentSs, rightExtentSs, rightExtentSs,
            stemTopSs, stemBottomSs, lyric, syllableWidthSs, beamed);
    }

    // ==========================================================================
    // Primary Element
    // ==========================================================================

    /**
     * Returns the primary element (note, rest, barline, etc.) for this column.
     */
    public StaffElement getElement() {
        return element;
    }

    /**
     * Returns whether this column represents a rest.
     */
    public boolean isRest() {
        return element.getType().isRest();
    }

    /**
     * Returns whether this column represents a barline.
     */
    public boolean isBarline() {
        return element.getType().isBarLine();
    }

    /**
     * Returns whether this column represents a grace note.
     */
    public boolean isGraceNote() {
        return element.getType().isGraceNote();
    }

    /**
     * Returns whether this column's element renders a stem (a pitched or grace note shorter than
     * a whole note).
     */
    public boolean hasStem() {
        return element.getType().isNoteWithStem();
    }

    /**
     * Returns the <em>drawn</em> stem direction of this column's element
     * ({@link NoteGeometry#effectiveDirection}), so a grace note reports {@code UP} — the only
     * direction it ever renders — rather than whatever direction it happens to store. Only
     * meaningful when {@link #hasStem()} is {@code true}: for rests, whole notes, and barlines this
     * returns whatever direction the underlying element happens to hold (default {@code DOWN}),
     * which callers must not treat as a real stem direction.
     */
    public StaffElement.Direction getDirection() {
        return NoteGeometry.effectiveDirection(element);
    }

    // ==========================================================================
    // Grace Notes
    // ==========================================================================

    /**
     * Returns the grace notes anchored to this column.
     * Grace notes borrow space from the main element's left side.
     *
     * @return Unmodifiable list of grace notes (empty if none)
     */
    public List<StaffElement> getGraceNotes() {
        return graceNotes;
    }

    /**
     * Returns whether this column has grace notes.
     */
    public boolean hasGraceNotes() {
        return !graceNotes.isEmpty();
    }

    // ==========================================================================
    // Horizontal Extents
    // ==========================================================================

    /**
     * Returns the left extent relative to the element head left edge (glyph origin).
     * 0.0 with no accidental; negative when an accidental is present (extends further left).
     */
    public double getLeftExtentSs() {
        return leftExtentSs;
    }

    /**
     * Returns the right extent relative to the element head left edge (glyph origin).
     * Equal to the element head width with no dots or fall; larger when dots or a fall are present.
     */
    public double getRightExtentSs() {
        return rightExtentSs;
    }

    /**
     * Returns the right extent excluding augmentation dots and fall.
     * Used for comfortable (default) spacing so dots and a fall do not push the next element
     * unless the minimum gap would otherwise be violated.
     */
    public double getRightExtentExcludingAugmentationSs() {
        return rightExtentExcludingAugmentationSs;
    }

    /**
     * Returns how much of {@link #getRightExtentExcludingAugmentationSs()} is owed to an unbeamed
     * flag rather than the notehead itself — the difference between the two, since the flag is the
     * only thing that widens the augmentation-excluded right extent past {@link #getNoteheadWidthSs()}.
     * Zero for beamed notes, notes with no flag, and non-note columns.
     * <p>
     * A flag's footprint sits well above or below the staff and has no bearing on lyric clearance
     * (which measures from the notehead, see {@link #getNoteheadWidthSs()}), so callers that judge
     * whether a gap needs widening <em>for lyrics</em> must subtract this out of the comfortable rest
     * first — otherwise a stem flip, which changes only the flag's side and footprint, would silently
     * change how much lyric-driven lift the rest of the line receives (refs #629).
     */
    public double getFlagExtentSs() {
        return rightExtentExcludingAugmentationSs - noteheadWidthSs;
    }

    /**
     * Returns how far this column's ink reaches right of its origin <em>as met by a neighbour whose
     * left-facing band is</em> {@code [neighbourTopYSs, neighbourBottomYSs]} (absolute layout Y,
     * screen-down Ss — see {@link #getLeftFacingTopYSs()}). This is {@link #getRightExtentSs()},
     * except that a flag hanging entirely clear of that band is not charged.
     *
     * <p>{@link #getRightExtentSs()} is a bounding box: it collapses a two-level ink profile — the
     * notehead at the note's own height, the flag up at the stem tip — into the wider of the two, at
     * every height. That is the right answer for a neighbour standing beside the flag, and the wrong
     * one for a neighbour four steps below it, which is pushed away for a collision that cannot
     * happen. The flag is the only glyph that can widen a note column past its notehead
     * ({@link #getFlagExtentSs()}), so discounting it is the whole of the correction.
     *
     * <p>Once augmentation is present the flag no longer sets the right edge — dots stack to the
     * right of it ({@link NoteGeometry#firstDotXSs}) and a fall reaches past it — so there is nothing
     * to discount and the full extent stands.
     *
     * @param neighbourTopYSs    top (higher, numerically smaller) of the neighbour's vertical span
     * @param neighbourBottomYSs bottom (lower, numerically larger) of the neighbour's vertical span
     */
    public double getRightExtentFacingSs(double neighbourTopYSs, double neighbourBottomYSs) {
        if (getFlagExtentSs() <= 0 || rightExtentSs > rightExtentExcludingAugmentationSs) {
            return rightExtentSs;
        }

        var flagBoundsSs = NoteGeometry.flagBBoxLocalSs(element.getType(), getDirection(), beamed);

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

    /**
     * Returns the total width of this column (leftExtent + rightExtent).
     */
    public double getWidthSs() {
        return Math.abs(leftExtentSs) + rightExtentSs;
    }

    /**
     * Returns the absolute left edge X position.
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getLeftEdgeXSs() {
        return xSs + leftExtentSs;
    }

    /**
     * Returns the absolute right edge X position.
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getRightEdgeXSs() {
        return xSs + rightExtentSs;
    }

    /**
     * Returns the notehead's own width in staff spaces — the reduced notehead for grace notes, the
     * standard notehead for other notes — excluding the stem, flag, and augmentation dots. Lyric
     * centering uses this so neither a flag nor a dot shifts the syllable off the notehead (the
     * Gould/Ross rule, which already excluded dots, extended to the flag). Set by
     * {@link ElementColumnBuilder} for note columns; otherwise the augmentation-excluded right extent.
     */
    public double getNoteheadWidthSs() {
        return noteheadWidthSs;
    }

    /**
     * Sets the notehead-only width. Called by {@link ElementColumnBuilder} for note columns so lyric
     * centering uses the head width rather than the flag-inflated right extent.
     */
    public void setNoteheadWidthSs(double noteheadWidthSs) {
        this.noteheadWidthSs = noteheadWidthSs;
    }

    /**
     * Returns the absolute X of the notehead center, excluding the flag and augmentation dots.
     * Used to horizontally anchor lyrics so neither a flag nor a dot shifts the lyric position.
     * Only valid after X position has been set by the spacing calculator.
     * <p>
     * Agrees with {@link NoteGeometry#getNoteheadCenterXSs} for every type now
     * that both derive from the same per-type width, {@link ElementType#getElementWidthSs()}.
     * Previously this one used the black-notehead constant, so the two drifted apart on any type
     * whose head is not a black notehead — a whole note most visibly (refs #694).
     */
    public double getNoteheadCenterXSs() {
        return xSs + getNoteheadWidthSs() / 2.0;
    }

    /**
     * Returns the absolute X of the notehead's right edge, excluding the stem, flag, and
     * augmentation dots. Used to anchor melisma extenders so a terminating flag or dot never
     * shifts where the extender ends (see {@link LyricLayoutBuilder#MIN_MELISMA_LENGTH_SS}).
     * Only valid after X position has been set by the spacing calculator.
     */
    public double getNoteheadRightEdgeXSs() {
        return xSs + getNoteheadWidthSs();
    }

    // ==========================================================================
    // Stem Information
    // ==========================================================================

    /**
     * Returns the Y position of the stem top (for stem-up elements or beaming).
     * For elements without stems, returns the element head top.
     */
    public double getStemTopSs() {
        return stemTopSs;
    }

    /**
     * Returns the Y position of the stem bottom (for stem-down elements or beaming).
     * For elements without stems, returns the element head bottom.
     */
    public double getStemBottomSs() {
        return stemBottomSs;
    }

    /**
     * Returns this column's notehead-center staff position converted to ss. Screen-down convention:
     * a more negative value is higher on the staff. Single source of truth for the absolute vertical
     * anchor shared by {@link #getAbsoluteTopYSs()}, {@link #getAbsoluteBottomYSs()}, and the optical
     * spacing corrections.
     */
    public double getPositionSs() {
        return Staff.spToSs(getElement().getStaffPosition());
    }

    /**
     * Returns the absolute layout-Y top of this column: the element's staff position converted
     * to ss, plus the note-local stem top extent. Single source of truth for the absolute top used
     * when building skyline contours.
     */
    public double getAbsoluteTopYSs() {
        return getPositionSs() + getStemTopSs();
    }

    /**
     * Returns the top (higher, numerically smaller) of this column's <em>left-facing</em> vertical
     * span — the band of ink a neighbour approaching from the left can actually meet, which is not
     * the same as the column's whole span ({@link #getAbsoluteTopYSs()}).
     *
     * <p>An up-stem hangs off the notehead's <em>right</em> edge and carries its flag with it, a
     * column-width away from anything on the left; only the notehead itself faces left. So the
     * left-facing band starts at the notehead top in every case, and the column's own top counts only
     * when the stem points down — which {@link #getLeftFacingBottomYSs()} covers, since a down-stem
     * reaches downward.
     *
     * <p>"Notehead top" is this element type's own glyph bounding-box top
     * ({@link ElementType#getNoteheadTopOffsetSs()}), not a fixed notehead half-height: a rest or a
     * barline faces left with all the ink its glyph actually occupies.
     */
    public double getLeftFacingTopYSs() {
        return getPositionSs() + element.getType().getNoteheadTopOffsetSs();
    }

    /**
     * Returns the bottom (lower, numerically larger) of this column's left-facing vertical span. A
     * down-stem sits on the notehead's left edge, so it and its flag do face left and the span runs
     * all the way to the stem tip; for an up-stem or a stemless element
     * {@link #getAbsoluteBottomYSs()} is already the notehead bottom.
     * See {@link #getLeftFacingTopYSs()} for why the two ends are asymmetric.
     */
    public double getLeftFacingBottomYSs() {
        return getAbsoluteBottomYSs();
    }

    /**
     * Returns the absolute layout-Y bottom of this column: the element's notehead-center position
     * plus the note-local stem bottom extent. Single source of truth for the absolute bottom used
     * when measuring vertical overlap between columns. Screen-down convention: this is the
     * numerically larger (lower) of the column's two vertical extremes.
     */
    public double getAbsoluteBottomYSs() {
        return getPositionSs() + getStemBottomSs();
    }

    // ==========================================================================
    // Syllable (Lyric)
    // ==========================================================================

    /**
     * Returns the associated lyric syllable text, read off {@link #getLyric()} so it always
     * belongs to the verse {@link #getSyllableWidthSs()} was measured for.
     *
     * @return Syllable text, or null if no syllable
     */
    public @Nullable String getSyllable() {
        return lyric != null ? lyric.text() : null;
    }

    /**
     * Returns whether this column has an associated syllable.
     */
    public boolean hasSyllable() {
        var syllable = getSyllable();
        return syllable != null && !syllable.isEmpty();
    }

    /**
     * Returns the measured width of the syllable text in pixels.
     *
     * @return Syllable width, or 0 if no syllable
     */
    public double getSyllableWidthSs() {
        return syllableWidthSs;
    }

    /**
     * Returns the measured width of the syllable's first grapheme cluster in staff spaces, which is
     * what a grace note centers on its notehead (see
     * {@link HorizontalSpacingCalculator#graceLyricLeftOffsetSs}). Defaults to the whole syllable
     * width until {@link ElementColumnBuilder} measures it, so a column built without it behaves as
     * though the syllable were a single grapheme.
     *
     * @return First grapheme width, or 0 if no syllable
     */
    public double getSyllableFirstGraphemeWidthSs() {
        return syllableFirstGraphemeWidthSs;
    }

    void setSyllableFirstGraphemeWidthSs(double syllableFirstGraphemeWidthSs) {
        this.syllableFirstGraphemeWidthSs = syllableFirstGraphemeWidthSs;
    }

    /**
     * Returns the element's lyric for the verse this column was built for, or null if it carries
     * none there. Reading the lyric off the column rather than off the element is what keeps every
     * later pass on the same verse the syllable width was measured for — the element itself holds
     * every verse and cannot say which one is being laid out.
     */
    public @Nullable Lyric getLyric() {
        return lyric;
    }

    /**
     * Returns the minimum required gap between this column's right edge and the next
     * column's syllable left edge. Equals the lyric space width for non-hyphenated or
     * lyric-less columns; equals the bare hyphen glyph advance for hyphenated ones.
     */
    public double getMinGapToNextSyllableSs() {
        return minGapToNextSyllableSs;
    }

    void setMinGapToNextSyllableSs(double minGapToNextSyllableSs) {
        this.minGapToNextSyllableSs = minGapToNextSyllableSs;
    }

    /**
     * Returns the hard collision floor for the gap to the next syllable — the closest two
     * syllables may ever come. Equals one lyric space width for non-hyphenated or lyric-less
     * columns (including melisma carriers); equals the bare hyphen glyph advance for hyphenated
     * ones. Equal to {@link #getMinGapToNextSyllableSs()} — the gap a lyric aims for is already
     * the tightest one it tolerates — but kept distinct because the two answer different
     * questions: this one floors the spring strut so lyrics never touch, that one feeds the rest
     * the lyric lift aims at.
     */
    public double getMinCollisionGapToNextSyllableSs() {
        return minCollisionGapToNextSyllableSs;
    }

    void setMinCollisionGapToNextSyllableSs(double minCollisionGapToNextSyllableSs) {
        this.minCollisionGapToNextSyllableSs = minCollisionGapToNextSyllableSs;
    }

    // ==========================================================================
    // Beam Group
    // ==========================================================================

    /**
     * Returns whether this element is part of a beam group.
     */
    public boolean isBeamed() {
        return beamed;
    }

    /**
     * Returns the identifier of the beam group this column belongs to, or
     * {@link #NO_BEAM_GROUP} if it is not beamed. Adjacent beam groups have distinct ids so
     * the spacing calculator keeps them separate.
     */
    public int getBeamGroupId() {
        return beamGroupId;
    }

    /**
     * Returns whether this column and {@code other} sit in the same beam group. False when either is
     * unbeamed — {@link #NO_BEAM_GROUP} never matches itself, so two unbeamed columns do not count as
     * sharing a group. Single source of truth for the "one beam spans this gap" question, which the
     * beam-internal rest reduction and {@link OpticalSpacing}'s knee exclusion both ask.
     */
    public boolean sharesBeamGroupWith(ElementColumn other) {
        return beamGroupId != NO_BEAM_GROUP && beamGroupId == other.beamGroupId;
    }

    void setBeamGroupId(int beamGroupId) {
        this.beamGroupId = beamGroupId;
    }

    /**
     * Returns whether this element has an outgoing glissando.
     */
    public boolean hasGlissando() {
        return element.hasGlissando();
    }

    // ==========================================================================
    // X Position (set by HorizontalSpacingCalculator)
    // ==========================================================================

    /**
     * Returns the X position of this column's element head left edge (glyph origin).
     * This is set by the HorizontalSpacingCalculator during layout.
     */
    public double getXSs() {
        return xSs;
    }

    /**
     * Sets the X position of this column's element head left edge (glyph origin).
     * Called by the HorizontalSpacingCalculator during layout.
     *
     * @param xSs X position in staff spaces
     */
    void setXSs(double xSs) {
        this.xSs = xSs;
    }

    // ==========================================================================
    // Object Methods
    // ==========================================================================

    @Override
    public String toString() {
        var sb = new StringBuilder(200);
        sb.append("ElementColumn{");
        sb.append("element=").append(element.getType());

        if (hasGraceNotes()) {
            sb.append(", graceNotes=").append(graceNotes.size());
        }

        sb.append(", extent=[").append(leftExtentSs).append(", ").append(rightExtentSs).append(']');

        if (hasSyllable()) {
            sb.append(", syllable='").append(getSyllable()).append('\'');
            sb.append(", syllableWidth=").append(syllableWidthSs);
        }

        if (beamed) {
            sb.append(", beamed");
        }

        sb.append(", x=").append(xSs);
        sb.append('}');

        return sb.toString();
    }

    // ==========================================================================
    // Span Resolution
    // ==========================================================================

    /**
     * Resolves a {@link RangeElement} span's anchor and end elements to their columns, or {@code null}
     * when either endpoint is unset ({@link RangeElement#getAnchorElement} /
     * {@link RangeElement#getEndElement}) or either has no column in {@code columnsByElement}.
     *
     * @param span              the range element to resolve
     * @param columnsByElement  element-to-column lookup for the line being laid out
     * @return the resolved endpoints and their columns, or {@code null} if the span cannot be placed
     */
    @Nullable
    public static SpanColumns resolveSpan(
        RangeElement span, Map<StaffElement, ElementColumn> columnsByElement) {
        var anchor = span.getAnchorElement();
        var end = span.getEndElement();

        if (anchor == null || end == null) {
            return null;
        }

        var anchorColumn = columnsByElement.get(anchor);
        var endColumn = columnsByElement.get(end);

        if (anchorColumn == null || endColumn == null) {
            return null;
        }

        return new SpanColumns(anchor, end, anchorColumn, endColumn);
    }

    /**
     * A {@link RangeElement} span's anchor and end elements together with their resolved columns.
     * Returned by {@link #resolveSpan}.
     */
    public record SpanColumns(
        StaffElement anchor, StaffElement end, ElementColumn anchorColumn, ElementColumn endColumn) {}
}
