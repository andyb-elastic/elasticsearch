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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.elasticsearch.packaging.FileUtils.append;
import static org.elasticsearch.packaging.FileUtils.getTempDir;
import static org.elasticsearch.packaging.FileUtils.lsGlob;
import static org.elasticsearch.packaging.FileUtils.rm;
import static org.elasticsearch.packaging.FileUtils.slurp;
import static org.elasticsearch.packaging.Platforms.isAptGet;
import static org.elasticsearch.packaging.Platforms.isDPKG;
import static org.elasticsearch.packaging.Platforms.isRPM;
import static org.elasticsearch.packaging.Platforms.isSysVInit;
import static org.elasticsearch.packaging.Platforms.isSystemd;
import static org.elasticsearch.packaging.Platforms.isYUM;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class RunElasticsearch {

    public enum Status { red, yellow, green }

    private static final List<String> LINUX_ELASTICSEARCH_FILES = Arrays.asList(
        "/usr/share/elasticsearch",
        "/etc/elasticsearch",
        "/var/lib/elasticsearch",
        "/var/log/elasticsearch",
        "/etc/default/elasticsearch",
        "/etc/sysconfig/elasticsearch",
        "/var/run/elasticsearch",
        "/usr/share/doc/elasticsearch",
        "/usr/lib/systemd/system/elasticsearch.conf",
        "/usr/lib/tmpfiles.d/elasticsearch.conf",
        "/usr/lib/sysctl.d/elasticsearch.conf"
    );

    public static void cleanup() {
        final Shell sh = new Shell();

        if (sh.run("id", "elasticsearch").isSuccess()) {
            sh.run("pkill", "-u", "elasticsearch");
        }

        sh.run("bash", "-c", "ps aux | grep -i 'org.elasticsearch.bootstrap.Elasticsearch' | awk {'print $2'} | xargs kill -9");

        if (isRPM()) {
            sh.run("rpm", "--quiet", "-e", "elasticsearch");
        }

        if (isYUM()) {
            sh.run("yum", "remove", "-y", "elasticsearch");
        }

        if (isDPKG()) {
            sh.run("dpkg", "--purge", "elasticsearch");
        }

        if (isAptGet()) {
            sh.run("apt-get", "--quiet", "--yes", "purge", "elasticsearch");
        }

        sh.run("userdel", "elasticsearch");
        sh.run("groupdel", "elasticsearch");

        lsGlob(getTempDir(), "elasticsearch*").forEach(FileUtils::rm);
        for (String file : LINUX_ELASTICSEARCH_FILES) {
            Path path = Paths.get(file);
            if (Files.exists(path)) {
                rm(path);
            }
        }

        if (isSystemd()) {
            sh.run("systemctl", "unmask", "systemd-sysctl.service");
        }
    }

    public static void startServiceAndWait(Shell shell) {
        startServiceAndWait(shell, Status.green, null, emptyList());
    }

    public static void startServiceAndWait(Shell sh, Status expectedStatus, String index, List<String> arguments) {
        startService(sh, arguments);
        waitForStatus(expectedStatus, index);

        final Path pidFile = getTempDir().resolve("elasticsearch/elasticsearch.pid");
        if (Files.exists(pidFile)) {
            final String pid = slurp(pidFile).trim();
            assertThat(Integer.parseInt(pid), greaterThan(0));
            sh.runAndAssertSuccess("ps", pid);
        } else if (isSystemd()) {
            sh.runAndAssertSuccess("systemctl", "is-active", "elasticsearch.service");
            sh.runAndAssertSuccess("systemctl", "status", "elasticsearch.service");
        } else if (isSysVInit()) {
            sh.runAndAssertSuccess("service", "elasticsearch", "status");
        }
    }

    public static void startService(Shell sh, List<String> arguments) {
        String packageEnvAppends = "";
        if (sh.env.containsKey("ES_PATH_CONF")) {
            packageEnvAppends += "ES_PATH_CONF=" + sh.env.get("ES_PATH_CONF") + "\n";
        }
        if (sh.env.containsKey("ES_JAVA_OPTS")) {
            packageEnvAppends += "ES_JAVA_OPTS=" + sh.env.get("ES_JAVA_OPTS") + "\n";
        }

        if (packageEnvAppends.isEmpty() == false) {
            if (isDPKG()) {
                append(Paths.get("/etc/default/elasticsearch"), packageEnvAppends);
            } else if (isRPM()) {
                append(Paths.get("/etc/sysconfig/elasticsearch"), packageEnvAppends);
            }
        }

        if (Files.exists(getTempDir().resolve("elasticsearch/bin/elasticsearch"))) {
            // we check the temp dir, rather than ESHOME, to detect the case where we're running from an archive rather than
            // a package. this is kind of a hack and there should probably be a better way to indicate it

            // If jayatana is installed then we try to use it. Elasticsearch should ignore it even when we try.
            // If it doesn't ignore it then Elasticsearch will fail to start because of security errors.
            // This line is attempting to emulate the on login behavior of /usr/share/upstart/sessions/jayatana.conf
            if (Files.exists(Paths.get("/usr/share/java/jayatanaag.jar"))) {
                sh.env.put("JAVA_TOOL_OPTIONS", "-javaagent:/usr/share/java/jayatanaag.jar");
            }

            // we start it in the background so that we can capture the exit code
            final String archiveRunner =
                "timeout 60s /tmp/elasticsearch/bin/elasticsearch -d -p /tmp/elasticsearch/elasticsearch.pid" + String.join(" ", arguments);

            sh.runAndAssertSuccess("sudo", "-E", "-u", "elasticsearch", "bash", "-c", archiveRunner);
        } else if (isSystemd()) {
            sh.runAndAssertSuccess("systemctl", "daemon-reload");
            sh.runAndAssertSuccess("systemctl", "enable", "elasticsearch.service");
            sh.runAndAssertSuccess("systemctl", "is-enabled", "elasticsearch.service");
            sh.runAndAssertSuccess("systemctl", "start", "elasticsearch.service");
        } else if (isSystemd()) {
            sh.runAndAssertSuccess("service", "elasticsearch", "start");
        }
    }

    public static void waitForStatus(Status expectedStatus, String index) {
        final Shell sh = new Shell();

        // todo replace this with java http client

        sh.runAndAssertSuccess("wget", "-O", "-", "--retry-connrefused", "--waitretry=1", "--timeout=120", "--tries=120",
            "http://localhost:9200/_cluster/health");

        final String esRequest;
        if (index == null) {
            esRequest = "http://localhost:9200/_cluster/health?wait_for_status=" + expectedStatus.name() + "&timeout=60s&pretty";
        } else {
            esRequest = "http://localhost:9200/_cluster/health/" + index + "?wait_for_status=" + expectedStatus.name() +
                "&timeout=60s&pretty";
        }

        sh.runAndAssertSuccess("curl", "-sS", esRequest);
        final Result result = sh.runAndAssertSuccess("curl", "-sS", "-XGET", "http://localhost:9200/_cat/health?h=status&v=false");
        assertThat(result.stdout, containsString(expectedStatus.name()));
    }

    public static void stopService() {
        final Shell sh = new Shell();

        final Path pidFile = Paths.get("/tmp/elasticsearch/elasticsearch.pid");

        if (Files.exists(pidFile)) {
            final String pid = slurp(pidFile).trim();
            assertThat(Integer.parseInt(pid), greaterThan(0));
            sh.runAndAssertSuccess("kill", "-SIGTERM", pid);
        } else if (isSystemd()) {
            sh.runAndAssertSuccess("systemctl", "stop", "elasticsearch.service");
            final Result result = sh.run("systemctl", "is-active", "elasticsearch.service");
            assertThat(result.exitCode, is(3));
            assertThat(result.stdout, anyOf(containsString("inactive"), containsString("failed")));
        } else if (isSysVInit()) {
            sh.runAndAssertSuccess("service", "elasticsearch", "stop");
            final Result result = sh.run("service", "elasticsearch", "status");
            assertThat(result.exitCode, is(not(0)));
        }
    }

    public static void smokeTest() {
        final Shell sh = new Shell();

        // todo replace with java http client
        sh.runAndAssertSuccess("curl", "-s", "-H", "Content-Type: application/json", "-XPOST",
            "http://localhost:9200/library/book/1?refresh=true&pretty", "-d", "{\"title\":\"Book #1\",\"pages\":123}");

        sh.runAndAssertSuccess("curl", "-s", "-H", "Content-Type: application/json", "-XPOST",
            "http://localhost:9200/library/book/2?refresh=true&pretty", "-d", "{\"title\":\"Book #2\",\"pages\":456}");

        final Result result = sh.runAndAssertSuccess("curl", "-s", "-XGET", "http://localhost:9200/_count?pretty");
        assertThat(result.stdout, containsString("\"count\" : 2"));

        sh.runAndAssertSuccess("curl", "-s", "-XDELETE", "http://localhost:9200/_all");
    }
}
