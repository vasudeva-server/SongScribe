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

import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.layout.ElementColumn;
import songscribe.layout.LayoutResult;
import songscribe.layout.LayoutResultBuilder;
import songscribe.layout.NoteGeometry;
import songscribe.layout.ShapeProfile;
import songscribe.layout.StaffExtents;
import songscribe.dom.FermataAttachment;
import songscribe.shape.AccentShape;
import songscribe.smufl.BravuraFont;
import songscribe.smufl.SMuFLGlyph;
import songscribe.engraving.Staff;
import songscribe.dom.Trill;

import org.jspecify.annotations.Nullable;

import static songscribe.layout.stacking.StackingUtils.stackStaccato;

/**
 * Seeds note bounds and stacks note-attached decorations (tiers 1-2).
 * <p>
 * Tier 1: near-note decorations (articulations — staccato, accent), via {@link #stackInner()}.
 * Tier 2: note decorations (fermata, trill), via {@link #stackOuterScripts}.
 * <p>
 * The two are separate entry points, and the tuplet-bracket pass runs between them. See
 * {@link VerticalStackingCalculator} for the full tier order and why it is this way.
 */
public class NoteAttachedStacker {

    /**
     * Default vertical margin between note decorations during stacking.
     * Used for trill until a later phase gives it its own padding.
     */
    public static final double NOTE_DECORATION_MARGIN_SS = 0.5;  // 4px

    /**
     * Padding a staccato dot keeps from the neighbor it stacks against.
     */
    public static final double STACCATO_PADDING_SS = 0.20;  // LilyPond script.scm staccato

    /**
     * Padding an accent keeps from the neighbor it stacks against.
     */
    public static final double ACCENT_PADDING_SS = 0.20;  // LilyPond script.scm accent

    /**
     * Padding a fermata keeps from the neighbor it stacks against.
     */
    public static final double FERMATA_PADDING_SS = 0.466;  // LilyPond script.scm fermata

    // The fermata looks no further than its own bbox when it clears what lies beneath it. A tie
    // arcs upward toward its center, so under the fermata's footprint the tie is highest at the
    // fermata's inner bbox corner (the edge toward the arc's center). Sampling with no horizon
    // padding makes the placement take the tie's Y at exactly that corner — the ray traced straight
    // down from the inner bottom corner of the fermata's box — and seat the fermata FERMATA_PADDING_SS
    // above it. A non-zero horizon would sample SCRIPT_HORIZON_PADDING_SS further up the rising arc
    // and over-clear it.
    private static final double FERMATA_HORIZON_PADDING_SS = 0.0;

    /**
     * Padding the trill keeps from the first note of its range, where it seats as a script.
     */
    public static final double TRILL_SCRIPT_PADDING_SS = 0.40;  // LilyPond script.scm trill is 0.20

    /**
     * Padding the trill keeps from the notes after the first in its range, where it seats as a
     * spanner.
     */
    // LilyPond define-grobs.scm TrillSpanner padding is 0.50
    public static final double TRILL_SPANNER_PADDING_SS = 0.70;

    /**
     * Staff-padding for scripts (staccato, accent, fermata, trill): the minimum gap they keep from
     * the staff line when the staff clamp is the outward constraint.
     */
    // LilyPond define-grobs.scm Script staff-padding
    public static final double SCRIPT_STAFF_PADDING_SS = 0.25;

    /**
     * Staff-padding for dynamics: the minimum gap they keep from the staff line when the staff
     * clamp is the outward constraint.
     */
    // LilyPond define-grobs.scm DynamicLineSpanner staff-padding
    public static final double DYNAMIC_STAFF_PADDING_SS = 0.10;

