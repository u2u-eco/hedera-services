/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.node.app.grpc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import utils.TestUtils;

class NoopMarshallerTest {
    private final NoopMarshaller marshaller = new NoopMarshaller();

    @Test
    void nullByteBufferThrows() {
        //noinspection resource,ConstantConditions
        assertThrows(NullPointerException.class, () -> marshaller.stream(null));
    }

    @Test
    void nullStreamThrows() {
        //noinspection ConstantConditions
        assertThrows(NullPointerException.class, () -> marshaller.parse(null));
    }

    private static Stream<Arguments> provideBuffers() {
        return Stream.of(
                Arguments.of(0, 0),
                Arguments.of(100, 0),
                Arguments.of(100, 80),
                Arguments.of(100, 100));
    }

    @ParameterizedTest(name = "A buffer with capacity {0} and position {1}")
    @MethodSource("provideBuffers")
    @DisplayName("ByteBuffer contents are streamed")
    void byteBufferContentsAreStreamed(int capacity, int position) throws IOException {
        final var arr = TestUtils.randomBytes(capacity);
        final var buf = ByteBuffer.wrap(arr);
        buf.position(position);
        try (final var stream = marshaller.stream(buf)) {
            final var numBytesToRead = buf.remaining();
            for (int i = 0; i < numBytesToRead; i++) {
                assertEquals(Byte.toUnsignedInt(arr[i + position]), stream.read());
                assertEquals(numBytesToRead - i - 1, stream.available());
            }

            assertEquals(-1, stream.read());
        }
    }

    @Test
    void callingStreamTwiceReturnsDifferentStreamsOnTheSameUnderlyingBuffer() throws IOException {
        final var arr = TestUtils.randomBytes(100);
        final var buf = ByteBuffer.wrap(arr);
        buf.position(50);

        try (final var stream1 = marshaller.stream(buf);
                final var stream2 = marshaller.stream(buf)) {

            assertEquals(stream1.available(), stream2.available());
            assertNotEquals(-1, stream1.read());
            assertEquals(stream1.available(), stream2.available());

            assertEquals(49, stream2.skip(49));
            assertEquals(0, stream1.available());
        }
    }

    @Test
    void parseStream() {
        final var arr = TestUtils.randomBytes(100);
        final var stream = new ByteArrayInputStream(arr);
        final var buf = marshaller.parse(stream);

        assertEquals(arr.length, buf.remaining());
        for (byte b : arr) {
            assertEquals(b, buf.get());
        }
    }

    @ParameterizedTest(name = "With {0} bytes")
    @ValueSource(ints = {1024 * 6 + 1, 1024 * 1024})
    void parseStreamThatIsTooBig(int numBytes) {
        final var arr = TestUtils.randomBytes(numBytes);
        final var stream = new ByteArrayInputStream(arr);
        assertThrows(RuntimeException.class, () -> marshaller.parse(stream));
    }

    @Test
    void parseStreamThatFailsInTheMiddle() throws IOException {
        final var arr = TestUtils.randomBytes(100);
        try (final var stream = Mockito.mock(InputStream.class)) {
            Mockito.when(stream.read(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(
                            invocation -> {
                                byte[] data = invocation.getArgument(0);
                                int offset = invocation.getArgument(1);
                                // Don't quite read everything
                                System.arraycopy(arr, 0, data, offset, 99);
                                return 99;
                            })
                    .thenThrow(new IOException("Stream Terminated unexpectedly"));

            assertThrows(RuntimeException.class, () -> marshaller.parse(stream));
        }
    }

    @Test
    void parseStreamThatTakesMultipleReads() throws IOException {
        final var arr = TestUtils.randomBytes(100);
        try (final var stream = Mockito.mock(InputStream.class)) {
            Mockito.when(stream.read(Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenAnswer(
                            invocation -> {
                                byte[] data = invocation.getArgument(0);
                                int offset = invocation.getArgument(1);
                                // Don't quite read everything
                                System.arraycopy(arr, 0, data, offset, 50);
                                return 50;
                            })
                    .thenAnswer(
                            invocation -> {
                                byte[] data = invocation.getArgument(0);
                                int offset = invocation.getArgument(1);
                                // Read the rest
                                System.arraycopy(arr, 50, data, offset, 50);
                                return 50;
                            })
                    .thenReturn(-1);

            final var buf = marshaller.parse(stream);
            assertEquals(arr.length, buf.remaining());
            for (byte b : arr) {
                assertEquals(b, buf.get());
            }
        }
    }
}
