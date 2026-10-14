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

package songscribe.layout.stacking;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.NoteElement;
import songscribe.dom.StaffElement;
import songscribe.dom.Direction;
import songscribe.dom.Trill;
import songscribe.layout.Chord;
import songscribe.layout.DecorationLayouts;
import songscribe.layout.ElementAtColumn;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementSeat;
import songscribe.layout.Footprint;
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.layout.ShapeProfile;
import songscribe.layout.StaffExtents;
import songscribe.layout.SupportPadding;
import songscribe.layout.TrillGeometry;
import songscribe.shape.AccentShape;
import songscribe.smufl.BravuraFont;
import songscribe.smufl.SMuFLGlyph;

/**
 * Seeds note bounds and stacks note-attached decorations (tiers 1-2).
 * <p>
 * The two tiers are separate entry points, and the tuplet-bracket pass runs between them. See
 * {@link VerticalStackingCalculator} for the full tier order and why it is this way.
 */
public class NoteAttachedStacker {

    public static final double STACCATO_PADDING_SS = 0.20;  // LilyPond script.scm staccato

    public static final double ACCENT_PADDING_SS = 0.20;  // LilyPond script.scm accent

    public static final double FERMATA_PADDING_SS = 0.466;  // LilyPond script.scm fermata

    // The fermata looks no further than its own bbox when it clears what lies beneath it. A tie
    // arcs upward toward its center, so under the fermata's footprint the tie is highest at the
    // fermata's inner bbox corner (the edge toward the arc's center). Sampling with no horizon
    // padding makes the placement take the tie's Y at exactly that corner — the ray traced straight
    // down from the inner bottom corner of the fermata's box — and seat the fermata FERMATA_PADDING_SS
    // above it. A non-zero horizon would sample SCRIPT_HORIZON_PADDING_SS further up the rising arc
    // and over-clear it.
    private static final double FERMATA_HORIZON_PADDING_SS = 0.0;

    public static final double TRILL_SCRIPT_PADDING_SS = 0.40;  // LilyPond script.scm trill is 0.20

    // LilyPond define-grobs.scm TrillSpanner padding is 0.50
    public static final double TRILL_SPANNER_PADDING_SS = 0.70;

    // LilyPond define-grobs.scm Script staff-padding
    public static final double SCRIPT_STAFF_PADDING_SS = 0.25;

    // The fermata sits above the staff and keeps its full padding from the staff line as well as
    // from a tie or notehead.
    private static final AlignedSide FERMATA_SIDE = new AlignedSide(Direction.UP,
        FERMATA_PADDING_SS, scriptStaffPaddingSs(FERMATA_PADDING_SS), FERMATA_HORIZON_PADDING_SS);

    // LilyPond define-grobs.scm DynamicLineSpanner staff-padding
    public static final double DYNAMIC_STAFF_PADDING_SS = 0.10;

    // LilyPond define-grobs.scm DynamicLineSpanner padding
    public static final double DYNAMIC_PADDING_SS = 0.60;

    // The accent's inner edge, derived once from the wedge that ArticulationRenderer actually draws.
    // Unlike every other script, the accent's staff-facing boundary is a sloping arm, not the edge
    // of its box: at its tip the outline has receded a full half-height from the box. Stacking the
    // box against a tie arc pushes the accent roughly twice as far out as LilyPond does, because a
    // box collides where the glyph has long since sloped away (LilyPond builds a Script's skyline
    // from the stencil outline — define-grobs.scm always-vertical-skylines-from-stencil).
    //
    // The wedge is symmetric about its horizontal axis, so the two profiles are mirror images;
    // deriving each from the outline rather than flipping one keeps that an observation, not a
    // premise. Asserted in ShapeProfileTest.
    private static final StaffExtents.Profile ACCENT_PROFILE_ABOVE =
        ShapeProfile.innerEdge(AccentShape.accent(), true);

    private static final StaffExtents.Profile ACCENT_PROFILE_BELOW =
        ShapeProfile.innerEdge(AccentShape.accent(), false);

