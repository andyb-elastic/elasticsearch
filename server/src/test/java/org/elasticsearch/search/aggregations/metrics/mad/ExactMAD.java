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

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Arrays;

/**
 * This class is an implementation of median absolute deviation that computes an exact value, rather than an approximation. It's used to
 * verify that the aggregation's results are close enough to the exact result
 */
public class ExactMAD {

    public static double calculateMAD(long[] sample) {
        return calculateMAD(Arrays.stream(sample)
            .mapToDouble(point -> (double) point)
            .toArray());
    }

    public static double calculateMAD(double[] sample) {
        final double median = calculateMedian(sample);

        final double[] deviations = Arrays.stream(sample)
            .map(point -> Math.abs(median - point))
            .toArray();

        final double mad = calculateMedian(deviations);
        return mad;
    }

    private static double calculateMedian(double[] sample) {
        final double[] sorted = Arrays.copyOf(sample, sample.length);
        Arrays.sort(sorted);

        final int halfway =  (int) Math.ceil(sorted.length / 2d);
        final double median = (sorted[halfway - 1] + sorted[halfway]) / 2d;
        return median;
    }

    public static class IsCloseToRelative extends TypeSafeMatcher<Double> {

        private final double expected;
        private final double error;

        public IsCloseToRelative(double expected, double error) {
            this.expected = expected;
            this.error = error;
        }

        @Override
        protected boolean matchesSafely(Double actual) {
            final double deviation = Math.abs(actual - expected);
            final double observedError = deviation / Math.abs(expected);
            return observedError <= error;
        }

        @Override
        public void describeTo(Description description) {
            description
                .appendText("within ")
                .appendValue(error * 100)
                .appendText(" percent of ")
                .appendValue(expected);
        }

        public static IsCloseToRelative closeToRelative(double expected, double error) {
            return new IsCloseToRelative(expected, error);
        }

        public static IsCloseToRelative closeToRelative(double expected) {
            return closeToRelative(expected, 0.05);
        }
    }
}
