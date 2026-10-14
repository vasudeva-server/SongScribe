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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Lyric;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.layout.HorizontalSpacingCalculator.GraceSyllable;
import songscribe.layout.HorizontalSpacingCalculator.PairMelisma;

/**
 * Computes lyric box and connector geometry from per-element {@link Lyric} records.
 * <p>
 * Span rules (authoritative):
 * <ul>
 *   <li>note-with-Lyric(relation = SYLLABLE/COMPOUND_WORD) → HYPHEN span to next syllable</li>
 *   <li>note-with-Lyric(extend = START) → EXTENDER span from end of syllable onward</li>
 *   <li>note-with-Lyric(extend = STOP) → active EXTENDER ends at
 *       {@code max(syllable end + }{@value MIN_MELISMA_LENGTH_SS}{@code , note's notehead right edge)},
 *       matching LilyPond's {@code LyricExtender} minimum-length rule; emits no lyric box (the
 *       lyric is a melisma terminator)</li>
 *   <li>note-with-Lyric(extend = CONTINUE) → continues current EXTENDER silently (cross-line
 *       carrier); emits no lyric box</li>
 *   <li>note-with-no-Lyric + active EXTENDER → continues current EXTENDER</li>
 *   <li>note-with-no-Lyric → a placeholder box ({@link Result#placeholders()}), placed as a
 *       syllable would be, that takes no space and neither opens nor closes a span</li>
 *   <li>rest + active EXTENDER → continues EXTENDER through the rest, which bears a chain but
 *       never a syllable ({@link songscribe.dom.ElementType#bearsSyllableText()}), exactly as a
 *       barline, a breath mark or a key change does</li>
 *   <li>rest-with-Lyric(extend = STOP) + active EXTENDER → ends EXTENDER the same way as the
 *       note case above, anchored to the rest's notehead-equivalent right edge</li>
 *   <li>note-with-Lyric(extend = NONE, text) + active EXTENDER → EXTENDER ends at start of this
 *       syllable; a new span (if any) begins after this syllable</li>
 *   <li>host of a paired grace note → emits no lyric box (the syllable belongs to the grace); a
 *       STOP on it ends the active EXTENDER exactly as the note case above, closing the automatic
 *       grace-host melisma, while any other lyric state passes through so a hyphen or extender
 *       from the grace reaches the next lyric-bearing element. The one exception is a grace
 *       syllable that already spans the grace-host union
 *       ({@link HorizontalSpacingCalculator#graceSyllableSpansUnion}): the syllable itself already
 *       reaches the host, so the melisma — real in the model — is not drawn at all</li>
 * </ul>
 * <p>
 * Spans that extend past the last column produce a {@link LyricConnectorLayout.Kind#DANGLING_EXTENDER}
 * anchored to the right edge of the last eligible element on the line; the caller threads
 * {@link Result#hasTrailingContinuation()} into the next line's {@code hasLeadingContinuation}
 * so that line emits a matching leading stub from x = 0 to its first lyric-bearing element
 * (or to the first rest that breaks the continuation).
 */
