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

package songscribe.ui.renderer;

import java.awt.Color;
import java.awt.Font;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementLocation;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.LyricRun;
import songscribe.dom.DocumentScale;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.lint.LintReport;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;

/**
 * Immutable per-line rendering state, built once per {@code LineRenderer.render()} call.
 * <p>
 * Carries the fields that depend only on the line (and song) being rendered, together
 * with the color-resolution and playback-highlight logic that reads them. Per-element
 * state (current element index, X override, preview shift) lives on {@link ElementFrame}.
 * <p>
 * Assembled through {@link Builder}; because every field is final, a renderer cannot
 * observe a half-configured invariants object.
 * <p>
 * An instance is valid only for the pass it was built for and must not be stored: the
 * view's {@link ViewScale} is a value captured at construction, so an instance kept
 * across a zoom change would keep converting at the old zoom.
 */
public final class LineInvariants {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Color for an existing element that will be replaced by the current preview element. */
    static final Color REPLACED_ELEMENT_COLOR = new Color(249, 161, 161);

    /**
     * The note currently sounding during playback, and the grace note paired with it.
     * <p>
     * A grace index exists only while a note is playing, which is why the pair is one record
     * rather than two independently nullable fields.
     *
     * @param noteIndex      index of the currently playing note
     * @param graceNoteIndex index of the grace note preceding it, or {@code null} when none
     */
    public record Playing(int noteIndex, @Nullable Integer graceNoteIndex) {
    }

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    private final Song song;
    private final DocumentFontsHolder fonts;
    @Nullable
    private final Line currentLine;
    private final double middleLineYSs;
    private final int lineIndex;
    private final LayoutResult layoutResult;
    private final LyricRenderMetrics lyricRenderMetrics;
    @Nullable
    private final StaffElement activelyEditedElement;
    private final @Nullable Integer activelyEditedVerse;
    private final LineComponent.@Nullable SelectionProvider selectionProvider;
    private final Color selectionColor;
    private final @Nullable Playing playing;
    private final ViewScale viewScale;
    private final LintReport lintReport;

    /**
     * The elements of this line the playing note's tie passes over, both notes included, or
     * {@code null} when nothing is playing or the playing note is not tied. See
     * {@link #playingTieRange(Line, Playing)}.
     * <p>
     * Resolved once for the line because {@link #isElementInPlayingTie} is asked once per
     * element as the line paints, and the answer comes from the tie's two notes, which are the
     * same for every element.
     */
    private final @Nullable ElementRange playingTieRange;

    private LineInvariants(
        Builder b,
        LayoutResult layoutResult,
        LyricRenderMetrics lyricRenderMetrics
    ) {
        song = b.song;
        fonts = b.fonts;
        currentLine = b.currentLine;
        middleLineYSs = b.middleLineYSs;
        lineIndex = b.lineIndex;
        this.layoutResult = layoutResult;
        this.lyricRenderMetrics = lyricRenderMetrics;
        activelyEditedElement = b.activelyEditedElement;
        activelyEditedVerse = b.activelyEditedVerse;
        selectionProvider = b.selectionProvider;
        selectionColor = b.selectionColor;
        playing = b.playing;
        viewScale = b.viewScale;
        lintReport = b.lintReport;
        playingTieRange = (b.playing != null && b.currentLine != null)
            ? playingTieRange(b.currentLine, b.playing)
            : null;
    }

    /** The elements of {@code line} the tie the playing note starts, or else ends, is drawn over. */
    private static @Nullable ElementRange playingTieRange(Line line, Playing playing) {
        var noteIndex = playing.noteIndex();

        if (!line.hasIndex(noteIndex) || !(line.getElement(noteIndex) instanceof NoteElement note)) {
            return null;
        }

        var tie = note.tie();

        if (tie == null) {
            tie = note.incomingTie();
        }

        if (tie == null) {
            return null;
        }

        var placement = line.placementOf(tie);

        return placement == null ? null : placement.range();
    }

    /**
     * Returns a new builder for invariants describing the given song, rendered at the given zoom.
     * <p>
     * The zoom is a constructor argument rather than a setter because there is no sensible value
     * to fall back on: a render pass that left it out would draw this line at natural size inside
     * a zoomed view, and look like a layout bug rather than a missing argument.
     *
     * @param song      the song being rendered
     * @param fonts     supplies the document's fonts
     * @param viewScale the zoom this render pass draws at
     * @return a builder whose remaining required values are the layout result and the lyric
     *         render metrics
     */
    public static Builder builder(Song song, DocumentFontsHolder fonts, ViewScale viewScale) {
        return new Builder(song, fonts, viewScale);
    }

