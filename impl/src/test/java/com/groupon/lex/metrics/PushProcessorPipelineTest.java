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

import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

/**
 *
 * @author ariane
 */
@RunWith(MockitoJUnitRunner.class)
public class PushProcessorPipelineTest {
    @Mock
    private PushMetricRegistryInstance registry;
    @Mock
    private PushProcessor processor;

    @Test
    public void constructor() {
        try (PushProcessorPipeline impl = new PushProcessorPipeline(registry, 10, processor)) {
            assertEquals(10, impl.getIntervalSeconds());
            assertSame(registry, impl.getMetricRegistry());
        }
    }

    @Test(timeout = 15000)
    public void run_one_cycle() throws Exception {
        CompletableFuture<Object> run_implementation_called = new CompletableFuture<Object>();

        Mockito
                .doAnswer((Answer) (InvocationOnMock invocation) -> {
                    run_implementation_called.complete(null);
                    return null;
                })
                .when(processor).accept(Mockito.any(), Mockito.any(), Mockito.anyLong());

        try (PushProcessorPipeline impl = new PushProcessorPipeline(registry, 10, processor)) {
            impl.start();
            run_implementation_called.get();
        }

        Mockito.verify(processor, Mockito.times(1)).accept(Mockito.any(), Mockito.any(), Mockito.anyLong());
    }

    @Test(timeout = 25000)
    public void keep_running_when_excepting() throws Exception {
        CompletableFuture<Object> run_implementation_called = new CompletableFuture<Object>();

        Mockito
                .doThrow(new Exception())
                .doAnswer((Answer) (InvocationOnMock invocation) -> {
                    run_implementation_called.complete(null);
                    return null;
                })
                .when(processor).accept(Mockito.any(), Mockito.any(), Mockito.anyLong());

        try (PushProcessorPipeline impl = new PushProcessorPipeline(registry, 10, processor)) {
            impl.start();
            run_implementation_called.get();
        }

        Mockito.verify(processor, Mockito.times(2)).accept(Mockito.any(), Mockito.any(), Mockito.anyLong());
    }
}
