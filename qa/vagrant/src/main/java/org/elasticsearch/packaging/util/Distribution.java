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

public enum Distribution {

    OSS_TAR(Kind.TAR, License.OPENSOURCE),
    OSS_DEB(Kind.DEB, License.OPENSOURCE),
    OSS_RPM(Kind.RPM, License.OPENSOURCE),

    TAR(Kind.TAR, License.COMMERCIAL),
    DEB(Kind.DEB, License.COMMERCIAL),
    RPM(Kind.RPM, License.COMMERCIAL);

    public final Kind kind;
    public final License license;

    Distribution(Kind kind, License license) {
        this.kind = kind;
        this.license = license;
    }

    public String filename(String version) {
        return license.name + "-" + version + kind.extension;
    }

    public enum Kind {

        TAR(".tar.gz", true),
        DEB(".deb", Platforms.isDPKG()),
        RPM(".rpm", Platforms.isRPM());

        public final String extension;
        public final boolean compatible;

        Kind(String extension, boolean compatible) {
            this.extension = extension;
            this.compatible = compatible;
        }
    }

    public enum License {

        OPENSOURCE("elasticsearch-oss"),
        COMMERCIAL("elasticsearch");

        public final String name;

        License(String name) {
            this.name = name;
        }
    }
}