    // ==========================================================================
    // Accessors
    // ==========================================================================

    /** Returns the song being rendered. */
    public Song getSong() {
        return song;
    }

    /** Returns the document fonts holder, providing per-role rendering fonts. */
    public DocumentFontsHolder getFonts() {
        return fonts;
    }

    /** Convenience accessor for the attribution font. */
    public Font getAttributionFont() {
        return fonts.getFont(FontKey.ATTRIBUTION);
    }

    /** Convenience accessor for the annotation font. */
    public Font getAnnotationFont() {
        return fonts.getFont(FontKey.ANNOTATION);
    }

    /** Returns the current line being rendered. */
    public @Nullable Line getCurrentLine() {
        return currentLine;
    }

    /**
     * Returns the current line being rendered, or throws if it is absent.
     * Call this in rendering paths where a missing line represents corruption.
     */
    public Line requireCurrentLine() {
        if (currentLine == null) {
            throw RuntimeError.exit("no current line");
        }

        return currentLine;
    }

    /**
     * Returns the Y coordinate of the middle staff line (B line)
     * in staff-space units.
     */
    public double getMiddleLineYSs() {
        return middleLineYSs;
    }

    /** Returns the index of the current line within the song. */
    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * Returns the zoomed staff-space-to-pixel scale (document {@code pixelsPerStaffSpace}
     * × the view zoom factor). The name states that this value already carries the zoom, so
     * it cannot be confused with the document-scale conversion. This is the sole reader of
     * the zoomed scale — used only by the stripped-transform lyric path.
     */
    public double getViewPixelsPerStaffSpace() {
        return DocumentScale.PIXELS_PER_STAFF_SPACE * viewScale.factor();
    }

    /** Returns the view zoom captured for this render pass. */
    public ViewScale getViewScale() {
        return viewScale;
    }

    /**
     * Returns the layout result for the current line.
     * <p>
     * The layout result contains calculated bounds for all elements on the line.
     * Renderers use this to get pre-calculated positions instead of
     * computing them during rendering.
     */
    public LayoutResult getLayoutResult() {
        return layoutResult;
    }

    /** Returns the song-wide lyric render metrics. */
    public LyricRenderMetrics getLyricRenderMetrics() {
        return lyricRenderMetrics;
    }

    /** Returns the element currently being edited in the lyric overlay, or null. */
    @Nullable
    public StaffElement getActivelyEditedElement() {
        return activelyEditedElement;
    }

    /**
     * Returns the verse being edited in the lyric overlay, or {@code null}
     * when no editor is open.
     */
    public @Nullable Integer getActivelyEditedVerse() {
        return activelyEditedVerse;
    }

    /**
     * Returns the selection provider for checking element selection state.
     *
     * @return The selection provider, or null if not available
     */
    public LineComponent.@Nullable SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    /**
     * Returns the color used to render selected elements and beams.
     * Defaults to {@link ScoreView#getSelectionColor()}; overridden during an
     * element pitch-drag to use {@code INSERTION_NOTE_COLOR} instead.
     */
    public Color getSelectionColor() {
        return selectionColor;
    }

    /** Returns the lint report this render pass draws findings from. */
    public LintReport getLintReport() {
        return lintReport;
    }

    // ==========================================================================
    // Color resolution
    // ==========================================================================

    /**
     * Returns whether {@code color} is the one every colour lookup here falls back to when
     * nothing applies — no playback, no selection, no hover, no lint finding.
     * <p>
     * Callers ask this to decide whether a lookup contributed anything, so that a renderer can
     * fall through to its own colour choice. One predicate rather than a comparison written out
     * at each site: those comparisons were split between reference equality and
     * {@code equals}, which agree only because the fallback is a shared constant. Naming the
     * question keeps that a detail of this class instead of something five call sites rely on.
     */
    public static boolean isDefaultColor(Color color) {
        return color.equals(Color.BLACK);
    }

