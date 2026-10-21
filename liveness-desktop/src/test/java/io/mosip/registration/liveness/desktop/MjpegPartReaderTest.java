package io.mosip.registration.liveness.desktop;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MjpegPartReaderTest {

    private static final byte[] FIRST = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, (byte) 0xFF, (byte) 0xD9};
    private static final byte[] SECOND = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 3, (byte) 0xFF, (byte) 0xD9};

    @Test
    @DisplayName("Reads each part of a MockMDS stream by its Content-Length, then reports the end")
    void readsMockMdsParts() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ascii(stream, "HTTP/1.0 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=--BoundaryString\r\n\r\n");
        mockMdsPart(stream, FIRST);
        mockMdsPart(stream, SECOND);

        MjpegPartReader reader = new MjpegPartReader(new ByteArrayInputStream(stream.toByteArray()));

        assertArrayEquals(FIRST, reader.nextPart());
        assertArrayEquals(SECOND, reader.nextPart());
        assertNull(reader.nextPart());
    }

    @Test
    @DisplayName("Header names are case-insensitive and may have a space after the colon")
    void headerNamesAreCaseInsensitive() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ascii(stream, "--frame\r\ncontent-type: image/jpeg\r\ncontent-length: " + FIRST.length + "\r\n\r\n");
        stream.write(FIRST);

        assertArrayEquals(FIRST, new MjpegPartReader(new ByteArrayInputStream(stream.toByteArray())).nextPart());
    }

    @Test
    @DisplayName("A part without Content-Length is read up to the JPEG end marker")
    void partWithoutContentLengthEndsAtEndMarker() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ascii(stream, "--frame\r\nContent-Type: image/jpeg\r\n\r\n");
        stream.write(FIRST);
        ascii(stream, "\r\n--frame\r\nContent-Type: image/jpeg\r\n\r\n");
        stream.write(SECOND);

        MjpegPartReader reader = new MjpegPartReader(new ByteArrayInputStream(stream.toByteArray()));

        assertArrayEquals(FIRST, reader.nextPart());
        assertArrayEquals(SECOND, reader.nextPart());
    }

    @Test
    @DisplayName("A stream that stops inside a frame is an error, not a short frame")
    void truncatedFrameIsAnError() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ascii(stream, "--BoundaryString\r\nContent-Length:100\r\n\r\n");
        stream.write(FIRST);

        MjpegPartReader reader = new MjpegPartReader(new ByteArrayInputStream(stream.toByteArray()));

        assertThrows(EOFException.class, reader::nextPart);
    }

    /** One part exactly as MockMDS writes it (SBIServiceResponse.createHeader / createFooter). */
    private static void mockMdsPart(ByteArrayOutputStream stream, byte[] jpeg) throws IOException {
        ascii(stream, "--BoundaryString\r\nAccess-Control-Allow-Origin:*\r\nContent-Type:image/jpeg\r\nContent-Length:"
                + jpeg.length + "\r\n\r\n");
        stream.write(jpeg);
        ascii(stream, "\r\n\r\n");
    }

    private static void ascii(ByteArrayOutputStream stream, String text) throws IOException {
        stream.write(text.getBytes(StandardCharsets.US_ASCII));
    }
}
