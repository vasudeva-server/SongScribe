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

import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.SpanBound;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.error.RuntimeError;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
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
 */
public final class LineInvariants {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Color for an existing element that will be replaced by the current preview element. */
    static final Color REPLACED_ELEMENT_COLOR = new Color(249, 161, 161);

    /** Verse reported by {@link #getActivelyEditedVerse()} when no lyric editor is open. */
    public static final int NO_VERSE = -1;

    /**
     * Element index to pass to {@link #colorFor(HitTarget, int)} for the target variants that
     * ignore it. Only {@link HitTarget.Element} and {@link HitTarget.Accidental} consult the
     * index; naming the absence keeps a bare {@code -1} out of the call sites.
     */
    public static final int NO_ELEMENT_INDEX = -1;

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
    private final int activelyEditedVerse;
    private final LineComponent.@Nullable SelectionProvider selectionProvider;
    private final Color selectionColor;
    private final int playingNoteIndex;
    private final int playingGraceNoteIndex;
    private final ViewScale viewScale;

    /** Tie span containing the playing note, resolved once; null when nothing is playing. */
    @Nullable
    private final Tie playingTieSpan;

    /**
     * Where the playing tie's two endpoints sit relative to this line, resolved once alongside
     * the tie itself. Null exactly when {@link #playingTieSpan} is.
     * <p>
     * Cached because {@link #isElementInPlayingTie} is asked once per element as the line
     * paints, and for a tie crossing a line boundary resolving the far endpoint walks the
     * song's line list to work out which side of this line it fell off. That answer is the
     * same for every element, so it is resolved for the line rather than for each of them.
     */
    @Nullable
    private final SpanBound playingTieAnchorBound;

    /** @see #playingTieAnchorBound */
    @Nullable
    private final SpanBound playingTieEndBound;

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
        playingNoteIndex = b.playingNoteIndex;
        playingGraceNoteIndex = b.playingGraceNoteIndex;
        viewScale = b.viewScale;
        playingTieSpan = (b.playingNoteIndex >= 0 && b.currentLine != null)
            ? b.currentLine.findTieAt(b.playingNoteIndex)
            : null;

