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
import songscribe.engraving.Staff;
import songscribe.hit.HitRegistry;

/**
 * Immutable result of the layout engine containing all positioned elements for rendering, in
 * staff-space units. The {@link DecorationLayouts} it carries is written only by the stacking pass
 * that precedes construction. Use {@link LayoutResultBuilder} to create instances.
 */
public final class LayoutResult {

    private final Map<StaffElement, ElementColumn> elementColumns;
    private final Map<Beam, BeamLayout> beamLayouts;
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
     * Use {@link LayoutResultBuilder} rather than calling this constructor directly.
     *
     * @param contentAboveStaffSs True extent of this line's content above the staff top; not the
     *                           staff top's Y position within the line
     * @param contentBelowStaffSs True extent of this line's content below the staff bottom. Never
     *                           negative; a line with nothing below its staff passes 0
     * @param overflowsStaffWidth True when the content could not fit the staff width even at its
     *                           collision floors, so the tail of the line runs past the staff
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
     * The hit registry is computed last, so it is attached to a built result without laying the
     * line out, or copying its maps, a second time.
     */
    LayoutResult withHitRegistry(HitRegistry hitRegistry) {
        return new LayoutResult(this, hitRegistry);
    }

    // ==========================================================================
    // Note Column Access
    // ==========================================================================

    /**
     * @return null if the element was not laid out
     */
    public @Nullable ElementColumn getElementColumn(StaffElement element) {
        return elementColumns.get(element);
    }

    public ElementColumn requireElementColumn(StaffElement element) {
        var column = elementColumns.get(element);

        if (column == null) {
            throw new IllegalStateException("no column laid out for " + element);
        }

        return column;
    }

    /**
     * @return 0 if the element was not laid out
     */
    public double getElementXSs(StaffElement element) {
        var column = elementColumns.get(element);
        return column != null ? column.getXSs() : 0;
    }

    /**
     * @param element an element this result laid out
     * @return a new rectangle in the hit registry's line-local, midline-relative staff spaces
     */
    public Rectangle2D.Double elementHitRectSs(StaffElement element) {
        var rectSs = new Rectangle2D.Double();
        ElementHitGeometry.elementHitRectSs(getElementXSs(element), element, rectSs);
        return rectSs;
    }

    // ==========================================================================
    // Beam + Stem Layout Access
    // ==========================================================================

    /**
     * @return null if not computed
     */
    public @Nullable BeamLayout getBeamLayout(Beam beam) {
        return beamLayouts.get(beam);
    }

    /**
     * @return null if not computed
     */
    public @Nullable StemLayout getStemLayout(StaffElement element) {
        return allStemLayouts.get(element);
    }

    // ==========================================================================
    // Tie Layout Access
    // ==========================================================================

    /**
     * @return the tie layouts, in {@link TieLayout#LINE_ORDER}; unmodifiable
     */
    public List<TieLayout> tieLayouts() {
        return tieLayouts;
    }

    // ==========================================================================
    // Slide Layout Access
    // ==========================================================================

    /**
     * Null when the note owns no slide, or when the slide has no drawable geometry — a connecting
     * glissando too short to render.
     */
    public @Nullable SlideLayout getSlideLayout(StaffElement note) {
        return slideLayouts.get(note);
    }

    // ==========================================================================
    // Decoration + Span Layout Access
    // ==========================================================================

    public DecorationLayouts decorationLayouts() {
        return decorationLayouts;
    }

    // ==========================================================================
    // Hit Testing
    // ==========================================================================

    /**
     * @return {@link HitRegistry#EMPTY} if nothing on the line is clickable
     */
    public HitRegistry getHitRegistry() {
        return hitRegistry;
    }

    // ==========================================================================
    // Staff Header Access
    // ==========================================================================

    public StaffHeader staffHeader() {
        return staffHeader;
    }

    // ==========================================================================
    // Staff Geometry
    // ==========================================================================

    /**
     * Unfloored: a line with nothing above its staff returns 0. A <em>spacing</em> value, not the
     * staff's position; for that use {@link #staffTopYSsInLine}.
     */
    public double getContentAboveStaffSs() {
        return contentAboveStaffSs;
    }