    // The tr glyph's real inner (staff-facing) edge — its descending flourish, not the flat bottom of
    // its box. Trills only ever sit above the staff, so only the above-staff edge is derived. Like the
    // accent, the tr clears its neighbours by this outline (LilyPond builds a Script's skyline from its
    // stencil — define-grobs.scm always-vertical-skylines-from-stencil); a notehead-wide box would let
    // the flourish overlap a tie arc the box's flat bottom never reaches.
    private static final StaffExtents.Profile TRILL_PROFILE_ABOVE =
        ShapeProfile.innerEdge(BravuraFont.glyphOutline(SMuFLGlyph.ORNAMENT_TRILL), true);

    /**
     * Flattening tolerance for the staccato dot's reserved outline. The chords lie inside the
     * circle, so the error is one-sided: the accent seats a hair closer than the true outline
     * allows, never further.
     */
    public static final double STACCATO_OUTLINE_FLATNESS_SS = 0.02;

    // The staccato dot's outer edge — the boundary an accent stacking outside it must clear. The dot
    // is a circle (Bravura draws it round to within 0.0006 ss of a true one), so reserving the top of
    // its box would make the accent clear a rectangle the dot touches at a single point. LilyPond
    // builds the dot's skyline from its outline for exactly this reason, and pads that skyline — not
    // the accent — by the accent's horizon-padding (skyline.cc internal_distance pads `dim`). The
    // binding x therefore lands a horizon-padding left of the dot, where a round dot has already
    // dropped away.
    //
    // Worth 0.168 * (1 + m - sqrt(1 + m*m)) = 0.038 ss to the accent for a true circle, where 0.168 ss
    // is the dot's radius and m = 0.2594 the accent arm's slope; the chorded reservation gives 0.044.
    // Derived and asserted in AccentOverStaccatoTest.
    private static final StaffExtents.Profile STACCATO_RESERVE_PROFILE_ABOVE =
        ShapeProfile.outerEdge(BravuraFont.glyphOutline(SMuFLGlyph.ARTIC_STACCATO_ABOVE), true,
            STACCATO_OUTLINE_FLATNESS_SS);

    private static final StaffExtents.Profile STACCATO_RESERVE_PROFILE_BELOW =
        ShapeProfile.outerEdge(BravuraFont.glyphOutline(SMuFLGlyph.ARTIC_STACCATO_BELOW), false,
            STACCATO_OUTLINE_FLATNESS_SS);

    // Fewest chords the tie arc is reserved as; a wide arc gets one per staff space instead. Each
    // chord joins two points that lie exactly on the curve, so the reservation departs from the arc
    // only by the chord's sagitta.
    //
    // A chord lies *inside* a convex arc, so this under-reserves rather than over-reserves. The
    // sagitta scales as 1/n^2: at 8 chords it is ~0.009 ss, at 16 ~0.002 ss. 16 costs one
    // reservation per chord and keeps the error far below the ~0.2 ss a midpoint-sampled flat step
    // would introduce.
    private static final int TIE_BOUND_MIN_SAMPLES = 16;

    // A trill's layout records no margin: each note's padding is already folded into the inner
    // edge it seats at.
    private static final double TRILL_LAYOUT_MARGIN_SS = 0.0;

    private final StackingContext context;
    private final StackingLayer noteAttachedLayer;

    public NoteAttachedStacker(StackingContext context, StaffExtents noteAttachedExtents) {
        this.context = context;
        noteAttachedLayer =
            new StackingLayer(noteAttachedExtents, context.getBuilder().decorationLayouts());
    }

    /**
     * The priority-less scripts are folded into a tuplet bracket's clearance; the outer scripts
     * are stacked after the bracket by {@link #stackOuterScripts}. See
     * {@link VerticalStackingCalculator} for the full tier order.
     */
    public void stackInner() {
        var columns = context.getColumns();

        seedNoteBounds();

        // Seed the tie arc before the articulations. In LilyPond a script takes the tie as a
        // side-support element (Script_engraver::acknowledge_tie -> add_support), so the tie is
        // innermost — nearest the note — and each script is positioned just *outside* it wherever
        // the two overlap horizontally. This is the opposite of a slur, which is stacked outside
        // the scripts via `avoid-slur`. Seeding
        // the tie first lets the staccato and accent passes clear it through the same skyline
        // query they already use for the notehead.
        seedTieBounds();

        // Tier 1: Articulations, stacked as a script column outside the tie/notehead skyline: the
        // staccato innermost (nearest the note), the accent just outside it. Each clears only what
        // its own footprint overlaps, so an edge-attached tie — whose arc begins beyond the notehead
        // — leaves the articulations exactly where they'd sit untied, while a center-attached one,
        // which starts on top of the notehead, pushes them outward.
        for (var column : columns) {
            stackStaccatoColumn(column);
        }

        for (var column : columns) {
            stackAccentColumn(column);
        }
    }

