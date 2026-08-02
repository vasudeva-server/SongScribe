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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import songscribe.dom.*;
import songscribe.layout.BeamMath;
import songscribe.layout.LineEndingSupport;

/**
 * Per-element-index span precompute for {@link MusicXmlWriter}'s line-driven
 * measure loop. {@link #buildSpanIndex} resolves every span's anchor/end
 * element index exactly once per line, so the measure loop can do O(1)
 * lookups instead of calling {@code getAnchorElementIndex()} (O(n)) per
 * element per span.
 */
final class MusicXmlSpanIndex {

    private MusicXmlSpanIndex() {}

    // -------------------------------------------------------------------------
    // Per-element-index span precompute types
    //
    // IndexSpanMarkers holds all span activity for one element index.
    // The noteMarkers field (per-note spans) is threaded into NoteWriteContext
    // so writeNote/writeNotations can do O(1) lookups; the hairpin and ending
    // fields are consumed by the measure-level element loop (Phase 3).
    // -------------------------------------------------------------------------

    record IndexSpanMarkers(
        NoteSpanMarkers noteMarkers,
        List<Hairpin> hairpinsStartingHere,
        List<Hairpin> hairpinsEndingHere,
        List<EndingMarker> endingLeftBarlineMarkers,
        List<EndingMarker> endingRightBarlineMarkers
    ) {}

    /**
     * One {@code <ending number type>} child to fold onto a {@code <barline>}.
     * The structural anchor/split/end → volta mapping is resolved once in
     * {@link #buildSpanIndex} and stored as left-barline / right-barline marker
     * lists per element index (see the ASCII diagram there).
     */
    record EndingMarker(String number, String type) {}

    /**
     * Mutable builder for one element's {@link IndexSpanMarkers}. Created once
     * per element index during {@link #buildSpanIndex}, discarded after the
     * final records are assembled.
     */
    private static final class SpanBuilder {

        // Empty array = not in a beam group (matches NoteSpanMarkers sentinel).
        String[] beamLevelValues = new String[0];
        @Nullable Tie tieStart;
        @Nullable Tie tieStop;
        @Nullable Tuplet tuplet;
        boolean isTupletAnchor;
        boolean isTupletEnd;
        @Nullable Trill trill;
        boolean isTrillAnchor;
        boolean isTrillEnd;

        // Lazily allocated: most indices carry no hairpin or ending markers, so
        // these stay null (and resolve to a shared empty list in build()) until
        // the first marker is bucketed here. Crescendos and diminuendos share one
        // pair of buckets — the wedge type is recovered from the Hairpin subtype.
        @Nullable List<Hairpin> hairpinsStartingHere;
        @Nullable List<Hairpin> hairpinsEndingHere;
        @Nullable List<EndingMarker> endingLeftBarlineMarkers;
        @Nullable List<EndingMarker> endingRightBarlineMarkers;

        IndexSpanMarkers build() {
            var noteMarkers = new NoteSpanMarkers(
                beamLevelValues,
                tieStart, tieStop,
                tuplet, isTupletAnchor, isTupletEnd,
                trill, isTrillAnchor, isTrillEnd
            );

            return new IndexSpanMarkers(
                noteMarkers,
                orEmpty(hairpinsStartingHere), orEmpty(hairpinsEndingHere),
                orEmpty(endingLeftBarlineMarkers), orEmpty(endingRightBarlineMarkers)
            );
        }
    }

    /** Appends {@code value} to {@code list}, allocating the list on first use. */
    private static <T> List<T> appendLazily(@Nullable List<T> list, T value) {
        var target = list == null ? new ArrayList<T>() : list;
        target.add(value);
        return target;
    }

    /** Returns {@code list}, or a shared empty list when it was never allocated. */
    private static <T> List<T> orEmpty(@Nullable List<T> list) {
        return list == null ? List.of() : list;
    }

