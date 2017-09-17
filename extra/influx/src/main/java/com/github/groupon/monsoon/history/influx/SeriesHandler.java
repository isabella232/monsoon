/*
 * Copyright (c) 2017, ariane
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.groupon.monsoon.history.influx;

import static com.github.groupon.monsoon.history.influx.InfluxUtil.TIME_COLUMN;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.groupon.lex.metrics.GroupName;
import com.groupon.lex.metrics.Histogram;
import com.groupon.lex.metrics.MetricName;
import com.groupon.lex.metrics.MetricValue;
import com.groupon.lex.metrics.SimpleGroupPath;
import com.groupon.lex.metrics.Tags;
import com.groupon.lex.metrics.lib.SimpleMapEntry;
import com.groupon.lex.metrics.timeseries.ImmutableTimeSeriesValue;
import com.groupon.lex.metrics.timeseries.SimpleTimeSeriesCollection;
import com.groupon.lex.metrics.timeseries.TimeSeriesCollection;
import com.groupon.lex.metrics.timeseries.TimeSeriesValue;
import java.time.Instant;
import java.util.Collection;
import static java.util.Collections.unmodifiableSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.influxdb.dto.QueryResult;
import org.joda.time.DateTime;

/**
 * Handles an ordered list of series and exposes them as TimeSeriesCollections.
 */
public class SeriesHandler {
    private final Multimap<DateTime, IntermediateTSV> datums = MultimapBuilder
            .treeKeys()
            .arrayListValues()
            .build();

    public Set<DateTime> keySet() {
        return unmodifiableSet(datums.keySet());
    }

    public Stream<TimeSeriesCollection> build() {
        return datums.asMap().entrySet().stream()
                .map(timestampedTsc -> buildTsc(timestampedTsc.getKey(), timestampedTsc.getValue()));
    }

    public void addSeries(QueryResult.Series series) {
        final GroupName group = seriesToGroupName(series);
        final Optional<Histogram.Range> range = rangeFromSeries(series);
        final int timeColumnIdx = InfluxUtil.getColumnIndexFromSeries(series, TIME_COLUMN).orElseThrow(() -> new IllegalStateException("missing time column"));

        series.getValues().forEach(row -> {
            assert series.getColumns().size() == row.size();

            final DateTime timestamp = new DateTime((Instant) row.get(timeColumnIdx));
            final IntermediateTSV valueMap = new IntermediateTSV(group);

            final ListIterator<String> columnIter = series.getColumns().listIterator();
            final Iterator<Object> rowIter = row.iterator();
            while (rowIter.hasNext()) {
                final int columnIdx = columnIter.nextIndex();
                final String columnName = columnIter.next();
                if (columnIdx != timeColumnIdx)
                    valueMap.addMetric(valueKeyToMetricName(columnName), range, seriesValueToMetricValue(rowIter.next()));
            }

            datums.put(timestamp, valueMap);
        });
    }

    public void merge(SeriesHandler other) {
        datums.putAll(other.datums);
    }

    private static TimeSeriesCollection buildTsc(DateTime timestamp, Collection<IntermediateTSV> c) {
        return new SimpleTimeSeriesCollection(timestamp, mergedTimeseriesValues(c));
    }

    private static Stream<TimeSeriesValue> mergedTimeseriesValues(Collection<IntermediateTSV> c) {
        return c.stream()
                .collect(Collectors.groupingBy(
                        IntermediateTSV::getGroup,
                        Collectors.reducing(IntermediateTSV::withMerged)))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(IntermediateTSV::build);
    }

    private static GroupName seriesToGroupName(QueryResult.Series series) {
        final SimpleGroupPath groupPath = pathStrToGroupPath(series.getName());
        final Tags tags = Tags.valueOf(series.getTags().entrySet().stream()
                .filter(tagEntry -> !Objects.equals(tagEntry.getKey(), InfluxUtil.MONSOON_RANGE_TAG))
                .filter(tagEntry -> tagEntry.getValue() != null)
                .map(tagEntry -> SimpleMapEntry.create(tagEntry.getKey(), tagValueToMetricValue(tagEntry.getValue()))));
        return GroupName.valueOf(groupPath, tags);
    }

