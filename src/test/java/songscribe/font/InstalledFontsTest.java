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

package songscribe.font;

import java.util.HashSet;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * The resolution of a stored font name to an installed face.
 * <p>
 * <b>How the fallback ladder is reached.</b> The ladder is driven through the public
 * {@link InstalledFonts#createFont} using the bundled Source Sans 3 faces
 * {@link SourceSans3Font#install} registers, rather than by widening the private weight
 * mapping so a test can call it: the mapping is reachable through the published entry point,
 * and the PostScript name of the face that comes back names the step that answered.
 * <p>
 * Registration must happen before anything in the process first asks {@link InstalledFonts}
 * for the installed faces, because that read is taken once and never repeated. This class
 * registers the faces in {@code @BeforeAll}; a face registered after some other class has
 * already triggered the read would not be in the set.
 * <p>
 * <b>The fourth step is not reachable from the unit suite.</b> The look-and-feel's label font
 * answers only when no Source Sans 3 face is registered at all, and the faces are registered
 * for the life of the process — so no test that reaches step four can share a process with a
 * test that reaches steps one to three. Rather than assert something weaker that passes,
 * this class covers the first three steps and asserts nothing about the fourth.
 */
class InstalledFontsTest extends UnitTest {

    // Arbitrary; these tests assert which face answers, not the size it is derived at.
    private static final int FONT_SIZE_PT = 14;

    // Not a PostScript name of any installed face, so it always falls past the first two
    // steps of the ladder and into the Source Sans 3 approximation.
    private static final String UNREGISTERED_FAMILY = "Unregistered";

    private record SuffixCase(String description, String style, String expectedSuffix) {}

    @BeforeAll
    static void registerBundledFaces() {
        SourceSans3Font.install();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("suffixCases")
    void testUnregisteredNameResolvesToTheClosestSourceSans3Face(SuffixCase testCase) {
        var stored = UNREGISTERED_FAMILY + '-' + testCase.style();

        var font = InstalledFonts.createFont(stored, FONT_SIZE_PT);

        assertThat(font.getPSName())
            .isEqualTo(SourceSans3Font.PS_PREFIX + testCase.expectedSuffix());
    }

    static Stream<SuffixCase> suffixCases() {
        return Stream.of(
            new SuffixCase("no weight and no slant is Regular", "", "Regular"),
            new SuffixCase("italic alone is the Italic face", "Italic", "Italic"),
            new SuffixCase("medium alone is the Medium face", "Medium", "Medium"),
            new SuffixCase("medium with italic", "MediumItalic", "MediumItalic"),
            new SuffixCase("bold alone is the Bold face", "Bold", "Bold"),
            new SuffixCase("bold with italic", "BoldItalic", "BoldItalic"),
            new SuffixCase(
                "semibold outranks the bold its own name contains", "SemiBold", "SemiBold"
            ),
            new SuffixCase("semibold with italic", "SemiBoldItalic", "SemiBoldItalic"),
            new SuffixCase("semibold outranks medium", "SemiBoldMedium", "SemiBold"),
            new SuffixCase("medium outranks bold", "MediumBold", "Medium"),
            new SuffixCase("a weight outranks italic alone", "ItalicBold", "BoldItalic")
        );
    }

    @Test
    void testStoredPostScriptNameResolvesToThatExactFace() {
        var psName = SourceSans3Font.PS_PREFIX + "Bold";

        var font = InstalledFonts.createFont(psName, FONT_SIZE_PT);

        assertThat(font.getPSName()).isEqualTo(psName);
        assertThat(font.getSize()).isEqualTo(FONT_SIZE_PT);
    }

    @Test
    void testStoredFamilyNameResolvesToThatFamilyRatherThanAnApproximation() {
        var family = anInstalledFamilyThatIsNotAPostScriptName();

        if (family == null) {
            abort(
                "no installed family that is not also a PostScript name, so the family step "
                + "of the ladder cannot be told apart from the first step on this machine"
            );
            return;
        }

        var font = InstalledFonts.createFont(family, FONT_SIZE_PT);

        assertThat(font.getFamily()).isEqualTo(family);
        assertThat(font.getSize()).isEqualTo(FONT_SIZE_PT);
    }

    /*
      An installed family whose name is not also some face's PostScript name, so that
      resolving it exercises the family step rather than the PostScript step. The bundled
      family is excluded so that a miss would fall through to the Source Sans 3
      approximation and the assertion would see the difference.
    */
    private static @Nullable String anInstalledFamilyThatIsNotAPostScriptName() {
        var psNames = new HashSet<String>();

        for (var font : InstalledFonts.getAllFonts()) {
            psNames.add(font.getPSName());
        }

        for (var font : InstalledFonts.getAllFonts()) {
            var family = font.getFamily();

            if (!family.equals(SourceSans3Font.FAMILY) && !psNames.contains(family)) {
                return family;
            }
        }

        return null;
    }
}