    /**
     * Returns the rendering color for the element at the given index, through
     * {@link #resolveColor}: the element or its tie playing, then a range selection that
     * covers it, then the hover highlight, then a lint finding on it.
     * <p>
     * Does not include grace-cancel coloring (handled in LineRenderer).
     */
    public Color getElementColor(int elementIndex) {
        return resolveColor(
            elementIndex,
            () -> isElementOrTiePlaying(elementIndex),
            () -> isElementRangeSelected(elementIndex)
        );
    }

    /**
     * Returns the rendering color for a lyric syllable attached to the element at
     * {@code elementIndex}, for the given verse. Differs from {@link #getElementColor} in its
     * playing test, described below, and in its selection test, which also holds when the
     * syllable itself is selected.
     * <p>
     * The playback highlight extends across the syllable's span: a melisma anchor
     * ({@link Lyric.Extend#START}) stays highlighted while any extender carrier of
     * its melisma plays, and a {@link Lyric.Syllabic#BEGIN}/{@link Lyric.Syllabic#MIDDLE}
     * anchor stays highlighted while in-between unlyriced notes play, up to (but not
     * including) the next text-bearing syllable.
     */
    public Color getLyricColor(int elementIndex, StaffElement element, int verseIndex) {
        return resolveColor(
            elementIndex,
            () -> isLyricSpanPlaying(elementIndex, element, verseIndex),
            () -> isSelected(new HitTarget.Lyric(element, verseIndex))
                || isElementRangeSelected(elementIndex)
        );
    }

    /**
     * Returns the rendering color for a lyric connector (hyphen run or melisma extender)
     * anchored at {@code sourceElementIndex} for {@code verseIndex}. Tracks the same
     * span-aware playing highlight as the anchor syllable so that the connector stays
     * highlighted while the in-between or extender carrier notes are playing.
     *
     * @param sourceElementIndex the anchor's position on this line, or null for a leading
     *                           continuation, whose source element sits on the previous line and
     *                           so has no playing state here
     */
    public Color getLyricConnectorColor(@Nullable Integer sourceElementIndex, int verseIndex) {
        if (sourceElementIndex == null) {
            return Color.BLACK;
        }

        int anchorIndex = sourceElementIndex;

        if (currentLine == null) {
            return getElementColor(anchorIndex);
        }

        var anchor = currentLine.getElement(anchorIndex);
        return resolveColor(
            anchorIndex,
            () -> isLyricSpanPlaying(anchorIndex, anchor, verseIndex),
            () -> isElementRangeSelected(anchorIndex)
        );
    }

    /**
     * Returns the rendering color for {@code target}, a thing that is selectable in its own
     * right (an articulation, a tie, a beam): the selection color when the selection provider
     * holds this exact target, and the default otherwise.
     * <p>
     * Playback and the hover highlight are properties of a note, not of the things hanging
     * off it, so no variant but {@link HitTarget.Element} carries them; that variant is drawn
     * through {@link #colorFor(HitTarget.Element, Integer)}.
     *
     * @param target the thing being drawn, as a click would address it
     * @return the selection color when {@code target} is selected, otherwise the default color
     */
    public Color colorFor(HitTarget target) {
        return isSelected(target) ? selectionColor : Color.BLACK;
    }

    /**
     * Returns the rendering color for the element {@code target} addresses, the target-keyed
     * counterpart of {@link #getElementColor(int)}, resolved through {@link #resolveColor}
     * with selection of this exact target as its selection test.
     * <p>
     * Playback, hover and lint are asked by position, so an element that has no position in
     * the line — a preview being dragged — is colored by selection alone.
     * <p>
     * {@code elementIndex} is not derived here: the call site already has it from its own
     * loop, so deriving it would repeat a lookup the caller has already done.
     *
     * @param target       the element being drawn, as a click would address it
     * @param elementIndex that element's index within the line, or {@code null} when the
     *     element is not in the line
     * @return what {@link #resolveColor} returns; for a {@code null} index, what
     *     {@link #colorFor(HitTarget)} returns
     */
    public Color colorFor(HitTarget.Element target, @Nullable Integer elementIndex) {
        if (elementIndex == null) {
            return colorFor((HitTarget) target);
        }

        int index = elementIndex;
        return resolveColor(index, () -> isElementOrTiePlaying(index), () -> isSelected(target));
    }

    /** Returns whether the selection provider holds exactly {@code target} on this line. */
    private boolean isSelected(HitTarget target) {
        return selectionProvider != null && selectionProvider.isSelected(target, lineIndex);
    }

