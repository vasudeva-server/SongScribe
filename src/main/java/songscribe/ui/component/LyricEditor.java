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

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.LyricRun;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.error.RuntimeError;
import songscribe.layout.InsetsSs;
import songscribe.layout.LyricEditFitCalculator;
import songscribe.message.MessageCenter;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.score.LineComponent;
import songscribe.undo.OpNames;
import songscribe.util.LogUtils;
import songscribe.util.UIUtils;

/**
 * In-place lyric editor overlay parented to {@link ScoreView}. Edits the active-verse lyric
 * of a single {@link StaffElement} with width and baseline matching the rendered lyric box.
 *
 * <p>An {@code EditLyricAction}, Enter, or a double-click constructs the editor, sizes it to the
 * lyric box, prefills it (with the existing lyric, or a placeholder — see below), attaches its
 * listeners, and adds it to the score with focus. While it is open, character edits recompute its
 * width, text longer than {@value #MAX_LENGTH_CHARS} characters is rejected, and newlines are
 * stripped. Tab and Space commit and advance; Enter commits and dismisses; Escape dismisses without
 * committing; focus-lost commits and dismisses. Escape, focus-lost and Enter all run
 * {@code applyDismissAdjustment} to repair a dangling extender or syllable chain. The boundary keys
 * {@code -}, {@code =}/{@code +} and {@code _} each end the syllable and decide what kind of chain
 * it joins, beeping and staying open where the caret position or emptiness makes that impossible.
 * {@code advance()} scans forward for the next element that is not a rest (or is a rest that
 * already has a lyric), reopening the editor there or dismissing if there is none.
 *
 * <p>See {@code docs/lyric-editor.md} for the full lifecycle, including the per-key boundary table.
 *
 * <p>Invariant: while the editor is active, no external code path may mutate the song or fire any
 * toolbar keystroke. This is enforced by {@code DISABLE_WHEN_EDITING_TEXT} on every toolbar
 * {@code UIAction}; {@code LyricEditorActionAuditTest} locks the whitelist.
 *
 * <p>An element with no syllable of its own opens prefilled with a selected placeholder naming
 * the role it plays for a neighbor: {@code -} when a word's hyphen spans it (given "a" "-" "mi",
 * the middle note), {@code _} when it carries a melisma's extender. A placeholder is not text:
 * while it is intact every state above reads the editor as empty, so committing it leaves the
 * chain as it was. Clearing it — with Space, or by deleting it and committing — is what breaks
 * the chain: a hyphen chain ends the word at the predecessor, a melisma gives up this carrier
 * and closes the chain behind it.
 */
public final class LyricEditor extends MyJTextField {

    private static final Logger LOG = LoggerFactory.getLogger(LyricEditor.class);

    static final int MAX_LENGTH_CHARS = 32;

    /**
     * Matches the leading word a bulk insertion keeps; see {@link #firstWordOf}.
     * {@code \p{L}} covers every letter category (upper, lower, title, modifier, and
     * caseless letters, e.g. CJK ideographs). {@code \p{Mn}} and {@code \p{Mc}} are also
     * needed, not just letters: in Devanagari and Bengali a vowel sign attaches to the
     * preceding consonant as a combining mark rather than a standalone letter, so without
     * them a word would be cut off after its first consonant.
     */
    private static final Pattern FIRST_WORD_PATTERN =
        Pattern.compile("^\\s*([\\p{L}\\p{Mn}\\p{Mc}]+)");

    // The text of each Placeholder. Only this editor uses them, so they live here.
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

    /**
     * A stand-in the editor opens with on an element that has no syllable of its own but plays
     * a role in a neighboring one. It names that role and gives the user something to delete in
     * order to end it. A placeholder is never text: while it is intact the editor commits as if
     * it were empty (see {@link #commitText()}).
     */
    private enum Placeholder {
        /** The hyphen of a word that continues across this element. */
        HYPHEN(LyricEditor.HYPHEN_TEXT),
        /** The extender of a melisma this element carries. */
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
    private final @Nullable LineComponent lineComponent;

    /**
     * The verse this session edits: the song's active verse, captured when the editor opened.
     * Held rather than re-read because an editor session must write every syllable, hyphen and
     * melisma it touches into the one verse it started in — the active verse changing underneath
     * an open editor would split a single edit across two languages.
     */
    private final int activeVerse;