    private static Optional<Histogram.Range> rangeFromSeries(QueryResult.Series series) {
        return series.getTags().entrySet().stream()
                .filter(tagEntry -> Objects.equals(tagEntry.getKey(), InfluxUtil.MONSOON_RANGE_TAG))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(rangeStr -> rangeStr.split(Pattern.quote(".."), 2))
                .map(parts -> new Histogram.Range(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])))
                .findAny();
    }

    private static SimpleGroupPath pathStrToGroupPath(String str) {
        return SimpleGroupPath.valueOf(str.split(Pattern.quote(".")));
    }

    private static MetricName valueKeyToMetricName(String str) {
        return MetricName.valueOf(str.split(Pattern.quote(".")));
    }

    private static MetricValue seriesValueToMetricValue(Object obj) {
        if (obj instanceof Boolean)
            return MetricValue.fromBoolean((Boolean) obj);
        if (obj instanceof Number)
            return MetricValue.fromNumberValue((Number) obj);
        assert obj instanceof String;
        return MetricValue.fromStrValue(obj.toString());
    }

    private static MetricValue tagValueToMetricValue(String str) {
        if ("true".equals(str))
            return MetricValue.TRUE;
        if ("false".equals(str))
            return MetricValue.FALSE;

        try {
            return MetricValue.fromIntValue(Long.parseLong(str));
        } catch (NumberFormatException ex) {
            /* SKIP: value is not an integer */
        }

        try {
            return MetricValue.fromDblValue(Double.parseDouble(str));
        } catch (NumberFormatException ex) {
            /* SKIP: value is not a floating point value */
        }

        return MetricValue.fromStrValue(str);
    }

    @RequiredArgsConstructor
    private static class IntermediateTSV {
        @Getter
        private final GroupName group;
        private final Map<MetricName, MetricValue> metrics = new HashMap<>();
        private final SetMultimap<MetricName, Histogram.RangeWithCount> histograms = MultimapBuilder
                .hashKeys()
                .hashSetValues() // Handle duplicate series correctly.
                .build();

        public TimeSeriesValue build() {
            final Stream<Map.Entry<MetricName, MetricValue>> metricsStream = metrics.entrySet().stream()
                    .filter(metricEntry -> !histograms.containsKey(metricEntry.getKey()));
            final Stream<Map.Entry<MetricName, MetricValue>> histogramsStream = histograms.asMap().entrySet().stream()
                    .map(histogramMetric -> {
                        final MetricValue histogram = MetricValue.fromHistValue(new Histogram(histogramMetric.getValue().stream()));
                        return SimpleMapEntry.create(histogramMetric.getKey(), histogram);
                    });
            return new ImmutableTimeSeriesValue(group, Stream.concat(metricsStream, histogramsStream), Map.Entry::getKey, Map.Entry::getValue);
        }

        private void addMetric(MetricName name, MetricValue value) {
            metrics.put(name, value);
        }

        private void addMetric(MetricName name, Histogram.Range range, MetricValue value) {
            final double count = value.value()
                    .orElseThrow(() -> new IllegalArgumentException("expected floating point value for range value"))
                    .doubleValue();
            histograms.put(name, new Histogram.RangeWithCount(range, count));
        }

        public void addMetric(MetricName name, Optional<Histogram.Range> range, MetricValue value) {
            if (range.isPresent())
                addMetric(name, range.get(), value);
            else
                addMetric(name, value);
        }

        public IntermediateTSV withMerged(IntermediateTSV other) {
            assert Objects.equals(group, other.group);

            metrics.putAll(other.metrics);
            histograms.putAll(other.histograms);
            return this;
        }
    }
}