    /**
     * Padding a text dynamic keeps from the neighbor it stacks against.
     */
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
     * Flattening tolerance for the staccato dot's reserved outline: four chords across a dot 0.336 ss
     * wide.
     * <p>
     * Every chord is a reservation the rest of the line is then scanned against, and the dot is the
     * most numerous script there is. Halving the tolerance to {@code ShapeProfile}'s own 0.01 ss
     * doubles the chords to eight and costs 5.0 µs per line of accented notes instead of 1.4, to move
     * the accent 0.0056 ss — a twentieth of a pixel at the default zoom. The chords lie inside the
     * circle, so the error is one-sided: the accent seats a hair closer than the true outline allows,
     * never further.
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
    // only by the chord's sagitta — never by the arc's full rise across the segment, as flat steps
    // did.
    //
    // A chord lies *inside* a convex arc, so this under-reserves rather than over-reserves. The
    // sagitta scales as 1/n^2: at 8 chords it is ~0.009 ss, at 16 ~0.002 ss. 16 is cheap (a chord
    // costs one reservation, exactly as a step did) and puts the error a hundred times below the
    // ~0.2 ss tip error that midpoint-sampled flat steps introduced.
    private static final int TIE_BOUND_MIN_SAMPLES = 16;

    private final StackingContext context;
    private final StaffExtents noteAttachedExtents;

    public NoteAttachedStacker(StackingContext context, StaffExtents noteAttachedExtents) {
        this.context = context;
        this.noteAttachedExtents = noteAttachedExtents;
    }

    /**
     * Seeds note and tie bounds, then stacks the priority-less scripts — the ones that sit closest
     * to the note and are folded into a tuplet bracket's clearance rather than pushed outside it.
     * <p>
     * This is the first of the two note-attached phases. The outer scripts (fermata, trill) are
     * stacked separately by {@link #stackOuterScripts}, because the tuplet bracket must be placed
     * between the two: it clears the inner scripts and they must therefore already be placed, while
     * the outer scripts float above the bracket and must therefore be placed after it. See
     * {@link VerticalStackingCalculator} for the full tier order.
     */
    public void stackInner() {
        var columns = context.getColumns();
        var builder = context.getBuilder();

        // Seed note bounding areas into noteAttachedExtents
        seedNoteBounds();

        // Seed the tie arc before the articulations. In LilyPond a script takes the tie as a
        // side-support element (Script_engraver::acknowledge_tie -> add_support), so the tie is
        // innermost — nearest the note — and each script is positioned just *outside* it wherever
        // the two overlap horizontally. This is the opposite of a slur, which is stacked outside
        // the scripts via `avoid-slur`; ties were previously modelled as slurs by mistake. Seeding
        // the tie first lets the staccato and accent passes clear it through the same skyline
        // query they already use for the notehead.
        seedTieBounds();

        // Tier 1: Articulations, stacked as a script column outside the tie/notehead skyline: the
        // staccato innermost (nearest the note), the accent just outside it. Each clears only what
        // its own footprint overlaps, so an edge-attached tie — whose arc begins beyond the notehead
        // — leaves the articulations exactly where they'd sit untied, while a center-attached one,
        // which starts on top of the notehead, pushes them outward.
        for (var column : columns) {
            stackStaccatoColumn(column, builder);
        }

        for (var column : columns) {
            stackAccentColumn(column, builder);
        }
    }

    /**
     * Stacks the outside-priority scripts (fermata, trill) against {@code outerExtents}.
     * <p>
     * These are the scripts LilyPond gives an {@code outside-staff-priority} and which its tuplet
     * bracket therefore skips (tuplet-bracket.cc lines 688-690): rather than being cleared by the
     * bracket, they float outside it. The full pipeline honours that by running this phase after the
     * bracket has reserved its footprint and passing the layer holding it, so the skyline query below
     * pushes them clear (issue #520). The bracket's own placement is unaffected — it never consults
     * the skyline, deriving its ceiling from note tips and inner scripts alone.
     *
     * @param outerExtents the layer to stack against and reserve into: the tuplet bracket's layer in
     *                     the full pipeline, or this stacker's own layer where no bracket exists
     */
    public void stackOuterScripts(StaffExtents outerExtents) {
        var builder = context.getBuilder();

        for (var column : context.getColumns()) {
            stackFermata(column, outerExtents, builder);
        }

        stackTrills(outerExtents, builder);
    }

