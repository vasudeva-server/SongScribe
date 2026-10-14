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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.lifecycle.Disposable;
import songscribe.lint.Linter;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.SongData;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LayoutField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.LyricsField;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.ActiveScriptDidChangeNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.LayoutDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.SongMetadataDidChangeNotification;
import songscribe.message.notification.TempoDidChangeNotification;
import songscribe.util.StringUtils;
import songscribe.util.StringUtils.ShortA;

/**
 * The model for data that is read from and written to SongScribe files.
 *
 * <h2>Lifecycle</h2>
 * A {@code Song} subscribes itself to the message bus in every constructor. MBassador holds
 * listeners weakly, so a discarded {@code Song} keeps responding to broadcasts, and posting
 * spurious undo steps, until it is garbage-collected: call {@link #dispose()} to detach it
 * deterministically ({@code ScoreView.setSong} does when replacing the active song).
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

    private final List<Line> linesView = Collections.unmodifiableList(lines);

    private final KeyInheritance keyInheritance = new KeyInheritance(this);

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
        underLyrics = normalizeUnderLyrics(data.underLyrics());
        banglaLyrics = normalizeBanglaLyrics(data.banglaLyrics());
        translatedLyrics = normalizeTranslatedLyrics(data.translatedLyrics());
        footnotes = normalizeFootnotes(data.footnotes());

        rowHeightAdjustmentSs = data.rowHeightAdjustmentSs();
        lineWidthSs = data.lineWidthSs();

        lines.clear();

        var loadedLines = data.lines();

        lines.addAll(loadedLines);

        // Parsing runs under suspended tracking, so no key change reached the per-mutation
        // propagation hook. A legacy file also predates the rule that no key change restates the
        // key in effect before it, so that repair belongs to reading rather than to any edit.
        keyInheritance.settleAfterParsing();

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

    public KeyInheritance keyInheritance() {
        return keyInheritance;
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

    /**
     * Read-only, because outside this class every change to which lines exist goes through
     * {@link #addLine} or {@link #removeLine}: a change made around them records no undo step and leaves the
     * inherited keys and the terminal element wrong.
     *
     * @return a live view, which follows later additions and removals
     */
    public List<Line> getLines() {
        return linesView;
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
        var newLyrics = normalizeUnderLyrics(text);
        mutateLyrics(LyricsField.UNDER, underLyrics, newLyrics, () -> underLyrics = newLyrics);
    }

    public void setBanglaLyrics(String text) {
        var newLyrics = normalizeBanglaLyrics(text);
        mutateLyrics(LyricsField.BANGLA, banglaLyrics, newLyrics, () -> banglaLyrics = newLyrics);
    }

    public void setTranslatedLyrics(String text) {
        var newLyrics = normalizeTranslatedLyrics(text);
        mutateLyrics(
            LyricsField.TRANSLATED, translatedLyrics, newLyrics,
            () -> translatedLyrics = newLyrics);
    }

    public void setFootnotes(String text) {
        var newFootnotes = normalizeFootnotes(text);
        mutateMetadata(MetadataField.FOOTNOTES, footnotes, newFootnotes, () -> footnotes = newFootnotes);
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
     * line insertion or deletion that shifts what a line inherits — brings the
     * {@link KeyInheritance key invariant} back up to date here, after the mutator has run. This
     * is the only place that does it, and that is deliberate: the ten mutators that can carry a key each route through this one
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
        var startingKeyBefore = keyInheritance.startingKeyBefore(mutation);

        modifications.applyChange(mutation, mutator);

        keyInheritance.maintainInvariant(mutation, startingKeyBefore);

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

    /**
     * A freshly parsed song needs this once. It is read under suspended mutation tracking, where
     * no chain walk runs, so its notes are {@link AccidentalState.Chosen Chosen} where the file
     * writes an accidental and {@link AccidentalState.Adopting Adopting} where it is bare, while
     * every edit afterwards assumes a note on a line holds a {@link ChainedAccidental}.
     * The walk reads each line's running key, so the inherited keys must be settled first — see
     * {@link KeyInheritance#rebuildAfterParsing()}.
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

    private static String normalizeUnderLyrics(String text) {
        return StringUtils.processText(text, ShortA.STRIP);
    }

    private static String normalizeBanglaLyrics(String text) {
        return text.trim();
    }

    private static String normalizeTranslatedLyrics(String text) {
        return StringUtils.processText(text, ShortA.KEEP);
    }

    private static String normalizeFootnotes(String text) {
        return StringUtils.processText(text, ShortA.KEEP);
    }

}