    /**
     * The outside-priority scripts (fermata, trill) float outside the tuplet bracket, so this runs
     * after the bracket has reserved its footprint in {@code outerExtents}. The bracket's own
     * placement never consults the skyline.
     *
     * @param outerExtents the layer to stack against and reserve into: the tuplet bracket's layer in
     *                     the full pipeline, or this stacker's own layer where no bracket exists
     */
    public void stackOuterScripts(StaffExtents outerExtents) {
        var outerLayer = new StackingLayer(outerExtents, context.getBuilder().decorationLayouts());

        for (var column : context.getColumns()) {
            stackFermata(column, outerLayer);
        }

        stackTrills(outerLayer);
    }

    /** Shared by the layout path and the render path so the "opposite the stem" rule is defined once. */
    public static Direction articulationDirection(NoteElement note) {
        return note.direction().opposite();
    }

    /**
     * Used by the insertion note preview, where no {@link LayoutResult} is available. It
     * intentionally skips the below-staff content extent update, since the preview does not size
     * the line.
     */
    public static DecorationLayouts computePreviewDecorationLayouts(NoteElement note, double xSs) {
        var noteheadWidthSs = note.getType().getElementWidthSs();

        var lineWidthSs = xSs + noteheadWidthSs + 1.0;
        var extents = new StaffExtents(lineWidthSs);

        var bounds = computeNoteBounds(note);
        var noteheadFootprint = new Footprint(xSs, noteheadWidthSs);
        extents.ySet(Direction.UP, noteheadFootprint, bounds.topSs());
        extents.ySet(Direction.DOWN, noteheadFootprint, bounds.botSs());

        var decorationLayouts = new DecorationLayouts.Builder();
        var layer = new StackingLayer(extents, decorationLayouts);
        var host = new ElementAtColumn<>(note, xSs);

        Articulation staccatoArticulation = null;
        Articulation accentArticulation = null;

        for (var articulation : note.getArticulations()) {
            switch (articulation.getType()) {
                case STACCATO -> staccatoArticulation = articulation;
                case ACCENT -> accentArticulation = articulation;
            }
        }

        // No tie in the preview: place the staccato, then the accent just outside it —
        // equivalent to the full pipeline with no tie seeded ahead of the two passes.
        stackStaccatoOnly(staccatoArticulation, layer, host);
        stackAccentAboveExtents(accentArticulation, layer, host);

        stackFermataAt(note.findAttachment(FermataAttachment.class), layer, host);

        return decorationLayouts.build();
    }

    public record NoteBounds(double topSs, double botSs) {
    }

    public static NoteBounds computeNoteBounds(StaffElement element) {
        var centerYSs = ElementSeat.seatSs(element);
        var type = element.getType();
        var noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
        var noteheadBotSs = centerYSs + type.getNoteheadBottomOffsetSs();

        if (!(element instanceof NoteElement note)) {
            return new NoteBounds(noteheadTopSs, noteheadBotSs);
        }

        var direction = note.direction();
        var topSs = Math.min(centerYSs + type.getTopYOffsetSs(direction), noteheadTopSs);
        var botSs = Math.max(topSs + type.getElementHeightSs(direction), noteheadBotSs);
        return new NoteBounds(topSs, botSs);
    }

