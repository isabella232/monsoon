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
package com.groupon.lex.metrics.history.xdr.support.reader;

import com.groupon.lex.metrics.history.xdr.support.IOLengthVerificationFailed;
import java.io.EOFException;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author ariane
 */
public class SizeVerifyingReaderTest {
    private Path file;
    private byte data[];

    @Before
    public void setup() throws Exception {
        File fileName = File.createTempFile("monsoon-", "-SizeVerifyingReader");
        fileName.deleteOnExit();
        file = fileName.toPath();

        data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; ++i)
            data[i] = (byte) (i ^ (i % 97));

        try (FileChannel fd = FileChannel.open(file, StandardOpenOption.WRITE)) {
            for (int i = 0; i < 2; ++i) {
                ByteBuffer buf = ByteBuffer.wrap(data);
                while (buf.hasRemaining())
                    fd.write(buf);
            }
        }
    }

    @Test
    public void read() throws Exception {
        byte output[] = new byte[data.length];

        try (FileChannel fd = FileChannel.open(file, StandardOpenOption.READ)) {
            try (FileReader reader = new SizeVerifyingReader(new FileChannelReader(fd, 0), data.length)) {
                int i = 0;
                while (i < output.length)
                    i += reader.read(ByteBuffer.wrap(output, i, Integer.min(output.length - i, 128)));
            }
        }

        assertArrayEquals(data, output);
    }

    @Test(expected = EOFException.class)
    public void readTooMuch() throws Exception {
        byte output[] = new byte[data.length + 1024];

        try (FileChannel fd = FileChannel.open(file, StandardOpenOption.READ)) {
            try (FileReader reader = new SizeVerifyingReader(new FileChannelReader(fd, 0), data.length)) {
                int i = 0;
                while (i < output.length)
                    i += reader.read(ByteBuffer.wrap(output, i, Integer.min(output.length - i, 128)));
            }
        }
    }

    @Test(expected = IOLengthVerificationFailed.class)
    public void readTooLittle() throws Exception {
        byte output[] = new byte[data.length - 1024];

        try (FileChannel fd = FileChannel.open(file, StandardOpenOption.READ)) {
            try (FileReader reader = new SizeVerifyingReader(new FileChannelReader(fd, 0), data.length)) {
                int i = 0;
                while (i < output.length)
                    i += reader.read(ByteBuffer.wrap(output, i, Integer.min(output.length - i, 128)));
            }
        }
    }
}
