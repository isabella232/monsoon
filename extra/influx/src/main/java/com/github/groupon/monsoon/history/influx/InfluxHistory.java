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

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import com.groupon.lex.metrics.GroupName;
import com.groupon.lex.metrics.Histogram;
import com.groupon.lex.metrics.MetricName;
import com.groupon.lex.metrics.MetricValue;
import com.groupon.lex.metrics.history.CollectHistory;
import com.groupon.lex.metrics.history.HistoryContext;
import static com.groupon.lex.metrics.history.HistoryContext.LOOK_BACK;
import static com.groupon.lex.metrics.history.HistoryContext.LOOK_FORWARD;
import com.groupon.lex.metrics.history.IntervalIterator;
import com.groupon.lex.metrics.lib.SimpleMapEntry;
import com.groupon.lex.metrics.timeseries.ExpressionLookBack;
import com.groupon.lex.metrics.timeseries.TimeSeriesCollection;
import com.groupon.lex.metrics.timeseries.TimeSeriesMetricFilter;
import com.groupon.lex.metrics.timeseries.TimeSeriesValue;
import com.groupon.lex.metrics.timeseries.expression.Context;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;

public class InfluxHistory extends InfluxUtil implements CollectHistory, AutoCloseable {
    private static final int TARGET_BATCH_SIZE = 10000;
    private static final Logger LOG = Logger.getLogger(InfluxHistory.class.getName());
    private final boolean verifyDatabaseConnection;
    private volatile int targetBatchSize = TARGET_BATCH_SIZE;

    public InfluxHistory(InfluxDB influxDB, String database) {
        this(influxDB, database, false);
    }

    public InfluxHistory(InfluxDB influxDB, String database, boolean verifyDatabaseConnection) {
        super(influxDB, database);
        this.verifyDatabaseConnection = verifyDatabaseConnection;
        if (verifyDatabaseConnection && !influxDB.databaseExists(database))
            throw new IllegalArgumentException("database does not exist");
    }

    @Override
    public void close() {
        getInfluxDB().close();
    }

    @Override
    public boolean add(@NonNull TimeSeriesCollection tsdata) {
        return addAll(singleton(tsdata));
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends TimeSeriesCollection> c) {
        return addAll(c.iterator());
    }

    @Override
    public boolean addAll(@NonNull Iterator<? extends TimeSeriesCollection> i) {
        boolean changed = false;
        final UnmodifiableIterator<List<Map.Entry<DateTime, TimeSeriesValue>>> batchPointIter = asBatchPointIteration_(i);
        while (batchPointIter.hasNext()) {
            write_(batchPointIter.next());
            changed = true;
        }

        return changed;
    }

    public int getTargetBatchSize() {
        return targetBatchSize;
    }

    public void setTargetBatchSize(int targetBatchSize) {
        if (targetBatchSize < 1)
            throw new IllegalArgumentException("targetBatchSize must be at least 1");
        this.targetBatchSize = targetBatchSize;
    }

    private void write_(List<Map.Entry<DateTime, TimeSeriesValue>> tsvList) {
        try {
            final Point[] points = tsvList.stream()
                    .flatMap(timestampedTsv -> timeSeriesValueToPoint(timestampedTsv.getValue(), timestampedTsv.getKey()))
                    .toArray(Point[]::new);
            getInfluxDB().write(BatchPoints.database(getDatabase()).points(points).build());
        } catch (InfluxDBException ex) {
            if (ex.getCause() instanceof SocketTimeoutException)
                LOG.log(Level.WARNING, "influx write timed out"); // Influx documentation says this might happen
            else
                throw ex;
        }
    }

    private UnmodifiableIterator<List<Map.Entry<DateTime, TimeSeriesValue>>> asBatchPointIteration_(@NonNull Iterator<? extends TimeSeriesCollection> i) {
        return Iterators.partition(
                Iterators.concat(
                        Iterators.transform(i, tsdata -> {
                            final DateTime timestamp = tsdata.getTimestamp();
                            LOG.log(Level.INFO, "Preparing TSData {0} for write", timestamp);
                            return Iterators.transform(tsdata.getTSValues().iterator(),
                                    tsv -> SimpleMapEntry.create(timestamp, tsv));
                        })),
                targetBatchSize);
    }

    @Override
    public long getFileSize() {
        final QueryResult result = getInfluxDB().query(new Query(String.format("select sum(\"diskBytes\") as \"diskBytes\" from (select last(\"diskBytes\"::field) as \"diskBytes\" from \"shard\" where \"database\"::tag = '%s' group by * limit 1)", getDatabase().replace("'", "\\'")), "_internal"), TimeUnit.MILLISECONDS);
        throwOnResultError(result);
        return result.getResults().stream()
                .filter(r -> !r.hasError())
                .filter(r -> r.getSeries() != null)
                .flatMap(r -> r.getSeries().stream())
                .map(s -> getColumnFromSeries(s, "diskBytes"))
                .filter(Optional::isPresent)
                .flatMap(Optional::get)
                .map(Number.class::cast)
                .mapToLong(Number::longValue)
                .findAny()
                .orElse(0);
    }

