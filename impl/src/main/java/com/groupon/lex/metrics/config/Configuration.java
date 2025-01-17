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
package com.groupon.lex.metrics.config;

import com.github.groupon.monsoon.tcp.TcpBuilder;
import com.groupon.lex.metrics.MetricRegistryInstance;
import com.groupon.lex.metrics.builders.collector.CollectorBuilder;
import com.groupon.lex.metrics.collector.collectd.CollectdPushBuilder;
import com.groupon.lex.metrics.collector.httpget.UrlGetBuilder;
import com.groupon.lex.metrics.collector.httpget.UrlJsonBuilder;
import com.groupon.lex.metrics.httpd.EndpointRegistration;
import com.groupon.lex.metrics.jmx.JmxBuilder;
import java.util.Collection;
import java.util.Objects;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import com.groupon.lex.metrics.resolver.NameBoundResolver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author ariane
 */
public class Configuration {
    /**
     * A mapping from collector name to collector builder.
     *
     * Contains all known collectors and is used by the parser to configure
     * collectors.
     * This Map is thread safe, it is fine to add new collectors at runtime.
     *
     * (You can also remove or replace collectors, but I'm not sure why you
     * would want to do that.)
     */
    public static final Map<String, Class<? extends CollectorBuilder>> COLLECTORS;

    static {
        COLLECTORS = new ConcurrentHashMap<>();
        COLLECTORS.put("url", UrlGetBuilder.class);
        COLLECTORS.put("json_url", UrlJsonBuilder.class);
        COLLECTORS.put("jmx_listener", JmxBuilder.class);
        COLLECTORS.put("collectd_push", CollectdPushBuilder.class);
        COLLECTORS.put("tcp", TcpBuilder.class);
    }

    private static Configuration defaultConfiguration_() {
        final JmxBuilder jmx_builder = new JmxBuilder();
        jmx_builder.setMain(Arrays.asList("metrics:name=*", "java.lang:*", "java.lang.*:*"));
        jmx_builder.setTagSet(NameBoundResolver.EMPTY);

        Configuration cfg = new Configuration(emptyList(),
                singleton(new CollectorBuilderWrapper("jmx_listener", jmx_builder)),
                emptyList());
        cfg.has_config_ = false;
        return cfg;
    }

    public static Configuration DEFAULT = defaultConfiguration_();
    private final static String PARAGRAPH_SEP = "\n\n";
    private boolean has_config_ = true;

    public static interface MetricRegistryConstructor<T extends MetricRegistryInstance> {
        public T construct(Supplier<DateTime> now, boolean has_config, EndpointRegistration api);
    }

    /**
     * Creates a new MetricRegistryInstance.
     * @param now A function returning DateTime.now(DateTimeZone.UTC).  Allowing specifying it, for the benefit of unit tests.
     * @param api The api with which to register configuration-specific endpoints.
     * @return A metric registry instance, initialized based on this configuration.
     * @throws RuntimeException if anything goes wrong during registration, for example the name is already in use.
     */
    public synchronized <T extends MetricRegistryInstance> T create(MetricRegistryConstructor<T> constructor, Supplier<DateTime> now, EndpointRegistration api) {
        if (needsResolve()) throw new IllegalStateException("Configuration.create invoked on unresolved configuration");

        T spawn = constructor.construct(now, has_config_, api);
        Logger.getLogger(MetricRegistryInstance.class.getName()).log(Level.INFO, "Using configuration:\n{0}", this);
        try {
            getMonitors().forEach((MonitorStatement mon) -> {
                try {
                    mon.apply(spawn);
                } catch (Exception ex) {
                    Logger.getLogger(MetricRegistryInstance.class.getName()).log(Level.SEVERE, "unable to apply configuration " + mon, ex);
                    throw new RuntimeException("failed to apply configuration " + mon, ex);
                }
            });

            getRules().stream().map(RuleStatement::get).forEach(spawn::decorate);
        } catch (RuntimeException ex) {
            spawn.close();
            throw ex;
        }
        return spawn;
    }

    /**
     * Creates a new MetricRegistryInstance.
     * @param api The api with which to register configuration-specific endpoints.
     * @return A metric registry instance, initialized based on this configuration.
     * @throws RuntimeException if anything goes wrong during registration, for example the name is already in use.
     */
    public <T extends MetricRegistryInstance> T create(MetricRegistryConstructor<T> constructor, EndpointRegistration api) {
        return create(constructor, () -> DateTime.now(DateTimeZone.UTC), api);
    }

