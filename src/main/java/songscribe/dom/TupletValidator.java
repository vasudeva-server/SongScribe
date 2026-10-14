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
 * <p>A tuplet means <em>N</em> notes in the time of <em>M</em> notes of written value <em>V</em>.
 * Only <em>N</em> comes from outside; <em>V</em> is {@code S / N} for the span's summed written
 * duration <em>S</em>, never the gcd of the durations (which would renumber a quarter plus two
 * eighths under a 3 into a sextuplet), and <em>M</em> is derived from <em>V</em> and the beat.
 *
 * <p>Written duration comes from {@link StaffElement#writtenDuration()}, never
 * {@code getDuration()}, which inflates a fermata; a span with a fermata is excluded outright, so
 * playback and validation sum the same values.
 *
 * <p>A power-of-two <em>N</em>/<em>M</em> is redundancy, a renotation rather than a re-timing.
 * Redundancy is a property of the derivation: a derived <em>M</em> that says nothing drops the
 * tuplet, while a stated <em>M</em> that is otherwise valid is kept even if unconventional.
 * {@link #validateStated} is the path for a stated ratio, {@link #validate} for a derived one.
 *
 * <p>{@link Song#beatUnitAt} is uncached and walks every element before the anchor, so judging
 * many tuplets of one song goes through {@link #sitesFrom}, which carries the beat forward.
 */
public final class TupletValidator {

    private static final int MAX_BEAT_MULTIPLE = 8;

    private static final int SHORTEST_SPAN_TICKS = NoteValue.THIRTY_SECOND.ticks();

    private TupletValidator() {
    }

    /**
     * The beat barrier and the bar or breath mark inside the span hold under {@link #STRICT}
     * only.
     */
    public enum Strictness {

        STRICT,

        /** For loading: an existing document is not improved by dropping a tuplet. */
        LENIENT
    }

    private enum Reason {
        EMPTY_SPAN,
        NOT_DIVISIBLE,
        SHARE_NOT_NOTATABLE,
        STATED_VALUE_MISMATCH,
        NUMBER_EQUALS_NORMAL_NOTES,
        NO_CONVENTIONAL_SPAN,
        POWER_OF_TWO_RATIO,
        FERMATA,
        BEAT_BARRIER,
        BAR_OR_BREATH_MARK,
        TOO_FEW_NOTES;

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

    enum PlacementFault {
        FERMATA,
        BEAT_BARRIER,
        BAR_OR_BREATH_MARK;

        private Reason reason() {
            return switch (this) {
                case FERMATA -> Reason.FERMATA;
                case BEAT_BARRIER -> Reason.BEAT_BARRIER;
                case BAR_OR_BREATH_MARK -> Reason.BAR_OR_BREATH_MARK;
            };
        }
    }

    sealed interface Result {

        record Valid(TupletNormalTime ratio) implements Result {
        }

        record Invalid(String reasonKey) implements Result {
        }
    }

    /**
     * @param writtenTicks S, the summed written duration of the span's non-grace elements
     * @param beat         B, the beat in effect at the span's anchor, including a beat the
     *                     anchor itself defines
     */
    record SpanContext(
        int writtenTicks, BeatUnit beat, boolean enoughPitchedNotes, Set<PlacementFault> placementFaults
    ) {

        SpanContext {
            placementFaults = Set.copyOf(placementFaults);
        }
    }

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

    private static @Nullable Reason divisionFailure(int writtenTicks, TupletGrade grade) {
        if (writtenTicks == 0) {
            return Reason.EMPTY_SPAN;
        }

        if (writtenTicks % grade.number() != 0) {
            return Reason.NOT_DIVISIBLE;
        }

        return null;
    }

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
     * @param endIndex in the anchor's line, at or after the anchor
     */
    public record Anchored<T>(T tuplet, int endIndex) {}

    @FunctionalInterface
    public interface AnchoredTuplets<T> {

        /**
         * @return {@code null} when no tuplet is anchored at that element
         */
        @Nullable Anchored<T> anchoredAt(Line line, int index);
    }

    public record Sited<T>(T tuplet, Tuplet.Site site) {}

    /**
     * Pass the song's first element to cover the whole song.
     *
     * @param from must exist in {@code song}
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

    private static final class SpanMeasure {

        private final ElementRange span;
        private final BeatUnit beat;
        private final EnumSet<PlacementFault> placementFaults = EnumSet.noneOf(PlacementFault.class);
        private BeatUnit runningBeat;
        private int writtenTicks = 0;

        /**
         * @param beatAtAnchor including a beat the anchor itself defines: a barrier on the anchor
         *                     defines the tuplet's beat rather than splitting it
         */
        private SpanMeasure(ElementRange span, BeatUnit beatAtAnchor) {
            this.span = span;
            beat = beatAtAnchor;
            runningBeat = beatAtAnchor;
        }

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
         * A bar or breath mark at the very end of the span divides nothing; counting it would
         * disable the tuplet control on ordinary selections, which sweep in a trailing breath
         * mark and end on the line's maintained barline.
         */
        private boolean isBarOrBreathMarkBeforeEnd(ElementType type, int index) {
            return index != span.end() && (type.isBar() || type.isBreathMark());
        }

        private SpanContext context(boolean enoughPitchedNotes) {
            return new SpanContext(writtenTicks, beat, enoughPitchedNotes, placementFaults);
        }
    }

    /**
     * A span of a single V-unit is excluded, which makes a compound duplet infer 2:3 rather than
     * the meaningless 2:1.
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
     * The fallback to the smallest span above N, for a compound-beat duplet, is deliberately not
     * widened under {@link Strictness#LENIENT}: widening would invent a re-timing nobody wrote
     * (three quavers under a dotted-crotchet beat would load as 3:6 and play at half speed).
     *
     * @return null when the beat offers no span at all
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