    /**
     * Returns the side articulations are placed on for the given note: opposite the stem, so
     * {@link Direction#UP} (above the staff) for down-stems and {@link Direction#DOWN}
     * (below the staff) for up-stems. Shared by the layout path (the staccato/accent stacking
     * passes) and the render path ({@code ArticulationRenderer}) so the "opposite the stem" rule
     * is defined exactly once.
     */
    public static Direction articulationDirection(StaffElement note) {
        return note.getDirection().opposite();
    }

    /**
     * Computes note-attached decoration layouts for a single note without a full layout pipeline.
     * <p>
     * Used by the insertion note preview, where no {@link LayoutResult} is available.
     * Creates a minimal {@link StaffExtents}, seeds note bounds, and runs the same
     * stacking logic as the full pipeline (articulations, then fermata).
     * <p>
     * Unlike the full pipeline's staccato and accent passes, this has no {@link StackingContext}
     * to update with the below-staff content extent (used for lyric placement) — the preview
     * exists only to compute decoration positions for rendering, not to size the line, so that
     * update is intentionally skipped here.
     *
     * @param note the note to compute layouts for
     * @param xSs  X position in staff-space units
     * @return a built {@link LayoutResult} containing {@link LayoutResult.DecorationLayout}s
     */
    public static LayoutResult computePreviewDecorationLayouts(StaffElement note, double xSs) {
        var noteheadWidthSs = note.getType().getElementWidthSs();

        // Create minimal extents just wide enough to contain the note
        var lineWidthSs = xSs + noteheadWidthSs + 1.0;
        var extents = new StaffExtents(lineWidthSs);

        // Seed note bounds using the non-beamed path
        var bounds = computeNoteBounds(note);
        extents.ySet(true, xSs, noteheadWidthSs, bounds.topSs());
        extents.ySet(false, xSs, noteheadWidthSs, bounds.botSs());

        var builder = new LayoutResultBuilder();
        var staffPosition = note.getStaffPosition();

        // Tier 1: Articulations
        Articulation staccatoArticulation = null;
        Articulation accentArticulation = null;

        for (var a : note.getArticulations()) {
            if (a.isStaccato()) {
                staccatoArticulation = a;
            } else if (a.isAccent()) {
                accentArticulation = a;
            }
        }

        var direction = articulationDirection(note);

        // No tie in the preview: place the staccato, then the accent just outside it —
        // equivalent to the full pipeline with no tie seeded ahead of the two passes.
        stackStaccatoOnly(staccatoArticulation, extents, note, xSs, STACCATO_PADDING_SS,
            staffPosition, direction, builder);
        stackAccentAboveExtents(accentArticulation, extents, note, xSs, direction, builder);

        // Tier 2: Fermata
        stackFermataAt(note.findAttachment(FermataAttachment.class), extents, note, xSs, builder);

        return builder.build();
    }


    // ---- Note bounds ----

    /**
     * Vertical bounds of a note without stem layout (non-beamed path).
     */
    public record NoteBounds(double topSs, double botSs) {
    }

    /**
     * Computes note vertical bounds from element type geometry alone (no stem layout).
     */
    public static NoteBounds computeNoteBounds(StaffElement element) {
        var centerYSs = Staff.spToSs(element.getStaffPosition());
        var type = element.getType();
        var direction = element.getDirection();
        var noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
        var noteheadBotSs = centerYSs + type.getNoteheadBottomOffsetSs();
        var topSs = Math.min(centerYSs + type.getTopYOffsetSs(direction), noteheadTopSs);
        var botSs = Math.max(topSs + type.getElementHeightSs(direction), noteheadBotSs);
        return new NoteBounds(topSs, botSs);
    }


