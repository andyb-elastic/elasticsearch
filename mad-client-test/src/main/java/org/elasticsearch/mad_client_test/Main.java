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

package org.elasticsearch.mad_client_test;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.distribution.ParetoDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.http.HttpHost;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.mad.MedianAbsoluteDeviation;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class Main {

    private static class Benchmark {

        public final int size;
        public final Density density;
        public final Distribution distribution;
        public final double compression;

        public Benchmark(int size, Density density, Distribution distribution, double compression) {
            this.size = size;
            this.density = density;
            this.distribution = distribution;
            this.compression = compression;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                .append(getClass().getName())
                .append("<")
                .append("size=[").append(size).append("]")
                .append(", ")
                .append("density=[").append(density).append("]")
                .append(", ")
                .append("distribution=[").append(distribution).append("]")
                .append(", ")
                .append("compression=[").append(compression).append("]")
                .append(">")
                .toString();
        }
    }

    private static class Result {

        public final double exactMAD;
        public final double[] aggMeasurements;
        public final double aggMedianOfMeasurements;
        public final double aggMADOfMeasurements;
        public final double absoluteErrorFromAggMedian;
        public final double relativeErrorFromAggMedian;

        public Result(double exactMAD, double[] aggMeasurements) {
            this.exactMAD = exactMAD;
            this.aggMeasurements = aggMeasurements;
            this.aggMedianOfMeasurements = median(this.aggMeasurements);
            this.aggMADOfMeasurements = exactMAD(this.aggMeasurements);
            this.absoluteErrorFromAggMedian = Math.abs(this.exactMAD - this.aggMedianOfMeasurements);
            this.relativeErrorFromAggMedian = Math.abs(1 - (this.aggMedianOfMeasurements / this.exactMAD));
        }
    }

    private enum Density { SPARSE, DENSE }

    private enum Distribution {

        UNIFORM {
            @Override
            public double[] sample(int size, Density density, RandomGenerator random) {
                final int lower;
                final int upper;
                if (density == Density.DENSE) {
                    lower = - size / 4;
                    upper = size / 4;
                } else {
                    lower = - size * 2;
                    upper = size * 2;
                }
                final UniformRealDistribution distribution = new UniformRealDistribution(random, lower, upper);
                return distribution.sample(size);
            }
        },
        NORMAL {
            @Override
            public double[] sample(int size, Density density, RandomGenerator random) {
                final double standardDeviation;
                if (density == Density.DENSE) {
                    standardDeviation = (size / 2d) / 3d;
                } else {
                    standardDeviation = (size * 4) / 3d;
                }

                final NormalDistribution distribution = new NormalDistribution(random, 0, standardDeviation,
                    NormalDistribution.DEFAULT_INVERSE_ABSOLUTE_ACCURACY);
                return distribution.sample(size);
            }
        },
        PARETO {
            // this distribution's probability density function is pretty dense regardless of the shape parameter chosen
            @Override
            public double[] sample(int size, Density density, RandomGenerator random) {
                final double shape;
                if (density == Density.DENSE) {
                    shape = 10;
                } else {
                    shape = 0.1;
                }

                final ParetoDistribution distribution = new ParetoDistribution(random, 1, shape);
                return distribution.sample(size);
            }
        };

        public abstract double[] sample(int size, Density density, RandomGenerator random);
    }

    private static final int BULK_SIZE = 1000;
    private static final int NUMBER_OF_MEASUREMENTS = 10;

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        final int iterations = System.getProperties().containsKey("benchmark.iterations")
            ? Integer.valueOf(System.getProperty("benchmark.iterations"))
            : 10;

        final String host = System.getProperties().containsKey("benchmark.host")
            ? System.getProperty("benchmark.host")
            : "localhost";

        final int port = System.getProperties().containsKey("benchmark.port")
            ? Integer.valueOf(System.getProperty("benchmark.port"))
            : 9200;

        final Path output = System.getProperties().containsKey("benchmark.output")
            ? Paths.get(System.getProperty("benchmark.output"))
            : Paths.get(System.getProperty("user.dir")).resolve("benchmark-" + System.currentTimeMillis());

        final Map<String, String> tags = System.getProperties().containsKey("benchmark.tags")
            ? stringToTags(System.getProperty("benchmark.tags"))
            : Collections.emptyMap();

        final List<Integer> sizes = System.getProperties().containsKey("benchmark.sizes")
            ? Arrays.stream(System.getProperty("benchmark.sizes").split(",")).map(Integer::valueOf).collect(Collectors.toList())
            : Arrays.asList(100, 1000, 10000);

        final List<Density> densities = System.getProperties().containsKey("benchmark.densities")
            ? Arrays.stream(System.getProperty("benchmark.densities").split(","))
                .map(p -> p.toUpperCase(Locale.ROOT))
                .map(Density::valueOf)
                .collect(Collectors.toList())
            : Arrays.asList(Density.values());

        final List<Distribution> distributions = System.getProperties().containsKey("benchmark.distributions")
            ? Arrays.stream(System.getProperty("benchmark.distributions").split(","))
                .map(p -> p.toUpperCase(Locale.ROOT))
                .map(Distribution::valueOf)
                .collect(Collectors.toList())
            : Arrays.asList(Distribution.values());

        final List<Double> compressions = System.getProperties().containsKey("benchmark.compressions")
            ? Arrays.stream(System.getProperty("benchmark.compressions").split(",")).map(Double::valueOf).collect(Collectors.toList())
            : Arrays.asList(20d, 100d, 1000d);

        final long seed = System.getProperties().containsKey("benchmark.seed")
            ? Long.valueOf(System.getProperty("benchmark.seed"))
            : System.currentTimeMillis();

        final int numberOfShards = System.getProperties().containsKey("benchmark.number_of_shards")
            ? Integer.valueOf(System.getProperty("benchmark.number_of_shards"))
            : 4;

        final int numberOfReplicas = System.getProperties().containsKey("benchmark.number_of_replicas")
            ? Integer.valueOf(System.getProperty("benchmark.number_of_replicas"))
            : 0;

        logger.error("System properties [{}]", System.getProperties());

        runBenchmarks(
            iterations,
            host,
            port,
            output,
            tags,
            sizes,
            densities,
            distributions,
            compressions,
            seed,
            numberOfShards,
            numberOfReplicas
        );
    }

    public static Map<String, String> stringToTags(String string) {
        final Map<String, String> tags = new HashMap<>();
        String[] pairs = string.split(",");
        for (String pair : pairs) {
            String[] split = pair.split(":");
            if (split.length != 2) {
                throw new IllegalArgumentException("Could not parse tags at: " + pair);
            }
            tags.put(split[0], split[1]);
        }

        return tags;
    }


    private static void runBenchmarks(int iterations,
                                      String host,
                                      int port,
                                      Path output,
                                      Map<String, String> tags,
                                      List<Integer> sizes,
                                      List<Density> densities,
                                      List<Distribution> distributions,
                                      List<Double> compressions,
                                      long seed,
                                      int numberOfShards,
                                      int numberOfReplicas) throws Exception {

        final List<Benchmark> benchmarks = new ArrayList<>();
        for (int size : sizes) {
            for (Density density : densities) {
                for (Distribution distribution : distributions) {
                    for (double compression : compressions) {
                        benchmarks.add(new Benchmark(size, density, distribution, compression));
                    }
                }
            }
        }

        logger.error("Running [{}] total benchmarking scenarios", benchmarks.size());

        final RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"));
        try (RestHighLevelClient client = new RestHighLevelClient(builder);
             BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {

            for (Benchmark benchmark : benchmarks) {
                final List<Result> results = runBenchmark(benchmark, client, iterations, seed, numberOfShards, numberOfReplicas);

                XContentBuilder jsonBuilder = XContentFactory.jsonBuilder();
                jsonBuilder.startObject()
                    .field("size", benchmark.size)
                    .field("density", benchmark.density)
                    .field("distribution", benchmark.distribution)
                    .field("compression", benchmark.compression)
                    .field("seed", seed)
                    .field("iterations", iterations)
                    .field("output", output)
                    .field("tags").map(tags)
                    .field("number_of_shards", numberOfShards)
                    .field("number_of_replicas", numberOfReplicas)
                    .startArray("results");

                for (Result result : results) {
                    jsonBuilder.startObject()
                        .field("exact", result.exactMAD)
                        .field("agg_median_of_measurements", result.aggMedianOfMeasurements)
                        .field("agg_mad_of_measurements", result.aggMADOfMeasurements)
                        .field("absolute_error_from_agg_median_of_measurements", result.absoluteErrorFromAggMedian)
                        .field("relative_error_from_agg_median_of_measurements", result.relativeErrorFromAggMedian)
                        .array("agg_measurements", result.aggMeasurements)
                        .endObject();

                }

                jsonBuilder.endArray()
                    .endObject();

                writer.write(BytesReference.bytes(jsonBuilder).utf8ToString());
                writer.newLine();
            }
        }
    }

    private static List<Result> runBenchmark(Benchmark benchmark,
                                       RestHighLevelClient client,
                                       int iterations,
                                       long seed,
                                       int numberOfShards,
                                       int numberOfReplicas) throws IOException {


        logger.error("Running [{}] iterations of benchmark with settings [{}]", iterations, benchmark);

        final List<Result> results = new ArrayList<>(iterations);
        final RandomGenerator random = new JDKRandomGenerator();
        random.setSeed(seed);
        for (int i = 0; i < iterations; i++) {
            logger.error("Running benchmark with settings [{}] iteration [{}]", benchmark, i);
            results.add(runIteration(benchmark, client, random, numberOfShards, numberOfReplicas));
        }
        return results;
    }

    private static Result runIteration(Benchmark benchmark,
                                       RestHighLevelClient client,
                                       RandomGenerator random,
                                       int numberOfShards,
                                       int numberOfReplicas) throws IOException {

        final String indexName = Long.toHexString(new Random().nextLong()).toLowerCase(Locale.ROOT);
        final CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName)
            .settings(Settings.builder()
                .put("index.number_of_shards", numberOfShards)
                .put("index.number_of_replicas", numberOfReplicas)
                .put("index.requests.cache.enable", false))
            .mapping("doc", "value", "type=double");
        logger.error("Creating index [{}] with [{}] shards and [{}] replicas", indexName, numberOfShards, numberOfReplicas);
        client.indices().create(createIndexRequest, RequestOptions.DEFAULT);

        final double[] sample = benchmark.distribution.sample(benchmark.size, benchmark.density, random);

        final List<BulkRequest> bulkRequests = new ArrayList<>();
        for (int i = 0; i < sample.length; i += BULK_SIZE) {
            final int lastIndex = i + BULK_SIZE > sample.length
                ? sample.length
                : i + BULK_SIZE;
            final BulkRequest bulk = new BulkRequest();
            for (int m = i; m < lastIndex; m++) {
                bulk.add(new IndexRequest(indexName, "doc")
                    .source(XContentType.JSON, "value", sample[m]));
            }
            bulkRequests.add(bulk);
        }

        logger.error("Indexing [{}] documents to index [{}]", benchmark.size, indexName);
        for (BulkRequest bulk : bulkRequests) {
            client.bulk(bulk, RequestOptions.DEFAULT);
        }

        logger.error("Refreshing index [{}]", indexName);
        client.indices().refresh(new RefreshRequest(indexName), RequestOptions.DEFAULT);

        final List<MedianAbsoluteDeviation> responses = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_MEASUREMENTS; i++) {
            final SearchRequest request = new SearchRequest(indexName);
            final SearchSourceBuilder builder = new SearchSourceBuilder();
            request.requestCache(false);
            builder.size(0);
            builder.query(QueryBuilders.matchAllQuery());
            builder.aggregation(AggregationBuilders.medianAbsoluteDeviation("mad")
                .field("value")
                .setCompression(benchmark.compression));
            request.source(builder);
            logger.error("Sending search request to index [{}]", indexName);
            final SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            final MedianAbsoluteDeviation mad = response.getAggregations().get("mad");
            assert mad != null;
            responses.add(mad);
        }

        final DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        logger.error("Deleting index [{}]", indexName);
        client.indices().delete(deleteIndexRequest, RequestOptions.DEFAULT);

        return new Result(exactMAD(sample), responses.stream().mapToDouble(MedianAbsoluteDeviation::getMAD).toArray());
    }

    private static double exactMAD(double[] sample) {
        final double median = median(sample);

        final double[] deviations = Arrays.stream(sample)
            .map(point -> Math.abs(median - point))
            .toArray();

        final double mad = median(deviations);
        return mad;
    }

    private static double median(double[] sample) {
        final double[] sorted = Arrays.copyOf(sample, sample.length);
        Arrays.sort(sorted);

        if (sample.length == 0) {
            return Double.NaN;
        } else {
            final int lower = (sample.length - 1) / 2;
            final int higher = sample.length / 2;
            return (sorted[lower] + sorted[higher]) / 2d;
        }


    }
}
