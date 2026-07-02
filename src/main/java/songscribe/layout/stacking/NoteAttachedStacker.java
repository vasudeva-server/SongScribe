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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.Articulation;
import songscribe.layout.ElementColumn;
import songscribe.dom.FermataAttachment;
import songscribe.layout.LayoutResult;
import songscribe.smufl.Engraving;
import songscribe.layout.StaffExtents;
import songscribe.dom.Trill;

import org.jspecify.annotations.Nullable;

import static songscribe.layout.stacking.StackingUtils.anchorCeilingSs;
import static songscribe.layout.stacking.StackingUtils.stackAbove;
import static songscribe.layout.stacking.StackingUtils.stackAtAnchor;
import static songscribe.layout.stacking.StackingUtils.stackBeyond;
import static songscribe.layout.stacking.StackingUtils.stackStaccato;

/**
 * Seeds note bounds and stacks note-attached decorations (tiers 1-2).
 * <p>
 * Tier 1: near-note decorations (articulations — staccato, accent).
 * Tier 2: note decorations (fermata, trill).
 * <p>
 * Also populates the {@code notesWithUpwardTie} set on the {@link StackingContext}
 * during tie seeding, which downstream stackers read for margin adjustments.
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
     * Vertical margin between the staff (or notehead) and articulations (staccato, accent).
     * Kept tighter than {@link NoteAttachedStacker#NOTE_DECORATION_MARGIN_SS} so articulations
     * sit close to the note, matching standard engraving.
     */
    public static final double ARTICULATION_MARGIN_SS = 0.20;

    // Gap between accent and staccato when both are stacked on the same note.
    public static final double ACCENT_STACCATO_GAP_SS = 0.125;

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
     * <p>
     * Populates {@link StackingContext#setNotesWithUpwardTie(Set)} during tie seeding
     * so downstream stackers can use reduced margins for tie-affected notes.
     */
    public void stack() {
        var columns = context.getColumns();
        var builder = context.getBuilder();

        // Seed note bounding areas into noteAttachedExtents
        seedNoteBounds();

        // Seed upward-arcing tie bounds so decorations stack above ties
        context.setNotesWithUpwardTie(seedTieBounds());

        // Tier 1: Near-note decorations (articulations)
        for (var column : columns) {
            stackArticulations(column, builder);
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
     * (below the staff) for up-stems. Shared by the layout path ({@link #stackArticulations})
     * and the render path ({@code ArticulationRenderer}) so the "opposite the stem" rule is
     * defined exactly once.
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
     * Unlike {@link #stackArticulations}, this has no {@link StackingContext} to update with
     * the below-staff content extent (used for lyric placement) — the preview exists only to
     * compute decoration positions for rendering, not to size the line, so that update is
     * intentionally skipped here.
     *
     * @param note the note to compute layouts for
     * @param xSs  X position in staff-space units
     * @return a built {@link LayoutResult} containing {@link LayoutResult.DecorationLayout}s
     */
    public static LayoutResult computePreviewDecorationLayouts(StaffElement note, double xSs) {
        // Create minimal extents just wide enough to contain the note
        var lineWidthSs = xSs + Engraving.NOTE_HEAD_WIDTH_SS + 1.0;
        var extents = new StaffExtents(lineWidthSs);

        // Seed note bounds using the non-beamed path
        var bounds = computeNoteBounds(note);
        extents.ySet(true, xSs, Engraving.NOTE_HEAD_WIDTH_SS, bounds.topSs());
        extents.ySet(false, xSs, Engraving.NOTE_HEAD_WIDTH_SS, bounds.botSs());

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

        dispatchArticulationStacking(staccatoArticulation, accentArticulation,
            extents, xSs, ARTICULATION_MARGIN_SS,
            staffPosition, direction, builder);

        // Tier 2: Fermata
        var fermata = note.findAttachment(FermataAttachment.class);

        if (fermata != null) {
            stackAbove(extents, fermata, xSs,
                fermata.getContentWidthSs(), fermata.getContentHeightSs(),
                NOTE_DECORATION_MARGIN_SS, staffPosition, builder);
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
        var centerYSs = StaffExtents.spToSs(element.getStaffPosition());
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
                    * StaffExtents.STAFF_POSITION_OFFSET_SS;
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

            noteAttachedExtents.ySet(true, xSs, Engraving.NOTE_HEAD_WIDTH_SS, topSs);
            noteAttachedExtents.ySet(false, xSs, Engraving.NOTE_HEAD_WIDTH_SS, botSs);

            // Track lowest notehead bottom for lyrics baseline calculation
            var noteheadCenterYSs = element.getStaffPosition()
                * StaffExtents.STAFF_POSITION_OFFSET_SS;
            context.updateLowestNoteBotSs(
                noteheadCenterYSs + StackingUtils.NOTE_HEAD_RADIUS_SS);

            // Track full element bottom (notehead + stem) as below-staff content for lyric placement
            context.updateBotContentExtentSs(botSs);
        }
    }

    /**
     * Seeds upward-arcing tie bounds into the note-attached StaffExtents layer.
     * <p>
     * For each tie where the stem points down ({@code getDirection().isDown()}), the tie arcs upward
     * and may interfere with above-staff decorations. This method samples the outer Bezier
     * curve of each such tie and reserves the curve's vertical extent in the extents layer,
     * ensuring decorations stack above the tie arc.
     * <p>
     * Also returns the set of notes with upward ties so stacking methods
     * can use a reduced margin ({@link NoteAttachedStacker#TIE_DECORATION_MARGIN_SS}) for
     * single-note decorations. A note is only added to the set when the tie endpoint at
     * that note's position is above (more negative Y than) the anchored ceiling, meaning
     * the tie is the actual constraint that pushes decorations higher. When the tie stays
     * within the staff, the normal margin applies.
     *
     * @return notes whose upward tie arc is the active constraint
     */
    private Set<StaffElement> seedTieBounds() {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();
        var builder = context.getBuilder();
        var ties = line.findTies();

        if (ties.isEmpty()) {
            return Set.of();
        }

        var upwardTieNotes = new HashSet<StaffElement>();

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

            var startEdgeT = Math.min(Engraving.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            var endEdgeT = Math.max(1.0 - Engraving.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            var startEdgeYSs = evaluateBezierYSs(startEdgeT, tieLayout);
            var endEdgeYSs = evaluateBezierYSs(endEdgeT, tieLayout);

            // Upper notes (stem up) get downward-arcing ties; others get upward-arcing ties.
            var arcsDown = startElement.getDirection().isUp();
            var sampleCount = Math.max(TIE_BOUND_MIN_SAMPLES, (int) Math.ceil(spanWidthSs));

            if (!arcsDown) {
                // Only use reduced margin for notes where the tie protrudes above the anchor ceiling.
                // Use the notehead-edge Y (not the raw endpoint) since that reflects the visible arc.
                if (startEdgeYSs < anchorCeilingSs(startElement)) {
                    upwardTieNotes.add(startElement);
                }

                if (endEdgeYSs < anchorCeilingSs(endElement)) {
                    upwardTieNotes.add(endElement);
                }
            }

            seedTieArcIntoExtents(tieLayout, startColumn, endColumn,
                startEdgeYSs, endEdgeYSs, sx, spanWidthSs, sampleCount, !arcsDown);
        }

        return upwardTieNotes.isEmpty() ? Set.of() : upwardTieNotes;
    }

    /**
     * Reserves the tie arc's vertical extent in the note-attached layer so line
     * sizing accounts for the arc. For downward arcs (above=false) also feeds the
     * notehead-edge Y values and sampled Y values into the context's below-staff
     * content extent so lyric placement clears the arc.
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
                Engraving.NOTE_HEAD_WIDTH_SS, startEdgeYSs);

            if (!above) {
                context.updateBotContentExtentSs(startEdgeYSs);
            }
        }

        if (endColumn != null) {
            noteAttachedExtents.ySet(above, endColumn.getXSs(),
                Engraving.NOTE_HEAD_WIDTH_SS, endEdgeYSs);

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
     * Stacks articulations for the given column using StaffExtents collision detection.
     * <p>
     * Articulations are placed opposite the stem: above the staff for down-stems,
     * below the staff for up-stems. Staccato anchors relative to the note: it clears
     * an interior staff line by {@link StackingUtils#STACCATO_ON_LINE_DISTANCE_SS}, or
     * sits {@link StackingUtils#STACCATO_BETWEEN_LINES_DISTANCE_SS} from the note center
     * in a space; beyond the staff it anchors at the notehead. Accent anchors at the
     * nearer staff line (or the notehead beyond it) and, when paired with staccato,
     * stacks beyond it using {@link #ACCENT_STACCATO_GAP_SS} as their gap.
     */
    private void stackArticulations(
        ElementColumn column,
        LayoutResult.Builder builder) {

        var note = column.getElement();
        var articulations = note.getArticulations();

        if (articulations.isEmpty()) {
            return;
        }

        var xSs = column.getXSs();
        var staffPosition = note.getStaffPosition();

        // Identify articulation types
        Articulation staccatoArticulation = null;
        Articulation accentArticulation = null;

        for (var a : articulations) {
            if (a.isStaccato()) {
                staccatoArticulation = a;
            } else if (a.isAccent()) {
                accentArticulation = a;
            }
        }

        var direction = articulationDirection(note);

        double marginSs;

        if (direction.isUp()) {
            // Down-stem articulations stack above the staff, where an upward-arcing tie may
            // intrude; use the reduced tie margin when this note's tie is the active constraint.
            marginSs = context.getNotesWithUpwardTie().contains(note)
                ? TIE_DECORATION_MARGIN_SS
                : ARTICULATION_MARGIN_SS;
        } else {
            // Up-stem articulations stack below the staff; upward ties never arc into them.
            marginSs = ARTICULATION_MARGIN_SS;
        }

        var edgeYSs = dispatchArticulationStacking(staccatoArticulation, accentArticulation,
            noteAttachedExtents, xSs, marginSs, staffPosition, direction, builder);

        if (direction.isDown() && edgeYSs != null) {
            // Push the below-staff content extent down so lyrics clear the articulations.
            context.updateBotContentExtentSs(edgeYSs);
        }
    }

    /**
     * Dispatches articulation stacking based on whether both accent and staccato are present.
     * <p>
     * When both are present, staccato is stacked first at its note-relative distance, then
     * accent is stacked beyond it (via {@link StackingUtils#stackBeyond}) using
     * {@link #ACCENT_STACCATO_GAP_SS} as their gap — but never closer to the staff than
     * {@link #ARTICULATION_MARGIN_SS} from the staff line, since staccato's note-relative
     * position can sit closer to the staff than accent's minimum clearance requires.
     *
     * @return the placed element's top Y (above) or bottom Y (below) in staff-space units,
     *     or {@code null} if neither articulation was present (both callers guard against this
     *     by only invoking this method for notes with at least one articulation, so this is
     *     unreachable given {@link songscribe.dom.ArticulationType}'s current two members)
     */
    private static @Nullable Double dispatchArticulationStacking(
        @Nullable Articulation staccatoArticulation,
        @Nullable Articulation accentArticulation,
        StaffExtents extents,
        double xSs, double marginSs, int staffPosition,
        Direction direction,
        LayoutResult.Builder builder) {

        if (staccatoArticulation != null && accentArticulation != null) {
            stackSingleArticulation(staccatoArticulation, extents,
                xSs, marginSs, staffPosition, direction, builder);

            var accentWidthSs = accentArticulation.getContentWidthSs();
            var accentHeightSs = accentArticulation.getContentHeightSs();

            return stackBeyond(direction, extents, accentArticulation, xSs,
                accentWidthSs, accentHeightSs, ACCENT_STACCATO_GAP_SS, ARTICULATION_MARGIN_SS, builder);
        }

        if (staccatoArticulation != null) {
            return stackSingleArticulation(staccatoArticulation, extents,
                xSs, marginSs, staffPosition, direction, builder);
        }

        if (accentArticulation != null) {
            return stackSingleArticulation(accentArticulation, extents,
                xSs, marginSs, staffPosition, direction, builder);
        }

        return null;
    }

    /**
     * Stacks a single articulation with anchored collision detection, above or below the staff.
     * <p>
     * Staccato uses its note-relative anchor ({@link StackingUtils#stackStaccato}); accent
     * uses the generic staff-line anchor ({@link StackingUtils#stackAtAnchor}).
     *
     * @return the placed element's top Y (above) or bottom Y (below) in staff-space units
     */
    private static double stackSingleArticulation(
        Articulation articulation,
        StaffExtents extents,
        double xSs, double marginSs, int staffPosition,
        Direction direction,
        LayoutResult.Builder builder) {

        var widthSs = articulation.getContentWidthSs();
        var heightSs = articulation.getContentHeightSs();

        if (articulation.isStaccato()) {
            return stackStaccato(direction, extents, articulation, xSs,
                widthSs, heightSs, marginSs, staffPosition, builder);
        }

        return stackAtAnchor(direction, extents, articulation, xSs,
            widthSs, heightSs, marginSs, staffPosition, builder);
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

        var xSs = column.getXSs();
        var staffPosition = note.getStaffPosition();

        stackAbove(noteAttachedExtents, fermata, xSs,
            fermata.getContentWidthSs(), fermata.getContentHeightSs(),
            NOTE_DECORATION_MARGIN_SS, staffPosition, builder);
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

        var staffPosition = anchor.getStaffPosition();
        var widthSs = trill.getSpanWidthSs(anchorXSs, endXSs);
        stackAbove(noteAttachedExtents, trill, anchorXSs, widthSs,
            trill.getContentHeightSs(), NOTE_DECORATION_MARGIN_SS,
            staffPosition, builder);
    }

}
