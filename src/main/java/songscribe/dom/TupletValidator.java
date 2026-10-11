/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.dom;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;

/**
 * Decides whether a span of elements can carry a tuplet of a given printed number, and
 * derives the ratio that number stands for. {@link Tuplet#spanning} and
 * {@link Tuplet#spanningStated} ask it, and answer its verdict as a {@link Spanning}.
 *
 * <p>A tuplet means: <em>N</em> notes played in the time normally occupied by <em>M</em>
 * notes of written value <em>V</em>. Only <em>N</em> — the printed number, called the
 * grade elsewhere in the model — comes from outside. <em>V</em> is derived as
 * {@code S / N}, where <em>S</em> is the summed written duration of the span's
 * non-grace elements, and <em>M</em> is derived from <em>V</em> and the beat in effect
 * at the span's anchor.
 *
 * <p><em>V</em> is deliberately not derived as the greatest common divisor of the span's
 * durations. A gcd would renumber ordinary mixed-value notation — a quarter plus two
 * eighths under a 3 — into a sextuplet.
 *
 * <p>Written duration always comes from {@link StaffElement#writtenDuration()},
 * never from {@code getDuration()}: the latter inflates a fermata's value by half again,
 * which is a performance instruction rather than a change to the written rhythm. That
 * inflation is also why the fermata rule excludes a span containing a fermata outright — with
 * no fermata in the span, written and performed durations stay in the exact ratio
 * <em>M</em>:<em>N</em>, so playback and validation sum the same values.
 *
 * <h2>Why a power-of-two <em>N</em>/<em>M</em> is redundancy rather than a ratio</h2>
 *
 * <p>When <em>N</em>/<em>M</em> is a power of two the group is exactly expressible at the
 * next finer written value with no bracket at all, so the printed number is a renotation
 * instruction rather than a re-timing: 4:2 over four eighths at a quarter beat says no
 * more than "read these as sixteenths".
 *
 * <p>Redundancy is a property of the <em>derivation</em>, not of the tuplet. When
 * <em>M</em> is derived and the number therefore says nothing, the tuplet is dropped; when
 * <em>M</em> is stated in a file and is otherwise valid it is kept even if it is not the
 * conventional span, because a file that explicitly says 3:2 for three eighths under a
 * dotted-quarter beat is asking for a real re-timing, and a third-party 7:8 must survive.
 * {@link #validateStated} is the path for a stated ratio; {@link #validate} is the path for
 * a derived one.
 *
 * <h2>The cost of beat resolution</h2>
 *
 * <p>{@link Song#beatUnitAt} walks backward over every element before the anchor and is
 * deliberately uncached — see its documentation for why a maintained beat index was
 * rejected. Resolving the beat once per tuplet therefore makes a whole-song check quadratic,
 * which is why a {@link Tuplet.Site} carries the beat its caller already holds rather than
 * looking it up. {@link #sitesFrom} is the way to judge many tuplets in one song: it resolves
 * the beat once and carries it forward, so every site it answers costs nothing more.
 */
public final class TupletValidator {

    /** Largest binary multiple of the beat a tuplet may conventionally replace. */
    private static final int MAX_BEAT_MULTIPLE = 8;

    /** Shortest span a beat is subdivided down to, in PPQ ticks — the shortest written value. */
    private static final int SHORTEST_SPAN_TICKS = NoteValue.THIRTY_SECOND.ticks();

    private TupletValidator() {
    }

    /**
     * How much of the rule set a tuplet is judged against. The beat barrier and the bar or
     * breath mark inside the span hold under {@link #STRICT} only; every other rule holds under
     * both.
     */
    public enum Strictness {

        /** Every rule: editing and pasting, which choose what gets written. */
        STRICT,

        /**
         * Every rule but the beat barrier and the bar or breath mark inside the span: loading,
         * since a document that already exists is not improved by dropping a tuplet whose only
         * fault is that this editor would not have offered to create it there.
         */
        LENIENT
    }

    /** Why a span cannot carry a tuplet. */
    private enum Reason {

        /** The span holds no written duration: S is zero. */
        EMPTY_SPAN,

