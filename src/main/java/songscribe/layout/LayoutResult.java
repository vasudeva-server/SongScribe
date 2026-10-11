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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Beam;
import songscribe.dom.Direction;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.engraving.Staff;
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
 * This class is immutable after construction; the {@link DecorationLayouts} it carries is written
 * only by the stacking pass that precedes construction. Use {@link LayoutResultBuilder} to
 * create instances.
 */
public final class LayoutResult {

    private final Map<StaffElement, ElementColumn> elementColumns;
    private final Map<Beam, BeamLayout> beamLayouts;
    /**
     * Flat lookup keyed by element for every stem in the line — beamed stems
     * (extracted from {@link BeamLayout#stems()}) and unbeamed stems alike.
     * Built once at construction so per-element render lookups are O(1) instead
     * of O(beam-count).
     */
    private final Map<StaffElement, StemLayout> allStemLayouts;
    private final List<TieLayout> tieLayouts;
    private final DecorationLayouts decorationLayouts;
    private final Map<StaffElement, SlideLayout> slideLayouts;
    private final StaffHeader staffHeader;
    private final double contentAboveStaffSs;
    private final double contentBelowStaffSs;
    private final Map<StaffElement, List<LyricBoxLayout>> lyricBoxes;
    private final Map<StaffElement, LyricBoxLayout> lyricPlaceholders;
    private final List<LyricConnectorLayout> lyricConnectors;
    private final boolean hasTrailingLyricContinuation;
    private final boolean overflowsStaffWidth;
    private final HitRegistry hitRegistry;
    private final LayoutHitTester hitTester;

