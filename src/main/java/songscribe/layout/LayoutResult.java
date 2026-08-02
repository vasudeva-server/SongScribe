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

import module java.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Attachment;
import songscribe.engraving.Staff;
import songscribe.dom.Beam;
import songscribe.dom.Clef;
import songscribe.dom.CollisionRegion;
import songscribe.dom.KeySignature;
import songscribe.dom.Line;
import songscribe.dom.LineElement;
import songscribe.dom.RangeElement;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.hit.HitRegistry;

/**
 * Immutable result of the layout engine containing all positioned elements for rendering.
 * <p>
 * All positions and dimensions are in staff-space units.
 * <p>
 * The LayoutResult provides rendering code with final positions for all elements in a line,
 * eliminating the need for any position calculations during rendering. It contains:
 * <ul>
 *   <li>Note columns with their horizontal positions</li>
 *   <li>Line elements (attachments, articulations) with their bounds</li>
 *   <li>Staff geometry (top, bottom, lyric baseline)</li>
 *   <li>Total line height for vertical spacing</li>
 * </ul>
 * <p>
 * This class is immutable after construction. Use {@link Builder} to create instances.
 */
public final class LayoutResult {

    /**
     * Offset for positioning a preview element before the first element in the line (ss).
     */
    private static final double PREVIEW_BEFORE_FIRST_OFFSET_SS = 1.875;  // 15px

    private final Map<StaffElement, ElementColumn> elementColumns;
    private final Map<Beam, BeamLayout> beamLayouts;
    /**
     * Flat lookup keyed by element for every stem in the line — beamed stems
     * (extracted from {@link BeamLayout#stems()}) and unbeamed stems alike.
     * Built once at construction so per-element render lookups are O(1) instead
     * of O(beam-count).
     */
    private final Map<StaffElement, StemLayout> allStemLayouts;
    private final Map<Tie, TieLayout> tieLayouts;
    private final Map<LineElement, DecorationLayout> decorationLayouts;
    private final Map<StaffElement, SlideLayout> slideLayouts;
    @Nullable
    private final Clef clef;
    @Nullable
    private final KeySignature keySignature;
    private final double contentAboveStaffSs;
    private final double contentBelowStaffSs;
    private final Map<StaffElement, List<LyricBoxLayout>> lyricBoxes;
    private final List<LyricConnectorLayout> lyricConnectors;
    private final boolean hasTrailingLyricContinuation;
    private final boolean overflowsStaffWidth;
    private final HitRegistry hitRegistry;

    /**
     * Creates a layout result with the given data.
     * <p>
     * Use {@link Builder} rather than calling this constructor directly.
     *
     * @param elementColumns   Map of elements to their columns with positions
     * @param beamLayouts      Map of beam spans to their computed beam geometry
     * @param stemLayouts      Map of unbeamed notes to their computed stem geometry
     * @param tieLayouts       Map of tie spans to their computed tie geometry
     * @param slideLayouts     Map of slide-owning notes to their computed slide geometry
     * @param contentAboveStaffSs True extent of this line's content above the staff top, in staff
     *                           spaces, and also the staff top's Y position within the line's local
     *                           coordinate frame
     * @param contentBelowStaffSs True extent of this line's content below the staff bottom, in staff
     *                           spaces; the anchor for placing this line's lyrics
     * @param overflowsStaffWidth True when the line's content could not fit the staff width even at
     *                           its collision floors, so the columns were placed on those floors and
     *                           the tail of the line runs past the staff and is clipped
     * @param hitRegistry        Every clickable area of the line, in layout space
     */
    private LayoutResult(
        Map<StaffElement, ElementColumn> elementColumns,
        Map<Beam, BeamLayout> beamLayouts,
        Map<StaffElement, StemLayout> stemLayouts,
        Map<Tie, TieLayout> tieLayouts,
        Map<LineElement, DecorationLayout> decorationLayouts,
        Map<StaffElement, SlideLayout> slideLayouts,
        @Nullable Clef clef,
        @Nullable KeySignature keySignature,
        double contentAboveStaffSs,
        double contentBelowStaffSs,
        Map<StaffElement, List<LyricBoxLayout>> lyricBoxes,
        List<LyricConnectorLayout> lyricConnectors,
        boolean hasTrailingLyricContinuation,
        boolean overflowsStaffWidth,
        HitRegistry hitRegistry) {
        this.elementColumns = Map.copyOf(elementColumns);
        this.beamLayouts = Map.copyOf(beamLayouts);

        var mergedStems = new HashMap<>(stemLayouts);

        for (var beamLayout : beamLayouts.values()) {
            mergedStems.putAll(beamLayout.stems());
        }

        allStemLayouts = Map.copyOf(mergedStems);
        this.tieLayouts = Map.copyOf(tieLayouts);
        this.decorationLayouts = Map.copyOf(decorationLayouts);
        this.slideLayouts = Map.copyOf(slideLayouts);
        this.clef = clef;
        this.keySignature = keySignature;
        this.contentAboveStaffSs = contentAboveStaffSs;
        this.contentBelowStaffSs = contentBelowStaffSs;
        var copiedBoxes = new HashMap<StaffElement, List<LyricBoxLayout>>(lyricBoxes.size() * 2);

        for (var entry : lyricBoxes.entrySet()) {
            copiedBoxes.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        this.lyricBoxes = Collections.unmodifiableMap(copiedBoxes);
        this.lyricConnectors = List.copyOf(lyricConnectors);
        this.hasTrailingLyricContinuation = hasTrailingLyricContinuation;
        this.overflowsStaffWidth = overflowsStaffWidth;
        this.hitRegistry = hitRegistry;
    }

    /**
     * Copies {@code source}, replacing only its hit registry. Every other field is already
     * immutable, so the copy shares them instead of duplicating them.
     */
    private LayoutResult(LayoutResult source, HitRegistry hitRegistry) {
        elementColumns = source.elementColumns;
        beamLayouts = source.beamLayouts;
        allStemLayouts = source.allStemLayouts;
        tieLayouts = source.tieLayouts;
        decorationLayouts = source.decorationLayouts;
        slideLayouts = source.slideLayouts;
        clef = source.clef;
        keySignature = source.keySignature;
        contentAboveStaffSs = source.contentAboveStaffSs;
        contentBelowStaffSs = source.contentBelowStaffSs;
        lyricBoxes = source.lyricBoxes;
        lyricConnectors = source.lyricConnectors;
        hasTrailingLyricContinuation = source.hasTrailingLyricContinuation;
        overflowsStaffWidth = source.overflowsStaffWidth;
        this.hitRegistry = hitRegistry;
    }

    /**
     * Returns this result carrying {@code hitRegistry} in place of its own.
     * <p>
     * The hit registry is the last thing layout computes: it reads the finished columns, lyric
     * boxes, decorations, ties and slides, and the derived accessors that own their geometry
     * formulas, so it cannot be assembled until everything else is done. Attaching it to a
     * built result preserves that ordering without laying the line out — or copying its maps —
     * a second time.
     */
    LayoutResult withHitRegistry(HitRegistry hitRegistry) {
        return new LayoutResult(this, hitRegistry);
    }

    // ==========================================================================
    // Note Column Access
    // ==========================================================================

    /**
     * Returns the element column for a specific element.
     *
     * @param element The element to look up
     * @return The element column, or null if the element was not laid out
     */
    public @Nullable ElementColumn getElementColumn(StaffElement element) {
        return elementColumns.get(element);
    }

    /**
     * Returns the X position of an element's head left edge (glyph origin).
     *
     * @param element The element to look up
     * @return The X position, or 0 if the element was not laid out
     */
    public double getElementXSs(StaffElement element) {
        var column = elementColumns.get(element);
        return column != null ? column.getXSs() : 0;
    }

    /**
     * Returns an unmodifiable view of all element columns.
     *
     * @return Map of elements to their columns
     */
    public Map<StaffElement, ElementColumn> getElementColumns() {
        return elementColumns;
    }

    /**
     * Returns whether an element was laid out.
     *
     * @param element The element to check
     * @return true if the element has a column
     */
    public boolean hasElement(StaffElement element) {
        return elementColumns.containsKey(element);
    }

    // ==========================================================================
    // Beam + Stem Layout Access
    // ==========================================================================

    /**
     * Returns the beam geometry for a beam span.
     *
     * @param span The beam span to look up
     * @return The beam layout, or null if not computed
     */
    public @Nullable BeamLayout getBeamLayout(Beam beam) {
        return beamLayouts.get(beam);
    }

    /**
     * Returns the stem geometry for an element.
     * <p>
     * Checks beamed stem layouts first (elements inside a beam group), then falls back
     * to the standalone stem layouts for unbeamed elements.
     *
     * @param element The element to look up
     * @return The stem layout, or null if not computed
     */
    public @Nullable StemLayout getStemLayout(StaffElement element) {
        return allStemLayouts.get(element);
    }

    // ==========================================================================
    // Tie Layout Access
    // ==========================================================================

    /**
     * Returns the tie geometry for a tie span, if it was computed during layout.
     * <p>
     * Returns null when the span was not laid out (e.g., degenerate
     * tie spanning notes that could not be positioned). Callers should skip rendering
     * if the result is null.
     *
     * @param span The tie span to look up
     * @return the tie layout, or null if not computed
     */
    @Nullable
    public TieLayout getTieLayout(Tie tie) {
        return tieLayouts.get(tie);
    }

    // ==========================================================================
    // Slide Layout Access
    // ==========================================================================

    /**
     * Returns the slide geometry for a slide-owning note, if it was computed during layout.
     * <p>
     * Returns null when the note owns no slide, or when the slide has no drawable geometry — a
     * connecting glissando too short to render. Callers should skip rendering and hit-testing
     * if the result is null.
     *
     * @param note The note owning the slide
     * @return the slide layout, or null if not computed
     */
    public @Nullable SlideLayout getSlideLayout(StaffElement note) {
        return slideLayouts.get(note);
    }

    // ==========================================================================
    // Decoration + Span Layout Access
    // ==========================================================================

    /**
     * Returns the decoration layout for an above-staff decoration element.
     *
     * @param element The decoration element to look up
     * @return The decoration layout, or null if not computed
     */
    public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
        return decorationLayouts.get(element);
    }

