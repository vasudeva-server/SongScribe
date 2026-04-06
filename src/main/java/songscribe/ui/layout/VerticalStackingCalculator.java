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

package songscribe.ui.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.renderer.LineThickness;

/**
 * Calculates vertical positions for all elements above and below the staff.
 * <p>
 * Uses a three-layer {@link StaffExtents} model for collision detection:
 * <ol>
 *   <li><b>Note-attached layer</b> ({@code noteAttachedExtents}): articulations, fermata, trill</li>
 *   <li><b>Structural layer</b> ({@code structuralExtents}): dynamics hairpins, text dynamics, volta, tuplets</li>
 *   <li><b>System layer</b> ({@code systemExtents}): tempo, beat changes, annotations</li>
 * </ol>
 * <p>
 * Each layer starts by importing the previous layer's top extents, ensuring higher
 * layers clear all lower-layer elements. All calculations are in staff-space units.
 * Results are written directly to the {@link LayoutResult.Builder}.
 */
public class VerticalStackingCalculator {

    // Note head dimensions from SMuFL noteheadBlack bounding box (staff-space units)
    private static final double NOTE_HEAD_WIDTH_SS = SMuFLMetadata.getInstance().noteHeadWidthSs();
    private static final double NOTE_HEAD_HEIGHT_SS = SMuFLMetadata.getInstance().noteHeadHeightSs();

    private static final double NOTE_HEAD_RADIUS_SS = NOTE_HEAD_HEIGHT_SS / 2.0;

    // Staff position of the top staff line (F5); positions <= this are at or above the staff
    private static final int TOP_STAFF_LINE_POSITION = -4;

    // Y coordinate of the top staff line in the middleLineY=0 coordinate system
    private static final double STAFF_TOP_Y_SS =
        TOP_STAFF_LINE_POSITION * LayoutStylesheet.STAFF_POSITION_OFFSET_SS;

    // Minimum number of Bezier samples when seeding tie bounds into extents.
    // Ensures adequate curve resolution even for short ties.
    private static final int TIE_BOUND_MIN_SAMPLES = 8;

    // Horizontal collision margin for structural/system elements (collapses between adjacent elements)
    private static final double STRUCTURAL_HORIZONTAL_MARGIN_SS = 0.75; // 6px

    // Lyrics constants (to be measured from actual font in later phases)
    private static final double LYRICS_HEIGHT_SS = 2.5;       // ~20px
    private static final double INTER_LINE_MARGIN_SS = 1.25;  // ~10px

    /**
     * Notes that are part of an upward-arcing tie (stem down).
     * Populated by {@link #seedTieBounds} and read by stacking methods to adjust margins.
     */
    private Set<StaffElement> notesWithUpwardTie = Set.of();

    /**
     * Creates a new vertical stacking calculator.
     */
    public VerticalStackingCalculator() {
    }

    /**
     * Calculates vertical positions for all elements in the given columns.
     * <p>
     * This is the main entry point for vertical layout. It creates the three-layer
     * StaffExtents model, seeds note bounding areas, then processes each decoration
     * tier in order. Results are written directly to the builder.
     *
     * @param columns     List of element columns with X positions already set
     * @param line        The line being laid out
     * @param builder     The LayoutResult builder to write decoration positions into
     * @param lineWidthSs Total width of the staff line in staff-space units
     */
    public void calculate(
        List<ElementColumn> columns,
        Line line,
        LayoutResult.Builder builder,
        double lineWidthSs) {

        // Create three-layer StaffExtents model
        var noteAttachedExtents = new StaffExtents(lineWidthSs);
        var structuralExtents = new StaffExtents(lineWidthSs);
        var systemExtents = new StaffExtents(lineWidthSs);

        // Build element-to-column map for range element lookups
        var columnsByElement = buildColumnMap(columns);

        // Seed note bounding areas into noteAttachedExtents
        seedNoteBounds(columns, builder, noteAttachedExtents);

        // Seed upward-arcing tie bounds so decorations stack above ties
        seedTieBounds(line, columnsByElement, builder, noteAttachedExtents);

        // Tier 1: Near-note decorations (articulations)
        for (var column : columns) {
            stackArticulations(column, noteAttachedExtents, builder);
        }

        // Tier 2: Note decorations (fermata, trill)
        for (var column : columns) {
            stackFermata(column, noteAttachedExtents, builder);
        }

        stackTrills(line, columnsByElement, noteAttachedExtents, builder);

        // Initialize structural layer from note-attached layer
        structuralExtents.copyTopFrom(noteAttachedExtents);

        // Tier 3a: Tuplet brackets
        stackTuplets(line, columnsByElement, structuralExtents, builder);

        // Tier 3b: Hairpins (crescendo/diminuendo)
        stackHairpins(line, columnsByElement, structuralExtents, builder);

        // Tier 3c: Text dynamics (DynamicAttachment on notes)
        for (var column : columns) {
            stackTextDynamics(column, structuralExtents, builder);
        }

        // Tier 3d: Volta brackets (endings)
        stackEndings(line, columnsByElement, structuralExtents, builder);

        // Tier 4: System-level stacking (tempo, beat changes, annotations)
        // Initialize system layer from structural layer
        systemExtents.copyTopFrom(structuralExtents);

        for (var column : columns) {
            stackTempo(column, line, systemExtents, builder);
            stackBeatChange(column, line, systemExtents, builder);
            stackAnnotations(column, line, systemExtents, builder);
        }

        // Apply manual offsets post-layout (no collision re-run)
        applyManualOffsets(builder);

        // Calculate lyrics baseline and line height
        double lowestNoteBotSs = findLowestNoteBoundingSs(columns);
        double lyricsBaselineYSs = lowestNoteBotSs + LayoutStylesheet.LYRICS_BASELINE_OFFSET_SS;
        boolean hasLyrics = columns.stream().anyMatch(ElementColumn::hasSyllable);
        double lyricsHeightSs = hasLyrics ? LYRICS_HEIGHT_SS : 0.0;

        // Find the highest point reached across all layers (most negative Y)
        double maxAboveStaffSs = findMaxAboveStaffSs(systemExtents, lineWidthSs);

        double lineHeightSs = LayoutStylesheet.STAFF_HEIGHT_SS
            + Math.abs(maxAboveStaffSs)
            + lyricsHeightSs
            + INTER_LINE_MARGIN_SS;

        builder.setLineHeightSs(lineHeightSs);
        builder.setLyricBaselineYSs(hasLyrics ? lyricsBaselineYSs : 0);
    }

