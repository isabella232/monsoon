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
package com.groupon.lex.metrics.collector.collectd;

import com.groupon.lex.metrics.GroupName;
import com.groupon.lex.metrics.Metric;
import com.groupon.lex.metrics.MetricGroup;
import com.groupon.lex.metrics.MetricName;
import com.groupon.lex.metrics.MetricValue;
import com.groupon.lex.metrics.SimpleGroupPath;
import java.util.Arrays;
import static java.util.Collections.singletonMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author ariane
 */
public class CollectdPushCollector_CollectdMessageTest {
    private CollectdPushCollector.CollectdMessage msg;

    @Before
    public void setup() {
        msg = new CollectdPushCollector.CollectdMessage();
        msg.values = Arrays.asList(10.0d, 20l, 30l);
        msg.dstypes = Arrays.asList("gauge", "gauge", "gauge");
        msg.dsnames = Arrays.asList("x", "y", "z");
        msg.time = DateTime.now(DateTimeZone.UTC).getMillis() / 1000.0;
        msg.interval = 60;
        msg.host = "otherhost";
        msg.plugin = "plugin";
        msg.plugin_instance = "instance-0";
        msg.type = "type";
        msg.type_instance = "type-0";
    }

    @Test
    public void getKey() {
        assertEquals(new CollectdPushCollector.CollectdKey("otherhost", "plugin", "instance-0", "type", "type-0"),
                msg.getKey());
    }

    @Test
    public void metricCount() {
        assertEquals(3, msg.metricCount());
    }

    @Test
    public void toMetricGroup() {
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(GroupName.valueOf(SimpleGroupPath.valueOf("foo", "plugin", "instance-0"), singletonMap("host", MetricValue.fromStrValue("otherhost"))),
                group.getName());

        /** Convenience for testing: metrics as a map. */
        Map<MetricName, MetricValue> metric_map = Arrays.stream(group.getMetrics())
                .collect(Collectors.toMap(Metric::getName, Metric::getValue));
        assertThat(metric_map,
                allOf(
                        hasEntry(MetricName.valueOf("type", "type-0", "x"), MetricValue.fromDblValue(10)),
                        hasEntry(MetricName.valueOf("type", "type-0", "y"), MetricValue.fromIntValue(20)),
                        hasEntry(MetricName.valueOf("type", "type-0", "z"), MetricValue.fromIntValue(30))));
    }

    @Test
    public void special_handling_of_value() {
        msg = new CollectdPushCollector.CollectdMessage();
        msg.values = Arrays.asList(10.0d);
        msg.dstypes = Arrays.asList("gauge");
        msg.dsnames = Arrays.asList("value");
        msg.time = DateTime.now(DateTimeZone.UTC).getMillis() / 1000.0;
        msg.interval = 60;
        msg.host = "otherhost";
        msg.plugin = "plugin";
        msg.plugin_instance = "instance-0";
        msg.type = "type";
        msg.type_instance = "type-0";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(GroupName.valueOf(SimpleGroupPath.valueOf("foo", "plugin", "instance-0"), singletonMap("host", MetricValue.fromStrValue("otherhost"))),
                group.getName());
        assertEquals(1, group.getMetrics().length);
        assertEquals(MetricName.valueOf("type", "type-0"), group.getMetrics()[0].getName());
        assertEquals(MetricValue.fromDblValue(10.0d), group.getMetrics()[0].getValue());
    }

    @Test
    public void no_special_handling_of_single_entry() {
        msg = new CollectdPushCollector.CollectdMessage();
        msg.values = Arrays.asList(10.0d);
        msg.dstypes = Arrays.asList("gauge");
        msg.dsnames = Arrays.asList("x");
        msg.time = DateTime.now(DateTimeZone.UTC).getMillis() / 1000.0;
        msg.interval = 60;
        msg.host = "otherhost";
        msg.plugin = "plugin";
        msg.plugin_instance = "instance-0";
        msg.type = "type";
        msg.type_instance = "type-0";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(GroupName.valueOf(SimpleGroupPath.valueOf("foo", "plugin", "instance-0"), singletonMap("host", MetricValue.fromStrValue("otherhost"))),
                group.getName());
        assertEquals(1, group.getMetrics().length);
        assertEquals(MetricName.valueOf("type", "type-0", "x"), group.getMetrics()[0].getName());
        assertEquals(MetricValue.fromDblValue(10.0d), group.getMetrics()[0].getValue());
    }