        /** S does not divide into N equal parts. */
        NOT_DIVISIBLE,

        /** S divides into N equal parts, but the share S / N is not a notatable value. */
        SHARE_NOT_NOTATABLE,

        /** The stated V differs from the share S / N. */
        STATED_VALUE_MISMATCH,

        /** N equals the stated M, so the tuplet plays its notes in their own time. */
        NUMBER_EQUALS_NORMAL_NOTES,

        /** The beat offers no conventional span below N, and no duplet fallback above it. */
        NO_CONVENTIONAL_SPAN,

        /** N / M is a power of two: a renotation, not a re-timing. */
        POWER_OF_TWO_RATIO,

        /** An element in the span carries a fermata. */
        FERMATA,

        /** The beat changes at an element after the anchor. */
        BEAT_BARRIER,

        /** A bar of any type, or a breath mark, stands in the span before its last element. */
        BAR_OR_BREATH_MARK,

        /** The span holds fewer than {@value Span#MIN_PITCHED_NOTES} pitched notes. */
        TOO_FEW_NOTES;

        /** The {@code Strings} key of the text that tells the user why the tuplet failed. */
        private String messageKey() {
            return switch (this) {
                case EMPTY_SPAN -> Strings.REPORT_REASON_EMPTY_SPAN;
                case NOT_DIVISIBLE -> Strings.REPORT_REASON_NOT_DIVISIBLE;
                case SHARE_NOT_NOTATABLE -> Strings.REPORT_REASON_NOT_NOTATABLE;
                case STATED_VALUE_MISMATCH -> Strings.REPORT_REASON_STATED_VALUE_MISMATCH;
                case NUMBER_EQUALS_NORMAL_NOTES -> Strings.REPORT_REASON_NUMBER_EQUALS_NORMAL_NOTES;
                case NO_CONVENTIONAL_SPAN -> Strings.REPORT_REASON_NO_CONVENTIONAL_SPAN;
                case POWER_OF_TWO_RATIO -> Strings.REPORT_REASON_POWER_OF_TWO;
                case FERMATA -> Strings.REPORT_REASON_FERMATA;
                case BEAT_BARRIER -> Strings.REPORT_REASON_BEAT_BARRIER;
                case BAR_OR_BREATH_MARK -> Strings.REPORT_REASON_TUPLET_BAR_OR_BREATH_MARK;
                case TOO_FEW_NOTES -> Strings.REPORT_REASON_TOO_FEW_NOTES;
            };
        }
    }

    /** Where a span's elements sit that makes it unfit to carry a tuplet. */
    enum PlacementFault {

        /** An element in the span carries a fermata. */
        FERMATA,

        /** The beat changes at an element after the anchor. */
        BEAT_BARRIER,

        /** A bar of any type, or a breath mark, stands in the span before its last element. */
        BAR_OR_BREATH_MARK;

        /** The refusal reason this fault produces. */
        private Reason reason() {
            return switch (this) {
                case FERMATA -> Reason.FERMATA;
                case BEAT_BARRIER -> Reason.BEAT_BARRIER;
                case BAR_OR_BREATH_MARK -> Reason.BAR_OR_BREATH_MARK;
            };
        }
    }

    /**
     * The verdict on a span: {@link Valid} carries the ratio the tuplet is to be built with,
     * {@link Invalid} the reason no tuplet can be.
     */
    sealed interface Result {

        /**
         * The span can carry the tuplet.
         *
         * @param ratio the ratio the tuplet is to be built with
         */
        record Valid(TupletNormalTime ratio) implements Result {
        }

        /**
         * The span cannot carry the tuplet.
         *
         * @param reasonKey the {@code Strings} key of the reason, whose value is plain text the
         *                  user reads
         */
        record Invalid(String reasonKey) implements Result {
        }
    }

