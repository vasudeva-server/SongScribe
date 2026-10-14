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
package songscribe.io.musicxml;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.audiveris.proxymusic.Attributes;
import org.audiveris.proxymusic.BackwardForward;
import org.audiveris.proxymusic.BarStyle;
import org.audiveris.proxymusic.Barline;
import org.audiveris.proxymusic.ClefSign;
import org.audiveris.proxymusic.Direction;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.Print;
import org.audiveris.proxymusic.RightLeftMiddle;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.ScorePartwise.Part.Measure;
import org.audiveris.proxymusic.StartStopDiscontinue;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.smufl.SMuFLGlyph;

/**
 * Builds the {@code <measure>} objects one {@link Line} contributes to a
 * {@link ScorePartwise}, together with every {@code <barline>} and the
 * {@code <ending>} children folded onto them.
 *
 * <p><b>Where a {@code <key>} goes.</b> It lives in the {@code <attributes>} of the measure the
 * key takes effect in: a {@link Line} whose {@link Line#getRunningKey()} differs from the running
 * key emits one into that line's first measure, and a {@link KeyChangeElement} emits one into
 * the measure the barline before it opened. The cautionary key signature drawn at the end of a
 * system is <em>rendering only</em> and is never written — the reader re-derives it from the next
 * line's key.
 */
final class MeasureBuilder {

    private static final BigInteger TREBLE_CLEF_LINE = BigInteger.valueOf(2);

    private MeasureBuilder() {}

    // -------------------------------------------------------------------------
    // Running score state
    // -------------------------------------------------------------------------

    /**
     * Measure numbers are continuous across lines. A MusicXML key persists until restated, so
     * lines that keep the running key emit nothing.
     */
    static final class ScoreState {

        private int nextMeasureNumber = MusicXmlUnits.FIRST_MEASURE_NUMBER;
        private Key runningKey;

        private ScoreState(Key runningKey) {
            this.runningKey = runningKey;
        }

        boolean isAtFirstMeasure() {
            return nextMeasureNumber == MusicXmlUnits.FIRST_MEASURE_NUMBER;
        }

        private int takeNextMeasureNumber() {
            return nextMeasureNumber++;
        }
    }

    /**
     * The key is initialized to the value measure 1's {@code <attributes>} carries.
     */
    static ScoreState newScoreState(Song song) {
        return new ScoreState(song.getStartingKey());
    }

    // -------------------------------------------------------------------------
    // Per-element graph handles
    // -------------------------------------------------------------------------

    /**
     * The nodes one {@link StaffElement} contributed. One per element of the line, in element
     * order; an element that produced nothing leaves every field null.
     */
    static final class ElementOutput {

        @Nullable private Barline leftBarline = null;

        @Nullable private Barline rightBarline = null;

        @Nullable private Measure measureAfter = null;

        @Nullable private Measure measure = null;

        @Nullable private Object head = null;

        @Nullable private Note note = null;

        @Nullable private Direction annotation = null;

        /** Null when the element produced no content. */
        @Nullable Measure measure() {
            return measure;
        }

        /**
         * Placing a wedge before this node puts it after the tempo and metric-modulation
         * directions and before the annotation. Null when the element produced no note.
         */
        @Nullable Object wedgeAnchor() {
            return annotation != null ? annotation : note;
        }
    }

    // -------------------------------------------------------------------------
    // Entry points
    // -------------------------------------------------------------------------

    /**
     * What one line contributed to the score. The emitted list holds only the elements that
     * produced a {@code <note>}: the next note is not the next element, since a breath mark or
     * a barline produces none.
     */
    record LineOutput(
        Line line,
        int lineIndex,
        List<Measure> measures,
        List<ElementOutput> elements,
        List<StaffElement> emitted
    ) {}

