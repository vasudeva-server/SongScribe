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

package songscribe.ui.component;

import module java.desktop;

import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.EnumSet;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.music.Line;
import songscribe.music.Lyric;
import songscribe.music.StaffElement;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.layout.InsetsSs;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.ScaleContext;

/**
 * In-place lyric editor overlay parented to {@link Score}. Edits the verse-1 lyric of a
 * single {@link StaffElement} with width and baseline matching the rendered lyric box.
 * <p>
 * Phase 1a scope: scaffold and lifecycle only — geometry pipeline, attach/dismiss, and
 * prefill on construction. Commit, advance, keystroke handling, validation, and the
 * action-audit invariant arrive in later phases.
 *
 * <pre>
 *  ┌───────────────────────────────────────────────────────────┐
 *  │ LyricEditor lifecycle (Phase 1a/1b)                       │
 *  │                                                           │
 *  │   AddLyricAction.actionPerformed                          │
 *  │           │                                               │
 *  │           ▼                                               │
 *  │   new LyricEditor(line, element)                          │
 *  │   editor.setBounds(...)                                   │
 *  │   if existing lyric: setText, selectAll, caret end        │
 *  │   editor.attachListeners()                                │
 *  │   score.add(editor); setVisible(true); requestFocus       │
 *  │           │                                               │
 *  │           ▼                                               │
 *  │   ┌──── ACTIVE ─────────────────────────────────┐         │
 *  │   │                                             │         │
 *  │   │  user keystroke                             │         │
 *  │   │   - char insert/delete → recompute width    │         │
 *  │   │   - len > 32 → beep, reject                 │         │
 *  │   │   - newline → reject                        │         │
 *  │   │                                             │         │
 *  │   │  Tab/Space  → commit(NONE,NONE) → advance() │         │
 *  │   │  Enter      → commit(NONE,NONE) → dismiss() │         │
 *  │   │  Escape     → dismiss() (no commit)         │         │
 *  │   │  focus-lost → commit(NONE,NONE) → dismiss() │         │
 *  │   │                                             │         │
 *  │   │  Boundary keys (Phase 1b):                  │         │
 *  │   │  ┌────────┬──────────┬────────────────────┐ │         │
 *  │   │  │ Key    │ State    │ Effect             │ │         │
 *  │   │  ├────────┼──────────┼────────────────────┤ │         │
 *  │   │  │ -      │ non-empty│ commit(SYLLABLE,   │ │         │
 *  │   │  │        │          │   NONE) → advance  │ │         │
 *  │   │  │ -      │ empty    │ advance only       │ │         │
 *  │   │  │ =      │ non-empty│ commit(COMPOUND_   │ │         │
 *  │   │  │        │          │   WORD,NONE)→adv.  │ │         │
 *  │   │  │ =      │ empty    │ beep, no-op        │ │         │
 *  │   │  │ _      │ non-empty│ commit(NONE,START) │ │         │
 *  │   │  │        │          │   → advance        │ │         │
 *  │   │  │ _      │ empty    │ scan-back → advance│ │         │
 *  │   │  └────────┴──────────┴────────────────────┘ │         │
 *  │   └─────────────────────────────────────────────┘         │
 *  │           │                                               │
 *  │           ▼                                               │
 *  │   advance(): scan forward for eligible element            │
 *  │     eligible: !rest, OR rest with existing lyric          │
 *  │     found: dismiss this, new LyricEditor(line, next)      │
 *  │     none:  dismiss()                                      │
 *  │                                                           │
 *  │   scanBack(): walk backwards from current element         │
 *  │     find prev syllable with non-blank text, extend !=     │
 *  │       STOP/CONTINUE                                       │
 *  │     found: set extend=START on prev syllable, remove      │
 *  │       lyric from current element (one bracket)            │
 *  │     none: remove lyric from current element               │
 *  │                                                           │
 *  │   dismiss(): score.remove(this); editor reference cleared │
 *  │                                                           │
 *  │ Invariant: while editor is active, no external code path  │
 *  │ may mutate the song or fire any toolbar keystroke.        │
 *  │ Enforced by DISABLE_WHEN_EDITING_TEXT on every toolbar    │
 *  │ UIAction. LyricEditorActionAuditTest locks the whitelist. │
 *  └───────────────────────────────────────────────────────────┘
 * </pre>
 */