    /**
     * Everything about a span that does not depend on N, computed once so every candidate grade
     * can be tested against it.
     *
     * @param writtenTicks    S, the summed written duration of the span's non-grace elements
     * @param beat            B, the beat in effect at the span's anchor, including a beat the
     *                        anchor itself defines
     * @param enoughPitchedNotes whether the span holds the pitched notes a tuplet needs, as
     *                           {@link Span#holdsEnoughPitchedNotes} judges it
     * @param placementFaults every fault found in the span; stored as an unmodifiable copy
     */
    record SpanContext(
        int writtenTicks, BeatUnit beat, boolean enoughPitchedNotes, Set<PlacementFault> placementFaults
    ) {

        SpanContext {
            placementFaults = Set.copyOf(placementFaults);
        }
    }

    /**
     * Measures the span {@code site} names, against the beat it carries. The cost is the span's
     * length: the beat is not looked up.
     *
     * @param site the span and the beat in effect at its anchor
     * @return the grade-independent facts about the span
     */
    static SpanContext measure(Tuplet.Site site) {
        var line = site.line();
        var range = site.range();
        var measure = new SpanMeasure(range, site.beat());

        for (var index = range.begin(); index <= range.end(); index++) {
            measure.add(line.getElement(index), index);
        }

        return measure.context(Span.holdsEnoughPitchedNotes(line.getElements(), range));
    }

    /**
     * Judges a span for a tuplet of {@code grade} whose ratio is derived here.
     *
     * <p>The checks run in this order, and the first that fails is the answer: the span holds
     * some written duration; it divides into N equal parts; the share is a notatable value; no
     * element carries a fermata; the span holds at least {@value Span#MIN_PITCHED_NOTES} pitched
     * notes; under {@link Strictness#STRICT} only, the beat does not change after the anchor,
     * and no bar or breath mark stands before the last element; the beat offers a conventional
     * span for M; and N / M is not a power of two.
     *
     * <p>The conventional span and the power of two hold under both strictnesses, because a
     * derived M that lands nowhere conventional, or on a power-of-two ratio, means the printed
     * number states nothing to preserve. A ratio a file states is judged by
     * {@link #validateStated}, which applies neither.
     *
     * @param context    the span as measured by {@link #measure}
     * @param grade      N, the printed tuplet number
     * @param strictness how much of the rule set to enforce
     * @return a valid result carrying the derived M and V, or the reason of the first check
     *     that failed
     */
    static Result validate(SpanContext context, TupletGrade grade, Strictness strictness) {
        var divisionFailure = divisionFailure(context.writtenTicks(), grade);

        if (divisionFailure != null) {
            return invalid(divisionFailure);
        }

        var noteValueTicks = context.writtenTicks() / grade.number();
        var noteValue = NotatableValue.withTicks(noteValueTicks);

        if (noteValue == null) {
            return invalid(Reason.SHARE_NOT_NOTATABLE);
        }

        var contentFailure = contentFailure(context, strictness);

        if (contentFailure != null) {
            return invalid(contentFailure);
        }

        var beat = context.beat();
        var spans = conventionalSpans(noteValue, beat);
        var normalNotes = chooseNormalNotes(spans, grade, beat);

        if (normalNotes == null) {
            return invalid(Reason.NO_CONVENTIONAL_SPAN);
        }

        var actualNotes = grade.number();

        if (actualNotes % normalNotes == 0 && isPowerOfTwo(actualNotes / normalNotes)) {
            return invalid(Reason.POWER_OF_TWO_RATIO);
        }

        return new Result.Valid(new TupletNormalTime(normalNotes, noteValue));
    }