    /**
     * Every line-starting measure carries {@code <print new-system="yes">}, so
     * {@code new-system="yes"} always starts a new line on read. The song-level tempo
     * {@code <direction>} is not emitted here.
     */
    static LineOutput buildLine(BuildContext context, ScoreState state, Line line, int lineIndex) {
        var measures = new ArrayList<Measure>();
        var isFirstMeasure = state.isAtFirstMeasure();
        var measure = openMeasure(context, state, measures);

        measure.getNoteOrBackupOrForward().add(buildNewSystemPrint(context));

        if (isFirstMeasure) {
            measure.getNoteOrBackupOrForward().add(buildAttributes(context));
        } else {
            var lineKey = line.getRunningKey();

            if (!lineKey.equals(state.runningKey)) {
                measure.getNoteOrBackupOrForward()
                    .add(buildKeyOnlyAttributes(context, state.runningKey, lineKey));
                state.runningKey = lineKey;
            }
        }

        var elements = line.getElements();
        var outputs = new ArrayList<ElementOutput>(elements.size());
        var emitted = new ArrayList<StaffElement>();

        for (var i = 0; i < elements.size(); i++) {
            outputs.add(new ElementOutput());
        }

        // True once a real right barline has closed the line's final measure. Only the line's
        // last element can leave it true — every earlier barline opens the next measure — so
        // this decides one thing: whether the line break needs an invisible right barline of
        // its own, or would only add a spurious empty measure.
        var lineEndsWithBarline = false;

        for (var i = 0; i < elements.size(); i++) {
            var element = elements.get(i);
            var type = element.getType();
            var output = outputs.get(i);
            var isLastElement = i == elements.size() - 1;

            if (type == ElementType.REPEAT_LEFT) {
                // REPEAT_LEFT opens a new measure: the forward-repeat barline is a left
                // barline, not a right one. An invisible right barline closes the current
                // measure so the reader can still see the boundary. An ending anchored (or
                // ended) on the REPEAT_LEFT rides on the forward-left barline.
                appendAnnotation(context, measure, element);
                measure.getNoteOrBackupOrForward().add(buildInvisibleBarline(context, BarlineStyleMapping.LOCATION_RIGHT));
                measure = openMeasure(context, state, measures);
                output.leftBarline = openForwardRepeat(context, measure);
            } else if (type == ElementType.REPEAT_LEFT_RIGHT) {
                // REPEAT_LEFT_RIGHT straddles a measure boundary: a backward-repeat right
                // barline closes the current measure and a forward-repeat left barline opens
                // the next one. The reader reconstructs the REPEAT_LEFT_RIGHT from the pair.
                appendAnnotation(context, measure, element);
                output.rightBarline = buildBarlineFor(context, ElementType.REPEAT_RIGHT);
                measure.getNoteOrBackupOrForward().add(output.rightBarline);
                measure = openMeasure(context, state, measures);
                output.leftBarline = openForwardRepeat(context, measure);
            } else if (type.isBar()) {
                // Every other barline/repeat type closes the current measure with a right
                // barline. REPEAT_LEFT and REPEAT_LEFT_RIGHT are handled above, so every
                // remaining type has a forward-map entry; one that ever does not is passed
                // over rather than failing the save.
                var entry = BarlineStyleMapping.forElementType(type);

                if (entry == null) {
                    continue;
                }

                appendAnnotation(context, measure, element);
                output.rightBarline = buildBarline(context, entry.location(), entry.barStyle(), entry.repeatDirection());
                measure.getNoteOrBackupOrForward().add(output.rightBarline);

                if (isLastElement) {
                    lineEndsWithBarline = true;
                } else {
                    measure = openMeasure(context, state, measures);
                    output.measureAfter = measure;
                }
            } else if (type.isKeyChange()) {
                // The key takes effect here, so its <key> goes in this measure's <attributes>.
                // KeyChangeElement's position invariant puts a barline immediately before this
                // element, and that barline opened the measure we are in — so the <attributes>
                // lands at the head of the measure, which is where the schema wants it.
                //
                // The cast is unguarded on purpose: every KEY_CHANGE element is a
                // KeyChangeElement, and one that is not must fail here rather than fall into
                // the branch below, which appends nothing for an element with no <note> form and
                // would drop the key change without a word.
                var newKey = ((KeyChangeElement) element).getKey();
                measure.getNoteOrBackupOrForward()
                    .add(buildKeyOnlyAttributes(context, line.keyBefore(i), newKey));
                state.runningKey = newKey;
                output.measure = measure;
            } else {
                appendElementContent(context, measure, line, i, output);

                if (output.note != null) {
                    emitted.add(element);
                }
            }
        }

        if (!lineEndsWithBarline) {
            // The line break ends the measure. An invisible right barline marks it so the
            // reader can reconstruct the line boundary without inserting a barline element.
            measure.getNoteOrBackupOrForward().add(buildInvisibleBarline(context, BarlineStyleMapping.LOCATION_RIGHT));
        }

        attachEndings(context, line, outputs);
        return new LineOutput(line, lineIndex, measures, outputs, emitted);
    }

