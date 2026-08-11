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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.desktop;

// java.desktop exports both java.awt.event.MouseEvent and org.w3c.dom.events.MouseEvent,
// so the module import above leaves the simple name ambiguous. A single-type import wins
// over it and resolves the name for the whole file.
import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.ui.renderer.RenderingUtils;

@SuppressWarnings("PackageVisibleInnerClass")
class UIUtilsTest extends UnitTest {

    @Nested
    class PositionDialog {

        // 3/8-down formula constants — matching the production comment
        private static final int VERTICAL_POSITION_NUMERATOR = 3;
        private static final int VERTICAL_POSITION_DENOMINATOR = 8;

        private static final int SCREEN_W = 1920;
        private static final int SCREEN_H = 1080;

        private static final int WINDOW_X = 100;
        private static final int WINDOW_Y = 200;
        private static final int WINDOW_W = 800;
        private static final int WINDOW_H = 600;

        private static final int DIALOG_W = 400;
        private static final int DIALOG_H = 300;

        // Small parent window so that unclamped x/y both fall below SCREEN_MARGIN_PX
        private static final int TINY_WINDOW_W = 100;
        private static final int TINY_WINDOW_H = 100;

        @Test
        void testPositionDialogPlacesAt3_8DownParentAndCentersHorizontally() {
            var screenBounds = new Rectangle(0, 0, SCREEN_W, SCREEN_H);
            var windowBounds = new Rectangle(WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H);
            var dialogSize = new Dimension(DIALOG_W, DIALOG_H);

            var mockWindow = mock(Window.class);
            when(mockWindow.getBounds()).thenReturn(windowBounds);

            var mockDialog = mock(JDialog.class);
            when(mockDialog.getSize()).thenReturn(dialogSize);

            try (var uiUtilsMock = mockStatic(UIUtils.class, CALLS_REAL_METHODS)) {
                uiUtilsMock.when(() -> UIUtils.getParentWindow(any())).thenReturn(mockWindow);
                uiUtilsMock.when(() -> UIUtils.getScreenBounds(any())).thenReturn(screenBounds);

                UIUtils.positionDialog(mockDialog, mockWindow);

                var expectedX = WINDOW_X + (WINDOW_W - DIALOG_W) / 2;
                var expectedY = WINDOW_Y
                    + WINDOW_H * VERTICAL_POSITION_NUMERATOR / VERTICAL_POSITION_DENOMINATOR
                    - DIALOG_H / 2;
                verify(mockDialog).setLocation(expectedX, expectedY);
            }
        }

        @Test
        void testPositionDialogClampsToScreenMarginWhenComputedPositionIsNegative() {
            var screenBounds = new Rectangle(0, 0, SCREEN_W, SCREEN_H);
            var tinyWindowBounds = new Rectangle(0, 0, TINY_WINDOW_W, TINY_WINDOW_H);
            var dialogSize = new Dimension(DIALOG_W, DIALOG_H);

            var mockWindow = mock(Window.class);
            when(mockWindow.getBounds()).thenReturn(tinyWindowBounds);

            var mockDialog = mock(JDialog.class);
            when(mockDialog.getSize()).thenReturn(dialogSize);

            try (var uiUtilsMock = mockStatic(UIUtils.class, CALLS_REAL_METHODS)) {
                uiUtilsMock.when(() -> UIUtils.getParentWindow(any())).thenReturn(mockWindow);
                uiUtilsMock.when(() -> UIUtils.getScreenBounds(any())).thenReturn(screenBounds);

                UIUtils.positionDialog(mockDialog, mockWindow);

                verify(mockDialog).setLocation(UIUtils.SCREEN_MARGIN_PX, UIUtils.SCREEN_MARGIN_PX);
            }
        }
    }

    @Nested
    class IsLeftDoubleClick {

        private static final int SINGLE_CLICK = 1;
        private static final int TRIPLE_CLICK = 3;

        @Test
        void testIsLeftDoubleClickTrueForLeftButtonDoubleClick() {
            assertThat(UIUtils.isLeftDoubleClick(clickEvent(MouseEvent.BUTTON1, UIUtils.DOUBLE_CLICK_COUNT)))
                .isTrue();
        }

        @Test
        void testIsLeftDoubleClickFalseForLeftButtonSingleClick() {
            assertThat(UIUtils.isLeftDoubleClick(clickEvent(MouseEvent.BUTTON1, SINGLE_CLICK))).isFalse();
        }

        @Test
        void testIsLeftDoubleClickFalseForNonLeftButtonDoubleClick() {
            assertThat(UIUtils.isLeftDoubleClick(clickEvent(MouseEvent.BUTTON3, UIUtils.DOUBLE_CLICK_COUNT)))
                .isFalse();
        }

        /**
         * Pins the exact-equality choice in {@link UIUtils#isLeftDoubleClick} rather than a
         * {@code >=} reading — a triple click is not a double-click.
         */
        @Test
        void testIsLeftDoubleClickFalseForLeftButtonTripleClick() {
            assertThat(UIUtils.isLeftDoubleClick(clickEvent(MouseEvent.BUTTON1, TRIPLE_CLICK))).isFalse();
        }

        private MouseEvent clickEvent(int button, int clickCount) {
            var source = new JPanel();
            return new MouseEvent(
                source,
                MouseEvent.MOUSE_CLICKED,
                0L,         // when (not examined by production code)
                0,          // modifiers
                10, 10,     // x, y
                10, 10,     // xAbs, yAbs
                clickCount,
                false,      // popupTrigger
                button
            );
        }
    }

