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
package songscribe.io.musicxml;

import java.util.function.Consumer;
import java.util.stream.Stream;

import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which {@code <supports>} declarations {@link SupportedFeature#declaredIn} takes as
 * {@link SupportedFeature#NOTE_RELATIVE_X}: only an entry naming exactly that element and
 * attribute with {@code type="yes"}.
 */
class SupportedFeatureTest extends UnitTest {

    private static final ObjectFactory FACTORY = new ObjectFactory();

    private record SupportsCase(String description, Consumer<ScorePartwise> declare, boolean expectNoteRelativeX) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportsCases")
    void testNoteRelativeXFollowsTheDeclaration(SupportsCase testCase) {
        var score = FACTORY.createScorePartwise();
        testCase.declare().accept(score);

        assertThat(SupportedFeature.declaredIn(score).contains(SupportedFeature.NOTE_RELATIVE_X))
            .isEqualTo(testCase.expectNoteRelativeX());
    }

    static Stream<SupportsCase> supportsCases() {
        return Stream.of(
            new SupportsCase("no encoding block", score -> {}, false),
            new SupportsCase("entry names the element but no attribute",
                score -> declare(score, MusicXmlTags.NOTE, null, YesNo.YES), false),
            new SupportsCase("entry names the element but another attribute",
                score -> declare(score, MusicXmlTags.NOTE, MusicXmlTags.ATTR_DEFAULT_X, YesNo.YES), false),
            new SupportsCase("entry names the attribute but another element",
                score -> declare(score, MusicXmlTags.SUPPORTS_BEAM, MusicXmlTags.ATTR_RELATIVE_X, YesNo.YES), false),
            new SupportsCase("exact entry with type no",
                score -> declare(score, MusicXmlTags.NOTE, MusicXmlTags.ATTR_RELATIVE_X, YesNo.NO), false),
            new SupportsCase("exact entry with type yes",
                score -> declare(score, MusicXmlTags.NOTE, MusicXmlTags.ATTR_RELATIVE_X, YesNo.YES), true)
        );
    }

    private static void declare(ScorePartwise score, String element, @Nullable String attribute, YesNo type) {
        var supports = FACTORY.createSupports();
        supports.setElement(element);
        supports.setAttribute(attribute);
        supports.setType(type);

        var encoding = FACTORY.createEncoding();
        encoding.getEncodingDateOrEncoderOrSoftware().add(FACTORY.createEncodingSupports(supports));

        var identification = FACTORY.createIdentification();
        identification.setEncoding(encoding);
        score.setIdentification(identification);
    }
}