public final class LyricEditor extends MyJTextField {

    static final int MAX_LENGTH_CHARS = 32;

    private static final int VERSE = 1;

    private static final EnumSet<ElementField> LYRIC_FIELDS = EnumSet.of(ElementField.LYRIC);

    /**
     * Visible padding from the box edge to the glyph ink, on each side. The LineBorder
     * width is included in this padding (i.e. the EmptyBorder portion is this minus the
     * line-border width). Left/right are realized symmetrically around the glyph ink by
     * compensating for the font's left side bearing in {@link #recomputeBounds}.
     */
    public static final InsetsSs EDITOR_PADDING_SS = new InsetsSs(0.25, 0.5, 0.25, 0.5);

    private static final int LINE_BORDER_WIDTH_PX = 1;

    /**
     * One pixel of horizontal slack at the right end of the JTextField content area so
     * the caret has room to paint at the trailing edge without JTextField horizontally
     * scrolling the view to keep it in sight.
     */
    private static final int CARET_SLACK_PX = 1;

    /**
     * Swing's {@link FieldView} clips to the field allocation before text is drawn. Some
     * rasterized glyph edges can land just outside that allocation even when Java reports
     * no negative left bearing, so the editor view gives the paint clip a small left guard.
     */
    private static final int LEADING_PAINT_SLACK_PX = 1;

    /**
     * Minimum content-area width when the editor is empty so the caret remains visible
     * and the box reads as a clickable target rather than collapsing to zero.
     */
    private static final double EMPTY_BOX_MIN_WIDTH_SS = 0.125;  // 1px

    private static final String ACTION_KEY_TAB = "lyric.editor.tab";
    private static final String ACTION_KEY_ENTER = "lyric.editor.enter";
    private static final String ACTION_KEY_ESCAPE = "lyric.editor.escape";

    private final Score score;
    private final Line line;
    private final StaffElement element;
    private final @Nullable LineComponent lineComponent;

    private boolean focused;

    @Nullable private AWTEventListener outsideClickListener;

    /**
     * Constructs a {@link LyricEditor} on {@code element}, attaches it to {@code score},
     * and gives it focus. Used by both {@link songscribe.ui.action.AddLyricAction} and
     * {@link #advance()} so the open sequence is centralized.
     */
    public static void openOn(Score score, Line line, StaffElement element) {
        var editor = new LyricEditor(score, line, element);
        score.addOverlay(editor);
        score.setComponentZOrder(editor, 0);

        // A mutation that triggered this open (e.g. committing a lyric before advancing)
        // may have invalidated the line's layout. Force a synchronous layout pass so
        // recomputeBounds sees fresh anchor positions on the first paint.
        if (editor.lineComponent != null) {
            editor.lineComponent.ensureLayout();
        }

        editor.recomputeBounds();
        editor.attachListeners();
        editor.setVisible(true);

        // Defer focus so the toolbar button that triggered the action can finish its
        // own focus dance before we request focus, preventing an immediate focusLost.
        SwingUtilities.invokeLater(editor::requestFocusInWindow);
        score.revalidate();
        score.repaint(editor.getBounds());
    }

    public LyricEditor(Score score, Line line, StaffElement element) {
        this.score = score;
        this.line = line;
        this.element = element;

        var lineIndex = score.getSong().indexOfLine(line);
        lineComponent = lineIndex >= 0 ? score.getLineComponent(lineIndex) : null;

        configureLAF();

        // Tab is a focus-traversal key by default; clear the set so VK_TAB reaches the
        // input map rather than moving focus to the next component.
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.emptySet());

        installKeyBindings();

        var existingLyric = element.getMainLyric();