    /**
     * Unfloored: a line with nothing below its staff returns 0. A <em>spacing</em> value, not the
     * lyric anchor: the row is placed against {@link #lyricAnchorBelowStaffSs} instead.
     */
    public double getContentBelowStaffSs() {
        return contentBelowStaffSs;
    }

    /**
     * Every consumer that positions or reserves space for the lyric row must use this rather than
     * the raw extent: the drawn baseline, the hit-test row and the line's own height all hang off
     * it. Private so those three stay the only consumers.
     */
    private double lyricAnchorBelowStaffSs() {
        if (contentBelowStaffSs > 0.0) {
            return contentBelowStaffSs;
        }

        return LineSpacing.EMPTY_BELOW_STAFF_LYRIC_INSET_SS;
    }

    public double aboveMidlineSs() {
        return Staff.STAFF_HALF_SS + contentAboveStaffSs;
    }

    /** Built on {@link #lyricAnchorBelowStaffSs} so the height reserved matches where the row is drawn. */
    public double belowMidlineSs(LyricRenderMetrics lyricRenderMetrics) {
        return Staff.STAFF_HALF_SS + lyricAnchorBelowStaffSs() + lyricsBandHeightSs(lyricRenderMetrics);
    }

    /**
     * The band is one row deep whatever the line holds: only the active script is laid out, and the
     * row is reserved even before it has lyrics, so entering the first lyric does not re-space the
     * song.
     * <p>
     * Deliberately built from {@link LineSpacing#LYRICS_ROW_MARGIN_SS} rather than from
     * {@link LyricRenderMetrics#staffToLyricsGapSs}, a <em>baseline</em> offset that already
     * contains the row's above-baseline ink.
     */
    public double lyricsBandHeightSs(LyricRenderMetrics lyricRenderMetrics) {
        return LineSpacing.LYRICS_ROW_MARGIN_SS + lyricRenderMetrics.lyricBoxHeightSs();
    }

    /** Covers only this line's own content; the gap to the next line belongs to the parent layout manager. */
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

    public double paintAboveMidlineSs() {
        return Math.max(aboveMidlineSs(), LineSpacing.MIN_ABOVE_MIDLINE_SS);
    }

    public double paintBelowMidlineSs(LyricRenderMetrics lyricRenderMetrics) {
        return Math.max(belowMidlineSs(lyricRenderMetrics), LineSpacing.MIN_BELOW_MIDLINE_SS);
    }

    /** The height its bounds need for nothing it draws to be clipped; use this for sizing, not {@link #lineHeightSs}. */
    public Ss paintLineHeightSs(LyricRenderMetrics lyricRenderMetrics) {
        return new Ss(paintAboveMidlineSs() + paintBelowMidlineSs(lyricRenderMetrics));
    }

    /**
     * Derived from {@link #paintAboveMidlineSs}, not from {@code contentAboveStaffSs}: this is a
     * <em>geometry</em> query, and {@code LineComponent} draws the staff at the painted midline.
     */
    public double staffTopYSsInLine() {
        return paintAboveMidlineSs() - Staff.STAFF_HALF_SS;
    }

    /** Anchored on the painted midline for the reason given in {@link #staffTopYSsInLine}. */
    public double staffBottomYSsInLine() {
        return paintAboveMidlineSs() + Staff.STAFF_HALF_SS;
    }

    /**
     * Driven by this line's own below-staff content, so lyrics hug each line individually. One row
     * per line, not per script: whichever script is active sits on this baseline.
     */
    public double lyricBaselineYSsInLine(LyricRenderMetrics lyricRenderMetrics) {
        return staffBottomYSsInLine()
            + lyricAnchorBelowStaffSs()
            + lyricRenderMetrics.staffToLyricsGapSs();
    }

    // ==========================================================================
    // Lyric Layout
    // ==========================================================================

    /** At most one, for the script this layout pass was built for. */
    public List<LyricBoxLayout> getLyricBoxes(StaffElement element) {
        var boxes = lyricBoxes.get(element);
        return boxes != null ? boxes : List.of();
    }

    /**
     * The placeholder takes no space, and a renderer decides whether to draw it.
     *
     * @return null when the element has a syllable or cannot bear one
     */
    public @Nullable LyricBoxLayout getLyricPlaceholder(StaffElement element) {
        return lyricPlaceholders.get(element);
    }

