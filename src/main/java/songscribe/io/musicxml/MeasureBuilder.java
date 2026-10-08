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
 * <p>A measure is an object created and appended to. The one running value across measures is
 * the measure counter, which lives in {@link ScoreState} because measure numbers are continuous
 * across lines. Every ending is attached to the {@code Barline} object it belongs to. Where
 * MusicXML offers no host — {@code <ending>} is only ever a child of {@code <barline>} — the
 * barline is inserted positionally with {@code items.add(index, barline)}. A note-terminated
 * ending reuses the barline that follows its boundary note when that barline is already an
 * invisible right barline; see {@link #rightBarlineAfterContent}.
 *
 * <p>The key carry is sequential state: a MusicXML key persists until restated, so which lines
 * and which mid-line key changes emit a {@code <key>} lives in {@link ScoreState} alongside the
 * measure counter.
 *
 * <p><b>Where a {@code <key>} goes.</b> It lives in the {@code <attributes>} of the measure the
 * key takes effect in: a {@link Line} whose {@link Line#getRunningKey()} differs from the running
 * key emits one into that line's first measure, and a {@link KeyChangeElement} emits one into
 * the measure the barline before it opened. The cautionary key signature drawn at the end of a
 * system is <em>rendering only</em> and is never written — the reader re-derives it from the next
 * line's key.
 *
 * <p><b>One owner for the loop.</b> This class decides where the measure boundaries fall
 * <em>and</em> builds the notes and directions between them, calling {@link NoteBuilder},
 * {@link DirectionBuilder} and {@link LyricBuilder} directly. The two are one walk over the
 * line's elements, so the nodes an element produced are known to the code that appended them
 * and are recorded once, in {@link ElementOutput} — rather than deduced afterwards by measuring
 * the child list, and rather than recorded a second time by whoever else needs them.
 */
final class MeasureBuilder {

    /** The {@code <line>} of a treble {@code <clef>}: the G sits on the second staff line. */
    private static final BigInteger TREBLE_CLEF_LINE = BigInteger.valueOf(2);

    private MeasureBuilder() {}

    // -------------------------------------------------------------------------
    // Running score state
    // -------------------------------------------------------------------------

    /**
     * The two values a line-by-line build genuinely carries forward: the measure counter
     * (measure numbers are continuous across lines) and the key currently in
     * effect.
     *
     * <p>Measure 1 emits the key the song starts in through the full {@code <attributes>} block;
     * every later key change — a line whose running key differs from this value, or a mid-line
     * {@link KeyChangeElement} — emits a key-only {@code <attributes>} and advances it. A
     * MusicXML key persists until restated, so lines that keep the running key emit nothing and
     * the reader carries it forward.
     */
    static final class ScoreState {

        private int nextMeasureNumber = MusicXmlUnits.FIRST_MEASURE_NUMBER;
        private Key runningKey;

        private ScoreState(Key runningKey) {
            this.runningKey = runningKey;
        }

        /** Whether the next measure opened will be the score's first. */
        boolean isAtFirstMeasure() {
            return nextMeasureNumber == MusicXmlUnits.FIRST_MEASURE_NUMBER;
        }

        private int takeNextMeasureNumber() {
            return nextMeasureNumber++;
        }
    }

    /**
     * Creates the running state for a whole-song build, with the key initialized to the
     * key {@code song} starts in — the same value measure 1's {@code <attributes>} carries, so the
     * seed and the first written {@code <key>} cannot disagree.
     *
     * @param song the song about to be built
     * @return the fresh running state, positioned before measure 1
     */
    static ScoreState newScoreState(Song song) {
        return new ScoreState(song.getStartingKey());
    }

    // -------------------------------------------------------------------------
    // Per-element graph handles
    // -------------------------------------------------------------------------

    /**
     * The nodes one {@link StaffElement} contributed, recorded by the code that appended them.
     * There is one per element of the line, in element order, and an element that produced
     * nothing leaves every field null.
     *
     * <p>This is the write-side counterpart of the read path's {@code ChildSite}: it is what
     * lets the ending logic here, and the hairpin pass in {@code ScorePartwiseBuilder}, reach
     * an element's nodes without walking the heterogeneous
     * {@code Measure.getNoteOrBackupOrForward()} list looking for them.
     */
    static final class ElementOutput {

        /** A left barline hosting this element's ending starts, once one exists. */
        @Nullable private Barline leftBarline = null;

        /** The right barline closing this element's measure, when the element is a barline. */
        @Nullable private Barline rightBarline = null;

        /** The measure opened immediately after this element's right barline, if any. */
        @Nullable private Measure measureAfter = null;

        /** The measure this element's content was appended to. */
        @Nullable private Measure measure = null;

        /** The first node appended for this element — its earliest direction, or its note. */
        @Nullable private Object head = null;

        /** The {@code <note>} built for this element, which is the last node it contributed. */
        @Nullable private Note note = null;

        /** This element's annotation {@code <direction>}, when it emitted one. */
        @Nullable private Direction annotation = null;

        /** The measure this element's content went into, or null when it produced none. */
        @Nullable Measure measure() {
            return measure;
        }

        /**
         * The node a wedge {@code <direction>} bound to this element must be inserted before:
         * its annotation direction when it has one, otherwise its note. That places a wedge
         * after the tempo and metric-modulation directions and before the annotation. Null when
         * the element produced no note, which
         * leaves a wedge bound to it with nothing to bind to.
         */
        @Nullable Object wedgeAnchor() {
            return annotation != null ? annotation : note;
        }
    }

    // -------------------------------------------------------------------------
    // Entry points
    // -------------------------------------------------------------------------

    /**
     * What one line contributed to the score: its measures, the per-element record of the nodes
     * built from it, and the elements that actually produced a {@code <note>}.
     *
     * <p>The emitted list is what makes "the next note" answerable without a peek. It is not the
     * same as the next element — a breath mark or a barline produces no note.
     *
     * @param line the line these measures came from
     * @param lineIndex its index within the song
     * @param measures the measures it contributed, in document order
     * @param elements one entry per element of {@code line}, in element order
     * @param emitted the elements that produced a {@code <note>}, in emission order
     */
    record LineOutput(
        Line line,
        int lineIndex,
        List<Measure> measures,
        List<ElementOutput> elements,
        List<StaffElement> emitted
    ) {}

    /**
     * Builds everything {@code line} contributes: its measures, segmented at every
     * barline/repeat element and closed at the line break, and the notes and directions inside
     * them.
     *
     * <p>One class owns the whole loop. Where the measure boundaries fall and what goes between
     * them are decided together, so the nodes an element produced are known to the code that
     * appended them rather than deduced afterwards from the measure's child list.
     *
     * <p>The first measure of the line carries {@code <print new-system="yes">} — every
     * line-starting measure does, so the reader has one uniform rule: {@code new-system="yes"}
     * always starts a new line. The song-level tempo {@code <direction>} is <em>not</em>
     * emitted here; it belongs to the score rather than to any line, and the orchestrator
     * inserts it at index 0 of the first measure, ahead of the {@code <print>}.
     *
     * @param context the shared build context
     * @param state the running measure counter and key, advanced by this call
     * @param line the line to build measures for
     * @param lineIndex the index of {@code line} within the song
     * @return what the line contributed
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
     * Creates a numbered {@code <measure>}. Exposed for the empty-song case, which contributes
     * one measure belonging to no line.
     */
    static Measure buildMeasure(BuildContext context, int measureNumber) {
        var measure = context.factory().createScorePartwisePartMeasure();
        measure.setNumber(Integer.toString(measureNumber));
        return measure;
    }

    /**
     * Builds the full {@code <attributes>} block measure 1 carries: divisions, the key the song
     * starts in, an unmetered {@code <time>} and a treble clef.
     *
     * <p>Measure 1's {@code <key>} never carries a {@code <cancel>}: nothing precedes it, so there
     * is no signature for it to cancel.
     *
     * @param context the shared build context
     * @return the {@code <attributes>} block
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

    /** Creates the next numbered measure, appends it to {@code measures} and returns it. */
    private static Measure openMeasure(
        BuildContext context, ScoreState state, List<Measure> measures) {
        var measure = buildMeasure(context, state.takeNextMeasureNumber());
        measures.add(measure);
        return measure;
    }

    /**
     * Builds the key-only {@code <attributes>} block a key change emits — at the head of the line
     * it starts, or at the head of the measure a mid-line {@link KeyChangeElement} opens.
     *
     * @param context     the shared build context
     * @param previousKey the key in effect immediately before the change
     * @param newKey      the key taking effect
     * @return an {@code <attributes>} block whose only child is the {@code <key>}
     */
    private static Attributes buildKeyOnlyAttributes(BuildContext context, Key previousKey, Key newKey) {
        var attributes = context.factory().createAttributes();
        attributes.getKey().add(buildKey(context, previousKey, newKey));
        return attributes;
    }

    /**
     * Builds the {@code <key>} for a change to {@code newKey}, with the {@code <cancel>} the
     * change owes and the {@code <mode>} every SongScribe key carries.
     *
     * <p><b>The {@code <cancel>} comes from the policy, not from a second copy of it.</b>
     * {@link Key#accidentalsFrom} promises that a change which cancels puts its naturals first, so
     * the question "is a cancellation owed?" is answered by asking what the change draws. Restating
     * the rule here — "when the key type differs" — would let this file answer a different question
     * from the one the policy answers, with both subsystems' tests still passing.
     *
     * <p>Child order ({@code cancel, fifths, mode}) comes from the generated class's
     * {@code propOrder}, so unlike {@code <lyric>}'s choice list the caller cannot get it wrong.
     *
     * @param context     the shared build context
     * @param previousKey the key in effect immediately before the change, or null at measure 1,
     *                    where nothing precedes the key and no cancellation is possible
     * @param newKey      the key taking effect
     * @return the {@code <key>} node
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
     * Appends only {@code element}'s annotation {@code <direction>}, if it has one.
     *
     * <p>A barline element's annotation must be emitted <em>before</em> the barline: a
     * forward-repeat left barline is required to be its measure's first child, so nothing may
     * be emitted between a right barline and the forward-left barline that follows it.
     */
    private static void appendAnnotation(BuildContext context, Measure measure, StaffElement element) {
        addIfPresent(
            measure.getNoteOrBackupOrForward(),
            DirectionBuilder.buildElementAnnotationDirection(context, element));
    }

    /**
     * Appends everything {@code element} contributes — its directions, then its {@code <note>}
     * — and records the nodes in {@code output}. An element with no MusicXML note form appends
     * nothing at all, directions included: that is a breath mark, already folded onto the
     * preceding note's {@code <notations>}, or a type MusicXML has no note for.
     *
     * <p>Every {@code <direction>} precedes the note it marks, because the reader binds a
     * direction to the next note. That is the binding mechanism MusicXML provides, not one the
     * graph could improve on — see {@link DirectionBuilder}.
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
     * Inserts a forward-repeat left barline as {@code measure}'s first child and returns it.
     * MusicXML requires a forward-repeat barline to open its measure, so this is one of the
     * two places a barline is manufactured rather than mapped from an element.
     */
    private static Barline openForwardRepeat(BuildContext context, Measure measure) {
        var barline = buildBarlineFor(context, ElementType.REPEAT_LEFT);
        measure.getNoteOrBackupOrForward().add(0, barline);
        return barline;
    }

    /**
     * Builds the {@code <barline>} for a barline/repeat {@link ElementType}. The type must
     * have a forward-map entry; {@code REPEAT_LEFT_RIGHT}, the only one that does not, is
     * decomposed by its caller into the {@code REPEAT_RIGHT} / {@code REPEAT_LEFT} pair.
     */
    private static Barline buildBarlineFor(BuildContext context, ElementType type) {
        var entry = BarlineStyleMapping.forElementType(type);

        if (entry == null) {
            throw new IllegalStateException("No MusicXML barline mapping for " + type);
        }

        return buildBarline(context, entry.location(), entry.barStyle(), entry.repeatDirection());
    }

    /**
     * Builds a {@code <barline>} with {@code <bar-style>none</bar-style>} — a barline that
     * marks a boundary or hosts an {@code <ending>} without being drawn.
     */
    private static Barline buildInvisibleBarline(BuildContext context, String location) {
        return buildBarline(context, location, BarlineStyleMapping.BAR_STYLE_NONE, null);
    }

    /**
     * Builds a {@code <barline>} from the string forms {@link BarlineStyleMapping} stores.
     * The generated enums parse those tokens directly, so the mapping table stays the single
     * source of truth for both directions.
     *
     * <p>{@code <ending>} and {@code <repeat>} are typed properties of {@code Barline}, so
     * JAXB emits them in schema order whatever order they are set in.
     */
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
     * Attaches every ending on {@code line} to the barlines it belongs to.
     *
     * <p>One SongScribe {@link songscribe.dom.Ending} expands to two MusicXML voltas. Volta 1
     * runs from the anchor (a plain barline, or a note or a rest) to the
     * split (a {@code REPEAT_RIGHT} or {@code REPEAT_LEFT_RIGHT}); volta 2 runs
     * from that same split to the end. So the anchor carries {@code [1 start]}, the split
     * carries both {@code [1 stop]} and {@code [2 start]}, and the end carries {@code [2
     * stop]}. Every ending has a split, so it always expands to two voltas.
     *
     * <p>Each ending is walked once and attached to the {@code Barline} objects it touches.
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
     * Attaches {@code <ending number="1" type="start">}. A note or rest anchor has
     * no barline of its own, so one is inserted immediately before it; a plain barline anchor
     * closes the previous measure as a right barline and hosts the start there.
     *
     * <p>Carries the ending's {@code userYOffsetSs} as {@code relative-y}: the start volta is
     * the one bracket the reader can recover the offset from, so the writer states it once,
     * here.
     */
    private static void attachEndingAnchor(BuildContext context, ElementOutput output, Ending ending) {
        var anchorType = ending.getAnchorElement().getType();
        var host = anchorType.isDuration() ? leftBarlineBeforeContent(context, output) : output.rightBarline;
        var start = attachEnding(context, host, MusicXmlTags.NUMBER_1, StartStopDiscontinue.START);

        if (start != null) {
            start.setRelativeY(MusicXmlUnits.shiftTenths(ending.getUserYOffsetSs()));
        }
    }

    /**
     * Attaches the volta-2 close. A barline or repeat end hosts it on its own barline; a note
     * end has no barline, so it rides on a right barline after the note. The type
     * is {@code type="stop"} when {@link Ending#isSecondBracketClosed()}, and
     * {@code type="discontinue"} — an open bracket — otherwise.
     */
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

    /**
     * Attaches the split pair: {@code <ending number="1" type="stop">} closes volta 1 on the
     * right barline, and {@code <ending number="2" type="start">} opens volta 2 on the left
     * barline of the measure that follows.
     *
     * <p>A {@code REPEAT_LEFT_RIGHT} split already has that forward-left barline. A
     * {@code REPEAT_RIGHT} split does not, so an invisible one is inserted at index 0 of the
     * following measure.
     */
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
     * The left barline hosting a note-anchored volta start: an invisible barline inserted
     * immediately before everything the note contributed, directions included.
     *
     * <p>Returns {@code null} when the element produced no content, which leaves the volta
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
     * The right barline hosting a note-terminated volta close, immediately after the note.
     *
     * <p>When the note is already followed by an invisible right barline — the line break's
     * marker, or the one that precedes a {@code REPEAT_LEFT} — the volta rides on that one
     * rather than adding a second invisible barline beside it. The following node is simply
     * there to look at.
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

    /** Whether {@code barline} is an undrawn right barline, so a volta close can ride on it. */
    private static boolean isInvisibleRightBarline(Barline barline) {
        var style = barline.getBarStyle();
        return barline.getLocation() == RightLeftMiddle.RIGHT && style != null && style.getValue() == BarStyle.NONE;
    }

    /**
     * Sets {@code <ending number type>} on {@code host}, if there is one and it is free.
     *
     * <p>MusicXML allows at most one {@code <ending>} per {@code <barline>} — it is a single
     * typed property, not a list — so a second volta marker landing on the same barline is
     * dropped rather than emitted.
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
     * Where {@code node} sits in a measure's child list, or {@code null} when it is not there.
     *
     * <p>Compared by identity, not by {@code equals}. The comparison that matters is "is this
     * the very node I built", and {@code List.indexOf} would answer that correctly today only
     * because the generated MusicXML classes happen to define no equality — the same
     * assumption {@link BuildIndex} guards against one level up.
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
