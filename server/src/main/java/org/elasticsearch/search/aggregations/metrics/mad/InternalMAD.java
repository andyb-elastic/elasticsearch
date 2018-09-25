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

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.metrics.InternalNumericMetricsAggregation;
import org.elasticsearch.search.aggregations.metrics.TDigestState;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.elasticsearch.search.aggregations.metrics.mad.MADAggregator.computeMAD;

public class InternalMAD extends InternalNumericMetricsAggregation.SingleValue implements MedianAbsoluteDeviation {

    private final TDigestState valueSketch;
    private final TDigestState deviationSketch;
    private final String method;

    public InternalMAD(String name,
                       List<PipelineAggregator> pipelineAggregators,
                       Map<String, Object> metaData,
                       DocValueFormat format,
                       TDigestState valueSketch,
                       TDigestState deviationSketch,
                       String method) {

        super(name, pipelineAggregators, metaData);
        this.format = format;
        this.valueSketch = valueSketch;
        this.deviationSketch = deviationSketch;
        this.method = method;
    }

    public InternalMAD(StreamInput in) throws IOException {
        super(in);
        format = in.readNamedWriteable(DocValueFormat.class);
        valueSketch = TDigestState.read(in);
        deviationSketch = TDigestState.read(in);
        method = in.readString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(format);
        TDigestState.write(valueSketch, out);
        TDigestState.write(deviationSketch, out);
        out.writeString(method);
    }

    @Override
    public InternalAggregation doReduce(List<InternalAggregation> aggregations, ReduceContext reduceContext) {
        TDigestState valueMerged = null;
        TDigestState deviationMerged = null;
        for (InternalAggregation aggregation : aggregations) {
            final InternalMAD magAgg = (InternalMAD) aggregation;
            if (valueMerged == null) {
                valueMerged = new TDigestState(magAgg.valueSketch.compression());
            }
            if (deviationMerged == null) {
                deviationMerged = new TDigestState(magAgg.deviationSketch.compression());
            }
            valueMerged.add(magAgg.valueSketch);
            deviationMerged.add(magAgg.deviationSketch);
        }

        return new InternalMAD(name, pipelineAggregators(), metaData, format, valueMerged, deviationSketch, method);
    }

    @Override
    public XContentBuilder doXContentBody(XContentBuilder builder, Params params) throws IOException {
        final boolean anyResults = valueSketch.size() > 0;
        final Double mad = anyResults
            ? getMAD()
            : null;

        builder.field(CommonFields.VALUE.getPreferredName(), mad);
        if (format != DocValueFormat.RAW && anyResults) {
            builder.field(CommonFields.VALUE_AS_STRING.getPreferredName(), format.format(mad).toString());
        }

        return builder;
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(valueSketch);
    }

    @Override
    protected boolean doEquals(Object obj) {
        InternalMAD other = (InternalMAD) obj;
        return Objects.equals(valueSketch, other.valueSketch)
            && Objects.equals(deviationSketch, other.deviationSketch)
            && Objects.equals(method, other.method);
    }

    @Override
    public String getWriteableName() {
        return MADAggregationBuilder.NAME;
    }

    @Override
    public double value() {
        return getMAD();
    }

    // todo maybe - compute this when the object is constructed so we don't have to build a new tdigest for the deviations every time
    @Override
    public double getMAD() {
        return computeMAD(valueSketch, deviationSketch, method);
    }
}