    /**
     * Judges a span for a tuplet of {@code grade} that already carries a ratio — read from a
     * file that stated one, or travelling on the clipboard — rather than one to be derived here.
     *
     * <p>The stated M is accepted without the conventional-span or redundancy test: a
     * document that explicitly says 3:2 is asking for a real re-timing, and redundancy is a
     * property of the derivation rather than of the tuplet. What is still checked is that
     * the ratio is self-consistent — the stated V must equal {@code S / N} — and that the
     * span's contents and placement do not break it. Under {@link Strictness#STRICT} the placement
     * check includes the beat barrier and the bar or breath mark, which is what makes a
     * paste drop a tuplet the destination genuinely splits while leaving one it merely would
     * not have derived alone.
     *
     * <p>The checks run in this order, and the first that fails is the answer: N differs from
     * the stated M; the span holds some written duration; it divides into N equal parts; the
     * share equals the stated V; no element carries a fermata; the span holds at least
     * {@value Span#MIN_PITCHED_NOTES} pitched notes; and under {@link Strictness#STRICT} only, the
     * beat does not change after the anchor, and no bar or breath mark stands before the last
     * element.
     *
     * @param context    the span as measured by {@link #measure}
     * @param grade      N, as stated
     * @param stated     M and V, as stated
     * @param strictness how much of the placement rule set to enforce
     * @return a valid result echoing the stated ratio, or the reason of the first check that
     *     failed
     */
    static Result validateStated(
        SpanContext context, TupletGrade grade, TupletNormalTime stated, Strictness strictness
    ) {
        if (grade.number() == stated.normalNotes()) {
            return invalid(Reason.NUMBER_EQUALS_NORMAL_NOTES);
        }

        var divisionFailure = divisionFailure(context.writtenTicks(), grade);

        if (divisionFailure != null) {
            return invalid(divisionFailure);
        }

        if (context.writtenTicks() / grade.number() != stated.noteValue().ticks()) {
            return invalid(Reason.STATED_VALUE_MISMATCH);
        }

        var contentFailure = contentFailure(context, strictness);

        if (contentFailure != null) {
            return invalid(contentFailure);
        }

        return new Result.Valid(stated);
    }

    /**
     * The constraints on the span's total duration, shared by the derived and the stated
     * paths: the span holds some written duration, and that duration divides into N equal
     * parts.
     *
     * @return the reason it failed, or null when it did not
     */
    private static @Nullable Reason divisionFailure(int writtenTicks, TupletGrade grade) {
        if (writtenTicks == 0) {
            return Reason.EMPTY_SPAN;
        }

        if (writtenTicks % grade.number() != 0) {
            return Reason.NOT_DIVISIBLE;
        }

        return null;
    }

    /**
     * The constraints on the span's elements rather than on its total duration, shared by the
     * derived and the stated paths. The fermata holds under either strictness — it inflates the
     * performed duration and so breaks the exact ratio whatever the tuplet says — and so does
     * the pitched-note count, since a group with nothing for the number to count is not a
     * tuplet in any document. The beat barrier and the bar or breath mark are editor policy
     * and hold under {@link Strictness#STRICT} only.
     *
     * @return the reason it failed, or null when it did not
     */
    private static @Nullable Reason contentFailure(SpanContext context, Strictness strictness) {
        var faults = context.placementFaults();

        if (faults.contains(PlacementFault.FERMATA)) {
            return PlacementFault.FERMATA.reason();
        }

        if (!context.enoughPitchedNotes()) {
            return Reason.TOO_FEW_NOTES;
        }

        if (strictness != Strictness.STRICT) {
            return null;
        }

        if (faults.contains(PlacementFault.BEAT_BARRIER)) {
            return PlacementFault.BEAT_BARRIER.reason();
        }

        if (faults.contains(PlacementFault.BAR_OR_BREATH_MARK)) {
            return PlacementFault.BAR_OR_BREATH_MARK.reason();
        }

        return null;
    }

    /**
     * A tuplet a caller hands the walk at its anchor: what the caller knows it by, and where it
     * ends.
     *
     * @param tuplet   the caller's own tuplet, handed back unchanged in its {@link Sited}
     * @param endIndex the index of the tuplet's last element in the anchor's line, at or after
     *                 the anchor
     * @param <T>      the caller's tuplet type
     */
    public record Anchored<T>(T tuplet, int endIndex) {}

    /**
     * The tuplets a caller asks {@link #sitesFrom} to place, looked up by anchor.
     *
     * @param <T> the caller's tuplet type
     */
    @FunctionalInterface
    public interface AnchoredTuplets<T> {

        /**
         * The caller's tuplet anchored at {@code index} in {@code line}.
         *
         * @param line  the line the walk is in
         * @param index the index of the element the walk has reached
         * @return the tuplet whose anchor is that element, or {@code null} when none is
         */
        @Nullable Anchored<T> anchoredAt(Line line, int index);
    }

