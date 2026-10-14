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

package songscribe.smufl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import songscribe.error.RuntimeError;

/**
 * What the music font declares about its glyphs, in staff spaces with Y-down (screen)
 * convention.
 *
 * <p>Every query is total: a font that cannot answer one shows a fatal error dialog and
 * terminates the application. Nothing here may therefore be queried before the application can
 * show a dialog, including from a static initializer.
 */
public final class SMuFLMetadata {

    private static final String METADATA_RESOURCE = "/fonts/bravura_metadata.json";

    private final BBox[] bboxesSs;

    private final double[] advanceWidthsSs;

    private final StemAnchors[] stemAnchors;

    private final double stemThicknessSs;

    private SMuFLMetadata(JsonObject root) {
        bboxesSs = parseBBoxes(
                requiredObject(root, "glyphBBoxes", "glyph bounding boxes"));
        advanceWidthsSs = parseAdvanceWidths(
                requiredObject(root, "glyphAdvanceWidths", "glyph advance widths"));
        stemAnchors = parseStemAnchors(
                requiredObject(root, "glyphsWithAnchors", "glyph anchors"));
        stemThicknessSs = requiredDouble(
                requiredObject(root, "engravingDefaults", "engraving defaults"),
                "stemThickness", "stem thickness");
    }

    public static BBox bboxSs(SMuFLGlyph glyph) {
        return Holder.INSTANCE.bboxesSs[glyph.ordinal()];
    }

    /**
     * The measure for laying glyphs out in sequence, not of where a glyph's ink ends.
     */
    public static double advanceWidthSs(SMuFLGlyph glyph) {
        return Holder.INSTANCE.advanceWidthsSs[glyph.ordinal()];
    }

    public static StemAnchors stemAnchors(StemmedNotehead notehead) {
        return Holder.INSTANCE.stemAnchors[notehead.ordinal()];
    }

    /**
     * The notehead anchors and flags are cut for a stem of this width; a wider stem reaches past
     * the anchor into the notehead's counter.
     */
    public static double stemThicknessSs() {
        return Holder.INSTANCE.stemThicknessSs;
    }

    private static BBox[] parseBBoxes(JsonObject obj) {
        var glyphs = SMuFLGlyph.values();
        var boxesSs = new BBox[glyphs.length];

        for (var glyph : glyphs) {
            var glyphName = glyph.smuflName();
            var entry = requiredObject(obj, glyphName, "bounding box for " + glyphName);
            var sw = requiredCorner(entry, "bBoxSW", glyphName);
            var ne = requiredCorner(entry, "bBoxNE", glyphName);
            boxesSs[glyph.ordinal()] = BBox.fromSMuFL(
                    sw.get(0).getAsDouble(), sw.get(1).getAsDouble(),
                    ne.get(0).getAsDouble(), ne.get(1).getAsDouble());
        }

        return boxesSs;
    }

    private static double[] parseAdvanceWidths(JsonObject obj) {
        var glyphs = SMuFLGlyph.values();
        var widthsSs = new double[glyphs.length];

        for (var glyph : glyphs) {
            var glyphName = glyph.smuflName();
            widthsSs[glyph.ordinal()] =
                    requiredDouble(obj, glyphName, "advance width for " + glyphName);
        }

        return widthsSs;
    }

    private static StemAnchors[] parseStemAnchors(JsonObject obj) {
        var noteheads = StemmedNotehead.values();
        var anchors = new StemAnchors[noteheads.length];

        for (var notehead : noteheads) {
            var glyphName = notehead.glyph().smuflName();
            var entry = requiredObject(obj, glyphName, "anchors for " + glyphName);
            anchors[notehead.ordinal()] = new StemAnchors(
                    requiredAnchor(entry, "stemUpSE", glyphName),
                    requiredAnchor(entry, "stemDownNW", glyphName));
        }

        return anchors;
    }

    private static Anchor requiredAnchor(JsonObject entry, String key, String glyphName) {
        var coordinates = requiredArray(entry, key, key + " anchor for " + glyphName);
        return Anchor.fromSMuFL(coordinates.get(0).getAsDouble(), coordinates.get(1).getAsDouble());
    }

    private static JsonArray requiredCorner(JsonObject entry, String key, String glyphName) {
        return requiredArray(entry, key, key + " corner of the bounding box for " + glyphName);
    }

    private static JsonArray requiredArray(JsonObject obj, String key, String what) {
        var entry = obj.getAsJsonArray(key);

        if (entry == null) {
            throw missingMetadata(what);
        }

        return entry;
    }

    private static JsonObject requiredObject(JsonObject obj, String key, String what) {
        var entry = obj.getAsJsonObject(key);

        if (entry == null) {
            throw missingMetadata(what);
        }

        return entry;
    }

    private static double requiredDouble(JsonObject obj, String key, String what) {
        var value = obj.get(key);

        if (value == null) {
            throw missingMetadata(what);
        }

        return value.getAsDouble();
    }

    private static RuntimeException missingMetadata(String what) {
        return RuntimeError.missingResource(METADATA_RESOURCE + " declares no " + what);
    }

    private static final class Holder {
        static final SMuFLMetadata INSTANCE = load();

        private static SMuFLMetadata load() {
            var stream = SMuFLMetadata.class.getResourceAsStream(METADATA_RESOURCE);

            if (stream == null) {
                throw RuntimeError.missingResource(METADATA_RESOURCE + " is not on the classpath");
            }

            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                return new SMuFLMetadata(root);
            } catch (IOException e) {
                throw RuntimeError.missingResource(METADATA_RESOURCE + " could not be read", e);
            }
        }
    }
}