        if (playingTieSpan == null || b.currentLine == null) {
            playingTieAnchorBound = null;
            playingTieEndBound = null;
        } else {
            playingTieAnchorBound = b.currentLine.anchorIndexOf(playingTieSpan);
            playingTieEndBound = b.currentLine.endIndexOf(playingTieSpan);
        }
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
        return ScaleContext.getPixelsPerStaffSpace() * viewScale.factor();
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
     * Returns the verse being edited in the lyric overlay, or {@link #NO_VERSE}
     * when no editor is open.
     */
    public int getActivelyEditedVerse() {
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

    // ==========================================================================
    // Color resolution
    // ==========================================================================

    /**
     * Returns whether {@code color} is the one every colour lookup here falls back to when
     * nothing applies — no playback, no selection, no hover.
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
     * Returns the rendering color for the element at the given index.
     * <p>
     * Covers playback highlighting, selection, and hover (replaced-element) highlighting.
     * Does not include grace-cancel coloring (handled in LineRenderer).
     */
    public Color getElementColor(int elementIndex) {
        return colorFor(
            elementIndex,
            () -> isElementPlaying(elementIndex) || isElementInPlayingTie(elementIndex),
            () -> false
        );
    }

    /**
     * Returns the rendering color for a lyric syllable attached to the element at
     * {@code elementIndex}, for the given verse. Extends {@link #getElementColor} with
     * a per-verse {@code isLyricSelected} check inserted between playback and
     * element-level selection.
     * <p>
     * The playback highlight extends across the syllable's span: a melisma anchor
     * ({@link Lyric.Extend#START}) stays highlighted while any extender carrier of
     * its melisma plays, and a {@link Lyric.Syllabic#BEGIN}/{@link Lyric.Syllabic#MIDDLE}
     * anchor stays highlighted while in-between unlyriced notes play, up to (but not
     * including) the next text-bearing syllable.
     */
    public Color getLyricColor(int elementIndex, StaffElement element, int verseIndex) {
        return colorFor(
            elementIndex,
            () -> isLyricSpanPlaying(elementIndex, element, verseIndex),
            () -> selectionProvider != null
                    && selectionProvider.isSelected(new HitTarget.Lyric(element, verseIndex), lineIndex)
        );
    }

    /**
     * Returns the rendering color for a lyric connector (hyphen run or melisma extender)
     * anchored at {@code sourceElementIndex} for {@code verseIndex}. Tracks the same
     * span-aware playing highlight as the anchor syllable so that the connector stays
     * highlighted while the in-between or extender carrier notes are playing.
     */
    public Color getLyricConnectorColor(int sourceElementIndex, int verseIndex) {
        if (sourceElementIndex < 0) {
            return Color.BLACK;
        }

        if (currentLine == null) {
            return getElementColor(sourceElementIndex);
        }

        var anchor = currentLine.getElement(sourceElementIndex);
        return colorFor(
            sourceElementIndex,
            () -> isLyricSpanPlaying(sourceElementIndex, anchor, verseIndex),
            () -> false
        );
    }

    /**
     * Returns the rendering color for {@code target}, the target-keyed counterpart of
     * {@link #getElementColor(int)}.
     * <p>
     * Follows the same cascade — playback, then selection, then the hovered
     * replaced-element highlight — but asks the selection provider about this exact target
     * rather than about an element index, so a kind that is selectable in its own right
     * (an articulation, a tie, a beam) highlights on its own.
     * <p>
     * {@code elementIndex} is <b>not</b> derived here: it is only ever fed to the three
     * index-keyed predicates ({@link #isElementPlaying}, {@link #isElementInPlayingTie},
     * {@link #isElementHovered}), and only {@link HitTarget.Element} and
     * {@link HitTarget.Accidental} consult them. Every other variant ignores the argument —
     * pass {@link #NO_ELEMENT_INDEX}. Every call site that draws the two variants that do
     * use it already has the index from its own loop, so deriving it here would repeat a
     * lookup the caller has already done, for nothing.
     *
     * @param target       the thing being drawn, as a click would address it
     * @param elementIndex the owning element's index, or {@link #NO_ELEMENT_INDEX}
     */
    public Color colorFor(HitTarget target, int elementIndex) {
        // Playback and the hover highlight are properties of a note, not of the things
        // hanging off it, so they apply to exactly these two variants.
        var indexedChecksApply = target instanceof HitTarget.Element
                || target instanceof HitTarget.Accidental;

        if (indexedChecksApply
                && (isElementPlaying(elementIndex) || isElementInPlayingTie(elementIndex))) {
            return ScoreView.getPlayingNoteColor();
        }

        if (selectionProvider != null && selectionProvider.isSelected(target, lineIndex)) {
            return selectionColor;
        }

        if (indexedChecksApply && isElementHovered(elementIndex)) {
            return REPLACED_ELEMENT_COLOR;
        }

        return Color.BLACK;
    }

    private Color colorFor(
        int elementIndex,
        BooleanSupplier playingCheck,
        BooleanSupplier extraSelectionCheck
    ) {
        if (playingCheck.getAsBoolean()) {
            return ScoreView.getPlayingNoteColor();
        }

        if (extraSelectionCheck.getAsBoolean()) {
            return selectionColor;
        }

        if (isElementRangeSelected(elementIndex)) {
            return selectionColor;
        }

        if (isElementHovered(elementIndex)) {
            return REPLACED_ELEMENT_COLOR;
        }

        return Color.BLACK;
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
        return matched != null && matched.matches(lineIndex, elementIndex);
    }

    /** Returns the index of the currently playing note, or -1 if none. */
    public int getPlayingNoteIndex() {
        return playingNoteIndex;
    }

    /** Returns the index of the currently playing grace note, or -1 if none. */
    public int getPlayingGraceNoteIndex() {
        return playingGraceNoteIndex;
    }

    /**
     * Returns whether the given element index is currently playing
     * (either as the primary note or as a grace note).
     *
     * @param elementIndex the element index to check
     * @return true if the element is currently playing
     */
    public boolean isElementPlaying(int elementIndex) {
        return elementIndex >= 0
                && (elementIndex == playingNoteIndex || elementIndex == playingGraceNoteIndex);
    }

    /**
     * Returns whether the given element index is part of the same tie span
     * as the currently playing note, making it co-highlighted during playback.
     * <p>
     * Answers for the half of the tie that lies in the line being painted. A tie crossing a
     * line boundary is asked separately by each line's invariants, and each reports its own
     * half; neither speaks for the other line's elements.
     *
     * @param elementIndex the element index to check
     * @return true if the element is in the same tie as the playing note
     */
    public boolean isElementInPlayingTie(int elementIndex) {
        // Receiver-relative resolution, done once for the line rather than per element. Reading
        // the pair off the tie would take the anchor index from one line and the end index from
        // the other, and for a cross-line tie the resulting 7 <= i && i <= 0 is unsatisfiable,
        // so the highlight would silently never fire. Resolving against currentLine yields
        // At(7)/AFTER_LINE here and BEFORE_LINE/At(0) in the next line, and each half covers
        // exactly the elements it passes over. The two bounds are null whenever the tie is.
        if (playingTieAnchorBound == null || playingTieEndBound == null) {
            return false;
        }

        return Span.containing(elementIndex).test(playingTieAnchorBound, playingTieEndBound);
    }

    /**
     * Returns whether the lyric anchored at {@code anchorIndex} for {@code verseIndex}
     * should render in the playing-note color: either its own element is playing (or
     * tied to the playing note), or the playing note falls inside the anchor's lyric
     * span (melisma extender carriers, or the unlyriced notes between a BEGIN/MIDDLE
     * syllable and the next text-bearing syllable).
     */
    private boolean isLyricSpanPlaying(int anchorIndex, StaffElement element, int verseIndex) {
        if (isElementPlaying(anchorIndex) || isElementInPlayingTie(anchorIndex)) {
            return true;
        }

        if (playingNoteIndex < 0 || currentLine == null || playingNoteIndex <= anchorIndex) {
            return false;
        }

        var lyric = element.getLyricForVerse(verseIndex);

        if (lyric == null) {
            return false;
        }

        var syllabic = lyric.syllabic();
        var extendsForward = lyric.extend() == Lyric.Extend.START
                || syllabic == Lyric.Syllabic.BEGIN
                || syllabic == Lyric.Syllabic.MIDDLE;

        if (!extendsForward) {
            return false;
        }

        var count = currentLine.elementCount();

        for (var i = anchorIndex + 1; i < count; i++) {
            var next = currentLine.getElement(i).getLyricForVerse(verseIndex);

            if (next == null) {
                continue;
            }

            var extend = next.extend();

            // A CONTINUE carrier is an interior note of this anchor's melisma — keep
            // scanning for the boundary so the span covers every carrier, not just the first.
            if (extend == Lyric.Extend.CONTINUE) {
                continue;
            }

            // A STOP carrier is the final note of this anchor's melisma — span includes it.
            // A text-bearing lyric starts a new span — this anchor's span ends just before it.
            var spanEnd = extend == Lyric.Extend.STOP ? i : i - 1;
            return playingNoteIndex <= spanEnd;
        }

        // No further lyric on this line: span runs to the end of the line.
        return playingNoteIndex < count;
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
        private int activelyEditedVerse = NO_VERSE;
        private LineComponent.@Nullable SelectionProvider selectionProvider;
        private Color selectionColor = ScoreView.getSelectionColor();
        private int playingNoteIndex = -1;
        private int playingGraceNoteIndex = -1;
        private final ViewScale viewScale;

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

        /** Sets the verse being edited, or {@link #NO_VERSE} when no editor is open. */
        public Builder setActivelyEditedVerse(int activelyEditedVerse) {
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

        public Builder setPlayingNoteIndex(int playingNoteIndex) {
            this.playingNoteIndex = playingNoteIndex;
            return this;
        }

        public Builder setPlayingGraceNoteIndex(int playingGraceNoteIndex) {
            this.playingGraceNoteIndex = playingGraceNoteIndex;
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