    /** The chain rewrites this session performs on {@link #line}'s {@link #activeVerse} lyrics. */
    private final LyricChainEditor chainEditor;

    private boolean focused;

    @Nullable private AWTEventListener outsideClickListener;

    /**
     * {@code true} when the editor was opened on an element that already carried a
     * {@code CONTINUE} or {@code STOP} extender at construction time (i.e. opened on a
     * carrier lyric).
     */
    private final boolean openedAsExtender;

    /**
     * The role the editor was opened on and prefilled with, or {@code null} when it was opened
     * on a syllable (or on an element with no role to show). Cleared by the paths that rewrite
     * this element's lyric themselves, since the opening role no longer describes it.
     */
    private @Nullable Placeholder placeholder;

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
     * Set when the Alt-A key press was handled here, so the key typed event it generates
     * must not also reach the document. macOS treats Option as a printable modifier: the
     * consumed key press is still followed by a key typed carrying the layout's Option-A
     * character (å on a US keyboard). Scoping the drop to that one event leaves every other
     * Option-key character — the accented letters lyrics need — free to be typed.
     */
    private boolean dropNextTypedChar;

    /**
     * Constructs a {@link LyricEditor} on {@code element}, attaches it to {@code score},
     * and gives it focus. Used by {@link #advance()} and {@link #deselectAndOpenOn} so the
     * open sequence is centralized; every user gesture arrives through the latter.
     */
    public static void openOn(ScoreView score, Line line, StaffElement element) {
        var editor = new LyricEditor(score, line, element);
        score.addOverlay(editor);
        // Reclaims the topmost index, pushing any line overlays below the caret.
        score.setComponentZOrder(editor, ScoreView.LYRIC_EDITOR_Z_ORDER);
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
        editor.repaintHyphenChainPreview();
    }

    public LyricEditor(ScoreView score, Line line, StaffElement element) {
        this.score = score;
        this.line = line;
        this.element = element;
        activeVerse = line.getSong().getActiveVerse();
        chainEditor = new LyricChainEditor(line, activeVerse);

        var openingLyric = element.getLyricForVerse(activeVerse);
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
        MessageCenter.subscribe(this);
    }