    /**
     * Exposed for the empty-song case, which contributes one measure belonging to no line.
     */
    static Measure buildMeasure(BuildContext context, int measureNumber) {
        var measure = context.factory().createScorePartwisePartMeasure();
        measure.setNumber(Integer.toString(measureNumber));
        return measure;
    }

    /**
     * Measure 1's {@code <key>} never carries a {@code <cancel>}: nothing precedes it, so there
     * is no signature for it to cancel.
     */
    static Attributes buildAttributes(BuildContext context) {
        var factory = context.factory();
        var attributes = factory.createAttributes();
        attributes.setDivisions(BigDecimal.valueOf(MusicXmlUnits.DIVISIONS));
        attributes.getKey().add(buildKey(context, null, context.song().getStartingKey()));

        // SongScribe songs are unmetered, so the time signature is present but not printed.
        var time = factory.createTime();
        time.setPrintObject(YesNo.NO);
        time.setSenzaMisura("");
        attributes.getTime().add(time);

        var clef = factory.createClef();
        clef.setSign(ClefSign.G);
        clef.setLine(TREBLE_CLEF_LINE);
        attributes.getClef().add(clef);

        return attributes;
    }

    // -------------------------------------------------------------------------
    // Measure and attribute construction
    // -------------------------------------------------------------------------

    private static Measure openMeasure(
        BuildContext context, ScoreState state, List<Measure> measures) {
        var measure = buildMeasure(context, state.takeNextMeasureNumber());
        measures.add(measure);
        return measure;
    }

    private static Attributes buildKeyOnlyAttributes(BuildContext context, Key previousKey, Key newKey) {
        var attributes = context.factory().createAttributes();
        attributes.getKey().add(buildKey(context, previousKey, newKey));
        return attributes;
    }

    /**
     * <b>The {@code <cancel>} comes from the policy, not from a second copy of it.</b>
     * {@link Key#accidentalsFrom} promises that a change which cancels puts its naturals first, so
     * whether a cancellation is owed is answered by what the change draws.
     *
     * @param previousKey null at measure 1, where nothing precedes the key and no cancellation
     *                    is possible
     */
    private static org.audiveris.proxymusic.Key buildKey(
        BuildContext context, @Nullable Key previousKey, Key newKey) {

        var factory = context.factory();
        var key = factory.createKey();

        if (previousKey != null) {
            var drawn = newKey.accidentalsFrom(previousKey);

            if (!drawn.isEmpty() && drawn.getFirst().glyph() == SMuFLGlyph.ACCIDENTAL_NATURAL) {
                // <cancel>'s value is the signed fifths of the key being cancelled, not of the
                // new one.
                var cancel = factory.createCancel();
                cancel.setValue(BigInteger.valueOf(previousKey.fifths()));
                key.setCancel(cancel);
            }
        }

        key.setFifths(BigInteger.valueOf(newKey.fifths()));

        // Written on every <key> and never read back: nothing in the model stores a mode, and
        // only SongScribe-authored files get past the provenance gate, so an incoming <mode> is
        // always the one this writer emitted. It is written because output is for everyone — a
        // foreign consumer reads it.
        key.setMode(MusicXmlTags.MODE_MAJOR);

        return key;
    }

    private static Print buildNewSystemPrint(BuildContext context) {
        var print = context.factory().createPrint();
        print.setNewSystem(YesNo.YES);
        return print;
    }

    /**
     * A barline element's annotation must be emitted <em>before</em> the barline: a
     * forward-repeat left barline is required to be its measure's first child, so nothing may
     * be emitted between a right barline and the forward-left barline that follows it.
     */
    private static void appendAnnotation(BuildContext context, Measure measure, StaffElement element) {
        addIfPresent(
            measure.getNoteOrBackupOrForward(),
            DirectionBuilder.buildElementAnnotationDirection(context, element));
    }