    /**
     * Returns an unmodifiable view of all decoration layouts.
     *
     * @return Map of decoration elements to their layouts
     */
    public Map<LineElement, DecorationLayout> getDecorationLayouts() {
        return decorationLayouts;
    }

    /**
     * Returns all decoration layout entries whose key is an instance of the given type.
     * <p>
     * Used by renderers to iterate all elements of a specific type (e.g., all Trills,
     * all Crescendos) in a single pass, regardless of whether they originated from
     * new range elements or were bridged from legacy flags during layout.
     *
     * @param type The element type to filter by
     * @return List of matching entries (element + layout)
     */
    public <T extends LineElement> List<Map.Entry<T, DecorationLayout>> getDecorationLayoutsByType(
        Class<? extends T> type) {

        var result = new ArrayList<Map.Entry<T, DecorationLayout>>();

        for (var entry : decorationLayouts.entrySet()) {
            if (type.isInstance(entry.getKey())) {
                result.add(Map.entry(type.cast(entry.getKey()), entry.getValue()));
            }
        }

        return result;
    }

    /**
     * Finds the decoration layout for an attachment with the given owner element and type.
     * <p>
     * Used by renderers that need to look up layout results for attachments
     * but don't have direct access to the attachment object created during layout.
     *
     * @param ownerElement   The element the attachment is attached to
     * @param attachmentType The type of attachment to find
     * @return The decoration layout if found, null otherwise
     */
    public @Nullable DecorationLayout findAttachmentDecorationLayout(
        StaffElement ownerElement,
        Class<? extends Attachment> attachmentType) {

        return findByAttachment(decorationLayouts, ownerElement, attachmentType);
    }