    /**
     * Re-derives this editor's zoomed font and bounds when it is the currently active editor.
     * <p>
     * This is an absolutely-positioned {@link JComponent}, not a layout-managed
     * child, so a zoom change does not move or resize it on its own. Guarded on being the
     * active editor because {@link MessageCenter} holds subscribers weakly — a dismissed
     * editor stays reachable (and therefore subscribed) until GC'd, and must not react to a
     * zoom change after {@link #dismiss}. Priority is intentionally left at the default: see
     * the priority requirement documented on {@link ZoomDidChangeNotification}.
     */
    @Handler
    void zoomDidChange(ZoomDidChangeNotification message) {
        if (score.getActiveLyricEditor() != this) {
            return;
        }

        refreshFont();
        recomputeBounds();
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

    /**
     * The three legal shapes a committed syllable can have. Only a shape-declaring key picks
     * one directly; a neutral commit takes whatever shape the lyric already has, via
     * {@link #neutralCommitSpec()}.
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
     * The three booleans a {@code (kind, extend)} pair implies, for the two places that must
     * reason about a commit <em>before</em> it happens: {@link #ensureLyricFits} (which probes
     * whether the line still lays out) and {@link #commitInner}'s no-op check (which compares the
     * pending commit against the stored lyric). Derived in one place so those two cannot drift
     * apart if the {@link CommitKind}/{@link Lyric.Extend} mapping ever changes.
     *
     * <p>The write itself does not use this — it passes {@code kind} and {@code extend} straight
     * to {@link LyricRun#writeLyricForVerse(int, int, String, Lyric.Extend, boolean, boolean)}, which
     * applies the same mapping to decide the stored syllabic and compound flag.
     *
     * <p><b>Assumed by {@code wantsContinues}:</b> a non-{@link CommitKind#WORD_FINAL} commit
     * always carries non-blank text and a non-carrier extend. The write applies the stricter
     * test — it only continues into the next syllable when there is text to continue from — so
     * the two agree only while that holds. It holds because the hyphen and compound keys are the
     * only sources of a continuing kind, and both reject an empty editor before committing. A
     * new continuing commit path must uphold it, or this no-op check would read a real change as
     * no change and skip the write.
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

    /** Returns the verse this editor session is editing. */
    public int getActiveVerse() {
        return activeVerse;
    }

    @Override
    protected TextFocusDelegate createFocusDelegate() {
        return new LyricFocusDelegate();
    }

    /**
     * Adds nothing but a trace, and is kept deliberately. Together with the {@code raw} traces
     * in the key listener and the document filter, it shows the whole path a dead key takes
     * through the editor — the view that made the Option-N composition bug findable. Alt-N has
     * only been exercised on macOS so far; these stay until it has been tried on Windows and
     * Linux, where a different keyboard layout may route the keystroke differently.
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
     * Returns the text to actually insert, or {@code null} when the insertion must be
     * rejected. Newlines are dropped silently. A bulk insertion — paste, an IME commit,
     * a drag-and-drop — is trimmed to its first word, since a lyric editor holds a single
     * syllable; a single typed character is left as is. Insertions that would push the
     * document past {@link #MAX_LENGTH_CHARS} are rejected with a beep so the user notices
     * the limit; the length check runs against the trimmed text, not the raw pasted text.
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

    /**
     * Returns the leading run of word characters in {@code text}, after skipping any
     * leading whitespace, or an empty string when it has none. Word characters are letters
     * of any script plus the combining marks some scripts attach to them; the first
     * character outside that set, and everything after it, is discarded.
     */
    private static String firstWordOf(String text) {
        var matcher = FIRST_WORD_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Consumes {@code event}, arranges for the key typed event it generates to be dropped,
     * and inserts {@code letter} ({@code capitalLetter} when Shift is down) at the caret.
     * Goes through the document filter, so the length limit still applies. {@code logLabel}
     * names the shortcut in the trace; it arrives whole so no message is assembled while
     * tracing is off.
     */
    private void insertAccentedLetter(KeyEvent event, String logLabel, String letter, String capitalLetter) {
        event.consume();
        dropNextTypedChar = true;
        logState(logLabel);
        replaceSelection(event.isShiftDown() ? capitalLetter : letter);
    }

    /**
     * Returns {@code true} when {@code event} is the Alt-A the editor turns into a long a.
     * Ctrl, Meta, and AltGraph are excluded so the mapping never shadows a system shortcut
     * or an AltGr combination on layouts that build characters from one.
     */
    private static boolean isLongAKeyPress(KeyEvent event) {
        return isAccentShortcutKeyPress(event, KeyEvent.VK_A);
    }

    /**
     * Returns {@code true} when {@code event} is the Alt-N the editor turns into an n with
     * tilde, bypassing the dead-key composition Option-N normally starts on the US macOS
     * layout. Ctrl, Meta, and AltGraph are excluded for the same reason as {@link
     * #isLongAKeyPress}.
     */
    private static boolean isNTildeKeyPress(KeyEvent event) {
        return isAccentShortcutKeyPress(event, KeyEvent.VK_N);
    }

    /**
     * Shared guard for the editor's Option-letter shortcuts: {@code keyCode} held with Alt
     * alone. Ctrl, Meta, and AltGraph are excluded so no shortcut shadows a system command
     * or an AltGr combination a keyboard layout builds from one of these keys.
     */
    private static boolean isAccentShortcutKeyPress(KeyEvent event, int keyCode) {
        return event.getKeyCode() == keyCode
            && event.isAltDown()
            && !event.isControlDown()
            && !event.isMetaDown()
            && !event.isAltGraphDown();
    }

    /**
     * Binds Tab, Enter, and Escape on the {@code WHEN_FOCUSED} input map. Tab and Enter
     * would otherwise insert their literal characters; Escape has no default binding.
     */
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

            line.withModification(commitOpName(), () -> {
                commitInner(commitSpec.kind(), commitSpec.extend());
                applyDismissAdjustment();
            });
            dismiss(true);
        });

