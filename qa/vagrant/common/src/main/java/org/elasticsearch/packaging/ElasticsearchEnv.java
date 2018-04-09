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
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.unmodifiableMap;

public class ElasticsearchEnv {

    public final Path ESHOME;
    public final Path ESMODULES;
    public final Path ESPLUGINS;
    public final Path ESCONFIG;
    public final Path ESSCRIPTS;
    public final Path ESDATA;
    public final Path ESLOG;

    public ElasticsearchEnv(Path ESHOME) {
        this(
            ESHOME,
            ESHOME.resolve("modules"),
            ESHOME.resolve("plugins"),
            ESHOME.resolve("config"),
            ESHOME.resolve("scripts"),
            ESHOME.resolve("data"),
            ESHOME.resolve("logs")
        );
    }

    public ElasticsearchEnv(Path ESHOME, Path ESMODULES, Path ESPLUGINS, Path ESCONFIG, Path ESSCRIPTS, Path ESDATA, Path ESLOG) {
        this.ESHOME = ESHOME;
        this.ESMODULES = ESMODULES;
        this.ESPLUGINS = ESPLUGINS;
        this.ESCONFIG = ESCONFIG;
        this.ESSCRIPTS = ESSCRIPTS;
        this.ESDATA = ESDATA;
        this.ESLOG = ESLOG;
    }

    public Map<String, String> toMap() {
        Map<String, String> env = new HashMap<>();
        env.put("ESHOME", ESHOME.toString());
        env.put("ESMODULES", ESMODULES.toString());
        env.put("ESPLUGINS", ESPLUGINS.toString());
        env.put("ESCONFIG", ESCONFIG.toString());
        env.put("ESSCRIPTS", ESSCRIPTS.toString());
        env.put("ESDATA", ESDATA.toString());
        env.put("ESLOG", ESLOG.toString());
        return unmodifiableMap(env);
    }
}
