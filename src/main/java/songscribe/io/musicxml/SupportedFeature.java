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

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.Supports;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;

/**
 * The features a document declares, one {@code <supports type="yes">} entry each. The
 * writer emits every constant; the reader honors a feature only when the document
 * declares it, which is how an older document is read as its own writer meant it.
 *
 * <p>Written and recognized form are one definition here, so the writer cannot emit an
 * entry the reader does not recognize, nor the reader gate on one the writer never
 * emits. Adding a feature is adding a constant.
 */
enum SupportedFeature {

    /** The writer emits every {@code <accidental>} a note needs. Write-forward. */
    ACCIDENTAL(MusicXmlTags.SUPPORTS_ACCIDENTAL, null),

    /** The writer emits every {@code <beam>}. Write-forward. */
    BEAM(MusicXmlTags.SUPPORTS_BEAM, null),

    /** The writer emits every {@code <stem>}. Write-forward. */
    STEM(MusicXmlTags.SUPPORTS_STEM, null),

    /**
     * {@code <note relative-x>} is the user's own horizontal offset. A document without
     * this feature wrote its layout position there, which the reader must not take for an
     * offset.
     */
    NOTE_RELATIVE_X(MusicXmlTags.NOTE, MusicXmlTags.ATTR_RELATIVE_X);

    /** The {@code element} token of the entry. */
    final String element;

    /** The {@code attribute} token of the entry, or {@code null} when the feature is a whole element. */
    final @Nullable String attribute;

    SupportedFeature(String element, @Nullable String attribute) {
        this.element = element;
        this.attribute = attribute;
    }

    /**
     * The features {@code score} declares. Absent {@code <encoding>}, an entry naming a
     * different element or attribute, or {@code type="no"} all leave a feature out.
     */
    static Set<SupportedFeature> declaredIn(ScorePartwise score) {
        var declared = EnumSet.noneOf(SupportedFeature.class);
        var encoding = ProxyMusicAccess.encoding(score);

        if (encoding == null) {
            return declared;
        }

        for (var supports : ProxyMusicAccess.jaxbValues(
                encoding.getEncodingDateOrEncoderOrSoftware(), MusicXmlTags.SUPPORTS, Supports.class)) {
            for (var feature : values()) {
                if (feature.matches(supports)) {
                    declared.add(feature);
                }
            }
        }

        return declared;
    }

    private boolean matches(Supports supports) {
        return element.equals(supports.getElement())
            && Objects.equals(attribute, supports.getAttribute())
            && supports.getType() == YesNo.YES;
    }
}
