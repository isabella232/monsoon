/*
 * Copyright (c) 2016, Groupon, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * Neither the name of GROUPON nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.groupon.lex.metrics.history;

import com.groupon.lex.metrics.timeseries.InterpolatedTSC;
import com.groupon.lex.metrics.timeseries.TimeSeriesCollection;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SORTED;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.NonNull;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/**
 * An iterator wrapper that changes an iteration over Collections into a windowed Collections.
 *
 * The underlying iterator must emit unique collections in chronological order.
 * @author ariane
 */
public class WindowedTSCIterator implements Iterator<TimeSeriesCollection> {
    /** Iterator supplying items. */
    private final Iterator<TimeSeriesCollection> underlying;
    /** Window duration. */
    private final Duration lookBack, lookForward;
    /** Past window items in reverse chronological order. */
    private final Deque<TimeSeriesCollection> past = new ArrayDeque<>();
    /** Future window items in chronological order. */
    private final Deque<TimeSeriesCollection> future = new ArrayDeque<>();
    /** Look ahead item from underlying iterator. */
    private TimeSeriesCollection underlyingNext;

    public static Stream<TimeSeriesCollection> stream(Iterator<TimeSeriesCollection> in, Duration lookBack, Duration lookForward) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new WindowedTSCIterator(in, lookBack, lookForward),
                        NONNULL | IMMUTABLE | ORDERED | SORTED | DISTINCT),
                false);
    }

    public static Stream<TimeSeriesCollection> stream(Stream<TimeSeriesCollection> in, Duration lookBack, Duration lookForward) {
        return stream(in.iterator(), lookBack, lookForward);
    }

    public WindowedTSCIterator(@NonNull Iterator<TimeSeriesCollection> underlying, @NonNull Duration lookBack, @NonNull Duration lookForward) {
        this.underlying = underlying;
        this.lookBack = lookBack;
        this.lookForward = lookForward;
        consumeUnderlyingNext();  // Initialize underlyingNext.
    }

    @Override
    public boolean hasNext() {
        return !future.isEmpty() || underlyingNext != null;
    }

    @Override
    public TimeSeriesCollection next() {
        final TimeSeriesCollection winPresent = updateWindow();  // Must happen before creating winFuture.
        past.addFirst(winPresent);
        return interpolated(winPresent, past, future);
    }

    private TimeSeriesCollection updateWindow() {
        if (future.isEmpty()) {
            if (underlyingNext != null) {
                future.add(underlyingNext);
                consumeUnderlyingNext();
            } else {
                throw new NoSuchElementException();
            }
        }

        final TimeSeriesCollection next = future.removeFirst();

        // Remove old entries outside the lookBack window.
        final DateTime tsBegin = next.getTimestamp().minus(lookBack);
        while (!past.isEmpty() && past.getLast().getTimestamp().isBefore(tsBegin))
            past.removeLast();

        // Include new entries inside the lookForward window.
        final DateTime tsEnd = next.getTimestamp().plus(lookForward);
        while (underlyingNext != null) {
            if (underlyingNext.getTimestamp().isAfter(tsEnd))
                break;
            future.addLast(underlyingNext);
            consumeUnderlyingNext();
        }

        return next;
    }

    private void consumeUnderlyingNext() {
        if (underlying.hasNext())
            underlyingNext = underlying.next();
        else
            underlyingNext = null;
    }

    private TimeSeriesCollection interpolated(TimeSeriesCollection present, Collection<TimeSeriesCollection> past, Collection<TimeSeriesCollection> future) {
        if (future.isEmpty() || past.isEmpty())
            return present;

        return new InterpolatedTSC(present, past, future);
    }
}
