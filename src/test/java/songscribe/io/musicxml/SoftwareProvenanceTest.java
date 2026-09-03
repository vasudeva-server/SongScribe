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

import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class SoftwareProvenanceTest extends UnitTest {

    private record RejectionCase(@Nullable String software, Class<? extends Exception> expectedExceptionType) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedSoftwareValues")
    void testCheckAcceptsSongScribeAtOrAboveMinimumVersion(String software) {
        assertThatCode(() -> SoftwareProvenance.check(software)).doesNotThrowAnyException();
    }

    static Stream<String> acceptedSoftwareValues() {
        return Stream.of(SoftwareProvenance.SOFTWARE, "SongScribe 2.0.0", "SongScribe 3.0.0");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectionCases")
    void testCheckRejectsWithExpectedException(RejectionCase testCase) {
        assertThatExceptionOfType(testCase.expectedExceptionType())
            .isThrownBy(() -> SoftwareProvenance.check(testCase.software()));
    }

    static Stream<RejectionCase> rejectionCases() {
        return Stream.of(
            new RejectionCase(null, MusicXmlReader.ForeignSoftwareException.class),
            new RejectionCase("", MusicXmlReader.ForeignSoftwareException.class),
            new RejectionCase("MuseScore 4.0", MusicXmlReader.ForeignSoftwareException.class),
            new RejectionCase("SongScribeMax 1.0", MusicXmlReader.ForeignSoftwareException.class),
            new RejectionCase("SongScribe 2.x", MusicXmlReader.UnsupportedVersionException.class),
            new RejectionCase("SongScribe 1.9.9", MusicXmlReader.UnsupportedVersionException.class)
        );
    }
}
