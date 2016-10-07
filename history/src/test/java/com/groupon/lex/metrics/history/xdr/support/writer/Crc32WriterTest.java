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
package com.groupon.lex.metrics.history.xdr.support.writer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class Crc32WriterTest {
    private FileWriter fileWriter = new FileWriter() {
        @Override
        public int write(ByteBuffer data) throws IOException {
            int wlen = data.remaining();
            data.position(data.limit());
            return wlen;
        }

        @Override
        public ByteBuffer allocateByteBuffer(int size) {
            return ByteBuffer.allocate(size);
        }
        @Override
        public void close() throws IOException {}
    };

    private byte data[];
    private int expectedCrc;

    @Before
    public void setup() {
        data = new byte[1024];
        for (int i = 0; i < data.length; ++i)
            data[i] = (byte)(i ^ (i % 97));

        CRC32 crc = new CRC32();
        crc.update(data);
        expectedCrc = (int)crc.getValue();
    }

    @Test
    public void write() throws Exception {
        try (Crc32Writer writer = new Crc32Writer(fileWriter)) {
            ByteBuffer buf = ByteBuffer.wrap(data);
            while (buf.hasRemaining())
                writer.write(buf);

            assertEquals(expectedCrc, writer.getCrc32());
        }
    }
}
