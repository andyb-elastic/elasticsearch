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

package org.elasticsearch.packaging.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.elasticsearch.packaging.util.FileMatcher.Fileness.Directory;
import static org.elasticsearch.packaging.util.FileMatcher.Fileness.File;
import static org.elasticsearch.packaging.util.FileMatcher.file;
import static org.elasticsearch.packaging.util.FileMatcher.p644;
import static org.elasticsearch.packaging.util.FileMatcher.p660;
import static org.elasticsearch.packaging.util.FileMatcher.p755;
import static org.elasticsearch.packaging.util.FileUtils.getCurrentVersion;
import static org.elasticsearch.packaging.util.FileUtils.getDefaultArchiveInstallPath;
import static org.elasticsearch.packaging.util.FileUtils.getPackagingArchivesDir;
import static org.elasticsearch.packaging.util.FileUtils.lsGlob;

import static org.elasticsearch.packaging.util.FileUtils.mv;
import static org.elasticsearch.packaging.util.FileUtils.rm;
import static org.elasticsearch.packaging.util.Platforms.isDPKG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

/**
 * Installation and verification logic for archive distributions
 */
public class Archives {

    /** the user that archive files should be owned by and run as */
    private static final String USER = "elasticsearch";

    public static Installation installArchive(Distribution distribution) {
        return installArchive(distribution, getDefaultArchiveInstallPath(), getCurrentVersion());
    }

    public static Installation installArchive(Distribution distribution, Path fullInstallPath, String version) {
        final Shell sh = new Shell();

        final Path distributionFile = getPackagingArchivesDir().resolve(distribution.filename(version));
        final Path baseInstallPath = fullInstallPath.getParent();
        final Path extractedPath = baseInstallPath.resolve("elasticsearch-" + version);

        assertThat("distribution file must exist", Files.exists(distributionFile), is(true));
        assertThat("elasticsearch must not already be installed", lsGlob(baseInstallPath, "elasticsearch*"), empty());

        if (distribution.kind == Distribution.Kind.TAR) {
            if (Platforms.WINDOWS) {
                // 7zip needs two steps, first to decompress gzip, then to expand tar
                sh.runAndAssertSuccess("7z", "x", "-o" + baseInstallPath.toString(), distributionFile.toString());
                final String distributionName = distribution.filename(version);
                final String tarName = distributionName.substring(0, distributionName.length() - 3);
                final Path decompressedTar = baseInstallPath.resolve(tarName);
                assertThat(Files.exists(decompressedTar), is(true));
                sh.runAndAssertSuccess("7z", "x", "-o" + baseInstallPath.toString(), decompressedTar.toString());
                rm(decompressedTar);
            } else {
                sh.runAndAssertSuccess("tar", "-C", baseInstallPath.toString(), "-xzpf", distributionFile.toString());
            }
        } else { // zip
            throw new RuntimeException("Not implemented yet");
        }

        assertThat("archive was extracted", Files.exists(extractedPath), is(true));

        mv(extractedPath, fullInstallPath);

        assertThat("extracted archive moved to install location", Files.exists(fullInstallPath));
        final List<Path> installations = lsGlob(baseInstallPath, "elasticsearch*");
        assertThat("only the intended installation exists", installations, hasSize(1));
        assertThat("only the intended installation exists", installations.get(0), is(fullInstallPath));

        setupArchiveUsers(fullInstallPath);

        return new Installation(fullInstallPath);
    }

    private static void setupArchiveUsers(Path installPath) {
        if (Platforms.WINDOWS) {
            setupArchiveUsersWindows(installPath);
        } else {
            setupArchiveUsersLinux(installPath);
        }
    }

    private static void setupArchiveUsersWindows(Path installPath) {
        final Shell sh = new Shell();

        if (sh.run("powershell.exe", "-Command", "Get-LocalUser 'elasticsearch'").isSuccess() == false) {

            // we can't run commands as another user unless we give them a password
            sh.runAndAssertSuccess("powershell.exe", "-Command",
                "$pw = ConvertTo-SecureString 'Foobar2000' -AsPlainText -Force; " +
                "New-LocalUser -Name 'elasticsearch' -Password $pw");
        }

        sh.runAndAssertSuccess("powershell.exe", "-Command",
            "$esUser = New-Object System.Security.Principal.NTAccount 'elasticsearch'; " +
            "$archiveFiles = Get-ChildItem -Path '" + installPath.toString() + "' -Recurse; " +
            "$archiveFiles += Get-Item -Path '" + installPath.toString() + "'; " +
            "$archiveFiles | ForEach-Object { " +
            "$acl = Get-Acl -Path $_.FullName; $acl.setOwner($esUser); Set-Acl -Path $_.FullName -AclObject $acl; " +
            "}");
    }

