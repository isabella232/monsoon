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
import java.io.OutputStream;
import static java.lang.Math.min;
import java.nio.ByteBuffer;
import static java.util.Objects.requireNonNull;
import lombok.NonNull;

public class AbstractOutputStreamWriter implements FileWriter {
    private final OutputStream out;

    protected AbstractOutputStreamWriter(OutputStream out) throws IOException {
        this.out = requireNonNull(out);
    }

    @Override
    public int write(ByteBuffer data) throws IOException {
        if (data.hasArray()) {
            final int wlen = data.remaining();
            out.write(data.array(), data.arrayOffset() + data.position(), wlen);
            data.position(data.limit());
            return wlen;
        } else {
            int written = 0;
            byte buf[] = new byte[512];
            while (data.hasRemaining()) {
                final int buflen = min(data.remaining(), buf.length);
                data.get(buf, 0, buflen);
                out.write(buf, 0, buflen);
                written += buflen;
            }
            return written;
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public ByteBuffer allocateByteBuffer(int size) {
        return ByteBuffer.allocate(size);
    }

    protected static OutputStream newAdapter(FileWriter out) {
        return new Adapter(out);
    }

    private static class Adapter extends OutputStream {
        private final FileWriter out;

        public Adapter(@NonNull FileWriter out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            byte tmp[] = new byte[1];
            tmp[0] = (byte) (b & 0xff);
            write(tmp);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(b, off, len);
            while (buf.hasRemaining())
                out.write(buf);
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }
}
