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

import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.error.RuntimeError;
import songscribe.message.MessageCenter;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.message.mutation.ElementField;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.EditLyricAction;
import songscribe.ui.component.score.LineComponent;
import songscribe.undo.OpNames;
import songscribe.layout.InsetsSs;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.util.UIUtils;

/**
 * In-place lyric editor overlay parented to {@link ScoreView}. Edits the active-verse lyric
 * of a single {@link StaffElement} with width and baseline matching the rendered lyric box.
 *
 * <pre>
 *  ┌───────────────────────────────────────────────────────────┐
 *  │ LyricEditor lifecycle                                     │
 *  │                                                           │
 *  │   EditLyricAction.actionPerformed                          │
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
 *  │   │   - newline → strip                         │         │
 *  │   │                                             │         │
 *  │   │  Tab/Space  → commit + advance              │         │
 *  │   │  Enter      → commit + dismiss              │         │
 *  │   │  Escape     → applyDismissAdjustment +      │         │
 *  │   │               dismiss (no commit)           │         │
 *  │   │  focus-lost → commit + applyDismissAdjust   │         │
 *  │   │               + dismiss                     │         │
 *  │   │                                             │         │
 *  │   │  Boundary keys:                             │         │
 *  │   │  ┌────────┬───────────────┬───────────────┐ │         │
 *  │   │  │ Key    │ State         │ Effect        │ │         │
 *  │   │  ├────────┼───────────────┼───────────────┤ │         │
 *  │   │  │ -      │ non-empty     │ commit as     │ │         │
 *  │   │  │        │               │ syllable →adv │ │         │
 *  │   │  │ -      │ empty         │ advance only  │ │         │
 *  │   │  │ =, +   │ non-empty,    │ commit as     │ │         │
 *  │   │  │        │ caret-at-end  │ compound →adv │ │         │
 *  │   │  │ =, +   │ empty or mid  │ beep, stay    │ │         │
 *  │   │  │        │               │ open          │ │         │
 *  │   │  │ _      │ non-empty,    │ commit as     │ │         │
 *  │   │  │        │ caret-at-end  │ START → adv   │ │         │
 *  │   │  │ _      │ non-empty,    │ beep, stay    │ │         │
 *  │   │  │        │ caret-mid     │ open          │ │         │
 *  │   │  │ _      │ empty         │ extend chain  │ │         │
 *  │   │  │        │               │ backward →adv │ │         │
 *  │   │  └────────┴───────────────┴───────────────┘ │         │
 *  │   └─────────────────────────────────────────────┘         │
 *  │           │                                               │
 *  │           ▼                                               │
 *  │   advance(): scan forward for eligible element            │
 *  │     eligible: !rest, OR rest with existing lyric          │
 *  │     found: dismiss this, new LyricEditor(line, next)      │
 *  │     none:  dismiss()                                      │
 *  │                                                           │
 *  │   applyDismissAdjustment(): on every dismiss, walk back   │
 *  │     to repair dangling extender or syllable chains.       │
 *  │     Suppressed when extendChainBackward has just built a  │
 *  │     well-formed chain.                                     │
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

    // Placeholder for the user's active verse until multi-verse support lands.
    private static final int CURRENT_VERSE = 1;

    /**
     * Visible padding from the box edge to the JTextField text allocation. For selected
     * text, Swing highlights from the allocation origin to the caret advance, so the
     * editor frame is centered around that same span rather than the glyph ink bounds.
     */
    public static final InsetsSs EDITOR_PADDING_SS = new InsetsSs(0.25, 0.5, 0.25, 0.5);

    private static final int LINE_BORDER_WIDTH_PX = 1;
    private static final int EXTRA_VERTICAL_PADDING_PX = 1;

    /**
     * {@link BasicTextUI#getVisibleEditorRect()} leaves one
     * trailing pixel outside the view allocation. Account for it explicitly so the
     * selected text allocation gets the intended width without growing the visible
     * right margin.
     */
    private static final int TEXT_FIELD_RESERVED_TRAILING_PX = 1;

    /**
     * Minimum horizontal room for the caret when the editor is empty. Non-empty text uses
     * the text advance as the caret/selection end; widening that allocation visually
     * uncenters the selected text span inside the editor frame.
     */
    private static final int MIN_TRAILING_CARET_ROOM_PX = 1;

    /**
     * Swing's {@link FieldView} clips to the field allocation before text is drawn. Some
     * rasterized glyph edges can land just outside that allocation even when Java reports
     * no negative left bearing, so the editor view gives the paint clip a small left guard.
     */
    static final int LEADING_PAINT_SLACK_PX = 1;

    /**
     * Trailing pixel added to the expanded paint clip so the rightmost glyph column is
     * not lost to {@link FieldView}'s right-edge clipping. Distinct from
     * {@link #LEADING_PAINT_SLACK_PX}, which guards the left edge.
     */
    private static final int TRAILING_PAINT_SLACK_PX = 1;

    /**
     * Minimum content-area width when the editor is empty so the caret remains visible
     * and the box reads as a clickable target rather than collapsing to zero.
     */
    private static final double EMPTY_BOX_MIN_WIDTH_SS = 0.125;  // 1px

    private static final String ACTION_KEY_TAB = "lyric.editor.tab";
    private static final String ACTION_KEY_SHIFT_TAB = "lyric.editor.shift.tab";
    private static final String ACTION_KEY_ENTER = "lyric.editor.enter";
    private static final String ACTION_KEY_ESCAPE = "lyric.editor.escape";

    private final ScoreView score;
    private final Line line;
    private final StaffElement element;
    private final @Nullable LineComponent lineComponent;

    private boolean focused;

    @Nullable private AWTEventListener outsideClickListener;

    /**
     * {@code true} when the editor was opened on an element that already carried a
     * {@code CONTINUE} or {@code STOP} extender at construction time (i.e. opened on a
     * carrier lyric).
     */
    private final boolean openedAsExtender;

    /** Font metrics for the current (zoom-scaled) lyrics font; refreshed by {@link #refreshFont}. */
    private FontMetrics fontMetrics = getFontMetrics(getFont());

    /**
     * When {@code true}, {@link #applyDismissAdjustment()} clears the flag and returns
     * immediately without walking the chain. Set by {@code extendChainBackward} after
     * it builds a well-formed chain so the dismiss pass does not tear it down.
     */
    private boolean suppressDismissAdjustment;

    /**
     * Guards against a stacked duplicate of the "lyric will not fit" alert. The alert is modal and
     * steals focus, which fires {@link #focusLost} and re-enters {@link #ensureLyricFits}; while the
     * alert is up this stays {@code true} so the re-entrant check refuses without a second dialog.
     */
    private boolean showingLyricFitAlert;

    /**
     * Constructs a {@link LyricEditor} on {@code element}, attaches it to {@code score},
     * and gives it focus. Used by both {@link EditLyricAction} and
     * {@link #advance()} so the open sequence is centralized.
     */
    public static void openOn(ScoreView score, Line line, StaffElement element) {
        var editor = new LyricEditor(score, line, element);
        score.addOverlay(editor);
        score.setComponentZOrder(editor, 0);
        score.setActiveLyricEditor(editor);

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

    public LyricEditor(ScoreView score, Line line, StaffElement element) {
        this.score = score;
        this.line = line;
        this.element = element;

        var openingLyric = element.getLyricForVerse(CURRENT_VERSE);
        var openingExtend = openingLyric != null ? openingLyric.extend() : null;
        openedAsExtender = openingExtend == Lyric.Extend.CONTINUE
            || openingExtend == Lyric.Extend.STOP;

        var lineIndex = score.getSong().indexOfLine(line);
        lineComponent = lineIndex >= 0 ? score.getLineComponent(lineIndex) : null;

        configureLAF();

        // Tab/Shift-Tab are focus-traversal keys by default; clear both sets so VK_TAB
        // (with and without Shift) reaches the input map rather than moving focus.
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.emptySet());
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, Collections.emptySet());

        installKeyBindings();

        var existingLyric = element.getMainLyric();
        var existingText = existingLyric != null ? existingLyric.text() : null;

        if (existingText != null && !existingText.isBlank()) {
            // selectAll() leaves the caret at the end of the text (mark at 0, dot at length),
            // which gives "fully selected with caret at end" — no follow-up setCaretPosition
            // needed (and using one would collapse the selection).
            setText(existingText);
            selectAll();
        }
    }

    // PlainDocument replaces '\n' with space before calling the filter; override to strip instead.
    @Override
    protected Document createDefaultModel() {
        return new MyPlainDocument();
    }

    private void configureLAF() {
        setUI(new LyricTextFieldUI());
        refreshFont();
        setOpaque(true);
        setBackground(FlatLafProps.getColor(FlatLafKey.SCORE_PAGE_SCREEN_BACKGROUND));
        setForeground(Color.BLACK);
        setCaretColor(Color.BLACK);
        setHorizontalAlignment(LEFT);

        var paddingPx = EDITOR_PADDING_SS.toInsetsPx();
        var rightPaddingPx = Math.max(0, paddingPx.right - TEXT_FIELD_RESERVED_TRAILING_PX);
        setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(Color.BLACK, LINE_BORDER_WIDTH_PX),
            new EmptyBorder(
                paddingPx.top + EXTRA_VERTICAL_PADDING_PX,
                paddingPx.left,
                paddingPx.bottom + EXTRA_VERTICAL_PADDING_PX,
                rightPaddingPx
            )
        ));
    }

    /**
     * Derives the editor's font from the document's base lyrics font scaled by the
     * current zoom factor, since (unlike canvas-rendered text) this overlay is a real
     * {@link JComponent} positioned in absolute pixel coordinates rather than drawn
     * inside a staff-space {@code Graphics2D} transform.
     */
    void refreshFont() {
        var zoomedFont = score.getViewScale().zoomedFont(score.getDocumentFonts().getLyricsFont());
        setFont(zoomedFont);
        fontMetrics = getFontMetrics(zoomedFont);
    }

    /** The three legal shapes a committed syllable can have. */
    public enum CommitKind {
        /** Word-final syllable: SINGLE or END, compound = false. */
        WORD_FINAL,
        /** Word-continuing via hyphen: BEGIN or MIDDLE, compound = false. */
        WORD_CONTINUING_HYPHEN,
        /** Word-continuing via compound join: compound = true. */
        WORD_CONTINUING_COMPOUND
    }

    private record CommitSpec(CommitKind kind, Lyric.Extend extend) {}

    /**
     * The three spacing-relevant booleans a {@code (kind, extend)} pair implies. Derived in one
     * place so {@link #ensureLyricFits} (fit pre-check) and {@link #commitInner} (the actual write)
     * cannot drift apart if the {@link CommitKind}/{@link Lyric.Extend} mapping ever changes.
     */
    private record CommitIntent(boolean wantsCarrier, boolean wantsContinues, boolean wantsCompound) {
        static CommitIntent of(CommitKind kind, Lyric.Extend extend) {
            return new CommitIntent(
                extend == Lyric.Extend.STOP || extend == Lyric.Extend.CONTINUE,
                kind != CommitKind.WORD_FINAL,
                kind == CommitKind.WORD_CONTINUING_COMPOUND);
        }
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
    static final class LeadingSlackFieldView extends FieldView {
        boolean paintingWithLeadingSlack;

        LeadingSlackFieldView(Element elem) {
            super(elem);
        }

        @Override
        public void paint(Graphics g, Shape a) {
            var expanded = a.getBounds();
            expanded.x -= LEADING_PAINT_SLACK_PX;
            expanded.width += LEADING_PAINT_SLACK_PX + TRAILING_PAINT_SLACK_PX;
            paintingWithLeadingSlack = true;

            try {
                super.paint(g, expanded);
            } finally {
                paintingWithLeadingSlack = false;
            }
        }

        @Override
        protected @Nullable Shape adjustAllocation(@Nullable Shape a) {
            Shape adjusted;

            if (paintingWithLeadingSlack && a != null) {
                var textAllocation = a.getBounds();
                textAllocation.x += LEADING_PAINT_SLACK_PX;
                textAllocation.width -= LEADING_PAINT_SLACK_PX;
                adjusted = super.adjustAllocation(textAllocation);
                return keepAllocationAtContentOrigin(textAllocation, adjusted);
            }

            adjusted = super.adjustAllocation(a);
            return keepAllocationAtContentOrigin(a, adjusted);
        }
    }

    /**
     * Prevents {@code adjustAllocation} from shifting the allocation to the left of the
     * original content origin. When {@code super.adjustAllocation} moves the text leftward
     * to center it in a wide allocation, this clamps the x coordinate back to the input
     * content origin so lyric alignment and caret placement remain stable.
     *
     * @param input    the allocation passed to {@link LeadingSlackFieldView#adjustAllocation}
     * @param adjusted the result returned by {@code super.adjustAllocation}
     * @return {@code adjusted} if its x is at or to the right of {@code input.x};
     *         otherwise {@code adjusted} with x clamped to {@code input.x}
     */
    @SuppressWarnings("ConstantValue")
    static @Nullable Shape keepAllocationAtContentOrigin(@Nullable Shape input, @Nullable Shape adjusted) {
        if (input == null || adjusted == null) {
            return adjusted;
        }

        var inputBounds = input.getBounds();
        var adjustedBounds = adjusted.getBounds();

        if (adjustedBounds.x < inputBounds.x) {
            adjustedBounds.x = inputBounds.x;
            return adjustedBounds;
        }

        return adjusted;
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

    @SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
    private static class MyPlainDocument extends PlainDocument {
        @Override
        public void insertString(int offset, String str, AttributeSet a) throws BadLocationException {
            if (str.indexOf('\n') >= 0) {
                str = str.replace("\n", "");
            }

            super.insertString(offset, str, a);
        }
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
            var commitSpec = navigationCommitSpec();

            if (!ensureLyricFits(commitSpec.kind(), commitSpec.extend())) {
                // Keep the editor open and focused so the user can shorten the too-long lyric.
                focused = true;
                requestFocusInWindow();
                return;
            }

            line.withModification(commitOpName(), () -> {
                commitInner(commitSpec.kind(), commitSpec.extend());
                applyDismissAdjustment();
            });
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
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
                var filtered = filterInsertion(fb.getDocument().getLength(), 0, string);

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
                    case ' ' -> {
                        e.consume();
                        breakChainCommitAndAdvance(CommitKind.WORD_FINAL, findNextEligibleIndex());
                    }
                    case '-' -> { e.consume(); handleHyphen(); }
                    case '=', '+' -> { e.consume(); handleCompound(); }
                    case '_' -> { e.consume(); handleUnderscore(); }
                }
            }
        });

        installOutsideClickListener();
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

        if (currentLength - replacedLength + text.length() > MAX_LENGTH_CHARS) {
            UIUtils.beep();
            return null;
        }

        return text;
    }

    /**
     * Binds Tab, Enter, and Escape on the {@code WHEN_FOCUSED} input map. Tab and Enter
     * would otherwise insert their literal characters; Escape has no default binding.
     */
    private void installKeyBindings() {
        bindKey(KeyEvent.VK_TAB, 0, ACTION_KEY_TAB, this::advance);
        bindKey(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK, ACTION_KEY_SHIFT_TAB, this::retreat);

        bindKey(KeyEvent.VK_ENTER, ACTION_KEY_ENTER, () -> {
            var commitSpec = navigationCommitSpec();

            if (!ensureLyricFits(commitSpec.kind(), commitSpec.extend())) {
                // Keep the editor open (it retains focus) so the user can shorten the lyric.
                return;
            }

            line.withModification(commitOpName(), () -> {
                commitInner(commitSpec.kind(), commitSpec.extend());
                applyDismissAdjustment();
            });
            dismiss(true);
        });

        bindKey(KeyEvent.VK_ESCAPE, ACTION_KEY_ESCAPE, () -> {
            line.withModification(this::applyDismissAdjustment);
            dismiss(true);
        });
    }

    /**
     * Installs the global mouse listener that commits and dismisses the editor on any
     * click outside its bounds. The focusLost handler covers clicks on focusable
     * components (toolbar buttons, etc.); this covers clicks on the score canvas, which
     * is not focusable and would not otherwise steal focus.
     *
     * <p>Registered alongside the document and key listeners in {@link #attachListeners}
     * so the AWT listener — which holds a strong reference to this editor — cannot be
     * installed unless the rest of the listener stack is too.
     */
    private void installOutsideClickListener() {
        outsideClickListener = event -> {
            if (event.getID() != MouseEvent.MOUSE_PRESSED) {
                return;
            }

            var source = (Component) event.getSource();

            if (source == this || isAncestorOf(source)) {
                return;
            }

            Toolkit.getDefaultToolkit().removeAWTEventListener(outsideClickListener);
            outsideClickListener = null;
            commitAndDismiss();
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(outsideClickListener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void bindKey(int keyCode, String actionKey, Runnable handler) {
        bindKey(keyCode, 0, actionKey, handler);
    }

    private void bindKey(int keyCode, int modifiers, String actionKey, Runnable handler) {
        UIUtils.bindKey(this, WHEN_FOCUSED, KeyStroke.getKeyStroke(keyCode, modifiers), actionKey, handler);
    }

    /**
     * Recomputes the editor's pixel bounds from the current text using the same width
     * formula the renderer uses, then translates the resulting line-local rectangle into
     * ScoreView-local coordinates.
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
        var text = getText();
        var boxMetrics = lyricRenderMetrics.lyricBoxMetricsSs(text);
        var advanceSs = Math.max(boxMetrics.advanceSs(), EMPTY_BOX_MIN_WIDTH_SS);
        var anchor = layoutResult.getLyricAnchor(element, lyricRenderMetrics);

        var advanceLeftSs = anchor.centerXSs() - advanceSs / 2.0;
        var heightSs = lyricRenderMetrics.editorBoxHeightSs();

        // The editor is a real overlay component positioned in absolute view pixels, so
        // ss→px conversions here honor the current zoom via the view scale (not the
        // fixed document scale). Sizes round up (ceilPx); positions round to nearest.
        var viewScale = score.getViewScale();
        var roundedAdvancePx = viewScale.toViewPx(new Ss(advanceSs)).ceilPx();
        var trailingCaretRoomPx = text.isEmpty()
            ? MIN_TRAILING_CARET_ROOM_PX
            : 0;
        var contentWidthPx = roundedAdvancePx + trailingCaretRoomPx;
        // editorBoxHeightSs covers ascent+descent only; FieldView's selection height uses
        // getHeight() = ascent+descent+leading. Adding leading here makes fieldViewSlopPx
        // equal to SELECTION_MARGIN_PX*2, so the selection gets exactly SELECTION_MARGIN_PX
        // pixels of breathing room above and below.
        var contentHeightPx = viewScale.toViewPx(new Ss(heightSs)).ceilPx()
            + fontMetrics.getLeading();

        var insets = getInsets();

        // Snap content_left exactly to the advance-origin pixel: JTextField paints there,
        // and any rounding drift would visibly shift the painted text within the box.
        var contentLeftPx = viewScale.toViewPx(new Ss(advanceLeftSs)).roundedPx();

        var baselineYPxInt = viewScale.toViewPx(new Ss(anchor.baselineYSs())).roundedPx();
        var fieldViewSlopPx = contentHeightPx - fontMetrics.getHeight();
        var contentTopPx = baselineYPxInt - fontMetrics.getAscent() - fieldViewSlopPx / 2;

        var xLinePx = contentLeftPx - insets.left;
        var yLinePx = contentTopPx - insets.top;
        var widthPx = insets.left + contentWidthPx + insets.right + TEXT_FIELD_RESERVED_TRAILING_PX;
        var heightPx = insets.top + contentHeightPx + insets.bottom;

        var scoreLocal = SwingUtilities.convertPoint(lineComponent, xLinePx, yLinePx, score);
        setBounds(scoreLocal.x, scoreLocal.y, widthPx, heightPx);

    }

    /**
     * Writes the editor's current text into the active element's lyric for {@link #CURRENT_VERSE}
     * as a word-final syllable with {@code extend = NONE}. Same-text commits and
     * empty-on-empty commits emit zero mutations — the modification bracket is skipped.
     */
    public void commit() {
        commit(CommitKind.WORD_FINAL, Lyric.Extend.NONE);
    }

    private void commit(CommitKind kind, Lyric.Extend extend) {
        if (!ensureLyricFits(kind, extend)) {
            return;
        }

        line.withModification(commitOpName(), () -> commitInner(kind, extend));
    }

    /**
     * Returns whether committing the current text as {@code kind}/{@code extend} keeps the line
     * layout feasible. A wider syllable can force spacing past the staff margin, and committing it
     * anyway leaves the line unable to lay out (issue #449); when that would happen this warns the
     * user, leaves the model untouched, and returns false so the caller aborts the commit and keeps
     * the editor open for the user to shorten the lyric.
     *
     * <p>An already-overflowing line is never blocked, so the user can still shorten a too-long
     * syllable to recover; only an edit that turns a fitting line into one that cannot lay out is
     * refused.
     */
    private boolean ensureLyricFits(CommitKind kind, Lyric.Extend extend) {
        if (showingLyricFitAlert) {
            // The alert's focus-steal re-entered this check; refuse without stacking a second alert.
            return false;
        }

        var intent = CommitIntent.of(kind, extend);

        // The candidate syllabic only needs the correct continues-status: that is the sole part of
        // the committed syllabic that affects horizontal spacing (see LyricEditFitCalculator).
        var probeSyllabic = deriveProbeSyllabic(intent);

        var metrics = score.getLyricRenderMetrics();
        var marginSs = line.getSong().getLineWidthSs();

        var index = line.getElementIndex(element);
        var candidate = new Lyric(CURRENT_VERSE, getText(), extend, probeSyllabic, intent.wantsCompound());

        // Probe the candidate first: it accepts almost every real edit, and returning here spares the
        // already-overflowing check below its own full rebuild-and-solve of the line.
        if (LyricEditFitCalculator.lyricEditFits(line, index, candidate, metrics, marginSs)) {
            return true;
        }

        if (!LyricEditFitCalculator.lineFits(line, metrics, marginSs)) {
            return true;
        }

        showingLyricFitAlert = true;

        try {
            OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_LINE_TOO_FULL, Strings.ERROR_LINE_FULL_LYRIC);
        } finally {
            showingLyricFitAlert = false;
        }

        return false;
    }

    /**
     * The syllabic to probe the fit-check with: {@code null} for a melisma carrier, {@code BEGIN}
     * for a word-continuing syllable (reserves the hyphen cell), {@code SINGLE} otherwise. Only the
     * continues-status matters to horizontal spacing (see {@link LyricEditFitCalculator}).
     */
    private static Lyric.@Nullable Syllabic deriveProbeSyllabic(CommitIntent intent) {
        if (intent.wantsCarrier()) {
            return null;
        }

        if (intent.wantsContinues()) {
            return Lyric.Syllabic.BEGIN;
        }

        return Lyric.Syllabic.SINGLE;
    }

    /**
     * The add/edit/delete op-name for committing the editor's current text into
     * {@code element}'s lyric, derived from before/after text emptiness. Evaluate
     * this <em>before</em> opening the modification bracket (as the labeled
     * {@code withModification} argument) so the op-name is captured at the depth
     * 0->1 transition — a nested bracket never re-captures.
     */
    private String commitOpName() {
        var existingLyric = element.getLyricForVerse(CURRENT_VERSE);
        var beforeText = existingLyric != null ? existingLyric.text() : "";
        return OpNames.lyricLabel(beforeText, getText());
    }

    /**
     * Writes the editor's current text into the active element's lyric for {@link #CURRENT_VERSE},
     * then sets the syllable boundary on this element (and propagates to the next
     * lyric-bearing element where needed) via {@link Line#setSyllableBoundary}.
     *
     * <p>The text/extend write goes through the {@link StaffElement#setLyricForVerse}
     * primitive with a placeholder syllabic; the follow-up {@code setSyllableBoundary}
     * call corrects the syllabic from chain context. When the resulting syllabic matches
     * the placeholder, the boundary helper skips its own emission, so a plain word-final
     * commit still produces exactly one {@code ElementModification}.
     *
     * <p>Same-text/equivalent-state commits and empty-on-empty commits emit zero
     * mutations — assumes an open modification bracket so zero-mutation early-returns
     * produce an empty bracket with no notification.
     *
     * @param kind   the syllable shape being committed
     * @param extend melisma extender state for the lyric
     */
    private void commitInner(CommitKind kind, Lyric.Extend extend) {
        var text = getText();
        var existingLyric = element.getLyricForVerse(CURRENT_VERSE);

        var existingText = existingLyric != null ? existingLyric.text() : "";
        var intent = CommitIntent.of(kind, extend);
        var existingSyllabic = existingLyric != null ? existingLyric.syllabic() : null;
        var existingContinues = existingSyllabic == Lyric.Syllabic.BEGIN
            || existingSyllabic == Lyric.Syllabic.MIDDLE;
        var existingCompound = existingLyric != null && existingLyric.compound();

        if (text.equals(existingText)
                && existingLyric != null
                && existingLyric.extend() == extend
                && existingContinues == intent.wantsContinues()
                && existingCompound == intent.wantsCompound()) {
            return;
        }

        if (text.isEmpty() && existingLyric == null) {
            return;
        }

        var index = line.getElementIndex(element);
        var placeholderSyllabic = intent.wantsCarrier() ? null : Lyric.Syllabic.SINGLE;

        line.modifyElement(index, ElementField.LYRIC, () ->
            element.setLyricForVerse(CURRENT_VERSE, placeholderSyllabic, false, text, extend));

        if (!text.isEmpty() && !intent.wantsCarrier()) {
            line.setSyllableBoundary(index, CURRENT_VERSE, kind == CommitKind.WORD_FINAL, kind == CommitKind.WORD_CONTINUING_COMPOUND);
        }
    }

    /**
     * Commits the editor's text with the given parameters, runs the dismiss-adjustment
     * pass, then advances to the next eligible element (or dismisses if none exists).
     * The commit and adjustment are wrapped in a single modification bracket so both
     * produce one {@link songscribe.message.notification.SongDidChangeNotification}.
     */
    public void advance(CommitKind kind, Lyric.Extend extend) {
        advanceWithIndex(kind, extend, findNextEligibleIndex());
    }

    /**
     * Commits the editor's text as a word-final syllable (no compound, no melisma),
     * runs the dismiss-adjustment pass, then advances to the next eligible element.
     */
    public void advance() {
        var commitSpec = navigationCommitSpec();
        advance(commitSpec.kind(), commitSpec.extend());
    }

    /**
     * Commits the editor's text as a word-final syllable (no compound, no melisma),
     * runs the dismiss-adjustment pass, then retreats to the previous eligible element.
     */
    public void retreat() {
        var commitSpec = navigationCommitSpec();
        advanceWithIndex(commitSpec.kind(), commitSpec.extend(), findPreviousEligibleIndex());
    }

    private void advanceWithIndex(CommitKind kind, Lyric.Extend extend, int nextIndex) {
        if (!ensureLyricFits(kind, extend)) {
            return;
        }

        line.withModification(commitOpName(), () -> {
            commitInner(kind, extend);
            applyDismissAdjustment();
        });
        openIndexOrDismiss(nextIndex);
    }

    // Must NOT be inside an open modification bracket — opens its own.
    private void breakChainCommitAndAdvance(CommitKind kind, int nextIndex) {
        if (!ensureLyricFits(kind, Lyric.Extend.NONE)) {
            return;
        }

        var currentIndex = line.getElementIndex(element);
        line.withModification(commitOpName(), () -> {
            breakChainAtCurrentElement(currentIndex);
            suppressDismissAdjustment = true;
            commitInner(kind, Lyric.Extend.NONE);
            applyDismissAdjustment();
        });
        openIndexOrDismiss(nextIndex);
    }

    /**
     * Tab/Shift-Tab without a text edit should not rewrite the current lyric's syllabic
     * or extend state. Reuse the stored shape when the text is unchanged; otherwise fall
     * back to the default word-final/no-melisma commit.
     */
    private CommitSpec navigationCommitSpec() {
        var lyric = element.getLyricForVerse(CURRENT_VERSE);

        if (lyric == null || !getText().equals(lyric.text())) {
            return new CommitSpec(CommitKind.WORD_FINAL, Lyric.Extend.NONE);
        }

        var extend = lyric.extend();

        if (extend == Lyric.Extend.CONTINUE || extend == Lyric.Extend.STOP) {
            return new CommitSpec(CommitKind.WORD_FINAL, extend);
        }

        var kind = switch (lyric.syllabic()) {
            case BEGIN, MIDDLE -> lyric.compound()
                ? CommitKind.WORD_CONTINUING_COMPOUND
                : CommitKind.WORD_CONTINUING_HYPHEN;
            case SINGLE, END -> CommitKind.WORD_FINAL;
            case null -> throw RuntimeError.exit(
                "Text-bearing lyric at editor element is missing syllabic");
        };

        return new CommitSpec(kind, extend);
    }

    private void openIndexOrDismiss(int nextIndex) {
        if (nextIndex >= 0) {
            dismiss(false);
            openOn(score, line, line.getElement(nextIndex));
        } else {
            dismiss(true);
        }
    }

    /**
     * Returns true when {@code index} is a structurally valid lyric target: a pitched note,
     * rest, or grace note that is NOT the host of a paired grace note. This is the single
     * source of truth for the host-block rule used by both action enablement and navigation.
     */
    public static boolean isLyricTargetEligible(Line line, int index) {
        if (line.isHostOfPairedGraceNote(index)) {
            return false;
        }

        var type = line.getElement(index).getType();
        return type.isPitchedNote() || type.isRest() || type.isGraceNote();
    }

    /** Package-private for testing. */
    static int findNextEligibleIndex(Line searchLine, int currentIndex, int verse) {
        var count = searchLine.effectiveElementCount();

        for (var i = currentIndex + 1; i < count; i++) {
            if (isLyricTargetEligible(searchLine, i) && searchLine.getElement(i).isEligibleForLyric(verse)) {
                return i;
            }
        }

        return -1;
    }

    /** Package-private for testing. */
    static int findPreviousEligibleIndex(Line searchLine, int currentIndex, int verse) {
        for (var i = currentIndex - 1; i >= 0; i--) {
            if (isLyricTargetEligible(searchLine, i) && searchLine.getElement(i).isEligibleForLyric(verse)) {
                return i;
            }
        }

        return -1;
    }

    private int findNextEligibleIndex() {
        return findNextEligibleIndex(line, line.getElementIndex(element), CURRENT_VERSE);
    }

    private int findPreviousEligibleIndex() {
        return findPreviousEligibleIndex(line, line.getElementIndex(element), CURRENT_VERSE);
    }

    private void handleHyphen() {
        if (openedAsExtender) {
            if (getText().isEmpty()) {
                UIUtils.beep();
                return;
            }

            var nextIndex = findNextEligibleIndex();

            if (nextIndex < 0) {
                UIUtils.beep();
                return;
            }

            breakChainCommitAndAdvance(CommitKind.WORD_CONTINUING_HYPHEN, nextIndex);
            return;
        }

        if (!getText().isEmpty()) {
            var nextIndex = findNextEligibleIndex();

            if (nextIndex < 0) {
                UIUtils.beep();
                return;
            }

            advanceWithIndex(CommitKind.WORD_CONTINUING_HYPHEN, Lyric.Extend.NONE, nextIndex);
            return;
        }

        // Lone '-' on empty editor, not a carrier.

        if (element.getLyricForVerse(CURRENT_VERSE) != null) {
            UIUtils.beep();
            return;
        }

        var currentIndex = line.getElementIndex(element);
        var backIndex = findPreviousLyricBearingIndex(currentIndex);
        var backLyric = backIndex >= 0
            ? line.getElement(backIndex).getLyricForVerse(CURRENT_VERSE)
            : null;

        if (backLyric == null) {
            UIUtils.beep();
            return;
        }

        var backSyllabic = backLyric.syllabic();

        if (!Lyric.syllabicContinues(backSyllabic)) {
            UIUtils.beep();
            return;
        }

        var nextIndex = findNextEligibleIndex();

        if (nextIndex < 0) {
            UIUtils.beep();
            return;
        }

        openIndexOrDismiss(nextIndex);
    }

    private boolean isCaretAtEnd() {
        return getCaretPosition() == getText().length();
    }

    private void handleCompound() {
        if (getText().isEmpty() || !isCaretAtEnd()) {
            UIUtils.beep();
            return;
        }

        var nextIndex = findNextEligibleIndex();

        if (nextIndex < 0) {
            UIUtils.beep();
            return;
        }

        if (openedAsExtender) {
            breakChainCommitAndAdvance(CommitKind.WORD_CONTINUING_COMPOUND, nextIndex);
            return;
        }

        advanceWithIndex(CommitKind.WORD_CONTINUING_COMPOUND, Lyric.Extend.NONE, nextIndex);
    }

    private void handleUnderscore() {
        if (!getText().isEmpty()) {
            UIUtils.beep();
            return;
        }

        extendChainBackward();
    }

    /**
     * Implements the empty-editor {@code _} keystroke: retroactively builds a
     * {@code CONTINUE} chain from the previous lyric-bearing element through the
     * current element, then advances. The chain build runs in its own modification
     * bracket; the subsequent {@link #advance()} suppresses the dismiss adjustment so
     * the just-built chain is not torn down.
     */
    private void extendChainBackward() {
        var currentIndex = line.getElementIndex(element);
        var backIndex = findPreviousLyricBearingIndex(currentIndex);

        if (backIndex < 0) {
            UIUtils.beep();
            return;
        }

        var backElement = line.getElement(backIndex);
        var backLyric = backElement.getLyricForVerse(CURRENT_VERSE);

        // Invariant: findPreviousLyricBearingIndex only returns indices with non-null lyrics.
        if (backLyric == null) {
            throw RuntimeError.exit("Predecessor at " + backIndex + " lost verse " + CURRENT_VERSE + " lyric between scan and rewrite");
        }

        line.withModification(() -> {
            var backExtend = backLyric.extend();

            if (backExtend == Lyric.Extend.STOP) {
                // STOP carrier: flip back to CONTINUE so the new chain extends through it.
                line.modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.setLyricForVerse(CURRENT_VERSE, null, false, null, Lyric.Extend.CONTINUE));
            } else if (backExtend != Lyric.Extend.CONTINUE) {
                // Text-bearing (NONE or START): rewrite extend to START, preserving syllabic/compound/text.
                line.modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.setLyricForVerse(CURRENT_VERSE,
                        backLyric.syllabic(), backLyric.compound(),
                        backLyric.text(), Lyric.Extend.START));
            }
            // CONTINUE carrier: leave unchanged — the chain already extends through it.

            for (var i = backIndex + 1; i < currentIndex; i++) {
                var midElement = line.getElement(i);
                line.modifyElement(i, ElementField.LYRIC, () ->
                    midElement.setLyricForVerse(CURRENT_VERSE, null, false, null, Lyric.Extend.CONTINUE));
            }

            line.modifyElement(currentIndex, ElementField.LYRIC, () ->
                element.setLyricForVerse(CURRENT_VERSE, null, false, null, Lyric.Extend.STOP));
        });

        suppressDismissAdjustment = true;
        advance();
    }

    // Re-entrant guard: clearing focused before the bracket opens prevents a second
    // focusLost (fired by layout reflow inside the mutation) from re-entering.
    private void commitAndDismiss() {
        if (!focused || getParent() == null) {
            return;
        }

        focused = false;
        var commitSpec = navigationCommitSpec();

        if (!ensureLyricFits(commitSpec.kind(), commitSpec.extend())) {
            // Keep the editor open and focused; re-arm the outside-click listener (the caller
            // removed it) so a later click can dismiss the editor once the lyric fits.
            focused = true;
            installOutsideClickListener();
            requestFocusInWindow();
            return;
        }

        line.withModification(commitOpName(), () -> {
            commitInner(commitSpec.kind(), commitSpec.extend());
            applyDismissAdjustment();
        });
        dismiss(true);
    }

    // Must be called inside an open modification bracket.
    private void applyDismissAdjustment() {
        if (suppressDismissAdjustment) {
            suppressDismissAdjustment = false;
            return;
        }

        // Editor was opened on a carrier and the user dismissed without typing — the chain
        // is already well-formed, so nothing to repair.
        if (openedAsExtender && getText().isEmpty()) {
            return;
        }

        var currentIndex = line.getElementIndex(element);

        if (openedAsExtender) {
            // Editor was opened on a carrier and text was committed in its place.
            // Terminate the predecessor extender chain and clear stale forward
            // carriers up to the next STOP or text-bearing element.
            breakChainAtCurrentElement(currentIndex);
            return;
        }

        // Common case: repair any dangling chain marker left on a predecessor.
        line.adjustNeighborsForLyricDeletion(currentIndex, CURRENT_VERSE);
    }

    private void terminatePrecedingContinueChain(int currentIndex) {
        var backIndex = findPreviousLyricBearingIndex(currentIndex);

        if (backIndex < 0) {
            return;
        }

        var backLyric = line.getElement(backIndex).getLyricForVerse(CURRENT_VERSE);

        if (backLyric == null) {
            return;
        }

        var backExtend = backLyric.extend();

        if (backExtend == Lyric.Extend.CONTINUE) {
            rewriteLyricExtend(backIndex, backLyric, Lyric.Extend.STOP);
        } else if (backExtend == Lyric.Extend.START) {
            // START directly precedes the break point — the whole chain collapses.
            rewriteLyricExtend(backIndex, backLyric, Lyric.Extend.NONE);
        }
    }

    private void clearForwardCarriers(int currentIndex) {
        var effectiveCount = line.effectiveElementCount();

        for (var i = currentIndex + 1; i < effectiveCount; i++) {
            var forwardElement = line.getElement(i);
            var forwardLyric = forwardElement.getLyricForVerse(CURRENT_VERSE);

            if (forwardLyric == null) {
                continue;
            }

            var extend = forwardLyric.extend();

            if (extend != Lyric.Extend.CONTINUE && extend != Lyric.Extend.STOP) {
                // Text-bearing (extend NONE or START): halt without modification.
                return;
            }

            var forwardIndex = i;
            line.modifyElement(forwardIndex, ElementField.LYRIC, () ->
                forwardElement.setLyricForVerse(CURRENT_VERSE, null, false, null, Lyric.Extend.NONE));

            if (extend == Lyric.Extend.STOP) {
                return;
            }
        }
    }

    // Must be called inside an open modification bracket.
    void breakChainAtCurrentElement(int currentIndex) {
        terminatePrecedingContinueChain(currentIndex);
        clearForwardCarriers(currentIndex);
    }

    private void rewriteLyricExtend(int index, Lyric existing, Lyric.Extend newExtend) {
        var indexElement = line.getElement(index);
        line.modifyElement(index, ElementField.LYRIC, () ->
            indexElement.setLyricForVerse(CURRENT_VERSE,
                existing.syllabic(), existing.compound(), existing.text(), newExtend));
    }

    /**
     * Removes the editor from its parent, clears the active-editor reference on
     * {@code ScoreView}, and repaints the vacated region.
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
        score.setActiveLyricEditor(null);
        parent.repaint(bounds.x, bounds.y, bounds.width, bounds.height);

        // We want to notify actions that editing has ended so they can update their enabled state,
        // but only when not advancing to another lyric editor.
        if (isDoneEditing) {
            MessageCenter.post(new TextEditingDidChangeNotification(false));
        }
    }

    /**
     * Walks backward from {@code fromIndex - 1} and returns the index of the first
     * element whose {@code CURRENT_VERSE} lyric is non-null, or {@code -1} if none.
     */
    private int findPreviousLyricBearingIndex(int fromIndex) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            if (line.getElement(i).getLyricForVerse(CURRENT_VERSE) != null) {
                return i;
            }
        }

        return -1;
    }

    /** Test-only hook to set the focused state without a real focus event. */
    void setFocusedForTesting(boolean focused) {
        this.focused = focused;
    }

    /** Test-only hook to set the suppress-dismiss-adjustment flag directly. */
    void setSuppressDismissAdjustmentForTesting(boolean suppress) {
        suppressDismissAdjustment = suppress;
    }
}