        if (existingLyric != null && !existingLyric.text().isBlank()) {
            // selectAll() leaves the caret at the end of the text (mark at 0, dot at length),
            // which gives "fully selected with caret at end" — no follow-up setCaretPosition
            // needed (and using one would collapse the selection).
            setText(existingLyric.text());
            selectAll();
        }
    }

    private void configureLAF() {
        setUI(new LyricTextFieldUI());
        setFont(score.getLyricRenderMetrics().lyricsFont());
        setOpaque(true);
        setBackground(LayoutStylesheet.getScreenBackground());
        setForeground(Color.BLACK);
        setCaretColor(Color.BLACK);
        setHorizontalAlignment(LEFT);
        // The border is installed per-text in recomputeBounds() because its EmptyBorder
        // insets compensate for the active glyph's left side bearing — see comment there.
    }

    /**
     * Text-field UI used only by the lyric overlay so we can customize its Swing text
     * view without changing global look-and-feel behavior.
     */
    private static final class LyricTextFieldUI extends BasicTextFieldUI {
        @Override
        public View create(Element elem) {
            return new LeadingSlackFieldView(elem);
        }
    }

    /**
     * Single-line text view that gives {@link FieldView#paint(Graphics, Shape)} one
     * extra pixel of left paint clip. {@code FieldView} clips before {@code PlainView}
     * draws glyphs; with the lyrics font, the rasterized leading edge of characters
     * such as "d" can land just outside that clip even when the reported left bearing
     * is non-negative. Expanding the paint clip exposes that edge, while
     * {@link #adjustAllocation(Shape)} restores the original text allocation so caret
     * placement and lyric alignment do not shift.
     */
    private static final class LeadingSlackFieldView extends FieldView {
        private boolean paintingWithLeadingSlack;

        LeadingSlackFieldView(Element elem) {
            super(elem);
        }

        @Override
        public void paint(Graphics g, Shape a) {
            var expanded = a.getBounds();
            expanded.x -= LEADING_PAINT_SLACK_PX;
            expanded.width += LEADING_PAINT_SLACK_PX;
            paintingWithLeadingSlack = true;

            try {
                super.paint(g, expanded);
            } finally {
                paintingWithLeadingSlack = false;
            }
        }

        @Override
        protected Shape adjustAllocation(Shape a) {
            var adjusted = super.adjustAllocation(a);

            if (paintingWithLeadingSlack && adjusted != null) {
                var bounds = adjusted.getBounds();
                bounds.x += LEADING_PAINT_SLACK_PX;
                bounds.width -= LEADING_PAINT_SLACK_PX;
                return bounds;
            }

            return adjusted;
        }
    }

    /** Returns the element this editor session is bound to. */
    public StaffElement getActiveElement() {
        return element;
    }

    /** Returns the line containing {@link #getActiveElement()}. */
    public Line getActiveLine() {
        return line;
    }

    @Override
    protected TextFocusDelegate createFocusDelegate() {
        return new LyricFocusDelegate();
    }

    private class LyricFocusDelegate extends TextFocusDelegate {
        LyricFocusDelegate() {
            super(LyricEditor.this);
        }

        @Override
        public void focusGained(FocusEvent e) {
            focused = true;
            super.focusGained(e);
        }

        @Override
        public void focusLost(FocusEvent e) {
            if (!focused || getParent() == null) {
                return;
            }

            focused = false;
            commit();
            dismiss(false);
        }
    }

    /**
     * Attaches the document listener that drives live width recompute. Must be called
     * after construction-time prefill so programmatic prefill does not trigger.
     */
    public void attachListeners() {
        ((AbstractDocument) getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                throws BadLocationException {
                var filtered = filterInsertion(fb.getDocument().getLength(), 0, text);

                if (filtered != null) {
                    super.insertString(fb, offset, filtered, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
                var filtered = filterInsertion(fb.getDocument().getLength(), length, text);

                if (filtered != null) {
                    super.replace(fb, offset, length, filtered, attrs);
                }
            }
        });

        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                recomputeBounds();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                recomputeBounds();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Plain-text document: attribute changes don't fire here.
            }
        });

        // Boundary characters are normal printables so they arrive via keyTyped rather
        // than the input map. Consuming the event prevents insertion into the document.
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                switch (e.getKeyChar()) {
                    case ' ' -> { e.consume(); commit(); advance(); }
                    case '-' -> { e.consume(); handleHyphen(); }
                    case '=' -> { e.consume(); handleEquals(); }
                    case '_' -> { e.consume(); handleUnderscore(); }
                }
            }
        });

    }

    /**
     * Returns {@code text} unchanged when the proposed insertion is permitted, or
     * {@code null} when it must be rejected. Newlines are dropped silently; insertions
     * that would push the document past {@link #MAX_LENGTH_CHARS} are rejected with a
     * beep so the user notices the limit.
     */
    @Nullable
    private static String filterInsertion(int currentLength, int replacedLength, String text) {
        if (text.isEmpty()) {
            return text;
        }

        if (text.indexOf('\n') >= 0) {
            return null;
        }

        if (currentLength - replacedLength + text.length() > MAX_LENGTH_CHARS) {
            Toolkit.getDefaultToolkit().beep();
            return null;
        }

        return text;
    }

    /**
     * Binds Tab, Enter, and Escape on the {@code WHEN_FOCUSED} input map. Tab and Enter
     * would otherwise insert their literal characters; Escape has no default binding.
     */
    private void installKeyBindings() {
        bindKey(KeyEvent.VK_TAB, ACTION_KEY_TAB, () -> {
            commit();
            advance();
        });

        bindKey(KeyEvent.VK_ENTER, ACTION_KEY_ENTER, () -> {
            commit();
            dismiss(true);
        });

        bindKey(KeyEvent.VK_ESCAPE, ACTION_KEY_ESCAPE, () -> dismiss(true));

        // Commit and dismiss on any click outside the editor. The focusLost handler covers
        // clicks on focusable components (toolbar buttons, etc.); this covers clicks on the
        // score canvas, which is not focusable and would not otherwise steal focus.
        outsideClickListener = event -> {
            if (event.getID() != MouseEvent.MOUSE_PRESSED) {
                return;
            }

            var source = (Component) event.getSource();

            if (source == LyricEditor.this || isAncestorOf(source)) {
                return;
            }

            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            outsideClickListener = null;
            commitAndDismiss();
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void bindKey(int keyCode, String actionKey, Runnable handler) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyCode, 0), actionKey);
        getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.run();
            }
        });
    }

    /**
     * Recomputes the editor's pixel bounds from the current text using the same width
     * formula the renderer uses, then translates the resulting line-local rectangle into
     * Score-local coordinates.
     */
    public void recomputeBounds() {
        if (lineComponent == null) {
            return;
        }

        var layoutResult = lineComponent.getLayoutResult();

        if (layoutResult == null) {
            return;
        }

        var lyricRenderMetrics = score.getLyricRenderMetrics();
        var songLayoutMetrics = score.getSongLayoutMetrics();
        var scaleContext = ScaleContext.getInstance();

        var boxMetrics = lyricRenderMetrics.lyricBoxMetricsSs(getText());
        var advanceSs = Math.max(boxMetrics.advanceSs(), EMPTY_BOX_MIN_WIDTH_SS);
        var anchor = layoutResult.getLyricAnchor(element, songLayoutMetrics);

        var lyricsFont = lyricRenderMetrics.lyricsFont();
        var advanceLeftSs = anchor.centerXSs() - advanceSs / 2.0;
        var heightSs = lyricRenderMetrics.lyricBoxHeightSs();

        var paddingPx = EDITOR_PADDING_SS.toInsetsPx();
        var leftBearingPx = scaleContext.toPixels(boxMetrics.leftBearingSs());
        var rightExtentPx = scaleContext.toPixels(boxMetrics.rightExtentSs());
        var advancePx = scaleContext.toPixels(advanceSs);
        var contentWidthPx = (int) Math.ceil(advancePx) + CARET_SLACK_PX;
        var contentHeightPx = (int) Math.ceil(scaleContext.toPixels(heightSs));
        // Bearing-compensated insets so the visible gap from the inner LineBorder edge to
        // the glyph ink is equal on both sides. JTextField paints the advance origin at
        // contentLeft, so on the left the ink lands at contentLeft + leftBearing and on
        // the right the content area extends beyond the ink by
        // (contentWidth - rightExtent) — i.e. CARET_SLACK_PX, the ceil(advance) rounding
        // remainder, and the glyph's right side bearing. Subtracting each side's slack
        // from paddingPx makes both visible gaps land at paddingPx (with sub-pixel
        // rounding error). The trade-off is that the EmptyBorder bands are no longer
        // symmetric whenever a glyph's bearings or the trailing slack are non-zero —
        // visual centering of the glyph wins over band symmetry.
        var emptyLeftPx = Math.max(0,
            (int) Math.ceil(paddingPx.left - leftBearingPx));
        var emptyRightPx = Math.max(0,
            (int) Math.ceil(paddingPx.right - (contentWidthPx - rightExtentPx)));

        setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Color.BLACK, LINE_BORDER_WIDTH_PX),
            new EmptyBorder(paddingPx.top, emptyLeftPx, paddingPx.bottom, emptyRightPx)
        ));

        // getInsets() now reflects the dynamic border just set above.
        var insets = getInsets();

        // Snap content_left exactly to the advance-origin pixel: JTextField paints there,
        // and any rounding drift would visibly shift the painted text within the box.
        var contentLeftPx = scaleContext.toRoundedPixels(advanceLeftSs);

        // Derive the content top from the (pixel-snapped) baseline and the integer font-metrics
        // ascent, then compensate for FieldView.adjustAllocation. PlainView paints at
        // lineArea.y + ascent, but FieldView first shifts alloc.y by slop/2 (where
        // slop = contentHeight - fontMetrics.getHeight()) whenever they differ — including the
        // common case where contentHeight is smaller than the font's full line height
        // (ascent + descent + leading). Without subtracting slop/2 here, the actual rendered
        // baseline lands 1 px off baselineYPxInt.
        var baselineYPxInt = scaleContext.toRoundedPixels(anchor.baselineYSs());
        var fontMetrics = getFontMetrics(lyricsFont);
        var fieldViewSlopPx = contentHeightPx - fontMetrics.getHeight();
        var contentTopPx = baselineYPxInt - fontMetrics.getAscent() - fieldViewSlopPx / 2;

        var xLinePx = contentLeftPx - insets.left;
        var yLinePx = contentTopPx - insets.top;
        var widthPx = insets.left + contentWidthPx + insets.right;
        var heightPx = insets.top + contentHeightPx + insets.bottom;

        var scoreLocal = SwingUtilities.convertPoint(lineComponent, xLinePx, yLinePx, score);
        setBounds(scoreLocal.x, scoreLocal.y, widthPx, heightPx);
    }

    /**
     * Writes the editor's current text into the active element's lyric for {@link #VERSE}
     * with {@code relation = NONE} and {@code extend = NONE}. Same-text commits and
     * empty-on-empty commits emit zero mutations — the modification bracket is skipped.
     */
    public void commit() {
        commit(StaffElement.SyllableRelation.NONE, Lyric.Extend.NONE);
    }

    /**
     * Writes the editor's current text into the active element's lyric for {@link #VERSE}
     * with the supplied {@code relation} and {@code extend}. Same-text commits and
     * empty-on-empty commits emit zero mutations — the modification bracket is skipped.
     */
    private void commit(StaffElement.SyllableRelation relation, Lyric.Extend extend) {
        var text = getText();
        var existingLyric = element.getLyricForVerse(VERSE);
        var existingText = existingLyric != null ? existingLyric.text() : "";

        if (text.equals(existingText)
                && (existingLyric == null || (existingLyric.relation() == relation && existingLyric.extend() == extend))) {
            return;
        }

        if (text.isEmpty() && existingLyric == null) {
            return;
        }

        line.withModification(() -> line.modifyElement(
            line.getElementIndex(element),
            LYRIC_FIELDS,
            () -> element.setLyricForVerse(VERSE, relation, text, extend)
        ));
    }

    /**
     * Dismisses this editor and opens a new one on the next eligible element in the
     * current line. Eligible elements are non-rests, plus rests that already carry a
     * verse-1 lyric. If no eligible element exists past the current one, simply
     * dismisses without wrapping to the next line.
     */
    public void advance() {
        var currentIndex = line.getElementIndex(element);
        var startIndex = currentIndex + 1;
        var effectiveCount = line.effectiveElementCount();

        for (var i = startIndex; i < effectiveCount; i++) {
            var candidate = line.getElement(i);

            if (isEligibleForLyric(candidate)) {
                dismiss(false);
                openOn(score, line, candidate);
                return;
            }
        }

        dismiss(true);
    }

    private static boolean isEligibleForLyric(StaffElement candidate) {
        if (!candidate.getType().isRest()) {
            return true;
        }

        var lyric = candidate.getLyricForVerse(VERSE);
        return lyric != null && !lyric.text().isBlank();
    }

    private void handleHyphen() {
        if (!getText().isEmpty()) {
            commit(StaffElement.SyllableRelation.SYLLABLE, Lyric.Extend.NONE);
        }

        advance();
    }

    private void handleEquals() {
        if (getText().isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        commit(StaffElement.SyllableRelation.COMPOUND_WORD, Lyric.Extend.NONE);
        advance();
    }

    private void handleUnderscore() {
        if (!getText().isEmpty()) {
            commit(StaffElement.SyllableRelation.NONE, Lyric.Extend.START);
        } else {
            scanBack();
        }

        advance();
    }

    /**
     * Implements the scan-back semantics for {@code _} pressed on an empty editor.
     * Walks backwards to find the previous element whose {@link #VERSE} lyric has
     * non-blank text and whose extend is not {@code STOP} or {@code CONTINUE}, then
     * sets {@code extend = START} on that lyric and removes the current element's lyric
     * — all inside a single modification bracket so undo is atomic.
     */
    private void scanBack() {
        var currentIndex = line.getElementIndex(element);
        StaffElement previousSyllable = null;
        var previousSyllableIndex = -1;

        for (var i = currentIndex - 1; i >= 0; i--) {
            var candidate = line.getElement(i);
            var lyric = candidate.getLyricForVerse(VERSE);

            if (lyric != null && !lyric.text().isBlank()
                    && lyric.extend() != Lyric.Extend.STOP
                    && lyric.extend() != Lyric.Extend.CONTINUE) {
                previousSyllable = candidate;
                previousSyllableIndex = i;
                break;
            }
        }

        var existingLyric = element.getLyricForVerse(VERSE);

        if (previousSyllable != null) {
            var finalPreviousSyllable = previousSyllable;
            var finalPreviousSyllableIndex = previousSyllableIndex;
            var prevLyric = previousSyllable.getLyricForVerse(VERSE);

            // Invariant: the loop assigned previousSyllable only when lyric != null
            if (prevLyric == null) {
                throw RuntimeError.exit("Previous syllable lost verse " + VERSE + " lyric between scan and commit");
            }

            line.withModification(() -> {
                line.modifyElement(finalPreviousSyllableIndex, LYRIC_FIELDS, () ->
                    finalPreviousSyllable.setLyricForVerse(
                        VERSE, prevLyric.relation(), prevLyric.text(), Lyric.Extend.START)
                );

                if (existingLyric != null) {
                    line.modifyElement(currentIndex, LYRIC_FIELDS, () ->
                        element.setLyricForVerse(VERSE, StaffElement.SyllableRelation.NONE, null, Lyric.Extend.NONE)
                    );
                }
            });
        } else if (existingLyric != null) {
            line.withModification(() -> line.modifyElement(
                currentIndex, LYRIC_FIELDS,
                () -> element.setLyricForVerse(VERSE, StaffElement.SyllableRelation.NONE, null, Lyric.Extend.NONE)
            ));
        }
    }

    // Re-entrant guard: clearing focused before commit/dismiss prevents a second
    // focusLost (fired by layout reflow inside the mutation) from re-entering.
    private void commitAndDismiss() {
        if (!focused || getParent() == null) {
            return;
        }

        focused = false;
        commit();
        dismiss(true);
    }

    /**
     * Removes the editor from its parent, clears the active-editor reference on
     * {@code Score}, and repaints the vacated region.
     */
    public void dismiss(boolean isDoneEditing) {
        if (outsideClickListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            outsideClickListener = null;
        }

        var parent = getParent();

        if (parent == null) {
            return;
        }

        var bounds = getBounds();

        // Move focus off the text field before removing it, so the
        // KeyboardFocusManager doesn't retain the detached JTextComponent as focus owner.
        score.requestFocusInWindow();

        parent.remove(this);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);

        // We want to notify actions that editing has ended so they can update their enabled state,
        // but only when not advancing to another lyric editor.
        if (isDoneEditing) {
            MessageCenter.post(new TextEditingDidChangeNotification(false));
        }
    }

    /** Test-only hook to set the focused state without a real focus event. */
    void setFocusedForTesting(boolean focused) {
        this.focused = focused;
    }
}
