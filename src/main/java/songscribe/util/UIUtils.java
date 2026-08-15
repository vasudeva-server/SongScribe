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

package songscribe.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatInspector;
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.font.SourceSans3Font;
import songscribe.ui.AppearanceManager;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.UIAction;
import songscribe.ui.renderer.RenderingUtils;

// java.desktop exports both java.awt.event.MouseEvent and org.w3c.dom.events.MouseEvent,
// so the module import above leaves the simple name ambiguous. A single-type import wins
// over it and resolves the name for the whole file.

@SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
public final class UIUtils {

    private static final Logger LOG = LoggerFactory.getLogger(UIUtils.class);

    // Constants
    public static final int MENU_SHORTCUT_MASK = GraphicsEnvironment.isHeadless()
        ? InputEvent.CTRL_DOWN_MASK
        : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    /** A fully transparent 1x1 cursor, for suppressing the system cursor over a component. */
    public static final @Nullable Cursor HIDDEN_CURSOR = GraphicsEnvironment.isHeadless()
        ? null
        : Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            new Point(),
            "hidden-cursor");

    private static final Dimension LABEL_SPACER = new Dimension(5, 5);

    static final int SCREEN_MARGIN_PX = 20;

    /** Click count that identifies a double-click in a {@link MouseEvent}. */
    public static final int DOUBLE_CLICK_COUNT = 2;

    private UIUtils() {
    }

    //
    // Tooltips
    //

    public static void setToolTipText(JComponent component, @Nullable Action action) {
        if (action == null) {
            component.setToolTipText(null);
            return;
        }

        var tip = (String) action.getValue(Action.SHORT_DESCRIPTION);

        if ((tip != null) && !tip.isEmpty()) {
            var name = (String) action.getValue(Action.NAME);
            var html = "<html><strong>" + escapeHtml(name) + "</strong>";
            var accelerator = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);

            if (accelerator != null) {
                var modifiers = Utils.getPlatformModifiersString(accelerator);
                var keyName = Utils.getPlatformKeyString(accelerator);
                html += "&nbsp;&nbsp;(" + escapeHtml(modifiers + keyName) + ')';
            }

            tip = html + "<br>" + tip + "</html>";
        }

        component.setToolTipText(tip);
    }

    /**
     * Escapes the characters Swing's HTML renderer would otherwise treat as markup.
     * Without this, an accelerator or action name containing {@code <} — the staccato
     * shortcut, for one — opens what the renderer reads as a tag, so the character and
     * everything after it vanish from the tooltip.
     */
    private static String escapeHtml(@Nullable String text) {
        if (text == null) {
            return "";
        }

        // Ampersand first, so the ampersands introduced below are not escaped again.
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    //
    // Audio feedback
    //

    public static void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    //
    // Mouse events
    //

    /**
     * Answers whether {@code e} is a left-button click with a click count of
     * {@value #DOUBLE_CLICK_COUNT}.
     * <p>
     * Modifiers are deliberately not consulted. Callers disagree about them: Alt
     * switches the staff to SELECT mode and must not disqualify a gesture, shift is
     * excluded only where it would discard a selection being built, and the window-zoom
     * gesture excludes nothing. Each caller adds the modifier policy it wants.
     */
    public static boolean isLeftDoubleClick(MouseEvent e) {
        return SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == DOUBLE_CLICK_COUNT;
    }

    //
    // Actions
    //

    /*
     * This method configures the visual properties of a button
     * based on the properties of an action. It does NOT set the
     * action of the button.
     *
     * Custom properties defined by UIAction are honored.
     */

    public static void configureButtonFromAction(AbstractButton button, UIAction action) {
        setIcon(button, action);
        setToolTipText(button, action);

        // If the action has a selected state, set the button's selected state accordingly
        var selected = (Boolean) action.getValue(Action.SELECTED_KEY);

        if (selected != null) {
            button.setSelected(selected);
        }

        button.setEnabled(action.isEnabled());
        button.setActionCommand(action.getActionCommand());
        button.setName(action.getActionCommand());
    }

    /**
     * Reacts to a {@link PropertyChangeEvent} fired by a {@link UIAction} on a button that was
     * configured via {@link #configureButtonFromAction}, keeping the button's icon, tooltip, and
     * enabled state in sync with the action.
     */
    public static void handleActionPropertyChange(AbstractButton button, UIAction action, PropertyChangeEvent event) {
        var prop = event.getPropertyName();

        if (prop.equals(UIAction.FONT_ICON_KEY) || prop.equals(UIAction.FONT_KEY)) {
            configureButtonFromAction(button, action);
        } else if (prop.equals(Action.SHORT_DESCRIPTION)) {
            setToolTipText(button, action);
        }
    }

    private static void setIcon(AbstractButton button, @Nullable UIAction action) {
        if (action == null) {
            button.setIcon(null);
            button.setText(null);
            return;
        }

        var fontIcon = action.getValue(UIAction.FONT_ICON_KEY);

        if (fontIcon != null) {
            // If the action has a font icon, set it and remove any icon image
            var font = (Font) action.getValue(UIAction.FONT_KEY);
            button.setFont(font);
            button.setText((String) fontIcon);
            button.setIcon(null);
        } else {
            var icon = (Icon) action.getValue(Action.LARGE_ICON_KEY);

            if (icon != null) {
                // If the action has a large icon, remove any text
                button.setIcon(icon);
                button.setText(null);
            }
        }
    }

    public static void addAction(JRootPane rootPane, Object maybeAction) {
        if (maybeAction instanceof UIAction action) {
            var keyStroke = action.getAccelerator();
            registerActionKeystroke(rootPane, keyStroke, action);
        }
    }

    public static void registerActionKeystroke(
        JRootPane rootPane,
        KeyStroke keyStroke,
        UIAction action
    ) {
        var actionCommand = action.getActionCommand();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(keyStroke, actionCommand);
        rootPane.getActionMap().put(actionCommand, action);
    }

    //
    // Components
    //

    public static JPanel padComponent(JComponent component, int padding) {
        return padComponent(component, padding, padding, padding, padding);
    }

    public static JPanel padComponent(JComponent component, int paddingX, int paddingY) {
        return padComponent(component, paddingY, paddingX, paddingY, paddingX);
    }

    public static JPanel padComponent(JComponent component, Insets insets) {
        return padComponent(component, insets.top, insets.left, insets.bottom, insets.right);
    }

    public static JPanel padComponent(
        JComponent component,
        int top,
        int left,
        int bottom,
        int right
    ) {
        component.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        var panel = new JPanel(new BorderLayout());
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    public static Border spacingBorder(FlatLafKey flatLafKey) {
        var insets = FlatLafProps.getInsets(flatLafKey);
        return BorderFactory.createEmptyBorder(insets.top, insets.left, insets.bottom, insets.right);
    }

    // FlatLaf's standard component border color, used to box or underline components
    // so they match the look of the theme's default field borders.
    private static final String COMPONENT_BORDER_COLOR_KEY = "Component.borderColor";

    public static Color getComponentBorderColor() {
        return UIManager.getColor(COMPONENT_BORDER_COLOR_KEY);
    }

    /**
     * This should be called AFTER all components have been added.
     */
    public static void setCanGrow(JComponent component, boolean horizontal, boolean vertical) {
        var size = component.getPreferredSize();
        component.setMaximumSize(
            new Dimension(
                horizontal ? Short.MAX_VALUE : size.width,
                vertical ? Short.MAX_VALUE : size.height
            )
        );
    }

    /**
     * Convenience: allows the component to grow and shrink horizontally
     * but locks its height to the preferred size. This should be called
     * AFTER all components have been added.
     */
    public static void setFlexibleWidth(JComponent component) {
        setCanGrow(component, true, false);
        setCanShrink(component, true, false);
    }

    /**
     * This should be called AFTER all components have been added.
     */
    public static void setCanShrink(JComponent component, boolean horizontal, boolean vertical) {
        var size = component.getPreferredSize();
        component.setMinimumSize(
            new Dimension(
                horizontal ? 1 : size.width,
                vertical ? 1 : size.height
            )
        );
    }

    /**
     * Forces a combo box to render in light mode — white background, black foreground, in both
     * the closed box and the popup — regardless of the active theme.
     *
     * <p>Use this for a combo whose entries are drawn as they would appear on the score itself
     * (e.g. a key signature), which is always black on white; a themed combo showing them in the
     * app's current (possibly dark) colors would look unlike what they represent. The FlatLaf
     * style property covers the combo's own chrome; a {@link ListCellRenderer} still has to force
     * the same colors on the popup rows it paints, since the property does not reach them.
     */
    public static void forceLightModeCombo(JComboBox<?> combo) {
        combo.setOpaque(true);
        combo.putClientProperty(
            FlatClientProperties.STYLE,
            "popupBackground: #FFFFFF; " +
                "foreground: #000000; " +
                "background: #FFFFFF; " +
                "editableBackground: #FFFFFF"
        );
    }

    public static void initToolbarButton(
        AbstractButton button,
        Dimension buttonSize
    ) {
        // We don't want focus leaving the music sheet
        button.setFocusable(false);

        // No margin around toolbar buttons
        button.setMargin(new Insets(0, 0, 0, 0));

        // Make sure they are fixed size
        button.setPreferredSize(buttonSize);
        button.setMaximumSize(buttonSize);
        button.setMinimumSize(buttonSize);
    }

    /**
     * Returns the deepest component at the given screen point within the frame.
     */
    @Nullable
    public static Component getDeepestComponentAt(JFrame frame, Point screenPoint) {
        var frameLocation = frame.getLocationOnScreen();
        var relX = screenPoint.x - frameLocation.x;
        var relY = screenPoint.y - frameLocation.y;
        return SwingUtilities.getDeepestComponentAt(frame, relX, relY);
    }

    // Returns the deepest component under the mouse
    @Nullable
    public static Component getComponentUnderMouse() {
        var frame = getApplicationFrame();

        if (frame == null) {
            return null;
        }

        var parent = frame.getRootPane();

        // Retrieve mouse position on the screen
        var mousePosition = MouseInfo.getPointerInfo().getLocation();

        // Convert screen coordinates to the coordinates relative to the parent component
        SwingUtilities.convertPointFromScreen(mousePosition, parent);

        // Get the deepest component at the given coordinates
        return SwingUtilities.getDeepestComponentAt(parent, mousePosition.x, mousePosition.y);
    }

    @Nullable
    public static JFrame getApplicationFrame() {
        return Arrays.stream(Window.getWindows())
            .filter(window -> window.isVisible() && window.isFocused() && (window instanceof JFrame))
            .map(window -> (JFrame) window)
            .findFirst()
            .orElse(null);
    }

    @Nullable
    public static JFrame getParentFrame(Component component) {
        var ancestor = SwingUtilities.getWindowAncestor(component);
        return (ancestor instanceof JFrame frame) ? frame : null;
    }

    @Nullable
    public static JFrame getFocusedFrame() {
        var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        if (focusOwner == null) {
            return null;
        }

        return getParentFrame(focusOwner);
    }

    // Position the dialog at 3/8 of the way down the parent window (or screen if no
    // parent), centered horizontally, clamped to the screen bounds with a 20px margin.
    public static void positionDialog(JDialog dialog, @Nullable Component parent) {
        var window = getParentWindow(parent);
        var screen = getScreenBounds(window);
        var bounds = window != null ? window.getBounds() : screen;
        var size = dialog.getSize();

        var x = bounds.x + (bounds.width - size.width) / 2;
        var y = bounds.y + bounds.height * 3 / 8 - size.height / 2;

        x = Math.clamp(x, screen.x + SCREEN_MARGIN_PX, screen.x + screen.width - size.width - SCREEN_MARGIN_PX);
        y = Math.clamp(y, screen.y + SCREEN_MARGIN_PX, screen.y + screen.height - size.height - SCREEN_MARGIN_PX);

        dialog.setLocation(x, y);
    }

    public static @Nullable Window getParentWindow(@Nullable Component parent) {
        if (parent instanceof Window w) {
            return w;
        }

        return parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
    }

    static Rectangle getScreenBounds(@Nullable Window window) {
        if (window != null) {
            return window.getGraphicsConfiguration().getBounds();
        }

        return GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration()
            .getBounds();
    }

    /**
     * Binds {@code keyStroke} on {@code component}'s input map for {@code condition}
     * (one of {@link JComponent#WHEN_FOCUSED}, {@link JComponent#WHEN_IN_FOCUSED_WINDOW},
     * or {@link JComponent#WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}) to invoke {@code handler}.
     */
    public static void bindKey(
        JComponent component,
        int condition,
        KeyStroke keyStroke,
        String actionKey,
        Runnable handler
    ) {
        component.getInputMap(condition).put(keyStroke, actionKey);
        component.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.run();
            }
        });
    }

    public static void addStandardDialogKeyBindings(JDialog dialog) {
        Runnable dispatchClosing = () -> dialog.dispatchEvent(
            new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING)
        );

        var actionKey = "action:WINDOW_CLOSING";
        var rootPane = dialog.getRootPane();

        bindKey(rootPane, JComponent.WHEN_IN_FOCUSED_WINDOW,
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), actionKey, dispatchClosing);
        bindKey(rootPane, JComponent.WHEN_IN_FOCUSED_WINDOW,
            KeyStroke.getKeyStroke(KeyEvent.VK_W, MENU_SHORTCUT_MASK), actionKey, dispatchClosing);
    }

    /**
     * Pre-warms the AWT native window peer and FlatLaf rendering pipeline.
     * Call once after the main JFrame is visible.
     */
    public static void preWarmDialogPeer(Component parent) {
        SwingUtilities.invokeLater(() -> {
            try {
                var ancestor = SwingUtilities.getWindowAncestor(parent);

                if (!(ancestor instanceof Frame)) {
                    return; // fallback if no suitable parent
                }

                var dummy = getJDialog((Frame) ancestor);

                // Give the native window a tiny moment to settle (usually not needed, but safe)
                SwingUtilities.invokeLater(dummy::dispose);
            } catch (Exception ignored) {
                // Never let pre-warming break startup
            }
        });
    }

    private static JDialog getJDialog(Frame ancestor) {
        var dummy = new JDialog(ancestor, "Pre-warm", false);
        dummy.setResizable(false);
        dummy.setSize(200, 100);

        // Critical order: set location *and* make sure it's offscreen before any realization
        dummy.setLocation(-10000, -10000);

        // Optional but helps on some platforms: pack first (forces some peer init without showing)
        dummy.pack();

        // Make it visible very briefly on the EDT, then immediately hide/dispose
        dummy.setVisible(true);
        return dummy;
    }

    /**
     * Initializes the minimum theme state needed before the splash window paints:
     * the Regular font face, the preferred font family, the custom defaults source,
     * and the appearance manager. Also installs FlatLaf debug inspectors when running
     * under {@code DEBUG=1}.
     */
    public static void initMinimalTheme() {
        SourceSans3Font.installRegular();

        // Set up the base font family for FlatLaf
        FlatLaf.setPreferredFontFamily(SourceSans3Font.FAMILY);

        FlatLaf.registerCustomDefaultsSource("songscribe");

        AppearanceManager.init();

        // In DEBUG mode, install FlatLaf's inspectors
        if (System.getenv("DEBUG") != null) {
            FlatUIDefaultsInspector.install("ctrl shift alt Y");
            FlatInspector.install("ctrl shift alt X");
        }
    }

    /**
     * Installs the remaining (non-Regular) SourceSans3 faces plus the TiroBangla font.
     * Called eagerly after the splash is visible so that all faces are available
     * before the main window opens.
     */
    public static void installEagerFonts() {
        SourceSans3Font.installRemaining();
        MyFontUtils.installLocalFont("TiroBangla-Regular.ttf");
    }

    //
    // Misc
    //

    public static boolean isEditingTextIn(Window window) {
        var focusFrame = UIUtils.getFocusedFrame();

        //noinspection ObjectEquality
        if (window != focusFrame) {
            return false;
        }

        var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        // A detached JTextComponent (one whose editor was just removed from the
        // component tree) is not really being edited. Require it to still be showing.
        return manager.getFocusOwner() instanceof JTextComponent textComponent
            && textComponent.isShowing();
    }

    public record TaggedString(String text, @Nullable Font font) {
    }

    public static TaggedString getTaggedString(String taggedText) {
        // If the text starts with "@", use the icon font.
        // If the text starts with "#", use the note font.
        // If the text ends with "/-?\d+", shift the baseline by that much.
        var text = taggedText;
        Font font = null;

        if (text.startsWith("@")) {
            font = MyFontUtils.getIconFont();
        } else if (text.startsWith("#")) {
            font = RenderingUtils.getMusicFont();
        }

        if (font != null) {
            var parts = text.split("/");

            if (parts.length > 1) {
                text = parts[0];
                var baselineShift = Integer.parseInt(parts[1]);
                font = MyFontUtils.deriveBaselineShiftedFont(font, baselineShift);
            }

            text = text.substring(1);
            return new TaggedString(text, font);
        }

        return new TaggedString(text, null);
    }

    public static void readComboValuesFromFile(
        JComboBox<? super String> combo,
        String file
    ) {
        try {
            var inputStream =
                UIUtils.class.getResourceAsStream("/conf/" + file);

            if (inputStream == null) {
                throw new FileNotFoundException("File not found: " + file);
            }

            try (
                var reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                )
            ) {
                var line = reader.readLine();

                while (line != null) {
                    combo.addItem(line);
                    line = reader.readLine();
                }
            }
        } catch (IOException e) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_FILE_ERROR,
                Strings.ERROR_FILE_REINSTALL
            );
        }
    }
}