    /**
     * @param box                a syllable box ({@link #getLyricBoxes}) or a placeholder box
     *                           ({@link #getLyricPlaceholder}) of this result
     * @param lyricRenderMetrics the metrics this result was laid out with
     * @return a new rectangle in the hit registry's line-local, midline-relative staff spaces
     */
    public Rectangle2D lyricBoxRectSs(LyricBoxLayout box, LyricRenderMetrics lyricRenderMetrics) {
        // The lyric boxes are the one geometry source in line-top origin. paintAboveMidlineSs()
        // is the distance from the top of the line's component to the staff midline, so
        // subtracting it puts the row in the midline-relative space every other region uses.
        var rowTopYSs = lyricAreaBaseYSs() - paintAboveMidlineSs();
        return new Rectangle2D.Double(box.xSs(), rowTopYSs, box.widthSs(), lyricRenderMetrics.lyricBoxHeightSs());
    }

    /**
     * The maximum rather than the last column's edge: a wide trailing extent anywhere in the chain
     * still has to be cleared. The renderer and the hit test place the cautionary key signature
     * past it and must agree to the pixel.
     *
     * @return the rightmost solved column edge, or 0 when the chain holds no columns
     */
    public double contentRightEdgeSs() {
        return elementColumns.values().stream()
            .mapToDouble(ElementColumn::getRightEdgeXSs)
            .max()
            .orElse(0);
    }

    public @Nullable Line hitTestHeaderKeyEdit(double mouseXSs, Line line) {
        return hitTester.hitTestHeaderKeyEdit(mouseXSs, line);
    }

    public @Nullable Line hitTestCautionaryKeyEdit(double mouseXSs, Line line) {
        return hitTester.hitTestCautionaryKeyEdit(mouseXSs, line);
    }

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

    /** The Y is the line's single lyric baseline. */
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

    public List<LyricConnectorLayout> getLyricConnectors() {
        return lyricConnectors;
    }

    public boolean hasTrailingLyricContinuation() {
        return hasTrailingLyricContinuation;
    }

    /**
     * True when this line's content could not fit the staff width even with every gap compressed
     * to its collision floor. The columns were placed on those floors, and the tail runs past the
     * staff, where the component's bounds clip it.
     */
    public boolean overflowsStaffWidth() {
        return overflowsStaffWidth;
    }

    // ==========================================================================
    // Preview Element Positioning (Edit Mode)
    // ==========================================================================

    /**
     * @return {@code null} when no element's glyph body contains the X
     */
    public @Nullable Integer findElementAtXSs(double mouseXSs, Line line) {
        return hitTester.findElementAtXSs(mouseXSs, line);
    }

