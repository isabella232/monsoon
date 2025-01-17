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

import com.groupon.lex.metrics.history.v2.Compression;
import com.groupon.lex.metrics.history.xdr.support.FilePos;
import com.groupon.lex.metrics.history.xdr.support.reader.FileChannelSegmentReader;
import com.groupon.lex.metrics.lib.GCCloseable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.acplt.oncrpc.OncRpcException;
import org.acplt.oncrpc.XdrAble;
import org.acplt.oncrpc.XdrDecodingStream;
import org.acplt.oncrpc.XdrEncodingStream;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AbstractSegmentWriterTest {
    private XdrAbleImpl xdrAble = new XdrAbleImpl("Pop goes the weasel!", 23, 29);
    private File file;
    private AbstractSegmentWriter writeable;

    @Before
    public void setup() throws Exception {
        file = File.createTempFile("monsoon-", "-AbstractSegmentWriter");
        file.deleteOnExit();

        writeable = new AbstractSegmentWriter() {
            @Override
            public XdrAble encode(long[] timestamps) {
                return xdrAble;
            }
        };
    }

    @Test
    public void write() throws Exception {
        GCCloseable<FileChannel> fd = new GCCloseable<>(FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE));
        FilePos pos = writeable.write(new AbstractSegmentWriter.Writer(fd.get(), 0, Compression.NONE, false), new long[]{9});

        assertEquals(xdrAble, new FileChannelSegmentReader<>(XdrAbleImpl::new, fd, pos, Compression.NONE).decode());
    }

    @Test
    public void writeCompressed() throws Exception {
        GCCloseable<FileChannel> fd = new GCCloseable<>(FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE));
        FilePos pos = writeable.write(new AbstractSegmentWriter.Writer(fd.get(), 0, Compression.DEFAULT_APPEND, false), new long[]{9});

        assertEquals(xdrAble, new FileChannelSegmentReader<>(XdrAbleImpl::new, fd, pos, Compression.DEFAULT_APPEND).decode());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class XdrAbleImpl implements XdrAble {
        private String strVal;
        private int intVal;
        private long longVal;

        @Override
        public void xdrEncode(XdrEncodingStream xdr) throws OncRpcException, IOException {
            xdr.xdrEncodeString(strVal);
            xdr.xdrEncodeInt(intVal);
            xdr.xdrEncodeLong(longVal);
        }

        @Override
        public void xdrDecode(XdrDecodingStream xdr) throws OncRpcException, IOException {
            strVal = xdr.xdrDecodeString();
            intVal = xdr.xdrDecodeInt();
            longVal = xdr.xdrDecodeLong();
        }
    }
}