    /**
     * Seeds note bounding areas into the note-attached StaffExtents layer.
     * <p>
     * For each column, uses the StemLayout from the builder (computed during beam/stem pass)
     * to get accurate stem top/bottom positions, and uses the notehead width from SMuFL metadata.
     */
    private void seedNoteBounds(
        List<ElementColumn> columns,
        LayoutResult.Builder builder,
        StaffExtents noteAttachedExtents) {

        for (var column : columns) {
            var element = column.getElement();
            var stemLayout = builder.getStemLayout(element);

            double xSs = column.getXSs();

            double centerYSs = element.getStaffPosition()
                * LayoutStylesheet.STAFF_POSITION_OFFSET_SS;
            var type = element.getType();
            boolean upper = element.isUpper();

            double topSs;
            double botSs;

            double noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
            double noteheadBotSs = noteheadTopSs + type.getNoteheadHeightSs();

            if (stemLayout != null) {
                topSs = Math.min(stemLayout.topYSs(), noteheadTopSs);
                botSs = Math.max(stemLayout.bottomYSs(), noteheadBotSs);
            } else {
                topSs = Math.min(centerYSs + type.getTopYOffsetSs(upper), noteheadTopSs);
                botSs = Math.max(topSs + type.getElementHeightSs(upper), noteheadBotSs);
            }

            noteAttachedExtents.ySet(true, xSs, NOTE_HEAD_WIDTH_SS, topSs);
            noteAttachedExtents.ySet(false, xSs, NOTE_HEAD_WIDTH_SS, botSs);
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
     * Also populates {@link #notesWithUpwardTie} so stacking methods can use a reduced
     * margin ({@link LayoutStylesheet#TIE_DECORATION_MARGIN_SS}) for single-note decorations.
     * A note is only added to the set when the tie endpoint at that note's position is above
     * (more negative Y than) the anchored ceiling, meaning the tie is the actual constraint
     * that pushes decorations higher. When the tie stays within the staff, the normal margin
     * applies.
     */
    private void seedTieBounds(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        LayoutResult.Builder builder,
        StaffExtents noteAttachedExtents) {

        var ties = line.getTies();

        if (ties.isEmpty()) {
            return;
        }

        var upwardTieNotes = new HashSet<StaffElement>();

        for (var it = ties.listIterator(); it.hasNext(); ) {
            var interval = it.next();
            var startElement = line.getElement(interval.getStart());

            // Only seed upward-arcing ties (stem down → !isUpper())
            if (startElement.isUpper()) {
                continue;
            }

            var tieLayout = builder.getTieLayout(interval);

            if (tieLayout == null) {
                continue;
            }

            var endElement = line.getElement(interval.getEnd());

            // Sample the outer Bezier curve to reserve tie vertical extent
            double sx = tieLayout.startXSs();
            double ex = tieLayout.endXSs();
            double spanWidthSs = ex - sx;

            if (spanWidthSs <= 0) {
                continue;
            }

            // Seed tie bounds at the start and end noteheads using the Bezier Y at the
            // far edge of each notehead (where the tie has curved upward), not at the
            // attachment point (where the tie just touches the notehead).
            var startColumn = columnsByElement.get(startElement);
            var endColumn = columnsByElement.get(endElement);

            double startEdgeT = Math.min(NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            double endEdgeT = Math.max(1.0 - NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            double startEdgeYSs = evaluateBezierYSs(startEdgeT, tieLayout);
            double endEdgeYSs = evaluateBezierYSs(endEdgeT, tieLayout);

            // Only use reduced margin for notes where the tie protrudes above the anchor ceiling.
            // Use the notehead-edge Y (not the raw endpoint) since that reflects the visible arc.
            if (startEdgeYSs < anchorCeilingSs(startElement)) {
                upwardTieNotes.add(startElement);
            }

            if (endEdgeYSs < anchorCeilingSs(endElement)) {
                upwardTieNotes.add(endElement);
            }

            if (startColumn != null) {
                noteAttachedExtents.ySet(true, startColumn.getXSs(),
                    NOTE_HEAD_WIDTH_SS, startEdgeYSs);
            }

            if (endColumn != null) {
                noteAttachedExtents.ySet(true, endColumn.getXSs(),
                    NOTE_HEAD_WIDTH_SS, endEdgeYSs);
            }

            int sampleCount = Math.max(TIE_BOUND_MIN_SAMPLES, (int) Math.ceil(spanWidthSs));
            double segmentWidthSs = spanWidthSs / sampleCount;

            for (int i = 0; i < sampleCount; i++) {
                double tMid = (i + 0.5) / sampleCount;
                double ySs = evaluateBezierYSs(tMid, tieLayout);
                double segmentXSs = sx + i * segmentWidthSs;
                noteAttachedExtents.ySet(true, segmentXSs, segmentWidthSs, ySs);
            }
        }

        notesWithUpwardTie = upwardTieNotes.isEmpty() ? Set.of() : upwardTieNotes;
    }

    /**
     * Returns the anchor ceiling Y for a note, without consulting extents.
     * <p>
     * Notes within or below the staff anchor at the top staff line.
     * Notes at or above the top staff line anchor above the notehead.
     */
    private static double anchorCeilingSs(StaffElement note) {
        return anchorCeilingSs(note.getStaffPosition());
    }

    /**
     * Returns the anchor ceiling Y for the given staff position.
     */
    private static double anchorCeilingSs(int staffPosition) {
        if (staffPosition > TOP_STAFF_LINE_POSITION) {
            return STAFF_TOP_Y_SS;
        }

        double noteHeadYSs = staffPosition * LayoutStylesheet.STAFF_POSITION_OFFSET_SS;
        return noteHeadYSs - NOTE_HEAD_RADIUS_SS;
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
        StaffExtents noteAttachedExtents,
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
        double marginSs = notesWithUpwardTie.contains(note)
            ? LayoutStylesheet.TIE_DECORATION_MARGIN_SS
            : LayoutStylesheet.NOTE_DECORATION_MARGIN_SS;

        // Position staccato first (closest to notehead), then accent beyond
        if (staccatoArticulation != null) {
            stackSingleArticulation(staccatoArticulation, noteAttachedExtents,
                xSs, marginSs, staffPosition, builder);
        }

        if (accentArticulation != null) {
            stackSingleArticulation(accentArticulation, noteAttachedExtents,
                xSs, marginSs, staffPosition, builder);
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
        double lineWidthSs = xSs + NOTE_HEAD_WIDTH_SS + 1.0;
        var extents = new StaffExtents(lineWidthSs);

        // Seed note bounds — same logic as seedNoteBounds for the non-beamed path
        double centerYSs = note.getStaffPosition() * LayoutStylesheet.STAFF_POSITION_OFFSET_SS;
        var type = note.getType();
        boolean upper = note.isUpper();
        double noteheadTopSs = centerYSs + type.getNoteheadTopOffsetSs();
        double noteheadBotSs = noteheadTopSs + type.getNoteheadHeightSs();
        double topSs = Math.min(centerYSs + type.getTopYOffsetSs(upper), noteheadTopSs);
        double botSs = Math.max(topSs + type.getElementHeightSs(upper), noteheadBotSs);
        extents.ySet(true, xSs, NOTE_HEAD_WIDTH_SS, topSs);
        extents.ySet(false, xSs, NOTE_HEAD_WIDTH_SS, botSs);

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

        if (staccatoArticulation != null) {
            stackSingleArticulation(staccatoArticulation, extents,
                xSs, LayoutStylesheet.NOTE_DECORATION_MARGIN_SS,
                staffPosition, builder);
        }

        if (accentArticulation != null) {
            stackSingleArticulation(accentArticulation, extents,
                xSs, LayoutStylesheet.NOTE_DECORATION_MARGIN_SS,
                staffPosition, builder);
        }

        // Tier 2: Fermata
        if (note.isFermata()) {
            var fermata = new FermataAttachment(note);
            stackAbove(extents, fermata, xSs,
                fermata.getContentWidthSs(), fermata.getContentHeightSs(),
                LayoutStylesheet.NOTE_DECORATION_MARGIN_SS, staffPosition, builder);
        }

        return builder.build();
    }

    /**
     * Stacks all trills for the line.
     * <p>
     * Processes {@link Trill} objects from {@code line.findRangeElements(Trill.class)},
     * then bridges any legacy {@code note.isTrill()} flags not already covered.
     * Multi-note trills reserve the full horizontal span so subsequent layers clear them.
     */
    private void stackTrills(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        StaffExtents noteAttachedExtents,
        LayoutResult.Builder builder) {

        // Process new Trill range elements
        var trills = line.findRangeElements(Trill.class);

        for (var trill : trills) {
            stackSingleTrill(trill, columnsByElement, noteAttachedExtents, builder);
        }

        // Bridge legacy isTrill() flags not covered by Trill range elements
        bridgeLegacyTrillFlags(line, columnsByElement, trills, noteAttachedExtents, builder);
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
        StaffExtents noteAttachedExtents,
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
            trill.getContentHeightSs(), LayoutStylesheet.NOTE_DECORATION_MARGIN_SS,
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
        StaffExtents noteAttachedExtents,
        LayoutResult.Builder builder) {

        for (int i = 0; i < line.elementCount(); i++) {
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

            while (trillEnd + 1 < line.elementCount()
                    && line.getElement(trillEnd + 1).isTrill()) {
                trillEnd++;
            }

            var endElement = line.getElement(trillEnd);

            // Check if already covered by an existing Trill range element
            if (isRangeCovered(element, endElement, existingTrills)) {
                continue;
            }

            // Bridge: create temporary Trill and stack it
            var trill = new Trill(element, endElement);
            stackSingleTrill(trill, columnsByElement, noteAttachedExtents, builder);
        }
    }

    /**
     * Checks whether a range interval is already covered by an existing range element.
     */
    private static boolean isRangeCovered(
        StaffElement startNote,
        StaffElement endNote,
        List<? extends RangeElement> existingElements) {

        for (var element : existingElements) {
            if (element.getAnchorElement() == startNote && element.getEndElement() == endNote) {
                return true;
            }
        }

        return false;
    }

    /**
     * Stacks fermata for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.isFermata()} flag and bridges it to a temporary {@link FermataAttachment}
     * so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackFermata(
        ElementColumn column,
        StaffExtents noteAttachedExtents,
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
            LayoutStylesheet.NOTE_DECORATION_MARGIN_SS, staffPosition, builder);
    }

    /**
     * Stacks all hairpins (crescendo/diminuendo) for the line.
     * <p>
     * Processes {@link Crescendo} and {@link Diminuendo} range elements first,
     * then bridges legacy {@link songscribe.music.DynamicsInterval} data not
     * already covered by range elements.
     */
    private void stackHairpins(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        StaffExtents structuralExtents,
        LayoutResult.Builder builder) {

        // Process new Crescendo range elements
        for (var crescendo : line.findRangeElements(Crescendo.class)) {
            stackSpanElement(crescendo, LayoutStylesheet.HAIRPIN_MARGIN_SS,
                columnsByElement, structuralExtents, builder);
        }

        // Process new Diminuendo range elements
        for (var diminuendo : line.findRangeElements(Diminuendo.class)) {
            stackSpanElement(diminuendo, LayoutStylesheet.HAIRPIN_MARGIN_SS,
                columnsByElement, structuralExtents, builder);
        }

        // Bridge legacy crescendo intervals
        bridgeLegacyHairpinIntervals(line, line.getCrescendos(), true,
            columnsByElement, structuralExtents, builder);

        // Bridge legacy diminuendo intervals
        bridgeLegacyHairpinIntervals(line, line.getDiminuendos(), false,
            columnsByElement, structuralExtents, builder);
    }

    /**
     * Bridges legacy {@link songscribe.music.DynamicsInterval} data to temporary
     * hairpin range elements and stacks them.
     */
    private void bridgeLegacyHairpinIntervals(
        Line line,
        songscribe.music.IntervalSet<songscribe.music.DynamicsInterval> intervals,
        boolean isCrescendo,
        Map<StaffElement, ElementColumn> columnsByElement,
        StaffExtents structuralExtents,
        LayoutResult.Builder builder) {

        Class<? extends RangeElement> rangeClass =
            isCrescendo ? Crescendo.class : Diminuendo.class;
        var existingRangeElements = line.findRangeElements(rangeClass);

        for (var iter = intervals.listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getElement(interval.getStart());
            var endNote = line.getElement(interval.getEnd());

            // Check if already covered by a range element
            if (isRangeCovered(startNote, endNote, existingRangeElements)) {
                continue;
            }

            var startColumn = columnsByElement.get(startNote);
            var endColumn = columnsByElement.get(endNote);

            if (startColumn == null || endColumn == null) {
                continue;
            }

            // Bridge to temporary range element for dimension calculations
            double anchorXSs = startColumn.getXSs();
            double endXSs = endColumn.getXSs();

            RangeElement bridged;

            if (isCrescendo) {
                bridged = new Crescendo(startNote, endNote);
            } else {
                bridged = new Diminuendo(startNote, endNote);
            }

            int staffPosition = startNote.getStaffPosition();
            double widthSs = endXSs - anchorXSs + NOTE_HEAD_WIDTH_SS;
            double ySs = stackAbove(structuralExtents, bridged, anchorXSs, widthSs,
                LayoutStylesheet.HAIRPIN_OPENING_HEIGHT_SS, LayoutStylesheet.HAIRPIN_MARGIN_SS,
                staffPosition, builder);

            // Write SpanLayout keyed by the legacy interval for renderer access
            builder.putSpanLayout(interval,
                new LayoutResult.SpanLayout(anchorXSs, endXSs,
                    ySs, LayoutStylesheet.HAIRPIN_OPENING_HEIGHT_SS));
        }
    }

    /**
     * Stacks text dynamics (DynamicAttachment) for the given column.
     * <p>
     * Positions text dynamics (pp, p, mp, mf, f, ff, sfz, fp) in the structural tier
     * using collision detection against previously placed elements.
     */
    private void stackTextDynamics(
        ElementColumn column,
        StaffExtents structuralExtents,
        LayoutResult.Builder builder) {

        var note = column.getElement();
        var dynamic = note.findAttachment(DynamicAttachment.class);

        if (dynamic == null) {
            return;
        }

        double columnXSs = column.getXSs();
        double contentWidthSs = dynamic.getContentWidthSs();
        double centeredXSs = columnXSs + note.getType().getCenterXSs() - contentWidthSs / 2.0;
        int staffPosition = note.getStaffPosition();
        stackAbove(structuralExtents, dynamic, centeredXSs,
            contentWidthSs, dynamic.getContentHeightSs(),
            LayoutStylesheet.NOTE_DECORATION_MARGIN_SS,
            staffPosition, builder);
    }

    /**
     * Stacks all endings (volta brackets) for the line.
     * <p>
     * Each ending computes its bracket ranges (first and second brackets split
     * at the REPEAT_RIGHT barline), then collision regions are created for each
     * bracket and stacked together as one element.
     */
    private void stackEndings(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        StaffExtents structuralExtents,
        LayoutResult.Builder builder) {

        var lt = LineThickness.getInstance();

        for (var ending : line.findRangeElements(Ending.class)) {
            var anchor = ending.getAnchorElement();

            if (anchor == null || ending.getEndElement() == null) {
                continue;
            }

            // Compute bracket ranges (stored on the Ending for renderer use)
            var brackets = ending.computeBracketRanges(
                line,
                e -> {
                    var col = columnsByElement.get(e);

                    if (col == null) {
                        throw new IllegalStateException(
                            "No column for element");
                    }

                    return col.getXSs();
                },
                lt);

            if (brackets.isEmpty()) {
                continue;
            }

            // Element anchor X = first bracket's left edge
            double anchorXSs = brackets.getFirst().x1Ss();

            // Combine collision regions from all brackets
            var allRegions = new ArrayList<CollisionRegion>();

            for (var bracket : brackets) {
                double xBaseSs = bracket.x1Ss() - anchorXSs;
                allRegions.addAll(
                    ending.computeCollisionRegions(bracket, xBaseSs));
            }

            // Overall width = from first bracket start to last bracket end
            double widthSs = brackets.getLast().x2Ss() - anchorXSs;

            int staffPosition = anchor.getStaffPosition();

            stackAboveWithRegions(structuralExtents, ending, allRegions,
                anchorXSs, widthSs,
                LayoutStylesheet.ENDING_MARGIN_SS,
                staffPosition, builder);
        }
    }

    /**
     * Stacks all tuplet brackets for the line.
     * <p>
     * Processes {@link Tuplet} range elements first, then bridges legacy
     * {@link songscribe.music.TupletInterval} data not already covered
     * by range elements.
     */
    private void stackTuplets(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        StaffExtents structuralExtents,
        LayoutResult.Builder builder) {

        // Process new Tuplet range elements
        for (var tuplet : line.findRangeElements(Tuplet.class)) {
            stackSpanElement(tuplet, LayoutStylesheet.TUPLET_MARGIN_SS,
                columnsByElement, structuralExtents, builder);
        }

        // Bridge legacy tuplet intervals
        bridgeLegacyTupletIntervals(line, columnsByElement, structuralExtents, builder);
    }

    /**
     * Bridges legacy {@link songscribe.music.TupletInterval} data to temporary
     * {@link Tuplet} range elements and stacks them.
     */
    private void bridgeLegacyTupletIntervals(
        Line line,
        Map<StaffElement, ElementColumn> columnsByElement,
        StaffExtents structuralExtents,
        LayoutResult.Builder builder) {

        var existingTuplets = line.findRangeElements(Tuplet.class);

        for (var iter = line.getTuplets().listIterator(); iter.hasNext(); ) {
            var interval = iter.next();
            var startNote = line.getElement(interval.getStart());
            var endNote = line.getElement(interval.getEnd());

            // Check if already covered by a range element
            if (isRangeCovered(startNote, endNote, existingTuplets)) {
                continue;
            }

            var startColumn = columnsByElement.get(startNote);
            var endColumn = columnsByElement.get(endNote);

            if (startColumn == null || endColumn == null) {
                continue;
            }

            // Anchor at the right edge of each notehead
            double anchorXSs = startColumn.getXSs() + NOTE_HEAD_WIDTH_SS;
            double endXSs = endColumn.getXSs() + NOTE_HEAD_WIDTH_SS;
            var bridged = new Tuplet(startNote, endNote, interval.getGrade());

            int staffPosition = startNote.getStaffPosition();
            double widthSs = bridged.getSpanWidthSs(anchorXSs, endXSs);
            double contentHeightSs = bridged.getContentHeightSs();

            double ySs = stackAbove(structuralExtents, bridged, anchorXSs, widthSs,
                contentHeightSs, LayoutStylesheet.TUPLET_MARGIN_SS,
                staffPosition, builder);

            // Write SpanLayout keyed by the legacy interval for renderer access
            builder.putSpanLayout(interval,
                new LayoutResult.SpanLayout(anchorXSs, endXSs, ySs, contentHeightSs));
        }
    }

    /**
     * Stacks tempo marking for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.getTempoChange()} property and bridges it to a temporary
     * {@link TempoAttachment} so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackTempo(
        ElementColumn column,
        Line line,
        StaffExtents systemExtents,
        LayoutResult.Builder builder) {

        var note = column.getElement();

        // Check new attachment hierarchy first
        var tempo = note.findAttachment(TempoAttachment.class);

        // Bridge legacy flag to a temporary TempoAttachment
        if (tempo == null && note.getTempoChange() != null) {
            tempo = new TempoAttachment(note, note.getTempoChange());
        }

        if (tempo == null) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();
        var attrFontMetrics = line.getComposition().getAttributionFontMetrics();
        var metrics = tempo.computeContentMetrics(attrFontMetrics);

        stackAboveWithRegions(systemExtents, tempo, metrics.regions(), xSs,
            metrics.widthSs(), LayoutStylesheet.TEMPO_MARGIN_SS,
            staffPosition, builder);
    }

    /**
     * Stacks beat change for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.getBeatChange()} property and bridges it to a temporary
     * {@link BeatChangeAttachment} so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackBeatChange(
        ElementColumn column,
        Line line,
        StaffExtents systemExtents,
        LayoutResult.Builder builder) {

        var note = column.getElement();

        // Check new attachment hierarchy first
        var beatChange = note.findAttachment(BeatChangeAttachment.class);

        // Bridge legacy flag to a temporary BeatChangeAttachment
        if (beatChange == null && note.getBeatChange() != null) {
            beatChange = new BeatChangeAttachment(note, note.getBeatChange());
        }

        if (beatChange == null) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();
        double widthSs = beatChange.computeContentWidthSs(
            line.getComposition().getAttributionFontMetrics());
        stackAbove(systemExtents, beatChange, xSs,
            widthSs, beatChange.getContentHeightSs(),
            LayoutStylesheet.TEMPO_MARGIN_SS,
            staffPosition, builder);
    }

    /**
     * Stacks annotation for the given column.
     * <p>
     * Checks the new attachment hierarchy first; falls back to the legacy
     * {@code note.getAnnotation()} property and bridges it to a temporary
     * {@link AnnotationAttachment} so both paths write a {@link LayoutResult.DecorationLayout}.
     */
    private void stackAnnotations(
        ElementColumn column,
        Line line,
        StaffExtents systemExtents,
        LayoutResult.Builder builder) {

        var note = column.getElement();

        // Check new attachment hierarchy first
        var annotation = note.findAttachment(AnnotationAttachment.class);

        // Bridge legacy flag to a temporary AnnotationAttachment
        if (annotation == null && note.getAnnotation() != null) {
            annotation = new AnnotationAttachment(note, note.getAnnotation());
        }

        if (annotation == null) {
            return;
        }

        double xSs = column.getXSs();
        int staffPosition = note.getStaffPosition();
        double widthSs = annotation.computeContentWidthSs(
            line.getComposition().getAnnotationFontMetrics());
        stackAbove(systemExtents, annotation, xSs,
            widthSs, annotation.getContentHeightSs(),
            LayoutStylesheet.ANNOTATION_ABOVE_MARGIN_SS,
            staffPosition, builder);
    }


    // ---- Shared stacking helpers ----

    /**
     * Places an element above the staff using anchored ceiling collision detection.
     * <p>
     * Uses the anchored ceiling (top staff line or notehead) as the reference point,
     * combined with existing extents reservations, to determine the highest clear Y.
     * Updates the extents and writes a {@link LayoutResult.DecorationLayout}.
     *
     * @return the computed top Y in staff-space units
     */
    private static double stackAbove(
        StaffExtents extents,
        LineElement element,
        double xSs, double widthSs, double heightSs, double marginSs,
        int staffPosition,
        LayoutResult.Builder builder) {

        // Query: expand by horizontal margin (collapses between adjacent elements)
        var queryXSs = xSs - STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var queryWidthSs = widthSs + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS;
        var currentTopSs = extents.yGet(true, queryXSs, queryWidthSs);
        var anchorSs = anchorCeilingSs(staffPosition);
        var ceilingSs = Math.min(currentTopSs, anchorSs);

        // Position: bottom margin between this element's bottom and the ceiling
        var elementYSs = ceilingSs - marginSs - heightSs;

        // Reserve at element top. Upper tiers apply their own bottom margin
        // when they query, so each tier-to-tier gap = the upper element's margin.
        extents.ySet(true, xSs, widthSs, elementYSs);

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(xSs, elementYSs, widthSs, heightSs, marginSs));

        return elementYSs;
    }

    /**
     * Places a composite element above the staff using sub-region collision detection.
     * <p>
     * Each sub-region independently queries the existing extents at its horizontal
     * range to find its own ceiling. The element is placed at the highest (furthest
     * from staff) position needed across all sub-regions. Each sub-region is then
     * reserved at its own visual bottom, allowing later elements to nestle into
     * the gaps between shorter and taller sub-regions.
     *
     * @return the computed top Y in staff-space units
     */
    private static double stackAboveWithRegions(
        StaffExtents extents,
        LineElement element,
        List<CollisionRegion> regions,
        double xSs, double widthSs, double marginSs,
        int staffPosition,
        LayoutResult.Builder builder
    ) {
        var anchorSs = anchorCeilingSs(staffPosition);
        double elementYSs = Double.MAX_VALUE;

        // Query phase: each sub-region finds its own ceiling independently.
        // The element Y is the min (highest on page) across all sub-regions,
        // so the element clears all content beneath every sub-region.
        for (int i = 0; i < regions.size(); i++) {
            var region = regions.get(i);
            double regionXSs = xSs + region.xOffsetSs();
            double queryXSs = regionXSs - STRUCTURAL_HORIZONTAL_MARGIN_SS;
            double queryWidthSs = region.widthSs() + 2 * STRUCTURAL_HORIZONTAL_MARGIN_SS;

            double regionTopSs = extents.yGet(true, queryXSs, queryWidthSs);
            double regionCeilingSs = Math.min(regionTopSs, anchorSs);

            // Constraint: elementY + yOffset + height <= ceiling - margin
            double regionYSs = regionCeilingSs - marginSs
                - region.yOffsetSs() - region.heightSs();

            elementYSs = Math.min(elementYSs, regionYSs);
        }

        // Set phase: reserve each sub-region at its visual top.
        // Shorter sub-regions (e.g. text) have a higher yOffset → shallower reservation,
        // enabling later elements to nestle closer where only the short sub-region exists.
        for (var region : regions) {
            double regionXSs = xSs + region.xOffsetSs();
            double regionTopSs = elementYSs + region.yOffsetSs();
            extents.ySet(true, regionXSs, region.widthSs(), regionTopSs);
        }

        // Overall height is the max extent across all sub-regions
        double overallHeightSs = 0;

        for (var region : regions) {
            overallHeightSs = Math.max(
                overallHeightSs, region.yOffsetSs() + region.heightSs());
        }

        builder.putDecorationLayout(element,
            new LayoutResult.DecorationLayout(
                xSs, elementYSs, widthSs, overallHeightSs, marginSs, regions));

        return elementYSs;
    }

    /**
     * Stacks a span element (hairpin, tuplet) that requires both anchor and end notes.
     * <p>
     * Resolves anchor/end columns, computes span width via the range element,
     * and delegates to {@link #stackAbove}.
     */
    private void stackSpanElement(
        RangeElement element,
        double marginSs,
        Map<StaffElement, ElementColumn> columnsByElement,
        StaffExtents extents,
        LayoutResult.Builder builder) {

        var anchor = element.getAnchorElement();
        var endNote = element.getEndElement();

        if (anchor == null || endNote == null) {
            return;
        }

        var anchorColumn = columnsByElement.get(anchor);
        var endColumn = columnsByElement.get(endNote);

        if (anchorColumn == null || endColumn == null) {
            return;
        }

        int staffPosition = anchor.getStaffPosition();
        double anchorXSs = anchorColumn.getXSs();
        double endXSs = endColumn.getXSs();
        double widthSs = element.getSpanWidthSs(anchorXSs, endXSs);

        stackAbove(extents, element, anchorXSs, widthSs,
            element.getContentHeightSs(), marginSs,
            staffPosition, builder);
    }


    // ---- Manual offset application ----

    /**
     * Applies manual user offsets to all decoration and span layouts post-layout.
     * <p>
     * Per spec, offsets are applied after all collision detection is complete.
     * The user takes responsibility for any resulting overlaps — no collision
     * re-run occurs.
     * <p>
     * Offset sources:
     * <ul>
     *   <li>{@link LineElement#getUserXOffsetSs()} / {@link LineElement#getUserYOffsetSs()}:
     *       base offsets for all decoration elements</li>
     *   <li>{@link Trill#getYPositionSs()}: additional Y offset for trills</li>
     *   <li>{@link Ending#getYPositionSs()}: additional Y offset for endings</li>
     *   <li>{@link Crescendo#getYShift()} etc.: pixel-based hairpin shifts (converted to ss)</li>
     *   <li>{@link songscribe.music.Annotation#getUserYOffsetSs()}: legacy annotation Y offset</li>
     *   <li>{@link songscribe.music.DynamicsInterval}: legacy hairpin shifts (already in ss)</li>
     * </ul>
     */
    private void applyManualOffsets(LayoutResult.Builder builder) {
        applyDecorationOffsets(builder);
        applySpanOffsets(builder);
    }

    /**
     * Applies manual offsets to all {@link LayoutResult.DecorationLayout} entries.
     */
    private void applyDecorationOffsets(LayoutResult.Builder builder) {
        // Collect entries to avoid ConcurrentModificationException during iteration
        var entries = List.copyOf(builder.getDecorationLayoutEntries());

        for (var entry : entries) {
            var element = entry.getKey();
            var layout = entry.getValue();

            double xOffsetSs = element.getUserXOffsetSs();
            double yOffsetSs = element.getUserYOffsetSs();
            double widthAdjustSs = 0;

            // Element-specific additional offsets
            if (element instanceof Trill trill) {
                yOffsetSs += trill.getYPositionSs();
            } else if (element instanceof Ending ending) {
                yOffsetSs += ending.getYPositionSs();
            } else if (element instanceof Crescendo cresc) {
                double x1ShiftSs = ScaleContext.getInstance().fromPixels(cresc.getX1Shift());
                double x2ShiftSs = ScaleContext.getInstance().fromPixels(cresc.getX2Shift());
                xOffsetSs += x1ShiftSs;
                widthAdjustSs = x2ShiftSs - x1ShiftSs;
                yOffsetSs += ScaleContext.getInstance().fromPixels(cresc.getYShift());
            } else if (element instanceof Diminuendo dim) {
                double x1ShiftSs = ScaleContext.getInstance().fromPixels(dim.getX1Shift());
                double x2ShiftSs = ScaleContext.getInstance().fromPixels(dim.getX2Shift());
                xOffsetSs += x1ShiftSs;
                widthAdjustSs = x2ShiftSs - x1ShiftSs;
                yOffsetSs += ScaleContext.getInstance().fromPixels(dim.getYShift());
            } else if (element instanceof AnnotationAttachment annAttach) {
                yOffsetSs += annAttach.getAnnotation().getUserYOffsetSs();
            }

            if (xOffsetSs != 0 || yOffsetSs != 0 || widthAdjustSs != 0) {
                builder.putDecorationLayout(element, new LayoutResult.DecorationLayout(
                    layout.xSs() + xOffsetSs,
                    layout.ySs() + yOffsetSs,
                    layout.widthSs() + widthAdjustSs,
                    layout.heightSs(),
                    layout.marginSs(),
                    layout.regions()));
            }
        }
    }

    /**
     * Applies manual offsets to all {@link LayoutResult.SpanLayout} entries.
     * <p>
     * Handles {@link songscribe.music.DynamicsInterval} shifts and
     * {@link songscribe.music.TupletInterval} vertical position adjustments.
     */
    private void applySpanOffsets(LayoutResult.Builder builder) {
        var entries = List.copyOf(builder.getSpanLayoutEntries());

        for (var entry : entries) {
            var interval = entry.getKey();
            var layout = entry.getValue();

            if (interval instanceof songscribe.music.DynamicsInterval dynInterval) {
                double x1ShiftSs = dynInterval.getX1ShiftSs();
                double x2ShiftSs = dynInterval.getX2ShiftSs();
                double yShiftSs = dynInterval.getYShiftSs();

                if (x1ShiftSs != 0 || x2ShiftSs != 0 || yShiftSs != 0) {
                    builder.putSpanLayout(interval, new LayoutResult.SpanLayout(
                        layout.startXSs() + x1ShiftSs,
                        layout.endXSs() + x2ShiftSs,
                        layout.ySs() + yShiftSs,
                        layout.heightSs()));
                }
            } else if (interval instanceof songscribe.music.TupletInterval tupletInterval) {
                double yShiftSs = tupletInterval.getVerticalPositionSs();

                if (yShiftSs != 0) {
                    builder.putSpanLayout(interval, new LayoutResult.SpanLayout(
                        layout.startXSs(),
                        layout.endXSs(),
                        layout.ySs() + yShiftSs,
                        layout.heightSs()));
                }
            }
        }
    }


    // ---- Utility methods ----

    /**
     * Builds a map from StaffElement to its ElementColumn for range element lookups.
     */
    private Map<StaffElement, ElementColumn> buildColumnMap(List<ElementColumn> columns) {
        var map = new HashMap<StaffElement, ElementColumn>(columns.size());

        for (var column : columns) {
            map.put(column.getElement(), column);
        }

        return map;
    }

    /**
     * Finds the lowest Y position of any note bounding area across all columns (ss).
     * Used to calculate the lyrics baseline position.
     */
    private double findLowestNoteBoundingSs(List<ElementColumn> columns) {
        double lowestSs = LayoutStylesheet.STAFF_HEIGHT_SS;

        for (var column : columns) {
            double elementYSs = column.getElement().getStaffPosition() * 0.5;
            double botSs = elementYSs + NOTE_HEAD_HEIGHT_SS / 2.0;

            if (botSs > lowestSs) {
                lowestSs = botSs;
            }
        }

        return lowestSs;
    }

    /**
     * Finds the maximum above-staff extent from the system layer (most negative Y).
     * Returns 0.0 if nothing extends above the staff top.
     */
    private double findMaxAboveStaffSs(StaffExtents systemExtents, double lineWidthSs) {
        // Query the entire line width for the highest point
        return systemExtents.yGet(true, 0, lineWidthSs);
    }
}