    public int findInsertionIndex(double mouseXSs, Line line) {
        return hitTester.findInsertionIndex(mouseXSs, line);
    }

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
     * @param lengtheningSs Extra stem extension beyond the natural minimum length (≥ 0): for a beamed
     *                   note, the amount needed to reach the beam; for an unbeamed note, the amount
     *                   needed to extend the tip to the staff center (Y=0)
     * @param forcedShorteningSs Amount (≥ 0, ss) this stem falls short of the natural minimum length;
     *                   0 for every natural, auto-direction, and grace stem. Bracket-independent —
     *                   not a tuplet trim.
     * @param frenchShorteningLevels Number of beam levels (not staff spaces) the <em>drawn</em> stem
     *                   stops short of the outer beam under French beaming; 0 for unbeamed notes. See
     *                   {@link BeamMath#frenchBeamShortening}. The logical tip is unaffected.
     */
    public record StemLayout(
        double topYSs,
        double bottomYSs,
        double lengtheningSs,
        double forcedShorteningSs,
        int frenchShorteningLevels) {}

    /**
     * @param direction  The direction every stem of the group points ({@link Beam#direction()})
     * @param thickeningSs Extra beam thickness from the {@code 1/cos(angle)} raster correction (ss);
     *                   added symmetrically to the nominal {@code BEAM_DEPTH}
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
     * @param slope the line's Y-down rise per unit of X (dimensionless)
     */
    public record BeamLine(double originXSs, double originYSs, double slope) {

        public double yAtSs(double xSs) {
            return originYSs + slope * (xSs - originXSs);
        }
    }

    /**
     * The outer and inner curves form a filled lens when rendered as a closed path: the outer cubic
     * from start to end, then the inner in reverse. A half running off an edge of the line is a
     * complete arc over its own width that returns to the baseline at the edge.
     */
    public record TieLayout(
        Line.TiePlacement placement,
        double startXSs, double startYSs,
        double endXSs, double endYSs,
        double cp1XSs, double cp1YSs,
        double cp2XSs, double cp2YSs,
        double innerCp1XSs, double innerCp1YSs,
        double innerCp2XSs, double innerCp2YSs) {

        /** The order {@link Line#ownedTies} lists them. */
        public static final Comparator<TieLayout> LINE_ORDER = Comparator
            .<TieLayout>comparingInt(layout -> layout.placement().range().begin())
            .thenComparing(layout -> layout.placement().holdsAnchor());
    }

    /**
     * Exactly one component is ever populated: a note owns at most one slide.
     *
     * @param glissando  the connecting glissando's endpoints, or null when the note owns a fall
     * @param fallBounds the fall glyph's axis-aligned drawn rect, or null when the note owns a
     *                   glissando
     */
    public record SlideLayout(
        SlideGeometry.@Nullable Endpoints glissando,
        Rectangle2D.@Nullable Double fallBounds) {

        public static SlideLayout ofGlissando(SlideGeometry.Endpoints endpoints) {
            return new SlideLayout(endpoints, null);
        }

        public static SlideLayout ofFall(Rectangle2D.Double bounds) {
            return new SlideLayout(null, bounds);
        }
    }

    /**
     * Positioned bounds of a single above-staff decoration. Each kind is its own member, recorded
     * and read back through the typed entries of {@link DecorationLayouts.Builder}, so a reader
     * never has to ask which member it holds.
     */
    public sealed interface DecorationLayout
        permits DecorationLayout.Plain, DecorationLayout.Typeset, DecorationLayout.Sloped,
                DecorationLayout.Volta {

        double xSs();

        double ySs();

        double widthSs();

        double heightSs();

        double marginSs();

        /**
         * The drawn box of the decoration, with nothing added. For a {@link Sloped} tuplet bracket
         * the box does not follow the tilt; the hit registry gives a tuplet its own shape.
         */
        default Rectangle2D.Double inkRectSs() {
            return new Rectangle2D.Double(xSs(), ySs(), widthSs(), heightSs());
        }

        /**
         * Each member implements this itself, so a component added to one of them cannot be
         * silently dropped here and make a nudged decoration vanish from the score.
         */
        DecorationLayout shiftedBy(double dxSs, double dySsShift);

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
         * The bounds are the content's own, so the box hit testing and vertical stacking measured is
         * the box the renderer draws into.
         */
        record Typeset(
            double xSs,
            double ySs,
            double widthSs,
            double heightSs,
            double marginSs,
            DecorationContent content) implements DecorationLayout {

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

        /** {@code dySs} is the bracket line's rise over {@code widthSs}, from {@code xSs}; negative means an ascending contour. */
        record Sloped(
            double xSs,
            double ySs,
            double dySs,
            double widthSs,
            double heightSs,
            double marginSs) implements DecorationLayout {

            /** The slope holds past both ends, so an X outside the run extrapolates it. */
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
         * The brackets are layout output, derived from the columns of the line they were laid out
         * on, so they live here rather than on the ending. The horizontal bounds are the brackets'
         * own, so they are derived from the pair rather than stored beside it.
         *
         * @param brackets the ending's two brackets, in the line's layout space
         */
        record Volta(
            double ySs,
            double heightSs,
            double marginSs,
            BracketRange.Pair brackets) implements DecorationLayout {

            /**
             * @param placed its horizontal bounds are the brackets' own, so only its Y, height and
             *               margin are carried
             */
            public static Volta of(Plain placed, BracketRange.Pair brackets) {
                return new Volta(placed.ySs(), placed.heightSs(), placed.marginSs(), brackets);
            }

            @Override
            public double xSs() {
                return brackets.first().x1Ss();
            }

            @Override
            public double widthSs() {
                return brackets.second().x2Ss() - brackets.first().x1Ss();
            }

            @Override
            public Volta shiftedBy(double dxSs, double dySsShift) {
                return new Volta(ySs + dySsShift, heightSs, marginSs, brackets.shiftedBy(dxSs));
            }
        }
    }

    public record LyricAnchor(Ss centerXSs, Ss baselineYSs) {}
}
