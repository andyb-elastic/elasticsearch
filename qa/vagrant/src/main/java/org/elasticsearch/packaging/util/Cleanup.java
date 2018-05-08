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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.elasticsearch.packaging.util.FileUtils.getTempDir;
import static org.elasticsearch.packaging.util.FileUtils.lsGlob;
import static org.elasticsearch.packaging.util.Platforms.isAptGet;
import static org.elasticsearch.packaging.util.Platforms.isDPKG;
import static org.elasticsearch.packaging.util.Platforms.isRPM;
import static org.elasticsearch.packaging.util.Platforms.isSystemd;
import static org.elasticsearch.packaging.util.Platforms.isYUM;

public class Cleanup {

    private static final List<String> ELASTICSEARCH_FILES_LINUX = Arrays.asList(
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

    // todo
    private static final List<String> ELASTICSEARCH_FILES_WINDOWS = Collections.emptyList();

    public static void cleanEverything() {
        final Shell sh = new Shell();

        // kill elasticsearch processes
        if (Platforms.WINDOWS) {

            sh.run("powershell.exe", "-Command",
                "Get-Process -IncludeUserName | " +
                "Where-Object { $_.UserName -Match 'elasticsearch' } | " +
                "Stop-Process -Force");

            // the view of processes returned by Get-Process doesn't expose command line arguments, so we use WMI here
            sh.run("powershell.exe", "-Command",
                "Get-WmiObject Win32_Process | " +
                "Where-Object { $_.CommandLine -Match 'org.elasticsearch.bootstrap.Elasticsearch' } | " +
                "ForEach-Object { $_.Terminate() }");

        } else {

            sh.run("pkill", "-u", "elasticsearch");
            sh.run("bash", "-c", "ps aux | grep -i 'org.elasticsearch.bootstrap.Elasticsearch' | awk {'print $2'} | xargs kill -9");

        }

        if (Platforms.LINUX) {
            purgePackagesLinux();
        }

        // remove elasticsearch users
        if (Platforms.WINDOWS) {

            sh.run("powershell.exe", "-Command", "Remove-LocalUser -Name 'elasticsearch'");

        } else {

            sh.run("userdel", "elasticsearch");
            sh.run("groupdel", "elasticsearch");

        }

        // delete files that may still exist
        lsGlob(getTempDir(), "elasticsearch*").forEach(FileUtils::rm);
        final List<String> filesToDelete = Platforms.WINDOWS
            ? ELASTICSEARCH_FILES_WINDOWS
            : ELASTICSEARCH_FILES_LINUX;
        filesToDelete.stream()
            .map(Paths::get)
            .filter(Files::exists)
            .forEach(FileUtils::rm);

        // disable elasticsearch service
        if (Platforms.WINDOWS) {

            // todo

        } else {

            if (isSystemd()) {
                sh.runAndAssertSuccess("systemctl", "unmask", "systemd-sysctl.service");
            }

        }
    }

    private static void purgePackagesLinux() {
        final Shell sh = new Shell();

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
    }
}
