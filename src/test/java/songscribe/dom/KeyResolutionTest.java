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

package songscribe.dom;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.note;
import static songscribe.dom.StaffElementFactory.singleBarline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;

/**
 * Exercises the promises about key resolution that no single class owns, and that no single
 * phase's tests can see: each one is a place where two subsystems have to agree about what a note
 * sounds, and either could drift without the other's tests noticing. The rules themselves are
 * stated in {@code docs/key-signatures.md}.
 *
 * <p><b>Which key applies where</b> — {@link StaffElement#findEffectiveAccidental} falls back to
 * the key in effect <em>at the note's index</em>, which is what {@link Line#keyAt} answers and why
 * the resolver takes an index at all. Enumerated, not sampled: every ordered pair of
 * {@link Key#allSignatures()} as (line's running key, mid-line key), each asserted at one staff
 * position per pitch class. The oracle is a line that runs in the key alone, so the case pins
 * <em>which key is consulted</em> rather than restating the key-to-accidental mapping
 * {@link StaffElement#keyAccidentalFor} owns and {@code KeyChangeTest} covers.
 *
 * <p><b>A key change is a barrier</b> — {@link ElementType#cancelsAccidentals} is true for
 * {@code KEY_SIGNATURE}, so an explicit accidental written before a mid-line key change reaches no
 * later note at that staff position. Asserted over the same enumerated domain, with a double sharp
 * as the accidental left behind: a key signature never yields one, so a carried accidental is
 * always distinguishable from a resolved one. A key signature is always immediately preceded by a
 * barline, which is a barrier too, so what this pins is the outcome — the accidental does not
 * survive, and the key consulted afterwards is the new one, not the one the line started in.
 *
 * <p><b>The tie escape</b> — a tie whose anchor sits before a barrier carries the anchor's
 * accidental across it, which {@code findEffectiveAccidental} documents for barlines and must mean
 * for a key change equally. Each arrangement is paired with the same one untied, which is what
 * shows the tie doing the work: one key change, then a chain across two.
 *
 * <p><b>The two resolvers agree</b> — {@code AccidentalReconciliation} resolves against a
 * <em>projection</em> of the line an edit will produce, {@code findEffectiveAccidental} against the
 * line as it stands, and a preview that disagrees with its committed result is the failure this
 * guards. Asserted two ways, both as properties over many inputs:
 * <ul>
 *   <li>at every insertion point of a key-changing line × one staff position per pitch class, a
 *       note pasted with the live resolver's answer as its source context needs no accidental
 *       written — which it would if the projected resolver answered anything else; and</li>
 *   <li>over representative edits, every note keeps the pitch it was promised once the edit and
 *       its reconciliation are committed and read back through the live resolver.</li>
 * </ul>
 * Agreement is by sounding adjustment rather than by enum identity, which is the only sameness
 * {@code AccidentalReconciliation} claims: null and {@code NATURAL} sound alike.
 *
 * <p><b>The inheritance chain</b> — the keys of {@code [C, -, -, D, -]}, before and after a
 * mid-line change on the second line, plus the note resolution the changed inheritance produces two
 * lines further down. The stopping rule is the fourth line, whose own key pins it.
 *
 * <p>The MIDI half of this — that {@link StaffElement#getPitch} carries the index through to
 * {@code keyInEffectAt} — is asserted in {@code LineTrackBuilderTest}, where the rest of the MIDI
 * pitch chain lives.
 */
class KeyResolutionTest extends UnitTest {

    private static final Key C_MAJOR = new Key(KeyType.NONE, 0);

    /** One sharp is F♯, which is why every fixture below resolves an F. */
    private static final Key G_MAJOR = new Key(KeyType.SHARPS, 1);

    private static final Key D_MAJOR = new Key(KeyType.SHARPS, 2);

    /** A signature with an accidental on every pitch class, so no fixture note escapes it. */
    private static final Key ALL_FLATS = new Key(KeyType.FLATS, Key.MAX_ACCIDENTAL_COUNT);