    /**
     * An element with no MusicXML note form appends nothing at all, directions included.
     */
    private static void appendElementContent(
        BuildContext context, Measure measure, Line line, int elementIndex, ElementOutput output) {
        var element = line.getElement(elementIndex);
        var pmNote = NoteBuilder.buildNote(context, line, elementIndex);

        if (pmNote == null) {
            return;
        }

        var items = measure.getNoteOrBackupOrForward();
        var headIndex = items.size();
        var annotation = DirectionBuilder.buildElementAnnotationDirection(context, element);

        addIfPresent(items, DirectionBuilder.buildElementTempoDirection(context, element));
        addIfPresent(items, DirectionBuilder.buildElementMetricModulationDirection(context, element));
        addIfPresent(items, annotation);

        LyricBuilder.addLyrics(context, element, pmNote);
        items.add(pmNote);

        output.measure = measure;
        // The earliest of the nodes just appended — the first direction, or the note when the
        // element carries none. A note-anchored volta start is inserted ahead of it.
        output.head = items.get(headIndex);
        output.note = pmNote;
        output.annotation = annotation;
    }

    private static void addIfPresent(List<Object> items, @Nullable Direction direction) {
        if (direction != null) {
            items.add(direction);
        }
    }

    // -------------------------------------------------------------------------
    // Barline construction
    // -------------------------------------------------------------------------

    /**
     * MusicXML requires a forward-repeat barline to open its measure.
     */
    private static Barline openForwardRepeat(BuildContext context, Measure measure) {
        var barline = buildBarlineFor(context, ElementType.REPEAT_LEFT);
        measure.getNoteOrBackupOrForward().add(0, barline);
        return barline;
    }

    /**
     * The type must have a forward-map entry; the caller decomposes {@code REPEAT_LEFT_RIGHT},
     * the only one without, into {@code REPEAT_RIGHT} and {@code REPEAT_LEFT}.
     */
    private static Barline buildBarlineFor(BuildContext context, ElementType type) {
        var entry = BarlineStyleMapping.forElementType(type);

        if (entry == null) {
            throw new IllegalStateException("No MusicXML barline mapping for " + type);
        }

        return buildBarline(context, entry.location(), entry.barStyle(), entry.repeatDirection());
    }

    private static Barline buildInvisibleBarline(BuildContext context, String location) {
        return buildBarline(context, location, BarlineStyleMapping.BAR_STYLE_NONE, null);
    }

    private static Barline buildBarline(
        BuildContext context, String location, String barStyle, @Nullable String repeatDirection) {
        var factory = context.factory();
        var barline = factory.createBarline();
        barline.setLocation(RightLeftMiddle.fromValue(location));

        var style = factory.createBarStyleColor();
        style.setValue(BarStyle.fromValue(barStyle));
        barline.setBarStyle(style);

        if (repeatDirection != null) {
            var repeat = factory.createRepeat();
            repeat.setDirection(BackwardForward.fromValue(repeatDirection));
            barline.setRepeat(repeat);
        }

        return barline;
    }

    // -------------------------------------------------------------------------
    // Endings
    // -------------------------------------------------------------------------

    /**
     * One SongScribe {@link songscribe.dom.Ending} expands to two MusicXML voltas: the anchor
     * carries {@code [1 start]}, the split carries {@code [1 stop]} and {@code [2 start]}, and
     * the end carries {@code [2 stop]}. Every ending has a split.
     */
    private static void attachEndings(BuildContext context, Line line, List<ElementOutput> outputs) {
        for (var ending : line.spanRegistry().findEndings()) {
            var anchorIndex = ending.requireAnchorElementIndex();
            var endIndex = ending.requireEndElementIndex();

            attachEndingAnchor(context, outputs.get(anchorIndex), ending);
            attachEndingEnd(context, outputs.get(endIndex), ending);
            attachEndingSplit(context, outputs.get(ending.getSplitIndex()));
        }
    }

    /**
     * The start volta is the one bracket the reader recovers the offset from.
     */
    private static void attachEndingAnchor(BuildContext context, ElementOutput output, Ending ending) {
        var anchorType = ending.getAnchorElement().getType();
        var host = anchorType.isDuration() ? leftBarlineBeforeContent(context, output) : output.rightBarline;
        var start = attachEnding(context, host, MusicXmlTags.NUMBER_1, StartStopDiscontinue.START);

        if (start != null) {
            start.setRelativeY(MusicXmlUnits.shiftTenths(ending.getUserYOffsetSs()));
        }
    }

