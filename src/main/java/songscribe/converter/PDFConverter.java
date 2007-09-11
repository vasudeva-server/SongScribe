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
package songscribe.converter;

import java.io.File;
import java.io.IOException;

import songscribe.data.PageLayoutData;
import songscribe.ui.action.ExportPDFAction;
import songscribe.ui.component.Score;
import songscribe.util.FileUtils;
import songscribe.util.Log;

@SuppressWarnings({ "ConstantValue", "FieldMayBeStatic" })
public class PDFConverter {

    @ArgumentDescribe("Paper size: a4, letter, legal, custom")
    @NoDefault
    public String paperSize = null;

    @ArgumentDescribe(
        "Paper width in 100ths of an inch, ignored if paperSize is not 'custom'"
    )
    @NoDefault
    public int paperWidth = -1;

    @ArgumentDescribe(
        "Paper height in 100ths of an inch, ignored if paperSize is not 'custom'"
    )
    @NoDefault
    public int paperHeight = -1;

    @ArgumentDescribe("Export PDF without lyrics under the song")
    public final boolean withoutLyrics = false;

    @ArgumentDescribe("Export PDF without song title")
    public final boolean withoutSongTitle = false;

    @ArgumentDescribe("Margin around the PDF in pixels")
    public int margin = 0;

    @ArgumentDescribe(
        "Top margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public static final int topMargin = -1;

    @ArgumentDescribe(
        "Left margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public static final int leftMargin = -1;

    @ArgumentDescribe(
        "Bottom margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public static final int bottomMargin = -1;

    @ArgumentDescribe(
        "Right margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public static final int rightMargin = -1;

    @FileArgument
    public File[] files = null;

    public static void main(String[] args) {
        Log.setNameWithoutExtension("pdf-converter");
        var reader = new ArgumentReader<>(args, PDFConverter.class);
        reader.getObj().convert();
    }

    public void convert() {
        paperSize = paperSize.toLowerCase();

        switch (paperSize) {
            case "a4" -> {
                paperWidth = 827;
                paperHeight = 1169;
            }
            case "letter" -> {
                paperWidth = 850;
                paperHeight = 1100;
            }
            case "legal" -> {
                paperWidth = 850;
                paperHeight = 1400;
            }
            case "custom" -> {
                if ((paperWidth <= 0) || (paperHeight <= 0)) {
                    System.out.println(
                        "paperWidth and paperHeight must be specified for custom paperSize"
                    );
                    return;
                }
            }
            default -> {
                System.out.println("invalid paperSize");
                return;
            }
        }

        var mainFrame = new ConverterMainFrame();
        var score = new Score(mainFrame);
        mainFrame.setScore(score);

        var data = new PageLayoutData();
        data.paperWidth = paperWidth;
        data.paperHeight = paperHeight;
        data.topMargin = 75;
        data.bottomMargin = 75;
        data.leftInnerMargin = 75;
        data.rightOuterMargin = 75;
        data.mainFrame = mainFrame;

        if (topMargin > -1) {
            data.topMargin = topMargin;
        }

        if (leftMargin > -1) {
            data.leftInnerMargin = leftMargin;
        }

        if (bottomMargin > -1) {
            data.bottomMargin = bottomMargin;
        }

        if (rightMargin > -1) {
            data.rightOuterMargin = rightMargin;
        }

        for (var file : files) {
            var composition = Converter.getCompositionFromFile(
                file,
                mainFrame,
                withoutLyrics,
                withoutSongTitle,
                false
            );

            try {
                var path = FileUtils.getPathWithoutExtension(file) + ".pdf";
                ExportPDFAction.createPDF(data, new File(path), false);
            } catch (IOException e) {
                System.out.println("Could not convert " + file.getName());
            }
        }
    }
}