    private final List<ImportStatement> imports_;
    private final List<MonitorStatement> monitors_;
    private final List<RuleStatement> rules_;

    /**
     * Reducer for configuration resolution.
     */
    private static class ConfigurationReducer {
        private final Stream<MonitorStatement> monitors_;
        private final Stream<RuleStatement> rules_;

        private static <T> Stream<T> concat(Stream<T> x, Stream<T> y) { return Stream.concat(x, y); }

        public ConfigurationReducer() {
            monitors_ = Stream.empty();
            rules_ = Stream.empty();
        }

        public ConfigurationReducer(ConfigurationReducer x, ConfigurationReducer y) {
            monitors_ = concat(x.monitors_, y.monitors_);
            rules_ = concat(x.rules_, y.rules_);
        }

        public ConfigurationReducer(ConfigurationReducer x, Configuration y) {
            if (y.needsResolve()) throw new IllegalArgumentException("reduction requires resolved configuration");
            monitors_ = concat(x.monitors_, y.getMonitors().stream());
            rules_ = concat(x.rules_, y.getRules().stream());
        }

        public Configuration resolve(Configuration owner) {
            return new Configuration(emptyList(),
                    concat(monitors_, owner.getMonitors().stream()).collect(Collectors.toList()),
                    concat(rules_, owner.getRules().stream()).collect(Collectors.toList()));
        }
    }

    public Configuration(
            Collection<ImportStatement> imports,
            Collection<MonitorStatement> monitors,
            Collection<RuleStatement> rules) {
        /* We fix the types of collections, to ensure the equals() methods function properly. */
        imports_ = new ArrayList<>(Objects.requireNonNull(imports));
        monitors_ = new ArrayList<>(Objects.requireNonNull(monitors));
        rules_ = new ArrayList<>(Objects.requireNonNull(rules));
    }

    public Collection<ImportStatement> getImports() { return imports_; }
    public Collection<MonitorStatement> getMonitors() { return monitors_; }
    public List<RuleStatement> getRules() { return rules_; }

    private static Configuration resolveImportStatement(ImportStatement stmt) throws ConfigurationException {
        try {
            return stmt.read();
        } catch (IOException ex) {
            throw new ConfigurationException("failed to read " + stmt.getConfigFile(), ex);
        }
    }

    public boolean needsResolve() { return !getImports().isEmpty(); }
    public boolean isResolved() { return !needsResolve(); }

    public Configuration resolve() throws ConfigurationException {
        if (isResolved()) return this;

        Collection<Configuration> import_cfgs = new ArrayList<>();
        for (ImportStatement import_stmt : getImports())
            import_cfgs.add(resolveImportStatement(import_stmt).resolve());

        return import_cfgs.stream()
                .reduce(new ConfigurationReducer(), ConfigurationReducer::new, ConfigurationReducer::new)
                .resolve(this);
    }

    public static Configuration readFromFile(File dir, Reader reader) throws IOException, ConfigurationException {
        return new ParserSupport(Optional.ofNullable(dir), reader).configuration();
    }

    public static Configuration readFromFile(File file) throws IOException, ConfigurationException {
        return readFromFile(file.getParentFile(), new FileReader(file));
    }

    public StringBuffer configString() {
        StringBuffer buf = new StringBuffer();
        if (!getImports().isEmpty()) {
            getImports().forEach((stmt) -> buf.append(stmt.configString()));
            buf.append(PARAGRAPH_SEP);
        }

        if (!getMonitors().isEmpty()) {
            getMonitors().forEach((stmt) -> buf.append(stmt.configString()));
            buf.append(PARAGRAPH_SEP);
        }

        if (!getRules().isEmpty()) {
            getRules().forEach((stmt) -> buf.append(stmt.configString()));
            buf.append(PARAGRAPH_SEP);
        }

        return buf;
    }

    @Override
    public String toString() {
        return configString().toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 29 * hash + Objects.hashCode(this.imports_);
        hash = 29 * hash + Objects.hashCode(this.monitors_);
        hash = 29 * hash + Objects.hashCode(this.rules_);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Configuration other = (Configuration) obj;
        if (!Objects.equals(this.imports_, other.imports_)) {
            return false;
        }
        if (!Objects.equals(this.monitors_, other.monitors_)) {
            return false;
        }
        if (!Objects.equals(this.rules_, other.rules_)) {
            return false;
        }
        return true;
    }
}
