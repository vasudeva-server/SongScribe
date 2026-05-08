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
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;

/**
 * Lazy singleton that parses bravura_metadata.json and provides typed access
 * to glyph bounding boxes, anchor points, advance widths, and engraving defaults.
 * All spatial values are in staff spaces with Y-down (screen) convention.
 */
public final class SMuFLMetadata {

    private static final String METADATA_RESOURCE = "/fonts/bravura_metadata.json";

    private final SMuFLData engravingDefaults;
    private final Map<SMuFLGlyph, BBox> bboxes;
    private final Map<SMuFLGlyph, GlyphAnchors> anchors;
    private final Map<SMuFLGlyph, Double> advanceWidths;

    private SMuFLMetadata(JsonObject root) {
        engravingDefaults = parseEngravingDefaults(root.getAsJsonObject("engravingDefaults"));
        bboxes = parseBBoxes(root.getAsJsonObject("glyphBBoxes"));
        anchors = parseAnchors(root.getAsJsonObject("glyphsWithAnchors"));
        advanceWidths = parseAdvanceWidths(root.getAsJsonObject("glyphAdvanceWidths"));
    }

    /** Returns the singleton instance, loading metadata on first access. */
    public static SMuFLMetadata getInstance() {
        return Holder.INSTANCE;
    }

    /** Width of the standard notehead (noteheadBlack) in staff spaces. */
    public double noteHeadWidthSs() {
        return requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).width();
    }

    /** Height of the standard notehead (noteheadBlack) in staff spaces. */
    public double noteHeadHeightSs() {
        return requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).height();
    }

    SMuFLData getEngravingDefaults() {
        return engravingDefaults;
    }

    /**
     * Returns the bounding box for a glyph, or null if not present in metadata.
     */
    @Nullable
    public BBox getBBox(SMuFLGlyph glyph) {
        return bboxes.get(glyph);
    }

    /**
     * Returns the anchor points for a glyph, or null if not present in metadata.
     */
    @Nullable
    public GlyphAnchors getAnchors(SMuFLGlyph glyph) {
        return anchors.get(glyph);
    }

    /**
     * Returns the advance width for a glyph, or null if not present in metadata.
     */
    @Nullable
    public Double getAdvanceWidth(SMuFLGlyph glyph) {
        return advanceWidths.get(glyph);
    }

    /**
     * Returns the bounding box for a glyph, exiting fatally if not present.
     * Use for well-known glyphs whose metadata is guaranteed by the font.
     */
    public BBox requireBBox(SMuFLGlyph glyph) {
        return requireMapValue(bboxes, glyph, "bounding box");
    }

    /**
     * Returns the anchor points for a glyph, exiting fatally if not present.
     * Use for well-known glyphs whose metadata is guaranteed by the font.
     */
    public GlyphAnchors requireAnchors(SMuFLGlyph glyph) {
        return requireMapValue(anchors, glyph, "anchors");
    }

    /**
     * Returns the advance width for a glyph, exiting fatally if not present.
     * Use for well-known glyphs whose metadata is guaranteed by the font.
     */
    public double requireAdvanceWidth(SMuFLGlyph glyph) {
        return requireMapValue(advanceWidths, glyph, "advance width");
    }

    private static <V> V requireMapValue(Map<SMuFLGlyph, V> map, SMuFLGlyph glyph, String description) {
        var result = map.get(glyph);

        if (result == null) {
            throw RuntimeError.exit("missing " + description + " for glyph: " + glyph);
        }

        return result;
    }


    // --- Parsing ---

    private static SMuFLData parseEngravingDefaults(JsonObject obj) {
        return new SMuFLData(
                obj.get("beamThickness").getAsDouble(),
                obj.get("beamSpacing").getAsDouble(),
                obj.get("repeatBarlineDotSeparation").getAsDouble(),
                obj.get("legerLineThickness").getAsDouble(),
                obj.get("legerLineExtension").getAsDouble(),
                obj.get("tieMidpointThickness").getAsDouble()
        );
    }

    private static Map<SMuFLGlyph, BBox> parseBBoxes(JsonObject obj) {
        var map = new EnumMap<SMuFLGlyph, BBox>(SMuFLGlyph.class);

        for (var glyph : SMuFLGlyph.values()) {
            var entry = obj.getAsJsonObject(glyph.smuflName());

            if (entry != null) {
                var ne = entry.getAsJsonArray("bBoxNE");
                var sw = entry.getAsJsonArray("bBoxSW");
                map.put(glyph, BBox.fromSMuFL(
                        sw.get(0).getAsDouble(), sw.get(1).getAsDouble(),
                        ne.get(0).getAsDouble(), ne.get(1).getAsDouble()
                ));
            }
        }

        return map;
    }

    private static Map<SMuFLGlyph, GlyphAnchors> parseAnchors(JsonObject obj) {
        var map = new EnumMap<SMuFLGlyph, GlyphAnchors>(SMuFLGlyph.class);

        for (var glyph : SMuFLGlyph.values()) {
            var entry = obj.getAsJsonObject(glyph.smuflName());

            if (entry != null) {
                map.put(glyph, new GlyphAnchors(
                        parseAnchor(entry, "stemUpSE"),
                        parseAnchor(entry, "stemDownNW"),
                        parseAnchor(entry, "cutOutNW"),
                        parseAnchor(entry, "cutOutSE")
                ));
            }
        }

        return map;
    }

    private static GlyphAnchors.@Nullable Anchor parseAnchor(JsonObject entry, String key) {
        var arr = entry.getAsJsonArray(key);

        if (arr == null) {
            return null;
        }

        return GlyphAnchors.Anchor.fromSMuFL(arr.get(0).getAsDouble(), arr.get(1).getAsDouble());
    }

    private static Map<SMuFLGlyph, Double> parseAdvanceWidths(JsonObject obj) {
        var map = new EnumMap<SMuFLGlyph, Double>(SMuFLGlyph.class);

        for (var glyph : SMuFLGlyph.values()) {
            var width = obj.get(glyph.smuflName());

            if (width != null) {
                map.put(glyph, width.getAsDouble());
            }
        }

        return map;
    }

    private static final class Holder {
        static final SMuFLMetadata INSTANCE = load();

        private static SMuFLMetadata load() {
            var stream = SMuFLMetadata.class.getResourceAsStream(METADATA_RESOURCE);

            if (stream == null) {
                throw new RuntimeException("SMuFL metadata resource not found: " + METADATA_RESOURCE);
            }

            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                return new SMuFLMetadata(root);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load SMuFL metadata from " + METADATA_RESOURCE, e);
            }
        }
    }
}
