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

import java.util.List;
import java.util.Map;

import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.layout.ElementColumn;
import songscribe.dom.FermataAttachment;
import songscribe.dom.LineElement;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.engraving.SMuFLConstants;
import songscribe.layout.Neighbor;
import songscribe.layout.StaffExtents;
import songscribe.engraving.Staff;
import songscribe.dom.Trill;

import org.jspecify.annotations.Nullable;

import static songscribe.layout.stacking.StackingUtils.stackStaccato;

/**
 * Seeds note bounds and stacks note-attached decorations (tiers 1-2).
 * <p>
 * Tier 1: near-note decorations (articulations — staccato, accent).
 * Tier 2: note decorations (fermata, trill).
 */
public class NoteAttachedStacker {

    /**
     * Default vertical margin between note decorations during stacking.
     * Used for articulations, fermata, trill, and text dynamics.
     */
    public static final double NOTE_DECORATION_MARGIN_SS = 0.5;  // 4px
    /**
     * Vertical margin between single-note decorations and upward-arcing tie curves.
     * Smaller than {@link NoteAttachedStacker#NOTE_DECORATION_MARGIN_SS} since the tie arc already
     * provides visual separation from the notehead.
     */
    public static final double TIE_DECORATION_MARGIN_SS = 0.25;  // 2px
    /**
     * Vertical clearance between an articulation and the staff or notehead. Per #507, an accent's
     * outer edge clears the outer staff line — or the notehead's outer edge when the notehead
     * protrudes past the staff — by this amount. Kept tighter than
     * {@link NoteAttachedStacker#NOTE_DECORATION_MARGIN_SS} so articulations sit close to the note,
     * matching standard engraving.
     */
    public static final double ARTICULATION_MARGIN_SS = 0.195;  // per #507

    /**
     * Distance from a staccato dot's center to the accent's inner edge when both stack on the same
     * note, per #507. The center-to-edge conversion (since the skyline reports the staccato's outer
     * edge, not its center) is applied in {@link NoteAttachedStacker#stackAgainstNeighbor} via the
     * staccato half-height.
     */
    public static final double ACCENT_STACCATO_CENTER_MARGIN_SS = 0.27;  // per #507

    /**
     * Outward gap between a placed staccato dot's center and the tie endpoint, in staff spaces.
     * When a staccato is tucked under a tie arc, the tie is shifted outward until its endpoint
     * clears the dot center by this amount.
     */
    public static final double STACCATO_TIE_GAP_SS = 0.55;

    // Minimum number of Bezier samples when seeding tie bounds into extents.
    // Ensures adequate curve resolution even for short ties.
    private static final int TIE_BOUND_MIN_SAMPLES = 8;

    private final StackingContext context;
    private final StaffExtents noteAttachedExtents;

    public NoteAttachedStacker(StackingContext context, StaffExtents noteAttachedExtents) {
        this.context = context;
        this.noteAttachedExtents = noteAttachedExtents;
    }

    /**
     * Seeds note bounds and tie bounds, then stacks all note-attached decorations.
     */
    public void stack() {
        var columns = context.getColumns();
        var builder = context.getBuilder();

        // Seed note bounding areas into noteAttachedExtents
        seedNoteBounds();

        // Tier 1a: Staccato — placed before the tie is seeded so it ignores the tie and tucks
        // under the arc, staying note-relative (LilyPond `avoid-slur inside`).
        for (var column : columns) {
            stackStaccatoColumn(column, builder);
        }

        // Shift each tie outward to clear the staccato dots just placed. Runs before seeding so
        // seedTieBounds samples the shifted arc.
        clearStaccatoUnderTies();

        // Seed tie arc bounds so the accent stacks clear of the ties
        seedTieBounds();

        // Tier 1b: Accent — placed after the tie is seeded so it stacks above whichever is
        // highest in the extents, the tie when present (LilyPond `avoid-slur around`).
        for (var column : columns) {
            stackAccentColumn(column, builder);
        }

        // Tier 2: Note decorations (fermata, trill)
        for (var column : columns) {
            stackFermata(column, builder);
        }

        stackTrills(builder);
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
        // Create minimal extents just wide enough to contain the note
        var lineWidthSs = xSs + SMuFLConstants.NOTE_HEAD_WIDTH_SS + 1.0;
        var extents = new StaffExtents(lineWidthSs);

        // Seed note bounds using the non-beamed path
        var bounds = computeNoteBounds(note);
        extents.ySet(true, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, bounds.topSs(), Neighbor.NOTEHEAD);
        extents.ySet(false, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, bounds.botSs(), Neighbor.NOTEHEAD);

        var builder = new LayoutResult.Builder();
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
        var staccatoHalfHeightSs = staccatoHalfHeightSs(staccatoArticulation);

        // No tie in the preview: place staccato, then the accent above it back-to-back —
        // equivalent to the full pipeline with an empty tie seed between the two passes.
        stackStaccatoOnly(staccatoArticulation, extents, xSs, ARTICULATION_MARGIN_SS,
            staffPosition, direction, builder);
        stackAccentAboveExtents(accentArticulation, staccatoHalfHeightSs, extents,
            xSs, direction, builder);

        // Tier 2: Fermata
        var fermata = note.findAttachment(FermataAttachment.class);

        if (fermata != null) {
            stackAgainstNeighbor(Direction.UP, extents, fermata, xSs,
                fermata.getContentWidthSs(), fermata.getContentHeightSs(), Neighbor.FERMATA,
                0, builder);
        }

        return builder.build();
    }