    private static void attachEndingEnd(BuildContext context, ElementOutput output, Ending ending) {
        var endType = ending.getEndElement().getType();
        var type = ending.isSecondBracketClosed() ? StartStopDiscontinue.STOP : StartStopDiscontinue.DISCONTINUE;

        if (endType == ElementType.REPEAT_LEFT) {
            attachEnding(context, output.leftBarline, MusicXmlTags.NUMBER_2, type);
        } else if (endType.isDuration()) {
            attachEnding(context, rightBarlineAfterContent(context, output), MusicXmlTags.NUMBER_2, type);
        } else {
            attachEnding(context, output.rightBarline, MusicXmlTags.NUMBER_2, type);
        }
    }

    private static void attachEndingSplit(BuildContext context, ElementOutput output) {
        attachEnding(context, output.rightBarline, MusicXmlTags.NUMBER_1, StartStopDiscontinue.STOP);

        var left = output.leftBarline;
        var measureAfter = output.measureAfter;

        if (left == null && measureAfter != null) {
            left = buildInvisibleBarline(context, BarlineStyleMapping.LOCATION_LEFT);
            measureAfter.getNoteOrBackupOrForward().add(0, left);
            output.leftBarline = left;
        }

        attachEnding(context, left, MusicXmlTags.NUMBER_2, StartStopDiscontinue.START);
    }

    /**
     * Returns {@code null} when the element produced no content, which leaves the volta
     * start unattached.
     */
    @Nullable
    private static Barline leftBarlineBeforeContent(BuildContext context, ElementOutput output) {
        if (output.leftBarline != null) {
            return output.leftBarline;
        }

        var measure = output.measure;
        var head = output.head;

        if (measure == null || head == null) {
            return null;
        }

        var items = measure.getNoteOrBackupOrForward();
        var position = positionOf(items, head);

        if (position == null) {
            return null;
        }

        var barline = buildInvisibleBarline(context, BarlineStyleMapping.LOCATION_LEFT);
        items.add(position, barline);
        output.leftBarline = barline;
        return barline;
    }

    /**
     * When the note is already followed by an invisible right barline, the volta rides on that
     * one rather than adding a second.
     */
    @Nullable
    private static Barline rightBarlineAfterContent(BuildContext context, ElementOutput output) {
        if (output.rightBarline != null) {
            return output.rightBarline;
        }

        var measure = output.measure;
        var tail = output.note;

        if (measure == null || tail == null) {
            return null;
        }

        var items = measure.getNoteOrBackupOrForward();
        var position = positionOf(items, tail);

        if (position == null) {
            return null;
        }

        var next = position + 1 < items.size() ? items.get(position + 1) : null;
        Barline barline;

        if (next instanceof Barline following && isInvisibleRightBarline(following)) {
            barline = following;
        } else {
            barline = buildInvisibleBarline(context, BarlineStyleMapping.LOCATION_RIGHT);
            items.add(position + 1, barline);
        }

        output.rightBarline = barline;
        return barline;
    }

    private static boolean isInvisibleRightBarline(Barline barline) {
        var style = barline.getBarStyle();
        return barline.getLocation() == RightLeftMiddle.RIGHT && style != null && style.getValue() == BarStyle.NONE;
    }

    /**
     * MusicXML allows at most one {@code <ending>} per {@code <barline>}, so a second volta
     * marker landing on the same barline is dropped.
     */
    private static org.audiveris.proxymusic.@Nullable Ending attachEnding(
        BuildContext context, @Nullable Barline host, String number, StartStopDiscontinue type) {
        if (host == null || host.getEnding() != null) {
            return null;
        }

        var ending = context.factory().createEnding();
        ending.setNumber(number);
        ending.setType(type);
        host.setEnding(ending);

        return ending;
    }

    /**
     * Returns {@code null} when {@code node} is not there. Compared by identity, not by
     * {@code equals}.
     */
    static @Nullable Integer positionOf(List<Object> items, Object node) {
        for (var i = 0; i < items.size(); i++) {
            if (items.get(i) == node) {
                return i;
            }
        }

        return null;
    }
}
