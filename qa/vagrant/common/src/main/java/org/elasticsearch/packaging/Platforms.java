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

public class Platforms {
    public static final String OS_NAME = System.getProperty("os.name");
    public static final boolean LINUX = OS_NAME.startsWith("Linux");
    public static final boolean WINDOWS = OS_NAME.startsWith("Windows");

    public static boolean isDPKG() {
        assert WINDOWS == false;
        return new Shell().run("which", "dpkg").isSuccess();
    }

    public static boolean isAptGet() {
        assert WINDOWS == false;
        return new Shell().run("which", "apt-get").isSuccess();
    }

    public static boolean isRPM() {
        assert WINDOWS == false;
        return new Shell().run("which", "rpm").isSuccess();
    }

    public static boolean isYUM() {
        assert WINDOWS == false;
        return new Shell().run("which", "yum").isSuccess();
    }

    // todo in bats tests we -x check for existence of these executables - why?
    public static boolean isSystemd() {
        assert WINDOWS == false;
        return new Shell().run("which", "systemctl").isSuccess();
    }

    public static boolean isSysVInit() {
        assert WINDOWS == false;
        return new Shell().run("which", "service").isSuccess();
    }
}
