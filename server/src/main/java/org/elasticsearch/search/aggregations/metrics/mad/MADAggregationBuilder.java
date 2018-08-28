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

package org.elasticsearch.search.aggregations.metrics.mad;

import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorFactories;
import org.elasticsearch.search.aggregations.AggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValueType;
import org.elasticsearch.search.aggregations.support.ValuesSource;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder.LeafOnly;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregatorFactory;
import org.elasticsearch.search.aggregations.support.ValuesSourceConfig;
import org.elasticsearch.search.aggregations.support.ValuesSourceParserHelper;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MADAggregationBuilder extends LeafOnly<ValuesSource.Numeric, MADAggregationBuilder> {

    public static final String NAME = "mad";

    private static final ParseField COMPRESSION_FIELD = new ParseField("compression");
    private static final ParseField METHOD_FIELD = new ParseField("method"); // todo remove

    public static final List<String> METHODS = Arrays.asList("collection_median", "reduce_percentiles", "reduce_centroids"); // todo remove

    private static final ObjectParser<MADAggregationBuilder, Void> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME);
        ValuesSourceParserHelper.declareNumericFields(PARSER, true, true, false); // todo verify these arguments
        PARSER.declareDouble(MADAggregationBuilder::setCompression, COMPRESSION_FIELD);
        PARSER.declareString(MADAggregationBuilder::setMethod, METHOD_FIELD); // todo remove
    }

    public static MADAggregationBuilder parse(String aggregationName, XContentParser parser) throws IOException {
        return PARSER.parse(parser, new MADAggregationBuilder(aggregationName), null);
    }

    private double compression = 100.0d;
    private String method = "collection_median"; // todo remove

    public MADAggregationBuilder(String name) {
        super(name, ValuesSourceType.NUMERIC, ValueType.NUMERIC);
    }

    public MADAggregationBuilder(StreamInput in) throws IOException {
        super(in, ValuesSourceType.NUMERIC, ValueType.NUMERIC);
        compression = in.readDouble();
        method = in.readString(); //todo remove
    }

    protected MADAggregationBuilder(MADAggregationBuilder clone,
                                    AggregatorFactories.Builder factoriesBuilder,
                                    Map<String, Object> metaData) {
        super(clone, factoriesBuilder, metaData);
        this.compression = clone.compression;
        this.method = clone.method;  // todo remove
    }

    /**
     * Returns the compression factor of the t-digest sketches used
     */
    public double getCompression() {
        return compression;
    }

    /**
     * Set the compression factor of the t-digest sketches used
     */
    public MADAggregationBuilder setCompression(double compression) {
        if (compression < 0.0) {
            throw new IllegalArgumentException(
                "[compression] must be greater than or equal to 0. Found [" + compression + "] in [" + name + "]");
        }
        this.compression = compression;
        return this;
    }

    public String getMethod() { // todo remove
        return method;
    }

    public MADAggregationBuilder setMethod(String method) { // todo remove
        if (METHODS.contains(method) == false) {
            throw new IllegalArgumentException("Invalid MAD method [" + method + "]");
        }

        this.method = method;
        return this;
    }

    @Override
    protected AggregationBuilder shallowCopy(AggregatorFactories.Builder factoriesBuilder, Map<String, Object> metaData) {
        return new MADAggregationBuilder(this, factoriesBuilder, metaData);
    }

    @Override
    protected void innerWriteTo(StreamOutput out) throws IOException {
        out.writeDouble(compression);
        out.writeString(method); // todo remove
    }

    @Override
    protected ValuesSourceAggregatorFactory<ValuesSource.Numeric, ?> innerBuild(SearchContext context,
                                                                                ValuesSourceConfig<ValuesSource.Numeric> config,
                                                                                AggregatorFactory<?> parent,
                                                                                AggregatorFactories.Builder subFactoriesBuilder)
                                                                                throws IOException {

        return new MADAggregatorFactory(name, config, context, parent, subFactoriesBuilder, metaData, compression, method);
        // todo remove method
    }

    @Override
    protected XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        builder.field(COMPRESSION_FIELD.getPreferredName(), compression);
        builder.field(METHOD_FIELD.getPreferredName(), method); // todo remove
        return builder;
    }

    @Override
    protected int innerHashCode() {
        return Objects.hash(compression, method);
    } // todo remove method

    @Override
    protected boolean innerEquals(Object obj) {
        MADAggregationBuilder other = (MADAggregationBuilder) obj;
        return Objects.equals(compression, other.compression)
            && Objects.equals(method, other.method); // todo remove
    }

    @Override
    public String getType() {
        return NAME;
    }
}
