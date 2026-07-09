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
import songscribe.dom.FermataAttachment;
import songscribe.dom.LineElement;
import songscribe.layout.LayoutResult;
import songscribe.engraving.SMuFLConstants;
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
    public static final double FERMATA_PADDING_SS = 0.40;  // LilyPond script.scm fermata

    /**
     * Padding the trill keeps from the first note of its range, where it seats as a script.
     */
    public static final double TRILL_SCRIPT_PADDING_SS = 0.20;  // LilyPond script.scm trill

    /**
     * Padding the trill keeps from the notes after the first in its range, where it seats as a
     * spanner.
     */
    // LilyPond define-grobs.scm TrillSpanner padding
    public static final double TRILL_SPANNER_PADDING_SS = 0.50;

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
        // its own footprint overlaps — the tie only where the arc has curved past the notehead, so
        // a tie that stays tucked against the note leaves the articulations where they'd sit
        // untied.
        for (var column : columns) {
            stackStaccatoColumn(column, builder);
        }

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
        extents.ySet(true, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, bounds.topSs());
        extents.ySet(false, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, bounds.botSs());

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

        // No tie in the preview: place the staccato, then the accent just outside it —
        // equivalent to the full pipeline with no tie seeded ahead of the two passes.
        stackStaccatoOnly(staccatoArticulation, extents, xSs, STACCATO_PADDING_SS,
            staffPosition, direction, builder);
        stackAccentAboveExtents(accentArticulation, extents, xSs, direction, builder);

        // Tier 2: Fermata
        var fermata = note.findAttachment(FermataAttachment.class);

        if (fermata != null) {
            stackAgainstNeighbor(Direction.UP, extents, fermata, xSs,
                fermata.getContentWidthSs(), fermata.getContentHeightSs(),
                FERMATA_PADDING_SS, SCRIPT_STAFF_PADDING_SS, builder);
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

            noteAttachedExtents.ySet(true, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, topSs);
            noteAttachedExtents.ySet(false, xSs, SMuFLConstants.NOTE_HEAD_WIDTH_SS, botSs);

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

            // Reserve on the side the renderer actually draws: Tie.arcSign() > 0 ⇒ arc below
            // (reserve on the below side, above=false); < 0 ⇒ arc above. Routing this through the
            // shared rule keeps a conflicting-stem tie from reserving the opposite side from its bulge.
            var arcsDown = span.arcSign() > 0;
            var sampleCount = Math.max(TIE_BOUND_MIN_SAMPLES, (int) Math.ceil(spanWidthSs));

            seedTieArcIntoExtents(tieLayout, startColumn, endColumn,
                startEdgeYSs, endEdgeYSs, sx, spanWidthSs, sampleCount, !arcsDown);
        }
    }

    /**
     * Reserves the tie arc's vertical extent in the note-attached layer so line sizing accounts for
     * the arc. For downward arcs (above=false) also feeds the notehead-edge Y values and sampled Y
     * values into the context's below-staff content extent so lyric placement clears the arc. Only
     * ties flow through this path today; slur seeding is future work (#515).
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
                SMuFLConstants.NOTE_HEAD_WIDTH_SS, startEdgeYSs);

            if (!above) {
                context.updateBotContentExtentSs(startEdgeYSs);
            }
        }

        if (endColumn != null) {
            noteAttachedExtents.ySet(above, endColumn.getXSs(),
                SMuFLConstants.NOTE_HEAD_WIDTH_SS, endEdgeYSs);

            if (!above) {
                context.updateBotContentExtentSs(endEdgeYSs);
            }
        }

        var segmentWidthSs = spanWidthSs / sampleCount;

        for (var i = 0; i < sampleCount; i++) {
            var tMid = (i + 0.5) / sampleCount;
            var ySs = evaluateBezierYSs(tMid, tieLayout);
            var segmentXSs = sx + i * segmentWidthSs;
            noteAttachedExtents.ySet(above, segmentXSs, segmentWidthSs, ySs);

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
     * The staccato is the innermost script. It anchors relative to the note — clearing an interior
     * staff line by {@link StackingUtils#STACCATO_ON_LINE_DISTANCE_SS}, or sitting
     * {@link StackingUtils#STACCATO_BETWEEN_LINES_DISTANCE_SS} from the note center in a space, and
     * beyond the staff anchoring at the notehead — but is pushed outward if the tie (seeded first)
     * protrudes into its footprint, since LilyPond adds the tie as a side-support element to every
     * script.
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
     * neighbor's outer edge, resolved by {@link #stackAgainstNeighbor}.
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

        var edgeYSs = stackAccentAboveExtents(accent, noteAttachedExtents,
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
        double xSs, double paddingSs, int staffPosition,
        Direction direction,
        LayoutResult.Builder builder) {

        if (staccato == null) {
            return null;
        }

        return stackStaccato(direction, extents, staccato, xSs,
            staccato.getContentWidthSs(), staccato.getContentHeightSs(), paddingSs,
            SCRIPT_STAFF_PADDING_SS, staffPosition, builder);
    }

    /**
     * Places the accent (if present) against its immediate neighbor in the skyline via
     * {@link #stackAgainstNeighbor}. The neighbor — tie, staccato, notehead, or staff line — and
     * its clearance are resolved from the extents, so one accent path serves both the full
     * pipeline's accent pass and the no-tie preview.
     *
     * @return the accent's top Y (above) or bottom Y (below) in staff-space units, or
     *     {@code null} when no accent is present
     */
    private static @Nullable Double stackAccentAboveExtents(
        @Nullable Articulation accent,
        StaffExtents extents,
        double xSs,
        Direction direction,
        LayoutResult.Builder builder) {

        if (accent == null) {
            return null;
        }

        return stackAgainstNeighbor(direction, extents, accent, xSs,
            accent.getContentWidthSs(), accent.getContentHeightSs(),
            ACCENT_PADDING_SS, SCRIPT_STAFF_PADDING_SS, builder);
    }

    /**
     * Places a note-attached decoration at LilyPond's {@code aligned_side} position via
     * {@link StackingUtils#placeAndReserveClamped}: the more-outward of its real-reservation support
     * edge plus {@code paddingSs} and the staff edge plus {@code staffPaddingSs}.
     *
     * @param paddingSs      the decoration's own padding, applied against whatever real reservation
     *     its footprint contacts
     * @param staffPaddingSs the decoration's staff-padding, applied when the staff clamp is the
     *     outward constraint
     * @return the decoration's outer Y in staff-space units (top above, bottom below)
     */
    static double stackAgainstNeighbor(
        Direction direction,
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs,
        double paddingSs, double staffPaddingSs,
        LayoutResult.Builder builder) {

        return StackingUtils.placeAndReserveClamped(direction, extents, element, xSs, widthSs,
            heightSs, paddingSs, staffPaddingSs, builder);
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
            fermata.getContentWidthSs(), fermata.getContentHeightSs(),
            FERMATA_PADDING_SS, SCRIPT_STAFF_PADDING_SS, builder);
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

        var line = context.getLine();
        var anchorIndex = trill.getAnchorElementIndex();
        var endIndex = trill.getEndElementIndex();

        // A single-note trill has no end element (endIndex -1); it degenerates to the anchor alone,
        // seated with the first-note script padding.
        if (endIndex < anchorIndex) {
            endIndex = anchorIndex;
        }

        // Start from the staff clamp (the trill never seats closer to the staff than its
        // staff-padding). Each note then pulls the inner edge further out — above, more outward is a
        // smaller Y — by its own padding past the real reservations under its footprint.
        var innerEdgeYSs = StackingUtils.STAFF_TOP_Y_SS - SCRIPT_STAFF_PADDING_SS;

        for (var index = anchorIndex; index <= endIndex; index++) {
            var column = columnsByElement.get(line.getElement(index));

            if (column == null) {
                continue;
            }

            var supportBoundSs = StackingUtils.yGetExpanded(noteAttachedExtents, true,
                column.getXSs(), SMuFLConstants.NOTE_HEAD_WIDTH_SS);

            // An empty footprint carries no real reservation, so only the staff clamp applies.
            if (supportBoundSs == StaffExtents.EMPTY_EXTENT_SS) {
                continue;
            }

            var paddingSs = (index == anchorIndex)
                ? TRILL_SCRIPT_PADDING_SS
                : TRILL_SPANNER_PADDING_SS;
            innerEdgeYSs = Math.min(innerEdgeYSs, supportBoundSs - paddingSs);
        }

        // Reserve the full span at the computed inner edge; the per-note padding is already folded
        // in, so the placement core adds no further margin.
        var widthSs = trill.getSpanWidthSs(anchorXSs, endXSs);
        StackingUtils.placeAndReserve(Direction.UP, noteAttachedExtents, trill, anchorXSs, widthSs,
            trill.getContentHeightSs(), innerEdgeYSs, 0.0, builder);
    }

}
