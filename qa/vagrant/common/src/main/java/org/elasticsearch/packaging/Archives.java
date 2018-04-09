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

import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static java.nio.file.Files.exists;
import static org.elasticsearch.packaging.FileUtils.getPackagingDir;
import static org.elasticsearch.packaging.FileUtils.getTempDir;
import static org.elasticsearch.packaging.FileUtils.lsGlob;
import static org.elasticsearch.packaging.FileUtils.mkdir;
import static org.elasticsearch.packaging.FileUtils.mv;
import static org.elasticsearch.packaging.FileUtils.rm;
import static org.elasticsearch.packaging.Platforms.isDPKG;
import static org.elasticsearch.packaging.matchers.PosixFile.posixFile;
import static org.elasticsearch.packaging.matchers.PosixFile.Fileness.File;
import static org.elasticsearch.packaging.matchers.PosixFile.Fileness.Directory;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import static java.nio.file.attribute.PosixFilePermissions.fromString;

public class Archives {

    public static ElasticsearchEnv installTar() {
        return installTar(getTempDir().resolve("elasticsearch"));
    }

    public static ElasticsearchEnv installTar(Path esHome) {
        Shell sh = new Shell();

        // we must only have one tar archive
        List<Path> archives = lsGlob(getPackagingDir(), "elasticsearch*.tar.gz");
        assertThat(archives, hasSize(1));
        Path archive = archives.get(0);

        // elasticsearch must not already be installed
        assertThat(lsGlob(esHome.getParent(), "elasticsearch*"), empty());

        Path untarPath = getTempDir().resolve("untar");
        rm(untarPath);
        mkdir(untarPath);

        // extract archive
        sh.runAndAssertSuccess("tar", "-xzpf", archive.toString(), "-C", untarPath.toString());

        // move extracted archive to ESHOME
        List<Path> extractedArchives = lsGlob(untarPath, "elasticsearch*");
        assertThat(extractedArchives, hasSize(1));
        Path extractedArchive = extractedArchives.get(0);
        mv(extractedArchive, esHome);

        // create elasticsearch user and group and chown ESHOME
        setupUsers(esHome);

        return new ElasticsearchEnv(esHome);
    }

    private static void setupUsers(Path esHome) {
        Shell sh = new Shell();

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
        sh.runAndAssertSuccess("chown", "-R", "elasticsearch:elasticsearch", esHome.toString());
    }

    public static void verifyTarInstallation(ElasticsearchEnv env) {
        final Set<PosixFilePermission> p755 = fromString("rwxr-xr-x"); // todo should go somewhere better
        final Set<PosixFilePermission> p660 = fromString("rw-rw----");
        final Set<PosixFilePermission> p644 = fromString("rw-r--r--");

        assertThat(env.ESHOME, posixFile(Directory, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("bin"), posixFile(Directory, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("bin/elasticsearch"), posixFile(File, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("bin/elasticsearch-env"), posixFile(File, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("bin/elasticsearch-keystore"), posixFile(File, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("bin/elasticsearch-plugin"), posixFile(File, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("bin/elasticsearch-translog"), posixFile(File, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESCONFIG, posixFile(Directory, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESCONFIG.resolve("elasticsearch.yml"), posixFile(File, "elasticsearch", "elasticsearch", p660));
        assertThat(env.ESCONFIG.resolve("jvm.options"), posixFile(File, "elasticsearch", "elasticsearch", p660));
        assertThat(env.ESCONFIG.resolve("log4j2.properties"), posixFile(File, "elasticsearch", "elasticsearch", p660));
        assertThat(env.ESPLUGINS, posixFile(Directory, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("lib"), posixFile(Directory, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("logs"), posixFile(Directory, "elasticsearch", "elasticsearch", p755));
        assertThat(env.ESHOME.resolve("NOTICE.txt"), posixFile(File, "elasticsearch", "elasticsearch", p644));
        assertThat(env.ESHOME.resolve("LICENSE.txt"), posixFile(File, "elasticsearch", "elasticsearch", p644));
        assertThat(env.ESHOME.resolve("README.textile"), posixFile(File, "elasticsearch", "elasticsearch", p644));
        assertThat(exists(env.ESCONFIG.resolve("elasticsearch.keystore")), is(false));
    }
}