    @Override
    public DateTime getEnd() {
        final QueryResult result = getInfluxDB().query(new Query("select * from /.*/ order by time desc limit 1", getDatabase()), TimeUnit.MILLISECONDS);
        throwOnResultError(result);
        return extractTimestamps(result)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> new DateTime(DateTimeZone.UTC));
    }

    public Optional<DateTime> getBegin() {
        final QueryResult result = getInfluxDB().query(new Query("select * from /.*/ order by time asc limit 1", getDatabase()), TimeUnit.MILLISECONDS);
        throwOnResultError(result);
        return extractTimestamps(result)
                .min(Comparator.naturalOrder());
    }

    @Override
    public Stream<TimeSeriesCollection> streamReversed() {
        return StreamSupport.stream(
                () -> Spliterators.spliteratorUnknownSize(new ReverseStream(getInfluxDB(), getDatabase(), getEnd()), ReverseStream.SPLITERATOR_CHARACTERISTICS),
                ReverseStream.SPLITERATOR_CHARACTERISTICS,
                false);
    }

    @Override
    public Stream<TimeSeriesCollection> stream() {
        return stream(TimeSeriesMetricFilter.ALL_GROUPS);
    }

    public Stream<TimeSeriesCollection> stream(@NonNull TimeSeriesMetricFilter filter) {
        return StreamSupport.stream(
                () -> {
                    final DateTime end = getEnd();
                    final DateTime begin = getBegin().map(b -> b.minus(1)).orElse(end);
                    return new ForwardStream(getInfluxDB(), getDatabase(), begin, end, filter);
                },
                ForwardStream.SPLITERATOR_CHARACTERISTICS,
                false);
    }

    public Stream<TimeSeriesCollection> stream(@NonNull Duration stepsize, @NonNull TimeSeriesMetricFilter filter) {
        return IntervalIterator.stream(stream(filter), stepsize, LOOK_BACK, LOOK_FORWARD);
    }

    @Override
    public Stream<TimeSeriesCollection> stream(DateTime begin) {
        return stream(begin, TimeSeriesMetricFilter.ALL_GROUPS);
    }

    public Stream<TimeSeriesCollection> stream(@NonNull DateTime begin, @NonNull TimeSeriesMetricFilter filter) {
        return StreamSupport.stream(
                () -> new ForwardStream(getInfluxDB(), getDatabase(), begin.minus(1), getEnd(), filter),
                ForwardStream.SPLITERATOR_CHARACTERISTICS,
                false);
    }

    public Stream<TimeSeriesCollection> stream(DateTime begin, Duration stepsize, TimeSeriesMetricFilter filter) {
        return IntervalIterator.stream(stream(begin.minus(LOOK_BACK), filter), stepsize, LOOK_BACK, LOOK_FORWARD)
                .filter(ts -> !ts.getTimestamp().isBefore(begin));
    }

    @Override
    public Stream<TimeSeriesCollection> stream(DateTime begin, DateTime end) {
        return stream(begin, end, TimeSeriesMetricFilter.ALL_GROUPS);
    }

    public Stream<TimeSeriesCollection> stream(@NonNull DateTime begin, @NonNull DateTime end, @NonNull TimeSeriesMetricFilter filter) {
        return StreamSupport.stream(
                () -> new ForwardStream(getInfluxDB(), getDatabase(), begin.minus(1), end, filter),
                ForwardStream.SPLITERATOR_CHARACTERISTICS,
                false);
    }

    public Stream<TimeSeriesCollection> stream(DateTime begin, DateTime end, Duration stepsize, TimeSeriesMetricFilter filter) {
        return IntervalIterator.stream(stream(begin.minus(LOOK_BACK), end.plus(LOOK_FORWARD), filter), stepsize, LOOK_BACK, LOOK_FORWARD)
                .filter(ts -> !ts.getTimestamp().isBefore(begin) && !ts.getTimestamp().isAfter(end));
    }

    @Override
    public Stream<Context> getContext(Duration stepsize, ExpressionLookBack lookback, TimeSeriesMetricFilter filter) {
        return HistoryContext.stream(stream(stepsize, filter), lookback);
    }

    @Override
    public Stream<Context> getContext(DateTime begin, Duration stepsize, ExpressionLookBack lookback, TimeSeriesMetricFilter filter) {
        return HistoryContext.stream(stream(begin.minus(lookback.hintDuration()), stepsize, filter), lookback)
                .filter(ctx -> !ctx.getTSData().getCurrentCollection().getTimestamp().isBefore(begin));
    }

    @Override
    public Stream<Context> getContext(DateTime begin, DateTime end, Duration stepsize, ExpressionLookBack lookback, TimeSeriesMetricFilter filter) {
        return HistoryContext.stream(stream(begin.minus(lookback.hintDuration()), end, stepsize, filter), lookback)
                .filter(ctx -> !ctx.getTSData().getCurrentCollection().getTimestamp().isBefore(begin));
    }

    private Stream<Point> timeSeriesValueToPoint(@NonNull TimeSeriesValue tsv, @NonNull DateTime timestamp) {
        final PointBuilder pointBuilder = new PointBuilder(new PointBuilderTemplate(tsv.getGroup(), timestamp), verifyDatabaseConnection);
        tsv.getMetrics().forEach(pointBuilder::addMetric);
        return pointBuilder.stream()
                .map(Point.Builder::build);
    }

    private static class PointBuilderTemplate {
        private final long timestampMillis;
        private final String measurement;
        private final Map<String, String> tags;

        public PointBuilderTemplate(@NonNull GroupName group, @NonNull DateTime timestamp) {
            this.timestampMillis = timestamp.getMillis();
            this.measurement = String.join(".", group.getPath().getPath());
            this.tags = unmodifiableMap(group.getTags().stream()
                    .map((tagEntry) -> {
                        assert tagEntry.getValue().isPresent() && (tagEntry.getValue().getBoolValue() != null
                                || tagEntry.getValue().getIntValue() != null
                                || tagEntry.getValue().getFltValue() != null
                                || tagEntry.getValue().getStrValue() != null);

                        final String value;
                        if (tagEntry.getValue().getBoolValue() != null)
                            value = tagEntry.getValue().getBoolValue().toString();
                        else if (tagEntry.getValue().getIntValue() != null)
                            value = tagEntry.getValue().getIntValue().toString();
                        else if (tagEntry.getValue().getFltValue() != null)
                            value = tagEntry.getValue().getFltValue().toString();
                        else if (tagEntry.getValue().getStrValue() != null)
                            value = tagEntry.getValue().getStrValue();
                        else /* UNREACHABLE */
                            return null;

                        return SimpleMapEntry.create(tagEntry.getKey(), value);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        public Point.Builder getBuilder() {
            return Point.measurement(measurement)
                    .time(timestampMillis, TimeUnit.MILLISECONDS)
                    .tag(tags);
        }

        public Point.Builder getBuilder(@NonNull Histogram.Range range) {
            return getBuilder()
                    .tag(MONSOON_RANGE_TAG, range.getFloor() + ".." + range.getCeil());
        }
    }

    @RequiredArgsConstructor
    private static class PointBuilder {
        @NonNull
        private final PointBuilderTemplate template;
        private final boolean verifyDatabaseConnection;

        private final List<Point.Builder> plainValue = new ArrayList<>(1);
        private final Map<Histogram.Range, Point.Builder> histogramValue = new HashMap<>();

        public void addMetric(MetricName name, MetricValue value) {
            if (!value.isPresent() || value.isInfiniteOrNaN()) return;
            final String nameStr = String.join(".", name.getPath());

            assert value.getBoolValue() != null
                    || value.getIntValue() != null
                    || value.getFltValue() != null
                    || value.getStrValue() != null
                    || value.getHistValue() != null;

            if (value.getBoolValue() != null)
                getOrCreatePlain().addField(nameStr, value.getBoolValue());
            if (value.getIntValue() != null)
                getOrCreatePlain().addField(nameStr, value.getIntValue());
            if (value.getFltValue() != null)
                getOrCreatePlain().addField(nameStr, value.getFltValue());
            if (value.getStrValue() != null)
                getOrCreatePlain().addField(nameStr, value.getStrValue());
            if (value.getHistValue() != null) {
                value.getHistValue().stream()
                        .forEach((rangeWithCount) -> {
                            histogramValue.computeIfAbsent(rangeWithCount.getRange(), template::getBuilder)
                                    .addField(nameStr, rangeWithCount.getCount());
                        });
            }
        }

        private Point.Builder getOrCreatePlain() {
            if (plainValue.isEmpty()) {
                final Point.Builder newPlainValue = template.getBuilder();
                plainValue.add(newPlainValue);
                return newPlainValue;
            }

            Point.Builder last = plainValue.get(plainValue.size() - 1);
            if (verifyDatabaseConnection && last.build().lineProtocol().length() >= 32768) {
                last = template.getBuilder();
                plainValue.add(last);
            }
            return last;
        }

        public Stream<Point.Builder> stream() {
            final Stream<Point.Builder> plainStream = plainValue.stream();
            final Stream<Point.Builder> histogramStream = histogramValue.values().stream();
            return Stream.concat(plainStream, histogramStream);
        }
    }
}