    /**
     * Finds the decoration layout for a range element with the given anchor element and type.
     * <p>
     * Used by renderers that need to look up layout results for range elements
     * but don't have direct access to the range element object created during layout.
     *
     * @param anchorElement    The anchor (start) element of the range
     * @param rangeElementType The type of range element to find
     * @return The decoration layout if found, null otherwise
     */
    public @Nullable DecorationLayout findRangeElementDecorationLayout(
        StaffElement anchorElement,
        Class<? extends RangeElement> rangeElementType) {

        for (var entry : decorationLayouts.entrySet()) {
            var element = entry.getKey();

            if (rangeElementType.isInstance(element)) {
                var rangeElement = (RangeElement) element;

                if (rangeElement.getAnchorElement() == anchorElement) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    // ==========================================================================
    // Hit Testing
    // ==========================================================================

    /**
     * Returns every clickable area of this line, resolved by priority rather than by a cascade
     * of per-kind hit testers.
     * <p>
     * Built at layout time, so it answers from the moment the line is laid out — before any
     * paint, and unaffected by repaints.
     *
     * @return the line's hit registry; {@link HitRegistry#EMPTY} if nothing on it is clickable
     */
    public HitRegistry getHitRegistry() {
        return hitRegistry;
    }

    // ==========================================================================
    // Header Element Access
    // ==========================================================================

    /**
     * Returns the clef element for this line.
     *
     * @return The clef, or null if not yet created (empty line)
     */
    public @Nullable Clef getClef() {
        return clef;
    }

    /**
     * Returns the key signature element for this line.
     *
     * @return The key signature, or null if not yet created (empty line)
     */
    public @Nullable KeySignature getKeySignature() {
        return keySignature;
    }

    private <T> @Nullable T findByAttachment(
        Map<LineElement, T> map,
        StaffElement ownerElement,
        Class<? extends Attachment> attachmentType) {

        for (var entry : map.entrySet()) {
            var element = entry.getKey();

            if (attachmentType.isInstance(element)) {
                var attachment = (Attachment) element;

                if (attachment.getOwnerElement() == ownerElement) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    // ==========================================================================
    // Staff Geometry
    // ==========================================================================

    /**
     * Returns the true extent of this line's content above the staff top, in staff spaces.
     * <p>
     * Unfloored: a line with nothing above its staff returns 0. This is a <em>spacing</em>
     * value, not the staff's position — for that use {@link #staffTopYSsInLine}, which is
     * floored and so is where the staff is actually drawn.
     */
    public double getContentAboveStaffSs() {
        return contentAboveStaffSs;
    }

    /**
     * Returns the true extent of this line's content below the staff bottom, in staff spaces.
     * <p>
     * This is the lyric-positioning anchor: the maximum distance below the staff bottom that
     * anything on this line reaches. Unfloored: a line with nothing below its staff returns 0.
     */
    public double getContentBelowStaffSs() {
        return contentBelowStaffSs;
    }

    /**
     * Returns the distance from this line's staff midline to the top of its component,
     * in staff spaces. The midline is the reference the parent layout manager spaces
     * lines by, so this is the line's upward reach from that reference.
     */
    public double aboveMidlineSs() {
        return Staff.STAFF_HALF_SS + contentAboveStaffSs;
    }

    /**
     * Returns the distance from this line's staff midline to the bottom of its component,
     * in staff spaces, including its own lyrics band.
     */
    public double belowMidlineSs(LyricRenderMetrics lyricRenderMetrics) {
        return Staff.STAFF_HALF_SS + contentBelowStaffSs + lyricsBandHeightSs(lyricRenderMetrics);
    }

    /**
     * Returns the total vertical space this line's lyrics occupy, in staff spaces, measured from
     * this line's below-staff content down to the bottom of the lyric row. The band is one row deep
     * whatever the line holds: only the song's active verse is ever laid out, and the row is
     * reserved even before that verse has any lyrics on this line, so entering the first lyric does
     * not re-space the song and a song carrying a second language is no taller than one that does
     * not.
     * <p>
     * Deliberately built from {@link LineSpacing#LYRICS_ROW_MARGIN_SS} rather than from
     * {@link LyricRenderMetrics#staffToLyricsGapSs}: that gap is a <em>baseline</em> offset, so
     * it already contains the row's above-baseline ink. Adding the row on top of it counted that
     * ink twice and left dead space at the bottom of every line. The band is simply the visual
     * margin plus one row of ink — which also lands the row's ink bottom exactly on the band's
     * bottom edge, since {@code baseline + belowBaseline} collapses to this same expression.
     */
    public double lyricsBandHeightSs(LyricRenderMetrics lyricRenderMetrics) {
        return LineSpacing.LYRICS_ROW_MARGIN_SS + lyricRenderMetrics.lyricBoxHeightSs();
    }

    /**
     * Returns the total height of this line's component, in staff spaces. Covers only this
     * line's own content — the gap to the next line belongs to the parent layout manager.
     */
    public double lineHeightSs(LyricRenderMetrics lyricRenderMetrics) {
        return aboveMidlineSs() + belowMidlineSs(lyricRenderMetrics);
    }

    // ==========================================================================
    // Painted extents
    // ==========================================================================
    //
    // The measured extents above drive inter-line spacing; the floored ones below drive
    // component bounds and the staff's placement within those bounds. Every consumer of a
    // line's *geometry* must use the painted pair, and every consumer of its *spacing* the
    // measured pair — mixing them puts the staff at a different offset than the layout
    // manager reserved for it, which draws the line clear of its own bounds.

    /**
     * Returns the distance from the top of this line's component to its staff midline, in
     * staff spaces: the measured content above the midline, floored at
     * {@link LineSpacing#MIN_ABOVE_MIDLINE_SS}.
     */
    public double paintAboveMidlineSs() {
        return Math.max(aboveMidlineSs(), LineSpacing.MIN_ABOVE_MIDLINE_SS);
    }

    /**
     * Returns the distance from this line's staff midline to the bottom of its component, in
     * staff spaces: the measured content below the midline, floored at
     * {@link LineSpacing#MIN_BELOW_MIDLINE_SS}.
     */
    public double paintBelowMidlineSs(LyricRenderMetrics lyricRenderMetrics) {
        return Math.max(belowMidlineSs(lyricRenderMetrics), LineSpacing.MIN_BELOW_MIDLINE_SS);
    }

    /**
     * Returns the height of this line's component, in staff spaces — the height its bounds
     * must have for nothing it draws to be clipped. Use this for sizing, not
     * {@link #lineHeightSs}, which reports measured content only.
     */
    public double paintLineHeightSs(LyricRenderMetrics lyricRenderMetrics) {
        return paintAboveMidlineSs() + paintBelowMidlineSs(lyricRenderMetrics);
    }

    /**
     * Returns the Y position of the staff top within this line's local coordinate frame,
     * in staff spaces.
     * <p>
     * Derived from {@link #paintAboveMidlineSs}, not from {@code contentAboveStaffSs}: this is
     * a <em>geometry</em> query, and {@code LineComponent} draws the staff at the painted
     * midline. On a line whose content stops short of {@link LineSpacing#MIN_ABOVE_MIDLINE_SS}
     * the two frames differ by the floor, and answering from the measured one reports a staff
     * that is not where anything is drawn.
     */
    public double staffTopYSsInLine() {
        return paintAboveMidlineSs() - Staff.STAFF_HALF_SS;
    }

    /**
     * Returns the Y position of the staff bottom within this line's local coordinate frame,
     * in staff spaces. Anchored on the painted midline for the reason given in
     * {@link #staffTopYSsInLine}.
     */
    public double staffBottomYSsInLine() {
        return paintAboveMidlineSs() + Staff.STAFF_HALF_SS;
    }

    /**
     * Returns the baseline Y of this line's lyric row within its local coordinate frame, in staff
     * spaces. Driven by this line's own below-staff content, so lyrics hug each line individually
     * rather than a song-wide maximum.
     * <p>
     * There is one such row per line, not one per verse: the song shows a single verse at a time,
     * so whichever verse is active sits on this baseline.
     */
    public double lyricBaselineYSsInLine(LyricRenderMetrics lyricRenderMetrics) {
        return staffBottomYSsInLine()
            + contentBelowStaffSs
            + lyricRenderMetrics.staffToLyricsGapSs();
    }

    // ==========================================================================
    // Lyric Layout
    // ==========================================================================

    /**
     * Returns the lyric boxes for an element — at most one, for the verse this layout pass was
     * built for.
     *
     * @param element the element to look up
     * @return immutable list of lyric boxes; empty if the element has no lyric in that verse
     */
    public List<LyricBoxLayout> getLyricBoxes(StaffElement element) {
        var boxes = lyricBoxes.get(element);
        return boxes != null ? boxes : List.of();
    }

    public @Nullable LyricHit hitTestLyric(LyricRenderMetrics lyricRenderMetrics, Line line, Point2D pointPx) {
        var rowHeightSs = lyricRenderMetrics.lyricBoxHeightSs();
        var rowTopYSs = lyricAreaBaseYSs();
        var pointXSs = ScaleContext.pxToSs(pointPx.getX());
        var pointYSs = ScaleContext.pxToSs(pointPx.getY());

        for (var element : line.getElements()) {
            for (var box : getLyricBoxes(element)) {
                // Matches the ending and note-head hit tests, which also test containment
                // with Rectangle2D: left and top edges inclusive, right and bottom exclusive.
                var hitRect = new Rectangle2D.Double(box.xSs(), rowTopYSs, box.widthSs(), rowHeightSs);

                if (hitRect.contains(pointXSs, pointYSs)) {
                    return new LyricHit(element, box.verseIndex());
                }
            }
        }

        return null;
    }

    // Package-private for direct unit testing of the formula.
    //
    // Built on staffBottomYSsInLine() so the hit-test row rides the same painted frame as the
    // baseline LyricTextRenderer draws at. Anchoring this on contentAboveStaffSs instead put
    // the clickable row above the visible text on any line short enough to hit the floor.
    double lyricAreaBaseYSs() {
        return staffBottomYSsInLine() + contentBelowStaffSs + LineSpacing.LYRICS_ROW_MARGIN_SS;
    }

    /**
     * Returns the center-X and baseline-Y anchor for the lyric editor on {@code element}.
     * <p>
     * Uses the element's lyric box when one exists (box-anchored branch); otherwise falls back to
     * the element column's horizontal center (column-anchored branch). Throws
     * {@link IllegalStateException} when neither a box nor a column is available — that indicates
     * a broken layout state, not a recoverable condition.
     * <p>
     * The Y is the line's single lyric baseline: the editor edits the active verse, and the active
     * verse is what sits on that baseline.
     *
     * @param element            the element to anchor on
     * @param lyricRenderMetrics song-wide lyric render metrics providing the lyric baseline Y
     * @return the lyric anchor for positioning the editor
     * @throws IllegalStateException if neither a lyric box nor an element column exists for the element
     */
    public LyricAnchor getLyricAnchor(StaffElement element, LyricRenderMetrics lyricRenderMetrics) {
        var boxes = getLyricBoxes(element);
        var baselineYSs = lyricBaselineYSsInLine(lyricRenderMetrics);

        if (!boxes.isEmpty()) {
            var box = boxes.getFirst();
            var centerXSs = box.xSs() + box.widthSs() / 2.0;
            return new LyricAnchor(centerXSs, baselineYSs);
        }

        var column = getElementColumn(element);

        if (column == null) {
            throw new IllegalStateException(
                "getLyricAnchor: no lyric box and no column for element " + element);
        }

        var centerXSs = column.getNoteheadCenterXSs();
        return new LyricAnchor(centerXSs, baselineYSs);
    }

    /**
     * Returns all lyric connectors (hyphens, melisma extenders) on this line.
     *
     * @return immutable list of connectors in the order they were emitted
     */
    public List<LyricConnectorLayout> getLyricConnectors() {
        return lyricConnectors;
    }

    /**
     * Returns true when at least one melisma extender on this line spans past the last
     * note and continues onto the following line. Layout passes that build the next line
     * use this to know whether to emit a leading-stub extender from the line's left edge
     * to its first lyric-bearing element.
     */
    public boolean hasTrailingLyricContinuation() {
        return hasTrailingLyricContinuation;
    }

    /**
     * Returns true when this line's content could not fit the staff width even with every gap
     * compressed to its collision floor. The columns were then placed on those floors — the
     * tightest legal spacing there is — so the line still lays out and still draws, but its tail
     * runs past the end of the staff, where the component's bounds clip it.
     * <p>
     * The renderer draws such a line's staff lines in the overflow color, which is the user's
     * standing indication that content is missing; the accompanying alert is shown only once per
     * document (see {@code LineComponent.warnLineOverflows}).
     */
    public boolean overflowsStaffWidth() {
        return overflowsStaffWidth;
    }

    // ==========================================================================
    // Preview Element Positioning (Edit Mode)
    // ==========================================================================

    /**
     * Returns the index of the element whose head contains {@code mouseXSs}, or {@code -1} if none.
     * <p>
     * Only the horizontal (X) dimension is checked; Y position is ignored.
     * The hit zone is the actual element head body: {@code [xSs, xSs + rightExtentSs]}.
     *
     * @param mouseXSs Mouse X coordinate in staff-space units
     * @param line     The line containing the elements
     * @return Element index, or {@code -1} if mouseXSs is not within any element head's horizontal bounds
     */
    public int findElementAtXSs(double mouseXSs, Line line) {
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);
            var column = elementColumns.get(element);

            if (column == null) {
                continue;
            }

            var elementX = column.getXSs();

            if (mouseXSs >= elementX && mouseXSs <= elementX + column.getRightExtentSs()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Finds which insertion slot a mouse X coordinate falls into.
     * <p>
     * Insertion slots are the positions where an element can be inserted or replaced:
     * <ul>
     *   <li>Index 0 to elementCount-1: over an existing element (for replacement)</li>
     *   <li>Index elementCount: after the last element (for appending)</li>
     * </ul>
     * <p>
     * If the mouse is within the horizontal bounds of an element head, returns that element's index
     * to indicate replacement. Otherwise, returns the insertion slot between elements.
     *
     * @param mouseXSs Mouse X coordinate in staff-space units
     * @param line     The line containing the elements
     * @return Insertion index (0 to elementCount inclusive)
     */
    public int findInsertionIndex(double mouseXSs, Line line) {
        // Exclude the auto-maintained terminal: insertions always occur before it,
        // and the gap between the last real element and the barline should behave as
        // "after the last element" rather than a between-elements midpoint.
        var elementCount = line.effectiveElementCount();

        if (elementCount == 0) {
            return 0;
        }

        // Check each element to see if mouse is within its head bounds
        var elementAtX = findElementAtXSs(mouseXSs, line);

        if (elementAtX >= 0 && elementAtX < elementCount) {
            return elementAtX;
        }

        // Mouse is not over any element head - find insertion slot between elements

        // Check if before first element
        var firstElement = line.getElement(0);
        var firstColumn = elementColumns.get(firstElement);

        if (firstColumn == null) {
            return 0;
        }

        if (mouseXSs < firstColumn.getXSs()) {
            return 0;
        }

        // Check if after last element
        var lastElement = line.getElement(elementCount - 1);
        var lastColumn = elementColumns.get(lastElement);

        if (lastColumn == null) {
            return elementCount;
        }

        if (mouseXSs > lastColumn.getRightEdgeXSs()) {
            return elementCount;
        }

        // Find the slot between elements (excluding element head bounds)
        for (var i = 0; i < elementCount - 1; i++) {
            var currentElement = line.getElement(i);
            var nextElement = line.getElement(i + 1);

            var currentColumn = elementColumns.get(currentElement);
            var nextColumn = elementColumns.get(nextElement);

            if (currentColumn == null || nextColumn == null) {
                continue;
            }

            var currentRight = currentColumn.getRightEdgeXSs();
            var nextLeft = nextColumn.getXSs();

            // Check if mouseXSs is in the gap between element heads
            if (mouseXSs > currentRight && mouseXSs < nextLeft) {
                return i + 1;
            }
        }

        // Fallback: return position after last element
        return elementCount;
    }

    /**
     * Calculates the X position for rendering a preview element at a given index.
     * <p>
     * If the mouse is within the horizontal bounds of an element head, snaps to that element,
     * centering the preview's glyph on it. Otherwise, centers the preview in the room the
     * neighbouring glyphs leave, or between the last element and the line's right boundary.
     *
     * @param insertionIndex      The insertion index (0 to elementCount inclusive)
     * @param mouseXSs            Mouse X coordinate in staff-space units (used to detect if over an
     *                            element head); ignored when {@code betweenElementsOnly} is true
     * @param previewElement      The element to be inserted (used to calculate extents for after-last
     *                            positioning)
     * @param line                The line containing the elements
     * @param betweenElementsOnly When true, skips the element-head snap check below and always
     *                            returns a between-elements/before-first/after-last position — for
     *                            callers (e.g. the paste-mode insertion marker) that never merge onto
     *                            an existing element's own position
     * @return X position in staff-space units for rendering the preview element
     */
    public double calculateInsertionXSs(
        int insertionIndex,
        double mouseXSs,
        StaffElement previewElement,
        Line line,
        boolean betweenElementsOnly) {

        // Exclude the auto-maintained terminal from positioning decisions —
        // it sits at the line's right edge and must not be treated as a real
        // neighbour for spacing the preview.
        var elementCount = line.effectiveElementCount();

        // Empty line - use first element position (clef + key signature + offset)
        if (elementCount == 0) {
            return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
        }

        // Check if mouse is over any element head - if so, snap to that element's position.
        // Include the terminal so hovering over it snaps the preview to its position.
        if (!betweenElementsOnly) {
            for (var i = 0; i < line.elementCount(); i++) {
                var element = line.getElement(i);
                var column = elementColumns.get(element);

                if (column == null) {
                    continue;
                }

                var elementX = column.getXSs();

                if (mouseXSs >= elementX && mouseXSs <= elementX + column.getRightExtentSs()) {
                    // Mouse is over this element head - snap to its position.
                    // For the terminal, right-align the preview to the terminal's right edge
                    // so a wider replacement (e.g. REPEAT_RIGHT replacing FINAL_DOUBLE_BARLINE)
                    // doesn't overflow the staff boundary.
                    if (line.getSong().isAutoMaintainedTerminal(element, line)) {
                        var previewRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                                previewElement, false, StaffElement.Direction.UP);
                        return elementX + column.getRightExtentSs() - previewRightExtentSs;
                    }

                    // Center the preview's glyph on the hovered element's glyph rather than
                    // aligning the two origins: a preview narrower than what it would replace —
                    // a thin barline over a notehead — otherwise sits against that element's
                    // left edge instead of on it (refs #689). Both widths are measured the same
                    // way, so a preview of the hovered element's own type still lands exactly
                    // on it.
                    return centeredInRoomSs(
                        elementX, elementX + element.getType().getElementWidthSs(), previewElement);
                }
            }
        }

        // Mouse is not over an element head - handle insertion

        // Before first element - position to the left
        if (insertionIndex == 0) {
            var firstElement = line.getElement(0);
            var firstColumn = elementColumns.get(firstElement);

            if (firstColumn == null) {
                return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
            }

            return firstColumn.getXSs() - PREVIEW_BEFORE_FIRST_OFFSET_SS;
        }

        // After last element - use same spacing logic as layout engine
        if (insertionIndex >= elementCount) {
            var lastElement = line.getElement(elementCount - 1);
            var lastColumn = elementColumns.get(lastElement);

            if (lastColumn == null) {
                return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
            }

            // Build a temporary column for the preview element to calculate proper spacing
            var insertionColumn = new ElementColumn(
                previewElement,
                Collections.emptyList(),
                ElementColumnBuilder.calculateLeftExtentSs(previewElement),
                ElementColumnBuilder.calculateRightExtentSs(previewElement, false, StaffElement.Direction.UP),
                ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(previewElement, false, StaffElement.Direction.UP),
                0,
                0,
                null,  // a preview element carries no lyric
                0,
                false
            );

            var song = line.getSong();

            // Space the pair through the spring engine the committed layout uses, so the preview
            // honours the song's line rest instead of a fixed default gap.
            var naturalXSs = lastColumn.getXSs()
                + HorizontalSpacingCalculator.buildSpring(
                    lastColumn, insertionColumn, song.getDefaultRestLengthSs()).naturalLengthSs();

            // The line can already be visually full — its committed elements compressed to fit —
            // so the preview's default spacing can overrun what room is actually left. The nearest
            // hard boundary is the auto-maintained terminal (excluded from elementCount above) when
            // the line has one, otherwise the staff margin itself. Splitting the remaining room
            // between the last real element and that boundary reads as the preview floating past
            // the end of the line, rather than pinned flush against the boundary (refs #608).
            var boundarySs = song.getLineWidthSs();
            var terminalColumn = line.elementCount() > elementCount
                ? elementColumns.get(line.getElement(elementCount))
                : null;

            if (terminalColumn != null) {
                boundarySs = terminalColumn.getXSs();
            }

            if (naturalXSs + insertionColumn.getRightExtentSs() > boundarySs) {
                // The room starts at the last element's drawn right edge, measured the same way as
                // the between-elements case below: past the glyph, but not past its flag or dots.
                return centeredInRoomSs(
                    lastColumn.getXSs() + lastElement.getType().getElementWidthSs(), boundarySs, previewElement);
            }

            return naturalXSs;
        }

        // Between elements - center the preview in the room the two neighbouring glyphs leave
        var prevElement = line.getElement(insertionIndex - 1);
        var currElement = line.getElement(insertionIndex);

        var prevColumn = elementColumns.get(prevElement);
        var currColumn = elementColumns.get(currElement);

        if (prevColumn == null || currColumn == null) {
            return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
        }

        // The room runs from the previous element's drawn right edge to the next element's origin,
        // which is that element's drawn left edge. Extents are deliberately not consulted: a flag,
        // dot or accidental does not shrink the room, but the glyphs themselves do — starting the
        // room at the previous element's own origin would center the preview over that element's
        // notehead rather than in the space beside it (refs #689).
        var roomStartSs = prevColumn.getXSs() + prevElement.getType().getElementWidthSs();

        return centeredInRoomSs(roomStartSs, currColumn.getXSs(), previewElement);
    }

    /**
     * Returns the X at which {@code previewElement}'s glyph sits centered in the room between
     * {@code roomStartSs} and {@code roomEndSs}.
     * <p>
     * Centering is symmetric in both directions: a preview wider than the room straddles it,
     * overlapping each side equally, rather than being pinned to either edge.
     * <p>
     * The width centered is the element's own drawn width, not its column's spacing footprint —
     * a flag, accidental or dot would otherwise leave the glyph the eye actually reads as the
     * element sitting well off center.
     */
    private static double centeredInRoomSs(double roomStartSs, double roomEndSs, StaffElement previewElement) {
        var previewWidthSs = previewElement.getType().getElementWidthSs();
        return roomStartSs + (roomEndSs - roomStartSs - previewWidthSs) / 2;
    }

    // ==========================================================================
    // Statistics
    // ==========================================================================

    /**
     * Returns the number of element columns in this result.
     */
    public int getElementColumnCount() {
        return elementColumns.size();
    }

    // ==========================================================================
    // Builder
    // ==========================================================================

    /**
     * Builder for creating LayoutResult instances incrementally.
     */
    public static class Builder {

        private final Map<StaffElement, ElementColumn> elementColumns;
        private final Map<Beam, BeamLayout> beamLayouts;
        private final Map<StaffElement, StemLayout> stemLayouts;
        private final Map<Tie, TieLayout> tieLayouts;
        private final Map<LineElement, DecorationLayout> decorationLayouts;
        private final Map<StaffElement, SlideLayout> slideLayouts;
        @Nullable
        private Clef clef;
        @Nullable
        private KeySignature keySignature;
        private double contentAboveStaffSs = 0;
        private double contentBelowStaffSs = 0;
        private final Map<StaffElement, List<LyricBoxLayout>> lyricBoxes;
        private final List<LyricConnectorLayout> lyricConnectors;
        private boolean hasTrailingLyricContinuation = false;
        private boolean overflowsStaffWidth = false;

        public Builder() {
            elementColumns = new HashMap<>();
            beamLayouts = new HashMap<>();
            stemLayouts = new HashMap<>();
            tieLayouts = new HashMap<>();
            decorationLayouts = new HashMap<>();
            slideLayouts = new HashMap<>();
            lyricBoxes = new HashMap<>();
            lyricConnectors = new ArrayList<>();
        }

        /**
         * Sets the clef element for this line.
         *
         * @param clef The clef element
         * @return This builder for chaining
         */
        public Builder setClef(Clef clef) {
            this.clef = clef;
            return this;
        }

        /**
         * Sets the key signature element for this line.
         *
         * @param keySignature The key signature element
         * @return This builder for chaining
         */
        public Builder setKeySignature(KeySignature keySignature) {
            this.keySignature = keySignature;
            return this;
        }

        /**
         * Adds an element column to the result.
         *
         * @param element The element
         * @param column  The element's column with position
         * @return This builder for chaining
         */
        public Builder putElementColumn(StaffElement element, ElementColumn column) {
            elementColumns.put(element, column);
            return this;
        }

        /**
         * Sets the true extent of this line's content above the staff top.
         *
         * @param contentAboveStaffSs Distance above the staff top in staff-space units (i.e. the
         *                            staff top's Y position within the line's local coordinate frame)
         * @return This builder for chaining
         */
        public Builder setContentAboveStaffSs(double contentAboveStaffSs) {
            this.contentAboveStaffSs = contentAboveStaffSs;
            return this;
        }

        /**
         * Sets the true extent of this line's content below the staff bottom.
         *
         * @param contentBelowStaffSs Distance below the staff bottom in staff-space units
         * @return This builder for chaining
         */
        public Builder setContentBelowStaffSs(double contentBelowStaffSs) {
            this.contentBelowStaffSs = contentBelowStaffSs;
            return this;
        }

        /**
         * Adds a lyric box for an element. One verse is laid out at a time, so an element gets at
         * most one box; insertion order is preserved.
         *
         * @param element the element the box is anchored to
         * @param box     the lyric box layout
         * @return this builder for chaining
         */
        public Builder addLyricBox(StaffElement element, LyricBoxLayout box) {
            lyricBoxes.computeIfAbsent(element, e -> new ArrayList<>()).add(box);
            return this;
        }

        /**
         * Adds a lyric connector (hyphen or extender) to the line.
         *
         * @param connector the connector layout
         * @return this builder for chaining
         */
        public Builder addLyricConnector(LyricConnectorLayout connector) {
            lyricConnectors.add(connector);
            return this;
        }

        /**
         * Marks whether this line ends with an active melisma extender that continues onto
         * the next line.
         *
         * @param hasTrailingLyricContinuation true if continuation continues past the last note
         * @return this builder for chaining
         */
        public Builder setHasTrailingLyricContinuation(boolean hasTrailingLyricContinuation) {
            this.hasTrailingLyricContinuation = hasTrailingLyricContinuation;
            return this;
        }

        /**
         * Marks whether the line was placed on its collision floors because its content could not
         * fit the staff width, so its tail runs past the staff and is clipped.
         *
         * @param overflowsStaffWidth true if the line overflows the staff width
         * @return this builder for chaining
         */
        public Builder setOverflowsStaffWidth(boolean overflowsStaffWidth) {
            this.overflowsStaffWidth = overflowsStaffWidth;
            return this;
        }

        /**
         * Adds computed beam geometry for a beam span.
         *
         * @param span       The beam span
         * @param beamLayout The computed beam geometry
         * @return This builder for chaining
         */
        public Builder putBeamLayout(Beam beam, BeamLayout beamLayout) {
            beamLayouts.put(beam, beamLayout);
            return this;
        }

        /**
         * Adds computed stem geometry for an unbeamed element.
         *
         * @param element    The element
         * @param stemLayout The computed stem geometry
         * @return This builder for chaining
         */
        public Builder putStemLayout(StaffElement element, StemLayout stemLayout) {
            stemLayouts.put(element, stemLayout);
            return this;
        }

        /**
         * Adds computed tie geometry for a tie span.
         *
         * @param span      The tie span
         * @param tieLayout The computed tie geometry
         * @return This builder for chaining
         */
        public Builder putTieLayout(Tie tie, TieLayout tieLayout) {
            tieLayouts.put(tie, tieLayout);
            return this;
        }

        /**
         * Adds computed slide geometry for a slide-owning note.
         *
         * @param note        The note owning the slide
         * @param slideLayout The computed slide geometry
         * @return This builder for chaining
         */
        public Builder putSlideLayout(StaffElement note, SlideLayout slideLayout) {
            slideLayouts.put(note, slideLayout);
            return this;
        }

        /**
         * Adds computed decoration layout for an above-staff decoration element.
         *
         * @param element          The decoration element
         * @param decorationLayout The computed decoration geometry
         * @return This builder for chaining
         */
        public Builder putDecorationLayout(LineElement element, DecorationLayout decorationLayout) {
            decorationLayouts.put(element, decorationLayout);
            return this;
        }

        /**
         * Returns the decoration layout entries for iteration.
         * <p>
         * Used by the post-layout offset application pass to read and update
         * decoration positions with manual user offsets.
         *
         * @return The decoration layout entry set (mutable — callers may update via
         *         {@link #putDecorationLayout})
         */
        public Set<Map.Entry<LineElement, DecorationLayout>> getDecorationLayoutEntries() {
            return decorationLayouts.entrySet();
        }

        /**
         * Returns the decoration layout for a specific element, or null if not yet computed.
         *
         * @param element The decoration element to look up
         * @return The decoration layout, or null if not present
         */
        public @Nullable DecorationLayout getDecorationLayout(LineElement element) {
            return decorationLayouts.get(element);
        }

        /**
         * Returns the tie layout for a tie span from the builder's accumulated data.
         *
         * @param span The tie span to look up
         * @return The tie layout, or null if not yet computed
         */
        public @Nullable TieLayout getTieLayout(Tie tie) {
            return tieLayouts.get(tie);
        }

        /**
         * Returns the stem layout for an element from the builder's accumulated data.
         * <p>
         * Checks beamed stem layouts first, then standalone stem layouts.
         * Used by the vertical stacking calculator to seed note bounding areas.
         *
         * @param element The element to look up
         * @return The stem layout, or null if not yet computed
         */
        public @Nullable StemLayout getStemLayout(StaffElement element) {
            for (var beamLayout : beamLayouts.values()) {
                var stemLayout = beamLayout.stems().get(element);

                if (stemLayout != null) {
                    return stemLayout;
                }
            }

            return stemLayouts.get(element);
        }

        /**
         * Builds the immutable result.
         *
         * @return The layout result
         */
        public LayoutResult build() {
            return new LayoutResult(
                elementColumns,
                beamLayouts,
                stemLayouts,
                tieLayouts,
                decorationLayouts,
                slideLayouts,
                clef,
                keySignature,
                contentAboveStaffSs,
                contentBelowStaffSs,
                lyricBoxes,
                lyricConnectors,
                hasTrailingLyricContinuation,
                overflowsStaffWidth,
                // The registry is built from a finished result and attached with
                // withHitRegistry, so a freshly built one has nothing clickable yet.
                HitRegistry.EMPTY
            );
        }
    }

    /**
     * Creates a new builder for LayoutResult.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format(
            "LayoutResult{columns=%d, decorations=%d, hitRegions=%d, contentAbove=%.1f, contentBelow=%.1f}",
            elementColumns.size(),
            decorationLayouts.size(),
            hitRegistry.regions().size(),
            contentAboveStaffSs,
            contentBelowStaffSs
        );
    }

    // ==========================================================================
    // Layout Records
    // ==========================================================================

    /**
     * Immutable stem geometry for a single element, computed during layout.
     * <p>
     * All values are in staff-space units.
     *
     * @param topYSs       Y position of the top of the stem
     * @param bottomYSs    Y position of the bottom of the stem
     * @param lengtheningSs Extra stem extension beyond the natural minimum length (≥ 0): for a beamed
     *                   note, the amount needed to reach the beam; for an unbeamed note, the amount
     *                   needed to extend the tip to the staff center (Y=0)
     * @param forcedShorteningSs Amount (≥ 0, ss) this stem falls short of the natural minimum length.
     *                   For a beamed note this is the quanted beam geometry's shortening below the
     *                   standard stem length. For an unbeamed note it is the Ross &amp; Gourlay
     *                   shortening applied because the stem points in its forced (unnatural)
     *                   direction; 0 for every natural, auto-direction, and grace stem.
     *                   Bracket-independent — not a tuplet trim.
     * @param stubRight  For partial-beam notes: true if the stub extends to the right, false to the left.
     *                   Meaningless for full-beam and unbeamed notes.
     * @param frenchShorteningLevels Number of beam levels (not staff spaces) the <em>drawn</em> stem stops
     *                   short of the outer beam under French beaming; 0 for unbeamed notes and for every
     *                   stem that runs out to the outer beam. See {@link BeamMath#frenchBeamShortening}.
     *                   Drawing-time only: the logical tip described by the Y values above is unaffected.
     */
    public record StemLayout(
        double topYSs,
        double bottomYSs,
        double lengtheningSs,
        double forcedShorteningSs,
        boolean stubRight,
        int frenchShorteningLevels) {}

    /**
     * Immutable beam geometry for a beam group, computed during layout.
     * <p>
     * All values are in staff-space units unless noted.
     *
     * @param slope      Beam slope in staff-space units per staff-space unit (dimensionless)
     * @param startYSs     Beam Y position at the first element's X coordinate
     * @param stemsUp    True if stems point upward (beam below noteheads)
     * @param thickeningSs Extra beam thickness from the {@code 1/cos(angle)} raster correction (ss);
     *                   added symmetrically to the nominal {@code BEAM_DEPTH}
     * @param stems      Stem geometry keyed by element, for every element in this beam group
     */
    public record BeamLayout(
        double slope,
        double startYSs,
        boolean stemsUp,
        double thickeningSs,
        Map<StaffElement, StemLayout> stems) {}

    /**
     * Immutable tie geometry, computed during layout.
     * <p>
     * All values are in staff-space units. The outer and inner curves form a filled
     * lens shape when rendered as a closed path: draw the outer cubic Bezier from
     * start to end, then draw the inner cubic Bezier in reverse (end back to start),
     * then close and fill.
     *
     * @param startXSs    Tie start X position
     * @param startYSs    Tie start Y position
     * @param endXSs      Tie end X position
     * @param endYSs      Tie end Y position
     * @param cp1XSs      Outer curve control point 1 X
     * @param cp1YSs      Outer curve control point 1 Y
     * @param cp2XSs      Outer curve control point 2 X
     * @param cp2YSs      Outer curve control point 2 Y
     * @param innerCp1XSs Inner curve control point 1 X
     * @param innerCp1YSs Inner curve control point 1 Y
     * @param innerCp2XSs Inner curve control point 2 X
     * @param innerCp2YSs Inner curve control point 2 Y
     */
    public record TieLayout(
        double startXSs, double startYSs,
        double endXSs, double endYSs,
        double cp1XSs, double cp1YSs,
        double cp2XSs, double cp2YSs,
        double innerCp1XSs, double innerCp1YSs,
        double innerCp2XSs, double innerCp2YSs) {

        /**
         * Returns a copy of this tie rigidly shifted vertically by {@code delta} staff spaces.
         * <p>
         * Every Y — both endpoints and all four control points — moves by {@code delta}; the X
         * positions are unchanged, so the arc's shape is preserved and only its vertical position
         * changes. Used to slide a tie outward until it clears a staccato tucked under its arc.
         *
         * @param delta vertical shift in staff spaces (positive = downward)
         * @return the translated tie
         */
        public TieLayout translateY(double delta) {
            return new TieLayout(
                startXSs, startYSs + delta,
                endXSs, endYSs + delta,
                cp1XSs, cp1YSs + delta,
                cp2XSs, cp2YSs + delta,
                innerCp1XSs, innerCp1YSs + delta,
                innerCp2XSs, innerCp2YSs + delta);
        }
    }

    /**
     * Immutable slide geometry for one slide-owning note, computed during layout.
     * <p>
     * All values are in layout space: X in line-local staff spaces, Y in staff spaces relative to
     * the staff midline.
     * <p>
     * Exactly one component is ever populated. {@link StaffElement.Slide} is a sealed interface
     * permitting only {@link StaffElement.Glissando} and {@link StaffElement.Fall}, and a note owns
     * at most one slide, so the two are mutually exclusive. Both are nullable because only one
     * applies at a time, not because either computation can fail — although
     * {@link SlideGeometry#computeEndpoints} does reject a glissando too short to draw, in which
     * case no {@code SlideLayout} is stored at all.
     *
     * @param glissando  the connecting glissando's endpoints, or null when the note owns a fall
     * @param fallBounds the fall glyph's axis-aligned drawn rect, or null when the note owns a
     *                   glissando
     */
    public record SlideLayout(
        SlideGeometry.@Nullable Endpoints glissando,
        Rectangle2D.@Nullable Double fallBounds) {

        /** Creates a layout for a connecting glissando. */
        public static SlideLayout ofGlissando(SlideGeometry.Endpoints endpoints) {
            return new SlideLayout(endpoints, null);
        }

        /** Creates a layout for a trailing fall. */
        public static SlideLayout ofFall(Rectangle2D.Double bounds) {
            return new SlideLayout(null, bounds);
        }
    }

    /**
     * Immutable positioned bounds of a single above-staff decoration, computed during layout.
     * <p>
     * All values are in staff-space units.
     *
     * @param xSs      X position (left edge) of the decoration
     * @param ySs      Y position (top edge) of the decoration
     * @param dySs     Rise (in staff spaces) over the {@code widthSs} run, from the left (anchor)
     *                 edge to the right (end) edge; 0 for a flat decoration. Currently only
     *                 populated for sloped tuplet brackets — all other decoration types are flat.
     * @param widthSs  Width of the decoration
     * @param heightSs Height of the decoration
     * @param marginSs Bottom margin (space between element bottom and the tier below)
     * @param regions  Collision sub-regions for composite elements, empty for simple elements
     */
    public record DecorationLayout(
        double xSs,
        double ySs,
        double dySs,
        double widthSs,
        double heightSs,
        double marginSs,
        List<CollisionRegion> regions) {

        /**
         * Creates a flat DecorationLayout without collision sub-regions (simple elements).
         * {@code dySs} defaults to 0.0 (flat) and {@code regions} to empty.
         */
        public DecorationLayout(
            double xSs, double ySs, double widthSs,
            double heightSs, double marginSs
        ) {
            this(xSs, ySs, 0.0, widthSs, heightSs, marginSs, List.of());
        }
    }

    /**
     * Center-X and baseline-Y anchor returned by {@link #getLyricAnchor}.
     * <p>
     * All values are in staff-space units.
     *
     * @param centerXSs    horizontal center of the lyric box (or element column) in staff spaces
     * @param baselineYSs  the line's lyric text baseline Y in staff spaces
     */
    public record LyricAnchor(double centerXSs, double baselineYSs) {}
    public record LyricHit(StaffElement element, int verse) {}
}