    private void seedNoteBounds() {
        var columns = context.getColumns();
        var builder = context.getBuilder();
        var extents = noteAttachedLayer.extents();

        for (var column : columns) {
            var element = column.getElement();
            var stemLayout = builder.getStemLayout(element);
            var xSs = column.getXSs();

            double topSs;
            double botSs;

            if (stemLayout != null) {
                var centerYSs = ElementSeat.seatSs(element);
                var type = element.getType();
                var noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
                var noteheadBotSs = centerYSs + type.getNoteheadBottomOffsetSs();
                topSs = Math.min(stemLayout.topYSs(), noteheadTopSs);
                botSs = Math.max(stemLayout.bottomYSs(), noteheadBotSs);
            } else {
                var bounds = computeNoteBounds(element);
                topSs = bounds.topSs();
                botSs = bounds.botSs();
            }

            var noteheadFootprint = new Footprint(xSs, element.getType().getElementWidthSs());
            extents.ySet(Direction.UP, noteheadFootprint, topSs);
            extents.ySet(Direction.DOWN, noteheadFootprint, botSs);

            var noteheadCenterYSs = ElementSeat.seatSs(element);
            context.updateLowestNoteBotSs(
                noteheadCenterYSs + StackingUtils.NOTE_HEAD_RADIUS_SS);

            context.updateBotContentExtentSs(botSs);
        }
    }

    /** Runs before the articulation passes, so a staccato or accent clears the arc where it protrudes. */
    private void seedTieBounds() {
        for (var tieLayout : context.getBuilder().tieLayouts()) {
            var tie = tieLayout.placement().tie();

            var sx = tieLayout.startXSs();
            var ex = tieLayout.endXSs();
            var spanWidthSs = ex - sx;

            if (spanWidthSs <= 0) {
                continue;
            }

            // Reserve on the side the renderer actually draws: Tie.arcSign() > 0 ⇒ arc below
            // (reserve on the below side); < 0 ⇒ arc above. Routing this through the shared rule
            // keeps a conflicting-stem tie from reserving the opposite side from its bulge.
            var arcSide = tie.arcSign() > 0 ? Direction.DOWN : Direction.UP;

            seedTieArcIntoExtents(tieLayout, spanWidthSs, arcSide);
        }
    }

    /**
     * The reservation spans exactly {@code [startXSs, endXSs]} — where the arc physically is — and
     * never the noteheads it joins. The chord endpoints are evaluated at the same {@code t}, since
     * {@code x} is not linear in {@code t}.
     */
    private void seedTieArcIntoExtents(
        LayoutResult.TieLayout tieLayout,
        double spanWidthSs,
        Direction arcSide) {

        var sampleCount = Math.max(TIE_BOUND_MIN_SAMPLES, (int) Math.ceil(spanWidthSs));
        var extents = noteAttachedLayer.extents();
        var startXSs = evaluateBezierXSs(0.0, tieLayout);
        var startYSs = evaluateBezierYSs(0.0, tieLayout);

        for (var i = 0; i < sampleCount; i++) {
            var tEnd = (double) (i + 1) / sampleCount;
            var endXSs = evaluateBezierXSs(tEnd, tieLayout);
            var endYSs = evaluateBezierYSs(tEnd, tieLayout);

            extents.ySetSloped(arcSide, new Chord(startXSs, endXSs, startYSs, endYSs));

            if (arcSide.isDown()) {
                context.updateBotContentExtentSs(Math.max(startYSs, endYSs));
            }

            startXSs = endXSs;
            startYSs = endYSs;
        }
    }

    static double evaluateBezierYSs(double t, LayoutResult.TieLayout tieLayout) {
        var axis = new CubicAxis(tieLayout.startYSs(), tieLayout.cp1YSs(), tieLayout.cp2YSs(),
            tieLayout.endYSs());

        return axis.at(t);
    }

    static double evaluateBezierXSs(double t, LayoutResult.TieLayout tieLayout) {
        var axis = new CubicAxis(tieLayout.startXSs(), tieLayout.cp1XSs(), tieLayout.cp2XSs(),
            tieLayout.endXSs());

        return axis.at(t);
    }

    private record CubicAxis(double p0, double p1, double p2, double p3) {

        // The Bernstein coefficient of the two interior control points.
        private static final int INTERIOR_COEFFICIENT = 3;

        double at(double t) {
            var mt = 1.0 - t;
            return mt * mt * mt * p0
                + INTERIOR_COEFFICIENT * mt * mt * t * p1
                + INTERIOR_COEFFICIENT * mt * t * t * p2
                + t * t * t * p3;
        }
    }

    private void stackStaccatoColumn(ElementColumn column) {
        if (!(column.getElement() instanceof NoteElement note)) {
            return;
        }

        var staccato = findStaccato(note);

        if (staccato == null) {
            return;
        }

        var edgeYSs = stackStaccatoOnly(
            staccato, noteAttachedLayer, new ElementAtColumn<>(note, column.getXSs()));

        updateBelowStaffContentExtent(articulationDirection(note), edgeYSs);
    }

