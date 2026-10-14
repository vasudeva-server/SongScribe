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

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.KeyboardFocusManager;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.FieldView;
import javax.swing.text.PlainDocument;
import javax.swing.text.View;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.LyricRun;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.error.RuntimeError;
import songscribe.layout.InsetsSs;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.message.MessageCenter;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.score.LineComponent;
import songscribe.undo.OpNames;
import songscribe.util.LogUtils;
import songscribe.util.UIUtils;

/**
 * In-place lyric editor overlay parented to {@link ScoreView}, editing the active-script lyric
 * of a single {@link StaffElement}.
 *
 * <p>Invariant: while the editor is active, no external code path may mutate the song or fire any
 * toolbar keystroke. This is enforced by {@code DISABLE_WHEN_EDITING_TEXT} on every toolbar
 * {@code UIAction}; {@code LyricEditorActionAuditTest} locks the whitelist.
 */
public final class LyricEditor extends MyJTextField {

    private static final Logger LOG = LoggerFactory.getLogger(LyricEditor.class);

    static final int MAX_LENGTH_CHARS = 32;

    /**
     * {@code \p{Mn}} and {@code \p{Mc}} are needed: in Devanagari and Bengali a vowel sign is a
     * combining mark, so without them a word is cut off after its first consonant.
     */
    private static final Pattern FIRST_WORD_PATTERN =
        Pattern.compile("^\\s*([\\p{L}\\p{Mn}\\p{Mc}]+)");

    private static final String HYPHEN_TEXT = "-";
    private static final String UNDERSCORE_TEXT = "_";

    // The long a Alt-A inserts and the capital Alt-Shift-A inserts. Transliterated Sanskrit
    // lyrics need them constantly and no keyboard layout in common use types them directly.
    static final String LONG_A = "ā";
    static final String LONG_A_CAPITAL = "Ā";

    // The n with tilde Alt-N inserts and the capital Alt-Shift-N inserts. Transliterated
    // Bengali lyrics need it, and on the US macOS layout Option-N is a dead key that
    // waits for a second keystroke instead of typing the character directly.
    static final String N_TILDE = "ñ";
    static final String N_TILDE_CAPITAL = "Ñ";

    /**
     * Swing highlights selected text from the allocation origin to the caret advance, so the frame
     * is centered on that span, not on the glyph ink bounds.
     */
    public static final InsetsSs EDITOR_PADDING_SS = new InsetsSs(0.25, 0.5, 0.25, 0.5);

    private static final int LINE_BORDER_WIDTH_PX = 1;
    private static final int EXTRA_VERTICAL_PADDING_PX = 1;

    /**
     * {@link BasicTextUI#getVisibleEditorRect()} leaves one trailing pixel outside the view
     * allocation.
     */
    private static final int TEXT_FIELD_RESERVED_TRAILING_PX = 1;

    /**
     * Widening the allocation for non-empty text uncenters the selected span in the frame.
     */
    private static final int MIN_TRAILING_CARET_ROOM_PX = 1;

    /**
     * {@link FieldView} clips to the field allocation; some glyph edges land just outside it.
     */
    static final int LEADING_PAINT_SLACK_PX = 1;

    /**
     * Right-edge counterpart of {@link #LEADING_PAINT_SLACK_PX}.
     */
    private static final int TRAILING_PAINT_SLACK_PX = 1;

    /**
     * Keeps the caret visible and the empty box a clickable target.
     */
    private static final double EMPTY_BOX_MIN_WIDTH_SS = 0.125;  // 1px

    /**
     * Stand-in the editor opens with on an element that plays a role in a neighboring syllable.
     * Never text: while intact the editor commits as if empty (see {@link #commitText()}).
     */
    private enum Placeholder {
        HYPHEN(LyricEditor.HYPHEN_TEXT),
        MELISMA(LyricEditor.UNDERSCORE_TEXT);

        private final String text;