    @Nested
    class GetTaggedString {

        @Test
        void testGetTaggedStringAtPrefixSetsIconFontAndStripsPrefix() {
            var result = UIUtils.getTaggedString("@icon");
            assertThat(result.text()).isEqualTo("icon");
            assertThat(result.font()).isSameAs(MyFontUtils.getIconFont());
        }

        @Test
        void testGetTaggedStringHashPrefixSetsMusicFontAndStripsPrefix() {
            var result = UIUtils.getTaggedString("#music");
            assertThat(result.text()).isEqualTo("music");
            assertThat(result.font()).isSameAs(RenderingUtils.getMusicFont());
        }

        @Test
        void testGetTaggedStringNoPrefixReturnsNullFont() {
            var result = UIUtils.getTaggedString("hello");
            assertThat(result.text()).isEqualTo("hello");
            assertThat(result.font()).isNull();
        }

        @Test
        void testGetTaggedStringWithBaselineShiftDerivesDistinctFont() {
            var result = UIUtils.getTaggedString("@icon/-5");
            assertThat(result.text()).isEqualTo("icon");
            assertThat(result.font()).isNotNull().isNotSameAs(MyFontUtils.getIconFont());
        }

        @Test
        void testGetTaggedStringNoPrefixWithSlashIsReturnedVerbatim() {
            var result = UIUtils.getTaggedString("text/5");
            assertThat(result.text()).isEqualTo("text/5");
            assertThat(result.font()).isNull();
        }
    }

    @Nested
    class IsEditingTextIn {

        private MockedStatic<UIUtils> uiUtilsMock;
        private MockedStatic<KeyboardFocusManager> kfmMock;
        private JFrame focusedFrame;
        private KeyboardFocusManager mockManager;

        @BeforeEach
        void setUp() {
            focusedFrame = mock(JFrame.class);
            mockManager = mock(KeyboardFocusManager.class);

            uiUtilsMock = mockStatic(UIUtils.class, CALLS_REAL_METHODS);
            kfmMock = mockStatic(KeyboardFocusManager.class, CALLS_REAL_METHODS);

            uiUtilsMock.when(UIUtils::getFocusedFrame).thenReturn(focusedFrame);
            kfmMock.when(KeyboardFocusManager::getCurrentKeyboardFocusManager).thenReturn(mockManager);
        }

        @AfterEach
        void tearDown() {
            kfmMock.close();
            uiUtilsMock.close();
        }

        @Test
        void testIsEditingTextInReturnsFalseWhenWindowDoesNotMatchFocusedFrame() {
            var otherFrame = mock(JFrame.class);
            assertThat(UIUtils.isEditingTextIn(otherFrame)).isFalse();
        }

        @Test
        void testIsEditingTextInReturnsFalseWhenFocusOwnerIsNotTextComponent() {
            when(mockManager.getFocusOwner()).thenReturn(mock(JButton.class));
            assertThat(UIUtils.isEditingTextIn(focusedFrame)).isFalse();
        }

        @Test
        void testIsEditingTextInReturnsFalseWhenTextComponentNotShowing() {
            var textField = mock(JTextField.class);
            when(textField.isShowing()).thenReturn(false);
            when(mockManager.getFocusOwner()).thenReturn(textField);
            assertThat(UIUtils.isEditingTextIn(focusedFrame)).isFalse();
        }

        @Test
        void testIsEditingTextInReturnsTrueWhenTextComponentIsShowing() {
            var textField = mock(JTextField.class);
            when(textField.isShowing()).thenReturn(true);
            when(mockManager.getFocusOwner()).thenReturn(textField);
            assertThat(UIUtils.isEditingTextIn(focusedFrame)).isTrue();
        }
    }

    /**
     * The tooltip is assembled as HTML, so any name or accelerator carrying {@code <},
     * {@code >} or {@code &} has to be escaped. An unescaped {@code <} opens what the
     * renderer reads as a tag and takes the rest of the tooltip with it.
     */
    @Nested
    class SetToolTipText {

        private String tooltipFor(String name, @Nullable KeyStroke accelerator) {
            var action = new AbstractAction(name) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    // Never invoked; the tooltip is built from the action's values alone.
                }
            };
            action.putValue(Action.SHORT_DESCRIPTION, "Toggle it");
            action.putValue(Action.ACCELERATOR_KEY, accelerator);

            var component = new JButton();
            UIUtils.setToolTipText(component, action);
            return component.getToolTipText();
        }

        @Test
        void testEscapesLessThanInAccelerator() {
            assertThat(tooltipFor("Staccato", KeyStroke.getKeyStroke('<')))
                .contains("(&lt;)")
                .doesNotContain("(<)");
        }

        @Test
        void testEscapesGreaterThanInAccelerator() {
            assertThat(tooltipFor("Accent", KeyStroke.getKeyStroke('>')))
                .contains("(&gt;)")
                .doesNotContain("(>)");
        }

        @Test
        void testEscapesMarkupCharactersInActionName() {
            assertThat(tooltipFor("Fit <Width> & Height", null))
                .contains("Fit &lt;Width&gt; &amp; Height");
        }

        @Test
        void testLeavesAnOrdinaryAcceleratorUnchanged() {
            assertThat(tooltipFor("Sharp", KeyStroke.getKeyStroke(KeyEvent.VK_S, 0)))
                .contains("<strong>Sharp</strong>")
                .contains("(S)");
        }
    }
}
