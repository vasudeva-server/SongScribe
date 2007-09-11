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

package songscribe.webserver;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import songscribe.converter.AbcConverter;
import songscribe.converter.PDFConverter;
import songscribe.converter.SVGConverter;

@RestController
@RequestMapping(path = "/convert")
public class WebController {

    @SuppressWarnings("OverlyBroadThrowsClause")
    @PostMapping(
        path = "/mssw-to-pdf",
        consumes = MediaType.APPLICATION_XML_VALUE,
        produces = MediaType.APPLICATION_PDF_VALUE
    )
    public byte[] msswToPdfConverter(@RequestBody String mssw)
        throws IOException {
        var pdfConverter = new PDFConverter();
        pdfConverter.paperSize = "a4";
        var file = File.createTempFile("songscribe", "pdfconverter");
        pdfConverter.files = new File[] { file };
        try (var fileWriter = new FileWriter(file)) {
            fileWriter.append(mssw);
        }
        pdfConverter.convert();
        try (
            var pdfReader = new FileInputStream(
                file.getAbsolutePath() + ".pdf"
            )
        ) {
            return pdfReader.readAllBytes();
        }
    }

    @SuppressWarnings("OverlyBroadThrowsClause")
    @PostMapping(
        path = "/mssw-to-svg",
        consumes = MediaType.APPLICATION_XML_VALUE,
        produces = "image/svg+xml"
    )
    public byte[] msswToSvgConverter(@RequestBody String mssw)
        throws IOException {
        var svgConverter = new SVGConverter();
        var file = File.createTempFile("songscribe", "svgconverter");
        svgConverter.files = new File[] { file };
        try (var fileWriter = new FileWriter(file)) {
            fileWriter.append(mssw);
        }
        svgConverter.convert();
        try (
            var svgReader = new FileInputStream(
                file.getAbsolutePath() + ".svg"
            )
        ) {
            return svgReader.readAllBytes();
        }
    }

    @PostMapping(
        path = "/mssw-to-abc",
        consumes = MediaType.APPLICATION_XML_VALUE,
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String msswToAbcConverter(@RequestBody String mssw)
        throws IOException {
        var abcConverter = new AbcConverter();
        abcConverter.file = File.createTempFile("songscribe", "abcconverter");
        try (var fileWriter = new FileWriter(abcConverter.file)) {
            fileWriter.append(mssw);
        }
        var charArrayWriter = new CharArrayWriter();
        var writer = new PrintWriter(charArrayWriter);
        abcConverter.convert(writer);

        return charArrayWriter.toString();
    }
}
