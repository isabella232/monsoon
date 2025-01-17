package com.groupon.lex.metrics.lib;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

public final class BufferedIterator<T> {
    private static final Logger LOG = Logger.getLogger(BufferedIterator.class.getName());
    private static final AtomicInteger THR_IDX = new AtomicInteger();
    private static final Executor DFL_WORK_QUEUE = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            final Thread thr = new Thread(r);
            thr.setDaemon(true);
            thr.setName("BufferedIterator-thread-" + THR_IDX.getAndIncrement());
            return thr;
        }
    });
    private static final int DFL_BUFFER_LEN = 16;

    private final Executor work_queue_;
    private final Iterator<? extends T> iter_;
    private final List<T> queue_;
    private Exception exception = null;
    private boolean at_end_;
    private final int queue_size_;
    private boolean running_ = false;
    private Runnable wakeup_ = null;

    public BufferedIterator(Executor work_queue, Iterator<? extends T> iter, int queue_size) {
        if (queue_size <= 0)
            throw new IllegalArgumentException("queue size must be at least 1");
        work_queue_ = requireNonNull(work_queue);
        iter_ = requireNonNull(iter);
        queue_size_ = queue_size;
        queue_ = new LinkedList<>();
        at_end_ = false;

        fire_();
    }

    public BufferedIterator(Iterator<? extends T> iter, int queue_size) {
        this(DFL_WORK_QUEUE, iter, queue_size);
    }

    public BufferedIterator(Executor work_queue, Iterator<? extends T> iter) {
        this(work_queue, iter, DFL_BUFFER_LEN);
    }

    public BufferedIterator(Iterator<? extends T> iter) {
        this(DFL_WORK_QUEUE, iter);
    }

    public synchronized boolean atEnd() {
        return at_end_ && queue_.isEmpty() && exception == null;
    }

    public synchronized boolean nextAvail() {
        return !queue_.isEmpty() || exception != null;
    }

    public void waitAvail() throws InterruptedException {
        synchronized (this) {
            if (nextAvail() || atEnd()) return;
        }

        final WakeupListener w = new WakeupListener(() -> nextAvail() || atEnd());
        setWakeup(w::wakeup);
        ForkJoinPool.managedBlock(w);
    }

    public void waitAvail(long tv, TimeUnit tu) throws InterruptedException {
        synchronized (this) {
            if (nextAvail() || atEnd()) return;
        }

        final Delay w = new Delay(() -> {
        }, tv, tu);
        setWakeup(w::deliverWakeup);
        try {
            ForkJoinPool.managedBlock(w);
        } catch (InterruptedException ex) {
            throw ex;
        }
    }

    @SneakyThrows
    public synchronized T next() {
        if (exception != null) throw exception;
        try {
            final T result = queue_.remove(0);
            fire_();
            return result;
        } catch (IndexOutOfBoundsException ex) {
            LOG.log(Level.SEVERE, "next() called on empty queue!", ex);
            throw ex;
        }
    }

    public void setWakeup(Runnable wakeup) {
        requireNonNull(wakeup);
        boolean run_immediately_ = false;
        synchronized (this) {
            if (!queue_.isEmpty() || at_end_) {
                run_immediately_ = true;
                wakeup_ = null;
            } else {
                wakeup_ = wakeup;
            }
        }
        if (run_immediately_) work_queue_.execute(wakeup);
    }

    public void setWakeup(Runnable wakeup, long tv, TimeUnit tu) {
        final Delay delay = new Delay(wakeup, tv, tu);
        setWakeup(delay::deliverWakeup);
        work_queue_.execute(delay);
    }

    private synchronized void fire_() {
        if (at_end_) return;
        if (queue_.size() >= queue_size_) return;
        if (exception != null) return;

        if (!running_) {
            running_ = true;
            work_queue_.execute(this::add_next_iter_);
        }
    }

    private void add_next_iter_() {
        final long deadline = System.currentTimeMillis() + 50;  // Don't hog the queue, requeue once deadline expires.

        try {
            boolean stop_loop = false;
            while (!stop_loop && queue_.size() < queue_size_) {
                if (iter_.hasNext()) {
                    final T next = iter_.next();
                    final Optional<Runnable> wakeup;
                    synchronized (this) {
                        queue_.add(next);
                        wakeup = Optional.ofNullable(wakeup_);
                        wakeup_ = null;
                    }
                    wakeup.ifPresent(this.work_queue_::execute);
                } else {
                    final Optional<Runnable> wakeup;
                    synchronized (this) {
                        at_end_ = true;
                        stop_loop = true;
                        wakeup = Optional.ofNullable(wakeup_);
                        wakeup_ = null;
                    }
                    wakeup.ifPresent(this.work_queue_::execute);
                }

                if (System.currentTimeMillis() >= deadline)
                    stop_loop = true;
            }

            synchronized (this) {
                running_ = false;
                fire_();
            }
        } catch (Exception e) {
            final Optional<Runnable> wakeup;
            synchronized (this) {
                running_ = false;
                exception = e;
                wakeup = Optional.ofNullable(wakeup_);
                wakeup_ = null;
            }
            wakeup.ifPresent(Runnable::run);
        }
    }

    @RequiredArgsConstructor
    private static class WakeupListener implements ForkJoinPool.ManagedBlocker {
        private final BooleanSupplier predicate;

        public synchronized void wakeup() {
            this.notify();
        }

        @Override
        public synchronized boolean block() throws InterruptedException {
            while (!predicate.getAsBoolean())
                this.wait();
            return true;
        }

        @Override
        public boolean isReleasable() {
            return predicate.getAsBoolean();
        }
    }

    private static class Delay implements ForkJoinPool.ManagedBlocker, Runnable {
        private Runnable wakeup;
        private final long deadline;
        private boolean wakeupReceived = false;

        public Delay(@NonNull Runnable wakeup, long tv, TimeUnit tu) {
            this.deadline = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(tv, tu);
            this.wakeup = wakeup;
        }

        private synchronized void fireWakeup() {
            if (wakeup == null) return;
            try {
                wakeup.run();
            } finally {
                wakeup = null;
            }
        }

        public synchronized void deliverWakeup() {
            wakeupReceived = true;
            this.notify();
            fireWakeup();
        }

        @Override
        public void run() {
            try {
                ForkJoinPool.managedBlock(this);
            } catch (InterruptedException ex) {
                LOG.log(Level.WARNING, "interrupted wait", ex);
            }
        }

        @Override
        public synchronized boolean block() throws InterruptedException {
            final long now = System.currentTimeMillis();
            if (wakeupReceived || deadline <= now) return true;

            this.wait(deadline - now);
            fireWakeup();
            return true;
        }

        @Override
        public boolean isReleasable() {
            synchronized (this) {
                if (wakeupReceived) {
                    fireWakeup();
                    return true;
                }
            }
            return deadline <= System.currentTimeMillis();
        }
    }
}
