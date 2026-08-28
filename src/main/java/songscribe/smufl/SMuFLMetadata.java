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
 * <p>This is the boundary at which font metadata becomes application data, and it converts
 * rather than checks: every query below is total, because a font that cannot answer one of
 * them fails the application then, naming what it could not answer. No caller downstream can
 * hold a glyph whose measurements are unknown, so none has to ask whether they are.
 *
 * <p>The first query made reads the font's metadata, and a font that cannot be read or cannot
 * answer shows a fatal error dialog and terminates the application. Nothing here may
 * therefore be queried before the application is able to show a dialog, and that reaches
 * further than it looks: a query from a class's static initializer runs whenever that class
 * is first touched, so the classes holding glyph constants and rendering shapes carry the
 * same restriction.
 */
public final class SMuFLMetadata {

    private static final String METADATA_RESOURCE = "/fonts/bravura_metadata.json";

    private final EngravingDefaults engravingDefaults;

    /** Indexed by {@link SMuFLGlyph#ordinal()}. */
    private final BBox[] bboxesSs;

    /** Indexed by {@link SMuFLGlyph#ordinal()}. */
    private final double[] advanceWidthsSs;

    /** Indexed by {@link StemmedNotehead#ordinal()}. */
    private final StemAnchors[] stemAnchors;

    private SMuFLMetadata(JsonObject root) {
        engravingDefaults = parseEngravingDefaults(
                requiredObject(root, "engravingDefaults", "engraving defaults"));
        bboxesSs = parseBBoxes(
                requiredObject(root, "glyphBBoxes", "glyph bounding boxes"));
        advanceWidthsSs = parseAdvanceWidths(
                requiredObject(root, "glyphAdvanceWidths", "glyph advance widths"));
        stemAnchors = parseStemAnchors(
                requiredObject(root, "glyphsWithAnchors", "glyph anchors"));
    }

    /** @return The engraving measurements the font sets. */
    public static EngravingDefaults engravingDefaults() {
        return Holder.INSTANCE.engravingDefaults;
    }

    /**
     * The box the glyph's ink occupies, relative to the pen origin it draws from.
     *
     * @param glyph the glyph to measure
     * @return its ink box in staff spaces
     */
    public static BBox bboxSs(SMuFLGlyph glyph) {
        return Holder.INSTANCE.bboxesSs[glyph.ordinal()];
    }

    /**
     * How far the pen moves after drawing the glyph. This differs from the ink box wherever
     * the font pads a glyph or designs it to overlap what follows, so it is the measure for
     * laying glyphs out in sequence and not the measure of where a glyph's ink ends.
     *
     * @param glyph the glyph to measure
     * @return its advance width in staff spaces
     */
    public static double advanceWidthSs(SMuFLGlyph glyph) {
        return Holder.INSTANCE.advanceWidthsSs[glyph.ordinal()];
    }

    /**
     * Where a stem meets this notehead, in each direction.
     *
     * @param notehead the notehead a stem attaches to
     * @return both of its stem attachment points
     */
    public static StemAnchors stemAnchors(StemmedNotehead notehead) {
        return Holder.INSTANCE.stemAnchors[notehead.ordinal()];
    }

    // --- Parsing ---

    private static EngravingDefaults parseEngravingDefaults(JsonObject obj) {
        return new EngravingDefaults(
                engravingDefault(obj, "repeatBarlineDotSeparation"),
                engravingDefault(obj, "legerLineThickness"),
                engravingDefault(obj, "tieMidpointThickness")
        );
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

    private static double engravingDefault(JsonObject obj, String key) {
        return requiredDouble(obj, key, "engraving default " + key);
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