    /**
     * The color chain every element-keyed lookup here resolves through. The first step that
     * holds decides the color:
     * <ol>
     *   <li><b>playing</b> — {@code isPlaying} holds: the playing-note color;</li>
     *   <li><b>selection</b> — {@code isSelected} holds: the selection color;</li>
     *   <li><b>hover</b> — the element is the preview's replacement target: the
     *       replaced-element highlight;</li>
     *   <li><b>lint</b> — the lint report flags the element ({@link LintReport#isFlagged}):
     *       {@link ScoreView#getLintColor()};</li>
     *   <li>otherwise black.</li>
     * </ol>
     * Selection wins over hover and lint because the selection is what the next Delete
     * removes; the user is never left guessing at that because something else colors the
     * element.
     * <p>
     * Each caller supplies only its playing test and its selection test; the order is held
     * here once.
     *
     * @param elementIndex the element's index within this line; hover and lint are asked by it
     * @param isPlaying    whether the thing being drawn reads as playing
     * @param isSelected   whether the thing being drawn reads as selected
     * @return the color of the first step that holds
     */
    private Color resolveColor(
        int elementIndex,
        BooleanSupplier isPlaying,
        BooleanSupplier isSelected
    ) {
        if (isPlaying.getAsBoolean()) {
            return ScoreView.getPlayingNoteColor();
        }

        if (isSelected.getAsBoolean()) {
            return selectionColor;
        }

        if (isElementHovered(elementIndex)) {
            return REPLACED_ELEMENT_COLOR;
        }

        if (isElementFlagged(elementIndex)) {
            return ScoreView.getLintColor();
        }

        return Color.BLACK;
    }

    /** Returns whether the lint report flags the element at {@code elementIndex} on this line. */
    private boolean isElementFlagged(int elementIndex) {
        return currentLine != null
            && currentLine.hasIndex(elementIndex)
            && lintReport.isFlagged(currentLine.getElement(elementIndex));
    }

    /** Returns whether the element at {@code elementIndex} or the tie through it is playing. */
    private boolean isElementOrTiePlaying(int elementIndex) {
        return isElementPlaying(elementIndex) || isElementInPlayingTie(elementIndex);
    }

    /**
     * Returns whether the element at {@code elementIndex} on this line reads as selected.
     * <p>
     * Asks the provider by index rather than by {@link HitTarget}. Selecting a single note
     * collapses the index range onto it, so this answers for both a click on one note and a
     * drag across several.
     * <p>
     * Package-private because {@code SlideRenderer} needs the same answer for the note a
     * connecting glissando lands on.
     */
    boolean isElementRangeSelected(int elementIndex) {
        if (selectionProvider == null
                || currentLine == null
                || !currentLine.hasIndex(elementIndex)) {
            return false;
        }

        return selectionProvider.isElementRangeSelected(elementIndex, lineIndex);
    }

    /**
     * Returns whether the element at {@code elementIndex} on this line is the one the
     * user is currently hovering as a replacement target for the preview element.
     */
    public boolean isElementHovered(int elementIndex) {
        var matched = PreviewElementManager.getHoveredElementLocation();
        return new ElementLocation(lineIndex, elementIndex).equals(matched);
    }

    /** Returns the currently playing note and its paired grace note, or {@code null} if none. */
    public @Nullable Playing playing() {
        return playing;
    }

    /**
     * Returns whether the given element index is currently playing
     * (either as the primary note or as a grace note).
     *
     * @param elementIndex the element index to check
     * @return true if the element is currently playing
     */
    public boolean isElementPlaying(int elementIndex) {
        if (playing == null) {
            return false;
        }

        var graceNoteIndex = playing.graceNoteIndex();
        return elementIndex == playing.noteIndex()
                || (graceNoteIndex != null && elementIndex == graceNoteIndex);
    }

    /**
     * Returns whether the given element index is part of the same tie
     * as the currently playing note, making it co-highlighted during playback.
     * <p>
     * Answers for the half of the tie that lies in the line being painted; neither line of a
     * tie crossing a line break speaks for the other line's elements.
     *
     * @param elementIndex the element index to check
     * @return true if the element is in the same tie as the playing note
     */
    public boolean isElementInPlayingTie(int elementIndex) {
        return playingTieRange != null && playingTieRange.contains(elementIndex);
    }

