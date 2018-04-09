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

package org.elasticsearch.packaging.matchers;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;

import static org.elasticsearch.packaging.FileUtils.getPosixFileAttributes;
import static java.nio.file.attribute.PosixFilePermissions.fromString;

public class PosixFile extends TypeSafeMatcher<Path> {

    public enum Fileness { File, Directory }

    private Fileness fileness;
    private String owner;
    private String group;
    private Set<PosixFilePermission> permissions;

    public PosixFile(Fileness fileness, String owner, String group, Set<PosixFilePermission> permissions) {
        this.fileness = Objects.requireNonNull(fileness);
        this.owner = Objects.requireNonNull(owner);
        this.group = Objects.requireNonNull(group);
        this.permissions = Objects.requireNonNull(permissions);
    }

    @Override
    protected boolean matchesSafely(Path path) {
        PosixFileAttributes attributes = getPosixFileAttributes(path);
        boolean isDirectory = fileness.equals(Fileness.Directory);

        return isDirectory == attributes.isDirectory() &&
            owner.equals(attributes.owner().getName()) &&
            group.equals(attributes.group().getName()) &&
            permissions.equals(attributes.permissions());
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue("file/directory: ").appendValue(fileness)
            .appendText(" with owner ").appendValue(owner)
            .appendText(" with group ").appendValue(group)
            .appendText(" with permissions ").appendValueList("[", ",", "]", permissions);
    }

    public static PosixFile posixFile(Fileness fileness, String owner, String group, String permissions) {
        return posixFile(fileness, owner, group, fromString(permissions));
    }

    public static PosixFile posixFile(Fileness fileness, String owner, String group, Set<PosixFilePermission> permissions) {
        return new PosixFile(fileness, owner, group, permissions);
    }
}