public final class LyricLayoutBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(LyricLayoutBuilder.class);

    // LilyPond's LyricExtender minimum-length (scm/define-grobs.scm): the extender's total length,
    // measured from the end of the syllable, so a melisma spanning few/close notes still reads as a
    // visible line. Longer melismas are unaffected: the extender is clamped to end no earlier than
    // the terminating note's notehead right edge (see ElementColumn#getNoteheadRightEdgeXSs), never
    // the column's right edge, so a stem, flag, or augmentation dot never shifts where it ends.
    static final double MIN_MELISMA_LENGTH_SS = 1.5;

    private LyricLayoutBuilder() {}

    public static final String PLACEHOLDER_TEXT = "?";

    /**
     * @param boxes                   the syllable boxes, by the element that carries each
     * @param placeholders            for every note that can bear a syllable but has none in
     *                                the script laid out, a box of {@link #PLACEHOLDER_TEXT}; it
     *                                takes no space and touches no connector
     * @param connectors              the hyphens and extenders
     * @param hasTrailingContinuation whether an extender runs off the end of the line
     */
    public record Result(
        Map<StaffElement, List<LyricBoxLayout>> boxes,
        Map<StaffElement, LyricBoxLayout> placeholders,
        List<LyricConnectorLayout> connectors,
        boolean hasTrailingContinuation
    ) {}

    /**
     * Builds lyric boxes and connectors for a single line.
     * <p>
     * Only {@code activeScript} is laid out. A song's other scripts are the same lyrics in other
     * languages, held in the document but never shown alongside the one the user picked, so they
     * produce no boxes, no connectors and no second row to make space for.
     * <p>
     * {@code activeScript} stamps the boxes and connectors this emits. The lyrics themselves come
     * off the columns, which {@link ElementColumnBuilder} already resolved for that script and
     * measured the cached syllable widths against, so text and widths cannot come from different
     * scripts.
     *
     * @param columns                per-element columns in line order (X positions already finalized)
     * @param activeScript            the script to lay out, from {@link songscribe.dom.Song#getActiveScript()}
     * @param lyricRenderMetrics     metrics used to measure syllable text widths
     * @param hasLeadingContinuation true if the previous line ended with an active extender
     *                               that should continue from x = 0 on this line
     * @return the boxes and connectors for that script, and whether an extender runs off the line
     */
    public static Result build(
        List<ElementColumn> columns,
        Song.Script activeScript,
        LyricRenderMetrics lyricRenderMetrics,
        boolean hasLeadingContinuation) {

        var boxesByElement = new HashMap<StaffElement, List<LyricBoxLayout>>(columns.size());
        var placeholders = new HashMap<StaffElement, LyricBoxLayout>(columns.size());
        var placeholderWidthSs = lyricRenderMetrics.placeholderWidthSs();
        var connectors = new ArrayList<LyricConnectorLayout>(columns.size());
        var state = new ExtenderState(hasLeadingContinuation);

        for (var columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            var column = columns.get(columnIndex);
            var element = column.getElement();

            var lyric = column.getLyric();
            var extend = lyric != null ? lyric.extend() : null;

            // The host of a paired grace note never carries a syllable of its own, so it emits no
            // lyric box. A STOP carrier on it closes the automatic melisma that the grace started,
            // ending past the host's notehead like any other STOP; anything else passes through so
            // hyphens and extenders originating from the grace reach the next lyric-bearing element.
            if (isHostOfPairedGraceColumn(columns, columnIndex)) {
                if (extend == Lyric.Extend.STOP) {
                    state.closeGraceHostExtender(
                        connectors, activeScript, column.getNoteheadRightEdgeXSs(), columnIndex);
                }

                continue;
            }

            if (column.isRest()) {
                // A rest carries a melisma or a hyphen onward but never a syllable of its own
                // (ElementType.bearsSyllableText), so it emits no box and cannot end a chain by
                // standing in it. The one thing it can do to an active extender is close it with
                // a STOP carrier, which ends past the rest's notehead-equivalent right edge on
                // the LilyPond LyricExtender minimum-length rule (see MIN_MELISMA_LENGTH_SS).
                if (extend == Lyric.Extend.STOP) {
                    state.closeExtenderPastHead(connectors, activeScript, column.getNoteheadRightEdgeXSs());
                }

                continue;
            }

            if (lyric == null) {
                // Note with no lyric: extender (if active) continues silently through this column.
                // A note that can bear a syllable gets a placeholder where its syllable would go.
                if (columnCanBearSyllable(columns, columnIndex)) {
                    var placeholderXSs = computeLyricBoxLeftXSs(new BoxPlacement(
                        column,
                        placeholderWidthSs,
                        // The placeholder text is one grapheme, so its first grapheme is all of it.
                        placeholderWidthSs,
                        graceUnionWidthSs(columns, columnIndex),
                        // A note with no lyric starts no melisma, so its pair carries none.
                        PairMelisma.NONE));
                    placeholders.put(
                        element, new LyricBoxLayout(placeholderXSs, placeholderWidthSs, activeScript, PLACEHOLDER_TEXT));
                }

                continue;
            }

            if (extend == Lyric.Extend.CONTINUE) {
                // CONTINUE carrier: extender continues silently through this column.
                continue;
            }

            if (extend == Lyric.Extend.STOP) {
                // STOP carrier: ends active extender past this note's notehead, no box.
                state.closeExtenderPastHead(connectors, activeScript, column.getNoteheadRightEdgeXSs());
                continue;
            }

            var text = lyric.text();
            // ElementColumnBuilder measured this syllable's width when it resolved the lyric above;
            // reuse the cached value to avoid a redundant TextLayout allocation per layout pass.
            var widthSs = column.getSyllableWidthSs();
            var hostColumn = graceHostColumn(columns, columnIndex);
            var hostLyric = hostColumn != null ? hostColumn.getLyric() : null;
            var graceUnionWidthSs = graceUnionWidthSs(columns, columnIndex);
            var boxXSs = computeLyricBoxLeftXSs(new BoxPlacement(
                column,
                widthSs,
                lyricRenderMetrics.firstGraphemeWidthSs(text),
                graceUnionWidthSs,
                HorizontalSpacingCalculator.pairCarriesGraceHostMelisma(lyric, hostLyric)));
            var box = new LyricBoxLayout(boxXSs, widthSs, activeScript, text);
            boxesByElement.computeIfAbsent(element, e -> new ArrayList<>()).add(box);

            // Close any pending hyphen at the start of this syllable.
            var pendingHyphen = state.pendingHyphen;

            if (pendingHyphen != null) {
                connectors.add(new LyricConnectorLayout(
                    pendingHyphen.startXSs(),
                    boxXSs,
                    activeScript,
                    LyricConnectorLayout.Kind.HYPHEN,
                    pendingHyphen.columnIndex()));
                state.pendingHyphen = null;
            }

            // Close any active extender at the start of this syllable.
            state.closeExtender(connectors, activeScript, boxXSs);

            var syllableEndXSs = boxXSs + widthSs;
            var opensHyphen = Lyric.syllabicContinues(lyric.syllabic());

            if (opensHyphen) {
                state.pendingHyphen = new PendingHyphen(syllableEndXSs, columnIndex);
            }

            if (extend == Lyric.Extend.START && !opensHyphen) {
                state.activeExtender = new ActiveExtender(
                    syllableEndXSs,
                    columnIndex,
                    column.isGraceNote()
                        && HorizontalSpacingCalculator.graceSyllableSpansUnion(widthSs, graceUnionWidthSs));
            }
        }

        var hasTrailingContinuation = false;

        // Any START that reaches the end of the line without being closed by a STOP or a
        // text-bearing note continues the melisma onto the next line.
        if (state.activeExtender != null) {
            emitDanglingExtender(connectors, columns, state.activeExtender, activeScript);
            hasTrailingContinuation = true;
        }

        if (state.pendingHyphen != null) {
            emitDanglingHyphen(connectors, columns, state.pendingHyphen, activeScript);
        }

        // Always the lyric space width: a melisma is sung on a single (non-hyphenated) syllable,
        // so the gap before the following word is a word space, never a hyphen cell.
        clampExtendersToFollowingSyllable(connectors, boxesByElement, lyricRenderMetrics.spaceWidthSs());

        return new Result(boxesByElement, placeholders, connectors, hasTrailingContinuation);
    }

    /** Null for anything but a grace, and for a grace ending the line. */
    private static @Nullable ElementColumn graceHostColumn(List<ElementColumn> columns, int columnIndex) {
        return columns.get(columnIndex).isGraceNote() && columnIndex + 1 < columns.size()
            ? columns.get(columnIndex + 1)
            : null;
    }

    /** 0 when the column at {@code columnIndex} is not a grace note. */
    private static double graceUnionWidthSs(List<ElementColumn> columns, int columnIndex) {
        var column = columns.get(columnIndex);

        return column.isGraceNote()
            ? spacedGraceHostUnionWidthSs(columns, columnIndex, column, graceHostColumn(columns, columnIndex))
            : 0;
    }

    /**
     * Runs once the whole script is laid out, since the next syllable's box is not known when a
     * STOP carrier closes its extender. DANGLING_EXTENDER connectors are left untouched.
     */
    private static void clampExtendersToFollowingSyllable(
        List<LyricConnectorLayout> connectors,
        Map<StaffElement, ? extends List<LyricBoxLayout>> boxesByElement,
        double gapSs) {

        for (var i = 0; i < connectors.size(); i++) {
            var connector = connectors.get(i);

            if (connector.kind() != LyricConnectorLayout.Kind.EXTENDER) {
                continue;
            }

            var nextSyllableLeftXSs = firstBoxLeftXSsAfter(boxesByElement, connector.startXSs());

            if (Double.isNaN(nextSyllableLeftXSs)) {
                continue;
            }

            var maxEndXSs = nextSyllableLeftXSs - gapSs;

            if (connector.endXSs() > maxEndXSs) {
                connectors.set(i, new LyricConnectorLayout(
                    connector.startXSs(),
                    maxEndXSs,
                    connector.script(),
                    connector.kind(),
                    connector.sourceElementIndex()));
            }
        }
    }

    /** {@link Double#NaN} if there is none. */
    private static double firstBoxLeftXSsAfter(
        Map<StaffElement, ? extends List<LyricBoxLayout>> boxesByElement,
        double xSs) {

        var nextLeftXSs = Double.NaN;

        for (var boxes : boxesByElement.values()) {
            for (var box : boxes) {
                var boxLeftXSs = box.xSs();

                if (boxLeftXSs > xSs && (Double.isNaN(nextLeftXSs) || boxLeftXSs < nextLeftXSs)) {
                    nextLeftXSs = boxLeftXSs;
                }
            }
        }

        return nextLeftXSs;
    }

    /**
     * A syllable is centered on the notehead center, excluding the flag and augmentation dots.
     * <p>
     * A grace and its host are one unioned column for lyric layout; the offset comes from
     * {@link HorizontalSpacingCalculator#graceLyricLeftOffsetSs}, which the spacing calculator
     * also reads, so the box is drawn where space was reserved.
     */
    private static double computeLyricBoxLeftXSs(BoxPlacement placement) {
        var column = placement.column();

        if (column.isGraceNote()) {
            var syllable = new GraceSyllable(
                placement.widthSs(),
                placement.firstGraphemeWidthSs(),
                placement.graceUnionWidthSs(),
                placement.pairMelisma());

            return column.getXSs() + HorizontalSpacingCalculator.graceLyricLeftOffsetSs(syllable, column);
        }

        // Center the syllable on the notehead, which excludes the flag and augmentation dots
        // (getNoteheadCenterXSs) so neither shifts the lyric.
        return column.getNoteheadCenterXSs() - placement.widthSs() / 2.0;
    }

    /**
     * @param graceUnionWidthSs the grace–host union as this line was spaced
     *                          ({@link #graceUnionWidthSs}); 0 when {@code column} is not a grace
     *                          note
     */
    private record BoxPlacement(
        ElementColumn column,
        double widthSs,
        double firstGraphemeWidthSs,
        double graceUnionWidthSs,
        PairMelisma pairMelisma
    ) {}

    /**
     * The width of the grace–host lyric union, measured from the two columns' final X positions
     * rather than rebuilt from the ideal gap, which the solver's optical correction, struts and
     * compression all change. An unpaired grace keeps the ideal width, which is also what
     * {@link HorizontalSpacingCalculator} reserved against.
     */
    private static double spacedGraceHostUnionWidthSs(
        List<ElementColumn> columns,
        int graceIndex,
        ElementColumn grace,
        @Nullable ElementColumn hostColumn) {

        if (hostColumn != null && isHostOfPairedGraceColumn(columns, graceIndex + 1)) {
            return hostColumn.getNoteheadRightEdgeXSs() - grace.getXSs();
        }

        return HorizontalSpacingCalculator.idealGraceHostUnionWidthSs(grace, hostColumn);
    }

    /** Asked of the columns, not a line, so columns built from no line answer as a live line does. */
    private static boolean isHostOfPairedGraceColumn(List<ElementColumn> columns, int index) {
        return index >= 1 && index <= columns.size()
            && columns.get(index - 1).getElement() instanceof NoteElement note
            && note.isPairedGraceNote();
    }

    private static boolean columnCanBearSyllable(List<ElementColumn> columns, int index) {
        var predecessor = index >= 1 ? columns.get(index - 1).getElement() : null;
        return columns.get(index).getElement().canBearSyllable(predecessor);
    }

    /**
     * Columns with no lyric are passed over, as in the main loop, so an active extender keeps
     * running; stopping at the first would cut a melisma short.
     */
    private static void emitDanglingExtender(
        List<? super LyricConnectorLayout> connectors,
        List<ElementColumn> columns,
        ActiveExtender extender,
        Song.Script script
    ) {
        var startColumnIndex = extender.sourceColumnIndex();
        var endXSs = startColumnIndex != null
            ? columns.get(startColumnIndex).getRightEdgeXSs()
            : extender.startXSs();
        var firstWalkedColumnIndex = startColumnIndex != null ? startColumnIndex + 1 : 0;

        for (var i = firstWalkedColumnIndex; i < columns.size(); i++) {
            // Pass through the host of a paired grace; the host never carries a lyric, but
            // the extender's silent run must not be broken by it.
            if (isHostOfPairedGraceColumn(columns, i)) {
                continue;
            }

            var column = columns.get(i);
            var lyric = column.getLyric();
            var extend = lyric != null ? lyric.extend() : null;

            if (extend != Lyric.Extend.CONTINUE && extend != Lyric.Extend.STOP) {
                if (column.getElement().getType().bearsSyllableText()) {
                    break;
                }

                continue;
            }

            endXSs = column.getRightEdgeXSs();
        }

        connectors.add(new LyricConnectorLayout(
            extender.startXSs(),
            endXSs,
            script,
            LyricConnectorLayout.Kind.DANGLING_EXTENDER,
            startColumnIndex));
    }

    /**
     * A barline, a rest, a key change and the host of a paired grace note are passed over: a hyphen
     * belongs between two syllables. The lyric editor prevents a hyphen-opening syllable without a
     * following eligible element, so finding none here is a layout invariant violation.
     */
    private static void emitDanglingHyphen(
        List<? super LyricConnectorLayout> connectors,
        List<ElementColumn> columns,
        PendingHyphen hyphen,
        Song.Script script
    ) {
        for (var i = hyphen.columnIndex() + 1; i < columns.size(); i++) {
            var column = columns.get(i);

            if (columnCanBearSyllable(columns, i)) {
                connectors.add(new LyricConnectorLayout(
                    hyphen.startXSs(),
                    column.getLeftEdgeXSs(),
                    script,
                    LyricConnectorLayout.Kind.DANGLING_HYPHEN,
                    hyphen.columnIndex()));
                return;
            }
        }

        LOG.error("Dangling hyphen at column {} in script {} has no following eligible element on line",
            hyphen.columnIndex(), script);
    }

    /**
     * @param startXSs                    {@code 0.0} for a leading continuation
     * @param sourceColumnIndex           {@code null} for a leading continuation carried in from
     *                                    the previous line
     * @param syllableSpansGraceHostUnion whether the extender starts at a grace whose syllable
     *                                    already spans the grace–host union; such an extender is
     *                                    dropped rather than drawn (see
     *                                    {@link ExtenderState#closeGraceHostExtender})
     */
    private record ActiveExtender(
        double startXSs,
        @Nullable Integer sourceColumnIndex,
        boolean syllableSpansGraceHostUnion
    ) {
        static ActiveExtender leadingContinuation() {
            return new ActiveExtender(0.0, null, false);
        }
    }

    private record PendingHyphen(double startXSs, int columnIndex) {}

    /** Mutable scratch state for one script pass. */
    private static final class ExtenderState {
        @Nullable ActiveExtender activeExtender;
        @Nullable PendingHyphen pendingHyphen = null;

        ExtenderState(boolean hasLeadingContinuation) {
            activeExtender = hasLeadingContinuation ? ActiveExtender.leadingContinuation() : null;
        }

        void closeExtender(List<? super LyricConnectorLayout> connectors, Song.Script script, double endXSs) {
            if (activeExtender != null) {
                connectors.add(new LyricConnectorLayout(
                    activeExtender.startXSs(),
                    endXSs,
                    script,
                    LyricConnectorLayout.Kind.EXTENDER,
                    activeExtender.sourceColumnIndex()));
                activeExtender = null;
            }
        }

        /**
         * The end is {@code max(startXSs + MIN_MELISMA_LENGTH_SS, headRightEdgeXSs)} — LilyPond's
         * LyricExtender minimum-length rule.
         */
        void closeExtenderPastHead(List<? super LyricConnectorLayout> connectors, Song.Script script, double headRightEdgeXSs) {
            if (activeExtender == null) {
                return;
            }

            closeExtender(connectors, script, Math.max(activeExtender.startXSs() + MIN_MELISMA_LENGTH_SS, headRightEdgeXSs));
        }

        /**
         * Ends at {@code hostColumnIndex}'s host the melisma that reaches it. The extender is
         * dropped, not drawn (the melisma stays in the model), only when this host's <em>own</em>
         * grace started it and that grace's syllable already spans the pair
         * ({@link HorizontalSpacingCalculator#graceSyllableSpansUnion}). A melisma started at
         * another grace and running through this host must not be dropped.
         */
        void closeGraceHostExtender(
            List<? super LyricConnectorLayout> connectors,
            Song.Script script,
            double headRightEdgeXSs,
            int hostColumnIndex) {

            if (activeExtender != null && activeExtender.syllableSpansGraceHostUnion()) {
                var sourceColumnIndex = activeExtender.sourceColumnIndex();

                if (sourceColumnIndex != null && sourceColumnIndex == hostColumnIndex - 1) {
                    activeExtender = null;
                    return;
                }
            }

            closeExtenderPastHead(connectors, script, headRightEdgeXSs);
        }
    }
}
