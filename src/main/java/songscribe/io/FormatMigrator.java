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
package songscribe.io;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import songscribe.dom.AnnotationAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/**
 * Migrates song data from a legacy {@code .mssw} format to the current model.
 */
public final class FormatMigrator {

    private FormatMigrator() {
    }

    public static void migrateAnnotationDynamics(List<? extends Line> lines) {
        var symbolMap = buildDynamicSymbolMap();

        for (var line : lines) {
            for (var i = 0; i < line.elementCount(); i++) {
                migrateAnnotationDynamic(line.getElement(i), symbolMap);
            }
        }
    }

    private static Map<String, DynamicAttachment.DynamicType> buildDynamicSymbolMap() {
        var map = new HashMap<String, DynamicAttachment.DynamicType>();

        for (var type : DynamicAttachment.DynamicType.values()) {
            if (type.getGlyph() != null) {
                map.put(type.getSymbol(), type);
            }
        }

        return map;
    }

    private static void migrateAnnotationDynamic(
        StaffElement note,
        Map<String, DynamicAttachment.DynamicType> symbolMap
    ) {
        var annotationAttachment = note.findAttachment(AnnotationAttachment.class);

        if (annotationAttachment == null) {
            return;
        }

        var annotation = annotationAttachment.getAnnotation();
        var dynamicType = symbolMap.get(annotation.text());

        if (dynamicType == null) {
            return;
        }

        note.removeAttachment(annotationAttachment);

        if (note.findAttachment(DynamicAttachment.class) == null) {
            note.addAttachment(new DynamicAttachment(dynamicType));
        }
    }
}