    /**
     * A tuplet the caller supplied, with the site the walk found for it: ready for
     * {@link Tuplet#spanning} or {@link Tuplet#spanningStated}.
     *
     * @param tuplet the tuplet as the caller supplied it
     * @param site   the tuplet's line, its span in that line from the anchor to
     *               {@link Anchored#endIndex()}, and the beat in effect at the anchor
     * @param <T>    the caller's tuplet type
     */
    public record Sited<T>(T tuplet, Tuplet.Site site) {}

    /**
     * Finds the site of every tuplet {@code tuplets} supplies at an anchor at or after
     * {@code from}, in document order. Pass the song's first element to cover the whole song.
     *
     * <p>This is one forward walk rather than a {@link Tuplet.Site#at} per tuplet: the beat is
     * resolved once at the starting position and carried forward over every element, so the
     * walk costs O(elements) rather than O(tuplets × elements).
     *
     * @param song    the song to walk
     * @param from    the element to start at, which must exist in {@code song}
     * @param tuplets the tuplets to place, by anchor
     * @param <T>     the caller's tuplet type
     * @return one entry per element at or after {@code from} at which {@code tuplets} supplied a
     *     tuplet, in document order
     * @invariant an entry's site carries the beat {@link Song#beatUnitAt} answers at its anchor
     */
    public static <T> List<Sited<T>> sitesFrom(Song song, ElementLocation from, AnchoredTuplets<T> tuplets) {
        var sited = new ArrayList<Sited<T>>();
        var runningBeat = song.beatUnitAt(from.lineIndex(), from.elementIndex());
        var firstIndex = from.elementIndex();

        for (var lineIndex = from.lineIndex(); lineIndex < song.lineCount(); lineIndex++) {
            var line = song.getLine(lineIndex);

            for (var index = firstIndex; index < line.elementCount(); index++) {
                var definedBeat = TempoResolver.beatDefinedAt(line.getElement(index));

                if (definedBeat != null) {
                    runningBeat = definedBeat;
                }

                // The running beat already includes the anchor's own definition, which is the
                // beat a site carries.
                var anchored = tuplets.anchoredAt(line, index);

                if (anchored != null) {
                    var range = new ElementRange(index, anchored.endIndex());
                    sited.add(new Sited<>(anchored.tuplet(), new Tuplet.Site(line, range, runningBeat)));
                }
            }

            firstIndex = 0;
        }

        return sited;
    }

    /**
     * Measures one span as its elements are handed to it in order, from the anchor to the end.
     *
     * <p>Grace notes ride along and add no written duration; rests count toward the duration
     * exactly as notes do, and the non-duration elements a span may contain all have a zero
     * default duration.
     */
    private static final class SpanMeasure {

        private final ElementRange span;
        private final BeatUnit beat;
        private final EnumSet<PlacementFault> placementFaults = EnumSet.noneOf(PlacementFault.class);
        private BeatUnit runningBeat;
        private int writtenTicks = 0;

        /**
         * @param span         the span to measure
         * @param beatAtAnchor the beat in effect at the anchor, including a beat the anchor itself
         *                     defines: a barrier on the anchor defines the tuplet's beat rather
         *                     than splitting it
         */
        private SpanMeasure(ElementRange span, BeatUnit beatAtAnchor) {
            this.span = span;
            beat = beatAtAnchor;
            runningBeat = beatAtAnchor;
        }

        /** Takes the element at {@code index}, the next of the span in order. */
        private void add(StaffElement element, int index) {
            // Read the barrier against the beat running before this element, then let the
            // element redefine it.
            if (index != span.begin() && TempoResolver.isBeatChange(element, runningBeat)) {
                placementFaults.add(PlacementFault.BEAT_BARRIER);
            }

            var definedBeat = TempoResolver.beatDefinedAt(element);

            if (definedBeat != null) {
                runningBeat = definedBeat;
            }

            var type = element.getType();

            if (!type.isGraceNote()) {
                writtenTicks += element.writtenDuration();
            }

            if (element.findAttachment(FermataAttachment.class) != null) {
                placementFaults.add(PlacementFault.FERMATA);
            }

            if (isBarOrBreathMarkBeforeEnd(type, index)) {
                placementFaults.add(PlacementFault.BAR_OR_BREATH_MARK);
            }
        }