    // ---- Note bounds ----

    /**
     * Vertical bounds of a note without stem layout (non-beamed path).
     */
    record NoteBounds(double topSs, double botSs) {
    }

    /**
     * Computes note vertical bounds from element type geometry alone (no stem layout).
     */
    static NoteBounds computeNoteBounds(StaffElement element) {
        var centerYSs = Staff.spToSs(element.getStaffPosition());
        var type = element.getType();
        var direction = element.getDirection();
        var noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
        var noteheadBotSs = noteheadTopSs + type.getFullElementHeightSs();
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
                var noteheadBotSs = noteheadTopSs + type.getFullElementHeightSs();
                topSs = Math.min(stemLayout.topYSs(), noteheadTopSs);
                botSs = Math.max(stemLayout.bottomYSs(), noteheadBotSs);
            } else {
                var bounds = computeNoteBounds(element);
                topSs = bounds.topSs();
                botSs = bounds.botSs();
            }

            noteAttachedExtents.ySet(true, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, topSs, Neighbor.NOTEHEAD);
            noteAttachedExtents.ySet(false, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, botSs, Neighbor.NOTEHEAD);

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
     * Shifts each tie outward so its arc clears a staccato dot tucked beneath it.
     * <p>
     * Runs after the staccato pass has placed the actual dots but before the tie is seeded into
     * the extents, so the seed samples the shifted arc. Only ties carrying a staccato on either
     * endpoint note are moved: the tie is rigidly translated until its endpoint sits the larger of
     * two outward clearances beyond the note — {@link #STACCATO_TIE_GAP_SS} past the outermost
     * placed dot center, or {@link LayoutEngine#STAFF_LINE_TIE_CLEARANCE_GAP_SS} past the outer
     * staff line. The shift is always outward; a tie already clearing the dot is left untouched.
     */
    private void clearStaccatoUnderTies() {
        var line = context.getLine();
        var builder = context.getBuilder();

        for (var span : line.findTies()) {
            var startElement = span.getAnchorElement();
            var endElement = span.getEndElement();

            if (startElement == null || endElement == null) {
                continue;
            }

            var tieLayout = builder.getTieLayout(span);

            if (tieLayout == null) {
                continue;
            }

            // Arc sign: stem-up notes tie below (+1), stem-down notes tie above (-1); Y grows downward.
            var arcSign = startElement.getDirection().sign();

            // Outward magnitude (arcSign × Y) of the outermost placed dot; null when neither note has one.
            var dotCenterMag = outermostStaccatoCenterMag(startElement, endElement, arcSign, builder);

            if (dotCenterMag == null) {
                continue;
            }

            // Clear the dot, but never sit closer to the staff than the outer-staff-line clearance.
            var targetMag = Math.max(dotCenterMag + STACCATO_TIE_GAP_SS,
                Staff.STAFF_HALF_SS + LayoutEngine.STAFF_LINE_TIE_CLEARANCE_GAP_SS);
            var target = arcSign * targetMag;
            var delta = target - tieLayout.startYSs();

            // Staccato only ever pushes the tie outward; leave a tie that already clears the dot.
            if (arcSign * delta > 0) {
                builder.putTieLayout(span, tieLayout.translateY(delta));
            }
        }
    }

    /**
     * Returns the outward magnitude ({@code arcSign × center Y}) of the outermost placed staccato
     * dot across the tie's two endpoint notes, or {@code null} when neither note carries a placed
     * staccato. "Outermost" is the dot furthest from the note in the arc direction — the one the
     * tie must clear.
     */
    private static @Nullable Double outermostStaccatoCenterMag(
        StaffElement startElement,
        StaffElement endElement,
        int arcSign,
        LayoutResult.Builder builder) {

        Double outermostMag = null;

        for (var note : List.of(startElement, endElement)) {
            var staccato = findStaccato(note);

            if (staccato == null) {
                continue;
            }

            var dotLayout = builder.getDecorationLayout(staccato);

            if (dotLayout == null) {
                continue;
            }

            var dotCenterMag = arcSign * (dotLayout.ySs() + dotLayout.heightSs() / 2);

            if (outermostMag == null || dotCenterMag > outermostMag) {
                outermostMag = dotCenterMag;
            }
        }

        return outermostMag;
    }

    /**
     * Seeds tie arc bounds into the note-attached StaffExtents layer.
     * <p>
     * For each tie the outer Bezier curve is sampled and its vertical extent reserved in the
     * extents layer (tagged {@link Neighbor#TIE} by {@link #seedTieArcIntoExtents}) so decorations
     * stack clear of the tie arc. Upward-arcing ties (stem down) reserve above the staff;
     * downward-arcing ties (stem up) reserve below.
     */
    private void seedTieBounds() {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
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

            // Seed tie bounds at the start and end noteheads using the Bezier Y at the
            // far edge of each notehead (where the tie has curved away from the notehead),
            // not at the attachment point (where the tie just touches the notehead).
            var startColumn = columnsByElement.get(startElement);
            var endColumn = columnsByElement.get(endElement);

            var startEdgeT = Math.min(SMuFLConstants.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            var endEdgeT = Math.max(1.0 - SMuFLConstants.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            var startEdgeYSs = evaluateBezierYSs(startEdgeT, tieLayout);
            var endEdgeYSs = evaluateBezierYSs(endEdgeT, tieLayout);

            // Upper notes (stem up) get downward-arcing ties; others get upward-arcing ties.
            var arcsDown = startElement.getDirection().isUp();
            var sampleCount = Math.max(TIE_BOUND_MIN_SAMPLES, (int) Math.ceil(spanWidthSs));

            seedTieArcIntoExtents(tieLayout, startColumn, endColumn,
                startEdgeYSs, endEdgeYSs, sx, spanWidthSs, sampleCount, !arcsDown);
        }
    }

    /**
     * Reserves the tie arc's vertical extent in the note-attached layer — tagged
     * {@link Neighbor#TIE} so a decoration later querying the arc resolves it as a tie — so line
     * sizing accounts for the arc. For downward arcs (above=false) also feeds the notehead-edge Y
     * values and sampled Y values into the context's below-staff content extent so lyric placement
     * clears the arc. Only ties flow through this path today; slur seeding is future work (#515).
     */
    private void seedTieArcIntoExtents(
        LayoutResult.TieLayout tieLayout,
        @Nullable ElementColumn startColumn,
        @Nullable ElementColumn endColumn,
        double startEdgeYSs,
        double endEdgeYSs,
        double sx,
        double spanWidthSs,
        int sampleCount,
        boolean above) {

        if (startColumn != null) {
            noteAttachedExtents.ySet(above, startColumn.getXSs(),
                SMuFLConstants.NOTE_HEAD_WIDTH_SS, startEdgeYSs, Neighbor.TIE);

            if (!above) {
                context.updateBotContentExtentSs(startEdgeYSs);
            }
        }

        if (endColumn != null) {
            noteAttachedExtents.ySet(above, endColumn.getXSs(),
                SMuFLConstants.NOTE_HEAD_WIDTH_SS, endEdgeYSs, Neighbor.TIE);

            if (!above) {
                context.updateBotContentExtentSs(endEdgeYSs);
            }
        }

        var segmentWidthSs = spanWidthSs / sampleCount;

        for (var i = 0; i < sampleCount; i++) {
            var tMid = (i + 0.5) / sampleCount;
            var ySs = evaluateBezierYSs(tMid, tieLayout);
            var segmentXSs = sx + i * segmentWidthSs;
            noteAttachedExtents.ySet(above, segmentXSs, segmentWidthSs, ySs, Neighbor.TIE);

            if (!above) {
                context.updateBotContentExtentSs(ySs);
            }
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
        var mt = 1.0 - t;
        return mt * mt * mt * tieLayout.startYSs()
            + 3 * mt * mt * t * tieLayout.cp1YSs()
            + 3 * mt * t * t * tieLayout.cp2YSs()
            + t * t * t * tieLayout.endYSs();
    }


    // ---- Tier 1: Articulations ----

    /**
     * Places the staccato dot for the given column, if present.
     * <p>
     * Runs before the tie is seeded, so it stays note-relative and ignores the tie, tucking
     * under the arc (LilyPond {@code avoid-slur inside}). Staccato anchors relative to the note:
     * it clears an interior staff line by {@link StackingUtils#STACCATO_ON_LINE_DISTANCE_SS}, or
     * sits {@link StackingUtils#STACCATO_BETWEEN_LINES_DISTANCE_SS} from the note center in a
     * space; beyond the staff it anchors at the notehead.
     */
    private void stackStaccatoColumn(
        ElementColumn column,
        LayoutResult.Builder builder) {

        var note = column.getElement();
        var staccato = findStaccato(note);

        if (staccato == null) {
            return;
        }

        var direction = articulationDirection(note);
        var edgeYSs = stackStaccatoOnly(staccato, noteAttachedExtents, column.getXSs(),
            ARTICULATION_MARGIN_SS, note.getStaffPosition(), direction, builder);

        updateBelowStaffContentExtent(direction, edgeYSs);
    }

    /**
     * Places the accent for the given column, if present.
     * <p>
     * Runs after the tie is seeded, so it stacks against whatever is most outward in the extents —
     * the tie when present, else the staccato, else the notehead or staff line (LilyPond
     * {@code avoid-slur around}). The clearance for each neighbor is resolved by
     * {@link #pairMarginSs} inside {@link #stackAgainstNeighbor}.
     */
    private void stackAccentColumn(
        ElementColumn column,
        LayoutResult.Builder builder) {

        var note = column.getElement();
        var accent = findAccent(note);

        if (accent == null) {
            return;
        }

        var direction = articulationDirection(note);
        var staccatoHalfHeightSs = staccatoHalfHeightSs(findStaccato(note));

        var edgeYSs = stackAccentAboveExtents(accent, staccatoHalfHeightSs, noteAttachedExtents,
            column.getXSs(), direction, builder);

        updateBelowStaffContentExtent(direction, edgeYSs);
    }

    /**
     * Places the staccato dot (if present) at its note-relative anchor
     * ({@link StackingUtils#stackStaccato}), tagging its footprint {@link Neighbor#STACCATO} so a
     * later accent querying that step resolves the staccato as its neighbor. Shared by the full
     * pipeline's staccato pass and the no-tie preview so the two agree on staccato placement.
     *
     * @return the dot's top Y (above) or bottom Y (below) in staff-space units, or {@code null}
     *     when no staccato is present
     */
    private static @Nullable Double stackStaccatoOnly(
        @Nullable Articulation staccato,
        StaffExtents extents,
        double xSs, double marginSs, int staffPosition,
        Direction direction,
        LayoutResult.Builder builder) {

        if (staccato == null) {
            return null;
        }

        return stackStaccato(direction, extents, staccato, xSs,
            staccato.getContentWidthSs(), staccato.getContentHeightSs(), marginSs,
            staffPosition, Neighbor.STACCATO, builder);
    }

    /**
     * Half the staccato's content height, or 0 when no staccato is present. Used to convert the
     * accent's center-relative clearance to the outer edge the skyline reports.
     */
    private static double staccatoHalfHeightSs(@Nullable Articulation staccato) {
        return staccato == null ? 0 : staccato.getContentHeightSs() / 2;
    }

    /**
     * Places the accent (if present) against its immediate neighbor in the tagged skyline via
     * {@link #stackAgainstNeighbor}. The neighbor — tie, staccato, notehead, or staff line — and
     * its clearance are resolved from the extents, so one accent path serves both the full
     * pipeline's accent pass and the no-tie preview.
     *
     * @param staccatoHalfHeightSs half the staccato's content height, or 0 when no staccato is
     *     present; consulted only when the resolved neighbor is a {@link Neighbor#STACCATO}
     * @return the accent's top Y (above) or bottom Y (below) in staff-space units, or
     *     {@code null} when no accent is present
     */
    private static @Nullable Double stackAccentAboveExtents(
        @Nullable Articulation accent,
        double staccatoHalfHeightSs,
        StaffExtents extents,
        double xSs,
        Direction direction,
        LayoutResult.Builder builder) {

        if (accent == null) {
            return null;
        }

        return stackAgainstNeighbor(direction, extents, accent, xSs,
            accent.getContentWidthSs(), accent.getContentHeightSs(), Neighbor.ACCENT,
            staccatoHalfHeightSs, builder);
    }

    /**
     * Vertical clearance a {@code decoration} must keep from its immediate {@code neighbor} in the
     * stack, in staff spaces. Total by construction: the outer switch handles every decoration (an
     * explicit {@code ACCENT} arm plus a default covering {@code TRILL}/{@code FERMATA}), and the
     * accent arm handles every neighbor, so no reachable pair falls through to an undefined margin.
     * <p>
     * The accent-over-staccato pair is handled directly in {@link #stackAgainstNeighbor} (it needs
     * the staccato's half-height for the center-to-edge conversion) and so never reaches this
     * lookup.
     */
    static double pairMarginSs(Neighbor decoration, Neighbor neighbor) {
        return switch (decoration) {
            case ACCENT -> switch (neighbor) {
                case TIE -> TIE_DECORATION_MARGIN_SS;
                // Accent is always innermost, so its only real decoration neighbor is a tie; every
                // other neighbor (notehead, staff line) uses the articulation-to-staff clearance.
                default -> ARTICULATION_MARGIN_SS;
            };
            // TRILL and FERMATA use one uniform margin for every neighbor, including the reachable
            // TRILL×ACCENT, FERMATA×TRILL, and FERMATA×ACCENT (order outward: accent → trill →
            // fermata). #507 only constrains accent clearances; per-neighbor trill/fermata margins
            // are deferred to #515.
            default -> NOTE_DECORATION_MARGIN_SS;
        };
    }

    /**
     * Places a note-attached decoration against its immediate neighbor in the tagged skyline.
     * Queries {@link StaffExtents#contact} under the decoration's footprint, looks the clearance up
     * in {@link #pairMarginSs} from the neighbor's tag, then delegates to
     * {@link StackingUtils#placeAndReserve}, which reserves the footprint tagged
     * {@code decorationTag} and returns the decoration's outer Y.
     *
     * @param staccatoHalfHeightSs half the staccato's content height; consulted only for the
     *     accent-over-staccato pair, and 0 otherwise
     * @return the decoration's outer Y in staff-space units (top above, bottom below)
     */
    static double stackAgainstNeighbor(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, Neighbor decorationTag,
        double staccatoHalfHeightSs,
        LayoutResult.Builder builder) {

        var above = direction.isUp();
        var contact = StackingUtils.contactExpanded(extents, above, xSs, widthSs);

        double marginSs;

        if (decorationTag == Neighbor.ACCENT && contact.tag() == Neighbor.STACCATO) {
            // contact() reports the staccato's true outer edge, but #507 measures the accent's
            // inner-edge clearance from the staccato's *center*. Convert: an inner edge
            // ACCENT_STACCATO_CENTER_MARGIN_SS from the center is that value minus the staccato's
            // half-height from the outer edge contact() reports. The staccato stays reserved at its
            // true outer edge, so any later decoration querying that step sees its real occupancy.
            // Only the accent uses this center margin; a trill or fermata over a staccato takes its
            // uniform pairMarginSs clearance (below), measured from the same outer edge.
            marginSs = ACCENT_STACCATO_CENTER_MARGIN_SS - staccatoHalfHeightSs;
        } else {
            marginSs = pairMarginSs(decorationTag, contact.tag());
        }

        return StackingUtils.placeAndReserve(direction, extents, element, xSs, widthSs, heightSs,
            contact.ySs(), marginSs, decorationTag, builder);
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
        LayoutResult.Builder builder) {

        var note = column.getElement();
        var fermata = note.findAttachment(FermataAttachment.class);

        if (fermata == null) {
            return;
        }

        stackAgainstNeighbor(Direction.UP, noteAttachedExtents, fermata, column.getXSs(),
            fermata.getContentWidthSs(), fermata.getContentHeightSs(), Neighbor.FERMATA,
            0, builder);
    }

    /**
     * Stacks all trills for the line.
     * <p>
     * Processes {@link Trill} range elements from {@code line.findRangeElements(Trill.class)}.
     * Multi-note trills reserve the full horizontal span so subsequent layers clear them.
     */
    private void stackTrills(LayoutResult.Builder builder) {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
        var trills = line.findRangeElements(Trill.class);

        for (var trill : trills) {
            stackSingleTrill(trill, columnsByElement, builder);
        }
    }

    /**
     * Stacks a single trill range element.
     * <p>
     * Unlike hairpins and endings, trills allow a missing or same-as-anchor end note
     * (single-note trill), defaulting endX to the anchor X.
     */
    private void stackSingleTrill(
        Trill trill,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder) {

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

        var widthSs = trill.getSpanWidthSs(anchorXSs, endXSs);
        stackAgainstNeighbor(Direction.UP, noteAttachedExtents, trill, anchorXSs, widthSs,
            trill.getContentHeightSs(), Neighbor.TRILL, 0, builder);
    }

}
