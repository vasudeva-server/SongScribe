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
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.JTextComponent;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatInspector;
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector;
import com.formdev.flatlaf.util.SystemInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.font.LocalFonts;
import songscribe.font.MusescoreIconFont;
import songscribe.font.SourceSans3Font;
import songscribe.ui.AppearanceManager;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.action.UIAction;
import songscribe.ui.renderer.RenderingUtils;

// java.desktop exports both java.awt.event.MouseEvent and org.w3c.dom.events.MouseEvent,
// so the module import above leaves the simple name ambiguous. A single-type import wins
// over it and resolves the name for the whole file.

@SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
public final class UIUtils {

    private static final Logger LOG = LoggerFactory.getLogger(UIUtils.class);

    public static final int MENU_SHORTCUT_MASK = GraphicsEnvironment.isHeadless()
        ? InputEvent.CTRL_DOWN_MASK
        : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    public static final @Nullable Cursor HIDDEN_CURSOR = GraphicsEnvironment.isHeadless()
        ? null
        : Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            new Point(),
            "hidden-cursor");

    /** The index a {@link ListCellRenderer} receives for a closed combo box's display cell. */
    public static final int COMBO_DISPLAY_INDEX = -1;

    private static final Dimension LABEL_SPACER = new Dimension(5, 5);

    private static final String TOOLTIP_SECTION_SEPARATOR = "<br><br>";

    static final int SCREEN_MARGIN_PX = 20;

    private static final double DIALOG_CENTER_HEIGHT_FRACTION = 0.375;

    private static final int PREWARM_DIALOG_WIDTH_PX = 200;
    private static final int PREWARM_DIALOG_HEIGHT_PX = 100;

    /** Far enough off every screen that the pre-warm dialog is never seen. */
    private static final int OFFSCREEN_COORDINATE_PX = -10000;

    private static final int DOUBLE_CLICK_COUNT = 2;

    private UIUtils() {
    }

    /** @param action {@code null} for no tooltip */
    public static void setToolTipText(JComponent component, @Nullable Action action) {
        if (action == null) {
            component.setToolTipText(null);
            return;
        }

        var tip = (String) action.getValue(Action.SHORT_DESCRIPTION);

        if ((tip != null) && !tip.isEmpty()) {
            var name = Objects.requireNonNullElse((String) action.getValue(Action.NAME), "");
            var accelerator = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
            tip = toolTipHtml(List.of(new ToolTipSection(name, accelerator, tip)));
        }

        component.setToolTipText(tip);
    }

    public static void keepToolTipsOpenWhileHovered() {
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
    }

    public static void setToolTipText(JComponent component, ToolTipSection section) {
        component.setToolTipText(toolTipHtml(List.of(section)));
    }

    public static String toolTipHtml(List<ToolTipSection> sections) {
        var html = new StringJoiner(TOOLTIP_SECTION_SEPARATOR, "<html>", "</html>");

        for (var section : sections) {
            html.add(section.html());
        }

        return html.toString();
    }

    /**
     * All three are plain text: {@link #toolTipHtml} escapes them, so a {@code <}, {@code >} or
     * {@code &} shows as itself.
     *
     * @param accelerator {@code null} for none
     */
    public record ToolTipSection(String heading, @Nullable KeyStroke accelerator, String description) {

        private String html() {
            var html = "<strong>" + plainTextHtml(heading) + "</strong>";

            if (accelerator != null) {
                var modifiers = Utils.getPlatformModifiersString(accelerator);
                var keyName = Utils.getPlatformKeyString(accelerator);
                html += "&nbsp;&nbsp;(" + plainTextHtml(modifiers + keyName) + ')';
            }

            return html + "<br>" + plainTextHtml(description);
        }
    }

    private static String plainTextHtml(@Nullable String text) {
        if (text == null) {
            return "";
        }

        // Ampersand first, so the ampersands introduced below are not escaped again. The line
        // breaks come last, so their tags are not escaped.
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>");
    }

    public static void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    /** Modifiers are deliberately not consulted; each caller adds its own policy. */
    public static boolean isLeftClick(MouseEvent e) {
        return SwingUtilities.isLeftMouseButton(e);
    }

    /** Modifiers are deliberately not consulted; callers disagree about them and add their own policy. */
    public static boolean isLeftDoubleClick(MouseEvent e) {
        return SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == DOUBLE_CLICK_COUNT;
    }

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

        var selected = (Boolean) action.getValue(Action.SELECTED_KEY);

        if (selected != null) {
            button.setSelected(selected);
        }

        button.setEnabled(action.isEnabled());
        button.setActionCommand(action.getActionCommand());
        button.setName(action.getActionCommand());
    }

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
            var font = (Font) action.getValue(UIAction.FONT_KEY);
            button.setFont(font);
            button.setText((String) fontIcon);
            button.setIcon(null);
        } else {
            var icon = (Icon) action.getValue(Action.LARGE_ICON_KEY);

            if (icon != null) {
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

    private static final String COMPONENT_BORDER_COLOR_KEY = "Component.borderColor";

    public static Color getComponentBorderColor() {
        return UIManager.getColor(COMPONENT_BORDER_COLOR_KEY);
    }

    /** Call after all components have been added. */
    public static void setCanGrow(JComponent component, boolean horizontal, boolean vertical) {
        var size = component.getPreferredSize();
        component.setMaximumSize(
            new Dimension(
                horizontal ? Short.MAX_VALUE : size.width,
                vertical ? Short.MAX_VALUE : size.height
            )
        );
    }

    /** Call after all components have been added. */
    public static void setFlexibleWidth(JComponent component) {
        setCanGrow(component, true, false);
        setCanShrink(component, true, false);
    }

    /** Call after all components have been added. */
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
     * For a combo whose entries are drawn as on the score (always black on white). A
     * {@link ListCellRenderer} must still force the colors on the popup rows; the property does
     * not reach them.
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

    /**
     * Sized against the usable bounds of the display, the rectangle Swing measures the popup
     * against.
     *
     * @param cellHeightPx the height of every cell, as the renderer fixes it. A combo whose
     *                     cells vary in height is not sized here.
     */
    public static void fitPopupToScreen(JComboBox<?> combo, int cellHeightPx) {
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                var usableHeight = getScreenBounds(getParentWindow(combo)).height;
                combo.setMaximumRowCount(Math.max(1, usableHeight / cellHeightPx));
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // The next opening measures again, so a closing popup leaves nothing to undo.
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                // The next opening measures again, so a cancelled popup leaves nothing to undo.
            }
        });
    }

    public static void initToolbarButton(
        AbstractButton button,
        Dimension buttonSize
    ) {
        // We don't want focus leaving the music sheet
        button.setFocusable(false);

        button.setMargin(new Insets(0, 0, 0, 0));

        button.setPreferredSize(buttonSize);
        button.setMaximumSize(buttonSize);
        button.setMinimumSize(buttonSize);
    }

    /**
     * @return {@code null} when {@code screenPoint} is outside the frame's root pane
     */
    @Nullable
    public static Component getDeepestComponentAt(JFrame frame, Point screenPoint) {
        var rootPane = frame.getRootPane();
        var point = new Point(screenPoint);

        SwingUtilities.convertPointFromScreen(point, rootPane);
        return SwingUtilities.getDeepestComponentAt(rootPane, point.x, point.y);
    }

    /**
     * @return {@code null} when no application frame has focus, or the mouse is outside its
     *         root pane
     */
    @Nullable
    public static Component getComponentUnderMouse() {
        var frame = getApplicationFrame();

        if (frame == null) {
            return null;
        }

        return getDeepestComponentAt(frame, MouseInfo.getPointerInfo().getLocation());
    }

    @Nullable
    public static JFrame getApplicationFrame() {
        return Arrays.stream(Window.getWindows())
            .filter(window -> window.isVisible() && window.isFocused() && (window instanceof JFrame))
            .map(window -> (JFrame) window)
            .findFirst()
            .orElse(null);
    }

    /**
     * @return {@code component} itself when it is a frame, or {@code null} when it is in no frame
     */
    @Nullable
    public static JFrame getParentFrame(Component component) {
        return getParentWindow(component) instanceof JFrame frame ? frame : null;
    }

    @Nullable
    public static JFrame getFocusedFrame() {
        var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        if (focusOwner == null) {
            return null;
        }

        return getParentFrame(focusOwner);
    }

    /**
     * A dialog larger than the usable screen less {@link #SCREEN_MARGIN_PX} on each side is
     * shrunk to fit.
     */
    public static void positionDialog(JDialog dialog, @Nullable Component parent) {
        var window = getParentWindow(parent);
        var screen = getScreenBounds(window);
        var anchor = window != null ? window.getBounds() : screen;
        var size = dialog.getSize();

        var x = anchor.x + (anchor.width - size.width) / 2;
        var y = anchor.y + (int) Math.round(anchor.height * DIALOG_CENTER_HEIGHT_FRACTION) - size.height / 2;

        var marginedScreen = new Rectangle(screen);
        marginedScreen.grow(-SCREEN_MARGIN_PX, -SCREEN_MARGIN_PX);

        dialog.setBounds(clampInto(new Rectangle(x, y, size.width, size.height), marginedScreen));
    }

    /**
     * Measured against the usable bounds of the screen that holds the top-left corner, or of the
     * default screen when no screen holds it. A rectangle larger than that screen is shrunk to
     * fit.
     */
    public static Rectangle clampToScreen(Rectangle bounds) {
        return clampInto(bounds, usableBounds(configurationAt(bounds.getLocation())));
    }

    private static Rectangle clampInto(Rectangle bounds, Rectangle screen) {
        var width = Math.min(bounds.width, screen.width);
        var height = Math.min(bounds.height, screen.height);
        var x = Math.clamp(bounds.x, screen.x, screen.x + screen.width - width);
        var y = Math.clamp(bounds.y, screen.y, screen.y + screen.height - height);

        return new Rectangle(x, y, width, height);
    }

    private static GraphicsConfiguration configurationAt(Point point) {
        for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            var configuration = device.getDefaultConfiguration();

            if (configuration.getBounds().contains(point)) {
                return configuration;
            }
        }

        return defaultConfiguration();
    }

    private static GraphicsConfiguration defaultConfiguration() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration();
    }

    public static @Nullable Window getParentWindow(@Nullable Component parent) {
        if (parent instanceof Window w) {
            return w;
        }

        return parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
    }

    /**
     * Usable rather than full bounds: Swing measures its own popups against them, and overrules
     * anything larger.
     *
     * @param window {@code null} to measure the default display. A window not yet made
     *               displayable is on no display and measures the default one too.
     */
    static Rectangle getScreenBounds(@Nullable Window window) {
        var configuration = window == null ? null : window.getGraphicsConfiguration();

        return usableBounds(configuration == null ? defaultConfiguration() : configuration);
    }

    private static Rectangle usableBounds(GraphicsConfiguration configuration) {
        var bounds = configuration.getBounds();
        var insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);

        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;

        return bounds;
    }

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

    public static void preWarmDialogPeer(Component parent) {
        SwingUtilities.invokeLater(() -> {
            try {
                var frame = getParentFrame(parent);

                if (frame == null) {
                    return;
                }

                var dummy = getJDialog(frame);

                SwingUtilities.invokeLater(dummy::dispose);
            } catch (Exception ignored) {
                // Never let pre-warming break startup
            }
        });
    }

    private static JDialog getJDialog(Frame ancestor) {
        var dummy = new JDialog(ancestor, "Pre-warm", false);
        dummy.setResizable(false);
        dummy.setSize(PREWARM_DIALOG_WIDTH_PX, PREWARM_DIALOG_HEIGHT_PX);

        // Critical order: set location *and* make sure it's offscreen before any realization
        dummy.setLocation(OFFSCREEN_COORDINATE_PX, OFFSCREEN_COORDINATE_PX);

        // Optional but helps on some platforms: pack first (forces some peer init without showing)
        dummy.pack();

        dummy.setVisible(true);
        return dummy;
    }

    public static void initMinimalTheme() {
        // Every component measures its text under the antialiasing hint it gets from the UI
        // defaults. On macOS the toolkit suggests LCD, which FlatLaf passes on, but macOS paints
        // grayscale text, whose glyph widths differ: text measured under LCD paints wider than
        // its component and covers the right padding. A developer default outranks the hint
        // of every look and feel, so it holds across theme changes.
        if (SystemInfo.isMacOS) {
            UIManager.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        SourceSans3Font.installRegular();

        FlatLaf.setPreferredFontFamily(SourceSans3Font.FAMILY);

        FlatLaf.registerCustomDefaultsSource("songscribe");

        AppearanceManager.init();

        if (System.getenv("DEBUG") != null) {
            FlatUIDefaultsInspector.install("ctrl shift alt Y");
            FlatInspector.install("ctrl shift alt X");
        }
    }

    public static void installEagerFonts() {
        SourceSans3Font.installRemaining();
        LocalFonts.install("TiroBangla-Regular.ttf");
    }

    /** @return the theme's font for {@code key}, or a plain {@code JLabel}'s font when the theme declares none */
    public static Font getUIFont(String key) {
        var font = UIManager.getFont(key);

        if (font == null) {
            font = new JLabel().getFont();
        }

        return font;
    }

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

    private static Font shiftFontBaseline(
        Font font,
        int shiftPx
    ) {
        if (shiftPx == 0) {
            return font;
        }

        var transform = new AffineTransform();
        transform.translate(0, shiftPx);
        return font.deriveFont(transform);
    }

    /**
     * A leading {@code @} selects the icon font and a leading {@code #} the note font. A
     * trailing {@code /-?\d+} shifts the baseline by that many pixels.
     */
    public static TaggedString getTaggedString(String taggedText) {
        var text = taggedText;
        Font font = null;

        if (text.startsWith("@")) {
            font = MusescoreIconFont.font();
        } else if (text.startsWith("#")) {
            font = RenderingUtils.getMusicFont();
        }

        if (font != null) {
            var parts = text.split("/");

            if (parts.length > 1) {
                text = parts[0];
                var baselineShiftPx = Integer.parseInt(parts[1]);
                font = shiftFontBaseline(font, baselineShiftPx);
            }

            text = text.substring(1);
            return new TaggedString(text, font);
        }

        return new TaggedString(text, null);
    }

}
