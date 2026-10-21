package io.mosip.registration.liveness.desktop;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reads the JPEG parts of a {@code multipart/x-mixed-replace} stream, as sent by an SBI {@code STREAM} call. Each
 * part starts with a {@code --boundary} line and headers; its body is read by {@code Content-Length}, or up to the
 * JPEG end marker when a device leaves that header out. Lines before the first boundary (the HTTP response header)
 * are skipped. Not thread-safe.
 */
final class MjpegPartReader {

    private static final String CONTENT_LENGTH = "content-length:";

    private final DataInputStream in;

    MjpegPartReader(InputStream stream) {
        this.in = new DataInputStream(new BufferedInputStream(stream));
    }

    /**
     * @return the next part's body, or {@code null} when the stream ends between parts
     * @throws EOFException if the stream ends inside a part
     * @throws IOException  if reading fails
     */
    byte[] nextPart() throws IOException {
        String line;
        do {
            line = readLine();
            if (line == null) {
                return null;
            }
        } while (!line.startsWith("--"));

        int contentLength = -1;
        while (true) {
            line = readLine();
            if (line == null) {
                throw new EOFException("Stream ended inside part headers");
            }
            if (line.isEmpty()) {
                break;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith(CONTENT_LENGTH)) {
                contentLength = Integer.parseInt(line.substring(CONTENT_LENGTH.length()).trim());
            }
        }
        if (contentLength >= 0) {
            byte[] body = new byte[contentLength];
            in.readFully(body);
            return body;
        }
        return readToJpegEnd();
    }

    private byte[] readToJpegEnd() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("Stream ended inside a frame");
            }
            body.write(b);
            if (previous == 0xFF && b == 0xD9) {
                return body.toByteArray();
            }
            previous = b;
        }
    }

    /** @return the next line without its line break, or {@code null} at the end of the stream */
    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                break;
            }
            line.write(b);
        }
        if (b < 0 && line.size() == 0) {
            return null;
        }
        String text = new String(line.toByteArray(), StandardCharsets.US_ASCII);
        return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
    }
}
