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
package songscribe.data;

import module java.desktop;

import java.io.File;
import java.util.logging.Logger;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Properties;

import songscribe.util.Utils;

// TODO: Delete this when Bravura rendering is complete
public final class GeneralPathFile {

    private static final Logger LOG = Logger.getLogger(GeneralPathFile.class.getName());

    private static final char fermataChar = '\uf055';

    private static final Properties valueNum = new Properties();

    static {
        valueNum.setProperty(String.valueOf(PathIterator.SEG_MOVETO), "2");
        valueNum.setProperty(String.valueOf(PathIterator.SEG_LINETO), "2");
        valueNum.setProperty(String.valueOf(PathIterator.SEG_QUADTO), "4");
        valueNum.setProperty(String.valueOf(PathIterator.SEG_CUBICTO), "6");
        valueNum.setProperty(String.valueOf(PathIterator.SEG_CLOSE), "0");
    }

    private GeneralPathFile() {}

    public static void writeGeneralPath(char ch, File file)
        throws IOException, FileNotFoundException {
        var ret = new float[6];
        var outline = getShape(ch);
        var oos = new ObjectOutputStream(new FileOutputStream(file));

        for (var pi = outline.getPathIterator(null); !pi.isDone(); pi.next()) {
            var seg = pi.currentSegment(ret);
            //noinspection UseOfPropertiesAsHashtable
            int parNum = (Integer) valueNum.get(seg);
            oos.writeInt(seg);

            for (var i = 0; i < parNum; i++) {
                oos.writeFloat(ret[i]);
            }
        }

        oos.close();
    }

    public static void printGeneralPath(char ch) {
        var outline = getShape(ch);
        printGeneralPath(outline);
    }

    private static void printGeneralPath(Shape outline) {
        var buf = new double[6];

        for (
            var iter = outline.getPathIterator(null);
            !iter.isDone();
            iter.next()
        ) {
            var segment = iter.currentSegment(buf);

            switch (segment) {
                case PathIterator.SEG_MOVETO ->
                    LOG.fine(String.format("MOVETO: (%f, %f)", buf[0], buf[1]));
                case PathIterator.SEG_LINETO ->
                    LOG.fine(String.format("LINETO: (%f, %f)", buf[0], buf[1]));
                case PathIterator.SEG_QUADTO ->
                    LOG.fine(String.format("QUADTO: (%f, %f); (%f, %f)", buf[0], buf[1], buf[2], buf[3]));
                case PathIterator.SEG_CUBICTO ->
                    LOG.fine(String.format("CUBICTO: (%f, %f); (%f, %f); (%f, %f)", buf[0], buf[1], buf[2], buf[3], buf[4], buf[5]));
                case PathIterator.SEG_CLOSE -> LOG.fine("CLOSE");
            }
        }
    }

    public static GeneralPath readGeneralPath(File file)
        throws IOException, FileNotFoundException {
        var path = new GeneralPath(Path2D.WIND_NON_ZERO);
        var stream = new ObjectInputStream(new FileInputStream(file));

        while (stream.available() > 0) {
            var segment = stream.readInt();

            switch (segment) {
                case PathIterator.SEG_MOVETO -> path.moveTo(
                    stream.readFloat(),
                    stream.readFloat()
                );
                case PathIterator.SEG_LINETO -> path.lineTo(
                    stream.readFloat(),
                    stream.readFloat()
                );
                case PathIterator.SEG_QUADTO -> path.quadTo(
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat()
                );
                case PathIterator.SEG_CUBICTO -> path.curveTo(
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat(),
                    stream.readFloat()
                );
                case PathIterator.SEG_CLOSE -> path.closePath();
            }
        }

        return path;
    }

    private static Shape getShape(char ch) {
        var maestro = new Font("Maestro", Font.PLAIN, 512);
        var f = new JFrame();
        f.pack();
        var g2 = (Graphics2D) f.getGraphics();
        // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints
        // .VALUE_TEXT_ANTIALIAS_ON);
        return maestro
            .createGlyphVector(
                g2.getFontRenderContext(),
                Character.toString(ch)
            )
            .getOutline();
    }

    public static void main(String[] args)
        throws IOException, FileNotFoundException, FileNotFoundException {
        writeGeneralPath(
            fermataChar,
            new File(Utils.getResourcePath("fonts/fermata"))
        );
        // final GeneralPath gm = readGeneralPath(new File("fm"));
        // showInWindow(FughettaDrawer.breathMark);
    }

    private static void showInWindow(GeneralPath gm) {
        var f = new JFrame();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        var comp = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                var g2 = (Graphics2D) g;
                // g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints
                // .VALUE_ANTIALIAS_ON);
                g2.translate(50, 50);
                g2.fill(gm);
                //g2.translate(50, 0);
                g2.setPaint(Color.RED);
                g2.fill(getShape('\uf02c'));
            }
        };
        comp.setPreferredSize(new Dimension(500, 500));
        f.add(comp);
        f.pack();
        f.setVisible(true);
    }
}
