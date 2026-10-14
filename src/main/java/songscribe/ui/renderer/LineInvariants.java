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
import java.util.EnumSet;

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
import songscribe.layout.LyricBoxLayout;
import songscribe.layout.LyricRenderMetrics;
import songscribe.lint.LintMark;
import songscribe.lint.LintReport;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;

/**
 * Immutable per-line rendering state, built once per {@code LineRenderer.render()} call.
 * <p>
 * An instance is valid only for the pass it was built for and must not be stored: its
 * {@link ViewScale} is captured at construction, so a kept instance would convert at the old zoom
 * after a zoom change.
 */
public final class LineInvariants {

    static final Color REPLACED_ELEMENT_COLOR = new Color(249, 161, 161);

    /**
     * The note currently sounding during playback, and the grace note paired with it. A grace
     * index exists only while a note is playing.
     *
     * @param graceNoteIndex index of the grace note preceding it, or {@code null} when none
     */
    public record Playing(int noteIndex, @Nullable Integer graceNoteIndex) {
    }

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
    private final Song.@Nullable Script activelyEditedScript;
    private final LineComponent.@Nullable SelectionProvider selectionProvider;
    private final Color selectionColor;
    private final @Nullable Playing playing;
    private final ViewScale viewScale;
    private final LintReport lintReport;

    /**
     * The elements of this line the playing note's tie passes over, both notes included, or
     * {@code null} when nothing is playing or the playing note is not tied.
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
        activelyEditedScript = b.activelyEditedScript;
        selectionProvider = b.selectionProvider;
        selectionColor = b.selectionColor;
        playing = b.playing;
        viewScale = b.viewScale;
        lintReport = b.lintReport;
        playingTieRange = (b.playing != null && b.currentLine != null)
            ? playingTieRange(b.currentLine, b.playing)
            : null;
    }

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

    public static Builder builder(Song song, DocumentFontsHolder fonts, ViewScale viewScale) {
        return new Builder(song, fonts, viewScale);
    }

    public Song getSong() {
        return song;
    }

    public DocumentFontsHolder getFonts() {
        return fonts;
    }

    public Font getAttributionFont() {
        return fonts.getFont(FontKey.ATTRIBUTION);
    }

    public Font getAnnotationFont() {
        return fonts.getFont(FontKey.ANNOTATION);
    }

    public @Nullable Line getCurrentLine() {
        return currentLine;
    }

    public Line requireCurrentLine() {
        if (currentLine == null) {
            throw RuntimeError.exit("no current line");
        }

        return currentLine;
    }

    public double getMiddleLineYSs() {
        return middleLineYSs;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * Already carries the zoom, unlike the document-scale conversion. Used only by the
     * stripped-transform lyric path.
     */
    public double getViewPixelsPerStaffSpace() {
        return DocumentScale.PIXELS_PER_STAFF_SPACE * viewScale.factor();
    }

    public ViewScale getViewScale() {
        return viewScale;
    }

    public LayoutResult getLayoutResult() {
        return layoutResult;
    }

    public LyricRenderMetrics getLyricRenderMetrics() {
        return lyricRenderMetrics;
    }

    /** Null when no element is being edited in the lyric overlay. */
    @Nullable
    public StaffElement getActivelyEditedElement() {
        return activelyEditedElement;
    }

    /**
     * {@code null} when no editor is open.
     */
    public Song.@Nullable Script getActivelyEditedScript() {
        return activelyEditedScript;
    }

    /**
     * @return null if not available
     */
    public LineComponent.@Nullable SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    public Color getSelectionColor() {
        return selectionColor;
    }

    /**
     * @param flagged          whether the element itself is drawn in the lint color, when
     *                          no fact that outranks {@link ColorPriority#LINT} holds for it
     * @param lyricPlaceholder  the placeholder box a "?" is drawn in, or {@code null} when no
     *                          "?" is drawn on the element
     */
    public record ElementLint(boolean flagged, boolean annotationFlagged, @Nullable LyricBoxLayout lyricPlaceholder) {

        static final ElementLint NONE = new ElementLint(false, false, null);
    }

