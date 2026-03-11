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

import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

import javax.swing.*;
import javax.swing.text.*;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatInspector;
import com.formdev.flatlaf.extras.FlatUIDefaultsInspector;

import songscribe.ui.AppearanceManager;

import songscribe.font.SourceSans3Font;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;

@SuppressWarnings("ParameterNameDiffersFromOverriddenParameter")
public final class UIUtils {

    // Constants
    public static final int MENU_SHORTCUT_MASK = Toolkit.getDefaultToolkit()
        .getMenuShortcutKeyMaskEx();

    // The resolution of the score UI in logical pixels per inch
    public static final int RESOLUTION = 100;

    private static final Dimension LABEL_SPACER = new Dimension(5, 5);

    private UIUtils() {
    }

    //
    // Tooltips
    //

    public static String makeTooltipWithKeystroke(
        String name,
        KeyStroke acceleratorKey
    ) {
        var result = name;

        if (acceleratorKey != null) {
            result +=
                " (" +
                Utils.getPlatformKeyStrokeString(acceleratorKey) +
                ')';
        }

        return result;
    }

    public static void setToolTipText(JComponent component, UIAction action) {
        if (action == null) {
            component.setToolTipText(null);
            return;
        }

        var tip = (String) action.getValue(Action.SHORT_DESCRIPTION);

        if ((tip != null) && !tip.isEmpty()) {
            var name = action.getName();
            var html = "<html><strong>" + name + "</strong>";
            var accelerator = action.getAccelerator();

            if (accelerator != null) {
                var keyStrokeString = Utils.getPlatformKeyStrokeString(accelerator);
                html += "&nbsp;&nbsp;(" + keyStrokeString + ')';
            }

            tip = html + "<br>" + tip + "</html>";
        }

        component.setToolTipText(tip);
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

        if (action != null) {
            // If the action has a selected state, set the button's selected state accordingly
            var selected = (Boolean) action.getValue(Action.SELECTED_KEY);

            if (selected != null) {
                button.setSelected(selected);
            }

            button.setEnabled(action.isEnabled());
            button.setActionCommand(action.getActionCommand());
            button.setName(action.getActionCommand());
        } else {
            button.setEnabled(true);
        }
    }

    private static void setIcon(AbstractButton button, UIAction action) {
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

    public static void addAction(
        Object maybeAction
    ) {
        if (maybeAction instanceof UIAction action) {
            var keyStroke = action.getAccelerator();

            if (keyStroke == null) {
                return;
            }

            registerActionKeystroke(keyStroke, action);
        }
    }

    public static void registerActionKeystroke(KeyStroke keyStroke, @NotNull UIAction action) {
        var actionCommand = action.getActionCommand();
        var rootPane = MainFrame.getInstance().getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(keyStroke, actionCommand);
        rootPane.getActionMap().put(actionCommand, action);

    }

    public static void setupDesktopHandlers(MainFrame mainFrame, boolean hasPrefs) {
        var desktop = Desktop.getDesktop();

        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(_ -> Actions.ABOUT_ACTION.perform(mainFrame));
        }

        if (hasPrefs && desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(_ -> MainFrame.handlePrefs());
        }

        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(event -> mainFrame.handleOpenFile(event.getFiles()
                .getFirst()));
        }

        if (desktop.isSupported(Desktop.Action.APP_PRINT_FILE)) {
            desktop.setPrintFileHandler(event -> {
                mainFrame.handleOpenFile(event.getFiles()
                    .getFirst());
                mainFrame.handlePrint();
            });
        }

        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((_, response) -> {
                if (mainFrame.handleQuit()) {
                    response.performQuit();
                } else {
                    response.cancelQuit();
                }
            });
        }
    }

    //
    // Components
    //

    @NotNull
    public static JPanel padComponent(JComponent component, int padding) {
        return padComponent(component, padding, padding, padding, padding);
    }

    @NotNull
    public static JPanel padComponent(JComponent component, int paddingX, int paddingY) {
        return padComponent(component, paddingY, paddingX, paddingY, paddingX);
    }

    @NotNull
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

    public static void initToolbarButton(
        @NotNull AbstractButton button,
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
        return (SwingUtilities.getWindowAncestor(component) instanceof JFrame)
            ? (JFrame) SwingUtilities.getWindowAncestor(component)
            : null;
    }

    @Nullable
    public static JFrame getFocusedFrame() {
        var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

        if (focusOwner == null) {
            return null;
        }

        return getParentFrame(focusOwner);
    }

    public static void addStandardDialogKeyBindings(@NotNull JDialog dialog) {
        var escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        var actionKey = "action:WINDOW_CLOSING";

        var dispatchClosing = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispatchEvent(
                    new WindowEvent(dialog, WindowEvent.WINDOW_CLOSING)
                );
            }
        };

        var root = dialog.getRootPane();
        root
            .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(escapeKeyStroke, actionKey);
        root.getActionMap().put(actionKey, dispatchClosing);
    }

    public static void initLaf() {
        try {
            // Enable anti-aliasing and sub-pixel rendering for fonts
            System.setProperty("swing.aatext", "true");
            System.setProperty("awt.useSystemAAFontSettings", "lcd");

            SourceSans3Font.install();

            for (var style : new String[]{"Regular", "Italic", "Bold", "BoldItalic"}) {
                MyFontUtils.installLocalFont("LatoPlus-" + style + ".otf");
            }

            MyFontUtils.installLocalFont("Poetica-SuppOrnaments.otf");

            // Set up the base font families for FlatLaf
            FlatLaf.setPreferredFontFamily(SourceSans3Font.FAMILY);
            FlatLaf.setPreferredSemiboldFontFamily(
                SourceSans3Font.FAMILY_SEMIBOLD
            );

            FlatLaf.registerCustomDefaultsSource("songscribe");

            AppearanceManager.init();

            // In DEBUG mode, install FlatLaf's inspectors
            if (System.getenv("DEBUG") != null) {
                FlatUIDefaultsInspector.install("ctrl shift alt Y");
                FlatInspector.install("ctrl shift alt X");
            }
        } catch (Exception e) {
            Log.error("Error initializing laf", e);
        }
    }

    public enum LabelOrientation {
        LEFT, TOP
    }

    //
    // Misc
    //

    public static boolean isEditingText() {
        var focusFrame = UIUtils.getFocusedFrame();

        //noinspection ObjectEquality
        if (MainFrame.getInstance() != focusFrame) {
            return false;
        }

        var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        return manager.getFocusOwner() instanceof JTextComponent;
    }

    public record TaggedString(String text, Font font) {
    }

    @NotNull
    @Contract("_ -> new")
    public static TaggedString getTaggedString(String taggedText) {
        // If the text starts with "@", use the icon font.
        // If the text starts with "#", use the note font.
        // If the text ends with "/-?\d+", shift the baseline by that much.
        var text = taggedText;
        Font font = null;

        if (text.startsWith("@")) {
            font = MyFontUtils.getIconFont();
        } else if (text.startsWith("#")) {
            font = MyFontUtils.getNoteFont();
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
}