    private static void setupArchiveUsersLinux(Path installPath) {
        final Shell sh = new Shell();

        if (sh.run("getent", "group", "elasticsearch").isSuccess() == false) {
            if (isDPKG()) {
                sh.runAndAssertSuccess("addgroup", "--system", "elasticsearch");
            } else {
                sh.runAndAssertSuccess("groupadd", "-r", "elasticsearch");
            }
        }

        if (sh.run("id", "elasticsearch").isSuccess() == false) {
            if (isDPKG()) {
                sh.runAndAssertSuccess("adduser",
                    "--quiet",
                    "--system",
                    "--no-create-home",
                    "--ingroup", "elasticsearch",
                    "--disabled-password",
                    "--shell", "/bin/false",
                    "elasticsearch");
            } else {
                sh.runAndAssertSuccess("useradd",
                    "--system",
                    "-M",
                    "--gid", "elasticsearch",
                    "--shell", "/sbin/nologin",
                    "--comment", "elasticsearch user",
                    "elasticsearch");
            }
        }
        sh.runAndAssertSuccess("chown", "-R", "elasticsearch:elasticsearch", installPath.toString());
    }

    public static void verifyArchiveInstallation(Installation installation, Distribution distribution) {
        verifyOssInstallation(installation);
        if (distribution.license == Distribution.License.COMMERCIAL) {
            verifyCommercialInstallation(installation);
        }
    }

    public static void verifyOssInstallation(Installation es) {
        assertThat(es.home, file(Directory, USER, USER, p755));
        assertThat(es.config, file(Directory, USER, USER, p755));
        assertThat(es.plugins, file(Directory, USER, USER, p755));
        assertThat(es.modules, file(Directory, USER, USER, p755));
        assertThat(es.logs, file(Directory, USER, USER, p755));
        assertThat(Files.exists(es.data), is(false));
        assertThat(Files.exists(es.scripts), is(false));

        assertThat(es.home.resolve("lib"), file(Directory, USER, USER, p755));
        assertThat(Files.exists(es.config.resolve("elasticsearch.keystore")), is(false));

        assertThat(es.home.resolve("bin"), file(Directory, USER, USER, p755));
        Stream.of(
            "bin/elasticsearch",
            "bin/elasticsearch-env",
            "bin/elasticsearch-keystore",
            "bin/elasticsearch-plugin",
            "bin/elasticsearch-translog"
        ).forEach(executable -> {
            assertThat(es.home.resolve(executable), file(File, USER, USER, p755));
            assertThat(es.home.resolve(executable + ".bat"), file(File, USER, USER, p644));
        });

        Stream.of(
            "bin/elasticsearch-service.bat",
            "bin/elasticsearch-service-mgr.exe",
            "bin/elasticsearch-service-x64.exe"
        ).forEach(executable -> assertThat(es.home.resolve(executable), file(File, USER, USER, p644)));

        Stream.of(
            "elasticsearch.yml",
            "jvm.options",
            "log4j2.properties"
        ).forEach(config -> assertThat(es.config.resolve(config), file(File, USER, USER, p660)));

        Stream.of(
            "NOTICE.txt",
            "LICENSE.txt",
            "README.textile"
        ).forEach(doc -> assertThat(es.home.resolve(doc), file(File, USER, USER, p644)));
    }

    public static void verifyCommercialInstallation(Installation es) {

        Stream.of(
            "bin/elasticsearch-certgen",
            "bin/elasticsearch-certutil",
            "bin/elasticsearch-croneval",
            "bin/elasticsearch-migrate",
            "bin/elasticsearch-saml-metadata",
            "bin/elasticsearch-setup-passwords",
            "bin/elasticsearch-sql-cli",
            "bin/elasticsearch-syskeygen",
            "bin/elasticsearch-users",
            "bin/x-pack-env",
            "bin/x-pack-security-env",
            "bin/x-pack-watcher-env"
        ).forEach(executable -> {
            assertThat(es.home.resolve(executable), file(File, USER, USER, p755));
            assertThat(es.home.resolve(executable + ".bat"), file(File, USER, USER, p755));
        });

        // it may not be good that we always grab current version here
        assertThat(es.home.resolve("bin/elasticsearch-sql-cli-" + getCurrentVersion() + ".jar"), file(File, USER, USER, p755));

        Stream.of(
            "users",
            "users_roles",
            "roles.yml",
            "role_mapping.yml",
            "log4j2.properties"
        ).forEach(config -> assertThat(es.config.resolve(config), file(File, USER, USER, p660)));
    }

}