    /**
     * @param element an element of this line, compared by identity
     */
    public ElementLint lintOn(StaffElement element) {
        var marks = lintReport.marksOn(element);

        if (marks.isEmpty()) {
            return ElementLint.NONE;
        }

        var flagged = false;
        var annotationFlagged = false;
        LyricBoxLayout lyricPlaceholder = null;

        for (var mark : marks) {
            switch (mark) {
                case LintMark.Flagged _ -> flagged = true;
                case LintMark.FlaggedAnnotation _ -> annotationFlagged = true;
                case LintMark.MissingLyric missingLyric -> {
                    var placeholder = drawnLyricPlaceholder(missingLyric, layoutResult);

                    if (placeholder != null) {
                        lyricPlaceholder = placeholder;
                    }
                }
            }
        }

        return new ElementLint(flagged, annotationFlagged, lyricPlaceholder);
    }

    /**
     * The one statement of where a "?" is drawn: the renderer draws by it, through
     * {@link #lintOn}, and the pointer code hit-tests by it. Layout lays out the active script
     * only, so a "?" stands only for a mark in the script the lyric row shows.
     *
     * @param layoutResult the layout of the line that holds the mark's element
     * @return the placeholder, or {@code null} when no "?" is drawn for the mark
     */
    public static @Nullable LyricBoxLayout drawnLyricPlaceholder(LintMark.MissingLyric mark, LayoutResult layoutResult) {
        var placeholder = layoutResult.getLyricPlaceholder(mark.element());

        if (placeholder == null || placeholder.script() != mark.script()) {
            return null;
        }

        return placeholder;
    }

    /**
     * Reference equality and {@code equals} agree here only because the fallback is a shared
     * constant, so callers ask this instead of comparing.
     */
    public static boolean isDefaultColor(Color color) {
        return color.equals(Color.BLACK);
    }

    /**
     * Does not include grace-cancel coloring (handled in LineRenderer).
     */
    public Color getElementColor(int elementIndex) {
        var facts = positionFacts(elementIndex);

        if (isElementOrTiePlaying(elementIndex)) {
            facts.add(ColorPriority.PLAYING);
        }

        if (isElementRangeSelected(elementIndex)) {
            facts.add(ColorPriority.SELECTED);
        }

        return colorOf(facts);
    }

    /**
     * The playback highlight extends across the syllable's span: a melisma anchor
     * ({@link Lyric.Extend#START}) stays highlighted while any extender carrier of its melisma
     * plays, and a {@link Lyric.Syllabic#BEGIN}/{@link Lyric.Syllabic#MIDDLE} anchor while
     * in-between unlyriced notes play, up to the next text-bearing syllable. The selection test
     * also holds when the syllable itself is selected.
     */
    public Color getLyricColor(int elementIndex, StaffElement element, Song.Script script) {
        var facts = positionFacts(elementIndex);

        if (isLyricSpanPlaying(elementIndex, element, script)) {
            facts.add(ColorPriority.PLAYING);
        }

        if (isSelected(new HitTarget.Lyric(element, script)) || isElementRangeSelected(elementIndex)) {
            facts.add(ColorPriority.SELECTED);
        }

        return colorOf(facts);
    }

    /**
     * Tracks the anchor syllable's span-aware playing highlight.
     *
     * @param sourceElementIndex null for a leading continuation, whose source element sits on
     *                           the previous line and so has no playing state here
     */
    public Color getLyricConnectorColor(@Nullable Integer sourceElementIndex, Song.Script script) {
        if (sourceElementIndex == null) {
            return Color.BLACK;
        }

        int anchorIndex = sourceElementIndex;

        if (currentLine == null) {
            return getElementColor(anchorIndex);
        }

        var facts = positionFacts(anchorIndex);

        if (isLyricSpanPlaying(anchorIndex, currentLine.getElement(anchorIndex), script)) {
            facts.add(ColorPriority.PLAYING);
        }

        if (isElementRangeSelected(anchorIndex)) {
            facts.add(ColorPriority.SELECTED);
        }

        return colorOf(facts);
    }