    @Test
    public void no_special_handling_of_value_when_multiple_entries() {
        msg = new CollectdPushCollector.CollectdMessage();
        msg.values = Arrays.asList(10.0d, 11l);
        msg.dstypes = Arrays.asList("gauge", "gauge");
        msg.dsnames = Arrays.asList("value", "x");
        msg.time = DateTime.now(DateTimeZone.UTC).getMillis() / 1000.0;
        msg.interval = 60;
        msg.host = "otherhost";
        msg.plugin = "plugin";
        msg.plugin_instance = "instance-0";
        msg.type = "type";
        msg.type_instance = "type-0";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(GroupName.valueOf(SimpleGroupPath.valueOf("foo", "plugin", "instance-0"), singletonMap("host", MetricValue.fromStrValue("otherhost"))),
                group.getName());
        assertEquals(2, group.getMetrics().length);

        /** Convenience for testing: metrics as a map. */
        Map<MetricName, MetricValue> metric_map = Arrays.stream(group.getMetrics())
                .collect(Collectors.toMap(Metric::getName, Metric::getValue));
        assertThat(metric_map,
                allOf(
                        hasEntry(MetricName.valueOf("type", "type-0", "value"), MetricValue.fromDblValue(10)),
                        hasEntry(MetricName.valueOf("type", "type-0", "x"), MetricValue.fromIntValue(11))));
    }

    @Test
    public void tag_handling() {
        msg.plugin_instance = "instance-0[zero=0,aleph=\u2135]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("zero", MetricValue.fromIntValue(0));
                            put("aleph", MetricValue.fromStrValue("\u2135"));
                        }}),
                group.getName());
    }

    @Test
    public void tag_handling_hosttag_overrides_host() {
        msg.plugin_instance = "instance-0[host=dont-call-me-otherhost,aleph=\u2135]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("dont-call-me-otherhost"));
                            put("aleph", MetricValue.fromStrValue("\u2135"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_backslash() {
        msg.plugin_instance = "instance-0[test=\"\\\\\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\\"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_backslash_a() {
        msg.plugin_instance = "instance-0[test=\"\\a\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\007"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_backslash_b() {
        msg.plugin_instance = "instance-0[test=\"\\b\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\010"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_backslash_t() {
        msg.plugin_instance = "instance-0[test=\"\\t\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\t"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_backslash_n() {
        msg.plugin_instance = "instance-0[test=\"\\n\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\n"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_backslash_v() {
        msg.plugin_instance = "instance-0[test=\"\\v\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\013"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_backslash_f() {
        msg.plugin_instance = "instance-0[test=\"\\f\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\f"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_backslash_r() {
        msg.plugin_instance = "instance-0[test=\"\\r\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\r"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_single_quote() {
        msg.plugin_instance = "instance-0[test=\"\\'\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("'"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_double_quote() {
        msg.plugin_instance = "instance-0[test=\"\\\"\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\""));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_slash() {
        msg.plugin_instance = "instance-0[test=\"\\/\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("/"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_octal3() {
        msg.plugin_instance = "instance-0[test=\"\\002\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\002"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_octal2() {
        msg.plugin_instance = "instance-0[test=\"\\02\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\002"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_octal1() {
        msg.plugin_instance = "instance-0[test=\"\\2\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\002"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_null_char() {
        msg.plugin_instance = "instance-0[test=\"\\0\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\000"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_hex() {
        msg.plugin_instance = "instance-0[test=\"\\x2f\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\u002f"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_unicode_2byte() {
        msg.plugin_instance = "instance-0[test=\"\\u202f\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue("\u202f"));
                        }}),
                group.getName());
    }

    @Test
    public void handle_escaped_unicode_4byte() {
        final String expected = String.valueOf(Character.toChars(0x0009202f));
        msg.plugin_instance = "instance-0[test=\"\\U0009202f\"]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromStrValue(expected));
                        }}),
                group.getName());
    }

    @Test
    public void handle_uint_dec() {
        msg.plugin_instance = "instance-0[test=19]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromIntValue(19));
                        }}),
                group.getName());
    }

    @Test
    public void handle_uint_oct() {
        msg.plugin_instance = "instance-0[test=017]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromIntValue(017));
                        }}),
                group.getName());
    }

    @Test
    public void handle_uint_hex() {
        msg.plugin_instance = "instance-0[test=0x19]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromIntValue(0x19));
                        }}),
                group.getName());
    }

    @Test
    public void handle_int_dec() {
        msg.plugin_instance = "instance-0[test=-19]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromIntValue(-19));
                        }}),
                group.getName());
    }

    @Test
    public void handle_int_oct() {
        msg.plugin_instance = "instance-0[test=-017]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromIntValue(-017));
                        }}),
                group.getName());
    }

    @Test
    public void handle_int_hex() {
        msg.plugin_instance = "instance-0[test=-0x19]";
        MetricGroup group = msg.toMetricGroup(SimpleGroupPath.valueOf("foo"));

        assertEquals(
                GroupName.valueOf(
                        SimpleGroupPath.valueOf("foo", "plugin", "instance-0"),
                        new HashMap<String, MetricValue>() {{
                            put("host", MetricValue.fromStrValue("otherhost"));
                            put("test", MetricValue.fromIntValue(-0x19));
                        }}),
                group.getName());
    }
}