        Placeholder(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    private static final String ACTION_KEY_TAB = "lyric.editor.tab";
    private static final String ACTION_KEY_SHIFT_TAB = "lyric.editor.shift.tab";
    private static final String ACTION_KEY_ENTER = "lyric.editor.enter";
    private static final String ACTION_KEY_ESCAPE = "lyric.editor.escape";

    private final ScoreView score;
    private final Line line;
    private final StaffElement element;
    private final LineComponent lineComponent;

    /**
     * Captured at open: the active script changing under an open editor would split one edit
     * across two languages.
     */
    private final Song.Script activeScript;

    private final LyricChainEditor chainEditor;

    private boolean focused;

    @Nullable private AWTEventListener outsideClickListener;

    private final boolean openedAsExtender;

    /**
     * {@code null} when the editor was opened on a syllable or on an element with no role.
     */
    private @Nullable Placeholder placeholder;

    private FontMetrics fontMetrics = getFontMetrics(getFont());

    /**
     * Keeps the dismiss pass from tearing down the chain {@code extendChainBackward} built.
     */
    private boolean suppressDismissAdjustment;

    /**
     * The modal "lyric will not fit" alert steals focus, which fires {@link #focusLost} and
     * re-enters {@link #ensureLyricFits}; this stops a second dialog.
     */
    private boolean showingLyricFitAlert;

    /**
     * macOS follows the consumed Alt-A key press with a key typed carrying the layout's Option-A
     * character (å); this drops that one event and leaves every other Option-key character typeable.
     */
    private boolean dropNextTypedChar;

    public static void openOn(ScoreView score, Line line, StaffElement element) {
        var editor = new LyricEditor(score, line, element);
        score.addOverlay(editor);
        // Reclaims the topmost index, pushing any line overlays below the caret.
        score.setComponentZOrder(editor, ScoreView.LYRIC_EDITOR_Z_ORDER);
        score.setActiveLyricEditor(editor);

        // A mutation that triggered this open (e.g. committing a lyric before advancing)
        // may have invalidated the line's layout. Force a synchronous layout pass so
        // recomputeBounds sees fresh anchor positions on the first paint.
        editor.lineComponent.ensureLayout();

        editor.recomputeBounds();
        editor.attachListeners();
        editor.setVisible(true);

        // Defer focus so the toolbar button that triggered the action can finish its
        // own focus dance before we request focus, preventing an immediate focusLost.
        SwingUtilities.invokeLater(editor::requestFocusInWindow);
        score.revalidate();
        score.repaint(editor.getBounds());
        editor.repaintHyphenChainPreview();
    }

    public LyricEditor(ScoreView score, Line line, StaffElement element) {
        this.score = score;
        this.line = line;
        this.element = element;
        activeScript = line.getSong().getActiveScript();
        chainEditor = new LyricChainEditor(line, activeScript);

        var openingLyric = element.getLyricForScript(activeScript);
        var openingExtend = openingLyric != null ? openingLyric.extend() : null;
        openedAsExtender = openingExtend == Lyric.Extend.CONTINUE
            || openingExtend == Lyric.Extend.STOP;

        lineComponent = score.requireLineComponent(line);

        configureLAF();

        // Tab/Shift-Tab are focus-traversal keys by default; clear both sets so VK_TAB
        // (with and without Shift) reaches the input map rather than moving focus.
        setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, Collections.emptySet());
        setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, Collections.emptySet());

        installKeyBindings();

        var existingText = openingLyric != null ? openingLyric.text() : null;

        if (existingText != null && !existingText.isBlank()) {
            // selectAll() leaves the caret at the end of the text (mark at 0, dot at length),
            // which gives "fully selected with caret at end" — no follow-up setCaretPosition
            // needed (and using one would collapse the selection).
            setText(existingText);
            selectAll();
        } else {
            placeholder = derivePlaceholder();

            if (placeholder != null) {
                setText(placeholder.text());
                selectAll();
            }
        }

        logState("open");
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

    void refreshFont() {
        var zoomedFont = score.getViewScale().zoomedFont(score.getDocumentFonts().getLyricsFont());
        setFont(zoomedFont);
        fontMetrics = getFontMetrics(zoomedFont);
    }

