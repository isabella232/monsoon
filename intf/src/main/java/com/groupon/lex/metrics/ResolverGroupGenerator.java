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
package com.groupon.lex.metrics;

import com.groupon.lex.metrics.resolver.NameBoundResolver;
import com.groupon.lex.metrics.resolver.NamedResolverMap;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.unmodifiableCollection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A group generator that manages GroupGenerators based on the active resolver.
 *
 * When a group generator does not exist, for a given argument set, it will
 * create one. When the resolver does not return a given argument set, the
 * associated group generator will be closed.
 */
@RequiredArgsConstructor
public class ResolverGroupGenerator implements GroupGenerator {
    private static final Logger LOG = Logger.getLogger(ResolverGroupGenerator.class.getName());
    private final Map<NamedResolverMap, GroupGenerator> generators = new HashMap<>();
    @NonNull
    @Getter
    private final NameBoundResolver resolver;
    @NonNull
    @Getter
    private final GroupGeneratorFactory createGenerator;

    public Collection<GroupGenerator> getCurrentGenerators() {
        return unmodifiableCollection(generators.values());
    }

    @Override
    public Collection<CompletableFuture<? extends Collection<? extends MetricGroup>>> getGroups(Executor threadpool, CompletableFuture<TimeoutObject> timeout) throws Exception {
        final Set<NamedResolverMap> resolvedMaps = resolver.resolve().collect(Collectors.toSet());

        // Close all generators that are not to run.
        final Iterator<Map.Entry<NamedResolverMap, GroupGenerator>> genIter = generators.entrySet().iterator();
        while (genIter.hasNext()) {
            final Map.Entry<NamedResolverMap, GroupGenerator> gen = genIter.next();
            if (!resolvedMaps.contains(gen.getKey())) {
                final GroupGenerator toBeClosed = gen.getValue();
                genIter.remove();
                threadpool.execute(() -> {
                    try {
                        toBeClosed.close();
                    } catch (Exception ex) {
                        LOG.log(Level.WARNING, "unable to close " + toBeClosed, ex);
                    }
                });
            }
        }

        // Create missing generators.
        resolvedMaps.removeAll(generators.keySet());
        for (NamedResolverMap resolvedMap : resolvedMaps) {
            try {
                generators.put(resolvedMap, requireNonNull(createGenerator.create(resolvedMap)));
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "unable to create generator", ex);
                throw ex;
            }
        }

        // Evaluate all generators.
        final List<CompletableFuture<? extends Collection<? extends MetricGroup>>> results = new ArrayList<>(generators.size());
        try {
            for (GroupGenerator generator : generators.values())
                results.addAll(generator.getGroups(threadpool, timeout));
        } catch (Exception ex) {
            results.forEach(future -> future.cancel(false));
            throw ex;
        }
        return results;
    }

    @Override
    public void close() throws Exception {
        Exception thrown = null;

        try {
            resolver.close();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "unable to close resolver " + resolver, ex);
            if (thrown == null)
                thrown = ex;
            else
                thrown.addSuppressed(ex);
        }

        for (GroupGenerator generator : generators.values()) {
            try {
                generator.close();
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "unable to close generator " + generator, ex);
                if (thrown == null)
                    thrown = ex;
                else
                    thrown.addSuppressed(ex);
            }
        }
        generators.clear();

        if (thrown != null) throw thrown;
    }

    public static interface GroupGeneratorFactory {
        public GroupGenerator create(NamedResolverMap args) throws Exception;
    }
}
