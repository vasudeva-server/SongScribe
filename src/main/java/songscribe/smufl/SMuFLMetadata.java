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

    @SuppressWarnings("SameReturnValue")
    private static SMuFLMetadata instance() {
        return Holder.INSTANCE;
    }

    /** Width of the standard notehead (noteheadBlack) in staff spaces. */
    public static double noteHeadWidthSs() {
        return requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).width();
    }

    /** Height of the standard notehead (noteheadBlack) in staff spaces. */
    public static double noteHeadHeightSs() {
        return requireBBox(SMuFLGlyph.NOTEHEAD_BLACK).height();
    }

    public static SMuFLData getEngravingDefaults() {
        return instance().engravingDefaults;
    }

    /**
     * Returns the bounding box for a glyph, or null if not present in metadata.
     */
    @Nullable
    public static BBox getBBox(SMuFLGlyph glyph) {
        return instance().bboxes.get(glyph);
    }

    /**
     * Returns the anchor points for a glyph, or null if not present in metadata.
     */
    @Nullable
    public static GlyphAnchors getAnchors(SMuFLGlyph glyph) {
        return instance().anchors.get(glyph);
    }

    /**
     * Returns the glyph's advance width in staff spaces, or 0 if the font has no metadata for it.
     *
     * @return the advance width in staff spaces, or 0 when the font declares none for this glyph
     */
    public static double getAdvanceWidthOrZero(SMuFLGlyph glyph) {
        var width = instance().advanceWidths.get(glyph);

        return width != null ? width : 0.0;
    }

    /**
     * Returns the bounding box for a glyph, exiting fatally if not present.
     * Use for well-known glyphs whose metadata is guaranteed by the font.
     */
    public static BBox requireBBox(SMuFLGlyph glyph) {
        return requireMapValue(instance().bboxes, glyph, "bounding box");
    }

    /**
     * Returns the anchor points for a glyph, exiting fatally if not present.
     * Use for well-known glyphs whose metadata is guaranteed by the font.
     */
    public static GlyphAnchors requireAnchors(SMuFLGlyph glyph) {
        return requireMapValue(instance().anchors, glyph, "anchors");
    }

    /**
     * Returns the advance width for a glyph, exiting fatally if not present.
     * Use for well-known glyphs whose metadata is guaranteed by the font.
     */
    public static double requireAdvanceWidth(SMuFLGlyph glyph) {
        return requireMapValue(instance().advanceWidths, glyph, "advance width");
    }

    /**
     * Returns the value {@code map} holds for {@code glyph}, exiting fatally if absent.
     *
     * <p>{@code map} is expected to hold an entry for every {@link SMuFLGlyph} the font
     * declares, so a miss is a metadata defect rather than a recoverable condition.
     * {@link #requireBBox}, {@link #requireAnchors} and {@link #requireAdvanceWidth} are
     * the instance-bound convenience wrappers; a test reaches this directly with a
     * caller-supplied map to exercise the fail-loud path without needing a real glyph
     * absent from Bravura metadata.
     *
     * @param map         the metadata map to look up {@code glyph} in
     * @param glyph       the glyph to look up
     * @param description the human-readable name of what {@code map} holds, used only in
     *                     the exit message when {@code glyph} is absent
     * @return the value {@code map} holds for {@code glyph}; never null
     * @throws RuntimeException (via {@link RuntimeError#missingResource}) if {@code glyph}
     *     has no entry in {@code map}
     */
    static <V> V requireMapValue(Map<SMuFLGlyph, V> map, SMuFLGlyph glyph, String description) {
        var result = map.get(glyph);

        if (result == null) {
            throw RuntimeError.missingResource("missing " + description + " for glyph: " + glyph);
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
