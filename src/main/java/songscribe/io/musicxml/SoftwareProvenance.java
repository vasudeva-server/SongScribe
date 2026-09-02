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

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.semver4j.Semver;

import songscribe.Version;

/**
 * The {@code <software>} provenance string: the one place that spells what this
 * program writes into {@code <encoding>} and what its reader will accept there.
 *
 * <p><b>The invariant this class exists for: a document SongScribe writes passes
 * SongScribe's own provenance gate.</b> Written and recognized form are one
 * definition here — {@link #SOFTWARE} is built from {@link #PRODUCT_NAME} and
 * {@link #RECOGNIZED} matches against the same name — so neither can drift out
 * from under the other. Two separate spellings cannot promise that: reordering
 * the writer's concatenation would leave a gate that still accepted the string
 * it no longer emits.
 *
 * <p>{@code MusicXmlRoundTripSupport.roundTrip} exercises the invariant end to
 * end on every write that is read back — each of those reads runs the gate over
 * a string this class emitted — which is why no test asserts it directly.
 *
 * <p>Recognition is deliberately exact rather than a prefix match: a product
 * whose name merely begins with this one, such as {@code SongScribeMax 1.0},
 * is foreign and is refused.
 */
final class SoftwareProvenance {

    /** The product name, as it appears at the head of the {@code <software>} value. */
    static final String PRODUCT_NAME = "SongScribe";

    /**
     * The oldest version whose documents this reader accepts: the first version
     * that wrote MusicXML at all, so nothing below it was ever emitted.
     */
    static final String MIN_VERSION = "2.0.0";

    /**
     * The exact {@code <software>} value this program emits: the product name, a
     * space, and {@link Version#PUBLIC_VERSION}.
     */
    static final String SOFTWARE = PRODUCT_NAME + ' ' + Version.PUBLIC_VERSION;

    /**
     * The recognized {@code <software>} form. Capture group 1 is the version, which
     * {@link #check} parses as semver.
     */
    private static final Pattern RECOGNIZED = Pattern.compile('^' + Pattern.quote(PRODUCT_NAME) + " (.+)$");

    private SoftwareProvenance() {}

    /**
     * Admits {@code software} as this program's own provenance, or rejects it.
     *
     * @param software the document's {@code <software>} value, or {@code null} when
     *                 the document carries no {@code <software>} element
     * @invariant {@link #SOFTWARE} always passes, whatever
     *            {@link Version#PUBLIC_VERSION} says, since no released version
     *            precedes {@link #MIN_VERSION}
     * @throws MusicXmlReader.ForeignSoftwareException if {@code software} is
     *         {@code null}, blank, or does not have the form <em>product name,
     *         space, version</em> — including a product whose name merely begins
     *         with this one. The document was written by other software.
     * @throws MusicXmlReader.UnsupportedFormatException if the form matches but the
     *         version is not semver, or is lower than {@link #MIN_VERSION}. Either
     *         means a hand-edited document, since no SongScribe that wrote MusicXML
     *         emitted such a version.
     */
    static void check(@Nullable String software)
        throws MusicXmlReader.ForeignSoftwareException, MusicXmlReader.UnsupportedFormatException {
        var matcher = software == null ? null : RECOGNIZED.matcher(software);

        if (matcher == null || !matcher.matches()) {
            throw new MusicXmlReader.ForeignSoftwareException(software);
        }

        var version = matcher.group(1);
        var parsed = Semver.parse(version);

        if (parsed == null) {
            throw new MusicXmlReader.UnsupportedFormatException("unparseable version '" + version + '\'');
        }

        if (parsed.isLowerThan(MIN_VERSION)) {
            throw new MusicXmlReader.UnsupportedFormatException(
                "unsupported " + PRODUCT_NAME + " version '" + version + "'; requires " + MIN_VERSION + " or later"
            );
        }
    }
}
