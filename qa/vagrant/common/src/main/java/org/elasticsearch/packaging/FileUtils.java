/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.packaging;

import org.elasticsearch.core.internal.io.IOUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsEmptyString.isEmptyOrNullString;

public class FileUtils {

    public static List<Path> lsGlob(Path directory, String glob) {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {

            for (Path path : stream) {
                paths.add(path);
            }
            return paths;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void rm(Path... paths) {
        try {
            IOUtils.rm(paths);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path mkdir(Path path, FileAttribute<?>... attrs) {
        try {
            return Files.createDirectories(path, attrs);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path mv(Path source, Path target, CopyOption... options) {
        try {
            return Files.move(source, target, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path cp(Path source, Path target, CopyOption... options) {
        try {
            return Files.copy(source, target, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String slurp(Path file) {
        try {
            return String.join("\n", Files.readAllLines(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void append(Path file, String text) {
        try (FileWriter writer = new FileWriter(file.toFile(), true)) {
            writer.write(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static PosixFileAttributes getPosixFileAttributes(Path path, LinkOption... options) {
        try {
            return Files.readAttributes(path, PosixFileAttributes.class, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path getTempDir() {
        String path = Platforms.WINDOWS
            ? System.getenv("TEMP")
            : "/tmp";
        assertThat(path, not(isEmptyOrNullString()));
        return Paths.get(path);
    }

    public static Path getPackagingDir() {
        String fromEnv = System.getenv("PACKAGING_ARCHIVES");
        assertThat(fromEnv, not(isEmptyOrNullString()));
        return Paths.get(fromEnv);
    }
}
