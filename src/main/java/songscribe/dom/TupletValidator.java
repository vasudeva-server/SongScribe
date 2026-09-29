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
 * derives the ratio that number stands for.
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
 * inflation is also why constraint 4 excludes a span containing a fermata outright — with
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
 * <h2>ABC import derives <em>M</em> rather than trusting the file's stated ratio</h2>
 *
 * <p>No ABC importer exists yet; this note is here for the one that will. ABC's {@code q}
 * is an optional hand-written field with a documented default that producers get wrong: of
 * 897 authored ratios in a 22,818-file corpus, 83 are wrong from writing the group size
 * into the ratio slot, while the documented convention is wrong in 4. Deriving fixes 83
 * and costs 4. <em>N</em> still comes from the file — ABC's printed number is reliable.
 *
 * <p>This is deliberately the opposite of the MusicXML policy, and the distinction is
 * principled: in MusicXML {@code actual-notes}/{@code normal-notes} is a required,
 * machine-written pair, whereas in ABC {@code q} is optional, hand-written and
 * demonstrably unreliable.
 *
 * <h2>The cost of beat resolution</h2>
 *
 * <p>{@link Song#beatUnitAt} walks backward over every element before the anchor and is
 * deliberately uncached — see its documentation for why a maintained beat index was
 * rejected. Calling {@link #describeSpan} once per tuplet therefore makes a whole-song
 * check quadratic. {@link #validateFrom} is the way to avoid paying that cost per tuplet:
 * it resolves the beat once and carries it forward, and the beat barriers fall out of the
 * same walk for free.
 */
public final class TupletValidator {

    /** Largest binary multiple of the beat a tuplet may conventionally replace. */
    private static final int MAX_BEAT_MULTIPLE = 8;

    /** Shortest span a beat is subdivided down to, in PPQ ticks — the shortest written value. */
    private static final int SHORTEST_SPAN_TICKS = NoteValue.THIRTY_SECOND.ticks();

    /**
     * Fewest pitched notes a span must hold to carry a tuplet. A bracket has no meaningful slope
     * across fewer, and a group of rests and grace notes has nothing for the number to count.
     */
    private static final int MIN_PITCHED_NOTES = 2;

    private TupletValidator() {
    }

    public enum Strictness { STRICT, LENIENT }

    public enum Reason {
        EMPTY_SPAN,             // S == 0
        NOT_NOTATABLE,          // V = S/N is not an exact notatable value
        BAD_RATIO,              // N == M
        NO_CONVENTIONAL_SPAN,   // no conventional span below N (and no fallback)
        POWER_OF_TWO_RATIO,     // N / M is a power of two — renotation, not re-timing
        FERMATA,                // an element in the span carries a FermataAttachment
        BEAT_BARRIER,           // a beat barrier lies strictly inside the span
        STRUCTURAL_BOUNDARY,    // barline, repeat, or interior breath mark
        TOO_FEW_NOTES;          // fewer pitched notes than MIN_PITCHED_NOTES

        /**
         * Returns the {@code Strings} key of the text that tells the user why a tuplet failed
         * for this reason.
         *
         * @return the key of this reason's user-facing text
         */
        public String messageKey() {
            return switch (this) {
                case EMPTY_SPAN -> Strings.DIALOG_REPORT_REASON_EMPTY_SPAN;
                case NOT_NOTATABLE -> Strings.DIALOG_REPORT_REASON_NOT_NOTATABLE;
                case BAD_RATIO -> Strings.DIALOG_REPORT_REASON_BAD_RATIO;
                case NO_CONVENTIONAL_SPAN -> Strings.DIALOG_REPORT_REASON_NO_CONVENTIONAL_SPAN;
                case POWER_OF_TWO_RATIO -> Strings.DIALOG_REPORT_REASON_POWER_OF_TWO;
                case FERMATA -> Strings.DIALOG_REPORT_REASON_FERMATA;
                case BEAT_BARRIER -> Strings.DIALOG_REPORT_REASON_BEAT_BARRIER;
                case STRUCTURAL_BOUNDARY -> Strings.DIALOG_REPORT_REASON_STRUCTURAL_BOUNDARY;
                case TOO_FEW_NOTES -> Strings.DIALOG_REPORT_REASON_TOO_FEW_NOTES;
            };
        }
    }

    /**
     * The verdict on a span: {@link Valid} carries the derived (or accepted) ratio the tuplet
     * is to be built with, {@link Invalid} carries the reason no tuplet can be.
     */
    public sealed interface Result {

        /**
         * The span can carry the tuplet.
         *
         * @param ratio the ratio the tuplet is to be built with
         */
        record Valid(TupletRatio ratio) implements Result {
        }

        /**
         * The span cannot carry the tuplet.
         *
         * @param reason why not
         */
        record Invalid(Reason reason) implements Result {
        }
    }

    /**
     * Everything about a span that does not depend on N, computed once so the six
     * candidate grades can be tested without repeating the O(document) beat walk.
     *
     * @param writtenTicks      S, the summed written duration of the span's non-grace elements
     * @param beat              B, the beat in effect at the span's anchor, including a beat the
     *                          anchor itself defines
     * @param pitchedNotes      how many of the span's elements are pitched notes — every note
     *                          but a grace note — which {@link Reason#TOO_FEW_NOTES} is judged on
     * @param placementReasons  each of {@link Reason#FERMATA}, {@link Reason#BEAT_BARRIER} (a
     *                          barrier strictly after the anchor element) and
     *                          {@link Reason#STRUCTURAL_BOUNDARY} found in the span; stored as
     *                          an unmodifiable copy
     */
    public record SpanContext(int writtenTicks, BeatUnit beat, int pitchedNotes, Set<Reason> placementReasons) {

        public SpanContext {
            placementReasons = Set.copyOf(placementReasons);
        }
    }

    /**
     * Measures a span once, so every candidate grade can be tested against it without
     * repeating the beat walk. Resolving the beat is O(elements before the anchor) — see
     * {@link Song#beatUnitAt} — which is the whole reason this is separate from
     * {@link #validate}.
     *
     * @param line the line holding the span, which must be one of its song's lines
     * @param span the span's elements, from its anchor to its last element
     * @return the grade-independent facts about the span
     */
    public static SpanContext describeSpan(Line line, ElementRange span) {
        var measure = new SpanMeasure(span, line.getSong().beatUnitAt(line.index(), span.begin()));

        for (var index = span.begin(); index <= span.end(); index++) {
            measure.add(line.getElement(index), index);
        }

        return measure.context();
    }

    /**
     * Applies the constraints to a span already measured by {@link #describeSpan}.
     *
     * <p>The ratio, the fermata and the pitched-note count hold under both strictnesses; the
     * conventional span,
     * the redundancy test, the beat barrier and the structural boundary hold under
     * {@link Strictness#STRICT} only. Editing and pasting are strict — they choose what
     * gets written — while loading and importing are lenient, because a document that
     * already exists is not improved by dropping a tuplet whose only fault is that this
     * editor would not have offered to create it there.
     *
     * @param context    the span as measured by {@link #describeSpan}
     * @param grade      N, the printed tuplet number
     * @param strictness how much of the rule set to enforce
     * @return a valid result carrying the derived M and V, or the first reason it failed
     */
    public static Result validate(SpanContext context, TupletGrade grade, Strictness strictness) {
        var divisionFailure = divisionFailure(context.writtenTicks(), grade);

        if (divisionFailure != null) {
            return invalid(divisionFailure);
        }

        var noteValueTicks = context.writtenTicks() / grade.number();
        var noteValue = NotatableValue.withTicks(noteValueTicks);

        if (noteValue == null) {
            return invalid(Reason.NOT_NOTATABLE);
        }

        var contentFailure = contentFailure(context, strictness);

        if (contentFailure != null) {
            return invalid(contentFailure);
        }

        // Both of these apply under either strictness, because reaching them at all means
        // M is being derived, and a derived M that lands nowhere conventional — or on a
        // power-of-two ratio — means the printed number states nothing to preserve. The
        // lenient path exists to keep a ratio the file *stated*; that goes through
        // validateStated, which never reaches here.
        var beat = context.beat();
        var spans = conventionalSpans(noteValueTicks, beat);
        var normalNotes = chooseNormalNotes(spans, grade, beat);

        if (normalNotes == null) {
            return invalid(Reason.NO_CONVENTIONAL_SPAN);
        }

        var actualNotes = grade.number();

        if (actualNotes % normalNotes == 0 && isPowerOfTwo(actualNotes / normalNotes)) {
            return invalid(Reason.POWER_OF_TWO_RATIO);
        }

        return new Result.Valid(new TupletRatio(normalNotes, noteValue));
    }

    /** Convenience: {@link #describeSpan} + {@link #validate}. */
    public static Result validateDerived(Line line, ElementRange span, TupletGrade grade, Strictness strictness) {
        return validate(describeSpan(line, span), grade, strictness);
    }

    /**
     * The path for a tuplet that already carries a ratio — read from a file that stated one,
     * or travelling on the clipboard — rather than one to be derived here.
     *
     * <p>The stated M is accepted without the conventional-span or redundancy test: a
     * document that explicitly says 3:2 is asking for a real re-timing, and redundancy is a
     * property of the derivation rather than of the tuplet. What is still checked is that
     * the ratio is self-consistent — the stated V must equal {@code S / N} — and that the
     * span's contents and placement do not break it. Under {@link Strictness#STRICT} the placement
     * check includes the beat barrier and the structural boundary, which is what makes a
     * paste drop a tuplet the destination genuinely splits while leaving one it merely would
     * not have derived alone.
     *
     * @param context    the span as measured by {@link #describeSpan}
     * @param grade      N, as stated
     * @param stated     M and V, as stated
     * @param strictness how much of the placement rule set to enforce
     * @return a valid result echoing the stated ratio, or the reason it was rejected
     */
    public static Result validateStated(
        SpanContext context, TupletGrade grade, TupletRatio stated, Strictness strictness
    ) {
        if (grade.number() == stated.normalNotes()) {
            return invalid(Reason.BAD_RATIO);
        }

        var divisionFailure = divisionFailure(context.writtenTicks(), grade);

        if (divisionFailure != null) {
            return invalid(divisionFailure);
        }

        if (context.writtenTicks() / grade.number() != stated.noteValue().ticks()) {
            return invalid(Reason.NOT_NOTATABLE);
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
            return Reason.NOT_NOTATABLE;
        }

        return null;
    }

    /**
     * The constraints on the span's elements rather than on its total duration, shared by the
     * derived and the stated paths. The fermata holds under either strictness — it inflates the
     * performed duration and so breaks the exact ratio whatever the tuplet says — and so does
     * the pitched-note count, since a group with nothing for the number to count is not a
     * tuplet in any document. The beat barrier and the structural boundary are editor policy
     * and hold under {@link Strictness#STRICT} only.
     *
     * @return the reason it failed, or null when it did not
     */
    private static @Nullable Reason contentFailure(SpanContext context, Strictness strictness) {
        var reasons = context.placementReasons();

        if (reasons.contains(Reason.FERMATA)) {
            return Reason.FERMATA;
        }

        if (context.pitchedNotes() < MIN_PITCHED_NOTES) {
            return Reason.TOO_FEW_NOTES;
        }

        if (strictness != Strictness.STRICT) {
            return null;
        }

        if (reasons.contains(Reason.BEAT_BARRIER)) {
            return Reason.BEAT_BARRIER;
        }

        if (reasons.contains(Reason.STRUCTURAL_BOUNDARY)) {
            return Reason.STRUCTURAL_BOUNDARY;
        }

        return null;
    }

    /**
     * A tuplet a caller hands the walk at its anchor: what the caller knows it by, where it
     * ends, and the number it prints.
     *
     * @param tuplet   the caller's own tuplet, handed back unchanged in its {@link Verdict}
     * @param endIndex the index of the tuplet's last element in the anchor's line, at or after
     *                 the anchor
     * @param grade    N, the printed tuplet number
     * @param <T>      the caller's tuplet type
     */
    public record Anchored<T>(T tuplet, int endIndex, TupletGrade grade) {}

    /**
     * The tuplets a caller asks {@link #validateFrom} to judge, looked up by anchor.
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
     * Judges every tuplet {@code tuplets} supplies at an anchor at or after {@code from}, in
     * document order. Pass the song's first element to cover the whole song. {@code tuplets}
     * supplies each tuplet's end index in the anchor's line, at or after the anchor.
     *
     * <p>This is one forward walk rather than a {@link #describeSpan} per tuplet: the beat
     * is resolved once at the starting position and carried forward, so the walk costs
     * O(elements) rather than O(tuplets × elements). Beat barriers fall out of the same
     * walk, since a barrier is precisely an element that redefines the running beat.
     *
     * <p>The walk opens one span at a time and does not look for overlap. The tuplets the
     * caller supplies therefore must not overlap: those in a line's registry never do, since
     * adding one displaces every tuplet it overlaps, and those a reader found never do, since
     * the writer cannot produce two overlapping tuplets.
     *
     * @param song       the song to walk
     * @param from       the element to start at, which must exist in {@code song}
     * @param strictness how much of the rule set to enforce
     * @param tuplets    the tuplets to judge, by anchor
     * @param <T>        the caller's tuplet type
     * @return one verdict per tuplet {@code tuplets} supplied at an element the walk reached
     *     outside any span already open, in document order
     * @invariant a verdict's span runs from the element {@code tuplets} supplied the tuplet at
     *     to its {@link Anchored#endIndex()}
     */
    public static <T> List<Verdict<T>> validateFrom(
        Song song, ElementLocation from, Strictness strictness, AnchoredTuplets<T> tuplets
    ) {
        var verdicts = new ArrayList<Verdict<T>>();
        var runningBeat = song.beatUnitAt(from.lineIndex(), from.elementIndex());
        var firstIndex = from.elementIndex();

        for (var lineIndex = from.lineIndex(); lineIndex < song.lineCount(); lineIndex++) {
            var line = song.getLine(lineIndex);
            OpenTuplet<T> open = null;

            for (var index = firstIndex; index < line.elementCount(); index++) {
                var element = line.getElement(index);

                if (open == null) {
                    var definedBeat = TempoResolver.beatDefinedAt(element);

                    if (definedBeat != null) {
                        runningBeat = definedBeat;
                    }

                    var anchored = tuplets.anchoredAt(line, index);

                    // The running beat already includes the anchor's own definition, which is
                    // the start SpanMeasure requires.
                    if (anchored != null) {
                        var span = new ElementRange(index, anchored.endIndex());
                        open = new OpenTuplet<>(anchored, new SpanMeasure(span, runningBeat));
                    }
                }

                if (open != null) {
                    var measure = open.measure();
                    measure.add(element, index);

                    if (measure.endsAt(index)) {
                        verdicts.add(open.verdict(lineIndex, strictness));
                        runningBeat = measure.runningBeat();
                        open = null;
                    }
                }
            }

            firstIndex = 0;
        }

        return verdicts;
    }

    /**
     * A tuplet the caller supplied and the verdict the walk reached for it. The measured span
     * rides along so a caller that wants to judge the tuplet a second way — the load pass
     * re-judges one carrying a stated ratio — can do so without walking the beat again.
     *
     * @param lineIndex the index of the line holding the tuplet
     * @param span      the elements the walk judged, from the anchor on — see
     *                  {@link #validateFrom} for where it ends
     * @param tuplet    the tuplet as the caller supplied it
     * @param context   the span as the walk measured it
     * @param result    the verdict under the walk's strictness, with a derived ratio
     * @param <T>       the caller's tuplet type
     */
    public record Verdict<T>(int lineIndex, ElementRange span, T tuplet, SpanContext context, Result result) {}

    /**
     * The tuplet the forward walk currently has open, and its span measured so far.
     *
     * @param anchored the tuplet as the caller supplied it
     * @param measure  the span from the tuplet's anchor to its end index
     * @param <T>      the caller's tuplet type
     */
    private record OpenTuplet<T>(Anchored<T> anchored, SpanMeasure measure) {

        /** The verdict on this tuplet, once {@link #measure} has taken its last element. */
        private Verdict<T> verdict(int lineIndex, Strictness strictness) {
            var context = measure.context();

            return new Verdict<>(
                lineIndex, measure.span(), anchored.tuplet(), context,
                validate(context, anchored.grade(), strictness));
        }
    }

    /**
     * Measures one span as its elements are handed to it in order, from the anchor to the end,
     * so {@link #describeSpan} and the forward walk of {@link #validateFrom} measure a span the
     * same way.
     *
     * <p>Grace notes ride along and add no written duration; rests count toward the duration
     * exactly as notes do, and the non-duration elements a span may contain all have a zero
     * default duration.
     */
    private static final class SpanMeasure {

        private final ElementRange span;
        private final BeatUnit beat;
        private final EnumSet<Reason> placementReasons = EnumSet.noneOf(Reason.class);
        private BeatUnit runningBeat;
        private int writtenTicks = 0;
        private int pitchedNotes = 0;

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
            if (index != span.begin() && TempoResolver.splitsBeat(element, runningBeat)) {
                placementReasons.add(Reason.BEAT_BARRIER);
            }

            var definedBeat = TempoResolver.beatDefinedAt(element);

            if (definedBeat != null) {
                runningBeat = definedBeat;
            }

            var type = element.getType();

            if (!type.isGraceNote()) {
                writtenTicks += element.writtenDuration();
            }

            if (type.isPitchedNote()) {
                pitchedNotes++;
            }

            if (element.findAttachment(FermataAttachment.class) != null) {
                placementReasons.add(Reason.FERMATA);
            }

            if (isStructuralBoundary(type, index, span.end())) {
                placementReasons.add(Reason.STRUCTURAL_BOUNDARY);
            }
        }

        /** Whether the element at {@code index}, once taken, is the span's last. */
        private boolean endsAt(int index) {
            return index == span.end();
        }

        private ElementRange span() {
            return span;
        }

        /** The beat in effect after the last element taken. */
        private BeatUnit runningBeat() {
            return runningBeat;
        }

        private SpanContext context() {
            return new SpanContext(writtenTicks, beat, pitchedNotes, placementReasons);
        }
    }

    /**
     * Whether the element at {@code index} breaks the span in two. A boundary at the very
     * end of the span breaks nothing, because every note of the span lies before it:
     * selections already sweep in a breath mark that immediately follows the last note
     * (commit 6bcc1c6b), and a span running to the end of a line ends on the barline the
     * model maintains there. Treating either as a boundary would disable the tuplet
     * control on ordinary selections.
     *
     * @param endIndex the index the span ends on
     */
    private static boolean isStructuralBoundary(ElementType type, int index, int endIndex) {
        return index != endIndex && (type.isBar() || type.isBreathMark());
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
    private static NavigableSet<Integer> conventionalSpans(int noteValueTicks, BeatUnit beat) {
        var spans = new TreeSet<Integer>();
        var beatTicks = beat.ticks();

        for (var ticks = beat.binaryDivisionRootTicks(); ticks >= SHORTEST_SPAN_TICKS; ticks /= 2) {
            addSpan(spans, ticks, noteValueTicks);
        }

        for (var multiple = 1; multiple <= MAX_BEAT_MULTIPLE; multiple *= 2) {
            addSpan(spans, beatTicks * multiple, noteValueTicks);
        }

        return spans;
    }

    private static void addSpan(NavigableSet<? super Integer> spans, int spanTicks, int noteValueTicks) {
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
        return new Result.Invalid(reason);
    }
}