    /**
     * Creates a layout result with the given data.
     * <p>
     * Use {@link LayoutResultBuilder} rather than calling this constructor directly.
     *
     * @param elementColumns   Map of elements to their columns with positions
     * @param beamLayouts      Map of beam spans to their computed beam geometry
     * @param stemLayouts      Map of unbeamed notes to their computed stem geometry
     * @param tieLayouts       The computed geometry of each tie this line draws, in any order
     * @param decorationLayouts The decoration table the stacking pass filled for this line
     * @param slideLayouts     Map of slide-owning notes to their computed slide geometry
     * @param staffHeader      The clef and key signature this line opens with
     * @param contentAboveStaffSs True extent of this line's content above the staff top, in staff
     *                           spaces, and also the staff top's Y position within the line's local
     *                           coordinate frame
     * @param contentBelowStaffSs True extent of this line's content below the staff bottom, in staff
     *                           spaces. Never negative; a line with nothing below its staff passes 0
     * @param overflowsStaffWidth True when the line's content could not fit the staff width even at
     *                           its collision floors, so the columns were placed on those floors and
     *                           the tail of the line runs past the staff and is clipped
     * @param hitRegistry        Every clickable area of the line, in layout space
     */
    // Package-private rather than private so LayoutResultBuilder, the only intended caller,
    // can reach it from its own file.
    LayoutResult(
        Map<StaffElement, ElementColumn> elementColumns,
        Map<Beam, BeamLayout> beamLayouts,
        Map<StaffElement, StemLayout> stemLayouts,
        List<TieLayout> tieLayouts,
        DecorationLayouts decorationLayouts,
        Map<StaffElement, SlideLayout> slideLayouts,
        StaffHeader staffHeader,
        double contentAboveStaffSs,
        double contentBelowStaffSs,
        Map<StaffElement, List<LyricBoxLayout>> lyricBoxes,
        Map<StaffElement, LyricBoxLayout> lyricPlaceholders,
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
        this.tieLayouts = tieLayouts.stream().sorted(TieLayout.LINE_ORDER).toList();
        this.decorationLayouts = decorationLayouts;
        this.slideLayouts = Map.copyOf(slideLayouts);
        this.staffHeader = staffHeader;
        this.contentAboveStaffSs = contentAboveStaffSs;
        this.contentBelowStaffSs = contentBelowStaffSs;
        var copiedBoxes = new HashMap<StaffElement, List<LyricBoxLayout>>(lyricBoxes.size() * 2);

        for (var entry : lyricBoxes.entrySet()) {
            copiedBoxes.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        this.lyricBoxes = Collections.unmodifiableMap(copiedBoxes);
        this.lyricPlaceholders = Map.copyOf(lyricPlaceholders);
        this.lyricConnectors = List.copyOf(lyricConnectors);
        this.hasTrailingLyricContinuation = hasTrailingLyricContinuation;
        this.overflowsStaffWidth = overflowsStaffWidth;
        this.hitRegistry = hitRegistry;
        hitTester = new LayoutHitTester(this);
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
        staffHeader = source.staffHeader;
        contentAboveStaffSs = source.contentAboveStaffSs;
        contentBelowStaffSs = source.contentBelowStaffSs;
        lyricBoxes = source.lyricBoxes;
        lyricPlaceholders = source.lyricPlaceholders;
        lyricConnectors = source.lyricConnectors;
        hasTrailingLyricContinuation = source.hasTrailingLyricContinuation;
        overflowsStaffWidth = source.overflowsStaffWidth;
        this.hitRegistry = hitRegistry;
        // The copy answers hit tests against itself, not against source.
        hitTester = new LayoutHitTester(this);
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
     * Returns the element column of an element this line laid out, for a caller holding a
     * decoration the stacking pass placed from that column.
     *
     * @param element an element of this line
     * @return the element's column
     * @throws IllegalStateException if {@code element} was not laid out on this line
     */
    public ElementColumn requireElementColumn(StaffElement element) {
        var column = elementColumns.get(element);

        if (column == null) {
            throw new IllegalStateException("no column laid out for " + element);
        }

        return column;
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
     * Returns the laid-out ties of this line: one per tie or tie half the line owns
     * ({@link Line#ownedTies}) whose notes here have columns.
     *
     * @return the tie layouts, in {@link TieLayout#LINE_ORDER}; unmodifiable
     */
    public List<TieLayout> tieLayouts() {
        return tieLayouts;
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
     * Returns the decoration table of this line: the placed bounds of every decorated element,
     * queried by element, by type, or by the element a decoration hangs off.
     *
     * @return the line's decoration layouts; empty when nothing on the line is decorated
     */
    public DecorationLayouts decorationLayouts() {
        return decorationLayouts;
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
    // Staff Header Access
    // ==========================================================================

    /**
     * Returns the header this line opens with — the clef and the key signature, at the positions
     * layout gave them. Every line has one, an empty line included.
     *
     * @return the line's staff header
     */
    public StaffHeader staffHeader() {
        return staffHeader;
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
     * Returns the true extent of this line's content below the staff bottom, in staff spaces:
     * the maximum distance below the staff bottom that anything on this line reaches.
     * Unfloored: a line with nothing below its staff returns 0.
     * <p>
     * This is a <em>spacing</em> value, not the lyric anchor — a line with nothing below its
     * staff still holds its lyrics an inset clear of the staff bottom, so the row is placed
     * against {@link #lyricAnchorBelowStaffSs} rather than against this.
     */
    public double getContentBelowStaffSs() {
        return contentBelowStaffSs;
    }

    /**
     * Returns the below-staff extent the lyric row is anchored to, in staff spaces:
     * {@link #getContentBelowStaffSs} when this line has any below-staff content, and
     * {@link LineSpacing#EMPTY_BELOW_STAFF_LYRIC_INSET_SS} when it has none.
     * <p>
     * Every consumer that positions or reserves space for the lyric row must use this rather
     * than the raw extent — the drawn baseline, the hit-test row, and the line's own height all
     * hang off it, and anchoring only some of them here would leave the clickable row somewhere
     * other than the text.
     * <p>
     * Private so those three stay the only consumers: this and {@link #getContentBelowStaffSs}
     * return different numbers for the same-sounding quantity, and nothing outside this class
     * should have to know which one it wants.
     */
    private double lyricAnchorBelowStaffSs() {
        if (contentBelowStaffSs > 0.0) {
            return contentBelowStaffSs;
        }

        return LineSpacing.EMPTY_BELOW_STAFF_LYRIC_INSET_SS;
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
     * <p>
     * Built on {@link #lyricAnchorBelowStaffSs} so the height reserved matches where the row is
     * actually drawn: on a line with nothing below its staff the row sits an extra inset down,
     * and the line has to grow by the same amount or the lyrics fall outside their own bounds.
     */
    public double belowMidlineSs(LyricRenderMetrics lyricRenderMetrics) {
        return Staff.STAFF_HALF_SS + lyricAnchorBelowStaffSs() + lyricsBandHeightSs(lyricRenderMetrics);
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
    public Ss paintLineHeightSs(LyricRenderMetrics lyricRenderMetrics) {
        return new Ss(paintAboveMidlineSs() + paintBelowMidlineSs(lyricRenderMetrics));
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
     * spaces. Driven by this line's own below-staff content — see
     * {@link #lyricAnchorBelowStaffSs} — so lyrics hug each line individually rather than a
     * song-wide maximum.
     * <p>
     * There is one such row per line, not one per verse: the song shows a single verse at a time,
     * so whichever verse is active sits on this baseline.
     */
    public double lyricBaselineYSsInLine(LyricRenderMetrics lyricRenderMetrics) {
        return staffBottomYSsInLine()
            + lyricAnchorBelowStaffSs()
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

    /**
     * Returns the placeholder box for an element: a box of
     * {@link LyricLayoutBuilder#PLACEHOLDER_TEXT} placed exactly as a syllable of that text would
     * be. Layout gives one to every note that can bear a syllable but has none in the verse this
     * pass was built for; the placeholder takes no space, and a renderer decides whether to draw
     * it.
     *
     * @param element the element to look up
     * @return the placeholder box, or null when the element has a syllable or cannot bear one
     */
    public @Nullable LyricBoxLayout getLyricPlaceholder(StaffElement element) {
        return lyricPlaceholders.get(element);
    }

    /**
     * Returns whether a point lies on a lyric box, tested exactly as the hit registry tests the
     * region it holds for a syllable there.
     * <p>
     * For a box the registry does not hold: a placeholder, which only a renderer decides to draw.
     *
     * @param box                a syllable or placeholder box of this result
     * @param lyricRenderMetrics song-wide lyric render metrics providing the row height
     * @param pointSs            the point, in the hit registry's line-local, midline-relative
     *                           staff spaces
     * @return true when the point lies on the box
     */
    public boolean lyricBoxContains(LyricBoxLayout box, LyricRenderMetrics lyricRenderMetrics, Point2D pointSs) {
        return HitRegionBuilder.inclusiveOf(lyricBoxRectSs(box, lyricRenderMetrics)).contains(pointSs);
    }

    /**
     * Returns the region a lyric box answers to: the box's horizontal extent across the full
     * height of the lyric row, in the hit registry's midline-relative staff spaces.
     */
    public Rectangle2D lyricBoxRectSs(LyricBoxLayout box, LyricRenderMetrics lyricRenderMetrics) {
        // The lyric boxes are the one geometry source in line-top origin. paintAboveMidlineSs()
        // is the distance from the top of the line's component to the staff midline, so
        // subtracting it puts the row in the midline-relative space every other region uses.
        var rowTopYSs = lyricAreaBaseYSs() - paintAboveMidlineSs();
        return new Rectangle2D.Double(box.xSs(), rowTopYSs, box.widthSs(), lyricRenderMetrics.lyricBoxHeightSs());
    }

    /**
     * Delegates to {@link LayoutHitTester#hitTestLyric}.
     */
    public @Nullable LyricHit hitTestLyric(LyricRenderMetrics lyricRenderMetrics, Line line, Point2D pointPx) {
        return hitTester.hitTestLyric(lyricRenderMetrics, line, pointPx);
    }

    /**
     * Returns the X of the rightmost edge any column of this result's solved chain reaches, which
     * is where the line's drawn content ends.
     *
     * <p>The maximum rather than the last column's edge: a wide trailing extent anywhere in the
     * chain still has to be cleared, and on an overflowing line this is what the cautionary key
     * signature is placed past — by the renderer that draws it and by the hit test that finds it,
     * which must agree to the pixel or the target drifts off the glyphs.
     *
     * @return the rightmost solved column edge, or 0 when the chain holds no columns
     */
    public double contentRightEdgeSs() {
        return elementColumns.values().stream()
            .mapToDouble(ElementColumn::getRightEdgeXSs)
            .max()
            .orElse(0);
    }

    /**
     * Delegates to {@link LayoutHitTester#hitTestHeaderKeyEdit}.
     */
    public @Nullable Line hitTestHeaderKeyEdit(double mouseXSs, Line line) {
        return hitTester.hitTestHeaderKeyEdit(mouseXSs, line);
    }

    /**
     * Delegates to {@link LayoutHitTester#hitTestCautionaryKeyEdit}.
     */
    public @Nullable Line hitTestCautionaryKeyEdit(double mouseXSs, Line line) {
        return hitTester.hitTestCautionaryKeyEdit(mouseXSs, line);
    }

    /**
     * Delegates to {@link LayoutHitTester#hitTestMidLineKeyEdit}.
     */
    public @Nullable KeyChangeElement hitTestMidLineKeyEdit(double mouseXSs, Line line) {
        return hitTester.hitTestMidLineKeyEdit(mouseXSs, line);
    }

    // Package-private for direct unit testing of the formula.
    //
    // Built on staffBottomYSsInLine() so the hit-test row rides the same painted frame as the
    // baseline LyricTextRenderer draws at. Anchoring this on contentAboveStaffSs instead put
    // the clickable row above the visible text on any line short enough to hit the floor.
    double lyricAreaBaseYSs() {
        return staffBottomYSsInLine() + lyricAnchorBelowStaffSs() + LineSpacing.LYRICS_ROW_MARGIN_SS;
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
            return new LyricAnchor(new Ss(centerXSs), new Ss(baselineYSs));
        }

        var column = getElementColumn(element);

        if (column == null) {
            throw new IllegalStateException(
                "getLyricAnchor: no lyric box and no column for element " + element);
        }

        var centerXSs = column.getNoteheadCenterXSs();
        return new LyricAnchor(new Ss(centerXSs), new Ss(baselineYSs));
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
     * Delegates to {@link LayoutHitTester#findElementAtXSs}.
     *
     * @return the element index, or {@code null} when no element's glyph body contains the X
     */
    public @Nullable Integer findElementAtXSs(double mouseXSs, Line line) {
        return hitTester.findElementAtXSs(mouseXSs, line);
    }

    /**
     * Delegates to {@link LayoutHitTester#findInsertionIndex}.
     */
    public int findInsertionIndex(double mouseXSs, Line line) {
        return hitTester.findInsertionIndex(mouseXSs, line);
    }

    /**
     * Delegates to {@link LayoutHitTester#calculateInsertionXSs}.
     */
    public double calculateInsertionXSs(
        int insertionIndex,
        double mouseXSs,
        StaffElement previewElement,
        Line line,
        boolean betweenElementsOnly) {

        return hitTester.calculateInsertionXSs(
            insertionIndex, mouseXSs, previewElement, line, betweenElementsOnly);
    }

    // ==========================================================================
    // Statistics
    // ==========================================================================


    // ==========================================================================
    // Builder
    // ==========================================================================

    /**
     * Creates a new builder for a LayoutResult opening with {@code staffHeader}.
     *
     * @param staffHeader the clef and key signature the line opens with
     * @return A new builder instance
     */
    public static LayoutResultBuilder builder(StaffHeader staffHeader) {
        return new LayoutResultBuilder(staffHeader);
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
        int frenchShorteningLevels) {}

    /**
     * Immutable beam geometry for a beam group, computed during layout.
     * <p>
     * All values are in staff-space units unless noted.
     *
     * @param line       The outer edge of the outermost beam
     * @param direction  The direction every stem of the group points ({@link Beam#direction()}),
     *                   computed once for the layout
     * @param thickeningSs Extra beam thickness from the {@code 1/cos(angle)} raster correction (ss);
     *                   added symmetrically to the nominal {@code BEAM_DEPTH}
     * @param stems      Stem geometry keyed by element, for every element in this beam group
     */
    public record BeamLayout(
        BeamLine line,
        Direction direction,
        double thickeningSs,
        Map<StaffElement, StemLayout> stems) {}

    /**
     * The outer edge of a beam group's outermost beam: a straight line in layout space, Y-down
     * positive, measured from the first member's column.
     *
     * @param originXSs the X the line is measured from: the first member's column X
     * @param originYSs the line's Y at {@code originXSs}
     * @param slope     the line's Y-down rise per unit of X (dimensionless)
     */
    public record BeamLine(double originXSs, double originYSs, double slope) {

        /**
         * Returns the line's Y at {@code xSs}.
         *
         * @param xSs an X in layout space
         * @return the Y of the beam's outer edge at {@code xSs}
         */
        public double yAtSs(double xSs) {
            return originYSs + slope * (xSs - originXSs);
        }
    }

    /**
     * Immutable tie geometry, computed during layout.
     * <p>
     * All values are in staff-space units. The outer and inner curves form a filled
     * lens shape when rendered as a closed path: draw the outer cubic Bezier from
     * start to end, then draw the inner cubic Bezier in reverse (end back to start),
     * then close and fill.
     * <p>
     * A half running off an edge of the line is described by the same twelve numbers as a whole
     * tie: it is a complete arc over its own width that returns to the baseline at the edge, so
     * nothing downstream reconstructs the side that is missing.
     *
     * @param placement   The tie this geometry draws and its place in the line
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
        Line.TiePlacement placement,
        double startXSs, double startYSs,
        double endXSs, double endYSs,
        double cp1XSs, double cp1YSs,
        double cp2XSs, double cp2YSs,
        double innerCp1XSs, double innerCp1YSs,
        double innerCp2XSs, double innerCp2YSs) {

        /**
         * The order of the notes the ties of one line were found on, as {@link Line#ownedTies}
         * lists them: by the first element each is drawn over, and the half entering from the
         * left edge before a tie starting on the line's first element.
         */
        public static final Comparator<TieLayout> LINE_ORDER = Comparator
            .<TieLayout>comparingInt(layout -> layout.placement().range().begin())
            .thenComparing(layout -> layout.placement().holdsAnchor());
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
     * <p>
     * A decoration either draws itself from the model — a hairpin, an articulation — or draws
     * {@link DecorationContent} the layout pass typeset for it. The tuplet bracket is the one
     * decoration whose line tilts, so it carries its own member with the extra {@code dySs} rise
     * rather than every other kind carrying an always-zero field. An ending likewise carries its
     * own member with the {@link BracketRange.Pair} layout computed for it, so the renderer draws
     * the brackets the stacking pass measured.
     * <p>
     * Each kind of decoration is recorded as its own member, through the typed entries of
     * {@link DecorationLayouts.Builder}, and read back through the same types: a reader of
     * {@link Typeset#content()}, {@link Sloped#dySs()} or {@link Volta#brackets()} never has to
     * ask which member it holds.
     */
    public sealed interface DecorationLayout
        permits DecorationLayout.Plain, DecorationLayout.Typeset, DecorationLayout.Sloped,
                DecorationLayout.Volta {

        /** X position (left edge) of the decoration. */
        double xSs();

        /** Y position (top edge) of the decoration. */
        double ySs();

        /** Width of the decoration. */
        double widthSs();

        /** Height of the decoration. */
        double heightSs();

        /** Bottom margin: the space between the element's bottom and the tier below. */
        double marginSs();

        /**
         * Returns a copy shifted by the given offsets, with every other component carried
         * through.
         * <p>
         * User X and Y offsets are applied by rebuilding the layout. Each member implements this
         * itself, so a component added to one of them cannot be silently dropped here — a dropped
         * {@code content} makes a nudged decoration vanish from the score with no exception and
         * nothing to catch it.
         *
         * @param dxSs      shift applied to the left edge
         * @param dySsShift shift applied to the top edge
         */
        DecorationLayout shiftedBy(double dxSs, double dySsShift);

        /**
         * The layout of a decoration that draws itself, such as a hairpin or an articulation. It
         * carries bounds and nothing else.
         */
        record Plain(
            double xSs,
            double ySs,
            double widthSs,
            double heightSs,
            double marginSs) implements DecorationLayout {

            @Override
            public Plain shiftedBy(double dxSs, double dySsShift) {
                return new Plain(xSs + dxSs, ySs + dySsShift, widthSs, heightSs, marginSs);
            }
        }

        /**
         * The layout of a decoration that draws the {@link DecorationContent} the layout pass
         * typeset for it: a metronome marking or the attribution block.
         * <p>
         * The bounds are the content's own, so the box hit testing and vertical stacking measured
         * is the box the renderer draws into.
         */
        record Typeset(
            double xSs,
            double ySs,
            double widthSs,
            double heightSs,
            double marginSs,
            DecorationContent content) implements DecorationLayout {

            /**
             * Returns the layout of a decoration placed at {@code placed}'s bounds drawing
             * {@code content}.
             *
             * @param placed  the bounds the stacking pass placed the content's box at
             * @param content the typeset content drawn into those bounds
             * @return a layout with {@code placed}'s bounds carrying {@code content}
             */
            public static Typeset of(Plain placed, DecorationContent content) {
                return new Typeset(
                    placed.xSs(), placed.ySs(), placed.widthSs(), placed.heightSs(),
                    placed.marginSs(), content);
            }

            @Override
            public Typeset shiftedBy(double dxSs, double dySsShift) {
                return new Typeset(xSs + dxSs, ySs + dySsShift, widthSs, heightSs, marginSs, content);
            }
        }

        /**
         * The layout of a tuplet bracket, the one decoration whose line tilts: {@code dySs} is
         * the bracket line's rise over {@code widthSs}, measured from {@code xSs}; negative means
         * an ascending contour.
         */
        record Sloped(
            double xSs,
            double ySs,
            double dySs,
            double widthSs,
            double heightSs,
            double marginSs) implements DecorationLayout {

            /**
             * Returns the Y of the sloped top edge at {@code xSs}: the line through
             * ({@code xSs()}, {@code ySs}) that rises {@code dySs} over {@code widthSs}. The
             * slope holds past both ends, so an X outside the run extrapolates it.
             *
             * @param xSs an X in layout space
             * @return the top edge's Y at {@code xSs}
             */
            public double topYAtSs(double xSs) {
                // widthSs is always positive: the stacker floors it at TupletGeometry.MIN_SPAN_WIDTH_SS.
                return ySs + dySs / widthSs * (xSs - this.xSs);
            }

            @Override
            public Sloped shiftedBy(double dxSs, double dySsShift) {
                return new Sloped(xSs + dxSs, ySs + dySsShift, dySs, widthSs, heightSs, marginSs);
            }
        }

        /**
         * The layout of an ending: its two visual brackets, which the renderer draws at the
         * layout's Y, and the vertical bounds the stacking pass placed them at.
         * <p>
         * The brackets are layout output, derived from the columns of the line they were laid out
         * on, so they live here rather than on the ending: a reader holding this layout holds the
         * brackets that were measured when these bounds were placed. The horizontal bounds are the
         * brackets' own, so they are derived from the pair rather than stored beside it.
         *
         * @param ySs      Y position (top edge) of the ending
         * @param heightSs height of the ending
         * @param marginSs bottom margin between the ending's bottom and the tier below
         * @param brackets the ending's two brackets, in the line's layout space
         */
        record Volta(
            double ySs,
            double heightSs,
            double marginSs,
            BracketRange.Pair brackets) implements DecorationLayout {

            /**
             * Returns the layout of an ending whose brackets were placed at {@code placed}'s
             * vertical bounds.
             *
             * @param placed   the bounds the stacking pass placed the ending at; its horizontal
             *                 bounds are the brackets' own, so only its Y, height and margin are
             *                 carried
             * @param brackets the ending's two brackets
             * @return a layout at {@code placed}'s Y carrying {@code brackets}
             */
            public static Volta of(Plain placed, BracketRange.Pair brackets) {
                return new Volta(placed.ySs(), placed.heightSs(), placed.marginSs(), brackets);
            }

            /**
             * Returns the left edge of the ending: the first bracket's {@link BracketRange#x1Ss}.
             *
             * @return the ending's left edge, in staff spaces
             */
            @Override
            public double xSs() {
                return brackets.first().x1Ss();
            }

            /**
             * Returns the width of the ending, from the first bracket's {@link BracketRange#x1Ss}
             * to the second bracket's {@link BracketRange#x2Ss}.
             *
             * @return the ending's width, in staff spaces
             */
            @Override
            public double widthSs() {
                return brackets.second().x2Ss() - brackets.first().x1Ss();
            }

            /**
             * {@inheritDoc}
             * <p>
             * The brackets move with the bounds: both shift by {@code dxSs}.
             */
            @Override
            public Volta shiftedBy(double dxSs, double dySsShift) {
                return new Volta(ySs + dySsShift, heightSs, marginSs, brackets.shiftedBy(dxSs));
            }
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
    public record LyricAnchor(Ss centerXSs, Ss baselineYSs) {}
    public record LyricHit(StaffElement element, int verse) {}
}