    // -------------------------------------------------------------------------
    // Per-element span precompute
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when both span endpoints fall within the line's
     * element range — the guard every span loop in {@link #buildSpanIndex}
     * applies before bucketing a span (a malformed song can leave an anchor or
     * end index dangling at -1 or past the element count).
     */
    private static boolean indicesInRange(int anchorIdx, int endIdx, int count) {
        return anchorIdx >= 0 && endIdx >= 0 && anchorIdx < count && endIdx < count;
    }

    /**
     * Buckets one hairpin's start and end onto its anchor and end builders. A
     * span whose endpoints fall outside the line is silently skipped.
     */
    private static void bucketHairpin(SpanBuilder[] builders, Hairpin hairpin, int count) {
        var anchorIdx = hairpin.getAnchorElementIndex();
        var endIdx = hairpin.getEndElementIndex();

        if (!indicesInRange(anchorIdx, endIdx, count)) {
            return;
        }

        var anchorBuilder = builders[anchorIdx];
        var endBuilder = builders[endIdx];
        anchorBuilder.hairpinsStartingHere = appendLazily(anchorBuilder.hairpinsStartingHere, hairpin);
        endBuilder.hairpinsEndingHere = appendLazily(endBuilder.hairpinsEndingHere, hairpin);
    }

    /**
     * Builds the per-element-index span marker array for {@code line}.
     *
     * <p>For each of the six span types, this method calls the line accessor
     * once and resolves every span's anchor/end element index exactly once
     * via {@link Span#getAnchorElementIndex()} /
     * {@link Span#getEndElementIndex()}.
     * The element loop can then do O(1) lookups instead of calling
     * {@code indexOf} (O(n)) per element per span.
     *
     * <p>Bucket rules:
     * <ul>
     *   <li><b>Beam / Tuplet / Trill</b>: span reference set on anchor through
     *       end (inclusive); anchor and end flags set only at the endpoints.</li>
     *   <li><b>Tie</b>: {@code tieStart} reference set at anchor; {@code tieStop}
     *       set at end. Both may be non-null on the same note when it is the
     *       end of one tie and the anchor of the next in a chain.</li>
     *   <li><b>Crescendo / Diminuendo / Ending</b>: anchor and end indices only;
     *       Ending additionally records its split index when present.</li>
     * </ul>
     */
    static IndexSpanMarkers[] buildSpanIndex(Line line) {
        var count = line.elementCount();
        var builders = new SpanBuilder[count];

        for (var i = 0; i < count; i++) {
            builders[i] = new SpanBuilder();
        }

        // Beams: pre-compute per-note, per-level beam values for every note
        // in the group [anchor, end].  A single-note beam (anchor == end)
        // is degenerate and produces no <beam> output.
        for (var beam : line.findSpans(Beam.class)) {
            var anchorIdx = beam.getAnchorElementIndex();
            var endIdx = beam.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            // Skip degenerate single-note beams — they cannot be beamed.
            if (anchorIdx == endIdx) {
                continue;
            }

            for (var i = anchorIdx; i <= endIdx; i++) {
                // A grace note inside the span is not a beam member — the beam passes
                // over it, so it carries no <beam> of its own (refs #592).
                if (line.getElement(i).getType().isGraceNote()) {
                    continue;
                }

                builders[i].beamLevelValues = computeNoteBeamValues(line, i, anchorIdx, endIdx);
            }
        }

        // Ties: anchor and end flags are independent so a note that is the end
        // of one tie and the anchor of the next will have both flags set.
        for (var tie : line.findTies()) {
            var anchorIdx = tie.getAnchorElementIndex();
            var endIdx = tie.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            builders[anchorIdx].tieStart = tie;
            builders[endIdx].tieStop = tie;
        }

        // Tuplets: set the span reference on every note in the group [anchor, end].
        for (var tuplet : line.findSpans(Tuplet.class)) {
            var anchorIdx = tuplet.getAnchorElementIndex();
            var endIdx = tuplet.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            builders[anchorIdx].isTupletAnchor = true;
            builders[endIdx].isTupletEnd = true;

            for (var i = anchorIdx; i <= endIdx; i++) {
                // A grace note inside the span is not a tuplet member — it takes no
                // <time-modification> (refs #592).
                if (line.getElement(i).getType().isGraceNote()) {
                    continue;
                }

                builders[i].tuplet = tuplet;
            }
        }

        // Trills: set the span reference on every note in the group [anchor, end].
        for (var trill : line.findSpans(Trill.class)) {
            var anchorIdx = trill.getAnchorElementIndex();
            var endIdx = trill.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            builders[anchorIdx].isTrillAnchor = true;
            builders[endIdx].isTrillEnd = true;

            for (var i = anchorIdx; i <= endIdx; i++) {
                builders[i].trill = trill;
            }
        }

        // Hairpins (measure-level): anchor and end indices only. Crescendos and
        // diminuendos share one pair of start/end buckets; the wedge type is
        // recovered from the Hairpin subtype at emission time.
        for (var crescendo : line.getCrescendos()) {
            bucketHairpin(builders, crescendo, count);
        }

        for (var diminuendo : line.getDiminuendos()) {
            bucketHairpin(builders, diminuendo, count);
        }

        // Endings (measure-level): one SongScribe Ending expands to one or two
        // MusicXML voltas, folded onto the <barline> elements Phase 2 emits.
        //
        //   anchor               split (REPEAT_RIGHT /         end
        //   (REPEAT_LEFT or       REPEAT_LEFT_RIGHT)           (terminal barline)
        //    SINGLE_BARLINE)
        //        |                      |                          |
        //   [1 start]            [1 stop] [2 start]            [2 stop]
        //        '------ volta 1 -------'  '------- volta 2 -------'
        //
        // Every ending has a split, so it always expands to two voltas.
        //
        // Markers are bucketed per element index as left-barline vs right-barline
        // children so the element loop can attach them to the correct <barline>
        // emission without re-deriving the structure:
        //   - REPEAT_LEFT anchor  → forward (left) barline   → left bucket
        //   - SINGLE_BARLINE anchor / terminal end / split stop → right barline
        //   - split [2 start]     → forward (left) barline    → left bucket
        // getSplitIndex() throws if an ending has no split (should never happen).
        for (var ending : LineEndingSupport.findEndings(line)) {
            var anchorIdx = ending.getAnchorElementIndex();
            var endIdx = ending.getEndElementIndex();

            if (!indicesInRange(anchorIdx, endIdx, count)) {
                continue;
            }

            var splitIdx = ending.getSplitIndex(line);

            // Anchor: <ending number="1" type="start">. A REPEAT_LEFT anchor is
            // written as a forward (left) barline; a note anchor (no barline
            // element, per issue #306) rides on an invisible left barline the note
            // branch emits immediately before the note — both go in the left
            // bucket. Any other anchor (SINGLE_BARLINE) closes the previous measure
            // as a right barline.
            var anchorStart = new EndingMarker(MusicXmlTags.NUMBER_1, MusicXmlTags.TYPE_START);
            var anchorBuilder = builders[anchorIdx];
            var anchorType = line.getElement(anchorIdx).getType();

            if (anchorType == ElementType.REPEAT_LEFT || anchorType.isDuration()) {
                anchorBuilder.endingLeftBarlineMarkers = appendLazily(anchorBuilder.endingLeftBarlineMarkers, anchorStart);
            } else {
                anchorBuilder.endingRightBarlineMarkers = appendLazily(anchorBuilder.endingRightBarlineMarkers, anchorStart);
            }

            // End: always number="2" — every ending has a split, so the end closes
            // volta 2. A barline/repeat end closes with type="stop"; a note end (no
            // barline element, per issue #306) closes with type="discontinue" (an open
            // bracket) on an invisible right barline the note branch emits immediately
            // after the note.
            var endNumber = MusicXmlTags.NUMBER_2;
            var endBuilder = builders[endIdx];
            var endType = line.getElement(endIdx).getType();

            if (endType == ElementType.REPEAT_LEFT) {
                endBuilder.endingLeftBarlineMarkers = appendLazily(endBuilder.endingLeftBarlineMarkers,
                    new EndingMarker(endNumber, MusicXmlTags.TYPE_STOP));
            } else if (endType.isDuration()) {
                endBuilder.endingRightBarlineMarkers = appendLazily(endBuilder.endingRightBarlineMarkers,
                    new EndingMarker(endNumber, MusicXmlTags.ENDING_DISCONTINUE));
            } else {
                endBuilder.endingRightBarlineMarkers = appendLazily(endBuilder.endingRightBarlineMarkers,
                    new EndingMarker(endNumber, MusicXmlTags.TYPE_STOP));
            }

            // Split: <ending number="1" type="stop"> closes volta 1 on the right
            // (backward) barline; <ending number="2" type="start"> opens volta 2
            // on the left (forward) barline.
            var splitBuilder = builders[splitIdx];
            splitBuilder.endingRightBarlineMarkers = appendLazily(splitBuilder.endingRightBarlineMarkers,
                new EndingMarker(MusicXmlTags.NUMBER_1, MusicXmlTags.TYPE_STOP));
            splitBuilder.endingLeftBarlineMarkers = appendLazily(splitBuilder.endingLeftBarlineMarkers,
                new EndingMarker(MusicXmlTags.NUMBER_2, MusicXmlTags.TYPE_START));
        }

        // Assemble the final immutable per-index records.
        var result = new IndexSpanMarkers[count];

        for (var i = 0; i < count; i++) {
            result[i] = builders[i].build();
        }

        return result;
    }

    /**
     * Computes the MusicXML {@code <beam>} text-content values for note
     * {@code noteIdx} within the beam group [{@code anchorIdx}, {@code endIdx}].
     *
     * <p>Returns an array indexed by {@code (number - 1)}: {@code result[0]}
     * holds the value for {@code <beam number="1">}, {@code result[1]} for
     * {@code number="2"}, etc. An empty-string entry means no {@code <beam>}
     * element should be emitted at that number for this note.
     *
     * <p>Level 0 (primary, {@code number="1"}): always {@code begin/continue/end}
     * for every note in the group — never a hook.
     *
     * <p>Secondary levels (1 = 16th, 2 = 32nd): for each level L, this method
     * finds the maximal contiguous run of notes at that level containing
     * {@code noteIdx}. A run of length ≥ 2 emits {@code begin/continue/end};
     * a run of length 1 is a partial-beam hook whose direction is determined
     * by {@link BeamMath#stubRight}.
     *
     * <p>Hook direction: {@code stubRight == true} → {@code "forward hook"};
     * {@code stubRight == false} → {@code "backward hook"}.
     */
    private static String[] computeNoteBeamValues(Line line, int noteIdx, int anchorIdx, int endIdx) {
        // Initialize all levels to the "no beam" sentinel; each slot is overwritten
        // if the note participates at that level. Sized by BeamMath.LEVEL_COUNT so
        // the array always matches the level loop below.
        var values = new String[BeamMath.LEVEL_COUNT];
        Arrays.fill(values, MusicXmlUnits.NO_BEAM_AT_LEVEL);

        // Level 0 (primary beam, number="1"): begin/continue/end — never a hook.
        if (noteIdx == anchorIdx) {
            values[0] = MusicXmlTags.BEAM_BEGIN;
        } else if (noteIdx == endIdx) {
            values[0] = MusicXmlTags.BEAM_END;
        } else {
            values[0] = MusicXmlTags.BEAM_CONTINUE;
        }

        // Secondary beam levels: level 1 = 16th (number="2"), level 2 = 32nd (number="3").
        for (var level = 1; level < BeamMath.LEVEL_COUNT; level++) {
            if (!BeamMath.noteTypeInLevel(line, noteIdx, level)) {
                // This note does not participate at this level; no <beam> element.
                continue;
            }

            // Find the maximal contiguous run at this level that contains noteIdx.
            var runStart = noteIdx;

            while (runStart > anchorIdx && BeamMath.noteTypeInLevel(line, runStart - 1, level)) {
                runStart--;
            }

            var runEnd = noteIdx;

            while (runEnd < endIdx && BeamMath.noteTypeInLevel(line, runEnd + 1, level)) {
                runEnd++;
            }

            if (runStart == runEnd) {
                // Single-note run: emit a partial-beam hook.  Direction is a pure
                // function of note durations + position in the beam group.
                values[level] = BeamMath.stubRight(line, noteIdx, anchorIdx, endIdx)
                    ? MusicXmlTags.BEAM_FORWARD_HOOK
                    : MusicXmlTags.BEAM_BACKWARD_HOOK;
            } else if (noteIdx == runStart) {
                values[level] = MusicXmlTags.BEAM_BEGIN;
            } else if (noteIdx == runEnd) {
                values[level] = MusicXmlTags.BEAM_END;
            } else {
                values[level] = MusicXmlTags.BEAM_CONTINUE;
            }
        }

        return values;
    }
}