    // ---- Seeding ----

    /**
     * Seeds note bounding areas into the note-attached StaffExtents layer.
     * <p>
     * For each column, uses the StemLayout from the builder (computed during beam/stem pass)
     * to get accurate stem top/bottom positions, and uses the notehead width from SMuFL metadata.
     */
    private void seedNoteBounds() {
        var columns = context.getColumns();
        var builder = context.getBuilder();

        for (var column : columns) {
            var element = column.getElement();
            var stemLayout = builder.getStemLayout(element);
            var xSs = column.getXSs();

            double topSs;
            double botSs;

            if (stemLayout != null) {
                var centerYSs = element.getStaffPosition()
                    * Staff.STAFF_POSITION_OFFSET_SS;
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

            var noteheadWidthSs = element.getType().getElementWidthSs();
            noteAttachedExtents.ySet(true, xSs, noteheadWidthSs, topSs);
            noteAttachedExtents.ySet(false, xSs, noteheadWidthSs, botSs);

            // Track lowest notehead bottom for lyrics baseline calculation
            var noteheadCenterYSs = element.getStaffPosition()
                * Staff.STAFF_POSITION_OFFSET_SS;
            context.updateLowestNoteBotSs(
                noteheadCenterYSs + StackingUtils.NOTE_HEAD_RADIUS_SS);

            // Track full element bottom (notehead + stem) as below-staff content for lyric placement
            context.updateBotContentExtentSs(botSs);
        }
    }

    /**
     * Seeds tie arc bounds into the note-attached StaffExtents layer.
     * <p>
     * For each tie the outer Bezier curve is sampled and its vertical extent reserved in the
     * extents layer (by {@link #seedTieArcIntoExtents}) so line sizing, lyric clearance,
     * articulations, and tier-2 decorations (fermata, trill) account for the arc.
     * Runs before the articulation passes, so a staccato or accent clears the arc where it
     * protrudes past the note — matching LilyPond, where every script takes the tie as a
     * side-support element (add_support) and is positioned outside it. Upward-arcing ties (stem
     * down) reserve above the staff; downward-arcing ties (stem up) reserve below.
     */
    private void seedTieBounds() {
        var line = context.getLine();
        var builder = context.getBuilder();
        var ties = line.findTies();

        if (ties.isEmpty()) {
            return;
        }

        for (var span : ties) {
            var startElement = span.getAnchorElement();
            var endElement = span.getEndElement();

            if (startElement == null || endElement == null) {
                continue;
            }

            var tieLayout = builder.getTieLayout(span);

            if (tieLayout == null) {
                continue;
            }

            // Sample the outer Bezier curve to reserve tie vertical extent
            var sx = tieLayout.startXSs();
            var ex = tieLayout.endXSs();
            var spanWidthSs = ex - sx;

            if (spanWidthSs <= 0) {
                continue;
            }

            // Reserve on the side the renderer actually draws: Tie.arcSign() > 0 ⇒ arc below
            // (reserve on the below side, above=false); < 0 ⇒ arc above. Routing this through the
            // shared rule keeps a conflicting-stem tie from reserving the opposite side from its bulge.
            var arcsDown = span.arcSign() > 0;

            seedTieArcIntoExtents(tieLayout, spanWidthSs, !arcsDown);
        }
    }

    /**
     * Reserves the tie arc's vertical extent in the note-attached layer so line sizing accounts for
     * the arc. For downward arcs (above=false) also feeds the sampled Y values into the context's
     * below-staff content extent so lyric placement clears the arc. Only ties flow through this
     * path today; slur seeding is future work (#515).
     * <p>
     * The reservation spans exactly {@code [startXSs, endXSs]} — where the arc physically is — and
     * never the noteheads it joins. That single rule reproduces LilyPond in both attachment modes:
     * an edge-attached tie starts a {@code NOTE_HEAD_GAP_SS} beyond the notehead, clear of the
     * scripts, which therefore sit exactly where they would untied; a center-attached tie (one whose
     * seat drops past the head box, so {@code tieEndpointXSs} recedes to the notehead center) starts
     * inside the head, genuinely overlaps the scripts, and pushes them outward. Reserving the arc
     * across the notehead — as this once did, at the Bezier Y a full notehead-width into the span —
     * forced the second behaviour onto both.
     * <p>
     * The arc is reserved as a run of chords between points sampled on the curve itself, rather than
     * as flat steps at each segment's midpoint Y. The chord's endpoints are the curve's own
     * {@code (x(t), y(t))}, so the two must be evaluated at the same {@code t}: the control points
     * are inset by {@code BezierBow.indent}, not by a third of the span, so {@code x} is not linear
     * in {@code t} and a chord drawn between uniform x-steps would shear against the arc.
     */
    private void seedTieArcIntoExtents(
        LayoutResult.TieLayout tieLayout,
        double spanWidthSs,
        boolean above) {

        var sampleCount = Math.max(TIE_BOUND_MIN_SAMPLES, (int) Math.ceil(spanWidthSs));
        var startXSs = evaluateBezierXSs(0.0, tieLayout);
        var startYSs = evaluateBezierYSs(0.0, tieLayout);

        for (var i = 0; i < sampleCount; i++) {
            var tEnd = (double) (i + 1) / sampleCount;
            var endXSs = evaluateBezierXSs(tEnd, tieLayout);
            var endYSs = evaluateBezierYSs(tEnd, tieLayout);

            noteAttachedExtents.ySetSloped(above, startXSs, endXSs, startYSs, endYSs);

            if (!above) {
                context.updateBotContentExtentSs(Math.max(startYSs, endYSs));
            }

            startXSs = endXSs;
            startYSs = endYSs;
        }
    }

    /**
     * Evaluates the outer cubic Bezier curve Y at parameter {@code t}.
     *
     * @param t         Bezier parameter in [0, 1]
     * @param tieLayout the tie layout providing control point coordinates
     * @return the Y coordinate of the outer curve at {@code t}
     */
    static double evaluateBezierYSs(double t, LayoutResult.TieLayout tieLayout) {
        return cubicAt(t, tieLayout.startYSs(), tieLayout.cp1YSs(), tieLayout.cp2YSs(),
            tieLayout.endYSs());
    }

    /**
     * Evaluates the outer cubic Bezier curve X at parameter {@code t}.
     */
    static double evaluateBezierXSs(double t, LayoutResult.TieLayout tieLayout) {
        return cubicAt(t, tieLayout.startXSs(), tieLayout.cp1XSs(), tieLayout.cp2XSs(),
            tieLayout.endXSs());
    }

    private static double cubicAt(double t, double p0, double p1, double p2, double p3) {
        var mt = 1.0 - t;
        return mt * mt * mt * p0
            + 3 * mt * mt * t * p1
            + 3 * mt * t * t * p2
            + t * t * t * p3;
    }


    // ---- Tier 1: Articulations ----

    /**
     * Places the staccato dot for the given column, if present.
     * <p>
     * The staccato is the innermost script. It anchors relative to the note — clearing an interior
     * staff line by {@link StackingUtils#STACCATO_ON_LINE_DISTANCE_SS}, or sitting
     * {@link StackingUtils#STACCATO_BETWEEN_LINES_DISTANCE_SS} from the note center in a space, and
     * beyond the staff anchoring at the notehead — but is pushed outward if the tie (seeded first)
     * protrudes into its footprint, since LilyPond adds the tie as a side-support element to every
     * script.
     */
    private void stackStaccatoColumn(
        ElementColumn column,
        LayoutResultBuilder builder) {

        var note = column.getElement();
        var staccato = findStaccato(note);

        if (staccato == null) {
            return;
        }

        var direction = articulationDirection(note);
        var edgeYSs = stackStaccatoOnly(staccato, noteAttachedExtents, note, column.getXSs(),
            STACCATO_PADDING_SS, note.getStaffPosition(), direction, builder);

        updateBelowStaffContentExtent(direction, edgeYSs);
    }

    /**
     * Places the accent for the given column, if present.
     * <p>
     * Stacks against its nearest neighbor in the skyline: the staccato when present, else the tie
     * where its arc protrudes past the note, else the notehead or staff line. Every script takes
     * the tie as a side-support element in LilyPond, so the accent clears the arc directly when no
     * staccato sits between them. Clearance is uniformly {@link #ACCENT_PADDING_SS} from the
     * neighbor's outer edge, resolved by {@link StackingUtils#placeAndReserveClamped}.
     */
    private void stackAccentColumn(
        ElementColumn column,
        LayoutResultBuilder builder) {

        var note = column.getElement();
        var accent = findAccent(note);

        if (accent == null) {
            return;
        }

        var direction = articulationDirection(note);

        var edgeYSs = stackAccentAboveExtents(accent, noteAttachedExtents, note,
            column.getXSs(), direction, builder);

        updateBelowStaffContentExtent(direction, edgeYSs);
    }

    /**
     * Places the staccato dot (if present) at its note-relative anchor
     * ({@link StackingUtils#stackStaccato}). Shared by the full pipeline's staccato pass and the
     * no-tie preview so the two agree on staccato placement.
     *
     * @return the dot's top Y (above) or bottom Y (below) in staff-space units, or {@code null}
     *     when no staccato is present
     */
    private static @Nullable Double stackStaccatoOnly(
        @Nullable Articulation staccato,
        StaffExtents extents,
        StaffElement note,
        double columnXSs, double paddingSs, int staffPosition,
        Direction direction,
        LayoutResultBuilder builder) {

        if (staccato == null) {
            return null;
        }

        var widthSs = staccato.getContentWidthSs();
        var xSs = articulationFootprintXSs(note, columnXSs, widthSs);
        var reserveProfile = direction.isUp()
            ? STACCATO_RESERVE_PROFILE_ABOVE
            : STACCATO_RESERVE_PROFILE_BELOW;

        return stackStaccato(direction, extents, staccato, xSs,
            widthSs, staccato.getContentHeightSs(), reserveProfile, paddingSs,
            scriptStaffPaddingSs(paddingSs), StackingUtils.SCRIPT_HORIZON_PADDING_SS,
            staffPosition, builder);
    }

    /**
     * Places the accent (if present) against its immediate neighbor in the skyline via
     * {@link StackingUtils#placeAndReserveClamped}. The neighbor — tie, staccato, notehead, or
     * staff line — and its clearance are resolved from the extents, so one accent path serves
     * both the full pipeline's accent pass and the no-tie preview.
     *
     * @return the accent's top Y (above) or bottom Y (below) in staff-space units, or
     *     {@code null} when no accent is present
     */
    private static @Nullable Double stackAccentAboveExtents(
        @Nullable Articulation accent,
        StaffExtents extents,
        StaffElement note,
        double columnXSs,
        Direction direction,
        LayoutResultBuilder builder) {

        if (accent == null) {
            return null;
        }

        var widthSs = accent.getContentWidthSs();
        var xSs = articulationFootprintXSs(note, columnXSs, widthSs);
        var innerEdge = direction.isUp() ? ACCENT_PROFILE_ABOVE : ACCENT_PROFILE_BELOW;

        // The accent reserves its flat box, not its wedge: letting a dynamic or hairpin nestle under
        // its arm is a separate change with a far wider blast radius (#528 §6).
        var profiles = new StaffExtents.Profiles(innerEdge, StaffExtents.Profile.flat(widthSs));

        return StackingUtils.placeAndReserveClamped(direction, extents, accent, xSs,
            widthSs, accent.getContentHeightSs(), profiles,
            ACCENT_PADDING_SS, scriptStaffPaddingSs(ACCENT_PADDING_SS),
            StackingUtils.SCRIPT_HORIZON_PADDING_SS, builder);
    }

    /**
     * For below-staff (up-stem) articulations, pushes the below-staff content extent down to the
     * given edge so lyrics clear it. The extent tracks its maximum, so feeding it the staccato
     * (inner) edge from one pass and the accent (outer) edge from the other leaves the accent —
     * the true outermost articulation — as the constraint (Issue 6).
     */
    private void updateBelowStaffContentExtent(Direction direction, @Nullable Double edgeYSs) {
        if (direction.isDown() && edgeYSs != null) {
            context.updateBotContentExtentSs(edgeYSs);
        }
    }

    /**
     * X of an articulation's collision footprint — the glyph's left edge, centered on the notehead,
     * which is exactly where {@code ArticulationRenderer} draws it. The note's column x is not the
     * footprint: an accent is wider than a notehead, so anchoring its box at the column x pushes the
     * box off the glyph by half the width difference and makes it collide with whatever sits to the
     * note's right — a tie arc, most visibly.
     */
    private static double articulationFootprintXSs(
        StaffElement note, double columnXSs, double widthSs) {

        return NoteGeometry.centeredOverNoteheadXSs(note, columnXSs, widthSs);
    }

    /**
     * The staff-padding a script clamps against the staff line by: the larger of its own
     * neighbor padding and {@link #SCRIPT_STAFF_PADDING_SS}. A script keeps its own padding from any
     * neighbor below it, and the staff line is no exception — so it never seats closer to the staff
     * than it would to a tie or notehead. {@code SCRIPT_STAFF_PADDING_SS} is only a floor, binding
     * for the scripts whose own padding is smaller than it (the staccato and accent).
     */
    private static double scriptStaffPaddingSs(double neighborPaddingSs) {
        return Math.max(neighborPaddingSs, SCRIPT_STAFF_PADDING_SS);
    }

    /**
     * Returns the note's staccato articulation, or {@code null} if it has none.
     */
    private static @Nullable Articulation findStaccato(StaffElement note) {
        return note.findArticulation(ArticulationType.STACCATO);
    }

    /**
     * Returns the note's accent articulation, or {@code null} if it has none.
     */
    private static @Nullable Articulation findAccent(StaffElement note) {
        return note.findArticulation(ArticulationType.ACCENT);
    }


    // ---- Tier 2: Fermata and Trill ----

    /**
     * Stacks fermata for the given column.
     */
    private void stackFermata(
        ElementColumn column,
        StaffExtents outerExtents,
        LayoutResultBuilder builder) {

        var note = column.getElement();

        stackFermataAt(note.findAttachment(FermataAttachment.class), outerExtents,
            note, column.getXSs(), builder);
    }

    /**
     * Places the fermata (if present) above the staff via {@link
     * StackingUtils#placeAndReserveClamped}. Shared by the full pipeline's fermata pass and the
     * no-tie preview so the two agree on fermata placement.
     * <p>
     * The collision footprint is centered on the notehead, exactly where {@code FermataRenderer}
     * draws the glyph — not anchored at the column x. Anchoring at the column x offsets the box
     * from the glyph by half the difference between the fermata's width and the notehead's, so it
     * clears the tie at the wrong x and the drawn glyph's inner bottom corner ends up nearer the
     * tie than its padding. This mirrors {@link #articulationFootprintXSs}.
     * <p>
     * The staff clamp goes through {@link #scriptStaffPaddingSs}, so the fermata keeps its full
     * padding from the top staff line just as it does from a tie or notehead.
     */
    private static void stackFermataAt(
        @Nullable FermataAttachment fermata,
        StaffExtents extents,
        StaffElement note,
        double columnXSs,
        LayoutResultBuilder builder) {

        if (fermata == null) {
            return;
        }

        var widthSs = fermata.getContentWidthSs();
        var xSs = articulationFootprintXSs(note, columnXSs, widthSs);

        StackingUtils.placeAndReserveClamped(Direction.UP, extents, fermata, xSs,
            widthSs, fermata.getContentHeightSs(),
            StaffExtents.Profiles.flat(widthSs),
            FERMATA_PADDING_SS, scriptStaffPaddingSs(FERMATA_PADDING_SS),
            FERMATA_HORIZON_PADDING_SS, builder);
    }

    /**
     * Stacks all trills for the line.
     * <p>
     * Processes {@link Trill} spans from {@code line.findSpans(Trill.class)}.
     * Multi-note trills reserve the full horizontal span so subsequent layers clear them.
     */
    private void stackTrills(StaffExtents outerExtents, LayoutResultBuilder builder) {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
        var trills = line.findSpans(Trill.class);

        for (var trill : trills) {
            stackSingleTrill(trill, outerExtents, columnsByElement, builder);
        }
    }

    /**
     * Stacks a single trill span.
     * <p>
     * Unlike hairpins and endings, trills allow a missing or same-as-anchor end note
     * (single-note trill), defaulting endX to the anchor X.
     * <p>
     * The trill is one horizontal reservation spanning the range, but each note in the range
     * demands its own vertical clearance: the first note seats the trill as a script
     * ({@link #TRILL_SCRIPT_PADDING_SS}), later notes as a spanner
     * ({@link #TRILL_SPANNER_PADDING_SS}). LilyPond places the whole span at whichever note pushes
     * it furthest out, so the span seats at the most-outward inner edge any note requires. Trills
     * are above only; the wavy-line reservation height is unchanged.
     */
    private void stackSingleTrill(
        Trill trill,
        StaffExtents outerExtents,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResultBuilder builder) {

        var anchor = trill.getAnchorElement();

        if (anchor == null) {
            return;
        }

        var anchorColumn = columnsByElement.get(anchor);

        if (anchorColumn == null) {
            return;
        }

        var anchorXSs = anchorColumn.getXSs();
        var endXSs = anchorXSs;

        var endNote = trill.getEndElement();

        if (endNote != null && endNote != anchor) {
            var endColumn = columnsByElement.get(endNote);

            if (endColumn != null) {
                endXSs = endColumn.getXSs();
            }
        }

        var line = context.getLine();
        var anchorIndex = trill.getAnchorElementIndex();
        var endIndex = trill.getEndElementIndex();

        // A single-note trill has no end element (endIndex -1); it degenerates to the anchor alone,
        // seated with the first-note script padding.
        if (endIndex < anchorIndex) {
            endIndex = anchorIndex;
        }

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
                footprintXSs = Trill.glyphLeftXSs(
                    column.getXSs(), NoteGeometry.getNoteheadCenterXSs(note));
                profile = TRILL_PROFILE_ABOVE;
                paddingSs = TRILL_SCRIPT_PADDING_SS;
            } else {
                // Later notes sit under the flat wavy line, which clears them by a notehead-wide box.
                footprintXSs = column.getXSs();
                profile = StaffExtents.Profile.flat(note.getType().getElementWidthSs());
                paddingSs = TRILL_SPANNER_PADDING_SS;
            }

            var support = outerExtents.clearance(true, footprintXSs, profile,
                paddingSs, StackingUtils.SCRIPT_HORIZON_PADDING_SS);

            // An empty footprint carries no real reservation, so only the staff clamp applies.
            if (!support.present()) {
                continue;
            }

            innerEdgeYSs = Math.min(innerEdgeYSs, support.ySs());
        }

        // Reserve the full span at the computed inner edge; the per-note padding is already folded
        // in, so the placement core adds no further margin.
        var widthSs = trill.getSpanWidthSs(anchorXSs, endXSs);
        StackingUtils.placeAndReserve(Direction.UP, outerExtents, trill, anchorXSs, widthSs,
            trill.getContentHeightSs(), innerEdgeYSs, 0.0, builder);
    }

}
