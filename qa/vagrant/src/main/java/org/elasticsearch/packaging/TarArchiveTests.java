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

import org.elasticsearch.packaging.Shell.Result;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.Files.exists;
import static org.elasticsearch.packaging.Archives.installTar;
import static org.elasticsearch.packaging.Archives.verifyTarInstallation;
import static org.elasticsearch.packaging.FileUtils.append;
import static org.elasticsearch.packaging.FileUtils.cp;
import static org.elasticsearch.packaging.FileUtils.getTempDir;
import static org.elasticsearch.packaging.FileUtils.mkdir;
import static org.elasticsearch.packaging.FileUtils.rm;
import static org.elasticsearch.packaging.RunElasticsearch.smokeTest;
import static org.elasticsearch.packaging.RunElasticsearch.startServiceAndWait;
import static org.elasticsearch.packaging.RunElasticsearch.stopService;
import static org.elasticsearch.packaging.matchers.PosixFile.Fileness.File;
import static org.elasticsearch.packaging.matchers.PosixFile.posixFile;
import static org.elasticsearch.packaging.RunElasticsearch.cleanup;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assume.assumeThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TarArchiveTests {

    @BeforeClass
    public static void setup() {
        cleanup();
    }

    // todo this is probably not the best way to persist an env between tests
    private static ElasticsearchEnv env;

    @Test
    public void test000TarToolAvailable() {
        assumeThat(Platforms.WINDOWS, is(false));

        final Shell sh = new Shell();
        sh.runAndAssertSuccess("tar", "--version");
    }

    @Test
    public void test001InstallTarArchive() {
        assumeThat(Platforms.WINDOWS, is(false));

        env = installTar();
        verifyTarInstallation(env);
    }

    @Test
    public void test002ListPlugins() {
        assumeThat(Platforms.WINDOWS, is(false));
        assumeThat(env, is(notNullValue()));

        final Shell sh = new Shell(env.toMap());
        final Result result = sh.runAndAssertSuccess(env.ESHOME.resolve("bin/elasticsearch-plugin").toString(), "list");
        assertThat(result.stdout, isEmptyString());
    }

    @Test
    public void test003CreateKeystore() {
        assumeThat(Platforms.WINDOWS, is(false));
        assumeThat(env, is(notNullValue()));

        final Shell sh = new Shell(env.toMap());
        final String keystoreCommand = env.ESHOME.resolve("bin/elasticsearch-keystore").toString();
        sh.runAndAssertSuccess("sudo", "-E", "-u", "elasticsearch", keystoreCommand, "create");
        assertThat(env.ESCONFIG.resolve("elasticsearch.keystore"), posixFile(File, "elasticsearch", "elasticsearch", "rw-rw----"));
        final Result listKeystore = sh.runAndAssertSuccess("sudo", "-E", "-u", "elasticsearch", keystoreCommand, "list");
        assertThat(listKeystore.stdout, containsString("keystore.seed"));

        // clean up for next keystore test
        rm(env.ESCONFIG.resolve("elasticsearch.keystore"));
    }

    @Test
    public void test004RunElasticsearch() {
        assumeThat(Platforms.WINDOWS, is(false));
        assumeThat(env, is(notNullValue()));

        Shell sh = new Shell(env.toMap());
        startServiceAndWait(sh);
        smokeTest();
        stopService();
    }

    @Test
    public void test005AutoCreatedKeystore() {
        assumeThat(Platforms.WINDOWS, is(false));
        assumeThat(env, is(notNullValue()));

        final Shell sh = new Shell(env.toMap());
        assertThat(env.ESCONFIG.resolve("elasticsearch.keystore"), posixFile(File, "elasticsearch", "elasticsearch", "rw-rw----"));
        final String keystoreCommand = env.ESHOME.resolve("bin/elasticsearch-keystore").toString();
        final Result result = sh.runAndAssertSuccess("sudo", "-E", "-u", "elasticsearch", keystoreCommand, "list");
        assertThat(result.stdout, containsString("keystore.seed"));
    }

    @Test
    public void test006EsPathConf() {
        assumeThat(Platforms.WINDOWS, is(false));
        assumeThat(env, is(notNullValue()));

        final Path tempConf = getTempDir().resolve("ESCONF-alternate");

        final Map<String, String> modifiedEnv = new HashMap<>(env.toMap());
        modifiedEnv.put("ES_PATH_CONF", tempConf.toString());
        modifiedEnv.put("ES_JAVA_OPTS", "-XX:-UseCompressedOops");
        final Shell sh = new Shell(modifiedEnv);

        try {
            mkdir(tempConf);
            cp(env.ESCONFIG.resolve("elasticsearch.yml"), tempConf.resolve("elasticsearch.yml"));
            cp(env.ESCONFIG.resolve("log4j2.properties"), tempConf.resolve("log4j2.properties"));

            /*
             * we have to disable Log4j from using JMX lest it will hit a security
             * manager exception before we have configured logging; this will fail
             * startup since we detect usages of logging before it is configured
             */
            final String jvmOptions =
                "-Xms512m\n" +
                "-Xmx512m\n" +
                "-Dlog4j2.disable.jmx=true\n";
            append(tempConf.resolve("jvm.options"), jvmOptions);
            sh.runAndAssertSuccess("chown", "-R", "elasticsearch:elasticsearch", tempConf.toString());

            startServiceAndWait(sh);

            final Result result = sh.runAndAssertSuccess("curl", "-s", "-XGET", "localhost:9200/_nodes");
            assertThat(result.stdout, containsString("\"heap_init_in_bytes\":536870912"));
            assertThat(result.stdout, containsString("\"using_compressed_ordinary_object_pointers\":\"false\""));

            stopService();

        } finally {
            rm(tempConf);
        }
    }

    @Test
    public void test007GCLogsExist() {
        assumeThat(Platforms.WINDOWS, is(false));
        assumeThat(env, is(notNullValue()));

        final Shell sh = new Shell(env.toMap());
        startServiceAndWait(sh);
        assertThat(exists(env.ESLOG.resolve("gc.log.0.current")), is(true));
        stopService();
    }

    @Test
    public void test008RelativeEsPathConf() {
        assumeThat(Platforms.WINDOWS, is(false));
        assumeThat(env, is(notNullValue()));

        final Path temp = getTempDir().resolve("ESCONF-alternate");
        final Path tempConf = temp.resolve("config");

        final Map<String, String> modifiedEnv = new HashMap<>(env.toMap());
        modifiedEnv.put("ES_PATH_CONF", tempConf.getFileName().toString());
        final Shell sh = new Shell(modifiedEnv, temp);

        try {
            mkdir(tempConf);
            cp(env.ESCONFIG.resolve("elasticsearch.yml"), tempConf.resolve("elasticsearch.yml"));
            cp(env.ESCONFIG.resolve("log4j2.properties"), tempConf.resolve("log4j2.properties"));
            cp(env.ESCONFIG.resolve("jvm.options"), tempConf.resolve("jvm.options"));
            append(tempConf.resolve("elasticsearch.yml"), "node.name: relative");
            sh.runAndAssertSuccess("chown", "-R", "elasticsearch:elasticsearch", temp.toString());

            startServiceAndWait(sh);
            final Result result = sh.runAndAssertSuccess("curl", "-s", "-XGET", "localhost:9200/_nodes");
            assertThat(result.stdout, containsString("\"name\":\"relative\""));

            stopService();
        } finally {
            rm(temp);
        }
    }

}
