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
package songscribe.ui.dialog;

import java.awt.Frame;
import java.io.File;

import com.formdev.flatlaf.util.SystemFileChooser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.util.ExtensionFileFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PlatformFileDialog}: {@code convertFilter},
 * {@code getFileFilter}, {@code showSaveDialog}, and constructor index clamping.
 */
class PlatformFileDialogTest extends UnitTest {

    /** Dummy non-null window — only used to satisfy the constructor's @NonNull contract. */
    private static final Frame DUMMY_FRAME = mock(Frame.class);

    // ---- convertFilter ----

    @Nested
    class ConvertFilter {

        @Test
        void testStripsParenSuffixFromSingleExtension() {
            // ExtensionFileFilter always appends " (ext)" to the description
            var filter = new ExtensionFileFilter("MSSW Files", "mssw");
            var converted = PlatformFileDialog.convertFilter(filter);
            assertThat(converted.getDescription())
                .as("paren suffix stripped from single-extension description")
                .isEqualTo("MSSW Files");
        }

        @Test
        void testStripsParenSuffixFromMultipleExtensions() {
            var filter = new ExtensionFileFilter("Music Files", "mssw", "xml");
            var converted = PlatformFileDialog.convertFilter(filter);
            assertThat(converted.getDescription())
                .as("paren suffix stripped from multi-extension description")
                .isEqualTo("Music Files");
        }

        @Test
        void testExtensionsArePreservedAfterConversion() {
            var filter = new ExtensionFileFilter("MSSW Files", "mssw", "xml");
            var converted = PlatformFileDialog.convertFilter(filter);
            assertThat(converted.getExtensions())
                .as("extensions are preserved in converted filter")
                .containsExactly("mssw", "xml");
        }

        @Test
        void testDescriptionUnchangedWhenNoParenPresent() {
            // Subclass to inject a description with no '(' character
            var filter = new ExtensionFileFilter("Plain Description", "mssw") {
                @Override
                public String getDescription() {
                    return "Plain Description";
                }
            };
            var converted = PlatformFileDialog.convertFilter(filter);
            assertThat(converted.getDescription())
                .as("description unchanged when no paren is present")
                .isEqualTo("Plain Description");
        }

        @Test
        void testDescriptionUnchangedWhenParenAtIndexZero() {
            // parenIndex == 0 is not > 0, so description must be returned as-is
            var filter = new ExtensionFileFilter("Files", "mssw") {
                @Override
                public String getDescription() {
                    return "(mssw)";
                }
            };
            var converted = PlatformFileDialog.convertFilter(filter);
            assertThat(converted.getDescription())
                .as("description unchanged when '(' is at index 0")
                .isEqualTo("(mssw)");
        }
    }

    // ---- getFileFilter ----

    @Nested
    class GetFileFilter {

        @Test
        void testExtensionMatchTakesPriorityOverDropdown() {
            var mssw = new ExtensionFileFilter("MSSW Files", "mssw");
            var xml = new ExtensionFileFilter("XML Files", "xml");
            var filters = new ExtensionFileFilter[] { mssw, xml };

            try (var construction = mockConstruction(SystemFileChooser.class)) {
                var dialog = new PlatformFileDialog(DUMMY_FRAME, "title", true, filters, 0);
                var chooserMock = construction.constructed().get(0);

                // File has .mssw extension: extension-based match should win
                when(chooserMock.getSelectedFile()).thenReturn(new File("song.mssw"));
                // Dropdown reports XML Files — but extension match should take priority
                when(chooserMock.getFileFilter()).thenReturn(
                    new SystemFileChooser.FileNameExtensionFilter("XML Files", "xml")
                );

                assertThat(dialog.getFileFilter())
                    .as("extension-based match takes priority over dropdown selection")
                    .isSameAs(mssw);
            }
        }

        @Test
        void testDropdownMatchUsedWhenNoExtensionMatch() {
            var mssw = new ExtensionFileFilter("MSSW Files", "mssw");
            var xml = new ExtensionFileFilter("XML Files", "xml");
            var filters = new ExtensionFileFilter[] { mssw, xml };

            try (var construction = mockConstruction(SystemFileChooser.class)) {
                var dialog = new PlatformFileDialog(DUMMY_FRAME, "title", true, filters, 0);
                var chooserMock = construction.constructed().get(0);

                // No selected file: fall back to dropdown
                when(chooserMock.getSelectedFile()).thenReturn(null);
                // Dropdown reports "XML Files" — description matches convertedFilters[1]
                when(chooserMock.getFileFilter()).thenReturn(
                    new SystemFileChooser.FileNameExtensionFilter("XML Files", "xml")
                );

                assertThat(dialog.getFileFilter())
                    .as("dropdown description match returns matching original filter")
                    .isSameAs(xml);
            }
        }

        @Test
        void testFallsBackToFirstFilterWhenNeitherMatches() {
            var mssw = new ExtensionFileFilter("MSSW Files", "mssw");
            var xml = new ExtensionFileFilter("XML Files", "xml");
            var filters = new ExtensionFileFilter[] { mssw, xml };

            try (var construction = mockConstruction(SystemFileChooser.class)) {
                var dialog = new PlatformFileDialog(DUMMY_FRAME, "title", true, filters, 0);
                var chooserMock = construction.constructed().get(0);

                // No file, dropdown returns unrecognized filter
                when(chooserMock.getSelectedFile()).thenReturn(null);
                when(chooserMock.getFileFilter()).thenReturn(
                    new SystemFileChooser.FileNameExtensionFilter("Unknown", "unk")
                );

                assertThat(dialog.getFileFilter())
                    .as("fallback to first filter when no extension or dropdown match")
                    .isSameAs(mssw);
            }
        }
    }