    /**
     * Returns whether the lyric anchored at {@code anchorIndex} for {@code verseIndex}
     * should render in the playing-note color: either its own element is playing (or
     * tied to the playing note), or the playing note falls inside the span the anchor's
     * lyric opens on this line ({@link LyricRun#lyricSpanEnd}).
     */
    private boolean isLyricSpanPlaying(int anchorIndex, StaffElement element, int verseIndex) {
        if (isElementOrTiePlaying(anchorIndex)) {
            return true;
        }

        if (playing == null || currentLine == null || playing.noteIndex() <= anchorIndex) {
            return false;
        }

        var lyric = element.getLyricForVerse(verseIndex);

        if (lyric == null || !LyricRun.opensLyricSpan(lyric)) {
            return false;
        }

        var spanEnd = currentLine.lyricSpanEnd(anchorIndex + 1, verseIndex);
        return playing.noteIndex() <= spanEnd.lastCoveredIndex();
    }

    // ==========================================================================
    // LayoutResultBuilder
    // ==========================================================================

    /**
     * Accumulates the per-line fields and produces an immutable {@link LineInvariants}.
     * The layout fields ({@code layoutResult}, {@code lyricRenderMetrics}) are required
     * and must be set before {@link #build()}.
     */
    public static final class Builder {

        private final Song song;
        private final DocumentFontsHolder fonts;
        @Nullable
        private Line currentLine;
        private double middleLineYSs;
        private int lineIndex;
        @Nullable
        private LayoutResult layoutResult;
        @Nullable
        private LyricRenderMetrics lyricRenderMetrics;
        @Nullable
        private StaffElement activelyEditedElement;
        private @Nullable Integer activelyEditedVerse;
        private LineComponent.@Nullable SelectionProvider selectionProvider;
        private Color selectionColor = ScoreView.getSelectionColor();
        private @Nullable Playing playing;
        private final ViewScale viewScale;
        private LintReport lintReport = LintReport.EMPTY;

        private Builder(Song song, DocumentFontsHolder fonts, ViewScale viewScale) {
            this.song = song;
            this.fonts = fonts;
            this.viewScale = viewScale;
        }

        public Builder setCurrentLine(@Nullable Line currentLine) {
            this.currentLine = currentLine;
            return this;
        }

        public Builder setMiddleLineYSs(double middleLineYSs) {
            this.middleLineYSs = middleLineYSs;
            return this;
        }

        public Builder setLineIndex(int lineIndex) {
            this.lineIndex = lineIndex;
            return this;
        }

        public Builder setLayoutResult(LayoutResult layoutResult) {
            this.layoutResult = layoutResult;
            return this;
        }

        public Builder setLyricRenderMetrics(LyricRenderMetrics lyricRenderMetrics) {
            this.lyricRenderMetrics = lyricRenderMetrics;
            return this;
        }

        public Builder setActivelyEditedElement(@Nullable StaffElement activelyEditedElement) {
            this.activelyEditedElement = activelyEditedElement;
            return this;
        }

        /** Sets the verse being edited, or {@code null} when no editor is open. */
        public Builder setActivelyEditedVerse(@Nullable Integer activelyEditedVerse) {
            this.activelyEditedVerse = activelyEditedVerse;
            return this;
        }

        public Builder setSelectionProvider(LineComponent.@Nullable SelectionProvider selectionProvider) {
            this.selectionProvider = selectionProvider;
            return this;
        }

        public Builder setSelectionColor(Color selectionColor) {
            this.selectionColor = selectionColor;
            return this;
        }

        /** Sets the currently playing note and its paired grace note, or {@code null} when none. */
        public Builder setPlaying(@Nullable Playing playing) {
            this.playing = playing;
            return this;
        }

        /** Sets the lint report whose findings this pass draws; {@link LintReport#EMPTY} if unset. */
        public Builder setLintReport(LintReport lintReport) {
            this.lintReport = lintReport;
            return this;
        }

        /**
         * Builds the immutable invariants.
         *
         * @throws IllegalStateException if any required layout field is unset
         */
        public LineInvariants build() {
            var resolvedLayout = layoutResult;
            var resolvedLyricMetrics = lyricRenderMetrics;

            if (resolvedLayout == null || resolvedLyricMetrics == null) {
                throw new IllegalStateException(
                    "LineInvariants requires layoutResult and lyricRenderMetrics to be set"
                );
            }

            return new LineInvariants(this, resolvedLayout, resolvedLyricMetrics);
        }
    }
}
