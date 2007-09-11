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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;

import songscribe.util.Log;

public class ArgumentReader<T> {

    private final String[] args;
    private final Class<? extends T> klass;
    private T obj = null;
    private FileType fileType = FileType.NONE;
    private Field fileField = null;

    public ArgumentReader(String[] args, Class<? extends T> klass) {
        this.args = args;
        this.klass = klass;
    }

    public T getObj() {
        if (obj == null) {
            parseArguments();
        }

        return obj;
    }

    public String infoBuilder() {
        var sb = new StringBuilder(200);
        sb.append("Usage: [options] ");

        switch (fileType) {
            case SINGLE -> sb.append("file");
            case MANY -> sb.append("file1 [file2] [file3] ...");
        }

        sb.append('\n');

        for (var f : klass.getFields()) {
            if (f.isAnnotationPresent(ArgumentDescribe.class)) {
                sb
                    .append('-')
                    .append(f.getName())
                    .append("= ")
                    .append(f.getAnnotation(ArgumentDescribe.class).value());

                if (!f.isAnnotationPresent(NoDefault.class)) {
                    try {
                        sb.append(" (default=").append(f.get(obj)).append(')');
                    } catch (IllegalAccessException e) {
                        assert false;
                    }
                }

                sb.append('\n');
            }
        }

        return sb.toString();
    }

    public void parseArguments() {
        try {
            obj = klass.getDeclaredConstructor().newInstance();

            // Determine the file argument type
            for (var f : klass.getFields()) {
                if (f.isAnnotationPresent(FileArgument.class)) {
                    if (f.getType() == File.class) {
                        fileType = FileType.SINGLE;
                    }

                    if (f.getType() == File[].class) {
                        fileType = FileType.MANY;
                    }

                    fileField = f;
                    break;
                }
            }

            // Read the parameters
            for (var arg : args) {
                if (arg.charAt(0) != '-') {
                    break;
                }

                if (arg.equals("-?") || arg.equals("-help")) {
                    System.out.println(infoBuilder());
                    System.exit(-1);
                }

                var equalPos = arg.indexOf('=');

                if (equalPos == -1) {
                    equalPos = arg.length();
                }

                var f = findField(arg.substring(1, equalPos));

                if (f == null) {
                    System.out.println("Bad argument: " + arg);
                    System.exit(-1);
                }

                setField(
                    f,
                    (equalPos < arg.length())
                        ? arg.substring(equalPos + 1)
                        : null
                );
            }

            // Read the files
            if (fileType != FileType.NONE) {
                var files = new ArrayList<File>();

                for (var arg : args) {
                    if (arg.charAt(0) == '-') {
                        continue;
                    }

                    var file = new File(arg);

                    if (file.exists()) {
                        files.add(file);
                    } else {
                        System.out.println("File not found: " + arg);
                        System.exit(-1);
                    }

                    if (fileType == FileType.SINGLE) {
                        break;
                    }
                }

                if (files.isEmpty()) {
                    System.out.println("No file specified");
                    System.exit(-1);
                }

                if (fileType == FileType.SINGLE) {
                    fileField.set(obj, files.getFirst());
                } else {
                    fileField.set(obj, files.toArray(new File[0]));
                }
            }
        } catch (InstantiationException | IllegalAccessException e) {
            Log.error("Could not parse arguments", e);
        } catch (InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private Field findField(String name) {
        try {
            return klass.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private void setField(Field field, String value) {
        var t = field.getType().getSimpleName();

        try {
            if (t.equals("int")) {
                field.setInt(obj, Integer.parseInt(value));
            } else if (t.equals("boolean")) {
                field.setBoolean(
                    obj,
                    (value == null) || Boolean.parseBoolean(value)
                );
            } else {
                field.set(obj, value);
            }
        } catch (IllegalAccessException e) {
            Log.error("Error setting argument value", e);
        } catch (NumberFormatException e) {
            Log.error(
                "Not a number given for parameter " + field.getName()
            );
        }
    }

    private enum FileType {
        NONE,
        SINGLE,
        MANY,
    }
}