    // ---- showSaveDialog ----

    @Nested
    class ShowSaveDialog {

        @Test
        void testReturnsNullOnCancel() {
            try (var ignored = mockConstruction(SystemFileChooser.class,
                    (mock, ctx) -> when(mock.showSaveDialog(any()))
                        .thenReturn(SystemFileChooser.CANCEL_OPTION))) {

                var result = PlatformFileDialog.showSaveDialog(
                    DUMMY_FRAME, "Save", "MSSW Files", "song", "mssw"
                );
                assertThat(result).as("null returned when user cancels").isNull();
            }
        }

        @Test
        void testReturnsFileUnchangedWhenExtensionAlreadyMatches() {
            try (var ignored = mockConstruction(SystemFileChooser.class,
                    (mock, ctx) -> {
                        when(mock.showSaveDialog(any())).thenReturn(SystemFileChooser.APPROVE_OPTION);
                        when(mock.getSelectedFile()).thenReturn(new File("/tmp/song.mssw"));
                    })) {

                var result = PlatformFileDialog.showSaveDialog(
                    DUMMY_FRAME, "Save", "MSSW Files", "song", "mssw"
                );
                assertThat(result)
                    .as("file returned unchanged when extension already matches")
                    .isNotNull()
                    .satisfies(f -> assertThat(f.getName())
                        .as("filename unchanged when extension already matches")
                        .isEqualTo("song.mssw"));
            }
        }

        @Test
        void testAppendsExtensionWhenMissing() {
            try (var ignored = mockConstruction(SystemFileChooser.class,
                    (mock, ctx) -> {
                        when(mock.showSaveDialog(any())).thenReturn(SystemFileChooser.APPROVE_OPTION);
                        when(mock.getSelectedFile()).thenReturn(new File("/tmp/song"));
                    })) {

                var result = PlatformFileDialog.showSaveDialog(
                    DUMMY_FRAME, "Save", "MSSW Files", "song", "mssw"
                );
                assertThat(result)
                    .as("extension appended when missing")
                    .isNotNull()
                    .satisfies(f -> assertThat(f.getName())
                        .as("appended extension is the first extension")
                        .isEqualTo("song.mssw"));
            }
        }

        @Test
        void testAppendsFirstExtensionWhenMultipleGivenAndNoneMatch() {
            // When the file has no matching extension and multiple are offered,
            // the first extension in the array is appended
            try (var ignored = mockConstruction(SystemFileChooser.class,
                    (mock, ctx) -> {
                        when(mock.showSaveDialog(any())).thenReturn(SystemFileChooser.APPROVE_OPTION);
                        when(mock.getSelectedFile()).thenReturn(new File("/tmp/song"));
                    })) {

                var result = PlatformFileDialog.showSaveDialog(
                    DUMMY_FRAME, "Save", "Music Files", "song", "mssw", "xml"
                );
                assertThat(result)
                    .as("result non-null with multi-extension array")
                    .isNotNull()
                    .satisfies(f -> assertThat(f.getName())
                        .as("first extension appended when file has no recognized extension")
                        .isEqualTo("song.mssw"));
            }
        }

    }

    // ---- constructor initialFilterIndex clamping ----

    @Nested
    class ConstructorIndexClamping {

        @Test
        void testNegativeIndexClampedToZero() {
            var mssw = new ExtensionFileFilter("MSSW Files", "mssw");
            var xml = new ExtensionFileFilter("XML Files", "xml");
            var filters = new ExtensionFileFilter[] { mssw, xml };

            try (var construction = mockConstruction(SystemFileChooser.class)) {
                new PlatformFileDialog(DUMMY_FRAME, "title", true, filters, -1);
                var chooserMock = construction.constructed().get(0);

                // setFileFilter should be called with the filter at index 0 (clamped from -1)
                verify(chooserMock).setFileFilter(
                    argThat(f -> f != null && "MSSW Files".equals(f.getDescription()))
                );
            }
        }

        @Test
        void testOverLengthIndexClampedToLast() {
            var mssw = new ExtensionFileFilter("MSSW Files", "mssw");
            var xml = new ExtensionFileFilter("XML Files", "xml");
            var filters = new ExtensionFileFilter[] { mssw, xml };

            try (var construction = mockConstruction(SystemFileChooser.class)) {
                new PlatformFileDialog(DUMMY_FRAME, "title", true, filters, 99);
                var chooserMock = construction.constructed().get(0);

                // setFileFilter should be called with the filter at index 1 (clamped from 99)
                verify(chooserMock).setFileFilter(
                    argThat(f -> f != null && "XML Files".equals(f.getDescription()))
                );
            }
        }

        @Test
        void testValidIndexIsUsedDirectly() {
            var mssw = new ExtensionFileFilter("MSSW Files", "mssw");
            var xml = new ExtensionFileFilter("XML Files", "xml");
            var filters = new ExtensionFileFilter[] { mssw, xml };

            try (var construction = mockConstruction(SystemFileChooser.class)) {
                new PlatformFileDialog(DUMMY_FRAME, "title", true, filters, 1);
                var chooserMock = construction.constructed().get(0);

                verify(chooserMock).setFileFilter(
                    argThat(f -> f != null && "XML Files".equals(f.getDescription()))
                );
            }
        }
    }
}
