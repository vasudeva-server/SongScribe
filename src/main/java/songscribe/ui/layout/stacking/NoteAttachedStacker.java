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

package songscribe.ui.layout.stacking;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.ElementColumn;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.LayoutResult;
import songscribe.smufl.Engraving;
import songscribe.ui.layout.StaffExtents;
import songscribe.ui.layout.Trill;

import org.jspecify.annotations.Nullable;

import static songscribe.ui.layout.stacking.StackingUtils.anchorCeilingSs;
import static songscribe.ui.layout.stacking.StackingUtils.stackAbove;

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
    // Precomposed accent+staccato glyph dimensions (staff-space units)
    private static final double ACCENT_STACCATO_WIDTH_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE).width();

    private static final double ACCENT_STACCATO_HEIGHT_SS =
        SMuFLMetadata.getInstance().requireBBox(SMuFLGlyph.ARTIC_ACCENT_STACCATO_ABOVE).height();

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
     * Computes note-attached decoration layouts for a single note without a full layout pipeline.
     * <p>
     * Used by the insertion note preview, where no {@link LayoutResult} is available.
     * Creates a minimal {@link StaffExtents}, seeds note bounds, and runs the same
     * stacking logic as the full pipeline (articulations, then fermata).
     *
     * @param note the note to compute layouts for
     * @param xSs  X position in staff-space units
     * @return a built {@link LayoutResult} containing {@link LayoutResult.DecorationLayout}s
     */
    public static LayoutResult computePreviewDecorationLayouts(StaffElement note, double xSs) {
        // Create minimal extents just wide enough to contain the note
        double lineWidthSs = xSs + Engraving.NOTE_HEAD_WIDTH_SS + 1.0;
        var extents = new StaffExtents(lineWidthSs);

        // Seed note bounds using the non-beamed path
        var bounds = computeNoteBounds(note);
        extents.ySet(true, xSs, Engraving.NOTE_HEAD_WIDTH_SS, bounds.topSs());
        extents.ySet(false, xSs, Engraving.NOTE_HEAD_WIDTH_SS, bounds.botSs());

        var builder = new LayoutResult.Builder();
        int staffPosition = note.getStaffPosition();

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

        dispatchArticulationStacking(staccatoArticulation, accentArticulation,
            extents, xSs, NOTE_DECORATION_MARGIN_SS,
            staffPosition, builder);

        // Tier 2: Fermata
        if (note.isFermata()) {
            var fermata = new FermataAttachment(note);
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
    private record NoteBounds(double topSs, double botSs) {
    }

    /**
     * Computes note vertical bounds from element type geometry alone (no stem layout).
     */
    private static NoteBounds computeNoteBounds(StaffElement element) {
        double centerYSs = StaffExtents.spToSs(element.getStaffPosition());
        var type = element.getType();
        boolean upper = element.isUpper();
        double noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
        double noteheadBotSs = noteheadTopSs + type.getFullElementHeightSs();
        double topSs = Math.min(centerYSs + type.getTopYOffsetSs(upper), noteheadTopSs);
        double botSs = Math.max(topSs + type.getElementHeightSs(upper), noteheadBotSs);
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
            double xSs = column.getXSs();

            double topSs;
            double botSs;

            if (stemLayout != null) {
                double centerYSs = element.getStaffPosition()
                    * StaffExtents.STAFF_POSITION_OFFSET_SS;
                var type = element.getType();
                double noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
                double noteheadBotSs = noteheadTopSs + type.getFullElementHeightSs();
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
            double noteheadCenterYSs = element.getStaffPosition()
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
     * For each tie where the stem points down ({@code !isUpper()}), the tie arcs upward
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
        var ties = line.getTies();

        if (ties.isEmpty()) {
            return Set.of();
        }

        var upwardTieNotes = new HashSet<StaffElement>();

        for (var it = ties.listIterator(); it.hasNext(); ) {
            var span = it.next();
            var startElement = line.getElement(span.getStart());
            var tieLayout = builder.getTieLayout(span);

            if (tieLayout == null) {
                continue;
            }

            var endElement = line.getElement(span.getEnd());

            // Sample the outer Bezier curve to reserve tie vertical extent
            double sx = tieLayout.startXSs();
            double ex = tieLayout.endXSs();
            double spanWidthSs = ex - sx;

            if (spanWidthSs <= 0) {
                continue;
            }

            // Seed tie bounds at the start and end noteheads using the Bezier Y at the
            // far edge of each notehead (where the tie has curved away from the notehead),
            // not at the attachment point (where the tie just touches the notehead).
            var startColumn = columnsByElement.get(startElement);
            var endColumn = columnsByElement.get(endElement);

            double startEdgeT = Math.min(Engraving.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            double endEdgeT = Math.max(1.0 - Engraving.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            double startEdgeYSs = evaluateBezierYSs(startEdgeT, tieLayout);
            double endEdgeYSs = evaluateBezierYSs(endEdgeT, tieLayout);

            // Upper notes (stem up) get downward-arcing ties; others get upward-arcing ties.
            var arcsDown = startElement.isUpper();
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

        double segmentWidthSs = spanWidthSs / sampleCount;

        for (int i = 0; i < sampleCount; i++) {
            double tMid = (i + 0.5) / sampleCount;
            double ySs = evaluateBezierYSs(tMid, tieLayout);
            double segmentXSs = sx + i * segmentWidthSs;
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
    private static double evaluateBezierYSs(double t, LayoutResult.TieLayout tieLayout) {
        double mt = 1.0 - t;
        return mt * mt * mt * tieLayout.startYSs()
            + 3 * mt * mt * t * tieLayout.cp1YSs()
            + 3 * mt * t * t * tieLayout.cp2YSs()
            + t * t * t * tieLayout.endYSs();
    }


    // ---- Tier 1: Articulations ----

    /**
     * Stacks articulations for the given column using StaffExtents collision detection.
     * <p>
     * Articulations are always placed above the staff (vocal music context).
     * Notes within or below the staff anchor above the top staff line;
     * notes at or above the top staff line anchor above the notehead.
     * Staccato is placed closest to the notehead; accent stacks beyond staccato.
     */
    private void stackArticulations(
        ElementColumn column,
        LayoutResult.Builder builder) {

        var note = column.getElement();
        var articulations = note.getArticulations();

        if (articulations.isEmpty()) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();

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

        // Use reduced margin for notes with upward ties
        double marginSs = context.getNotesWithUpwardTie().contains(note)
            ? TIE_DECORATION_MARGIN_SS
            : NOTE_DECORATION_MARGIN_SS;

        dispatchArticulationStacking(staccatoArticulation, accentArticulation,
            noteAttachedExtents, xSs, marginSs, staffPosition, builder);
    }

    /**
     * Dispatches articulation stacking based on whether both accent and staccato are present.
     * When both are present, uses the precomposed accent+staccato glyph keyed on the staccato
     * articulation; otherwise stacks each articulation individually.
     */
    private static void dispatchArticulationStacking(
        @Nullable Articulation staccatoArticulation,
        @Nullable Articulation accentArticulation,
        StaffExtents extents,
        double xSs, double marginSs, int staffPosition,
        LayoutResult.Builder builder) {

        if (staccatoArticulation != null && accentArticulation != null) {
            stackAbove(extents, staccatoArticulation, xSs,
                ACCENT_STACCATO_WIDTH_SS, ACCENT_STACCATO_HEIGHT_SS,
                marginSs, staffPosition, builder);
        } else {
            if (staccatoArticulation != null) {
                stackSingleArticulation(staccatoArticulation, extents,
                    xSs, marginSs, staffPosition, builder);
            }

            if (accentArticulation != null) {
                stackSingleArticulation(accentArticulation, extents,
                    xSs, marginSs, staffPosition, builder);
            }
        }
    }

    /**
     * Stacks a single articulation above the staff with anchored ceiling collision detection.
     */
    private static void stackSingleArticulation(
        Articulation articulation,
        StaffExtents extents,
        double xSs, double marginSs, int staffPosition,
        LayoutResult.Builder builder) {

        stackAbove(extents, articulation, xSs,
            articulation.getContentWidthSs(), articulation.getContentHeightSs(),
            marginSs, staffPosition, builder);
    }


    // ---- Tier 2: Fermata and Trill ----

    /**
     * Stacks fermata for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.isFermata()} flag and bridges it to a temporary {@link FermataAttachment}
     * so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackFermata(
        ElementColumn column,
        LayoutResult.Builder builder) {

        var note = column.getElement();

        // Check new attachment hierarchy first
        var fermata = note.findAttachment(FermataAttachment.class);

        // Bridge legacy flag to a temporary FermataAttachment
        if (fermata == null && note.isFermata()) {
            fermata = new FermataAttachment(note);
        }

        if (fermata == null) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();

        stackAbove(noteAttachedExtents, fermata, xSs,
            fermata.getContentWidthSs(), fermata.getContentHeightSs(),
            NOTE_DECORATION_MARGIN_SS, staffPosition, builder);
    }

    /**
     * Stacks all trills for the line.
     * <p>
     * Processes {@link Trill} objects from {@code line.findRangeElements(Trill.class)},
     * then bridges any legacy {@code note.isTrill()} flags not already covered.
     * Multi-note trills reserve the full horizontal span so subsequent layers clear them.
     */
    private void stackTrills(LayoutResult.Builder builder) {
        var line = context.getLine();
        var columnsByElement = context.getColumnsByElement();

        // Process new Trill range elements
        var trills = line.findRangeElements(Trill.class);

        for (var trill : trills) {
            stackSingleTrill(trill, columnsByElement, builder);
        }

        // Bridge legacy isTrill() flags not covered by Trill range elements
        bridgeLegacyTrillFlags(line, columnsByElement, trills, builder);
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

        double anchorXSs = anchorColumn.getXSs();
        double endXSs = anchorXSs;

        var endNote = trill.getEndElement();

        if (endNote != null && endNote != anchor) {
            var endColumn = columnsByElement.get(endNote);

            if (endColumn != null) {
                endXSs = endColumn.getXSs();
            }
        }

        int staffPosition = anchor.getStaffPosition();
        double widthSs = trill.getSpanWidthSs(anchorXSs, endXSs);
        stackAbove(noteAttachedExtents, trill, anchorXSs, widthSs,
            trill.getContentHeightSs(), NOTE_DECORATION_MARGIN_SS,
            staffPosition, builder);
    }

    /**
     * Bridges legacy {@code isTrill()} flags to temporary {@link Trill} objects.
     * <p>
     * Finds consecutive trill-flagged notes that aren't already covered by a
     * {@link Trill} range element, creates temporary Trill objects, and stacks them.
     */
    private void bridgeLegacyTrillFlags(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        List<Trill> existingTrills,
        LayoutResult.Builder builder) {

        for (int i = 0; i < line.effectiveElementCount(); i++) {
            var element = line.getElement(i);

            if (!element.isTrill()) {
                continue;
            }

            // Skip if this is not the start of a trill sequence
            if (i > 0 && line.getElement(i - 1).isTrill()) {
                continue;
            }

            // Find the end of the consecutive trill sequence
            int trillEnd = i;

            while (trillEnd + 1 < line.effectiveElementCount()
                    && line.getElement(trillEnd + 1).isTrill()) {
                trillEnd++;
            }

            var endElement = line.getElement(trillEnd);

            // Check if already covered by an existing Trill range element
            if (StackingUtils.isRangeCovered(element, endElement, existingTrills)) {
                continue;
            }

            // Bridge: create temporary Trill and stack it
            var trill = new Trill(element, endElement);
            stackSingleTrill(trill, columnsByElement, builder);
        }
    }

}
