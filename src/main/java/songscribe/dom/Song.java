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
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.io.SongIO;
import songscribe.lifecycle.Disposable;
import songscribe.lint.Linter;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.SongData;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.notification.ActiveScriptDidChangeNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.util.StringUtils;

/**
 * The model for data that is read from and written to SongScribe files.
 *
 * <h2>Lifecycle</h2>
 * A {@code Song} subscribes itself to the message bus in every constructor. MBassador holds
 * listeners weakly, so a discarded {@code Song} keeps responding to broadcasts, and posting
 * spurious undo steps, until it is garbage-collected: call {@link #dispose()} to detach it
 * deterministically ({@code ScoreView.setSong} does when replacing the active song).
 *
 * <h2>Key invariant</h2>
 * A song has no key of its own — its key is line 0's. Across the line list, line 0 establishes a
 * key of its own ({@link Line#getKey()} is non-null), and every other line's inherited key
 * equals the key in effect at the end of the line before it. {@link Line#getRunningKey()} is
 * total.
 */
public final class Song implements Disposable {

    public enum LANGUAGE {
        // Not used, the ordinals of the actual languages start at 1
        NONE,
        BENGALI,
        ENGLISH,
        BENGALI_ENGLISH,
        ENGLISH_BENGALI,
        SANSKRIT,
        FRENCH,
        SPANISH,
        OTHER,
    }

    public static final String SRI_CHINMOY = "Sri Chinmoy";

    public enum LyricsSource {
        LYRICIST(" by ", Strings.DIALOG_SONG_SETTINGS_SOURCE_LYRICIST),
        TEXT(" from ", Strings.DIALOG_SONG_SETTINGS_SOURCE_TEXT),
        OTHER(": ", Strings.DIALOG_SONG_SETTINGS_SOURCE_OTHER);

        private final String connector;
        private final String labelKey;

        LyricsSource(String connector, String labelKey) {
            this.connector = connector;
            this.labelKey = labelKey;
        }

        public String getConnector() {
            return connector;
        }

        @Override
        public String toString() {
            return Strings.get(labelKey);
        }
    }

    /**
     * A script a song's lyrics are written in. The application holds a {@code Script}, never a
     * lyric number, which exists only in a file; only {@code songscribe.io.LyricNumbers} converts
     * between the two.
     */
    public enum Script {
        LATIN,
        BENGALI
    }

    public static final double DEFAULT_REST_LENGTH_SS = 2.0;
    private static final double MIN_DEFAULT_REST_LENGTH_SS = 1.0;

    /**
     * Stands in for {@code PageModel.getDefaultLineWidthSs()}, which is unreachable from the model
     * layer, wherever a Song is built without the app running, so a Song is never born with a
     * zero-width staff.
     */
    public static final double FALLBACK_LINE_WIDTH_SS = 90.0;

    private static DoubleSupplier defaultLineWidthProvider = () -> FALLBACK_LINE_WIDTH_SS;

    public static void setDefaultLineWidthProvider(DoubleSupplier provider) {
        defaultLineWidthProvider = provider;
    }

    // Never null: whether the header mark is drawn is decided by the mark's computed content
    // width, never by nullness.
    private Tempo tempo = new Tempo();

    private SongMetadata metadata = new SongMetadata(
        Strings.get(Strings.DOCUMENT_UNTITLED),  // title
        Strings.get(Strings.SONG_DEFAULT_NUMBER), // number
        "",    // place
        PartialDate.EmptyDate.INSTANCE, // date
        SRI_CHINMOY,           // composer
        SRI_CHINMOY,           // lyricist
        LyricsSource.LYRICIST, // lyricsSource
        false,  // arrangement
        false,  // unofficialTranslation
        "",     // subtitle
        PartialDate.EmptyDate.INSTANCE // wordsDate
    );

    private final LANGUAGE language = LANGUAGE.NONE;

    private String underLyrics = "";

    private String banglaLyrics = "";

    private String translatedLyrics = "";

    private String footnotes = "";

    private final Attribution attributionElement = new Attribution();

    private final SongTempoMark tempoMarkElement = new SongTempoMark();

    private double rowHeightAdjustmentSs = 0;

    // Clamped to at least MIN_DEFAULT_REST_LENGTH_SS on every set.
    private double defaultRestLengthSs = DEFAULT_REST_LENGTH_SS;

    private double lineWidthSs = defaultLineWidthProvider.getAsDouble();

    private final ArrayList<Line> lines = new ArrayList<>();

    /**
     * State, not a cache: every mutation that can move a key drives {@link #maintainKeyInvariant}
     * rather than an invalidate-and-recompute path.
     */
    private final Map<Line, Key> inheritedKeys = new IdentityHashMap<>();

    // Deliberately not persisted: which script a reader last looked at is a property of the
    // session, not of the document, so every file opens on Script.LATIN.
    private Script activeScript = Script.LATIN;

    /**
     * False for a legacy document, whose xPos values are absolute positions and are ignored.
     * Always true when saving.
     */
    private boolean hasBeenDynamicallyLaidOut = false;

    /**
     * Version of the in-memory representation (1 legacy, 2 LineElement), distinct from the IO
     * file format version.
     */
    private int formatVersion = 1;

    private boolean modified;

    // Held rather than inlined so the depth counters and the accumulated batch cannot be
    // reached except through the API that keeps them balanced.
    private final ModificationSession modifications = new ModificationSession(this);

    private final TerminalMaintainer terminal = new TerminalMaintainer(this);

    private final TempoResolver tempoResolver = new TempoResolver(this);

    // This song's own linter. Initialized here so every constructor, the parsing stub's
    // included, creates it before anything can build a LintingNotification for this song.
    private final Linter linter = new Linter(this);

    private final MessageSubscription subscription;