        /**
         * Whether an element of type {@code type} at {@code index} is a bar of any type, a single
         * barline included, or a breath mark, standing before the span's last element. One at
         * the very end of the span divides nothing, because every note of the span lies before
         * it: selections already sweep in a breath mark that immediately follows the last note,
         * and a span running to the end of a line ends on the barline the
         * model maintains there. Counting either would disable the tuplet control on ordinary
         * selections.
         */
        private boolean isBarOrBreathMarkBeforeEnd(ElementType type, int index) {
            return index != span.end() && (type.isBar() || type.isBreathMark());
        }

        /**
         * @param enoughPitchedNotes whether the span holds the pitched notes a tuplet needs
         * @return what this measure found, with {@code enoughPitchedNotes}
         */
        private SpanContext context(boolean enoughPitchedNotes) {
            return new SpanContext(writtenTicks, beat, enoughPitchedNotes, placementFaults);
        }
    }

    /**
     * The spans, measured in V-units, that a tuplet of value V may conventionally
     * replace under beat B: the binary divisions of B's
     * {@linkplain BeatUnit#binaryDivisionRootTicks() division root} — a dotted beat divides
     * into {@value BeatUnit#DOTTED_BEAT_PARTS} before subdividing binarily — and the binary
     * multiples of B.
     *
     * <p>Only spans that V divides exactly are kept, and a span of a single V-unit is
     * excluded. Excluding it is what makes a compound duplet infer 2:3 rather than the
     * meaningless 2:1.
     */
    private static NavigableSet<Integer> conventionalSpans(NotatableValue noteValue, BeatUnit beat) {
        var spans = new TreeSet<Integer>();
        var beatTicks = beat.ticks();

        for (var ticks = beat.binaryDivisionRootTicks(); ticks >= SHORTEST_SPAN_TICKS; ticks /= 2) {
            addSpan(spans, ticks, noteValue);
        }

        for (var multiple = 1; multiple <= MAX_BEAT_MULTIPLE; multiple *= 2) {
            addSpan(spans, beatTicks * multiple, noteValue);
        }

        return spans;
    }

    private static void addSpan(NavigableSet<? super Integer> spans, int spanTicks, NotatableValue noteValue) {
        var noteValueTicks = noteValue.ticks();

        if (spanTicks % noteValueTicks != 0) {
            return;
        }

        var units = spanTicks / noteValueTicks;

        if (units > 1) {
            spans.add(units);
        }
    }

    /**
     * Picks M: the largest conventional span strictly below N. When there is none, a
     * compound-beat duplet falls back to the smallest span above N, because a duplet is
     * the one grade whose whole purpose is to replace a longer conventional span; that
     * restriction is what produces the invalid diagonal in the expected-results tables.
     *
     * <p>The fallback is deliberately <em>not</em> widened under
     * {@link Strictness#LENIENT}. Widening it would keep a bracket at the price of
     * inventing a re-timing nobody wrote: three quavers under a dotted-crotchet beat
     * would load as 3:6 and play at half speed. Redundancy is a property of the
     * derivation, so when M has to be derived and the printed number says nothing, the
     * tuplet is dropped rather than repaired. A ratio the <em>file</em> states takes the
     * {@link #validateStated} path instead, which never consults this method.
     *
     * @return M, or null when the beat offers no span at all
     */
    private static @Nullable Integer chooseNormalNotes(
        NavigableSet<Integer> spans, TupletGrade grade, BeatUnit beat
    ) {
        var below = spans.lower(grade.number());

        if (below != null) {
            return below;
        }

        if (beat.dotted() && grade == TupletGrade.DUPLET) {
            return spans.higher(grade.number());
        }

        return null;
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static Result invalid(Reason reason) {
        return new Result.Invalid(reason.messageKey());
    }
}