        bindKey(KeyEvent.VK_ESCAPE, ACTION_KEY_ESCAPE, () -> {
            logState("key ESCAPE");
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
     * Writes the editor's current text into the active element's lyric for {@link #activeVerse}
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
     * anyway leaves the line laid out on its collision floors with its tail clipped at the end of
     * the staff (refs #696) — quite possibly cutting off the syllable just typed. When that would
     * happen this warns the user, leaves the model untouched, and returns false so the caller aborts
     * the commit and keeps the editor open for the user to shorten the lyric.
     *
     * <p>An already-overflowing line is never blocked, so the user can still shorten a too-long
     * syllable to recover; only an edit that turns a fitting line into one that does not fit is
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
        var candidate = new Lyric(activeVerse, commitText(), extend, probeSyllabic, intent.wantsCompound());

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
        var existingLyric = element.getLyricForVerse(activeVerse);
        var beforeText = existingLyric != null ? existingLyric.text() : "";
        return OpNames.lyricLabel(beforeText, commitText());
    }

    /**
     * The text this editor would write into the model: the empty string while a placeholder is
     * untouched (it stands for a neighboring syllable's hyphen or extender, not for a syllable
     * of this element's own), otherwise the field text. Model-facing code reads this; the field
     * text itself is only for what the user sees and edits.
     */
    private String commitText() {
        return isPlaceholderIntact() ? "" : getText();
    }

    private boolean isPlaceholderIntact() {
        //noinspection InstanceVariableUsedBeforeInitialized
        return placeholder != null && getText().equals(placeholder.text());
    }

    /**
     * Traces one step of a lyric edit: what the editor is about to do, the field state it acts
     * from, and the whole verse's lyric row, so consecutive entries can be diffed to see what
     * a keystroke actually changed. Guarded because rendering the row walks the entire line.
     */
    private void logState(String action) {
        if (!LogUtils.isTracingLyrics(LOG)) {
            return;
        }

        LOG.debug("{} | index={} text='{}' commit='{}' placeholder={} extender={} sel={}..{} caret={} | {}",
            action, line.getElementIndex(element), getText(), commitText(), placeholder,
            openedAsExtender, getSelectionStart(), getSelectionEnd(), getCaretPosition(),
            chainEditor.lyricRowDescription());
    }

    /**
     * Records one decision the lyric machinery made. Carries no editor state of its own, so pair
     * it with {@link #logState} where the state that drove the decision also matters.
     */
    private static void trace(String format, @Nullable Object... args) {
        if (LogUtils.isTracingLyrics(LOG)) {
            LOG.debug(format, args);
        }
    }

    /** Beeps the keystroke away, recording why it was refused. */
    private void reject(String reason) {
        if (LogUtils.isTracingLyrics(LOG)) {
            logState("reject (" + reason + ')');
        }

        UIUtils.beep();
    }

    /**
     * Beeps the keystroke away, recording why. Takes the reason as a supplier so a reason that
     * has to be assembled from surrounding state is never built while tracing is off; use
     * {@link #reject(String)} for a reason that is already a plain literal.
     */
    private void reject(Supplier<String> reason) {
        if (LogUtils.isTracingLyrics(LOG)) {
            logState("reject (" + reason.get() + ')');
        }

        UIUtils.beep();
    }

    /**
     * The role to open this element with, or {@code null} when it plays none. A carrier's
     * extender takes precedence: an element that carries one cannot also be a hyphen gap,
     * since it holds a lyric of its own.
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
     * Writes the editor's current text into the active element's lyric for {@link #activeVerse}
     * via {@link LyricRun#writeLyricForVerse(int, int, String, Lyric.Extend, boolean, boolean)}, which
     * derives the syllabic from chain context and propagates to the next lyric-bearing element
     * where needed. Every commit therefore produces exactly one {@code ElementModification} for
     * this element, plus at most one more for the following syllable when it has to be
     * re-lettered.
     *
     * <p>Same-text/equivalent-state commits and empty-on-empty commits emit zero
     * mutations — assumes an open modification bracket so zero-mutation early-returns
     * produce an empty bracket with no notification.
     *
     * @param kind   the syllable shape being committed
     * @param extend melisma extender state for the lyric
     */
    private void commitInner(CommitKind kind, Lyric.Extend extend) {
        var text = commitText();
        var existingLyric = element.getLyricForVerse(activeVerse);

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

        line.writeLyricForVerse(index, activeVerse, text, extend,
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
        var commitSpec = neutralCommitSpec();
        advance(commitSpec.kind(), commitSpec.extend());
    }

    /**
     * Commits the editor's text as a word-final syllable (no compound, no melisma),
     * runs the dismiss-adjustment pass, then retreats to the previous eligible element.
     */
    public void retreat() {
        var commitSpec = neutralCommitSpec();
        advanceWithIndex(commitSpec.kind(), commitSpec.extend(), findPreviousEligibleIndex());
    }

    private void advanceWithIndex(CommitKind kind, Lyric.Extend extend, int nextIndex) {
        if (!ensureLyricFits(kind, extend)) {
            trace("advance: refused, the lyric does not fit");
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
            trace("breakChainCommitAndAdvance: refused, the lyric does not fit");
            return;
        }

        var currentIndex = line.getElementIndex(element);
        line.withModification(commitOpName(), () -> {
            chainEditor.breakChainAtCurrentElement(currentIndex);
            suppressDismissAdjustment = true;
            commitInner(kind, Lyric.Extend.NONE);
            applyDismissAdjustment();
        });
        openIndexOrDismiss(nextIndex);
    }

    /**
     * The shape a <em>neutral</em> commit should write.
     *
     * <p>Every way of leaving the editor falls into one of two groups:
     *
     * <ul>
     *   <li><b>Shape-declaring</b> — the hyphen, compound ({@code =}/{@code +}), space and
     *       underscore keys. Each states how the syllable joins its neighbors, so each passes
     *       its own {@link CommitKind} and {@link Lyric.Extend} explicitly.
     *   <li><b>Neutral</b> — Tab, Shift-Tab, Enter, focus loss, a click elsewhere. These say
     *       only "keep what I typed and move on"; they carry no opinion about joining, so they
     *       come here and must leave the stored syllabic, compound and extend state alone —
     *       whether or not the text was edited. Editing a syllable and pressing Tab must not
     *       break its hyphen to the next syllable.
     * </ul>
     *
     * <p>The stored shape is therefore reused for any element that already has a lyric. Three
     * cases have no shape left to keep and fall back to a plain word-final, no-melisma commit:
     * an element with no lyric yet, one whose text the user emptied, and a melisma carrier the
     * user typed over.
     */
    private CommitSpec neutralCommitSpec() {
        var lyric = element.getLyricForVerse(activeVerse);

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

    /** The commit a neutral exit falls back to when there is no stored shape worth keeping. */
    private static CommitSpec plainWordFinal() {
        return new CommitSpec(CommitKind.WORD_FINAL, Lyric.Extend.NONE);
    }

    private void openIndexOrDismiss(int nextIndex) {
        if (LogUtils.isTracingLyrics(LOG)) {
            logState(nextIndex >= 0 ? "moving to " + nextIndex : "no target element, dismissing");
        }

        if (nextIndex >= 0) {
            dismiss(false);
            openOn(score, line, line.getElement(nextIndex));
        } else {
            dismiss(true);
        }
    }

    /**
     * Deselects and opens the editor on the element at {@code index} in {@code line}.
     * Every caller that opens the editor in response to a user gesture goes through here,
     * so the gesture's own selection is always cleared first. Tab/Shift-Tab navigation
     * within an already-open editor calls {@link #openOn} directly instead: there is no
     * selection to clear, the editor having taken focus when it opened.
     */
    public static void deselectAndOpenOn(ScoreView score, Line line, int index) {
        score.deselect();
        openOn(score, line, line.getElement(index));
    }

    private int findNextEligibleIndex() {
        return LyricTargetResolver.findNextEligibleIndex(line, line.getElementIndex(element));
    }

    private int findPreviousEligibleIndex() {
        return LyricTargetResolver.findPreviousEligibleIndex(line, line.getElementIndex(element));
    }

    private void handleHyphen() {
        if (openedAsExtender) {
            if (commitText().isEmpty()) {
                reject("hyphen on a carrier with no text to commit");
                return;
            }

            var nextIndex = findNextEligibleIndex();

            if (nextIndex < 0) {
                reject("hyphen over a carrier with no element to hyphenate to");
                return;
            }

            trace("hyphen: text over a carrier, breaking its chain, advancing to {}", nextIndex);
            breakChainCommitAndAdvance(CommitKind.WORD_CONTINUING_HYPHEN, nextIndex);
            return;
        }

        if (!commitText().isEmpty()) {
            var nextIndex = findNextEligibleIndex();

            if (nextIndex < 0) {
                reject("hyphen with no element to hyphenate to");
                return;
            }

            trace("hyphen: committing syllable as word-continuing, advancing to {}", nextIndex);
            advanceWithIndex(CommitKind.WORD_CONTINUING_HYPHEN, Lyric.Extend.NONE, nextIndex);
            return;
        }

        // Lone '-' on empty editor, not a carrier.

        if (element.getLyricForVerse(activeVerse) != null) {
            reject("lone hyphen on an element that already has a lyric");
            return;
        }

        var currentIndex = line.getElementIndex(element);
        var backIndex = line.previousLyricBearingIndex(currentIndex, activeVerse);
        var backLyric = backIndex >= 0
            ? line.getElement(backIndex).getLyricForVerse(activeVerse)
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

        if (nextIndex < 0) {
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

        if (nextIndex < 0) {
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

    /**
     * Implements the {@code _} keystroke:
     * <ul>
     *   <li>Empty editor, or the whole lyric selected — replace this element's lyric with a
     *       melisma carrier (see {@link #extendChainBackward()}), or beep when no predecessor
     *       can start the melisma.</li>
     *   <li>Caret at the end of unselected text and the next eligible element carries no
     *       syllable — commit the text as a melisma start and make that element the carrier
     *       (see {@link #startMelismaOnNextElement(int)}).</li>
     *   <li>Anything else (caret mid-text, partial selection, next element already has a
     *       syllable) — beep and reject.</li>
     * </ul>
     */
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

        if (nextIndex < 0) {
            reject("underscore with no element to carry the melisma");
            return;
        }

        var nextLyric = line.getElement(nextIndex).getLyricForVerse(activeVerse);

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

    /**
     * Implements {@code _} over a fully selected lyric: the melisma replaces the syllable, so
     * the text is dropped and the element becomes a carrier of the chain reaching back to its
     * predecessor. Beeps without mutating when there is no predecessor to start the melisma.
     */
    private void replaceLyricWithMelisma() {
        var currentIndex = line.getElementIndex(element);
        var backIndex = line.previousLyricBearingIndex(currentIndex, activeVerse);

        if (backIndex < 0) {
            reject("no preceding lyric for this element to carry a melisma from");
            return;
        }

        extendChainBackward(currentIndex, backIndex);
    }

    /**
     * Implements {@code _} typed at the end of unselected text: commits the text as a melisma
     * start, turns the next eligible element into the chain's {@code STOP} carrier, and moves
     * the editor past that carrier to the following eligible element.
     */
    private void startMelismaOnNextElement(int nextIndex) {
        if (!ensureLyricFits(CommitKind.WORD_FINAL, Lyric.Extend.START)) {
            return;
        }

        var currentIndex = line.getElementIndex(element);

        line.withModification(commitOpName(), () -> {
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

        openIndexOrDismiss(LyricTargetResolver.findNextEligibleIndex(line, nextIndex));
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
        var backIndex = line.previousLyricBearingIndex(currentIndex, activeVerse);

        if (backIndex < 0) {
            reject("no preceding lyric to extend a chain from");
            return;
        }

        extendChainBackward(currentIndex, backIndex);
    }

    /**
     * Builds the chain described by {@link #extendChainBackward()}, for callers that have
     * already located the current element and the lyric-bearing element before it.
     *
     * @param currentIndex the element the editor is open on, which becomes the chain's carrier
     * @param backIndex    the lyric-bearing element the chain runs back to, never negative
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

        line.withModification(commitOpName(), () -> {
            commitInner(commitSpec.kind(), commitSpec.extend());
            applyDismissAdjustment();
        });
        dismiss(true);
    }

    // Must be called inside an open modification bracket.
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
        var lyric = element.getLyricForVerse(activeVerse);

        if (lyric != null && lyric.syllabic() != null) {
            trace("dismissAdjustment: {} kept a syllable of its own, nothing to repair",
                currentIndex);
            return;
        }

        trace("dismissAdjustment: repairing neighbors of {}", currentIndex);
        line.adjustNeighborsForLyricDeletion(currentIndex, activeVerse);
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
        repaintHyphenChainPreview();

        // We want to notify actions that editing has ended so they can update their enabled state,
        // but only when not advancing to another lyric editor.
        if (isDoneEditing) {
            MessageCenter.post(new TextEditingDidChangeNotification(false));
        }
    }

    /**
     * Repaints the whole line, because an unclosed hyphen chain is engraved up to wherever this
     * editor sits and so changes shape when the editor opens or closes. That chain starts to the
     * left of the editor, outside the bounds the open and dismiss paths repaint, and advancing
     * with a lone hyphen writes nothing to the model, so no mutation-driven repaint follows either.
     */
    private void repaintHyphenChainPreview() {
        if (lineComponent != null) {
            lineComponent.repaint();
        }
    }
}