    private void stackAccentColumn(ElementColumn column) {
        if (!(column.getElement() instanceof NoteElement note)) {
            return;
        }

        var accent = findAccent(note);

        if (accent == null) {
            return;
        }

        var edgeYSs = stackAccentAboveExtents(
            accent, noteAttachedLayer, new ElementAtColumn<>(note, column.getXSs()));

        updateBelowStaffContentExtent(articulationDirection(note), edgeYSs);
    }

    /**
     * Shared by the full pipeline's staccato pass and the no-tie preview so the two agree.
     *
     * @param staccato {@code null} when the note has none
     * @return {@code null} when no staccato is present
     */
    private static @Nullable Double stackStaccatoOnly(
        @Nullable Articulation staccato, StackingLayer layer, ElementAtColumn<NoteElement> host) {

        if (staccato == null) {
            return null;
        }

        var note = host.element();
        var direction = articulationDirection(note);
        var footprint = NoteGeometry.centeredOverNotehead(host, staccato.intrinsicWidthSs());
        var reserveProfile = direction.isUp()
            ? STACCATO_RESERVE_PROFILE_ABOVE
            : STACCATO_RESERVE_PROFILE_BELOW;
        var outline = new Outline(footprint, new StaffExtents.Profiles(
            StaffExtents.Profile.flat(footprint.widthSs()), reserveProfile));
        var side = new AlignedSide(direction, STACCATO_PADDING_SS,
            scriptStaffPaddingSs(STACCATO_PADDING_SS), StackingUtils.SCRIPT_HORIZON_PADDING_SS);

        return layer.stackStaccato(staccato, outline, side, note.getStaffPosition().value());
    }

    /**
     * Serves both the full pipeline's accent pass and the no-tie preview. The collision footprint
     * is centered on the notehead, where {@code ArticulationRenderer} draws the glyph, not
     * anchored at the column x.
     *
     * @param accent {@code null} when the note has none
     * @return {@code null} when no accent is present
     */
    private static @Nullable Double stackAccentAboveExtents(
        @Nullable Articulation accent, StackingLayer layer, ElementAtColumn<NoteElement> host) {

        if (accent == null) {
            return null;
        }

        var direction = articulationDirection(host.element());
        var footprint = NoteGeometry.centeredOverNotehead(host, accent.intrinsicWidthSs());
        var innerEdge = direction.isUp() ? ACCENT_PROFILE_ABOVE : ACCENT_PROFILE_BELOW;

        // The accent reserves its flat box, not its wedge: letting a dynamic or hairpin nestle under
        // its arm is a separate change.
        var outline = new Outline(footprint, new StaffExtents.Profiles(
            innerEdge, StaffExtents.Profile.flat(footprint.widthSs())));
        var side = new AlignedSide(direction, ACCENT_PADDING_SS,
            scriptStaffPaddingSs(ACCENT_PADDING_SS), StackingUtils.SCRIPT_HORIZON_PADDING_SS);

        return layer.placeAndReserveClamped(accent, outline, side);
    }

    /** The extent tracks its maximum, so the accent, the outermost articulation, ends up the constraint. */
    private void updateBelowStaffContentExtent(Direction direction, @Nullable Double edgeYSs) {
        if (direction.isDown() && edgeYSs != null) {
            context.updateBotContentExtentSs(edgeYSs);
        }
    }

    /**
     * The staff-padding a script clamps against the staff line by: the larger of its own neighbor
     * padding and the floor {@link #SCRIPT_STAFF_PADDING_SS}.
     */
    private static double scriptStaffPaddingSs(double neighborPaddingSs) {
        return Math.max(neighborPaddingSs, SCRIPT_STAFF_PADDING_SS);
    }

    private static @Nullable Articulation findStaccato(NoteElement note) {
        return note.findArticulation(ArticulationType.STACCATO);
    }

    private static @Nullable Articulation findAccent(NoteElement note) {
        return note.findArticulation(ArticulationType.ACCENT);
    }

    private void stackFermata(ElementColumn column, StackingLayer outerLayer) {
        var note = column.getElement();

        stackFermataAt(note.findAttachment(FermataAttachment.class), outerLayer,
            new ElementAtColumn<>(note, column.getXSs()));
    }

