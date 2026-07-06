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
import java.util.List;
import java.util.Map;
import java.util.Set;

import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Direction;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.layout.ElementColumn;
import songscribe.dom.FermataAttachment;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.engraving.SMuFLConstants;
import songscribe.layout.StaffExtents;
import songscribe.engraving.Staff;
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
     * <p>
     * Populates {@link StackingContext#setNotesWithUpwardTie(Set)} during tie seeding
     * so downstream stackers can use reduced margins for tie-affected notes.
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

        // Seed upward-arcing tie bounds so the accent stacks above ties
        context.setNotesWithUpwardTie(seedTieBounds());

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

        // No tie in the preview: place staccato, then the accent above it back-to-back —
        // equivalent to the full pipeline with an empty tie seed between the two passes.
        stackStaccatoOnly(staccatoArticulation, extents, xSs, ARTICULATION_MARGIN_SS,
            staffPosition, direction, builder);
        stackAccentAboveExtents(accentArticulation, staccatoArticulation != null, extents,
            xSs, ARTICULATION_MARGIN_SS, staffPosition, direction, builder);

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

            var startEdgeT = Math.min(SMuFLConstants.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
            var endEdgeT = Math.max(1.0 - SMuFLConstants.NOTE_HEAD_WIDTH_SS / spanWidthSs, 0.5);
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
     * Runs after the tie is seeded, so it stacks above whatever is highest in the extents — the
     * tie when present, else the staccato, else the notehead (LilyPond {@code avoid-slur around}).
     * When paired with staccato it stacks beyond it using {@link #ACCENT_STACCATO_GAP_SS} as
     * their gap; otherwise it anchors at the nearer staff line (or the notehead beyond it).
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
        var staccatoPresent = findStaccato(note) != null;
        var marginSs = accentAnchorMarginSs(note, direction);

        var edgeYSs = stackAccentAboveExtents(accent, staccatoPresent, noteAttachedExtents,
            column.getXSs(), marginSs, note.getStaffPosition(), direction, builder);

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
        double xSs, double marginSs, int staffPosition,
        Direction direction,
        LayoutResult.Builder builder) {

        if (staccato == null) {
            return null;
        }

        return stackStaccato(direction, extents, staccato, xSs,
            staccato.getContentWidthSs(), staccato.getContentHeightSs(), marginSs,
            staffPosition, builder);
    }

    /**
     * Places the accent (if present) above the current extents.
     * <p>
     * When a staccato is present the accent stacks beyond it — and beyond any already-seeded tie
     * — via {@link StackingUtils#stackBeyond}, using {@link #ACCENT_STACCATO_GAP_SS} as their gap
     * but never closer than {@link #ARTICULATION_MARGIN_SS} to the staff edge, since staccato's
     * note-relative position can sit closer to the staff than accent's minimum clearance
     * requires. Otherwise the accent anchors at the nearer staff line (or notehead beyond it)
     * with {@code marginSs}, which still clears any seeded tie via the extents. Shared by the
     * full pipeline's accent pass and the no-tie preview.
     *
     * @return the accent's top Y (above) or bottom Y (below) in staff-space units, or
     *     {@code null} when no accent is present
     */
    private static @Nullable Double stackAccentAboveExtents(
        @Nullable Articulation accent,
        boolean staccatoPresent,
        StaffExtents extents,
        double xSs, double marginSs, int staffPosition,
        Direction direction,
        LayoutResult.Builder builder) {

        if (accent == null) {
            return null;
        }

        var widthSs = accent.getContentWidthSs();
        var heightSs = accent.getContentHeightSs();

        if (staccatoPresent) {
            return stackBeyond(direction, extents, accent, xSs,
                widthSs, heightSs, ACCENT_STACCATO_GAP_SS, ARTICULATION_MARGIN_SS, builder);
        }

        return stackAtAnchor(direction, extents, accent, xSs,
            widthSs, heightSs, marginSs, staffPosition, builder);
    }

    /**
     * Margin used to anchor an accent that stacks alone (no staccato below it). Down-stem
     * articulations stack above the staff, where an upward-arcing tie may intrude; use the
     * reduced tie margin when this note's tie is the active constraint. Up-stem articulations
     * stack below the staff, which upward ties never reach, so they use the standard margin.
     */
    private double accentAnchorMarginSs(StaffElement note, Direction direction) {
        if (direction.isUp() && context.getNotesWithUpwardTie().contains(note)) {
            return TIE_DECORATION_MARGIN_SS;
        }

        return ARTICULATION_MARGIN_SS;
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