    /**
     * Only {@link HitTarget.Element} carries playback and hover; it is drawn through
     * {@link #colorFor(HitTarget.Element, Integer)}.
     */
    public Color colorFor(HitTarget target) {
        var facts = EnumSet.noneOf(ColorPriority.class);

        if (isSelected(target)) {
            facts.add(ColorPriority.SELECTED);
        }

        return colorOf(facts);
    }

    /**
     * An element that has no position in the line — a preview being dragged — is colored by
     * selection alone.
     *
     * @param elementIndex that element's index within the line, or {@code null} when the
     *     element is not in the line
     */
    public Color colorFor(HitTarget.Element target, @Nullable Integer elementIndex) {
        if (elementIndex == null) {
            return colorFor((HitTarget) target);
        }

        int index = elementIndex;
        var facts = positionFacts(index);

        if (isElementOrTiePlaying(index)) {
            facts.add(ColorPriority.PLAYING);
        }

        if (isSelected(target)) {
            facts.add(ColorPriority.SELECTED);
        }

        return colorOf(facts);
    }

    private boolean isSelected(HitTarget target) {
        return selectionProvider != null && selectionProvider.isSelected(target, lineIndex);
    }

    /**
     * Returns a new, mutable set, for the caller to add its own playing and selection facts to.
     */
    private EnumSet<ColorPriority> positionFacts(int elementIndex) {
        var facts = EnumSet.noneOf(ColorPriority.class);

        if (isElementHovered(elementIndex)) {
            facts.add(ColorPriority.HOVERED);
        }

        if (isElementFlagged(elementIndex)) {
            facts.add(ColorPriority.LINT);
        }

        return facts;
    }

    private Color colorOf(EnumSet<ColorPriority> facts) {
        return switch (ColorPriority.decide(facts)) {
            case PLAYING -> ScoreView.getPlayingNoteColor();
            case SELECTED -> selectionColor;
            case HOVERED -> REPLACED_ELEMENT_COLOR;
            case LINT -> ScoreView.getLintColor();
            case DEFAULT -> Color.BLACK;
        };
    }

    private boolean isElementFlagged(int elementIndex) {
        return currentLine != null
            && currentLine.hasIndex(elementIndex)
            && lintOn(currentLine.getElement(elementIndex)).flagged();
    }

    private boolean isElementOrTiePlaying(int elementIndex) {
        return isElementPlaying(elementIndex) || isElementInPlayingTie(elementIndex);
    }

    /**
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

    public boolean isElementHovered(int elementIndex) {
        var matched = PreviewElementManager.getHoveredElementLocation();
        return new ElementLocation(lineIndex, elementIndex).equals(matched);
    }

    /** Null if no note is playing. */
    public @Nullable Playing playing() {
        return playing;
    }

    public boolean isElementPlaying(int elementIndex) {
        if (playing == null) {
            return false;
        }

        var graceNoteIndex = playing.graceNoteIndex();
        return elementIndex == playing.noteIndex()
                || (graceNoteIndex != null && elementIndex == graceNoteIndex);
    }

    /**
     * Answers only for the half of the tie in the line being painted.
     */
    public boolean isElementInPlayingTie(int elementIndex) {
        return playingTieRange != null && playingTieRange.contains(elementIndex);
    }

    private boolean isLyricSpanPlaying(int anchorIndex, StaffElement element, Song.Script script) {
        if (isElementOrTiePlaying(anchorIndex)) {
            return true;
        }

        if (playing == null || currentLine == null || playing.noteIndex() <= anchorIndex) {
            return false;
        }

        var lyric = element.getLyricForScript(script);

        if (lyric == null || !LyricRun.opensLyricSpan(lyric)) {
            return false;
        }

        var spanEnd = currentLine.lyricSpanEnd(anchorIndex + 1, script);
        return playing.noteIndex() <= spanEnd.lastCoveredIndex();
    }

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
        private Song.@Nullable Script activelyEditedScript;
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

        public Builder setActivelyEditedScript(Song.@Nullable Script activelyEditedScript) {
            this.activelyEditedScript = activelyEditedScript;
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

        public Builder setPlaying(@Nullable Playing playing) {
            this.playing = playing;
            return this;
        }

        public Builder setLintReport(LintReport lintReport) {
            this.lintReport = lintReport;
            return this;
        }

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