    /**
     * The legal shapes of a committed syllable; a neutral commit keeps the stored shape (see
     * {@link #neutralCommitSpec()}).
     */
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
     * Derived in one place so {@link #ensureLyricFits} and {@link #commitInner}'s no-op check
     * cannot drift apart if the {@link CommitKind}/{@link Lyric.Extend} mapping changes.
     *
     * <p><b>Assumed by {@code wantsContinues}:</b> a non-{@link CommitKind#WORD_FINAL} commit
     * always carries non-blank text and a non-carrier extend. A new continuing commit path must
     * uphold it, or the no-op check reads a real change as no change and skips the write.
     */
    private record CommitIntent(boolean wantsCarrier, boolean wantsContinues, boolean wantsCompound) {
        static CommitIntent of(CommitKind kind, Lyric.Extend extend) {
            return new CommitIntent(
                Lyric.isCarrier(extend),
                kind != CommitKind.WORD_FINAL,
                kind == CommitKind.WORD_CONTINUING_COMPOUND);
        }
    }

    /**
     * Scopes the text-view customization to this overlay, not the global look-and-feel.
     */
    private static final class LyricTextFieldUI extends BasicTextFieldUI {
        @Override
        public View create(Element elem) {
            return new LeadingSlackFieldView(elem);
        }
    }

    /**
     * Gives the paint clip one extra pixel on the left so glyph edges are not clipped; the
     * original text allocation is restored so caret placement and lyric alignment do not shift.
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

    public StaffElement getActiveElement() {
        return element;
    }

    public Line getActiveLine() {
        return line;
    }

    public Song.Script getActiveScript() {
        return activeScript;
    }

    @Override
    protected TextFocusDelegate createFocusDelegate() {
        return new LyricFocusDelegate();
    }

    /**
     * Trace only, kept deliberately: with the {@code raw} traces in the key listener and document
     * filter it shows a dead key's whole path. Stays until Alt-N is tried on Windows and Linux.
     */
    @Override
    protected void processInputMethodEvent(InputMethodEvent event) {
        trace("raw processInputMethodEvent: {} sel={}..{} caret={}",
            event, getSelectionStart(), getSelectionEnd(), getCaretPosition());
        super.processInputMethodEvent(event);
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
            var commitSpec = neutralCommitSpec();

            if (!ensureLyricFits(commitSpec.kind(), commitSpec.extend())) {
                // Keep the editor open and focused so the user can shorten the too-long lyric.
                focused = true;
                requestFocusInWindow();
                return;
            }

            line.getSong().withModification(commitOpName(), () -> {
                commitInner(commitSpec.kind(), commitSpec.extend());
                applyDismissAdjustment();
            });
            dismiss(false);
        }
    }

    /**
     * Call after construction-time prefill, so the prefill does not trigger the listeners.
     */
    public void attachListeners() {
        ((AbstractDocument) getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
                trace("filter insertString: offset={} string='{}' sel={}..{} caret={}",
                    offset, string, getSelectionStart(), getSelectionEnd(), getCaretPosition());
                var filtered = filterInsertion(fb.getDocument().getLength(), 0, string);

                if (filtered != null) {
                    super.insertString(fb, offset, filtered, attr);
                }
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, @Nullable String text, AttributeSet attrs)
                throws BadLocationException {
                trace("filter replace: offset={} length={} text='{}' sel={}..{} caret={}",
                    offset, length, text, getSelectionStart(), getSelectionEnd(), getCaretPosition());

                // A null text is not a rejected edit: JTextComponent#replaceInputMethodText
                // passes it to mean "no text to insert," e.g. while clearing the selection a
                // dead key like Option-N is about to compose over. The removal must still go
                // through, so this bypasses filterInsertion, which only judges text to insert.
                if (text == null) {
                    super.replace(fb, offset, length, null, attrs);
                    return;
                }

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
            public void keyPressed(KeyEvent e) {
                trace("raw keyPressed: keyCode={} keyChar={} alt={} shift={} ctrl={} meta={} altGraph={}",
                    e.getKeyCode(), (int) e.getKeyChar(), e.isAltDown(), e.isShiftDown(),
                    e.isControlDown(), e.isMetaDown(), e.isAltGraphDown());

                // Only the key typed event generated by this very press may be dropped.
                dropNextTypedChar = false;

                if (isLongAKeyPress(e)) {
                    insertAccentedLetter(e, "key ALT-A", LONG_A, LONG_A_CAPITAL);
                } else if (isNTildeKeyPress(e)) {
                    insertAccentedLetter(e, "key ALT-N", N_TILDE, N_TILDE_CAPITAL);
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                trace("raw keyTyped: keyChar={} ({}) dropNextTypedChar={}",
                    (int) e.getKeyChar(), e.getKeyChar(), dropNextTypedChar);

                if (dropNextTypedChar) {
                    dropNextTypedChar = false;
                    e.consume();
                    return;
                }

                switch (e.getKeyChar()) {
                    case ' ' -> {
                        e.consume();
                        logState("key SPACE");

                        // Space ends a word, so over a placeholder it reads as wiping it out:
                        // clearing the field routes the commit through the matching chain break.
                        if (isPlaceholderIntact()) {
                            setText("");
                        }

                        breakChainCommitAndAdvance(CommitKind.WORD_FINAL, findNextEligibleIndex());
                    }
                    case '-' -> { e.consume(); logState("key HYPHEN"); handleHyphen(); }
                    case '=', '+' -> { e.consume(); logState("key COMPOUND"); handleCompound(); }
                    case '_' -> { e.consume(); logState("key UNDERSCORE"); handleUnderscore(); }
                }
            }
        });

        installOutsideClickListener();
    }

    /**
     * Returns {@code null} when the insertion is rejected (past {@link #MAX_LENGTH_CHARS}).
     */
    @Nullable
    private static String filterInsertion(int currentLength, int replacedLength, String text) {
        if (text.isEmpty()) {
            return text;
        }

        var candidate = text.length() > 1 ? firstWordOf(text) : text;

        if (currentLength - replacedLength + candidate.length() > MAX_LENGTH_CHARS) {
            trace("reject: inserting '{}' would exceed {} characters", candidate, MAX_LENGTH_CHARS);
            UIUtils.beep();
            return null;
        }

        return candidate;
    }

    private static String firstWordOf(String text) {
        var matcher = FIRST_WORD_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void insertAccentedLetter(KeyEvent event, String logLabel, String letter, String capitalLetter) {
        event.consume();
        dropNextTypedChar = true;
        logState(logLabel);
        replaceSelection(event.isShiftDown() ? capitalLetter : letter);
    }

    /**
     * Ctrl, Meta and AltGraph are excluded so the mapping never shadows a system shortcut or an
     * AltGr combination.
     */
    private static boolean isLongAKeyPress(KeyEvent event) {
        return isAccentShortcutKeyPress(event, KeyEvent.VK_A);
    }

    /**
     * Bypasses the dead-key composition Option-N starts on the US macOS layout.
     */
    private static boolean isNTildeKeyPress(KeyEvent event) {
        return isAccentShortcutKeyPress(event, KeyEvent.VK_N);
    }

    private static boolean isAccentShortcutKeyPress(KeyEvent event, int keyCode) {
        return event.getKeyCode() == keyCode
            && event.isAltDown()
            && !event.isControlDown()
            && !event.isMetaDown()
            && !event.isAltGraphDown();
    }

    private void installKeyBindings() {
        bindKey(KeyEvent.VK_TAB, 0, ACTION_KEY_TAB, () -> { logState("key TAB"); advance(); });
        bindKey(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK, ACTION_KEY_SHIFT_TAB,
            () -> { logState("key SHIFT-TAB"); retreat(); });

        bindKey(KeyEvent.VK_ENTER, ACTION_KEY_ENTER, () -> {
            logState("key ENTER");
            var commitSpec = neutralCommitSpec();

            if (!ensureLyricFits(commitSpec.kind(), commitSpec.extend())) {
                // Keep the editor open (it retains focus) so the user can shorten the lyric.
                return;
            }

            line.getSong().withModification(commitOpName(), () -> {
                commitInner(commitSpec.kind(), commitSpec.extend());
                applyDismissAdjustment();
            });
            dismiss(true);
        });

        bindKey(KeyEvent.VK_ESCAPE, ACTION_KEY_ESCAPE, () -> {
            logState("key ESCAPE");
            line.getSong().withModification(this::applyDismissAdjustment);
            dismiss(true);
        });
    }

    /**
     * Covers clicks on the score canvas, which is not focusable so {@code focusLost} never fires.
     * Registered in {@link #attachListeners} because the AWT listener strongly references this
     * editor.
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
     * The width formula is the renderer's.
     */
    public void recomputeBounds() {
        var layoutResult = lineComponent.getLayoutResult();

        if (layoutResult == null) {
            return;
        }

        var lyricRenderMetrics = score.getLyricRenderMetrics();
        var text = getText();
        var boxMetrics = lyricRenderMetrics.lyricBoxMetricsSs(text);
        var advanceSs = Math.max(boxMetrics.advanceSs(), EMPTY_BOX_MIN_WIDTH_SS);
        var anchor = layoutResult.getLyricAnchor(element, lyricRenderMetrics);

        var advanceLeftSs = anchor.centerXSs().value() - advanceSs / 2.0;
        var heightSs = lyricRenderMetrics.editorBoxHeightSs();

        // The editor is a real overlay component positioned in absolute view pixels, so
        // ss→px conversions here honor the current zoom via the view scale (not the
        // fixed document scale). Sizes round up (sizePx); positions round to nearest.
        var viewScale = score.getViewScale();
        var roundedAdvancePx = viewScale.toViewPx(new Ss(advanceSs)).sizePx();
        var trailingCaretRoomPx = text.isEmpty()
            ? MIN_TRAILING_CARET_ROOM_PX
            : 0;
        var contentWidthPx = roundedAdvancePx + trailingCaretRoomPx;
        // editorBoxHeightSs covers ascent+descent only; FieldView's selection height uses
        // getHeight() = ascent+descent+leading. Adding leading here makes fieldViewSlopPx
        // equal to SELECTION_MARGIN_PX*2, so the selection gets exactly SELECTION_MARGIN_PX
        // pixels of breathing room above and below.
        var contentHeightPx = viewScale.toViewPx(heightSs).sizePx()
            + fontMetrics.getLeading();

        var insets = getInsets();

        // Snap content_left exactly to the advance-origin pixel: JTextField paints there,
        // and any rounding drift would visibly shift the painted text within the box.
        var contentLeftPx = viewScale.toViewPx(new Ss(advanceLeftSs)).positionPx();

        var baselineYPxInt = viewScale.toViewPx(anchor.baselineYSs()).positionPx();
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
     * Same-text and empty-on-empty commits emit no mutations.
     */
    public void commit() {
        commit(CommitKind.WORD_FINAL, Lyric.Extend.NONE);
    }

    private void commit(CommitKind kind, Lyric.Extend extend) {
        if (!ensureLyricFits(kind, extend)) {
            return;
        }

        line.getSong().withModification(commitOpName(), () -> commitInner(kind, extend));
    }

    /**
     * Returns false, warning the user and leaving the model untouched, when the commit would turn
     * a fitting line into one that overflows the staff. An already-overflowing line is never
     * blocked, nor is a commit of empty non-carrier text.
     */
    private boolean ensureLyricFits(CommitKind kind, Lyric.Extend extend) {
        if (showingLyricFitAlert) {
            // The alert's focus-steal re-entered this check; refuse without stacking a second alert.
            return false;
        }

        var intent = CommitIntent.of(kind, extend);
        var text = commitText();

        if (text.isEmpty() && !intent.wantsCarrier()) {
            // No syllable to probe: a text-bearing lyric cannot have empty text.
            trace("fit check: the commit writes no syllable, allowing it");
            return true;
        }

        // The candidate syllabic only needs the correct continues-status: that is the sole part of
        // the committed syllabic that affects horizontal spacing (see LyricEditFitCalculator).
        var probeSyllabic = deriveProbeSyllabic(intent);

        var metrics = score.getLyricRenderMetrics();
        var marginSs = line.getSong().getLineWidthSs().value();

        var index = line.getElementIndex(element);
        var candidate = new Lyric(activeScript, text, extend, probeSyllabic, intent.wantsCompound());

        // Probe the candidate first: it accepts almost every real edit, and returning here spares the
        // already-overflowing check below its own full rebuild-and-solve of the line.
        if (LyricEditFitCalculator.lyricEditFits(line, index, candidate, metrics, marginSs)) {
            return true;
        }

        if (!LyricEditFitCalculator.lineFits(line, metrics, marginSs)) {
            trace("fit check: line was already overflowing, allowing the edit anyway");
            return true;
        }

        trace("fit check: '{}' at {} would overflow the line, refusing", candidate.text(), index);
        showingLyricFitAlert = true;

        try {
            OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_LINE_TOO_FULL, Strings.ERROR_LINE_FULL_LYRIC);
        } finally {
            showingLyricFitAlert = false;
        }

        return false;
    }

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
     * Evaluate before opening the modification bracket: the op-name is captured at the depth
     * 0->1 transition only.
     */
    private String commitOpName() {
        var existingLyric = element.getLyricForScript(activeScript);
        var beforeText = existingLyric != null ? existingLyric.text() : "";
        return OpNames.lyricLabel(beforeText, commitText());
    }

    /**
     * Empty while a placeholder is untouched. Model-facing code reads this, never the field text.
     */
    private String commitText() {
        return isPlaceholderIntact() ? "" : getText();
    }

    private boolean isPlaceholderIntact() {
        //noinspection InstanceVariableUsedBeforeInitialized
        return placeholder != null && getText().equals(placeholder.text());
    }

    private void logState(String action) {
        if (!LogUtils.isTracingLyrics(LOG)) {
            return;
        }

        LOG.debug("{} | index={} text='{}' commit='{}' placeholder={} extender={} sel={}..{} caret={} | {}",
            action, line.getElementIndex(element), getText(), commitText(), placeholder,
            openedAsExtender, getSelectionStart(), getSelectionEnd(), getCaretPosition(),
            chainEditor.lyricRowDescription());
    }

    private static void trace(String format, @Nullable Object... args) {
        if (LogUtils.isTracingLyrics(LOG)) {
            LOG.debug(format, args);
        }
    }

    private void reject(String reason) {
        if (LogUtils.isTracingLyrics(LOG)) {
            logState("reject (" + reason + ')');
        }

        UIUtils.beep();
    }

    private void reject(Supplier<String> reason) {
        if (LogUtils.isTracingLyrics(LOG)) {
            logState("reject (" + reason.get() + ')');
        }

        UIUtils.beep();
    }

    /**
     * {@code null} when the element plays no role for a neighbor.
     */
    private @Nullable Placeholder derivePlaceholder() {
        if (openedAsExtender) {
            return Placeholder.MELISMA;
        }

        return chainEditor.isInsideHyphenChain(line.getElementIndex(element))
            ? Placeholder.HYPHEN
            : null;
    }

    /**
     * Assumes an open modification bracket. Same-state and empty-on-empty commits emit no
     * mutations.
     */
    private void commitInner(CommitKind kind, Lyric.Extend extend) {
        var text = commitText();
        var existingLyric = element.getLyricForScript(activeScript);

        if (LogUtils.isTracingLyrics(LOG)) {
            logState("commit " + kind + '/' + extend);
        }

        // The user cleared the placeholder rather than replacing it, so this element gives up
        // the role it opened with. Any path that rewrites the lyric itself drops the
        // placeholder first, so reaching here means the role is still this editor's to end.
        var openingRole = placeholder;

        if (openingRole != null && getText().isEmpty()) {
            trace("commit: {} placeholder was cleared, breaking its chain", openingRole);

            // The element is giving the role up, so it no longer describes this element —
            // keep the field in step with the model the way extendChainBackward does.
            placeholder = null;
            var currentIndex = line.getElementIndex(element);

            switch (openingRole) {
                case HYPHEN -> chainEditor.breakHyphenChain(currentIndex);
                case MELISMA -> chainEditor.breakMelismaChain(currentIndex);
            }

            return;
        }

        var existingText = existingLyric != null ? existingLyric.text() : "";
        var intent = CommitIntent.of(kind, extend);
        var existingSyllabic = existingLyric != null ? existingLyric.syllabic() : null;
        var existingContinues = Lyric.syllabicContinues(existingSyllabic);
        var existingCompound = existingLyric != null && existingLyric.compound();

        if (text.equals(existingText)
                && existingLyric != null
                && existingLyric.extend() == extend
                && existingContinues == intent.wantsContinues()
                && existingCompound == intent.wantsCompound()) {
            trace("commit: no-op, the stored lyric already matches");
            return;
        }

        if (text.isEmpty() && existingLyric == null) {
            trace("commit: no-op, nothing typed on an element with no lyric");
            return;
        }

        var index = line.getElementIndex(element);

        // This commit stops the element sustaining a melisma, so the carriers it fed are now
        // orphaned. Clear them before the write below, so the boundary fix that follows sees
        // the first real syllable ahead rather than a carrier that is about to disappear.
        if (LyricChainEditor.sustainsMelisma(
                existingLyric != null ? existingLyric.extend() : Lyric.Extend.NONE)
                && !LyricChainEditor.sustainsMelisma(extend)) {
            trace("commit: melisma dropped ({} -> {}), clearing the carriers it fed",
                existingLyric != null ? existingLyric.extend() : null, extend);
            chainEditor.clearForwardCarriers(index);
        }

        line.writeLyricForScript(index, activeScript, text, extend,
            kind == CommitKind.WORD_FINAL, kind == CommitKind.WORD_CONTINUING_COMPOUND);

        // A syllable on a paired grace note implies a melisma across its host, so the
        // extender follows the text: established by this commit, torn down when the text
        // is deleted. Guarded on the pairing because the sync tears down the two-element
        // chain at index/index + 1, which for an unpaired element is an ordinary melisma.
        if (line.isPairedGraceNote(index)) {
            line.syncGraceHostMelisma(index);
        }

        logState("commit done");
    }

    /**
     * The commit and the dismiss adjustment produce one
     * {@link songscribe.message.notification.SongDidChangeNotification}.
     */
    public void advance(CommitKind kind, Lyric.Extend extend) {
        advanceWithIndex(kind, extend, findNextEligibleIndex());
    }

    public void advance() {
        var commitSpec = neutralCommitSpec();
        advance(commitSpec.kind(), commitSpec.extend());
    }

    public void retreat() {
        var commitSpec = neutralCommitSpec();
        advanceWithIndex(commitSpec.kind(), commitSpec.extend(), findPreviousEligibleIndex());
    }

    private void advanceWithIndex(CommitKind kind, Lyric.Extend extend, @Nullable Integer nextIndex) {
        if (!ensureLyricFits(kind, extend)) {
            trace("advance: refused, the lyric does not fit");
            return;
        }

        line.getSong().withModification(commitOpName(), () -> {
            commitInner(kind, extend);
            applyDismissAdjustment();
        });
        openIndexOrDismiss(nextIndex);
    }

    /**
     * Not called inside an open modification bracket; it opens its own.
     */
    private void breakChainCommitAndAdvance(CommitKind kind, @Nullable Integer nextIndex) {
        if (!ensureLyricFits(kind, Lyric.Extend.NONE)) {
            trace("breakChainCommitAndAdvance: refused, the lyric does not fit");
            return;
        }

        var currentIndex = line.getElementIndex(element);
        line.getSong().withModification(commitOpName(), () -> {
            chainEditor.breakChainAtCurrentElement(currentIndex);
            suppressDismissAdjustment = true;
            commitInner(kind, Lyric.Extend.NONE);
            applyDismissAdjustment();
        });
        openIndexOrDismiss(nextIndex);
    }

    /**
     * A neutral commit (Tab, Shift-Tab, Enter, focus loss, a click elsewhere) leaves the stored
     * syllabic, compound and extend state alone, edited or not: editing a syllable and pressing
     * Tab must not break its hyphen to the next syllable.
     */
    private CommitSpec neutralCommitSpec() {
        var lyric = element.getLyricForScript(activeScript);

        if (lyric == null) {
            trace("neutralCommitSpec: no stored shape to keep, committing as a plain word-final syllable");
            return plainWordFinal();
        }

        var text = commitText();
        var extend = lyric.extend();

        if (lyric.isCarrier()) {
            // A carrier holds no text of its own, so it survives only while the element is
            // still empty; text typed over it makes the element a syllable in its own right.
            if (text.isEmpty()) {
                trace("neutralCommitSpec: keeping the {} carrier", extend);
                return new CommitSpec(CommitKind.WORD_FINAL, extend);
            }

            trace("neutralCommitSpec: text typed over a {} carrier, committing a plain syllable", extend);
            return plainWordFinal();
        }

        if (text.isEmpty()) {
            trace("neutralCommitSpec: the syllable's text was emptied, no shape left to keep");
            return plainWordFinal();
        }

        var kind = switch (lyric.syllabic()) {
            case BEGIN, MIDDLE -> lyric.compound()
                ? CommitKind.WORD_CONTINUING_COMPOUND
                : CommitKind.WORD_CONTINUING_HYPHEN;
            case SINGLE, END -> CommitKind.WORD_FINAL;
            case null -> throw RuntimeError.exit(
                "Text-bearing lyric at editor element is missing syllabic");
        };

        trace("neutralCommitSpec: keeping {}/{}", kind, extend);
        return new CommitSpec(kind, extend);
    }

    private static CommitSpec plainWordFinal() {
        return new CommitSpec(CommitKind.WORD_FINAL, Lyric.Extend.NONE);
    }

    private void openIndexOrDismiss(@Nullable Integer nextIndex) {
        if (LogUtils.isTracingLyrics(LOG)) {
            logState(nextIndex != null ? "moving to " + nextIndex : "no target element, dismissing");
        }

        if (nextIndex != null) {
            dismiss(false);
            openOn(score, line, line.getElement(nextIndex));
        } else {
            dismiss(true);
        }
    }

    /**
     * Entry for a user gesture, whose own selection must be cleared first. Tab navigation within
     * an open editor calls {@link #openOn} directly.
     */
    public static void deselectAndOpenOn(ScoreView score, Line line, int index) {
        score.deselect();
        openOn(score, line, line.getElement(index));
    }

    private @Nullable Integer findNextEligibleIndex() {
        return LyricTargetResolver.findNextEligibleIndex(line, line.getElementIndex(element));
    }

    private @Nullable Integer findPreviousEligibleIndex() {
        return LyricTargetResolver.findPreviousEligibleIndex(line, line.getElementIndex(element));
    }

    private void handleHyphen() {
        if (openedAsExtender) {
            if (commitText().isEmpty()) {
                reject("hyphen on a carrier with no text to commit");
                return;
            }

            var nextIndex = findNextEligibleIndex();

            if (nextIndex == null) {
                reject("hyphen over a carrier with no element to hyphenate to");
                return;
            }

            trace("hyphen: text over a carrier, breaking its chain, advancing to {}", nextIndex);
            breakChainCommitAndAdvance(CommitKind.WORD_CONTINUING_HYPHEN, nextIndex);
            return;
        }

        if (!commitText().isEmpty()) {
            var nextIndex = findNextEligibleIndex();

            if (nextIndex == null) {
                reject("hyphen with no element to hyphenate to");
                return;
            }

            trace("hyphen: committing syllable as word-continuing, advancing to {}", nextIndex);
            advanceWithIndex(CommitKind.WORD_CONTINUING_HYPHEN, Lyric.Extend.NONE, nextIndex);
            return;
        }

        // Lone '-' on empty editor, not a carrier.

        if (element.getLyricForScript(activeScript) != null) {
            reject("lone hyphen on an element that already has a lyric");
            return;
        }

        var currentIndex = line.getElementIndex(element);
        var backIndex = line.previousLyricBearingIndex(currentIndex, activeScript);
        var backLyric = backIndex != null
            ? line.getElement(backIndex).getLyricForScript(activeScript)
            : null;

        if (backLyric == null) {
            reject("lone hyphen with no preceding lyric to continue");
            return;
        }

        var backSyllabic = backLyric.syllabic();

        if (!Lyric.syllabicContinues(backSyllabic)) {
            reject(() -> "lone hyphen after a word-final syllable (" + backSyllabic + ')');
            return;
        }

        var nextIndex = findNextEligibleIndex();

        if (nextIndex == null) {
            reject("lone hyphen with no next eligible element");
            return;
        }

        trace("hyphen: skipping the gap at {}, advancing to {}", currentIndex, nextIndex);
        openIndexOrDismiss(nextIndex);
    }

    private boolean isCaretAtEnd() {
        return getCaretPosition() == getText().length();
    }

    private void handleCompound() {
        if (commitText().isEmpty() || !isCaretAtEnd()) {
            reject("compound needs non-empty text with the caret at the end");
            return;
        }

        var nextIndex = findNextEligibleIndex();

        if (nextIndex == null) {
            reject("compound with no element to join to");
            return;
        }

        if (openedAsExtender) {
            trace("compound: text over a carrier, breaking its chain, advancing to {}", nextIndex);
            breakChainCommitAndAdvance(CommitKind.WORD_CONTINUING_COMPOUND, nextIndex);
            return;
        }

        trace("compound: committing syllable as compound, advancing to {}", nextIndex);
        advanceWithIndex(CommitKind.WORD_CONTINUING_COMPOUND, Lyric.Extend.NONE, nextIndex);
    }

    private void handleUnderscore() {
        var text = commitText();

        if (text.isEmpty()) {
            trace("underscore: empty editor, extending the chain backward");
            extendChainBackward();
            return;
        }

        if (isEntireTextSelected()) {
            trace("underscore: whole syllable selected, replacing it with a carrier");
            replaceLyricWithMelisma();
            return;
        }

        if (getSelectionStart() != getSelectionEnd() || !isCaretAtEnd()) {
            reject("underscore needs either the whole text selected or the caret at the end");
            return;
        }

        var nextIndex = findNextEligibleIndex();

        if (nextIndex == null) {
            reject("underscore with no element to carry the melisma");
            return;
        }

        var nextLyric = line.getElement(nextIndex).getLyricForScript(activeScript);

        if (nextLyric != null && !nextLyric.text().isEmpty()) {
            reject(() -> "underscore blocked: element " + nextIndex + " already has a syllable");
            return;
        }

        trace("underscore: starting a melisma, carrier at {}", nextIndex);
        startMelismaOnNextElement(nextIndex);
    }

    private boolean isEntireTextSelected() {
        return getSelectionStart() == 0 && getSelectionEnd() == getText().length();
    }

    private void replaceLyricWithMelisma() {
        var currentIndex = line.getElementIndex(element);
        var backIndex = line.previousLyricBearingIndex(currentIndex, activeScript);

        if (backIndex == null) {
            reject("no preceding lyric for this element to carry a melisma from");
            return;
        }

        extendChainBackward(currentIndex, backIndex);
    }

    private void startMelismaOnNextElement(int nextIndex) {
        if (!ensureLyricFits(CommitKind.WORD_FINAL, Lyric.Extend.START)) {
            return;
        }

        var currentIndex = line.getElementIndex(element);

        line.getSong().withModification(commitOpName(), () -> {
            if (openedAsExtender) {
                // Text typed over a carrier: end the chain that ran through this element
                // before starting a new one from it.
                chainEditor.breakChainAtCurrentElement(currentIndex);
                suppressDismissAdjustment = true;
            }

            commitInner(CommitKind.WORD_FINAL, Lyric.Extend.START);
            applyDismissAdjustment();
            chainEditor.markMelismaCarrierRun(currentIndex, nextIndex);
        });

        // This element now holds a melisma-starting syllable, whatever role it opened with, so
        // the commit that follows must not end that role on its way out.
        placeholder = null;

        if (LogUtils.isTracingLyrics(LOG)) {
            logState("startMelismaOnNextElement done, carrier at " + nextIndex);
        }

        var followingIndex = LyricTargetResolver.findNextEligibleIndex(line, nextIndex);
        openIndexOrDismiss(followingIndex);
    }

    private void extendChainBackward() {
        var currentIndex = line.getElementIndex(element);
        var backIndex = line.previousLyricBearingIndex(currentIndex, activeScript);

        if (backIndex == null) {
            reject("no preceding lyric to extend a chain from");
            return;
        }

        extendChainBackward(currentIndex, backIndex);
    }

    /**
     * @param backIndex never negative
     */
    private void extendChainBackward(int currentIndex, int backIndex) {
        chainEditor.buildBackwardChain(currentIndex, backIndex);

        // This element is now the chain's carrier, whatever role it opened with, so the
        // commit that follows must not end that role on its way out. Clearing the field is
        // what stops that commit writing anything back over the carrier — both a syllable the
        // melisma just replaced and a placeholder, which is no longer read as empty once the
        // role it stood for is gone.
        placeholder = null;
        setText("");
        suppressDismissAdjustment = true;
        logState("extendChainBackward done");
        advance();
    }

    // Re-entrant guard: clearing focused before the bracket opens prevents a second
    // focusLost (fired by layout reflow inside the mutation) from re-entering.
    private void commitAndDismiss() {
        if (!focused || getParent() == null) {
            return;
        }

        focused = false;
        logState("commitAndDismiss");
        var commitSpec = neutralCommitSpec();

        if (!ensureLyricFits(commitSpec.kind(), commitSpec.extend())) {
            // Keep the editor open and focused; re-arm the outside-click listener (the caller
            // removed it) so a later click can dismiss the editor once the lyric fits.
            focused = true;
            installOutsideClickListener();
            requestFocusInWindow();
            return;
        }

        line.getSong().withModification(commitOpName(), () -> {
            commitInner(commitSpec.kind(), commitSpec.extend());
            applyDismissAdjustment();
        });
        dismiss(true);
    }

    /**
     * Called inside an open modification bracket.
     */
    private void applyDismissAdjustment() {
        if (suppressDismissAdjustment) {
            trace("dismissAdjustment: suppressed, a chain was just built by hand");
            suppressDismissAdjustment = false;
            return;
        }

        // Editor was opened on a carrier and the user committed nothing over it — either the
        // extender placeholder is untouched or the chain was already ended by the commit, so
        // there is nothing left to repair.
        if (openedAsExtender && commitText().isEmpty()) {
            trace("dismissAdjustment: carrier left as it was, nothing to repair");
            return;
        }

        var currentIndex = line.getElementIndex(element);

        if (openedAsExtender) {
            // Editor was opened on a carrier and text was committed in its place.
            // Terminate the predecessor extender chain and clear stale forward
            // carriers up to the next STOP or text-bearing element.
            trace("dismissAdjustment: text replaced the carrier at {}, breaking its chain",
                currentIndex);
            chainEditor.breakChainAtCurrentElement(currentIndex);
            return;
        }

        // Common case: repair any dangling chain marker left on a predecessor. The repair reads
        // this element as having given up its syllable, so it must not run when the commit just
        // wrote one here — it would undo that syllable's own chain markers.
        var lyric = element.getLyricForScript(activeScript);

        if (lyric != null && lyric.syllabic() != null) {
            trace("dismissAdjustment: {} kept a syllable of its own, nothing to repair",
                currentIndex);
            return;
        }

        trace("dismissAdjustment: repairing neighbors of {}", currentIndex);
        line.adjustNeighborsForLyricDeletion(currentIndex, activeScript);
    }

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
        repaintHyphenChainPreview();

        // We want to notify actions that editing has ended so they can update their enabled state,
        // but only when not advancing to another lyric editor.
        if (isDoneEditing) {
            MessageCenter.post(new TextEditingDidChangeNotification(false));
        }
    }

    /**
     * Repaints the whole line: an unclosed hyphen chain starts left of the editor, outside the
     * bounds the open and dismiss paths repaint, and a lone hyphen writes no mutation.
     */
    private void repaintHyphenChainPreview() {
        lineComponent.repaint();
    }
}
