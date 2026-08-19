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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.SongScribe;
import songscribe.export.PDFExporter;
import songscribe.export.PageLayoutData;
import songscribe.ui.component.ScoreView;
import songscribe.util.FileUtils;

@SuppressWarnings({"FieldMayBeStatic" })
public class PDFConverter {

    private static final Logger LOG = LoggerFactory.getLogger(PDFConverter.class);

    private static final int PAPER_A4_WIDTH = 827;
    private static final int PAPER_A4_HEIGHT = 1169;
    private static final int PAPER_LETTER_WIDTH = 850;
    private static final int PAPER_LETTER_HEIGHT = 1100;
    private static final int PAPER_LEGAL_HEIGHT = 1400;
    private static final int DEFAULT_MARGIN = 75;

    @ArgumentDescribe("Paper size: a4, letter, legal, custom")
    @NoDefault
    public @Nullable String paperSize = null;

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
    public int topMargin = -1;

    @ArgumentDescribe(
        "Left margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public int leftMargin = -1;

    @ArgumentDescribe(
        "Bottom margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public int bottomMargin = -1;

    @ArgumentDescribe(
        "Right margin. If not present, the size of margin parameter is applied."
    )
    @NoDefault
    public int rightMargin = -1;

    @FileArgument
    public File[] files = new File[0];

    public static void main(String[] args) {
        Converter.run(args, PDFConverter.class, PDFConverter::convert);
    }

    public void convert() {
        if (paperSize == null) {
            LOG.error("No paper size specified");
            return;
        }

        var size = paperSize.toLowerCase();
        paperSize = size;

        switch (size) {
            case "a4" -> {
                paperWidth = PAPER_A4_WIDTH;
                paperHeight = PAPER_A4_HEIGHT;
            }
            case "letter" -> {
                paperWidth = PAPER_LETTER_WIDTH;
                paperHeight = PAPER_LETTER_HEIGHT;
            }
            case "legal" -> {
                paperWidth = PAPER_LETTER_WIDTH;
                paperHeight = PAPER_LEGAL_HEIGHT;
            }
            case "custom" -> {
                if ((paperWidth <= 0) || (paperHeight <= 0)) {
                    LOG.warn("paperWidth and paperHeight must be specified for custom paperSize");
                    return;
                }
            }
            default -> {
                LOG.warn("invalid paperSize");
                return;
            }
        }

        var score = new ScoreView(null);

        var data = new PageLayoutData();
        data.paperWidthPx = paperWidth;
        data.paperHeightPx = paperHeight;
        data.scoreView = score;
        data.applyMarginOverrides(DEFAULT_MARGIN, topMargin, leftMargin, bottomMargin, rightMargin);

        if (files.length == 0) {
            LOG.error("No files specified");
            return;
        }

        for (var file : files) {
            var song = Converter.loadSong(file, score);
            Converter.applyExportExclusions(song, withoutLyrics, withoutSongTitle);

            try {
                var path = FileUtils.getPathWithoutExtension(file) + ".pdf";
                PDFExporter.createPDF(data, new File(path));
                LOG.info("Converted {} to PDF (paper: {})", file.getName(), paperSize);
            } catch (IOException e) {
                LOG.error("Could not convert {}", file.getName(), e);
            }
        }
    }
}