    private static final Key ALL_SHARPS = new Key(KeyType.SHARPS, Key.MAX_ACCIDENTAL_COUNT);

    // Staff position 0 is B4 and positions grow downwards, so each step down is one letter back.
    private static final int G_STAFF_POSITION = 2;
    private static final int F_STAFF_POSITION = 3;

    /**
     * One staff position per pitch class, walked upwards from B4 until a pitch class repeats. The
     * mapping decides how many there are, so a change to it reaches every case resolved per pitch
     * class rather than leaving a hand-written list one short.
     */
    private static final List<Integer> STAFF_POSITIONS_PER_PITCH_CLASS = staffPositionsPerPitchClass();

    private static List<Integer> staffPositionsPerPitchClass() {
        var staffPositions = new ArrayList<Integer>();
        var pitchClasses = new HashSet<Integer>();

        for (
            var staffPosition = 0;
            pitchClasses.add(StaffElement.getPitchIndex(staffPosition));
            staffPosition++
        ) {
            staffPositions.add(staffPosition);
        }

        return List.copyOf(staffPositions);
    }

    static Stream<Arguments> allOrderedKeyPairs() {
        var signatures = Key.allSignatures();

        return signatures.stream()
            .flatMap(running -> signatures.stream().map(midLine -> Arguments.of(running, midLine)));
    }

    // ==========================================================================
    // Fixture construction
    // ==========================================================================

    private static Line lineRunningIn(Key key) {
        var line = detachedLine();
        line.setKey(key);

        return line;
    }

    /**
     * The accidental a bare note at {@code staffPosition} resolves to on a line that runs in
     * {@code key} and holds nothing else — the oracle the mid-line cases compare against, so they
     * assert which key is consulted rather than re-deriving what that key does.
     */
    private static StaffElement.@Nullable Accidental resolvedOnALineRunningIn(Key key, int staffPosition) {
        var line = lineRunningIn(key);
        var note = note(staffPosition, null);
        line.addElement(note);

        return note.findLastAccidental();
    }

    private static final int NOTE_BEFORE_THE_CHANGE = 0;
    private static final int MID_LINE_BARLINE = 1;
    private static final int MID_LINE_KEY_SIGNATURE = 2;
    private static final int NOTE_AFTER_THE_CHANGE = 3;

    /**
     * A line running in {@code runningKey} that changes to {@code midLineKey} halfway along, with a
     * note at {@code staffPosition} on each side of the change. The barline is the position
     * invariant's, not the fixture's: a key signature is never at index 0 and never without a
     * barline in front of it.
     */
    private static Line lineChangingKeyMidLine(
        Key runningKey,
        Key midLineKey,
        int staffPosition,
        StaffElement.@Nullable Accidental leadingAccidental) {

        var line = lineRunningIn(runningKey);
        line.addElement(note(staffPosition, leadingAccidental));
        line.addElement(singleBarline());
        line.addElement(new KeySignatureElement(midLineKey));
        line.addElement(note(staffPosition, null));

        return line;
    }

    // ==========================================================================
    // Which key a note resolves against
    // ==========================================================================

