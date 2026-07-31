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

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.FileExtensions;
import songscribe.dom.TupletLoadPass;
import songscribe.io.musicxml.MusicXmlReader;
import songscribe.util.FileUtils;

/**
 * Routes an open request to the correct reader based on file extension.
 * <p>
 * Use this for all file-open paths (UI and headless converters) so the
 * extension allow-list and error mapping live in one place.
 */
public final class SongFileLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SongFileLoader.class);

    /** Suffix of the scratch file the in-place rewrite writes before it swaps. */
    private static final String REWRITE_TEMP_SUFFIX = ".rewrite";

    private SongFileLoader() {}

    /*
     * Open-dispatch decision tree:
     *
     *  file
     *   │
     *   ▼  SongFileLoader.load(file)
     *   │   hasExtension(file, …)  (case-insensitive)
     *   │
     *   ├─ .mssw ─────────────► SongLoader.load(file)   (unchanged legacy path)
     *   │                        └─► Success │ IoError │ ParseError │ NewerVersion
     *   │
     *   ├─ .musicxml | .xml ──► MusicXmlReader.read(file)
     *   │      startElement: root ≠ <score-partwise>            ─► UnsupportedFormatException
     *   │      startElement: version missing/unparseable/<4.0   ─► UnsupportedFormatException
     *   │      endDocument:  <software> null/blank/¬startsWith(PACKAGE_NAME) ─► ForeignSoftwareException
     *   │      (otherwise) ─► Success
     *   │      Success that migrated anything ─► rewritten in place, then silenced
     *   │      catch ForeignSoftwareException  ► WrongSoftware(file, software)
     *   │      catch UnsupportedFormatException ► UnsupportedFileFormat(file, detail)
     *   │      catch SAXException              ► ParseError(file, e)
     *   │      catch IOException               ► IoError(file, e)
     *   │
     *   └─ else (.pdf, .txt, none, …) ► UnsupportedFileFormat(file, ext)
     */
    public static SongLoadResult load(File file) {
        if (FileUtils.hasExtension(file, FileExtensions.SONGWRITER)) {
            return SongLoader.load(file);
        }

        if (FileUtils.hasExtension(file, FileExtensions.MUSICXML, FileExtensions.XML)) {
            try {
                var result = MusicXmlReader.read(file);

                if (result.tupletReport().isEmpty() && !result.accidentalsConverted()) {
                    return result;
                }

                return rewriteAndSilence(file, result);
            } catch (MusicXmlReader.ForeignSoftwareException e) {
                return new SongLoadResult.WrongSoftware(file, e.software());
            } catch (MusicXmlReader.UnsupportedFormatException e) {
                return new SongLoadResult.UnsupportedFileFormat(file, e.detail());
            } catch (SAXException e) {
                return new SongLoadResult.ParseError(file, e);
            } catch (IOException e) {
                return new SongLoadResult.IoError(file, e);
            }
        }

        return new SongLoadResult.UnsupportedFileFormat(file, FileUtils.getExtension(file.getName()));
    }

    /**
     * Brings a migrated MusicXML file up to date on disk and returns a result that reports
     * nothing, so the migration never reaches the user.
     * <p>
     * The current format can record everything the migration derived, so writing the loaded
     * song back means the next open finds nothing to migrate. Only {@code .musicxml} takes
     * this route — {@code .mssw} is legacy read-only and is reported to the user instead.
     * <p>
     * A failed rewrite is deliberately not fatal and deliberately not reported: the song in
     * memory is correct either way, the original file is untouched, and the only cost is
     * that the same migration runs again on the next open.
     */
    private static SongLoadResult.Success rewriteAndSilence(File file, SongLoadResult.Success success) {
        try {
            rewriteInPlace(file, success);
        } catch (IOException e) {
            LOG.warn("Could not rewrite migrated file '{}'; it will be migrated again on the next open",
                file.getName(), e);
        }

        return new SongLoadResult.Success(
            success.song(),
            success.fonts(),
            success.warnings(),
            false,
            TupletLoadPass.Report.empty());
    }

    /**
     * Overwrites {@code file} with the loaded song, never leaving it half-written.
     * <p>
     * The new content is written to a scratch file in the same directory and only then
     * swapped in, so a crash or a full disk mid-write loses the rewrite rather than the
     * user's song. The scratch file is a sibling because a rename is only atomic within one
     * filesystem. {@code ATOMIC_MOVE} is the swap of choice and the plain replace is the
     * fallback for filesystems that do not offer it.
     */
    private static void rewriteInPlace(File file, SongLoadResult.Success success) throws IOException {
        var target = file.toPath();
        var directory = target.toAbsolutePath().getParent();
        var scratch = Files.createTempFile(directory, file.getName(), REWRITE_TEMP_SUFFIX);

        try {
            if (!SongFileWriter.write(success.song(), success.fonts(), scratch.toFile())) {
                throw new IOException("The writer reported an error while rewriting " + target);
            }

            copyPermissions(target, scratch);
            swap(scratch, target);
        } finally {
            // A no-op once the swap has moved it; the cleanup matters on the failure paths.
            Files.deleteIfExists(scratch);
        }
    }

    /**
     * Carries the song's own permissions onto its replacement. A scratch file is created
     * readable by its owner alone, so without this a rewrite the user never asked for would
     * also quietly narrow who may read the song. Failing to copy them is not worth losing
     * the rewrite over — the content is what matters.
     */
    private static void copyPermissions(Path target, Path scratch) {
        try {
            var view = Files.getFileAttributeView(target, PosixFileAttributeView.class);

            if (view != null) {
                Files.setPosixFilePermissions(scratch, view.readAttributes().permissions());
            }
        } catch (IOException | UnsupportedOperationException e) {
            LOG.warn("Could not carry the permissions of '{}' onto its replacement", target, e);
        }
    }

    private static void swap(Path scratch, Path target) throws IOException {
        try {
            Files.move(scratch, target,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(scratch, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