    /**
     * Shared by the full pipeline's fermata pass and the no-tie preview so the two agree. The
     * collision footprint is centered on the notehead ({@link NoteGeometry#centeredOverNotehead}),
     * where {@code FermataRenderer} draws the glyph, not anchored at the column x.
     *
     * @param fermata {@code null} when the element has none
     */
    private static void stackFermataAt(
        @Nullable FermataAttachment fermata, StackingLayer layer, ElementAtColumn<?> host) {

        if (fermata == null) {
            return;
        }

        var footprint = NoteGeometry.centeredOverNotehead(host, fermata.intrinsicWidthSs());

        layer.placeAndReserveClamped(fermata, Outline.flat(footprint), FERMATA_SIDE);
    }

    private void stackTrills(StackingLayer outerLayer) {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
        var trills = line.spanRegistry().findSpans(Trill.class);

        for (var trill : trills) {
            stackSingleTrill(trill, outerLayer, columnsByElement);
        }
    }

    /**
     * The trill is one horizontal reservation over its ink extent
     * ({@link TrillGeometry#inkExtentSs}), but each note in the range demands its own vertical
     * clearance; the span seats at the most-outward inner edge any note requires. Trills are above
     * only.
     */
    private void stackSingleTrill(
        Trill trill,
        StackingLayer outerLayer,
        Map<StaffElement, ElementColumn> columnsByElement) {

        var spanColumns = ElementColumn.resolveSpan(trill, columnsByElement);

        if (spanColumns == null) {
            return;
        }

        var line = context.getLine();
        var outerExtents = outerLayer.extents();
        var anchorIndex = trill.requireAnchorElementIndex();
        var endIndex = trill.requireEndElementIndex();

        // Start from the staff clamp. With no support the trill is a lone script at its anchor, so
        // it keeps its own script padding from the staff line — never seating closer to the staff
        // than to a tie or notehead (scriptStaffPaddingSs). Each note then pulls the inner edge
        // further out — above, more outward is a smaller Y — by its own padding past the real
        // reservations under its footprint.
        var innerEdgeYSs =
            StackingUtils.STAFF_TOP_INK_Y_SS - scriptStaffPaddingSs(TRILL_SCRIPT_PADDING_SS);

        for (var index = anchorIndex; index <= endIndex; index++) {
            var note = line.getElement(index);
            var column = columnsByElement.get(note);

            if (column == null) {
                continue;
            }

            double footprintXSs;
            StaffExtents.Profile profile;
            double paddingSs;

            if (index == anchorIndex) {
                // The first note seats the tr glyph as a script: it clears the tie/notehead by the
                // glyph's real inner edge, centered on the notehead exactly as TrillRenderer draws it.
                footprintXSs = TrillGeometry.glyphLeftXSs(
                    column.getXSs(), NoteGeometry.getNoteheadCenterXSs(note));
                profile = TRILL_PROFILE_ABOVE;
                paddingSs = TRILL_SCRIPT_PADDING_SS;
            } else {
                // Later notes sit under the flat wavy line, which clears them by a notehead-wide box.
                footprintXSs = column.getXSs();
                profile = StaffExtents.Profile.flat(note.getType().getElementWidthSs());
                paddingSs = TRILL_SPANNER_PADDING_SS;
            }

            var support = outerExtents.clearance(Direction.UP, footprintXSs, profile,
                new SupportPadding(paddingSs, StackingUtils.SCRIPT_HORIZON_PADDING_SS));

            // An empty footprint carries no real reservation, so only the staff clamp applies.
            if (!support.present()) {
                continue;
            }

            innerEdgeYSs = Math.min(innerEdgeYSs, support.ySs());
        }

        // Reserve the full span at the computed inner edge; the per-note padding is already folded
        // in, so the placement adds no further margin.
        var inkExtent = TrillGeometry.inkExtentSs(trill, spanColumns);
        var footprint = new Footprint(inkExtent.leftXSs(), inkExtent.widthSs());

        outerLayer.placeAtInnerEdge(trill, Outline.flat(footprint),
            new InnerEdge(Direction.UP, innerEdgeYSs, TRILL_LAYOUT_MARGIN_SS));
    }

}