    public Song() {
        // Tracking is suspended so that setup changes post no SongDidChangeNotification to global
        // subscribers before this Song is installed in any ScoreView.
        withoutMutationTracking(() -> {
            var initialLine = new Line(this);
            initialLine.setKey(Key.DEFAULT);
            initialLine.addElement(newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            lines.add(initialLine);
        });

        subscription = new MessageSubscription(this);
    }

    public Song(SongData data) {
        loadFrom(data);
        subscription = new MessageSubscription(this);
    }

    public static Song newParsingStub() {
        return new Song(Stub.INSTANCE);
    }

    private enum Stub { INSTANCE }

    private Song(Stub ignored) {
        subscription = new MessageSubscription(this);
    }

    /**
     * Idempotent. See the class's {@code Lifecycle} section.
     */
    @Override
    public void dispose() {
        subscription.dispose();
    }

    @Handler
    public void documentWasSaved(DocumentWasSavedNotification message) {
        modified = false;
    }

    /**
     * Posts no notification; the caller ({@code ScoreView.setSong}) posts a
     * {@link songscribe.message.notification.DocumentDidLoadNotification} once the song is
     * installed.
     *
     * <p>Must run under suspended mutation tracking: the fields are assigned directly, outside
     * any mutation bracket.
     */
    @SuppressWarnings("CallToSimpleSetterFromWithinClass")
    public void loadFrom(SongData data) {
        metadata = new SongMetadata(
            data.title(),
            data.number(),
            data.place(),
            data.date(),
            data.composer(),
            data.lyricist(),
            data.lyricsSource(),
            data.arrangement(),
            data.unofficialTranslation(),
            data.subtitle(),
            data.wordsDate()
        );

        // A legacy .mssw file may carry no <tempo> element, so the parsed value is nullable.
        var loadedTempo = data.tempo();
        tempo = loadedTempo != null ? loadedTempo : new Tempo();
        applyUnderLyrics(data.underLyrics());
        applyBanglaLyrics(data.banglaLyrics());
        applyTranslatedLyrics(data.translatedLyrics());
        applyFootnotes(data.footnotes());

        applyRowHeightAdjustmentSs(data.rowHeightAdjustmentSs());
        applyLineWidthSs(data.lineWidthSs());

        lines.clear();

        var loadedLines = data.lines();

        lines.addAll(loadedLines);

        // Parsing runs under suspended tracking, so no key change reached the per-mutation
        // propagation hook. A legacy file also predates the rule that no key change restates the
        // key in effect before it, so that repair belongs to reading rather than to any edit.
        settleKeysAfterParsing();

        hasBeenDynamicallyLaidOut = data.hasBeenDynamicallyLaidOut();
        formatVersion = data.formatVersion();

        modified = false;
    }

    public Tempo getTempo() {
        return tempo;
    }

    public Tempo getTempoAt(int lineIndex, int noteIndex) {
        return tempoResolver.getTempoAt(lineIndex, noteIndex);
    }

    public BeatUnit beatUnitAt(int lineIndex, int elementIndex) {
        return tempoResolver.beatUnitAt(lineIndex, elementIndex);
    }

    public SongMetadata getMetadata() {
        return metadata;
    }

    public String getTitle() {
        return metadata.title();
    }

    public String getSubtitle() {
        return metadata.subtitle();
    }

    public String getNumberedTitle() {
        return numberedTitle(metadata.number(), metadata.title());
    }

    public static String numberedTitle(String number, String title) {
        if (number.isEmpty()) {
            return title;
        }

        return number + ". " + title;
    }

    public String getPlace() {
        return metadata.place();
    }

    public PartialDate getDate() {
        return metadata.date();
    }

    /**
     * @return {@link PartialDate.EmptyDate} when the words are dated with the music
     */
    public PartialDate getWordsDate() {
        return metadata.wordsDate();
    }

    public LANGUAGE getLanguage() {
        return language;
    }

    public String getLyricsText() {
        var sb = new StringBuilder(1000);

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);

            for (var j = 0; j < line.effectiveElementCount(); j++) {
                var lyric = line.getElement(j).getLyricForScript(activeScript);

                if (lyric == null) {
                    continue;
                }

                sb.append(lyric.text());

                if (lyric.extend() != Lyric.Extend.NONE) {
                    sb.append('_');
                } else {
                    if (lyric.compound()) {
                        sb.append("--");
                    } else {
                        sb.append(Lyric.syllabicContinues(lyric.syllabic()) ? '-' : ' ');
                    }
                }
            }

            if (i < lines.size() - 1 && !sb.isEmpty()) {
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    public String getUnderLyrics() {
        return underLyrics;
    }

    public String getBanglaLyrics() {
        return banglaLyrics;
    }

    public String getTranslatedLyrics() {
        return translatedLyrics;
    }

    public String getFootnotes() {
        return footnotes;
    }

    public boolean isUnofficialTranslation() {
        return metadata.unofficialTranslation();
    }

    public String getComposer() {
        return metadata.composer();
    }

    public String getLyricist() {
        return metadata.lyricist();
    }

    public LyricsSource getLyricsSource() {
        return metadata.lyricsSource();
    }

    public boolean isArrangement() {
        return metadata.arrangement();
    }

    /**
     * The single authoritative derivation of the translation flag; rendering and IO pass this to
     * {@link AttributionFormatter}.
     */
    public boolean showTranslation() {
        return !metadata.unofficialTranslation() && !translatedLyrics.isEmpty();
    }

    public Attribution getAttributionElement() {
        return attributionElement;
    }

    public SongTempoMark getTempoMarkElement() {
        return tempoMarkElement;
    }

    public String getNumber() {
        return metadata.number();
    }

    /**
     * @return {@link Key#DEFAULT} while the song holds no lines, which only a reader part-way
     *         through a load ever sees
     */
    public Key getStartingKey() {
        return lines.isEmpty() ? Key.DEFAULT : lines.getFirst().getRunningKey();
    }

    public boolean isModified() {
        return modified;
    }

    public Linter getLinter() {
        return linter;
    }

    public Script getActiveScript() {
        return activeScript;
    }

    public Set<Script> scripts() {
        var scripts = EnumSet.noneOf(Script.class);

        for (var line : lines) {
            scripts.addAll(line.scripts());
        }

        return Collections.unmodifiableSet(scripts);
    }

    /**
     * Which script is shown is a property of the session, not of the document: selecting one does
     * not mark the song modified, and it adds no undo step.
     */
    public void setActiveScript(Script activeScript) {
        if (activeScript == this.activeScript) {
            return;
        }

        this.activeScript = activeScript;
        MessageCenter.post(new ActiveScriptDidChangeNotification(this));
    }

    public Line getLine(int index) {
        return lines.get(index);
    }

    public ArrayList<Line> getLines() {
        return lines;
    }

    public int lineCount() {
        return lines.size();
    }

    /**
     * @return {@code null} when {@code line} is not one of this song's lines
     */
    @Nullable Integer indexOfLine(Line line) {
        var index = lines.indexOf(line);

        return index < 0 ? null : index;
    }

    public boolean contains(Line line) {
        return indexOfLine(line) != null;
    }

    public boolean isEmpty() {
        return lines.isEmpty() || lines.stream().allMatch(Line::isEmpty);
    }

    public double getRowHeightAdjustmentSs() {
        return rowHeightAdjustmentSs;
    }

    public double getDefaultRestLengthSs() {
        return defaultRestLengthSs;
    }

    public Ss getLineWidthSs() {
        return new Ss(lineWidthSs);
    }

    public int getLineWidthPx() {
        return DocumentScale.ssToPx(lineWidthSs).sizePx();
    }

    public boolean hasBeenDynamicallyLaidOut() {
        return hasBeenDynamicallyLaidOut;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    /**
     * Stays public because the undo replayer and the MusicXML header reader drive it from outside
     * the editing UI.
     */
    public void setTempo(Tempo tempo) {
        mutateMetadata(MetadataField.TEMPO, this.tempo, tempo, () -> this.tempo = tempo);
    }

    /**
     * @return {@code null} when the song has no lines or every line is empty
     */
    public @Nullable Integer firstNonEmptyLineIndex() {
        for (var lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            if (!lines.get(lineIndex).isEmpty()) {
                return lineIndex;
            }
        }

        return null;
    }

    /**
     * @return {@code null} when every line is empty or the song has no lines
     */
    public @Nullable StaffElement firstElement() {
        var lineIndex = firstNonEmptyLineIndex();

        return lineIndex == null ? null : lines.get(lineIndex).getElement(0);
    }

    /**
     * The record must already be normalized; this method does not re-normalize.
     */
    public void setMetadata(SongMetadata newMetadata) {
        if (metadata.equals(newMetadata)) {
            return;
        }

        var oldMetadata = metadata;
        withModification(() -> applyChange(
            new MetadataChange(MetadataField.ATTRIBUTION, oldMetadata, newMetadata),
            () -> metadata = newMetadata
        ));
    }

    public void setUnderLyrics(String text) {
        var newLyrics = StringUtils.processText(text, true);
        mutateLyrics(LyricsField.UNDER, underLyrics, newLyrics, () -> underLyrics = newLyrics);
    }

    public void setBanglaLyrics(String text) {
        var newLyrics = text.trim();
        mutateLyrics(LyricsField.BANGLA, banglaLyrics, newLyrics, () -> banglaLyrics = newLyrics);
    }

    public void setTranslatedLyrics(String text) {
        var newLyrics = StringUtils.processText(text, false);
        mutateLyrics(
            LyricsField.TRANSLATED, translatedLyrics, newLyrics,
            () -> translatedLyrics = newLyrics);
    }

    public void setFootnotes(String text) {
        var newFootnotes = StringUtils.processText(text, false);
        mutateMetadata(MetadataField.FOOTNOTES, footnotes, newFootnotes, () -> footnotes = newFootnotes);
    }

    private static final Pattern BLANK_LINE_RUN = Pattern.compile("(\\s*\\n){2,}");

    /**
     * Normalizes a person's name for storage and display. Still public because
     * {@link SongIO} uses it when parsing legacy files.
     *
     * @param text the raw name, as typed or read from storage
     * @return the trimmed name, with every run of blank lines collapsed to a single newline;
     *     never empty — {@link #SRI_CHINMOY} when {@code text} carries no other content
     * @invariant idempotent: {@code coercePerson(coercePerson(text))} equals
     *     {@code coercePerson(text)}
     */
    public static String coercePerson(String text) {
        var trimmed = text.trim();
        var collapsed = BLANK_LINE_RUN.matcher(trimmed).replaceAll("\n");
        return collapsed.isEmpty() ? SRI_CHINMOY : collapsed;
    }

    public void setRowHeightAdjustmentSs(double rowHeightAdjustment) {
        mutateLayout(
            LayoutField.ROW_HEIGHT_ADJUSTMENT_SS, rowHeightAdjustmentSs, rowHeightAdjustment,
            () -> rowHeightAdjustmentSs = rowHeightAdjustment
        );
    }

    /**
     * Records no mutation: written only by document load.
     */
    public void setDefaultRestLengthSs(double defaultRestLength) {
        defaultRestLengthSs = Math.max(MIN_DEFAULT_REST_LENGTH_SS, defaultRestLength);
    }

    /**
     * Use {@link songscribe.ui.component.ScoreView#updatePageLayout}, which also re-lays out the
     * page; writing the width here alone leaves the page laid out for a different width.
     */
    public void setLineWidthSs(Ss lineWidth) {
        var lineWidthValueSs = lineWidth.value();
        mutateLayout(
            LayoutField.LINE_WIDTH_SS, lineWidthSs, lineWidthValueSs, () -> lineWidthSs = lineWidthValueSs);
    }

    /**
     * {@code current} and {@code newValue} compare by value, so that setting an unchanged value
     * records nothing.
     */
    private void mutateMetadata(
        MetadataField field, @Nullable Object current, @Nullable Object newValue, Runnable apply
    ) {
        if (Objects.equals(current, newValue)) {
            return;
        }

        withModification(() -> applyChange(new MetadataChange(field, current, newValue), apply));
    }

    private void mutateLayout(LayoutField field, double current, double newValue, Runnable apply) {
        if (current == newValue) {
            return;
        }

        withModification(() -> applyChange(new LayoutChange(field, current, newValue), apply));
    }

    private void mutateLyrics(LyricsField field, String current, String newValue, Runnable apply) {
        if (current.equals(newValue)) {
            return;
        }

        withModification(Strings.get(Strings.ACTION_EDIT_OP_CHANGE_LYRICS),
            () -> applyChange(new LyricsChange(field, current, newValue), apply));
    }

    public void addLine(Line line) {
        addLine(lines.size(), line);
    }

    /**
     * When the insertion makes {@code line} the new last line, the terminal invariant transfers to
     * it, so the last element of the last line is always a valid terminal. The transfer is skipped
     * when mutation tracking is suspended, so test setup can install lines with arbitrary
     * terminals.
     *
     * <p>A tie on the last note of a line ends at the start of the next line, so the insertion can
     * move the end of a tie crossing out of the line before {@code line} or out of {@code line};
     * the tie rule ({@link TieRule}) judges both lines.
     */
    public void addLine(int index, Line line) {
        if (line.getSong() != this) {
            throw new IllegalArgumentException("Line must be constructed with this Song");
        }

        var willBecomeNewLast = index == lines.size();
        var previousLastLine = lines.isEmpty() ? null : lines.getLast();

        withModification(() -> modifications.withAutoMaintenance(() -> {
            applyChange(new LineInsertion(index, line), () -> lines.add(index, line));

            if (willBecomeNewLast && !isMutationTrackingSuspended() && !isReplaying()) {
                terminal.maintainOnLastLineChange(previousLastLine, line);
            }
        }));
    }

    /**
     * A normally-tracked removal always leaves the song with at least one line: removing the sole
     * line inserts a fresh empty one, and removing the last line installs the terminal on the new
     * last line. Both are skipped when mutation tracking is suspended or while replaying, which
     * legitimately pass through a transient zero-line state.
     *
     * <p>The removed line keeps its elements, and they keep pointing at it, so undo can put it
     * back unchanged. A tie ending on the removed line's first note is untied first
     * ({@link Line#untieEndingIn}), recorded and not reported; the tie rule ({@link TieRule})
     * judges a tied last note of the line before whose walk found no note in the removed line.
     */
    public void removeLine(int index) {
        var deletedLine = lines.get(index);
        var wasLast = index == lines.size() - 1;

        withModification(() -> modifications.withAutoMaintenance(() -> {
            // Recorded before the deletion, so reverse-order undo puts the line back before it
            // re-ties the anchor.
            if (deletedLine.elementCount() > 0) {
                deletedLine.untieEndingIn(new StaffElementRun.ElementRange(0, deletedLine.elementCount() - 1));
            }

            applyChange(
                new LineDeletion(index, deletedLine),
                () -> lines.remove(index)
            );

            if (wasLast && !isMutationTrackingSuspended() && !isReplaying()) {
                if (!lines.isEmpty()) {
                    terminal.maintainOnLastLineChange(null, lines.getLast());
                } else {
                    addLine(0, new Line(this));
                }
            }
        }));
    }

    /**
     * Must be called while mutation tracking is still suspended, so the fix-up is silent. A no-op
     * when the last line already ends with a valid terminal, so a span ending on that barline
     * keeps its exact element reference.
     */
    public void installTerminalAfterParsing() {
        terminal.installAfterParsing();
    }

    /**
     * A reader calls this once its grace-host pairs are settled. Runs silently whether or not
     * tracking is suspended, because the legacy reader calls it after tracking has resumed.
     */
    public void repairGraceHostMelismasAfterParsing() {
        withoutMutationTracking(() -> lines.forEach(Line::repairGraceHostMelismas));
    }

    static StaffElement newTerminalElement(ElementType type) {
        return TerminalMaintainer.newTerminalElement(type);
    }

    public boolean isTerminal(StaffElement element, Line line) {
        return terminal.isTerminal(element, line);
    }

    /**
     * Returns {@code false} for an element that belongs to no line. That is right for an
     * applicability predicate but wrong for an invariant check, which must not treat "cannot
     * tell" as "safe": a check inside the DOM must use {@link #isTerminal(StaffElement, Line)}.
     */
    public static boolean isTerminalOfItsSong(StaffElement element) {
        var line = element.getParentLine();
        return line != null && line.getSong().isTerminal(element, line);
    }

    public void checkTerminalPolicyForInsertion(Line line, int index, ElementType incoming) {
        terminal.checkTerminalPolicyForInsertion(line, index, incoming);
    }

    public void checkTerminalPolicyForReplacement(Line line, int index, ElementType incoming) {
        terminal.checkTerminalPolicyForReplacement(line, index, incoming);
    }

    public void checkTerminalPolicyForRemoval(Line line, StaffElementRun.ElementRange range) {
        terminal.checkTerminalPolicyForRemoval(line, range);
    }

    public ElementType currentTerminalType() {
        return terminal.currentTerminalType();
    }

    public boolean canReplaceTerminal(ElementType incomingType) {
        return terminal.canReplaceTerminal(incomingType);
    }

    public void replaceTerminal(ElementType incomingType) {
        terminal.replaceTerminal(incomingType);
    }

    public boolean isModifying() {
        return modifications.isModifying();
    }

    public boolean isMutationTrackingSuspended() {
        return modifications.isMutationTrackingSuspended();
    }

    boolean isInAutoMaintenance() {
        return modifications.isInAutoMaintenance();
    }

    public boolean isReplaying() {
        return modifications.isReplaying();
    }

    /**
     * The caller is asserting that this run owes no undo entry of its own: it is not a user
     * edit, or the entry for it is recorded elsewhere, or it must leave none. An edit that owes
     * its own entry goes through {@link #withModification(Runnable)}.
     */
    public void withoutMutationTracking(Runnable body) {
        modifications.withoutMutationTracking(body);
    }

    public <T> T withoutMutationTrackingResult(Supplier<T> body) {
        modifications.beginSuspendMutationTracking();

        try {
            return body.get();
        } finally {
            modifications.endSuspendMutationTracking();
        }
    }

    /**
     * The edit closes while replay mode is still in force, so no modification rule runs over the
     * replayed batch, which already carries every removal the rules made when it was first
     * recorded. Replay mode outside and the edit inside is what keeps undo and redo from judging
     * the song a second time.
     */
    public void replay(Runnable body) {
        modifications.replay(body);
    }

    /**
     * Only for a suspension scope that crosses methods, as in the two file readers; otherwise use
     * {@link #withoutMutationTracking(Runnable)}.
     */
    public void beginSuspendMutationTracking() {
        modifications.beginSuspendMutationTracking();
    }

    public void endSuspendMutationTracking() {
        modifications.endSuspendMutationTracking();
    }

    /**
     * Executes {@code body} inside a modification bracket, then posts a single
     * {@link SongDidChangeNotification} with all accumulated mutations. The bracket is
     * closed even if {@code body} throws, without running the modification rules over the
     * half-applied edit. Brackets nest; the notification fires only when the outermost one
     * closes.
     *
     * <p>Every state change {@code body} makes must be recorded as a {@link Mutation} in this
     * bracket's batch, through {@link #applyChange} or a {@code Line} helper wrapping it. Undo
     * replays the recorded batch mechanically, so a change made outside that route — a raw
     * {@code spans.removeIf}, a field written directly — is invisible to it and makes the round
     * trip lossy. The failure is silent: the batch still replays cleanly, with the unrecorded
     * change simply missing. A helper that drops dependent state therefore routes each removal
     * through the typed tracked helper {@code Line.removeInvalidatedSpan} dispatches to, so the
     * proper removal mutation lands in the batch.
     *
     * @invariant every state change made inside the bracket is recorded as a mutation in its batch
     */
    public void withModification(Runnable body) {
        modifications.withModification(body);
    }

    public <T> T withModificationResult(Supplier<T> body) {
        return modifications.withModificationResult(body);
    }

    /**
     * The label is captured only if this is the outermost bracket: a labeled bracket nested
     * inside an open one never replaces the op-name the outermost bracket captured.
     * {@code body} carries the same recording obligation stated on
     * {@link #withModification(Runnable)}.
     */
    public void withModification(String label, Runnable body) {
        modifications.withModification(label, body);
    }

    /**
     * The mutations that subscribers make in response coalesce into one
     * {@link SongDidChangeNotification}.
     */
    public void postWithModification(Message message) {
        modifications.postWithModification(message);
    }

    public void postWithModification(String label, Message message) {
        modifications.postWithModification(label, message);
    }

    /**
     * Applies a single mutation within an open modification bracket: runs {@code mutator},
     * then records {@code mutation} in the accumulated batch. Under suspended tracking the
     * mutator runs and nothing is recorded.
     *
     * <p>Every mutation that can move a key — a line's own key, a mid-line key change, or a
     * line insertion or deletion that shifts what a line inherits — brings the key invariant
     * back up to date here, after the mutator has run. This is the only place that does it, and
     * that is deliberate: the ten mutators that can carry a key each route through this one
     * method, undo and redo replay through it too, and an omission at any individual mutator
     * would be wrong pitches on every line downstream with no error and nothing visible to
     * notice. An ordinary note edit costs one type test and no walk.
     *
     * <p>Every recorded change is then followed by the chain walk ({@link AccidentalChain#walk}
     * over {@link ChainEdit#of the edit the mutation made}), so no edit decides accidental
     * states and none can skip it: each restamp the walk returns is recorded here as an
     * {@link ElementField#ACCIDENTAL} modification of its note, inside the same bracket. The
     * walk runs after the key invariant because it reads the keys the invariant maintains. It
     * does not run on a replay of undo or redo, which applies the restamps the batch already
     * recorded, nor under suspended tracking, where nothing is recorded, which leaves a file
     * reader's notes for the load pass and makes grace mode's untracked insertion walk itself.
     *
     * @throws IllegalStateException if mutation tracking is on and no modification bracket is
     *     open; under suspended tracking no bracket is required
     * @see ModificationSession#applyChange
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        // Captured before the mutator runs: a line insertion or deletion can promote a different
        // line to index 0, and only the key the song started in can tell the repair below what
        // that line should now establish. Null while a document is being built, where line 0 may
        // not be in a key yet — the load settles that at the end, in one pass.
        var startingKeyBefore = promotesLineZero(mutation) ? startingKey() : null;

        modifications.applyChange(mutation, mutator);

        maintainKeyInvariant(mutation, startingKeyBefore);

        if (isReplaying() || isMutationTrackingSuspended()) {
            return;
        }

        var edit = ChainEdit.of(mutation);

        if (edit == null) {
            return;
        }

        for (var restamp : AccidentalChain.walk(this, edit)) {
            var line = restamp.line();
            line.modifyElement(line.getElementIndex(restamp.note()), ElementField.ACCIDENTAL, restamp::stamp);
        }
    }

    private static boolean promotesLineZero(Mutation mutation) {
        return mutation instanceof LineInsertion || mutation instanceof LineDeletion;
    }

    private @Nullable Key startingKey() {
        if (lines.isEmpty()) {
            return null;
        }

        var firstLine = lines.getFirst();
        var ownKey = firstLine.getKey();
        return ownKey != null ? ownKey : inheritedKeys.get(firstLine);
    }

    /**
     * Distinct from {@link Line#getRunningKey()}: {@link Line#setKey}'s no-op normalization needs
     * the inherited value, not the line's own.
     *
     * @return null when nothing precedes it — line 0, a line not in this song, or any line while a
     *         load is still in progress
     */
    @Nullable
    Key inheritedKeyOf(Line line) {
        return inheritedKeys.get(line);
    }

    private void maintainKeyInvariant(Mutation mutation, @Nullable Key startingKeyBefore) {
        switch (mutation) {
            case LineInsertion _ -> repairLineZeroKey(startingKeyBefore);
            case LineDeletion deletion -> {
                // A line out of the song inherits from nothing, so it must not go on answering
                // with what it inherited while it was in one. Dropping the entry also bounds the
                // line's lifetime: LineDeletion holds the line for undo, and without this the map
                // would keep it reachable for the song's whole life after that record is gone.
                inheritedKeys.remove(deletion.deletedLine());
                repairLineZeroKey(startingKeyBefore);
            }
            default -> {
                // Only a change to which lines exist can leave line 0 without a key of its own.
            }
        }

        var keyMove = keyMoveOf(mutation);

        if (keyMove != null) {
            propagateInheritedKeys(keyMove);
        }
    }

    /**
     * @param originIndex    the line {@link #inheritanceFrom} is asked about
     * @param reachFromIndex the first line {@link #keyMoveReach} names, at or before
     *                       {@code originIndex}: a line whose start changes also changes the
     *                       cautionary on the line before it
     */
    private record KeyMove(int originIndex, int reachFromIndex) {
    }

    /**
     * The one enumeration of the mutations that can move a key, read by
     * {@link #maintainKeyInvariant} and {@link #keyMoveReach}; a second would drift out of step
     * and show as a line rendering the key it was in before the edit.
     *
     * @return {@code null} when {@code mutation} moves no key, including one naming a line no
     *     longer in this song or leaving no line to re-derive from
     */
    private @Nullable KeyMove keyMoveOf(Mutation mutation) {
        return switch (mutation) {
            case LineInsertion insertion -> lineStartKeyMove(insertion.line());
            case LineDeletion deletion -> departingLineKeyMove(deletion.lineIndex());
            case LineKeyChange change -> lineStartKeyMove(change.line());
            case ElementInsertion insertion -> midLineKeyMove(
                insertion.line(),
                changesKey(insertion.element()));
            case ElementDeletion deletion -> midLineKeyMove(
                deletion.line(),
                deletion.deletedElements().stream().anyMatch(Song::changesKey));
            case ElementReplacement replacement -> midLineKeyMove(
                replacement.line(),
                changesKey(replacement.oldElement()) || changesKey(replacement.newElement()));
            case ElementModification modification -> midLineKeyMove(
                modification.line(),
                changesKey(modification.beforeElement()) || changesKey(modification.afterElement()));
            default -> null;
        };
    }

    private @Nullable KeyMove lineStartKeyMove(Line line) {
        if (!contains(line)) {
            return null;
        }

        var lineIndex = line.index();
        return new KeyMove(lineIndex, Math.max(lineIndex - 1, 0));
    }

    /**
     * @return null when no line stands where the chain is re-derived from: the song is now empty,
     *     or a later mutation in the same batch has since shortened it
     */
    private @Nullable KeyMove departingLineKeyMove(int lineIndex) {
        var originIndex = Math.max(lineIndex - 1, 0);

        if (originIndex >= lines.size()) {
            return null;
        }

        return new KeyMove(originIndex, originIndex);
    }

    private @Nullable KeyMove midLineKeyMove(Line line, boolean movesKey) {
        if (!movesKey) {
            return null;
        }

        if (!contains(line)) {
            return null;
        }

        var lineIndex = line.index();
        return new KeyMove(lineIndex, lineIndex);
    }

    /**
     * Returns the lines whose drawn key content {@code mutation}'s key move can have changed: the
     * line the move is felt from, the line before it when the move changes the key that line's
     * cautionary key signature warns of, and every following line that runs in the key it
     * inherits, through the end of the inheritance chain. Empty when the mutation moves no key.
     *
     * <p>The set is the whole chain rather than exactly the lines whose running key moved, because
     * it follows the same stopping rule propagation runs on. A line in it whose key did not move
     * draws what it drew before, so re-solving it costs work and never draws anything wrong.
     *
     * <p>A view holding per-line geometry needs this. A line's header signature and the spacing
     * around it are solved from the key that line runs in, and the room kept clear at its end is
     * solved from the cautionary it leads into — so a key move leaves stale geometry on lines no
     * mutation names, and the line ahead of the move is one of them.
     *
     * <p>Asked after the mutation has been applied, so the answer describes the document as it now
     * stands, which is what a stale view has to be brought up to.
     *
     * <p>A line insertion or deletion answers here too, but no view need ask: it changes which
     * lines exist, which costs a rebuild of every line rather than an invalidation of some.
     *
     * @param mutation the mutation to ask about
     * @return the lines, in song order, or empty when {@code mutation} moves no key
     */
    public List<Line> keyMoveReach(Mutation mutation) {
        var keyMove = keyMoveOf(mutation);

        if (keyMove == null) {
            return List.of();
        }

        var origin = lines.get(keyMove.originIndex());
        var reach = new ArrayList<>(lines.subList(keyMove.reachFromIndex(), keyMove.originIndex() + 1));

        for (var inheritance : inheritanceFrom(origin, origin.keyAtEndOfLine()).inheriting()) {
            reach.add(inheritance.line());
        }

        return List.copyOf(reach);
    }

    /**
     * Returns the lines, other than the one {@code mutation} names, whose drawn ties it can
     * change.
     *
     * <p>A line draws the half of a tie that crosses into it from the line before it, so an edit
     * near one edge of a line can change what the line beside it draws while the mutation's own
     * record names only its own line. Deleting a tied last note, inserting a same-pitch note
     * after an anchor, and flipping the stem of a note a crossing tie ends on are each felt this
     * way.
     *
     * <p>A mutation names an origin in its line L: a run of positions from {@code first} to
     * {@code last}. An insertion's, a replacement's or a modification's index, or a deletion's
     * first index, is the origin when no beam of L contains it; when one does, the origin is
     * that beam's whole range, because a beamed note's stem follows its beam, so a change to
     * one member can turn the stem of the member at L's edge. A {@link TieAddition}'s or
     * {@link TieRemoval}'s anchor index is the origin of a tie record, and a {@link Beam}'s range
     * is the origin of a {@link SpanAddition} or {@link SpanRemoval} of that beam. It reaches:
     * <ul>
     *   <li>L's next line, when there is one and {@code last} is at or after L's last element
     *       that is not a legal separator ({@link Tie#isLegalSeparator}), or L has no such
     *       element;</li>
     *   <li>L's previous line, when there is one and {@code first} is at or before L's first
     *       such element, or L has none.</li>
     * </ul>
     *
     * @param mutation the mutation to ask about
     * @return the lines, in no promised order, or empty when {@code mutation} names no origin —
     *     any other kind of mutation, a span record of a span that is not a beam, a beam record
     *     whose endpoints are no longer both in the line it names, or a tie record whose anchor
     *     is no longer in that line — or names one too far from either edge to reach a
     *     neighboring line
     */
    public List<Line> tieReach(Mutation mutation) {
        var origin = tieReachOrigin(mutation);

        if (origin == null) {
            return List.of();
        }

        var line = origin.line();
        var elements = line.getElements();
        var firstNonSeparator = Tie.firstNonSeparatorIndexFrom(elements, 0);
        var lastNonSeparator = Tie.lastNonSeparatorIndexBefore(elements, elements.size());
        var reach = new ArrayList<Line>();

        if (!line.isLast() && (lastNonSeparator == null || origin.last() >= lastNonSeparator)) {
            reach.add(line.nextLine());
        }

        if (!line.isFirst() && (firstNonSeparator == null || origin.first() <= firstNonSeparator)) {
            reach.add(line.previousLine());
        }

        return List.copyOf(reach);
    }

    private static @Nullable TieReachOrigin tieReachOrigin(Mutation mutation) {
        return switch (mutation) {
            case ElementInsertion insertion -> elementOrigin(insertion.line(), insertion.index());
            case ElementReplacement replacement -> elementOrigin(replacement.line(), replacement.index());
            case ElementDeletion deletion -> elementOrigin(deletion.line(), deletion.range().begin());
            case ElementModification modification -> elementOrigin(modification.line(), modification.index());
            case TieAddition(var line, var anchor) -> tieAnchorOrigin(line, anchor);
            case TieRemoval(var line, var anchor) -> tieAnchorOrigin(line, anchor);
            case SpanAddition(var line, var span) -> beamOrigin(line, span);
            case SpanRemoval(var line, var span) -> beamOrigin(line, span);
            default -> null;
        };
    }

    private static TieReachOrigin elementOrigin(Line line, int position) {
        var beam = line.spanRegistry().findBeamAt(position);

        if (beam == null) {
            return new TieReachOrigin(line, position, position);
        }

        var range = beam.range();

        return new TieReachOrigin(line, range.begin(), range.end());
    }

    private static @Nullable TieReachOrigin tieAnchorOrigin(Line line, NoteElement anchor) {
        if (!line.contains(anchor)) {
            return null;
        }

        var index = line.getElementIndex(anchor);

        return new TieReachOrigin(line, index, index);
    }

    private static @Nullable TieReachOrigin beamOrigin(Line line, Span<?> span) {
        if (!(span instanceof Beam beam)) {
            return null;
        }

        var anchor = beam.getAnchorElement();
        var end = beam.getEndElement();

        if (!line.contains(anchor) || !line.contains(end)) {
            return null;
        }

        return new TieReachOrigin(line, line.getElementIndex(anchor), line.getElementIndex(end));
    }

    private record TieReachOrigin(Line line, int first, int last) {
    }

    private static boolean changesKey(StaffElement element) {
        return element.getType().isKeyChange();
    }

    /**
     * Recorded as a {@link LineKeyChange} in the same batch, so undo puts the line back to
     * inheriting. Skipped while mutation tracking is suspended: a file reader adds its lines one
     * at a time, and a key guessed here would stand, because
     * {@link #rebuildInheritedKeysAfterParsing()} leaves a line that already has one alone.
     */
    private void repairLineZeroKey(@Nullable Key startingKeyBefore) {
        if (lines.isEmpty() || isMutationTrackingSuspended()) {
            return;
        }

        var firstLine = lines.getFirst();

        if (firstLine.getKey() != null) {
            inheritedKeys.remove(firstLine);
            return;
        }

        var inherited = inheritedKeys.get(firstLine);
        Key materialized;

        if (inherited != null) {
            materialized = inherited;
        } else if (startingKeyBefore != null) {
            materialized = startingKeyBefore;
        } else {
            materialized = Key.DEFAULT;
        }

        // Cleared first so setKey's no-op normalization cannot collapse the repair back to null.
        inheritedKeys.remove(firstLine);
        firstLine.setKey(materialized);
    }

    private void propagateInheritedKeys(KeyMove keyMove) {
        // A document under construction may not have put line 0 in a key yet, and until it does
        // there is no key for any later line to be in either. The load settles the whole list in
        // one pass at the end.
        if (lines.isEmpty() || lines.getFirst().getKey() == null) {
            return;
        }

        var originIndex = keyMove.originIndex();
        var origin = lines.get(originIndex);

        // Line 0 inherits nothing. Any other origin is refreshed whether or not it has a new
        // predecessor: the entry is derived, so refreshing one that did not move stores what was
        // already there.
        if (originIndex > 0) {
            inheritedKeys.put(origin, lines.get(originIndex - 1).keyAtEndOfLine());
        }

        var chain = inheritanceFrom(origin, origin.keyAtEndOfLine());

        for (var inheritance : chain.inheriting()) {
            inheritedKeys.put(inheritance.line(), inheritance.inheritedKey());
        }

        var stop = chain.stop();

        if (stop != null) {
            inheritedKeys.put(stop.line(), stop.inheritedKey());
        }
    }

    /**
     * @param line establishes no key of its own
     */
    public record Inheritance(Line line, Key inheritedKey) {

        public Key keyAtEnd() {
            return line.keyAtEndUnder(inheritedKey);
        }
    }

    /**
     * @param inheriting consecutive and in song order; empty when the line after the origin has a
     *                   key of its own or the origin is the last line
     * @param stop       null when the inheriting lines run to the end of the song
     */
    public record InheritanceChain(List<Inheritance> inheriting, @Nullable Stop stop) {

        public InheritanceChain {
            inheriting = List.copyOf(inheriting);
        }

        /**
         * @param inheritedKey the key the line before it leaves off in, which the line runs in the
         *                     moment it gives up its own key
         */
        public record Stop(Line line, Key inheritedKey, Key ownKey) {
        }
    }

    /**
     * Returns the inheritance chain after {@code origin}, supposing {@code origin} leaves off in
     * {@code originEndKey}: every line after it that runs in the key it inherits, each with that
     * key, and the first line after them that establishes a key of its own.
     *
     * <p>This is the stopping rule the whole feature runs on — <em>forward to the first line with
     * its own key</em> — stated once: that line's running key cannot move, so nothing past it can
     * either. The line that stops the chain is still reported, because what it inherits is kept
     * current too: giving that line a key is compared against it, and clearing that line's key
     * makes it the key the line runs in.
     *
     * <p>Pure: it reads each line's own key and elements and never the inherited keys this song
     * keeps. A caller projecting an edit that has not happened yet therefore gets the chain the
     * edit will leave, provided the edit changes nothing after {@code origin} but the key
     * {@code origin} leaves off in — not a later line's own key, its elements, or which lines
     * exist.
     *
     * @param origin       the line the chain starts after; one of this song's lines
     * @param originEndKey the key to suppose {@code origin} leaves off in
     * @return the chain; with no inheriting lines and no stop when {@code origin} is the last line
     * @invariant the inheriting lines, then the stop, are consecutive, the first being the line
     *     after {@code origin}
     * @invariant the first line reported inherits {@code originEndKey}
     * @invariant each later line reported inherits the {@link Inheritance#keyAtEnd() end key} of
     *     the inheriting line before it
     */
    public InheritanceChain inheritanceFrom(Line origin, Key originEndKey) {
        var inheriting = new ArrayList<Inheritance>();
        var inheritedKey = originEndKey;

        for (var lineIndex = origin.index() + 1; lineIndex < lines.size(); lineIndex++) {
            var line = lines.get(lineIndex);
            var ownKey = line.getKey();

            if (ownKey != null) {
                return new InheritanceChain(inheriting, new InheritanceChain.Stop(line, inheritedKey, ownKey));
            }

            var inheritance = new Inheritance(line, inheritedKey);
            inheriting.add(inheritance);
            inheritedKey = inheritance.keyAtEnd();
        }

        return new InheritanceChain(inheriting, null);
    }

    /**
     * File readers build lines under suspended mutation tracking, so the per-mutation
     * propagation in {@link #applyChange} never ran for any of them.
     *
     * <p>Must be called while mutation tracking is still suspended, so the fix-up is silent — no
     * notification, no undo entry and no {@code modified} flag.
     *
     * <p>Line 0 has nothing to inherit from, so a document whose first line establishes no key of
     * its own falls back to {@link Key#DEFAULT}. Every reader is expected to have put the key its
     * file carried on line 0 before calling this — the fallback is for a file that named no key
     * anywhere, not the route by which a stated one arrives.
     */
    public void rebuildInheritedKeysAfterParsing() {
        // Emptied rather than overwritten: Song.loadFrom replaces the line list wholesale, without
        // a LineDeletion per line, so entries for the lines it discarded have nothing else to take
        // them out and would keep those lines reachable for the rest of the song's life.
        inheritedKeys.clear();

        if (lines.isEmpty()) {
            return;
        }

        var firstLine = lines.getFirst();

        if (firstLine.getKey() == null) {
            firstLine.setKey(Key.DEFAULT);
        }

        for (var lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            inheritedKeys.put(lines.get(lineIndex), lines.get(lineIndex - 1).keyAtEndOfLine());
        }
    }

    /**
     * One of the two places the stranded-key-change rule is enforced: an edit reaches only what
     * its own key move strands, so only a read sees a stranding an older document holds.
     * Removing a stranded key change leaves the chain {@link #rebuildInheritedKeysAfterParsing()}
     * settled intact, so no second pass is owed.
     */
    private void removeStrandedKeyChangesAfterParsing() {
        for (var line : lines) {
            line.deleteRanges(line.redundantKeyChangeRanges(line.getRunningKey()));
        }
    }

    /**
     * One call because the order is not the reader's to choose: the stranding repair reads each
     * line's running key, which only the inheritance pass settles. Must be called while mutation
     * tracking is still suspended, so it is silent.
     */
    public void settleKeysAfterParsing() {
        rebuildInheritedKeysAfterParsing();
        removeStrandedKeyChangesAfterParsing();
    }

    /**
     * A freshly parsed song needs this once. It is read under suspended mutation tracking, where
     * no chain walk runs, so its notes are {@link AccidentalState.Chosen Chosen} where the file
     * writes an accidental and {@link AccidentalState.Adopting Adopting} where it is bare, while
     * every edit afterwards assumes a note on a line holds a {@link ChainedAccidental}.
     * The walk reads each line's running key, so the inherited keys must be settled first — see
     * {@link #rebuildInheritedKeysAfterParsing()}.
     *
     * <p>The stamps are recorded nowhere — no mutation, no undo entry — because they establish the
     * state the document is read in rather than editing it.
     */
    public void stampAccidentalsAfterParsing() {
        AccidentalChain.walkSong(this).forEach(AccidentalChain.Restamp::stamp);
    }

    /**
     * @param change names lines of this song
     * @see ModificationSession#report
     */
    public void report(RecordedChange change) {
        modifications.report(change);
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public void setHasBeenDynamicallyLaidOut(boolean hasBeenDynamicallyLaidOut) {
        this.hasBeenDynamicallyLaidOut = hasBeenDynamicallyLaidOut;
    }

    public void setFormatVersion(int formatVersion) {
        this.formatVersion = formatVersion;
    }

    @Handler
    public void metadataDidChange(SongMetadataDidChangeNotification update) {
        setMetadata(update.getMetadata());
    }

    @Handler
    public void tempoDidChange(TempoDidChangeNotification update) {
        // Capture in a local so the lambda below closes over what the song holds now rather
        // than re-reading a field the replayer may have reassigned.
        var currentTempo = tempo;

        // An update whose values already match must not dirty the undo step — the settings
        // dialog seeds its widgets from getTempo(), so confirming it unedited resends exactly
        // what is already there.
        var newTempo = update.getTempo();

        if (currentTempo.equals(newTempo)) {
            return;
        }

        withModification(() -> applyChange(
            new MetadataChange(MetadataField.TEMPO, currentTempo, newTempo),
            () -> tempo = newTempo
        ));
    }

    @Handler
    public void layoutDidChange(LayoutDidChangeNotification update) {
        withModification(() -> {
            if (update.getRowHeightAdjustmentSs() != null) {
                setRowHeightAdjustmentSs(update.getRowHeightAdjustmentSs());
            }

            if (update.getLineWidthSs() != null) {
                setLineWidthSs(new Ss(update.getLineWidthSs()));
            }
        });
    }

    private void applyUnderLyrics(String text) {
        underLyrics = StringUtils.processText(text, true);
    }

    private void applyBanglaLyrics(String text) {
        banglaLyrics = text.trim();
    }

    private void applyTranslatedLyrics(String text) {
        translatedLyrics = StringUtils.processText(text, false);
    }

    private void applyFootnotes(String text) {
        footnotes = StringUtils.processText(text, false);
    }

    private void applyRowHeightAdjustmentSs(double rowHeightAdjustment) {
        rowHeightAdjustmentSs = rowHeightAdjustment;
    }

    private void applyLineWidthSs(double lineWidth) {
        lineWidthSs = lineWidth;
    }

}