    @Test
    void testTheStaffPositionsResolvedAgainstCoverEveryPitchClassAKeyCanAlter() {
        // A signature carrying the maximum number of accidentals alters every pitch class there
        // is, so it is the domain's own statement of what the list above has to span.
        assertThat(STAFF_POSITIONS_PER_PITCH_CLASS).allSatisfy(staffPosition ->
            assertThat(ALL_SHARPS.altersPitchClass(StaffElement.getPitchIndex(staffPosition)))
                .as("staff position %d", staffPosition)
                .isTrue());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allOrderedKeyPairs")
    void testANoteBeforeAMidLineKeyChangeResolvesAgainstTheKeyTheLineStartsIn(Key running, Key midLine) {
        for (var staffPosition : STAFF_POSITIONS_PER_PITCH_CLASS) {
            var line = lineChangingKeyMidLine(running, midLine, staffPosition, null);

            assertThat(line.getElement(NOTE_BEFORE_THE_CHANGE).findLastAccidental())
                .as("staff position %d, before the change", staffPosition)
                .isEqualTo(resolvedOnALineRunningIn(running, staffPosition));
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allOrderedKeyPairs")
    void testANoteAfterAMidLineKeyChangeResolvesAgainstTheNewKey(Key running, Key midLine) {
        for (var staffPosition : STAFF_POSITIONS_PER_PITCH_CLASS) {
            var line = lineChangingKeyMidLine(running, midLine, staffPosition, null);

            assertThat(line.getElement(NOTE_AFTER_THE_CHANGE).findLastAccidental())
                .as("staff position %d, after the change", staffPosition)
                .isEqualTo(resolvedOnALineRunningIn(midLine, staffPosition));
        }
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allOrderedKeyPairs")
    void testAnExplicitAccidentalDoesNotCarryAcrossAMidLineKeyChange(Key running, Key midLine) {
        // No key signature ever yields a double sharp, so a note resolving to one could only have
        // reached back across the change for it.
        var carried = StaffElement.Accidental.DOUBLE_SHARP;

        for (var staffPosition : STAFF_POSITIONS_PER_PITCH_CLASS) {
            var line = lineChangingKeyMidLine(running, midLine, staffPosition, carried);
            var resolved = line.getElement(NOTE_AFTER_THE_CHANGE).findLastAccidental();

            assertThat(resolved)
                .as("staff position %d resolved to the accidental written before the change",
                    staffPosition)
                .isNotEqualTo(carried);
            assertThat(resolved)
                .as("staff position %d, after the change", staffPosition)
                .isEqualTo(resolvedOnALineRunningIn(midLine, staffPosition));
        }
    }

    // ==========================================================================
    // The tie escape, which is the one way across a key change
    // ==========================================================================

    /**
     * @param arrange fills a line already running in C major and returns the note to resolve
     */
    private record TieCase(
        String description,
        Function<Line, StaffElement> arrange,
        StaffElement.@Nullable Accidental expected) {}

    private static final int TIE_ANCHOR = 0;
    private static final int FIRST_TIE_END = 3;
    private static final int SECOND_TIE_END = 6;

    /** {@code F♯ | [7♭] F}, with the second F resolvable only through the key or the tie. */
    private static Line acrossOneKeyChange(Line line) {
        line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.SHARP));
        line.addElement(singleBarline());
        line.addElement(new KeySignatureElement(ALL_FLATS));
        line.addElement(note(F_STAFF_POSITION, null));

        return line;
    }

    /** The same, extended with a second measure that changes key again, to C major. */
    private static Line acrossTwoKeyChanges(Line line) {
        acrossOneKeyChange(line);
        line.addElement(singleBarline());
        line.addElement(new KeySignatureElement(C_MAJOR));
        line.addElement(note(F_STAFF_POSITION, null));

        return line;
    }

    private static void tie(Line line, int anchorIndex, int endIndex) {
        line.addTie(new Tie(line.getElement(anchorIndex), line.getElement(endIndex)));
    }

    static Stream<TieCase> tieCases() {
        return Stream.of(
            new TieCase(
                "a tie from before the change carries the anchor's accidental across it",
                line -> {
                    acrossOneKeyChange(line);
                    tie(line, TIE_ANCHOR, FIRST_TIE_END);

                    return line.getElement(FIRST_TIE_END);
                },
                StaffElement.Accidental.SHARP),
            new TieCase(
                "without a tie, the same note falls back to the key the change establishes",
                line -> acrossOneKeyChange(line).getElement(FIRST_TIE_END),
                StaffElement.Accidental.FLAT),
            new TieCase(
                "a chain of ties carries the accidental across two key changes",
                line -> {
                    acrossTwoKeyChanges(line);
                    tie(line, TIE_ANCHOR, FIRST_TIE_END);
                    tie(line, FIRST_TIE_END, SECOND_TIE_END);

                    return line.getElement(SECOND_TIE_END);
                },
                StaffElement.Accidental.SHARP),
            new TieCase(
                "without the chain, the last note falls back to the second change's key",
                line -> acrossTwoKeyChanges(line).getElement(SECOND_TIE_END),
                null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tieCases")
    void testAnAccidentalCrossesAKeyChangeOnlyThroughATie(TieCase testCase) {
        var line = lineRunningIn(C_MAJOR);

        assertThat(testCase.arrange().apply(line).findLastAccidental()).isEqualTo(testCase.expected());
    }

    // ==========================================================================
    // The projected resolver and the live one agree
    // ==========================================================================

    // The shared fixture: one line in G major (F♯) changing to seven flats halfway along.
    //
    //   0: F   1: F♮   2: F   3: |   4: [7♭]   5: F   6: G   7: F
    private static final int BARE_F = 0;
    private static final int EXPLICITLY_NATURAL_F = 1;
    private static final int INHERITING_F = 2;
    private static final int FIXTURE_KEY_SIGNATURE = 4;
    private static final int FIRST_NOTE_AFTER_THE_CHANGE = 5;

    private static Song fixtureSong() {
        var song = new Song();
        var line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            line.setKey(G_MAJOR);
            line.addElement(note(F_STAFF_POSITION, null));
            line.addElement(note(F_STAFF_POSITION, StaffElement.Accidental.NATURAL));
            line.addElement(note(F_STAFF_POSITION, null));
            line.addElement(singleBarline());
            line.addElement(new KeySignatureElement(ALL_FLATS));
            line.addElement(note(F_STAFF_POSITION, null));
            line.addElement(note(G_STAFF_POSITION, null));
            line.addElement(note(F_STAFF_POSITION, null));
        });

        return song;
    }

    /**
     * Every index of the fixture a paste may land at. The key signature's own index is excluded:
     * inserting there would push it away from the barline it has to sit behind, which is a document
     * the program cannot produce.
     */
    static Stream<Integer> insertionIndices() {
        var line = fixtureSong().getLine(0);

        return Stream.iterate(0, index -> index <= line.elementCount(), index -> index + 1)
            .filter(index -> index != FIXTURE_KEY_SIGNATURE);
    }

    /** A prior-accidental list, which {@code List.of} cannot build: null is meaningful in it. */
    private static List<StaffElement.@Nullable Accidental> priors(
        StaffElement.@Nullable Accidental... accidentals) {

        return Arrays.asList(accidentals);
    }

    @ParameterizedTest(name = "insertion index {0}")
    @MethodSource("insertionIndices")
    void testTheProjectedResolverAnswersWhatTheLiveResolverAnswersAtEveryInsertionPoint(int insertionIndex) {
        var line = fixtureSong().getLine(0);

        for (var staffPosition : STAFF_POSITIONS_PER_PITCH_CLASS) {
            var pasted = note(staffPosition, null);
            var live = pasted.findEffectiveAccidental(line, insertionIndex);

            // A pasted note is protected: it is given an explicit accidental exactly when the
            // context it lands in sounds different from the context it came from. Handing it the
            // live resolver's own answer as that source context therefore leaves it untouched if,
            // and only if, the projected resolver answered the same thing.
            var changes = AccidentalReconciliation.reconcile(
                new AccidentalReconciliation.InsertionRegion(
                    line, insertionIndex, null, List.of(pasted), priors(live), List.of()));

            assertThat(changes.stream().filter(change -> change.note() == pasted).toList())
                .as("the resolvers disagree at index %d, staff position %d, where the live one "
                    + "answers %s", insertionIndex, staffPosition, live)
                .isEmpty();
        }
    }

    /**
     * An edit, reconciled and committed the way a controller commits it.
     *
     * @return the sounding adjustment each note the edit brings in was promised to keep; empty for
     *         an edit that brings none in
     */
    @FunctionalInterface
    private interface Edit {
        Map<StaffElement, Integer> commitOn(Song song);
    }

    private record EditCase(String description, Edit edit) {}

    private static int soundingAdjustment(StaffElement note) {
        var own = note.getAccidental();

        return StaffElement.getPitchAdjustment((own != null) ? own : note.findLastAccidental());
    }

    /**
     * What every pitched note on {@code line} sounds, read through the live resolver. Keyed by
     * element: {@link StaffElement} overrides neither {@code equals} nor {@code hashCode}, so the
     * map compares by identity, which is what a note surviving an edit needs.
     */
    private static Map<StaffElement, Integer> soundingAdjustments(Line line) {
        var adjustments = new HashMap<StaffElement, Integer>();

        for (var index = 0; index < line.elementCount(); index++) {
            var element = line.getElement(index);

            if (element.getType().isPitchedNote()) {
                adjustments.put(element, soundingAdjustment(element));
            }
        }

        return adjustments;
    }

    /** Pastes {@code pasted} into the fixture at {@code index}, as it sounded where it came from. */
    private static Map<StaffElement, Integer> paste(
        Song song, int index, StaffElement pasted, StaffElement.@Nullable Accidental sourceContext) {

        var line = song.getLine(0);
        var accidentalChanges = AccidentalReconciliation.reconcile(
            new AccidentalReconciliation.InsertionRegion(
                line, index, null, List.of(pasted), priors(sourceContext), List.of()));

        song.withModification(() -> {
            AccidentalMaterializer.applyIfAccepted(line, accidentalChanges, List.of(pasted), () -> true);
            line.addElement(index, pasted);
        });

        return Map.of(pasted, StaffElement.getPitchAdjustment(sourceContext));
    }

    static Stream<EditCase> editCases() {
        return Stream.of(
            new EditCase(
                "pasting a note that sounded sharp where it came from, after the key change",
                song -> paste(
                    song,
                    FIRST_NOTE_AFTER_THE_CHANGE,
                    note(F_STAFF_POSITION, null),
                    StaffElement.Accidental.SHARP)),
            new EditCase(
                "pasting a note that sounded unaltered where it came from, after the key change",
                song -> paste(
                    song, FIRST_NOTE_AFTER_THE_CHANGE, note(F_STAFF_POSITION, null), null)),
            new EditCase(
                "inserting a mid-line key change in front of a note that inherits an accidental",
                song -> {
                    var line = song.getLine(0);
                    var inserted = List.of(singleBarline(), new KeySignatureElement(ALL_SHARPS));
                    var accidentalChanges = AccidentalReconciliation.reconcile(
                        new AccidentalReconciliation.InsertionRegion(
                            line, INHERITING_F, null, inserted, List.of(), List.of()));

                    song.withModification(() -> {
                        AccidentalMaterializer.applyIfAccepted(
                            line, accidentalChanges, inserted, () -> true);

                        for (var offset = 0; offset < inserted.size(); offset++) {
                            line.addElement(INHERITING_F + offset, inserted.get(offset));
                        }
                    });

                    return Map.of();
                }),
            new EditCase(
                "changing the key the line itself runs in",
                song -> {
                    var line = song.getLine(0);
                    var reconciled = AccidentalReconciliation.reconcileModification(
                        AccidentalReconciliation.lineKeyChangeReach(line, ALL_FLATS),
                        AccidentalReconciliation.RestatementRemoval.NONE);

                    song.withModification(() -> {
                        for (var reconciledLine : reconciled) {
                            AccidentalMaterializer.commit(
                                reconciledLine.line(), reconciledLine.changes());
                        }

                        line.setKey(ALL_FLATS);
                    });

                    return Map.of();
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("editCases")
    void testEveryNoteSoundsWhatItsReconciliationPromisedOnceTheEditIsCommitted(EditCase testCase) {
        var song = fixtureSong();
        var line = song.getLine(0);

        // What the live resolver says now is what the projected resolver had to have said for the
        // reconciliation to protect it. A disagreement between the two shows up here as a note
        // sounding something else afterwards.
        var promised = soundingAdjustments(line);
        promised.putAll(testCase.edit().commitOn(song));

        assertThat(soundingAdjustments(line)).isEqualTo(promised);
    }

    @Test
    void testTheFixtureReachesEveryResolutionOutcomeTheEditCasesDependOn() {
        // The edits above are only worth running against a line whose notes do not already all
        // sound alike: a fixture that lost its explicit natural, or its mid-line change, would go
        // on passing them while reaching none of the cases they are for.
        var line = fixtureSong().getLine(0);

        assertThat(soundingAdjustment(line.getElement(BARE_F)))
            .as("bare, in a key that sharpens it")
            .isEqualTo(StaffElement.Accidental.SHARP.semitones());
        assertThat(soundingAdjustment(line.getElement(EXPLICITLY_NATURAL_F))).isZero();
        assertThat(soundingAdjustment(line.getElement(INHERITING_F)))
            .as("inherits the natural written before it")
            .isZero();
        assertThat(soundingAdjustment(line.getElement(FIRST_NOTE_AFTER_THE_CHANGE)))
            .as("the mid-line change flattens it")
            .isEqualTo(StaffElement.Accidental.FLAT.semitones());
    }

    // ==========================================================================
    // The inheritance chain
    // ==========================================================================

    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;

    /**
     * The chain {@code docs/key-signatures.md} draws, shortened: a line establishing a key, two
     * inheriting it, one establishing its own, and one inheriting that.
     */
    private static final List<@Nullable Key> CHAIN_KEYS =
        Arrays.asList(C_MAJOR, null, null, D_MAJOR, null);

    /**
     * Built through modification brackets rather than with tracking suspended, because the
     * inherited keys under test are maintained by {@code Song.applyChange} and a suspended mutation
     * never reaches it.
     */
    private static Song chainSong() {
        var song = new Song();

        song.withModification(() -> {
            song.getLine(0).setKey(CHAIN_KEYS.getFirst());

            for (var lineIndex = 1; lineIndex < CHAIN_KEYS.size(); lineIndex++) {
                var line = new Line(song);
                song.addLine(line);
                line.setKey(CHAIN_KEYS.get(lineIndex));
            }
        });

        return song;
    }

    private static List<Key> runningKeys(Song song) {
        var keys = new ArrayList<Key>();

        for (var lineIndex = 0; lineIndex < song.lineCount(); lineIndex++) {
            keys.add(song.getLine(lineIndex).getRunningKey());
        }

        return keys;
    }

    private static void addMidLineKeyChange(Song song, int lineIndex, Key key) {
        var line = song.getLine(lineIndex);

        song.withModification(() -> {
            line.addElement(crotchet());
            line.addElement(singleBarline());
            line.addElement(new KeySignatureElement(key));
        });
    }

    @Test
    void testALineWithNoKeyOfItsOwnRunsInTheKeyTheLineBeforeItEndsIn() {
        var song = chainSong();

        assertThat(runningKeys(song))
            .containsExactly(C_MAJOR, C_MAJOR, C_MAJOR, D_MAJOR, D_MAJOR);
        assertKeyPropagationInvariant(song);
    }

    @Test
    void testAMidLineKeyChangeMovesWhatTheLinesBelowInheritUntilOneWithAKeyOfItsOwn() {
        var song = chainSong();

        addMidLineKeyChange(song, SECOND_LINE, G_MAJOR);

        assertThat(runningKeys(song))
            .as("the line holding the change still starts in the key it inherited; the line after "
                + "it inherits the change, and the line with its own key stops it there")
            .containsExactly(C_MAJOR, C_MAJOR, G_MAJOR, D_MAJOR, D_MAJOR);
        assertKeyPropagationInvariant(song);
    }

    @Test
    void testANoteResolvesAgainstAMidLineKeyChangeMadeOnTheLineAboveIt() {
        var song = chainSong();
        addMidLineKeyChange(song, SECOND_LINE, G_MAJOR);

        var note = note(F_STAFF_POSITION, null);
        song.withModification(() -> song.getLine(THIRD_LINE).addElement(note));

        assertThat(note.findLastAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }
}
